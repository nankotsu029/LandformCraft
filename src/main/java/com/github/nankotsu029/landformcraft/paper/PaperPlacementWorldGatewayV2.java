package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.worldedit.v2.PlacementWorldMutationAccessV2;
import com.github.nankotsu029.landformcraft.worldedit.v2.WorldEditBlockMutationAccessV2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Paper scheduler adapter for the feature-neutral Release 2 block stream. Artifact/source work
 * stays on the caller's apply/settle/verify worker; only bounded Bukkit/WorldEdit reads and writes
 * run on Paper.
 */
public final class PaperPlacementWorldGatewayV2 implements PlacementWorldGatewayV2 {
    public static final int DEFAULT_READ_SLICE_BLOCKS = 1_024;

    private final PaperSchedulerV2 dispatcher;
    private final PlacementWorldMutationAccessV2 worldEdit;
    private final int readSliceBlocks;

    public PaperPlacementWorldGatewayV2(PaperMainThreadDispatcher dispatcher) {
        this(dispatcher, new WorldEditBlockMutationAccessV2(), DEFAULT_READ_SLICE_BLOCKS);
    }

    PaperPlacementWorldGatewayV2(
            PaperSchedulerV2 dispatcher,
            PlacementWorldMutationAccessV2 worldEdit,
            int readSliceBlocks
    ) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.worldEdit = Objects.requireNonNull(worldEdit, "worldEdit");
        if (readSliceBlocks < 1 || readSliceBlocks > 4_096) {
            throw new IllegalArgumentException("readSliceBlocks out of range");
        }
        this.readSliceBlocks = readSliceBlocks;
    }

    @Override
    public void streamRegionBlockStates(
            UUID worldId,
            WorldAabbV2 region,
            PlacementBlockStateConsumerV2 consumer
    ) throws IOException {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(consumer, "consumer");
        if (dispatcher.isMainThread()) {
            throw new IOException("snapshot streaming must not block the Paper primary thread");
        }
        long index = 0L;
        while (index < region.volumeBlocks()) {
            int count = Math.toIntExact(Math.min(readSliceBlocks, region.volumeBlocks() - index));
            long sliceStart = index;
            List<PlacementWorldMutationAccessV2.ReadBlockV2> blocks = await(dispatcher.supply(
                    () -> worldEdit.readCanonicalSlice(worldId, region, sliceStart, count)));
            if (blocks.size() != count) {
                throw new IOException("Paper world read slice returned the wrong block count");
            }
            for (PlacementWorldMutationAccessV2.ReadBlockV2 block : blocks) {
                consumer.accept(block.x(), block.y(), block.z(), block.blockState());
            }
            index += count;
        }
    }

    @Override
    public CompletionStage<PlacementApplySliceReceiptV2> applyBlockSlice(PlacementApplySliceV2 slice) {
        Objects.requireNonNull(slice, "slice");
        return dispatcher.supply(() -> {
            boolean mainThread = dispatcher.isMainThread();
            PlacementWorldMutationAccessV2.AppliedSliceV2 applied = worldEdit.apply(slice);
            return new PlacementApplySliceReceiptV2(
                    slice.operationId(),
                    slice.tileId(),
                    slice.sliceSequence(),
                    applied.appliedMutations(),
                    true,
                    mainThread,
                    applied.resourcesClosed());
        });
    }

    @Override
    public CompletionStage<PlacementSettleTickReceiptV2> advanceSettleTick(PlacementSettleTickV2 tick) {
        Objects.requireNonNull(tick, "tick");
        return dispatcher.supply(() -> new PlacementSettleTickReceiptV2(
                tick.operationId(),
                tick.tickIndex(),
                true,
                dispatcher.isMainThread(),
                0,
                0,
                List.of()));
    }

    @Override
    public CompletionStage<PlacementRestoreSliceReceiptV2> restoreBlockSlice(
            PlacementRestoreSliceV2 slice
    ) {
        Objects.requireNonNull(slice, "slice");
        return dispatcher.supply(() -> {
            boolean mainThread = dispatcher.isMainThread();
            PlacementWorldMutationAccessV2.AppliedSliceV2 restored = worldEdit.restore(slice);
            return new PlacementRestoreSliceReceiptV2(
                    slice.operationId(), slice.tileId(), slice.sliceSequence(),
                    restored.appliedMutations(), true, mainThread, restored.resourcesClosed());
        });
    }

    @Override
    public CompletionStage<PlacementVerifyReadSliceReceiptV2> readVerifySlice(
            PlacementVerifyReadSliceV2 slice
    ) {
        Objects.requireNonNull(slice, "slice");
        return dispatcher.supply(() -> {
            List<PlacementWorldMutationAccessV2.ReadBlockV2> blocks = worldEdit.readCanonicalSlice(
                    slice.worldId(),
                    slice.effectEnvelope(),
                    slice.startIndex(),
                    slice.blockCount());
            List<String> states = new ArrayList<>(blocks.size());
            for (PlacementWorldMutationAccessV2.ReadBlockV2 block : blocks) {
                states.add(block.blockState());
            }
            return new PlacementVerifyReadSliceReceiptV2(
                    slice.operationId(),
                    slice.sliceSequence(),
                    true,
                    dispatcher.isMainThread(),
                    states);
        });
    }

    private static <T> T await(CompletionStage<T> stage) throws IOException {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof UncheckedIOException unchecked) {
                throw unchecked.getCause();
            }
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("Paper scheduler world read failed", cause == null ? exception : cause);
        }
    }
}
