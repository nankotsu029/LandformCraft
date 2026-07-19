package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalShelfPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL continental-shelf foundation module (V2-9-08). */
public final class LandformContinentalShelfModuleV2 {
    public static final String MODULE_ID = ContinentalShelfPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = ContinentalShelfPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-continental-shelf";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            ContinentalShelfPlanV2.DEPTH_FIELD_ID,
            ContinentalShelfPlanV2.SLOPE_FIELD_ID,
            ContinentalShelfPlanV2.COAST_DISTANCE_FIELD_ID,
            ContinentalShelfPlanV2.OWNERSHIP_FIELD_ID,
            ContinentalShelfPlanV2.FLUID_COLUMN_HINT_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF),
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
            List.of("feature.continental-shelf.validator"),
            List.of("feature.continental-shelf.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
