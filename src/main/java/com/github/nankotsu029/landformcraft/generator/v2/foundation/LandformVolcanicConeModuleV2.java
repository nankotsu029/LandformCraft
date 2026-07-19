package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.VolcanicConePlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL volcanic-cone foundation module (V2-9-07). */
public final class LandformVolcanicConeModuleV2 {
    public static final String MODULE_ID = VolcanicConePlanV2.MODULE_ID;
    public static final String MODULE_VERSION = VolcanicConePlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-volcanic-cone";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            VolcanicConePlanV2.CONE_MASK_FIELD_ID,
            VolcanicConePlanV2.CRATER_MASK_FIELD_ID,
            VolcanicConePlanV2.DRAINAGE_FIELD_ID,
            VolcanicConePlanV2.SOLID_OWNERSHIP_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.VOLCANIC_CONE),
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
            List.of("feature.volcanic-cone.validator"),
            List.of("feature.volcanic-cone.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
