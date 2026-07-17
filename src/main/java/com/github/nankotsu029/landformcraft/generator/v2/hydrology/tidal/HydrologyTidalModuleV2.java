package com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal;

import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Built-in supported module for offline TIDAL_CHANNEL_NETWORK graph and channel rasters. */
public final class HydrologyTidalModuleV2 {
    public static final String MODULE_ID = "v2.hydrology.tidal";
    public static final String MODULE_VERSION = "0.1.0-v2-3-08";
    public static final String STAGE_ID = "generate.hydrology-tidal";

    public static final String CHANNEL_MASK_FIELD_ID = "hydrology.tidal.channel-mask";
    public static final String BRANCH_INDEX_FIELD_ID = "hydrology.tidal.branch-index";
    public static final String DEPTH_CORRIDOR_FIELD_ID = "hydrology.tidal.depth-corridor";
    public static final String MARINE_CONNECTION_FIELD_ID = "hydrology.tidal.marine-connection";

    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 128;

    private static final List<String> REQUIRED = List.of(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID);
    private static final List<String> PROVIDED = List.of(
            BRANCH_INDEX_FIELD_ID,
            CHANNEL_MASK_FIELD_ID,
            DEPTH_CORRIDOR_FIELD_ID,
            MARINE_CONNECTION_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK),
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
            List.of("feature.tidal.validator"),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
