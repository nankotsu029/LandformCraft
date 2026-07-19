package com.github.nankotsu029.landformcraft.worldedit.v2;

import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.CanonicalBlockStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.util.List;
import java.util.UUID;

/** Synchronous main-thread world access port; the Paper gateway owns scheduler dispatch. */
public interface PlacementWorldMutationAccessV2 {
    List<ReadBlockV2> readCanonicalSlice(
            UUID worldId,
            WorldAabbV2 region,
            long startIndex,
            int blockCount
    );

    AppliedSliceV2 apply(PlacementApplySliceV2 slice);

    default AppliedSliceV2 restore(PlacementRestoreSliceV2 slice) {
        throw new UnsupportedOperationException("Release 2 restore is not implemented");
    }

    record ReadBlockV2(int x, int y, int z, String blockState) {
        public ReadBlockV2 {
            blockState = CanonicalBlockStateV2.requireCanonical(blockState);
        }
    }

    record AppliedSliceV2(int appliedMutations, boolean resourcesClosed) {
        public AppliedSliceV2 {
            if (appliedMutations < 1) {
                throw new IllegalArgumentException("appliedMutations must be positive");
            }
        }
    }
}
