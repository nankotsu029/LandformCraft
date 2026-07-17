package com.github.nankotsu029.landformcraft.generator.v2.environment.water;

import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Integer-only global-X/Z sampler for V2-4-05 regional water-condition fields.
 * Distances must be pre-bounded; the sampler never invents ocean or runs diffusion.
 */
public final class WaterConditionFieldSamplerV2 {
    private final WaterConditionPlanV2 plan;

    public WaterConditionFieldSamplerV2(WaterConditionPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public int rawValueAt(
            WaterConditionPlanV2.FieldSemantic semantic,
            int globalX,
            int globalZ,
            CellInputs inputs
    ) {
        requireCoordinate(globalX, globalZ);
        CellInputs validated = requireInputs(inputs);
        return switch (semantic) {
            case WATER_DISTANCE -> waterDistanceProximity(validated);
            case GROUNDWATER_PROXY -> groundwaterProxy(validated);
            case TIDAL_INFLUENCE -> tidalInfluence(validated);
            case SALINITY -> salinity(validated);
            case HYDROPERIOD -> hydroperiod(validated);
            case WETNESS -> wetness(validated);
            case WETNESS_RESIDUAL -> wetnessResidual(validated);
        };
    }

    public int[] sampleWindow(
            WaterConditionPlanV2.FieldSemantic semantic,
            int startX,
            int startZ,
            int width,
            int length,
            CellInputSource inputSource
    ) {
        if (width < 1 || length < 1
                || width > plan.budget().maximumWindowSize()
                || length > plan.budget().maximumWindowSize()
                || startX < 0 || startZ < 0
                || Math.addExact(startX, width) > plan.width()
                || Math.addExact(startZ, length) > plan.length()) {
            throw new IllegalArgumentException("water-condition window is outside declared bounds");
        }
        long requiredBytes = Math.multiplyExact(Math.multiplyExact((long) width, length), Integer.BYTES);
        if (requiredBytes > plan.budget().maximumWorkingBytes()) {
            throw new IllegalArgumentException("water-condition window exceeds working-memory budget");
        }
        Objects.requireNonNull(inputSource, "inputSource");
        int[] result = new int[Math.multiplyExact(width, length)];
        for (int localZ = 0; localZ < length; localZ++) {
            for (int localX = 0; localX < width; localX++) {
                int globalX = startX + localX;
                int globalZ = startZ + localZ;
                result[localZ * width + localX] = rawValueAt(
                        semantic, globalX, globalZ, inputSource.at(globalX, globalZ));
            }
        }
        return result;
    }

    public String checksum(WaterConditionPlanV2.FieldSemantic semantic, CellInputSource inputSource) {
        MessageDigest digest = sha256();
        digest.update("LFC_WATER_CONDITION_FIELD_V1\n".getBytes(StandardCharsets.UTF_8));
        digest.update(semantic.name().getBytes(StandardCharsets.UTF_8));
        ByteBuffer cell = ByteBuffer.allocate(Integer.BYTES);
        Objects.requireNonNull(inputSource, "inputSource");
        for (int z = 0; z < plan.length(); z++) {
            for (int x = 0; x < plan.width(); x++) {
                cell.clear();
                cell.putInt(rawValueAt(semantic, x, z, inputSource.at(x, z)));
                digest.update(cell.array());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private int waterDistanceProximity(CellInputs inputs) {
        int nearest = nearestWaterDistance(inputs);
        return proximityFromDistance(nearest);
    }

    private int groundwaterProxy(CellInputs inputs) {
        WaterConditionPlanV2.Kernel kernel = plan.kernel();
        int heightAbove = Math.max(0, Math.subtractExact(inputs.elevationY(), plan.referenceWaterY()));
        int depthDecay = Math.min(kernel.maximumRaw(),
                Math.multiplyExact(heightAbove, kernel.groundwaterDecayRawPerBlock()));
        int base = Math.max(kernel.minimumRaw(), Math.subtractExact(kernel.maximumRaw(), depthDecay));
        long withPermeability = roundedDivide(
                Math.multiplyExact((long) base, inputs.permeabilityRaw()), 1_000L);
        return clamp(Math.toIntExact(roundedDivide(
                Math.multiplyExact(withPermeability, waterDistanceProximity(inputs)), 1_000L)),
                kernel.minimumRaw(), kernel.maximumRaw());
    }

    private int tidalInfluence(CellInputs inputs) {
        if (!inputs.marineConnected()) {
            return plan.kernel().minimumRaw();
        }
        WaterConditionPlanV2.Kernel kernel = plan.kernel();
        int marineDistance = Math.min(inputs.tidalDistanceBlocks(), inputs.seaDistanceBlocks());
        long proximity = proximityFromDistance(marineDistance);
        long rangeScaled = roundedDivide(
                Math.multiplyExact(proximity, inputs.tidalRangeRaw()), 1_000L);
        long falloff = Math.subtractExact(1_000L, kernel.tidalFalloffRaw());
        return clamp(Math.toIntExact(roundedDivide(
                Math.multiplyExact(rangeScaled, falloff), 1_000L)),
                kernel.minimumRaw(), kernel.maximumRaw());
    }

    private int salinity(CellInputs inputs) {
        if (!inputs.marineConnected()) {
            return plan.kernel().minimumRaw();
        }
        WaterConditionPlanV2.Kernel kernel = plan.kernel();
        int marineDistance = Math.min(inputs.tidalDistanceBlocks(), inputs.seaDistanceBlocks());
        long proximity = proximityFromDistance(marineDistance);
        long marine = roundedDivide(
                Math.multiplyExact((long) kernel.salinityMarineBaseRaw(), proximity), 1_000L);
        long dilution = roundedDivide(
                Math.multiplyExact((long) kernel.freshwaterDilutionRaw(), inputs.freshwaterDischargeRaw()),
                1_000L);
        long kept = Math.max(0L, Math.subtractExact(1_000L, dilution));
        return clamp(Math.toIntExact(roundedDivide(Math.multiplyExact(marine, kept), 1_000L)),
                kernel.minimumRaw(), kernel.maximumRaw());
    }

    private int hydroperiod(CellInputs inputs) {
        WaterConditionPlanV2.Kernel kernel = plan.kernel();
        long proximity = waterDistanceProximity(inputs);
        long tidal = roundedDivide(
                Math.multiplyExact((long) tidalInfluence(inputs), kernel.hydroperiodTidalGainRaw()),
                1_000L);
        long submergedBoost = inputs.elevationY() <= plan.referenceWaterY() ? 200L : 0L;
        return clamp(Math.toIntExact(Math.min(1_000L,
                Math.addExact(Math.addExact(roundedDivide(proximity, 2L), tidal), submergedBoost))),
                kernel.minimumRaw(), kernel.maximumRaw());
    }

    private int wetness(CellInputs inputs) {
        WaterConditionPlanV2.Kernel kernel = plan.kernel();
        long moisture = roundedDivide(
                Math.multiplyExact((long) inputs.moistureRaw(), kernel.wetnessMoistureWeightRaw()), 1_000L);
        long groundwater = roundedDivide(
                Math.multiplyExact((long) groundwaterProxy(inputs), kernel.wetnessGroundwaterWeightRaw()),
                1_000L);
        long proximity = roundedDivide(
                Math.multiplyExact((long) waterDistanceProximity(inputs), kernel.wetnessProximityWeightRaw()),
                1_000L);
        return clamp(Math.toIntExact(Math.addExact(Math.addExact(moisture, groundwater), proximity)),
                kernel.minimumRaw(), kernel.maximumRaw());
    }

    private int wetnessResidual(CellInputs inputs) {
        return Math.subtractExact(wetness(inputs), inputs.moistureRaw());
    }

    private int nearestWaterDistance(CellInputs inputs) {
        return Math.min(Math.min(inputs.riverDistanceBlocks(), inputs.lakeDistanceBlocks()),
                Math.min(inputs.tidalDistanceBlocks(), inputs.seaDistanceBlocks()));
    }

    private int proximityFromDistance(int distanceBlocks) {
        WaterConditionPlanV2.Kernel kernel = plan.kernel();
        if (distanceBlocks < 0 || distanceBlocks > kernel.maximumDistanceBlocks()) {
            throw new IllegalArgumentException("water distance exceeds bounded support");
        }
        long remaining = Math.subtractExact((long) kernel.maximumDistanceBlocks(), distanceBlocks);
        return clamp(Math.toIntExact(roundedDivide(
                Math.multiplyExact(remaining, 1_000L), kernel.maximumDistanceBlocks())),
                kernel.minimumRaw(), kernel.maximumRaw());
    }

    private void requireCoordinate(int globalX, int globalZ) {
        if (globalX < 0 || globalX >= plan.width() || globalZ < 0 || globalZ >= plan.length()) {
            throw new IllegalArgumentException("water-condition coordinate outside release-local bounds");
        }
    }

    private CellInputs requireInputs(CellInputs inputs) {
        Objects.requireNonNull(inputs, "water-condition inputs");
        if (inputs.noData()) {
            throw new IllegalArgumentException("water-condition rejects no-data inputs");
        }
        WaterConditionPlanV2.Kernel kernel = plan.kernel();
        requireDistance(inputs.riverDistanceBlocks(), "riverDistanceBlocks");
        requireDistance(inputs.lakeDistanceBlocks(), "lakeDistanceBlocks");
        requireDistance(inputs.tidalDistanceBlocks(), "tidalDistanceBlocks");
        requireDistance(inputs.seaDistanceBlocks(), "seaDistanceBlocks");
        if (inputs.elevationY() < plan.minY() || inputs.elevationY() > plan.maxY()
                || inputs.moistureRaw() < kernel.minimumRaw() || inputs.moistureRaw() > kernel.maximumRaw()
                || inputs.tidalRangeRaw() < kernel.minimumRaw() || inputs.tidalRangeRaw() > kernel.maximumRaw()
                || inputs.freshwaterDischargeRaw() < kernel.minimumRaw()
                || inputs.freshwaterDischargeRaw() > kernel.maximumRaw()
                || inputs.permeabilityRaw() < kernel.minimumRaw()
                || inputs.permeabilityRaw() > kernel.maximumRaw()) {
            throw new IllegalArgumentException("water-condition inputs are outside declared hard ranges");
        }
        if (inputs.implicitOceanFallback()) {
            throw new IllegalArgumentException("water-condition rejects implicit ocean fallback");
        }
        if (inputs.unboundedDiffusion()) {
            throw new IllegalArgumentException("water-condition rejects unbounded diffusion");
        }
        return inputs;
    }

    private void requireDistance(int distanceBlocks, String name) {
        if (distanceBlocks < 0 || distanceBlocks > plan.kernel().maximumDistanceBlocks()) {
            throw new IllegalArgumentException(name + " exceeds bounded distance support");
        }
    }

    private static int roundedDivide(long numerator, long denominator) {
        if (denominator <= 0) throw new IllegalArgumentException("denominator must be positive");
        long absolute = Math.abs(numerator);
        long rounded = Math.floorDiv(Math.addExact(absolute, denominator / 2L), denominator);
        return Math.toIntExact(numerator < 0 ? -rounded : rounded);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    /**
     * Sample-time hydrology/climate inputs. Distances are already clamped to the plan support;
     * {@code marineConnected} must be explicit and never invented from missing sea labels.
     */
    public record CellInputs(
            int elevationY,
            int moistureRaw,
            int riverDistanceBlocks,
            int lakeDistanceBlocks,
            int tidalDistanceBlocks,
            int seaDistanceBlocks,
            boolean marineConnected,
            int tidalRangeRaw,
            int freshwaterDischargeRaw,
            int permeabilityRaw,
            boolean noData,
            boolean implicitOceanFallback,
            boolean unboundedDiffusion
    ) {
        public static CellInputs of(
                int elevationY,
                int moistureRaw,
                int riverDistanceBlocks,
                int lakeDistanceBlocks,
                int tidalDistanceBlocks,
                int seaDistanceBlocks,
                boolean marineConnected,
                int tidalRangeRaw,
                int freshwaterDischargeRaw,
                int permeabilityRaw
        ) {
            return new CellInputs(
                    elevationY, moistureRaw, riverDistanceBlocks, lakeDistanceBlocks,
                    tidalDistanceBlocks, seaDistanceBlocks, marineConnected, tidalRangeRaw,
                    freshwaterDischargeRaw, permeabilityRaw, false, false, false);
        }
    }

    @FunctionalInterface
    public interface CellInputSource {
        CellInputs at(int globalX, int globalZ);
    }
}
