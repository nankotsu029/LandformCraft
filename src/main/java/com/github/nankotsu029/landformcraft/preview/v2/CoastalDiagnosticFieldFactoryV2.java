package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationInputV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationReportV2;

import java.util.Objects;

/** Converts the same field-only validation input into lazy preview fields without retaining a raster. */
public final class CoastalDiagnosticFieldFactoryV2 {
    private CoastalDiagnosticFieldFactoryV2() {
    }

    public static CoastalDiagnosticFieldsV2 create(
            CoastalValidationInputV2 input,
            CoastalValidationReportV2 report
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(report, "report");
        int min = Math.multiplyExact(input.blueprint().space().bounds().minY(), 1_000_000);
        int max = Math.multiplyExact(input.blueprint().space().bounds().maxY(), 1_000_000);
        var actual = input.actualFields();
        var desired = input.desiredFields();
        return new CoastalDiagnosticFieldsV2(
                actual.width(), actual.length(), min, max,
                (x, z) -> actual.valueAt(CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID, x, z),
                (x, z) -> actual.valueAt(CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID, x, z),
                (x, z) -> actual.valueAt(CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID, x, z),
                (x, z) -> actual.valueAt(CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID, x, z),
                (x, z) -> desired.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z),
                (x, z) -> actual.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z),
                (x, z) -> residual(
                        desired.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z),
                        actual.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z)),
                (x, z) -> desired.valueAt(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, x, z),
                (x, z) -> actual.valueAt(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, x, z),
                (x, z) -> residual(
                        desired.valueAt(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, x, z),
                        actual.valueAt(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, x, z)),
                (x, z) -> error(actual.valueAt(CoastalTransitionModuleV2.CONFLICT_FIELD_ID, x, z),
                        desired.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z),
                        actual.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z), report)
        );
    }

    private static int residual(int desired, int actual) {
        if (desired == CoastalValidationInputV2.NO_DATA || actual == CoastalValidationInputV2.NO_DATA) {
            return CoastalDiagnosticFieldsV2.NO_DATA;
        }
        return Math.toIntExact((long) actual - desired);
    }

    private static int error(int conflict, int desired, int actual, CoastalValidationReportV2 report) {
        if (conflict != 0 || (desired != CoastalValidationInputV2.NO_DATA && actual != desired)) return 1;
        return report.passesHardValidation() ? 0 : 1;
    }
}
