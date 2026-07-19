package com.github.nankotsu029.landformcraft.generator.v2.landform.mountain;

import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Built-in supported offline mountain ridge and V2-4 environment module. */
public final class LandformMountainModuleV2 {
    public static final String MODULE_ID = "v2.landform.mountain";
    public static final String MODULE_VERSION = "0.1.0-v2-3-10";
    public static final String STAGE_ID = "generate.landform-mountain";
    public static final String RIDGE_MASK_FIELD_ID = "landform.mountain.ridge-mask";
    public static final String PEAK_MASK_FIELD_ID = "landform.mountain.peak-mask";
    public static final String SADDLE_MASK_FIELD_ID = "landform.mountain.saddle-mask";
    public static final String SPUR_MASK_FIELD_ID = "landform.mountain.spur-mask";
    public static final String PROVISIONAL_SURFACE_FIELD_ID = "landform.mountain.provisional-surface";
    public static final String RIDGE_SEGMENT_ID_FIELD_ID = "landform.mountain.ridge-segment-id";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 256;

    private static final List<String> PROVIDED = List.of(
            RIDGE_MASK_FIELD_ID,
            PEAK_MASK_FIELD_ID,
            SADDLE_MASK_FIELD_ID,
            SPUR_MASK_FIELD_ID,
            PROVISIONAL_SURFACE_FIELD_ID,
            RIDGE_SEGMENT_ID_FIELD_ID);
    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE, TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE),
            List.of(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID),
            PROVIDED,
            PROVIDED.stream()
                    .map(field -> new ModuleDescriptorV2.FieldWrite(
                            field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER))
                    .toList(),
            STAGE_ID,
            MAXIMUM_SUPPORT_RADIUS_XZ,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of("feature.mountain.validator"),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
