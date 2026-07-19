package com.github.nankotsu029.landformcraft.model.v2.environment.local;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-14 post-volume local environment／material／sparse-placement contract.
 * Applies after volume geometry／fluid freeze: surface class, wetness／drip／shade, host material
 * profiles, and sparse moss／root／pool placements. New volume shapes, lighting engines, and
 * Paper biome／entity placement are out of scope.
 */
public record VolumeLocalEnvironmentPlanV2(
        int planVersion,
        String localEnvironmentContractVersion,
        String featureId,
        Kernel kernel,
        List<HostVolumeBinding> hostBindings,
        Catalog catalog,
        List<SurfaceProfileRule> surfaceProfiles,
        List<SparsePlacementRule> sparsePlacements,
        MaterialProfileBinding materialProfileBinding,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String LOCAL_ENVIRONMENT_CONTRACT_VERSION =
            "volume-local-environment-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 48L * 1024L;
    public static final int MAXIMUM_HOST_BINDINGS = 8;
    public static final int MAXIMUM_SURFACE_PROFILES = 16;
    public static final int MAXIMUM_SPARSE_RULES = 8;
    public static final int MAXIMUM_DESCRIPTOR_SAMPLES = 64_000;
    public static final int MATERIAL_CLASS_COUNT = 10;
    public static final int SURFACE_CLASS_COUNT = 7;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public VolumeLocalEnvironmentPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("volume-local-environment planVersion must be 1");
        }
        localEnvironmentContractVersion = nonBlank(
                localEnvironmentContractVersion, "localEnvironmentContractVersion", 64);
        if (!LOCAL_ENVIRONMENT_CONTRACT_VERSION.equals(localEnvironmentContractVersion)) {
            throw new IllegalArgumentException("unknown volume-local-environment contract version");
        }
        featureId = qualified(featureId, "featureId");
        Objects.requireNonNull(kernel, "kernel");
        hostBindings = immutable(hostBindings, "hostBindings", MAXIMUM_HOST_BINDINGS).stream()
                .sorted(Comparator.comparing(HostVolumeBinding::featureId)).toList();
        Objects.requireNonNull(catalog, "catalog");
        surfaceProfiles = immutable(surfaceProfiles, "surfaceProfiles", MAXIMUM_SURFACE_PROFILES).stream()
                .sorted(Comparator.comparingInt(SurfaceProfileRule::ruleOrder)).toList();
        sparsePlacements = immutable(sparsePlacements, "sparsePlacements", MAXIMUM_SPARSE_RULES).stream()
                .sorted(Comparator.comparingInt(SparsePlacementRule::ruleOrder)).toList();
        Objects.requireNonNull(materialProfileBinding, "materialProfileBinding");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (hostBindings.isEmpty()) {
            throw new IllegalArgumentException("volume-local-environment requires host bindings");
        }
        if (surfaceProfiles.isEmpty()) {
            throw new IllegalArgumentException("volume-local-environment requires surface profiles");
        }
        validateSurfaceProfiles(surfaceProfiles, catalog);
        validateSparsePlacements(sparsePlacements);
        validateBudget(budget);
    }

    public VolumeLocalEnvironmentPlanV2 withCanonicalChecksum(String checksum) {
        return new VolumeLocalEnvironmentPlanV2(
                planVersion, localEnvironmentContractVersion, featureId, kernel, hostBindings,
                catalog, surfaceProfiles, sparsePlacements, materialProfileBinding, budget, checksum);
    }

    public enum HostVolumeKind {
        LUSH_CAVE,
        SEA_CAVE,
        CAVE_NETWORK,
        SKY_ISLAND_GROUP,
        WATERFALL_VOLUME
    }

    public enum VolumeSurfaceClass {
        FLOOR,
        WALL,
        CEILING,
        UNDERSIDE,
        EXTERIOR_TOP,
        EDGE,
        SUBMERGED
    }

    public enum LocalMaterialClass {
        CAVE_WET_ROCK(1, "material.local.cave-wet-rock"),
        CAVE_DRY_ROCK(2, "material.local.cave-dry-rock"),
        SEA_CAVE_WET_ROCK(3, "material.local.sea-cave-wet-rock"),
        LUSH_MOSS_FLOOR(4, "material.local.lush-moss-floor"),
        LUSH_ROOT_CEILING(5, "material.local.lush-root-ceiling"),
        LUSH_POOL_MARGIN(6, "material.local.lush-pool-margin"),
        SKY_TOP(7, "material.local.sky-top"),
        SKY_EDGE(8, "material.local.sky-edge"),
        SKY_UNDERSIDE(9, "material.local.sky-underside"),
        WATERFALL_WET_ROCK(10, "material.local.waterfall-wet-rock");

        private final int compactCode;
        private final String classId;

        LocalMaterialClass(int compactCode, String classId) {
            this.compactCode = compactCode;
            this.classId = classId;
        }

        public int compactCode() {
            return compactCode;
        }

        public String classId() {
            return classId;
        }
    }

    public enum SparsePlacementKind {
        MOSS,
        ROOT,
        POOL_EDGE
    }

    public enum LightExposureClass {
        OPEN,
        SHADED,
        DEEP
    }

    public record Kernel(
            String kernelVersion,
            int minimumWetnessMillionths,
            int minimumDripMillionths,
            int minimumShadeMillionths,
            int maximumDescriptorSamples,
            int maximumSparsePlacementsPerWindow
    ) {
        public static final String VERSION = "volume-local-environment-v1";

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown volume-local-environment kernel version");
            }
            if (minimumWetnessMillionths < 0 || minimumWetnessMillionths > 1_000_000
                    || minimumDripMillionths < 0 || minimumDripMillionths > 1_000_000
                    || minimumShadeMillionths < 0 || minimumShadeMillionths > 1_000_000
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES
                    || maximumSparsePlacementsPerWindow < 1
                    || maximumSparsePlacementsPerWindow > 4096) {
                throw new IllegalArgumentException("volume-local-environment kernel out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(VERSION, 400_000, 200_000, 300_000, MAXIMUM_DESCRIPTOR_SAMPLES, 256);
        }
    }

    public record HostVolumeBinding(
            HostVolumeKind hostKind,
            String featureId,
            String sourceGeometryChecksum
    ) {
        public HostVolumeBinding {
            Objects.requireNonNull(hostKind, "hostKind");
            featureId = VolumeLocalEnvironmentPlanV2.qualified(featureId, "featureId");
            sourceGeometryChecksum = VolumeLocalEnvironmentPlanV2.checksum(
                    sourceGeometryChecksum, "sourceGeometryChecksum");
        }
    }

    public record Catalog(
            String catalogId,
            String catalogContractVersion,
            List<CatalogEntry> entries
    ) {
        public static final String CATALOG_ID = "landformcraft.builtin-volume-local-material";
        public static final String CONTRACT = "builtin-volume-local-material-catalog-v1";

        public Catalog {
            catalogId = nonBlank(catalogId, "catalogId", 64);
            catalogContractVersion = nonBlank(catalogContractVersion, "catalogContractVersion", 64);
            if (!CATALOG_ID.equals(catalogId) || !CONTRACT.equals(catalogContractVersion)) {
                throw new IllegalArgumentException("unknown volume-local material catalog");
            }
            entries = immutable(entries, "entries", MATERIAL_CLASS_COUNT).stream()
                    .sorted(Comparator.comparingInt(CatalogEntry::classCode)).toList();
            if (entries.size() != MATERIAL_CLASS_COUNT) {
                throw new IllegalArgumentException("volume-local catalog size mismatch");
            }
            Set<Integer> codes = new HashSet<>();
            Set<LocalMaterialClass> kinds = EnumSet.noneOf(LocalMaterialClass.class);
            for (CatalogEntry entry : entries) {
                if (!codes.add(entry.classCode()) || !kinds.add(entry.kind())) {
                    throw new IllegalArgumentException("duplicate volume-local catalog entry");
                }
                if (entry.classCode() != entry.kind().compactCode()
                        || !entry.classId().equals(entry.kind().classId())) {
                    throw new IllegalArgumentException("volume-local catalog entry mismatch");
                }
            }
            if (kinds.size() != MATERIAL_CLASS_COUNT) {
                throw new IllegalArgumentException("volume-local catalog incomplete");
            }
        }

        public CatalogEntry requireByKind(LocalMaterialClass kind) {
            Objects.requireNonNull(kind, "kind");
            return entries.stream()
                    .filter(entry -> entry.kind() == kind)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("missing catalog kind " + kind));
        }

        public static Catalog standard() {
            List<CatalogEntry> entries = Arrays.stream(LocalMaterialClass.values())
                    .map(kind -> new CatalogEntry(kind, kind.compactCode(), kind.classId()))
                    .toList();
            return new Catalog(CATALOG_ID, CONTRACT, entries);
        }
    }

    public record CatalogEntry(LocalMaterialClass kind, int classCode, String classId) {
        public CatalogEntry {
            Objects.requireNonNull(kind, "kind");
            classId = qualified(classId, "classId");
            if (classCode < 1 || classCode > MATERIAL_CLASS_COUNT) {
                throw new IllegalArgumentException("classCode out of range");
            }
        }
    }

    public record SurfaceProfileRule(
            int ruleOrder,
            String ruleId,
            HostVolumeKind hostKind,
            VolumeSurfaceClass surfaceClass,
            LocalMaterialClass materialClass,
            boolean requiresWetness,
            boolean requiresSupport,
            int minimumWetnessMillionths,
            int minimumDripMillionths,
            int minimumShadeMillionths,
            LightExposureClass maximumLightExposure
    ) {
        public SurfaceProfileRule {
            if (ruleOrder < 0 || ruleOrder >= MAXIMUM_SURFACE_PROFILES) {
                throw new IllegalArgumentException("surface profile ruleOrder out of range");
            }
            ruleId = qualified(ruleId, "ruleId");
            Objects.requireNonNull(hostKind, "hostKind");
            Objects.requireNonNull(surfaceClass, "surfaceClass");
            Objects.requireNonNull(materialClass, "materialClass");
            Objects.requireNonNull(maximumLightExposure, "maximumLightExposure");
            if (minimumWetnessMillionths < 0 || minimumWetnessMillionths > 1_000_000
                    || minimumDripMillionths < 0 || minimumDripMillionths > 1_000_000
                    || minimumShadeMillionths < 0 || minimumShadeMillionths > 1_000_000) {
                throw new IllegalArgumentException("surface profile thresholds out of range");
            }
        }
    }

    public record SparsePlacementRule(
            int ruleOrder,
            String ruleId,
            SparsePlacementKind placementKind,
            HostVolumeKind hostKind,
            List<VolumeSurfaceClass> allowedSurfaceClasses,
            boolean requiresWetness,
            boolean requiresSupport,
            int minimumWetnessMillionths
    ) {
        public SparsePlacementRule {
            if (ruleOrder < 0 || ruleOrder >= MAXIMUM_SPARSE_RULES) {
                throw new IllegalArgumentException("sparse placement ruleOrder out of range");
            }
            ruleId = qualified(ruleId, "ruleId");
            Objects.requireNonNull(placementKind, "placementKind");
            Objects.requireNonNull(hostKind, "hostKind");
            allowedSurfaceClasses = List.copyOf(Objects.requireNonNull(
                    allowedSurfaceClasses, "allowedSurfaceClasses"));
            if (allowedSurfaceClasses.isEmpty() || allowedSurfaceClasses.size() > SURFACE_CLASS_COUNT) {
                throw new IllegalArgumentException("allowedSurfaceClasses size invalid");
            }
            Set<VolumeSurfaceClass> seen = EnumSet.noneOf(VolumeSurfaceClass.class);
            for (VolumeSurfaceClass surface : allowedSurfaceClasses) {
                Objects.requireNonNull(surface, "allowedSurfaceClasses element");
                if (!seen.add(surface)) {
                    throw new IllegalArgumentException("duplicate allowed surface class");
                }
            }
            if (minimumWetnessMillionths < 0 || minimumWetnessMillionths > 1_000_000) {
                throw new IllegalArgumentException("sparse placement wetness out of range");
            }
        }
    }

    public record MaterialProfileBinding(
            int bindingVersion,
            String sourceMaterialProfilePlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT = "volume-local-material-profile-binding-v1";

        public MaterialProfileBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("unknown material profile binding version");
            }
            bindingContractVersion = nonBlank(bindingContractVersion, "bindingContractVersion", 64);
            if (!CONTRACT.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown material profile binding contract");
            }
            sourceMaterialProfilePlanChecksum = checksum(
                    sourceMaterialProfilePlanChecksum, "sourceMaterialProfilePlanChecksum");
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes,
            long maximumWorkingBytes,
            int maximumDescriptorSamples,
            int maximumSparsePlacementsPerWindow,
            int hostBindingCount,
            int surfaceProfileCount,
            int sparseRuleCount
    ) {
        public static final String VERSION = "volume-local-environment-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown volume-local-environment budget version");
            }
            if (estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1
                    || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1
                    || maximumDescriptorSamples < 1
                    || maximumDescriptorSamples > MAXIMUM_DESCRIPTOR_SAMPLES
                    || maximumSparsePlacementsPerWindow < 1
                    || maximumSparsePlacementsPerWindow > 4096
                    || hostBindingCount < 1
                    || hostBindingCount > MAXIMUM_HOST_BINDINGS
                    || surfaceProfileCount < 1
                    || surfaceProfileCount > MAXIMUM_SURFACE_PROFILES
                    || sparseRuleCount < 0
                    || sparseRuleCount > MAXIMUM_SPARSE_RULES) {
                throw new IllegalArgumentException("volume-local-environment budget out of range");
            }
        }
    }

    private static void validateSurfaceProfiles(List<SurfaceProfileRule> rules, Catalog catalog) {
        Set<String> ids = new HashSet<>();
        Set<String> hostSurface = new HashSet<>();
        for (SurfaceProfileRule rule : rules) {
            if (!ids.add(rule.ruleId())) {
                throw new IllegalArgumentException("duplicate surface profile ruleId");
            }
            String key = rule.hostKind().name() + ":" + rule.surfaceClass().name();
            if (!hostSurface.add(key)) {
                throw new IllegalArgumentException("duplicate host/surface profile");
            }
            catalog.requireByKind(rule.materialClass());
            if (rule.hostKind() == HostVolumeKind.SKY_ISLAND_GROUP
                    && rule.surfaceClass() == VolumeSurfaceClass.UNDERSIDE
                    && rule.materialClass() != LocalMaterialClass.SKY_UNDERSIDE) {
                throw new IllegalArgumentException("sky underside must map to SKY_UNDERSIDE");
            }
            if (rule.hostKind() == HostVolumeKind.SKY_ISLAND_GROUP
                    && rule.surfaceClass() == VolumeSurfaceClass.EXTERIOR_TOP
                    && rule.materialClass() == LocalMaterialClass.SKY_UNDERSIDE) {
                throw new IllegalArgumentException("sky top must not map to SKY_UNDERSIDE");
            }
        }
    }

    private static void validateSparsePlacements(List<SparsePlacementRule> rules) {
        Set<String> ids = new HashSet<>();
        for (SparsePlacementRule rule : rules) {
            if (!ids.add(rule.ruleId())) {
                throw new IllegalArgumentException("duplicate sparse placement ruleId");
            }
            if (rule.placementKind() == SparsePlacementKind.MOSS
                    && rule.allowedSurfaceClasses().contains(VolumeSurfaceClass.CEILING)
                    && !rule.requiresWetness()) {
                throw new IllegalArgumentException("moss on ceiling requires wetness gate");
            }
            if (rule.placementKind() == SparsePlacementKind.ROOT && !rule.requiresSupport()) {
                throw new IllegalArgumentException("root placement requires support gate");
            }
        }
    }

    private static void validateBudget(ResourceBudget budget) {
        if (budget.maximumDescriptorSamples() > MAXIMUM_DESCRIPTOR_SAMPLES) {
            throw new IllegalArgumentException("volume-local-environment exceeds descriptor budget");
        }
    }

    private static <T> List<T> immutable(List<T> values, String name, int max) {
        Objects.requireNonNull(values, name);
        if (values.size() > max) {
            throw new IllegalArgumentException(name + " exceeds maximum");
        }
        return List.copyOf(values);
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
