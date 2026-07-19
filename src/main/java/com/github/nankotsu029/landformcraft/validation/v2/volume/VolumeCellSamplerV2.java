package com.github.nankotsu029.landformcraft.validation.v2.volume;

/** Public XZ sampler of projected volume diagnostic cells. */
@FunctionalInterface
public interface VolumeCellSamplerV2 {
    VolumeCellSnapshotV2 at(int globalX, int globalZ);
}
