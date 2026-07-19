package com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic;

import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Built-in supported offline volcanic archipelago and V2-4 material module. */
public final class LandformVolcanicModuleV2 {
    public static final String MODULE_ID = "v2.landform.volcanic";
    public static final String MODULE_VERSION = "0.1.0-v2-3-11";
    public static final String STAGE_ID = "generate.landform-volcanic";
    public static final String ISLAND_MASK_FIELD_ID = "landform.volcanic.island-mask";
    public static final String ISLAND_INDEX_FIELD_ID = "landform.volcanic.island-index";
    public static final String SUMMIT_RELIEF_FIELD_ID = "landform.volcanic.summit-relief";
    public static final String SUBMARINE_SADDLE_MASK_FIELD_ID = "landform.volcanic.submarine-saddle-mask";
    public static final String RADIAL_DRAINAGE_FIELD_ID = "landform.volcanic.radial-drainage";
    public static final String PROVISIONAL_SURFACE_FIELD_ID = "landform.volcanic.provisional-surface";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 256;

    private static final List<String> PROVIDED = List.of(
            ISLAND_MASK_FIELD_ID, ISLAND_INDEX_FIELD_ID, SUMMIT_RELIEF_FIELD_ID,
            SUBMARINE_SADDLE_MASK_FIELD_ID, RADIAL_DRAINAGE_FIELD_ID, PROVISIONAL_SURFACE_FIELD_ID);
    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO),
            List.of(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID),
            PROVIDED,
            PROVIDED.stream().map(field -> new ModuleDescriptorV2.FieldWrite(
                    field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)).toList(),
            STAGE_ID,
            MAXIMUM_SUPPORT_RADIUS_XZ,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of("feature.volcanic.validator"),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
