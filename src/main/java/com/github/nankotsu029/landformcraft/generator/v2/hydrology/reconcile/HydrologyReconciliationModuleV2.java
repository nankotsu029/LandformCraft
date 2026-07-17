package com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta.HydrologyDeltaModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.HydrologyLakeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.HydrologyTidalModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.HydrologyWaterfallModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.LandformFjordModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;

import java.util.List;

/** Built-in supported descriptor for the offline fixed-pass global reconciliation stage. */
public final class HydrologyReconciliationModuleV2 {
    public static final String MODULE_ID = "v2.hydrology.reconcile";
    public static final String MODULE_VERSION = "0.1.0-v2-3-12";
    public static final String STAGE_ID = "reconcile.hydrology";

    private static final List<String> REQUIRED_FIELDS = List.of(
            HydrologyIrModuleV2.BED_ELEVATION_FIELD,
            HydrologyDeltaModuleV2.CHANNEL_MASK_FIELD_ID,
            HydrologyLakeModuleV2.SPILLWAY_MASK_FIELD_ID,
            HydrologyTidalModuleV2.MARINE_CONNECTION_FIELD_ID,
            HydrologyWaterfallModuleV2.BASE_ELEVATION_FIELD_ID,
            HydrologyWaterfallModuleV2.LIP_ELEVATION_FIELD_ID,
            LandformFjordModuleV2.CHANNEL_MASK_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(),
            REQUIRED_FIELDS,
            List.of(),
            List.of(),
            STAGE_ID,
            0,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of("hydrology.reconciliation"),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
