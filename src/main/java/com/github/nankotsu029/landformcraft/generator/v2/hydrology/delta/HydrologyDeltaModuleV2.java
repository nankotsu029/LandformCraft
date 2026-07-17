package com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta;

import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.HydrologyRiverModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Built-in supported module for offline DELTA distributary DAG and 2.5D fan shaping. */
public final class HydrologyDeltaModuleV2 {
    public static final String MODULE_ID = "v2.hydrology.delta";
    public static final String MODULE_VERSION = "0.1.0-v2-3-07";
    public static final String STAGE_ID = "generate.hydrology-delta";

    public static final String FAN_MASK_FIELD_ID = "hydrology.delta.fan-mask";
    public static final String CHANNEL_MASK_FIELD_ID = "hydrology.delta.channel-mask";
    public static final String BRANCH_INDEX_FIELD_ID = "hydrology.delta.branch-index";
    public static final String FAN_SURFACE_FIELD_ID = "hydrology.delta.fan-surface";
    public static final String SANDBAR_MASK_FIELD_ID = "hydrology.delta.sandbar-mask";
    public static final String SHALLOW_SEA_DEPTH_FIELD_ID = "hydrology.delta.shallow-sea-depth";
    public static final String DISCHARGE_SHARE_FIELD_ID = "hydrology.delta.discharge-share";

    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 128;

    private static final List<String> REQUIRED = List.of(
            CoastalTransitionModuleV2.LAND_WATER_FIELD_ID,
            HydrologyRiverModuleV2.CHANNEL_MASK_FIELD_ID);
    private static final List<String> PROVIDED = List.of(
            BRANCH_INDEX_FIELD_ID,
            CHANNEL_MASK_FIELD_ID,
            DISCHARGE_SHARE_FIELD_ID,
            FAN_MASK_FIELD_ID,
            FAN_SURFACE_FIELD_ID,
            SANDBAR_MASK_FIELD_ID,
            SHALLOW_SEA_DEPTH_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(TerrainIntentV2.FeatureKind.DELTA),
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
            List.of("feature.delta.validator"),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
