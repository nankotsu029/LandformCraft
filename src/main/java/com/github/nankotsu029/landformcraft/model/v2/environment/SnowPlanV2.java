package com.github.nankotsu029.landformcraft.model.v2.environment;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-4 snow condition contract. Fields are derived from final terrain height,
 * climate temperature and moisture, and slope/exposure, without explicit Minecraft block state binding.
 */
public record SnowPlanV2(
        int planVersion,
        String fieldContractVersion,
        String moduleId,
        String moduleVersion,
        String stageId,
        long namedSeed,
        String seedNamespace,
        int width,
        int length,
        int minY,
        int maxY,
        Kernel kernel,
        ClimateBinding climateBinding,
        List<FieldBinding> fields,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String FIELD_CONTRACT_VERSION = "snow-field-contract-v1";
    public static final String SEED_NAMESPACE = "terrain.v2.snow";
    public static final int FIXED_SCALE = 1_000_000;
    public static final int RAW_SCALE_MILLIONTHS = 1_000;
    public static final int MAX_FIELDS = 2;
    public static final long MAX_CANONICAL_BYTES = 128L * 1024L;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public SnowPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("snow planVersion must be 1");
        }
        fieldContractVersion = nonBlank(fieldContractVersion, "fieldContractVersion", 64);
        if (!FIELD_CONTRACT_VERSION.equals(fieldContractVersion)) {
            throw new IllegalArgumentException("unknown snow field contract version");
        }
        moduleId = qualified(moduleId, "moduleId");
        moduleVersion = nonBlank(moduleVersion, "moduleVersion", 64);
        stageId = qualified(stageId, "stageId");
        seedNamespace = qualified(seedNamespace, "seedNamespace");
        if (!SEED_NAMESPACE.equals(seedNamespace)) {
            throw new IllegalArgumentException("unknown snow seed namespace");
        }
        if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING
                || minY >= maxY || (long) maxY - minY + 1L > 512L) {
            throw new IllegalArgumentException("snow dimensions are invalid");
        }
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(climateBinding, "climateBinding");
        fields = immutable(fields, "fields", MAX_FIELDS).stream()
                .sorted(Comparator.comparing(FieldBinding::fieldId)).toList();
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateContract(width, length, minY, maxY, moduleId, kernel, climateBinding,
                fields, budget);
    }

    public SnowPlanV2 withCanonicalChecksum(String checksum) {
        return new SnowPlanV2(
                planVersion, fieldContractVersion, moduleId, moduleVersion, stageId,
                namedSeed, seedNamespace, width, length, minY, maxY,
                kernel, climateBinding, fields, budget, checksum);
    }

    public void requireClimatePlan(ClimatePlanV2 climatePlan) {
        Objects.requireNonNull(climatePlan, "climatePlan");
        if (!climateBinding.sourceClimatePlanChecksum().equals(climatePlan.canonicalChecksum())
                || !climateBinding.temperatureFieldId().equals("climate.final.temperature")
                || !climateBinding.moistureFieldId().equals("climate.final.moisture")) {
            throw new IllegalArgumentException("snow climate binding mismatch");
        }
        boolean hasTemperature = climatePlan.fields().stream().anyMatch(field ->
                field.semantic() == ClimatePlanV2.FieldSemantic.FINAL_TEMPERATURE
                        && field.fieldId().equals(climateBinding.temperatureFieldId()));
        boolean hasMoisture = climatePlan.fields().stream().anyMatch(field ->
                field.semantic() == ClimatePlanV2.FieldSemantic.FINAL_MOISTURE
                        && field.fieldId().equals(climateBinding.moistureFieldId()));
        if (!hasTemperature || !hasMoisture) {
            throw new IllegalArgumentException("snow requires climate final temperature and moisture fields");
        }
    }

    public enum FieldSemantic {
        SNOW_POTENTIAL,
        SNOW_COVER
    }

    public enum FieldValueType { U16 }
    public enum Ownership { SINGLE_OWNER }
    public enum Sampling { NEAREST }

    public record Kernel(
            String kernelVersion,
            int snowlineTemperatureRaw,
            int snowlineTransitionBlocks,
            int steepSlopeThresholdRaw,
            int steepSlopePenaltyRaw,
            int minimumRaw,
            int maximumRaw
    ) {
        public static final String KERNEL_VERSION = "snow-fixed-v1";

        public Kernel {
            if (!KERNEL_VERSION.equals(kernelVersion)
                    || snowlineTemperatureRaw < -1_000 || snowlineTemperatureRaw > 1_000
                    || snowlineTransitionBlocks < 1 || snowlineTransitionBlocks > 100
                    || steepSlopeThresholdRaw < 0 || steepSlopeThresholdRaw > 1_000
                    || steepSlopePenaltyRaw < 0 || steepSlopePenaltyRaw > 1_000
                    || minimumRaw != 0 || maximumRaw != 1_000) {
                throw new IllegalArgumentException("unknown or invalid snow kernel");
            }
        }

        public static Kernel standard() {
            return new Kernel(
                    KERNEL_VERSION, 150, 10, 700, 500, 0, 1_000);
        }
    }

    public record ClimateBinding(
            int bindingVersion,
            String sourceClimatePlanChecksum,
            String temperatureFieldId,
            String moistureFieldId,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "snow-climate-binding-v1";
        public static final String TEMPERATURE_FIELD_ID = "climate.final.temperature";
        public static final String MOISTURE_FIELD_ID = "climate.final.moisture";

        public ClimateBinding {
            if (bindingVersion != VERSION
                    || !TEMPERATURE_FIELD_ID.equals(temperatureFieldId)
                    || !MOISTURE_FIELD_ID.equals(moistureFieldId)
                    || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown snow climate binding");
            }
            sourceClimatePlanChecksum = checksum(sourceClimatePlanChecksum, "sourceClimatePlanChecksum");
            temperatureFieldId = qualified(temperatureFieldId, "temperatureFieldId");
            moistureFieldId = qualified(moistureFieldId, "moistureFieldId");
        }
    }

    public record FieldBinding(
            String fieldId,
            FieldSemantic semantic,
            FieldValueType valueType,
            String ownerModuleId,
            Ownership ownership,
            Sampling sampling,
            int scaleMillionths
    ) {
        public FieldBinding {
            fieldId = qualified(fieldId, "fieldId");
            Objects.requireNonNull(semantic, "semantic");
            Objects.requireNonNull(valueType, "valueType");
            ownerModuleId = qualified(ownerModuleId, "ownerModuleId");
            if (ownership != Ownership.SINGLE_OWNER || sampling != Sampling.NEAREST
                    || scaleMillionths != RAW_SCALE_MILLIONTHS) {
                throw new IllegalArgumentException("snow field ownership/sampling/scale mismatch");
            }
            if (valueType != FieldValueType.U16) {
                throw new IllegalArgumentException("snow field value type mismatch");
            }
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumFields,
            long globalCellCount,
            long estimatedCpuWorkUnits,
            long estimatedRetainedBytes,
            int maximumWindowSize,
            long maximumWorkingBytes,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "snow-field-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)
                    || maximumFields != MAX_FIELDS
                    || globalCellCount < 1 || globalCellCount > 1_000_000L
                    || estimatedCpuWorkUnits < globalCellCount || estimatedCpuWorkUnits > 16_000_000L
                    || estimatedRetainedBytes < 1 || estimatedRetainedBytes > 2L * 1024L * 1024L
                    || maximumWindowSize < 1 || maximumWindowSize > 256
                    || maximumWorkingBytes < 1 || maximumWorkingBytes > 8L * 1024L * 1024L
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES) {
                throw new IllegalArgumentException("snow resource budget is outside trusted bounds");
            }
        }
    }

    private static void validateContract(
            int width,
            int length,
            int minY,
            int maxY,
            String moduleId,
            Kernel kernel,
            ClimateBinding climateBinding,
            List<FieldBinding> fields,
            ResourceBudget budget
    ) {
        Set<String> ids = new HashSet<>();
        EnumSet<FieldSemantic> semantics = EnumSet.noneOf(FieldSemantic.class);
        for (FieldBinding field : fields) {
            if (!ids.add(field.fieldId()) || !semantics.add(field.semantic())
                    || !field.ownerModuleId().equals(moduleId)) {
                throw new IllegalArgumentException("conflicting snow field binding");
            }
        }
        if (!semantics.equals(EnumSet.allOf(FieldSemantic.class))) {
            throw new IllegalArgumentException("snow field contract is incomplete");
        }
        long cells = Math.multiplyExact((long) width, length);
        int windowWidth = Math.min(width, budget.maximumWindowSize());
        int windowLength = Math.min(length, budget.maximumWindowSize());
        long working = Math.multiplyExact(Math.multiplyExact((long) windowWidth, windowLength),
                MAX_FIELDS * (long) Integer.BYTES);
        long retained = 16L * 1024L;
        long cpu = Math.multiplyExact(cells, MAX_FIELDS);
        long verticalSpan = Math.addExact(Math.subtractExact((long) maxY, minY), 1L);
        if (verticalSpan > 512L
                || budget.globalCellCount() != cells
                || budget.estimatedCpuWorkUnits() < cpu
                || budget.estimatedRetainedBytes() < retained
                || budget.maximumWorkingBytes() < working
                || fields.size() != budget.maximumFields()
                || climateBinding.moistureFieldId() == null) {
            throw new IllegalArgumentException("snow plan exceeds its declared resource budget");
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
