package com.github.nankotsu029.landformcraft.core.v2.placement.snapshot;

import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Release 2 world gateway boundary (V2-6-04). WorldEdit／Paper adapters implement this contract in
 * V2-6 adapters implement this contract. CPU and artifact I/O entry points must run off the Paper
 * main thread; every Bukkit／WorldEdit world access must be dispatched through the platform
 * scheduler internally.
 *
 * <p>Snapshot-all invokes only {@link #streamRegionBlockStates}. The mutation entry point is part
 * of the same contract so orchestration (V2-6-06) shares one gateway and V2-6-04 tests can prove
 * the all-before-any-apply invariant: no apply call is ever made while snapshots are incomplete.
 */
public interface PlacementWorldGatewayV2 {
    String GATEWAY_CONTRACT_VERSION = "release-2-placement-world-gateway-v1";

    /**
     * Streams every block state of the inclusive region exactly once in canonical order — X
     * fastest, then Z, then Y — as canonical block-state strings (for example
     * {@code minecraft:water[level=0]}). Implementations must visit exactly
     * {@code region.volumeBlocks()} positions.
     */
    void streamRegionBlockStates(
            UUID worldId,
            WorldAabbV2 region,
            PlacementBlockStateConsumerV2 consumer
    ) throws IOException;

    /**
     * Legacy V2-6-04 probe retained only so snapshot tests can prove that snapshot-all performs no
     * mutation. V2-6-06 uses {@link #applyBlockSlice(PlacementApplySliceV2)}.
     */
    default void applyTileBlockStates(UUID worldId, String tileId, WorldAabbV2 mutationRegion)
            throws IOException {
        throw new UnsupportedOperationException("use bounded asynchronous applyBlockSlice");
    }

    /**
     * Accepts one bounded mutation slice and completes only after the scheduler operation finished
     * and its WorldEdit／FAWE resources closed. The returned stage is observational: cancelling or
     * timing out a derived observer must not cancel a scheduler-accepted world operation.
     */
    default CompletionStage<PlacementApplySliceReceiptV2> applyBlockSlice(PlacementApplySliceV2 slice) {
        return CompletableFuture.<PlacementApplySliceReceiptV2>failedFuture(
                new UnsupportedOperationException("Release 2 apply is not implemented by this gateway"))
                .minimalCompletionStage();
    }

    /**
     * Accepts one bounded restore slice that writes snapshotted block states back verbatim
     * (V2-6-08). Completes only after the scheduler operation finished and its WorldEdit／FAWE
     * resources closed. Cancelling or timing out a derived observer must not cancel a
     * scheduler-accepted restore.
     */
    default CompletionStage<PlacementRestoreSliceReceiptV2> restoreBlockSlice(
            PlacementRestoreSliceV2 slice
    ) {
        return CompletableFuture.<PlacementRestoreSliceReceiptV2>failedFuture(
                new UnsupportedOperationException(
                        "Release 2 restore is not implemented by this gateway"))
                .minimalCompletionStage();
    }

    /**
     * Advances one bounded settle tick for the effect envelope. Implementations must count every
     * observed block-state change and report those outside the envelope without dropping them.
     */
    default CompletionStage<PlacementSettleTickReceiptV2> advanceSettleTick(PlacementSettleTickV2 tick) {
        return CompletableFuture.<PlacementSettleTickReceiptV2>failedFuture(
                new UnsupportedOperationException(
                        "Release 2 settle tick is not implemented by this gateway"))
                .minimalCompletionStage();
    }

    /**
     * Reads one bounded verify slice of the effect envelope in canonical X-fastest→Z→Y order.
     * Cancelling a derived observer must not cancel a scheduler-accepted read.
     */
    default CompletionStage<PlacementVerifyReadSliceReceiptV2> readVerifySlice(
            PlacementVerifyReadSliceV2 slice
    ) {
        return CompletableFuture.<PlacementVerifyReadSliceReceiptV2>failedFuture(
                new UnsupportedOperationException(
                        "Release 2 verify-read is not implemented by this gateway"))
                .minimalCompletionStage();
    }

    /** Bounded per-block consumer; must not retain the whole region. */
    @FunctionalInterface
    interface PlacementBlockStateConsumerV2 {
        void accept(int x, int y, int z, String blockState) throws IOException;
    }
}
