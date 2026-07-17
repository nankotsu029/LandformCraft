package com.github.nankotsu029.landformcraft.generator.v2.hydrology;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;

import java.util.List;

/** Descriptor-only field ownership contract for V2-3-01. It performs no routing or rasterization. */
public final class HydrologyIrModuleV2 {
    public static final String MODULE_ID = "v2.hydrology.ir";
    public static final String MODULE_VERSION = "0.1.0-v2-3-01";
    public static final String STAGE_ID = "compile.hydrology-ir";

    public static final String WATER_BODY_ID_FIELD = "hydrology.water-body-id";
    public static final String FLOW_DIRECTION_FIELD = "hydrology.flow-direction";
    public static final String FLOW_ACCUMULATION_FIELD = "hydrology.flow-accumulation";
    public static final String BED_ELEVATION_FIELD = "hydrology.bed-elevation";
    public static final String WATER_SURFACE_FIELD = "hydrology.water-surface";
    public static final String WATER_DEPTH_FIELD = "hydrology.water-depth";

    private static final List<String> FIELDS = List.of(
            BED_ELEVATION_FIELD,
            FLOW_ACCUMULATION_FIELD,
            FLOW_DIRECTION_FIELD,
            WATER_BODY_ID_FIELD,
            WATER_DEPTH_FIELD,
            WATER_SURFACE_FIELD);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(),
            List.of(),
            FIELDS,
            FIELDS.stream().map(field -> new ModuleDescriptorV2.FieldWrite(
                    field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)).toList(),
            STAGE_ID,
            0,
            0,
            ModuleDescriptorV2.ResourceClass.DIAGNOSTIC_LOW,
            List.of("hydrology.graph-contract"),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }

    public List<HydrologyPlanV2.FieldBinding> fieldBindings() {
        return List.of(
                binding(BED_ELEVATION_FIELD, HydrologyPlanV2.FieldSemantic.BED_ELEVATION,
                        HydrologyPlanV2.FieldValueType.I32),
                binding(FLOW_ACCUMULATION_FIELD, HydrologyPlanV2.FieldSemantic.FLOW_ACCUMULATION,
                        HydrologyPlanV2.FieldValueType.I32),
                binding(FLOW_DIRECTION_FIELD, HydrologyPlanV2.FieldSemantic.FLOW_DIRECTION,
                        HydrologyPlanV2.FieldValueType.U8),
                binding(WATER_BODY_ID_FIELD, HydrologyPlanV2.FieldSemantic.WATER_BODY_ID,
                        HydrologyPlanV2.FieldValueType.I32),
                binding(WATER_DEPTH_FIELD, HydrologyPlanV2.FieldSemantic.WATER_DEPTH,
                        HydrologyPlanV2.FieldValueType.I32),
                binding(WATER_SURFACE_FIELD, HydrologyPlanV2.FieldSemantic.WATER_SURFACE,
                        HydrologyPlanV2.FieldValueType.I32));
    }

    private static HydrologyPlanV2.FieldBinding binding(
            String fieldId,
            HydrologyPlanV2.FieldSemantic semantic,
            HydrologyPlanV2.FieldValueType valueType
    ) {
        return new HydrologyPlanV2.FieldBinding(
                fieldId, semantic, valueType, MODULE_ID, HydrologyPlanV2.Ownership.SINGLE_OWNER);
    }
}
