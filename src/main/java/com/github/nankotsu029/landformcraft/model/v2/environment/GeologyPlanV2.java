package com.github.nankotsu029.landformcraft.model.v2.environment;

import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Versioned V2-4 geology foundation contract. It contains no Minecraft block state or dense payload. */
public record GeologyPlanV2(
        int planVersion,
        String fieldContractVersion,
        String moduleId,
        String moduleVersion,
        String stageId,
        PriorReplacement priorReplacement,
        long namedSeed,
        String seedNamespace,
        int width,
        int length,
        List<ProvinceDescriptor> provinces,
        List<FieldBinding> fields,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String FIELD_CONTRACT_VERSION = "geology-field-contract-v1";
    public static final int NO_DATA_RAW = 65_535;
    public static final int MAX_PROVINCES = 256;
    public static final int MAX_FIELDS = 4;
    public static final long MAX_HEADER_BYTES_PER_FIELD = 2_048L;
    public static final long STRICT_READ_BACK_BUFFER_BYTES = 64L * 1024L;
    public static final long ESTIMATED_RETAINED_BYTES = 64L * 1024L;

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public GeologyPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("geology planVersion must be 1");
        }
        fieldContractVersion = nonBlank(fieldContractVersion, "fieldContractVersion", 64);
        if (!FIELD_CONTRACT_VERSION.equals(fieldContractVersion)) {
            throw new IllegalArgumentException("unknown geology field contract version");
        }
        moduleId = qualified(moduleId, "moduleId");
        moduleVersion = nonBlank(moduleVersion, "moduleVersion", 64);
        stageId = qualified(stageId, "stageId");
        Objects.requireNonNull(priorReplacement, "priorReplacement");
        seedNamespace = qualified(seedNamespace, "seedNamespace");
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000) {
            throw new IllegalArgumentException("geology dimensions outside 1..1000");
        }
        provinces = immutable(provinces, "provinces", MAX_PROVINCES).stream()
                .sorted(Comparator.comparingInt(ProvinceDescriptor::provinceCode)
                        .thenComparing(ProvinceDescriptor::provinceId))
                .toList();
        fields = immutable(fields, "fields", MAX_FIELDS).stream()
                .sorted(Comparator.comparing(FieldBinding::fieldId))
                .toList();
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateContract(moduleId, width, length, provinces, fields, budget);
    }

    public GeologyPlanV2 withCanonicalChecksum(String checksum) {
        return new GeologyPlanV2(
                planVersion, fieldContractVersion, moduleId, moduleVersion, stageId,
                priorReplacement, namedSeed, seedNamespace, width, length, provinces, fields,
                budget, checksum);
    }

    /** Explicit, versioned replacement for the immutable V2-3 uniform geology prior. */
    public record PriorReplacement(
            int bindingVersion,
            SourcePriorKind sourcePriorKind,
            String sourcePriorChecksum,
            ReplacementMode replacementMode,
            int sourceHardnessMillionths,
            int sourcePermeabilityMillionths
    ) {
        public static final int VERSION = 1;

        public PriorReplacement {
            if (bindingVersion != VERSION
                    || sourcePriorKind != SourcePriorKind.UNIFORM_GEOLOGY_PRIOR
                    || replacementMode != ReplacementMode.TYPED_GEOLOGY_FIELDS
                    || sourceHardnessMillionths
                    != HydrologyPlanV2.FixedPriors.UNIFORM_HARDNESS_MILLIONTHS
                    || sourcePermeabilityMillionths
                    != HydrologyPlanV2.FixedPriors.UNIFORM_PERMEABILITY_MILLIONTHS) {
                throw new IllegalArgumentException("unknown geology prior replacement contract");
            }
            sourcePriorChecksum = checksum(sourcePriorChecksum, "sourcePriorChecksum");
            if (!HydrologyPlanV2.FixedPriors.CHECKSUM.equals(sourcePriorChecksum)) {
                throw new IllegalArgumentException("geology source prior checksum mismatch");
            }
        }

        public static PriorReplacement v2Phase3UniformPrior() {
            return new PriorReplacement(
                    VERSION,
                    SourcePriorKind.UNIFORM_GEOLOGY_PRIOR,
                    HydrologyPlanV2.FixedPriors.CHECKSUM,
                    ReplacementMode.TYPED_GEOLOGY_FIELDS,
                    HydrologyPlanV2.FixedPriors.UNIFORM_HARDNESS_MILLIONTHS,
                    HydrologyPlanV2.FixedPriors.UNIFORM_PERMEABILITY_MILLIONTHS);
        }
    }

    public enum SourcePriorKind { UNIFORM_GEOLOGY_PRIOR }
    public enum ReplacementMode { TYPED_GEOLOGY_FIELDS }
    public enum FieldSemantic { PROVINCE_ID, FORMATION_ID, HARDNESS, PERMEABILITY }
    public enum FieldValueType { U16 }
    public enum Ownership { SINGLE_OWNER }

    /** Opaque formation IDs are typed here; semantic lithology meaning belongs to V2-4-02. */
    public record ProvinceDescriptor(
            String provinceId,
            int provinceCode,
            String formationId,
            int formationCode,
            int hardnessMillionths,
            int permeabilityMillionths
    ) {
        public ProvinceDescriptor {
            provinceId = slug(provinceId, "provinceId");
            formationId = qualified(formationId, "formationId");
            if (provinceCode < 1 || provinceCode >= NO_DATA_RAW
                    || formationCode < 1 || formationCode >= NO_DATA_RAW) {
                throw new IllegalArgumentException("geology province/formation code outside 1..65534");
            }
            if (hardnessMillionths < 0 || hardnessMillionths > 1_000_000
                    || permeabilityMillionths < 0 || permeabilityMillionths > 1_000_000) {
                throw new IllegalArgumentException("geology hardness/permeability outside 0..1000000");
            }
        }

        public int hardnessRaw() {
            return hardnessMillionths / 1_000;
        }

        public int permeabilityRaw() {
            return permeabilityMillionths / 1_000;
        }
    }

    public record FieldBinding(
            String fieldId,
            FieldSemantic semantic,
            FieldValueType valueType,
            String ownerModuleId,
            Ownership ownership,
            String encodingVersion
    ) {
        public FieldBinding {
            fieldId = qualified(fieldId, "fieldId");
            Objects.requireNonNull(semantic, "semantic");
            if (valueType != FieldValueType.U16 || ownership != Ownership.SINGLE_OWNER) {
                throw new IllegalArgumentException("geology field type/ownership mismatch");
            }
            ownerModuleId = qualified(ownerModuleId, "ownerModuleId");
            if (!FieldArtifactDescriptorV2.ENCODING_VERSION.equals(encodingVersion)) {
                throw new IllegalArgumentException("unknown geology field encoding version");
            }
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumProvinces,
            int maximumFields,
            long globalCellCount,
            long estimatedCpuWorkUnits,
            long estimatedRetainedBytes,
            int maximumWindowSize,
            long maximumWorkingBytes,
            long estimatedArtifactBytes,
            long maximumSingleArtifactBytes
    ) {
        public static final String VERSION = "geology-field-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown geology budget version");
            }
            if (maximumProvinces < 0 || maximumProvinces > MAX_PROVINCES
                    || maximumFields != MAX_FIELDS
                    || globalCellCount < 1 || globalCellCount > 1_000_000L
                    || estimatedCpuWorkUnits < globalCellCount
                    || estimatedCpuWorkUnits > 16_000_000L
                    || estimatedRetainedBytes < 1 || estimatedRetainedBytes > 16L * 1024L * 1024L
                    || maximumWindowSize < 1 || maximumWindowSize > 256
                    || maximumWorkingBytes < 1 || maximumWorkingBytes > 16L * 1024L * 1024L
                    || estimatedArtifactBytes < 1 || estimatedArtifactBytes > 16L * 1024L * 1024L
                    || maximumSingleArtifactBytes < 1
                    || maximumSingleArtifactBytes > 8L * 1024L * 1024L
                    || maximumSingleArtifactBytes > estimatedArtifactBytes) {
                throw new IllegalArgumentException("geology resource budget is outside trusted bounds");
            }
        }
    }

    private static void validateContract(
            String moduleId,
            int width,
            int length,
            List<ProvinceDescriptor> provinces,
            List<FieldBinding> fields,
            ResourceBudget budget
    ) {
        Set<String> provinceIds = new HashSet<>();
        Set<Integer> provinceCodes = new HashSet<>();
        Set<String> formationIds = new HashSet<>();
        Set<Integer> formationCodes = new HashSet<>();
        for (ProvinceDescriptor province : provinces) {
            if (!provinceIds.add(province.provinceId()) || !provinceCodes.add(province.provinceCode())
                    || !formationIds.add(province.formationId())
                    || !formationCodes.add(province.formationCode())) {
                throw new IllegalArgumentException("duplicate geology province or formation ID/code");
            }
            if (province.hardnessMillionths() % 1_000 != 0
                    || province.permeabilityMillionths() % 1_000 != 0) {
                throw new IllegalArgumentException("geology scalar values must use U16 thousandths");
            }
        }
        Set<String> fieldIds = new HashSet<>();
        EnumSet<FieldSemantic> semantics = EnumSet.noneOf(FieldSemantic.class);
        for (FieldBinding field : fields) {
            if (!field.ownerModuleId().equals(moduleId)
                    || !fieldIds.add(field.fieldId()) || !semantics.add(field.semantic())) {
                throw new IllegalArgumentException("conflicting geology field binding");
            }
        }
        if (!semantics.equals(EnumSet.allOf(FieldSemantic.class))) {
            throw new IllegalArgumentException("geology field contract is incomplete");
        }
        long cells = Math.multiplyExact((long) width, length);
        int windowWidth = Math.min(width, budget.maximumWindowSize());
        int windowLength = Math.min(length, budget.maximumWindowSize());
        long windowCells = Math.multiplyExact((long) windowWidth, windowLength);
        long readerWorkingBytes = Math.addExact(
                Math.multiplyExact(windowCells, MAX_FIELDS * (long) Integer.BYTES),
                Math.multiplyExact(windowWidth, MAX_FIELDS * (long) Short.BYTES));
        long writerWorkingBytes = Math.addExact(
                STRICT_READ_BACK_BUFFER_BYTES, MAX_HEADER_BYTES_PER_FIELD);
        long requiredWorkingBytes = Math.max(readerWorkingBytes, writerWorkingBytes);
        long requiredSingleArtifactBytes = Math.addExact(
                Math.multiplyExact(cells, Short.BYTES), MAX_HEADER_BYTES_PER_FIELD);
        long requiredArtifactBytes = Math.multiplyExact(requiredSingleArtifactBytes, MAX_FIELDS);
        if (provinces.size() > budget.maximumProvinces()
                || fields.size() != budget.maximumFields()
                || budget.globalCellCount() != cells
                || budget.estimatedCpuWorkUnits() < Math.multiplyExact(cells, MAX_FIELDS)
                || budget.estimatedRetainedBytes() < ESTIMATED_RETAINED_BYTES
                || budget.maximumWorkingBytes() < requiredWorkingBytes
                || budget.estimatedArtifactBytes() < requiredArtifactBytes
                || budget.maximumSingleArtifactBytes() < requiredSingleArtifactBytes) {
            throw new IllegalArgumentException("geology plan exceeds its declared resource budget");
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
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase slug");
        }
        return value;
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
