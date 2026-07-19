package com.github.nankotsu029.landformcraft.core.v2.placement.rollback;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyLimitsV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyTestFixtureV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyTransactionServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementDesiredBlockV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementJournalStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleVerifyRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleVerifyServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationExceptionV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementRollbackServiceV2Test {
    private static final Duration WAIT = Duration.ofSeconds(8);
    private static final String BASELINE_STATE = "minecraft:stone";

    // --- rollback from each real failure point -------------------------------------------------

    @Test
    void rollbackFromVerifyMismatchRestoresBaselineAndSealsRolledBack(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            PlacementRollbackServiceV2.RollbackResultV2 result =
                    await(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementJournalStateV2.ROLLED_BACK, result.rolledBackJournal().state());
            assertTrue(result.rolledBackJournal().tiles().stream()
                    .allMatch(tile -> tile.state() == PlacementTileStateV2.RESTORED));
            assertEquals(result.baselineChecksum(), result.observedChecksum());
            assertEquals(
                    harness.fixture.envelope.unionEffectEnvelope().volumeBlocks(),
                    result.scannedBlocks());
            assertEquals(PlacementJournalStateV2.ROLLING_BACK, journals.states().get(0));
            assertEquals(PlacementJournalStateV2.ROLLED_BACK,
                    journals.states().get(journals.states().size() - 1));
            // Every previously mutated coordinate is back to the snapshot baseline.
            for (List<PlacementDesiredBlockV2> blocks : harness.fixture.desiredBlocks.values()) {
                for (PlacementDesiredBlockV2 block : blocks) {
                    assertEquals(BASELINE_STATE,
                            harness.gateway.blockAt(block.x(), block.y(), block.z()));
                }
            }
            // Reservation finalization: the durable lease is released.
            assertThrows(PlacementReservationExceptionV2.class, () ->
                    harness.fixture.safetyStore.assertOwned(
                            harness.fixture.plan.placementId(), harness.fixture.plan.actor()));
            // Disk lifetime: published snapshot evidence is untouched and still strict-verifies.
            PlacementSnapshotPlanV2 reloaded = harness.fixture.snapshotCompiler.loadPublished(
                    harness.fixture.plan,
                    harness.fixture.envelope,
                    harness.fixture.reservation,
                    PlacementApplyTestFixtureV2.NEVER);
            assertEquals(harness.fixture.snapshot.canonicalChecksum(), reloaded.canonicalChecksum());
        }
    }

    @Test
    void rollbackFromMidApplyFailureRestoresBothTilesInReverseOrder(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterApplyFailureOnSecondTile(directory);
        assertEquals(
                List.of(PlacementTileStateV2.APPLIED, PlacementTileStateV2.SNAPSHOTTED),
                harness.failedJournal.tiles().stream()
                        .map(PlacementJournalV2.PlacementTileEntryV2::state)
                        .toList());
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            PlacementRollbackServiceV2.RollbackResultV2 result =
                    await(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementJournalStateV2.ROLLED_BACK, result.rolledBackJournal().state());
            assertEquals(2, result.restoredTiles());
        }
        List<Integer> restoredTileOrder = harness.gateway.restoreSlices.stream()
                .map(PlacementRestoreSliceV2::tileIndex)
                .distinct()
                .toList();
        assertEquals(List.of(1, 0), restoredTileOrder);
        // The first checkpoint restores the last tile: canonical RESTORED suffix.
        PlacementJournalV2 firstCheckpoint = journals.saved.get(1);
        assertEquals(PlacementJournalStateV2.ROLLING_BACK, firstCheckpoint.state());
        assertEquals(PlacementTileStateV2.APPLIED, firstCheckpoint.tiles().get(0).state());
        assertEquals(PlacementTileStateV2.RESTORED, firstCheckpoint.tiles().get(1).state());
    }

    @Test
    void rollbackFromSettleOutOfEnvelopeFailureSucceeds(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterSettleOutsideUpdate(directory);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            PlacementRollbackServiceV2.RollbackResultV2 result =
                    await(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementJournalStateV2.ROLLED_BACK, result.rolledBackJournal().state());
            assertEquals(result.baselineChecksum(), result.observedChecksum());
        }
    }

    // --- reverse restore order determinism ------------------------------------------------------

    @Test
    void reverseRestoreOrderIsThreadAndRepeatInvariant(@TempDir Path directory) throws Exception {
        List<String> firstOrder = restoreOrder(directory.resolve("run1"), 1);
        List<String> secondOrder = restoreOrder(directory.resolve("run2"), 4);
        assertEquals(firstOrder, secondOrder);
    }

    private static List<String> restoreOrder(Path root, int workerThreads) throws Exception {
        Harness harness = Harness.afterVerifyMismatch(root, true);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(workerThreads))) {
            await(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
        }
        List<String> order = new ArrayList<>();
        for (PlacementRestoreSliceV2 slice : harness.gateway.restoreSlices) {
            PlacementRestoreSliceV2.RestoreBlockV2 first = slice.blocks().get(0);
            order.add(slice.tileId() + "#" + slice.sliceSequence()
                    + "@" + first.x() + "," + first.y() + "," + first.z());
        }
        return order;
    }

    // --- bounded slices --------------------------------------------------------------------------

    @Test
    void restoreSlicesAreBoundedByLimits(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRollbackLimitsV2 smallSlices = new PlacementRollbackLimitsV2(
                PlacementRollbackLimitsV2.VERSION, 2, 16, 7, 1_000_000L);
        try (PlacementRollbackServiceV2 service = harness.service(journals, smallSlices)) {
            await(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
        }
        assertTrue(harness.gateway.restoreSlices.size() > 1);
        assertTrue(harness.gateway.restoreSlices.stream()
                .allMatch(slice -> slice.blocks().size() <= 7));
    }

    @Test
    void restoreSliceBudgetIsRejectedBeforeAnyMutation(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRollbackLimitsV2 tinyBudget = new PlacementRollbackLimitsV2(
                PlacementRollbackLimitsV2.VERSION, 2, 16, 1, 2);
        try (PlacementRollbackServiceV2 service = harness.service(journals, tinyBudget)) {
            PlacementRollbackExceptionV2 failure =
                    failure(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementRollbackFailureCodeV2.RESTORE_SLICE_BUDGET, failure.code());
        }
        assertTrue(harness.gateway.restoreSlices.isEmpty());
        assertTrue(journals.saved.isEmpty());
    }

    // --- missing / tampered snapshot rejection ---------------------------------------------------

    @Test
    void missingSnapshotDirectoryIsRejectedBeforeAnyMutation(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        Path published = harness.fixture.safetyStore.snapshotsRoot()
                .resolve(harness.fixture.plan.placementId().toString());
        deleteRecursively(published);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            PlacementRollbackExceptionV2 failure =
                    failure(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementRollbackFailureCodeV2.SNAPSHOT_MISSING, failure.code());
            assertFalse(failure.worldMutationMayHaveOccurred());
        }
        assertTrue(harness.gateway.restoreSlices.isEmpty());
        assertTrue(journals.saved.isEmpty());
    }

    @Test
    void tamperedSnapshotFileIsRejectedBeforeAnyMutation(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        Path published = harness.fixture.safetyStore.snapshotsRoot()
                .resolve(harness.fixture.plan.placementId().toString());
        Path snapshotFile = published.resolve(
                harness.fixture.snapshot.tiles().get(0).snapshotFile());
        byte[] bytes = Files.readAllBytes(snapshotFile);
        bytes[bytes.length - 1] ^= 0x1;
        Files.write(snapshotFile, bytes);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            PlacementRollbackExceptionV2 failure =
                    failure(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementRollbackFailureCodeV2.SNAPSHOT_TAMPERED, failure.code());
        }
        assertTrue(harness.gateway.restoreSlices.isEmpty());
        assertTrue(journals.saved.isEmpty());
    }

    // --- rollback failure, late completion, shutdown, cancel -------------------------------------

    @Test
    void restoreFailureLeavesRecoveryRequiredWithPartialSuffix(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, true);
        harness.gateway.failRestoreOnCall = 0;
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            PlacementRollbackExceptionV2 failure =
                    failure(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementRollbackFailureCodeV2.RESTORE_GATEWAY_FAILURE, failure.code());
            assertTrue(failure.worldMutationMayHaveOccurred());
        }
        PlacementJournalV2 last = journals.last();
        assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, last.state());
        assertTrue(last.tiles().stream().noneMatch(
                tile -> tile.state() == PlacementTileStateV2.RESTORED));
    }

    @Test
    void invalidRestoreReceiptRequiresRecovery(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        harness.gateway.invalidReceipts = true;
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            PlacementRollbackExceptionV2 failure =
                    failure(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementRollbackFailureCodeV2.RESTORE_RECEIPT_INVALID, failure.code());
        }
        assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
    }

    @Test
    void verifyMismatchAfterRestoreRequiresRecovery(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        harness.gateway.corruptFirstRollbackVerifyBlock = true;
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            PlacementRollbackExceptionV2 failure =
                    failure(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementRollbackFailureCodeV2.VERIFY_MISMATCH, failure.code());
        }
        assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
    }

    @Test
    void outOfEnvelopeUpdateDuringRollbackSettleRequiresRecovery(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        harness.gateway.outsideUpdatesOnSettleTick = 0;
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            PlacementRollbackExceptionV2 failure =
                    failure(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(
                    PlacementRollbackFailureCodeV2.SETTLE_OUT_OF_ENVELOPE_UPDATE, failure.code());
        }
        assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
    }

    @Test
    void shutdownDuringRestoreRequiresRecoveryAndIgnoresLateCompletion(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        harness.gateway.holdFirstRestore = true;
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRollbackServiceV2 service = harness.service(journals, limits(2));
        CompletionStage<PlacementRollbackServiceV2.RollbackResultV2> stage =
                service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER));
        assertTrue(harness.gateway.firstRestoreSubmitted.await(
                WAIT.toMillis(), TimeUnit.MILLISECONDS));
        service.close();
        harness.gateway.completeHeldRestore();
        PlacementRollbackExceptionV2 failure = failure(stage);
        assertEquals(PlacementRollbackFailureCodeV2.ROLLBACK_SHUTDOWN, failure.code());
        assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
        await(service.termination());
    }

    @Test
    void observerTimeoutDoesNotCancelAcceptedRestore(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        harness.gateway.holdFirstRestore = true;
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            CompletionStage<PlacementRollbackServiceV2.RollbackResultV2> stage =
                    service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER));
            CompletableFuture<PlacementRollbackServiceV2.RollbackResultV2> stable =
                    stage.toCompletableFuture();
            assertTrue(harness.gateway.firstRestoreSubmitted.await(
                    WAIT.toMillis(), TimeUnit.MILLISECONDS));
            CompletableFuture<PlacementRollbackServiceV2.RollbackResultV2> cancelled =
                    stage.toCompletableFuture();
            assertTrue(cancelled.cancel(true));
            assertFalse(harness.gateway.heldRestore.isCancelled());
            harness.gateway.completeHeldRestore();
            PlacementRollbackServiceV2.RollbackResultV2 result =
                    stable.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            assertEquals(PlacementJournalStateV2.ROLLED_BACK, result.rolledBackJournal().state());
            assertFalse(journals.states().contains(PlacementJournalStateV2.RECOVERY_REQUIRED));
        }
    }

    @Test
    void cancelDuringRestoreRequiresRecovery(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        AtomicBoolean cancelled = new AtomicBoolean();
        harness.gateway.afterRestoreSlice = () -> cancelled.set(true);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            PlacementRollbackExceptionV2 failure =
                    failure(service.rollback(harness.request(cancelled::get)));
            assertEquals(PlacementRollbackFailureCodeV2.CANCELLED, failure.code());
        }
        assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
    }

    // --- state / binding rejections ---------------------------------------------------------------

    @Test
    void nonRecoveryJournalIsRejected(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            PlacementRollbackRequestV2 request = new PlacementRollbackRequestV2(
                    harness.fixture.plan,
                    harness.fixture.envelope,
                    harness.fixture.reservation,
                    harness.fixture.snapshot,
                    harness.applyCompleteJournal,
                    PlacementSettleVerifyPolicyV2.standard(),
                    PlacementApplyTestFixtureV2.NEVER);
            PlacementRollbackExceptionV2 failure = failure(service.rollback(request));
            assertEquals(PlacementRollbackFailureCodeV2.STATE_MISMATCH, failure.code());
        }
        assertTrue(harness.gateway.restoreSlices.isEmpty());
        assertTrue(journals.saved.isEmpty());
    }

    @Test
    void duplicateActiveOperationIsRejected(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterVerifyMismatch(directory, false);
        harness.gateway.holdFirstRestore = true;
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 service = harness.service(journals, limits(2))) {
            CompletionStage<PlacementRollbackServiceV2.RollbackResultV2> first =
                    service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER));
            assertTrue(harness.gateway.firstRestoreSubmitted.await(
                    WAIT.toMillis(), TimeUnit.MILLISECONDS));
            PlacementRollbackExceptionV2 duplicate =
                    failure(service.rollback(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementRollbackFailureCodeV2.STATE_MISMATCH, duplicate.code());
            harness.gateway.completeHeldRestore();
            PlacementRollbackServiceV2.RollbackResultV2 result = await(first);
            assertEquals(PlacementJournalStateV2.ROLLED_BACK, result.rolledBackJournal().state());
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static PlacementRollbackLimitsV2 limits(int workerThreads) {
        return new PlacementRollbackLimitsV2(
                PlacementRollbackLimitsV2.VERSION, workerThreads, 16, 1_024, 1_000_000L);
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static PlacementRollbackExceptionV2 failure(CompletionStage<?> stage) throws Exception {
        ExecutionException execution = assertThrows(
                ExecutionException.class,
                () -> stage.toCompletableFuture().get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        Throwable failure = execution.getCause();
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return assertInstanceOf(PlacementRollbackExceptionV2.class, failure);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            });
        }
    }

    /**
     * Builds a real RECOVERY_REQUIRED journal by running the actual apply (V2-6-06) and settle／
     * verify (V2-6-07) services against the shared world gateway, then exposes a rollback request
     * bound to the same sealed prerequisite chain.
     */
    private static final class Harness {
        final PlacementApplyTestFixtureV2 fixture;
        final RollbackWorldGateway gateway;
        final PlacementJournalV2 applyCompleteJournal;
        final PlacementJournalV2 failedJournal;

        private Harness(
                PlacementApplyTestFixtureV2 fixture,
                RollbackWorldGateway gateway,
                PlacementJournalV2 applyCompleteJournal,
                PlacementJournalV2 failedJournal
        ) {
            this.fixture = fixture;
            this.gateway = gateway;
            this.applyCompleteJournal = applyCompleteJournal;
            this.failedJournal = failedJournal;
        }

        static Harness afterVerifyMismatch(Path root, boolean twoTiles) throws Exception {
            PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(root, twoTiles);
            RollbackWorldGateway gateway = new RollbackWorldGateway();
            PlacementJournalV2 applyComplete = runApply(fixture, gateway);
            gateway.corruptFirstApplyVerifyBlock = true;
            PlacementJournalV2 failed = runSettleVerifyExpectingRecovery(
                    fixture, gateway, applyComplete);
            gateway.corruptFirstApplyVerifyBlock = false;
            return new Harness(fixture, gateway, applyComplete, failed);
        }

        static Harness afterSettleOutsideUpdate(Path root) throws Exception {
            PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(root, false);
            RollbackWorldGateway gateway = new RollbackWorldGateway();
            PlacementJournalV2 applyComplete = runApply(fixture, gateway);
            gateway.outsideUpdatesOnSettleTick = 0;
            PlacementJournalV2 failed = runSettleVerifyExpectingRecovery(
                    fixture, gateway, applyComplete);
            gateway.outsideUpdatesOnSettleTick = null;
            return new Harness(fixture, gateway, applyComplete, failed);
        }

        static Harness afterApplyFailureOnSecondTile(Path root) throws Exception {
            PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(root, true);
            RollbackWorldGateway gateway = new RollbackWorldGateway();
            gateway.failApplyOnTileIndex = 1;
            RecordingJournalStore applyJournals = new RecordingJournalStore();
            try (PlacementApplyTransactionServiceV2 applyService =
                    new PlacementApplyTransactionServiceV2(
                            fixture.strictVerifier(),
                            gateway,
                            applyJournals,
                            PlacementApplyTestFixtureV2.CLOCK,
                            applyLimits())) {
                assertThrows(ExecutionException.class, () -> applyService
                        .apply(fixture.request(
                                fixture.source(false), PlacementApplyTestFixtureV2.NEVER))
                        .toCompletableFuture()
                        .get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
            }
            gateway.failApplyOnTileIndex = null;
            PlacementJournalV2 failed = applyJournals.last();
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, failed.state());
            return new Harness(fixture, gateway, null, failed);
        }

        private static PlacementJournalV2 runApply(
                PlacementApplyTestFixtureV2 fixture,
                RollbackWorldGateway gateway
        ) throws Exception {
            RecordingJournalStore applyJournals = new RecordingJournalStore();
            try (PlacementApplyTransactionServiceV2 applyService =
                    new PlacementApplyTransactionServiceV2(
                            fixture.strictVerifier(),
                            gateway,
                            applyJournals,
                            PlacementApplyTestFixtureV2.CLOCK,
                            applyLimits())) {
                return applyService
                        .apply(fixture.request(
                                fixture.source(false), PlacementApplyTestFixtureV2.NEVER))
                        .toCompletableFuture()
                        .get(WAIT.toMillis(), TimeUnit.MILLISECONDS)
                        .applyCompleteJournal();
            }
        }

        private static PlacementJournalV2 runSettleVerifyExpectingRecovery(
                PlacementApplyTestFixtureV2 fixture,
                RollbackWorldGateway gateway,
                PlacementJournalV2 applyComplete
        ) throws Exception {
            RecordingJournalStore verifyJournals = new RecordingJournalStore();
            try (PlacementSettleVerifyServiceV2 verifyService = new PlacementSettleVerifyServiceV2(
                    gateway, verifyJournals, PlacementApplyTestFixtureV2.CLOCK)) {
                assertThrows(ExecutionException.class, () -> verifyService
                        .settleAndVerify(new PlacementSettleVerifyRequestV2(
                                fixture.plan,
                                fixture.envelope,
                                fixture.snapshot,
                                fixture.evidence,
                                applyComplete,
                                fixture.source(false),
                                (x, y, z) -> BASELINE_STATE,
                                PlacementSettleVerifyPolicyV2.standard(),
                                PlacementApplyTestFixtureV2.NEVER))
                        .toCompletableFuture()
                        .get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
            }
            PlacementJournalV2 failed = verifyJournals.last();
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, failed.state());
            return failed;
        }

        private static PlacementApplyLimitsV2 applyLimits() {
            return new PlacementApplyLimitsV2(
                    PlacementApplyLimitsV2.VERSION, 2, 2, 32, 32, 1_000_000_000L, 640);
        }

        PlacementRollbackServiceV2 service(
                PlacementJournalStoreV2 journals,
                PlacementRollbackLimitsV2 limits
        ) {
            return new PlacementRollbackServiceV2(
                    fixture.snapshotCompiler,
                    fixture.safetyStore,
                    gateway,
                    journals,
                    PlacementApplyTestFixtureV2.CLOCK,
                    limits);
        }

        PlacementRollbackRequestV2 request(CancellationToken cancellation) {
            return new PlacementRollbackRequestV2(
                    fixture.plan,
                    fixture.envelope,
                    fixture.reservation,
                    fixture.snapshot,
                    failedJournal,
                    PlacementSettleVerifyPolicyV2.standard(),
                    cancellation);
        }
    }

    private static final class RecordingJournalStore implements PlacementJournalStoreV2 {
        final List<PlacementJournalV2> saved = new CopyOnWriteArrayList<>();

        @Override
        public void save(PlacementJournalV2 journal) {
            saved.add(journal);
        }

        PlacementJournalV2 last() {
            return saved.get(saved.size() - 1);
        }

        List<PlacementJournalStateV2> states() {
            return saved.stream().map(PlacementJournalV2::state).toList();
        }
    }

    /** Shared in-memory world for apply → settle/verify failure → rollback flows. */
    private static final class RollbackWorldGateway implements PlacementWorldGatewayV2 {
        private final Map<String, String> world = new ConcurrentHashMap<>();
        final List<PlacementRestoreSliceV2> restoreSlices = new CopyOnWriteArrayList<>();
        volatile Integer failApplyOnTileIndex;
        volatile boolean corruptFirstApplyVerifyBlock;
        volatile boolean corruptFirstRollbackVerifyBlock;
        volatile Integer outsideUpdatesOnSettleTick;
        volatile Integer failRestoreOnCall;
        volatile boolean invalidReceipts;
        volatile boolean holdFirstRestore;
        volatile Runnable afterRestoreSlice = () -> { };
        final CountDownLatch firstRestoreSubmitted = new CountDownLatch(1);
        final CompletableFuture<Void> heldRestore = new CompletableFuture<>();
        private volatile PlacementRestoreSliceV2 heldSlice;
        private final AtomicInteger settleCalls = new AtomicInteger();
        private final AtomicInteger restoreCalls = new AtomicInteger();
        private final AtomicBoolean applyVerifyCorrupted = new AtomicBoolean();
        private final AtomicBoolean rollbackVerifyCorrupted = new AtomicBoolean();

        String blockAt(int x, int y, int z) {
            return world.getOrDefault(key(x, y, z), BASELINE_STATE);
        }

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            throw new IOException("not used by rollback tests");
        }

        @Override
        public CompletionStage<PlacementApplySliceReceiptV2> applyBlockSlice(
                PlacementApplySliceV2 slice
        ) {
            Integer failTile = failApplyOnTileIndex;
            if (failTile != null && failTile == slice.tileIndex()) {
                return CompletableFuture.<PlacementApplySliceReceiptV2>failedFuture(
                        new IOException("injected apply failure for tile " + slice.tileIndex()))
                        .minimalCompletionStage();
            }
            for (PlacementDesiredBlockV2 mutation : slice.mutations()) {
                world.put(key(mutation.x(), mutation.y(), mutation.z()), mutation.blockState());
            }
            return CompletableFuture.completedFuture(new PlacementApplySliceReceiptV2(
                            slice.operationId(),
                            slice.tileId(),
                            slice.sliceSequence(),
                            slice.mutations().size(),
                            true,
                            true,
                            true))
                    .minimalCompletionStage();
        }

        @Override
        public CompletionStage<PlacementRestoreSliceReceiptV2> restoreBlockSlice(
                PlacementRestoreSliceV2 slice
        ) {
            int call = restoreCalls.getAndIncrement();
            Integer failCall = failRestoreOnCall;
            if (failCall != null && failCall == call) {
                return CompletableFuture.<PlacementRestoreSliceReceiptV2>failedFuture(
                        new IOException("injected restore failure at call " + call))
                        .minimalCompletionStage();
            }
            restoreSlices.add(slice);
            for (PlacementRestoreSliceV2.RestoreBlockV2 block : slice.blocks()) {
                world.put(key(block.x(), block.y(), block.z()), block.blockState());
            }
            PlacementRestoreSliceReceiptV2 receipt = new PlacementRestoreSliceReceiptV2(
                    slice.operationId(),
                    slice.tileId(),
                    slice.sliceSequence(),
                    slice.blocks().size(),
                    true,
                    true,
                    !invalidReceipts);
            afterRestoreSlice.run();
            if (holdFirstRestore && call == 0) {
                heldSlice = slice;
                firstRestoreSubmitted.countDown();
                return heldRestore.thenApply(ignored -> receipt).minimalCompletionStage();
            }
            return CompletableFuture.completedFuture(receipt).minimalCompletionStage();
        }

        void completeHeldRestore() {
            heldRestore.complete(null);
        }

        @Override
        public CompletionStage<PlacementSettleTickReceiptV2> advanceSettleTick(
                PlacementSettleTickV2 tick
        ) {
            int index = settleCalls.getAndIncrement();
            int outside = 0;
            List<PlacementSettleTickReceiptV2.OutsideUpdateSampleV2> samples = List.of();
            Integer outsideTick = outsideUpdatesOnSettleTick;
            if (outsideTick != null && outsideTick == tick.tickIndex()) {
                outside = 1;
                samples = List.of(new PlacementSettleTickReceiptV2.OutsideUpdateSampleV2(
                        tick.effectEnvelope().maxX() + 1,
                        tick.effectEnvelope().minY(),
                        tick.effectEnvelope().minZ(),
                        "minecraft:water[level=0]"));
            }
            return CompletableFuture.completedFuture(new PlacementSettleTickReceiptV2(
                            tick.operationId(),
                            tick.tickIndex(),
                            true,
                            true,
                            0,
                            outside,
                            samples))
                    .minimalCompletionStage();
        }

        @Override
        public CompletionStage<PlacementVerifyReadSliceReceiptV2> readVerifySlice(
                PlacementVerifyReadSliceV2 slice
        ) {
            List<String> states = new ArrayList<>(slice.blockCount());
            for (int i = 0; i < slice.blockCount(); i++) {
                int[] xyz = PlacementRollbackServiceV2.decodeCanonicalIndex(
                        slice.effectEnvelope(), slice.startIndex() + i);
                String state = blockAt(xyz[0], xyz[1], xyz[2]);
                if (corruptFirstApplyVerifyBlock && applyVerifyCorrupted.compareAndSet(false, true)) {
                    state = "minecraft:bedrock";
                }
                if (corruptFirstRollbackVerifyBlock
                        && rollbackVerifyCorrupted.compareAndSet(false, true)) {
                    state = "minecraft:bedrock";
                }
                states.add(state);
            }
            return CompletableFuture.completedFuture(new PlacementVerifyReadSliceReceiptV2(
                            slice.operationId(),
                            slice.sliceSequence(),
                            true,
                            true,
                            states))
                    .minimalCompletionStage();
        }

        private static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }
    }
}
