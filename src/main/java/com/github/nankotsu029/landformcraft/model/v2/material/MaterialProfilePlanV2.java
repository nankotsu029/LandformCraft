package com.github.nankotsu029.landformcraft.model.v2.material;

import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
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
 * Versioned V2-4-07 semantic material profile contract. It resolves host lithology, hydrology
 * substrate/submerged state, and snow cover into a closed, Minecraft-independent material class
 * catalog through an explicit, fixed-order rule table. No block state, palette, or feature-specific
 * (volcanic/canyon) rule is represented here.
 */
public record MaterialProfilePlanV2(
        int planVersion,
        String profileContractVersion,
        GeologyBinding geologyBinding,
        WaterConditionBinding waterConditionBinding,
        SnowBinding snowBinding,
        Catalog catalog,
        List<ResolutionRule> resolutionRules,
        Kernel kernel,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PROFILE_CONTRACT_VERSION = "material-profile-contract-v1";
    public static final int RESOLUTION_RULE_COUNT = 3;
    public static final long MAX_CANONICAL_BYTES = 32L * 1024L;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public MaterialProfilePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("material-profile planVersion must be 1");
        }
        profileContractVersion = nonBlank(profileContractVersion, "profileContractVersion", 64);
        if (!PROFILE_CONTRACT_VERSION.equals(profileContractVersion)) {
            throw new IllegalArgumentException("unknown material-profile contract version");
        }
        Objects.requireNonNull(geologyBinding, "geologyBinding");
        Objects.requireNonNull(waterConditionBinding, "waterConditionBinding");
        Objects.requireNonNull(snowBinding, "snowBinding");
        Objects.requireNonNull(catalog, "catalog");
        resolutionRules = immutable(resolutionRules, "resolutionRules", RESOLUTION_RULE_COUNT).stream()
                .sorted(Comparator.comparingInt(ResolutionRule::ruleOrder)).toList();
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateResolutionRules(resolutionRules);
        validateBudget(budget);
    }

    public MaterialProfilePlanV2 withCanonicalChecksum(String checksum) {
        return new MaterialProfilePlanV2(
                planVersion, profileContractVersion, geologyBinding, waterConditionBinding, snowBinding,
                catalog, resolutionRules, kernel, budget, checksum);
    }

    /** Fails closed unless this plan exactly binds the frozen geology/lithology/strata checksum chain. */
    public void requireGeologyPlan(
            GeologyPlanV2 geologyPlan,
            LithologyPlanV2 lithologyPlan,
            StrataPlanV2 strataPlan
    ) {
        Objects.requireNonNull(geologyPlan, "geologyPlan");
        Objects.requireNonNull(lithologyPlan, "lithologyPlan");
        Objects.requireNonNull(strataPlan, "strataPlan");
        strataPlan.requireLithologyPlan(geologyPlan, lithologyPlan);
        if (!geologyBinding.sourceGeologyPlanChecksum().equals(geologyPlan.canonicalChecksum())
                || !geologyBinding.sourceLithologyPlanChecksum().equals(lithologyPlan.canonicalChecksum())
                || !geologyBinding.sourceStrataPlanChecksum().equals(strataPlan.canonicalChecksum())) {
            throw new IllegalArgumentException("material-profile geology binding mismatch");
        }
    }

    public void requireWaterConditionPlan(WaterConditionPlanV2 waterConditionPlan) {
        Objects.requireNonNull(waterConditionPlan, "waterConditionPlan");
        if (!waterConditionBinding.sourceWaterConditionPlanChecksum().equals(waterConditionPlan.canonicalChecksum())
                || !waterConditionBinding.wetnessFieldId().equals(WaterConditionBinding.WETNESS_FIELD_ID)) {
            throw new IllegalArgumentException("material-profile water-condition binding mismatch");
        }
        boolean hasWetness = waterConditionPlan.fields().stream().anyMatch(field ->
                field.semantic() == WaterConditionPlanV2.FieldSemantic.WETNESS
                        && field.fieldId().equals(waterConditionBinding.wetnessFieldId()));
        if (!hasWetness) {
            throw new IllegalArgumentException("material-profile requires water-condition wetness field");
        }
    }

    public void requireSnowPlan(SnowPlanV2 snowPlan) {
        Objects.requireNonNull(snowPlan, "snowPlan");
        if (!snowBinding.sourceSnowPlanChecksum().equals(snowPlan.canonicalChecksum())
                || !snowBinding.snowCoverFieldId().equals(SnowBinding.SNOW_COVER_FIELD_ID)) {
            throw new IllegalArgumentException("material-profile snow binding mismatch");
        }
        boolean hasSnowCover = snowPlan.fields().stream().anyMatch(field ->
                field.semantic() == SnowPlanV2.FieldSemantic.SNOW_COVER
                        && field.fieldId().equals(snowBinding.snowCoverFieldId()));
        if (!hasSnowCover) {
            throw new IllegalArgumentException("material-profile requires snow cover field");
        }
    }

    /** The bounded surface/ceiling/floor hook. Wall and underside variants stay reserved for V2-5 volume. */
    public enum SurfaceAspect { SURFACE, CEILING, FLOOR }

    public enum SubstrateCategory { ROCK, SEDIMENT }

    public enum ResolutionRuleId { BASE_SUBSTRATE_FROM_LITHOLOGY, WETNESS_OVERRIDE, SNOW_OVERRIDE }

    /** Explicit merge semantics only; implicit last-write-wins is not a representable value. */
    public enum RuleMergeOperator { BASE_ASSIGNMENT, CONDITIONAL_OVERRIDE }

    public record GeologyBinding(
            int bindingVersion,
            String sourceGeologyPlanChecksum,
            String sourceLithologyPlanChecksum,
            String sourceStrataPlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "material-profile-geology-binding-v1";

        public GeologyBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown material-profile geology binding");
            }
            sourceGeologyPlanChecksum = checksum(sourceGeologyPlanChecksum, "sourceGeologyPlanChecksum");
            sourceLithologyPlanChecksum = checksum(sourceLithologyPlanChecksum, "sourceLithologyPlanChecksum");
            sourceStrataPlanChecksum = checksum(sourceStrataPlanChecksum, "sourceStrataPlanChecksum");
        }
    }

    public record WaterConditionBinding(
            int bindingVersion,
            String sourceWaterConditionPlanChecksum,
            String wetnessFieldId,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "material-profile-water-condition-binding-v1";
        public static final String WETNESS_FIELD_ID = "environment.water.wetness";

        public WaterConditionBinding {
            if (bindingVersion != VERSION
                    || !WETNESS_FIELD_ID.equals(wetnessFieldId)
                    || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown material-profile water-condition binding");
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
        public static final String CONTRACT_VERSION = "material-profile-snow-binding-v1";
        public static final String SNOW_COVER_FIELD_ID = "environment.snow.cover";

        public SnowBinding {
            if (bindingVersion != VERSION
                    || !SNOW_COVER_FIELD_ID.equals(snowCoverFieldId)
                    || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown material-profile snow binding");
            }
            sourceSnowPlanChecksum = checksum(sourceSnowPlanChecksum, "sourceSnowPlanChecksum");
        }
    }

    /** The closed, compile-time semantic material class catalog. No external preset or class is accepted. */
    public enum SemanticMaterialClass {
        HOST_ROCK_EXPOSED("material.host-rock-exposed", 1, SubstrateCategory.ROCK, false, false),
        HOST_ROCK_WET("material.host-rock-wet", 2, SubstrateCategory.ROCK, true, false),
        SEDIMENT_EXPOSED("material.sediment-exposed", 3, SubstrateCategory.SEDIMENT, false, false),
        SEDIMENT_WET("material.sediment-wet", 4, SubstrateCategory.SEDIMENT, true, false),
        SNOW_COVERED_ROCK("material.snow-covered-rock", 5, SubstrateCategory.ROCK, false, true),
        SNOW_COVERED_SEDIMENT("material.snow-covered-sediment", 6, SubstrateCategory.SEDIMENT, false, true);

        private final String classId;
        private final int compactCode;
        private final SubstrateCategory substrate;
        private final boolean wetVariant;
        private final boolean snowVariant;

        SemanticMaterialClass(
                String classId,
                int compactCode,
                SubstrateCategory substrate,
                boolean wetVariant,
                boolean snowVariant
        ) {
            this.classId = classId;
            this.compactCode = compactCode;
            this.substrate = substrate;
            this.wetVariant = wetVariant;
            this.snowVariant = snowVariant;
        }

        public String classId() { return classId; }
        public int compactCode() { return compactCode; }
        public SubstrateCategory substrate() { return substrate; }
        public boolean wetVariant() { return wetVariant; }
        public boolean snowVariant() { return snowVariant; }
    }

    public record Catalog(
            int catalogVersion,
            String catalogId,
            String catalogContractVersion,
            List<Entry> entries,
            CatalogBudget budget
    ) {
        public static final int VERSION = 1;
        public static final String ID = "landformcraft.builtin-material-profile";
        public static final String CONTRACT_VERSION = "builtin-material-profile-catalog-v1";

        public Catalog {
            if (catalogVersion != VERSION) {
                throw new IllegalArgumentException("material-profile catalogVersion must be 1");
            }
            if (!ID.equals(catalogId)) {
                throw new IllegalArgumentException("unknown material-profile catalog ID");
            }
            catalogContractVersion = nonBlank(catalogContractVersion, "catalogContractVersion", 64);
            if (!CONTRACT_VERSION.equals(catalogContractVersion)) {
                throw new IllegalArgumentException("unknown material-profile catalog contract version");
            }
            entries = immutable(entries, "entries", SemanticMaterialClass.values().length).stream()
                    .sorted(Comparator.comparingInt(Entry::classCode)).toList();
            Objects.requireNonNull(budget, "budget");
            validateCatalog(entries, budget);
        }

        public Entry requireByCode(int code) {
            return entries.stream().filter(entry -> entry.classCode() == code).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown material-profile compact code: " + code));
        }

        public Entry requireByKind(SemanticMaterialClass kind) {
            return entries.stream().filter(entry -> entry.kind() == kind).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown material-profile semantic class: " + kind));
        }

        public Entry baseEntry(SubstrateCategory substrate) {
            return requireUnique(substrate, false, false);
        }

        public Entry wetEntry(SubstrateCategory substrate) {
            return requireUnique(substrate, true, false);
        }

        public Entry snowEntry(SubstrateCategory substrate) {
            return requireUnique(substrate, false, true);
        }

        private Entry requireUnique(SubstrateCategory substrate, boolean wet, boolean snow) {
            return entries.stream()
                    .filter(entry -> entry.kind().substrate() == substrate
                            && entry.kind().wetVariant() == wet
                            && entry.kind().snowVariant() == snow)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "no material-profile catalog entry for " + substrate + "/wet=" + wet + "/snow=" + snow));
        }

        private static void validateCatalog(List<Entry> entries, CatalogBudget budget) {
            if (entries.size() != SemanticMaterialClass.values().length
                    || entries.size() > budget.maximumEntries()) {
                throw new IllegalArgumentException("material-profile catalog entry count is invalid");
            }
            Set<SemanticMaterialClass> kinds = EnumSet.noneOf(SemanticMaterialClass.class);
            Set<String> ids = new HashSet<>();
            Set<Integer> codes = new HashSet<>();
            for (Entry entry : entries) {
                if (!kinds.add(entry.kind()) || !ids.add(entry.classId()) || !codes.add(entry.classCode())) {
                    throw new IllegalArgumentException("duplicate material-profile catalog entry");
                }
            }
            if (!kinds.equals(EnumSet.allOf(SemanticMaterialClass.class))) {
                throw new IllegalArgumentException("material-profile catalog is missing a built-in semantic class");
            }
        }
    }

    public record Entry(
            SemanticMaterialClass kind,
            String classId,
            int classCode,
            SubstrateCategory substrate,
            boolean wetVariant,
            boolean snowVariant
    ) {
        public Entry {
            Objects.requireNonNull(kind, "kind");
            classId = qualified(classId, "classId");
            if (!classId.equals(kind.classId()) || classCode != kind.compactCode()
                    || classCode < 1 || classCode > 255
                    || substrate != kind.substrate()
                    || wetVariant != kind.wetVariant()
                    || snowVariant != kind.snowVariant()) {
                throw new IllegalArgumentException("material-profile entry is not the fixed built-in definition");
            }
        }
    }

    public record CatalogBudget(
            String budgetVersion,
            int maximumEntries,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "material-profile-catalog-budget-v1";

        public CatalogBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion) || maximumEntries != SemanticMaterialClass.values().length
                    || maximumCanonicalBytes < 1_024L || maximumCanonicalBytes > 16L * 1024L) {
                throw new IllegalArgumentException("material-profile catalog budget is invalid");
            }
        }
    }

    /**
     * A single explicit, fixed-order resolution rule. The full ordered sequence is frozen by
     * {@link #standardOrder()}; no caller-supplied rule table, threshold expression, or ad-hoc
     * merge operator is accepted.
     */
    public record ResolutionRule(
            int ruleOrder,
            ResolutionRuleId ruleId,
            RuleMergeOperator mergeOperator,
            List<SurfaceAspect> applicableAspects
    ) {
        public ResolutionRule {
            if (ruleOrder < 0 || ruleOrder >= RESOLUTION_RULE_COUNT) {
                throw new IllegalArgumentException("material-profile rule order is out of range");
            }
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(mergeOperator, "mergeOperator");
            applicableAspects = immutable(applicableAspects, "applicableAspects", SurfaceAspect.values().length)
                    .stream().sorted(Comparator.comparing(Enum::name)).toList();
            if (applicableAspects.isEmpty()
                    || applicableAspects.size() != Set.copyOf(applicableAspects).size()) {
                throw new IllegalArgumentException("material-profile rule aspects are invalid");
            }
        }

        /** The single frozen rule table: base assignment, then wetness, then surface-only snow override. */
        public static List<ResolutionRule> standardOrder() {
            List<SurfaceAspect> allAspects = List.of(SurfaceAspect.CEILING, SurfaceAspect.FLOOR, SurfaceAspect.SURFACE);
            return List.of(
                    new ResolutionRule(0, ResolutionRuleId.BASE_SUBSTRATE_FROM_LITHOLOGY,
                            RuleMergeOperator.BASE_ASSIGNMENT, allAspects),
                    new ResolutionRule(1, ResolutionRuleId.WETNESS_OVERRIDE,
                            RuleMergeOperator.CONDITIONAL_OVERRIDE, allAspects),
                    new ResolutionRule(2, ResolutionRuleId.SNOW_OVERRIDE,
                            RuleMergeOperator.CONDITIONAL_OVERRIDE, List.of(SurfaceAspect.SURFACE)));
        }
    }

    public record Kernel(
            String kernelVersion,
            int wetnessThresholdRaw,
            int snowThresholdRaw,
            int minimumRaw,
            int maximumRaw
    ) {
        public static final String KERNEL_VERSION = "material-profile-fixed-v1";

        public Kernel {
            if (!KERNEL_VERSION.equals(kernelVersion)
                    || wetnessThresholdRaw < 0 || wetnessThresholdRaw > 1_000
                    || snowThresholdRaw < 0 || snowThresholdRaw > 1_000
                    || minimumRaw != 0 || maximumRaw != 1_000) {
                throw new IllegalArgumentException("unknown or invalid material-profile kernel");
            }
        }

        public static Kernel standard() {
            return new Kernel(KERNEL_VERSION, 500, 300, 0, 1_000);
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            long globalCellCount,
            int resolutionRuleCount,
            long estimatedCpuWorkUnits,
            long estimatedRetainedBytes,
            int maximumWindowSize,
            long maximumWorkingBytes,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "material-profile-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)
                    || globalCellCount < 1 || globalCellCount > ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS
                    || resolutionRuleCount != RESOLUTION_RULE_COUNT
                    || estimatedCpuWorkUnits < globalCellCount || estimatedCpuWorkUnits > 16_000_000L
                    || estimatedRetainedBytes < 1 || estimatedRetainedBytes > 1L * 1024L * 1024L
                    || maximumWindowSize < 1 || maximumWindowSize > 256
                    || maximumWorkingBytes < 1 || maximumWorkingBytes > 4L * 1024L * 1024L
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES) {
                throw new IllegalArgumentException("material-profile resource budget is outside trusted bounds");
            }
        }
    }

    private static void validateResolutionRules(List<ResolutionRule> rules) {
        List<ResolutionRule> expected = ResolutionRule.standardOrder();
        if (!expected.equals(rules)) {
            throw new IllegalArgumentException("material-profile resolution rules must be the fixed built-in order");
        }
    }

    private static void validateBudget(ResourceBudget budget) {
        long requiredCpu = Math.multiplyExact(budget.globalCellCount(), (long) RESOLUTION_RULE_COUNT);
        if (budget.estimatedCpuWorkUnits() < requiredCpu) {
            throw new IllegalArgumentException("material-profile plan exceeds its declared CPU budget");
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
