package com.github.nankotsu029.landformcraft.core.v2.command;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportResultV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.Release2DiskBudgetV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.Release2PlacementApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.Release2PlacementOperationStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementActorKindV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2MeasuredDimensionGateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2MeasurementProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2PlacementDimensionPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-12-03 end-to-end evidence for the official v2 command path against a fake world gateway:
 * request → export → 64×64 placement → Undo. Every step is dispatched through
 * {@link V2CommandRouterV2}, so the grammar the operator types is the grammar under test.
 */
class V2CommandPathE2EV2Test {
    private static final Path REQUEST = Path.of("examples/v2/diagnostic/harbor-cove-64.request-v2.json");
    private static final Path INTENT = Path.of("examples/v2/diagnostic/harbor-cove-64.terrain-intent-v2.json");
    private static final UUID WORLD = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final V2CommandVerbV2.Surface PAPER = V2CommandVerbV2.Surface.PAPER;

    @Test
    void requestToExportToPlacementToUndoRunsThroughTheOfficialV2Verbs(@TempDir Path root) throws Exception {
        GenerationExecutors executors = GenerationExecutors.create(4, 2, 16);
        try {
            V2WorkflowServiceV2 workflow = new V2WorkflowServiceV2(executors, null);

            // v2 request info
            V2CommandRouteV2 requestRoute = accept(new String[] {
                    "v2", "request", "info", REQUEST.toString()});
            Map<String, Object> inspected = workflow.inspectRequest(
                    Path.of(requestRoute.arguments().get(3)));
            assertEquals("harbor-cove-64", inspected.get("requestId"));
            assertEquals(64, inspected.get("width"));
            assertEquals(64, inspected.get("length"));

            // v2 export
            Path exports = root.resolve("exports");
            V2CommandRouteV2 exportRoute = accept(new String[] {
                    "v2", "export", REQUEST.toString(), INTENT.toString(), exports.toString(),
                    "v2-12-03-e2e", "water", "54", "46"});
            List<String> exportArgs = exportRoute.arguments();
            Release2ExportResultV2 exported = workflow.export(
                    Path.of(exportArgs.get(2)), Path.of(exportArgs.get(3)),
                    root.resolve("work"), Path.of(exportArgs.get(4)), exportArgs.get(5),
                    V2WorkflowServiceV2.baseline(exportArgs.get(6), exportArgs.get(7), exportArgs.get(8)),
                    true);
            assertEquals(List.of("surface-2_5d"), exported.requiredCapabilities());
            assertTrue(exported.eligibility().eligible());

            // v2 preview
            Map<String, Object> previews = workflow.inspectPreviews(
                    Path.of(accept(new String[] {
                            "v2", "preview", exported.releaseDirectory().toString()}).arguments().get(2)));
            assertEquals(exported.manifestChecksum(), previews.get("manifestChecksum"));

            FakeWorld gateway = new FakeWorld("minecraft:stone");
            Release2PlacementApplicationServiceV2 placements = new Release2PlacementApplicationServiceV2(
                    exports, root.resolve("state"), executors, gateway, Clock.systemUTC(),
                    new Release2DiskBudgetV2(1L, 64L * 1024L * 1024L, 1L),
                    dimensionPolicy(), Release2PlacementOperationStoreV2.WriteFaultInjectorV2.none());
            try {
                var actor = PlacementPlanV2.PlacementActorV2.console();

                // v2 place plan
                V2CommandRouteV2 planRoute = accept(new String[] {
                        "v2", "place", "plan", exported.releaseDirectory().getFileName().toString(),
                        "world", "0", "64", "0"});
                assertEquals(V2CommandVerbV2.PLACE_PLAN, planRoute.requireVerb());
                var prepared = placements.plan(new Release2PlacementApplicationServiceV2.PlanRequestV2(
                                planRoute.arguments().get(3), WORLD, planRoute.arguments().get(4),
                                Integer.parseInt(planRoute.arguments().get(5)),
                                Integer.parseInt(planRoute.arguments().get(6)),
                                Integer.parseInt(planRoute.arguments().get(7)),
                                new WorldAabbV2(-256, -64, -256, 256, 320, 256), actor, () -> false))
                        .toCompletableFuture().join();
                assertEquals(PlacementJournalStateV2.CONFIRMATION_ISSUED, prepared.journal().state());
                assertEquals(0, gateway.mutationCalls);

                UUID placementId = prepared.plan().placementId();

                // v2 place confirm
                accept(new String[] {
                        "v2", "place", "confirm", placementId.toString(), prepared.confirmationToken()});
                var confirmed = placements.confirm(new Release2PlacementApplicationServiceV2.ConfirmRequestV2(
                        placementId, prepared.confirmationToken(), actor, () -> false))
                        .toCompletableFuture().join();
                assertEquals(PlacementJournalStateV2.SNAPSHOT_COMPLETE, confirmed.journal().state());

                // v2 place execute
                accept(new String[] {"v2", "place", "execute", placementId.toString()});
                var executed = placements.execute(new Release2PlacementApplicationServiceV2.ExecuteRequestV2(
                        placementId, actor, () -> false)).toCompletableFuture().join();
                assertEquals(PlacementJournalStateV2.APPLIED, executed.journal().state());
                assertTrue(gateway.mutationCalls > 0);

                // v2 status
                accept(new String[] {"v2", "status", placementId.toString()});
                assertEquals(PlacementJournalStateV2.APPLIED,
                        placements.status(placementId).toCompletableFuture().join().state());

                // v2 undo plan → v2 undo execute
                accept(new String[] {"v2", "undo", "plan", placementId.toString()});
                var preparedUndo = placements.prepareUndo(placementId, actor).toCompletableFuture().join();
                accept(new String[] {
                        "v2", "undo", "execute", placementId.toString(), preparedUndo.plaintextToken()});
                var undone = placements.executeUndo(placementId, preparedUndo.plaintextToken(), actor, () -> false)
                        .toCompletableFuture().join();
                assertEquals(PlacementJournalStateV2.UNDONE, undone.undoneJournal().state());
                assertTrue(gateway.restoreCalls > 0);
                assertTrue(gateway.allTrackedBlocksMatch("minecraft:stone"),
                        "Undo must restore every mutated block to its snapshot state");
            } finally {
                placements.close();
            }
        } finally {
            executors.shutdown(Duration.ofSeconds(30));
            executors.close();
        }
    }

