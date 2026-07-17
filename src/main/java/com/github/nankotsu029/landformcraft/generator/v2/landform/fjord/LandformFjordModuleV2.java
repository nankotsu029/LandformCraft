package com.github.nankotsu029.landformcraft.generator.v2.landform.fjord;

import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Built-in supported offline 2.5D marine channel and glacial-sidewall module. */
public final class LandformFjordModuleV2 {
    public static final String MODULE_ID = "v2.landform.fjord";
    public static final String MODULE_VERSION = "0.1.0-v2-3-09";
    public static final String STAGE_ID = "generate.landform-fjord";
    public static final String CHANNEL_MASK_FIELD_ID = "landform.fjord.channel-mask";
    public static final String FLOOR_MASK_FIELD_ID = "landform.fjord.floor-mask";
    public static final String SIDEWALL_MASK_FIELD_ID = "landform.fjord.sidewall-mask";
    public static final String THALWEG_DEPTH_FIELD_ID = "landform.fjord.thalweg-depth";
    public static final String SIDEWALL_RELIEF_FIELD_ID = "landform.fjord.sidewall-relief";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 256;

    private static final List<String> PROVIDED = List.of(CHANNEL_MASK_FIELD_ID, FLOOR_MASK_FIELD_ID,
            SIDEWALL_MASK_FIELD_ID, THALWEG_DEPTH_FIELD_ID, SIDEWALL_RELIEF_FIELD_ID);
    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID, MODULE_VERSION, ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(TerrainIntentV2.FeatureKind.FJORD),
            List.of(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID), PROVIDED,
            PROVIDED.stream().map(field -> new ModuleDescriptorV2.FieldWrite(
                    field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)).toList(),
            STAGE_ID, MAXIMUM_SUPPORT_RADIUS_XZ, 0, ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of("feature.fjord.validator"), List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
