package com.github.nankotsu029.landformcraft.validation.v2.environment;

/**
 * Bounded environment field sampler used by the independent V2-4-13 validator and preview factory.
 * Implementations must not call feature generators or retain generator-private arrays.
 */
@FunctionalInterface
public interface EnvironmentFieldSamplerV2 {
    EnvironmentCellSnapshotV2 at(int globalX, int globalZ);

    default int width() {
        throw new UnsupportedOperationException("width must be provided by EnvironmentValidationInputV2");
    }

    default int length() {
        throw new UnsupportedOperationException("length must be provided by EnvironmentValidationInputV2");
    }
}
