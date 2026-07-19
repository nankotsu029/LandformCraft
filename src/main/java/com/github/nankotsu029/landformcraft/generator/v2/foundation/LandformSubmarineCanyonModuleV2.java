package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SubmarineCanyonPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL submarine-canyon foundation module (V2-9-09). */
public final class LandformSubmarineCanyonModuleV2 {
    public static final String MODULE_ID = SubmarineCanyonPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = SubmarineCanyonPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-submarine-canyon";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            SubmarineCanyonPlanV2.MASK_FIELD_ID,
            SubmarineCanyonPlanV2.FLOOR_DEPTH_FIELD_ID,
            SubmarineCanyonPlanV2.OWNERSHIP_FIELD_ID,
            SubmarineCanyonPlanV2.HOST_HANDOFF_FIELD_ID,
            SubmarineCanyonPlanV2.FLUID_COLUMN_HINT_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.SUBMARINE_CANYON),
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
            List.of("feature.submarine-canyon.validator"),
            List.of("feature.submarine-canyon.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
