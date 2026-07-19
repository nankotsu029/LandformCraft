package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL general river foundation module (V2-9-04). */
public final class LandformRiverModuleV2 {
    public static final String MODULE_ID = RiverPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = RiverPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-river";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            RiverPlanV2.CHANNEL_MASK_FIELD_ID,
            RiverPlanV2.BANK_MASK_FIELD_ID,
            RiverPlanV2.FLOODPLAIN_MASK_FIELD_ID,
            RiverPlanV2.BED_ELEVATION_FIELD_ID,
            RiverPlanV2.DISCHARGE_INDEX_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.RIVER),
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
            List.of("feature.river.validator"),
            List.of("feature.river.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
