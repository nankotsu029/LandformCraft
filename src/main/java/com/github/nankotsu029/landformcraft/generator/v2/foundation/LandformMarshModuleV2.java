package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MarshPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL marsh foundation module (V2-9-05). */
public final class LandformMarshModuleV2 {
    public static final String MODULE_ID = MarshPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = MarshPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-marsh";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            MarshPlanV2.MARSH_MASK_FIELD_ID,
            MarshPlanV2.OPEN_WATER_FIELD_ID,
            MarshPlanV2.MICRO_RELIEF_FIELD_ID,
            MarshPlanV2.WETNESS_FIELD_ID,
            MarshPlanV2.HYDROPERIOD_FIELD_ID,
            MarshPlanV2.FLUID_OWNERSHIP_FIELD_ID,
            MarshPlanV2.SOLID_OWNERSHIP_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.MARSH),
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
            List.of("feature.marsh.validator"),
            List.of("feature.marsh.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
