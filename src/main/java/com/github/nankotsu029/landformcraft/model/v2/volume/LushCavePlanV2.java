package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-07 {@code LUSH_CAVE} plan. Composite on a host {@link CaveNetworkPlanV2} chamber:
 * WITHIN／REACHABLE_FROM bindings, chamber carve SDF／CSG, wet floor/wall/ceiling classification,
 * and ecology hooks only. Full ecology placement, underground lakes, and lighting are out of scope.
 */
public record LushCavePlanV2(
        int planVersion,
        String lushContractVersion,
        String featureId,
        Kernel kernel,
        HostBinding hostBinding,
        ReachableFromBinding reachableFrom,
        ChamberSpec chamber,
        WetCondition wetCondition,
        EcologyHook ecologyHook,
        VolumeSdfAabbV2 aabb,
        int surfaceHeightBlocks,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String LUSH_CONTRACT_VERSION = "lush-cave-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 48L * 1024L;
    public static final long MAXIMUM_RADIUS_MILLIONTHS = 30_000_000L;
    public static final int MAXIMUM_MOISTURE_MILLIONTHS = 1_000_000;
    public static final int MAXIMUM_POOL_SHARE_MILLIONTHS = 1_000_000;
    public static final int MAXIMUM_DESCRIPTOR_SAMPLES = 64_000;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public LushCavePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("lush-cave planVersion must be 1");
        }
        lushContractVersion = nonBlank(lushContractVersion, "lushContractVersion", 64);
        if (!LUSH_CONTRACT_VERSION.equals(lushContractVersion)) {
            throw new IllegalArgumentException("unknown lush-cave contract version");
        }
        featureId = qualified(featureId, "featureId");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(hostBinding, "hostBinding");
        Objects.requireNonNull(reachableFrom, "reachableFrom");
        Objects.requireNonNull(chamber, "chamber");
        Objects.requireNonNull(wetCondition, "wetCondition");
        Objects.requireNonNull(ecologyHook, "ecologyHook");
        Objects.requireNonNull(aabb, "aabb");
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (surfaceHeightBlocks < -512 || surfaceHeightBlocks > 512) {
            throw new IllegalArgumentException("lush-cave surfaceHeightBlocks out of range");
        }
        if (chamber.radiusMillionths() > kernel.maximumRadiusMillionths()) {
            throw new IllegalArgumentException("lush-cave chamber radius exceeds kernel");
        }
        if (wetCondition.moistureMillionths() < kernel.minimumMoistureMillionths()) {
            throw new IllegalArgumentException("lush-cave moisture below kernel minimum");
        }
        if (chamber.ceilingClearanceBlocks() < kernel.minimumCeilingClearanceBlocks()) {
            throw new IllegalArgumentException("lush-cave ceiling clearance below kernel minimum");
        }
        validateBudget(budget);
    }

    public LushCavePlanV2 withCanonicalChecksum(String checksum) {
        return new LushCavePlanV2(
                planVersion, lushContractVersion, featureId, kernel, hostBinding, reachableFrom,
                chamber, wetCondition, ecologyHook, aabb, surfaceHeightBlocks,
                sdfPlanBinding, csgPlanBinding, budget, checksum);
    }

    public enum WetSurfaceClass {
        FLOOR,
        WALL,
        CEILING
    }

    public record Kernel(
            String kernelVersion,
            int minimumRoofBlocks,
            int minimumMoistureMillionths,
            int minimumCeilingClearanceBlocks,
            long maximumRadiusMillionths,
            int maximumDescriptorSamples
    ) {
        public static final String VERSION = "lush-cave-v1";

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown lush-cave kernel version");
            }
            if (minimumRoofBlocks < 1 || minimumRoofBlocks > 64
                    || minimumMoistureMillionths < 1
                    || minimumMoistureMillionths > MAXIMUM_MOISTURE_MILLIONTHS
                    || minimumCeilingClearanceBlocks < 1
                    || minimumCeilingClearanceBlocks > 64
                    || maximumRadiusMillionths < 1_000_000L
                    || maximumRadiusMillionths > MAXIMUM_RADIUS_MILLIONTHS
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES) {
                throw new IllegalArgumentException("lush-cave kernel budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(
                    VERSION,
                    3,
                    750_000,
                    8,
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
                throw new IllegalArgumentException("lush-cave host relation must be WITHIN");
            }
            hostNetworkFeatureId = qualified(hostNetworkFeatureId, "hostNetworkFeatureId");
            hostNetworkPlanChecksum = checksum(hostNetworkPlanChecksum, "hostNetworkPlanChecksum");
            hostChamberNodeId = qualified(hostChamberNodeId, "hostChamberNodeId");
        }
    }

    public record ReachableFromBinding(
            String relationKind,
            String entranceNodeId
    ) {
        public static final String REACHABLE_FROM = "REACHABLE_FROM";

        public ReachableFromBinding {
            relationKind = nonBlank(relationKind, "relationKind", 32);
            if (!REACHABLE_FROM.equals(relationKind)) {
                throw new IllegalArgumentException("lush-cave reachability relation must be REACHABLE_FROM");
            }
            entranceNodeId = qualified(entranceNodeId, "entranceNodeId");
        }
    }

    public record ChamberSpec(
            VolumeSdfVec3V2 center,
            long radiusMillionths,
            int ceilingClearanceBlocks
    ) {
        public ChamberSpec {
            Objects.requireNonNull(center, "center");
            if (radiusMillionths < 1_000_000L || radiusMillionths > MAXIMUM_RADIUS_MILLIONTHS) {
                throw new IllegalArgumentException("lush-cave chamber radius out of range");
            }
            if (ceilingClearanceBlocks < 1 || ceilingClearanceBlocks > 64) {
                throw new IllegalArgumentException("lush-cave ceilingClearanceBlocks out of range");
            }
        }
    }

    public record WetCondition(
            int moistureMillionths,
            int poolShareMillionths,
            List<WetSurfaceClass> eligibleSurfaceClasses
    ) {
        public WetCondition {
            if (moistureMillionths < 0 || moistureMillionths > MAXIMUM_MOISTURE_MILLIONTHS
                    || poolShareMillionths < 0
                    || poolShareMillionths > MAXIMUM_POOL_SHARE_MILLIONTHS) {
                throw new IllegalArgumentException("lush-cave wet condition out of range");
            }
            eligibleSurfaceClasses = List.copyOf(Objects.requireNonNull(
                    eligibleSurfaceClasses, "eligibleSurfaceClasses"));
            if (eligibleSurfaceClasses.isEmpty() || eligibleSurfaceClasses.size() > 3) {
                throw new IllegalArgumentException("lush-cave eligible surfaces out of range");
            }
            Set<WetSurfaceClass> seen = new HashSet<>();
            for (WetSurfaceClass surface : eligibleSurfaceClasses) {
                Objects.requireNonNull(surface, "eligibleSurfaceClasses element");
                if (!seen.add(surface)) {
                    throw new IllegalArgumentException("duplicate lush-cave wet surface class");
                }
            }
        }
    }

    public record EcologyHook(
            String hookVersion,
            String ecologyPreset,
            List<String> reservedAssemblageIds
    ) {
        public static final String VERSION = "lush-cave-ecology-hook-v1";
        public static final String PRESET = "LUSH_SUBTERRANEAN";

        public EcologyHook {
            hookVersion = nonBlank(hookVersion, "hookVersion", 64);
            if (!VERSION.equals(hookVersion)) {
                throw new IllegalArgumentException("unknown lush-cave ecology hook version");
            }
            ecologyPreset = nonBlank(ecologyPreset, "ecologyPreset", 64);
            if (!PRESET.equals(ecologyPreset)) {
                throw new IllegalArgumentException("lush-cave ecologyPreset must be LUSH_SUBTERRANEAN");
            }
            reservedAssemblageIds = List.copyOf(Objects.requireNonNull(
                    reservedAssemblageIds, "reservedAssemblageIds"));
            if (reservedAssemblageIds.isEmpty() || reservedAssemblageIds.size() > 8) {
                throw new IllegalArgumentException("lush-cave ecology hook assemblage count out of range");
            }
            Set<String> seen = new HashSet<>();
            for (String id : reservedAssemblageIds) {
                if (id == null || !QUALIFIED.matcher(id).matches() || !seen.add(id)) {
                    throw new IllegalArgumentException("lush-cave reserved assemblage id invalid");
                }
            }
        }

        public static EcologyHook standard() {
            return new EcologyHook(
                    VERSION,
                    PRESET,
                    List.of(
                            "ecology.lush-ceiling-vine",
                            "ecology.lush-wall-clay",
                            "ecology.lush-floor-moss"));
        }
    }

    public record ArtifactBinding(
            int bindingVersion,
            String sourceArtifactChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String SDF_CONTRACT = "lush-cave-sdf-binding-v1";
        public static final String CSG_CONTRACT = "lush-cave-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("unknown lush-cave artifact binding version");
            }
            bindingContractVersion = nonBlank(bindingContractVersion, "bindingContractVersion", 64);
            if (!SDF_CONTRACT.equals(bindingContractVersion)
                    && !CSG_CONTRACT.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown lush-cave artifact binding");
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
        public static final String VERSION = "lush-cave-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown lush-cave budget version");
            }
            if (estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1
                    || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES) {
                throw new IllegalArgumentException("lush-cave budget out of range");
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
            throw new IllegalArgumentException("lush-cave exceeds descriptor budget");
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
