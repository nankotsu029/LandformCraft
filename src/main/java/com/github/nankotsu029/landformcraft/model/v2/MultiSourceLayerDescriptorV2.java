package com.github.nankotsu029.landformcraft.model.v2;

/**
 * Immutable descriptor for one published multi-source input layer (samples live in sidecars or
 * caller memory; this record never embeds rasters).
 */
public record MultiSourceLayerDescriptorV2(
        String sourceId,
        ImageFidelitySourceKindV2 kind,
        ImageFidelityStrengthV2 strength,
        int noDataSample,
        String samplesSha256,
        long samplesByteLength
) {
    public MultiSourceLayerDescriptorV2 {
        sourceId = V2Validation.qualifiedId(sourceId, "sourceId");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (strength == null) {
            throw new IllegalArgumentException("strength is required");
        }
        if (strength != kind.defaultStrength()) {
            throw new IllegalArgumentException(
                    "strength must match the frozen default for kind " + kind.name());
        }
        if (noDataSample < 0 || noDataSample > 255) {
            throw new IllegalArgumentException("noDataSample must be within 0..255");
        }
        samplesSha256 = V2Validation.checksum(samplesSha256, "samplesSha256");
        if (samplesByteLength < 1) {
            throw new IllegalArgumentException("samplesByteLength must be positive");
        }
    }
}
