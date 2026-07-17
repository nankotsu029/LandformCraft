package com.github.nankotsu029.landformcraft.generator.v2.coast.harbor;

import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.HarborBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarborBasinGeneratorV2Test {
    @Test
    void generatesPolygonWaterOpeningCorridorBottomProfileAndHoleExclusion() {
        HarborBasinGeneratorV2 generator = generator(257, 193, true);

        assertSample(generator, 128, 100, HarborBasinGeneratorV2.HarborRegion.INTERIOR,
                10_000_000, 40_000_000);
        assertSample(generator, 128, 140, HarborBasinGeneratorV2.HarborRegion.ENTRANCE_CORRIDOR,
                8_000_000, 42_000_000);
        assertSample(generator, 128, 154, HarborBasinGeneratorV2.HarborRegion.ENTRANCE_CORRIDOR,
                8_000_000, 42_000_000);
        assertSample(generator, 30, 30, HarborBasinGeneratorV2.HarborRegion.OUTSIDE,
                HarborBasinGeneratorV2.NO_DATA, HarborBasinGeneratorV2.NO_DATA);
        assertSample(generator, 100, 80, HarborBasinGeneratorV2.HarborRegion.OUTSIDE,
                HarborBasinGeneratorV2.NO_DATA, HarborBasinGeneratorV2.NO_DATA);

        HarborBasinGeneratorV2.HarborMetrics metrics = generator.evaluate(() -> false);
        assertTrue(metrics.navigableDepthP50Blocks() >= 8 && metrics.navigableDepthP50Blocks() <= 10);
        assertEquals(10_000_000, metrics.maximumDepthMillionths());
        assertTrue(metrics.interiorCells() > 0 && metrics.entranceCorridorCells() > 0
                && metrics.outsideCells() > 0);
    }

    @Test
    void rejectsHardLandInsideBasinOrOpening() {
        HarborBasinGeneratorV2 generator = generator(257, 193, false);
        HardLandWaterSourceV2 conflict = (x, z) -> x == 128 && z == 100
                ? HardLandWaterSourceV2.Classification.LAND
                : HardLandWaterSourceV2.Classification.UNSPECIFIED;
        HarborBasinGenerationException failure = assertThrows(HarborBasinGenerationException.class,
                () -> generator.sampleAt(128, 100, conflict));
        assertEquals("v2.harbor-basin-hard-mask-conflict", failure.ruleId());

        HardLandWaterSourceV2 hardWater = (x, z) -> x == 128 && z == 100
                ? HardLandWaterSourceV2.Classification.WATER
                : HardLandWaterSourceV2.Classification.UNSPECIFIED;
        assertTrue(generator.sampleAt(128, 100, hardWater).hardConstrained());
    }

    @Test
    void wholeAndTileFieldsMatchAcrossSeamsOrderThreadsLocaleAndTimezone() throws Exception {
        HarborBasinGeneratorV2 generator = generator(257, 193, true);
        Map<HarborBasinGeneratorV2.HarborField, String> direct =
                generator.fieldChecksums(HardLandWaterSourceV2.NONE, () -> false);
        Map<Long, HarborBasinWindowV2> forward = renderTiles(generator, 128, false, 1);
        Map<Long, HarborBasinWindowV2> reverse = renderTiles(generator, 128, true, 1);
        Map<Long, HarborBasinWindowV2> parallel = renderTiles(generator, 128, true, 4);
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, forward, 128), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, reverse, 128), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, parallel, 128), () -> false));

        for (int z = 0; z < generator.length(); z++) {
            for (int x : List.of(127, 128, 255, 256)) {
                if (x >= generator.width()) continue;
                HarborBasinGeneratorV2.HarborSample expected =
                        generator.sampleAt(x, z, HardLandWaterSourceV2.NONE);
                HarborBasinGeneratorV2.HarborSample actual = tiledSource(generator, parallel, 128).sampleAt(x, z);
                for (HarborBasinGeneratorV2.HarborField field : HarborBasinGeneratorV2.HarborField.values()) {
                    assertEquals(expected.rawValue(field), actual.rawValue(field));
                }
            }
        }

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(direct, generator.fieldChecksums(HardLandWaterSourceV2.NONE, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void windowMemoryAndGeometryBudgetsAreBounded() {
        HarborBasinGeneratorV2 generator = generator(1_000, 1_000, false);
        HarborBasinWindowV2 window = generator.renderWindow(
                128, 128, 128, 128, 64, HardLandWaterSourceV2.NONE, () -> false);
        assertEquals(1_114_112L, window.estimatedRetainedBytes());
        assertTrue(window.estimatedRetainedBytes() <= HarborBasinGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        HarborBasinGenerationException budget = assertThrows(HarborBasinGenerationException.class,
                () -> generator.renderWindow(
                        0, 0, 1_000, 1_000, 0, HardLandWaterSourceV2.NONE, () -> false));
        assertEquals("v2.harbor-basin-window", budget.ruleId());
    }

    private static void assertSample(
            HarborBasinGeneratorV2 generator,
            int x,
            int z,
            HarborBasinGeneratorV2.HarborRegion region,
            int depth,
            int bottom
    ) {
        HarborBasinGeneratorV2.HarborSample sample =
                generator.sampleAt(x, z, HardLandWaterSourceV2.NONE);
        assertEquals(region, sample.region());
        assertEquals(region == HarborBasinGeneratorV2.HarborRegion.OUTSIDE ? 0 : 1, sample.water());
        assertEquals(depth, sample.depthMillionths());
        assertEquals(bottom, sample.bottomHeightMillionths());
    }

    private static Map<Long, HarborBasinWindowV2> renderTiles(
            HarborBasinGeneratorV2 generator,
            int tileSize,
            boolean reverse,
            int threads
    ) throws Exception {
        List<Tile> tiles = new ArrayList<>();
        for (int z = 0; z < generator.length(); z += tileSize) {
            for (int x = 0; x < generator.width(); x += tileSize) {
                tiles.add(new Tile(x, z, Math.min(tileSize, generator.width() - x),
                        Math.min(tileSize, generator.length() - z)));
            }
        }
        if (reverse) Collections.reverse(tiles);
        Map<Long, HarborBasinWindowV2> result = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Tile tile : tiles) {
                futures.add(executor.submit(() -> {
                    result.put(key(tile.x(), tile.z()), generator.renderWindow(
                            tile.x(), tile.z(), tile.width(), tile.length(), 32,
                            HardLandWaterSourceV2.NONE, () -> false));
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return Map.copyOf(result);
    }

    private static HarborBasinGeneratorV2.CellSource tiledSource(
            HarborBasinGeneratorV2 generator,
            Map<Long, HarborBasinWindowV2> windows,
            int tileSize
    ) {
        return (x, z) -> {
            HarborBasinWindowV2 window = windows.get(key(
                    x / tileSize * tileSize, z / tileSize * tileSize));
            int regionRaw = window.rawValueAt(HarborBasinGeneratorV2.HarborField.REGION, x, z);
            return new HarborBasinGeneratorV2.HarborSample(
                    HarborBasinGeneratorV2.HarborRegion.values()[regionRaw],
                    window.rawValueAt(HarborBasinGeneratorV2.HarborField.WATER, x, z),
                    window.rawValueAt(HarborBasinGeneratorV2.HarborField.DEPTH, x, z),
                    window.rawValueAt(HarborBasinGeneratorV2.HarborField.BOTTOM_HEIGHT, x, z),
                    generator.sampleAt(x, z, HardLandWaterSourceV2.NONE).hardConstrained());
        };
    }

    private static long key(int x, int z) {
        return ((long) z << 32) | (x & 0xffff_ffffL);
    }

    private static HarborBasinGeneratorV2 generator(int width, int length, boolean hole) {
        long scaleX = width == 1_000 ? 4L : 1L;
        long scaleZ = length == 1_000 ? 5L : 1L;
        List<CoastalFeaturePlanV2.BlockPoint> outer = List.of(
                point(40 * scaleX, 40 * scaleZ), point(216 * scaleX, 40 * scaleZ),
                point(216 * scaleX, 140 * scaleZ), point(156 * scaleX, 140 * scaleZ),
                point(100 * scaleX, 140 * scaleZ), point(40 * scaleX, 140 * scaleZ),
                point(40 * scaleX, 40 * scaleZ));
        List<CoastalFeaturePlanV2.BlockRing> rings = new ArrayList<>();
        rings.add(new CoastalFeaturePlanV2.BlockRing(0, outer));
        if (hole) {
            rings.add(new CoastalFeaturePlanV2.BlockRing(1, List.of(
                    point(90, 70), point(110, 70), point(110, 90), point(90, 90), point(90, 70))));
        }
        CoastalFeaturePlanV2 coastalPlan = new CoastalFeaturePlanV2(
                CoastalFeaturePlanV2.VERSION, "test-harbor", TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                new CoastalFeaturePlanV2.BlockGeometry(
                        CoastalFeaturePlanV2.BlockGeometry.VERSION, TerrainIntentV2.GeometryType.POLYGON,
                        List.of(), rings, "c".repeat(64)),
                CoastalFeaturePlanV2.GeometryRole.WATER_REGION,
                CoastalFoundationModuleV2.COAST_SIDE_FIELD_ID,
                CoastalFeaturePlanV2.CoastSide.INTERIOR_WATER,
                new CoastalFeaturePlanV2.SignedDistanceDescriptor(
                        CoastalFoundationModuleV2.SIGNED_DISTANCE_FIELD_ID,
                        CoastalFeaturePlanV2.DistanceSign.NEGATIVE_INSIDE, 24),
                new CoastalFeaturePlanV2.NearshoreProfileDescriptor(
                        CoastalFoundationModuleV2.NEARSHORE_PROFILE_FIELD_ID,
                        CoastalFeaturePlanV2.NearshoreProfileKind.NONE, 0, 0),
                24);
        HarborBasinPlanV2 basinPlan = new HarborBasinPlanV2(
                HarborBasinPlanV2.VERSION, "test-harbor",
                HarborBasinPlanV2.BottomProfileKind.EDGE_TO_CENTER_LINEAR,
                8, 10, 20, List.of("east-opening", "west-opening"),
                point(156 * scaleX, 140 * scaleZ), point(100 * scaleX, 140 * scaleZ),
                0, 1_000_000, 56L * scaleX * 1_000_000L, 24,
                -64, 255, 50,
                CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID,
                CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID,
                CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID,
                CoastalFoundationModuleV2.HARBOR_BOTTOM_HEIGHT_FIELD_ID,
                24);
        return new HarborBasinGeneratorV2(basinPlan, coastalPlan, width, length);
    }

    private static CoastalFeaturePlanV2.BlockPoint point(long x, long z) {
        return new CoastalFeaturePlanV2.BlockPoint(x * 1_000_000L, z * 1_000_000L);
    }

    private record Tile(int x, int z, int width, int length) { }
}
