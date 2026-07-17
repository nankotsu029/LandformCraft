package com.github.nankotsu029.landformcraft.generator.v2.landform.canyon;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.HydrologyRiverModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Built-in supported module for offline CANYON 2.5D cross-section shaping. */
public final class LandformCanyonModuleV2 {
    public static final String MODULE_ID = "v2.landform.canyon";
    public static final String MODULE_VERSION = "0.1.0-v2-3-05";
    public static final String STAGE_ID = "generate.landform-canyon";

    public static final String CANYON_MASK_FIELD_ID = "landform.canyon.mask";
    public static final String FLOOR_MASK_FIELD_ID = "landform.canyon.floor-mask";
    public static final String RIM_MASK_FIELD_ID = "landform.canyon.rim-mask";
    public static final String TERRACE_MASK_FIELD_ID = "landform.canyon.terrace-mask";
    public static final String SURFACE_HEIGHT_FIELD_ID = "landform.canyon.surface-height";
    public static final String WALL_HEIGHT_FIELD_ID = "landform.canyon.wall-height";

    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 256;

    private static final List<String> REQUIRED = List.of(
            HydrologyIrModuleV2.BED_ELEVATION_FIELD,
            HydrologyRiverModuleV2.CHANNEL_MASK_FIELD_ID);
    private static final List<String> PROVIDED = List.of(
            CANYON_MASK_FIELD_ID,
            FLOOR_MASK_FIELD_ID,
            RIM_MASK_FIELD_ID,
            TERRACE_MASK_FIELD_ID,
            SURFACE_HEIGHT_FIELD_ID,
            WALL_HEIGHT_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(TerrainIntentV2.FeatureKind.CANYON),
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
            List.of("feature.canyon.validator"),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
