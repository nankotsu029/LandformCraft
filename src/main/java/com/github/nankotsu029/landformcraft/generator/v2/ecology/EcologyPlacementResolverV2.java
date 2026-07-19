package com.github.nankotsu029.landformcraft.generator.v2.ecology;

import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure sparse ecology placement resolver. Habitat U16 codes and assemblage placements are
 * derived on demand from cell inputs — no dense width×length object grid is allocated.
 */
public final class EcologyPlacementResolverV2 {
    private final EcologyPlanV2 plan;

    public EcologyPlacementResolverV2(
            ClimatePlanV2 climatePlan,
            WaterConditionPlanV2 waterConditionPlan,
            SnowPlanV2 snowPlan,
            EcologyPlanV2 ecologyPlan
    ) {
        Objects.requireNonNull(ecologyPlan, "ecologyPlan");
        ecologyPlan.requireClimatePlan(climatePlan);
        ecologyPlan.requireWaterConditionPlan(waterConditionPlan);
        ecologyPlan.requireSnowPlan(snowPlan);
        this.plan = ecologyPlan;
    }

    public EcologyPlanV2 plan() {
        return plan;
    }

    public int habitatCodeAt(int globalX, int globalZ, CellInputs inputs) {
        return habitatAt(globalX, globalZ, inputs).compactCode();
    }

    public EcologyPlanV2.HabitatClass habitatAt(int globalX, int globalZ, CellInputs inputs) {
        requireInBounds(globalX, globalZ);
        CellInputs validated = requireInputs(inputs);
        if (mangroveHabitatEligible(validated)) {
            return EcologyPlanV2.HabitatClass.MANGROVE_WETLAND;
        }
        if (coralHabitatEligible(validated)) {
            return EcologyPlanV2.HabitatClass.CORAL_REEF;
        }
        if (alpineHabitatEligible(validated)) {
            return EcologyPlanV2.HabitatClass.ALPINE_VEGETATION;
        }
        return EcologyPlanV2.HabitatClass.NONE;
    }

    public Optional<PlacementDecision> placementAt(
            EcologyPlanV2.AssemblageKind kind,
            int globalX,
            int globalZ,
            CellInputs inputs
    ) {
        Objects.requireNonNull(kind, "kind");
        requireInBounds(globalX, globalZ);
        CellInputs validated = requireInputs(inputs);
        if (!plan.activeAssemblages().contains(kind)) {
            return Optional.empty();
        }
        EcologyPlanV2.Entry entry = plan.catalog().requireByKind(kind);
        EcologyPlanV2.HabitatClass habitat = habitatAt(globalX, globalZ, validated);
        if (habitat != entry.habitatClass()) {
            return Optional.empty();
        }
        if (!supportPasses(entry, validated)) {
            return Optional.empty();
        }
        if (!isSpacingWinner(entry, globalX, globalZ)) {
            return Optional.empty();
        }
        if (!densityPasses(entry, globalX, globalZ)) {
            return Optional.empty();
        }
        long featureSeed = featureSeed(entry.assemblageId(), globalX, globalZ);
        return Optional.of(new PlacementDecision(
                entry.kind(),
                entry.assemblageId(),
                entry.compactCode(),
                habitat,
                entry.layer(),
                entry.supportRule(),
                globalX,
                globalZ,
                featureSeed));
    }

    public int[] sampleHabitatWindow(
            int startX,
            int startZ,
            int width,
            int length,
            CellInputSource inputSource
    ) {
        validateWindow(startX, startZ, width, length);
        Objects.requireNonNull(inputSource, "inputSource");
        int[] result = new int[Math.multiplyExact(width, length)];
        for (int localZ = 0; localZ < length; localZ++) {
            for (int localX = 0; localX < width; localX++) {
                int globalX = startX + localX;
                int globalZ = startZ + localZ;
                result[localZ * width + localX] = habitatCodeAt(globalX, globalZ, inputSource.at(globalX, globalZ));
            }
        }
        return result;
    }

    public List<PlacementDecision> collectPlacementsInWindow(
            int startX,
            int startZ,
            int width,
            int length,
            CellInputSource inputSource
    ) {
        validateWindow(startX, startZ, width, length);
        Objects.requireNonNull(inputSource, "inputSource");
        List<PlacementDecision> placements = new ArrayList<>();
        for (EcologyPlanV2.AssemblageKind kind : plan.activeAssemblages()) {
            for (int localZ = 0; localZ < length; localZ++) {
                for (int localX = 0; localX < width; localX++) {
                    int globalX = startX + localX;
                    int globalZ = startZ + localZ;
                    placementAt(kind, globalX, globalZ, inputSource.at(globalX, globalZ))
                            .ifPresent(placements::add);
                    if (placements.size() > plan.budget().maximumPlacementDescriptorsPerWindow()) {
                        throw new IllegalArgumentException(
                                "ecology placement descriptors exceed window budget");
                    }
                }
            }
        }
        return List.copyOf(placements);
    }

