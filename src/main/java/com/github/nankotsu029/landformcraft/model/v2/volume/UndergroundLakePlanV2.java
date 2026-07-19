package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-08 {@code UNDERGROUND_LAKE} plan. Basin carve plus a single contained
 * {@code ADD_FLUID} body inside a host {@link CaveNetworkPlanV2} chamber. Sea connection, Paper
 * fluid physics, and lush material finishing are out of scope.
 */
public record UndergroundLakePlanV2(
        int planVersion,
        String lakeContractVersion,
        String featureId,
        Kernel kernel,
        HostBinding hostBinding,
        CaveAccessBinding caveAccess,
        BasinSpec basin,
        FluidBody fluidBody,
        VolumeSdfAabbV2 aabb,
        int surfaceHeightBlocks,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String LAKE_CONTRACT_VERSION = "underground-lake-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 48L * 1024L;
    public static final long MAXIMUM_RADIUS_MILLIONTHS = 24_000_000L;
    public static final int MAXIMUM_DESCRIPTOR_SAMPLES = 64_000;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public UndergroundLakePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("underground-lake planVersion must be 1");
        }
        lakeContractVersion = nonBlank(lakeContractVersion, "lakeContractVersion", 64);
        if (!LAKE_CONTRACT_VERSION.equals(lakeContractVersion)) {
            throw new IllegalArgumentException("unknown underground-lake contract version");
        }
        featureId = qualified(featureId, "featureId");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(hostBinding, "hostBinding");
        Objects.requireNonNull(caveAccess, "caveAccess");
        Objects.requireNonNull(basin, "basin");
        Objects.requireNonNull(fluidBody, "fluidBody");
        Objects.requireNonNull(aabb, "aabb");
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (surfaceHeightBlocks < -512 || surfaceHeightBlocks > 512) {
            throw new IllegalArgumentException("underground-lake surfaceHeightBlocks out of range");
        }
        if (basin.radiusMillionths() > kernel.maximumRadiusMillionths()) {
            throw new IllegalArgumentException("underground-lake basin radius exceeds kernel");
        }
        if (basin.minimumAirCavityBlocks() < kernel.minimumAirCavityBlocks()) {
            throw new IllegalArgumentException("underground-lake air cavity below kernel minimum");
        }
        int basinCenterY = (int) Math.floorDiv(basin.center().yMillionths(), 1_000_000L);
        if (fluidBody.waterSurfaceYBlocks() >= basinCenterY + (int) Math.floorDiv(
                basin.radiusMillionths(), 1_000_000L)
                || fluidBody.waterSurfaceYBlocks() <= basinCenterY - (int) Math.floorDiv(
                basin.radiusMillionths(), 1_000_000L)) {
            throw new IllegalArgumentException("underground-lake water surface outside basin");
        }
        validateBudget(budget);
    }

    public UndergroundLakePlanV2 withCanonicalChecksum(String checksum) {
        return new UndergroundLakePlanV2(
                planVersion, lakeContractVersion, featureId, kernel, hostBinding, caveAccess,
                basin, fluidBody, aabb, surfaceHeightBlocks, sdfPlanBinding, csgPlanBinding,
                budget, checksum);
    }

    public record Kernel(
            String kernelVersion,
            int minimumRoofBlocks,
            int minimumAirCavityBlocks,
            int minimumRimThicknessBlocks,
            long maximumRadiusMillionths,
            int maximumDescriptorSamples
    ) {
        public static final String VERSION = "underground-lake-v1";

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown underground-lake kernel version");
            }
            if (minimumRoofBlocks < 1 || minimumRoofBlocks > 64
                    || minimumAirCavityBlocks < 1 || minimumAirCavityBlocks > 64
                    || minimumRimThicknessBlocks < 1 || minimumRimThicknessBlocks > 16
                    || maximumRadiusMillionths < 1_000_000L
                    || maximumRadiusMillionths > MAXIMUM_RADIUS_MILLIONTHS
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES) {
                throw new IllegalArgumentException("underground-lake kernel budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(
                    VERSION,
                    3,
                    2,
                    1,
                    MAXIMUM_RADIUS_MILLIONTHS,
                    MAXIMUM_DESCRIPTOR_SAMPLES);
        }
    }

    public record HostBinding(
            String relationKind,
            String hostNetworkFeatureId,
            String hostNetworkPlanChecksum,
            String hostChamberNodeId
    ) {
        public static final String WITHIN = "WITHIN";

        public HostBinding {
            relationKind = nonBlank(relationKind, "relationKind", 32);
            if (!WITHIN.equals(relationKind)) {
                throw new IllegalArgumentException("underground-lake host relation must be WITHIN");
            }
            hostNetworkFeatureId = qualified(hostNetworkFeatureId, "hostNetworkFeatureId");
            hostNetworkPlanChecksum = checksum(hostNetworkPlanChecksum, "hostNetworkPlanChecksum");
            hostChamberNodeId = qualified(hostChamberNodeId, "hostChamberNodeId");
        }
    }

    public record CaveAccessBinding(
            String relationKind,
            String entranceNodeId
    ) {
        public static final String REACHABLE_FROM = "REACHABLE_FROM";

        public CaveAccessBinding {
            relationKind = nonBlank(relationKind, "relationKind", 32);
            if (!REACHABLE_FROM.equals(relationKind)) {
                throw new IllegalArgumentException(
                        "underground-lake cave access must be REACHABLE_FROM");
            }
            entranceNodeId = qualified(entranceNodeId, "entranceNodeId");
        }
    }

    public record BasinSpec(
            VolumeSdfVec3V2 center,
            long radiusMillionths,
            int minimumAirCavityBlocks
    ) {
        public BasinSpec {
            Objects.requireNonNull(center, "center");
            if (radiusMillionths < 2_000_000L || radiusMillionths > MAXIMUM_RADIUS_MILLIONTHS) {
                throw new IllegalArgumentException("underground-lake basin radius out of range");
            }
            if (minimumAirCavityBlocks < 1 || minimumAirCavityBlocks > 64) {
                throw new IllegalArgumentException("underground-lake air cavity out of range");
            }
        }
    }

    public record FluidBody(
            String fluidBodyId,
            int waterSurfaceYBlocks
    ) {
        public FluidBody {
            fluidBodyId = qualified(fluidBodyId, "fluidBodyId");
            if (waterSurfaceYBlocks < -512 || waterSurfaceYBlocks > 512) {
                throw new IllegalArgumentException("underground-lake waterSurfaceYBlocks out of range");
            }
        }
    }

    public record ArtifactBinding(
            int bindingVersion,
            String sourceArtifactChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String SDF_CONTRACT = "underground-lake-sdf-binding-v1";
        public static final String CSG_CONTRACT = "underground-lake-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("unknown underground-lake artifact binding version");
            }
            bindingContractVersion = nonBlank(bindingContractVersion, "bindingContractVersion", 64);
            if (!SDF_CONTRACT.equals(bindingContractVersion)
                    && !CSG_CONTRACT.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown underground-lake artifact binding");
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
        public static final String VERSION = "underground-lake-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown underground-lake budget version");
            }
            if (estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1
                    || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES
                    || maximumFluidIntervalsPerColumn < 1
                    || maximumFluidIntervalsPerColumn > 8) {
                throw new IllegalArgumentException("underground-lake budget out of range");
            }
        }

        public static ResourceBudget standard() {
            return new ResourceBudget(
                    VERSION,
                    3072L,
                    MAX_CANONICAL_BYTES,
                    256L * 1024L,
                    MAXIMUM_DESCRIPTOR_SAMPLES,
                    1);
        }
    }

    private static void validateBudget(ResourceBudget budget) {
        if (budget.maximumDescriptorSamples() > MAXIMUM_DESCRIPTOR_SAMPLES) {
            throw new IllegalArgumentException("underground-lake exceeds descriptor budget");
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
