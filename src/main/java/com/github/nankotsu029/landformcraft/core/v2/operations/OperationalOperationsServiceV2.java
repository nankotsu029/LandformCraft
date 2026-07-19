package com.github.nankotsu029.landformcraft.core.v2.operations;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalAuditEventKindV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalAuditEventV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalDiagnosticsReportV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricsSnapshotV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.Release2RetentionCleanupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Application facade for Release 2 operational metrics, diagnostics, audit, and retention
 * (V2-6-13). Discriminator {@link #isRelease2Path()} separates this from v1 cleanup/doctor paths.
 */
public final class OperationalOperationsServiceV2 {
    private final GenerationExecutors executors;
    private final Path dataRoot;
    private final OperationalMetricsCollectorV2 metricsCollector;
    private final OperationalAuditLogV2 auditLog;
    private final OperationalDiagnosticsServiceV2 diagnostics;
    private final Release2RetentionServiceV2 retention;
    private final Clock clock;

    public OperationalOperationsServiceV2(
            GenerationExecutors executors,
            Path dataRoot,
            Release2RetentionServiceV2 retention
    ) {
        this(executors, dataRoot, retention, Clock.systemUTC());
    }

    public OperationalOperationsServiceV2(
            GenerationExecutors executors,
            Path dataRoot,
            Release2RetentionServiceV2 retention,
            Clock clock
    ) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot").toAbsolutePath().normalize();
        this.retention = Objects.requireNonNull(retention, "retention");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metricsCollector = new OperationalMetricsCollectorV2(clock,
                java.lang.management.ManagementFactory.getMemoryMXBean());
        this.auditLog = new OperationalAuditLogV2(this.dataRoot);
        this.diagnostics = new OperationalDiagnosticsServiceV2(auditLog, clock);
    }

    public OperationalOperationsServiceV2(
            GenerationExecutors executors,
            Path dataRoot,
            OperationalMetricsCollectorV2 metricsCollector,
            OperationalAuditLogV2 auditLog,
            OperationalDiagnosticsServiceV2 diagnostics,
            Release2RetentionServiceV2 retention,
            Clock clock
    ) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot").toAbsolutePath().normalize();
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.retention = Objects.requireNonNull(retention, "retention");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean isRelease2Path() {
        return true;
    }

    public OperationalMetricsSnapshotV2 captureMetrics(
            OperationalMetricsCollectorV2.PlacementStageCountsV2 stages,
            long settleTicksObserved,
            long verifyScannedBlocks,
            int runningGenerationJobs,
            String actorCanonical
    ) throws IOException {
        Objects.requireNonNull(stages, "stages");
        Objects.requireNonNull(actorCanonical, "actorCanonical");
        long disk = Files.getFileStore(dataRoot).getUsableSpace();
        OperationalMetricsSnapshotV2 snapshot = metricsCollector.collect(
                executors.snapshotLoad(),
                Math.max(0L, disk),
                stages,
                settleTicksObserved,
                verifyScannedBlocks,
                runningGenerationJobs);
        auditLog.append(new OperationalAuditEventV2(
                OperationalAuditEventV2.SCHEMA_VERSION,
                OperationalAuditEventV2.CONTRACT_VERSION,
                UUID.randomUUID(),
                OperationalAuditEventKindV2.METRICS_CAPTURE,
                actorCanonical,
                "metrics",
                "capture",
                "ok",
                null,
                "",
                0L,
                clock.instant().toString(),
                "metrics-snapshot-sealed"));
        return snapshot;
    }

    public OperationalDiagnosticsReportV2 diagnose(
            UUID correlationId,
            boolean openaiKeyPresent,
            boolean anthropicKeyPresent,
            String actorCanonical
    ) throws IOException {
        OperationalDiagnosticsReportV2 report = diagnostics.diagnose(
                correlationId, openaiKeyPresent, anthropicKeyPresent);
        auditLog.append(new OperationalAuditEventV2(
                OperationalAuditEventV2.SCHEMA_VERSION,
                OperationalAuditEventV2.CONTRACT_VERSION,
                correlationId,
                OperationalAuditEventKindV2.DIAGNOSTICS_LOOKUP,
                actorCanonical,
                "diagnostics",
                "lookup",
                report.findings().isEmpty() ? "empty" : "found",
                report.placementId(),
                "",
                0L,
                clock.instant().toString(),
                "diagnostics-lookup"));
        return report;
    }

    public Release2RetentionServiceV2.PreparedRetentionV2 planRetention(
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            PlacementPlanV2.PlacementActorV2 actor,
            CancellationToken cancellation
    ) throws IOException {
        return retention.plan(placementPlan, journal, actor, cancellation);
    }

    public Release2RetentionCleanupPlanV2 executeRetention(
            UUID planId,
            String confirmationToken,
            PlacementPlanV2.PlacementActorV2 actor,
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            CancellationToken cancellation
    ) throws IOException {
        return retention.execute(planId, confirmationToken, actor, placementPlan, journal, cancellation);
    }

    public OperationalAuditLogV2 auditLog() {
        return auditLog;
    }
}
