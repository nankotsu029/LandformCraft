package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-09 {@code SEA_CAVE} plan. Cliff-hosted chamber carve with a marine opening and a
 * single static {@code ADD_FLUID} body. Dynamic tide, wave erosion, and Paper placement are out of
 * scope.
 */
public record SeaCavePlanV2(
        int planVersion,
        String seaCaveContractVersion,
        String featureId,
        Kernel kernel,
        HostCliffBinding hostCliff,
        MarineBoundaryBinding marineBoundary,
        ChamberSpec chamber,
        FluidBody fluidBody,
        VolumeSdfAabbV2 aabb,
        int surfaceHeightBlocks,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String SEA_CAVE_CONTRACT_VERSION = "sea-cave-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 48L * 1024L;
    public static final long MAXIMUM_RADIUS_MILLIONTHS = 20_000_000L;
    public static final int MAXIMUM_DESCRIPTOR_SAMPLES = 64_000;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public SeaCavePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("sea-cave planVersion must be 1");
        }
        seaCaveContractVersion = nonBlank(seaCaveContractVersion, "seaCaveContractVersion", 64);
        if (!SEA_CAVE_CONTRACT_VERSION.equals(seaCaveContractVersion)) {
            throw new IllegalArgumentException("unknown sea-cave contract version");
        }
        featureId = qualified(featureId, "featureId");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(hostCliff, "hostCliff");
        Objects.requireNonNull(marineBoundary, "marineBoundary");
        Objects.requireNonNull(chamber, "chamber");
        Objects.requireNonNull(fluidBody, "fluidBody");
        Objects.requireNonNull(aabb, "aabb");
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (surfaceHeightBlocks < -512 || surfaceHeightBlocks > 512) {
            throw new IllegalArgumentException("sea-cave surfaceHeightBlocks out of range");
        }
        if (chamber.radiusMillionths() > kernel.maximumRadiusMillionths()) {
            throw new IllegalArgumentException("sea-cave chamber radius exceeds kernel");
        }
        if (fluidBody.waterSurfaceYBlocks() != marineBoundary.seaLevelYBlocks()) {
            throw new IllegalArgumentException("sea-cave static fluid must match sea level");
        }
        validateBudget(budget);
    }

    public SeaCavePlanV2 withCanonicalChecksum(String checksum) {
        return new SeaCavePlanV2(
                planVersion, seaCaveContractVersion, featureId, kernel, hostCliff, marineBoundary,
                chamber, fluidBody, aabb, surfaceHeightBlocks, sdfPlanBinding, csgPlanBinding,
                budget, checksum);
    }

    public enum CardinalFace {
        NORTH, EAST, SOUTH, WEST
    }

    public record Kernel(
            String kernelVersion,
            int minimumRoofBlocks,
            int minimumOpeningBlocks,
            long maximumRadiusMillionths,
            int maximumDescriptorSamples
    ) {
        public static final String VERSION = "sea-cave-v1";

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown sea-cave kernel version");
            }
            if (minimumRoofBlocks < 1 || minimumRoofBlocks > 64
                    || minimumOpeningBlocks < 1 || minimumOpeningBlocks > 32
                    || maximumRadiusMillionths < 1_000_000L
                    || maximumRadiusMillionths > MAXIMUM_RADIUS_MILLIONTHS
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES) {
                throw new IllegalArgumentException("sea-cave kernel budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(
                    VERSION,
                    3,
                    2,
                    MAXIMUM_RADIUS_MILLIONTHS,
                    MAXIMUM_DESCRIPTOR_SAMPLES);
        }
    }

    public record HostCliffBinding(
            String relationKind,
            String hostCliffFeatureId,
            CardinalFace seawardFace,
            VolumeSdfAabbV2 hostAabb
    ) {
        public static final String CARVES_FLANK_OF = "CARVES_FLANK_OF";

        public HostCliffBinding {
            relationKind = nonBlank(relationKind, "relationKind", 32);
            if (!CARVES_FLANK_OF.equals(relationKind)) {
                throw new IllegalArgumentException("sea-cave host relation must be CARVES_FLANK_OF");
            }
            hostCliffFeatureId = qualified(hostCliffFeatureId, "hostCliffFeatureId");
            Objects.requireNonNull(seawardFace, "seawardFace");
            Objects.requireNonNull(hostAabb, "hostAabb");
        }
    }

    public record MarineBoundaryBinding(
            String relationKind,
            String marineBoundaryId,
            int seaLevelYBlocks
    ) {
        public static final String EMPTIES_INTO = "EMPTIES_INTO";
        public static final String SEA = "boundary.sea";

        public MarineBoundaryBinding {
            relationKind = nonBlank(relationKind, "relationKind", 32);
            if (!EMPTIES_INTO.equals(relationKind)) {
                throw new IllegalArgumentException("sea-cave marine relation must be EMPTIES_INTO");
            }
            marineBoundaryId = qualified(marineBoundaryId, "marineBoundaryId");
            if (!SEA.equals(marineBoundaryId)) {
                throw new IllegalArgumentException("sea-cave marine boundary must be boundary.sea");
            }
            if (seaLevelYBlocks < -512 || seaLevelYBlocks > 512) {
                throw new IllegalArgumentException("sea-cave seaLevelYBlocks out of range");
            }
        }
    }

    public record ChamberSpec(
            VolumeSdfVec3V2 openingCenter,
            VolumeSdfVec3V2 inlandCenter,
            long radiusMillionths
    ) {
        public ChamberSpec {
            Objects.requireNonNull(openingCenter, "openingCenter");
            Objects.requireNonNull(inlandCenter, "inlandCenter");
            if (radiusMillionths < 2_000_000L || radiusMillionths > MAXIMUM_RADIUS_MILLIONTHS) {
                throw new IllegalArgumentException("sea-cave chamber radius out of range");
            }
            if (openingCenter.equals(inlandCenter)) {
                throw new IllegalArgumentException("sea-cave opening and inland centers must differ");
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
                throw new IllegalArgumentException("sea-cave waterSurfaceYBlocks out of range");
            }
        }
    }

    public record ArtifactBinding(
            int bindingVersion,
            String sourceArtifactChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String SDF_CONTRACT = "sea-cave-sdf-binding-v1";
        public static final String CSG_CONTRACT = "sea-cave-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("unknown sea-cave artifact binding version");
            }
            bindingContractVersion = nonBlank(bindingContractVersion, "bindingContractVersion", 64);
            if (!SDF_CONTRACT.equals(bindingContractVersion)
                    && !CSG_CONTRACT.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown sea-cave artifact binding");
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
        public static final String VERSION = "sea-cave-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown sea-cave budget version");
            }
            if (estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1
                    || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES
                    || maximumFluidIntervalsPerColumn < 1
                    || maximumFluidIntervalsPerColumn > 8) {
                throw new IllegalArgumentException("sea-cave budget out of range");
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
            throw new IllegalArgumentException("sea-cave exceeds descriptor budget");
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
