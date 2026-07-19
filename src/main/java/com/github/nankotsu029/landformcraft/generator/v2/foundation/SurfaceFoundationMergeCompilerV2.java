package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationFailureCodeV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;

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
import java.util.function.Predicate;

/**
 * Integer-only merge of synthetic surface foundation owners. Rejects ownerless cells, ties,
 * undeclared overlaps, and out-of-range transition bands. Whole and tiled scans must agree.
 */
public final class SurfaceFoundationMergeCompilerV2 {
    public static final int FIXED_SCALE = 1_000_000;
    public static final int NO_DATA = 0;

    private final SurfaceFoundationPlanV2 plan;
    private final int width;
    private final int length;
    private final List<OwnerLayer> layers;
    private final Map<String, SurfaceFoundationPlanV2.Interaction> interactionsByPair;

    public SurfaceFoundationMergeCompilerV2(
            SurfaceFoundationPlanV2 plan,
            List<OwnerLayer> layers
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.width = plan.width();
        this.length = plan.length();
        this.layers = List.copyOf(Objects.requireNonNull(layers, "layers")).stream()
                .sorted(Comparator.comparing(layer -> layer.owner().ownerId()))
                .toList();
        if (this.layers.size() != plan.owners().size()) {
            throw failure(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    "owner layers must match plan owners exactly");
        }
        for (int i = 0; i < this.layers.size(); i++) {
            if (!this.layers.get(i).owner().equals(plan.owners().get(i))) {
                throw failure(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                        "owner layer order/identity must match plan owners");
            }
        }
        Map<String, SurfaceFoundationPlanV2.Interaction> interactions = new HashMap<>();
        for (SurfaceFoundationPlanV2.Interaction interaction : plan.interactions()) {
            interactions.put(pair(interaction.firstOwnerId(), interaction.secondOwnerId()), interaction);
        }
        this.interactionsByPair = Map.copyOf(interactions);
        if (plan.supportRadiusXZ() < 0 || plan.supportRadiusXZ() > 32) {
            throw failure(SurfaceFoundationFailureCodeV2.TRANSITION_OUT_OF_RANGE,
                    "supportRadiusXZ outside 0..32");
        }
    }

    public MergeSample sampleAt(int globalX, int globalZ) {
        requireCoordinate(globalX, globalZ);
        List<ActiveOwner> active = activeAt(globalX, globalZ);
        if (active.isEmpty()) {
            throw failure(SurfaceFoundationFailureCodeV2.OWNERLESS_CELL,
                    "ownerless foundation cell at " + globalX + ',' + globalZ);
        }
        validatePairContracts(active, globalX, globalZ);
        if (active.size() == 1) {
            ActiveOwner only = active.getFirst();
            return new MergeSample(
                    only.owner().surfaceClass().code(),
                    only.elevationMillionths(),
                    0,
                    only.owner().ownerIndex(),
                    FIXED_SCALE);
        }

        long totalWeight = 0L;
        long weightedElevation = 0L;
        ActiveOwner best = null;
        long bestWeight = -1L;
        for (ActiveOwner candidate : active) {
            long weight = weight(candidate, active);
            totalWeight = Math.addExact(totalWeight, weight);
            weightedElevation = Math.addExact(weightedElevation,
                    Math.multiplyExact(weight, candidate.elevationMillionths()));
            if (weight > bestWeight
                    || (weight == bestWeight
                    && (best == null
                    || candidate.owner().ownerId().compareTo(best.owner().ownerId()) < 0))) {
                bestWeight = weight;
                best = candidate;
            }
        }
        if (best == null || totalWeight == 0L) {
            throw failure(SurfaceFoundationFailureCodeV2.OWNER_TIE,
                    "foundation merge produced no owner at " + globalX + ',' + globalZ);
        }
        final long winningWeight = bestWeight;
        long tied = active.stream().filter(candidate -> weight(candidate, active) == winningWeight).count();
        if (tied > 1L) {
            throw failure(SurfaceFoundationFailureCodeV2.OWNER_TIE,
                    "equal-priority foundation owners at " + globalX + ',' + globalZ);
        }
        int residual = Math.toIntExact(Math.abs(
                Math.subtractExact(best.elevationMillionths(),
                        roundDivide(weightedElevation, totalWeight))));
        int transitionWeight = Math.toIntExact(
                Math.floorDiv(Math.multiplyExact(bestWeight, FIXED_SCALE), totalWeight));
        return new MergeSample(
                best.owner().surfaceClass().code(),
                roundDivide(weightedElevation, totalWeight),
                residual,
                best.owner().ownerIndex(),
                transitionWeight);
    }

