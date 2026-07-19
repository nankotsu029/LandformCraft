package com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.HydrologyRiverModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/**
 * Built-in module for WATERFALL lip / base / plunge-pool 2.5D shaping. SUPPORTED (offline) since
 * the V2-5 phase gate: the deferred falling-column / behind-fall volume is delivered by the
 * sparse-volume {@code WaterfallVolumePlanV2} bound to this module's fall geometry checksum.
 */
public final class HydrologyWaterfallModuleV2 {
    public static final String MODULE_ID = "v2.hydrology.waterfall";
    public static final String MODULE_VERSION = "0.1.0-v2-3-06";
    public static final String STAGE_ID = "generate.hydrology-waterfall";

    public static final String LIP_MASK_FIELD_ID = "hydrology.waterfall.lip-mask";
    public static final String BASE_MASK_FIELD_ID = "hydrology.waterfall.base-mask";
    public static final String PLUNGE_POOL_MASK_FIELD_ID = "hydrology.waterfall.plunge-pool-mask";
    public static final String LIP_ELEVATION_FIELD_ID = "hydrology.waterfall.lip-elevation";
    public static final String BASE_ELEVATION_FIELD_ID = "hydrology.waterfall.base-elevation";
    public static final String PLUNGE_POOL_FLOOR_FIELD_ID = "hydrology.waterfall.plunge-pool-floor";

    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 128;

    private static final List<String> REQUIRED = List.of(
            HydrologyIrModuleV2.BED_ELEVATION_FIELD,
            HydrologyRiverModuleV2.CHANNEL_MASK_FIELD_ID);
    private static final List<String> PROVIDED = List.of(
            LIP_MASK_FIELD_ID,
            BASE_MASK_FIELD_ID,
            PLUNGE_POOL_MASK_FIELD_ID,
            LIP_ELEVATION_FIELD_ID,
            BASE_ELEVATION_FIELD_ID,
            PLUNGE_POOL_FLOOR_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(TerrainIntentV2.FeatureKind.WATERFALL),
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
            List.of("feature.waterfall.validator"),
            List.of("feature.waterfall.volume-deferred"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
