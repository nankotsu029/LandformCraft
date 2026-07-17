package com.github.nankotsu029.landformcraft.generator.v2.climate;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;

import java.util.List;

/** Compile-time-only module descriptors for the V2-4-04 prior and final climate stages. */
public final class ClimateFieldModulesV2 {
    public static final String PRIOR_MODULE_ID = "v2.environment.climate-prior";
    public static final String PRIOR_MODULE_VERSION = "0.1.0-v2-4-04";
    public static final String PRIOR_STAGE_ID = "generate.climate-prior";
    public static final String PRIOR_GENERATOR_VERSION = "climate-coarse-prior-v1";

    public static final String FINAL_MODULE_ID = "v2.environment.climate-final";
    public static final String FINAL_MODULE_VERSION = "0.1.0-v2-4-04";
    public static final String FINAL_STAGE_ID = "generate.climate-final";
    public static final String FINAL_GENERATOR_VERSION = "climate-final-fixed-v1";

    public static final String PRIOR_PRECIPITATION_FIELD = "climate.prior.precipitation";
    public static final String PRIOR_RUNOFF_FIELD = "climate.prior.runoff";
    public static final String FINAL_TEMPERATURE_FIELD = "climate.final.temperature";
    public static final String FINAL_MOISTURE_FIELD = "climate.final.moisture";

    private static final List<String> PRIOR_FIELDS = List.of(
            PRIOR_PRECIPITATION_FIELD, PRIOR_RUNOFF_FIELD);
    private static final List<String> FINAL_FIELDS = List.of(
            FINAL_MOISTURE_FIELD, FINAL_TEMPERATURE_FIELD);

    private final ModuleDescriptorV2 priorDescriptor = descriptor(
            PRIOR_MODULE_ID, PRIOR_MODULE_VERSION, PRIOR_STAGE_ID, List.of(), PRIOR_FIELDS);
    private final ModuleDescriptorV2 finalDescriptor = descriptor(
            FINAL_MODULE_ID, FINAL_MODULE_VERSION, FINAL_STAGE_ID,
            List.of(
                    PRIOR_PRECIPITATION_FIELD,
                    PRIOR_RUNOFF_FIELD,
                    HydrologyIrModuleV2.FLOW_ACCUMULATION_FIELD),
            FINAL_FIELDS);

    public ModuleDescriptorV2 priorDescriptor() {
        return priorDescriptor;
    }

    public ModuleDescriptorV2 finalDescriptor() {
        return finalDescriptor;
    }

    public List<ClimatePlanV2.FieldBinding> fieldBindings() {
        return List.of(
                binding(PRIOR_PRECIPITATION_FIELD, ClimatePlanV2.FieldSemantic.PRIOR_PRECIPITATION,
                        ClimatePlanV2.FieldValueType.U16, ClimatePlanV2.FieldPhase.PRIOR, PRIOR_MODULE_ID),
                binding(PRIOR_RUNOFF_FIELD, ClimatePlanV2.FieldSemantic.PRIOR_RUNOFF,
                        ClimatePlanV2.FieldValueType.U16, ClimatePlanV2.FieldPhase.PRIOR, PRIOR_MODULE_ID),
                binding(FINAL_TEMPERATURE_FIELD, ClimatePlanV2.FieldSemantic.FINAL_TEMPERATURE,
                        ClimatePlanV2.FieldValueType.I16, ClimatePlanV2.FieldPhase.FINAL, FINAL_MODULE_ID),
                binding(FINAL_MOISTURE_FIELD, ClimatePlanV2.FieldSemantic.FINAL_MOISTURE,
                        ClimatePlanV2.FieldValueType.U16, ClimatePlanV2.FieldPhase.FINAL, FINAL_MODULE_ID));
    }

    private static ModuleDescriptorV2 descriptor(
            String moduleId,
            String moduleVersion,
            String stageId,
            List<String> requiredFields,
            List<String> providedFields
    ) {
        return new ModuleDescriptorV2(
                moduleId,
                moduleVersion,
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                List.of(),
                requiredFields,
                providedFields,
                providedFields.stream().map(field -> new ModuleDescriptorV2.FieldWrite(
                        field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)).toList(),
                stageId,
                0,
                0,
                ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
                List.of(),
                List.of());
    }

    private static ClimatePlanV2.FieldBinding binding(
            String fieldId,
            ClimatePlanV2.FieldSemantic semantic,
            ClimatePlanV2.FieldValueType valueType,
            ClimatePlanV2.FieldPhase phase,
            String ownerModuleId
    ) {
        return new ClimatePlanV2.FieldBinding(
                fieldId, semantic, valueType, phase, ownerModuleId,
                ClimatePlanV2.Ownership.SINGLE_OWNER,
                ClimatePlanV2.Sampling.BILINEAR_FIXED,
                ClimatePlanV2.RAW_SCALE_MILLIONTHS);
    }
}
