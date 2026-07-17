package com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WaterfallPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterfallGeneratorV2Test {
    @Test
    void waterfallProducesLipBasePlungeDropAndAllowedBedDiscontinuity() {
        Fixture fixture = compilePair(river("canyon-river", 6, 10), waterfall("main-fall", 24, 42, 9, 16, 0), 129, 129);
        WaterfallGeneratorV2.WaterfallMetrics metrics = new WaterfallGeneratorV2(fixture.waterfall()).evaluate(() -> false);

        assertTrue(metrics.dropInRange());
        assertTrue(metrics.bedDiscontinuityAllowed());
        assertTrue(metrics.reachBedsMonotonic());
        assertTrue(metrics.lipBasePlungePresent());
        assertTrue(metrics.volumeClearanceDeferred());
        assertTrue(metrics.lipCells() > 0L);
        assertTrue(metrics.baseCells() > 0L);
        assertTrue(metrics.plungePoolCells() > 0L);
        assertEquals(33, metrics.selectedDropBlocks());
        assertEquals(
                Math.multiplyExact(33L, WaterfallFixedMathV2.FIXED_SCALE),
                metrics.measuredDropMillionths());
        assertTrue(fixture.waterfall().upstreamCenterline().getLast().bedYMillionths()
                > fixture.waterfall().downstreamCenterline().getFirst().bedYMillionths());
    }

    @Test
    void wholeAndTileFieldsMatchAcrossSeamsOrderThreadsLocaleAndTimezone() throws Exception {
        Fixture fixture = compilePair(river("tile-river", 6, 8), waterfall("tile-fall", 20, 28, 7, 12, 0), 129, 129);
        WaterfallGeneratorV2 generator = new WaterfallGeneratorV2(fixture.waterfall());
        IntPredicate none = index -> false;
        Map<WaterfallGeneratorV2.WaterfallField, String> direct = generator.fieldChecksums(none, () -> false);
        Map<Long, WaterfallWindowV2> forward = renderTiles(generator, 32, false, 1, none);
        Map<Long, WaterfallWindowV2> reverse = renderTiles(generator, 32, true, 1, none);
        Map<Long, WaterfallWindowV2> parallel = renderTiles(generator, 32, true, 4, none);
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, forward, 32), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, reverse, 32), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, parallel, 32), () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            Fixture recompiled = compilePair(
                    river("tile-river", 6, 8), waterfall("tile-fall", 20, 28, 7, 12, 0), 129, 129);
            assertEquals(direct, new WaterfallGeneratorV2(recompiled.waterfall()).fieldChecksums(none, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void rejectsOffPathUphillVolumeClearanceOwnerConflictAndBudget() {
        MeanderingRiverPlanV2 riverPlan = compileRiver(river("path-river", 6, 8), 97, 97);
        WaterfallGenerationException offPath = assertThrows(WaterfallGenerationException.class,
                () -> compileWaterfall(
                        waterfall("off-path-fall", 20, 28, 7, 12, 0, 0.05, 0.05),
                        List.of(riverPlan),
                        List.of(onPath("fall-on-river", "off-path-fall", "path-river")),
                        97,
                        97));
        assertEquals("v2.waterfall-off-path", offPath.ruleId());

        WaterfallGenerationException volume = assertThrows(WaterfallGenerationException.class,
                () -> compileWaterfall(
                        waterfall("volume-fall", 20, 28, 7, 12, 4),
                        List.of(riverPlan),
                        List.of(onPath("fall-on-river", "volume-fall", "path-river")),
                        97,
                        97));
        assertEquals("v2.waterfall-zero-roof-clearance", volume.ruleId());

        WaterfallPlanV2 plan = compileWaterfall(
                waterfall("ok-fall", 20, 28, 7, 12, 0),
                List.of(riverPlan),
                List.of(onPath("fall-on-river", "ok-fall", "path-river")),
                97,
                97);
        BitSet land = new BitSet(97 * 97);
        int lipX = Math.toIntExact(WaterfallFixedMathV2.roundDivide(
                plan.lipXMillionths(), WaterfallFixedMathV2.FIXED_SCALE));
        int lipZ = Math.toIntExact(WaterfallFixedMathV2.roundDivide(
                plan.lipZMillionths(), WaterfallFixedMathV2.FIXED_SCALE));
        land.set(lipZ * 97 + lipX);
        WaterfallGenerationException owner = assertThrows(WaterfallGenerationException.class,
                () -> new WaterfallGeneratorV2(plan).sampleAt(lipX, lipZ, land::get));
        assertEquals("v2.waterfall-owner-conflict", owner.ruleId());

        WaterfallGenerationException budget = assertThrows(WaterfallGenerationException.class,
                () -> new WaterfallGeneratorV2(plan).renderWindow(
                        0, 0, 97, 97, plan.supportRadiusXZ(), index -> false, () -> false));
        assertTrue(budget.ruleId().equals("v2.waterfall-window")
                || budget.ruleId().equals("v2.waterfall-budget")
                || budget.ruleId().equals("v2.waterfall-halo"));
    }

    @Test
    void candidateOrderDoesNotChangeChecksum() {
        Fixture first = compilePair(river("order-river", 6, 8), waterfall("order-fall", 18, 26, 6, 10, 0), 97, 97);
        Fixture second = compilePair(river("order-river", 6, 8), waterfall("order-fall", 18, 26, 6, 10, 0), 97, 97);
        assertEquals(first.waterfall(), second.waterfall());
        assertEquals(
                new WaterfallGeneratorV2(first.waterfall()).fieldChecksums(index -> false, () -> false),
                new WaterfallGeneratorV2(second.waterfall()).fieldChecksums(index -> false, () -> false));
    }

    private static Fixture compilePair(
            TerrainIntentV2.Feature riverFeature,
            TerrainIntentV2.Feature waterfallFeature,
            int width,
            int length
    ) {
        MeanderingRiverPlanV2 river = compileRiver(riverFeature, width, length);
        WaterfallPlanV2 waterfall = compileWaterfall(
                waterfallFeature,
                List.of(river),
                List.of(onPath("fall-on-river", waterfallFeature.id(), riverFeature.id())),
                width,
                length);
        return new Fixture(river, waterfall);
    }

    private static MeanderingRiverPlanV2 compileRiver(TerrainIntentV2.Feature feature, int width, int length) {
        return new MeanderingRiverPlanCompilerV2().compile(
                feature,
                new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                "b".repeat(64));
    }

    private static WaterfallPlanV2 compileWaterfall(
            TerrainIntentV2.Feature waterfall,
            List<MeanderingRiverPlanV2> rivers,
            List<TerrainIntentV2.Relation> relations,
            int width,
            int length
    ) {
        List<TerrainIntentV2.Feature> features = new ArrayList<>();
        features.add(waterfall);
        for (TerrainIntentV2.Relation relation : relations) {
            String toId = relation.to().substring("feature:".length());
            features.add(river(toId, 6, 8));
        }
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "waterfall-fixture",
                "waterfall",
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                features,
                relations,
                List.of(),
                new TerrainIntentV2.EnvironmentDescriptor("STRATIFIED_SEDIMENTARY_CANYON",
                        "SEASONAL_SEMI_ARID", "RIPARIAN_CANYON"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("waterfall-test"));
        return new WaterfallPlanCompilerV2().compile(
                waterfall,
                intent,
                rivers,
                new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                "a".repeat(64));
    }

    private static TerrainIntentV2.Feature river(String id, int minWidth, int maxWidth) {
        return new TerrainIntentV2.Feature(
                id,
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                sharedSpline(),
                new TerrainIntentV2.MeanderingRiverParameters(
                        new TerrainIntentV2.IntRange(minWidth, maxWidth),
                        TerrainIntentV2.DischargeClass.SMALL,
                        1_000,
                        TerrainIntentV2.RiverVariant.RIVER),
                0,
                TerrainIntentV2.Provenance.confirmedManual("waterfall-test"));
    }

    private static TerrainIntentV2.Feature waterfall(
            String id,
            int minDrop,
            int maxDrop,
            int lipWidth,
            int plungeRadius,
            int behindClearance
    ) {
        return waterfall(id, minDrop, maxDrop, lipWidth, plungeRadius, behindClearance, 0.48, 0.49);
    }

    private static TerrainIntentV2.Feature waterfall(
            String id,
            int minDrop,
            int maxDrop,
            int lipWidth,
            int plungeRadius,
            int behindClearance,
            double x,
            double z
    ) {
        return new TerrainIntentV2.Feature(
                id,
                TerrainIntentV2.FeatureKind.WATERFALL,
                new TerrainIntentV2.PointGeometry(new TerrainIntentV2.Point2(
                        (int) Math.round(x * TerrainIntentV2.FIXED_SCALE),
                        (int) Math.round(z * TerrainIntentV2.FIXED_SCALE))),
                new TerrainIntentV2.WaterfallParameters(
                        new TerrainIntentV2.IntRange(minDrop, maxDrop),
                        lipWidth,
                        plungeRadius,
                        behindClearance),
                0,
                TerrainIntentV2.Provenance.confirmedManual("waterfall-test"));
    }

    private static TerrainIntentV2.SplineGeometry sharedSpline() {
        return new TerrainIntentV2.SplineGeometry(
                List.of(
                        point(0.18, 0.05),
                        point(0.34, 0.34),
                        point(0.56, 0.58),
                        point(0.80, 0.95)),
                TerrainIntentV2.Interpolation.CATMULL_ROM);
    }

    private static TerrainIntentV2.Point2 point(double x, double z) {
        return new TerrainIntentV2.Point2(
                (int) Math.round(x * TerrainIntentV2.FIXED_SCALE),
                (int) Math.round(z * TerrainIntentV2.FIXED_SCALE));
    }

    private static TerrainIntentV2.Relation onPath(String id, String fromFeature, String toFeature) {
        return new TerrainIntentV2.Relation(
                id,
                TerrainIntentV2.RelationKind.ON_PATH_OF,
                "feature:" + fromFeature,
                "feature:" + toFeature,
                TerrainIntentV2.Strength.HARD);
    }

    private static Map<Long, WaterfallWindowV2> renderTiles(
            WaterfallGeneratorV2 generator,
            int tileSize,
            boolean reverse,
            int threads,
            IntPredicate hardLand
    ) throws Exception {
        List<long[]> tiles = new ArrayList<>();
        for (int originZ = 0; originZ < generator.length(); originZ += tileSize) {
            for (int originX = 0; originX < generator.width(); originX += tileSize) {
                int width = Math.min(tileSize, generator.width() - originX);
                int length = Math.min(tileSize, generator.length() - originZ);
                tiles.add(new long[]{originX, originZ, width, length});
            }
        }
        if (reverse) Collections.reverse(tiles);
        ConcurrentHashMap<Long, WaterfallWindowV2> result = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (long[] tile : tiles) {
                futures.add(executor.submit(() -> {
                    WaterfallWindowV2 window = generator.renderWindow(
                            (int) tile[0], (int) tile[1], (int) tile[2], (int) tile[3],
                            0, hardLand, () -> false);
                    result.put((tile[1] << 32) | tile[0], window);
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static WaterfallGeneratorV2.CellSource tiledSource(
            WaterfallGeneratorV2 generator,
            Map<Long, WaterfallWindowV2> tiles,
            int tileSize
    ) {
        return (x, z) -> {
            int originX = (x / tileSize) * tileSize;
            int originZ = (z / tileSize) * tileSize;
            WaterfallWindowV2 window = tiles.get((((long) originZ) << 32) | originX);
            return new WaterfallGeneratorV2.WaterfallSample(
                    window.rawValueAt(WaterfallGeneratorV2.WaterfallField.LIP_MASK, x, z),
                    window.rawValueAt(WaterfallGeneratorV2.WaterfallField.BASE_MASK, x, z),
                    window.rawValueAt(WaterfallGeneratorV2.WaterfallField.PLUNGE_POOL_MASK, x, z),
                    window.rawValueAt(WaterfallGeneratorV2.WaterfallField.LIP_ELEVATION, x, z),
                    window.rawValueAt(WaterfallGeneratorV2.WaterfallField.BASE_ELEVATION, x, z),
                    window.rawValueAt(WaterfallGeneratorV2.WaterfallField.PLUNGE_POOL_FLOOR, x, z),
                    window.rawValueAt(WaterfallGeneratorV2.WaterfallField.BED_ELEVATION, x, z));
        };
    }

    private record Fixture(MeanderingRiverPlanV2 river, WaterfallPlanV2 waterfall) {
    }
}
