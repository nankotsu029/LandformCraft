package com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal;

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
import com.github.nankotsu029.landformcraft.model.v2.TidalChannelPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TidalChannelGeneratorV2Test {
    private static final String GEOMETRY_CHECKSUM = "a".repeat(64);

    @Test
    void generatesMarineConnectedBidirectionalChannelsAndDepthCorridor() {
        Fixture fixture = fixture(129, 193, false, true, false, false);
        TidalChannelPlanV2 plan = fixture.tidalPlan();
        TidalChannelGeneratorV2.TidalMetrics metrics =
                new TidalChannelGeneratorV2(plan).evaluate(index -> false, () -> false);

        assertTrue(plan.edges().size() >= 2);
        assertTrue(metrics.marineNodeCount() >= 1L);
        assertTrue(metrics.allMarineEndpointsConnected());
        assertTrue(metrics.channelCells() > 0L);
        assertEquals(metrics.channelCells(), metrics.depthCorridorCells());
        assertEquals(TerrainIntentV2.TidalEdgeKind.BIDIRECTIONAL, plan.edgeKind());
        assertNotNull(plan.wetlandChildPlanHook());
        assertEquals(plan.estimatedRasterWorkUnits(), metrics.estimatedRasterWorkUnits());
    }

    @Test
    void wholeAndTileFieldsMatchAcrossSeamsOrderThreadsPathOrderLocaleAndTimezone() throws Exception {
        Fixture fixture = fixture(257, 193, false, true, false, false);
        TidalChannelGeneratorV2 generator = new TidalChannelGeneratorV2(fixture.tidalPlan());
        IntPredicate noConflict = index -> false;
        Map<TidalChannelGeneratorV2.TidalField, String> direct =
                generator.fieldChecksums(noConflict, () -> false);
        Map<Long, TidalWindowV2> forward = renderTiles(generator, 64, false, 1, noConflict);
        Map<Long, TidalWindowV2> reverse = renderTiles(generator, 64, true, 1, noConflict);
        Map<Long, TidalWindowV2> parallel = renderTiles(generator, 64, true, 4, noConflict);

        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(forward, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(reverse, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(parallel, 64), () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Fixture reordered = fixture(257, 193, true, true, false, false);
            assertEquals(fixture.tidalPlan(), reordered.tidalPlan());
            assertEquals(direct,
                    new TidalChannelGeneratorV2(reordered.tidalPlan()).fieldChecksums(noConflict, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void rejectsClosedAmbiguousIsolatedAndUnknownEdgeCorruption() {
        Fixture closed = fixture(129, 193, false, true, true, false);
        TidalGenerationException closedChannel = assertThrows(TidalGenerationException.class,
                () -> compileTidal(closed.intent(), 129, 193));
        assertEquals("v2.tidal-closed-channel", closedChannel.ruleId());

        Fixture ambiguous = fixture(129, 193, false, true, false, true);
        TidalGenerationException ambiguousDirection = assertThrows(TidalGenerationException.class,
                () -> compileTidal(ambiguous.intent(), 129, 193));
        assertEquals("v2.tidal-ambiguous-direction", ambiguousDirection.ruleId());

        TidalChannelPlanV2 plan = fixture(129, 193, false, true, false, false).tidalPlan();
        TidalChannelPlanV2.ChannelEdge first = plan.edges().getFirst();
        List<TidalChannelPlanV2.ChannelEdge> isolated = List.of(new TidalChannelPlanV2.ChannelEdge(
                first.edgeId(),
                first.pathId(),
                "orphan-from",
                "orphan-to",
                first.path(),
                first.edgeKind(),
                first.halfWidthBlocks(),
                first.depthBlocks()));
        TidalGenerationException isolatedComponent = assertThrows(TidalGenerationException.class,
                () -> TidalGraphValidatorV2.requireValid(plan, isolated));
        assertEquals("v2.tidal-isolated-component", isolatedComponent.ruleId());

        List<TidalChannelPlanV2.ChannelEdge> duplicate = List.of(first, first);
        TidalGenerationException unknown = assertThrows(TidalGenerationException.class,
                () -> TidalGraphValidatorV2.requireValid(plan, duplicate));
        assertEquals("v2.tidal-unknown-edge", unknown.ruleId());
    }

    @Test
    void rejectsHardSeaNoDataAndLandConflictBeforeCanonicalRaster() {
        Fixture hardLand = fixture(129, 193, false, false, false, false);
        TidalGenerationException hardNoData = assertThrows(TidalGenerationException.class,
                () -> compileTidal(hardLand.intent(), 129, 193));
        assertEquals("v2.tidal-hard-no-data", hardNoData.ruleId());

        TidalChannelPlanV2 plan = fixture(129, 193, false, true, false, false).tidalPlan();
        TidalChannelGeneratorV2 generator = new TidalChannelGeneratorV2(plan);
        TidalChannelPlanV2.ChannelPoint marine = plan.nodes().stream()
                .filter(TidalChannelPlanV2.ChannelNode::marine)
                .findFirst()
                .orElseThrow()
                .point();
        int x = Math.toIntExact(TidalFixedMathV2.roundDivide(marine.xMillionths(), TerrainIntentV2.FIXED_SCALE));
        int z = Math.toIntExact(TidalFixedMathV2.roundDivide(marine.zMillionths(), TerrainIntentV2.FIXED_SCALE));
        int blocked = z * plan.width() + x;
        TidalGenerationException rasterConflict = assertThrows(TidalGenerationException.class,
                () -> generator.sampleAt(x, z, index -> index == blocked));
        assertEquals("v2.tidal-hard-no-data", rasterConflict.ruleId());
    }

    @Test
    void enforcesRasterMemoryCpuAdmissionAndCooperativeCancellation() {
        assertEquals(-1L, TidalFixedMathV2.roundDivide(-2L, 2L));
        Fixture maximum = fixture(1_000, 1_000, false, true, false, false);
        TidalChannelGeneratorV2 generator = new TidalChannelGeneratorV2(maximum.tidalPlan());
        assertTrue(maximum.tidalPlan().estimatedRasterWorkUnits()
                <= TidalChannelPlanV2.MAXIMUM_RASTER_WORK_UNITS);
        assertTrue(TidalChannelGeneratorV2.estimateWindowRetainedBytes(1_000, 1_000)
                > TidalChannelGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        TidalGenerationException memory = assertThrows(TidalGenerationException.class,
                () -> generator.renderWindow(0, 0, 1_000, 1_000, 0, index -> false, () -> false));
        assertEquals("v2.tidal-budget", memory.ruleId());

        Fixture small = fixture(129, 193, false, true, false, false);
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
                priorCpu + small.tidalPlan().estimatedRasterWorkUnits() - 1L,
                defaults.maximumArtifactBytes());
        DiagnosticCompileRequestV2 request = new DiagnosticCompileRequestV2(
                small.intent().intentId(), new GenerationBounds(129, 193, -64, 255, 63), 64, 827413L,
                "c".repeat(64), cpuTooSmall);
        DiagnosticCompilationException cpu = assertThrows(DiagnosticCompilationException.class,
                () -> new DiagnosticBlueprintCompilerV2().compile(request, small.intent()));
        assertEquals("v2.tidal-budget", cpu.ruleId());

        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> new TidalChannelGeneratorV2(small.tidalPlan())
                .renderWindow(0, 0, 64, 64, 0, index -> false, () -> checks.incrementAndGet() > 1));
        assertTrue(checks.get() > 1);
    }

    private static Fixture fixture(
            int width,
            int length,
            boolean reversePathOrder,
            boolean hardSea,
            boolean closed,
            boolean ambiguousWest
    ) {
        List<TerrainIntentV2.NamedPath> paths = new ArrayList<>();
        if (closed) {
            paths.add(new TerrainIntentV2.NamedPath(
                    "main-channel", "", "",
                    List.of(
                            new TerrainIntentV2.Point2(480_000, 700_000),
                            new TerrainIntentV2.Point2(510_000, 500_000),
                            new TerrainIntentV2.Point2(360_000, 200_000))));
            paths.add(new TerrainIntentV2.NamedPath(
                    "east-branch", "", "",
                    List.of(
                            new TerrainIntentV2.Point2(500_000, 700_000),
                            new TerrainIntentV2.Point2(700_000, 520_000),
                            new TerrainIntentV2.Point2(880_000, 340_000))));
        } else if (ambiguousWest) {
            paths.add(new TerrainIntentV2.NamedPath(
                    "main-channel", "", "",
                    List.of(
                            new TerrainIntentV2.Point2(0, 500_000),
                            new TerrainIntentV2.Point2(250_000, 520_000),
                            new TerrainIntentV2.Point2(480_000, 1_000_000))));
            paths.add(new TerrainIntentV2.NamedPath(
                    "east-branch", "", "",
                    List.of(
                            new TerrainIntentV2.Point2(500_000, 700_000),
                            new TerrainIntentV2.Point2(700_000, 520_000),
                            new TerrainIntentV2.Point2(880_000, 340_000))));
        } else {
            // Branches share the marine endpoint so the undirected graph stays connected.
            paths.add(new TerrainIntentV2.NamedPath(
                    "main-channel", "", "",
                    List.of(
                            new TerrainIntentV2.Point2(480_000, 1_000_000),
                            new TerrainIntentV2.Point2(510_000, 680_000),
                            new TerrainIntentV2.Point2(360_000, 200_000))));
            paths.add(new TerrainIntentV2.NamedPath(
                    "east-branch", "", "",
                    List.of(
                            new TerrainIntentV2.Point2(480_000, 1_000_000),
                            new TerrainIntentV2.Point2(700_000, 520_000),
                            new TerrainIntentV2.Point2(880_000, 340_000))));
            paths.add(new TerrainIntentV2.NamedPath(
                    "west-branch", "", "",
                    List.of(
                            new TerrainIntentV2.Point2(480_000, 1_000_000),
                            new TerrainIntentV2.Point2(260_000, 470_000),
                            new TerrainIntentV2.Point2(120_000, 310_000))));
        }
        if (reversePathOrder) {
            Collections.reverse(paths);
        }
        TerrainIntentV2.Feature tidal = new TerrainIntentV2.Feature(
                "tidal-channels",
                TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK,
                new TerrainIntentV2.MultiSplineGeometry(paths, TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.TidalChannelParameters(
                        new TerrainIntentV2.IntRange(6, 12),
                        2,
                        TerrainIntentV2.TidalEdgeKind.BIDIRECTIONAL),
                0,
                TerrainIntentV2.Provenance.confirmedManual("tidal-test"));
        TerrainIntentV2.Feature wetland = new TerrainIntentV2.Feature(
                "mangrove-wetland",
                TerrainIntentV2.FeatureKind.MANGROVE_WETLAND,
                new TerrainIntentV2.PolygonGeometry(List.of(List.of(
                        new TerrainIntentV2.Point2(50_000, 100_000),
                        new TerrainIntentV2.Point2(950_000, 100_000),
                        new TerrainIntentV2.Point2(960_000, 1_000_000),
                        new TerrainIntentV2.Point2(80_000, 1_000_000),
                        new TerrainIntentV2.Point2(50_000, 100_000)))),
                defaultMangroveParameters(),
                0,
                TerrainIntentV2.Provenance.confirmedManual("tidal-test"));
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "tidal-fixture",
                "bounded tidal channel fixture",
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                List.of(tidal, wetland),
                List.of(
                        new TerrainIntentV2.Relation(
                                "channels-within-wetland", TerrainIntentV2.RelationKind.WITHIN,
                                "feature:tidal-channels", "feature:mangrove-wetland",
                                TerrainIntentV2.Strength.HARD),
                        new TerrainIntentV2.Relation(
                                "tidal-empties-south", TerrainIntentV2.RelationKind.EMPTIES_INTO,
                                "feature:tidal-channels", "boundary:SOUTH", TerrainIntentV2.Strength.HARD)),
                List.of(new TerrainIntentV2.EdgeClassificationConstraint(
                        "south-is-sea", TerrainIntentV2.Strength.HARD, "world",
                        TerrainIntentV2.Edge.SOUTH,
                        hardSea ? TerrainIntentV2.EdgeClassification.SEA
                                : TerrainIntentV2.EdgeClassification.LAND,
                        100_000, 0)),
                new TerrainIntentV2.EnvironmentDescriptor(
                        "TIDAL_MUD_AND_SILT", "WARM_HUMID_MARITIME", "MANGROVE_ESTUARY"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("tidal-test"));
        TidalChannelPlanV2 plan = hardSea && !closed && !ambiguousWest
                ? compileTidal(intent, width, length)
                : null;
        return new Fixture(intent, plan);
    }

    private static TidalChannelPlanV2 compileTidal(TerrainIntentV2 intent, int width, int length) {
        return new TidalChannelPlanCompilerV2().compile(
                intent.features().stream()
                        .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK)
                        .findFirst()
                        .orElseThrow(),
                intent,
                new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                GEOMETRY_CHECKSUM);
    }

    private static Map<Long, TidalWindowV2> renderTiles(
            TidalChannelGeneratorV2 generator,
            int tileSize,
            boolean reverse,
            int threads,
            IntPredicate hardLandConflict
    ) throws Exception {
        List<long[]> tiles = new ArrayList<>();
        for (int originZ = 0; originZ < generator.length(); originZ += tileSize) {
            for (int originX = 0; originX < generator.width(); originX += tileSize) {
                tiles.add(new long[]{
                        originX,
                        originZ,
                        Math.min(tileSize, generator.width() - originX),
                        Math.min(tileSize, generator.length() - originZ)});
            }
        }
        if (reverse) Collections.reverse(tiles);
        Map<Long, TidalWindowV2> result = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (long[] tile : tiles) {
                futures.add(executor.submit(() -> {
                    TidalWindowV2 window = generator.renderWindow(
                            (int) tile[0], (int) tile[1], (int) tile[2], (int) tile[3],
                            0, hardLandConflict, () -> false);
                    result.put((tile[1] << 32) | tile[0], window);
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static TidalChannelGeneratorV2.CellSource tiledSource(Map<Long, TidalWindowV2> tiles, int tileSize) {
        return (x, z) -> {
            int originX = (x / tileSize) * tileSize;
            int originZ = (z / tileSize) * tileSize;
            TidalWindowV2 window = tiles.get((((long) originZ) << 32) | originX);
            return new TidalChannelGeneratorV2.TidalSample(
                    window.rawValueAt(TidalChannelGeneratorV2.TidalField.CHANNEL_MASK, x, z),
                    window.rawValueAt(TidalChannelGeneratorV2.TidalField.BRANCH_INDEX, x, z),
                    window.rawValueAt(TidalChannelGeneratorV2.TidalField.DEPTH_CORRIDOR, x, z),
                    window.rawValueAt(TidalChannelGeneratorV2.TidalField.MARINE_CONNECTION, x, z));
        };
    }

    private static TerrainIntentV2.MangroveWetlandParameters defaultMangroveParameters() {
        return new TerrainIntentV2.MangroveWetlandParameters(
                new TerrainIntentV2.IntRange(2, 3),
                new TerrainIntentV2.FixedRange(200_000, 400_000));
    }

    private record Fixture(TerrainIntentV2 intent, TidalChannelPlanV2 tidalPlan) {
    }
}
