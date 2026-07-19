package com.github.nankotsu029.landformcraft.core.v2.placement.verify;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyLimitsV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyTestFixtureV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyTransactionServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementDesiredBlockV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementJournalStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementVerifyEvidenceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementSettleVerifyServiceV2Test {
    private static final Duration WAIT = Duration.ofSeconds(8);

    @Test
    void happyPathSettlesVerifiesAndSealsTerminalApplied(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                harness.gateway, journals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementSettleVerifyServiceV2.VerifyResultV2 result =
                    await(service.settleAndVerify(harness.request(policy(), PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementJournalStateV2.APPLYING, result.applyCompleteJournal().state());
            assertEquals(PlacementJournalStateV2.APPLIED, result.verifiedJournal().state());
            assertTrue(result.verifiedJournal().tiles().stream()
                    .allMatch(tile -> tile.state() == PlacementTileStateV2.VERIFIED));
            assertEquals(PlacementVerifyEvidenceV2.Verdict.VERIFIED, result.evidence().verdict());
            assertEquals(result.evidence().expectedStreamChecksum(),
                    result.evidence().observedStreamChecksum());
            assertEquals(result.evidence().canonicalChecksum(),
                    new LandformV2DataCodec().placementVerifyEvidenceChecksum(result.evidence()));
            assertEquals(List.of(
                            PlacementJournalStateV2.SETTLING,
                            PlacementJournalStateV2.VERIFYING,
                            PlacementJournalStateV2.APPLIED),
                    journals.states());
            assertTrue(result.settleTicks() >= 2);
            assertTrue(result.scannedBlocks() > 0);
            assertEquals(
                    EnumSet.allOf(PlacementVerifyEvidenceV2.ContinuityRuleV2.class),
                    result.evidence().continuityMetrics().stream()
                            .map(PlacementVerifyEvidenceV2.ContinuityMetricV2::rule)
                            .collect(java.util.stream.Collectors.toCollection(
                                    () -> EnumSet.noneOf(PlacementVerifyEvidenceV2.ContinuityRuleV2.class))));
        }
    }

    @Test
    void tileCheckpointAloneIsNotTerminalSuccess(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        assertEquals(PlacementJournalStateV2.APPLYING, harness.applyCompleteJournal.state());
        assertTrue(harness.applyCompleteJournal.tiles().stream()
                .allMatch(tile -> tile.state() == PlacementTileStateV2.APPLIED));
        assertNotEquals(PlacementJournalStateV2.APPLIED, harness.applyCompleteJournal.state());
    }

    @Test
    void delayedSettleUpdatesThenQuiescence(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        harness.gateway.settleUpdates = tick -> tick < 3 ? 2 : 0;
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                harness.gateway, journals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementSettleVerifyServiceV2.VerifyResultV2 result =
                    await(service.settleAndVerify(harness.request(policy(), PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementJournalStateV2.APPLIED, result.verifiedJournal().state());
            assertTrue(result.settleTicks() >= 5);
            assertEquals(6, result.evidence().settleStats().updatesInsideEnvelope());
        }
    }

    @Test
    void settleTimeoutRequiresRecovery(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        harness.gateway.settleUpdates = tick -> 1;
        PlacementSettleVerifyPolicyV2 shortPolicy = new PlacementSettleVerifyPolicyV2(
                PlacementSettleVerifyPolicyV2.POLICY_VERSION,
                3,
                2,
                30_000L,
                1_024,
                64,
                50_000_000L,
                true,
                true,
                PlacementSettleVerifyPolicyV2.ResourceBudget.standard());
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                harness.gateway, journals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementVerifyExceptionV2 failure =
                    failure(service.settleAndVerify(harness.request(shortPolicy, PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementVerifyFailureCodeV2.SETTLE_TIMEOUT, failure.code());
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
        }
    }

    @Test
    void outOfEnvelopeUpdateRequiresRecovery(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        harness.gateway.outsideUpdatesOnTick = 0;
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                harness.gateway, journals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementVerifyExceptionV2 failure =
                    failure(service.settleAndVerify(harness.request(policy(), PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementVerifyFailureCodeV2.SETTLE_OUT_OF_ENVELOPE_UPDATE, failure.code());
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
        }
    }

    @Test
    void verifyMismatchRequiresRecovery(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        harness.gateway.corruptFirstVerifyBlock = true;
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                harness.gateway, journals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementVerifyExceptionV2 failure =
                    failure(service.settleAndVerify(harness.request(policy(), PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementVerifyFailureCodeV2.VERIFY_MISMATCH, failure.code());
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
        }
    }

    @Test
    void unknownPolicyVersionIsRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new PlacementSettleVerifyPolicyV2(
                        "release-2-placement-settle-verify-policy-v2",
                        40,
                        2,
                        30_000L,
                        1_024,
                        64,
                        50_000_000L,
                        true,
                        true,
                        PlacementSettleVerifyPolicyV2.ResourceBudget.standard()));
        assertTrue(thrown.getMessage().contains("unknown placement settle/verify policy version"));
    }

    @Test
    void verifySliceBudgetExhaustedRequiresRecovery(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        PlacementSettleVerifyPolicyV2 tightSlices = new PlacementSettleVerifyPolicyV2(
                PlacementSettleVerifyPolicyV2.POLICY_VERSION,
                40,
                2,
                30_000L,
                1,
                1,
                50_000_000L,
                true,
                true,
                PlacementSettleVerifyPolicyV2.ResourceBudget.standard());
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                harness.gateway, journals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementVerifyExceptionV2 failure =
                    failure(service.settleAndVerify(harness.request(tightSlices, PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementVerifyFailureCodeV2.VERIFY_SLICE_BUDGET, failure.code());
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
        }
    }

    @Test
    void queueSaturatedRejectsWithoutRecovery(@TempDir Path directory) throws Exception {
        FixtureHarness first = FixtureHarness.create(directory.resolve("first"), false);
        FixtureHarness second = FixtureHarness.create(directory.resolve("second"), true);
        HoldingAllSettleGateway holding = new HoldingAllSettleGateway();
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementSettleVerifyLimitsV2 limits =
                new PlacementSettleVerifyLimitsV2(PlacementSettleVerifyLimitsV2.VERSION, 1, 1);
        PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                holding, journals, PlacementApplyTestFixtureV2.CLOCK, limits);
        try {
            CompletionStage<PlacementSettleVerifyServiceV2.VerifyResultV2> pendingFirst =
                    service.settleAndVerify(first.request(policy(), PlacementApplyTestFixtureV2.NEVER));
            assertTrue(holding.submitted.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));

            CompletionStage<PlacementSettleVerifyServiceV2.VerifyResultV2> pendingSecond =
                    service.settleAndVerify(second.request(policy(), PlacementApplyTestFixtureV2.NEVER));
            assertTrue(holding.secondSubmitted.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));

            PlacementVerifyExceptionV2 saturated = failure(service.settleAndVerify(
                    first.withOperationId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb03"))));
            assertEquals(PlacementVerifyFailureCodeV2.QUEUE_SATURATED, saturated.code());

            service.close();
            holding.completeAll();
            pendingFirst.toCompletableFuture().handle((ok, err) -> null)
                    .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            pendingSecond.toCompletableFuture().handle((ok, err) -> null)
                    .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            service.close();
        }
    }

    @Test
    void cancelDuringSettleRequiresRecovery(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        AtomicBoolean cancelled = new AtomicBoolean();
        harness.gateway.afterSettleTick = () -> cancelled.set(true);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                harness.gateway, journals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementVerifyExceptionV2 failure =
                    failure(service.settleAndVerify(harness.request(policy(), cancelled::get)));
            assertEquals(PlacementVerifyFailureCodeV2.CANCELLED, failure.code());
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
        }
    }

    @Test
    void shutdownDuringSettleRequiresRecovery(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        HoldingSettleGateway holding = new HoldingSettleGateway(harness.gateway);
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                holding, journals, PlacementApplyTestFixtureV2.CLOCK);
        CompletionStage<PlacementSettleVerifyServiceV2.VerifyResultV2> pending =
                service.settleAndVerify(harness.request(policy(), PlacementApplyTestFixtureV2.NEVER));
        assertTrue(holding.firstSubmitted.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        service.close();
        holding.completeFirst();
        PlacementVerifyExceptionV2 failure = failure(pending);
        assertEquals(PlacementVerifyFailureCodeV2.SETTLE_SHUTDOWN, failure.code());
        assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
    }

    @Test
    void twoTileSeamContinuityMetricsPresent(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, true);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                harness.gateway, journals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementSettleVerifyServiceV2.VerifyResultV2 result =
                    await(service.settleAndVerify(harness.request(policy(), PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementJournalStateV2.APPLIED, result.verifiedJournal().state());
            assertEquals(2, result.verifiedJournal().tiles().size());
            assertTrue(result.evidence().scanStats().tileSeamSamples() > 0
                    || result.evidence().continuityMetrics().stream()
                    .anyMatch(metric -> metric.examinedEdges() > 0));
            Set<PlacementVerifyEvidenceV2.ContinuityRuleV2> rules = result.evidence().continuityMetrics()
                    .stream()
                    .map(PlacementVerifyEvidenceV2.ContinuityMetricV2::rule)
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(EnumSet.allOf(PlacementVerifyEvidenceV2.ContinuityRuleV2.class), rules);
        }
    }

    @Test
    void solidAirFluidExpectedOrderMatchesApplyFixture(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        assertEquals(List.of("SOLID/0", "SOLID/10", "AIR_CARVE/0", "AIR_CARVE/10", "FLUID/10"),
                harness.gateway.sliceGroups());
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                harness.gateway, journals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementSettleVerifyServiceV2.VerifyResultV2 result =
                    await(service.settleAndVerify(harness.request(policy(), PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(harness.expectedChecksum, result.evidence().expectedStreamChecksum());
            assertEquals(result.evidence().expectedStreamChecksum(),
                    result.evidence().observedStreamChecksum());
        }
    }

    @Test
    void expectedChecksumIsLocaleTimezoneAndThreadInvariant(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            String checksumA = new PlacementExpectedBlockResolverV2(
                    harness.fixture.plan,
                    harness.fixture.envelope,
                    harness.source,
                    harness.baseline).streamChecksum();
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            String checksumB = new PlacementExpectedBlockResolverV2(
                    harness.fixture.plan,
                    harness.fixture.envelope,
                    harness.source,
                    harness.baseline).streamChecksum();
            assertEquals(harness.expectedChecksum, checksumA);
            assertEquals(harness.expectedChecksum, checksumB);
            List<String> threaded = new CopyOnWriteArrayList<>();
            Thread t1 = new Thread(() -> threaded.add(new PlacementExpectedBlockResolverV2(
                    harness.fixture.plan, harness.fixture.envelope, harness.source, harness.baseline)
                    .streamChecksum()));
            Thread t2 = new Thread(() -> threaded.add(new PlacementExpectedBlockResolverV2(
                    harness.fixture.plan, harness.fixture.envelope, harness.source, harness.baseline)
                    .streamChecksum()));
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            assertEquals(List.of(harness.expectedChecksum, harness.expectedChecksum), threaded);
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void observerTimeoutDoesNotCancelSettleTick(@TempDir Path directory) throws Exception {
        FixtureHarness harness = FixtureHarness.create(directory, false);
        HoldingSettleGateway holding = new HoldingSettleGateway(harness.gateway);
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementSettleVerifyServiceV2 service = new PlacementSettleVerifyServiceV2(
                holding, journals, PlacementApplyTestFixtureV2.CLOCK)) {
            CompletionStage<PlacementSettleVerifyServiceV2.VerifyResultV2> stage =
                    service.settleAndVerify(harness.request(policy(), PlacementApplyTestFixtureV2.NEVER));
            CompletableFuture<PlacementSettleVerifyServiceV2.VerifyResultV2> stable =
                    stage.toCompletableFuture();
            assertTrue(holding.firstSubmitted.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));

            CompletableFuture<PlacementSettleVerifyServiceV2.VerifyResultV2> timed =
                    stage.toCompletableFuture().orTimeout(20, TimeUnit.MILLISECONDS);
            ExecutionException timeout = assertThrows(
                    ExecutionException.class,
                    () -> timed.get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
            assertInstanceOf(TimeoutException.class, timeout.getCause());
            CompletableFuture<PlacementSettleVerifyServiceV2.VerifyResultV2> cancelled =
                    stage.toCompletableFuture();
            assertTrue(cancelled.cancel(true));
            assertFalse(holding.pending.isCancelled());

            holding.completeFirst();
            PlacementSettleVerifyServiceV2.VerifyResultV2 result =
                    stable.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            assertEquals(PlacementJournalStateV2.APPLIED, result.verifiedJournal().state());
            assertFalse(journals.states().contains(PlacementJournalStateV2.RECOVERY_REQUIRED));
        }
    }

    private static PlacementSettleVerifyPolicyV2 policy() {
        return PlacementSettleVerifyPolicyV2.standard();
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static PlacementVerifyExceptionV2 failure(CompletionStage<?> stage) throws Exception {
        ExecutionException execution = assertThrows(
                ExecutionException.class,
                () -> stage.toCompletableFuture().get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        Throwable failure = execution.getCause();
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return assertInstanceOf(PlacementVerifyExceptionV2.class, failure);
    }

    private static final class FixtureHarness {
        final PlacementApplyTestFixtureV2 fixture;
        final PlacementApplyTestFixtureV2.ImmutableSource source;
        final WorldGateway gateway;
        final PlacementJournalV2 applyCompleteJournal;
        final PlacementExpectedBlockResolverV2.SnapshotBaselineV2 baseline;
        final String expectedChecksum;

        private FixtureHarness(
                PlacementApplyTestFixtureV2 fixture,
                PlacementApplyTestFixtureV2.ImmutableSource source,
                WorldGateway gateway,
                PlacementJournalV2 applyCompleteJournal,
                PlacementExpectedBlockResolverV2.SnapshotBaselineV2 baseline,
                String expectedChecksum
        ) {
            this.fixture = fixture;
            this.source = source;
            this.gateway = gateway;
            this.applyCompleteJournal = applyCompleteJournal;
            this.baseline = baseline;
            this.expectedChecksum = expectedChecksum;
        }

        static FixtureHarness create(Path root, boolean twoTiles) throws Exception {
            PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(root, twoTiles);
            PlacementApplyTestFixtureV2.ImmutableSource source = fixture.source(false);
            WorldGateway gateway = new WorldGateway();
            RecordingJournalStore applyJournals = new RecordingJournalStore();
            PlacementApplyTransactionServiceV2.ApplyResultV2 applied;
            try (PlacementApplyTransactionServiceV2 applyService = new PlacementApplyTransactionServiceV2(
                    fixture.strictVerifier(),
                    gateway,
                    applyJournals,
                    PlacementApplyTestFixtureV2.CLOCK,
                    new PlacementApplyLimitsV2(
                            PlacementApplyLimitsV2.VERSION, 2, 2, 32, 32, 1_000_000_000L, 640))) {
                applied = await(applyService.apply(fixture.request(source, PlacementApplyTestFixtureV2.NEVER)));
            }
            PlacementExpectedBlockResolverV2.SnapshotBaselineV2 baseline = (x, y, z) -> "minecraft:stone";
            String expectedChecksum = new PlacementExpectedBlockResolverV2(
                    fixture.plan, fixture.envelope, source, baseline).streamChecksum();
            return new FixtureHarness(
                    fixture,
                    source,
                    gateway,
                    applied.applyCompleteJournal(),
                    baseline,
                    expectedChecksum);
        }

        PlacementSettleVerifyRequestV2 request(
                PlacementSettleVerifyPolicyV2 policy,
                CancellationToken cancellation
        ) {
            return new PlacementSettleVerifyRequestV2(
                    fixture.plan,
                    fixture.envelope,
                    fixture.snapshot,
                    fixture.evidence,
                    applyCompleteJournal,
                    source,
                    baseline,
                    policy,
                    cancellation);
        }

        PlacementSettleVerifyRequestV2 withOperationId(UUID operationId) {
            LandformV2DataCodec codec = new LandformV2DataCodec();
            var plan = fixture.plan;
            var clonedPlan = codec.sealPlacementPlan(new com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2(
                    plan.planVersion(),
                    plan.placementContractVersion(),
                    plan.placementId(),
                    operationId,
                    plan.requestId(),
                    plan.actor(),
                    plan.target(),
                    plan.releaseBinding(),
                    plan.requiredCapabilities(),
                    plan.tileOrder(),
                    plan.envelopeReferences(),
                    plan.reservationConfirmationBinding(),
                    plan.budget(),
                    com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2.UNBOUND_CHECKSUM));
            var journal = applyCompleteJournal;
            var clonedJournal = codec.sealPlacementJournal(new PlacementJournalV2(
                    journal.journalVersion(),
                    journal.journalContractVersion(),
                    clonedPlan,
                    clonedPlan.canonicalChecksum(),
                    journal.state(),
                    journal.tiles(),
                    journal.reservedBytes(),
                    journal.snapshotBytesUsed(),
                    journal.updatedAt(),
                    journal.message(),
                    com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2.UNBOUND_CHECKSUM));
            return new PlacementSettleVerifyRequestV2(
                    clonedPlan,
                    fixture.envelope,
                    fixture.snapshot,
                    fixture.evidence,
                    clonedJournal,
                    source,
                    baseline,
                    policy(),
                    PlacementApplyTestFixtureV2.NEVER);
        }
    }

    private static final class RecordingJournalStore implements PlacementJournalStoreV2 {
        private final List<PlacementJournalV2> saved = new CopyOnWriteArrayList<>();

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

    private static final class WorldGateway implements PlacementWorldGatewayV2 {
        private final Map<String, String> world = new ConcurrentHashMap<>();
        private final List<PlacementApplySliceV2> slices = new CopyOnWriteArrayList<>();
        private volatile IntUnaryOperator settleUpdates = tick -> 0;
        private volatile Integer outsideUpdatesOnTick = null;
        private volatile boolean corruptFirstVerifyBlock;
        private volatile Runnable afterSettleTick = () -> { };
        private final AtomicInteger settleCalls = new AtomicInteger();
        private final AtomicBoolean corrupted = new AtomicBoolean();

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            throw new IOException("not used by settle/verify tests");
        }

        @Override
        public CompletionStage<PlacementApplySliceReceiptV2> applyBlockSlice(PlacementApplySliceV2 slice) {
            slices.add(slice);
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

        List<String> sliceGroups() {
            return slices.stream()
                    .map(slice -> slice.pass() + "/" + slice.overlayOrdinal())
                    .distinct()
                    .toList();
        }

        @Override
        public CompletionStage<PlacementSettleTickReceiptV2> advanceSettleTick(PlacementSettleTickV2 tick) {
            int index = settleCalls.getAndIncrement();
            int inside = settleUpdates.applyAsInt(index);
            int outside = 0;
            List<PlacementSettleTickReceiptV2.OutsideUpdateSampleV2> samples = List.of();
            if (outsideUpdatesOnTick != null && outsideUpdatesOnTick == index) {
                outside = 1;
                samples = List.of(new PlacementSettleTickReceiptV2.OutsideUpdateSampleV2(
                        tick.effectEnvelope().maxX() + 1,
                        tick.effectEnvelope().minY(),
                        tick.effectEnvelope().minZ(),
                        "minecraft:water[level=0]"));
            }
            afterSettleTick.run();
            return CompletableFuture.completedFuture(new PlacementSettleTickReceiptV2(
                            tick.operationId(),
                            tick.tickIndex(),
                            true,
                            true,
                            inside,
                            outside,
                            samples))
                    .minimalCompletionStage();
        }

        @Override
        public CompletionStage<PlacementVerifyReadSliceReceiptV2> readVerifySlice(
                PlacementVerifyReadSliceV2 slice
        ) {
            List<String> states = new ArrayList<>(slice.blockCount());
            for (int i = 0; i < slice.blockCount(); i++) {
                int[] xyz = PlacementSettleVerifyServiceV2.decodeCanonicalIndex(
                        slice.effectEnvelope(), slice.startIndex() + i);
                String state = world.getOrDefault(key(xyz[0], xyz[1], xyz[2]), "minecraft:stone");
                if (corruptFirstVerifyBlock && corrupted.compareAndSet(false, true)) {
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

    private static final class HoldingAllSettleGateway implements PlacementWorldGatewayV2 {
        private final CountDownLatch submitted = new CountDownLatch(1);
        private final CountDownLatch secondSubmitted = new CountDownLatch(1);
        private final List<CompletableFuture<PlacementSettleTickReceiptV2>> pendings =
                new CopyOnWriteArrayList<>();
        private final AtomicInteger settleCalls = new AtomicInteger();

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            throw new IOException("not used");
        }

        @Override
        public CompletionStage<PlacementSettleTickReceiptV2> advanceSettleTick(PlacementSettleTickV2 tick) {
            CompletableFuture<PlacementSettleTickReceiptV2> pending = new CompletableFuture<>();
            int call = settleCalls.getAndIncrement();
            if (call == 0) {
                submitted.countDown();
            } else if (call == 1) {
                secondSubmitted.countDown();
            }
            pendings.add(pending);
            return pending.thenApply(ignored -> new PlacementSettleTickReceiptV2(
                            tick.operationId(),
                            tick.tickIndex(),
                            true,
                            true,
                            0,
                            0,
                            List.of()))
                    .minimalCompletionStage();
        }

        void completeAll() {
            for (CompletableFuture<PlacementSettleTickReceiptV2> pending : List.copyOf(pendings)) {
                pending.complete(null);
            }
        }
    }

    private static final class HoldingSettleGateway implements PlacementWorldGatewayV2 {
        private final WorldGateway delegate;
        private final CountDownLatch firstSubmitted = new CountDownLatch(1);
        private final CompletableFuture<PlacementSettleTickReceiptV2> pending = new CompletableFuture<>();
        private final AtomicInteger settleCalls = new AtomicInteger();
        private volatile PlacementSettleTickV2 first;

        private HoldingSettleGateway(WorldGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            throw new IOException("not used");
        }

        @Override
        public CompletionStage<PlacementApplySliceReceiptV2> applyBlockSlice(PlacementApplySliceV2 slice) {
            return delegate.applyBlockSlice(slice);
        }

        @Override
        public CompletionStage<PlacementSettleTickReceiptV2> advanceSettleTick(PlacementSettleTickV2 tick) {
            if (settleCalls.getAndIncrement() == 0) {
                first = tick;
                firstSubmitted.countDown();
                return pending.minimalCompletionStage();
            }
            return delegate.advanceSettleTick(tick);
        }

        @Override
        public CompletionStage<PlacementVerifyReadSliceReceiptV2> readVerifySlice(
                PlacementVerifyReadSliceV2 slice
        ) {
            return delegate.readVerifySlice(slice);
        }

        void completeFirst() {
            pending.complete(new PlacementSettleTickReceiptV2(
                    first.operationId(),
                    first.tickIndex(),
                    true,
                    true,
                    0,
                    0,
                    List.of()));
        }
    }
}