    public String habitatChecksum(int width, int length, CellInputSource inputSource) {
        Objects.requireNonNull(inputSource, "inputSource");
        if (width != plan.width() || length != plan.length()) {
            throw new IllegalArgumentException("ecology habitat checksum requires full plan dimensions");
        }
        MessageDigest digest = sha256();
        digest.update("LFC_ECOLOGY_HABITAT_FIELD_V1\n".getBytes(StandardCharsets.UTF_8));
        ByteBuffer cell = ByteBuffer.allocate(Integer.BYTES);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                cell.clear();
                cell.putInt(habitatCodeAt(x, z, inputSource.at(x, z)));
                digest.update(cell.array());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public String placementChecksum(int width, int length, CellInputSource inputSource) {
        Objects.requireNonNull(inputSource, "inputSource");
        if (width != plan.width() || length != plan.length()) {
            throw new IllegalArgumentException("ecology placement checksum requires full plan dimensions");
        }
        MessageDigest digest = sha256();
        digest.update("LFC_ECOLOGY_PLACEMENT_V1\n".getBytes(StandardCharsets.UTF_8));
        ByteBuffer cell = ByteBuffer.allocate(Integer.BYTES * 3);
        for (EcologyPlanV2.AssemblageKind kind : plan.activeAssemblages()) {
            digest.update(kind.assemblageId().getBytes(StandardCharsets.UTF_8));
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    Optional<PlacementDecision> decision = placementAt(kind, x, z, inputSource.at(x, z));
                    cell.clear();
                    cell.putInt(decision.map(PlacementDecision::compactCode).orElse(0));
                    cell.putInt(x);
                    cell.putInt(z);
                    digest.update(cell.array());
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private boolean mangroveHabitatEligible(CellInputs inputs) {
        EcologyPlanV2.Kernel kernel = plan.kernel();
        return inputs.wetlandMask() == 1
                && inputs.temperatureRaw() >= kernel.mangroveMinTemperatureRaw()
                && inputs.salinityRaw() >= kernel.mangroveMinSalinityRaw()
                && inputs.salinityRaw() <= kernel.mangroveMaxSalinityRaw();
    }

    private boolean coralHabitatEligible(CellInputs inputs) {
        EcologyPlanV2.Kernel kernel = plan.kernel();
        return inputs.reefMask() == 1
                && inputs.temperatureRaw() >= kernel.coralMinTemperatureRaw();
    }

    private boolean alpineHabitatEligible(CellInputs inputs) {
        EcologyPlanV2.Kernel kernel = plan.kernel();
        return inputs.surfaceY() >= kernel.alpineMinSurfaceY()
                && inputs.surfaceY() <= kernel.alpineMaxSurfaceY()
                && inputs.temperatureRaw() <= kernel.alpineMaxTemperatureRaw();
    }

    private boolean supportPasses(EcologyPlanV2.Entry entry, CellInputs inputs) {
        EcologyPlanV2.Kernel kernel = plan.kernel();
        return switch (entry.supportRule()) {
            case WETLAND_CANOPY -> inputs.openWaterGap() == 0
                    && inputs.wetnessRaw() >= kernel.mangroveMinWetnessRaw();
            case WETLAND_ROOT -> inputs.openWaterGap() == 0
                    && inputs.substrateWet() == 1
                    && inputs.wetnessRaw() >= kernel.mangroveMinWetnessRaw()
                    && inputs.hydroperiodRaw() >= kernel.mangroveMinHydroperiodRaw();
            case REEF_COLONY -> {
                int depth = reefDepthRaw(inputs);
                yield inputs.salinityRaw() >= kernel.coralMinSalinityRaw()
                        && depth >= kernel.coralMinDepthRaw()
                        && depth <= kernel.coralMaxDepthRaw()
                        && inputs.temperatureRaw() >= kernel.coralMinTemperatureRaw();
            }
            case ALPINE_SHRUB -> inputs.snowCoverRaw() <= kernel.alpineMaxSnowCoverRaw()
                    && inputs.surfaceY() >= kernel.alpineMinSurfaceY()
                    && inputs.surfaceY() <= kernel.alpineShrubMaxSurfaceY();
            case ALPINE_MEADOW -> inputs.snowCoverRaw() <= kernel.alpineMaxSnowCoverRaw()
                    && inputs.surfaceY() > kernel.alpineShrubMaxSurfaceY()
                    && inputs.surfaceY() <= kernel.alpineMaxSurfaceY();
        };
    }

    private static int reefDepthRaw(CellInputs inputs) {
        if (inputs.lagoonDepthRaw() > 0) {
            return inputs.lagoonDepthRaw();
        }
        return inputs.crestDepthRaw();
    }

    private boolean isSpacingWinner(EcologyPlanV2.Entry entry, int x, int z) {
        int spacing = entry.minSpacingBlocks();
        int gx = Math.floorDiv(x, spacing);
        int gz = Math.floorDiv(z, spacing);
        long gridHash = cellHash(plan.seedNamespace() + "|" + entry.assemblageId() + "|grid", gx, gz);
        int offsetX = (int) Long.remainderUnsigned(gridHash, spacing);
        int offsetZ = (int) Long.remainderUnsigned(mix64(gridHash ^ plan.namedSeed()), spacing);
        int representativeX = Math.addExact(Math.multiplyExact(gx, spacing), offsetX);
        int representativeZ = Math.addExact(Math.multiplyExact(gz, spacing), offsetZ);
        return x == representativeX && z == representativeZ;
    }

    private boolean densityPasses(EcologyPlanV2.Entry entry, int x, int z) {
        long hash = cellHash(plan.seedNamespace() + "|" + entry.assemblageId() + "|density", x, z);
        return hash < entry.densityMillionths();
    }

    private long featureSeed(String assemblageId, int x, int z) {
        return mix64(mix64(plan.namedSeed() ^ assemblageId.hashCode()) ^ (((long) x << 32) ^ (z & 0xffff_ffffL)));
    }

    private void requireInBounds(int globalX, int globalZ) {
        if (globalX < 0 || globalZ < 0 || globalX >= plan.width() || globalZ >= plan.length()) {
            throw new IllegalArgumentException("ecology sample is outside plan bounds");
        }
    }

    private void validateWindow(int startX, int startZ, int width, int length) {
        if (width < 1 || length < 1
                || width > plan.budget().maximumWindowSize()
                || length > plan.budget().maximumWindowSize()
                || startX < 0 || startZ < 0
                || startX + width > plan.width()
                || startZ + length > plan.length()) {
            throw new IllegalArgumentException("ecology window is outside declared bounds");
        }
        long requiredBytes = Math.multiplyExact(Math.multiplyExact((long) width, length), Integer.BYTES);
        if (requiredBytes > plan.budget().maximumWorkingBytes()) {
            throw new IllegalArgumentException("ecology window exceeds working-memory budget");
        }
    }

    private static CellInputs requireInputs(CellInputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.temperatureRaw() < 0 || inputs.temperatureRaw() > 1_000
                || inputs.wetnessRaw() < 0 || inputs.wetnessRaw() > 1_000
                || inputs.salinityRaw() < 0 || inputs.salinityRaw() > 1_000
                || inputs.hydroperiodRaw() < 0 || inputs.hydroperiodRaw() > 1_000
                || inputs.snowCoverRaw() < 0 || inputs.snowCoverRaw() > 1_000
                || inputs.wetlandMask() < 0 || inputs.wetlandMask() > 1
                || inputs.openWaterGap() < 0 || inputs.openWaterGap() > 1
                || inputs.substrateWet() < 0 || inputs.substrateWet() > 1
                || inputs.reefMask() < 0 || inputs.reefMask() > 1
                || inputs.crestDepthRaw() < 0 || inputs.crestDepthRaw() > 1_000
                || inputs.lagoonDepthRaw() < 0 || inputs.lagoonDepthRaw() > 1_000) {
            throw new IllegalArgumentException("ecology cell inputs are out of range");
        }
        return inputs;
    }

    static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    static long cellHash(String seedNamespace, int x, int z) {
        long mixed = mix64(mix64(seedNamespace.hashCode() ^ x) ^ z);
        return Long.remainderUnsigned(mixed, 1_000_000L);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record CellInputs(
            int temperatureRaw,
            int wetnessRaw,
            int salinityRaw,
            int hydroperiodRaw,
            int snowCoverRaw,
            int wetlandMask,
            int openWaterGap,
            int substrateWet,
            int reefMask,
            int crestDepthRaw,
            int lagoonDepthRaw,
            int surfaceY
    ) {
    }

    @FunctionalInterface
    public interface CellInputSource {
        CellInputs at(int globalX, int globalZ);
    }

    public record PlacementDecision(
            EcologyPlanV2.AssemblageKind kind,
            String assemblageId,
            int compactCode,
            EcologyPlanV2.HabitatClass habitat,
            EcologyPlanV2.PlacementLayer layer,
            EcologyPlanV2.SupportRule supportRule,
            int globalX,
            int globalZ,
            long featureSeed
    ) {
    }
}
