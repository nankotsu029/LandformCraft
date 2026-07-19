package com.github.nankotsu029.landformcraft.core.v2.recovery;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyLimitsV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyTestFixtureV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyTransactionServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementDesiredBlockV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementJournalStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRollbackLimitsV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRollbackServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleVerifyRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleVerifyServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementActorKindV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryActionV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryClassificationV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementRecoveryServiceV2Test {
    private static final Duration WAIT = Duration.ofSeconds(8);
    private static final String BASELINE_STATE = "minecraft:stone";

    // --- persisted state classification ----------------------------------------------------------

    @Test
    void preMutationJournalsClassifyAsNoWorldMutation(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        for (PlacementJournalV2 journal : List.of(
                harness.fixture.preSnapshotJournal, harness.fixture.journal)) {
            PlacementRecoveryDiagnosisV2 diagnosis = harness.recoveryService(
                    new RecordingJournalStore()).diagnose(harness.diagnoseRequest(journal, null));
            assertEquals(
                    PlacementRecoveryClassificationV2.NO_WORLD_MUTATION,
                    diagnosis.classification());
            assertEquals(List.of(PlacementRecoveryActionV2.RELEASE_LEASES), diagnosis.safeActions());
            assertEquals(0L, diagnosis.scannedBlocks());
        }
        assertEquals(0, harness.gateway.streamReads.get());
    }

    @Test
    void recoveryRequiredWithMatchingWorldAndSourceOffersAcceptAndRollback(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        PlacementRecoveryDiagnosisV2 diagnosis = harness.recoveryService(new RecordingJournalStore())
                .diagnose(harness.diagnoseRequest(harness.failedJournal, harness.source));
        assertEquals(PlacementRecoveryClassificationV2.SAFE_TO_ACCEPT, diagnosis.classification());
        assertEquals(
                List.of(PlacementRecoveryActionV2.ACCEPT, PlacementRecoveryActionV2.ROLLBACK),
                diagnosis.safeActions());
        assertEquals(diagnosis.observedWorldChecksum(), diagnosis.expectedAppliedStreamChecksum());
        assertNotEquals(diagnosis.baselineChecksum(), diagnosis.observedWorldChecksum());
        assertEquals(
                harness.fixture.envelope.unionEffectEnvelope().volumeBlocks(),
                diagnosis.scannedBlocks());
    }

    @Test
    void recoveryRequiredWithoutSourceOffersOnlyRollback(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        PlacementRecoveryDiagnosisV2 diagnosis = harness.recoveryService(new RecordingJournalStore())
                .diagnose(harness.diagnoseRequest(harness.failedJournal, null));
        assertEquals(PlacementRecoveryClassificationV2.SAFE_TO_ROLLBACK, diagnosis.classification());
        assertEquals(List.of(PlacementRecoveryActionV2.ROLLBACK), diagnosis.safeActions());
        assertTrue(diagnosis.expectedAppliedStreamChecksum().isEmpty());
    }

    @Test
    void diagnosisIsRepeatInvariant(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        PlacementRecoveryServiceV2 service = harness.recoveryService(new RecordingJournalStore());
        PlacementRecoveryDiagnosisV2 first =
                service.diagnose(harness.diagnoseRequest(harness.failedJournal, harness.source));
        PlacementRecoveryDiagnosisV2 second =
                service.diagnose(harness.diagnoseRequest(harness.failedJournal, harness.source));
        assertEquals(first, second);
    }

    @Test
    void terminalJournalsClassifyAsAlreadyTerminal(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRecoveryServiceV2 service = harness.recoveryService(journals);
        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareRollback(service, harness.failedJournal);
        PlacementRollbackServiceV2.RollbackResultV2 rollback = await(service.executeRollback(
                harness.rollbackRequest(prepared, harness.failedJournal,
                        harness.fixture.plan.actor())));
        PlacementJournalV2 rolledBack = rollback.rolledBackJournal();
        PlacementRecoveryDiagnosisV2 diagnosis = service.diagnose(
                harness.diagnoseRequest(rolledBack, null));
        assertEquals(PlacementRecoveryClassificationV2.ALREADY_TERMINAL, diagnosis.classification());
        assertEquals(List.of(PlacementRecoveryActionV2.CLEANUP_SNAPSHOTS), diagnosis.safeActions());
    }

    // --- missing / tampered evidence ------------------------------------------------------------

    @Test
    void missingSnapshotDirectoryRequiresManualIntervention(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        deleteRecursively(harness.snapshotDirectory());
        PlacementRecoveryDiagnosisV2 diagnosis = harness.recoveryService(new RecordingJournalStore())
                .diagnose(harness.diagnoseRequest(harness.failedJournal, harness.source));
        assertEquals(
                PlacementRecoveryClassificationV2.MANUAL_INTERVENTION_REQUIRED,
                diagnosis.classification());
        assertTrue(diagnosis.safeActions().isEmpty());
    }

    @Test
    void tamperedSnapshotFileRequiresManualIntervention(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        Path snapshotFile = harness.snapshotDirectory()
                .resolve(harness.fixture.snapshot.tiles().get(0).snapshotFile());
        byte[] bytes = Files.readAllBytes(snapshotFile);
        bytes[bytes.length - 1] ^= 0x1;
        Files.write(snapshotFile, bytes);
        PlacementRecoveryDiagnosisV2 diagnosis = harness.recoveryService(new RecordingJournalStore())
                .diagnose(harness.diagnoseRequest(harness.failedJournal, harness.source));
        assertEquals(
                PlacementRecoveryClassificationV2.MANUAL_INTERVENTION_REQUIRED,
                diagnosis.classification());
    }

    @Test
    void missingDurableLeaseRequiresManualIntervention(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        harness.fixture.safetyStore.release(harness.fixture.plan.placementId());
        PlacementRecoveryDiagnosisV2 diagnosis = harness.recoveryService(new RecordingJournalStore())
                .diagnose(harness.diagnoseRequest(harness.failedJournal, harness.source));
        assertEquals(
                PlacementRecoveryClassificationV2.MANUAL_INTERVENTION_REQUIRED,
                diagnosis.classification());
        assertTrue(diagnosis.safeActions().isEmpty());
    }

    @Test
    void sourceManifestMismatchWithholdsAcceptEligibility(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        PlacementCanonicalBlockSourceV2 foreign = new PlacementCanonicalBlockSourceV2() {
            @Override
            public SourceBindingV2 binding() {
                PlacementCanonicalBlockSourceV2.SourceBindingV2 original =
                        harness.source.binding();
                return new SourceBindingV2(
                        PlacementCanonicalBlockSourceV2.SOURCE_CONTRACT_VERSION,
                        "0".repeat(64),
                        original.requiredCapabilities(),
                        original.overlayOrdinals(),
                        original.immutableFingerprint());
            }

            @Override
            public BlockCursorV2 openTile(
                    PlacementPlanV2 plan,
                    PlacementPlanV2.TileRefV2 tile,
                    WorldAabbV2 mutationRegion
            ) throws IOException {
                return harness.source.openTile(plan, tile, mutationRegion);
            }
        };
        PlacementRecoveryDiagnosisV2 diagnosis = harness.recoveryService(new RecordingJournalStore())
                .diagnose(harness.diagnoseRequest(harness.failedJournal, foreign));
        assertEquals(PlacementRecoveryClassificationV2.SAFE_TO_ROLLBACK, diagnosis.classification());
        assertTrue(diagnosis.expectedAppliedStreamChecksum().isEmpty());
        assertTrue(diagnosis.findings().stream().anyMatch(
                finding -> finding.contains("manifest checksum")));
    }

    // --- bounded scan budget ---------------------------------------------------------------------

    @Test
    void scanBudgetIsRejectedBeforeAnyGatewayRead(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        PlacementRecoveryServiceV2 tiny = new PlacementRecoveryServiceV2(
                harness.fixture.snapshotCompiler,
                harness.fixture.safetyStore,
                harness.gateway,
                new RecordingJournalStore(),
                harness.rollbackService(new RecordingJournalStore()),
                PlacementApplyTestFixtureV2.CLOCK,
                new PlacementRecoveryLimitsV2(
                        PlacementRecoveryLimitsV2.VERSION, 1L, 64, 4_096, 1_000_000_000L));
        PlacementRecoveryExceptionV2 failure = assertThrows(
                PlacementRecoveryExceptionV2.class,
                () -> tiny.diagnose(harness.diagnoseRequest(harness.failedJournal, harness.source)));
        assertEquals(PlacementRecoveryFailureCodeV2.RESOURCE_BUDGET_EXCEEDED, failure.code());
        assertEquals(0, harness.gateway.streamReads.get());
    }

    // --- confirmation-bound accept ---------------------------------------------------------------

    @Test
    void acceptSealsTerminalAppliedAndReleasesLease(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRecoveryServiceV2 service = harness.recoveryService(journals);
        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareAccept(service, harness.failedJournal);
        PlacementRecoveryServiceV2.AcceptResultV2 result = service.executeAccept(
                harness.acceptRequest(prepared, harness.failedJournal,
                        harness.fixture.plan.actor()));
        assertEquals(PlacementJournalStateV2.APPLIED, result.acceptedJournal().state());
        assertTrue(result.acceptedJournal().tiles().stream()
                .allMatch(tile -> tile.state() == PlacementTileStateV2.VERIFIED));
        assertEquals(result.expectedAppliedStreamChecksum(), result.observedWorldChecksum());
        assertEquals(List.of(PlacementJournalStateV2.APPLIED), journals.states());
        assertThrows(PlacementReservationExceptionV2.class, () ->
                harness.fixture.safetyStore.assertOwned(
                        harness.fixture.plan.placementId(), harness.fixture.plan.actor()));
        // Snapshots stay retained for a later Undo／cleanup.
        PlacementSnapshotPlanV2 reloaded = harness.fixture.snapshotCompiler.loadPublished(
                harness.fixture.plan,
                harness.fixture.envelope,
                harness.fixture.reservation,
                PlacementApplyTestFixtureV2.NEVER);
        assertEquals(harness.fixture.snapshot.canonicalChecksum(), reloaded.canonicalChecksum());
        // World was never mutated by accept.
        for (List<PlacementDesiredBlockV2> blocks : harness.fixture.desiredBlocks.values()) {
            for (PlacementDesiredBlockV2 block : blocks) {
                assertEquals(block.blockState(),
                        harness.gateway.blockAt(block.x(), block.y(), block.z()));
            }
        }
    }

    @Test
    void acceptRefusesWorldDriftWithoutMutation(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRecoveryServiceV2 service = harness.recoveryService(journals);
        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareAccept(service, harness.failedJournal);
        WorldAabbV2 union = harness.fixture.envelope.unionEffectEnvelope();
        harness.gateway.put(union.minX(), union.minY(), union.minZ(), "minecraft:bedrock");
        PlacementRecoveryExceptionV2 failure = assertThrows(
                PlacementRecoveryExceptionV2.class,
                () -> service.executeAccept(harness.acceptRequest(
                        prepared, harness.failedJournal, harness.fixture.plan.actor())));
        assertEquals(PlacementRecoveryFailureCodeV2.WORLD_DRIFT, failure.code());
        assertFalse(failure.worldMutationMayHaveOccurred());
        assertTrue(journals.saved.isEmpty());
        harness.fixture.safetyStore.assertOwned(
                harness.fixture.plan.placementId(), harness.fixture.plan.actor());
    }

    @Test
    void prepareRefusesActionTheDiagnosisDoesNotPermit(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        PlacementRecoveryServiceV2 service = harness.recoveryService(new RecordingJournalStore());
        PlacementRecoveryDiagnosisV2 rollbackOnly = service.diagnose(
                harness.diagnoseRequest(harness.failedJournal, null));
        PlacementRecoveryExceptionV2 failure = assertThrows(
                PlacementRecoveryExceptionV2.class,
                () -> service.prepare(harness.prepareRequest(
                        rollbackOnly, PlacementRecoveryActionV2.ACCEPT, harness.failedJournal)));
        assertEquals(PlacementRecoveryFailureCodeV2.CLASSIFICATION_MISMATCH, failure.code());
    }

    @Test
    void staleDiagnosisIsRejectedOnPrepare(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        PlacementRecoveryServiceV2 service = harness.recoveryService(new RecordingJournalStore());
        PlacementRecoveryDiagnosisV2 diagnosis = service.diagnose(
                harness.diagnoseRequest(harness.failedJournal, null));
        PlacementRecoveryExceptionV2 failure = assertThrows(
                PlacementRecoveryExceptionV2.class,
                () -> service.prepare(harness.prepareRequest(
                        diagnosis, PlacementRecoveryActionV2.ROLLBACK, harness.fixture.journal)));
        assertEquals(PlacementRecoveryFailureCodeV2.STATE_MISMATCH, failure.code());
    }

    // --- confirmation-bound rollback -------------------------------------------------------------

    @Test
    void rollbackRestoresBaselineAndSealsRolledBack(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRecoveryServiceV2 service = harness.recoveryService(journals);
        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareRollback(service, harness.failedJournal);
        PlacementRollbackServiceV2.RollbackResultV2 result = await(service.executeRollback(
                harness.rollbackRequest(prepared, harness.failedJournal,
                        harness.fixture.plan.actor())));
        assertEquals(PlacementJournalStateV2.ROLLED_BACK, result.rolledBackJournal().state());
        assertEquals(result.baselineChecksum(), result.observedChecksum());
        for (List<PlacementDesiredBlockV2> blocks : harness.fixture.desiredBlocks.values()) {
            for (PlacementDesiredBlockV2 block : blocks) {
                assertEquals(BASELINE_STATE,
                        harness.gateway.blockAt(block.x(), block.y(), block.z()));
            }
        }
        assertThrows(PlacementReservationExceptionV2.class, () ->
                harness.fixture.safetyStore.assertOwned(
                        harness.fixture.plan.placementId(), harness.fixture.plan.actor()));
    }

    @Test
    void interruptedRollbackJournalIsReconciledDeterministically(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, true);
        // Fail the second restore call so the first (last-tile) restore checkpoints, leaving a
        // RECOVERY_REQUIRED journal that carries restore progress (canonical RESTORED suffix).
        harness.gateway.failRestoreOnCall = 1;
        RecordingJournalStore failedJournals = new RecordingJournalStore();
        try (PlacementRollbackServiceV2 failing = harness.rollbackService(failedJournals)) {
            failure(failing.rollback(
                    new com.github.nankotsu029.landformcraft.core.v2.placement.rollback
                            .PlacementRollbackRequestV2(
                            harness.fixture.plan,
                            harness.fixture.envelope,
                            harness.fixture.reservation,
                            harness.fixture.snapshot,
                            harness.failedJournal,
                            PlacementSettleVerifyPolicyV2.standard(),
                            PlacementApplyTestFixtureV2.NEVER)));
        }
        harness.gateway.failRestoreOnCall = null;
        PlacementJournalV2 interrupted = failedJournals.last();
        assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, interrupted.state());
        assertEquals(PlacementTileStateV2.RESTORED, interrupted.tiles().get(1).state());

        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRecoveryServiceV2 service = harness.recoveryService(journals);
        PlacementRecoveryDiagnosisV2 diagnosis = service.diagnose(
                harness.diagnoseRequest(interrupted, null));
        assertEquals(PlacementRecoveryClassificationV2.SAFE_TO_ROLLBACK, diagnosis.classification());
        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareRollback(service, interrupted);
        PlacementRollbackServiceV2.RollbackResultV2 result = await(service.executeRollback(
                harness.rollbackRequest(prepared, interrupted, harness.fixture.plan.actor())));
        assertEquals(PlacementJournalStateV2.ROLLED_BACK, result.rolledBackJournal().state());
        // Late-operation reconciliation resealed the journal deterministically before restoring.
        PlacementJournalV2 reconciled = journals.saved.get(0);
        assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, reconciled.state());
        assertEquals(
                List.of(PlacementTileStateV2.APPLIED, PlacementTileStateV2.SNAPSHOTTED),
                reconciled.tiles().stream()
                        .map(PlacementJournalV2.PlacementTileEntryV2::state)
                        .toList());
        for (List<PlacementDesiredBlockV2> blocks : harness.fixture.desiredBlocks.values()) {
            for (PlacementDesiredBlockV2 block : blocks) {
                assertEquals(BASELINE_STATE,
                        harness.gateway.blockAt(block.x(), block.y(), block.z()));
            }
        }
    }

    // --- confirmation misuse ---------------------------------------------------------------------

    @Test
    void consumedConfirmationIsRejectedOnReplay(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRecoveryServiceV2 service = harness.recoveryService(journals);
        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareRollback(service, harness.failedJournal);
        await(service.executeRollback(harness.rollbackRequest(
                prepared, harness.failedJournal, harness.fixture.plan.actor())));
        PlacementRecoveryExceptionV2 replay = assertThrows(
                PlacementRecoveryExceptionV2.class,
                () -> service.executeRollback(harness.rollbackRequest(
                        prepared, harness.failedJournal, harness.fixture.plan.actor())));
        assertEquals(PlacementRecoveryFailureCodeV2.CONFIRMATION_REPLAY, replay.code());
    }

    @Test
    void foreignActorIsRejected(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        PlacementRecoveryServiceV2 service = harness.recoveryService(new RecordingJournalStore());
        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareRollback(service, harness.failedJournal);
        PlacementRecoveryExceptionV2 failure = assertThrows(
                PlacementRecoveryExceptionV2.class,
                () -> service.executeRollback(harness.rollbackRequest(
                        prepared,
                        harness.failedJournal,
                        new PlacementPlanV2.PlacementActorV2(
                                PlacementActorKindV2.SYSTEM, "RECOVERY"))));
        assertEquals(PlacementRecoveryFailureCodeV2.ACTOR_MISMATCH, failure.code());
    }

    @Test
    void expiredConfirmationIsRejected(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRecoveryServiceV2 issuing = harness.recoveryService(journals);
        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareRollback(issuing, harness.failedJournal);
        PlacementRecoveryServiceV2 late = new PlacementRecoveryServiceV2(
                harness.fixture.snapshotCompiler,
                harness.fixture.safetyStore,
                harness.gateway,
                journals,
                harness.rollbackService(journals),
                Clock.offset(PlacementApplyTestFixtureV2.CLOCK, Duration.ofMinutes(11)));
        PlacementRecoveryExceptionV2 failure = assertThrows(
                PlacementRecoveryExceptionV2.class,
                () -> late.executeRollback(harness.rollbackRequest(
                        prepared, harness.failedJournal, harness.fixture.plan.actor())));
        assertEquals(PlacementRecoveryFailureCodeV2.CONFIRMATION_EXPIRED, failure.code());
    }

    // --- journal persistence failure (disk full proxy) ------------------------------------------

    @Test
    void journalPersistenceFailureKeepsLeaseAndSurfacesFailure(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        PlacementJournalStoreV2 failingStore = journal -> {
            throw new IOException("injected disk-full journal failure");
        };
        PlacementRecoveryServiceV2 service = new PlacementRecoveryServiceV2(
                harness.fixture.snapshotCompiler,
                harness.fixture.safetyStore,
                harness.gateway,
                failingStore,
                harness.rollbackService(new RecordingJournalStore()),
                PlacementApplyTestFixtureV2.CLOCK);
        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareAccept(service, harness.failedJournal);
        PlacementRecoveryExceptionV2 failure = assertThrows(
                PlacementRecoveryExceptionV2.class,
                () -> service.executeAccept(harness.acceptRequest(
                        prepared, harness.failedJournal, harness.fixture.plan.actor())));
        assertEquals(PlacementRecoveryFailureCodeV2.JOURNAL_PERSISTENCE_FAILED, failure.code());
        harness.fixture.safetyStore.assertOwned(
                harness.fixture.plan.placementId(), harness.fixture.plan.actor());
    }

    // --- cleanup retention -----------------------------------------------------------------------

    @Test
    void cleanupIsDryRunBoundAndDeletesOnlyTerminalSnapshots(@TempDir Path directory)
            throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRecoveryServiceV2 service = harness.recoveryService(journals);
        // Not yet terminal: refused.
        PlacementRecoveryExceptionV2 notEligible = assertThrows(
                PlacementRecoveryExceptionV2.class,
                () -> service.planCleanup(
                        harness.fixture.plan, harness.failedJournal,
                        PlacementApplyTestFixtureV2.NEVER));
        assertEquals(PlacementRecoveryFailureCodeV2.CLEANUP_NOT_ELIGIBLE, notEligible.code());

        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareRollback(service, harness.failedJournal);
        PlacementJournalV2 rolledBack = await(service.executeRollback(harness.rollbackRequest(
                prepared, harness.failedJournal, harness.fixture.plan.actor())))
                .rolledBackJournal();
        PlacementRecoveryCleanupPlanV2 plan = service.planCleanup(
                harness.fixture.plan, rolledBack, PlacementApplyTestFixtureV2.NEVER);
        assertFalse(plan.files().isEmpty());
        assertTrue(plan.totalBytes() > 0L);
        assertTrue(Files.exists(harness.snapshotDirectory()));

        // A stale dry-run plan is refused after the file set changes.
        Path extra = harness.snapshotDirectory().resolve("late-file.bin");
        Files.write(extra, new byte[] {1});
        PlacementRecoveryExceptionV2 stale = assertThrows(
                PlacementRecoveryExceptionV2.class,
                () -> service.executeCleanup(
                        plan, harness.fixture.plan, rolledBack, PlacementApplyTestFixtureV2.NEVER));
        assertEquals(PlacementRecoveryFailureCodeV2.CLEANUP_PLAN_STALE, stale.code());
        Files.delete(extra);

        long freed = service.executeCleanup(
                plan, harness.fixture.plan, rolledBack, PlacementApplyTestFixtureV2.NEVER);
        assertEquals(plan.totalBytes(), freed);
        assertFalse(Files.exists(harness.snapshotDirectory()));
        assertTrue(service.planCleanup(
                harness.fixture.plan, rolledBack, PlacementApplyTestFixtureV2.NEVER)
                .files().isEmpty());
    }

    @Test
    void cleanupRetentionBudgetIsEnforced(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementRecoveryServiceV2 service = harness.recoveryService(journals);
        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareRollback(service, harness.failedJournal);
        PlacementJournalV2 rolledBack = await(service.executeRollback(harness.rollbackRequest(
                prepared, harness.failedJournal, harness.fixture.plan.actor())))
                .rolledBackJournal();
        PlacementRecoveryServiceV2 tiny = new PlacementRecoveryServiceV2(
                harness.fixture.snapshotCompiler,
                harness.fixture.safetyStore,
                harness.gateway,
                journals,
                harness.rollbackService(journals),
                PlacementApplyTestFixtureV2.CLOCK,
                new PlacementRecoveryLimitsV2(
                        PlacementRecoveryLimitsV2.VERSION, 1_000_000L, 64, 4_096, 1L));
        PlacementRecoveryExceptionV2 failure = assertThrows(
                PlacementRecoveryExceptionV2.class,
                () -> tiny.planCleanup(
                        harness.fixture.plan, rolledBack, PlacementApplyTestFixtureV2.NEVER));
        assertEquals(PlacementRecoveryFailureCodeV2.RESOURCE_BUDGET_EXCEEDED, failure.code());
        assertTrue(Files.exists(harness.snapshotDirectory()));
    }

    // --- codec round trip and example ------------------------------------------------------------

    @Test
    void recoveryPlanCodecRoundTrip(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        PlacementRecoveryServiceV2 service = harness.recoveryService(new RecordingJournalStore());
        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared =
                harness.prepareAccept(service, harness.failedJournal);
        LandformV2DataCodec codec = new LandformV2DataCodec();
        Path path = directory.resolve("placement-recovery-plan-v2.json");
        codec.writePlacementRecoveryPlan(path, prepared.recoveryPlan());
        PlacementRecoveryPlanV2 read = codec.readPlacementRecoveryPlan(path);
        assertEquals(prepared.recoveryPlan(), read);
        assertEquals(codec.placementRecoveryPlanChecksum(read), read.canonicalChecksum());
        Path example = Path.of("examples/v2/placement/placement-recovery-plan-v2.json");
        codec.writePlacementRecoveryPlan(example, prepared.recoveryPlan());
    }

    @Test
    void applicationServiceIsExplicitRelease2Path(@TempDir Path directory) throws Exception {
        Harness harness = Harness.afterRecoveryRequired(directory, false);
        PlacementRecoveryApplicationServiceV2 application =
                new PlacementRecoveryApplicationServiceV2(
                        harness.recoveryService(new RecordingJournalStore()));
        assertTrue(application.isRelease2Path());
        PlacementRecoveryDiagnosisV2 diagnosis = application.diagnose(
                harness.diagnoseRequest(harness.failedJournal, harness.source));
        assertEquals(PlacementRecoveryClassificationV2.SAFE_TO_ACCEPT, diagnosis.classification());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Throwable failure(CompletionStage<?> stage) throws Exception {
        ExecutionException execution = assertThrows(
                ExecutionException.class,
                () -> stage.toCompletableFuture().get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        Throwable failure = execution.getCause();
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
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
     * Runs the real apply (V2-6-06) and settle／verify (V2-6-07) services against a shared
     * in-memory world, injecting one corrupted verify read so a genuine RECOVERY_REQUIRED journal
     * is persisted while the world actually holds the fully applied content.
     */
    private static final class Harness {
        final PlacementApplyTestFixtureV2 fixture;
        final RecoveryWorldGateway gateway;
        final PlacementApplyTestFixtureV2.ImmutableSource source;
        final PlacementJournalV2 failedJournal;

        private Harness(
                PlacementApplyTestFixtureV2 fixture,
                RecoveryWorldGateway gateway,
                PlacementApplyTestFixtureV2.ImmutableSource source,
                PlacementJournalV2 failedJournal
        ) {
            this.fixture = fixture;
            this.gateway = gateway;
            this.source = source;
            this.failedJournal = failedJournal;
        }

        static Harness afterRecoveryRequired(Path root, boolean twoTiles) throws Exception {
            PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(root, twoTiles);
            RecoveryWorldGateway gateway = new RecoveryWorldGateway();
            PlacementApplyTestFixtureV2.ImmutableSource source = fixture.source(false);
            RecordingJournalStore applyJournals = new RecordingJournalStore();
            PlacementJournalV2 applyComplete;
            try (PlacementApplyTransactionServiceV2 applyService =
                    new PlacementApplyTransactionServiceV2(
                            fixture.strictVerifier(),
                            gateway,
                            applyJournals,
                            PlacementApplyTestFixtureV2.CLOCK,
                            new PlacementApplyLimitsV2(
                                    PlacementApplyLimitsV2.VERSION,
                                    2, 2, 32, 32, 1_000_000_000L, 640))) {
                applyComplete = applyService
                        .apply(fixture.request(source, PlacementApplyTestFixtureV2.NEVER))
                        .toCompletableFuture()
                        .get(WAIT.toMillis(), TimeUnit.MILLISECONDS)
                        .applyCompleteJournal();
            }
            gateway.corruptFirstVerifyBlock = true;
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
                                source,
                                (x, y, z) -> BASELINE_STATE,
                                PlacementSettleVerifyPolicyV2.standard(),
                                PlacementApplyTestFixtureV2.NEVER))
                        .toCompletableFuture()
                        .get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
            }
            gateway.corruptFirstVerifyBlock = false;
            PlacementJournalV2 failed = verifyJournals.last();
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, failed.state());
            return new Harness(fixture, gateway, source, failed);
        }

        PlacementRollbackServiceV2 rollbackService(PlacementJournalStoreV2 journals) {
            return new PlacementRollbackServiceV2(
                    fixture.snapshotCompiler,
                    fixture.safetyStore,
                    gateway,
                    journals,
                    PlacementApplyTestFixtureV2.CLOCK,
                    new PlacementRollbackLimitsV2(
                            PlacementRollbackLimitsV2.VERSION, 2, 16, 1_024, 1_000_000L));
        }

        PlacementRecoveryServiceV2 recoveryService(PlacementJournalStoreV2 journals) {
            return new PlacementRecoveryServiceV2(
                    fixture.snapshotCompiler,
                    fixture.safetyStore,
                    gateway,
                    journals,
                    rollbackService(journals),
                    PlacementApplyTestFixtureV2.CLOCK);
        }

        PlacementRecoveryDiagnoseRequestV2 diagnoseRequest(
                PlacementJournalV2 journal,
                PlacementCanonicalBlockSourceV2 blockSource
        ) {
            return new PlacementRecoveryDiagnoseRequestV2(
                    fixture.plan,
                    fixture.envelope,
                    fixture.reservation,
                    fixture.snapshot,
                    journal,
                    blockSource,
                    PlacementApplyTestFixtureV2.NEVER);
        }

        PlacementRecoveryPrepareRequestV2 prepareRequest(
                PlacementRecoveryDiagnosisV2 diagnosis,
                PlacementRecoveryActionV2 action,
                PlacementJournalV2 journal
        ) {
            return new PlacementRecoveryPrepareRequestV2(
                    diagnosis,
                    action,
                    fixture.plan,
                    fixture.envelope,
                    fixture.reservation,
                    fixture.snapshot,
                    journal,
                    null,
                    null,
                    PlacementApplyTestFixtureV2.NEVER);
        }

        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepareRollback(
                PlacementRecoveryServiceV2 service,
                PlacementJournalV2 journal
        ) {
            PlacementRecoveryDiagnosisV2 diagnosis =
                    service.diagnose(diagnoseRequest(journal, null));
            return service.prepare(prepareRequest(
                    diagnosis, PlacementRecoveryActionV2.ROLLBACK, journal));
        }

        PlacementRecoveryServiceV2.PreparedRecoveryV2 prepareAccept(
                PlacementRecoveryServiceV2 service,
                PlacementJournalV2 journal
        ) {
            PlacementRecoveryDiagnosisV2 diagnosis =
                    service.diagnose(diagnoseRequest(journal, source));
            return service.prepare(prepareRequest(
                    diagnosis, PlacementRecoveryActionV2.ACCEPT, journal));
        }

        PlacementRecoveryRollbackRequestV2 rollbackRequest(
                PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared,
                PlacementJournalV2 journal,
                PlacementPlanV2.PlacementActorV2 actor
        ) {
            return new PlacementRecoveryRollbackRequestV2(
                    prepared.recoveryPlan(),
                    fixture.plan,
                    fixture.envelope,
                    fixture.reservation,
                    fixture.snapshot,
                    journal,
                    actor,
                    prepared.plaintextToken(),
                    PlacementSettleVerifyPolicyV2.standard(),
                    PlacementApplyTestFixtureV2.NEVER);
        }

        PlacementRecoveryAcceptRequestV2 acceptRequest(
                PlacementRecoveryServiceV2.PreparedRecoveryV2 prepared,
                PlacementJournalV2 journal,
                PlacementPlanV2.PlacementActorV2 actor
        ) {
            return new PlacementRecoveryAcceptRequestV2(
                    prepared.recoveryPlan(),
                    fixture.plan,
                    fixture.envelope,
                    fixture.reservation,
                    fixture.snapshot,
                    journal,
                    source,
                    actor,
                    prepared.plaintextToken(),
                    PlacementApplyTestFixtureV2.NEVER);
        }

        Path snapshotDirectory() {
            return fixture.safetyStore.snapshotsRoot()
                    .resolve(fixture.plan.placementId().toString());
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

    /** Shared in-memory world for apply → settle/verify failure → recovery flows. */
    private static final class RecoveryWorldGateway implements PlacementWorldGatewayV2 {
        private final Map<String, String> world = new ConcurrentHashMap<>();
        final AtomicInteger streamReads = new AtomicInteger();
        final List<PlacementRestoreSliceV2> restoreSlices = new CopyOnWriteArrayList<>();
        volatile boolean corruptFirstVerifyBlock;
        volatile Integer failRestoreOnCall;
        private final AtomicInteger restoreCalls = new AtomicInteger();
        private final java.util.concurrent.atomic.AtomicBoolean verifyCorrupted =
                new java.util.concurrent.atomic.AtomicBoolean();

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
                PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    for (int x = region.minX(); x <= region.maxX(); x++) {
                        streamReads.incrementAndGet();
                        consumer.accept(x, y, z, blockAt(x, y, z));
                    }
                }
            }
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
            return CompletableFuture.completedFuture(new PlacementRestoreSliceReceiptV2(
                            slice.operationId(),
                            slice.tileId(),
                            slice.sliceSequence(),
                            slice.blocks().size(),
                            true,
                            true,
                            true))
                    .minimalCompletionStage();
        }

        @Override
        public CompletionStage<PlacementSettleTickReceiptV2> advanceSettleTick(
                PlacementSettleTickV2 tick
        ) {
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
                int[] xyz = PlacementRecoveryServiceV2.decodeCanonicalIndex(
                        slice.effectEnvelope(), slice.startIndex() + i);
                String state = blockAt(xyz[0], xyz[1], xyz[2]);
                if (corruptFirstVerifyBlock && verifyCorrupted.compareAndSet(false, true)) {
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
