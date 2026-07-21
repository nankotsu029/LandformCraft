package com.github.nankotsu029.landformcraft.core.v2.operations;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyTestFixtureV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryCleanupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalAuditEventKindV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalAuditEventV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalDiagnosticsReportV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricLabelV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricsSnapshotV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.Release2RetentionCleanupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalOperationsV2Test {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void capturesBoundedMetricsAndRejectsSensitiveAuditDetail(@TempDir Path root) throws Exception {
        try (GenerationExecutors executors = GenerationExecutors.create(2, 2, 8)) {
            OperationalAuditLogV2 audit = new OperationalAuditLogV2(root);
            RetentionCleanupPortV2 unsupported = unsupportedCleanup();
            Release2RetentionServiceV2 retention =
                    new Release2RetentionServiceV2(unsupported, audit, CLOCK);
            OperationalOperationsServiceV2 ops = new OperationalOperationsServiceV2(
                    executors, root,
                    new OperationalMetricsCollectorV2(CLOCK,
                            java.lang.management.ManagementFactory.getMemoryMXBean()),
                    audit,
                    new OperationalDiagnosticsServiceV2(audit, CLOCK),
                    retention,
                    CLOCK);

            OperationalMetricsSnapshotV2 snapshot = ops.captureMetrics(
                    new OperationalMetricsCollectorV2.PlacementStageCountsV2(1, 0, 0, 0, 1, 2),
                    3L, 128L, 1, "CONSOLE:CONSOLE");
            // The runtime collector emits exactly its fixed RUNTIME_LABELS subset. The additive
            // V2-13-01 duration labels are measurement-only and must not appear in runtime
            // snapshots, so this asserts against the collector's set rather than the enum length.
            assertEquals(OperationalMetricsCollectorV2.RUNTIME_LABELS.size(), snapshot.samples().size());
            assertEquals(OperationalMetricsCollectorV2.RUNTIME_LABELS,
                    snapshot.samples().stream()
                            .map(OperationalMetricsSnapshotV2.SampleV2::label)
                            .collect(java.util.stream.Collectors.toSet()));
            assertEquals(1L, snapshot.valueOf(OperationalMetricLabelV2.PLACEMENT_STAGE_PLANNED));
            assertEquals(3L, snapshot.valueOf(OperationalMetricLabelV2.SETTLE_TICKS_OBSERVED));
            assertFalse(snapshot.hasPendingCanonicalChecksum());
            new StructuredDataValidator().validate(
                    "operational-metrics-snapshot-v2.schema.json",
                    "metrics",
                    new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(snapshot));

            assertThrows(IllegalArgumentException.class, () -> new OperationalAuditEventV2(
                    1, OperationalAuditEventV2.CONTRACT_VERSION, UUID.randomUUID(),
                    OperationalAuditEventKindV2.FAILURE, "CONSOLE:CONSOLE",
                    "ops", "fail", "denied", null, "", 0L, CLOCK.instant().toString(),
                    "/tmp/secret-path"));
            assertTrue(OperationalRedactionV2.containsSensitive("Authorization: Bearer sk-test"));
        }
    }

    @Test
    void diagnosticsCorrelateFailuresAndRetentionIsActorBound(@TempDir Path root) throws Exception {
        PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(root.resolve("fixture"), false);
        PlacementPlanV2 plan = fixture.plan;
        PlacementJournalV2 journal = fixture.journal;
        AtomicInteger executes = new AtomicInteger();
        AtomicLong freed = new AtomicLong();
        RetentionCleanupPortV2 cleanup = new RetentionCleanupPortV2() {
            @Override
            public PlacementRecoveryCleanupPlanV2 planCleanup(
                    PlacementPlanV2 placementPlan,
                    PlacementJournalV2 currentJournal,
                    CancellationToken cancellation
            ) {
                return PlacementRecoveryCleanupPlanV2.sealed(
                        placementPlan.placementId(),
                        currentJournal.journalChecksum(),
                        List.of(new PlacementRecoveryCleanupPlanV2.SnapshotFileEntryV2("snap-0.bin", 42)),
                        42L);
            }

            @Override
            public long executeCleanup(
                    PlacementRecoveryCleanupPlanV2 cleanupPlan,
                    PlacementPlanV2 placementPlan,
                    PlacementJournalV2 currentJournal,
                    CancellationToken cancellation
            ) {
                executes.incrementAndGet();
                freed.set(cleanupPlan.totalBytes());
                return cleanupPlan.totalBytes();
            }
        };
        OperationalAuditLogV2 audit = new OperationalAuditLogV2(root.resolve("audit"));
        Release2RetentionServiceV2 retention = new Release2RetentionServiceV2(cleanup, audit, CLOCK);
        try (GenerationExecutors executors = GenerationExecutors.create(2, 2, 8)) {
            OperationalOperationsServiceV2 ops = new OperationalOperationsServiceV2(
                    executors, root.resolve("data"),
                    new OperationalMetricsCollectorV2(CLOCK,
                            java.lang.management.ManagementFactory.getMemoryMXBean()),
                    audit,
                    new OperationalDiagnosticsServiceV2(audit, CLOCK),
                    retention,
                    CLOCK);

            UUID correlation = UUID.randomUUID();
            audit.append(new OperationalAuditEventV2(
                    1, OperationalAuditEventV2.CONTRACT_VERSION, correlation,
                    OperationalAuditEventKindV2.FAILURE, "CONSOLE:CONSOLE",
                    "apply", "verify", "mismatch", plan.placementId(), "tile0", 0L,
                    CLOCK.instant().toString(), "verify-mismatch"));
            OperationalDiagnosticsReportV2 report = ops.diagnose(
                    correlation, true, false, "CONSOLE:CONSOLE");
            assertTrue(report.findings().stream().anyMatch(value -> value.contains("verify-mismatch")
                    || value.contains("event-kind-failure")));
            assertTrue(report.openaiKeyPresent());
            assertFalse(report.anthropicKeyPresent());

            Release2RetentionServiceV2.PreparedRetentionV2 prepared = ops.planRetention(
                    plan, journal, PlacementPlanV2.PlacementActorV2.console(), () -> false);
            assertFalse(prepared.plan().executed());
            assertThrows(IllegalArgumentException.class, () -> ops.executeRetention(
                    prepared.plan().planId(), "wrong-token",
                    PlacementPlanV2.PlacementActorV2.console(), plan, journal, () -> false));
            assertThrows(IllegalArgumentException.class, () -> ops.executeRetention(
                    prepared.plan().planId(), prepared.confirmationToken(),
                    PlacementPlanV2.PlacementActorV2.system("OTHER"), plan, journal, () -> false));

            Release2RetentionCleanupPlanV2 executed = ops.executeRetention(
                    prepared.plan().planId(), prepared.confirmationToken(),
                    PlacementPlanV2.PlacementActorV2.console(), plan, journal, () -> false);
            assertTrue(executed.executed());
            assertEquals(1, executes.get());
            assertEquals(42L, freed.get());
            assertTrue(Files.exists(audit.auditFile()));
            assertTrue(audit.recentEvents().stream()
                    .anyMatch(event -> event.eventKind() == OperationalAuditEventKindV2.RETENTION_EXECUTE));
        }
    }

    @Test
    void executorLoadSnapshotIsBoundedAndLocaleInvariant() {
        try (GenerationExecutors executors = GenerationExecutors.create(2, 2, 8)) {
            GenerationExecutors.ExecutorLoadSnapshotV2 load = executors.snapshotLoad();
            assertEquals(8, load.generationQueueCapacity());
            assertEquals(0, load.generationQueueDepth());
            assertTrue(load.ioAvailablePermits() >= 0);
        }
    }

    private static RetentionCleanupPortV2 unsupportedCleanup() {
        return new RetentionCleanupPortV2() {
            @Override
            public PlacementRecoveryCleanupPlanV2 planCleanup(
                    PlacementPlanV2 placementPlan,
                    PlacementJournalV2 journal,
                    CancellationToken cancellation
            ) {
                throw new UnsupportedOperationException("cleanup unsupported in metrics-only path");
            }

            @Override
            public long executeCleanup(
                    PlacementRecoveryCleanupPlanV2 cleanupPlan,
                    PlacementPlanV2 placementPlan,
                    PlacementJournalV2 journal,
                    CancellationToken cancellation
            ) {
                throw new UnsupportedOperationException("cleanup unsupported in metrics-only path");
            }
        };
    }
}
