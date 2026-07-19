package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
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
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryActionV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryClassificationV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.Release2DiskBudgetV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementActorKindV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2MeasuredDimensionGateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2MeasurementProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2PlacementDimensionPolicyV2;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Release2PlacementApplicationServiceV2Test {
    private static final UUID WORLD = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final long SNAPSHOT_BYTES = 64L * 1024L * 1024L;

    @Test
    void explicitPlanConfirmExecuteRunsTheFullSafeOrderAndSurvivesStatusReload(@TempDir Path root)
            throws Exception {
        Path releases = root.resolve("releases");
        EnvironmentReleaseFixtureV2.Fixture fixture = EnvironmentReleaseFixtureV2.build(root.resolve("source"));
        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                releases, "paper-lifecycle", fixture.source().hydrology().surface(), false, () -> false);
        FakeWorld gateway = new FakeWorld("minecraft:stone");
        GenerationExecutors executors = GenerationExecutors.create(4, 2, 16);
        Release2PlacementApplicationServiceV2 service = new Release2PlacementApplicationServiceV2(
                releases, root.resolve("state"), executors, gateway, Clock.systemUTC(),
                64L * 1024L * 1024L);
        try {
            var prepared = service.plan(new Release2PlacementApplicationServiceV2.PlanRequestV2(
                    release.releaseDirectory().getFileName().toString(), WORLD, "world",
                    10, 64, 20, new WorldAabbV2(-100, -64, -100, 100, 320, 100),
                    PlacementPlanV2.PlacementActorV2.console(), () -> false))
                    .toCompletableFuture().join();
            assertEquals(PlacementJournalStateV2.CONFIRMATION_ISSUED, prepared.journal().state());
            assertEquals(0, gateway.mutationCalls);
            assertEquals(1, service.inspectRestartState().toCompletableFuture().join().size());

            var confirmed = service.confirm(new Release2PlacementApplicationServiceV2.ConfirmRequestV2(
                    prepared.plan().placementId(), prepared.confirmationToken(),
                    PlacementPlanV2.PlacementActorV2.console(), () -> false))
                    .toCompletableFuture().join();
            assertEquals(PlacementJournalStateV2.SNAPSHOT_COMPLETE, confirmed.journal().state());
            assertEquals(0, gateway.mutationCalls);
            assertTrue(gateway.snapshotReads > 0);

            var executed = service.execute(new Release2PlacementApplicationServiceV2.ExecuteRequestV2(
                    prepared.plan().placementId(), PlacementPlanV2.PlacementActorV2.console(),
                    () -> false)).toCompletableFuture().join();
            assertEquals(PlacementJournalStateV2.APPLIED, executed.journal().state());
            assertFalse(executed.rolledBack());
            assertTrue(gateway.mutationCalls > 0);
            assertTrue(gateway.firstMutationAfterSnapshot);
            assertEquals(PlacementJournalStateV2.APPLIED,
                    service.status(prepared.plan().placementId()).toCompletableFuture().join().state());
            assertTrue(service.inspectRestartState().toCompletableFuture().join().isEmpty());
        } finally {
            service.close();
            assertTrue(executors.shutdown(java.time.Duration.ofSeconds(5)));
        }
        assertThrows(Exception.class, () -> service.status(UUID.randomUUID()).toCompletableFuture().join());
    }

    @Test
    void containmentFailureNeverMutatesTheWorld(@TempDir Path root) throws Exception {
        Path releases = root.resolve("releases");
        EnvironmentReleaseFixtureV2.Fixture fixture = EnvironmentReleaseFixtureV2.build(root.resolve("source"));
        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                releases, "paper-containment", fixture.source().hydrology().surface(), false, () -> false);
        FakeWorld gateway = new FakeWorld("minecraft:tnt");
        GenerationExecutors executors = GenerationExecutors.create(2, 1, 8);
        Release2PlacementApplicationServiceV2 service = new Release2PlacementApplicationServiceV2(
                releases, root.resolve("state"), executors, gateway, Clock.systemUTC(),
                64L * 1024L * 1024L);
        try {
            var prepared = service.plan(new Release2PlacementApplicationServiceV2.PlanRequestV2(
                    release.releaseDirectory().getFileName().toString(), WORLD, "world",
                    0, 64, 0, new WorldAabbV2(-100, -64, -100, 100, 320, 100),
                    PlacementPlanV2.PlacementActorV2.console(), () -> false))
                    .toCompletableFuture().join();
            assertThrows(Exception.class, () -> service.confirm(
                    new Release2PlacementApplicationServiceV2.ConfirmRequestV2(
                            prepared.plan().placementId(), prepared.confirmationToken(),
                            PlacementPlanV2.PlacementActorV2.console(), () -> false))
                    .toCompletableFuture().join());
            assertEquals(0, gateway.mutationCalls);
        } finally {
            service.close();
            executors.shutdown(java.time.Duration.ofSeconds(5));
        }
    }

    @Test
    void exactVerifyFailureTriggersReverseRollbackAndNeverReportsApplied(@TempDir Path root)
            throws Exception {
        Path releases = root.resolve("releases");
        EnvironmentReleaseFixtureV2.Fixture fixture = EnvironmentReleaseFixtureV2.build(root.resolve("source"));
        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                releases, "paper-rollback", fixture.source().hydrology().surface(), false, () -> false);
        FakeWorld gateway = new FakeWorld("minecraft:stone");
        gateway.corruptNextVerify = true;
        GenerationExecutors executors = GenerationExecutors.create(4, 2, 16);
        Release2PlacementApplicationServiceV2 service = new Release2PlacementApplicationServiceV2(
                releases, root.resolve("state"), executors, gateway, Clock.systemUTC(),
                64L * 1024L * 1024L);
        try {
            var prepared = service.plan(new Release2PlacementApplicationServiceV2.PlanRequestV2(
                    release.releaseDirectory().getFileName().toString(), WORLD, "world",
                    0, 64, 0, new WorldAabbV2(-100, -64, -100, 100, 320, 100),
                    PlacementPlanV2.PlacementActorV2.console(), () -> false))
                    .toCompletableFuture().join();
            service.confirm(new Release2PlacementApplicationServiceV2.ConfirmRequestV2(
                    prepared.plan().placementId(), prepared.confirmationToken(),
                    PlacementPlanV2.PlacementActorV2.console(), () -> false))
                    .toCompletableFuture().join();

            var result = service.execute(new Release2PlacementApplicationServiceV2.ExecuteRequestV2(
                    prepared.plan().placementId(), PlacementPlanV2.PlacementActorV2.console(),
                    () -> false)).toCompletableFuture().join();

            assertTrue(result.rolledBack());
            assertEquals(PlacementJournalStateV2.ROLLED_BACK, result.journal().state());
            assertTrue(gateway.restoreCalls > 0);
        } finally {
            service.close();
            executors.shutdown(java.time.Duration.ofSeconds(5));
        }
    }

    @Test
    void restartBetweenConfirmApplyAndUndoKeepsSelfLeaseButRejectsThirdPartyOverlap(
            @TempDir Path root
    ) throws Exception {
        Path releases = root.resolve("releases");
        EnvironmentReleaseFixtureV2.Fixture fixture = EnvironmentReleaseFixtureV2.build(root.resolve("source"));
        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                releases, "paper-restart-undo", fixture.source().hydrology().surface(), false, () -> false);
        String releaseName = release.releaseDirectory().getFileName().toString();
        Path state = root.resolve("state");
        FakeWorld gateway = new FakeWorld("minecraft:stone");
        GenerationExecutors executors = GenerationExecutors.create(4, 2, 16);
        Release2PlacementApplicationServiceV2 service =
                service(releases, state, executors, gateway, Release2PlacementOperationStoreV2
                        .WriteFaultInjectorV2.none());
        try {
            var prepared = service.plan(planRequest(releaseName, 10, 64, 20))
                    .toCompletableFuture().join();
            service.confirm(confirmRequest(prepared)).toCompletableFuture().join();

            service.close();
            service = service(releases, state, executors, gateway,
                    Release2PlacementOperationStoreV2.WriteFaultInjectorV2.none());
            assertEquals(1, service.inspectRestartState().toCompletableFuture().join().size());
            var applied = service.execute(executeRequest(prepared.plan().placementId()))
                    .toCompletableFuture().join();
            assertEquals(PlacementJournalStateV2.APPLIED, applied.journal().state());

            service.close();
            service = service(releases, state, executors, gateway,
                    Release2PlacementOperationStoreV2.WriteFaultInjectorV2.none());
            assertTrue(service.inspectRestartState().toCompletableFuture().join().isEmpty());

            assertStageFails(service.plan(planRequest(releaseName, 10, 64, 20)),
                    "third-party overlap after restart");

            var undo = service.prepareUndo(
                    prepared.plan().placementId(), PlacementPlanV2.PlacementActorV2.console())
                    .toCompletableFuture().join();
            var undone = service.executeUndo(
                    prepared.plan().placementId(), undo.plaintextToken(),
                    PlacementPlanV2.PlacementActorV2.console(), () -> false)
                    .toCompletableFuture().join();
            assertEquals(PlacementJournalStateV2.UNDONE, undone.undoneJournal().state());
            assertTrue(gateway.restoreCalls > 0);
            assertTrue(gateway.allTrackedBlocksMatch("minecraft:stone"));
        } finally {
            service.close();
            assertTrue(executors.shutdown(Duration.ofSeconds(10)));
        }
    }

    @Test
    void everyProductionOperationStoreWriteFailpointIsRestartSafe(
            @TempDir Path root
    ) throws Exception {
        Path releases = root.resolve("releases");
        EnvironmentReleaseFixtureV2.Fixture fixture = EnvironmentReleaseFixtureV2.build(root.resolve("source"));
        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                releases, "paper-write-failpoints", fixture.source().hydrology().surface(), false,
                () -> false);
        String releaseName = release.releaseDirectory().getFileName().toString();

        for (Release2PlacementOperationStoreV2.WritePointV2 point
                : Release2PlacementOperationStoreV2.WritePointV2.values()) {
            for (Release2PlacementOperationStoreV2.WritePhaseV2 phase
                    : Release2PlacementOperationStoreV2.WritePhaseV2.values()) {
                Path state = root.resolve("state-" + point + "-" + phase);
                FakeWorld gateway = new FakeWorld("minecraft:stone");
                ArmableWriteFault fault = new ArmableWriteFault();
                GenerationExecutors executors = GenerationExecutors.create(2, 1, 16);
                Release2PlacementApplicationServiceV2 service =
                        service(releases, state, executors, gateway, fault);
                try {
                    if (isPreparedWrite(point)) {
                        fault.arm(point, phase);
                        assertStageFails(
                                service.plan(planRequest(releaseName, 0, 64, 0)),
                                point + " " + phase);
                        assertEquals(0, gateway.mutationCalls, point + " " + phase);
                        assertTrue(service.inspectRestartState().toCompletableFuture().join().isEmpty(),
                                point + " " + phase);
                        continue;
                    }

                    var prepared = service.plan(planRequest(releaseName, 0, 64, 0))
                            .toCompletableFuture().join();
                    if (isConfirmedWrite(point)) {
                        fault.arm(point, phase);
                        assertStageFails(service.confirm(confirmRequest(prepared)),
                                point + " " + phase);
                        assertEquals(0, gateway.mutationCalls, point + " " + phase);

                        service.close();
                        service = service(releases, state, executors, gateway, fault);
                        var diagnosis = service.diagnoseRecovery(
                                prepared.plan().placementId(), () -> false)
                                .toCompletableFuture().join();
                        assertEquals(PlacementRecoveryClassificationV2.NO_WORLD_MUTATION,
                                diagnosis.classification(), point + " " + phase);
                        continue;
                    }

                    service.confirm(confirmRequest(prepared)).toCompletableFuture().join();
                    if (point == Release2PlacementOperationStoreV2.WritePointV2.RECOVERY_PLAN) {
                        gateway.corruptNextVerify = true;
                        gateway.failRestore = true;
                        assertStageFails(
                                service.execute(executeRequest(prepared.plan().placementId())),
                                "create RECOVERY_REQUIRED before " + point + " " + phase);
                        gateway.failRestore = false;
                        assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED,
                                service.status(prepared.plan().placementId())
                                        .toCompletableFuture().join().state());

                        fault.arm(point, phase);
                        assertStageFails(service.prepareRecovery(
                                        prepared.plan().placementId(), PlacementRecoveryActionV2.ROLLBACK,
                                        PlacementPlanV2.PlacementActorV2.console(), () -> false),
                                point + " " + phase);

                        service.close();
                        service = service(releases, state, executors, gateway, fault);
                        var diagnosis = service.diagnoseRecovery(
                                prepared.plan().placementId(), () -> false).toCompletableFuture().join();
                        assertTrue(diagnosis.permits(PlacementRecoveryActionV2.ROLLBACK),
                                point + " " + phase);
                        var recovery = service.prepareRecovery(
                                prepared.plan().placementId(), PlacementRecoveryActionV2.ROLLBACK,
                                PlacementPlanV2.PlacementActorV2.console(), () -> false)
                                .toCompletableFuture().join();
                        var rolledBack = service.executeRecoveryRollback(
                                prepared.plan().placementId(), recovery.plaintextToken(),
                                PlacementPlanV2.PlacementActorV2.console(), () -> false)
                                .toCompletableFuture().join();
                        assertEquals(PlacementJournalStateV2.ROLLED_BACK,
                                rolledBack.rolledBackJournal().state(), point + " " + phase);
                        assertTrue(gateway.allTrackedBlocksMatch("minecraft:stone"),
                                point + " " + phase);
                        continue;
                    }

                    if (isAppliedWrite(point)) {
                        fault.arm(point, phase);
                        var result = service.execute(executeRequest(prepared.plan().placementId()))
                                .toCompletableFuture().join();
                        assertTrue(result.rolledBack(), point + " " + phase);
                        assertEquals(PlacementJournalStateV2.ROLLED_BACK,
                                result.journal().state(), point + " " + phase);
                        assertTrue(gateway.allTrackedBlocksMatch("minecraft:stone"),
                                point + " " + phase);

                        service.close();
                        service = service(releases, state, executors, gateway, fault);
                        assertTrue(service.inspectRestartState().toCompletableFuture().join().isEmpty(),
                                point + " " + phase);
                        continue;
                    }

                    service.execute(executeRequest(prepared.plan().placementId()))
                            .toCompletableFuture().join();
                    if (isUndoWrite(point)) {
                        fault.arm(point, phase);
                        assertStageFails(service.prepareUndo(
                                        prepared.plan().placementId(),
                                        PlacementPlanV2.PlacementActorV2.console()),
                                point + " " + phase);

                        assertStageFails(service.plan(planRequest(releaseName, 0, 64, 0)),
                                "third-party overlap after " + point + " " + phase);

                        service.close();
                        service = service(releases, state, executors, gateway, fault);
                        var undo = service.prepareUndo(
                                prepared.plan().placementId(), PlacementPlanV2.PlacementActorV2.console())
                                .toCompletableFuture().join();
                        service.executeUndo(
                                prepared.plan().placementId(), undo.plaintextToken(),
                                PlacementPlanV2.PlacementActorV2.console(), () -> false)
                                .toCompletableFuture().join();
                        assertTrue(gateway.allTrackedBlocksMatch("minecraft:stone"),
                                point + " " + phase);
                        continue;
                    }
                    throw new AssertionError("unclassified write point " + point);
                } finally {
                    service.close();
                    assertTrue(executors.shutdown(Duration.ofSeconds(10)),
                            point + " " + phase);
                }
            }
        }
    }

    @Test
    void executeRejectsActorMismatchBeforeWorldMutation(@TempDir Path root) throws Exception {
        Path releases = root.resolve("releases");
        EnvironmentReleaseFixtureV2.Fixture fixture = EnvironmentReleaseFixtureV2.build(root.resolve("source"));
        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                releases, "paper-actor-binding", fixture.source().hydrology().surface(), false,
                () -> false);
        FakeWorld gateway = new FakeWorld("minecraft:stone");
        GenerationExecutors executors = GenerationExecutors.create(2, 1, 8);
        Release2PlacementApplicationServiceV2 service = service(
                releases, root.resolve("state"), executors, gateway,
                Release2PlacementOperationStoreV2.WriteFaultInjectorV2.none());
        try {
            var prepared = service.plan(planRequest(
                    release.releaseDirectory().getFileName().toString(), 0, 64, 0))
                    .toCompletableFuture().join();
            service.confirm(confirmRequest(prepared)).toCompletableFuture().join();
            assertStageFails(service.execute(
                    new Release2PlacementApplicationServiceV2.ExecuteRequestV2(
                            prepared.plan().placementId(),
                            PlacementPlanV2.PlacementActorV2.system("OTHER"),
                            () -> false)), "execute actor mismatch");
            assertEquals(0, gateway.mutationCalls);
        } finally {
            service.close();
            assertTrue(executors.shutdown(Duration.ofSeconds(10)));
        }
    }

    /**
     * V2-11-02: a layout above the normal-operation ceiling must fail before any world mutation
     * or durable state write, and must only become reachable through the measurement profile.
     */
    @Test
    void aboveCeilingLayoutIsRejectedBeforeAnyWorldMutationOrStateWrite(@TempDir Path root)
            throws Exception {
        Path releases = root.resolve("releases");
        EnvironmentReleaseFixtureV2.Fixture fixture =
                EnvironmentReleaseFixtureV2.build(root.resolve("source"));
        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                releases, "paper-dimension-guard", fixture.source().hydrology().surface(), false,
                () -> false);
        String releaseName = release.releaseDirectory().getFileName().toString();
        Path state = root.resolve("state");
        FakeWorld gateway = new FakeWorld("minecraft:stone");
        GenerationExecutors executors = GenerationExecutors.create(2, 1, 16);

        // A ceiling of 1x1 stands in for "smaller than this Release" so the same fixture exercises
        // the guard without publishing an unmeasured 500x500 artifact.
        Release2PlacementApplicationServiceV2 clamped = new Release2PlacementApplicationServiceV2(
                releases, state, executors, gateway, Clock.systemUTC(),
                Release2DiskBudgetV2.legacy(SNAPSHOT_BYTES),
                Release2PlacementDimensionPolicyV2.of(new Release2MeasuredDimensionGateV2(1, 1)),
                Release2PlacementOperationStoreV2.WriteFaultInjectorV2.none());
        try {
            assertStageFails(clamped.plan(planRequest(releaseName, 10, 64, 20)),
                    "layout above the normal-operation ceiling");
            assertEquals(0, gateway.restoreCalls);
            assertTrue(isEmptyOrAbsent(state.resolve("journals")),
                    "a rejected plan must not write a journal");
            assertTrue(isEmptyOrAbsent(state.resolve("operations")),
                    "a rejected plan must not write durable operation state");
            assertFalse(Files.exists(state.resolve("placement-safety-v2.json")),
                    "a rejected plan must not take a reservation");
        } finally {
            clamped.close();
        }

        // The same layout on the same world is still rejected for a Player even with the profile
        // enabled, and is admitted only for a CONSOLE/RCON operator on the isolated world.
        Release2PlacementApplicationServiceV2 measurement =
                new Release2PlacementApplicationServiceV2(
                        releases, root.resolve("state-measurement"), executors, gateway,
                        Clock.systemUTC(), Release2DiskBudgetV2.legacy(SNAPSHOT_BYTES),
                        new Release2PlacementDimensionPolicyV2(
                                new Release2MeasuredDimensionGateV2(1, 1),
                                Release2MeasurementProfileV2.forIsolatedWorld("world", 500, 500)),
                        Release2PlacementOperationStoreV2.WriteFaultInjectorV2.none());
        try {
            var prepared = measurement.plan(planRequest(releaseName, 10, 64, 20))
                    .toCompletableFuture().join();
            assertEquals(PlacementActorKindV2.CONSOLE, prepared.plan().actor().kind());
        } finally {
            measurement.close();
            assertTrue(executors.shutdown(Duration.ofSeconds(10)));
        }
    }

    private static boolean isEmptyOrAbsent(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return true;
        }
        try (var entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }

    private static Release2PlacementApplicationServiceV2 service(
            Path releases,
            Path state,
            GenerationExecutors executors,
            FakeWorld gateway,
            Release2PlacementOperationStoreV2.WriteFaultInjectorV2 faultInjector
    ) throws IOException {
        return new Release2PlacementApplicationServiceV2(
                releases, state, executors, gateway, Clock.systemUTC(), SNAPSHOT_BYTES,
                Release2MeasuredDimensionGateV2.unlimited(), faultInjector);
    }

    private static Release2PlacementApplicationServiceV2.PlanRequestV2 planRequest(
            String releaseName,
            int x,
            int y,
            int z
    ) {
        return new Release2PlacementApplicationServiceV2.PlanRequestV2(
                releaseName, WORLD, "world", x, y, z,
                new WorldAabbV2(-100, -64, -100, 100, 320, 100),
                PlacementPlanV2.PlacementActorV2.console(), () -> false);
    }

    private static Release2PlacementApplicationServiceV2.ConfirmRequestV2 confirmRequest(
            Release2PlacementApplicationServiceV2.PreparedPlanV2 prepared
    ) {
        return new Release2PlacementApplicationServiceV2.ConfirmRequestV2(
                prepared.plan().placementId(), prepared.confirmationToken(),
                PlacementPlanV2.PlacementActorV2.console(), () -> false);
    }

    private static Release2PlacementApplicationServiceV2.ExecuteRequestV2 executeRequest(
            UUID placementId
    ) {
        return new Release2PlacementApplicationServiceV2.ExecuteRequestV2(
                placementId, PlacementPlanV2.PlacementActorV2.console(), () -> false);
    }

    private static void assertStageFails(CompletionStage<?> stage, String message) {
        assertThrows(Exception.class, () -> stage.toCompletableFuture().join(), message);
    }

    private static boolean isPreparedWrite(Release2PlacementOperationStoreV2.WritePointV2 point) {
        return switch (point) {
            case PREPARED_ENVELOPE, PREPARED_RESERVATION, PREPARED_JOURNAL -> true;
            default -> false;
        };
    }

    private static boolean isConfirmedWrite(Release2PlacementOperationStoreV2.WritePointV2 point) {
        return switch (point) {
            case CONFIRMED_SNAPSHOT, CONFIRMED_CONTAINMENT, CONFIRMED_JOURNAL -> true;
            default -> false;
        };
    }

    private static boolean isAppliedWrite(Release2PlacementOperationStoreV2.WritePointV2 point) {
        return switch (point) {
            case APPLIED_VERIFY_EVIDENCE, APPLIED_APPLY_COMPLETE_JOURNAL,
                    APPLIED_TERMINAL_JOURNAL -> true;
            default -> false;
        };
    }

    private static boolean isUndoWrite(Release2PlacementOperationStoreV2.WritePointV2 point) {
        return point == Release2PlacementOperationStoreV2.WritePointV2.UNDO_PLAN
                || point == Release2PlacementOperationStoreV2.WritePointV2.UNDO_RESERVATION;
    }

    private static final class ArmableWriteFault
            implements Release2PlacementOperationStoreV2.WriteFaultInjectorV2 {
        private Release2PlacementOperationStoreV2.WritePointV2 point;
        private Release2PlacementOperationStoreV2.WritePhaseV2 phase;
        private boolean armed;

        synchronized void arm(
                Release2PlacementOperationStoreV2.WritePointV2 point,
                Release2PlacementOperationStoreV2.WritePhaseV2 phase
        ) {
            this.point = point;
            this.phase = phase;
            this.armed = true;
        }

        @Override
        public synchronized void check(
                UUID placementId,
                Release2PlacementOperationStoreV2.WritePointV2 actualPoint,
                Release2PlacementOperationStoreV2.WritePhaseV2 actualPhase
        ) throws IOException {
            if (armed && point == actualPoint && phase == actualPhase) {
                armed = false;
                throw new IOException("injected operation-store write failure at "
                        + actualPoint + " " + actualPhase);
            }
        }
    }

    private static final class FakeWorld implements PlacementWorldGatewayV2 {
        private final String defaultState;
        private final Map<Coord, String> states = new ConcurrentHashMap<>();
        private int snapshotReads;
        private int mutationCalls;
        private boolean firstMutationAfterSnapshot;
        private boolean corruptNextVerify;
        private int restoreCalls;
        private boolean failRestore;

        private FakeWorld(String defaultState) {
            this.defaultState = defaultState;
        }

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
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
            if (mutationCalls++ == 0) firstMutationAfterSnapshot = snapshotReads >= 2;
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
            if (failRestore) {
                return CompletableFuture.failedFuture(new IOException("injected restore failure"));
            }
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
            if (corruptNextVerify && !blocks.isEmpty()) {
                corruptNextVerify = false;
                blocks.set(0, blocks.getFirst().equals("minecraft:air")
                        ? "minecraft:stone" : "minecraft:air");
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
