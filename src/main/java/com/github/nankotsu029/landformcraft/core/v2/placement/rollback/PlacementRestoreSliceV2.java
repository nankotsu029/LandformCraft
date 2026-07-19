package com.github.nankotsu029.landformcraft.core.v2.placement.rollback;

import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One bounded, scheduler-dispatched restore slice. Restore writes the snapshotted block states
 * back verbatim; there is no solid／air／fluid pass semantics because the snapshot baseline is the
 * complete pre-apply world content of the effect envelope.
 */
public record PlacementRestoreSliceV2(
        String gatewayContractVersion,
        UUID placementId,
        UUID operationId,
        UUID worldId,
        String tileId,
        int tileIndex,
        long sliceSequence,
        WorldAabbV2 effectRegion,
        List<RestoreBlockV2> blocks
) {
    public static final String GATEWAY_CONTRACT_VERSION = "release-2-placement-restore-slice-v1";

    public PlacementRestoreSliceV2 {
        if (!GATEWAY_CONTRACT_VERSION.equals(gatewayContractVersion)) {
            throw new IllegalArgumentException("unknown placement restore slice version");
        }
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(worldId, "worldId");
        if (tileId == null || tileId.isBlank() || tileIndex < 0 || sliceSequence < 0) {
            throw new IllegalArgumentException("invalid placement restore slice identity");
        }
        Objects.requireNonNull(effectRegion, "effectRegion");
        Objects.requireNonNull(blocks, "blocks");
        blocks = List.copyOf(blocks);
        if (blocks.isEmpty()
                || blocks.size() > PlacementRollbackLimitsV2.MAXIMUM_RESTORE_SLICE_BLOCKS) {
            throw new IllegalArgumentException("placement restore slice size out of range");
        }
        for (RestoreBlockV2 block : blocks) {
            Objects.requireNonNull(block, "blocks");
            if (!effectRegion.contains(block.x(), block.y(), block.z())) {
                throw new IllegalArgumentException("restore block outside its slice effect region");
            }
        }
    }

    /** One snapshotted block state to write back. */
    public record RestoreBlockV2(int x, int y, int z, String blockState) {
        public RestoreBlockV2 {
            if (blockState == null || blockState.isEmpty() || blockState.length() > 256) {
                throw new IllegalArgumentException("invalid restore block state");
            }
        }
    }
}
