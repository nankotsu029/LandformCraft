package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstSpringPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL karst-spring foundation module (V2-10-03). */
public final class LandformKarstSpringModuleV2 {
    public static final String MODULE_ID = KarstSpringPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = KarstSpringPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-karst-spring";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            KarstSpringPlanV2.DISCHARGE_MASK_FIELD_ID,
            KarstSpringPlanV2.OWNERSHIP_FIELD_ID,
            KarstSpringPlanV2.REACHABILITY_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.KARST_SPRING),
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
            List.of("feature.karst-spring.validator"),
            List.of("feature.karst-spring.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
