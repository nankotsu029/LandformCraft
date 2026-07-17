package com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Built-in supported module for offline independent LAKE basin / rim / spillway shaping. */
public final class HydrologyLakeModuleV2 {
    public static final String MODULE_ID = "v2.hydrology.lake";
    public static final String MODULE_VERSION = "0.1.0-v2-3-04";
    public static final String STAGE_ID = "generate.hydrology-lake";

    public static final String BASIN_MASK_FIELD_ID = "hydrology.lake.basin-mask";
    public static final String RIM_MASK_FIELD_ID = "hydrology.lake.rim-mask";
    public static final String SPILLWAY_MASK_FIELD_ID = "hydrology.lake.spillway-mask";
    public static final String DEPTH_FIELD_ID = "hydrology.lake.depth";
    public static final String FLOOR_HEIGHT_FIELD_ID = "hydrology.lake.floor-height";
    public static final String SURFACE_FIELD_ID = "hydrology.lake.surface";

    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 128;

    private static final List<String> REQUIRED = List.of(
            HydrologyIrModuleV2.FLOW_DIRECTION_FIELD,
            HydrologyIrModuleV2.FLOW_ACCUMULATION_FIELD);
    private static final List<String> PROVIDED = List.of(
            BASIN_MASK_FIELD_ID,
            DEPTH_FIELD_ID,
            FLOOR_HEIGHT_FIELD_ID,
            RIM_MASK_FIELD_ID,
            SPILLWAY_MASK_FIELD_ID,
            SURFACE_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(TerrainIntentV2.FeatureKind.LAKE),
            REQUIRED,
            PROVIDED,
            PROVIDED.stream()
                    .map(field -> new ModuleDescriptorV2.FieldWrite(
                            field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER))
                    .toList(),
            STAGE_ID,
            MAXIMUM_SUPPORT_RADIUS_XZ,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of("feature.lake.validator"),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
