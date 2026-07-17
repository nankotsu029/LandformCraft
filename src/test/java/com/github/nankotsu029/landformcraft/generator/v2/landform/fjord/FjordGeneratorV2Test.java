package com.github.nankotsu029.landformcraft.generator.v2.landform.fjord;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompilationException;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.ClimatePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.WaterConditionPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.GeologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.HydrologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.LithologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.StrataPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.FjordPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FjordGeneratorV2Test {
    @Test
    void compilesSeaConnectedGlacialUProfileAndSidewalls() {
        Fixture fixture = fixture(129, 193, true, false, false, 16);
        FjordGeneratorV2.FjordMetrics metrics = new FjordGeneratorV2(fixture.plan()).evaluate(() -> false);
        assertTrue(metrics.seaConnected());
        assertTrue(metrics.slendernessOk());
        assertTrue(metrics.uProfileOk());
        assertTrue(metrics.sidewallReliefMedian() > 0);
        FjordProfileValidatorV2.requireValid(fixture.plan(), fixture.intent());
        assertEquals("walls", fixture.plan().glacialWallPlanHook().wallFeatureId());
    }

    @Test
    void rejectsLandlockedTooWideBrokenWallAndMissingSea() {
        FjordGenerationException landlocked = assertThrows(FjordGenerationException.class,
                () -> compile(fixture(129, 193, false, false, false, 16).intent(), 129, 193));
        assertEquals("v2.fjord-landlocked", landlocked.ruleId());

        FjordGenerationException tooWide = assertThrows(FjordGenerationException.class,
                () -> compile(fixture(129, 193, true, false, false, 64).intent(), 129, 193));
        assertEquals("v2.fjord-too-wide", tooWide.ruleId());

        FjordGenerationException brokenWall = assertThrows(FjordGenerationException.class,
                () -> compile(fixture(129, 193, true, false, true, 16).intent(), 129, 193));
        assertEquals("v2.fjord-broken-wall", brokenWall.ruleId());

        FjordGenerationException noSea = assertThrows(FjordGenerationException.class,
                () -> compile(fixture(129, 193, true, true, false, 16).intent(), 129, 193));
        assertEquals("v2.fjord-hard-boundary-conflict", noSea.ruleId());
    }

    @Test
    void rejectsCorruptedWallHookAfterCompile() {
        Fixture fixture = fixture(129, 193, true, false, false, 16);
        TerrainIntentV2 withoutWall = new TerrainIntentV2(
                fixture.intent().intentVersion(),
                fixture.intent().intentId(),
                fixture.intent().theme(),
                fixture.intent().coordinateSystem(),
                List.of(fixture.intent().features().getFirst()),
                fixture.intent().relations().stream()
                        .filter(relation -> relation.kind() != TerrainIntentV2.RelationKind.FLANKS)
                        .toList(),
                fixture.intent().constraints(),
                fixture.intent().environment(),
                fixture.intent().mapReferences(),
                fixture.intent().structures(),
                fixture.intent().provenance());
        FjordGenerationException corrupted = assertThrows(FjordGenerationException.class,
                () -> FjordProfileValidatorV2.requireValid(fixture.plan(), withoutWall));
        assertEquals("v2.fjord-broken-wall", corrupted.ruleId());
    }

    @Test
    void rejectsHardLandConflictAndBoundsMemoryBudget() {
        FjordPlanV2 plan = fixture(129, 193, true, false, false, 16).plan();
        FjordGeneratorV2 generator = new FjordGeneratorV2(plan);
        FjordGenerationException conflict = assertThrows(FjordGenerationException.class,
                () -> generator.sampleAt(64, 0, index -> true));
        assertEquals("v2.fjord-hard-boundary-conflict", conflict.ruleId());
        assertTrue(FjordGeneratorV2.estimateWindowRetainedBytes(1_000, 1_000)
                > FjordGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
    }

    @Test
    void wholeTileSeamThreadAndLocaleChecksumsMatch() throws Exception {
        Fixture fixture = fixture(129, 193, true, false, false, 16);
        FjordGeneratorV2 generator = new FjordGeneratorV2(fixture.plan());
        IntPredicate noConflict = index -> false;
        Map<FjordGeneratorV2.FjordField, String> direct = generator.fieldChecksums(noConflict, () -> false);
        Map<Long, FjordWindowV2> forward = renderTiles(generator, 64, false, 1, noConflict);
        Map<Long, FjordWindowV2> reverse = renderTiles(generator, 64, true, 1, noConflict);
        Map<Long, FjordWindowV2> parallel = renderTiles(generator, 64, true, 4, noConflict);

        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(forward, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(reverse, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(parallel, 64), () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Fixture reordered = fixture(129, 193, true, false, false, 16);
            assertEquals(fixture.plan(), reordered.plan());
            assertEquals(direct,
                    new FjordGeneratorV2(reordered.plan()).fieldChecksums(noConflict, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void enforcesRasterMemoryCpuAdmissionAndCooperativeCancellation() {
        Fixture maximum = fixture(1_000, 1_000, true, false, false, 80);
        FjordGeneratorV2 generator = new FjordGeneratorV2(maximum.plan());
        assertTrue(maximum.plan().estimatedRasterWorkUnits() <= FjordPlanV2.MAXIMUM_RASTER_WORK_UNITS);
        assertTrue(FjordGeneratorV2.estimateWindowRetainedBytes(1_000, 1_000)
                > FjordGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        FjordGenerationException memory = assertThrows(FjordGenerationException.class,
                () -> generator.renderWindow(0, 0, 1_000, 1_000, 0, index -> false, () -> false));
        assertEquals("v2.fjord-budget", memory.ruleId());

        Fixture small = fixture(129, 193, true, false, false, 16);
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(129, 193, -64, 255, 63);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        GeologyPlanV2 geology = new GeologyPlanCompilerV2().compile(
                bounds, 64, 827413L, hydrology.fixedPriors());
        LithologyPlanV2 lithology = new LithologyPlanCompilerV2().compile(geology);
        StrataPlanV2 strata = new StrataPlanCompilerV2().compile(geology, lithology);
        ClimatePlanV2 climate = new ClimatePlanCompilerV2().compile(
                bounds, 64, 827413L, hydrology, ClimatePlanV2.BaseClimatePreset.TEMPERATE_MARITIME);
        WaterConditionPlanV2 water = new WaterConditionPlanCompilerV2().compile(
                bounds, 64, 827413L, hydrology, climate);
        long priorCpu = hydrology.budget().estimatedCpuWorkUnits()
                + geology.budget().estimatedCpuWorkUnits()
                + lithology.budget().estimatedCpuWorkUnits()
                + strata.budget().estimatedCpuWorkUnits()
                + climate.budget().estimatedCpuWorkUnits()
                + water.budget().estimatedCpuWorkUnits();
        WorldBlueprintV2.ResourceBudget defaults = DiagnosticCompileRequestV2.defaultBudget();
        WorldBlueprintV2.ResourceBudget cpuTooSmall = new WorldBlueprintV2.ResourceBudget(
                defaults.maximumFeatures(), defaults.maximumRelations(), defaults.maximumConstraints(),
                defaults.maximumGeometryPoints(), defaults.maximumModules(), defaults.maximumFields(),
                defaults.maximumHaloXZ(), defaults.maximumHaloY(), defaults.maximumResidentBytes(),
                priorCpu + small.plan().estimatedRasterWorkUnits() - 1L,
                defaults.maximumArtifactBytes());
        DiagnosticCompileRequestV2 request = new DiagnosticCompileRequestV2(
                small.intent().intentId(), new GenerationBounds(129, 193, -64, 255, 63), 64, 827413L,
                "c".repeat(64), cpuTooSmall);
        DiagnosticCompilationException cpu = assertThrows(DiagnosticCompilationException.class,
                () -> new DiagnosticBlueprintCompilerV2().compile(request, small.intent()));
        assertEquals("v2.fjord-budget", cpu.ruleId());

        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> new FjordGeneratorV2(small.plan())
                .renderWindow(0, 0, 64, 64, 0, index -> false, () -> checks.incrementAndGet() > 1));
        assertTrue(checks.get() > 1);
    }

    private static Map<Long, FjordWindowV2> renderTiles(
            FjordGeneratorV2 generator,
            int tileSize,
            boolean reverse,
            int threads,
            IntPredicate hardLandConflict
    ) throws Exception {
        List<int[]> tiles = new ArrayList<>();
        for (int z = 0; z < generator.length(); z += tileSize) {
            for (int x = 0; x < generator.width(); x += tileSize) {
                tiles.add(new int[]{
                        x,
                        z,
                        Math.min(tileSize, generator.width() - x),
                        Math.min(tileSize, generator.length() - z)});
            }
        }
        if (reverse) {
            tiles = tiles.reversed();
        }
        Map<Long, FjordWindowV2> result = new HashMap<>();
        if (threads <= 1) {
            for (int[] tile : tiles) {
                result.put(pack(tile[0], tile[1]), generator.renderWindow(
                        tile[0], tile[1], tile[2], tile[3], 0, hardLandConflict, () -> false));
            }
            return result;
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Map.Entry<Long, FjordWindowV2>>> futures = new ArrayList<>();
            for (int[] tile : tiles) {
                futures.add(executor.submit(() -> Map.entry(
                        pack(tile[0], tile[1]),
                        generator.renderWindow(tile[0], tile[1], tile[2], tile[3], 0, hardLandConflict, () -> false))));
            }
            for (Future<Map.Entry<Long, FjordWindowV2>> future : futures) {
                Map.Entry<Long, FjordWindowV2> entry = future.get();
                result.put(entry.getKey(), entry.getValue());
            }
        } catch (ExecutionException exception) {
            throw exception.getCause() instanceof Exception cause ? cause : exception;
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static FjordGeneratorV2.CellSource tiledSource(Map<Long, FjordWindowV2> tiles, int tileSize) {
        return (x, z) -> {
            int originX = (x / tileSize) * tileSize;
            int originZ = (z / tileSize) * tileSize;
            FjordWindowV2 window = tiles.get(pack(originX, originZ));
            return new FjordGeneratorV2.FjordSample(
                    window.rawValueAt(FjordGeneratorV2.FjordField.CHANNEL_MASK, x, z),
                    window.rawValueAt(FjordGeneratorV2.FjordField.FLOOR_MASK, x, z),
                    window.rawValueAt(FjordGeneratorV2.FjordField.SIDEWALL_MASK, x, z),
                    window.rawValueAt(FjordGeneratorV2.FjordField.THALWEG_DEPTH, x, z),
                    window.rawValueAt(FjordGeneratorV2.FjordField.SIDEWALL_RELIEF, x, z));
        };
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) | (z & 0xffff_ffffL);
    }

    private static Fixture fixture(
            int width,
            int length,
            boolean marine,
            boolean landBoundary,
            boolean brokenWall,
            int surfaceWidth
    ) {
        TerrainIntentV2.Feature fjord = new TerrainIntentV2.Feature(
                "fjord",
                TerrainIntentV2.FeatureKind.FJORD,
                new TerrainIntentV2.SplineGeometry(List.of(
                        new TerrainIntentV2.Point2(500_000, marine ? 0 : 100_000),
                        new TerrainIntentV2.Point2(500_000, 1_000_000)), TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.FjordParameters(
                        new TerrainIntentV2.IntRange(surfaceWidth, surfaceWidth),
                        new TerrainIntentV2.IntRange(8, 8),
                        TerrainIntentV2.FjordCrossSection.GLACIAL_U,
                        16),
                0,
                TerrainIntentV2.Provenance.confirmedManual("fjord-test"));
        TerrainIntentV2.Feature walls = new TerrainIntentV2.Feature(
                "walls",
                brokenWall
                        ? TerrainIntentV2.FeatureKind.MANGROVE_WETLAND
                        : TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE,
                new TerrainIntentV2.PolygonGeometry(List.of(List.of(
                        new TerrainIntentV2.Point2(100_000, 100_000),
                        new TerrainIntentV2.Point2(900_000, 100_000),
                        new TerrainIntentV2.Point2(900_000, 990_000),
                        new TerrainIntentV2.Point2(100_000, 990_000),
                        new TerrainIntentV2.Point2(100_000, 100_000)))),
                brokenWall
                        ? new TerrainIntentV2.NoParameters()
                        : new TerrainIntentV2.MountainParameters(
                                new TerrainIntentV2.IntRange(3, 3),
                                new TerrainIntentV2.IntRange(4, 4),
                                new TerrainIntentV2.IntRange(48, 48),
                                0,
                                600_000),
                0,
                TerrainIntentV2.Provenance.confirmedManual("fjord-test"));
        List<TerrainIntentV2.Relation> relations = new ArrayList<>();
        relations.add(new TerrainIntentV2.Relation(
                "fjord-outlet",
                TerrainIntentV2.RelationKind.EMPTIES_INTO,
                "feature:fjord",
                "boundary:NORTH",
                TerrainIntentV2.Strength.HARD));
        relations.add(new TerrainIntentV2.Relation(
                "walls-flank-fjord",
                TerrainIntentV2.RelationKind.FLANKS,
                "feature:walls",
                "feature:fjord",
                TerrainIntentV2.Strength.HARD));
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "fjord-test",
                "fjord",
                coordinateSystem(),
                List.of(fjord, walls),
                relations,
                List.of(new TerrainIntentV2.EdgeClassificationConstraint(
                        "north",
                        TerrainIntentV2.Strength.HARD,
                        "world",
                        TerrainIntentV2.Edge.NORTH,
                        landBoundary
                                ? TerrainIntentV2.EdgeClassification.LAND
                                : TerrainIntentV2.EdgeClassification.SEA,
                        100_000,
                        0)),
                new TerrainIntentV2.EnvironmentDescriptor(
                        "GLACIATED_HARD_ROCK", "COLD_MARITIME", "SUBALPINE_FJORD"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("fjord-test"));
        FjordPlanV2 plan = null;
        long lengthBlocks = Math.max(0L, length - 1L);
        long ratio = surfaceWidth == 0 ? 0L : lengthBlocks / surfaceWidth;
        if (marine && !landBoundary && !brokenWall && ratio >= 5L && ratio <= 14L) {
            plan = compile(intent, width, length);
        }
        return new Fixture(intent, plan);
    }

    private static TerrainIntentV2.CoordinateSystem coordinateSystem() {
        return new TerrainIntentV2.CoordinateSystem(
                TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                TerrainIntentV2.XAxis.EAST,
                TerrainIntentV2.ZAxis.SOUTH,
                TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET);
    }

    private static FjordPlanV2 compile(TerrainIntentV2 intent, int width, int length) {
        TerrainIntentV2.Feature feature = intent.features().stream()
                .filter(candidate -> candidate.kind() == TerrainIntentV2.FeatureKind.FJORD)
                .findFirst()
                .orElseThrow();
        return new FjordPlanCompilerV2().compile(
                feature,
                intent,
                new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                "a".repeat(64));
    }

    private record Fixture(TerrainIntentV2 intent, FjordPlanV2 plan) {
    }
}
