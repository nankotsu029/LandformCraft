package com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.HydrologyTidalModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Built-in supported offline mangrove wetland regional/environment profile module. */
public final class LandformMangroveModuleV2 {
    public static final String MODULE_ID = "v2.landform.mangrove";
    public static final String MODULE_VERSION = "0.1.0-v2-4-09";
    public static final String STAGE_ID = "generate.landform-mangrove";
    public static final String WETLAND_MASK_FIELD_ID = "landform.mangrove.wetland-mask";
    public static final String SURFACE_HEIGHT_FIELD_ID = "landform.mangrove.surface-height";
    public static final String OPEN_WATER_GAP_FIELD_ID = "landform.mangrove.open-water-gap";
    public static final String SUBSTRATE_CLASS_FIELD_ID = "landform.mangrove.substrate-class";
    public static final String MICRO_RELIEF_FIELD_ID = "landform.mangrove.micro-relief";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            WETLAND_MASK_FIELD_ID,
            SURFACE_HEIGHT_FIELD_ID,
            OPEN_WATER_GAP_FIELD_ID,
            SUBSTRATE_CLASS_FIELD_ID,
            MICRO_RELIEF_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(TerrainIntentV2.FeatureKind.MANGROVE_WETLAND),
            List.of(HydrologyTidalModuleV2.CHANNEL_MASK_FIELD_ID),
            PROVIDED,
            PROVIDED.stream()
                    .map(field -> new ModuleDescriptorV2.FieldWrite(
                            field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER))
                    .toList(),
            STAGE_ID,
            MAXIMUM_SUPPORT_RADIUS_XZ,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of("feature.mangrove.validator"),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