    public Map<CompositionField, String> wholeFieldChecksums() {
        EnumMap<CompositionField, MessageDigest> digests = new EnumMap<>(CompositionField.class);
        for (CompositionField field : CompositionField.values()) {
            digests.put(field, sha256());
        }
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                MergeSample sample = sampleAt(x, z);
                for (CompositionField field : CompositionField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<CompositionField, String> result = new EnumMap<>(CompositionField.class);
        digests.forEach((field, digest) -> result.put(field, HexFormat.of().formatHex(digest.digest())));
        return Map.copyOf(result);
    }

    public Map<CompositionField, String> tiledFieldChecksums(TilePlanV2 tilePlan) {
        Objects.requireNonNull(tilePlan, "tilePlan");
        if (tilePlan.widthBlocks() != width || tilePlan.lengthBlocks() != length) {
            throw failure(SurfaceFoundationFailureCodeV2.DIMENSIONS_INVALID,
                    "tile plan dimensions must match foundation plan");
        }
        int cells = Math.multiplyExact(width, length);
        int[][] values = new int[CompositionField.values().length][cells];
        boolean[] covered = new boolean[cells];
        for (int tileIndex = 0; tileIndex < tilePlan.tileCount(); tileIndex++) {
            var tile = tilePlan.tileByIndex(tileIndex);
            for (int localZ = 0; localZ < tile.coreLength(); localZ++) {
                for (int localX = 0; localX < tile.coreWidth(); localX++) {
                    int globalX = tile.coreMinX() + localX;
                    int globalZ = tile.coreMinZ() + localZ;
                    requireCoordinate(globalX, globalZ);
                    int index = globalZ * width + globalX;
                    if (covered[index]) {
                        throw failure(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                                "overlapping tile cores at " + globalX + ',' + globalZ);
                    }
                    covered[index] = true;
                    MergeSample sample = sampleAt(globalX, globalZ);
                    for (CompositionField field : CompositionField.values()) {
                        values[field.ordinal()][index] = sample.rawValue(field);
                    }
                }
            }
        }
        for (boolean cell : covered) {
            if (!cell) {
                throw failure(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                        "tile plan does not cover the foundation area");
            }
        }
        EnumMap<CompositionField, MessageDigest> digests = new EnumMap<>(CompositionField.class);
        for (CompositionField field : CompositionField.values()) {
            digests.put(field, sha256());
        }
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                for (CompositionField field : CompositionField.values()) {
                    updateInt(digests.get(field), values[field.ordinal()][index]);
                }
            }
        }
        EnumMap<CompositionField, String> result = new EnumMap<>(CompositionField.class);
        digests.forEach((field, digest) -> result.put(field, HexFormat.of().formatHex(digest.digest())));
        return Map.copyOf(result);
    }

    private List<ActiveOwner> activeAt(int x, int z) {
        List<ActiveOwner> active = new ArrayList<>(layers.size());
        for (OwnerLayer layer : layers) {
            if (layer.activeAt().test(pack(x, z))) {
                active.add(new ActiveOwner(layer.owner(), layer.elevationMillionthsAt(x, z)));
            }
        }
        return active;
    }

    private void validatePairContracts(List<ActiveOwner> active, int x, int z) {
        for (int first = 0; first < active.size(); first++) {
            for (int second = first + 1; second < active.size(); second++) {
                String firstId = active.get(first).owner().ownerId();
                String secondId = active.get(second).owner().ownerId();
                if (!interactionsByPair.containsKey(pair(firstId, secondId))) {
                    throw failure(SurfaceFoundationFailureCodeV2.UNDECLARED_OVERLAP,
                            "overlapping foundation owners have no merge contract at "
                                    + x + ',' + z + ": " + firstId + ", " + secondId);
                }
            }
        }
    }

    private long weight(ActiveOwner candidate, List<ActiveOwner> active) {
        long base = candidate.owner().priority() + 101L;
        int overlaps = 0;
        for (ActiveOwner other : active) {
            if (other.owner().ownerId().equals(candidate.owner().ownerId())) {
                continue;
            }
            SurfaceFoundationPlanV2.Interaction interaction =
                    interactionsByPair.get(pair(candidate.owner().ownerId(), other.owner().ownerId()));
            if (interaction != null) {
                overlaps++;
                base = Math.addExact(base, interaction.bandBlocks() + 1L);
            }
        }
        return Math.multiplyExact(base, overlaps == 0 ? 1L : (long) overlaps);
    }

    private void requireCoordinate(int x, int z) {
        if (x < 0 || z < 0 || x >= width || z >= length) {
            throw failure(SurfaceFoundationFailureCodeV2.DIMENSIONS_INVALID,
                    "coordinate outside foundation bounds");
        }
    }

    private static String pair(String first, String second) {
        return first.compareTo(second) < 0 ? first + '\n' + second : second + '\n' + first;
    }

    private static long pack(int x, int z) {
        return (((long) z) << 32) | (x & 0xffff_ffffL);
    }

    private static int roundDivide(long numerator, long denominator) {
        if (denominator == 0L) {
            throw failure(SurfaceFoundationFailureCodeV2.OWNER_TIE, "zero merge weight");
        }
        long half = denominator / 2L;
        if (numerator >= 0L) {
            return Math.toIntExact(Math.floorDiv(numerator + half, denominator));
        }
        return Math.toIntExact(Math.floorDiv(numerator - half, denominator));
    }

    private static SurfaceFoundationExceptionV2 failure(
            SurfaceFoundationFailureCodeV2 code,
            String message
    ) {
        return new SurfaceFoundationExceptionV2(code, message);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    public enum CompositionField {
        SURFACE_CLASS,
        ELEVATION,
        RESIDUAL,
        OWNER_INDEX,
        TRANSITION_WEIGHT
    }

    public record MergeSample(
            int surfaceClassCode,
            int elevationMillionths,
            int residualMillionths,
            int ownerIndex,
            int transitionWeightMillionths
    ) {
        public int rawValue(CompositionField field) {
            return switch (field) {
                case SURFACE_CLASS -> surfaceClassCode;
                case ELEVATION -> elevationMillionths;
                case RESIDUAL -> residualMillionths;
                case OWNER_INDEX -> ownerIndex;
                case TRANSITION_WEIGHT -> transitionWeightMillionths;
            };
        }
    }

    public record OwnerLayer(
            SurfaceFoundationPlanV2.OwnerDescriptor owner,
            Predicate<Long> activeAt,
            ElevationSource elevation
    ) {
        public OwnerLayer {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(activeAt, "activeAt");
            Objects.requireNonNull(elevation, "elevation");
        }

        public int elevationMillionthsAt(int x, int z) {
            return elevation.elevationMillionthsAt(x, z);
        }
    }

    @FunctionalInterface
    public interface ElevationSource {
        int elevationMillionthsAt(int globalX, int globalZ);
    }

    private record ActiveOwner(
            SurfaceFoundationPlanV2.OwnerDescriptor owner,
            int elevationMillionths
    ) {
    }
}
