package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.LavaTubePlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL lava-tube foundation module (V2-10-07). */
public final class LandformLavaTubeModuleV2 {
    public static final String MODULE_ID = LavaTubePlanV2.MODULE_ID;
    public static final String MODULE_VERSION = LavaTubePlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-lava-tube";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            LavaTubePlanV2.TUBE_MASK_FIELD_ID,
            LavaTubePlanV2.ROOF_CLEARANCE_FIELD_ID,
            LavaTubePlanV2.SUPPORT_FIELD_ID,
            LavaTubePlanV2.OWNERSHIP_FIELD_ID,
            LavaTubePlanV2.CONTINUITY_FIELD_ID,
            LavaTubePlanV2.MATERIAL_HANDOFF_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.LAVA_TUBE),
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
            List.of("feature.lava-tube.validator"),
            List.of("feature.lava-tube.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
