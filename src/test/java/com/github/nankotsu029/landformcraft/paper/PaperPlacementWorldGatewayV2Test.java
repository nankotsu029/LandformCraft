package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyPassV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementDesiredBlockV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.worldedit.v2.PlacementWorldMutationAccessV2;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPlacementWorldGatewayV2Test {
    private static final UUID PLACEMENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID WORLD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    void snapshotReadsAreBoundedCanonicalAndDispatchedToMainThread() throws Exception {
        FakeScheduler scheduler = new FakeScheduler();
        FakeWorldAccess access = new FakeWorldAccess(scheduler, true);
        PaperPlacementWorldGatewayV2 gateway = new PaperPlacementWorldGatewayV2(
                scheduler, access, 2);
        WorldAabbV2 region = new WorldAabbV2(4, 70, 8, 8, 70, 8);
        List<String> observed = new ArrayList<>();

        gateway.streamRegionBlockStates(WORLD_ID, region,
                (x, y, z, block) -> observed.add(x + ":" + y + ":" + z + ":" + block));

        assertEquals(List.of(2, 2, 1), access.readCounts);
        assertEquals(List.of(
                "4:70:8:minecraft:stone",
                "5:70:8:minecraft:stone",
                "6:70:8:minecraft:stone",
                "7:70:8:minecraft:stone",
                "8:70:8:minecraft:stone"), observed);
        assertEquals(3, scheduler.submissions);
        assertTrue(access.allCallsOnMainThread);
    }

    @Test
    void snapshotEntryPointRejectsBlockingOnPaperMainThread() {
        FakeScheduler scheduler = new FakeScheduler();
        scheduler.mainThread = true;
        PaperPlacementWorldGatewayV2 gateway = new PaperPlacementWorldGatewayV2(
                scheduler, new FakeWorldAccess(scheduler, true), 2);

        assertThrows(IOException.class, () -> gateway.streamRegionBlockStates(
                WORLD_ID,
                new WorldAabbV2(0, 64, 0, 0, 64, 0),
                (x, y, z, block) -> { }));
        assertEquals(0, scheduler.submissions);
    }

    @Test
    void applyCompletionProvesSchedulerMainThreadAndResourceClose() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeWorldAccess access = new FakeWorldAccess(scheduler, true);
        PaperPlacementWorldGatewayV2 gateway = new PaperPlacementWorldGatewayV2(
                scheduler, access, 2);
        PlacementApplySliceV2 slice = slice();

        PlacementApplySliceReceiptV2 receipt = gateway.applyBlockSlice(slice)
                .toCompletableFuture().join();

        receipt.requireMatches(slice);
        assertTrue(receipt.schedulerAccepted());
        assertTrue(receipt.executedOnMainThread());
        assertTrue(receipt.resourcesClosed());
        assertEquals(1, access.applyCalls);
        assertFalse(scheduler.isMainThread());
    }

    @Test
    void incompleteWorldEditCloseEvidenceIsRejectedByReceiptContract() {
        FakeScheduler scheduler = new FakeScheduler();
        PaperPlacementWorldGatewayV2 gateway = new PaperPlacementWorldGatewayV2(
                scheduler, new FakeWorldAccess(scheduler, false), 2);
        PlacementApplySliceV2 slice = slice();

        PlacementApplySliceReceiptV2 receipt = gateway.applyBlockSlice(slice)
                .toCompletableFuture().join();

        assertFalse(receipt.resourcesClosed());
        assertThrows(IllegalArgumentException.class, () -> receipt.requireMatches(slice));
    }

    @Test
    void restoreCompletionProvesSchedulerMainThreadAndResourceClose() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeWorldAccess access = new FakeWorldAccess(scheduler, true);
        PaperPlacementWorldGatewayV2 gateway = new PaperPlacementWorldGatewayV2(
                scheduler, access, 2);
        WorldAabbV2 region = new WorldAabbV2(0, 64, 0, 0, 64, 0);
        PlacementRestoreSliceV2 slice = new PlacementRestoreSliceV2(
                PlacementRestoreSliceV2.GATEWAY_CONTRACT_VERSION,
                PLACEMENT_ID, OPERATION_ID, WORLD_ID, "tile-x0-z0", 0, 0, region,
                List.of(new PlacementRestoreSliceV2.RestoreBlockV2(
                        0, 64, 0, "minecraft:stone")));

        var receipt = gateway.restoreBlockSlice(slice).toCompletableFuture().join();

        receipt.requireMatches(slice);
        assertEquals(1, access.restoreCalls);
        assertTrue(access.allCallsOnMainThread);
        assertFalse(scheduler.isMainThread());
    }

    private static PlacementApplySliceV2 slice() {
        WorldAabbV2 region = new WorldAabbV2(0, 64, 0, 0, 64, 0);
        return new PlacementApplySliceV2(
                PlacementApplySliceV2.GATEWAY_CONTRACT_VERSION,
                PLACEMENT_ID,
                OPERATION_ID,
                WORLD_ID,
                "tile-x0-z0",
                0,
                PlacementApplyPassV2.SOLID,
                0,
                0,
                region,
                List.of(new PlacementDesiredBlockV2(
                        0, 64, 0, "minecraft:stone", PlacementApplyPassV2.SOLID, 0, 0)),
                "a".repeat(64));
    }

    private static final class FakeScheduler implements PaperSchedulerV2 {
        private boolean mainThread;
        private int submissions;

        @Override
        public <T> CompletionStage<T> supply(Supplier<T> operation) {
            submissions++;
            boolean previous = mainThread;
            mainThread = true;
            try {
                return CompletableFuture.completedFuture(operation.get()).minimalCompletionStage();
            } catch (Throwable failure) {
                return CompletableFuture.<T>failedFuture(failure).minimalCompletionStage();
            } finally {
                mainThread = previous;
            }
        }

        @Override
        public boolean isMainThread() {
            return mainThread;
        }
    }

    private static final class FakeWorldAccess implements PlacementWorldMutationAccessV2 {
        private final FakeScheduler scheduler;
        private final boolean resourcesClosed;
        private final List<Integer> readCounts = new ArrayList<>();
        private boolean allCallsOnMainThread = true;
        private int applyCalls;
        private int restoreCalls;

        private FakeWorldAccess(FakeScheduler scheduler, boolean resourcesClosed) {
            this.scheduler = scheduler;
            this.resourcesClosed = resourcesClosed;
        }

        @Override
        public List<ReadBlockV2> readCanonicalSlice(
                UUID worldId,
                WorldAabbV2 region,
                long startIndex,
                int blockCount
        ) {
            allCallsOnMainThread &= scheduler.isMainThread();
            readCounts.add(blockCount);
            List<ReadBlockV2> blocks = new ArrayList<>();
            long width = (long) region.maxX() - region.minX() + 1L;
            long length = (long) region.maxZ() - region.minZ() + 1L;
            long layer = width * length;
            for (int offset = 0; offset < blockCount; offset++) {
                long index = startIndex + offset;
                long y = index / layer;
                long remainder = index % layer;
                long z = remainder / width;
                long x = remainder % width;
                blocks.add(new ReadBlockV2(
                        Math.toIntExact(region.minX() + x),
                        Math.toIntExact(region.minY() + y),
                        Math.toIntExact(region.minZ() + z),
                        "minecraft:stone"));
            }
            return List.copyOf(blocks);
        }

        @Override
        public AppliedSliceV2 apply(PlacementApplySliceV2 slice) {
            allCallsOnMainThread &= scheduler.isMainThread();
            applyCalls++;
            return new AppliedSliceV2(slice.mutations().size(), resourcesClosed);
        }

        @Override
        public AppliedSliceV2 restore(PlacementRestoreSliceV2 slice) {
            allCallsOnMainThread &= scheduler.isMainThread();
            restoreCalls++;
            return new AppliedSliceV2(slice.blocks().size(), resourcesClosed);
        }
    }
}
