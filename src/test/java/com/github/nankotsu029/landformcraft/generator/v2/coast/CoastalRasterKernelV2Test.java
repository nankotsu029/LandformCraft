package com.github.nankotsu029.landformcraft.generator.v2.coast;

import com.github.nankotsu029.landformcraft.core.v2.ConstraintMapSamplerV2;
import com.github.nankotsu029.landformcraft.format.v2.field.FieldWindow;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridReaderV1;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoastalRasterKernelV2Test {
    @TempDir
    Path temporaryDirectory;

    @Test
    void straightCoastHasCanonicalDistanceNormalSideAndNearshoreGolden() {
        CoastalRasterKernelV2 kernel = new CoastalRasterKernelV2(
                linePlan(2, TerrainIntentV2.Interpolation.POLYLINE, 4), 5, 5);

        int[] signed = new int[5];
        int[] actual = new int[5];
        int[] normalX = new int[5];
        int[] normalZ = new int[5];
        int[] nearshore = new int[5];
        for (int z = 0; z < 5; z++) {
            CoastalRasterKernelV2.RasterSample sample = kernel.sampleAt(2, z);
            signed[z] = sample.signedDistanceMillionths();
            actual[z] = sample.actualLandWater();
            normalX[z] = sample.normalXMillionths();
            normalZ[z] = sample.normalZMillionths();
            nearshore[z] = sample.nearshoreDepthMillionths();
        }
        assertArrayEquals(new int[]{2_000_000, 1_000_000, 0, -1_000_000, -2_000_000}, signed);
        assertArrayEquals(new int[]{1, 1, 1, 0, 0}, actual);
        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, normalX);
        assertArrayEquals(new int[]{-1_000_000, -1_000_000, -1_000_000, -1_000_000, -1_000_000}, normalZ);
        assertArrayEquals(new int[]{
                CoastalRasterKernelV2.NEARSHORE_NO_DATA,
                CoastalRasterKernelV2.NEARSHORE_NO_DATA,
                CoastalRasterKernelV2.NEARSHORE_NO_DATA,
                1_000_000,
                2_000_000
        }, nearshore);
    }

    @Test
    void curvedAndBoundaryCoastsHaveStableGoldenChecksums() {
        CoastalRasterKernelV2 curved = new CoastalRasterKernelV2(curvedPlan(), 33, 33);
        Map<CoastalRasterKernelV2.RasterField, String> curvedChecksums =
                curved.fieldChecksums(HardLandWaterSourceV2.NONE, () -> false);
        assertEquals("b1145c81d54a77a84b93eda824f6aa6e1729095195ae40b3f76d62ff70eaa55e",
                curvedChecksums.get(CoastalRasterKernelV2.RasterField.SIGNED_DISTANCE));
        assertEquals("ac4577d720df530d3f4732e801318d334d306d54c825e3558e4700da6e31e360",
                curvedChecksums.get(CoastalRasterKernelV2.RasterField.NORMAL_Z));

        CoastalRasterKernelV2 boundary = new CoastalRasterKernelV2(
                verticalBoundaryPlan(), 5, 5);
        Map<CoastalRasterKernelV2.RasterField, String> boundaryChecksums =
                boundary.fieldChecksums(HardLandWaterSourceV2.NONE, () -> false);
        assertEquals("749642a0e5a9449dc596942a6d8f14bfa371b485bac43232040abfa97b3668fa",
                boundaryChecksums.get(CoastalRasterKernelV2.RasterField.COAST_SIDE));
        for (int z = 0; z < 5; z++) {
            assertEquals(1, boundary.sampleAt(0, z).coastSide());
            assertEquals(0, boundary.sampleAt(4, z).coastSide());
        }
    }

    @Test
    void hardMaskAlwaysWinsAndStreamsThroughTheExistingLfcGridContract() throws IOException {
        CoastalRasterKernelV2 kernel = new CoastalRasterKernelV2(
                linePlan(2, TerrainIntentV2.Interpolation.POLYLINE, 4), 5, 5);
        int[] desired = new int[25];
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 5; x++) {
                desired[z * 5 + x] = (x + z) % 3 == 0 ? 1 : 0;
            }
        }
        FieldArtifactDescriptorV2 hardDescriptor = writeField(
                "fields/hard-mask.lfgrid", hardDefinition(false), (x, z) -> desired[z * 5 + x]);
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(temporaryDirectory, hardDescriptor)) {
            CoastalHardMaskWindowV2 hard = CoastalHardMaskWindowV2.read(
                    reader, kernel, 0, 0, 5, 5, 0, () -> false);
            CoastalRasterWindowV2 window = kernel.renderWindow(0, 0, 5, 5, 0, hard, () -> false);
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 5; x++) {
                    assertEquals(desired[z * 5 + x], window.rawValueAt(
                            CoastalRasterKernelV2.RasterField.ACTUAL_LAND_WATER, x, z));
                    assertTrue(window.isHardAt(x, z));
                }
            }

            FieldArtifactDescriptorV2 actualDescriptor = new LfcGridWriterV1().write(
                    temporaryDirectory,
                    "fields/actual-mask.lfgrid",
                    actualDefinition(),
                    derivedProvenance(),
                    kernel.fieldValueSource(CoastalRasterKernelV2.RasterField.ACTUAL_LAND_WATER, hard),
                    () -> false);
            try (LfcGridReaderV1 actualReader = LfcGridReaderV1.open(temporaryDirectory, actualDescriptor)) {
                FieldWindow actual = actualReader.readWindow(0, 0, 5, 5);
                assertArrayEquals(desired, actual.toRawArray());
            }
        }
    }

    @Test
    void hardClassificationUsesTheV2PixelCenterNearestMappingExactly() {
        CoastalRasterKernelV2 kernel = new CoastalRasterKernelV2(
                linePlan(2, TerrainIntentV2.Interpolation.POLYLINE, 4), 5, 5);
        ConstraintMapSamplerV2 sampler = new ConstraintMapSamplerV2(3, 3,
                new GenerationRequestV2.CoordinateMapping(
                        GenerationRequestV2.CoordinateOrigin.NORTH_WEST,
                        GenerationRequestV2.XAxis.EAST,
                        GenerationRequestV2.ZAxis.SOUTH,
                        GenerationRequestV2.PixelReference.PIXEL_CENTER,
                        GenerationRequestV2.AspectMismatchPolicy.REJECT,
                        GenerationRequestV2.QuarterTurn.DEGREES_0,
                        false,
                        false,
                        new GenerationRequestV2.PixelCrop(0, 0, 3, 3)));
        HardLandWaterSourceV2 hard = (x, z) -> sampler.sampleNearest(
                x, z, 5, 5, (rawX, rawZ) -> (rawX + rawZ) % 2) == 1
                ? HardLandWaterSourceV2.Classification.LAND
                : HardLandWaterSourceV2.Classification.WATER;

        CoastalRasterWindowV2 window = kernel.renderWindow(0, 0, 5, 5, 0, hard, () -> false);
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 5; x++) {
                int expected = sampler.sampleNearest(x, z, 5, 5, (rawX, rawZ) -> (rawX + rawZ) % 2);
                assertEquals(expected, window.rawValueAt(
                        CoastalRasterKernelV2.RasterField.ACTUAL_LAND_WATER, x, z));
            }
        }
    }

    @Test
    void hardMaskRejectsNoDataAndUnknownCategoricalValues() throws IOException {
        CoastalRasterKernelV2 kernel = new CoastalRasterKernelV2(
                linePlan(2, TerrainIntentV2.Interpolation.POLYLINE, 4), 5, 5);
        FieldArtifactDescriptorV2 noData = writeField(
                "fields/no-data.lfgrid", hardDefinition(true), (x, z) -> x == 2 && z == 2 ? 255 : 1);
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(temporaryDirectory, noData)) {
            CoastalRasterException failure = assertThrows(CoastalRasterException.class,
                    () -> CoastalHardMaskWindowV2.read(reader, kernel, 0, 0, 5, 5, 0, () -> false));
            assertEquals("v2.coastal-hard-mask-no-data", failure.ruleId());
        }

        FieldArtifactDescriptorV2 unknown = writeField(
                "fields/unknown.lfgrid", hardDefinition(false), (x, z) -> x == 2 && z == 2 ? 2 : 1);
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(temporaryDirectory, unknown)) {
            CoastalRasterException failure = assertThrows(CoastalRasterException.class,
                    () -> CoastalHardMaskWindowV2.read(reader, kernel, 0, 0, 5, 5, 0, () -> false));
            assertEquals("v2.coastal-hard-mask-label", failure.ruleId());
        }
    }

    @Test
    void wholeAndTiledSamplingMatchAcrossOrderThreadsSeamsLocaleAndTimezone() throws Exception {
        CoastalRasterKernelV2 kernel = new CoastalRasterKernelV2(curvedPlan(), 257, 193);
        HardLandWaterSourceV2 hard = (x, z) -> (x * 17 + z * 31) % 97 == 0
                ? HardLandWaterSourceV2.Classification.WATER
                : HardLandWaterSourceV2.Classification.UNSPECIFIED;
        Map<CoastalRasterKernelV2.RasterField, String> direct = kernel.fieldChecksums(hard, () -> false);

        Map<Tile, CoastalRasterWindowV2> forward = renderTiles(kernel, hard, 128, false, 1);
        Map<Tile, CoastalRasterWindowV2> reverse = renderTiles(kernel, hard, 128, true, 1);
        Map<Tile, CoastalRasterWindowV2> parallel = renderTiles(kernel, hard, 128, true, 4);
        assertEquals(direct, kernel.fieldChecksumsFrom(tiledSource(forward, 128), () -> false));
        assertEquals(direct, kernel.fieldChecksumsFrom(tiledSource(reverse, 128), () -> false));
        assertEquals(direct, kernel.fieldChecksumsFrom(tiledSource(parallel, 128), () -> false));

        for (int z = 0; z < kernel.length(); z++) {
            for (int x : List.of(127, 128, 255, 256)) {
                if (x >= kernel.width()) continue;
                assertEquals(kernel.sampleAt(x, z, hard), tiledSource(parallel, 128).sampleAt(x, z));
            }
        }

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(direct, kernel.fieldChecksums(hard, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void thousandSquareUsesOnlyBoundedTileWindows() {
        CoastalRasterKernelV2 kernel = new CoastalRasterKernelV2(
                wideLinePlan(500, 64), 1_000, 1_000);
        long coreCells = 0L;
        long maximumRetained = 0L;
        for (int z = 0; z < 1_000; z += 128) {
            for (int x = 0; x < 1_000; x += 128) {
                int tileWidth = Math.min(128, 1_000 - x);
                int tileLength = Math.min(128, 1_000 - z);
                CoastalRasterWindowV2 window = kernel.renderWindow(
                        x, z, tileWidth, tileLength, 64, HardLandWaterSourceV2.NONE, () -> false);
                coreCells += (long) tileWidth * tileLength;
                maximumRetained = Math.max(maximumRetained, window.estimatedRetainedBytes());
            }
        }
        assertEquals(1_000_000L, coreCells);
        assertEquals(1_703_936L, maximumRetained);
        assertTrue(maximumRetained <= CoastalRasterKernelV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        assertThrows(CoastalRasterException.class, () -> kernel.renderWindow(
                0, 0, 1_000, 1_000, 0, HardLandWaterSourceV2.NONE, () -> false));
    }

    @Test
    void cancellationStopsBeforeReturningAWindow() {
        CoastalRasterKernelV2 kernel = new CoastalRasterKernelV2(
                wideLinePlan(128, 64), 256, 256);
        assertThrows(CancellationException.class, () -> kernel.renderWindow(
                0, 0, 128, 128, 64, HardLandWaterSourceV2.NONE, () -> true));
    }

    @Test
    void rejectsQuantizedDegeneracyOverflowAndExcessSupport() {
        CoastalRasterException degenerate = assertThrows(CoastalRasterException.class,
                () -> new CoastalRasterKernelV2(plan(
                        List.of(pointRaw(1, 2_000_000), pointRaw(2, 2_000_000)),
                        TerrainIntentV2.Interpolation.POLYLINE, 4), 5, 5));
        assertEquals("v2.coastal-raster-degenerate", degenerate.ruleId());

        CoastalRasterException overflow = assertThrows(CoastalRasterException.class,
                () -> new CoastalRasterKernelV2(plan(
                        List.of(pointRaw(0, 2_000_000), pointRaw(Long.MAX_VALUE, 2_000_000)),
                        TerrainIntentV2.Interpolation.POLYLINE, 4), 5, 5));
        assertEquals("v2.coastal-raster-overflow", overflow.ruleId());

        CoastalRasterException support = assertThrows(CoastalRasterException.class,
                () -> new CoastalRasterKernelV2(linePlan(2, TerrainIntentV2.Interpolation.POLYLINE, 65), 5, 5));
        assertEquals("v2.coastal-raster-support", support.ruleId());
    }

    @Test
    void integerPrimitivesHaveStableHalfAndRootRules() {
        assertEquals(2L, CoastalRasterKernelV2.roundDivide(3L, 2L));
        assertEquals(-2L, CoastalRasterKernelV2.roundDivide(-3L, 2L));
        assertEquals(3L, CoastalRasterKernelV2.integerSquareRoot(15L));
        assertEquals(4L, CoastalRasterKernelV2.integerSquareRoot(16L));
    }

    private FieldArtifactDescriptorV2 writeField(
            String path,
            FieldArtifactDescriptorV2.Definition definition,
            com.github.nankotsu029.landformcraft.format.v2.field.FieldValueSource source
    ) throws IOException {
        return new LfcGridWriterV1().write(
                temporaryDirectory, path, definition, derivedProvenance(), source, () -> false);
    }

    private static FieldArtifactDescriptorV2.Definition hardDefinition(boolean noData) {
        return new FieldArtifactDescriptorV2.Definition(
                "coastal.desired-land-water",
                FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                FieldArtifactDescriptorV2.FieldValueType.U8,
                5,
                5,
                FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                FieldArtifactDescriptorV2.Sampling.NEAREST,
                FieldArtifactDescriptorV2.FIXED_SCALE,
                0L,
                noData,
                noData ? 255 : 0);
    }

    private static FieldArtifactDescriptorV2.Definition actualDefinition() {
        return new FieldArtifactDescriptorV2.Definition(
                "coastal.actual-land-water",
                FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER,
                FieldArtifactDescriptorV2.FieldValueType.U8,
                5,
                5,
                FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                FieldArtifactDescriptorV2.Sampling.NEAREST,
                FieldArtifactDescriptorV2.FIXED_SCALE,
                0L,
                false,
                0);
    }

    private static FieldArtifactDescriptorV2.Provenance derivedProvenance() {
        return new FieldArtifactDescriptorV2.Provenance(
                FieldArtifactDescriptorV2.SourceKind.DERIVED,
                "derived-source:coastal-raster",
                "a".repeat(64),
                "coast.raster",
                CoastalRasterKernelV2.VERSION,
                "identity-v1");
    }

    private static Map<Tile, CoastalRasterWindowV2> renderTiles(
            CoastalRasterKernelV2 kernel,
            HardLandWaterSourceV2 hard,
            int tileSize,
            boolean reverse,
            int threads
    ) throws Exception {
        List<Tile> tiles = new ArrayList<>();
        for (int z = 0; z < kernel.length(); z += tileSize) {
            for (int x = 0; x < kernel.width(); x += tileSize) {
                tiles.add(new Tile(x, z, Math.min(tileSize, kernel.width() - x),
                        Math.min(tileSize, kernel.length() - z)));
            }
        }
        if (reverse) Collections.reverse(tiles);
        Map<Tile, CoastalRasterWindowV2> result = threads == 1 ? new HashMap<>() : new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Tile tile : tiles) {
                futures.add(executor.submit(() -> {
                    result.put(tile, kernel.renderWindow(
                            tile.x(), tile.z(), tile.width(), tile.length(), 16, hard, () -> false));
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return Map.copyOf(result);
    }

    private static CoastalRasterKernelV2.CellSource tiledSource(
            Map<Tile, CoastalRasterWindowV2> windows,
            int tileSize
    ) {
        return (x, z) -> {
            int tileX = x / tileSize * tileSize;
            int tileZ = z / tileSize * tileSize;
            CoastalRasterWindowV2 window = windows.entrySet().stream()
                    .filter(entry -> entry.getKey().x() == tileX && entry.getKey().z() == tileZ)
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
            return new CoastalRasterKernelV2.RasterSample(
                    window.rawValueAt(CoastalRasterKernelV2.RasterField.ACTUAL_LAND_WATER, x, z),
                    window.rawValueAt(CoastalRasterKernelV2.RasterField.COAST_SIDE, x, z),
                    window.rawValueAt(CoastalRasterKernelV2.RasterField.SIGNED_DISTANCE, x, z),
                    window.rawValueAt(CoastalRasterKernelV2.RasterField.NORMAL_X, x, z),
                    window.rawValueAt(CoastalRasterKernelV2.RasterField.NORMAL_Z, x, z),
                    window.rawValueAt(CoastalRasterKernelV2.RasterField.NEARSHORE_PROFILE, x, z),
                    window.isHardAt(x, z));
        };
    }

    private static CoastalFeaturePlanV2 linePlan(
            int z,
            TerrainIntentV2.Interpolation interpolation,
            int support
    ) {
        return plan(List.of(point(0, z), point(4, z)), interpolation, support);
    }

    private static CoastalFeaturePlanV2 wideLinePlan(int z, int support) {
        return plan(List.of(point(0, z), point(999, z)), TerrainIntentV2.Interpolation.POLYLINE, support);
    }

    private static CoastalFeaturePlanV2 verticalBoundaryPlan() {
        return plan(List.of(point(0, 4), point(0, 0)), TerrainIntentV2.Interpolation.POLYLINE, 4);
    }

    private static CoastalFeaturePlanV2 curvedPlan() {
        return plan(List.of(
                point(0, 16), point(8, 12), point(16, 16), point(24, 20), point(32, 16)),
                TerrainIntentV2.Interpolation.CATMULL_ROM,
                16);
    }

    private static CoastalFeaturePlanV2 plan(
            List<CoastalFeaturePlanV2.BlockPoint> points,
            TerrainIntentV2.Interpolation interpolation,
            int support
    ) {
        return new CoastalFeaturePlanV2(
                CoastalFeaturePlanV2.VERSION,
                "test-beach",
                TerrainIntentV2.FeatureKind.SANDY_BEACH,
                new CoastalFeaturePlanV2.BlockGeometry(
                        CoastalFeaturePlanV2.BlockGeometry.VERSION,
                        TerrainIntentV2.GeometryType.SPLINE,
                        List.of(new CoastalFeaturePlanV2.BlockPath("coastline", interpolation, points)),
                        List.of(),
                        "b".repeat(64)),
                CoastalFeaturePlanV2.GeometryRole.COASTLINE,
                CoastalFoundationModuleV2.COAST_SIDE_FIELD_ID,
                CoastalFeaturePlanV2.CoastSide.LAND_LEFT,
                new CoastalFeaturePlanV2.SignedDistanceDescriptor(
                        CoastalFoundationModuleV2.SIGNED_DISTANCE_FIELD_ID,
                        CoastalFeaturePlanV2.DistanceSign.POSITIVE_ON_LAND_SIDE,
                        support),
                new CoastalFeaturePlanV2.NearshoreProfileDescriptor(
                        CoastalFoundationModuleV2.NEARSHORE_PROFILE_FIELD_ID,
                        CoastalFeaturePlanV2.NearshoreProfileKind.LINEAR_DEPTH_TARGET,
                        support,
                        Math.min(support, 12)),
                support);
    }

    private static CoastalFeaturePlanV2.BlockPoint point(int x, int z) {
        return pointRaw((long) x * 1_000_000L, (long) z * 1_000_000L);
    }

    private static CoastalFeaturePlanV2.BlockPoint pointRaw(long x, long z) {
        return new CoastalFeaturePlanV2.BlockPoint(x, z);
    }

    private record Tile(int x, int z, int width, int length) { }
}
