package com.github.nankotsu029.landformcraft.model.v2.ecology;

import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-4-11 sparse ecology placement contract. Habitat is a U16 semantic field derived
 * on demand; assemblages are a closed catalog with density/spacing selectors. No dense object
 * grid, entity, block entity, cave ecology, or Paper placement is represented here.
 */
public record EcologyPlanV2(
        int planVersion,
        String placementContractVersion,
        String moduleId,
        String moduleVersion,
        String stageId,
        long namedSeed,
        String seedNamespace,
        int width,
        int length,
        int minY,
        int maxY,
        EcologyPreset ecologyPreset,
        ClimateBinding climateBinding,
        WaterConditionBinding waterConditionBinding,
        SnowBinding snowBinding,
        Catalog catalog,
        List<AssemblageKind> activeAssemblages,
        Kernel kernel,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PLACEMENT_CONTRACT_VERSION = "ecology-placement-contract-v1";
    public static final String MODULE_ID = "v2.environment.ecology";
    public static final String MODULE_VERSION = "0.1.0-v2-4-11";
    public static final String STAGE_ID = "generate.ecology";
    public static final String SEED_NAMESPACE = "terrain.v2.ecology";
    public static final String HABITAT_FIELD_ID = "environment.ecology.habitat";
    public static final long MAX_CANONICAL_BYTES = 48L * 1024L;
    public static final int ASSEMBLAGE_COUNT = 5;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public EcologyPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("ecology planVersion must be 1");
        }
        placementContractVersion = nonBlank(placementContractVersion, "placementContractVersion", 64);
        if (!PLACEMENT_CONTRACT_VERSION.equals(placementContractVersion)) {
            throw new IllegalArgumentException("unknown ecology placement contract version");
        }
        if (!MODULE_ID.equals(moduleId) || !MODULE_VERSION.equals(moduleVersion) || !STAGE_ID.equals(stageId)) {
            throw new IllegalArgumentException("ecology module identity is not the fixed built-in definition");
        }
        if (!SEED_NAMESPACE.equals(seedNamespace)) {
            throw new IllegalArgumentException("ecology seedNamespace must be terrain.v2.ecology");
        }
        if (width < 1 || length < 1 || width > 3_072 || length > 3_072 || minY >= maxY) {
            throw new IllegalArgumentException("ecology dimensions are outside trusted bounds");
        }
        Objects.requireNonNull(ecologyPreset, "ecologyPreset");
        Objects.requireNonNull(climateBinding, "climateBinding");
        Objects.requireNonNull(waterConditionBinding, "waterConditionBinding");
        Objects.requireNonNull(snowBinding, "snowBinding");
        Objects.requireNonNull(catalog, "catalog");
        activeAssemblages = immutable(activeAssemblages, "activeAssemblages", ASSEMBLAGE_COUNT).stream()
                .sorted(Comparator.comparingInt(AssemblageKind::compactCode)).toList();
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateActiveAssemblages(ecologyPreset, activeAssemblages);
        validateBudget(budget, width, length);
    }

    public EcologyPlanV2 withCanonicalChecksum(String checksum) {
        return new EcologyPlanV2(
                planVersion, placementContractVersion, moduleId, moduleVersion, stageId,
                namedSeed, seedNamespace, width, length, minY, maxY, ecologyPreset,
                climateBinding, waterConditionBinding, snowBinding, catalog, activeAssemblages,
                kernel, budget, checksum);
    }

    public void requireClimatePlan(ClimatePlanV2 climatePlan) {
        Objects.requireNonNull(climatePlan, "climatePlan");
        if (!climateBinding.sourceClimatePlanChecksum().equals(climatePlan.canonicalChecksum())
                || !climateBinding.temperatureFieldId().equals(ClimateBinding.TEMPERATURE_FIELD_ID)
                || climatePlan.width() != width
                || climatePlan.length() != length) {
            throw new IllegalArgumentException("ecology climate binding mismatch");
        }
        boolean hasTemperature = climatePlan.fields().stream().anyMatch(field ->
                field.semantic() == ClimatePlanV2.FieldSemantic.FINAL_TEMPERATURE
                        && field.fieldId().equals(climateBinding.temperatureFieldId()));
        if (!hasTemperature) {
            throw new IllegalArgumentException("ecology requires climate final temperature field");
        }
    }

    public void requireWaterConditionPlan(WaterConditionPlanV2 waterConditionPlan) {
        Objects.requireNonNull(waterConditionPlan, "waterConditionPlan");
        if (!waterConditionBinding.sourceWaterConditionPlanChecksum()
                .equals(waterConditionPlan.canonicalChecksum())
                || !waterConditionBinding.wetnessFieldId().equals(WaterConditionBinding.WETNESS_FIELD_ID)
                || !waterConditionBinding.salinityFieldId().equals(WaterConditionBinding.SALINITY_FIELD_ID)
                || !waterConditionBinding.hydroperiodFieldId().equals(WaterConditionBinding.HYDROPERIOD_FIELD_ID)
                || waterConditionPlan.width() != width
                || waterConditionPlan.length() != length) {
            throw new IllegalArgumentException("ecology water-condition binding mismatch");
        }
        EnumSet<WaterConditionPlanV2.FieldSemantic> required = EnumSet.of(
                WaterConditionPlanV2.FieldSemantic.WETNESS,
                WaterConditionPlanV2.FieldSemantic.SALINITY,
                WaterConditionPlanV2.FieldSemantic.HYDROPERIOD);
        EnumSet<WaterConditionPlanV2.FieldSemantic> present = EnumSet.noneOf(
                WaterConditionPlanV2.FieldSemantic.class);
        for (WaterConditionPlanV2.FieldBinding field : waterConditionPlan.fields()) {
            if (required.contains(field.semantic())) {
                present.add(field.semantic());
            }
        }
        if (!present.equals(required)) {
            throw new IllegalArgumentException("ecology requires wetness/salinity/hydroperiod fields");
        }
    }

    public void requireSnowPlan(SnowPlanV2 snowPlan) {
        Objects.requireNonNull(snowPlan, "snowPlan");
        if (!snowBinding.sourceSnowPlanChecksum().equals(snowPlan.canonicalChecksum())
                || !snowBinding.snowCoverFieldId().equals(SnowBinding.SNOW_COVER_FIELD_ID)
                || snowPlan.width() != width
                || snowPlan.length() != length) {
            throw new IllegalArgumentException("ecology snow binding mismatch");
        }
        boolean hasSnowCover = snowPlan.fields().stream().anyMatch(field ->
                field.semantic() == SnowPlanV2.FieldSemantic.SNOW_COVER
                        && field.fieldId().equals(snowBinding.snowCoverFieldId()));
        if (!hasSnowCover) {
            throw new IllegalArgumentException("ecology requires snow cover field");
        }
    }

    /** Closed ecologyPreset symbols accepted by the compiler. External scripts/presets are rejected. */
    public enum EcologyPreset {
        MANGROVE_ESTUARY(List.of(AssemblageKind.MANGROVE_CANOPY, AssemblageKind.MANGROVE_ROOT)),
        SHALLOW_CORAL_REEF(List.of(AssemblageKind.CORAL_COLONY)),
        ALPINE_TREELINE(List.of(AssemblageKind.ALPINE_SHRUB, AssemblageKind.ALPINE_MEADOW)),
        SPARSE_COASTAL(List.of()),
        SUBALPINE_FJORD(List.of(AssemblageKind.ALPINE_SHRUB)),
        DELTA_MARSH(List.of(AssemblageKind.MANGROVE_CANOPY)),
        RIVER_CORRIDOR(List.of()),
        LAKE_BASIN(List.of()),
        VOLCANIC_SUCCESSION(List.of()),
        WIND_EXPOSED_CLIFF(List.of()),
        WIND_EXPOSED_SKY_MEADOW(List.of(AssemblageKind.ALPINE_MEADOW)),
        RIPARIAN_CANYON(List.of()),
        LUSH_SUBTERRANEAN(List.of());

        private final List<AssemblageKind> assemblages;

        EcologyPreset(List<AssemblageKind> assemblages) {
            this.assemblages = List.copyOf(assemblages);
        }

        public List<AssemblageKind> assemblages() {
            return assemblages;
        }
    }

    public enum HabitatClass {
        NONE(0, "ecology.habitat.none"),
        MANGROVE_WETLAND(1, "ecology.habitat.mangrove-wetland"),
        CORAL_REEF(2, "ecology.habitat.coral-reef"),
        ALPINE_VEGETATION(3, "ecology.habitat.alpine-vegetation");

        private final int compactCode;
        private final String habitatId;

        HabitatClass(int compactCode, String habitatId) {
            this.compactCode = compactCode;
            this.habitatId = habitatId;
        }

        public int compactCode() {
            return compactCode;
        }

        public String habitatId() {
            return habitatId;
        }

        public static HabitatClass requireByCode(int code) {
            for (HabitatClass habitat : values()) {
                if (habitat.compactCode == code) {
                    return habitat;
                }
            }
            throw new IllegalArgumentException("unknown ecology habitat compact code: " + code);
        }
    }

    public enum PlacementLayer { CANOPY, ROOT, UNDERSTORY, GROUND, AQUATIC }

    public enum SupportRule {
        WETLAND_CANOPY,
        WETLAND_ROOT,
        REEF_COLONY,
        ALPINE_SHRUB,
        ALPINE_MEADOW
    }

    /**
     * Closed assemblage catalog. Density/spacing are millionths and block counts frozen here —
     * callers cannot inject external scripts or arbitrary density expressions.
     */
    public enum AssemblageKind {
        MANGROVE_CANOPY(
                "ecology.mangrove-canopy", 1, HabitatClass.MANGROVE_WETLAND, PlacementLayer.CANOPY,
                SupportRule.WETLAND_CANOPY, 80_000, 4, 8),
        MANGROVE_ROOT(
                "ecology.mangrove-root", 2, HabitatClass.MANGROVE_WETLAND, PlacementLayer.ROOT,
                SupportRule.WETLAND_ROOT, 120_000, 2, 4),
        CORAL_COLONY(
                "ecology.coral-colony", 3, HabitatClass.CORAL_REEF, PlacementLayer.AQUATIC,
                SupportRule.REEF_COLONY, 100_000, 3, 6),
        ALPINE_SHRUB(
                "ecology.alpine-shrub", 4, HabitatClass.ALPINE_VEGETATION, PlacementLayer.UNDERSTORY,
                SupportRule.ALPINE_SHRUB, 60_000, 5, 10),
        ALPINE_MEADOW(
                "ecology.alpine-meadow", 5, HabitatClass.ALPINE_VEGETATION, PlacementLayer.GROUND,
                SupportRule.ALPINE_MEADOW, 200_000, 2, 4);

        private final String assemblageId;
        private final int compactCode;
        private final HabitatClass habitatClass;
        private final PlacementLayer layer;
        private final SupportRule supportRule;
        private final int densityMillionths;
        private final int minSpacingBlocks;
        private final int clusterScaleBlocks;

        AssemblageKind(
                String assemblageId,
                int compactCode,
                HabitatClass habitatClass,
                PlacementLayer layer,
                SupportRule supportRule,
                int densityMillionths,
                int minSpacingBlocks,
                int clusterScaleBlocks
        ) {
            this.assemblageId = assemblageId;
            this.compactCode = compactCode;
            this.habitatClass = habitatClass;
            this.layer = layer;
            this.supportRule = supportRule;
            this.densityMillionths = densityMillionths;
            this.minSpacingBlocks = minSpacingBlocks;
            this.clusterScaleBlocks = clusterScaleBlocks;
        }

        public String assemblageId() {
            return assemblageId;
        }

        public int compactCode() {
            return compactCode;
        }

        public HabitatClass habitatClass() {
            return habitatClass;
        }

        public PlacementLayer layer() {
            return layer;
        }

        public SupportRule supportRule() {
            return supportRule;
        }

        public int densityMillionths() {
            return densityMillionths;
        }

        public int minSpacingBlocks() {
            return minSpacingBlocks;
        }

        public int clusterScaleBlocks() {
            return clusterScaleBlocks;
        }
    }

    public record ClimateBinding(
            int bindingVersion,
            String sourceClimatePlanChecksum,
            String temperatureFieldId,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "ecology-climate-binding-v1";
        public static final String TEMPERATURE_FIELD_ID = "climate.final.temperature";

        public ClimateBinding {
            if (bindingVersion != VERSION
                    || !TEMPERATURE_FIELD_ID.equals(temperatureFieldId)
                    || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown ecology climate binding");
            }
            sourceClimatePlanChecksum = checksum(sourceClimatePlanChecksum, "sourceClimatePlanChecksum");
        }
    }

    public record WaterConditionBinding(
            int bindingVersion,
            String sourceWaterConditionPlanChecksum,
            String wetnessFieldId,
            String salinityFieldId,
            String hydroperiodFieldId,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "ecology-water-condition-binding-v1";
        public static final String WETNESS_FIELD_ID = "environment.water.wetness";
        public static final String SALINITY_FIELD_ID = "environment.water.salinity";
        public static final String HYDROPERIOD_FIELD_ID = "environment.water.hydroperiod";

        public WaterConditionBinding {
            if (bindingVersion != VERSION
                    || !WETNESS_FIELD_ID.equals(wetnessFieldId)
                    || !SALINITY_FIELD_ID.equals(salinityFieldId)
                    || !HYDROPERIOD_FIELD_ID.equals(hydroperiodFieldId)
                    || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown ecology water-condition binding");
            }
            sourceWaterConditionPlanChecksum = checksum(
                    sourceWaterConditionPlanChecksum, "sourceWaterConditionPlanChecksum");
        }
    }

    public record SnowBinding(
            int bindingVersion,
            String sourceSnowPlanChecksum,
            String snowCoverFieldId,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "ecology-snow-binding-v1";
        public static final String SNOW_COVER_FIELD_ID = "environment.snow.cover";

        public SnowBinding {
            if (bindingVersion != VERSION
                    || !SNOW_COVER_FIELD_ID.equals(snowCoverFieldId)
                    || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown ecology snow binding");
            }
            sourceSnowPlanChecksum = checksum(sourceSnowPlanChecksum, "sourceSnowPlanChecksum");
        }
    }

    public record Catalog(
            int catalogVersion,
            String catalogId,
            String catalogContractVersion,
            List<Entry> entries,
            CatalogBudget budget
    ) {
        public static final int VERSION = 1;
        public static final String ID = "landformcraft.builtin-ecology-assemblage";
        public static final String CONTRACT_VERSION = "builtin-ecology-assemblage-catalog-v1";

        public Catalog {
            if (catalogVersion != VERSION) {
                throw new IllegalArgumentException("ecology catalogVersion must be 1");
            }
            if (!ID.equals(catalogId)) {
                throw new IllegalArgumentException("unknown ecology catalog ID");
            }
            catalogContractVersion = nonBlank(catalogContractVersion, "catalogContractVersion", 64);
            if (!CONTRACT_VERSION.equals(catalogContractVersion)) {
                throw new IllegalArgumentException("unknown ecology catalog contract version");
            }
            entries = immutable(entries, "entries", ASSEMBLAGE_COUNT).stream()
                    .sorted(Comparator.comparingInt(Entry::compactCode)).toList();
            Objects.requireNonNull(budget, "budget");
            validateCatalog(entries, budget);
        }

        public Entry requireByKind(AssemblageKind kind) {
            return entries.stream().filter(entry -> entry.kind() == kind).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown ecology assemblage: " + kind));
        }

        public Entry requireById(String assemblageId) {
            return entries.stream().filter(entry -> entry.assemblageId().equals(assemblageId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown ecology assemblage id: " + assemblageId));
        }

        private static void validateCatalog(List<Entry> entries, CatalogBudget budget) {
            if (entries.size() != ASSEMBLAGE_COUNT || entries.size() > budget.maximumEntries()) {
                throw new IllegalArgumentException("ecology catalog entry count is invalid");
            }
            Set<AssemblageKind> kinds = EnumSet.noneOf(AssemblageKind.class);
            Set<String> ids = new HashSet<>();
            Set<Integer> codes = new HashSet<>();
            for (Entry entry : entries) {
                if (!kinds.add(entry.kind()) || !ids.add(entry.assemblageId()) || !codes.add(entry.compactCode())) {
                    throw new IllegalArgumentException("duplicate ecology catalog entry");
                }
            }
            if (!kinds.equals(EnumSet.allOf(AssemblageKind.class))) {
                throw new IllegalArgumentException("ecology catalog is missing a built-in assemblage");
            }
        }
    }

    public record Entry(
            AssemblageKind kind,
            String assemblageId,
            int compactCode,
            HabitatClass habitatClass,
            PlacementLayer layer,
            SupportRule supportRule,
            int densityMillionths,
            int minSpacingBlocks,
            int clusterScaleBlocks
    ) {
        public Entry {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(habitatClass, "habitatClass");
            Objects.requireNonNull(layer, "layer");
            Objects.requireNonNull(supportRule, "supportRule");
            assemblageId = qualified(assemblageId, "assemblageId");
            if (!assemblageId.equals(kind.assemblageId())
                    || compactCode != kind.compactCode()
                    || habitatClass != kind.habitatClass()
                    || layer != kind.layer()
                    || supportRule != kind.supportRule()
                    || densityMillionths != kind.densityMillionths()
                    || minSpacingBlocks != kind.minSpacingBlocks()
                    || clusterScaleBlocks != kind.clusterScaleBlocks()
                    || compactCode < 1 || compactCode > 255
                    || densityMillionths < 1 || densityMillionths > 1_000_000
                    || minSpacingBlocks < 1 || minSpacingBlocks > 64
                    || clusterScaleBlocks < minSpacingBlocks || clusterScaleBlocks > 128) {
                throw new IllegalArgumentException("ecology entry is not the fixed built-in definition");
            }
        }
    }

    public record CatalogBudget(
            String budgetVersion,
            int maximumEntries,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "ecology-catalog-budget-v1";

        public CatalogBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)
                    || maximumEntries != ASSEMBLAGE_COUNT
                    || maximumCanonicalBytes < 1_024L
                    || maximumCanonicalBytes > 16L * 1024L) {
                throw new IllegalArgumentException("ecology catalog budget is invalid");
            }
        }
    }

    public record Kernel(
            String kernelVersion,
            int mangroveMinTemperatureRaw,
            int mangroveMinSalinityRaw,
            int mangroveMaxSalinityRaw,
            int mangroveMinHydroperiodRaw,
            int mangroveMinWetnessRaw,
            int coralMinTemperatureRaw,
            int coralMinSalinityRaw,
            int coralMinDepthRaw,
            int coralMaxDepthRaw,
            int alpineMaxTemperatureRaw,
            int alpineMaxSnowCoverRaw,
            int alpineMinSurfaceY,
            int alpineMaxSurfaceY,
            int alpineShrubMaxSurfaceY,
            int minimumRaw,
            int maximumRaw
    ) {
        public static final String KERNEL_VERSION = "ecology-placement-fixed-v1";

        public Kernel {
            if (!KERNEL_VERSION.equals(kernelVersion)
                    || mangroveMinTemperatureRaw < 0 || mangroveMinTemperatureRaw > 1_000
                    || mangroveMinSalinityRaw < 0 || mangroveMinSalinityRaw > 1_000
                    || mangroveMaxSalinityRaw < mangroveMinSalinityRaw || mangroveMaxSalinityRaw > 1_000
                    || mangroveMinHydroperiodRaw < 0 || mangroveMinHydroperiodRaw > 1_000
                    || mangroveMinWetnessRaw < 0 || mangroveMinWetnessRaw > 1_000
                    || coralMinTemperatureRaw < 0 || coralMinTemperatureRaw > 1_000
                    || coralMinSalinityRaw < 0 || coralMinSalinityRaw > 1_000
                    || coralMinDepthRaw < 0 || coralMinDepthRaw > 1_000
                    || coralMaxDepthRaw < coralMinDepthRaw || coralMaxDepthRaw > 1_000
                    || alpineMaxTemperatureRaw < 0 || alpineMaxTemperatureRaw > 1_000
                    || alpineMaxSnowCoverRaw < 0 || alpineMaxSnowCoverRaw > 1_000
                    || alpineMinSurfaceY > alpineShrubMaxSurfaceY
                    || alpineShrubMaxSurfaceY > alpineMaxSurfaceY
                    || minimumRaw != 0 || maximumRaw != 1_000) {
                throw new IllegalArgumentException("unknown or invalid ecology kernel");
            }
        }

        public static Kernel standard() {
            return new Kernel(
                    KERNEL_VERSION,
                    550, 200, 700, 300, 400,
                    600, 700, 50, 400,
                    350, 700, 120, 220, 170,
                    0, 1_000);
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            long globalCellCount,
            int assemblageCount,
            int activeAssemblageCount,
            long estimatedCpuWorkUnits,
            long estimatedRetainedBytes,
            int maximumWindowSize,
            long maximumWorkingBytes,
            long maximumPlacementDescriptorsPerWindow,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "ecology-placement-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)
                    || globalCellCount < 1 || globalCellCount > ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS
                    || assemblageCount != ASSEMBLAGE_COUNT
                    || activeAssemblageCount < 0 || activeAssemblageCount > ASSEMBLAGE_COUNT
                    || estimatedCpuWorkUnits < globalCellCount || estimatedCpuWorkUnits > 32_000_000L
                    || estimatedRetainedBytes < 1 || estimatedRetainedBytes > 1L * 1024L * 1024L
                    || maximumWindowSize < 1 || maximumWindowSize > 256
                    || maximumWorkingBytes < 1 || maximumWorkingBytes > 4L * 1024L * 1024L
                    || maximumPlacementDescriptorsPerWindow < 1
                    || maximumPlacementDescriptorsPerWindow > 65_536L
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES) {
                throw new IllegalArgumentException("ecology resource budget is outside trusted bounds");
            }
        }
    }

    private static void validateActiveAssemblages(EcologyPreset preset, List<AssemblageKind> active) {
        if (!preset.assemblages().equals(active)) {
            throw new IllegalArgumentException("ecology active assemblages must match the closed preset");
        }
    }

    private static void validateBudget(ResourceBudget budget, int width, int length) {
        long cells = Math.multiplyExact((long) width, length);
        if (budget.globalCellCount() != cells) {
            throw new IllegalArgumentException("ecology budget cell count must match dimensions");
        }
        long requiredCpu = Math.multiplyExact(cells, (long) Math.max(1, budget.activeAssemblageCount()));
        if (budget.estimatedCpuWorkUnits() < requiredCpu) {
            throw new IllegalArgumentException("ecology plan exceeds its declared CPU budget");
        }
    }

    private static <T> List<T> immutable(List<T> values, String name, int maximum) {
        Objects.requireNonNull(values, name);
        if (values.size() > maximum || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " is invalid or exceeds " + maximum);
        }
        return List.copyOf(values);
    }

    private static String qualified(String value, String name) {
        value = nonBlank(value, name, 128);
        if (!QUALIFIED.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase qualified ID");
        }
        return value;
    }

    private static String checksum(String value, String name) {
        value = nonBlank(value, name, 64);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256");
        }
        return value;
    }

    private static String nonBlank(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must be non-blank and bounded");
        }
        return value;
    }
}
