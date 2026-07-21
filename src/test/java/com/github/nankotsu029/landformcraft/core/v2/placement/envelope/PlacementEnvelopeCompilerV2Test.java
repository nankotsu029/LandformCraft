package com.github.nankotsu029.landformcraft.core.v2.placement.envelope;

import com.github.nankotsu029.landformcraft.core.v2.placement.PlacementPlanCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPhysicsClassV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementEnvelopeCompilerV2Test {
    private static final String REGENERATE_EXAMPLES_ENV =
            "LANDFORMCRAFT_V21306_REGENERATE_PLACEMENT_EXAMPLES";
    private static final UUID PLACEMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPERATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORLD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String MANIFEST_CHECKSUM =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final PlacementPlanCompilerV2 planCompiler = new PlacementPlanCompilerV2();
    private final PlacementEnvelopeCompilerV2 envelopeCompiler = new PlacementEnvelopeCompilerV2();

    @Test
    void bundledExampleMatchesCompilerContract() throws IOException {
        PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 expected = compileSolidFixture();
        Path example = Path.of("examples/v2/placement/placement-envelope-plan-v2.json");
        if ("true".equals(System.getenv(REGENERATE_EXAMPLES_ENV))) {
            codec.writePlacementEnvelopePlan(example, expected.envelopePlan());
        }
        assertEquals(expected.envelopePlan(), codec.readPlacementEnvelopePlan(
                example));
    }

    @Test
    void solidAirFluidGravityBoundaryFixturesExpandConservatively(@TempDir Path directory)
            throws IOException {
        PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 solidCompiled = compileSolidFixture();
        PlacementEnvelopePlanV2 solid = solidCompiled.envelopePlan();
        assertEquals(solid.unionMutationEnvelope(), solid.unionEffectEnvelope());
        assertTrue(solidCompiled.boundPlacementPlan().envelopeReferences().bound());
        assertEquals(solid.mutationEnvelopeChecksum(),
                solidCompiled.boundPlacementPlan().envelopeReferences().mutationEnvelopePlanChecksum());
        assertEquals(solid.canonicalChecksum(),
                solidCompiled.boundPlacementPlan().envelopeReferences().effectEnvelopePlanChecksum());

        PlacementEnvelopePlanV2 fluid = compile(List.of(PlacementPhysicsClassV2.FLUID)).envelopePlan();
        assertEquals(solid.unionMutationEnvelope().expand(2, 2, 2), fluid.unionEffectEnvelope());

        PlacementEnvelopePlanV2 gravity = compile(List.of(PlacementPhysicsClassV2.GRAVITY)).envelopePlan();
        assertEquals(solid.unionMutationEnvelope().expand(0, 0, 64), gravity.unionEffectEnvelope());

        PlacementEnvelopePlanV2 neighbor = compile(List.of(PlacementPhysicsClassV2.NEIGHBOR)).envelopePlan();
        assertEquals(solid.unionMutationEnvelope().expand(1, 1, 1), neighbor.unionEffectEnvelope());

        PlacementEnvelopePlanV2 air = compile(List.of(PlacementPhysicsClassV2.AIR)).envelopePlan();
        assertEquals(solid.unionMutationEnvelope(), air.unionEffectEnvelope());

        Path path = directory.resolve("envelope.json");
        codec.writePlacementEnvelopePlan(path, solid);
        assertEquals(solid, codec.readPlacementEnvelopePlan(path));
        assertEquals(codec.canonicalPlacementEnvelopePlan(solid), Files.readString(path));
    }

    @Test
    void independentOracleDetectsUnderApproximation() {
        PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 compiled = compileSolidFixture();
        PlacementEnvelopePlanV2 oracle = envelopeCompiler.oracle(request(
                List.of(PlacementPhysicsClassV2.SOLID), sealedPlan()));
        compiled.envelopePlan().requireContainsOracle(oracle);

        long volume = 32L * 7L * 32L;
        PlacementEnvelopePlanV2 shrunk = new PlacementEnvelopePlanV2(
                compiled.envelopePlan().planVersion(),
                compiled.envelopePlan().envelopeContractVersion(),
                compiled.envelopePlan().placementPlanBinding(),
                compiled.envelopePlan().physicsPolicy(),
                compiled.envelopePlan().allowedWorldBounds(),
                List.of(new PlacementEnvelopePlanV2.TileEnvelopeV2(
                        compiled.envelopePlan().tiles().getFirst().tileId(),
                        0,
                        List.of(PlacementPhysicsClassV2.SOLID),
                        new WorldAabbV2(0, 64, 0, 31, 70, 31),
                        new WorldAabbV2(0, 64, 0, 31, 70, 31))),
                new WorldAabbV2(0, 64, 0, 31, 70, 31),
                new WorldAabbV2(0, 64, 0, 31, 70, 31),
                new PlacementEnvelopePlanV2.DiskEstimate(
                        volume, volume, volume * 16L, 4_096L, volume * 16L + 4_096L),
                compiled.envelopePlan().budget(),
                PlacementPlanV2.UNBOUND_CHECKSUM,
                PlacementPlanV2.UNBOUND_CHECKSUM);
        assertThrows(IllegalArgumentException.class, () -> shrunk.requireContainsOracle(oracle));
    }

    @Test
    void rejectsWorldBoundsOverflowEmptyPhysicsTileMismatchAndBudget() {
        PlacementPlanV2 plan = sealedPlan();
        WorldAabbV2 tightBounds = new WorldAabbV2(0, 64, 0, 63, 70, 63);
        PlacementEnvelopeExceptionV2 world = assertThrows(
                PlacementEnvelopeExceptionV2.class,
                () -> envelopeCompiler.compile(new PlacementEnvelopeCompilerV2.EnvelopeCompileRequestV2(
                        plan,
                        tightBounds,
                        PlacementEnvelopePlanV2.PhysicsPolicy.standard(),
                        generousBudget(),
                        List.of(new PlacementEnvelopeCompilerV2.TileMutationContentV2(
                                "tile-x0-z0",
                                0,
                                new WorldAabbV2(0, 64, 0, 63, 70, 63),
                                List.of(PlacementPhysicsClassV2.FLUID))))));
        assertEquals(PlacementEnvelopeFailureCodeV2.WORLD_BOUNDS_OVERFLOW, world.failureCode());

        assertThrows(PlacementEnvelopeExceptionV2.class, () -> envelopeCompiler.compile(
                new PlacementEnvelopeCompilerV2.EnvelopeCompileRequestV2(
                        plan,
                        generousWorldBounds(),
                        PlacementEnvelopePlanV2.PhysicsPolicy.standard(),
                        generousBudget(),
                        List.of(new PlacementEnvelopeCompilerV2.TileMutationContentV2(
                                "tile-x0-z0",
                                0,
                                new WorldAabbV2(0, 64, 0, 63, 70, 63),
                                List.of())))));

        assertThrows(PlacementEnvelopeExceptionV2.class, () -> envelopeCompiler.compile(
                new PlacementEnvelopeCompilerV2.EnvelopeCompileRequestV2(
                        plan,
                        generousWorldBounds(),
                        PlacementEnvelopePlanV2.PhysicsPolicy.standard(),
                        generousBudget(),
                        List.of(new PlacementEnvelopeCompilerV2.TileMutationContentV2(
                                "tile-x1-z0",
                                0,
                                new WorldAabbV2(0, 64, 0, 63, 70, 63),
                                List.of(PlacementPhysicsClassV2.SOLID))))));

        PlacementEnvelopeExceptionV2 volume = assertThrows(
                PlacementEnvelopeExceptionV2.class,
                () -> envelopeCompiler.compile(new PlacementEnvelopeCompilerV2.EnvelopeCompileRequestV2(
                        plan,
                        generousWorldBounds(),
                        PlacementEnvelopePlanV2.PhysicsPolicy.standard(),
                        new PlacementEnvelopePlanV2.ResourceBudget(
                                PlacementEnvelopePlanV2.ResourceBudget.VERSION,
                                16,
                                100L,
                                1_000_000_000L,
                                4_096L,
                                PlacementEnvelopePlanV2.MAX_CANONICAL_BYTES),
                        List.of(new PlacementEnvelopeCompilerV2.TileMutationContentV2(
                                "tile-x0-z0",
                                0,
                                new WorldAabbV2(0, 64, 0, 63, 70, 63),
                                List.of(PlacementPhysicsClassV2.SOLID))))));
        assertEquals(PlacementEnvelopeFailureCodeV2.EFFECT_VOLUME_EXCEEDED, volume.failureCode());
    }

    @Test
    void rejectsFutureVersionTamperedChecksumAndIsDeterministicAcrossLocaleTimezoneThreads()
            throws Exception {
        PlacementEnvelopePlanV2 plan = compileSolidFixture().envelopePlan();
        String canonical = codec.canonicalPlacementEnvelopePlan(plan);
        assertThrows(StructuredDataValidationException.class, () -> codec.readPlacementEnvelopePlan(
                canonical.replace("\"planVersion\":1", "\"planVersion\":2"), "future"));
        assertThrows(IOException.class, () -> codec.readPlacementEnvelopePlan(
                canonical.replace(plan.canonicalChecksum(), PlacementPlanV2.UNBOUND_CHECKSUM),
                "tampered"));

        assertEquals(plan.canonicalChecksum(), compileSolidFixture().envelopePlan().canonicalChecksum());

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals(plan.canonicalChecksum(), compileSolidFixture().envelopePlan().canonicalChecksum());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tasks = List.of(
                    () -> compileSolidFixture().envelopePlan().canonicalChecksum(),
                    () -> compileSolidFixture().envelopePlan().canonicalChecksum(),
                    () -> compileSolidFixture().envelopePlan().canonicalChecksum(),
                    () -> compileSolidFixture().envelopePlan().canonicalChecksum());
            for (Future<String> future : executor.invokeAll(tasks)) {
                assertEquals(plan.canonicalChecksum(), future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 compileSolidFixture() {
        return compile(List.of(PlacementPhysicsClassV2.SOLID));
    }

    private PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 compile(
            List<PlacementPhysicsClassV2> physics
    ) {
        return envelopeCompiler.compile(request(physics, sealedPlan()));
    }

    private PlacementEnvelopeCompilerV2.EnvelopeCompileRequestV2 request(
            List<PlacementPhysicsClassV2> physics,
            PlacementPlanV2 plan
    ) {
        return new PlacementEnvelopeCompilerV2.EnvelopeCompileRequestV2(
                plan,
                generousWorldBounds(),
                PlacementEnvelopePlanV2.PhysicsPolicy.standard(),
                generousBudget(),
                List.of(new PlacementEnvelopeCompilerV2.TileMutationContentV2(
                        "tile-x0-z0",
                        0,
                        new WorldAabbV2(0, 64, 0, 63, 70, 63),
                        physics)));
    }

    private PlacementPlanV2 sealedPlan() {
        return planCompiler.compile(new PlacementPlanCompilerV2.PlacementCompileRequestV2(
                PLACEMENT_ID,
                OPERATION_ID,
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

    private static WorldAabbV2 generousWorldBounds() {
        return new WorldAabbV2(-512, -64, -512, 512, 320, 512);
    }

    private static PlacementEnvelopePlanV2.ResourceBudget generousBudget() {
        return new PlacementEnvelopePlanV2.ResourceBudget(
                PlacementEnvelopePlanV2.ResourceBudget.VERSION,
                64,
                50_000_000L,
                1_000_000_000L,
                8_192L,
                PlacementEnvelopePlanV2.MAX_CANONICAL_BYTES);
    }
}
