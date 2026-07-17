package com.github.nankotsu029.landformcraft.generator.v2.hydrology.river;

import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
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

class MeanderingRiverGeneratorV2Test {
    @Test
    void generatesReachableMonotonicMeanderBoundedChannelBankAndFloodplain() {
        MeanderingRiverPlanV2 plan = compile(
                feature("main-river", TerrainIntentV2.RiverVariant.MEANDERING_RIVER,
                        8, 16, TerrainIntentV2.DischargeClass.MEDIUM, 1_000),
                129, 193);
        MeanderingRiverGeneratorV2 generator = new MeanderingRiverGeneratorV2(plan);
        MeanderingRiverGeneratorV2.RiverMetrics metrics = generator.evaluate(() -> false);

        assertTrue(metrics.sourceToMouthReachable());
        assertTrue(metrics.monotonicBed());
        assertTrue(metrics.meanderWithinCorridor());
        assertTrue(metrics.channelCells() > 0);
        assertTrue(metrics.bankCells() > 0);
        assertTrue(metrics.floodplainCells() > 0);
        assertTrue(metrics.corridorCells() >= metrics.channelCells());
        assertEquals(2, metrics.confluenceDischargeIndex());
        assertTrue(metrics.meanderAmplitudeBlocks() > 0);
        assertTrue(metrics.bedDropMillionths() >= plan.minimumBedSlopeMillionths());

        MeanderingRiverPlanV2.CenterlineSample mid = plan.centerline().get(plan.centerline().size() / 2);
        int midX = Math.toIntExact(mid.xMillionths() / RiverFixedMathV2.FIXED_SCALE);
        int midZ = Math.toIntExact(mid.zMillionths() / RiverFixedMathV2.FIXED_SCALE);
        assertEquals(1, generator.sampleAt(midX, midZ, index -> false).channelMask());
    }

    @Test
    void straightRiverVariantKeepsZeroMeanderAndSameReachContract() {
        MeanderingRiverPlanV2 plan = compile(
                feature("straight-river", TerrainIntentV2.RiverVariant.RIVER,
                        6, 10, TerrainIntentV2.DischargeClass.SMALL, 2_000),
                97, 129);
        assertEquals(0, plan.meanderAmplitudeBlocks());
        assertEquals(0, plan.meanderWavelengthBlocks());
        MeanderingRiverGeneratorV2.RiverMetrics metrics = new MeanderingRiverGeneratorV2(plan).evaluate(() -> false);
        assertTrue(metrics.sourceToMouthReachable());
        assertTrue(metrics.monotonicBed());
        assertTrue(metrics.meanderWithinCorridor());
    }

