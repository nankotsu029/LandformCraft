package com.github.nankotsu029.landformcraft.model.v2.operations;

/** Closed operational audit event kinds (V2-6-13). */
public enum OperationalAuditEventKindV2 {
    METRICS_CAPTURE,
    DIAGNOSTICS_LOOKUP,
    RETENTION_PLAN,
    RETENTION_EXECUTE,
    RETENTION_REJECT,
    FAILURE
}
