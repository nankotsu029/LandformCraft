package com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake;

import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
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

class LakeGeneratorV2Test {
    @Test
    void openSpillLakeProducesSingleSurfaceRimSpillAndInletOutletMetrics() {
        LakePlanV2 plan = compile(openLake("open-lake", 0), List.of(), 97, 97);
        LakeGeneratorV2 generator = new LakeGeneratorV2(plan);
        LakeGeneratorV2.LakeMetrics metrics = generator.evaluate(() -> false);

        assertTrue(metrics.singleSurfaceLevel());
        assertTrue(metrics.rimPresent());
        assertTrue(metrics.spillReachPresent());
        assertTrue(metrics.inletOutletDeclared());
        assertTrue(metrics.rimSealedExceptSpill());
        assertTrue(metrics.noReverseFlow());
        assertTrue(metrics.basinCells() > 0);
        assertTrue(metrics.rimCells() > 0);
        assertTrue(metrics.spillwayCells() > 0);
        assertEquals(plan.waterSurfaceYMillionths(), metrics.surfaceYMillionths());

        int midX = Math.toIntExact((plan.basinRing().get(0).xMillionths()
                + plan.basinRing().get(2).xMillionths()) / (2L * LakeFixedMathV2.FIXED_SCALE));
        int midZ = Math.toIntExact((plan.basinRing().get(0).zMillionths()
                + plan.basinRing().get(2).zMillionths()) / (2L * LakeFixedMathV2.FIXED_SCALE));
        assertEquals(1, generator.sampleAt(midX, midZ, index -> false).basinMask());
    }

    @Test
    void closedLakeOmitsSpillwayAndKeepsSealedRim() {
        LakePlanV2 plan = compile(closedLake("closed-lake"), List.of(), 97, 97);
        assertEquals(TerrainIntentV2.LakeTerminalPolicy.CLOSED, plan.terminalPolicy());
        assertEquals(-1, plan.spillEdgeStartIndex());
        LakeGeneratorV2.LakeMetrics metrics = new LakeGeneratorV2(plan).evaluate(() -> false);
        assertTrue(metrics.singleSurfaceLevel());
        assertTrue(metrics.rimSealedExceptSpill());
        assertEquals(0L, metrics.spillwayCells());
        assertTrue(metrics.spillReachPresent());
    }

