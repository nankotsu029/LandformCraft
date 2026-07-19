package com.github.nankotsu029.landformcraft.preview.v2;

import java.util.Objects;

/** Lazy V2-5-15 volume diagnostic values. Each field is sampled on demand during one PNG render. */
public record VolumeDiagnosticFieldsV2(
        int width,
        int length,
        IntField aabbFootprint,
        IntField operatorOrdinal,
        IntField ySlice,
        IntField solidFluid,
        IntField surfaceClass
) {
    public VolumeDiagnosticFieldsV2 {
        if (width < 1 || width > 256 || length < 1 || length > 256) {
            throw new IllegalArgumentException("invalid volume diagnostic dimensions");
        }
        Objects.requireNonNull(aabbFootprint, "aabbFootprint");
        Objects.requireNonNull(operatorOrdinal, "operatorOrdinal");
        Objects.requireNonNull(ySlice, "ySlice");
        Objects.requireNonNull(solidFluid, "solidFluid");
        Objects.requireNonNull(surfaceClass, "surfaceClass");
    }

    @FunctionalInterface
    public interface IntField {
        int valueAt(int globalX, int globalZ);
    }
}
