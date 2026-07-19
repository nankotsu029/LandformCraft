package com.github.nankotsu029.landformcraft.core.v2.operations;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryCleanupPlanV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryServiceV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalAuditEventKindV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalAuditEventV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.Release2RetentionCleanupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Actor-bound Release 2 snapshot retention cleanup with dry-run / confirm / audit (V2-6-13).
 * Delegates file eligibility and deletion to {@link RetentionCleanupPortV2}; never auto-deletes.
 */
public final class Release2RetentionServiceV2 {
    public static final Duration PLAN_TTL = Duration.ofMinutes(10);

    private final RetentionCleanupPortV2 cleanup;
    private final OperationalAuditLogV2 auditLog;
    private final Clock clock;
    private final Map<UUID, PreparedRetentionV2> prepared = new ConcurrentHashMap<>();

    public Release2RetentionServiceV2(
            PlacementRecoveryServiceV2 recovery,
            OperationalAuditLogV2 auditLog
    ) {
        this(RetentionCleanupPortV2.from(recovery), auditLog, Clock.systemUTC());
    }

    public Release2RetentionServiceV2(
            RetentionCleanupPortV2 cleanup,
            OperationalAuditLogV2 auditLog,
            Clock clock
    ) {
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PreparedRetentionV2 plan(
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            PlacementPlanV2.PlacementActorV2 actor,
            CancellationToken cancellation
    ) throws IOException {
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(cancellation, "cancellation");
        PlacementRecoveryCleanupPlanV2 recoveryPlan =
                cleanup.planCleanup(placementPlan, journal, cancellation);
        Instant now = clock.instant();
        Instant expires = now.plus(PLAN_TTL);
        UUID planId = UUID.randomUUID();
        String token = UUID.randomUUID().toString();
        String hash = confirmationHash(
                planId, actor.canonical(), now, expires, recoveryPlan, token);
        Release2RetentionCleanupPlanV2 sealed = new Release2RetentionCleanupPlanV2(
                Release2RetentionCleanupPlanV2.SCHEMA_VERSION,
                Release2RetentionCleanupPlanV2.CONTRACT_VERSION,
                planId,
                actor.canonical(),
                now.toString(),
                expires.toString(),
                placementPlan.placementId(),
                journal.journalChecksum(),
                recoveryPlan.planChecksum(),
                recoveryPlan.files().size(),
                recoveryPlan.totalBytes(),
                hash,
                false);
        PreparedRetentionV2 preparedPlan = new PreparedRetentionV2(sealed, recoveryPlan, token);
        prepared.put(planId, preparedPlan);
        auditLog.append(new OperationalAuditEventV2(
                OperationalAuditEventV2.SCHEMA_VERSION,
                OperationalAuditEventV2.CONTRACT_VERSION,
                UUID.randomUUID(),
                OperationalAuditEventKindV2.RETENTION_PLAN,
                actor.canonical(),
                "retention",
                "plan",
                "prepared",
                placementPlan.placementId(),
                planId.toString().replace("-", "").substring(0, 16),
                recoveryPlan.totalBytes(),
                now.toString(),
                "dry-run-retention-plan"));
        return preparedPlan;
    }

    public Release2RetentionCleanupPlanV2 execute(
            UUID planId,
            String confirmationToken,
            PlacementPlanV2.PlacementActorV2 actor,
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            CancellationToken cancellation
    ) throws IOException {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(confirmationToken, "confirmationToken");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(cancellation, "cancellation");
        PreparedRetentionV2 current = prepared.get(planId);
        Instant now = clock.instant();
        if (current == null) {
            reject(actor, placementPlan.placementId(), "missing-plan", now);
            throw new IllegalArgumentException("unknown retention cleanup plan");
        }
        Release2RetentionCleanupPlanV2 plan = current.plan();
        if (plan.executed()) {
            reject(actor, placementPlan.placementId(), "already-executed", now);
            throw new IllegalStateException("retention cleanup plan already executed");
        }
        if (!plan.actorCanonical().equals(actor.canonical())) {
            reject(actor, placementPlan.placementId(), "actor-mismatch", now);
            throw new IllegalArgumentException("retention cleanup actor mismatch");
        }
        if (Instant.parse(plan.expiresAt()).isBefore(now)) {
            reject(actor, placementPlan.placementId(), "expired", now);
            throw new IllegalArgumentException("retention cleanup plan expired");
        }
        String expected = confirmationHash(
                plan.planId(),
                plan.actorCanonical(),
                Instant.parse(plan.createdAt()),
                Instant.parse(plan.expiresAt()),
                current.recoveryPlan(),
                confirmationToken);
        if (!expected.equals(plan.confirmationHash())) {
            reject(actor, placementPlan.placementId(), "token-mismatch", now);
            throw new IllegalArgumentException("retention cleanup confirmation mismatch");
        }
        if (!plan.placementId().equals(placementPlan.placementId())
                || !plan.journalChecksum().equals(journal.journalChecksum())
                || !plan.recoveryCleanupPlanChecksum().equals(current.recoveryPlan().planChecksum())) {
            reject(actor, placementPlan.placementId(), "binding-mismatch", now);
            throw new IllegalArgumentException("retention cleanup binding mismatch");
        }
        long freed = cleanup.executeCleanup(
                current.recoveryPlan(), placementPlan, journal, cancellation);
        Release2RetentionCleanupPlanV2 executed = plan.withExecuted(true);
        prepared.put(planId, new PreparedRetentionV2(executed, current.recoveryPlan(), ""));
        auditLog.append(new OperationalAuditEventV2(
                OperationalAuditEventV2.SCHEMA_VERSION,
                OperationalAuditEventV2.CONTRACT_VERSION,
                UUID.randomUUID(),
                OperationalAuditEventKindV2.RETENTION_EXECUTE,
                actor.canonical(),
                "retention",
                "execute",
                "deleted",
                placementPlan.placementId(),
                planId.toString().replace("-", "").substring(0, 16),
                freed,
                now.toString(),
                "confirmed-retention-cleanup"));
        return executed;
    }

    private void reject(
            PlacementPlanV2.PlacementActorV2 actor,
            UUID placementId,
            String decision,
            Instant now
    ) throws IOException {
        auditLog.append(new OperationalAuditEventV2(
                OperationalAuditEventV2.SCHEMA_VERSION,
                OperationalAuditEventV2.CONTRACT_VERSION,
                UUID.randomUUID(),
                OperationalAuditEventKindV2.RETENTION_REJECT,
                actor.canonical(),
                "retention",
                "execute",
                decision,
                placementId,
                "",
                0L,
                now.toString(),
                "retention-rejected"));
    }

    static String confirmationHash(
            UUID planId,
            String actorCanonical,
            Instant createdAt,
            Instant expiresAt,
            PlacementRecoveryCleanupPlanV2 recoveryPlan,
            String token
    ) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        digest.update((planId + "\n" + actorCanonical + "\n" + createdAt + "\n" + expiresAt + "\n"
                + recoveryPlan.planChecksum() + "\n" + recoveryPlan.totalBytes() + "\n"
                + recoveryPlan.files().size() + "\n" + token + "\n")
                .getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    public record PreparedRetentionV2(
            Release2RetentionCleanupPlanV2 plan,
            PlacementRecoveryCleanupPlanV2 recoveryPlan,
            String confirmationToken
    ) {
        public PreparedRetentionV2 {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(recoveryPlan, "recoveryPlan");
            Objects.requireNonNull(confirmationToken, "confirmationToken");
        }
    }
}
