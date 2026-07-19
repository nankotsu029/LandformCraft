package com.github.nankotsu029.landformcraft.generator.v2.environment.snow;

import com.github.nankotsu029.landformcraft.generator.v2.climate.ClimateFieldModulesV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;

import java.util.List;

public final class SnowFieldModulesV2 {
    public static final String MODULE_ID = "v2.environment.snow";
    public static final String MODULE_VERSION = "0.1.0-v2-4-06";
    public static final String STAGE_ID = "generate.snow";
    public static final String GENERATOR_VERSION = "snow-fixed-v1";

    public static final String SNOW_POTENTIAL_FIELD = "environment.snow.potential";
    public static final String SNOW_COVER_FIELD = "environment.snow.cover";

    private static final List<String> PROVIDED = List.of(
            SNOW_POTENTIAL_FIELD,
            SNOW_COVER_FIELD);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(),
            List.of(ClimateFieldModulesV2.FINAL_TEMPERATURE_FIELD, ClimateFieldModulesV2.FINAL_MOISTURE_FIELD),
            PROVIDED,
            PROVIDED.stream().map(field -> new ModuleDescriptorV2.FieldWrite(
                    field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)).toList(),
            STAGE_ID,
            0,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of(),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }

    public List<SnowPlanV2.FieldBinding> fieldBindings() {
        return List.of(
                binding(SNOW_POTENTIAL_FIELD, SnowPlanV2.FieldSemantic.SNOW_POTENTIAL),
                binding(SNOW_COVER_FIELD, SnowPlanV2.FieldSemantic.SNOW_COVER));
    }

    private static SnowPlanV2.FieldBinding binding(
            String fieldId,
            SnowPlanV2.FieldSemantic semantic
    ) {
        return new SnowPlanV2.FieldBinding(
                fieldId, semantic, SnowPlanV2.FieldValueType.U16, MODULE_ID,
                SnowPlanV2.Ownership.SINGLE_OWNER,
                SnowPlanV2.Sampling.NEAREST,
                SnowPlanV2.RAW_SCALE_MILLIONTHS);
    }
}
