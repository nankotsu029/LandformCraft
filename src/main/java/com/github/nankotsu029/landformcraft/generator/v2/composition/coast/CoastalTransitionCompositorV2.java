package com.github.nankotsu029.landformcraft.generator.v2.composition.coast;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalTransitionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Integer-only, order-independent compositor for the four V2 coastal feature rasters. */
public final class CoastalTransitionCompositorV2 {
    public static final String VERSION = "coastal-transition-fixed-v1";
    public static final int FIXED_SCALE = 1_000_000;
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final int MAXIMUM_CORE_EXTENT = 256;
    public static final int MAXIMUM_HALO_XZ = 32;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 2_000_000L;

    private final CoastalTransitionPlanV2 plan;
    private final int width;
    private final int length;
    private final List<LayerBinding> layers;
    private final Map<String, CoastalTransitionPlanV2.Interaction> interactionsByPair;

    public CoastalTransitionCompositorV2(
            CoastalTransitionPlanV2 plan,
            int width,
            int length,
            List<LayerBinding> layers
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000) {
            throw failure("v2.coastal-transition-dimensions", "dimensions must be within 1..1000");
        }
        this.width = width;
        this.length = length;
        this.layers = List.copyOf(Objects.requireNonNull(layers, "layers")).stream()
                .sorted(Comparator.comparing(binding -> binding.contributor().featureId()))
                .toList();
        if (!this.layers.stream().map(LayerBinding::contributor).toList().equals(plan.contributors())) {
            throw failure("v2.coastal-transition-binding", "layer bindings must match plan contributors exactly");
        }
        Map<String, CoastalTransitionPlanV2.Interaction> interactions = new HashMap<>();
        for (CoastalTransitionPlanV2.Interaction interaction : plan.interactions()) {
            interactions.put(pair(interaction.firstFeatureId(), interaction.secondFeatureId()), interaction);
        }
        this.interactionsByPair = Map.copyOf(interactions);
    }

    public CompositionSample sampleAt(
            int globalX,
            int globalZ,
            HardLandWaterSourceV2 hardSource
    ) {
        requireCoordinate(globalX, globalZ);
        Objects.requireNonNull(hardSource, "hardSource");
        List<SampledLayer> active = activeAt(globalX, globalZ);
        if (active.isEmpty()) {
            HardLandWaterSourceV2.Classification hard = Objects.requireNonNull(
                    hardSource.classificationAt(globalX, globalZ), "hard classification");
            if (hard != HardLandWaterSourceV2.Classification.UNSPECIFIED) {
                throw failure("v2.coastal-transition-hard-unrepresented",
                        "HARD LAND_WATER_MASK cell has no coastal contributor at " + globalX + ',' + globalZ);
            }
            return CompositionSample.OUTSIDE;
        }
        validatePairContracts(active, globalX, globalZ);
        validateHardLayers(active, globalX, globalZ);
        active = removeExplicitlyCoveredWater(active);

        HardLandWaterSourceV2.Classification hard = Objects.requireNonNull(
                hardSource.classificationAt(globalX, globalZ), "hard classification");
        Integer protectedLandWater;
        if (hard == HardLandWaterSourceV2.Classification.UNSPECIFIED) {
            protectedLandWater = hardLayerClassification(active);
        } else {
            protectedLandWater = hard == HardLandWaterSourceV2.Classification.LAND ? 1 : 0;
        }
        if (protectedLandWater != null) {
            List<SampledLayer> matching = active.stream()
                    .filter(layer -> layer.sample().landWater() == protectedLandWater)
                    .toList();
            if (matching.isEmpty()) {
                throw failure("v2.coastal-transition-hard-conflict",
                        "no coastal contributor matches HARD land-water classification at "
                                + globalX + ',' + globalZ);
            }
            active = matching;
        }
        if (active.size() == 1) {
            SampledLayer only = active.getFirst();
            return new CompositionSample(
                    true, only.sample().landWater(), only.sample().surfaceHeightMillionths(),
                    only.binding().contributor().ownerIndex(), FIXED_SCALE, 0,
                    protectedLandWater != null || only.sample().hardConstrained());
        }

        long landWeight = 0;
        long waterWeight = 0;
        List<WeightedLayer> weighted = new ArrayList<>(active.size());
        for (SampledLayer layer : active) {
            long weight = weight(layer, active);
            weighted.add(new WeightedLayer(layer, weight));
            if (layer.sample().landWater() == 1) landWeight = Math.addExact(landWeight, weight);
            else waterWeight = Math.addExact(waterWeight, weight);
        }
        int landWater;
        if (protectedLandWater != null) {
            landWater = protectedLandWater;
        } else if (landWeight == waterWeight) {
            throw failure("v2.coastal-transition-ambiguous",
                    "priority blend has an equal categorical vote at " + globalX + ',' + globalZ);
        } else {
            landWater = landWeight > waterWeight ? 1 : 0;
        }

        long totalAll = Math.addExact(landWeight, waterWeight);
        long totalMatching = 0;
        long weightedHeight = 0;
        WeightedLayer owner = null;
        for (WeightedLayer candidate : weighted) {
            if (candidate.layer().sample().landWater() != landWater) continue;
            totalMatching = Math.addExact(totalMatching, candidate.weight());
            weightedHeight = Math.addExact(weightedHeight, Math.multiplyExact(
                    candidate.weight(), candidate.layer().sample().surfaceHeightMillionths()));
            if (owner == null || candidate.weight() > owner.weight()
                    || (candidate.weight() == owner.weight()
                    && candidate.layer().binding().contributor().featureId().compareTo(
                    owner.layer().binding().contributor().featureId()) < 0)) {
                owner = candidate;
            }
        }
        if (owner == null || totalMatching == 0) {
            throw failure("v2.coastal-transition-ambiguous", "priority blend produced no owner");
        }
        int blendWeight = protectedLandWater != null ? FIXED_SCALE
                : Math.toIntExact(Math.floorDiv(Math.multiplyExact(totalMatching, FIXED_SCALE), totalAll));
        return new CompositionSample(
                true,
                landWater,
                roundDivide(weightedHeight, totalMatching),
                owner.layer().binding().contributor().ownerIndex(),
                blendWeight,
                0,
                protectedLandWater != null);
    }

    public ConflictDiagnostic diagnoseAt(int globalX, int globalZ, HardLandWaterSourceV2 hardSource) {
        try {
            sampleAt(globalX, globalZ, hardSource);
            return null;
        } catch (CoastalTransitionException exception) {
            List<String> activeIds = activeAt(globalX, globalZ).stream()
                    .map(layer -> layer.binding().contributor().featureId()).toList();
            return new ConflictDiagnostic(exception.ruleId(), globalX, globalZ, activeIds, exception.getMessage());
        }
    }

    public CoastalTransitionWindowV2 renderWindow(
            int coreOriginX,
            int coreOriginZ,
            int coreWidth,
            int coreLength,
            int haloXZ,
            HardLandWaterSourceV2 hardSource,
            CancellationToken token
    ) {
        Objects.requireNonNull(hardSource, "hardSource");
        Objects.requireNonNull(token, "token");
        CoastalTransitionWindowV2.Bounds bounds = windowBounds(
                coreOriginX, coreOriginZ, coreWidth, coreLength, haloXZ);
        int cells = Math.multiplyExact(bounds.width(), bounds.length());
        long retained = estimateWindowRetainedBytes(bounds.width(), bounds.length());
        if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
            throw failure("v2.coastal-transition-budget", "transition window exceeds retained-memory budget");
        }
        int[][] fields = new int[CompositionField.values().length][cells];
        for (int localZ = 0; localZ < bounds.length(); localZ++) {
            token.throwIfCancellationRequested();
            for (int localX = 0; localX < bounds.width(); localX++) {
                int globalX = bounds.originX() + localX;
                int globalZ = bounds.originZ() + localZ;
                CompositionSample sample = sampleAt(globalX, globalZ, hardSource);
                int index = localZ * bounds.width() + localX;
                for (CompositionField field : CompositionField.values()) {
                    fields[field.ordinal()][index] = sample.rawValue(field);
                }
            }
        }
        return new CoastalTransitionWindowV2(bounds, fields, retained);
    }

    public Map<CompositionField, String> wholeFieldChecksums(HardLandWaterSourceV2 hardSource) {
        Objects.requireNonNull(hardSource, "hardSource");
        EnumMap<CompositionField, MessageDigest> digests = new EnumMap<>(CompositionField.class);
        for (CompositionField field : CompositionField.values()) digests.put(field, sha256());
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                CompositionSample sample = sampleAt(x, z, hardSource);
                for (CompositionField field : CompositionField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<CompositionField, String> result = new EnumMap<>(CompositionField.class);
        digests.forEach((field, digest) -> result.put(field, HexFormat.of().formatHex(digest.digest())));
        return Map.copyOf(result);
    }

    public static long estimateWindowRetainedBytes(int width, int length) {
        if (width < 1 || length < 1) throw new IllegalArgumentException("window dimensions must be positive");
        return Math.addExact(256L, Math.multiplyExact(
                Math.multiplyExact((long) width, length), (long) CompositionField.values().length * Integer.BYTES));
    }

    private List<SampledLayer> activeAt(int x, int z) {
        List<SampledLayer> active = new ArrayList<>(layers.size());
        for (LayerBinding binding : layers) {
            LayerSample sample = Objects.requireNonNull(binding.source().sampleAt(x, z), "layer sample");
            if (sample.active()) active.add(new SampledLayer(binding, sample));
        }
        return active;
    }

    private void validatePairContracts(List<SampledLayer> active, int x, int z) {
        for (int first = 0; first < active.size(); first++) {
            for (int second = first + 1; second < active.size(); second++) {
                String firstId = active.get(first).binding().contributor().featureId();
                String secondId = active.get(second).binding().contributor().featureId();
                if (!interactionsByPair.containsKey(pair(firstId, secondId))) {
                    throw failure("v2.coastal-transition-uncontracted-overlap",
                            "overlapping coastal features have no merge contract at " + x + ',' + z
                                    + ": " + firstId + ", " + secondId);
                }
            }
        }
    }

    private static void validateHardLayers(List<SampledLayer> active, int x, int z) {
        Integer hardValue = null;
        for (SampledLayer layer : active) {
            if (!layer.sample().hardConstrained()) continue;
            if (hardValue != null && hardValue != layer.sample().landWater()) {
                throw failure("v2.coastal-transition-hard-conflict",
                        "coastal HARD contributors conflict at " + x + ',' + z);
            }
            hardValue = layer.sample().landWater();
        }
    }

    private List<SampledLayer> removeExplicitlyCoveredWater(List<SampledLayer> active) {
        List<SampledLayer> result = new ArrayList<>(active);
        for (CoastalTransitionPlanV2.Interaction interaction : plan.interactions()) {
            if (interaction.profile() != CoastalTransitionPlanV2.InteractionProfile.STRUCTURE_OVER_WATER) continue;
            SampledLayer first = find(result, interaction.firstFeatureId());
            SampledLayer second = find(result, interaction.secondFeatureId());
            if (first == null || second == null) continue;
            SampledLayer basin = first.binding().contributor().kind() == TerrainIntentV2.FeatureKind.HARBOR_BASIN
                    ? first : second;
            result.remove(basin);
        }
        return List.copyOf(result);
    }

    private static SampledLayer find(List<SampledLayer> layers, String featureId) {
        return layers.stream().filter(layer -> layer.binding().contributor().featureId().equals(featureId))
                .findFirst().orElse(null);
    }

    private static Integer hardLayerClassification(List<SampledLayer> active) {
        return active.stream().filter(layer -> layer.sample().hardConstrained())
                .map(layer -> layer.sample().landWater()).findFirst().orElse(null);
    }

    private long weight(SampledLayer layer, List<SampledLayer> active) {
        int maximumBand = 0;
        for (SampledLayer other : active) {
            if (other == layer) continue;
            CoastalTransitionPlanV2.Interaction interaction = interactionsByPair.get(pair(
                    layer.binding().contributor().featureId(), other.binding().contributor().featureId()));
            maximumBand = Math.max(maximumBand, interaction.bandBlocks());
        }
        long edgeWeight = maximumBand == 0 ? FIXED_SCALE
                : Math.max(1L, Math.min(FIXED_SCALE,
                Math.floorDiv(layer.sample().boundaryDistanceMillionths(), maximumBand)));
        return Math.multiplyExact((long) layer.binding().contributor().priority() + 101L, edgeWeight);
    }

    private CoastalTransitionWindowV2.Bounds windowBounds(
            int coreOriginX, int coreOriginZ, int coreWidth, int coreLength, int haloXZ
    ) {
        if (coreOriginX < 0 || coreOriginZ < 0 || coreWidth < 1 || coreLength < 1
                || coreWidth > MAXIMUM_CORE_EXTENT || coreLength > MAXIMUM_CORE_EXTENT
                || (long) coreOriginX + coreWidth > width || (long) coreOriginZ + coreLength > length
                || haloXZ < 0 || haloXZ > MAXIMUM_HALO_XZ || haloXZ > plan.supportRadiusXZ()) {
            throw failure("v2.coastal-transition-window", "invalid transition core or halo");
        }
        int originX = Math.max(0, coreOriginX - haloXZ);
        int originZ = Math.max(0, coreOriginZ - haloXZ);
        int maximumX = Math.min(width, coreOriginX + coreWidth + haloXZ);
        int maximumZ = Math.min(length, coreOriginZ + coreLength + haloXZ);
        return new CoastalTransitionWindowV2.Bounds(
                coreOriginX, coreOriginZ, coreWidth, coreLength,
                originX, originZ, maximumX - originX, maximumZ - originZ, haloXZ);
    }

    private void requireCoordinate(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw failure("v2.coastal-transition-coordinate", "coordinate outside transition field");
        }
    }

    private static String pair(String first, String second) {
        return first.compareTo(second) < 0 ? first + '\n' + second : second + '\n' + first;
    }

    private static int roundDivide(long numerator, long denominator) {
        if (numerator >= 0) return Math.toIntExact(Math.floorDiv(Math.addExact(numerator, denominator / 2), denominator));
        return Math.toIntExact(-Math.floorDiv(Math.addExact(-numerator, denominator / 2), denominator));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static CoastalTransitionException failure(String ruleId, String message) {
        return new CoastalTransitionException(ruleId, message);
    }

    public enum CompositionField { LAND_WATER, SURFACE_HEIGHT, OWNER_INDEX, BLEND_WEIGHT, CONFLICT }

    public record LayerBinding(CoastalTransitionPlanV2.Contributor contributor, LayerSource source) {
        public LayerBinding {
            Objects.requireNonNull(contributor, "contributor");
            Objects.requireNonNull(source, "source");
        }
    }

    @FunctionalInterface
    public interface LayerSource { LayerSample sampleAt(int globalX, int globalZ); }

    public record LayerSample(
            boolean active,
            int landWater,
            int surfaceHeightMillionths,
            int boundaryDistanceMillionths,
            boolean hardConstrained
    ) {
        public static final LayerSample OUTSIDE = new LayerSample(false, 0, NO_DATA, 0, false);

        public LayerSample {
            if (active && (landWater < 0 || landWater > 1 || surfaceHeightMillionths == NO_DATA
                    || boundaryDistanceMillionths < 0)) {
                throw new IllegalArgumentException("invalid active coastal layer sample");
            }
            if (!active && (surfaceHeightMillionths != NO_DATA || hardConstrained)) {
                throw new IllegalArgumentException("inactive coastal layer must use canonical outside values");
            }
        }
    }

    public record CompositionSample(
            boolean active,
            int landWater,
            int surfaceHeightMillionths,
            int ownerIndex,
            int blendWeightMillionths,
            int conflict,
            boolean hardProtected
    ) {
        private static final CompositionSample OUTSIDE = new CompositionSample(false, 0, NO_DATA, 0, 0, 0, false);

        public int rawValue(CompositionField field) {
            return switch (Objects.requireNonNull(field, "field")) {
                case LAND_WATER -> landWater;
                case SURFACE_HEIGHT -> surfaceHeightMillionths;
                case OWNER_INDEX -> ownerIndex;
                case BLEND_WEIGHT -> blendWeightMillionths;
                case CONFLICT -> conflict;
            };
        }
    }

    public record ConflictDiagnostic(
            String ruleId,
            int globalX,
            int globalZ,
            List<String> featureIds,
            String message
    ) {
        public ConflictDiagnostic {
            Objects.requireNonNull(ruleId, "ruleId");
            featureIds = List.copyOf(featureIds);
            Objects.requireNonNull(message, "message");
        }
    }

    private record SampledLayer(LayerBinding binding, LayerSample sample) { }
    private record WeightedLayer(SampledLayer layer, long weight) { }
}
