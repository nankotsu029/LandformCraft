package com.github.nankotsu029.landformcraft.preview.v2;

import java.util.Objects;

/** Lazy V2-3 hydrology diagnostic values. Each field is sampled on demand during one PNG render. */
public record HydrologyDiagnosticFieldsV2(
        int width,
        int length,
        int minimumElevationMillionths,
        int maximumElevationMillionths,
        IntField basinId,
        IntField flowDirection,
        IntField flowAccumulation,
        IntField reachGraph,
        IntField bedElevation,
        IntField waterSurface,
        IntField waterBody,
        IntField lakeRimSpill,
        IntField deltaDistributary,
        IntField fjordThalweg,
        IntField waterfallEnvelope,
        IntField constraintResidual
) {
    public static final int NO_DATA = Integer.MIN_VALUE;

    public HydrologyDiagnosticFieldsV2 {
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000
                || minimumElevationMillionths >= maximumElevationMillionths) {
            throw new IllegalArgumentException("invalid hydrology diagnostic dimensions or elevation range");
        }
        Objects.requireNonNull(basinId, "basinId");
        Objects.requireNonNull(flowDirection, "flowDirection");
        Objects.requireNonNull(flowAccumulation, "flowAccumulation");
        Objects.requireNonNull(reachGraph, "reachGraph");
        Objects.requireNonNull(bedElevation, "bedElevation");
        Objects.requireNonNull(waterSurface, "waterSurface");
        Objects.requireNonNull(waterBody, "waterBody");
        Objects.requireNonNull(lakeRimSpill, "lakeRimSpill");
        Objects.requireNonNull(deltaDistributary, "deltaDistributary");
        Objects.requireNonNull(fjordThalweg, "fjordThalweg");
        Objects.requireNonNull(waterfallEnvelope, "waterfallEnvelope");
        Objects.requireNonNull(constraintResidual, "constraintResidual");
    }

    @FunctionalInterface
    public interface IntField {
        int valueAt(int globalX, int globalZ);
    }
}