    @Test
    void wholeAndTileFieldsMatchAcrossSeamsOrderThreadsLocaleAndTimezone() throws Exception {
        MeanderingRiverPlanV2 plan = compile(
                feature("tile-river", TerrainIntentV2.RiverVariant.MEANDERING_RIVER,
                        6, 12, TerrainIntentV2.DischargeClass.MEDIUM, 1_000),
                257, 193);
        MeanderingRiverGeneratorV2 generator = new MeanderingRiverGeneratorV2(plan);
        IntPredicate none = index -> false;
        Map<MeanderingRiverGeneratorV2.RiverField, String> direct =
                generator.fieldChecksums(none, () -> false);
        Map<Long, RiverWindowV2> forward = renderTiles(generator, 64, false, 1, none);
        Map<Long, RiverWindowV2> reverse = renderTiles(generator, 64, true, 1, none);
        Map<Long, RiverWindowV2> parallel = renderTiles(generator, 64, true, 4, none);
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, forward, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, reverse, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, parallel, 64), () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            MeanderingRiverPlanV2 recompiled = compile(
                    feature("tile-river", TerrainIntentV2.RiverVariant.MEANDERING_RIVER,
                            6, 12, TerrainIntentV2.DischargeClass.MEDIUM, 1_000),
                    257, 193);
            assertEquals(plan, recompiled);
            assertEquals(direct, new MeanderingRiverGeneratorV2(recompiled).fieldChecksums(none, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void rejectsReverseGradientIsolatedReachWidthConflictHardRouteAndBudget() {
        TerrainIntentV2.Feature reverse = feature(
                "steep-fail", TerrainIntentV2.RiverVariant.RIVER, 4, 8,
                TerrainIntentV2.DischargeClass.SMALL, 900_000);
        RiverGenerationException reverseFailure = assertThrows(RiverGenerationException.class,
                () -> compile(reverse, 97, 193));
        assertEquals("v2.river-vertical-bounds", reverseFailure.ruleId());

        TerrainIntentV2.Feature isolated = new TerrainIntentV2.Feature(
                "isolated",
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                new TerrainIntentV2.SplineGeometry(List.of(
                        new TerrainIntentV2.Point2(500_000, 500_000),
                        new TerrainIntentV2.Point2(500_001, 500_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.MeanderingRiverParameters(
                        new TerrainIntentV2.IntRange(4, 8),
                        TerrainIntentV2.DischargeClass.SMALL,
                        1_000,
                        TerrainIntentV2.RiverVariant.RIVER),
                0,
                TerrainIntentV2.Provenance.confirmedManual("river-test"));
        RiverGenerationException isolatedFailure = assertThrows(RiverGenerationException.class,
                () -> compile(isolated, 65, 65));
        assertEquals("v2.river-isolated-reach", isolatedFailure.ruleId());

        TerrainIntentV2.Feature wide = feature(
                "wide-river", TerrainIntentV2.RiverVariant.RIVER, 40, 64,
                TerrainIntentV2.DischargeClass.LARGE, 1_000);
        RiverGenerationException widthFailure = assertThrows(RiverGenerationException.class,
                () -> compile(wide, 48, 128));
        assertEquals("v2.river-width-conflict", widthFailure.ruleId());

        MeanderingRiverPlanV2 plan = compile(
                feature("blocked-river", TerrainIntentV2.RiverVariant.RIVER, 4, 8,
                        TerrainIntentV2.DischargeClass.SMALL, 1_000),
                97, 129);
        MeanderingRiverGeneratorV2 generator = new MeanderingRiverGeneratorV2(plan);
        MeanderingRiverPlanV2.CenterlineSample mid = plan.centerline().get(plan.centerline().size() / 2);
        int midX = Math.toIntExact(mid.xMillionths() / RiverFixedMathV2.FIXED_SCALE);
        int midZ = Math.toIntExact(mid.zMillionths() / RiverFixedMathV2.FIXED_SCALE);
        BitSet blocked = new BitSet();
        blocked.set(midZ * plan.width() + midX);
        RiverGenerationException hardConflict = assertThrows(RiverGenerationException.class,
                () -> generator.sampleAt(midX, midZ, blocked::get));
        assertEquals("v2.river-hard-route-conflict", hardConflict.ruleId());

        RiverGenerationException budget = assertThrows(RiverGenerationException.class,
                () -> generator.renderWindow(0, 0, plan.width(), plan.length(), plan.supportRadiusXZ(),
                        index -> false, () -> false));
        assertTrue(budget.ruleId().equals("v2.river-window") || budget.ruleId().equals("v2.river-budget")
                || budget.ruleId().equals("v2.river-halo"));
    }

    @Test
    void confluenceDischargeHookRejectsDownstreamSmallerThanUpstream() {
        MeanderingRiverPlanV2 upstreamA = compile(
                feature("upstream-a", TerrainIntentV2.RiverVariant.RIVER, 4, 6,
                        TerrainIntentV2.DischargeClass.MEDIUM, 1_000),
                65, 97);
        MeanderingRiverPlanV2 upstreamB = compile(
                feature("upstream-b", TerrainIntentV2.RiverVariant.RIVER, 4, 6,
                        TerrainIntentV2.DischargeClass.MEDIUM, 1_000),
                65, 97);
        MeanderingRiverPlanV2 downstream = compile(
                feature("downstream", TerrainIntentV2.RiverVariant.RIVER, 4, 6,
                        TerrainIntentV2.DischargeClass.SMALL, 1_000),
                65, 97);
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "confluence-fixture",
                "confluence",
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                List.of(
                        feature("upstream-a", TerrainIntentV2.RiverVariant.RIVER, 4, 6,
                                TerrainIntentV2.DischargeClass.MEDIUM, 1_000),
                        feature("upstream-b", TerrainIntentV2.RiverVariant.RIVER, 4, 6,
                                TerrainIntentV2.DischargeClass.MEDIUM, 1_000),
                        feature("downstream", TerrainIntentV2.RiverVariant.RIVER, 4, 6,
                                TerrainIntentV2.DischargeClass.SMALL, 1_000)),
                List.of(
                        new TerrainIntentV2.Relation(
                                "a-up", TerrainIntentV2.RelationKind.UPSTREAM_OF,
                                "feature:upstream-a", "feature:downstream", TerrainIntentV2.Strength.HARD),
                        new TerrainIntentV2.Relation(
                                "b-up", TerrainIntentV2.RelationKind.UPSTREAM_OF,
                                "feature:upstream-b", "feature:downstream", TerrainIntentV2.Strength.HARD)),
                List.of(),
                new TerrainIntentV2.EnvironmentDescriptor("ALLUVIAL_SEDIMENT", "TEMPERATE_HUMID", "RIVER_CORRIDOR"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("confluence"));
        RiverGenerationException failure = assertThrows(RiverGenerationException.class,
                () -> RiverConfluenceValidatorV2.requireConfluenceDischargeConsistent(
                        intent, List.of(upstreamA, upstreamB, downstream)));
        assertEquals("v2.river-confluence-flow", failure.ruleId());
    }

    @Test
    void candidateOrderDoesNotChangeChecksum() {
        TerrainIntentV2.Feature feature = feature(
                "ordered-river", TerrainIntentV2.RiverVariant.MEANDERING_RIVER, 6, 10,
                TerrainIntentV2.DischargeClass.MEDIUM, 1_000);
        MeanderingRiverPlanV2 first = compile(feature, 129, 129);
        MeanderingRiverPlanV2 second = compile(feature, 129, 129);
        assertEquals(first, second);
        assertEquals(
                new MeanderingRiverGeneratorV2(first).fieldChecksums(index -> false, () -> false),
                new MeanderingRiverGeneratorV2(second).fieldChecksums(index -> false, () -> false));
    }

    private static MeanderingRiverPlanV2 compile(TerrainIntentV2.Feature feature, int width, int length) {
        return new MeanderingRiverPlanCompilerV2().compile(
                feature,
                new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                "a".repeat(64));
    }

    private static TerrainIntentV2.Feature feature(
            String id,
            TerrainIntentV2.RiverVariant variant,
            int minWidth,
            int maxWidth,
            TerrainIntentV2.DischargeClass dischargeClass,
            long slopeMillionths
    ) {
        return new TerrainIntentV2.Feature(
                id,
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                new TerrainIntentV2.SplineGeometry(List.of(
                        new TerrainIntentV2.Point2(500_000, 50_000),
                        new TerrainIntentV2.Point2(420_000, 350_000),
                        new TerrainIntentV2.Point2(580_000, 650_000),
                        new TerrainIntentV2.Point2(500_000, 920_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.MeanderingRiverParameters(
                        new TerrainIntentV2.IntRange(minWidth, maxWidth),
                        dischargeClass,
                        slopeMillionths,
                        variant),
                0,
                TerrainIntentV2.Provenance.confirmedManual("river-test"));
    }

    private static Map<Long, RiverWindowV2> renderTiles(
            MeanderingRiverGeneratorV2 generator,
            int tileSize,
            boolean reverse,
            int threads,
            IntPredicate hardRoute
    ) throws Exception {
        List<long[]> tiles = new ArrayList<>();
        for (int z = 0; z < generator.length(); z += tileSize) {
            for (int x = 0; x < generator.width(); x += tileSize) {
                tiles.add(new long[] {x, z,
                        Math.min(tileSize, generator.width() - x),
                        Math.min(tileSize, generator.length() - z)});
            }
        }
        if (reverse) Collections.reverse(tiles);
        ConcurrentHashMap<Long, RiverWindowV2> result = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (long[] tile : tiles) {
                futures.add(executor.submit(() -> {
                    int halo = Math.min(generator.plan().supportRadiusXZ(), 8);
                    int originX = (int) tile[0];
                    int originZ = (int) tile[1];
                    int coreWidth = (int) tile[2];
                    int coreLength = (int) tile[3];
                    int safeHalo = Math.min(halo, Math.min(originX, originZ));
                    safeHalo = Math.min(safeHalo, generator.width() - (originX + coreWidth));
                    safeHalo = Math.min(safeHalo, generator.length() - (originZ + coreLength));
                    safeHalo = Math.max(0, safeHalo);
                    result.put(tile[0] << 32 | tile[1], generator.renderWindow(
                            originX, originZ, coreWidth, coreLength, safeHalo, hardRoute, () -> false));
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static MeanderingRiverGeneratorV2.CellSource tiledSource(
            MeanderingRiverGeneratorV2 generator,
            Map<Long, RiverWindowV2> tiles,
            int tileSize
    ) {
        return (x, z) -> {
            int tileX = (x / tileSize) * tileSize;
            int tileZ = (z / tileSize) * tileSize;
            RiverWindowV2 window = tiles.get(((long) tileX << 32) | tileZ);
            return new MeanderingRiverGeneratorV2.RiverSample(
                    window.rawValueAt(MeanderingRiverGeneratorV2.RiverField.CHANNEL_MASK, x, z),
                    window.rawValueAt(MeanderingRiverGeneratorV2.RiverField.BANK_MASK, x, z),
                    window.rawValueAt(MeanderingRiverGeneratorV2.RiverField.FLOODPLAIN_MASK, x, z),
                    window.rawValueAt(MeanderingRiverGeneratorV2.RiverField.MEANDER_CORRIDOR, x, z),
                    window.rawValueAt(MeanderingRiverGeneratorV2.RiverField.LOCAL_WIDTH, x, z),
                    window.rawValueAt(MeanderingRiverGeneratorV2.RiverField.DISCHARGE_INDEX, x, z),
                    window.rawValueAt(MeanderingRiverGeneratorV2.RiverField.BED_ELEVATION, x, z),
                    window.rawValueAt(MeanderingRiverGeneratorV2.RiverField.WATER_SURFACE, x, z),
                    window.rawValueAt(MeanderingRiverGeneratorV2.RiverField.WATER_DEPTH, x, z),
                    window.rawValueAt(MeanderingRiverGeneratorV2.RiverField.WATER_BODY_ID, x, z));
        };
    }
}
