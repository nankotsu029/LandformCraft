package com.github.nankotsu029.landformcraft.core.v2.placement.undo;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.FilePlacementJournalStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyLimitsV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyTestFixtureV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyTransactionServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementDesiredBlockV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementJournalStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleVerifyRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleVerifyServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementUndoPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementVerifyEvidenceV2;
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

class PlacementUndoServiceV2Test {
    private static final Duration WAIT = Duration.ofSeconds(8);
    private static final String BASELINE_STATE = "minecraft:stone";
    /** Deterministic placeholder confirmation nonce for the sealed example only (V2-12-11). */
    private static final String EXAMPLE_CONFIRMATION_TOKEN = "11111111-1111-1111-1111-111111111111";

    @Test
    void happyPathUndoesAppliedPlacementAndKeepsSnapshots(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterApplied(directory, true);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementUndoServiceV2 service = harness.undoService(journals)) {
            PlacementUndoServiceV2.UndoResultV2 result =
                    await(service.undo(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementJournalStateV2.UNDONE, result.undoneJournal().state());
            assertTrue(result.undoneJournal().tiles().stream()
                    .allMatch(tile -> tile.state() == PlacementTileStateV2.RESTORED));
            assertEquals(result.baselineChecksum(), result.observedChecksum());
            assertEquals(
                    harness.fixture.envelope.unionEffectEnvelope().volumeBlocks(),
                    result.scannedBlocks());
            assertEquals(
                    harness.fixture.envelope.unionEffectEnvelope().volumeBlocks(),
                    result.driftPreflightScannedBlocks());
            assertEquals(PlacementJournalStateV2.UNDOING, journals.states().get(0));
            assertEquals(PlacementJournalStateV2.UNDONE,
                    journals.states().get(journals.states().size() - 1));
            for (List<PlacementDesiredBlockV2> blocks : harness.fixture.desiredBlocks.values()) {
                for (PlacementDesiredBlockV2 block : blocks) {
                    assertEquals(BASELINE_STATE,
                            harness.gateway.blockAt(block.x(), block.y(), block.z()));
                }
            }
            assertThrows(PlacementReservationExceptionV2.class, () ->
                    harness.fixture.safetyStore.assertOwned(
                            harness.fixture.plan.placementId(), harness.prepared.undoPlan().actor()));
            PlacementSnapshotPlanV2 reloaded = harness.fixture.snapshotCompiler.loadPublished(
                    harness.fixture.plan,
                    harness.fixture.envelope,
                    harness.fixture.reservation,
                    PlacementApplyTestFixtureV2.NEVER);
            assertEquals(harness.fixture.snapshot.canonicalChecksum(), reloaded.canonicalChecksum());
        }
        List<Integer> restoredTileOrder = harness.gateway.restoreSlices.stream()
                .map(PlacementRestoreSliceV2::tileIndex)
                .distinct()
                .toList();
        assertEquals(List.of(1, 0), restoredTileOrder);
    }

    @Test
    void worldDriftRejectsWithZeroMutation(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterApplied(directory, false);
        WorldAabbV2 union = harness.fixture.envelope.unionEffectEnvelope();
        harness.gateway.put(union.minX(), union.minY(), union.minZ(), "minecraft:bedrock");
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementUndoServiceV2 service = harness.undoService(journals)) {
            PlacementUndoExceptionV2 failure =
                    failure(service.undo(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementUndoFailureCodeV2.WORLD_DRIFT, failure.code());
            assertFalse(failure.worldMutationMayHaveOccurred());
            assertTrue(journals.saved.isEmpty());
            assertTrue(harness.gateway.restoreSlices.isEmpty());
        }
    }

    @Test
    void actorMismatchRejectsBeforeMutation(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterApplied(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementUndoServiceV2 service = harness.undoService(journals)) {
            PlacementUndoRequestV2 mismatched = new PlacementUndoRequestV2(
                    harness.fixture.plan,
                    harness.fixture.envelope,
                    harness.fixture.reservation,
                    harness.prepared.undoReservation(),
                    harness.fixture.snapshot,
                    harness.verifyEvidence,
                    harness.prepared.undoPlan(),
                    harness.prepared.preparedJournal(),
                    harness.applyCompleteJournal,
                    PlacementSettleVerifyPolicyV2.standard(),
                    harness.prepared.plaintextToken(),
                    PlacementPlanV2.PlacementActorV2.system("OTHER-ACTOR"),
                    harness.source,
                    PlacementApplyTestFixtureV2.NEVER);
            PlacementUndoExceptionV2 failure = failure(service.undo(mismatched));
            assertEquals(PlacementUndoFailureCodeV2.ACTOR_MISMATCH, failure.code());
            assertFalse(failure.worldMutationMayHaveOccurred());
            assertTrue(journals.saved.isEmpty());
        }
    }

