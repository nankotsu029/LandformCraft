package com.github.nankotsu029.landformcraft.core.v2.job;

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
 * Reservation-bound confirmation tokens for the two-step v2 export (V2-12-09).
 *
 * <p>v1's {@code export plan} → {@code export create} pair made an operator confirm a specific
 * candidate before a Release was written; the v2 path had no such step. A plan here records what was
 * admitted — request digest, intent digest, release id, actor — and issues a single-use token bound
 * to exactly that reservation.</p>
 *
 * <p>Only the hash is retained, so a leaked store cannot yield a usable token, and the comparison is
 * constant-time. A token is rejected once used, once expired, from a different actor, or if the
 * plan's inputs no longer digest to the same value — a request edited between {@code plan} and
 * {@code create} invalidates the reservation instead of silently exporting something else.</p>
 */
public final class ExportPlanStoreV2 {
    public static final Duration DEFAULT_TIME_TO_LIVE = Duration.ofMinutes(10);
    /** Bound on outstanding plans, so unconfirmed plans cannot grow without limit. */
    public static final int MAXIMUM_PLANS = 256;

    private final Map<UUID, Plan> plans = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration timeToLive;

    public ExportPlanStoreV2(Clock clock) {
        this(clock, DEFAULT_TIME_TO_LIVE);
    }

    public ExportPlanStoreV2(Clock clock, Duration timeToLive) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timeToLive = Objects.requireNonNull(timeToLive, "timeToLive");
        if (timeToLive.isNegative() || timeToLive.isZero()) {
            throw new IllegalArgumentException("export plan time-to-live must be positive");
        }
    }

    /**
     * Reserves one export and returns the plan id with its one-time token. The caller has already
     * admitted budget and disk; this records what was admitted so {@link #consume} can prove the
     * confirmation refers to the same reservation.
     */
    public PreparedExportV2 prepare(ExportJobSubmissionV2 submission, String actor, String inputsDigest) {
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(inputsDigest, "inputsDigest");
        purgeExpired();
        if (plans.size() >= MAXIMUM_PLANS) {
            throw new IllegalStateException("too many outstanding v2 export plans; confirm or let them expire");
        }
        UUID planId = UUID.randomUUID();
        String token = UUID.randomUUID().toString();
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(timeToLive);
        plans.put(planId, new Plan(submission, actor, inputsDigest, expiresAt,
                hash(planId, actor, inputsDigest, submission.releaseId(), expiresAt, token)));
        return new PreparedExportV2(planId, token, expiresAt, submission.releaseId());
    }

    /**
     * Validates and consumes one confirmation. The plan is removed first, so a replay cannot win a
     * race against a slow export.
     */
    public ExportJobSubmissionV2 consume(
            UUID planId,
            String token,
            String actor,
            InputsDigest recompute
    ) throws java.io.IOException {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(recompute, "recompute");
        Plan plan = plans.remove(planId);
        if (plan == null) {
            throw new IllegalArgumentException("v2 export plan was not found, already used, or expired");
        }
        if (!clock.instant().isBefore(plan.expiresAt())) {
            throw new IllegalArgumentException("v2 export plan expired; create a new plan");
        }
        if (!plan.actor().equals(actor)) {
            throw new IllegalArgumentException("v2 export plan belongs to a different operator");
        }
        String currentDigest = recompute.of(plan.submission());
        if (!plan.inputsDigest().equals(currentDigest)) {
            throw new IllegalArgumentException(
                    "v2 export inputs changed after the plan was created; create a new plan");
        }
        String expected = hash(planId, actor, plan.inputsDigest(), plan.submission().releaseId(),
                plan.expiresAt(), token);
        if (!MessageDigest.isEqual(
                plan.tokenHash().getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("v2 export confirmation token is not valid for this plan");
        }
        return plan.submission();
    }

    public int outstanding() {
        purgeExpired();
        return plans.size();
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        plans.values().removeIf(plan -> !now.isBefore(plan.expiresAt()));
    }

    private static String hash(
            UUID planId, String actor, String inputsDigest, String releaseId,
            Instant expiresAt, String token
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String value = planId + "\n" + actor + "\n" + inputsDigest + "\n" + releaseId + "\n"
                    + expiresAt + "\n" + token;
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** Recomputes the reservation digest from the stored plan at confirmation time. */
    @FunctionalInterface
    public interface InputsDigest {
        String of(ExportJobSubmissionV2 submission) throws java.io.IOException;
    }

    /** What the operator is shown. The token is returned once and never stored in clear text. */
    public record PreparedExportV2(UUID planId, String confirmationToken, Instant expiresAt, String releaseId) {
    }

    private record Plan(
            ExportJobSubmissionV2 submission,
            String actor,
            String inputsDigest,
            Instant expiresAt,
            String tokenHash
    ) {
    }
}
