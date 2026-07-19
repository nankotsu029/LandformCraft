package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-11 {@code NATURAL_ARCH} plan. Two-pier solid mass with an ordered through
 * {@code CARVE_SOLID} opening. Bridge structure assets, sky islands, and material finishing are
 * out of scope.
 */
public record NaturalArchPlanV2(
        int planVersion,
        String naturalArchContractVersion,
        String featureId,
        Kernel kernel,
        PassageAxis passageAxis,
        MassSpec mass,
        OpeningSpec opening,
        VolumeSdfAabbV2 aabb,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String NATURAL_ARCH_CONTRACT_VERSION = "natural-arch-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 48L * 1024L;
    public static final long MAXIMUM_HALF_EXTENT_MILLIONTHS = 32_000_000L;
    public static final int MAXIMUM_DESCRIPTOR_SAMPLES = 64_000;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public NaturalArchPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("natural-arch planVersion must be 1");
        }
        naturalArchContractVersion = nonBlank(naturalArchContractVersion, "naturalArchContractVersion", 64);
        if (!NATURAL_ARCH_CONTRACT_VERSION.equals(naturalArchContractVersion)) {
            throw new IllegalArgumentException("unknown natural-arch contract version");
        }
        featureId = qualified(featureId, "featureId");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(passageAxis, "passageAxis");
        Objects.requireNonNull(mass, "mass");
        Objects.requireNonNull(opening, "opening");
        Objects.requireNonNull(aabb, "aabb");
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (mass.halfExtentsMillionths().xMillionths() > kernel.maximumHalfExtentMillionths()
                || mass.halfExtentsMillionths().yMillionths() > kernel.maximumHalfExtentMillionths()
                || mass.halfExtentsMillionths().zMillionths() > kernel.maximumHalfExtentMillionths()
                || opening.halfExtentsMillionths().xMillionths() > kernel.maximumHalfExtentMillionths()
                || opening.halfExtentsMillionths().yMillionths() > kernel.maximumHalfExtentMillionths()
                || opening.halfExtentsMillionths().zMillionths() > kernel.maximumHalfExtentMillionths()) {
            throw new IllegalArgumentException("natural-arch half-extent exceeds kernel");
        }
        validateBudget(budget);
    }

    public NaturalArchPlanV2 withCanonicalChecksum(String checksum) {
        return new NaturalArchPlanV2(
                planVersion, naturalArchContractVersion, featureId, kernel, passageAxis, mass,
                opening, aabb, sdfPlanBinding, csgPlanBinding, budget, checksum);
    }

    /** Axis of the through-corridor; span is the orthogonal horizontal axis. */
    public enum PassageAxis {
        X, Z
    }

    public record Kernel(
            String kernelVersion,
            int minimumPierBlocks,
            int minimumCrownBlocks,
            int minimumClearanceSamples,
            int minimumSpanBlocks,
            long maximumHalfExtentMillionths,
            int maximumDescriptorSamples
    ) {
        public static final String VERSION = "natural-arch-v1";

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown natural-arch kernel version");
            }
            if (minimumPierBlocks < 1 || minimumPierBlocks > 64
                    || minimumCrownBlocks < 1 || minimumCrownBlocks > 64
                    || minimumClearanceSamples < 1 || minimumClearanceSamples > 4096
                    || minimumSpanBlocks < 1 || minimumSpanBlocks > 64
                    || maximumHalfExtentMillionths < 1_000_000L
                    || maximumHalfExtentMillionths > MAXIMUM_HALF_EXTENT_MILLIONTHS
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES) {
                throw new IllegalArgumentException("natural-arch kernel budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(
                    VERSION,
                    3,
                    3,
                    8,
                    4,
                    MAXIMUM_HALF_EXTENT_MILLIONTHS,
                    MAXIMUM_DESCRIPTOR_SAMPLES);
        }
    }

    public record MassSpec(
            VolumeSdfVec3V2 center,
            VolumeSdfVec3V2 halfExtentsMillionths,
            long cornerRadiusMillionths
    ) {
        public MassSpec {
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(halfExtentsMillionths, "halfExtentsMillionths");
            requireHalfExtents(halfExtentsMillionths, "mass");
            requireCorner(cornerRadiusMillionths, halfExtentsMillionths, "mass");
        }
    }

    public record OpeningSpec(
            VolumeSdfVec3V2 center,
            VolumeSdfVec3V2 halfExtentsMillionths,
            long cornerRadiusMillionths
    ) {
        public OpeningSpec {
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(halfExtentsMillionths, "halfExtentsMillionths");
            requireHalfExtents(halfExtentsMillionths, "opening");
            requireCorner(cornerRadiusMillionths, halfExtentsMillionths, "opening");
        }
    }

    public record ArtifactBinding(
            int bindingVersion,
            String sourceArtifactChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String SDF_CONTRACT = "natural-arch-sdf-binding-v1";
        public static final String CSG_CONTRACT = "natural-arch-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("unknown natural-arch artifact binding version");
            }
            bindingContractVersion = nonBlank(bindingContractVersion, "bindingContractVersion", 64);
            if (!SDF_CONTRACT.equals(bindingContractVersion)
                    && !CSG_CONTRACT.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown natural-arch artifact binding");
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
        public static final String VERSION = "natural-arch-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown natural-arch budget version");
            }
            if (estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1
                    || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES) {
                throw new IllegalArgumentException("natural-arch budget out of range");
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

    private static void requireHalfExtents(VolumeSdfVec3V2 half, String name) {
        if (half.xMillionths() < 1_000_000L
                || half.yMillionths() < 1_000_000L
                || half.zMillionths() < 1_000_000L
                || half.xMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS
                || half.yMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS
                || half.zMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS) {
            throw new IllegalArgumentException("natural-arch " + name + " half-extents out of range");
        }
    }

    private static void requireCorner(long corner, VolumeSdfVec3V2 half, String name) {
        if (corner < 0L
                || corner >= half.xMillionths()
                || corner >= half.yMillionths()
                || corner >= half.zMillionths()) {
            throw new IllegalArgumentException("natural-arch " + name + " corner radius invalid");
        }
    }

    private static void validateBudget(ResourceBudget budget) {
        if (budget.maximumDescriptorSamples() > MAXIMUM_DESCRIPTOR_SAMPLES) {
            throw new IllegalArgumentException("natural-arch exceeds descriptor budget");
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
