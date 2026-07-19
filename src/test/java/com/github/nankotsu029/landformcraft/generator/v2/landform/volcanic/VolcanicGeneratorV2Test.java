package com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic;

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
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolcanicGeneratorV2Test {
    @Test
    void compilesSeparatedDominantIslandsWithCalderaAndLavaHooks() {
        Fixture fixture = archipelago(257, 257, false, false, false);
        VolcanicProfileValidatorV2.requireValid(fixture.plan());
        VolcanicGeneratorV2.VolcanicMetrics metrics = new VolcanicGeneratorV2(fixture.plan()).evaluate(() -> false);
        assertEquals(3, metrics.componentCount());
        assertTrue(metrics.dryGapOk());
        assertTrue(metrics.dominanceOk());
        assertTrue(metrics.marineSeparationOk());
        assertNotNull(fixture.plan().calderaPlanHook());
        assertNotNull(fixture.plan().lavaPlanHook());
        assertEquals("main-island", fixture.plan().calderaPlanHook().hostPointId());
    }

    @Test
    void rejectsMergedOrphanUnknownChildAndBoundsCorruption() {
        VolcanicGenerationException merged = assertThrows(VolcanicGenerationException.class,
                () -> compile(archipelago(257, 257, true, false, false).intent(), 257, 257));
        assertEquals("v2.volcanic-merged-islands", merged.ruleId());

        VolcanicGenerationException orphan = assertThrows(VolcanicGenerationException.class,
                () -> compile(archipelago(257, 257, false, true, false).intent(), 257, 257));
        assertEquals("v2.volcanic-orphan-caldera", orphan.ruleId());

        VolcanicGenerationException unknown = assertThrows(VolcanicGenerationException.class,
                () -> compile(archipelago(257, 257, false, false, true).intent(), 257, 257));
        assertEquals("v2.volcanic-unknown-child", unknown.ruleId());

        TerrainIntentV2.Feature oversized = new TerrainIntentV2.Feature(
                "island-arc",
                TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO,
                new TerrainIntentV2.MultiPointGeometry(List.of(
                        new TerrainIntentV2.NamedPoint("west-island", new TerrainIntentV2.Point2(50_000, 500_000)),
                        new TerrainIntentV2.NamedPoint("main-island", new TerrainIntentV2.Point2(500_000, 500_000)))),
                new TerrainIntentV2.VolcanicArchipelagoParameters(
                        List.of(
                                new TerrainIntentV2.IslandSpec("west-island", 64, 40),
                                new TerrainIntentV2.IslandSpec("main-island", 96, 80)),
                        new TerrainIntentV2.IntRange(10, 16)),
                0,
                TerrainIntentV2.Provenance.confirmedManual("volcanic-test"));
        TerrainIntentV2 boundsIntent = new TerrainIntentV2(
                TerrainIntentV2.VERSION, "volcanic-bounds", "bounds", coordinateSystem(),
                List.of(oversized), List.of(), List.of(),
                new TerrainIntentV2.EnvironmentDescriptor("", "", ""),
                List.of(), List.of(), TerrainIntentV2.Provenance.confirmedManual("volcanic-test"));
        VolcanicGenerationException bounds = assertThrows(VolcanicGenerationException.class,
                () -> compile(boundsIntent, 129, 129));
        assertEquals("v2.volcanic-bounds", bounds.ruleId());
    }

    @Test
    void wholeTileThreadLocaleAndPointOrderChecksumsMatch() throws Exception {
        Fixture fixture = archipelago(257, 257, false, false, false);
        Fixture reversedPoints = archipelagoReversedPoints(257, 257);
        assertEquals(fixture.plan(), reversedPoints.plan());

        VolcanicGeneratorV2 generator = new VolcanicGeneratorV2(fixture.plan());
        IntPredicate noConflict = index -> false;
        Map<VolcanicGeneratorV2.VolcanicField, String> direct = generator.fieldChecksums(noConflict, () -> false);
        Map<Long, VolcanicWindowV2> forward = renderTiles(generator, 64, false, 1, noConflict);
        Map<Long, VolcanicWindowV2> reverse = renderTiles(generator, 64, true, 1, noConflict);
        Map<Long, VolcanicWindowV2> parallel = renderTiles(generator, 64, true, 4, noConflict);
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(forward, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(reverse, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(parallel, 64), () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(direct,
                    new VolcanicGeneratorV2(archipelago(257, 257, false, false, false).plan())
                            .fieldChecksums(noConflict, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void enforcesRasterMemoryCpuAdmissionAndCooperativeCancellation() {
        Fixture maximum = archipelago(1_000, 1_000, false, false, false);
        VolcanicGeneratorV2 generator = new VolcanicGeneratorV2(maximum.plan());
        assertTrue(maximum.plan().estimatedRasterWorkUnits() <= VolcanicPlanV2.MAXIMUM_RASTER_WORK_UNITS);
        assertTrue(VolcanicGeneratorV2.estimateWindowRetainedBytes(1_000, 1_000)
                > VolcanicGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        VolcanicGenerationException memory = assertThrows(VolcanicGenerationException.class,
                () -> generator.renderWindow(0, 0, 1_000, 1_000, 0, index -> false, () -> false));
        assertEquals("v2.volcanic-budget", memory.ruleId());

        Fixture small = archipelago(257, 257, false, false, false);
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(257, 257, -64, 255, 63);
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
                small.intent().intentId(), new GenerationBounds(257, 257, -64, 255, 63), 64, 827413L,
                "c".repeat(64), cpuTooSmall);
        DiagnosticCompilationException cpu = assertThrows(DiagnosticCompilationException.class,
                () -> new DiagnosticBlueprintCompilerV2().compile(request, small.intent()));
        assertEquals("v2.volcanic-budget", cpu.ruleId());

        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> new VolcanicGeneratorV2(small.plan())
                .renderWindow(0, 0, 64, 64, 0, index -> false, () -> checks.incrementAndGet() > 1));
        assertTrue(checks.get() > 1);
    }

    private static Map<Long, VolcanicWindowV2> renderTiles(
            VolcanicGeneratorV2 generator, int tileSize, boolean reverse, int threads, IntPredicate hardSeaConflict
    ) throws Exception {
        List<int[]> tiles = new ArrayList<>();
        for (int z = 0; z < generator.length(); z += tileSize) {
            for (int x = 0; x < generator.width(); x += tileSize) {
                tiles.add(new int[]{x, z, Math.min(tileSize, generator.width() - x),
                        Math.min(tileSize, generator.length() - z)});
            }
        }
        if (reverse) tiles = tiles.reversed();
        Map<Long, VolcanicWindowV2> result = new HashMap<>();
        if (threads <= 1) {
            for (int[] tile : tiles) {
                result.put(pack(tile[0], tile[1]), generator.renderWindow(
                        tile[0], tile[1], tile[2], tile[3], 0, hardSeaConflict, () -> false));
            }
            return result;
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Map.Entry<Long, VolcanicWindowV2>>> futures = new ArrayList<>();
            for (int[] tile : tiles) {
                futures.add(executor.submit(() -> Map.entry(pack(tile[0], tile[1]),
                        generator.renderWindow(tile[0], tile[1], tile[2], tile[3], 0, hardSeaConflict, () -> false))));
            }
            for (Future<Map.Entry<Long, VolcanicWindowV2>> future : futures) {
                Map.Entry<Long, VolcanicWindowV2> entry = future.get();
                result.put(entry.getKey(), entry.getValue());
            }
        } catch (ExecutionException exception) {
            throw exception.getCause() instanceof Exception cause ? cause : exception;
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static VolcanicGeneratorV2.CellSource tiledSource(Map<Long, VolcanicWindowV2> tiles, int tileSize) {
        return (x, z) -> {
            VolcanicWindowV2 window = tiles.get(pack((x / tileSize) * tileSize, (z / tileSize) * tileSize));
            return new VolcanicGeneratorV2.VolcanicSample(
                    window.rawValueAt(VolcanicGeneratorV2.VolcanicField.ISLAND_MASK, x, z),
                    window.rawValueAt(VolcanicGeneratorV2.VolcanicField.ISLAND_INDEX, x, z),
                    window.rawValueAt(VolcanicGeneratorV2.VolcanicField.SUMMIT_RELIEF, x, z),
                    window.rawValueAt(VolcanicGeneratorV2.VolcanicField.SUBMARINE_SADDLE_MASK, x, z),
                    window.rawValueAt(VolcanicGeneratorV2.VolcanicField.RADIAL_DRAINAGE, x, z),
                    window.rawValueAt(VolcanicGeneratorV2.VolcanicField.PROVISIONAL_SURFACE, x, z));
        };
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) | (z & 0xffff_ffffL);
    }

    private static Fixture archipelago(
            int width, int length, boolean merged, boolean orphanCaldera, boolean unknownChild
    ) {
        return build(width, length, merged, orphanCaldera, unknownChild, false);
    }

    private static Fixture archipelagoReversedPoints(int width, int length) {
        return build(width, length, false, false, false, true);
    }

    private static Fixture build(
            int width, int length, boolean merged, boolean orphanCaldera, boolean unknownChild, boolean reversePoints
    ) {
        // Separated layout: centers leave >=12-block dry gaps for r≈28/48/26.
        // Merged layout: in-bounds centers with oversized radii so dry-gap fails before bounds.
        List<TerrainIntentV2.NamedPoint> points = merged
                ? new ArrayList<>(List.of(
                        new TerrainIntentV2.NamedPoint("west-island", new TerrainIntentV2.Point2(400_000, 500_000)),
                        new TerrainIntentV2.NamedPoint("main-island", new TerrainIntentV2.Point2(500_000, 500_000)),
                        new TerrainIntentV2.NamedPoint("east-island", new TerrainIntentV2.Point2(600_000, 500_000))))
                : new ArrayList<>(List.of(
                        new TerrainIntentV2.NamedPoint("west-island", new TerrainIntentV2.Point2(150_000, 520_000)),
                        new TerrainIntentV2.NamedPoint("main-island", new TerrainIntentV2.Point2(500_000, 420_000)),
                        new TerrainIntentV2.NamedPoint("east-island", new TerrainIntentV2.Point2(850_000, 540_000))));
        if (reversePoints) {
            points = new ArrayList<>(List.of(points.get(2), points.get(1), points.get(0)));
        }
        int westRadius = merged ? 80 : 28;
        int eastRadius = merged ? 80 : 26;
        TerrainIntentV2.Feature archipelago = new TerrainIntentV2.Feature(
                "island-arc",
                TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO,
                new TerrainIntentV2.MultiPointGeometry(points),
                new TerrainIntentV2.VolcanicArchipelagoParameters(
                        List.of(
                                new TerrainIntentV2.IslandSpec("east-island", eastRadius, 44),
                                new TerrainIntentV2.IslandSpec("main-island", 48, 96),
                                new TerrainIntentV2.IslandSpec("west-island", westRadius, 48)),
                        new TerrainIntentV2.IntRange(10, 16)),
                0,
                TerrainIntentV2.Provenance.confirmedManual("volcanic-test"));
        TerrainIntentV2.Feature caldera = new TerrainIntentV2.Feature(
                "central-caldera",
                TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA,
                new TerrainIntentV2.PointGeometry(new TerrainIntentV2.Point2(
                        orphanCaldera ? 100_000 : 500_000,
                        orphanCaldera ? 100_000 : 420_000)),
                new TerrainIntentV2.VolcanicCalderaParameters(
                        18, 22, 12, TerrainIntentV2.CalderaBreachDirection.SOUTH_EAST),
                0,
                TerrainIntentV2.Provenance.confirmedManual("volcanic-test"));
        TerrainIntentV2.Feature lava = new TerrainIntentV2.Feature(
                "young-lava-flow",
                TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD,
                new TerrainIntentV2.SplineGeometry(List.of(
                        new TerrainIntentV2.Point2(500_000, 420_000),
                        new TerrainIntentV2.Point2(550_000, 500_000),
                        new TerrainIntentV2.Point2(590_000, 650_000)), TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.LavaFlowParameters(new TerrainIntentV2.IntRange(8, 16), 700_000),
                0,
                TerrainIntentV2.Provenance.confirmedManual("volcanic-test"));
        List<TerrainIntentV2.Feature> features = new ArrayList<>(List.of(archipelago, caldera, lava));
        List<TerrainIntentV2.Relation> relations = new ArrayList<>();
        relations.add(new TerrainIntentV2.Relation(
                "caldera-on-main-island", TerrainIntentV2.RelationKind.WITHIN,
                "feature:central-caldera", "feature:island-arc", TerrainIntentV2.Strength.HARD));
        relations.add(new TerrainIntentV2.Relation(
                "lava-from-caldera", TerrainIntentV2.RelationKind.ORIGINATES_AT,
                "feature:young-lava-flow", "feature:central-caldera", TerrainIntentV2.Strength.HARD));
        if (unknownChild) {
            TerrainIntentV2.Feature fake = new TerrainIntentV2.Feature(
                    "fake-child",
                    TerrainIntentV2.FeatureKind.MANGROVE_WETLAND,
                    new TerrainIntentV2.PolygonGeometry(List.of(List.of(
                            new TerrainIntentV2.Point2(400_000, 400_000),
                            new TerrainIntentV2.Point2(600_000, 400_000),
                            new TerrainIntentV2.Point2(600_000, 600_000),
                            new TerrainIntentV2.Point2(400_000, 600_000),
                            new TerrainIntentV2.Point2(400_000, 400_000)))),
                    defaultMangroveParameters(),
                    0,
                    TerrainIntentV2.Provenance.confirmedManual("volcanic-test"));
            features.add(fake);
            relations.set(0, new TerrainIntentV2.Relation(
                    "fake-within", TerrainIntentV2.RelationKind.WITHIN,
                    "feature:fake-child", "feature:island-arc", TerrainIntentV2.Strength.HARD));
        }
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION, "volcanic-test", "arc", coordinateSystem(),
                features, relations, List.of(),
                new TerrainIntentV2.EnvironmentDescriptor("BASALTIC_VOLCANIC", "WARM_MARITIME", "VOLCANIC_SUCCESSION"),
                List.of(), List.of(), TerrainIntentV2.Provenance.confirmedManual("volcanic-test"));
        VolcanicPlanV2 plan = null;
        if (!merged && !orphanCaldera && !unknownChild) {
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

    private static VolcanicPlanV2 compile(TerrainIntentV2 intent, int width, int length) {
        TerrainIntentV2.Feature feature = intent.features().stream()
                .filter(candidate -> candidate.kind() == TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO)
                .findFirst()
                .orElseThrow();
        return new VolcanicPlanCompilerV2().compile(
                feature, intent, new WorldBlueprintV2.Bounds(width, length, -64, 255, 63), "a".repeat(64));
    }

    private static TerrainIntentV2.MangroveWetlandParameters defaultMangroveParameters() {
        return new TerrainIntentV2.MangroveWetlandParameters(
                new TerrainIntentV2.IntRange(2, 3),
                new TerrainIntentV2.FixedRange(200_000, 400_000));
    }

    private record Fixture(TerrainIntentV2 intent, VolcanicPlanV2 plan) {
    }
}
