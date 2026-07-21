package com.github.nankotsu029.landformcraft.core.v2.placement.snapshot;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.PlacementPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.envelope.PlacementEnvelopeCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FilePlacementSafetyStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementDiskSpaceProbeV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationConfirmCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPhysicsClassV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementSnapshotAllCompilerV2Test {
    private static final String REGENERATE_EXAMPLES_ENV =
            "LANDFORMCRAFT_V21306_REGENERATE_PLACEMENT_EXAMPLES";
    private static final UUID PLACEMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPERATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORLD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PLACEMENT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_OPERATION = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String MANIFEST_CHECKSUM =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final Instant T0 = Instant.parse("2026-07-18T00:00:00Z");
    private static final String FIXED_TOKEN = "11111111-2222-3333-4444-555555555555";
    private static final CancellationToken NEVER_CANCELLED = () -> false;
    private static final Path EXAMPLE =
            Path.of("examples/v2/placement/placement-snapshot-plan-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final PlacementPlanCompilerV2 planCompiler = new PlacementPlanCompilerV2();
    private final PlacementEnvelopeCompilerV2 envelopeCompiler = new PlacementEnvelopeCompilerV2();

    @Test
    void snapshotsAllEnvelopesInCanonicalOrderAndMatchesBundledExample(@TempDir Path directory)
            throws Exception {
        Fixture fixture = confirmedFixture(directory);
        List<PlacementJournalV2> journals = new ArrayList<>();
        PlacementSnapshotAllCompilerV2.PreparedSnapshotAllV2 prepared =
                fixture.compiler.snapshotAll(fixture.request(journals, NEVER_CANCELLED));

        assertEquals(2, journals.size());
        assertEquals(PlacementJournalStateV2.SNAPSHOTTING, journals.get(0).state());
        assertTrue(journals.get(0).tiles().stream()
                .allMatch(tile -> tile.state() == PlacementTileStateV2.PENDING));
        assertEquals(PlacementJournalStateV2.SNAPSHOT_COMPLETE, journals.get(1).state());
        assertTrue(journals.get(1).tiles().stream()
                .allMatch(tile -> tile.state() == PlacementTileStateV2.SNAPSHOTTED));
        assertTrue(journals.get(1).snapshotBytesUsed() > 0L);
        assertTrue(journals.get(1).snapshotBytesUsed() <= journals.get(1).reservedBytes());

        PlacementSnapshotPlanV2 plan = prepared.snapshotPlan();
        assertEquals(List.of("tile-x0-z0", "tile-x1-z0"),
                plan.tiles().stream().map(PlacementSnapshotPlanV2.TileSnapshotV2::tileId).toList());
        plan.requireBindings(fixture.confirmedPlan, fixture.envelopePlan, fixture.reservationPlan);
        assertEquals(0, fixture.gateway.applyCalls.get());

        assertTrue(Files.isDirectory(prepared.publishedDirectory()));
        assertFalse(Files.exists(fixture.stagingDirectory()));
        PlacementSnapshotPlanV2 reloaded = fixture.compiler.loadPublished(
                fixture.confirmedPlan, fixture.envelopePlan, fixture.reservationPlan, NEVER_CANCELLED);
        assertEquals(plan, reloaded);

        Path journalPath = directory.resolve("journal.json");
        codec.writePlacementJournal(journalPath, journals.get(1));
        assertEquals(journals.get(1), codec.readPlacementJournal(journalPath));

        if ("true".equals(System.getenv(REGENERATE_EXAMPLES_ENV))) {
            codec.writePlacementSnapshotPlan(EXAMPLE, plan);
        }
        assertEquals(plan, codec.readPlacementSnapshotPlan(EXAMPLE));
    }

    @Test
    void isDeterministicAcrossThreadsAndRepeatedRuns(@TempDir Path first, @TempDir Path second)
            throws Exception {
        Fixture direct = confirmedFixture(first);
        PlacementSnapshotAllCompilerV2.PreparedSnapshotAllV2 one =
                direct.compiler.snapshotAll(direct.request(new ArrayList<>(), NEVER_CANCELLED));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            PlacementSnapshotAllCompilerV2.PreparedSnapshotAllV2 two = executor.submit(() -> {
                Fixture pooled = confirmedFixture(second);
                return pooled.compiler.snapshotAll(pooled.request(new ArrayList<>(), NEVER_CANCELLED));
            }).get();
            assertEquals(one.snapshotPlan(), two.snapshotPlan());
            assertEquals(one.snapshotCompleteJournal().journalChecksum(),
                    two.snapshotCompleteJournal().journalChecksum());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsStateBindingReservationDiskAndBudgetViolations(@TempDir Path directory)
            throws Exception {
        Fixture fixture = confirmedFixture(directory);

        PlacementSnapshotAllCompilerV2.PreparedSnapshotAllV2 prepared =
                fixture.compiler.snapshotAll(fixture.request(new ArrayList<>(), NEVER_CANCELLED));
        assertEquals(PlacementSnapshotFailureCodeV2.STATE_MISMATCH, failure(() ->
                fixture.compiler.snapshotAll(fixture.requestWithJournal(
                        prepared.snapshotCompleteJournal(), new ArrayList<>(), NEVER_CANCELLED))));

        Fixture foreign = confirmedFixture(
                directory.resolve("foreign"), OTHER_PLACEMENT, OTHER_OPERATION, fixedProbe(TEN_GIB));
        assertEquals(PlacementSnapshotFailureCodeV2.BINDING_MISMATCH, failure(() ->
                fixture.compiler.snapshotAll(fixture.requestWithJournal(
                        foreign.journal, new ArrayList<>(), NEVER_CANCELLED))));

        Fixture released = confirmedFixture(directory.resolve("released"));
        released.store.release(released.confirmedPlan.placementId());
        assertEquals(PlacementSnapshotFailureCodeV2.RESERVATION_MISSING, failure(() ->
                released.compiler.snapshotAll(released.request(new ArrayList<>(), NEVER_CANCELLED))));

        Fixture shortage = confirmedFixture(directory.resolve("shortage"));
        PlacementSnapshotAllCompilerV2 starved = new PlacementSnapshotAllCompilerV2(
                shortage.store, shortage.clock, fixedProbe(1L));
        assertEquals(PlacementSnapshotFailureCodeV2.DISK_SHORTAGE, failure(() ->
                starved.snapshotAll(shortage.request(new ArrayList<>(), NEVER_CANCELLED))));

        Fixture tinyBudget = confirmedFixture(directory.resolve("tiny"));
        assertEquals(PlacementSnapshotFailureCodeV2.SNAPSHOT_BUDGET_EXCEEDED, failure(() ->
                tinyBudget.compiler.snapshotAll(tinyBudget.requestWithBudget(
                        new PlacementSnapshotPlanV2.ResourceBudget(
                                PlacementSnapshotPlanV2.ResourceBudget.VERSION,
                                64, 1L, 4_096, 8_192L, PlacementSnapshotPlanV2.MAX_CANONICAL_BYTES),
                        new ArrayList<>(), NEVER_CANCELLED))));

        Fixture tinyPalette = confirmedFixture(directory.resolve("palette"));
        List<PlacementJournalV2> paletteJournals = new ArrayList<>();
        assertEquals(PlacementSnapshotFailureCodeV2.PALETTE_BUDGET_EXCEEDED, failure(() ->
                tinyPalette.compiler.snapshotAll(tinyPalette.requestWithBudget(
                        new PlacementSnapshotPlanV2.ResourceBudget(
                                PlacementSnapshotPlanV2.ResourceBudget.VERSION,
                                64, 1_000_000_000L, 2, 8_192L,
                                PlacementSnapshotPlanV2.MAX_CANONICAL_BYTES),
                        paletteJournals, NEVER_CANCELLED))));
        assertEquals(1, paletteJournals.size());
        assertFalse(Files.exists(tinyPalette.stagingDirectory()));
        assertEquals(0, tinyPalette.gateway.applyCalls.get());
    }

    @Test
    void cleansStagingOnGatewayFailureWorldDriftAndCancel(@TempDir Path directory) throws Exception {
        Fixture ioFailure = confirmedFixture(directory.resolve("io"));
        ioFailure.gateway.failAtCall.set(3);
        List<PlacementJournalV2> journals = new ArrayList<>();
        assertEquals(PlacementSnapshotFailureCodeV2.SNAPSHOT_IO_FAILURE, failure(() ->
                ioFailure.compiler.snapshotAll(ioFailure.request(journals, NEVER_CANCELLED))));
        assertEquals(1, journals.size());
        assertEquals(PlacementJournalStateV2.SNAPSHOTTING, journals.get(0).state());
        assertFalse(Files.exists(ioFailure.stagingDirectory()));
        assertFalse(Files.exists(ioFailure.publishedDirectory()));
        assertEquals(0, ioFailure.gateway.applyCalls.get());

        Fixture driftUnknown = confirmedFixture(directory.resolve("drift-unknown"));
        driftUnknown.gateway.driftState = "minecraft:granite";
        assertEquals(PlacementSnapshotFailureCodeV2.WORLD_DRIFT, failure(() ->
                driftUnknown.compiler.snapshotAll(driftUnknown.request(new ArrayList<>(), NEVER_CANCELLED))));
        assertFalse(Files.exists(driftUnknown.stagingDirectory()));

        Fixture driftKnown = confirmedFixture(directory.resolve("drift-known"));
        driftKnown.gateway.driftState = "minecraft:stone";
        assertEquals(PlacementSnapshotFailureCodeV2.WORLD_DRIFT, failure(() ->
                driftKnown.compiler.snapshotAll(driftKnown.request(new ArrayList<>(), NEVER_CANCELLED))));
        assertFalse(Files.exists(driftKnown.stagingDirectory()));

        Fixture disorder = confirmedFixture(directory.resolve("disorder"));
        disorder.gateway.reverseOrder.set(true);
        assertEquals(PlacementSnapshotFailureCodeV2.GATEWAY_CONTRACT_VIOLATION, failure(() ->
                disorder.compiler.snapshotAll(disorder.request(new ArrayList<>(), NEVER_CANCELLED))));
        assertFalse(Files.exists(disorder.stagingDirectory()));

        Fixture cancelled = confirmedFixture(directory.resolve("cancel"));
        AtomicBoolean cancelFlag = new AtomicBoolean(true);
        assertEquals(PlacementSnapshotFailureCodeV2.CANCELLED, failure(() ->
                cancelled.compiler.snapshotAll(cancelled.request(new ArrayList<>(), cancelFlag::get))));
        assertFalse(Files.exists(cancelled.stagingDirectory()));
        assertFalse(Files.exists(cancelled.publishedDirectory()));
    }

    @Test
    void restartCleanupAndPublishedTamperDetection(@TempDir Path directory) throws Exception {
        Fixture fixture = confirmedFixture(directory);
        Path staging = fixture.stagingDirectory();
        Files.createDirectories(staging);
        Files.writeString(staging.resolve("tile-x0-z0.lfcsnap"), "partial-from-crash");

        assertEquals(PlacementSnapshotFailureCodeV2.SNAPSHOT_IN_PROGRESS, failure(() ->
                fixture.compiler.snapshotAll(fixture.request(new ArrayList<>(), NEVER_CANCELLED))));
        assertTrue(fixture.compiler.cleanupAbandoned(fixture.confirmedPlan.placementId()));
        assertFalse(Files.exists(staging));
        assertFalse(fixture.compiler.cleanupAbandoned(fixture.confirmedPlan.placementId()));

        PlacementSnapshotAllCompilerV2.PreparedSnapshotAllV2 prepared =
                fixture.compiler.snapshotAll(fixture.request(new ArrayList<>(), NEVER_CANCELLED));
        assertEquals(PlacementSnapshotFailureCodeV2.ALREADY_PUBLISHED, failure(() ->
                fixture.compiler.snapshotAll(fixture.request(new ArrayList<>(), NEVER_CANCELLED))));

        Path tileFile = prepared.publishedDirectory().resolve("tile-x0-z0.lfcsnap");
        byte[] original = Files.readAllBytes(tileFile);
        byte[] tampered = original.clone();
        tampered[tampered.length - 1] ^= 0x01;
        Files.write(tileFile, tampered, StandardOpenOption.TRUNCATE_EXISTING);
        assertEquals(PlacementSnapshotFailureCodeV2.SNAPSHOT_CORRUPT, failure(() ->
                fixture.compiler.loadPublished(
                        fixture.confirmedPlan, fixture.envelopePlan, fixture.reservationPlan,
                        NEVER_CANCELLED)));
        Files.write(tileFile, java.util.Arrays.copyOf(original, original.length - 2),
                StandardOpenOption.TRUNCATE_EXISTING);
        assertEquals(PlacementSnapshotFailureCodeV2.SNAPSHOT_CORRUPT, failure(() ->
                fixture.compiler.loadPublished(
                        fixture.confirmedPlan, fixture.envelopePlan, fixture.reservationPlan,
                        NEVER_CANCELLED)));
        Files.write(tileFile, original, StandardOpenOption.TRUNCATE_EXISTING);

        Path extra = prepared.publishedDirectory().resolve("extra.bin");
        Files.writeString(extra, "unexpected");
        assertEquals(PlacementSnapshotFailureCodeV2.FILE_SET_MISMATCH, failure(() ->
                fixture.compiler.loadPublished(
                        fixture.confirmedPlan, fixture.envelopePlan, fixture.reservationPlan,
                        NEVER_CANCELLED)));
        Files.delete(extra);

        Path index = prepared.publishedDirectory().resolve(PlacementSnapshotPlanV2.INDEX_FILE_NAME);
        String indexJson = Files.readString(index);
        Files.writeString(index, indexJson.replace(
                prepared.snapshotPlan().tiles().getFirst().artifactChecksum(),
                "c".repeat(64)), StandardOpenOption.TRUNCATE_EXISTING);
        assertEquals(PlacementSnapshotFailureCodeV2.SNAPSHOT_CORRUPT, failure(() ->
                fixture.compiler.loadPublished(
                        fixture.confirmedPlan, fixture.envelopePlan, fixture.reservationPlan,
                        NEVER_CANCELLED)));
        Files.writeString(index, indexJson, StandardOpenOption.TRUNCATE_EXISTING);

        Fixture other = confirmedFixture(
                directory.resolve("other"), OTHER_PLACEMENT, OTHER_OPERATION, fixedProbe(TEN_GIB));
        assertEquals(PlacementSnapshotFailureCodeV2.STATE_MISMATCH, failure(() ->
                fixture.compiler.loadPublished(
                        other.confirmedPlan, other.envelopePlan, other.reservationPlan,
                        NEVER_CANCELLED)));
        assertEquals(PlacementSnapshotFailureCodeV2.BINDING_MISMATCH, failure(() ->
                fixture.compiler.loadPublished(
                        fixture.confirmedPlan, other.envelopePlan, other.reservationPlan,
                        NEVER_CANCELLED)));

        assertEquals(prepared.snapshotPlan(), fixture.compiler.loadPublished(
                fixture.confirmedPlan, fixture.envelopePlan, fixture.reservationPlan, NEVER_CANCELLED));
    }

    @Test
    void journalContractRejectsPartialSnapshotStates(@TempDir Path directory) throws Exception {
        Fixture fixture = confirmedFixture(directory);
        PlacementSnapshotAllCompilerV2.PreparedSnapshotAllV2 prepared =
                fixture.compiler.snapshotAll(fixture.request(new ArrayList<>(), NEVER_CANCELLED));
        PlacementJournalV2 complete = prepared.snapshotCompleteJournal();

        List<PlacementJournalV2.PlacementTileEntryV2> partial = new ArrayList<>(complete.tiles());
        partial.set(0, new PlacementJournalV2.PlacementTileEntryV2(
                partial.get(0).tileId(), 0, PlacementTileStateV2.PENDING, "", ""));
        assertThrows(IllegalArgumentException.class, () -> new PlacementJournalV2(
                complete.journalVersion(),
                complete.journalContractVersion(),
                complete.plan(),
                complete.planChecksum(),
                PlacementJournalStateV2.SNAPSHOT_COMPLETE,
                partial,
                complete.reservedBytes(),
                complete.snapshotBytesUsed(),
                complete.updatedAt(),
                complete.message(),
                complete.journalChecksum()));

        assertThrows(IllegalArgumentException.class, () -> new PlacementJournalV2(
                complete.journalVersion(),
                complete.journalContractVersion(),
                complete.plan(),
                complete.planChecksum(),
                PlacementJournalStateV2.SNAPSHOTTING,
                complete.tiles(),
                complete.reservedBytes(),
                complete.snapshotBytesUsed(),
                complete.updatedAt(),
                complete.message(),
                complete.journalChecksum()));

        assertThrows(IllegalArgumentException.class, () -> new PlacementJournalV2(
                complete.journalVersion(),
                complete.journalContractVersion(),
                complete.plan(),
                complete.planChecksum(),
                PlacementJournalStateV2.SNAPSHOT_COMPLETE,
                complete.tiles(),
                complete.reservedBytes(),
                0L,
                complete.updatedAt(),
                complete.message(),
                complete.journalChecksum()));
    }

    @Test
    @Disabled("manual fixture regeneration helper")
    void rewriteSnapshotPlanExample(@TempDir Path directory) throws Exception {
        Fixture fixture = confirmedFixture(directory);
        PlacementSnapshotAllCompilerV2.PreparedSnapshotAllV2 prepared =
                fixture.compiler.snapshotAll(fixture.request(new ArrayList<>(), NEVER_CANCELLED));
        Files.copy(
                prepared.publishedDirectory().resolve(PlacementSnapshotPlanV2.INDEX_FILE_NAME),
                EXAMPLE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static PlacementSnapshotFailureCodeV2 failure(Runnable action) {
        PlacementSnapshotExceptionV2 exception =
                assertThrows(PlacementSnapshotExceptionV2.class, action::run);
        return exception.failureCode();
    }

    private Fixture confirmedFixture(Path directory) throws Exception {
        return confirmedFixture(directory, PLACEMENT_ID, OPERATION_ID, fixedProbe(TEN_GIB));
    }

    private Fixture confirmedFixture(
            Path directory,
            UUID placementId,
            UUID operationId,
            PlacementDiskSpaceProbeV2 probe
    ) throws Exception {
        Files.createDirectories(directory);
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        FilePlacementSafetyStoreV2 store = new FilePlacementSafetyStoreV2(
                directory.resolve("safety.json"),
                directory.resolve("snapshots"),
                clock,
                probe);
        PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 envelope =
                compileEnvelope(sealedPlan(placementId, operationId));
        PlacementReservationConfirmCompilerV2 reservationCompiler =
                new PlacementReservationConfirmCompilerV2(store, clock);
        PlacementReservationConfirmCompilerV2.PreparedReservationV2 prepared =
                reservationCompiler.prepare(
                        new PlacementReservationConfirmCompilerV2.ReservationConfirmRequestV2(
                                envelope.boundPlacementPlan(),
                                envelope.envelopePlan(),
                                reservationBudget(),
                                null,
                                FIXED_TOKEN));
        return new Fixture(
                store,
                clock,
                new PlacementSnapshotAllCompilerV2(store, clock, probe),
                prepared.plan(),
                envelope.envelopePlan(),
                prepared.reservationPlan(),
                prepared.journal(),
                new FakeWorldGatewayV2());
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
                List.of(
                        new PlacementEnvelopeCompilerV2.TileMutationContentV2(
                                "tile-x0-z0",
                                0,
                                new WorldAabbV2(0, 64, 0, 15, 66, 15),
                                List.of(PlacementPhysicsClassV2.SOLID)),
                        new PlacementEnvelopeCompilerV2.TileMutationContentV2(
                                "tile-x1-z0",
                                1,
                                new WorldAabbV2(128, 64, 0, 143, 66, 15),
                                List.of(PlacementPhysicsClassV2.SOLID, PlacementPhysicsClassV2.FLUID)))));
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
                        255, 80, 127),
                new PlacementPlanV2.ReleaseBindingV2(
                        PlacementPlanV2.ReleaseBindingV2.VERSION,
                        2,
                        "releases/azure-coast-r2",
                        MANIFEST_CHECKSUM,
                        PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION),
                List.of(PlacementPlanV2.CAPABILITY_SURFACE_TWO_POINT_FIVE_D),
                TilePlanV2.of(256, 128, ScaleProfileV2.defaults(ScaleClassV2.MEDIUM)))).plan();
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

    private static PlacementSnapshotPlanV2.ResourceBudget snapshotBudget() {
        return new PlacementSnapshotPlanV2.ResourceBudget(
                PlacementSnapshotPlanV2.ResourceBudget.VERSION,
                64,
                1_000_000_000L,
                4_096,
                8_192L,
                PlacementSnapshotPlanV2.MAX_CANONICAL_BYTES);
    }

    private static final long TEN_GIB = 10L * 1024L * 1024L * 1024L;

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

    private record Fixture(
            FilePlacementSafetyStoreV2 store,
            Clock clock,
            PlacementSnapshotAllCompilerV2 compiler,
            PlacementPlanV2 confirmedPlan,
            PlacementEnvelopePlanV2 envelopePlan,
            PlacementReservationPlanV2 reservationPlan,
            PlacementJournalV2 journal,
            FakeWorldGatewayV2 gateway
    ) {
        PlacementSnapshotAllCompilerV2.SnapshotAllRequestV2 request(
                List<PlacementJournalV2> journalSink,
                CancellationToken cancellation
        ) {
            return requestWithJournal(journal, journalSink, cancellation);
        }

        PlacementSnapshotAllCompilerV2.SnapshotAllRequestV2 requestWithJournal(
                PlacementJournalV2 journalOverride,
                List<PlacementJournalV2> journalSink,
                CancellationToken cancellation
        ) {
            return new PlacementSnapshotAllCompilerV2.SnapshotAllRequestV2(
                    confirmedPlan,
                    envelopePlan,
                    reservationPlan,
                    journalOverride,
                    gateway,
                    snapshotBudget(),
                    cancellation,
                    journalSink::add);
        }

        PlacementSnapshotAllCompilerV2.SnapshotAllRequestV2 requestWithBudget(
                PlacementSnapshotPlanV2.ResourceBudget budget,
                List<PlacementJournalV2> journalSink,
                CancellationToken cancellation
        ) {
            return new PlacementSnapshotAllCompilerV2.SnapshotAllRequestV2(
                    confirmedPlan,
                    envelopePlan,
                    reservationPlan,
                    journal,
                    gateway,
                    budget,
                    cancellation,
                    journalSink::add);
        }

        Path stagingDirectory() {
            return store.snapshotsRoot().resolve(
                    PlacementSnapshotAllCompilerV2.STAGING_PREFIX + confirmedPlan.placementId());
        }

        Path publishedDirectory() {
            return store.snapshotsRoot().resolve(confirmedPlan.placementId().toString());
        }
    }

    /**
     * Deterministic offline world. Streams canonical X→Z→Y order, never mutates, and records
     * apply calls so tests can prove snapshot-all keeps the all-before-any-apply invariant.
     */
    private static final class FakeWorldGatewayV2 implements PlacementWorldGatewayV2 {
        private final AtomicInteger applyCalls = new AtomicInteger();
        private final AtomicInteger streamCalls = new AtomicInteger();
        private final AtomicBoolean reverseOrder = new AtomicBoolean();
        private final AtomicInteger failAtCall = new AtomicInteger(-1);
        private final Map<String, String> overrides = new HashMap<>();
        private volatile String driftState;

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            int call = streamCalls.incrementAndGet();
            int failAt = failAtCall.get();
            if (failAt > 0 && call >= failAt) {
                throw new IOException("simulated gateway read failure");
            }
            if (reverseOrder.get()) {
                consumer.accept(region.maxX(), region.maxY(), region.maxZ(), "minecraft:stone");
                return;
            }
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    for (int x = region.minX(); x <= region.maxX(); x++) {
                        consumer.accept(x, y, z, stateAt(x, y, z));
                    }
                }
            }
            if (driftState != null) {
                overrides.put(region.minX() + ":" + region.minY() + ":" + region.minZ(), driftState);
                driftState = null;
            }
        }

        @Override
        public void applyTileBlockStates(UUID worldId, String tileId, WorldAabbV2 mutationRegion) {
            applyCalls.incrementAndGet();
        }

        private String stateAt(int x, int y, int z) {
            String override = overrides.get(x + ":" + y + ":" + z);
            if (override != null) {
                return override;
            }
            return switch (Math.floorMod(x + y + z, 3)) {
                case 0 -> "minecraft:stone";
                case 1 -> "minecraft:dirt";
                default -> "minecraft:water[level=0]";
            };
        }
    }
}
