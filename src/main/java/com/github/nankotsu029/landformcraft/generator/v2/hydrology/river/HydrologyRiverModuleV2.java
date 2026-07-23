package com.github.nankotsu029.landformcraft.generator.v2.hydrology.river;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/**
 * Built-in supported module for offline RIVER / MEANDERING_RIVER reach shaping.
 *
 * <p>V2-15-10 / ADR 0039 Candidate A: {@code RIVER} compiles into the same
 * {@code MeanderingRiverPlanV2} shape as {@code MEANDERING_RIVER} (bridged by
 * {@code MeanderingRiverSubtypeBridgeV2}), so both kinds share this module binding, the shared
 * hydrology-plan pipeline, and the same field set below. Neither kind's Paper column is promoted by
 * this Task.</p>
 */
public final class HydrologyRiverModuleV2 {
    public static final String MODULE_ID = "v2.hydrology.river";
    public static final String MODULE_VERSION = "0.1.0-v2-3-03";
    public static final String STAGE_ID = "generate.hydrology-river";

    public static final String CHANNEL_MASK_FIELD_ID = "hydrology.river.channel-mask";
    public static final String BANK_MASK_FIELD_ID = "hydrology.river.bank-mask";
    public static final String FLOODPLAIN_MASK_FIELD_ID = "hydrology.river.floodplain-mask";
    public static final String MEANDER_CORRIDOR_FIELD_ID = "hydrology.river.meander-corridor";
    public static final String LOCAL_WIDTH_FIELD_ID = "hydrology.river.local-width";
    public static final String DISCHARGE_INDEX_FIELD_ID = "hydrology.river.discharge-index";

    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 256;

    private static final List<String> REQUIRED = List.of(
            HydrologyIrModuleV2.FLOW_DIRECTION_FIELD,
            HydrologyIrModuleV2.FLOW_ACCUMULATION_FIELD);
    private static final List<String> PROVIDED = List.of(
            BANK_MASK_FIELD_ID,
            CHANNEL_MASK_FIELD_ID,
            DISCHARGE_INDEX_FIELD_ID,
            FLOODPLAIN_MASK_FIELD_ID,
            LOCAL_WIDTH_FIELD_ID,
            MEANDER_CORRIDOR_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(TerrainIntentV2.FeatureKind.MEANDERING_RIVER, TerrainIntentV2.FeatureKind.RIVER),
            REQUIRED,
            PROVIDED,
            PROVIDED.stream()
                    .map(field -> new ModuleDescriptorV2.FieldWrite(
                            field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER))
                    .toList(),
            STAGE_ID,
            MAXIMUM_SUPPORT_RADIUS_XZ,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of("feature.meandering-river.validator"),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
