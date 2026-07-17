package com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompilationException;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.ClimatePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.WaterConditionPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.GeologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.HydrologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.LithologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.StrataPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.DeltaPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeltaGeneratorV2Test {
    private static final String GEOMETRY_CHECKSUM = "a".repeat(64);
    private static final String RIVER_GEOMETRY_CHECKSUM = "b".repeat(64);

    @Test
    void generatesBoundedLowReliefFanSandbarsAndMarineReachableConservedBranches() {
        Fixture fixture = fixture(129, 193, false, true, false);
        DeltaPlanV2 plan = fixture.deltaPlan();
        DeltaGeneratorV2.DeltaMetrics metrics = new DeltaGeneratorV2(plan).evaluate(index -> false, () -> false);

        assertEquals(6, metrics.activeDistributaryCount());
        assertTrue(metrics.allActiveMouthsMarineReachable());
        assertTrue(metrics.flowConserved());
        assertEquals((long) plan.selectedFanReliefBlocks() * TerrainIntentV2.FIXED_SCALE,
                metrics.fanReliefMillionths());
        assertTrue(metrics.fanCells() > metrics.channelCells());
        assertTrue(metrics.channelCells() > 0L);
        assertTrue(metrics.sandbarCells() > 0L);
        assertEquals(plan.estimatedRasterWorkUnits(), metrics.estimatedRasterWorkUnits());
        assertEquals(TerrainIntentV2.FIXED_SCALE,
                plan.branches().stream().mapToInt(DeltaPlanV2.DistributaryBranch::dischargeShareMillionths).sum());
    }

    @Test
    void wholeAndTileFieldsMatchAcrossSeamsOrderThreadsCandidateOrderLocaleAndTimezone() throws Exception {
        Fixture fixture = fixture(257, 193, false, true, false);
        DeltaGeneratorV2 generator = new DeltaGeneratorV2(fixture.deltaPlan());
        IntPredicate noConflict = index -> false;
        Map<DeltaGeneratorV2.DeltaField, String> direct =
                generator.fieldChecksums(noConflict, () -> false);
        Map<Long, DeltaWindowV2> forward = renderTiles(generator, 64, false, 1, noConflict);
        Map<Long, DeltaWindowV2> reverse = renderTiles(generator, 64, true, 1, noConflict);
        Map<Long, DeltaWindowV2> parallel = renderTiles(generator, 64, true, 4, noConflict);

        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(forward, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(reverse, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(parallel, 64), () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Fixture reordered = fixture(257, 193, true, true, false);
            assertEquals(fixture.deltaPlan(), reordered.deltaPlan());
            assertEquals(direct,
                    new DeltaGeneratorV2(reordered.deltaPlan()).fieldChecksums(noConflict, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void rejectsDeadBranchLoopLandlockedMouthAndFlowCorruption() {
        DeltaPlanV2 plan = fixture(129, 193, false, true, false).deltaPlan();
        DeltaPlanV2.DistributaryBranch first = plan.branches().getFirst();

        DeltaGenerationException dead = assertThrows(DeltaGenerationException.class,
                () -> DeltaGraphValidatorV2.requireValid(plan, replaceFirst(plan,
                        copyBranch(first, "orphan-node", first.toNodeId(), first.path(),
                                first.dischargeShareMillionths()))));
        assertEquals("v2.delta-dead-branch", dead.ruleId());

        DeltaGenerationException loop = assertThrows(DeltaGenerationException.class,
                () -> DeltaGraphValidatorV2.requireValid(plan, replaceFirst(plan,
                        copyBranch(first, first.fromNodeId(), plan.apexNodeId(), first.path(),
                                first.dischargeShareMillionths()))));
        assertEquals("v2.delta-loop", loop.ruleId());

        List<DeltaPlanV2.FanPoint> landlockedPath = new ArrayList<>(first.path());
        DeltaPlanV2.FanPoint mouth = landlockedPath.getLast();
        landlockedPath.set(landlockedPath.size() - 1,
                new DeltaPlanV2.FanPoint(mouth.xMillionths(), mouth.zMillionths() - TerrainIntentV2.FIXED_SCALE));
        DeltaGenerationException landlocked = assertThrows(DeltaGenerationException.class,
                () -> DeltaGraphValidatorV2.requireValid(plan, replaceFirst(plan,
                        copyBranch(first, first.fromNodeId(), first.toNodeId(), landlockedPath,
                                first.dischargeShareMillionths()))));
        assertEquals("v2.delta-landlocked-mouth", landlocked.ruleId());

        DeltaGenerationException flow = assertThrows(DeltaGenerationException.class,
                () -> DeltaGraphValidatorV2.requireValid(plan, replaceFirst(plan,
                        copyBranch(first, first.fromNodeId(), first.toNodeId(), first.path(),
                                first.dischargeShareMillionths() + 1))));
        assertEquals("v2.delta-flow-conservation", flow.ruleId());
    }

    @Test
    void rejectsHardOutletConflictAndLandlockedFanBeforeCanonicalPlan() {
        Fixture hardLand = fixture(129, 193, false, false, false);
        DeltaGenerationException outlet = assertThrows(DeltaGenerationException.class,
                () -> compileDelta(hardLand.intent(), hardLand.riverPlan(), 129, 193));
        assertEquals("v2.delta-hard-outlet-conflict", outlet.ruleId());

        Fixture landlocked = fixture(129, 193, false, true, true);
        DeltaGenerationException mouth = assertThrows(DeltaGenerationException.class,
                () -> compileDelta(landlocked.intent(), landlocked.riverPlan(), 129, 193));
        assertEquals("v2.delta-landlocked-mouth", mouth.ruleId());

        DeltaPlanV2 plan = fixture(129, 193, false, true, false).deltaPlan();
        DeltaGeneratorV2 generator = new DeltaGeneratorV2(plan);
        int apexX = Math.toIntExact(DeltaFixedMathV2.roundDivide(
                plan.apex().xMillionths(), TerrainIntentV2.FIXED_SCALE));
        int apexZ = Math.toIntExact(DeltaFixedMathV2.roundDivide(
                plan.apex().zMillionths(), TerrainIntentV2.FIXED_SCALE));
        int blockedIndex = apexZ * plan.width() + apexX;
        DeltaGenerationException rasterConflict = assertThrows(DeltaGenerationException.class,
                () -> generator.sampleAt(apexX, apexZ, index -> index == blockedIndex));
        assertEquals("v2.delta-hard-outlet-conflict", rasterConflict.ruleId());
    }

    @Test
    void enforcesRasterMemoryCpuAdmissionAndCooperativeCancellation() {
        assertEquals(-1L, DeltaFixedMathV2.roundDivide(-2L, 2L));
        assertEquals(-2L, DeltaFixedMathV2.roundDivide(-3L, 2L));
        Fixture maximum = fixture(1_000, 1_000, false, true, false);
        DeltaGeneratorV2 generator = new DeltaGeneratorV2(maximum.deltaPlan());
        assertTrue(maximum.deltaPlan().estimatedRasterWorkUnits()
                <= DeltaPlanV2.MAXIMUM_RASTER_WORK_UNITS);
        assertTrue(DeltaGeneratorV2.estimateWindowRetainedBytes(1_000, 1_000)
                > DeltaGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        DeltaGenerationException memory = assertThrows(DeltaGenerationException.class,
                () -> generator.renderWindow(0, 0, 1_000, 1_000, 0, index -> false, () -> false));
        assertEquals("v2.delta-budget", memory.ruleId());
        DeltaGenerationException invalidWindow = assertThrows(DeltaGenerationException.class,
                () -> generator.renderWindow(0, 0, 0, 1, 0, index -> false, () -> false));
        assertEquals("v2.delta-window", invalidWindow.ruleId());

        Fixture small = fixture(129, 193, false, true, false);
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
                priorCpu + small.deltaPlan().estimatedRasterWorkUnits() - 1L,
                defaults.maximumArtifactBytes());
        DiagnosticCompileRequestV2 request = new DiagnosticCompileRequestV2(
                small.intent().intentId(), new GenerationBounds(129, 193, -64, 255, 63), 64, 827413L,
                "c".repeat(64), cpuTooSmall);
        DiagnosticCompilationException cpu = assertThrows(DiagnosticCompilationException.class,
                () -> new DiagnosticBlueprintCompilerV2().compile(request, small.intent()));
        assertEquals("v2.delta-budget", cpu.ruleId());

        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> new DeltaGeneratorV2(small.deltaPlan()).renderWindow(
                0, 0, 64, 64, 0, index -> false, () -> checks.incrementAndGet() > 1));
        assertTrue(checks.get() > 1);
    }

    private static Fixture fixture(
            int width,
            int length,
            boolean reverseRing,
            boolean hardSea,
            boolean landlocked
    ) {
        TerrainIntentV2.Feature river = new TerrainIntentV2.Feature(
                "delta-trunk",
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                new TerrainIntentV2.SplineGeometry(List.of(
                        new TerrainIntentV2.Point2(500_000, 50_000),
                        new TerrainIntentV2.Point2(500_000, 300_000),
                        new TerrainIntentV2.Point2(500_000, 580_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.MeanderingRiverParameters(
                        new TerrainIntentV2.IntRange(8, 12),
                        TerrainIntentV2.DischargeClass.MEDIUM,
                        1_000,
                        TerrainIntentV2.RiverVariant.RIVER),
                0,
                TerrainIntentV2.Provenance.confirmedManual("delta-test"));
        List<TerrainIntentV2.Point2> ring = new ArrayList<>(List.of(
                new TerrainIntentV2.Point2(220_000, 550_000),
                new TerrainIntentV2.Point2(780_000, 550_000),
                new TerrainIntentV2.Point2(landlocked ? 940_000 : 1_000_000, landlocked ? 950_000 : 1_000_000),
                new TerrainIntentV2.Point2(landlocked ? 60_000 : 0, landlocked ? 950_000 : 1_000_000),
                new TerrainIntentV2.Point2(220_000, 550_000)));
        if (reverseRing) {
            List<TerrainIntentV2.Point2> open = new ArrayList<>(ring.subList(0, ring.size() - 1));
            Collections.reverse(open);
            open.add(open.getFirst());
            ring = open;
        }
        TerrainIntentV2.Feature delta = new TerrainIntentV2.Feature(
                "main-delta",
                TerrainIntentV2.FeatureKind.DELTA,
                new TerrainIntentV2.PolygonGeometry(List.of(ring)),
                new TerrainIntentV2.DeltaParameters(
                        new TerrainIntentV2.IntRange(4, 8),
                        new TerrainIntentV2.FixedRange(10_000_000L, 170_000_000L),
                        new TerrainIntentV2.IntRange(2, 10),
                        new TerrainIntentV2.IntRange(3, 7),
                        new TerrainIntentV2.IntRange(2, 6),
                        TerrainIntentV2.DeltaFanProfile.APEX_TO_SEA_LINEAR),
                0,
                TerrainIntentV2.Provenance.confirmedManual("delta-test"));
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "delta-fixture",
                "bounded delta fixture",
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                List.of(delta, river),
                List.of(
                        new TerrainIntentV2.Relation(
                                "trunk-drains-delta", TerrainIntentV2.RelationKind.DRAINS_TO,
                                "feature:delta-trunk", "feature:main-delta", TerrainIntentV2.Strength.HARD),
                        new TerrainIntentV2.Relation(
                                "delta-empties-south", TerrainIntentV2.RelationKind.EMPTIES_INTO,
                                "feature:main-delta", "boundary:SOUTH", TerrainIntentV2.Strength.HARD)),
                List.of(
                        new TerrainIntentV2.MetricRangeConstraint(
                                "delta-branch-count", TerrainIntentV2.Strength.HARD, "feature:main-delta",
                                "ACTIVE_DISTRIBUTARY_COUNT",
                                new TerrainIntentV2.FixedRange(4_000_000L, 8_000_000L),
                                0L, 0),
                        new TerrainIntentV2.MetricRangeConstraint(
                                "delta-low-relief", TerrainIntentV2.Strength.HARD, "feature:main-delta",
                                "ELEVATION_RANGE_BLOCKS",
                                new TerrainIntentV2.FixedRange(2_000_000L, 10_000_000L),
                                0L, 0),
                        new TerrainIntentV2.EdgeClassificationConstraint(
                                "south-is-sea", TerrainIntentV2.Strength.HARD, "world",
                                TerrainIntentV2.Edge.SOUTH,
                                hardSea ? TerrainIntentV2.EdgeClassification.SEA
                                        : TerrainIntentV2.EdgeClassification.LAND,
                                100_000, 0)),
                new TerrainIntentV2.EnvironmentDescriptor(
                        "ALLUVIAL_SEDIMENT", "TEMPERATE_HUMID", "RIVER_CORRIDOR"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("delta-test"));
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(width, length, -64, 255, 63);
        MeanderingRiverPlanV2 riverPlan = new MeanderingRiverPlanCompilerV2().compile(
                river, bounds, RIVER_GEOMETRY_CHECKSUM);
        DeltaPlanV2 deltaPlan = hardSea && !landlocked
                ? compileDelta(intent, riverPlan, width, length)
                : null;
        return new Fixture(intent, riverPlan, deltaPlan);
    }

    private static DeltaPlanV2 compileDelta(
            TerrainIntentV2 intent,
            MeanderingRiverPlanV2 riverPlan,
            int width,
            int length
    ) {
        TerrainIntentV2.Feature delta = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.DELTA)
                .findFirst().orElseThrow();
        return new DeltaPlanCompilerV2().compile(
                delta, intent, List.of(riverPlan),
                new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                GEOMETRY_CHECKSUM);
    }

    private static List<DeltaPlanV2.DistributaryBranch> replaceFirst(
            DeltaPlanV2 plan,
            DeltaPlanV2.DistributaryBranch replacement
    ) {
        List<DeltaPlanV2.DistributaryBranch> result = new ArrayList<>(plan.branches());
        result.set(0, replacement);
        return List.copyOf(result);
    }

    private static DeltaPlanV2.DistributaryBranch copyBranch(
            DeltaPlanV2.DistributaryBranch source,
            String fromNodeId,
            String toNodeId,
            List<DeltaPlanV2.FanPoint> path,
            int dischargeShare
    ) {
        return new DeltaPlanV2.DistributaryBranch(
                source.branchId(), fromNodeId, toNodeId, path,
                source.apexBedYMillionths(), source.mouthBedYMillionths(),
                source.halfWidthBlocks(), dischargeShare);
    }

    private static Map<Long, DeltaWindowV2> renderTiles(
            DeltaGeneratorV2 generator,
            int tileSize,
            boolean reverse,
            int threads,
            IntPredicate hardLandConflict
    ) throws Exception {
        List<int[]> tiles = new ArrayList<>();
        for (int z = 0; z < generator.length(); z += tileSize) {
            for (int x = 0; x < generator.width(); x += tileSize) {
                tiles.add(new int[] {x, z,
                        Math.min(tileSize, generator.width() - x),
                        Math.min(tileSize, generator.length() - z)});
            }
        }
        if (reverse) Collections.reverse(tiles);
        ConcurrentHashMap<Long, DeltaWindowV2> result = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int[] tile : tiles) {
                futures.add(executor.submit(() -> result.put(tileKey(tile[0], tile[1]),
                        generator.renderWindow(
                                tile[0], tile[1], tile[2], tile[3], 0,
                                hardLandConflict, () -> false))));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static DeltaGeneratorV2.CellSource tiledSource(Map<Long, DeltaWindowV2> tiles, int tileSize) {
        return (x, z) -> {
            int tileX = (x / tileSize) * tileSize;
            int tileZ = (z / tileSize) * tileSize;
            DeltaWindowV2 window = tiles.get(tileKey(tileX, tileZ));
            return new DeltaGeneratorV2.DeltaSample(
                    window.rawValueAt(DeltaGeneratorV2.DeltaField.FAN_MASK, x, z),
                    window.rawValueAt(DeltaGeneratorV2.DeltaField.CHANNEL_MASK, x, z),
                    window.rawValueAt(DeltaGeneratorV2.DeltaField.BRANCH_INDEX, x, z),
                    window.rawValueAt(DeltaGeneratorV2.DeltaField.FAN_SURFACE, x, z),
                    window.rawValueAt(DeltaGeneratorV2.DeltaField.SANDBAR_MASK, x, z),
                    window.rawValueAt(DeltaGeneratorV2.DeltaField.SHALLOW_SEA_DEPTH, x, z),
                    window.rawValueAt(DeltaGeneratorV2.DeltaField.DISCHARGE_SHARE, x, z));
        };
    }

    private static long tileKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffff_ffffL);
    }

    private record Fixture(
            TerrainIntentV2 intent,
            MeanderingRiverPlanV2 riverPlan,
            DeltaPlanV2 deltaPlan
    ) {
    }
}
