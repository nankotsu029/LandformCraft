package com.github.nankotsu029.landformcraft.validation.v2.hydrology;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta.HydrologyDeltaModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.HydrologyLakeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.HydrologyRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.HydrologyTidalModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.HydrologyWaterfallModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.LandformFjordModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mountain.LandformMountainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic.LandformVolcanicModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;

import java.util.List;

/** Built-in supported descriptor for offline field-only hydrology validation and diagnostics. */
public final class HydrologyValidationPreviewModuleV2 {
    public static final String MODULE_ID = "v2.hydrology.validation-preview";
    public static final String MODULE_VERSION = "0.1.0-v2-3-13";
    public static final String STAGE_ID = "validate.hydrology";

    private static final List<String> REQUIRED_FIELDS = List.of(
            HydrologyIrModuleV2.WATER_BODY_ID_FIELD,
            HydrologyIrModuleV2.FLOW_DIRECTION_FIELD,
            HydrologyIrModuleV2.FLOW_ACCUMULATION_FIELD,
            HydrologyIrModuleV2.BED_ELEVATION_FIELD,
            HydrologyIrModuleV2.WATER_SURFACE_FIELD,
            HydrologyIrModuleV2.WATER_DEPTH_FIELD,
            HydrologyRiverModuleV2.CHANNEL_MASK_FIELD_ID,
            HydrologyLakeModuleV2.BASIN_MASK_FIELD_ID,
            HydrologyLakeModuleV2.RIM_MASK_FIELD_ID,
            HydrologyLakeModuleV2.SPILLWAY_MASK_FIELD_ID,
            HydrologyDeltaModuleV2.CHANNEL_MASK_FIELD_ID,
            HydrologyDeltaModuleV2.BRANCH_INDEX_FIELD_ID,
            HydrologyTidalModuleV2.MARINE_CONNECTION_FIELD_ID,
            LandformFjordModuleV2.CHANNEL_MASK_FIELD_ID,
            LandformFjordModuleV2.THALWEG_DEPTH_FIELD_ID,
            HydrologyWaterfallModuleV2.LIP_MASK_FIELD_ID,
            HydrologyWaterfallModuleV2.BASE_MASK_FIELD_ID,
            LandformMountainModuleV2.RIDGE_MASK_FIELD_ID,
            LandformVolcanicModuleV2.ISLAND_MASK_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID, MODULE_VERSION, ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(), REQUIRED_FIELDS, List.of(), List.of(), STAGE_ID, 0, 0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of(
                    "feature.meandering-river.validator", "feature.lake.validator",
                    "feature.delta.validator", "feature.tidal-channel-network.validator",
                    "feature.fjord.validator", "feature.waterfall.validator",
                    "feature.alpine-mountain-range.validator", "feature.glacial-mountain-range.validator",
                    "feature.volcanic-archipelago.validator", "hydrology.reconciliation"),
            List.of(
                    "feature.meandering-river.preview", "feature.lake.preview",
                    "feature.delta.preview", "feature.tidal-channel-network.preview",
                    "feature.fjord.preview", "feature.waterfall.preview",
                    "feature.alpine-mountain-range.preview", "feature.glacial-mountain-range.preview",
                    "feature.volcanic-archipelago.preview", "hydrology.basin.preview",
                    "hydrology.residual.preview"));

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }

    public static String dependsOnStageId() {
        return HydrologyReconciliationModuleV2.STAGE_ID;
    }
}
