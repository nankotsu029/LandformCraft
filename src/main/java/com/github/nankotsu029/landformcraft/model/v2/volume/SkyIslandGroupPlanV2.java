package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-12 {@code SKY_ISLAND_GROUP} plan. Ordered independent solid lobes with underside
 * carve, ground clearance, and inter-island gap. Ecology/material finishing and Paper apply are out
 * of scope.
 */
public record SkyIslandGroupPlanV2(
        int planVersion,
        String skyIslandGroupContractVersion,
        String featureId,
        Kernel kernel,
        int groundReferenceYBlocks,
        int minimumAllowedYBlocks,
        int maximumAllowedYBlocks,
        List<IslandComponent> components,
        VolumeSdfAabbV2 aabb,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String SKY_ISLAND_GROUP_CONTRACT_VERSION = "sky-island-group-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 64L * 1024L;
    public static final long MAXIMUM_HALF_EXTENT_MILLIONTHS = 32_000_000L;
    public static final int MAXIMUM_COMPONENTS = 8;
    public static final int MAXIMUM_DESCRIPTOR_SAMPLES = 64_000;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public SkyIslandGroupPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("sky-island-group planVersion must be 1");
        }
        skyIslandGroupContractVersion = nonBlank(
                skyIslandGroupContractVersion, "skyIslandGroupContractVersion", 64);
        if (!SKY_ISLAND_GROUP_CONTRACT_VERSION.equals(skyIslandGroupContractVersion)) {
            throw new IllegalArgumentException("unknown sky-island-group contract version");
        }
        featureId = qualified(featureId, "featureId");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(aabb, "aabb");
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (groundReferenceYBlocks < -512 || groundReferenceYBlocks > 512
                || minimumAllowedYBlocks < -512 || maximumAllowedYBlocks > 512
                || minimumAllowedYBlocks > maximumAllowedYBlocks) {
            throw new IllegalArgumentException("sky-island-group Y bounds out of range");
        }
        if (components.isEmpty()
                || components.size() > MAXIMUM_COMPONENTS
                || components.size() > kernel.maximumComponentCount()) {
            throw new IllegalArgumentException("sky-island-group component count out of range");
        }
        List<IslandComponent> sorted = new ArrayList<>(components);
        sorted.sort(Comparator.comparing(IslandComponent::componentId));
        Set<String> ids = new HashSet<>();
        for (IslandComponent component : sorted) {
            if (!ids.add(component.componentId())) {
                throw new IllegalArgumentException("sky-island-group duplicate componentId");
            }
            requireHalfExtents(component.lobe().halfExtentsMillionths(), kernel, "lobe");
            requireHalfExtents(component.underside().halfExtentsMillionths(), kernel, "underside");
        }
        components = List.copyOf(sorted);
        validateBudget(budget);
    }

    public SkyIslandGroupPlanV2 withCanonicalChecksum(String checksum) {
        return new SkyIslandGroupPlanV2(
                planVersion, skyIslandGroupContractVersion, featureId, kernel,
                groundReferenceYBlocks, minimumAllowedYBlocks, maximumAllowedYBlocks, components,
                aabb, sdfPlanBinding, csgPlanBinding, budget, checksum);
    }

    public record Kernel(
            String kernelVersion,
            int minimumComponentCount,
            int maximumComponentCount,
            int minimumGroundClearanceBlocks,
            int minimumInterIslandGapBlocks,
            int minimumThicknessBlocks,
            boolean supportFreeAllowed,
            long maximumHalfExtentMillionths,
            int maximumDescriptorSamples
    ) {
        public static final String VERSION = "sky-island-group-v1";

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown sky-island-group kernel version");
            }
            if (minimumComponentCount < 1 || minimumComponentCount > MAXIMUM_COMPONENTS
                    || maximumComponentCount < minimumComponentCount
                    || maximumComponentCount > MAXIMUM_COMPONENTS
                    || minimumGroundClearanceBlocks < 1 || minimumGroundClearanceBlocks > 256
                    || minimumInterIslandGapBlocks < 1 || minimumInterIslandGapBlocks > 256
                    || minimumThicknessBlocks < 1 || minimumThicknessBlocks > 64
                    || !supportFreeAllowed
                    || maximumHalfExtentMillionths < 1_000_000L
                    || maximumHalfExtentMillionths > MAXIMUM_HALF_EXTENT_MILLIONTHS
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES) {
                throw new IllegalArgumentException("sky-island-group kernel budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(
                    VERSION,
                    1,
                    MAXIMUM_COMPONENTS,
                    8,
                    4,
                    3,
                    true,
                    MAXIMUM_HALF_EXTENT_MILLIONTHS,
                    MAXIMUM_DESCRIPTOR_SAMPLES);
        }
    }

    public record IslandComponent(
            String componentId,
            BoxSpec lobe,
            BoxSpec underside
    ) {
        public IslandComponent {
            componentId = qualified(componentId, "componentId");
            Objects.requireNonNull(lobe, "lobe");
            Objects.requireNonNull(underside, "underside");
        }
    }

    public record BoxSpec(
            VolumeSdfVec3V2 center,
            VolumeSdfVec3V2 halfExtentsMillionths,
            long cornerRadiusMillionths
    ) {
        public BoxSpec {
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(halfExtentsMillionths, "halfExtentsMillionths");
            if (halfExtentsMillionths.xMillionths() < 1_000_000L
                    || halfExtentsMillionths.yMillionths() < 1_000_000L
                    || halfExtentsMillionths.zMillionths() < 1_000_000L
                    || halfExtentsMillionths.xMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS
                    || halfExtentsMillionths.yMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS
                    || halfExtentsMillionths.zMillionths() > MAXIMUM_HALF_EXTENT_MILLIONTHS) {
                throw new IllegalArgumentException("sky-island box half-extents out of range");
            }
            if (cornerRadiusMillionths < 0L
                    || cornerRadiusMillionths >= halfExtentsMillionths.xMillionths()
                    || cornerRadiusMillionths >= halfExtentsMillionths.yMillionths()
                    || cornerRadiusMillionths >= halfExtentsMillionths.zMillionths()) {
                throw new IllegalArgumentException("sky-island box corner radius invalid");
            }
        }
    }

    public record ArtifactBinding(
            int bindingVersion,
            String sourceArtifactChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String SDF_CONTRACT = "sky-island-group-sdf-binding-v1";
        public static final String CSG_CONTRACT = "sky-island-group-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("unknown sky-island-group artifact binding version");
            }
            bindingContractVersion = nonBlank(bindingContractVersion, "bindingContractVersion", 64);
            if (!SDF_CONTRACT.equals(bindingContractVersion)
                    && !CSG_CONTRACT.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown sky-island-group artifact binding");
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
            int maximumComponents
    ) {
        public static final String VERSION = "sky-island-group-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown sky-island-group budget version");
            }
            if (estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1
                    || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES
                    || maximumComponents < 1
                    || maximumComponents > MAXIMUM_COMPONENTS) {
                throw new IllegalArgumentException("sky-island-group budget out of range");
            }
        }

        public static ResourceBudget standard() {
            return new ResourceBudget(
                    VERSION,
                    4096L,
                    MAX_CANONICAL_BYTES,
                    256L * 1024L,
                    MAXIMUM_DESCRIPTOR_SAMPLES,
                    MAXIMUM_COMPONENTS);
        }
    }

    private static void requireHalfExtents(VolumeSdfVec3V2 half, Kernel kernel, String name) {
        if (half.xMillionths() > kernel.maximumHalfExtentMillionths()
                || half.yMillionths() > kernel.maximumHalfExtentMillionths()
                || half.zMillionths() > kernel.maximumHalfExtentMillionths()) {
            throw new IllegalArgumentException("sky-island " + name + " exceeds kernel half-extent");
        }
    }

    private static void validateBudget(ResourceBudget budget) {
        if (budget.maximumDescriptorSamples() > MAXIMUM_DESCRIPTOR_SAMPLES
                || budget.maximumComponents() > MAXIMUM_COMPONENTS) {
            throw new IllegalArgumentException("sky-island-group exceeds budget");
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
