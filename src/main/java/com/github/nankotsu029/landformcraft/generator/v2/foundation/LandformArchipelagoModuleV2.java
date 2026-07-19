package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ArchipelagoPlanV2;

import java.util.List;

/** Built-in EXPERIMENTAL archipelago foundation module (V2-9-07). */
public final class LandformArchipelagoModuleV2 {
    public static final String MODULE_ID = ArchipelagoPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = ArchipelagoPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = "generate.foundation-archipelago";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            ArchipelagoPlanV2.MASS_MASK_FIELD_ID,
            ArchipelagoPlanV2.SADDLE_FIELD_ID,
            ArchipelagoPlanV2.DOMINANCE_FIELD_ID,
            ArchipelagoPlanV2.GAP_FIELD_ID,
            ArchipelagoPlanV2.SOLID_OWNERSHIP_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(TerrainIntentV2.FeatureKind.ARCHIPELAGO),
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
            List.of("feature.archipelago.validator"),
            List.of("feature.archipelago.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
