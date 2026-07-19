package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-13 waterfall volume plan. Binds a hydrology fall geometry checksum to a falling
 * water column {@code ADD_FLUID}, behind-fall {@code CARVE_SOLID}, and plunge-pool fluid continuity.
 * Dynamic fluid simulation and Paper settle are out of scope.
 */
public record WaterfallVolumePlanV2(
        int planVersion,
        String waterfallVolumeContractVersion,
        String featureId,
        Kernel kernel,
        FallNodeBinding fallNode,
        VolumeSdfVec3V2 lipCenter,
        VolumeSdfVec3V2 baseCenter,
        long columnRadiusMillionths,
        BehindFallSpec behindFall,
        PlungePoolSpec plungePool,
        String fluidBodyId,
        VolumeSdfAabbV2 aabb,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String WATERFALL_VOLUME_CONTRACT_VERSION = "waterfall-volume-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 48L * 1024L;
    public static final long MAXIMUM_RADIUS_MILLIONTHS = 32_000_000L;
    public static final int MAXIMUM_DESCRIPTOR_SAMPLES = 64_000;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public WaterfallVolumePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("waterfall-volume planVersion must be 1");
        }
        waterfallVolumeContractVersion = nonBlank(
                waterfallVolumeContractVersion, "waterfallVolumeContractVersion", 64);
        if (!WATERFALL_VOLUME_CONTRACT_VERSION.equals(waterfallVolumeContractVersion)) {
            throw new IllegalArgumentException("unknown waterfall-volume contract version");
        }
        featureId = qualified(featureId, "featureId");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(fallNode, "fallNode");
        Objects.requireNonNull(lipCenter, "lipCenter");
        Objects.requireNonNull(baseCenter, "baseCenter");
        Objects.requireNonNull(behindFall, "behindFall");
        Objects.requireNonNull(plungePool, "plungePool");
        Objects.requireNonNull(aabb, "aabb");
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        Objects.requireNonNull(budget, "budget");
        fluidBodyId = qualified(fluidBodyId, "fluidBodyId");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (columnRadiusMillionths < 1_000_000L
                || columnRadiusMillionths > kernel.maximumRadiusMillionths()) {
            throw new IllegalArgumentException("waterfall-volume column radius out of range");
        }
        if (lipCenter.equals(baseCenter)) {
            throw new IllegalArgumentException("waterfall-volume lip and base must differ");
        }
        if (lipCenter.yMillionths() <= baseCenter.yMillionths()) {
            throw new IllegalArgumentException("waterfall-volume lip must be above base");
        }
        validateBudget(budget);
    }

    public WaterfallVolumePlanV2 withCanonicalChecksum(String checksum) {
        return new WaterfallVolumePlanV2(
                planVersion, waterfallVolumeContractVersion, featureId, kernel, fallNode,
                lipCenter, baseCenter, columnRadiusMillionths, behindFall, plungePool, fluidBodyId,
                aabb, sdfPlanBinding, csgPlanBinding, budget, checksum);
    }

    public record Kernel(
            String kernelVersion,
            int minimumColumnSamples,
            int minimumPoolSamples,
            int minimumBehindClearanceSamples,
            long maximumRadiusMillionths,
            int maximumDescriptorSamples
    ) {
        public static final String VERSION = "waterfall-volume-v1";

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown waterfall-volume kernel version");
            }
            if (minimumColumnSamples < 1 || minimumColumnSamples > 4096
                    || minimumPoolSamples < 1 || minimumPoolSamples > 4096
                    || minimumBehindClearanceSamples < 1 || minimumBehindClearanceSamples > 4096
                    || maximumRadiusMillionths < 1_000_000L
                    || maximumRadiusMillionths > MAXIMUM_RADIUS_MILLIONTHS
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES) {
                throw new IllegalArgumentException("waterfall-volume kernel budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(
                    VERSION,
                    4,
                    4,
                    4,
                    MAXIMUM_RADIUS_MILLIONTHS,
                    MAXIMUM_DESCRIPTOR_SAMPLES);
        }
    }

    public record FallNodeBinding(
            String relationKind,
            String fallNodeId,
            String waterfallFeatureId,
            String sourceGeometryChecksum
    ) {
        public static final String BOUND_TO_FALL = "BOUND_TO_FALL";

        public FallNodeBinding {
            relationKind = nonBlank(relationKind, "relationKind", 32);
            if (!BOUND_TO_FALL.equals(relationKind)) {
                throw new IllegalArgumentException("waterfall-volume relation must be BOUND_TO_FALL");
            }
            fallNodeId = qualified(fallNodeId, "fallNodeId");
            waterfallFeatureId = qualified(waterfallFeatureId, "waterfallFeatureId");
            sourceGeometryChecksum = checksum(sourceGeometryChecksum, "sourceGeometryChecksum");
        }
    }

    public record BehindFallSpec(
            VolumeSdfVec3V2 center,
            VolumeSdfVec3V2 halfExtentsMillionths,
            long cornerRadiusMillionths
    ) {
        public BehindFallSpec {
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(halfExtentsMillionths, "halfExtentsMillionths");
            requireHalf(halfExtentsMillionths, "behindFall");
            requireCorner(cornerRadiusMillionths, halfExtentsMillionths, "behindFall");
        }
    }

    public record PlungePoolSpec(
            VolumeSdfVec3V2 center,
            long radiusMillionths,
            int waterSurfaceYBlocks
    ) {
        public PlungePoolSpec {
            Objects.requireNonNull(center, "center");
            if (radiusMillionths < 2_000_000L || radiusMillionths > MAXIMUM_RADIUS_MILLIONTHS) {
                throw new IllegalArgumentException("plunge pool radius out of range");
            }
            if (waterSurfaceYBlocks < -512 || waterSurfaceYBlocks > 512) {
                throw new IllegalArgumentException("plunge pool waterSurfaceYBlocks out of range");
            }
        }
    }

    public record ArtifactBinding(
            int bindingVersion,
            String sourceArtifactChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String SDF_CONTRACT = "waterfall-volume-sdf-binding-v1";
        public static final String CSG_CONTRACT = "waterfall-volume-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("unknown waterfall-volume artifact binding version");
            }
            bindingContractVersion = nonBlank(bindingContractVersion, "bindingContractVersion", 64);
            if (!SDF_CONTRACT.equals(bindingContractVersion)
                    && !CSG_CONTRACT.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown waterfall-volume artifact binding");
            }
            sourceArtifactChecksum = checksum(sourceArtifactChecksum, "sourceArtifactChecksum");
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes,
            long maximumWorkingBytes,
            int maximumDescriptorSamples,
            int maximumFluidIntervalsPerColumn
    ) {
        public static final String VERSION = "waterfall-volume-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown waterfall-volume budget version");
            }
            if (estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1
                    || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES
                    || maximumFluidIntervalsPerColumn < 1
                    || maximumFluidIntervalsPerColumn > 8) {
                throw new IllegalArgumentException("waterfall-volume budget out of range");
            }
        }

        public static ResourceBudget standard() {
            return new ResourceBudget(
                    VERSION,
                    4096L,
                    MAX_CANONICAL_BYTES,
                    256L * 1024L,
                    MAXIMUM_DESCRIPTOR_SAMPLES,
                    2);
        }
    }

    private static void requireHalf(VolumeSdfVec3V2 half, String name) {
        if (half.xMillionths() < 1_000_000L
                || half.yMillionths() < 1_000_000L
                || half.zMillionths() < 1_000_000L
                || half.xMillionths() > MAXIMUM_RADIUS_MILLIONTHS
                || half.yMillionths() > MAXIMUM_RADIUS_MILLIONTHS
                || half.zMillionths() > MAXIMUM_RADIUS_MILLIONTHS) {
            throw new IllegalArgumentException(name + " half-extents out of range");
        }
    }

    private static void requireCorner(long corner, VolumeSdfVec3V2 half, String name) {
        if (corner < 0L
                || corner >= half.xMillionths()
                || corner >= half.yMillionths()
                || corner >= half.zMillionths()) {
            throw new IllegalArgumentException(name + " corner radius invalid");
        }
    }

    private static void validateBudget(ResourceBudget budget) {
        if (budget.maximumDescriptorSamples() > MAXIMUM_DESCRIPTOR_SAMPLES) {
            throw new IllegalArgumentException("waterfall-volume exceeds descriptor budget");
        }
    }

    private static String qualified(String value, String name) {
        if (value == null || !QUALIFIED.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " invalid");
        }
        return value;
    }

    private static String nonBlank(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(name + " invalid");
        }
        return value;
    }

    private static String checksum(String value, String name) {
        if (value == null || !CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be 64 lowercase hex");
        }
        return value;
    }
}
