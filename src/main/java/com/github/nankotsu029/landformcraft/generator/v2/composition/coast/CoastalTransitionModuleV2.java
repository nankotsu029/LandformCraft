package com.github.nankotsu029.landformcraft.generator.v2.composition.coast;

import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;

import java.util.List;

/** Built-in descriptor for the dedicated coastal field compositor. */
public final class CoastalTransitionModuleV2 {
    public static final String MODULE_ID = "v2.coast.transition";
    public static final String MODULE_VERSION = "0.1.0-v2-2-07";
    public static final String STAGE_ID = "compose.coastal-transitions";
    public static final String LAND_WATER_FIELD_ID = "coastal.composed.land-water";
    public static final String SURFACE_HEIGHT_FIELD_ID = "coastal.composed.surface-height";
    public static final String OWNER_INDEX_FIELD_ID = "coastal.composed.owner-index";
    public static final String BLEND_WEIGHT_FIELD_ID = "coastal.composed.blend-weight";
    public static final String CONFLICT_FIELD_ID = "coastal.composed.conflict";
    public static final int REQUIRED_HALO_XZ = 32;

    private static final List<String> INPUT_FIELDS = List.of(
            CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID,
            CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID,
            CoastalFoundationModuleV2.BEACH_SEMANTIC_SAND_FIELD_ID,
            CoastalFoundationModuleV2.BEACH_SURFACE_HEIGHT_FIELD_ID,
            CoastalFoundationModuleV2.BREAKWATER_ARM_INDEX_FIELD_ID,
            CoastalFoundationModuleV2.BREAKWATER_BOTTOM_HEIGHT_FIELD_ID,
            CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID,
            CoastalFoundationModuleV2.BREAKWATER_TOP_HEIGHT_FIELD_ID,
            CoastalFoundationModuleV2.CAPE_DESCRIPTOR_INDEX_FIELD_ID,
            CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID,
            CoastalFoundationModuleV2.CAPE_ROCK_EXPOSURE_FIELD_ID,
            CoastalFoundationModuleV2.CAPE_SURFACE_HEIGHT_FIELD_ID,
            CoastalFoundationModuleV2.HARBOR_BOTTOM_HEIGHT_FIELD_ID,
            CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID,
            CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID,
            CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID);

    private static final List<String> OUTPUT_FIELDS = List.of(
            BLEND_WEIGHT_FIELD_ID,
            CONFLICT_FIELD_ID,
            LAND_WATER_FIELD_ID,
            OWNER_INDEX_FIELD_ID,
            SURFACE_HEIGHT_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(),
            INPUT_FIELDS,
            OUTPUT_FIELDS,
            OUTPUT_FIELDS.stream().map(field -> new ModuleDescriptorV2.FieldWrite(
                    field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)).toList(),
            STAGE_ID,
            REQUIRED_HALO_XZ,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of(),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }

    public static List<String> inputFields() {
        return INPUT_FIELDS;
    }
}
