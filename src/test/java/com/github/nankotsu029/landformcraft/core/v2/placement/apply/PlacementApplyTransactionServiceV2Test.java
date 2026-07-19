package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSafetyStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementApplyTransactionServiceV2Test {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void strictGateAppliesSurfaceCaveSkyWaterfallAndUndergroundFluidInCanonicalPasses(
            @TempDir Path directory
    ) throws Exception {
        PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(directory, false);
        PlacementApplyTestFixtureV2.ImmutableSource source = fixture.source(false);
        RecordingGateway gateway = RecordingGateway.valid();
        RecordingJournalStore journals = new RecordingJournalStore();
        PlacementApplyLimitsV2 limits = limits(2, 2, 2, 32, 1_000_000_000L);

        PlacementApplyTransactionServiceV2.ApplyResultV2 result;
        try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                fixture.strictVerifier(), gateway, journals, PlacementApplyTestFixtureV2.CLOCK, limits)) {
            result = await(service.apply(fixture.request(source, PlacementApplyTestFixtureV2.NEVER)));
        }

        assertEquals(8L, result.appliedMutations());
        assertEquals(List.of(
                "SOLID/0",
                "SOLID/10",
                "AIR_CARVE/0",
                "AIR_CARVE/10",
                "FLUID/10"), gateway.sliceGroups());
        assertTrue(gateway.slices.stream().allMatch(slice -> slice.mutations().size() <= 2));
        assertEquals(expectedWorld(fixture), gateway.world);
        assertEquals(PlacementJournalStateV2.APPLYING, result.startedJournal().state());
        assertEquals(PlacementJournalStateV2.APPLYING, result.applyCompleteJournal().state());
        assertTrue(result.applyCompleteJournal().tiles().stream()
                .allMatch(tile -> tile.state() == PlacementTileStateV2.APPLIED));
        assertEquals(1, result.canonicalTileChecksums().size());
        assertEquals(64, result.canonicalTileChecksums().get(0).length());
        assertEquals(source.openCount.get(), source.closeCount.get());
        assertEquals(6, source.openCount.get());
        assertEquals(List.of(PlacementJournalStateV2.APPLYING, PlacementJournalStateV2.APPLYING),
                journals.states());

        assertEquals(PlacementApplyPassV2.AIR_CARVE,
                PlacementDesiredBlockV2.classify("minecraft:cave_air"));
        assertThrows(IllegalArgumentException.class, () -> new PlacementDesiredBlockV2(
                0, 64, 0, "minecraft:fire", PlacementApplyPassV2.SOLID, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlacementDesiredBlockV2(
                0, 64, 0, "minecraft:not_a_real_block", PlacementApplyPassV2.SOLID, 0, 0));
    }

    @Test
    void illegalStateEnvelopeChecksumSnapshotChecksumAndUnconsumedConfirmationRejectBeforeGateway(
            @TempDir Path directory
    ) throws Exception {
        PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(directory, false);
        PlacementApplyTestFixtureV2.ImmutableSource source = fixture.source(false);

        assertRejectedBeforeGateway(
                fixture,
                new PlacementApplyRequestV2(
                        fixture.plan,
                        fixture.envelope,
                        fixture.reservation,
                        fixture.snapshot,
                        fixture.evidence,
                        fixture.preSnapshotJournal,
                        source,
                        PlacementApplyTestFixtureV2.NEVER),
                PlacementApplyFailureCodeV2.STATE_MISMATCH);

        assertRejectedBeforeGateway(
                fixture,
                new PlacementApplyRequestV2(
                        fixture.plan,
                        fixture.envelope.withChecksums(
                                fixture.envelope.mutationEnvelopeChecksum(), "f".repeat(64)),
                        fixture.reservation,
                        fixture.snapshot,
                        fixture.evidence,
                        fixture.journal,
                        source,
                        PlacementApplyTestFixtureV2.NEVER),
                PlacementApplyFailureCodeV2.BINDING_MISMATCH);

        assertRejectedBeforeGateway(
                fixture,
                new PlacementApplyRequestV2(
                        fixture.plan,
                        fixture.envelope,
                        fixture.reservation,
                        fixture.snapshot.withCanonicalChecksum("e".repeat(64)),
                        fixture.evidence,
                        fixture.journal,
                        source,
                        PlacementApplyTestFixtureV2.NEVER),
                PlacementApplyFailureCodeV2.BINDING_MISMATCH);

        clearConsumedConfirmations(fixture);
        assertRejectedBeforeGateway(
                fixture,
                fixture.request(source, PlacementApplyTestFixtureV2.NEVER),
                PlacementApplyFailureCodeV2.CONFIRMATION_NOT_CONSUMED);
    }

    @Test
    void observerTimeoutAndCancelDoNotCancelSchedulerAcceptedMutation(@TempDir Path directory)
            throws Exception {
        PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(directory, false);
        HoldingFirstGateway gateway = new HoldingFirstGateway();
        RecordingJournalStore journals = new RecordingJournalStore();

        try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                request -> { }, gateway, journals, PlacementApplyTestFixtureV2.CLOCK,
                limits(1, 2, 2, 32, 1_000_000_000L))) {
            CompletionStage<PlacementApplyTransactionServiceV2.ApplyResultV2> stage = service.apply(
                    fixture.request(fixture.source(false), PlacementApplyTestFixtureV2.NEVER));
            CompletableFuture<PlacementApplyTransactionServiceV2.ApplyResultV2> stableObserver =
                    stage.toCompletableFuture();
            assertTrue(gateway.firstSubmitted.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));

            CompletableFuture<PlacementApplyTransactionServiceV2.ApplyResultV2> timedObserver =
                    stage.toCompletableFuture().orTimeout(20, TimeUnit.MILLISECONDS);
            ExecutionException timeout = assertThrows(
                    ExecutionException.class,
                    () -> timedObserver.get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
            assertInstanceOf(TimeoutException.class, timeout.getCause());
            CompletableFuture<PlacementApplyTransactionServiceV2.ApplyResultV2> cancelledObserver =
                    stage.toCompletableFuture();
            assertTrue(cancelledObserver.cancel(true));
            assertFalse(gateway.pending.isCancelled());

            gateway.completeFirst();
            PlacementApplyTransactionServiceV2.ApplyResultV2 result =
                    stableObserver.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            assertEquals(8L, result.appliedMutations());
            assertFalse(journals.states().contains(PlacementJournalStateV2.RECOVERY_REQUIRED));
        }
    }

    @Test
    void cancellationBeforeCommitIsCleanAndAfterAcceptedSliceRequiresRecovery(@TempDir Path directory)
            throws Exception {
        PlacementApplyTestFixtureV2 before = PlacementApplyTestFixtureV2.create(
                directory.resolve("before"), false);
        AtomicBoolean beforeCancelled = new AtomicBoolean(true);
        RecordingGateway beforeGateway = RecordingGateway.valid();
        RecordingJournalStore beforeJournals = new RecordingJournalStore();
        try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                request -> { }, beforeGateway, beforeJournals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementApplyExceptionV2 failure = failure(service.apply(before.request(
                    before.source(false), beforeCancelled::get)));
            assertEquals(PlacementApplyFailureCodeV2.CANCELLED_BEFORE_COMMIT, failure.code());
            assertFalse(failure.worldMutationMayHaveOccurred());
            assertEquals(0, beforeGateway.calls.get());
            assertTrue(beforeJournals.saved.isEmpty());
        }

        PlacementApplyTestFixtureV2 after = PlacementApplyTestFixtureV2.create(
                directory.resolve("after"), false);
        AtomicBoolean afterCancelled = new AtomicBoolean();
        RecordingGateway afterGateway = RecordingGateway.valid();
        afterGateway.afterReceipt = () -> afterCancelled.set(true);
        RecordingJournalStore afterJournals = new RecordingJournalStore();
        try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                request -> { }, afterGateway, afterJournals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementApplyExceptionV2 failure = failure(service.apply(after.request(
                    after.source(false), afterCancelled::get)));
            assertEquals(PlacementApplyFailureCodeV2.CANCELLED_AFTER_COMMIT, failure.code());
            assertTrue(failure.worldMutationMayHaveOccurred());
            assertEquals(1, afterGateway.calls.get());
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, afterJournals.last().state());
        }
    }

    @Test
    void tileFailureInvalidMainThreadReceiptAndShutdownPropagateAndRequireRecovery(
            @TempDir Path directory
    ) throws Exception {
        PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(directory, false);

        RecordingGateway failedGateway = RecordingGateway.failed();
        RecordingJournalStore failedJournals = new RecordingJournalStore();
        try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                request -> { }, failedGateway, failedJournals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementApplyExceptionV2 failure = failure(service.apply(fixture.request(
                    fixture.source(false), PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementApplyFailureCodeV2.GATEWAY_FAILURE, failure.code());
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, failedJournals.last().state());
        }

        RecordingGateway offMainGateway = RecordingGateway.offMainThread();
        RecordingJournalStore offMainJournals = new RecordingJournalStore();
        try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                request -> { }, offMainGateway, offMainJournals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementApplyExceptionV2 failure = failure(service.apply(fixture.request(
                    fixture.source(false), PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementApplyFailureCodeV2.GATEWAY_RECEIPT_INVALID, failure.code());
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, offMainJournals.last().state());
        }

        HoldingFirstGateway shutdownGateway = new HoldingFirstGateway();
        RecordingJournalStore shutdownJournals = new RecordingJournalStore();
        PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                request -> { }, shutdownGateway, shutdownJournals, PlacementApplyTestFixtureV2.CLOCK);
        CompletionStage<PlacementApplyTransactionServiceV2.ApplyResultV2> stage = service.apply(
                fixture.request(fixture.source(false), PlacementApplyTestFixtureV2.NEVER));
        assertTrue(shutdownGateway.firstSubmitted.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        service.close();
        shutdownGateway.completeFirst();
        PlacementApplyExceptionV2 failure = failure(stage);
        assertEquals(PlacementApplyFailureCodeV2.SHUTDOWN_AFTER_COMMIT, failure.code());
        assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, shutdownJournals.last().state());
        await(service.termination());
    }

    @Test
    void crossTileFluidContinuityIsIndependentOfRegistrationThreadLocaleAndTimezone(
            @TempDir Path directory
    ) throws Exception {
        PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(directory, true);
        PlacementApplyTestFixtureV2.ImmutableSource forward = fixture.source(false);
        PlacementApplyTestFixtureV2.ImmutableSource reverse = fixture.source(true);
        assertEquals(List.of("tile-x0-z0", "tile-x1-z0"), forward.registrationOrder());
        assertEquals(List.of("tile-x1-z0", "tile-x0-z0"), reverse.registrationOrder());

        RecordingGateway firstGateway = RecordingGateway.valid();
        PlacementApplyTransactionServiceV2.ApplyResultV2 first;
        try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                fixture.strictVerifier(), firstGateway, journal -> { },
                PlacementApplyTestFixtureV2.CLOCK,
                limits(1, 2, 1, 32, 1_000_000_000L))) {
            first = await(service.apply(fixture.request(forward, PlacementApplyTestFixtureV2.NEVER)));
        }

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        RecordingGateway secondGateway = RecordingGateway.valid();
        PlacementApplyTransactionServiceV2.ApplyResultV2 second;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                    fixture.strictVerifier(), secondGateway, journal -> { },
                    PlacementApplyTestFixtureV2.CLOCK,
                    limits(4, 4, 1, 32, 1_000_000_000L))) {
                second = await(service.apply(fixture.request(reverse, PlacementApplyTestFixtureV2.NEVER)));
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        assertEquals(first.canonicalTileChecksums(), second.canonicalTileChecksums());
        assertEquals(firstGateway.signature(), secondGateway.signature());
        assertEquals(firstGateway.world, secondGateway.world);
        assertEquals("minecraft:water[level=0]", firstGateway.world.get("127,64,0"));
        assertEquals("minecraft:water[level=0]", firstGateway.world.get("128,64,0"));
        assertEquals(List.of(0, 1), firstGateway.slices.stream()
                .map(PlacementApplySliceV2::tileIndex).distinct().toList());
    }

    @Test
    void blockOverlayAndTransactionQueueBudgetsRejectBeforeUnboundedWork(@TempDir Path directory)
            throws Exception {
        PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(directory, false);
        RecordingGateway gateway = RecordingGateway.valid();
        try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                request -> { }, gateway, journal -> { }, PlacementApplyTestFixtureV2.CLOCK,
                limits(1, 1, 1, 1, 7))) {
            PlacementApplyExceptionV2 failure = failure(service.apply(fixture.request(
                    fixture.source(false), PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementApplyFailureCodeV2.RESOURCE_BUDGET_EXCEEDED, failure.code());
            assertEquals(0, gateway.calls.get());
        }

        HoldAllGateway hold = new HoldAllGateway();
        PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                request -> { }, hold, journal -> { }, PlacementApplyTestFixtureV2.CLOCK,
                limits(1, 1, 1, 32, 1_000_000_000L));
        PlacementApplyRequestV2 base = fixture.request(
                fixture.source(false), PlacementApplyTestFixtureV2.NEVER);
        CompletionStage<?> first = service.apply(withOperation(base,
                UUID.fromString("00000000-0000-0000-0000-000000000001")));
        CompletionStage<?> second = service.apply(withOperation(base,
                UUID.fromString("00000000-0000-0000-0000-000000000002")));
        PlacementApplyExceptionV2 saturated = failure(service.apply(withOperation(base,
                UUID.fromString("00000000-0000-0000-0000-000000000003"))));
        assertEquals(PlacementApplyFailureCodeV2.QUEUE_SATURATED, saturated.code());
        service.close();
        hold.completeAll();
        failure(first);
        failure(second);
        await(service.termination());
    }

    @Test
    void fileJournalStorePublishesStrictlyAndRejectsSymlinkTarget(@TempDir Path directory)
            throws Exception {
        PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(
                directory.resolve("fixture"), false);
        Path journalRoot = directory.resolve("journals");
        FilePlacementJournalStoreV2 store = new FilePlacementJournalStoreV2(journalRoot);
        store.save(fixture.journal);
        Path target = journalRoot.resolve(fixture.plan.placementId() + ".json");
        assertEquals(fixture.journal, new LandformV2DataCodec().readPlacementJournal(target));
        try (var files = Files.list(journalRoot)) {
            assertEquals(List.of(target.getFileName().toString()),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }

        Files.delete(target);
        Path outside = directory.resolve("outside.json");
        Files.writeString(outside, "outside");
        Files.createSymbolicLink(target, outside);
        assertThrows(IOException.class, () -> store.save(fixture.journal));
        assertEquals("outside", Files.readString(outside));
    }

    @Test
    void applyingJournalRejectsNonCanonicalAppliedTileOrder(@TempDir Path directory)
            throws Exception {
        PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(directory, true);
        List<PlacementJournalV2.PlacementTileEntryV2> invalid = List.of(
                fixture.journal.tiles().get(0),
                applied(fixture.journal.tiles().get(1)));

        assertThrows(IllegalArgumentException.class, () -> new PlacementJournalV2(
                fixture.journal.journalVersion(),
                fixture.journal.journalContractVersion(),
                fixture.plan,
                fixture.plan.canonicalChecksum(),
                PlacementJournalStateV2.APPLYING,
                invalid,
                fixture.journal.reservedBytes(),
                fixture.journal.snapshotBytesUsed(),
                fixture.journal.updatedAt(),
                "invalid non-canonical apply prefix",
                PlacementPlanV2.UNBOUND_CHECKSUM));
    }

    @Test
    void corruptCoverageUndeclaredPhysicsAndApplyTimeSourceDriftFailClosed(@TempDir Path directory)
            throws Exception {
        PlacementApplyTestFixtureV2 fixture = PlacementApplyTestFixtureV2.create(directory, false);
        for (SourceFault fault : List.of(SourceFault.DROP_FIRST, SourceFault.UNDECLARED_GRAVITY)) {
            RecordingGateway gateway = RecordingGateway.valid();
            RecordingJournalStore journals = new RecordingJournalStore();
            try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                    request -> { }, gateway, journals, PlacementApplyTestFixtureV2.CLOCK)) {
                PlacementApplyExceptionV2 failure = failure(service.apply(fixture.request(
                        new FaultingSource(fixture.source(false), fault),
                        PlacementApplyTestFixtureV2.NEVER)));
                assertEquals(PlacementApplyFailureCodeV2.SOURCE_INVALID, failure.code());
                assertEquals(0, gateway.calls.get());
                assertTrue(journals.saved.isEmpty());
            }
        }

        RecordingGateway gateway = RecordingGateway.valid();
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                request -> { }, gateway, journals, PlacementApplyTestFixtureV2.CLOCK,
                limits(1, 1, 1, 32, 1_000_000_000L))) {
            PlacementApplyExceptionV2 failure = failure(service.apply(fixture.request(
                    new FaultingSource(fixture.source(false), SourceFault.DRIFT_AFTER_PREFLIGHT),
                    PlacementApplyTestFixtureV2.NEVER)));
            assertEquals(PlacementApplyFailureCodeV2.SOURCE_INVALID, failure.code());
            assertTrue(gateway.calls.get() >= 1);
            assertEquals(PlacementJournalStateV2.RECOVERY_REQUIRED, journals.last().state());
        }
    }

    private static void assertRejectedBeforeGateway(
            PlacementApplyTestFixtureV2 fixture,
            PlacementApplyRequestV2 request,
            PlacementApplyFailureCodeV2 expected
    ) throws Exception {
        RecordingGateway gateway = RecordingGateway.valid();
        RecordingJournalStore journals = new RecordingJournalStore();
        try (PlacementApplyTransactionServiceV2 service = new PlacementApplyTransactionServiceV2(
                fixture.strictVerifier(), gateway, journals, PlacementApplyTestFixtureV2.CLOCK)) {
            PlacementApplyExceptionV2 failure = failure(service.apply(request));
            assertEquals(expected, failure.code());
            assertFalse(failure.worldMutationMayHaveOccurred());
            assertEquals(0, gateway.calls.get());
            assertTrue(journals.saved.isEmpty());
        }
    }

    private static void clearConsumedConfirmations(PlacementApplyTestFixtureV2 fixture) throws Exception {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        PlacementSafetyStateV2 state = codec.readPlacementSafetyStateV2(fixture.safetyFile);
        PlacementSafetyStateV2 cleared = codec.sealPlacementSafetyState(new PlacementSafetyStateV2(
                PlacementSafetyStateV2.VERSION,
                PlacementSafetyStateV2.SAFETY_CONTRACT_VERSION,
                state.regionReservations(),
                state.diskReservations(),
                List.of(),
                PlacementPlanV2.UNBOUND_CHECKSUM));
        codec.writePlacementSafetyStateV2(fixture.safetyFile, cleared);
    }

    private static Map<String, String> expectedWorld(PlacementApplyTestFixtureV2 fixture) {
        java.util.LinkedHashMap<String, String> expected = new java.util.LinkedHashMap<>();
        for (List<PlacementDesiredBlockV2> tile : fixture.desiredBlocks.values()) {
            for (PlacementDesiredBlockV2 block : tile) {
                expected.put(key(block), block.blockState());
            }
        }
        return Map.copyOf(expected);
    }

    private static PlacementApplyLimitsV2 limits(
            int threads,
            int queue,
            int slice,
            int overlays,
            long blocks
    ) {
        return new PlacementApplyLimitsV2(
                PlacementApplyLimitsV2.VERSION,
                threads,
                queue,
                slice,
                overlays,
                blocks,
                640);
    }

    private static PlacementApplyRequestV2 withOperation(
            PlacementApplyRequestV2 request,
            UUID operationId
    ) {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        PlacementPlanV2 plan = request.placementPlan();
        PlacementPlanV2 clone = codec.sealPlacementPlan(new PlacementPlanV2(
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
                PlacementPlanV2.UNBOUND_CHECKSUM));
        PlacementJournalV2 journal = request.journal();
        PlacementJournalV2 clonedJournal = codec.sealPlacementJournal(new PlacementJournalV2(
                journal.journalVersion(),
                journal.journalContractVersion(),
                clone,
                clone.canonicalChecksum(),
                journal.state(),
                journal.tiles(),
                journal.reservedBytes(),
                journal.snapshotBytesUsed(),
                journal.updatedAt(),
                journal.message(),
                PlacementPlanV2.UNBOUND_CHECKSUM));
        return new PlacementApplyRequestV2(
                clone,
                request.envelopePlan(),
                request.reservationPlan(),
                request.snapshotPlan(),
                request.containmentEvidence(),
                clonedJournal,
                request.blockSource(),
                request.cancellation());
    }

    private static String key(PlacementDesiredBlockV2 block) {
        return block.x() + "," + block.y() + "," + block.z();
    }

    private static PlacementJournalV2.PlacementTileEntryV2 applied(
            PlacementJournalV2.PlacementTileEntryV2 tile
    ) {
        return new PlacementJournalV2.PlacementTileEntryV2(
                tile.tileId(),
                tile.tileIndex(),
                PlacementTileStateV2.APPLIED,
                tile.snapshotFile(),
                tile.snapshotChecksum());
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static PlacementApplyExceptionV2 failure(CompletionStage<?> stage) throws Exception {
        ExecutionException execution = assertThrows(
                ExecutionException.class,
                () -> stage.toCompletableFuture().get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        Throwable failure = execution.getCause();
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return assertInstanceOf(PlacementApplyExceptionV2.class, failure);
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

    private static final class RecordingGateway implements PlacementWorldGatewayV2 {
        private enum Mode { VALID, FAILED, OFF_MAIN }

        private final Mode mode;
        private final List<PlacementApplySliceV2> slices = new CopyOnWriteArrayList<>();
        private final Map<String, String> world = new java.util.concurrent.ConcurrentHashMap<>();
        private final AtomicInteger calls = new AtomicInteger();
        private volatile Runnable afterReceipt = () -> { };

        private RecordingGateway(Mode mode) {
            this.mode = mode;
        }

        static RecordingGateway valid() {
            return new RecordingGateway(Mode.VALID);
        }

        static RecordingGateway failed() {
            return new RecordingGateway(Mode.FAILED);
        }

        static RecordingGateway offMainThread() {
            return new RecordingGateway(Mode.OFF_MAIN);
        }

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            throw new IOException("not used by apply tests");
        }

        @Override
        public CompletionStage<PlacementApplySliceReceiptV2> applyBlockSlice(
                PlacementApplySliceV2 slice
        ) {
            calls.incrementAndGet();
            slices.add(slice);
            if (mode == Mode.FAILED) {
                return CompletableFuture.<PlacementApplySliceReceiptV2>failedFuture(
                        new IOException("injected tile failure")).minimalCompletionStage();
            }
            for (PlacementDesiredBlockV2 mutation : slice.mutations()) {
                world.put(key(mutation), mutation.blockState());
            }
            afterReceipt.run();
            return CompletableFuture.completedFuture(receipt(slice, mode != Mode.OFF_MAIN))
                    .minimalCompletionStage();
        }

        List<String> sliceGroups() {
            return slices.stream()
                    .map(slice -> slice.pass() + "/" + slice.overlayOrdinal())
                    .distinct()
                    .toList();
        }

        List<String> signature() {
            return slices.stream().map(slice -> slice.tileIndex()
                    + ":" + slice.pass()
                    + ":" + slice.overlayOrdinal()
                    + ":" + slice.mutations().stream().map(PlacementApplyTransactionServiceV2Test::key).toList())
                    .toList();
        }
    }

    private static final class HoldingFirstGateway implements PlacementWorldGatewayV2 {
        private final CountDownLatch firstSubmitted = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();
        private final CompletableFuture<PlacementApplySliceReceiptV2> pending = new CompletableFuture<>();
        private volatile PlacementApplySliceV2 first;

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            throw new IOException("not used by apply tests");
        }

        @Override
        public CompletionStage<PlacementApplySliceReceiptV2> applyBlockSlice(
                PlacementApplySliceV2 slice
        ) {
            if (calls.getAndIncrement() == 0) {
                first = slice;
                firstSubmitted.countDown();
                return pending.minimalCompletionStage();
            }
            return CompletableFuture.completedFuture(receipt(slice, true)).minimalCompletionStage();
        }

        void completeFirst() {
            pending.complete(receipt(first, true));
        }
    }

    private static final class HoldAllGateway implements PlacementWorldGatewayV2 {
        private final List<PendingSlice> pending = new CopyOnWriteArrayList<>();

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            throw new IOException("not used by apply tests");
        }

        @Override
        public CompletionStage<PlacementApplySliceReceiptV2> applyBlockSlice(
                PlacementApplySliceV2 slice
        ) {
            CompletableFuture<PlacementApplySliceReceiptV2> future = new CompletableFuture<>();
            pending.add(new PendingSlice(slice, future));
            return future.minimalCompletionStage();
        }

        void completeAll() {
            for (PendingSlice value : new ArrayList<>(pending)) {
                value.stage.complete(receipt(value.slice, true));
            }
        }

        private record PendingSlice(
                PlacementApplySliceV2 slice,
                CompletableFuture<PlacementApplySliceReceiptV2> stage
        ) { }
    }

    private enum SourceFault {
        DROP_FIRST,
        UNDECLARED_GRAVITY,
        DRIFT_AFTER_PREFLIGHT
    }

    private static final class FaultingSource implements PlacementCanonicalBlockSourceV2 {
        private final PlacementCanonicalBlockSourceV2 delegate;
        private final SourceFault fault;
        private final AtomicInteger opens = new AtomicInteger();

        private FaultingSource(PlacementCanonicalBlockSourceV2 delegate, SourceFault fault) {
            this.delegate = delegate;
            this.fault = fault;
        }

        @Override
        public SourceBindingV2 binding() {
            return delegate.binding();
        }

        @Override
        public BlockCursorV2 openTile(
                PlacementPlanV2 plan,
                PlacementPlanV2.TileRefV2 tile,
                WorldAabbV2 mutationRegion
        ) throws IOException {
            BlockCursorV2 cursor = delegate.openTile(plan, tile, mutationRegion);
            int open = opens.getAndIncrement();
            return new BlockCursorV2() {
                private boolean first = true;

                @Override
                public PlacementDesiredBlockV2 next() throws IOException {
                    PlacementDesiredBlockV2 block = cursor.next();
                    if (!first || block == null) {
                        return block;
                    }
                    first = false;
                    if (fault == SourceFault.DROP_FIRST) {
                        return cursor.next();
                    }
                    if (fault == SourceFault.UNDECLARED_GRAVITY
                            || fault == SourceFault.DRIFT_AFTER_PREFLIGHT && open > 0) {
                        String state = fault == SourceFault.UNDECLARED_GRAVITY
                                ? "minecraft:sand"
                                : "minecraft:deepslate";
                        return new PlacementDesiredBlockV2(
                                block.x(),
                                block.y(),
                                block.z(),
                                state,
                                PlacementDesiredBlockV2.classify(state),
                                block.overlayOrdinal(),
                                block.ownerTileIndex());
                    }
                    return block;
                }

                @Override
                public void close() throws IOException {
                    cursor.close();
                }
            };
        }
    }

    private static PlacementApplySliceReceiptV2 receipt(
            PlacementApplySliceV2 slice,
            boolean mainThread
    ) {
        return new PlacementApplySliceReceiptV2(
                slice.operationId(),
                slice.tileId(),
                slice.sliceSequence(),
                slice.mutations().size(),
                true,
                mainThread,
                true);
    }
}
