package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.HillRangePlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL hill-range foundation module (V2-9-02). */
public final class LandformHillRangeModuleV2 {
    public static final String MODULE_ID = HillRangePlanV2.MODULE_ID;
    public static final String MODULE_VERSION = HillRangePlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-hill-range";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            HillRangePlanV2.RIDGE_MASK_FIELD_ID,
            HillRangePlanV2.SADDLE_MASK_FIELD_ID,
            HillRangePlanV2.ELEVATION_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.HILL_RANGE),
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
            List.of("feature.hill-range.validator"),
            List.of("feature.hill-range.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
