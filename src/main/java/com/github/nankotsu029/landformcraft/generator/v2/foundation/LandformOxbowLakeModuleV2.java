package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OxbowLakePlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL oxbow lake foundation module (V2-10-11). */
public final class LandformOxbowLakeModuleV2 {
    public static final String MODULE_ID = OxbowLakePlanV2.MODULE_ID;
    public static final String MODULE_VERSION = OxbowLakePlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-oxbow-lake";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            OxbowLakePlanV2.BASIN_MASK_FIELD_ID,
            OxbowLakePlanV2.RIM_MASK_FIELD_ID,
            OxbowLakePlanV2.WETLAND_HANDOFF_FIELD_ID,
            OxbowLakePlanV2.OWNERSHIP_FIELD_ID,
            OxbowLakePlanV2.CUTOFF_FIELD_ID,
            OxbowLakePlanV2.LEVEL_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.OXBOW_LAKE),
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
            List.of("feature.oxbow-lake.validator"),
            List.of("feature.oxbow-lake.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
