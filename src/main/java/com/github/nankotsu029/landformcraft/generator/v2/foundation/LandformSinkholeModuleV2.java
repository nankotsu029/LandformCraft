package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SinkholePlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL sinkhole foundation module (V2-10-03). */
public final class LandformSinkholeModuleV2 {
    public static final String MODULE_ID = SinkholePlanV2.MODULE_ID;
    public static final String MODULE_VERSION = SinkholePlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-sinkhole";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            SinkholePlanV2.COLLAPSE_MASK_FIELD_ID,
            SinkholePlanV2.OWNERSHIP_FIELD_ID,
            SinkholePlanV2.REACHABILITY_FIELD_ID,
            SinkholePlanV2.ROOF_CLEARANCE_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.SINKHOLE),
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
            List.of("feature.sinkhole.validator"),
            List.of("feature.sinkhole.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
