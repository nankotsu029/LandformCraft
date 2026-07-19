package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RockyCoastPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL rocky-coast foundation module (V2-9-06). */
public final class LandformRockyCoastModuleV2 {
    public static final String MODULE_ID = RockyCoastPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = RockyCoastPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-rocky-coast";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            RockyCoastPlanV2.SHELF_MASK_FIELD_ID,
            RockyCoastPlanV2.EXPOSURE_FIELD_ID,
            RockyCoastPlanV2.CHANNEL_MASK_FIELD_ID,
            RockyCoastPlanV2.TALUS_HANDOFF_FIELD_ID,
            RockyCoastPlanV2.SOLID_OWNERSHIP_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.ROCKY_COAST),
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
            List.of("feature.rocky-coast.validator"),
            List.of("feature.rocky-coast.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
