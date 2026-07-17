package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta.HydrologyDeltaModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.HydrologyLakeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.HydrologyRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.HydrologyWaterfallModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.LandformFjordModuleV2;
import com.github.nankotsu029.landformcraft.validation.v2.hydrology.HydrologyValidationInputV2;
import com.github.nankotsu029.landformcraft.validation.v2.hydrology.HydrologyValidationReportV2;

import java.util.Objects;

/** Converts the same field-only validation input into lazy preview fields without retaining a raster. */
public final class HydrologyDiagnosticFieldFactoryV2 {
    private HydrologyDiagnosticFieldFactoryV2() {
    }

    public static HydrologyDiagnosticFieldsV2 create(
            HydrologyValidationInputV2 input,
            HydrologyValidationReportV2 report
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(report, "report");
        int min = Math.multiplyExact(input.blueprint().space().bounds().minY(), 1_000_000);
        int max = Math.multiplyExact(input.blueprint().space().bounds().maxY(), 1_000_000);
        var fields = input.fields();
        return new HydrologyDiagnosticFieldsV2(
                fields.width(), fields.length(), min, max,
                (x, z) -> fields.valueAt(HydrologyIrModuleV2.WATER_BODY_ID_FIELD, x, z),
                (x, z) -> fields.valueAt(HydrologyIrModuleV2.FLOW_DIRECTION_FIELD, x, z),
                (x, z) -> fields.valueAt(HydrologyIrModuleV2.FLOW_ACCUMULATION_FIELD, x, z),
                (x, z) -> overlay(
                        fields.valueAt(HydrologyRiverModuleV2.CHANNEL_MASK_FIELD_ID, x, z),
                        fields.valueAt(HydrologyDeltaModuleV2.CHANNEL_MASK_FIELD_ID, x, z)),
                (x, z) -> fields.valueAt(HydrologyIrModuleV2.BED_ELEVATION_FIELD, x, z),
                (x, z) -> fields.valueAt(HydrologyIrModuleV2.WATER_SURFACE_FIELD, x, z),
                (x, z) -> fields.valueAt(HydrologyIrModuleV2.WATER_BODY_ID_FIELD, x, z),
                (x, z) -> overlay(
                        fields.valueAt(HydrologyLakeModuleV2.RIM_MASK_FIELD_ID, x, z),
                        fields.valueAt(HydrologyLakeModuleV2.SPILLWAY_MASK_FIELD_ID, x, z) == 1 ? 2 : 0),
                (x, z) -> fields.valueAt(HydrologyDeltaModuleV2.BRANCH_INDEX_FIELD_ID, x, z),
                (x, z) -> fields.valueAt(LandformFjordModuleV2.THALWEG_DEPTH_FIELD_ID, x, z),
                (x, z) -> overlay(
                        fields.valueAt(HydrologyWaterfallModuleV2.LIP_MASK_FIELD_ID, x, z),
                        fields.valueAt(HydrologyWaterfallModuleV2.BASE_MASK_FIELD_ID, x, z) == 1 ? 2 : 0),
                (x, z) -> residual(fields, x, z, report)
        );
    }

    private static int overlay(int first, int second) {
        if (first == HydrologyValidationInputV2.NO_DATA && second == HydrologyValidationInputV2.NO_DATA) {
            return HydrologyDiagnosticFieldsV2.NO_DATA;
        }
        if (first == HydrologyValidationInputV2.NO_DATA) return second;
        if (second == HydrologyValidationInputV2.NO_DATA) return first;
        return Math.max(first, second);
    }

    private static int residual(
            com.github.nankotsu029.landformcraft.validation.v2.hydrology.HydrologyFieldSamplerV2 fields,
            int x,
            int z,
            HydrologyValidationReportV2 report
    ) {
        int lakeSpill = fields.valueAt(HydrologyLakeModuleV2.SPILLWAY_MASK_FIELD_ID, x, z);
        if (lakeSpill == 1) return 0;
        return report.passesHardValidation() ? 0 : 1;
    }
}
