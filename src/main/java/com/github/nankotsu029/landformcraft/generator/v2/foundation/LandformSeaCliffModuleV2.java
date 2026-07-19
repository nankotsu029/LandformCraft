package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SeaCliffPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL sea-cliff foundation module (V2-9-06). */
public final class LandformSeaCliffModuleV2 {
    public static final String MODULE_ID = SeaCliffPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = SeaCliffPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-sea-cliff";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            SeaCliffPlanV2.CLIFF_FACE_MASK_FIELD_ID,
            SeaCliffPlanV2.TALUS_MASK_FIELD_ID,
            SeaCliffPlanV2.NOTCH_MASK_FIELD_ID,
            SeaCliffPlanV2.SOLID_OWNERSHIP_FIELD_ID,
            SeaCliffPlanV2.VOLUME_HOST_OWNERSHIP_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.SEA_CLIFF),
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
            List.of("feature.sea-cliff.validator"),
            List.of("feature.sea-cliff.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
