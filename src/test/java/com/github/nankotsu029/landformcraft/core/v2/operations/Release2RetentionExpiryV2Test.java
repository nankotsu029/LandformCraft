package com.github.nankotsu029.landformcraft.core.v2.operations;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyTestFixtureV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryCleanupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.Release2RetentionCleanupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-12-10 retention snapshot cleanup: the backend the Paper {@code v2 retention} verb drives.
 * Actor binding and wrong-token rejection are covered by {@code OperationalOperationsV2Test}; this
 * pins the confirmation-token expiry the Gate calls out and the fact that cleanup deletes only what
 * the recovery planner scoped.
 */
class Release2RetentionExpiryV2Test {

    @Test
    void aConfirmationTokenExpiresAfterThePlanTimeToLive(@TempDir Path root) throws Exception {
        PlacementApplyTestFixtureV2 fixture =
                PlacementApplyTestFixtureV2.create(root.resolve("fixture"), false);
        PlacementPlanV2 plan = fixture.plan;
        PlacementJournalV2 journal = fixture.journal;
        AtomicInteger executes = new AtomicInteger();
        MutableClock clock = new MutableClock(Instant.parse("2026-07-21T00:00:00Z"));
        OperationalAuditLogV2 audit = new OperationalAuditLogV2(root.resolve("audit"));
        Release2RetentionServiceV2 retention = new Release2RetentionServiceV2(
                windowScopedPort(executes), audit, clock);

        Release2RetentionServiceV2.PreparedRetentionV2 prepared = retention.plan(
                plan, journal, PlacementPlanV2.PlacementActorV2.console(), () -> false);

        // Advance past the plan time-to-live; the confirmation is no longer honoured and nothing is
        // deleted.
        clock.advance(Release2RetentionServiceV2.PLAN_TTL.plusMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> retention.execute(
                prepared.plan().planId(), prepared.confirmationToken(),
                PlacementPlanV2.PlacementActorV2.console(), plan, journal, () -> false));
        assertEquals(0, executes.get(), "an expired plan must not delete any snapshot state");
    }

    @Test
    void cleanupDeletesOnlyWhatTheRecoveryPlannerScoped(@TempDir Path root) throws Exception {
        PlacementApplyTestFixtureV2 fixture =
                PlacementApplyTestFixtureV2.create(root.resolve("fixture"), false);
        PlacementPlanV2 plan = fixture.plan;
        PlacementJournalV2 journal = fixture.journal;
        AtomicInteger executes = new AtomicInteger();
        Clock clock = Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC);
        OperationalAuditLogV2 audit = new OperationalAuditLogV2(root.resolve("audit"));
        Release2RetentionServiceV2 retention = new Release2RetentionServiceV2(
                windowScopedPort(executes), audit, clock);

        Release2RetentionServiceV2.PreparedRetentionV2 prepared = retention.plan(
                plan, journal, PlacementPlanV2.PlacementActorV2.console(), () -> false);
        // The plan surfaces the recovery-scoped byte total; nothing outside it can be deleted.
        assertEquals(42L, prepared.recoveryPlan().totalBytes());

        Release2RetentionCleanupPlanV2 executed = retention.execute(
                prepared.plan().planId(), prepared.confirmationToken(),
                PlacementPlanV2.PlacementActorV2.console(), plan, journal, () -> false);

        assertTrue(executed.executed());
        assertEquals(1, executes.get());
    }

    private static RetentionCleanupPortV2 windowScopedPort(AtomicInteger executes) {
        return new RetentionCleanupPortV2() {
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
                return cleanupPlan.totalBytes();
            }
        };
    }

    /** Advanceable clock; the retention time-to-live is the behaviour under test, not wall time. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
