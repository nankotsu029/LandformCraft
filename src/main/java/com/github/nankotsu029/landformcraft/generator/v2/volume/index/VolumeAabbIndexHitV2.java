package com.github.nankotsu029.landformcraft.generator.v2.volume.index;

import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

/**
 * One overlap hit from an AABB index query. Candidates are returned in ascending operator
 * ordinal order.
 */
public record VolumeAabbIndexHitV2(
        String entryId,
        String operatorId,
        int ordinal,
        VolumeSdfAabbV2 indexedAabb
) {
    public VolumeAabbIndexHitV2 {
        if (entryId == null || entryId.isBlank()) {
            throw new IllegalArgumentException("entryId");
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal");
        }
        if (indexedAabb == null) {
            throw new IllegalArgumentException("indexedAabb");
        }
    }
}