    @Test
    void confirmationReplayIsRejected(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterApplied(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementUndoServiceV2 service = harness.undoService(journals)) {
            await(service.undo(harness.request(PlacementApplyTestFixtureV2.NEVER)));
        }
        // Re-prepare is required after success; consuming the same hash again must fail.
        RecordingJournalStore second = new RecordingJournalStore();
        try (PlacementUndoServiceV2 service = harness.undoService(second)) {
            // Rebuild a request that reuses the already-consumed confirmation hash.
            PlacementUndoExceptionV2 failure =
                    failure(service.undo(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementUndoFailureCodeV2.CONFIRMATION_REPLAY, failure.code());
            assertFalse(failure.worldMutationMayHaveOccurred());
        }
    }

    @Test
    void cancelDuringRestoreRequiresRecovery(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterApplied(directory, false);
        AtomicBoolean cancel = new AtomicBoolean();
        harness.gateway.holdFirstRestore = true;
        harness.gateway.afterRestoreSlice = () -> cancel.set(true);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementUndoServiceV2 service = harness.undoService(journals)) {
            CompletionStage<PlacementUndoServiceV2.UndoResultV2> stage =
                    service.undo(harness.request(cancel::get));
            assertTrue(harness.gateway.firstRestoreSubmitted.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
            harness.gateway.completeHeldRestore();
            PlacementUndoExceptionV2 failure = failure(stage);
            assertEquals(PlacementUndoFailureCodeV2.CANCELLED, failure.code());
            assertTrue(failure.worldMutationMayHaveOccurred());
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
        }
    }

    @Test
    void missingSnapshotRejectsWithZeroMutation(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterApplied(directory, false);
        Path published = harness.fixture.safetyStore.snapshotsRoot()
                .resolve(harness.fixture.plan.placementId().toString());
        deleteRecursively(published);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementUndoServiceV2 service = harness.undoService(journals)) {
            PlacementUndoExceptionV2 failure =
                    failure(service.undo(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementUndoFailureCodeV2.SNAPSHOT_MISSING, failure.code());
            assertFalse(failure.worldMutationMayHaveOccurred());
            assertTrue(journals.saved.isEmpty());
        }
    }

    @Test
    void applicationServiceIsExplicitRelease2Path(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterApplied(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementUndoServiceV2 undoService = harness.undoService(journals);
             PlacementUndoApplicationServiceV2 application = new PlacementUndoApplicationServiceV2(
                     new PlacementUndoPrepareCompilerV2(
                             harness.fixture.safetyStore, journals, PlacementApplyTestFixtureV2.CLOCK),
                     undoService)) {
            assertTrue(application.isRelease2Path());
            PlacementUndoServiceV2.UndoResultV2 result =
                    await(application.undo(harness.request(PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementJournalStateV2.UNDONE, result.undoneJournal().state());
        }
    }

    @Test
    void journalStoreLoadAndExistsRoundTrip(@TempDir Path directory) throws Exception {
        FilePlacementJournalStoreV2 store = new FilePlacementJournalStoreV2(directory.resolve("journals"));
        Harness harness = Harness.afterApplied(directory.resolve("fixture"), false);
        assertFalse(store.exists(harness.fixture.plan.placementId()));
        store.save(harness.prepared.preparedJournal());
        assertTrue(store.exists(harness.fixture.plan.placementId()));
        PlacementJournalV2 loaded = store.load(harness.fixture.plan.placementId());
        assertEquals(harness.prepared.preparedJournal(), loaded);
    }

    @Test
    void undoPlanCodecRoundTrip(@TempDir Path directory) throws Exception {
        // Fixed nonce so the sealed example is reproducible: without it the confirmation hash is a
        // random one-time token and every run would rewrite the tracked example (V2-12-11).
        Harness harness = Harness.afterApplied(directory, false, EXAMPLE_CONFIRMATION_TOKEN);
        LandformV2DataCodec codec = new LandformV2DataCodec();
        Path path = directory.resolve("placement-undo-plan-v2.json");
        codec.writePlacementUndoPlan(path, harness.prepared.undoPlan());
        PlacementUndoPlanV2 read = codec.readPlacementUndoPlan(path);
        assertEquals(harness.prepared.undoPlan(), read);
        assertEquals(
                codec.placementUndoPlanChecksum(read),
                read.canonicalChecksum());
        Path example = Path.of("examples/v2/placement/placement-undo-plan-v2.json");
        codec.writePlacementUndoPlan(example, harness.prepared.undoPlan());
    }

    private static PlacementUndoLimitsV2 limits() {
        return new PlacementUndoLimitsV2(
                PlacementUndoLimitsV2.VERSION, 2, 16, 32, 1_000_000L, 32);
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static PlacementUndoExceptionV2 failure(CompletionStage<?> stage) throws Exception {
        ExecutionException execution = assertThrows(
                ExecutionException.class,
                () -> stage.toCompletableFuture().get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        Throwable failure = execution.getCause();
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return assertInstanceOf(PlacementUndoExceptionV2.class, failure);
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

    private static final class Harness {
        final PlacementApplyTestFixtureV2 fixture;
        final UndoWorldGateway gateway;
        final PlacementApplyTestFixtureV2.ImmutableSource source;
        final PlacementJournalV2 applyCompleteJournal;
        final PlacementVerifyEvidenceV2 verifyEvidence;
        final PlacementUndoPrepareCompilerV2.PreparedUndoV2 prepared;

        private Harness(
                PlacementApplyTestFixtureV2 fixture,
                UndoWorldGateway gateway,
                PlacementApplyTestFixtureV2.ImmutableSource source,
                PlacementJournalV2 applyCompleteJournal,
                PlacementVerifyEvidenceV2 verifyEvidence,
                PlacementUndoPrepareCompilerV2.PreparedUndoV2 prepared
        ) {
            this.fixture = fixture;
            this.gateway = gateway;
            this.source = source;
            this.applyCompleteJournal = applyCompleteJournal;
            this.verifyEvidence = verifyEvidence;
            this.prepared = prepared;
        }

        static Harness afterApplied(Path root, boolean twoTiles) throws Exception {
            return afterApplied(root, twoTiles, null);
        }

        /**
         * {@code plaintextToken} pins the one-time confirmation nonce so a caller that seals the
         * canonical example (the codec round-trip) gets a reproducible {@code confirmationHash};
         * {@code null} keeps the realistic random token every other scenario uses.
         */
        static Harness afterApplied(Path root, boolean twoTiles, String plaintextToken)
                throws Exception {
            PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(root, twoTiles);
            UndoWorldGateway gateway = new UndoWorldGateway();
            PlacementApplyTestFixtureV2.ImmutableSource source = fixture.source(false);
            RecordingJournalStore applyJournals = new RecordingJournalStore();
            PlacementJournalV2 applyComplete;
            try (PlacementApplyTransactionServiceV2 applyService =
                    new PlacementApplyTransactionServiceV2(
                            fixture.strictVerifier(),
                            gateway,
                            applyJournals,
                            PlacementApplyTestFixtureV2.CLOCK,
                            applyLimits())) {
                applyComplete = applyService
                        .apply(fixture.request(source, PlacementApplyTestFixtureV2.NEVER))
                        .toCompletableFuture()
                        .get(WAIT.toMillis(), TimeUnit.MILLISECONDS)
                        .applyCompleteJournal();
            }
            RecordingJournalStore verifyJournals = new RecordingJournalStore();
            PlacementSettleVerifyServiceV2.VerifyResultV2 verified;
            try (PlacementSettleVerifyServiceV2 verifyService = new PlacementSettleVerifyServiceV2(
                    gateway, verifyJournals, PlacementApplyTestFixtureV2.CLOCK)) {
                verified = verifyService.settleAndVerify(new PlacementSettleVerifyRequestV2(
                                fixture.plan,
                                fixture.envelope,
                                fixture.snapshot,
                                fixture.evidence,
                                applyComplete,
                                source,
                                (x, y, z) -> BASELINE_STATE,
                                PlacementSettleVerifyPolicyV2.standard(),
                                PlacementApplyTestFixtureV2.NEVER))
                        .toCompletableFuture()
                        .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            }
            assertEquals(PlacementJournalStateV2.APPLIED, verified.verifiedJournal().state());
            RecordingJournalStore prepareJournals = new RecordingJournalStore();
            PlacementUndoPrepareCompilerV2.PreparedUndoV2 prepared =
                    new PlacementUndoPrepareCompilerV2(
                            fixture.safetyStore, prepareJournals, PlacementApplyTestFixtureV2.CLOCK)
                            .prepare(new PlacementUndoPrepareRequestV2(
                                    fixture.plan,
                                    fixture.envelope,
                                    fixture.reservation,
                                    fixture.snapshot,
                                    verified.evidence(),
                                    verified.verifiedJournal(),
                                    applyComplete,
                                    fixture.plan.actor(),
                                    plaintextToken));
            return new Harness(
                    fixture,
                    gateway,
                    source,
                    applyComplete,
                    verified.evidence(),
                    prepared);
        }

        PlacementUndoServiceV2 undoService(PlacementJournalStoreV2 journals) {
            return new PlacementUndoServiceV2(
                    fixture.snapshotCompiler,
                    fixture.safetyStore,
                    gateway,
                    journals,
                    PlacementApplyTestFixtureV2.CLOCK,
                    limits());
        }

        PlacementUndoRequestV2 request(CancellationToken cancellation) {
            return new PlacementUndoRequestV2(
                    fixture.plan,
                    fixture.envelope,
                    fixture.reservation,
                    prepared.undoReservation(),
                    fixture.snapshot,
                    verifyEvidence,
                    prepared.undoPlan(),
                    prepared.preparedJournal(),
                    applyCompleteJournal,
                    PlacementSettleVerifyPolicyV2.standard(),
                    prepared.plaintextToken(),
                    prepared.undoPlan().actor(),
                    source,
                    cancellation);
        }

        private static PlacementApplyLimitsV2 applyLimits() {
            return new PlacementApplyLimitsV2(
                    PlacementApplyLimitsV2.VERSION, 2, 2, 32, 32, 1_000_000_000L, 640);
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

    private static final class UndoWorldGateway implements PlacementWorldGatewayV2 {
        private final Map<String, String> world = new ConcurrentHashMap<>();
        final List<PlacementRestoreSliceV2> restoreSlices = new CopyOnWriteArrayList<>();
        volatile boolean holdFirstRestore;
        volatile Runnable afterRestoreSlice = () -> { };
        final CountDownLatch firstRestoreSubmitted = new CountDownLatch(1);
        final CompletableFuture<Void> heldRestore = new CompletableFuture<>();
        private final AtomicInteger settleCalls = new AtomicInteger();
        private final AtomicInteger restoreCalls = new AtomicInteger();

        String blockAt(int x, int y, int z) {
            return world.getOrDefault(key(x, y, z), BASELINE_STATE);
        }

        void put(int x, int y, int z, String state) {
            world.put(key(x, y, z), state);
        }

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementWorldGatewayV2.PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            throw new IOException("not used by undo tests");
        }

        @Override
        public CompletionStage<PlacementApplySliceReceiptV2> applyBlockSlice(
                PlacementApplySliceV2 slice
        ) {
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
                    true);
            afterRestoreSlice.run();
            if (holdFirstRestore && call == 0) {
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
            settleCalls.getAndIncrement();
            return CompletableFuture.completedFuture(new PlacementSettleTickReceiptV2(
                            tick.operationId(),
                            tick.tickIndex(),
                            true,
                            true,
                            0,
                            0,
                            List.of()))
                    .minimalCompletionStage();
        }

        @Override
        public CompletionStage<PlacementVerifyReadSliceReceiptV2> readVerifySlice(
                PlacementVerifyReadSliceV2 slice
        ) {
            List<String> states = new ArrayList<>(slice.blockCount());
            for (int i = 0; i < slice.blockCount(); i++) {
                int[] xyz = PlacementUndoServiceV2.decodeCanonicalIndex(
                        slice.effectEnvelope(), slice.startIndex() + i);
                states.add(blockAt(xyz[0], xyz[1], xyz[2]));
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
