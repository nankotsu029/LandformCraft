package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OutwashPlainPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL outwash-plain foundation module (V2-10-02). */
public final class LandformOutwashPlainModuleV2 {
    public static final String MODULE_ID = OutwashPlainPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = OutwashPlainPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-outwash-plain";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            OutwashPlainPlanV2.SEDIMENT_OWNERSHIP_FIELD_ID,
            OutwashPlainPlanV2.FLOW_DIRECTION_FIELD_ID,
            OutwashPlainPlanV2.MELTWATER_MASK_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.OUTWASH_PLAIN),
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
            List.of("feature.outwash-plain.validator"),
            List.of("feature.outwash-plain.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
