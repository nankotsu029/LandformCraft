package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.model.v2.ImageFidelityReconcileRoleV2;
import com.github.nankotsu029.landformcraft.model.v2.ImageFidelitySourceKindV2;
import com.github.nankotsu029.landformcraft.model.v2.ImageFidelityStrengthV2;

import java.util.Objects;

/** One in-memory proposal layer for multi-source reconciliation. */
public record MultiSourceProposalLayerV2(
        String sourceId,
        ImageFidelitySourceKindV2 kind,
        ImageFidelityStrengthV2 strength,
        int noDataSample,
        byte[] samples
) {
    public MultiSourceProposalLayerV2 {
        Objects.requireNonNull(sourceId, "sourceId");
        if (sourceId.isBlank() || sourceId.length() > 128) {
            throw new IllegalArgumentException("sourceId is invalid");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(strength, "strength");
        if (strength != kind.defaultStrength()) {
            throw new IllegalArgumentException("strength must match kind default");
        }
        if (noDataSample < 0 || noDataSample > 255) {
            throw new IllegalArgumentException("noDataSample must be within 0..255");
        }
        samples = Objects.requireNonNull(samples, "samples").clone();
        if (samples.length < 1) {
            throw new IllegalArgumentException("samples must not be empty");
        }
    }

    public int sampleAt(int index) {
        return Byte.toUnsignedInt(samples[index]);
    }

    public boolean isPresent(int index) {
        return sampleAt(index) != noDataSample;
    }
}
