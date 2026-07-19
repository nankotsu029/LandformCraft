package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlateauPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL plateau foundation module (V2-10-06). */
public final class LandformPlateauModuleV2 {
    public static final String MODULE_ID = PlateauPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = PlateauPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-plateau";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            PlateauPlanV2.CAP_MASK_FIELD_ID,
            PlateauPlanV2.OWNERSHIP_FIELD_ID,
            PlateauPlanV2.ELEVATION_FIELD_ID,
            PlateauPlanV2.MATERIAL_HANDOFF_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.PLATEAU),
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
            List.of("feature.plateau.validator"),
            List.of("feature.plateau.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
