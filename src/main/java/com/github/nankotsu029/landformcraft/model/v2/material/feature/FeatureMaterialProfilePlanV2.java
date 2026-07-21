package com.github.nankotsu029.landformcraft.model.v2.material.feature;

import com.github.nankotsu029.landformcraft.model.v2.CanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-4-12 feature material overlay. Resolves volcanic basalt/tuff/ash and canyon
 * strata/talus/sediment zones on top of the sealed V2-4-07 base material profile without
 * mutating shape generators, Minecraft palette, or dense 3D arrays.
 */
public record FeatureMaterialProfilePlanV2(
        int planVersion,
        String profileContractVersion,
        MaterialProfileBinding materialProfileBinding,
        GeologyBinding geologyBinding,
        List<FeatureGeometryBinding> volcanicBindings,
        List<FeatureGeometryBinding> canyonBindings,
        Catalog catalog,
        List<ResolutionRule> resolutionRules,
        List<ConflictRule> conflictRules,
        Kernel kernel,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PROFILE_CONTRACT_VERSION = "feature-material-profile-contract-v1";
    public static final int RESOLUTION_RULE_COUNT = 4;
    public static final int CONFLICT_RULE_COUNT = 2;
    public static final int FEATURE_CLASS_COUNT = 6;
    public static final long MAX_CANONICAL_BYTES = 48L * 1024L;
    public static final int MAX_FEATURE_BINDINGS = 16;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public FeatureMaterialProfilePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("feature-material planVersion must be 1");
        }
        profileContractVersion = nonBlank(profileContractVersion, "profileContractVersion", 64);
        if (!PROFILE_CONTRACT_VERSION.equals(profileContractVersion)) {
            throw new IllegalArgumentException("unknown feature-material profile contract version");
        }
        Objects.requireNonNull(materialProfileBinding, "materialProfileBinding");
        Objects.requireNonNull(geologyBinding, "geologyBinding");
        volcanicBindings = immutable(volcanicBindings, "volcanicBindings", MAX_FEATURE_BINDINGS).stream()
                .sorted(Comparator.comparing(FeatureGeometryBinding::featureId)).toList();
        canyonBindings = immutable(canyonBindings, "canyonBindings", MAX_FEATURE_BINDINGS).stream()
                .sorted(Comparator.comparing(FeatureGeometryBinding::featureId)).toList();
        Objects.requireNonNull(catalog, "catalog");
        resolutionRules = immutable(resolutionRules, "resolutionRules", RESOLUTION_RULE_COUNT).stream()
                .sorted(Comparator.comparingInt(ResolutionRule::ruleOrder)).toList();
        conflictRules = immutable(conflictRules, "conflictRules", CONFLICT_RULE_COUNT).stream()
                .sorted(Comparator.comparingInt(ConflictRule::ruleOrder)).toList();
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateResolutionRules(resolutionRules);
        validateConflictRules(conflictRules);
        validateBudget(budget);
        validateBindingKinds(volcanicBindings, FeatureKind.VOLCANIC);
        validateBindingKinds(canyonBindings, FeatureKind.CANYON);
    }

    public FeatureMaterialProfilePlanV2 withCanonicalChecksum(String checksum) {
        return new FeatureMaterialProfilePlanV2(
                planVersion, profileContractVersion, materialProfileBinding, geologyBinding,
                volcanicBindings, canyonBindings, catalog, resolutionRules, conflictRules,
                kernel, budget, checksum);
    }

    public void requireMaterialProfilePlan(MaterialProfilePlanV2 materialProfilePlan) {
        Objects.requireNonNull(materialProfilePlan, "materialProfilePlan");
        if (!materialProfileBinding.sourceMaterialProfilePlanChecksum()
                .equals(materialProfilePlan.canonicalChecksum())) {
            throw new IllegalArgumentException("feature-material material-profile binding mismatch");
        }
    }

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
            throw new IllegalArgumentException("feature-material geology binding mismatch");
        }
    }

    public void requireVolcanicPlan(VolcanicPlanV2 volcanicPlan) {
        Objects.requireNonNull(volcanicPlan, "volcanicPlan");
        FeatureGeometryBinding binding = volcanicBindings.stream()
                .filter(entry -> entry.featureId().equals(volcanicPlan.featureId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "feature-material missing volcanic binding: " + volcanicPlan.featureId()));
        if (!binding.geometryChecksum().equals(volcanicPlan.geometryChecksum())
                || binding.featureKind() != FeatureKind.VOLCANIC) {
            throw new IllegalArgumentException("feature-material volcanic binding mismatch");
        }
    }

    public void requireCanyonPlan(CanyonPlanV2 canyonPlan) {
        Objects.requireNonNull(canyonPlan, "canyonPlan");
        FeatureGeometryBinding binding = canyonBindings.stream()
                .filter(entry -> entry.featureId().equals(canyonPlan.featureId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "feature-material missing canyon binding: " + canyonPlan.featureId()));
        if (!binding.geometryChecksum().equals(canyonPlan.geometryChecksum())
                || binding.featureKind() != FeatureKind.CANYON) {
            throw new IllegalArgumentException("feature-material canyon binding mismatch");
        }
    }

    public enum FeatureKind { VOLCANIC, CANYON }

    public enum FeatureSemanticMaterialClass {
        VOLCANIC_BASALT_EXPOSED("material.feature.volcanic-basalt-exposed", 7, FeatureKind.VOLCANIC),
        VOLCANIC_TUFF_EXPOSED("material.feature.volcanic-tuff-exposed", 8, FeatureKind.VOLCANIC),
        VOLCANIC_ASH_EXPOSED("material.feature.volcanic-ash-exposed", 9, FeatureKind.VOLCANIC),
        CANYON_STRATA_EXPOSED("material.feature.canyon-strata-exposed", 10, FeatureKind.CANYON),
        CANYON_TALUS("material.feature.canyon-talus", 11, FeatureKind.CANYON),
        CANYON_FLOOR_SEDIMENT("material.feature.canyon-floor-sediment", 12, FeatureKind.CANYON);

        private final String classId;
        private final int compactCode;
        private final FeatureKind featureKind;

        FeatureSemanticMaterialClass(String classId, int compactCode, FeatureKind featureKind) {
            this.classId = classId;
            this.compactCode = compactCode;
            this.featureKind = featureKind;
        }

        public String classId() {
            return classId;
        }

        public int compactCode() {
            return compactCode;
        }

        public FeatureKind featureKind() {
            return featureKind;
        }
    }

    public enum ResolutionRuleId {
        BASE_FROM_MATERIAL_PROFILE,
        VOLCANIC_ZONE_OVERRIDE,
        CANYON_ZONE_OVERRIDE,
        FEATURE_WETNESS_HINT
    }

    public enum RuleMergeOperator { BASE_ASSIGNMENT, CONDITIONAL_OVERRIDE }

    public enum ConflictRuleId {
        CANYON_WINS_OVER_VOLCANIC,
        REJECT_UNKNOWN_FEATURE_CLAIM
    }

    public enum ConflictMergeOperator { PRIORITY_OVERRIDE, REJECT }

    public record MaterialProfileBinding(
            int bindingVersion,
            String sourceMaterialProfilePlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "feature-material-material-profile-binding-v1";

        public MaterialProfileBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown feature-material material-profile binding");
            }
            sourceMaterialProfilePlanChecksum = checksum(
                    sourceMaterialProfilePlanChecksum, "sourceMaterialProfilePlanChecksum");
        }
    }

    public record GeologyBinding(
            int bindingVersion,
            String sourceGeologyPlanChecksum,
            String sourceLithologyPlanChecksum,
            String sourceStrataPlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "feature-material-geology-binding-v1";

        public GeologyBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown feature-material geology binding");
            }
            sourceGeologyPlanChecksum = checksum(sourceGeologyPlanChecksum, "sourceGeologyPlanChecksum");
            sourceLithologyPlanChecksum = checksum(sourceLithologyPlanChecksum, "sourceLithologyPlanChecksum");
            sourceStrataPlanChecksum = checksum(sourceStrataPlanChecksum, "sourceStrataPlanChecksum");
        }
    }

    public record FeatureGeometryBinding(
            FeatureKind featureKind,
            String featureId,
            String geometryChecksum
    ) {
        public FeatureGeometryBinding {
            Objects.requireNonNull(featureKind, "featureKind");
            featureId = slug(featureId, "featureId");
            geometryChecksum = checksum(geometryChecksum, "geometryChecksum");
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
        public static final String ID = "landformcraft.builtin-feature-material-profile";
        public static final String CONTRACT_VERSION = "builtin-feature-material-profile-catalog-v1";

        public Catalog {
            if (catalogVersion != VERSION || !ID.equals(catalogId)) {
                throw new IllegalArgumentException("unknown feature-material catalog");
            }
            catalogContractVersion = nonBlank(catalogContractVersion, "catalogContractVersion", 64);
            if (!CONTRACT_VERSION.equals(catalogContractVersion)) {
                throw new IllegalArgumentException("unknown feature-material catalog contract version");
            }
            entries = immutable(entries, "entries", FEATURE_CLASS_COUNT).stream()
                    .sorted(Comparator.comparingInt(Entry::classCode)).toList();
            Objects.requireNonNull(budget, "budget");
            validateCatalog(entries, budget);
        }

        public Entry requireByKind(FeatureSemanticMaterialClass kind) {
            return entries.stream().filter(entry -> entry.kind() == kind).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown feature-material semantic class: " + kind));
        }

        public Entry requireByCode(int code) {
            return entries.stream().filter(entry -> entry.classCode() == code).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown feature-material compact code: " + code));
        }

        private static void validateCatalog(List<Entry> entries, CatalogBudget budget) {
            if (entries.size() != FEATURE_CLASS_COUNT || entries.size() > budget.maximumEntries()) {
                throw new IllegalArgumentException("feature-material catalog entry count is invalid");
            }
            Set<FeatureSemanticMaterialClass> kinds = EnumSet.noneOf(FeatureSemanticMaterialClass.class);
            Set<String> ids = new HashSet<>();
            Set<Integer> codes = new HashSet<>();
            for (Entry entry : entries) {
                if (!kinds.add(entry.kind()) || !ids.add(entry.classId()) || !codes.add(entry.classCode())) {
                    throw new IllegalArgumentException("duplicate feature-material catalog entry");
                }
            }
            if (!kinds.equals(EnumSet.allOf(FeatureSemanticMaterialClass.class))) {
                throw new IllegalArgumentException("feature-material catalog is missing a built-in class");
            }
        }
    }

    public record Entry(
            FeatureSemanticMaterialClass kind,
            String classId,
            int classCode,
            FeatureKind featureKind
    ) {
        public Entry {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(featureKind, "featureKind");
            classId = qualified(classId, "classId");
            if (!classId.equals(kind.classId())
                    || classCode != kind.compactCode()
                    || featureKind != kind.featureKind()
                    || classCode < 7 || classCode > 12) {
                throw new IllegalArgumentException("feature-material entry is not the fixed built-in definition");
            }
        }
    }

    public record CatalogBudget(
            String budgetVersion,
            int maximumEntries,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "feature-material-catalog-budget-v1";

        public CatalogBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)
                    || maximumEntries != FEATURE_CLASS_COUNT
                    || maximumCanonicalBytes < 1_024L
                    || maximumCanonicalBytes > 16L * 1024L) {
                throw new IllegalArgumentException("feature-material catalog budget is invalid");
            }
        }
    }

    public record ResolutionRule(
            int ruleOrder,
            ResolutionRuleId ruleId,
            RuleMergeOperator mergeOperator,
            List<FeatureKind> applicableFeatureKinds
    ) {
        public ResolutionRule {
            if (ruleOrder < 0 || ruleOrder >= RESOLUTION_RULE_COUNT) {
                throw new IllegalArgumentException("feature-material rule order is out of range");
            }
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(mergeOperator, "mergeOperator");
            applicableFeatureKinds = immutable(
                    applicableFeatureKinds, "applicableFeatureKinds", FeatureKind.values().length)
                    .stream().sorted(Comparator.comparing(Enum::name)).toList();
            if (applicableFeatureKinds.size() != Set.copyOf(applicableFeatureKinds).size()) {
                throw new IllegalArgumentException("feature-material rule feature kinds are invalid");
            }
        }

        public static List<ResolutionRule> standardOrder() {
            return List.of(
                    new ResolutionRule(0, ResolutionRuleId.BASE_FROM_MATERIAL_PROFILE,
                            RuleMergeOperator.BASE_ASSIGNMENT, List.of()),
                    new ResolutionRule(1, ResolutionRuleId.VOLCANIC_ZONE_OVERRIDE,
                            RuleMergeOperator.CONDITIONAL_OVERRIDE, List.of(FeatureKind.VOLCANIC)),
                    new ResolutionRule(2, ResolutionRuleId.CANYON_ZONE_OVERRIDE,
                            RuleMergeOperator.CONDITIONAL_OVERRIDE, List.of(FeatureKind.CANYON)),
                    new ResolutionRule(3, ResolutionRuleId.FEATURE_WETNESS_HINT,
                            RuleMergeOperator.CONDITIONAL_OVERRIDE,
                            List.of(FeatureKind.CANYON, FeatureKind.VOLCANIC)));
        }
    }

    public record ConflictRule(
            int ruleOrder,
            ConflictRuleId ruleId,
            ConflictMergeOperator mergeOperator,
            FeatureKind winner,
            FeatureKind loser
    ) {
        public ConflictRule {
            if (ruleOrder < 0 || ruleOrder >= CONFLICT_RULE_COUNT) {
                throw new IllegalArgumentException("feature-material conflict rule order is out of range");
            }
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(mergeOperator, "mergeOperator");
            Objects.requireNonNull(winner, "winner");
            Objects.requireNonNull(loser, "loser");
            if (winner == loser) {
                throw new IllegalArgumentException("feature-material conflict winner and loser must differ");
            }
        }

        public static List<ConflictRule> standardOrder() {
            return List.of(
                    new ConflictRule(0, ConflictRuleId.CANYON_WINS_OVER_VOLCANIC,
                            ConflictMergeOperator.PRIORITY_OVERRIDE,
                            FeatureKind.CANYON, FeatureKind.VOLCANIC),
                    new ConflictRule(1, ConflictRuleId.REJECT_UNKNOWN_FEATURE_CLAIM,
                            ConflictMergeOperator.REJECT,
                            FeatureKind.CANYON, FeatureKind.VOLCANIC));
        }
    }

    public record Kernel(
            String kernelVersion,
            int volcanicBasaltMinReliefMillionths,
            int volcanicTuffMinReliefMillionths,
            int volcanicAshMaxReliefMillionths,
            int volcanicShoreBandBlocks,
            int canyonTalusMaxWallHeightMillionths,
            int featureWetnessThresholdRaw,
            int minimumRaw,
            int maximumRaw
    ) {
        public static final String KERNEL_VERSION = "feature-material-profile-fixed-v1";

        public Kernel {
            if (!KERNEL_VERSION.equals(kernelVersion)
                    || volcanicBasaltMinReliefMillionths < volcanicTuffMinReliefMillionths
                    || volcanicTuffMinReliefMillionths < 0
                    || volcanicAshMaxReliefMillionths < 0
                    || volcanicAshMaxReliefMillionths > volcanicTuffMinReliefMillionths
                    || volcanicShoreBandBlocks < 1 || volcanicShoreBandBlocks > 32
                    || canyonTalusMaxWallHeightMillionths < 1
                    || featureWetnessThresholdRaw < 0 || featureWetnessThresholdRaw > 1_000
                    || minimumRaw != 0 || maximumRaw != 1_000) {
                throw new IllegalArgumentException("unknown or invalid feature-material kernel");
            }
        }

        public static Kernel standard() {
            int scale = 1_000_000;
            return new Kernel(
                    KERNEL_VERSION,
                    40 * scale,
                    12 * scale,
                    12 * scale,
                    2,
                    8 * scale,
                    500,
                    0,
                    1_000);
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            long globalCellCount,
            int resolutionRuleCount,
            int conflictRuleCount,
            int volcanicBindingCount,
            int canyonBindingCount,
            long estimatedCpuWorkUnits,
            long estimatedRetainedBytes,
            int maximumWindowSize,
            long maximumWorkingBytes,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "feature-material-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)
                    || globalCellCount < 1 || globalCellCount > ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS
                    || resolutionRuleCount != RESOLUTION_RULE_COUNT
                    || conflictRuleCount != CONFLICT_RULE_COUNT
                    || volcanicBindingCount < 0 || volcanicBindingCount > MAX_FEATURE_BINDINGS
                    || canyonBindingCount < 0 || canyonBindingCount > MAX_FEATURE_BINDINGS
                    || estimatedCpuWorkUnits < globalCellCount || estimatedCpuWorkUnits > 32_000_000L
                    || estimatedRetainedBytes < 1 || estimatedRetainedBytes > 1L * 1024L * 1024L
                    || maximumWindowSize < 1 || maximumWindowSize > 256
                    || maximumWorkingBytes < 1 || maximumWorkingBytes > 4L * 1024L * 1024L
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES) {
                throw new IllegalArgumentException("feature-material resource budget is outside trusted bounds");
            }
        }
    }

    private static void validateResolutionRules(List<ResolutionRule> rules) {
        if (!ResolutionRule.standardOrder().equals(rules)) {
            throw new IllegalArgumentException("feature-material resolution rules must be the fixed built-in order");
        }
    }

    private static void validateConflictRules(List<ConflictRule> rules) {
        if (!ConflictRule.standardOrder().equals(rules)) {
            throw new IllegalArgumentException("feature-material conflict rules must be the fixed built-in order");
        }
    }

    private static void validateBudget(ResourceBudget budget) {
        long required = Math.multiplyExact(budget.globalCellCount(), (long) RESOLUTION_RULE_COUNT);
        if (budget.estimatedCpuWorkUnits() < required) {
            throw new IllegalArgumentException("feature-material plan exceeds its declared CPU budget");
        }
    }

    private static void validateBindingKinds(List<FeatureGeometryBinding> bindings, FeatureKind expected) {
        Set<String> ids = new HashSet<>();
        for (FeatureGeometryBinding binding : bindings) {
            if (binding.featureKind() != expected || !ids.add(binding.featureId())) {
                throw new IllegalArgumentException("feature-material bindings are invalid for " + expected);
            }
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

    private static String slug(String value, String name) {
        value = nonBlank(value, name, 64);
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase slug");
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
