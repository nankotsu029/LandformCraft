package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.GlacialIcePlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL glacial-ice foundation module (V2-10-01). */
public final class LandformGlacialIceModuleV2 {
    public static final String MODULE_ID = GlacialIcePlanV2.MODULE_ID;
    public static final String MODULE_VERSION = GlacialIcePlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-glacial-ice";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            GlacialIcePlanV2.SURFACE_OWNERSHIP_FIELD_ID,
            GlacialIcePlanV2.VOLUME_OWNERSHIP_FIELD_ID,
            GlacialIcePlanV2.THICKNESS_FIELD_ID,
            GlacialIcePlanV2.FLOW_DIRECTION_FIELD_ID,
            GlacialIcePlanV2.BED_CONTACT_FIELD_ID,
            GlacialIcePlanV2.MELTWATER_MASK_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(
                    TerrainIntentV2.FeatureKind.VALLEY_GLACIER,
                    TerrainIntentV2.FeatureKind.ICE_CAP,
                    TerrainIntentV2.FeatureKind.ICE_SHEET),
            List.of(),
            PROVIDED,
            PROVIDED.stream()
                    .map(field -> new ModuleDescriptorV2.FieldWrite(
                            field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER))
                    .toList(),
            STAGE_ID,
            MAXIMUM_SUPPORT_RADIUS_XZ,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of("feature.glacial-ice.validator"),
            List.of("feature.glacial-ice.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
