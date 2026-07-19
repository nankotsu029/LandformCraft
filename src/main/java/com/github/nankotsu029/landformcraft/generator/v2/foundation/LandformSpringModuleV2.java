package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SpringPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL surface spring foundation module (V2-10-10). */
public final class LandformSpringModuleV2 {
    public static final String MODULE_ID = SpringPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = SpringPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-spring";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 32;

    private static final List<String> PROVIDED = List.of(
            SpringPlanV2.SOURCE_MASK_FIELD_ID,
            SpringPlanV2.OUTFLOW_MASK_FIELD_ID,
            SpringPlanV2.OWNERSHIP_FIELD_ID,
            SpringPlanV2.CONTINUITY_FIELD_ID,
            SpringPlanV2.REACHABILITY_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.SPRING),
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
            List.of("feature.spring.validator"),
            List.of("feature.spring.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