    @Test
    void oversizedPlacementIsRejectedByTheMeasuredDimensionGate() {
        // The E2E fixture is exactly the 64×64 WorldEdit-runtime ceiling; one block more is refused.
        Release2PlacementDimensionPolicyV2 policy = dimensionPolicy();
        policy.requireAdmitted(64, 64, "world", PlacementActorKindV2.CONSOLE);
        assertNotEquals(0, assertThrows(() -> policy.requireAdmitted(
                65, 64, "world", PlacementActorKindV2.CONSOLE)).length());
    }

    private static String assertThrows(Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException exception) {
            return String.valueOf(exception.getMessage());
        }
        throw new AssertionError("expected the dimension gate to reject the oversized layout");
    }

    private static Release2PlacementDimensionPolicyV2 dimensionPolicy() {
        return new Release2PlacementDimensionPolicyV2(
                Release2MeasuredDimensionGateV2.production(
                        false,
                        PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM,
                        PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM),
                Release2MeasurementProfileV2.disabled());
    }

    private static V2CommandRouteV2 accept(String[] args) {
        V2CommandRouteV2 route = V2CommandRouterV2.route(args, PAPER);
        assertTrue(route.accepted(), () -> route.message());
        return route;
    }

    private static final class FakeWorld implements PlacementWorldGatewayV2 {
        private final String defaultState;
        private final Map<Coord, String> states = new ConcurrentHashMap<>();
        private int snapshotReads;
        private int mutationCalls;
        private int restoreCalls;

        private FakeWorld(String defaultState) {
            this.defaultState = defaultState;
        }

        @Override
        public void streamRegionBlockStates(
                UUID worldId, WorldAabbV2 region, PlacementWorldGatewayV2.PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            snapshotReads++;
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    for (int x = region.minX(); x <= region.maxX(); x++) {
                        consumer.accept(x, y, z, state(x, y, z));
                    }
                }
            }
        }

        @Override
        public CompletionStage<PlacementApplySliceReceiptV2> applyBlockSlice(PlacementApplySliceV2 slice) {
            mutationCalls++;
            slice.mutations().forEach(block -> states.put(
                    new Coord(block.x(), block.y(), block.z()), block.blockState()));
            return CompletableFuture.completedFuture(new PlacementApplySliceReceiptV2(
                    slice.operationId(), slice.tileId(), slice.sliceSequence(), slice.mutations().size(),
                    true, true, true));
        }

        @Override
        public CompletionStage<PlacementRestoreSliceReceiptV2> restoreBlockSlice(PlacementRestoreSliceV2 slice) {
            mutationCalls++;
            restoreCalls++;
            slice.blocks().forEach(block -> states.put(
                    new Coord(block.x(), block.y(), block.z()), block.blockState()));
            return CompletableFuture.completedFuture(new PlacementRestoreSliceReceiptV2(
                    slice.operationId(), slice.tileId(), slice.sliceSequence(), slice.blocks().size(),
                    true, true, true));
        }

        @Override
        public CompletionStage<PlacementSettleTickReceiptV2> advanceSettleTick(PlacementSettleTickV2 tick) {
            return CompletableFuture.completedFuture(new PlacementSettleTickReceiptV2(
                    tick.operationId(), tick.tickIndex(), true, true, 0, 0, List.of()));
        }

        @Override
        public CompletionStage<PlacementVerifyReadSliceReceiptV2> readVerifySlice(
                PlacementVerifyReadSliceV2 slice
        ) {
            List<String> blocks = new ArrayList<>();
            WorldAabbV2 region = slice.effectEnvelope();
            for (int offset = 0; offset < slice.blockCount(); offset++) {
                long index = slice.startIndex() + offset;
                int width = region.maxX() - region.minX() + 1;
                int length = region.maxZ() - region.minZ() + 1;
                long layer = (long) width * length;
                int x = region.minX() + (int) (index % width);
                int z = region.minZ() + (int) ((index / width) % length);
                int y = region.minY() + (int) (index / layer);
                blocks.add(state(x, y, z));
            }
            return CompletableFuture.completedFuture(new PlacementVerifyReadSliceReceiptV2(
                    slice.operationId(), slice.sliceSequence(), true, true, blocks));
        }

        private String state(int x, int y, int z) {
            return states.getOrDefault(new Coord(x, y, z), defaultState);
        }

        private boolean allTrackedBlocksMatch(String state) {
            return !states.isEmpty() && states.values().stream().allMatch(state::equals);
        }
    }

    private record Coord(int x, int y, int z) { }
}
