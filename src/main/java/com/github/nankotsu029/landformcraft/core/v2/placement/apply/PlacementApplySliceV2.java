package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One bounded, scheduler-dispatched mutation slice. */
public record PlacementApplySliceV2(
        String gatewayContractVersion,
        UUID placementId,
        UUID operationId,
        UUID worldId,
        String tileId,
        int tileIndex,
        PlacementApplyPassV2 pass,
        int overlayOrdinal,
        long sliceSequence,
        WorldAabbV2 mutationRegion,
        List<PlacementDesiredBlockV2> mutations,
        String sourceFingerprint
) {
    public static final String GATEWAY_CONTRACT_VERSION = "release-2-placement-apply-slice-v1";

    public PlacementApplySliceV2 {
        if (!GATEWAY_CONTRACT_VERSION.equals(gatewayContractVersion)) {
            throw new IllegalArgumentException("unknown placement apply slice version");
        }
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(worldId, "worldId");
        if (tileId == null || tileId.isBlank() || tileIndex < 0 || sliceSequence < 0) {
            throw new IllegalArgumentException("invalid placement apply slice identity");
        }
        Objects.requireNonNull(pass, "pass");
        if (overlayOrdinal < 0 || overlayOrdinal > PlacementDesiredBlockV2.MAXIMUM_OVERLAY_ORDINAL) {
            throw new IllegalArgumentException("slice overlay ordinal out of range");
        }
        Objects.requireNonNull(mutationRegion, "mutationRegion");
        Objects.requireNonNull(mutations, "mutations");
        mutations = List.copyOf(mutations);
        if (mutations.isEmpty() || mutations.size() > 4_096) {
            throw new IllegalArgumentException("placement apply slice size out of range");
        }
        for (PlacementDesiredBlockV2 mutation : mutations) {
            Objects.requireNonNull(mutation, "mutations");
            if (mutation.pass() != pass || mutation.overlayOrdinal() != overlayOrdinal
                    || mutation.ownerTileIndex() != tileIndex
                    || !mutationRegion.contains(mutation.x(), mutation.y(), mutation.z())) {
                throw new IllegalArgumentException("mutation does not match its apply slice");
            }
        }
        if (sourceFingerprint == null || !sourceFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("slice sourceFingerprint must be sha-256");
        }
    }
}
