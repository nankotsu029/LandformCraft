package com.github.nankotsu029.landformcraft.validation.v2.coast;

import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;

import java.util.List;

/** Built-in descriptor for V2-2-08 field-only coastal validation and diagnostics. */
public final class CoastalValidationPreviewModuleV2 {
    public static final String MODULE_ID = "v2.coast.validation-preview";
    public static final String MODULE_VERSION = "0.1.0-v2-2-08";
    public static final String STAGE_ID = "validate.coastal";

    private static final List<String> REQUIRED_FIELDS = List.of(
            CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID,
            CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID,
            CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID,
            CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID,
            CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID,
            CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID,
            CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID,
            CoastalFoundationModuleV2.CAPE_ROCK_EXPOSURE_FIELD_ID,
            CoastalFoundationModuleV2.CAPE_DESCRIPTOR_INDEX_FIELD_ID,
            CoastalTransitionModuleV2.LAND_WATER_FIELD_ID,
            CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID,
            CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID,
            CoastalTransitionModuleV2.CONFLICT_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID, MODULE_VERSION, ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(), REQUIRED_FIELDS, List.of(), List.of(), STAGE_ID, 0, 0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of(
                    "feature.sandy-beach.validator", "feature.harbor-basin.validator",
                    "feature.breakwater-harbor.validator", "feature.rocky-cape.validator"),
            List.of(
                    "feature.sandy-beach.preview", "feature.harbor-basin.preview",
                    "feature.breakwater-harbor.preview", "feature.rocky-cape.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
