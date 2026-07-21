package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.model.v2.ImageFidelityReconcileRoleV2;

import java.util.List;
import java.util.Objects;

/** Explicit multi-source reconciliation controls. */
public record MultiSourceReconciliationOptionsV2(
        ImageFidelityReconcileRoleV2 role,
        int resultNoDataSample,
        List<MultiSourceProposalLayerV2> sources
) {
    public static final int DEFAULT_NODATA = 255;
    public static final int MAXIMUM_SOURCES = 32;

    public MultiSourceReconciliationOptionsV2 {
        Objects.requireNonNull(role, "role");
        if (resultNoDataSample < 0 || resultNoDataSample > 255) {
            throw new IllegalArgumentException("resultNoDataSample must be within 0..255");
        }
        Objects.requireNonNull(sources, "sources");
        if (sources.size() < 2 || sources.size() > MAXIMUM_SOURCES) {
            throw new IllegalArgumentException("sources size must be within 2.." + MAXIMUM_SOURCES);
        }
        sources = List.copyOf(sources);
        int expected = sources.getFirst().samples().length;
        var ids = new java.util.HashSet<String>();
        for (MultiSourceProposalLayerV2 source : sources) {
            if (!ids.add(source.sourceId())) {
                throw new IllegalArgumentException("duplicate sourceId");
            }
            if (source.samples().length != expected) {
                throw new IllegalArgumentException("all sources must share the same sample length");
            }
            if (source.noDataSample() == resultNoDataSample
                    && source.kind().defaultStrength() == com.github.nankotsu029.landformcraft.model.v2.ImageFidelityStrengthV2.HARD) {
                // allowed: hard layers may use same sentinel
            }
        }
    }

    public int width(int length) {
        if (length < 1 || sources.getFirst().samples().length % length != 0) {
            throw new IllegalArgumentException("length does not divide sample count");
        }
        return sources.getFirst().samples().length / length;
    }
}
