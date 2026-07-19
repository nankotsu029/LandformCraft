package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.validation.v2.volume.VolumeCellSamplerV2;
import com.github.nankotsu029.landformcraft.validation.v2.volume.VolumeCellSnapshotV2;

import java.util.Objects;

/** Builds lazy diagnostic fields from a public volume cell sampler without materializing dense arrays. */
public final class VolumeDiagnosticFieldFactoryV2 {
    private VolumeDiagnosticFieldFactoryV2() {
    }

    public static VolumeDiagnosticFieldsV2 create(int width, int length, VolumeCellSamplerV2 sampler) {
        Objects.requireNonNull(sampler, "sampler");
        if (width < 1 || width > 256 || length < 1 || length > 256) {
            throw new IllegalArgumentException("invalid volume diagnostic dimensions");
        }
        return new VolumeDiagnosticFieldsV2(
                width,
                length,
                (x, z) -> cell(sampler, x, z).aabbMask(),
                (x, z) -> cell(sampler, x, z).operatorOrdinal(),
                (x, z) -> cell(sampler, x, z).ySliceOccupancy(),
                (x, z) -> cell(sampler, x, z).solidFluidCode(),
                (x, z) -> cell(sampler, x, z).surfaceClassCode());
    }

    private static VolumeCellSnapshotV2 cell(VolumeCellSamplerV2 sampler, int x, int z) {
        return Objects.requireNonNull(sampler.at(x, z), "volume cell");
    }
}
