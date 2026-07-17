package com.github.nankotsu029.landformcraft.model.v2.environment;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Versioned built-in lithology catalog and the explicit mapping from V2-4-01 provinces to it.
 * Minecraft states, strata, and external catalog loading are deliberately outside this contract.
 */
public record LithologyPlanV2(
        int planVersion,
        String assignmentContractVersion,
        String sourceGeologyPlanChecksum,
        Catalog catalog,
        List<ProvinceAssignment> provinceAssignments,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String ASSIGNMENT_CONTRACT_VERSION = "lithology-province-assignment-v1";
    public static final int COMPACT_CODE_BITS = 8;
    public static final int MAX_ASSIGNMENTS = GeologyPlanV2.MAX_PROVINCES;

    public LithologyPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("lithology planVersion must be 1");
        }
        assignmentContractVersion = nonBlank(assignmentContractVersion, "assignmentContractVersion", 64);
        if (!ASSIGNMENT_CONTRACT_VERSION.equals(assignmentContractVersion)) {
            throw new IllegalArgumentException("unknown lithology assignment contract version");
        }
        sourceGeologyPlanChecksum = checksum(sourceGeologyPlanChecksum, "sourceGeologyPlanChecksum");
        Objects.requireNonNull(catalog, "catalog");
        provinceAssignments = immutable(provinceAssignments, "provinceAssignments", MAX_ASSIGNMENTS).stream()
                .sorted(Comparator.comparingInt(ProvinceAssignment::provinceCode)
                        .thenComparing(ProvinceAssignment::provinceId))
                .toList();
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateAssignments(catalog, provinceAssignments, budget);
    }

    public LithologyPlanV2 withCanonicalChecksum(String checksum) {
        return new LithologyPlanV2(
                planVersion, assignmentContractVersion, sourceGeologyPlanChecksum, catalog,
                provinceAssignments, budget, checksum);
    }

    /** Fails closed unless this plan exactly binds the frozen V2-4-01 province set. */
    public void requireGeologyPlan(GeologyPlanV2 geologyPlan) {
        Objects.requireNonNull(geologyPlan, "geologyPlan");
        if (!sourceGeologyPlanChecksum.equals(geologyPlan.canonicalChecksum())) {
            throw new IllegalArgumentException("lithology plan source geology checksum mismatch");
        }
        if (provinceAssignments.size() != geologyPlan.provinces().size()) {
            throw new IllegalArgumentException("lithology province assignment set is incomplete");
        }
        for (GeologyPlanV2.ProvinceDescriptor province : geologyPlan.provinces()) {
            ProvinceAssignment assignment = provinceAssignments.stream()
                    .filter(value -> value.provinceCode() == province.provinceCode()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "missing lithology assignment for province " + province.provinceId()));
            Entry lithology = catalog.requireByCode(assignment.lithologyCode());
            if (!assignment.provinceId().equals(province.provinceId())
                    || !assignment.formationId().equals(province.formationId())
                    || assignment.formationCode() != province.formationCode()
                    || !assignment.lithologyId().equals(lithology.lithologyId())
                    || province.hardnessMillionths() != lithology.hardnessMillionths()
                    || province.permeabilityMillionths() != lithology.permeabilityMillionths()) {
                throw new IllegalArgumentException("lithology assignment disagrees with geology province contract");
            }
        }
    }

    public record Catalog(
            int catalogVersion,
            String catalogId,
            String catalogContractVersion,
            List<Entry> entries,
            CatalogBudget budget,
            String canonicalChecksum
    ) {
        public static final int VERSION = 1;
        public static final String ID = "landformcraft.builtin-lithology";
        public static final String CONTRACT_VERSION = "builtin-lithology-catalog-v1";

        public Catalog {
            if (catalogVersion != VERSION) {
                throw new IllegalArgumentException("lithology catalogVersion must be 1");
            }
            if (!ID.equals(catalogId)) {
                throw new IllegalArgumentException("unknown lithology catalog ID");
            }
            catalogContractVersion = nonBlank(catalogContractVersion, "catalogContractVersion", 64);
            if (!CONTRACT_VERSION.equals(catalogContractVersion)) {
                throw new IllegalArgumentException("unknown lithology catalog contract version");
            }
            entries = immutable(entries, "entries", SemanticLithology.values().length).stream()
                    .sorted(Comparator.comparingInt(Entry::lithologyCode).thenComparing(Entry::lithologyId))
                    .toList();
            Objects.requireNonNull(budget, "budget");
            canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
            validateCatalog(entries, budget);
        }

        public Catalog withCanonicalChecksum(String checksum) {
            return new Catalog(catalogVersion, catalogId, catalogContractVersion, entries, budget, checksum);
        }

        public Entry requireByCode(int code) {
            return entries.stream().filter(entry -> entry.lithologyCode() == code).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown lithology compact code: " + code));
        }

        private static void validateCatalog(List<Entry> entries, CatalogBudget budget) {
            if (entries.size() != SemanticLithology.values().length
                    || entries.size() > budget.maximumEntries()) {
                throw new IllegalArgumentException("lithology catalog entry count is invalid");
            }
            Set<SemanticLithology> kinds = EnumSet.noneOf(SemanticLithology.class);
            Set<String> ids = new HashSet<>();
            Set<Integer> codes = new HashSet<>();
            for (Entry entry : entries) {
                if (!kinds.add(entry.kind()) || !ids.add(entry.lithologyId()) || !codes.add(entry.lithologyCode())) {
                    throw new IllegalArgumentException("duplicate lithology catalog entry");
                }
            }
            if (!kinds.equals(EnumSet.allOf(SemanticLithology.class))) {
                throw new IllegalArgumentException("lithology catalog is missing a built-in semantic ID");
            }
        }
    }

    /** The closed, compile-time semantic catalog. No arbitrary class, preset, or external artifact is accepted. */
    public enum SemanticLithology {
        HARD_INTRUSIVE("lithology.hard-intrusive", 1, 500_000, 500_000, ErosionResponse.MODERATE),
        ANDESITIC_VOLCANIC("lithology.andesitic-volcanic", 2, 650_000, 350_000, ErosionResponse.RESISTANT),
        BASALTIC_FLOW("lithology.basaltic-flow", 3, 700_000, 150_000, ErosionResponse.RESISTANT),
        TUFF_ASH("lithology.tuff-ash", 4, 250_000, 650_000, ErosionResponse.HIGHLY_ERODIBLE),
        CARBONATE_LIKE("lithology.carbonate-like", 5, 400_000, 600_000, ErosionResponse.ERODIBLE),
        SANDSTONE_STRATA("lithology.sandstone-strata", 6, 350_000, 550_000, ErosionResponse.ERODIBLE),
        ALLUVIAL_GRAVEL("lithology.alluvial-gravel", 7, 150_000, 900_000, ErosionResponse.HIGHLY_ERODIBLE),
        SILT_CLAY("lithology.silt-clay", 8, 100_000, 800_000, ErosionResponse.HIGHLY_ERODIBLE),
        REEF_CARBONATE("lithology.reef-carbonate", 9, 450_000, 500_000, ErosionResponse.MODERATE);

        private final String lithologyId;
        private final int compactCode;
        private final int hardnessMillionths;
        private final int permeabilityMillionths;
        private final ErosionResponse erosionResponse;

        SemanticLithology(
                String lithologyId,
                int compactCode,
                int hardnessMillionths,
                int permeabilityMillionths,
                ErosionResponse erosionResponse
        ) {
            this.lithologyId = lithologyId;
            this.compactCode = compactCode;
            this.hardnessMillionths = hardnessMillionths;
            this.permeabilityMillionths = permeabilityMillionths;
            this.erosionResponse = erosionResponse;
        }

        public String lithologyId() { return lithologyId; }
        public int compactCode() { return compactCode; }
        public int hardnessMillionths() { return hardnessMillionths; }
        public int permeabilityMillionths() { return permeabilityMillionths; }
        public ErosionResponse erosionResponse() { return erosionResponse; }
    }

    public enum ErosionResponse { RESISTANT, MODERATE, ERODIBLE, HIGHLY_ERODIBLE }

    public record Entry(
            SemanticLithology kind,
            String lithologyId,
            int lithologyCode,
            int hardnessMillionths,
            int permeabilityMillionths,
            ErosionResponse erosionResponse
    ) {
        public Entry {
            Objects.requireNonNull(kind, "kind");
            lithologyId = qualified(lithologyId, "lithologyId");
            if (!lithologyId.equals(kind.lithologyId()) || lithologyCode != kind.compactCode()
                    || lithologyCode < 1 || lithologyCode > 255
                    || hardnessMillionths != kind.hardnessMillionths()
                    || permeabilityMillionths != kind.permeabilityMillionths()
                    || erosionResponse != kind.erosionResponse()) {
                throw new IllegalArgumentException("lithology entry is not the fixed built-in definition");
            }
        }
    }

    public record CatalogBudget(
            String budgetVersion,
            int maximumEntries,
            int compactCodeBits,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "lithology-catalog-budget-v1";

        public CatalogBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion) || maximumEntries != SemanticLithology.values().length
                    || compactCodeBits != COMPACT_CODE_BITS || maximumCanonicalBytes < 1_024L
                    || maximumCanonicalBytes > 32L * 1024L) {
                throw new IllegalArgumentException("lithology catalog budget is invalid");
            }
        }
    }

    public record ProvinceAssignment(
            String provinceId,
            int provinceCode,
            String formationId,
            int formationCode,
            String lithologyId,
            int lithologyCode
    ) {
        public ProvinceAssignment {
            provinceId = slug(provinceId, "provinceId");
            formationId = qualified(formationId, "formationId");
            lithologyId = qualified(lithologyId, "lithologyId");
            if (provinceCode < 1 || provinceCode >= GeologyPlanV2.NO_DATA_RAW
                    || formationCode < 1 || formationCode >= GeologyPlanV2.NO_DATA_RAW
                    || lithologyCode < 1 || lithologyCode > 255) {
                throw new IllegalArgumentException("province lithology assignment code is invalid");
            }
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumAssignments,
            int compactCodeBits,
            long estimatedCpuWorkUnits,
            long estimatedRetainedBytes,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "lithology-assignment-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion) || maximumAssignments < 0 || maximumAssignments > MAX_ASSIGNMENTS
                    || compactCodeBits != COMPACT_CODE_BITS || estimatedCpuWorkUnits < 1L
                    || estimatedCpuWorkUnits > 1_000_000L || estimatedRetainedBytes < 1L
                    || estimatedRetainedBytes > 1L * 1024L * 1024L || maximumCanonicalBytes < 1_024L
                    || maximumCanonicalBytes > 64L * 1024L) {
                throw new IllegalArgumentException("lithology assignment budget is invalid");
            }
        }
    }

    private static void validateAssignments(
            Catalog catalog,
            List<ProvinceAssignment> assignments,
            ResourceBudget budget
    ) {
        if (assignments.size() > budget.maximumAssignments()) {
            throw new IllegalArgumentException("lithology assignments exceed their budget");
        }
        Set<String> provinceIds = new HashSet<>();
        Set<Integer> provinceCodes = new HashSet<>();
        Set<String> formationIds = new HashSet<>();
        Set<Integer> formationCodes = new HashSet<>();
        for (ProvinceAssignment assignment : assignments) {
            if (!provinceIds.add(assignment.provinceId()) || !provinceCodes.add(assignment.provinceCode())
                    || !formationIds.add(assignment.formationId()) || !formationCodes.add(assignment.formationCode())) {
                throw new IllegalArgumentException("duplicate lithology province assignment");
            }
            Entry entry = catalog.requireByCode(assignment.lithologyCode());
            if (!entry.lithologyId().equals(assignment.lithologyId())) {
                throw new IllegalArgumentException("lithology assignment ID/code mismatch");
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

    private static String slug(String value, String name) {
        value = nonBlank(value, name, 64);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(name + " is not a slug");
        }
        return value;
    }

    private static String qualified(String value, String name) {
        value = nonBlank(value, name, 128);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException(name + " is not a qualified ID");
        }
        return value;
    }

    private static String checksum(String value, String name) {
        value = nonBlank(value, name, 64);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " is not a SHA-256 checksum");
        }
        return value;
    }

    private static String nonBlank(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is blank or too long");
        }
        return value;
    }
}
