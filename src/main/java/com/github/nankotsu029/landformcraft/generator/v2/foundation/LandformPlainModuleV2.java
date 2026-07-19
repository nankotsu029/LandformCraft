package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlainPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL plain foundation module (V2-9-02). */
public final class LandformPlainModuleV2 {
    public static final String MODULE_ID = PlainPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = PlainPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-plain";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            PlainPlanV2.PLAIN_MASK_FIELD_ID,
            PlainPlanV2.BASE_ELEVATION_FIELD_ID,
            PlainPlanV2.MICRO_RELIEF_FIELD_ID,
            PlainPlanV2.GROUNDWATER_HANDOFF_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.PLAIN),
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
            List.of("feature.plain.validator"),
            List.of("feature.plain.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
