package com.github.nankotsu029.landformcraft.model.v2.environment;

import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-4 regional water-condition contract. Fields are derived from final terrain,
 * hydrology connectivity, and climate moisture without fluid simulation or implicit ocean.
 */
public record WaterConditionPlanV2(
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
        int referenceWaterY,
        Kernel kernel,
        HydrologyBinding hydrologyBinding,
        ClimateMoistureBinding climateBinding,
        List<FieldBinding> fields,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String FIELD_CONTRACT_VERSION = "water-condition-field-contract-v1";
    public static final String SEED_NAMESPACE = "terrain.v2.water-condition";
    public static final int FIXED_SCALE = 1_000_000;
    public static final int RAW_SCALE_MILLIONTHS = 1_000;
    public static final int MAX_FIELDS = 7;
    public static final int MAX_DISTANCE_BLOCKS = 64;
    public static final long MAX_CANONICAL_BYTES = 128L * 1024L;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public WaterConditionPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("water-condition planVersion must be 1");
        }
        fieldContractVersion = nonBlank(fieldContractVersion, "fieldContractVersion", 64);
        if (!FIELD_CONTRACT_VERSION.equals(fieldContractVersion)) {
            throw new IllegalArgumentException("unknown water-condition field contract version");
        }
        moduleId = qualified(moduleId, "moduleId");
        moduleVersion = nonBlank(moduleVersion, "moduleVersion", 64);
        stageId = qualified(stageId, "stageId");
        seedNamespace = qualified(seedNamespace, "seedNamespace");
        if (!SEED_NAMESPACE.equals(seedNamespace)) {
            throw new IllegalArgumentException("unknown water-condition seed namespace");
        }
        if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING
                || minY >= maxY || (long) maxY - minY + 1L > 512L
                || referenceWaterY < minY || referenceWaterY > maxY) {
            throw new IllegalArgumentException("water-condition dimensions or water reference are invalid");
        }
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(hydrologyBinding, "hydrologyBinding");
        Objects.requireNonNull(climateBinding, "climateBinding");
        fields = immutable(fields, "fields", MAX_FIELDS).stream()
                .sorted(Comparator.comparing(FieldBinding::fieldId)).toList();
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateContract(width, length, minY, maxY, moduleId, kernel, hydrologyBinding, climateBinding,
                fields, budget);
    }

    public WaterConditionPlanV2 withCanonicalChecksum(String checksum) {
        return new WaterConditionPlanV2(
                planVersion, fieldContractVersion, moduleId, moduleVersion, stageId,
                namedSeed, seedNamespace, width, length, minY, maxY, referenceWaterY,
                kernel, hydrologyBinding, climateBinding, fields, budget, checksum);
    }

    public void requireHydrologyPlan(HydrologyPlanV2 hydrologyPlan) {
        Objects.requireNonNull(hydrologyPlan, "hydrologyPlan");
        if (!hydrologyBinding.sourceHydrologyPlanChecksum().equals(hydrologyPlan.canonicalChecksum())) {
            throw new IllegalArgumentException("water-condition hydrology binding mismatch");
        }
    }

    public void requireClimatePlan(ClimatePlanV2 climatePlan) {
        Objects.requireNonNull(climatePlan, "climatePlan");
        if (!climateBinding.sourceClimatePlanChecksum().equals(climatePlan.canonicalChecksum())
                || !climateBinding.moistureFieldId().equals("climate.final.moisture")) {
            throw new IllegalArgumentException("water-condition climate moisture binding mismatch");
        }
        boolean hasMoisture = climatePlan.fields().stream().anyMatch(field ->
                field.semantic() == ClimatePlanV2.FieldSemantic.FINAL_MOISTURE
                        && field.fieldId().equals(climateBinding.moistureFieldId()));
        if (!hasMoisture) {
            throw new IllegalArgumentException("water-condition requires climate final moisture field");
        }
    }

    public enum FieldSemantic {
        WATER_DISTANCE,
        GROUNDWATER_PROXY,
        TIDAL_INFLUENCE,
        SALINITY,
        HYDROPERIOD,
        WETNESS,
        WETNESS_RESIDUAL
    }

    public enum FieldValueType { U16, I16 }
    public enum Ownership { SINGLE_OWNER }
    public enum Sampling { NEAREST }

    public record Kernel(
            String kernelVersion,
            int maximumDistanceBlocks,
            int groundwaterDecayRawPerBlock,
            int tidalFalloffRaw,
            int salinityMarineBaseRaw,
            int freshwaterDilutionRaw,
            int hydroperiodTidalGainRaw,
            int wetnessMoistureWeightRaw,
            int wetnessGroundwaterWeightRaw,
            int wetnessProximityWeightRaw,
            int minimumRaw,
            int maximumRaw
    ) {
        public static final String KERNEL_VERSION = "water-condition-fixed-v1";

        public Kernel {
            if (!KERNEL_VERSION.equals(kernelVersion)
                    || maximumDistanceBlocks < 8 || maximumDistanceBlocks > MAX_DISTANCE_BLOCKS
                    || groundwaterDecayRawPerBlock < 1 || groundwaterDecayRawPerBlock > 250
                    || tidalFalloffRaw < 0 || tidalFalloffRaw > 1_000
                    || salinityMarineBaseRaw < 0 || salinityMarineBaseRaw > 1_000
                    || freshwaterDilutionRaw < 0 || freshwaterDilutionRaw > 1_000
                    || hydroperiodTidalGainRaw < 0 || hydroperiodTidalGainRaw > 1_000
                    || wetnessMoistureWeightRaw < 0 || wetnessMoistureWeightRaw > 1_000
                    || wetnessGroundwaterWeightRaw < 0 || wetnessGroundwaterWeightRaw > 1_000
                    || wetnessProximityWeightRaw < 0 || wetnessProximityWeightRaw > 1_000
                    || minimumRaw != 0 || maximumRaw != 1_000) {
                throw new IllegalArgumentException("unknown or invalid water-condition kernel");
            }
            long weightSum = Math.addExact(Math.addExact(
                    (long) wetnessMoistureWeightRaw, wetnessGroundwaterWeightRaw), wetnessProximityWeightRaw);
            if (weightSum != 1_000L) {
                throw new IllegalArgumentException("water-condition wetness weights must sum to 1000");
            }
        }

        public static Kernel standard() {
            return new Kernel(
                    KERNEL_VERSION, MAX_DISTANCE_BLOCKS, 20, 0, 900, 700, 850,
                    400, 350, 250, 0, 1_000);
        }
    }

    public record HydrologyBinding(
            int bindingVersion,
            String sourceHydrologyPlanChecksum,
            int maximumDistanceBlocks,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "water-condition-hydrology-binding-v1";

        public HydrologyBinding {
            if (bindingVersion != VERSION
                    || maximumDistanceBlocks < 8 || maximumDistanceBlocks > MAX_DISTANCE_BLOCKS
                    || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown water-condition hydrology binding");
            }
            sourceHydrologyPlanChecksum = checksum(sourceHydrologyPlanChecksum, "sourceHydrologyPlanChecksum");
        }
    }

    public record ClimateMoistureBinding(
            int bindingVersion,
            String sourceClimatePlanChecksum,
            String moistureFieldId,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "water-condition-climate-binding-v1";
        public static final String MOISTURE_FIELD_ID = "climate.final.moisture";

        public ClimateMoistureBinding {
            if (bindingVersion != VERSION
                    || !MOISTURE_FIELD_ID.equals(moistureFieldId)
                    || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown water-condition climate moisture binding");
            }
            sourceClimatePlanChecksum = checksum(sourceClimatePlanChecksum, "sourceClimatePlanChecksum");
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
                throw new IllegalArgumentException("water-condition field ownership/sampling/scale mismatch");
            }
            FieldValueType expected = semantic == FieldSemantic.WETNESS_RESIDUAL
                    ? FieldValueType.I16 : FieldValueType.U16;
            if (valueType != expected) {
                throw new IllegalArgumentException("water-condition field value type mismatch");
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
            int maximumDistanceBlocks,
            long maximumWorkingBytes,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "water-condition-field-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)
                    || maximumFields != MAX_FIELDS
                    || globalCellCount < 1 || globalCellCount > ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS
                    || estimatedCpuWorkUnits < globalCellCount || estimatedCpuWorkUnits > 16_000_000L
                    || estimatedRetainedBytes < 1 || estimatedRetainedBytes > 2L * 1024L * 1024L
                    || maximumWindowSize < 1 || maximumWindowSize > 256
                    || maximumDistanceBlocks < 8 || maximumDistanceBlocks > MAX_DISTANCE_BLOCKS
                    || maximumWorkingBytes < 1 || maximumWorkingBytes > 8L * 1024L * 1024L
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES) {
                throw new IllegalArgumentException("water-condition resource budget is outside trusted bounds");
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
            HydrologyBinding hydrologyBinding,
            ClimateMoistureBinding climateBinding,
            List<FieldBinding> fields,
            ResourceBudget budget
    ) {
        if (hydrologyBinding.maximumDistanceBlocks() != kernel.maximumDistanceBlocks()
                || budget.maximumDistanceBlocks() != kernel.maximumDistanceBlocks()) {
            throw new IllegalArgumentException("water-condition distance budget/kernel mismatch");
        }
        Set<String> ids = new HashSet<>();
        EnumSet<FieldSemantic> semantics = EnumSet.noneOf(FieldSemantic.class);
        for (FieldBinding field : fields) {
            if (!ids.add(field.fieldId()) || !semantics.add(field.semantic())
                    || !field.ownerModuleId().equals(moduleId)) {
                throw new IllegalArgumentException("conflicting water-condition field binding");
            }
        }
        if (!semantics.equals(EnumSet.allOf(FieldSemantic.class))) {
            throw new IllegalArgumentException("water-condition field contract is incomplete");
        }
        long cells = Math.multiplyExact((long) width, length);
        int windowWidth = Math.min(width, budget.maximumWindowSize());
        int windowLength = Math.min(length, budget.maximumWindowSize());
        long working = Math.multiplyExact(Math.multiplyExact((long) windowWidth, windowLength),
                MAX_FIELDS * (long) Integer.BYTES);
        long retained = 48L * 1024L;
        long cpu = Math.multiplyExact(cells, MAX_FIELDS);
        long verticalSpan = Math.addExact(Math.subtractExact((long) maxY, minY), 1L);
        if (verticalSpan > 512L
                || budget.globalCellCount() != cells
                || budget.estimatedCpuWorkUnits() < cpu
                || budget.estimatedRetainedBytes() < retained
                || budget.maximumWorkingBytes() < working
                || fields.size() != budget.maximumFields()
                || climateBinding.moistureFieldId() == null) {
            throw new IllegalArgumentException("water-condition plan exceeds its declared resource budget");
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
