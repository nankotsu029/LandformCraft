package com.github.nankotsu029.landformcraft.generator.v2.coast.beach;

import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalRasterKernelV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.SandyBeachPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandyBeachGeneratorV2Test {
    @Test
    void generatesWidthSlopeForeshoreBackshoreNearshoreAndSemanticSand() {
        SandyBeachGeneratorV2 generator = generator(
                coastPlan(129, 64, 16, 112, 20, 16, 4),
                beachPlan(8, 20, 16, 500_000, 5_000_000L, 87_489, 16, 4),
                129, 129);

        assertSample(generator, 64, 64, SandyBeachGeneratorV2.BeachBand.FORESHORE,
                50_000_000, 1);
        assertSample(generator, 64, 60, SandyBeachGeneratorV2.BeachBand.FORESHORE,
                50_349_956, 1);
        assertSample(generator, 64, 50, SandyBeachGeneratorV2.BeachBand.BACKSHORE,
                51_224_846, 1);
        assertSample(generator, 64, 40, SandyBeachGeneratorV2.BeachBand.OUTSIDE,
                SandyBeachGeneratorV2.NO_DATA, 0);
        assertSample(generator, 64, 68, SandyBeachGeneratorV2.BeachBand.NEARSHORE,
                49_000_000, 0);
        assertSample(generator, 64, 80, SandyBeachGeneratorV2.BeachBand.NEARSHORE,
                46_000_000, 0);
        assertSample(generator, 64, 81, SandyBeachGeneratorV2.BeachBand.OUTSIDE,
                SandyBeachGeneratorV2.NO_DATA, 0);

        assertEquals(8_000_000, generator.sampleAt(16, 64, HardLandWaterSourceV2.NONE)
                .localWidthMillionths());
        assertEquals(20_000_000, generator.sampleAt(64, 64, HardLandWaterSourceV2.NONE)
                .localWidthMillionths());
        SandyBeachGeneratorV2.BeachMetrics metrics = generator.evaluate(() -> false);
        assertTrue(metrics.widthP50Blocks() >= 8 && metrics.widthP50Blocks() <= 20);
        assertEquals(5_000_000L, metrics.shoreSlopeDegreesMillionths());
        assertEquals(4_000_000, metrics.maximumNearshoreDepthMillionths());
        assertTrue(metrics.foreshoreCells() > 0 && metrics.backshoreCells() > 0
                && metrics.nearshoreCells() > 0);

        SandyBeachGeneratorV2 equalWidthAndNearshore = generator(
                coastPlan(129, 64, 16, 112, 21, 20, 4),
                beachPlan(8, 20, 16, 500_000, 5_000_000L, 87_489, 20, 4),
                129, 129);
        assertSample(equalWidthAndNearshore, 64, 84, SandyBeachGeneratorV2.BeachBand.NEARSHORE,
                46_000_000, 0);
        assertSample(equalWidthAndNearshore, 64, 100, SandyBeachGeneratorV2.BeachBand.OUTSIDE,
                SandyBeachGeneratorV2.NO_DATA, 0);
    }

    @Test
    void wholeAndTileFieldsMatchAcrossSeamsOrderThreadsLocaleAndTimezone() throws Exception {
        CoastalFeaturePlanV2 coastalPlan = coastPlan(257, 96, 16, 240, 32, 24, 6);
        SandyBeachGeneratorV2 generator = generator(coastalPlan,
                beachPlan(12, 32, 32, 600_000, 6_000_000L, 105_104, 24, 6),
                257, 193);
        Map<SandyBeachGeneratorV2.BeachField, String> direct =
                generator.fieldChecksums(HardLandWaterSourceV2.NONE, () -> false);
        Map<Long, SandyBeachWindowV2> forward = renderTiles(generator, 128, false, 1);
        Map<Long, SandyBeachWindowV2> reverse = renderTiles(generator, 128, true, 1);
        Map<Long, SandyBeachWindowV2> parallel = renderTiles(generator, 128, true, 4);
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, forward, 128), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, reverse, 128), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, parallel, 128), () -> false));

        for (int z = 0; z < generator.length(); z++) {
            for (int x : List.of(127, 128, 255, 256)) {
                if (x >= generator.width()) continue;
                SandyBeachGeneratorV2.BeachSample directSample =
                        generator.sampleAt(x, z, HardLandWaterSourceV2.NONE);
                SandyBeachGeneratorV2.BeachSample tiledSample = tiledSource(generator, parallel, 128).sampleAt(x, z);
                for (SandyBeachGeneratorV2.BeachField field : SandyBeachGeneratorV2.BeachField.values()) {
                    assertEquals(directSample.rawValue(field), tiledSample.rawValue(field));
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
    void rejectsHardMaskConflict() {
        SandyBeachGeneratorV2 generator = generator(
                coastPlan(129, 64, 16, 112, 20, 16, 4),
                beachPlan(8, 20, 16, 500_000, 5_000_000L, 87_489, 16, 4),
                129, 129);
        HardLandWaterSourceV2 conflict = (x, z) -> x == 64 && z == 60
                ? HardLandWaterSourceV2.Classification.WATER
                : HardLandWaterSourceV2.Classification.UNSPECIFIED;
        SandyBeachGenerationException hardFailure = assertThrows(SandyBeachGenerationException.class,
                () -> generator.sampleAt(64, 60, conflict));
        assertEquals("v2.sandy-beach-hard-mask-conflict", hardFailure.ruleId());

    }

    @Test
    void planCompilerRejectsInsufficientWidthEndpointCorruptionAndVerticalOverflow() {
        SandyBeachPlanCompilerV2 compiler = new SandyBeachPlanCompilerV2();
        TerrainIntentV2.Feature feature = feature(8, 20, 16, 5_000_000L, 16, 4);
        CoastalFeaturePlanV2 normal = coastPlan(129, 64, 16, 112, 20, 16, 4);

        SandyBeachGenerationException width = assertThrows(SandyBeachGenerationException.class,
                () -> compiler.compile(feature, normal, new WorldBlueprintV2.Bounds(16, 129, -64, 255, 50)));
        assertEquals("v2.sandy-beach-width-insufficient", width.ruleId());

        CoastalFeaturePlanV2 shortPath = coastPlan(129, 64, 50, 70, 20, 16, 4);
        SandyBeachGenerationException endpoint = assertThrows(SandyBeachGenerationException.class,
                () -> compiler.compile(feature, shortPath, new WorldBlueprintV2.Bounds(129, 129, -64, 255, 50)));
        assertEquals("v2.sandy-beach-endpoint-profile", endpoint.ruleId());

        TerrainIntentV2.Feature steep = feature(8, 20, 16, 30_000_000L, 16, 4);
        SandyBeachGenerationException vertical = assertThrows(SandyBeachGenerationException.class,
                () -> compiler.compile(steep, normal, new WorldBlueprintV2.Bounds(129, 129, 49, 51, 50)));
        assertEquals("v2.sandy-beach-vertical-bounds", vertical.ruleId());
    }

    @Test
    void fixedSlopeAndWindowBudgetAreVersionedAndBounded() {
        assertEquals(87_489, SandyBeachFixedMathV2.tangentMillionths(5_000_000L));
        assertEquals(96_297, SandyBeachFixedMathV2.tangentMillionths(5_500_000L));
        assertThrows(SandyBeachGenerationException.class,
                () -> SandyBeachFixedMathV2.tangentMillionths(30_000_001L));

        SandyBeachGeneratorV2 generator = generator(
                coastPlan(1_000, 500, 64, 935, 64, 40, 6),
                beachPlan(20, 64, 64, 600_000, 5_000_000L, 87_489, 40, 6),
                1_000, 1_000);
        SandyBeachWindowV2 window = generator.renderWindow(
                128, 128, 128, 128, 64, HardLandWaterSourceV2.NONE, () -> false);
        assertEquals(1_114_112L, window.estimatedRetainedBytes());
        assertTrue(window.estimatedRetainedBytes() <= SandyBeachGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        assertThrows(SandyBeachGenerationException.class, () -> generator.renderWindow(
                0, 0, 1_000, 1_000, 0, HardLandWaterSourceV2.NONE, () -> false));
    }

    private static void assertSample(
            SandyBeachGeneratorV2 generator,
            int x,
            int z,
            SandyBeachGeneratorV2.BeachBand band,
            int surface,
            int sand
    ) {
        SandyBeachGeneratorV2.BeachSample sample = generator.sampleAt(x, z, HardLandWaterSourceV2.NONE);
        assertEquals(band, sample.band());
        assertEquals(surface, sample.surfaceHeightMillionths());
        assertEquals(sand, sample.semanticSand());
    }

    private static Map<Long, SandyBeachWindowV2> renderTiles(
            SandyBeachGeneratorV2 generator,
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
        Map<Long, SandyBeachWindowV2> result = new ConcurrentHashMap<>();
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

    private static SandyBeachGeneratorV2.CellSource tiledSource(
            SandyBeachGeneratorV2 generator,
            Map<Long, SandyBeachWindowV2> windows,
            int tileSize
    ) {
        return (x, z) -> {
            SandyBeachWindowV2 window = windows.get(key(x / tileSize * tileSize, z / tileSize * tileSize));
            SandyBeachGeneratorV2.BeachSample direct =
                    generator.sampleAt(x, z, HardLandWaterSourceV2.NONE);
            int bandRaw = window.rawValueAt(SandyBeachGeneratorV2.BeachField.BAND, x, z);
            return new SandyBeachGeneratorV2.BeachSample(
                    window.rawValueAt(SandyBeachGeneratorV2.BeachField.LOCAL_WIDTH, x, z),
                    window.rawValueAt(SandyBeachGeneratorV2.BeachField.SURFACE_HEIGHT, x, z),
                    SandyBeachGeneratorV2.BeachBand.values()[bandRaw],
                    window.rawValueAt(SandyBeachGeneratorV2.BeachField.SEMANTIC_SAND, x, z),
                    direct.coastalSample());
        };
    }

    private static long key(int x, int z) {
        return ((long) z << 32) | (x & 0xffff_ffffL);
    }

    private static SandyBeachGeneratorV2 generator(
            CoastalFeaturePlanV2 coastalPlan,
            SandyBeachPlanV2 beachPlan,
            int width,
            int length
    ) {
        return new SandyBeachGeneratorV2(beachPlan, new CoastalRasterKernelV2(coastalPlan, width, length));
    }

    private static CoastalFeaturePlanV2 coastPlan(
            int width,
            int z,
            int startX,
            int endX,
            int support,
            int nearshoreDistance,
            int nearshoreDepth
    ) {
        assertNotEquals(startX, endX);
        return new CoastalFeaturePlanV2(
                CoastalFeaturePlanV2.VERSION,
                "test-beach",
                TerrainIntentV2.FeatureKind.SANDY_BEACH,
                new CoastalFeaturePlanV2.BlockGeometry(
                        CoastalFeaturePlanV2.BlockGeometry.VERSION,
                        TerrainIntentV2.GeometryType.SPLINE,
                        List.of(new CoastalFeaturePlanV2.BlockPath(
                                "coastline", TerrainIntentV2.Interpolation.POLYLINE,
                                List.of(point(startX, z), point(endX, z)))),
                        List.of(), "b".repeat(64)),
                CoastalFeaturePlanV2.GeometryRole.COASTLINE,
                CoastalFoundationModuleV2.COAST_SIDE_FIELD_ID,
                CoastalFeaturePlanV2.CoastSide.LAND_LEFT,
                new CoastalFeaturePlanV2.SignedDistanceDescriptor(
                        CoastalFoundationModuleV2.SIGNED_DISTANCE_FIELD_ID,
                        CoastalFeaturePlanV2.DistanceSign.POSITIVE_ON_LAND_SIDE, support),
                new CoastalFeaturePlanV2.NearshoreProfileDescriptor(
                        CoastalFoundationModuleV2.NEARSHORE_PROFILE_FIELD_ID,
                        CoastalFeaturePlanV2.NearshoreProfileKind.LINEAR_DEPTH_TARGET,
                        nearshoreDistance, nearshoreDepth),
                support);
    }

    private static SandyBeachPlanV2 beachPlan(
            int minimumWidth,
            int maximumWidth,
            int taper,
            int foreshoreShare,
            long slope,
            int rise,
            int nearshoreDistance,
            int nearshoreDepth
    ) {
        return new SandyBeachPlanV2(
                SandyBeachPlanV2.VERSION, "test-beach", SandyBeachPlanV2.WidthProfileKind.ENDPOINT_TAPER,
                minimumWidth, maximumWidth, taper, foreshoreShare,
                slope, slope, slope, rise, nearshoreDistance, nearshoreDepth,
                -64, 255, 50,
                CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID,
                CoastalFoundationModuleV2.BEACH_SURFACE_HEIGHT_FIELD_ID,
                CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID,
                CoastalFoundationModuleV2.BEACH_SEMANTIC_SAND_FIELD_ID,
                Math.max(maximumWidth, nearshoreDistance + 1));
    }

    private static TerrainIntentV2.Feature feature(
            int minimumWidth,
            int maximumWidth,
            int taper,
            long slope,
            int nearshoreDistance,
            int nearshoreDepth
    ) {
        return new TerrainIntentV2.Feature(
                "test-beach",
                TerrainIntentV2.FeatureKind.SANDY_BEACH,
                new TerrainIntentV2.SplineGeometry(
                        List.of(new TerrainIntentV2.Point2(100_000, 500_000),
                                new TerrainIntentV2.Point2(900_000, 500_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.SandyBeachParameters(
                        new TerrainIntentV2.IntRange(minimumWidth, maximumWidth),
                        new TerrainIntentV2.FixedRange(slope, slope),
                        new TerrainIntentV2.NearshoreDepth(nearshoreDistance, nearshoreDepth),
                        500_000, taper, TerrainIntentV2.LandSide.LEFT),
                0,
                TerrainIntentV2.Provenance.confirmedManual("test"));
    }

    private static CoastalFeaturePlanV2.BlockPoint point(int x, int z) {
        return new CoastalFeaturePlanV2.BlockPoint((long) x * 1_000_000L, (long) z * 1_000_000L);
    }

    private record Tile(int x, int z, int width, int length) { }
}
