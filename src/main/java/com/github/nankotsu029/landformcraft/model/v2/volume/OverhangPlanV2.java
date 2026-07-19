package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-10 {@code OVERHANG} plan. Host-cliff support with seaward {@code ADD_SOLID}
 * lobe and underside {@code CARVE_SOLID} recess. Natural arch, sea cave, and Paper gravity are
 * out of scope.
 */
public record OverhangPlanV2(
        int planVersion,
        String overhangContractVersion,
        String featureId,
        Kernel kernel,
        HostCliffBinding hostCliff,
        LobeSpec lobe,
        RecessSpec recess,
        VolumeSdfAabbV2 aabb,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String OVERHANG_CONTRACT_VERSION = "overhang-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 48L * 1024L;
    public static final long MAXIMUM_HALF_EXTENT_MILLIONTHS = 32_000_000L;
    public static final int MAXIMUM_DESCRIPTOR_SAMPLES = 64_000;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public OverhangPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("overhang planVersion must be 1");
        }
        overhangContractVersion = nonBlank(overhangContractVersion, "overhangContractVersion", 64);
        if (!OVERHANG_CONTRACT_VERSION.equals(overhangContractVersion)) {
            throw new IllegalArgumentException("unknown overhang contract version");
        }
        featureId = qualified(featureId, "featureId");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(hostCliff, "hostCliff");
        Objects.requireNonNull(lobe, "lobe");
        Objects.requireNonNull(recess, "recess");
        Objects.requireNonNull(aabb, "aabb");
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (lobe.halfExtentsMillionths().xMillionths() > kernel.maximumHalfExtentMillionths()
                || lobe.halfExtentsMillionths().yMillionths() > kernel.maximumHalfExtentMillionths()
                || lobe.halfExtentsMillionths().zMillionths() > kernel.maximumHalfExtentMillionths()
                || recess.halfExtentsMillionths().xMillionths() > kernel.maximumHalfExtentMillionths()
                || recess.halfExtentsMillionths().yMillionths() > kernel.maximumHalfExtentMillionths()
                || recess.halfExtentsMillionths().zMillionths() > kernel.maximumHalfExtentMillionths()) {
            throw new IllegalArgumentException("overhang half-extent exceeds kernel");
        }
        validateBudget(budget);
    }

    public OverhangPlanV2 withCanonicalChecksum(String checksum) {
        return new OverhangPlanV2(
                planVersion, overhangContractVersion, featureId, kernel, hostCliff, lobe, recess,
                aabb, sdfPlanBinding, csgPlanBinding, budget, checksum);
    }

    public enum CardinalFace {
        NORTH, EAST, SOUTH, WEST
    }

    public record Kernel(
            String kernelVersion,
            int minimumRoofBlocks,
            int minimumSupportSamples,
            int minimumClearanceSamples,
            int minimumProjectionBlocks,
            long maximumHalfExtentMillionths,
            int maximumDescriptorSamples
    ) {
        public static final String VERSION = "overhang-v1";

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown overhang kernel version");
            }
            if (minimumRoofBlocks < 1 || minimumRoofBlocks > 64
                    || minimumSupportSamples < 1 || minimumSupportSamples > 4096
                    || minimumClearanceSamples < 1 || minimumClearanceSamples > 4096
                    || minimumProjectionBlocks < 1 || minimumProjectionBlocks > 64
                    || maximumHalfExtentMillionths < 1_000_000L
                    || maximumHalfExtentMillionths > MAXIMUM_HALF_EXTENT_MILLIONTHS
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES) {
                throw new IllegalArgumentException("overhang kernel budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(
                    VERSION,
                    3,
                    4,
                    8,
                    4,
                    MAXIMUM_HALF_EXTENT_MILLIONTHS,
                    MAXIMUM_DESCRIPTOR_SAMPLES);
        }
    }

    public record HostCliffBinding(
            String relationKind,
            String hostCliffFeatureId,
            CardinalFace seawardFace,
            VolumeSdfAabbV2 hostAabb
    ) {
        public static final String SUPPORTS_FROM = "SUPPORTS_FROM";

        public HostCliffBinding {
            relationKind = nonBlank(relationKind, "relationKind", 32);
            if (!SUPPORTS_FROM.equals(relationKind)) {
                throw new IllegalArgumentException("overhang host relation must be SUPPORTS_FROM");
            }
            hostCliffFeatureId = qualified(hostCliffFeatureId, "hostCliffFeatureId");
            Objects.requireNonNull(seawardFace, "seawardFace");
            Objects.requireNonNull(hostAabb, "hostAabb");
        }
    }

    public record LobeSpec(
            VolumeSdfVec3V2 center,
            VolumeSdfVec3V2 halfExtentsMillionths,
            long cornerRadiusMillionths
    ) {
        public LobeSpec {
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(halfExtentsMillionths, "halfExtentsMillionths");
            if (halfExtentsMillionths.xMillionths() < 1_000_000L
                    || halfExtentsMillionths.yMillionths() < 1_000_000L
                    || halfExtentsMillionths.zMillionths() < 1_000_000L
                    || halfExtentsMillionths.xMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS
                    || halfExtentsMillionths.yMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS
                    || halfExtentsMillionths.zMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS) {
                throw new IllegalArgumentException("overhang lobe half-extents out of range");
            }
            if (cornerRadiusMillionths < 0L
                    || cornerRadiusMillionths >= halfExtentsMillionths.xMillionths()
                    || cornerRadiusMillionths >= halfExtentsMillionths.yMillionths()
                    || cornerRadiusMillionths >= halfExtentsMillionths.zMillionths()) {
                throw new IllegalArgumentException("overhang lobe corner radius invalid");
            }
        }
    }

    public record RecessSpec(
            VolumeSdfVec3V2 center,
            VolumeSdfVec3V2 halfExtentsMillionths,
            long cornerRadiusMillionths
    ) {
        public RecessSpec {
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(halfExtentsMillionths, "halfExtentsMillionths");
            if (halfExtentsMillionths.xMillionths() < 1_000_000L
                    || halfExtentsMillionths.yMillionths() < 1_000_000L
                    || halfExtentsMillionths.zMillionths() < 1_000_000L
                    || halfExtentsMillionths.xMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS
                    || halfExtentsMillionths.yMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS
                    || halfExtentsMillionths.zMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS) {
                throw new IllegalArgumentException("overhang recess half-extents out of range");
            }
            if (cornerRadiusMillionths < 0L
                    || cornerRadiusMillionths >= halfExtentsMillionths.xMillionths()
                    || cornerRadiusMillionths >= halfExtentsMillionths.yMillionths()
                    || cornerRadiusMillionths >= halfExtentsMillionths.zMillionths()) {
                throw new IllegalArgumentException("overhang recess corner radius invalid");
            }
        }
    }

    public record ArtifactBinding(
            int bindingVersion,
            String sourceArtifactChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String SDF_CONTRACT = "overhang-sdf-binding-v1";
        public static final String CSG_CONTRACT = "overhang-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("unknown overhang artifact binding version");
            }
            bindingContractVersion = nonBlank(bindingContractVersion, "bindingContractVersion", 64);
            if (!SDF_CONTRACT.equals(bindingContractVersion)
                    && !CSG_CONTRACT.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown overhang artifact binding");
            }
            sourceArtifactChecksum = checksum(sourceArtifactChecksum, "sourceArtifactChecksum");
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes,
            long maximumWorkingBytes,
            int maximumDescriptorSamples
    ) {
        public static final String VERSION = "overhang-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown overhang budget version");
            }
            if (estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1
                    || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES) {
                throw new IllegalArgumentException("overhang budget out of range");
            }
        }

        public static ResourceBudget standard() {
            return new ResourceBudget(
                    VERSION,
                    3072L,
                    MAX_CANONICAL_BYTES,
                    256L * 1024L,
                    MAXIMUM_DESCRIPTOR_SAMPLES);
        }
    }

    private static void validateBudget(ResourceBudget budget) {
        if (budget.maximumDescriptorSamples() > MAXIMUM_DESCRIPTOR_SAMPLES) {
            throw new IllegalArgumentException("overhang exceeds descriptor budget");
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
