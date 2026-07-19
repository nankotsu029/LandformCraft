package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.CaveEntrancePlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL cave-entrance foundation module (V2-9-10). */
public final class LandformCaveEntranceModuleV2 {
    public static final String MODULE_ID = CaveEntrancePlanV2.MODULE_ID;
    public static final String MODULE_VERSION = CaveEntrancePlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-cave-entrance";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            CaveEntrancePlanV2.OPENING_MASK_FIELD_ID,
            CaveEntrancePlanV2.APPROACH_MASK_FIELD_ID,
            CaveEntrancePlanV2.OWNERSHIP_FIELD_ID,
            CaveEntrancePlanV2.REACHABILITY_FIELD_ID,
            CaveEntrancePlanV2.ROOF_CLEARANCE_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.CAVE_ENTRANCE),
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
            List.of("feature.cave-entrance.validator"),
            List.of("feature.cave-entrance.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
