package com.github.nankotsu029.landformcraft.core.v2.placement.reservation;

import com.github.nankotsu029.landformcraft.core.v2.placement.PlacementPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.envelope.PlacementEnvelopeCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPhysicsClassV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementReservationConfirmCompilerV2Test {
    private static final UUID PLACEMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPERATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORLD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PLACEMENT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_OPERATION = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String MANIFEST_CHECKSUM =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final Instant T0 = Instant.parse("2026-07-18T00:00:00Z");
    private static final String FIXED_TOKEN = "11111111-2222-3333-4444-555555555555";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final PlacementPlanCompilerV2 planCompiler = new PlacementPlanCompilerV2();
    private final PlacementEnvelopeCompilerV2 envelopeCompiler = new PlacementEnvelopeCompilerV2();

    @Test
    void reservesAllRegionsIssuesConfirmationAndMatchesBundledExample(@TempDir Path directory)
            throws Exception {
        MutableClock clock = new MutableClock(T0);
        FilePlacementSafetyStoreV2 store = new FilePlacementSafetyStoreV2(
                directory.resolve("safety.json"),
                directory.resolve("snapshots"),
                clock,
                fixedProbe(10L * 1024L * 1024L * 1024L));
        PlacementReservationConfirmCompilerV2 compiler =
                new PlacementReservationConfirmCompilerV2(store, clock);

        PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 envelope = compileEnvelope(sealedPlan());
        PlacementReservationConfirmCompilerV2.PreparedReservationV2 prepared = compiler.prepare(
                new PlacementReservationConfirmCompilerV2.ReservationConfirmRequestV2(
                        envelope.boundPlacementPlan(),
                        envelope.envelopePlan(),
                        reservationBudget(),
                        null,
                        FIXED_TOKEN));

        assertEquals(PlacementJournalStateV2.CONFIRMATION_ISSUED, prepared.journal().state());
        assertTrue(prepared.plan().reservationConfirmationBinding().confirmationIssued());
        assertEquals(1, store.read().regionReservations().size());
        assertEquals(FIXED_TOKEN, prepared.plaintextToken());

        Path reservationExample = Path.of("examples/v2/placement/placement-reservation-plan-v2.json");
        Path safetyExample = Path.of("examples/v2/placement/placement-safety-state-v2.json");
        assertEquals(prepared.reservationPlan(), codec.readPlacementReservationPlan(reservationExample));
        assertEquals(store.read(), codec.readPlacementSafetyStateV2(safetyExample));

        Path journalPath = directory.resolve("journal.json");
        codec.writePlacementJournal(journalPath, prepared.journal());
        assertEquals(prepared.journal(), codec.readPlacementJournal(journalPath));

        compiler.verifyAndConsume(
                prepared.plan(),
                envelope.envelopePlan(),
                prepared.reservationPlan(),
                prepared.plan().actor(),
                FIXED_TOKEN);
        assertTrue(store.isConfirmationConsumed(
                prepared.plan().reservationConfirmationBinding().confirmationHash()));

        PlacementReservationExceptionV2 replay = assertThrows(
                PlacementReservationExceptionV2.class,
                () -> compiler.verifyAndConsume(
                        prepared.plan(),
                        envelope.envelopePlan(),
                        prepared.reservationPlan(),
                        prepared.plan().actor(),
                        FIXED_TOKEN));
        assertEquals(PlacementReservationFailureCodeV2.CONFIRMATION_REPLAY, replay.failureCode());
    }

    @Test
    void rejectsOverlapDiskShortageExpiryActorChecksumMismatchAndRollsBackPartial(
            @TempDir Path directory
    ) throws Exception {
        MutableClock clock = new MutableClock(T0);
        AtomicInteger usable = new AtomicInteger(Integer.MAX_VALUE);
        FilePlacementSafetyStoreV2 store = new FilePlacementSafetyStoreV2(
                directory.resolve("safety.json"),
                directory.resolve("snapshots"),
                clock,
                new PlacementDiskSpaceProbeV2() {
                    @Override
                    public long usableBytes(Path root) {
                        return usable.get();
                    }

                    @Override
                    public String fileStoreKey(Path root) {
                        return "test|fs";
                    }
                });
        PlacementReservationConfirmCompilerV2 compiler =
                new PlacementReservationConfirmCompilerV2(store, clock);

        PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 first = compileEnvelope(sealedPlan());
        compiler.prepare(PlacementReservationConfirmCompilerV2.ReservationConfirmRequestV2.of(
                first.boundPlacementPlan(), first.envelopePlan(), reservationBudget()));
        assertEquals(1, store.read().regionReservations().size());

        PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 overlap = compileEnvelope(sealedPlan(
                OTHER_PLACEMENT, OTHER_OPERATION));
        PlacementReservationExceptionV2 overlapFailure = assertThrows(
                PlacementReservationExceptionV2.class,
                () -> compiler.prepare(PlacementReservationConfirmCompilerV2.ReservationConfirmRequestV2.of(
                        overlap.boundPlacementPlan(), overlap.envelopePlan(), reservationBudget())));
        assertEquals(PlacementReservationFailureCodeV2.REGION_OVERLAP, overlapFailure.failureCode());
        assertEquals(1, store.read().regionReservations().size());

        store.release(PLACEMENT_ID);
        usable.set(1);
        PlacementReservationExceptionV2 disk = assertThrows(
                PlacementReservationExceptionV2.class,
                () -> compiler.prepare(PlacementReservationConfirmCompilerV2.ReservationConfirmRequestV2.of(
                        first.boundPlacementPlan(), first.envelopePlan(), reservationBudget())));
        assertEquals(PlacementReservationFailureCodeV2.DISK_SHORTAGE, disk.failureCode());
        assertTrue(store.read().regionReservations().isEmpty());

        usable.set(Integer.MAX_VALUE);
        PlacementReservationConfirmCompilerV2.PreparedReservationV2 prepared = compiler.prepare(
                new PlacementReservationConfirmCompilerV2.ReservationConfirmRequestV2(
                        first.boundPlacementPlan(),
                        first.envelopePlan(),
                        reservationBudget(),
                        null,
                        FIXED_TOKEN));
        clock.set(T0.plus(PlacementConfirmationBinderV2.CONFIRMATION_TTL).plusSeconds(1));
        PlacementReservationExceptionV2 expired = assertThrows(
                PlacementReservationExceptionV2.class,
                () -> compiler.verifyAndConsume(
                        prepared.plan(),
                        first.envelopePlan(),
                        prepared.reservationPlan(),
                        prepared.plan().actor(),
                        FIXED_TOKEN));
        assertEquals(PlacementReservationFailureCodeV2.CONFIRMATION_EXPIRED, expired.failureCode());

        PlacementReservationExceptionV2 actor = assertThrows(
                PlacementReservationExceptionV2.class,
                () -> new PlacementConfirmationBinderV2().verify(
                        prepared.plan(),
                        first.envelopePlan(),
                        prepared.reservationPlan(),
                        prepared.plan().reservationConfirmationBinding().confirmationAction(),
                        PlacementPlanV2.PlacementActorV2.system("OTHER"),
                        T0,
                        FIXED_TOKEN));
        assertEquals(PlacementReservationFailureCodeV2.ACTOR_MISMATCH, actor.failureCode());

        PlacementReservationPlanV2 tampered = prepared.reservationPlan().withCanonicalChecksum(
                PlacementPlanV2.UNBOUND_CHECKSUM);
        assertThrows(IllegalArgumentException.class, () -> new PlacementConfirmationBinderV2().verify(
                prepared.plan(),
                first.envelopePlan(),
                tampered,
                prepared.plan().reservationConfirmationBinding().confirmationAction(),
                prepared.plan().actor(),
                T0,
                FIXED_TOKEN));

        PlacementReservationExceptionV2 badToken = assertThrows(
                PlacementReservationExceptionV2.class,
                () -> new PlacementConfirmationBinderV2().verify(
                        prepared.plan(),
                        first.envelopePlan(),
                        prepared.reservationPlan(),
                        prepared.plan().reservationConfirmationBinding().confirmationAction(),
                        prepared.plan().actor(),
                        T0,
                        "00000000-0000-0000-0000-000000000000"));
        assertEquals(PlacementReservationFailureCodeV2.CONFIRMATION_INVALID, badToken.failureCode());
    }

    @Test
    void raceAndRestartRebuildKeepCanonicalOrder(@TempDir Path directory) throws Exception {
        MutableClock clock = new MutableClock(T0);
        FilePlacementSafetyStoreV2 store = new FilePlacementSafetyStoreV2(
                directory.resolve("safety.json"),
                directory.resolve("snapshots"),
                clock,
                fixedProbe(10L * 1024L * 1024L * 1024L));
        PlacementReservationConfirmCompilerV2 compiler =
                new PlacementReservationConfirmCompilerV2(store, clock);

        PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 envelope = compileEnvelope(sealedPlan());
        PlacementReservationConfirmCompilerV2.PreparedReservationV2 prepared = compiler.prepare(
                PlacementReservationConfirmCompilerV2.ReservationConfirmRequestV2.of(
                        envelope.boundPlacementPlan(), envelope.envelopePlan(), reservationBudget()));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            AtomicInteger overlaps = new AtomicInteger();
            List<Callable<Void>> tasks = List.of(
                    overlapTask(compiler, overlaps),
                    overlapTask(compiler, overlaps),
                    overlapTask(compiler, overlaps),
                    overlapTask(compiler, overlaps));
            for (Future<Void> future : executor.invokeAll(tasks)) {
                future.get();
            }
            assertEquals(4, overlaps.get());
            assertEquals(1, store.read().regionReservations().size());
        } finally {
            executor.shutdownNow();
        }

        FilePlacementSafetyStoreV2 rebuilt = new FilePlacementSafetyStoreV2(
                directory.resolve("safety-rebuilt.json"),
                directory.resolve("snapshots"),
                clock,
                fixedProbe(10L * 1024L * 1024L * 1024L));
        rebuilt.rebuild(List.of(new FilePlacementSafetyStoreV2.RebuildEntryV2(
                prepared.journal(), envelope.envelopePlan())));
        assertEquals(1, rebuilt.read().regionReservations().size());
        assertEquals(prepared.plan().placementId(),
                rebuilt.read().regionReservations().getFirst().placementId());
        // V2-11-02: the rebuilt lease must cover the effect envelope, not the plan target box.
        // The effect envelope is the mutation box expanded by the containment radii, so a
        // target-based rebuild would leave the halo unreserved after a restart.
        List<WorldAabbV2> rebuiltAabbs = rebuilt.read().regionReservations().getFirst().regions();
        List<WorldAabbV2> effectAabbs = envelope.envelopePlan().tiles().stream()
                .map(PlacementEnvelopePlanV2.TileEnvelopeV2::effectAabb)
                .toList();
        assertEquals(effectAabbs, rebuiltAabbs);
        assertEquals(
                store.read().regionReservations().getFirst().regions(),
                rebuiltAabbs,
                "restart rebuild must reproduce the reserve-time effect-envelope leases");
        WorldAabbV2 target = new WorldAabbV2(
                prepared.plan().target().minimumX(), prepared.plan().target().minimumY(),
                prepared.plan().target().minimumZ(), prepared.plan().target().maximumX(),
                prepared.plan().target().maximumY(), prepared.plan().target().maximumZ());
        assertNotEquals(List.of(target), rebuiltAabbs);
        assertTrue(
                rebuilt.read().diskReservations().getFirst().reservedBytes()
                        >= envelope.envelopePlan().diskEstimate().totalBytes(),
                "rebuilt disk lease must cover the sealed envelope disk estimate");

        Path safetyPath = directory.resolve("safety-roundtrip.json");
        codec.writePlacementSafetyStateV2(safetyPath, store.read());
        assertEquals(store.read(), codec.readPlacementSafetyStateV2(safetyPath));
        assertEquals(codec.canonicalPlacementSafetyState(store.read()), Files.readString(safetyPath));
    }

    private Callable<Void> overlapTask(
            PlacementReservationConfirmCompilerV2 compiler,
            AtomicInteger overlaps
    ) {
        return () -> {
            try {
                PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 envelope = compileEnvelope(sealedPlan(
                        OTHER_PLACEMENT, OTHER_OPERATION));
                compiler.prepare(PlacementReservationConfirmCompilerV2.ReservationConfirmRequestV2.of(
                        envelope.boundPlacementPlan(), envelope.envelopePlan(), reservationBudget()));
            } catch (PlacementReservationExceptionV2 exception) {
                if (exception.failureCode() == PlacementReservationFailureCodeV2.REGION_OVERLAP) {
                    overlaps.incrementAndGet();
                } else {
                    throw exception;
                }
            }
            return null;
        };
    }

    private PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 compileEnvelope(PlacementPlanV2 plan) {
        return envelopeCompiler.compile(new PlacementEnvelopeCompilerV2.EnvelopeCompileRequestV2(
                plan,
                new WorldAabbV2(-512, -64, -512, 512, 320, 512),
                PlacementEnvelopePlanV2.PhysicsPolicy.standard(),
                new PlacementEnvelopePlanV2.ResourceBudget(
                        PlacementEnvelopePlanV2.ResourceBudget.VERSION,
                        64,
                        50_000_000L,
                        1_000_000_000L,
                        8_192L,
                        PlacementEnvelopePlanV2.MAX_CANONICAL_BYTES),
                List.of(new PlacementEnvelopeCompilerV2.TileMutationContentV2(
                        "tile-x0-z0",
                        0,
                        new WorldAabbV2(0, 64, 0, 63, 70, 63),
                        List.of(PlacementPhysicsClassV2.SOLID)))));
    }

    private PlacementPlanV2 sealedPlan() {
        return sealedPlan(PLACEMENT_ID, OPERATION_ID);
    }

    private PlacementPlanV2 sealedPlan(UUID placementId, UUID operationId) {
        return planCompiler.compile(new PlacementPlanCompilerV2.PlacementCompileRequestV2(
                placementId,
                operationId,
                "azure-coast-demo",
                PlacementPlanV2.PlacementActorV2.console(),
                new PlacementPlanV2.PlacementTargetV2(
                        WORLD_ID,
                        "world",
                        PlacementPlanV2.AnchorKind.MINIMUM_CORNER,
                        0, 64, 0,
                        0, 64, 0,
                        63, 80, 63),
                new PlacementPlanV2.ReleaseBindingV2(
                        PlacementPlanV2.ReleaseBindingV2.VERSION,
                        2,
                        "releases/azure-coast-r2",
                        MANIFEST_CHECKSUM,
                        PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION),
                List.of(PlacementPlanV2.CAPABILITY_SURFACE_TWO_POINT_FIVE_D),
                TilePlanV2.of(64, 64, ScaleProfileV2.defaults(ScaleClassV2.MEDIUM)))).plan();
    }

    private static PlacementReservationPlanV2.ResourceBudget reservationBudget() {
        return new PlacementReservationPlanV2.ResourceBudget(
                PlacementReservationPlanV2.ResourceBudget.VERSION,
                64,
                PlacementReservationPlanV2.MAXIMUM_ENTRIES,
                1_000_000_000L,
                8_192L,
                PlacementReservationPlanV2.MAX_CANONICAL_BYTES);
    }

    private static PlacementDiskSpaceProbeV2 fixedProbe(long usableBytes) {
        return new PlacementDiskSpaceProbeV2() {
            @Override
            public long usableBytes(Path root) {
                return usableBytes;
            }

            @Override
            public String fileStoreKey(Path root) {
                return "test|fs";
            }
        };
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
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
            return instant;
        }
    }
}
