package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;

import java.util.Objects;

/**
 * Explicit surface baseline for release-local cells that no coastal feature owns (V2-12-02).
 *
 * <p>V2-2 declares coastal features only; there is no v2 contract for a generic base landform yet.
 * The production export path therefore refuses to guess: the caller states the baseline, and the
 * pipeline applies it verbatim outside every active coastal contributor.</p>
 */
public record SurfaceBaselineV2(
        HardLandWaterSourceV2.Classification classification,
        int landSurfaceY,
        int waterBedY
) {
    public SurfaceBaselineV2 {
        Objects.requireNonNull(classification, "classification");
        if (classification == HardLandWaterSourceV2.Classification.UNSPECIFIED) {
            throw new IllegalArgumentException("surface baseline classification must be LAND or WATER");
        }
    }

    /** Fails closed when the declared baseline does not fit the request bounds. */
    void requireWithin(int minY, int maxY) {
        if (landSurfaceY < minY || landSurfaceY > maxY || waterBedY < minY || waterBedY > maxY) {
            throw new IllegalArgumentException("surface baseline heights are outside the request bounds");
        }
    }

    int surfaceYFor(int landWater) {
        return landWater == 1 ? landSurfaceY : waterBedY;
    }
}
