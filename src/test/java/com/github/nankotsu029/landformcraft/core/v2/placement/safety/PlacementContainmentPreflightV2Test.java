package com.github.nankotsu029.landformcraft.core.v2.placement.safety;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.PlacementPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.envelope.PlacementEnvelopeCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FilePlacementSafetyStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementDiskSpaceProbeV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationConfirmCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementSnapshotAllCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.minecraft.PlacementBlockPhysicsCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.minecraft.PlacementBlockPhysicsKindV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentEvidenceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPhysicsClassV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementContainmentPreflightV2Test {
    private static final String REGENERATE_EXAMPLES_ENV =
            "LANDFORMCRAFT_V21306_REGENERATE_PLACEMENT_EXAMPLES";
    private static final UUID PLACEMENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OPERATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID WORLD_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String MANIFEST_CHECKSUM =
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
    private static final Instant T0 = Instant.parse("2026-07-18T12:00:00Z");
    private static final String FIXED_TOKEN = "99999999-8888-7777-6666-555555555555";
    private static final CancellationToken NEVER = () -> false;
    private static final Path POLICY_EXAMPLE =
            Path.of("examples/v2/placement/placement-containment-policy-v2.json");
    private static final Path EVIDENCE_EXAMPLE =
            Path.of("examples/v2/placement/placement-containment-evidence-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final PlacementPlanCompilerV2 planCompiler = new PlacementPlanCompilerV2();
    private final PlacementEnvelopeCompilerV2 envelopeCompiler = new PlacementEnvelopeCompilerV2();

    @Test
    void catalogClassifiesClosedSetAndRejectsUnknown() {
        assertEquals(PlacementBlockPhysicsKindV2.FLUID,
                PlacementBlockPhysicsCatalogV2.require("minecraft:water[level=0]"));
        assertEquals(PlacementBlockPhysicsKindV2.GRAVITY,
                PlacementBlockPhysicsCatalogV2.require("minecraft:sand"));
        assertEquals(PlacementBlockPhysicsKindV2.UNSUPPORTED,
                PlacementBlockPhysicsCatalogV2.require("minecraft:fire"));
        assertTrue(PlacementBlockPhysicsCatalogV2.find("minecraft:dragon_egg").isEmpty());
        assertEquals(PlacementBlockPhysicsCatalogV2.CATALOG_VERSION,
                PlacementContainmentPolicyV2.CATALOG_VERSION);
    }

    @Test
    void containedSealedWaterAndSolidPassAndMatchBundledExample(@TempDir Path directory) throws Exception {
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.SOLID, PlacementPhysicsClassV2.FLUID),
                new WorldAabbV2(4, 68, 4, 11, 72, 11));
        MapWorldView world = solidFill(fixture.envelopePlan.unionEffectEnvelope());
        // Sealed water pocket fully inside mutation, surrounded by stone.
        for (int y = 69; y <= 71; y++) {
            for (int z = 5; z <= 10; z++) {
                for (int x = 5; x <= 10; x++) {
                    world.put(x, y, z, "minecraft:water[level=0]");
                }
            }
        }
        // Stone shell already present from solidFill.

        PlacementContainmentPreflightV2 preflight = new PlacementContainmentPreflightV2(
                Clock.fixed(T0, ZoneOffset.UTC));
        PlacementContainmentEvidenceV2 evidence = preflight.preflight(fixture.request(world));
        assertEquals(PlacementContainmentEvidenceV2.Verdict.CONTAINED, evidence.verdict());
        evidence.requireBindings(fixture.confirmedPlan, fixture.envelopePlan, fixture.snapshotPlan);
        assertEquals(0, fixture.gateway.applyCalls.get());

        if ("true".equals(System.getenv(REGENERATE_EXAMPLES_ENV))) {
            codec.writePlacementContainmentPolicy(
                    POLICY_EXAMPLE, PlacementContainmentPolicyV2.standard());
            codec.writePlacementContainmentEvidence(EVIDENCE_EXAMPLE, evidence);
        }
        assertEquals(evidence, codec.readPlacementContainmentEvidence(EVIDENCE_EXAMPLE));
        assertEquals(
                PlacementContainmentPolicyV2.standard(),
                codec.readPlacementContainmentPolicy(POLICY_EXAMPLE));
    }

    @Test
    void rejectsUncontainedWaterOpenToEnvelopeExterior(@TempDir Path directory) throws Exception {
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.FLUID),
                new WorldAabbV2(4, 68, 4, 11, 72, 11));
        WorldAabbV2 effect = fixture.envelopePlan.unionEffectEnvelope();
        MapWorldView world = solidFill(effect);
        // Water corridor from mutation to the minX face of the effect envelope.
        for (int x = effect.minX(); x <= 8; x++) {
            world.put(x, 70, 8, "minecraft:water[level=0]");
        }

        PlacementContainmentExceptionV2 failure = assertThrows(
                PlacementContainmentExceptionV2.class,
                () -> new PlacementContainmentPreflightV2().preflight(fixture.request(world)));
        assertEquals(PlacementContainmentFailureCodeV2.UNCONTAINED_FLUID, failure.failureCode());
    }

    @Test
    void rejectsUncontainedLava(@TempDir Path directory) throws Exception {
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.FLUID),
                new WorldAabbV2(4, 68, 4, 11, 72, 11));
        WorldAabbV2 effect = fixture.envelopePlan.unionEffectEnvelope();
        MapWorldView world = solidFill(effect);
        for (int z = effect.minZ(); z <= 8; z++) {
            world.put(8, 70, z, "minecraft:lava[level=0]");
        }

        PlacementContainmentExceptionV2 failure = assertThrows(
                PlacementContainmentExceptionV2.class,
                () -> new PlacementContainmentPreflightV2().preflight(fixture.request(world)));
        assertEquals(PlacementContainmentFailureCodeV2.UNCONTAINED_FLUID, failure.failureCode());
    }

    @Test
    void containedSandWithSupportInsideEnvelope(@TempDir Path directory) throws Exception {
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.GRAVITY),
                new WorldAabbV2(4, 68, 4, 11, 80, 11));
        MapWorldView world = solidFill(fixture.envelopePlan.unionEffectEnvelope());
        world.put(8, 78, 8, "minecraft:sand");
        world.put(8, 77, 8, "minecraft:air");
        world.put(8, 76, 8, "minecraft:air");
        world.put(8, 75, 8, "minecraft:stone");

        PlacementContainmentEvidenceV2 evidence =
                new PlacementContainmentPreflightV2().preflight(fixture.request(world));
        assertEquals(1, evidence.scanStats().gravityBlocks());
        assertEquals(PlacementContainmentEvidenceV2.Verdict.CONTAINED, evidence.verdict());
    }

    @Test
    void rejectsSandWhoseFallPathLeavesEnvelope(@TempDir Path directory) throws Exception {
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.GRAVITY),
                new WorldAabbV2(4, 68, 4, 11, 72, 11));
        WorldAabbV2 effect = fixture.envelopePlan.unionEffectEnvelope();
        MapWorldView world = airFill(effect);
        // Sand inside mutation; air column through the entire downward effect halo so the next
        // fall step would leave the envelope before a solid support appears.
        world.put(8, 68, 8, "minecraft:sand");
        for (int y = effect.minY(); y < 68; y++) {
            world.put(8, y, 8, "minecraft:air");
        }

        PlacementContainmentExceptionV2 failure = assertThrows(
                PlacementContainmentExceptionV2.class,
                () -> new PlacementContainmentPreflightV2().preflight(fixture.request(world)));
        assertEquals(PlacementContainmentFailureCodeV2.UNCONTAINED_GRAVITY, failure.failureCode());
    }

    @Test
    void rejectsSandWithoutSolidSupportWithinFallBudget(@TempDir Path directory) throws Exception {
        // Shrink gravityFallBlocks relative to envelope by using a custom policy that still matches
        // the envelope physics radii — instead place solid outside the fall budget window inside
        // the envelope: sand at y=80, solid at y=80-65 would be outside fall budget of 64.
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.GRAVITY),
                new WorldAabbV2(4, 68, 4, 11, 80, 11));
        WorldAabbV2 effect = fixture.envelopePlan.unionEffectEnvelope();
        MapWorldView world = solidFill(effect);
        world.put(8, 80, 8, "minecraft:sand");
        for (int y = 80 - 64; y < 80; y++) {
            world.put(8, y, 8, "minecraft:air");
        }
        // Solid sits just beyond the 64-block fall budget but still inside the effect envelope.
        world.put(8, 80 - 65, 8, "minecraft:stone");

        PlacementContainmentExceptionV2 failure = assertThrows(
                PlacementContainmentExceptionV2.class,
                () -> new PlacementContainmentPreflightV2().preflight(fixture.request(world)));
        assertEquals(PlacementContainmentFailureCodeV2.NO_GRAVITY_SUPPORT, failure.failureCode());
    }

    @Test
    void containedNeighborSensitiveFence(@TempDir Path directory) throws Exception {
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.NEIGHBOR),
                new WorldAabbV2(4, 68, 4, 11, 72, 11));
        MapWorldView world = solidFill(fixture.envelopePlan.unionEffectEnvelope());
        world.put(8, 70, 8, "minecraft:oak_fence");

        PlacementContainmentEvidenceV2 evidence =
                new PlacementContainmentPreflightV2().preflight(fixture.request(world));
        assertEquals(1, evidence.scanStats().neighborBlocks());
    }

    @Test
    void rejectsUnknownAndUnsupportedStates(@TempDir Path directory) throws Exception {
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.SOLID),
                new WorldAabbV2(4, 68, 4, 11, 72, 11));
        MapWorldView world = solidFill(fixture.envelopePlan.unionEffectEnvelope());
        world.put(8, 70, 8, "minecraft:dragon_egg");
        PlacementContainmentExceptionV2 unknown = assertThrows(
                PlacementContainmentExceptionV2.class,
                () -> new PlacementContainmentPreflightV2().preflight(fixture.request(world)));
        assertEquals(PlacementContainmentFailureCodeV2.UNKNOWN_BLOCK_STATE, unknown.failureCode());

        world.put(8, 70, 8, "minecraft:fire");
        PlacementContainmentExceptionV2 unsupported = assertThrows(
                PlacementContainmentExceptionV2.class,
                () -> new PlacementContainmentPreflightV2().preflight(fixture.request(world)));
        assertEquals(PlacementContainmentFailureCodeV2.UNSUPPORTED_BLOCK_STATE, unsupported.failureCode());
    }

    @Test
    void rejectsPhysicsClassUnderstatement(@TempDir Path directory) throws Exception {
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.SOLID),
                new WorldAabbV2(4, 68, 4, 11, 72, 11));
        MapWorldView world = solidFill(fixture.envelopePlan.unionEffectEnvelope());
        world.put(8, 70, 8, "minecraft:water[level=0]");

        PlacementContainmentExceptionV2 failure = assertThrows(
                PlacementContainmentExceptionV2.class,
                () -> new PlacementContainmentPreflightV2().preflight(fixture.request(world)));
        assertEquals(PlacementContainmentFailureCodeV2.PHYSICS_CLASS_UNDERSTATED, failure.failureCode());
    }

    @Test
    void rejectsEnvelopeGapAndInvalidJournal(@TempDir Path directory) throws Exception {
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.SOLID),
                new WorldAabbV2(4, 68, 4, 11, 72, 11));
        WorldAabbV2 effect = fixture.envelopePlan.unionEffectEnvelope();
        MapWorldView gappy = solidFill(effect);
        gappy.remove(effect.minX(), effect.minY(), effect.minZ());

        PlacementContainmentExceptionV2 gap = assertThrows(
                PlacementContainmentExceptionV2.class,
                () -> new PlacementContainmentPreflightV2().preflight(fixture.request(gappy)));
        assertEquals(PlacementContainmentFailureCodeV2.ENVELOPE_GAP, gap.failureCode());

        PlacementJournalV2 confirmedOnly = fixture.journal;
        // Rebuild a CONFIRMATION_ISSUED journal by lowering state via snapshot fixture's prior journal.
        // Use the reservation journal before snapshot-all from a fresh fixture path:
        SnapshotReadyFixture other = snapshotReady(
                directory.resolve("other"),
                List.of(PlacementPhysicsClassV2.SOLID),
                new WorldAabbV2(4, 68, 4, 11, 72, 11));
        PlacementJournalV2 wrongState = other.preSnapshotJournal;
        assertEquals(PlacementJournalStateV2.CONFIRMATION_ISSUED, wrongState.state());
        PlacementContainmentExceptionV2 journal = assertThrows(
                PlacementContainmentExceptionV2.class,
                () -> new PlacementContainmentPreflightV2().preflight(
                        new PlacementContainmentPreflightV2.ContainmentRequestV2(
                                other.confirmedPlan,
                                other.envelopePlan,
                                other.snapshotPlan,
                                other.reservationPlan,
                                wrongState,
                                PlacementContainmentPolicyV2.standard(),
                                solidFill(other.envelopePlan.unionEffectEnvelope()))));
        assertEquals(PlacementContainmentFailureCodeV2.JOURNAL_STATE_INVALID, journal.failureCode());
        assertEquals(PlacementJournalStateV2.SNAPSHOT_COMPLETE, confirmedOnly.state());
    }

    @Test
    void rejectsUnknownPolicyVersionAndScanBudget(@TempDir Path directory) throws Exception {
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.SOLID),
                new WorldAabbV2(4, 68, 4, 11, 72, 11));
        PlacementContainmentPolicyV2 tiny = new PlacementContainmentPolicyV2(
                PlacementContainmentPolicyV2.POLICY_VERSION,
                PlacementContainmentPolicyV2.CATALOG_VERSION,
                2, 2, 64, 1,
                new PlacementContainmentPolicyV2.ResourceBudget(
                        PlacementContainmentPolicyV2.ResourceBudget.VERSION,
                        1L,
                        4_096,
                        50_000_000,
                        PlacementContainmentPolicyV2.ResourceBudget.MAX_CANONICAL_BYTES));
        PlacementContainmentExceptionV2 budget = assertThrows(
                PlacementContainmentExceptionV2.class,
                () -> new PlacementContainmentPreflightV2().preflight(
                        new PlacementContainmentPreflightV2.ContainmentRequestV2(
                                fixture.confirmedPlan,
                                fixture.envelopePlan,
                                fixture.snapshotPlan,
                                fixture.reservationPlan,
                                fixture.journal,
                                tiny,
                                solidFill(fixture.envelopePlan.unionEffectEnvelope()))));
        assertEquals(PlacementContainmentFailureCodeV2.SCAN_BUDGET_EXCEEDED, budget.failureCode());
    }

    @Test
    void classificationOrderIsLocaleTimezoneAndThreadInvariant(@TempDir Path directory) throws Exception {
        SnapshotReadyFixture fixture = snapshotReady(
                directory,
                List.of(PlacementPhysicsClassV2.SOLID, PlacementPhysicsClassV2.FLUID),
                new WorldAabbV2(4, 68, 4, 11, 72, 11));
        MapWorldView world = solidFill(fixture.envelopePlan.unionEffectEnvelope());
        for (int y = 69; y <= 71; y++) {
            for (int z = 5; z <= 10; z++) {
                for (int x = 5; x <= 10; x++) {
                    world.put(x, y, z, "minecraft:water[level=0]");
                }
            }
        }
        PlacementContainmentPreflightV2 preflight = new PlacementContainmentPreflightV2(
                Clock.fixed(T0, ZoneOffset.UTC));
        PlacementContainmentEvidenceV2 first = preflight.preflight(fixture.request(world));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            PlacementContainmentEvidenceV2 second = preflight.preflight(fixture.request(world));
            assertEquals(first.canonicalChecksum(), second.canonicalChecksum());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> preflight.preflight(fixture.request(world)).canonicalChecksum()));
            }
            for (Future<String> future : futures) {
                assertEquals(first.canonicalChecksum(), future.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private SnapshotReadyFixture snapshotReady(
            Path directory,
            List<PlacementPhysicsClassV2> physicsClasses,
            WorldAabbV2 mutation
    ) throws Exception {
        FilesCreate(directory);
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        FilePlacementSafetyStoreV2 store = new FilePlacementSafetyStoreV2(
                directory.resolve("safety.json"),
                directory.resolve("snapshots"),
                clock,
                fixedProbe());
        PlacementPlanV2 plan = planCompiler.compile(new PlacementPlanCompilerV2.PlacementCompileRequestV2(
                PLACEMENT_ID,
                OPERATION_ID,
                "containment-demo",
                PlacementPlanV2.PlacementActorV2.console(),
                new PlacementPlanV2.PlacementTargetV2(
                        WORLD_ID,
                        "world",
                        PlacementPlanV2.AnchorKind.MINIMUM_CORNER,
                        0, 64, 0,
                        0, 64, 0,
                        127, 96, 127),
                new PlacementPlanV2.ReleaseBindingV2(
                        PlacementPlanV2.ReleaseBindingV2.VERSION,
                        2,
                        "releases/containment-demo",
                        MANIFEST_CHECKSUM,
                        PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION),
                List.of(PlacementPlanV2.CAPABILITY_SURFACE_TWO_POINT_FIVE_D),
                TilePlanV2.of(128, 128, ScaleProfileV2.defaults(ScaleClassV2.SMALL)))).plan();

        PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 envelope = envelopeCompiler.compile(
                new PlacementEnvelopeCompilerV2.EnvelopeCompileRequestV2(
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
                                mutation,
                                physicsClasses))));

        PlacementReservationConfirmCompilerV2 reservationCompiler =
                new PlacementReservationConfirmCompilerV2(store, clock);
        PlacementReservationConfirmCompilerV2.PreparedReservationV2 prepared =
                reservationCompiler.prepare(
                        new PlacementReservationConfirmCompilerV2.ReservationConfirmRequestV2(
                                envelope.boundPlacementPlan(),
                                envelope.envelopePlan(),
                                new PlacementReservationPlanV2.ResourceBudget(
                                        PlacementReservationPlanV2.ResourceBudget.VERSION,
                                        64,
                                        PlacementReservationPlanV2.MAXIMUM_ENTRIES,
                                        1_000_000_000L,
                                        8_192L,
                                        PlacementReservationPlanV2.MAX_CANONICAL_BYTES),
                                null,
                                FIXED_TOKEN));

        FakeWorldGateway gateway = new FakeWorldGateway();
        PlacementSnapshotAllCompilerV2 snapshotCompiler =
                new PlacementSnapshotAllCompilerV2(store, clock, fixedProbe());
        List<PlacementJournalV2> journals = new ArrayList<>();
        PlacementSnapshotAllCompilerV2.PreparedSnapshotAllV2 snap = snapshotCompiler.snapshotAll(
                new PlacementSnapshotAllCompilerV2.SnapshotAllRequestV2(
                        prepared.plan(),
                        envelope.envelopePlan(),
                        prepared.reservationPlan(),
                        prepared.journal(),
                        gateway,
                        new PlacementSnapshotPlanV2.ResourceBudget(
                                PlacementSnapshotPlanV2.ResourceBudget.VERSION,
                                64,
                                1_000_000_000L,
                                4_096,
                                8_192L,
                                PlacementSnapshotPlanV2.MAX_CANONICAL_BYTES),
                        NEVER,
                        journals::add));
        return new SnapshotReadyFixture(
                prepared.plan(),
                envelope.envelopePlan(),
                prepared.reservationPlan(),
                prepared.journal(),
                journals.get(journals.size() - 1),
                snap.snapshotPlan(),
                gateway);
    }

    private static void FilesCreate(Path directory) throws Exception {
        java.nio.file.Files.createDirectories(directory);
    }

    private static MapWorldView solidFill(WorldAabbV2 region) {
        MapWorldView view = new MapWorldView();
        for (int y = region.minY(); y <= region.maxY(); y++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    view.put(x, y, z, "minecraft:stone");
                }
            }
        }
        return view;
    }

    private static MapWorldView airFill(WorldAabbV2 region) {
        MapWorldView view = new MapWorldView();
        for (int y = region.minY(); y <= region.maxY(); y++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    view.put(x, y, z, "minecraft:air");
                }
            }
        }
        return view;
    }

    private static PlacementDiskSpaceProbeV2 fixedProbe() {
        return new PlacementDiskSpaceProbeV2() {
            @Override
            public long usableBytes(Path root) {
                return 10L * 1024L * 1024L * 1024L;
            }

            @Override
            public String fileStoreKey(Path root) {
                return "test|containment";
            }
        };
    }

    private record SnapshotReadyFixture(
            PlacementPlanV2 confirmedPlan,
            PlacementEnvelopePlanV2 envelopePlan,
            PlacementReservationPlanV2 reservationPlan,
            PlacementJournalV2 preSnapshotJournal,
            PlacementJournalV2 journal,
            PlacementSnapshotPlanV2 snapshotPlan,
            FakeWorldGateway gateway
    ) {
        PlacementContainmentPreflightV2.ContainmentRequestV2 request(PlacementContainmentWorldViewV2 world) {
            return new PlacementContainmentPreflightV2.ContainmentRequestV2(
                    confirmedPlan,
                    envelopePlan,
                    snapshotPlan,
                    reservationPlan,
                    journal,
                    PlacementContainmentPolicyV2.standard(),
                    world);
        }
    }

    private static final class MapWorldView implements PlacementContainmentWorldViewV2 {
        private final Map<String, String> blocks = new HashMap<>();

        void put(int x, int y, int z, String state) {
            blocks.put(key(x, y, z), state);
        }

        void remove(int x, int y, int z) {
            blocks.remove(key(x, y, z));
        }

        @Override
        public String blockStateAt(int x, int y, int z) {
            String state = blocks.get(key(x, y, z));
            if (state == null) {
                throw new PlacementContainmentExceptionV2(
                        PlacementContainmentFailureCodeV2.ENVELOPE_GAP,
                        "gap at (" + x + "," + y + "," + z + ")");
            }
            return state;
        }

        private static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }
    }

    private static final class FakeWorldGateway implements PlacementWorldGatewayV2 {
        private final AtomicInteger applyCalls = new AtomicInteger();

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
        ) throws java.io.IOException {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    for (int x = region.minX(); x <= region.maxX(); x++) {
                        consumer.accept(x, y, z, "minecraft:stone");
                    }
                }
            }
        }

        @Override
        public void applyTileBlockStates(UUID worldId, String tileId, WorldAabbV2 mutationRegion) {
            applyCalls.incrementAndGet();
        }
    }
}
