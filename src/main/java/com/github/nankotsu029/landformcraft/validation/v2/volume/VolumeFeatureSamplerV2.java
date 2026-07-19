package com.github.nankotsu029.landformcraft.validation.v2.volume;

import java.util.List;

/** Public sampler of sealed volume feature descriptors for independent validation. */
@FunctionalInterface
public interface VolumeFeatureSamplerV2 {
    List<VolumeFeatureSnapshotV2> features();
}
