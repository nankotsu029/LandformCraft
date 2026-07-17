package com.github.nankotsu029.landformcraft.generator.v2.environment.water;

import com.github.nankotsu029.landformcraft.generator.v2.climate.ClimateFieldModulesV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;

import java.util.List;

/** Compile-time-only module descriptor for the V2-4-05 regional water-condition stage. */
public final class WaterConditionFieldModulesV2 {
    public static final String MODULE_ID = "v2.environment.water-condition";
    public static final String MODULE_VERSION = "0.1.0-v2-4-05";
    public static final String STAGE_ID = "generate.water-condition";
    public static final String GENERATOR_VERSION = "water-condition-fixed-v1";

    public static final String WATER_DISTANCE_FIELD = "environment.water.distance";
    public static final String GROUNDWATER_PROXY_FIELD = "environment.water.groundwater";
    public static final String TIDAL_INFLUENCE_FIELD = "environment.water.tidal-influence";
    public static final String SALINITY_FIELD = "environment.water.salinity";
    public static final String HYDROPERIOD_FIELD = "environment.water.hydroperiod";
    public static final String WETNESS_FIELD = "environment.water.wetness";
    public static final String WETNESS_RESIDUAL_FIELD = "environment.water.wetness-residual";

    private static final List<String> PROVIDED = List.of(
            WATER_DISTANCE_FIELD,
            GROUNDWATER_PROXY_FIELD,
            HYDROPERIOD_FIELD,
            SALINITY_FIELD,
            TIDAL_INFLUENCE_FIELD,
            WETNESS_FIELD,
            WETNESS_RESIDUAL_FIELD);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(),
            List.of(ClimateFieldModulesV2.FINAL_MOISTURE_FIELD),
            PROVIDED,
            PROVIDED.stream().map(field -> new ModuleDescriptorV2.FieldWrite(
                    field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)).toList(),
            STAGE_ID,
            WaterConditionPlanV2.MAX_DISTANCE_BLOCKS,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of(),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }

    public List<WaterConditionPlanV2.FieldBinding> fieldBindings() {
        return List.of(
                binding(GROUNDWATER_PROXY_FIELD, WaterConditionPlanV2.FieldSemantic.GROUNDWATER_PROXY,
                        WaterConditionPlanV2.FieldValueType.U16),
                binding(HYDROPERIOD_FIELD, WaterConditionPlanV2.FieldSemantic.HYDROPERIOD,
                        WaterConditionPlanV2.FieldValueType.U16),
                binding(SALINITY_FIELD, WaterConditionPlanV2.FieldSemantic.SALINITY,
                        WaterConditionPlanV2.FieldValueType.U16),
                binding(TIDAL_INFLUENCE_FIELD, WaterConditionPlanV2.FieldSemantic.TIDAL_INFLUENCE,
                        WaterConditionPlanV2.FieldValueType.U16),
                binding(WATER_DISTANCE_FIELD, WaterConditionPlanV2.FieldSemantic.WATER_DISTANCE,
                        WaterConditionPlanV2.FieldValueType.U16),
                binding(WETNESS_FIELD, WaterConditionPlanV2.FieldSemantic.WETNESS,
                        WaterConditionPlanV2.FieldValueType.U16),
                binding(WETNESS_RESIDUAL_FIELD, WaterConditionPlanV2.FieldSemantic.WETNESS_RESIDUAL,
                        WaterConditionPlanV2.FieldValueType.I16));
    }

    private static WaterConditionPlanV2.FieldBinding binding(
            String fieldId,
            WaterConditionPlanV2.FieldSemantic semantic,
            WaterConditionPlanV2.FieldValueType valueType
    ) {
        return new WaterConditionPlanV2.FieldBinding(
                fieldId, semantic, valueType, MODULE_ID,
                WaterConditionPlanV2.Ownership.SINGLE_OWNER,
                WaterConditionPlanV2.Sampling.NEAREST,
                WaterConditionPlanV2.RAW_SCALE_MILLIONTHS);
    }
}
