package com.github.nankotsu029.landformcraft.core.v2.job;

import com.github.nankotsu029.landformcraft.core.v2.export.ExportBudgetV2;
import com.github.nankotsu029.landformcraft.core.v2.export.SurfaceBaselineV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobKindV2;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-12-09 reservation-bound export confirmation tokens. */
class ExportPlanStoreV2Test {
    private static final String ACTOR = "player:harbourmaster";
    private static final String DIGEST = "d".repeat(64);

    @Test
    void aPreparedPlanIsConsumedExactlyOnce() throws Exception {
        ExportPlanStoreV2 plans = new ExportPlanStoreV2(Clock.systemUTC());
        ExportJobSubmissionV2 submission = submission("release-a");

        ExportPlanStoreV2.PreparedExportV2 prepared = plans.prepare(submission, ACTOR, DIGEST);
        assertEquals("release-a", prepared.releaseId());

        assertEquals(submission,
                plans.consume(prepared.planId(), prepared.confirmationToken(), ACTOR, any -> DIGEST));
        // Replay finds nothing: the plan is removed before the token is even checked.
        assertThrows(IllegalArgumentException.class, () -> plans.consume(
                prepared.planId(), prepared.confirmationToken(), ACTOR, any -> DIGEST));
    }

    @Test
    void aForgedOrSwappedTokenIsRejected() {
        ExportPlanStoreV2 plans = new ExportPlanStoreV2(Clock.systemUTC());
        ExportPlanStoreV2.PreparedExportV2 first = plans.prepare(submission("release-a"), ACTOR, DIGEST);
        ExportPlanStoreV2.PreparedExportV2 second = plans.prepare(submission("release-b"), ACTOR, DIGEST);

        assertThrows(IllegalArgumentException.class, () -> plans.consume(
                first.planId(), UUID.randomUUID().toString(), ACTOR, any -> DIGEST));
        // The other plan's token does not authorise this plan.
        assertThrows(IllegalArgumentException.class, () -> plans.consume(
                second.planId(), first.confirmationToken(), ACTOR, any -> DIGEST));
    }

    @Test
    void aPlanBelongsToTheOperatorWhoCreatedIt() {
        ExportPlanStoreV2 plans = new ExportPlanStoreV2(Clock.systemUTC());
        ExportPlanStoreV2.PreparedExportV2 prepared = plans.prepare(submission("release-a"), ACTOR, DIGEST);

        assertThrows(IllegalArgumentException.class, () -> plans.consume(
                prepared.planId(), prepared.confirmationToken(), "player:someone-else", any -> DIGEST));
    }

    @Test
    void inputsChangedAfterThePlanInvalidateTheReservation() {
        ExportPlanStoreV2 plans = new ExportPlanStoreV2(Clock.systemUTC());
        ExportPlanStoreV2.PreparedExportV2 prepared = plans.prepare(submission("release-a"), ACTOR, DIGEST);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> plans.consume(prepared.planId(), prepared.confirmationToken(), ACTOR,
                        any -> "e".repeat(64)));

        assertTrue(failure.getMessage().contains("inputs changed"), failure.getMessage());
    }

    @Test
    void anExpiredPlanIsRefusedAndPurged() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-21T00:00:00Z"));
        ExportPlanStoreV2 plans = new ExportPlanStoreV2(clock, Duration.ofMinutes(10));
        ExportPlanStoreV2.PreparedExportV2 prepared = plans.prepare(submission("release-a"), ACTOR, DIGEST);
        assertEquals(1, plans.outstanding());

        clock.advance(Duration.ofMinutes(11));

        assertThrows(IllegalArgumentException.class, () -> plans.consume(
                prepared.planId(), prepared.confirmationToken(), ACTOR, any -> DIGEST));
        assertEquals(0, plans.outstanding());
    }

    @Test
    void outstandingPlansAreBounded() {
        ExportPlanStoreV2 plans = new ExportPlanStoreV2(Clock.systemUTC());
        for (int index = 0; index < ExportPlanStoreV2.MAXIMUM_PLANS; index++) {
            plans.prepare(submission("release-a"), ACTOR, DIGEST);
        }

        assertThrows(IllegalStateException.class, () -> plans.prepare(submission("release-a"), ACTOR, DIGEST));
    }

    @Test
    void everyPlanIssuesADistinctTokenAndIdentifier() {
        ExportPlanStoreV2 plans = new ExportPlanStoreV2(Clock.systemUTC());

        ExportPlanStoreV2.PreparedExportV2 first = plans.prepare(submission("release-a"), ACTOR, DIGEST);
        ExportPlanStoreV2.PreparedExportV2 second = plans.prepare(submission("release-a"), ACTOR, DIGEST);

        assertNotEquals(first.planId(), second.planId());
        assertNotEquals(first.confirmationToken(), second.confirmationToken());
    }

    private static ExportJobSubmissionV2 submission(String releaseId) {
        return new ExportJobSubmissionV2(
                "harbor-cove-64",
                releaseId,
                ExportJobKindV2.EXPORT,
                Path.of("examples/v2/diagnostic/harbor-cove-64.request-v2.json"),
                Path.of("examples/v2/diagnostic/harbor-cove-64.terrain-intent-v2.json"),
                Path.of("build", "work", releaseId),
                Path.of("build", "exports"),
                new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                ExportBudgetV2.defaults());
    }

    /** Minimal advanceable clock; the store's expiry is the behaviour under test, not wall time. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