    @Test
    void wholeAndTileFieldsMatchAcrossSeamsOrderThreadsLocaleAndTimezone() throws Exception {
        LakePlanV2 plan = compile(openLake("tile-lake", 0), List.of(), 129, 129);
        LakeGeneratorV2 generator = new LakeGeneratorV2(plan);
        IntPredicate none = index -> false;
        Map<LakeGeneratorV2.LakeField, String> direct = generator.fieldChecksums(none, () -> false);
        Map<Long, LakeWindowV2> forward = renderTiles(generator, 32, false, 1, none);
        Map<Long, LakeWindowV2> reverse = renderTiles(generator, 32, true, 1, none);
        Map<Long, LakeWindowV2> parallel = renderTiles(generator, 32, true, 4, none);
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, forward, 32), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, reverse, 32), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, parallel, 32), () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            LakePlanV2 recompiled = compile(openLake("tile-lake", 0), List.of(), 129, 129);
            assertEquals(plan, recompiled);
            assertEquals(direct, new LakeGeneratorV2(recompiled).fieldChecksums(none, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void rejectsAmbiguousSpillReverseFlowHardConflictAndBudget() {
        TerrainIntentV2.Feature ambiguous = new TerrainIntentV2.Feature(
                "ambiguous-lake",
                TerrainIntentV2.FeatureKind.LAKE,
                new TerrainIntentV2.PolygonGeometry(List.of(List.of(
                        new TerrainIntentV2.Point2(200_000, 200_000),
                        new TerrainIntentV2.Point2(400_000, 200_000),
                        new TerrainIntentV2.Point2(600_000, 200_000),
                        new TerrainIntentV2.Point2(600_000, 600_000),
                        new TerrainIntentV2.Point2(200_000, 600_000),
                        new TerrainIntentV2.Point2(200_000, 200_000)))),
                new TerrainIntentV2.LakeParameters(
                        new TerrainIntentV2.IntRange(4, 8),
                        2,
                        TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL,
                        TerrainIntentV2.LakeSpillSelection.LOWEST_RIM_SADDLE,
                        -1,
                        4,
                        6,
                        TerrainIntentV2.LakeFloorProfile.EDGE_TO_CENTER_LINEAR),
                0,
                TerrainIntentV2.Provenance.confirmedManual("lake-test"));
        LakeGenerationException ambiguousFailure = assertThrows(LakeGenerationException.class,
                () -> compile(ambiguous, List.of(), 65, 65));
        assertEquals("v2.lake-ambiguous-spill", ambiguousFailure.ruleId());

        TerrainIntentV2.Feature reverse = openLake("reverse-lake", 2);
        // Force reverse by declaring an inward edge: top edge of the square opens north into basin.
        TerrainIntentV2.Feature reverseEdge = new TerrainIntentV2.Feature(
                reverse.id(),
                reverse.kind(),
                reverse.geometry(),
                new TerrainIntentV2.LakeParameters(
                        new TerrainIntentV2.IntRange(4, 8),
                        2,
                        TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL,
                        TerrainIntentV2.LakeSpillSelection.DECLARED_EDGE,
                        2,
                        4,
                        8,
                        TerrainIntentV2.LakeFloorProfile.EDGE_TO_CENTER_LINEAR),
                0,
                TerrainIntentV2.Provenance.confirmedManual("lake-test"));
        // Edge index 2 on square (200,200)-(800,200)-(800,800)-(200,800) is top (800,800)-(200,800);
        // outward should leave basin. If compiler accepts, reverse-flow check covers corridor.
        LakePlanV2 acceptedOrRejected;
        try {
            acceptedOrRejected = compile(reverseEdge, List.of(), 97, 97);
            LakeGeneratorV2.LakeMetrics metrics = new LakeGeneratorV2(acceptedOrRejected).evaluate(() -> false);
            assertTrue(metrics.noReverseFlow());
            assertTrue(metrics.rimSealedExceptSpill());
        } catch (LakeGenerationException exception) {
            assertTrue(exception.ruleId().equals("v2.lake-reverse-flow")
                    || exception.ruleId().equals("v2.lake-spill-orientation")
                    || exception.ruleId().equals("v2.lake-spill-width"));
        }

        LakePlanV2 plan = compile(openLake("blocked-lake", 0), List.of(), 97, 97);
        LakeGeneratorV2 generator = new LakeGeneratorV2(plan);
        int midX = Math.toIntExact((plan.basinRing().get(0).xMillionths()
                + plan.basinRing().get(2).xMillionths()) / (2L * LakeFixedMathV2.FIXED_SCALE));
        int midZ = Math.toIntExact((plan.basinRing().get(0).zMillionths()
                + plan.basinRing().get(2).zMillionths()) / (2L * LakeFixedMathV2.FIXED_SCALE));
        BitSet blocked = new BitSet();
        blocked.set(midZ * plan.width() + midX);
        LakeGenerationException hardConflict = assertThrows(LakeGenerationException.class,
                () -> generator.sampleAt(midX, midZ, blocked::get));
        assertEquals("v2.lake-hard-conflict", hardConflict.ruleId());

        LakeGenerationException budget = assertThrows(LakeGenerationException.class,
                () -> generator.renderWindow(0, 0, plan.width(), plan.length(), plan.supportRadiusXZ(),
                        index -> false, () -> false));
        assertTrue(budget.ruleId().equals("v2.lake-window")
                || budget.ruleId().equals("v2.lake-budget")
                || budget.ruleId().equals("v2.lake-halo"));
    }

    @Test
    void riverInletRelationBindsAndInvalidInletIsRejected() {
        TerrainIntentV2.Feature river = new TerrainIntentV2.Feature(
                "inlet-river",
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                new TerrainIntentV2.SplineGeometry(List.of(
                        new TerrainIntentV2.Point2(500_000, 50_000),
                        new TerrainIntentV2.Point2(500_000, 300_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.MeanderingRiverParameters(
                        new TerrainIntentV2.IntRange(4, 6),
                        TerrainIntentV2.DischargeClass.SMALL,
                        1_000,
                        TerrainIntentV2.RiverVariant.RIVER),
                0,
                TerrainIntentV2.Provenance.confirmedManual("lake-test"));
        TerrainIntentV2.Feature lake = openLake("inlet-lake", 0);
        List<TerrainIntentV2.Relation> relations = List.of(new TerrainIntentV2.Relation(
                "river-to-lake",
                TerrainIntentV2.RelationKind.DRAINS_TO,
                "feature:inlet-river",
                "feature:inlet-lake",
                TerrainIntentV2.Strength.HARD));
        LakePlanV2 plan = compile(lake, List.of(river), relations, 97, 97);
        assertEquals(List.of("inlet-inlet-river"), plan.inletNodeIds());

        TerrainIntentV2.Feature notRiver = new TerrainIntentV2.Feature(
                "not-river",
                TerrainIntentV2.FeatureKind.MANGROVE_WETLAND,
                new TerrainIntentV2.PolygonGeometry(List.of(List.of(
                        new TerrainIntentV2.Point2(100_000, 100_000),
                        new TerrainIntentV2.Point2(200_000, 100_000),
                        new TerrainIntentV2.Point2(200_000, 200_000),
                        new TerrainIntentV2.Point2(100_000, 200_000),
                        new TerrainIntentV2.Point2(100_000, 100_000)))),
                defaultMangroveParameters(),
                0,
                TerrainIntentV2.Provenance.confirmedManual("lake-test"));
        List<TerrainIntentV2.Relation> bad = List.of(new TerrainIntentV2.Relation(
                "non-river-to-lake",
                TerrainIntentV2.RelationKind.DRAINS_TO,
                "feature:not-river",
                "feature:inlet-lake",
                TerrainIntentV2.Strength.HARD));
        LakeGenerationException failure = assertThrows(LakeGenerationException.class,
                () -> compile(lake, List.of(notRiver), bad, 97, 97));
        assertEquals("v2.lake-inlet-relation", failure.ruleId());
    }

    @Test
    void candidateOrderDoesNotChangeChecksum() {
        TerrainIntentV2.Feature feature = openLake("ordered-lake", 0);
        LakePlanV2 first = compile(feature, List.of(), 97, 97);
        LakePlanV2 second = compile(feature, List.of(), 97, 97);
        assertEquals(first, second);
        assertEquals(
                new LakeGeneratorV2(first).fieldChecksums(index -> false, () -> false),
                new LakeGeneratorV2(second).fieldChecksums(index -> false, () -> false));
    }

    private static LakePlanV2 compile(TerrainIntentV2.Feature lake, List<TerrainIntentV2.Feature> extras,
                                      int width, int length) {
        return compile(lake, extras, List.of(), width, length);
    }

    private static LakePlanV2 compile(
            TerrainIntentV2.Feature lake,
            List<TerrainIntentV2.Feature> extras,
            List<TerrainIntentV2.Relation> relations,
            int width,
            int length
    ) {
        List<TerrainIntentV2.Feature> features = new ArrayList<>();
        features.add(lake);
        features.addAll(extras);
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "lake-fixture",
                "lake",
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                features,
                relations,
                List.of(),
                new TerrainIntentV2.EnvironmentDescriptor("ALLUVIAL_SEDIMENT", "TEMPERATE_HUMID", "LAKE_BASIN"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("lake-test"));
        return new LakePlanCompilerV2().compile(
                lake,
                intent,
                new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                "a".repeat(64));
    }

    private static TerrainIntentV2.Feature openLake(String id, int spillEdgeStartIndex) {
        return new TerrainIntentV2.Feature(
                id,
                TerrainIntentV2.FeatureKind.LAKE,
                squareBasin(),
                new TerrainIntentV2.LakeParameters(
                        new TerrainIntentV2.IntRange(4, 8),
                        2,
                        TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL,
                        TerrainIntentV2.LakeSpillSelection.DECLARED_EDGE,
                        spillEdgeStartIndex,
                        4,
                        6,
                        TerrainIntentV2.LakeFloorProfile.EDGE_TO_CENTER_LINEAR),
                0,
                TerrainIntentV2.Provenance.confirmedManual("lake-test"));
    }

    private static TerrainIntentV2.Feature closedLake(String id) {
        return new TerrainIntentV2.Feature(
                id,
                TerrainIntentV2.FeatureKind.LAKE,
                squareBasin(),
                new TerrainIntentV2.LakeParameters(
                        new TerrainIntentV2.IntRange(4, 8),
                        2,
                        TerrainIntentV2.LakeTerminalPolicy.CLOSED,
                        TerrainIntentV2.LakeSpillSelection.DECLARED_EDGE,
                        -1,
                        0,
                        0,
                        TerrainIntentV2.LakeFloorProfile.EDGE_TO_CENTER_LINEAR),
                0,
                TerrainIntentV2.Provenance.confirmedManual("lake-test"));
    }

    private static TerrainIntentV2.PolygonGeometry squareBasin() {
        return new TerrainIntentV2.PolygonGeometry(List.of(List.of(
                new TerrainIntentV2.Point2(250_000, 250_000),
                new TerrainIntentV2.Point2(750_000, 250_000),
                new TerrainIntentV2.Point2(750_000, 750_000),
                new TerrainIntentV2.Point2(250_000, 750_000),
                new TerrainIntentV2.Point2(250_000, 250_000))));
    }

    private static Map<Long, LakeWindowV2> renderTiles(
            LakeGeneratorV2 generator,
            int tileSize,
            boolean reverse,
            int threads,
            IntPredicate hard
    ) throws Exception {
        List<long[]> tiles = new ArrayList<>();
        for (int originZ = 0; originZ < generator.length(); originZ += tileSize) {
            for (int originX = 0; originX < generator.width(); originX += tileSize) {
                int width = Math.min(tileSize, generator.width() - originX);
                int length = Math.min(tileSize, generator.length() - originZ);
                tiles.add(new long[] {originX, originZ, width, length});
            }
        }
        if (reverse) Collections.reverse(tiles);
        Map<Long, LakeWindowV2> rendered = new ConcurrentHashMap<>();
        if (threads <= 1) {
            for (long[] tile : tiles) {
                rendered.put(tileKey((int) tile[0], (int) tile[1]),
                        generator.renderWindow((int) tile[0], (int) tile[1], (int) tile[2], (int) tile[3],
                                0, hard, () -> false));
            }
            return rendered;
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (long[] tile : tiles) {
                futures.add(executor.submit(() -> rendered.put(tileKey((int) tile[0], (int) tile[1]),
                        generator.renderWindow((int) tile[0], (int) tile[1], (int) tile[2], (int) tile[3],
                                0, hard, () -> false))));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return rendered;
    }

    private static LakeGeneratorV2.CellSource tiledSource(
            LakeGeneratorV2 generator,
            Map<Long, LakeWindowV2> tiles,
            int tileSize
    ) {
        return (x, z) -> {
            int originX = (x / tileSize) * tileSize;
            int originZ = (z / tileSize) * tileSize;
            LakeWindowV2 window = tiles.get(tileKey(originX, originZ));
            LakeGeneratorV2.LakeSample sample = generator.sampleAt(x, z, index -> false);
            for (LakeGeneratorV2.LakeField field : LakeGeneratorV2.LakeField.values()) {
                assertEquals(sample.rawValue(field), window.rawValueAt(field, x, z));
            }
            return sample;
        };
    }

    private static TerrainIntentV2.MangroveWetlandParameters defaultMangroveParameters() {
        return new TerrainIntentV2.MangroveWetlandParameters(
                new TerrainIntentV2.IntRange(2, 3),
                new TerrainIntentV2.FixedRange(200_000, 400_000));
    }

    private static long tileKey(int originX, int originZ) {
        return (((long) originX) << 32) | (originZ & 0xffffffffL);
    }
}
