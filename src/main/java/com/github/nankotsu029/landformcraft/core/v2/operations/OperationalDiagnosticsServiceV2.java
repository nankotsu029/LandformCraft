package com.github.nankotsu029.landformcraft.core.v2.operations;

import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalAuditEventV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalDiagnosticsReportV2;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds redacted admin diagnostics from correlation-indexed audit events (V2-6-13).
 */
public final class OperationalDiagnosticsServiceV2 {
    private final OperationalAuditLogV2 auditLog;
    private final Clock clock;

    public OperationalDiagnosticsServiceV2(OperationalAuditLogV2 auditLog) {
        this(auditLog, Clock.systemUTC());
    }

    public OperationalDiagnosticsServiceV2(OperationalAuditLogV2 auditLog, Clock clock) {
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OperationalDiagnosticsReportV2 diagnose(
            UUID correlationId,
            boolean openaiKeyPresent,
            boolean anthropicKeyPresent
    ) {
        Objects.requireNonNull(correlationId, "correlationId");
        Optional<OperationalAuditEventV2> match = auditLog.findByCorrelationId(correlationId);
        List<String> findings = new ArrayList<>();
        String operation = "unknown";
        String stage = "unknown";
        String suggested = "review-operations-audit";
        UUID placementId = null;
        String journalState = "";
        if (match.isEmpty()) {
            findings.add("no-audit-event-for-correlation-id");
            suggested = "confirm-correlation-id-and-retry";
        } else {
            OperationalAuditEventV2 event = match.get();
            operation = event.operation();
            stage = event.stage();
            placementId = event.placementId();
            findings.add("event-kind-" + event.eventKind().name().toLowerCase(Locale.ROOT));
            findings.add("decision-" + event.decision());
            if (!event.detail().isBlank()) {
                findings.add(OperationalRedactionV2.requireSafeDetail(
                        event.detail(), OperationalDiagnosticsReportV2.MAXIMUM_FINDING_CHARS));
            }
            if (event.bytesAffected() > 0L) {
                findings.add("bytes-affected-present");
            }
            suggested = switch (event.eventKind()) {
                case FAILURE, RETENTION_REJECT -> "inspect-journal-and-retry-safely";
                case RETENTION_EXECUTE -> "verify-disk-and-audit-trail";
                case RETENTION_PLAN -> "confirm-actor-bound-token";
                case DIAGNOSTICS_LOOKUP, METRICS_CAPTURE -> "no-action-required";
            };
        }
        if (openaiKeyPresent) {
            findings.add("openai-credential-configured");
        }
        if (anthropicKeyPresent) {
            findings.add("anthropic-credential-configured");
        }
        return new OperationalDiagnosticsReportV2(
                OperationalDiagnosticsReportV2.SCHEMA_VERSION,
                OperationalDiagnosticsReportV2.CONTRACT_VERSION,
                correlationId,
                operation,
                stage,
                suggested,
                placementId,
                journalState,
                openaiKeyPresent,
                anthropicKeyPresent,
                findings,
                clock.instant().toString());
    }
}
