package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MountainRangePlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL mountain-range foundation module (V2-9-03). */
public final class LandformMountainRangeModuleV2 {
    public static final String MODULE_ID = MountainRangePlanV2.MODULE_ID;
    public static final String MODULE_VERSION = MountainRangePlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-mountain-range";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            MountainRangePlanV2.RIDGE_MASK_FIELD_ID,
            MountainRangePlanV2.PEAK_MASK_FIELD_ID,
            MountainRangePlanV2.SADDLE_MASK_FIELD_ID,
            MountainRangePlanV2.SPUR_MASK_FIELD_ID,
            MountainRangePlanV2.PASS_MASK_FIELD_ID,
            MountainRangePlanV2.ELEVATION_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE),
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
            List.of("feature.mountain-range.validator"),
            List.of("feature.mountain-range.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
