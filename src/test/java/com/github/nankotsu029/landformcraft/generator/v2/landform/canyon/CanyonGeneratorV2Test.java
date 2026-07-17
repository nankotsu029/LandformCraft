package com.github.nankotsu029.landformcraft.generator.v2.landform.canyon;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.CanyonPlanV2;
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

class CanyonGeneratorV2Test {
    @Test
    void canyonContainsRiverFloorRimAndMonotonicSharedBed() {
        Fixture fixture = compilePair(
                river("canyon-river", 4, 6),
                canyon("main-canyon", 12, 20, 40, 64, 24, 40,
                        TerrainIntentV2.CanyonCrossSection.V, 0, 0),
                129, 193);
        CanyonGeneratorV2.CanyonMetrics metrics = new CanyonGeneratorV2(fixture.canyon()).evaluate(() -> false);
        assertTrue(metrics.riverContainedInFloor());
        assertTrue(metrics.monotonicBed());
        assertTrue(metrics.floorAndRimPresent());
        assertTrue(metrics.terraceContractSatisfied());
        assertTrue(metrics.riverFitsFloor());
        assertTrue(metrics.floorCells() > 0);
        assertTrue(metrics.rimCells() > 0);
        assertEquals(fixture.canyon().sourceBedYMillionths() >= fixture.canyon().mouthBedYMillionths(), true);
    }

    @Test
    void terracedProfileProducesTerraceMask() {
        Fixture fixture = compilePair(
                river("terrace-river", 4, 6),
                canyon("terrace-canyon", 10, 16, 36, 56, 20, 36,
                        TerrainIntentV2.CanyonCrossSection.TERRACED_V, 2, 2),
                129, 161);
        CanyonGeneratorV2.CanyonMetrics metrics = new CanyonGeneratorV2(fixture.canyon()).evaluate(() -> false);
        assertTrue(metrics.terraceContractSatisfied());
        assertTrue(metrics.terraceCells() > 0);
    }

    @Test
    void wholeAndTileFieldsMatchAcrossSeamsOrderThreadsLocaleAndTimezone() throws Exception {
        Fixture fixture = compilePair(
                river("tile-river", 4, 6),
                canyon("tile-canyon", 10, 14, 30, 48, 16, 28,
                        TerrainIntentV2.CanyonCrossSection.U, 0, 0),
                193, 129);
        CanyonGeneratorV2 generator = new CanyonGeneratorV2(fixture.canyon());
        IntPredicate none = index -> false;
        Map<CanyonGeneratorV2.CanyonField, String> direct = generator.fieldChecksums(none, () -> false);
        Map<Long, CanyonWindowV2> forward = renderTiles(generator, 32, false, 1, none);
        Map<Long, CanyonWindowV2> reverse = renderTiles(generator, 32, true, 1, none);
        Map<Long, CanyonWindowV2> parallel = renderTiles(generator, 32, true, 4, none);
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, forward, 32), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, reverse, 32), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(generator, parallel, 32), () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            Fixture recompiled = compilePair(
                    river("tile-river", 4, 6),
                    canyon("tile-canyon", 10, 14, 30, 48, 16, 28,
                            TerrainIntentV2.CanyonCrossSection.U, 0, 0),
                    193, 129);
            assertEquals(fixture.canyon(), recompiled.canyon());
            assertEquals(direct, new CanyonGeneratorV2(recompiled.canyon()).fieldChecksums(none, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void rejectsMissingWithinThinWallDisconnectedCrossingOwnerConflictAndBudget() {
        TerrainIntentV2.Feature canyonOnly = canyon("lonely-canyon", 10, 14, 30, 48, 16, 28,
                TerrainIntentV2.CanyonCrossSection.V, 0, 0);
        CanyonGenerationException missing = assertThrows(CanyonGenerationException.class,
                () -> compileCanyon(canyonOnly, List.of(), List.of(), 97, 129));
        assertEquals("v2.canyon-within-missing", missing.ruleId());

        Fixture thinFixture = compilePair(
                river("thin-river", 4, 6),
                canyon("thin-canyon", 20, 20, 22, 22, 16, 28,
                        TerrainIntentV2.CanyonCrossSection.V, 0, 0),
                97, 129);
        CanyonGenerationException thin = assertThrows(CanyonGenerationException.class,
                () -> new CanyonGeneratorV2(thinFixture.canyon()));
        assertEquals("v2.canyon-thin-wall", thin.ruleId());

        TerrainIntentV2.Feature disconnectedCanyon = new TerrainIntentV2.Feature(
                "far-canyon",
                TerrainIntentV2.FeatureKind.CANYON,
                new TerrainIntentV2.SplineGeometry(List.of(
                        new TerrainIntentV2.Point2(100_000, 100_000),
                        new TerrainIntentV2.Point2(120_000, 200_000),
                        new TerrainIntentV2.Point2(140_000, 300_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.CanyonParameters(
                        new TerrainIntentV2.IntRange(10, 14),
                        new TerrainIntentV2.IntRange(30, 48),
                        new TerrainIntentV2.IntRange(16, 28),
                        TerrainIntentV2.CanyonCrossSection.V,
                        0,
                        0),
                0,
                TerrainIntentV2.Provenance.confirmedManual("canyon-test"));
        CanyonGenerationException disconnected = assertThrows(CanyonGenerationException.class,
                () -> compilePair(river("aligned-river", 4, 6), disconnectedCanyon, 129, 193));
        assertEquals("v2.canyon-disconnected-centerline", disconnected.ruleId());

        TerrainIntentV2.Feature riverA = river("river-a", 4, 6);
        TerrainIntentV2.Feature riverB = river("river-b", 4, 6);
        TerrainIntentV2.Feature shared = canyon("shared-canyon", 12, 20, 40, 64, 20, 36,
                TerrainIntentV2.CanyonCrossSection.V, 0, 0);
        List<TerrainIntentV2.Relation> crossing = List.of(
                within("a-in", "river-a", "shared-canyon"),
                within("b-in", "river-b", "shared-canyon"));
        MeanderingRiverPlanV2 planA = compileRiver(riverA, 129, 193);
        MeanderingRiverPlanV2 planB = compileRiver(riverB, 129, 193);
        CanyonGenerationException cross = assertThrows(CanyonGenerationException.class,
                () -> compileCanyon(shared, List.of(planA, planB), crossing, 129, 193));
        assertEquals("v2.canyon-crossing", cross.ruleId());

        Fixture fixture = compilePair(
                river("blocked-river", 4, 6),
                canyon("blocked-canyon", 10, 14, 30, 48, 16, 28,
                        TerrainIntentV2.CanyonCrossSection.V, 0, 0),
                97, 129);
        CanyonGeneratorV2 generator = new CanyonGeneratorV2(fixture.canyon());
        MeanderingRiverPlanV2.CenterlineSample mid = fixture.canyon().centerline()
                .get(fixture.canyon().centerline().size() / 2);
        int midX = Math.toIntExact(mid.xMillionths() / CanyonFixedMathV2.FIXED_SCALE);
        int midZ = Math.toIntExact(mid.zMillionths() / CanyonFixedMathV2.FIXED_SCALE);
        BitSet blocked = new BitSet();
        blocked.set(midZ * fixture.canyon().width() + midX);
        CanyonGenerationException owner = assertThrows(CanyonGenerationException.class,
                () -> generator.sampleAt(midX, midZ, blocked::get));
        assertEquals("v2.canyon-owner-conflict", owner.ruleId());

        CanyonGenerationException budget = assertThrows(CanyonGenerationException.class,
                () -> generator.renderWindow(0, 0, fixture.canyon().width(), fixture.canyon().length(),
                        fixture.canyon().supportRadiusXZ(), index -> false, () -> false));
        assertTrue(budget.ruleId().equals("v2.canyon-window")
                || budget.ruleId().equals("v2.canyon-budget")
                || budget.ruleId().equals("v2.canyon-halo"));
    }

    @Test
    void candidateOrderDoesNotChangeChecksum() {
        Fixture first = compilePair(
                river("ordered-river", 4, 6),
                canyon("ordered-canyon", 10, 14, 30, 48, 16, 28,
                        TerrainIntentV2.CanyonCrossSection.V, 0, 0),
                129, 129);
        Fixture second = compilePair(
                river("ordered-river", 4, 6),
                canyon("ordered-canyon", 10, 14, 30, 48, 16, 28,
                        TerrainIntentV2.CanyonCrossSection.V, 0, 0),
                129, 129);
        assertEquals(first.canyon(), second.canyon());
        assertEquals(
                new CanyonGeneratorV2(first.canyon()).fieldChecksums(index -> false, () -> false),
                new CanyonGeneratorV2(second.canyon()).fieldChecksums(index -> false, () -> false));
    }

    private static Fixture compilePair(
            TerrainIntentV2.Feature riverFeature,
            TerrainIntentV2.Feature canyonFeature,
            int width,
            int length
    ) {
        MeanderingRiverPlanV2 river = compileRiver(riverFeature, width, length);
        CanyonPlanV2 canyon = compileCanyon(
                canyonFeature,
                List.of(river),
                List.of(within("river-in-canyon", riverFeature.id(), canyonFeature.id())),
                width,
                length);
        return new Fixture(river, canyon);
    }

    private static MeanderingRiverPlanV2 compileRiver(TerrainIntentV2.Feature feature, int width, int length) {
        return new MeanderingRiverPlanCompilerV2().compile(
                feature,
                new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                "b".repeat(64));
    }

    private static CanyonPlanV2 compileCanyon(
            TerrainIntentV2.Feature canyon,
            List<MeanderingRiverPlanV2> rivers,
            List<TerrainIntentV2.Relation> relations,
            int width,
            int length
    ) {
        List<TerrainIntentV2.Feature> features = new ArrayList<>();
        features.add(canyon);
        for (TerrainIntentV2.Relation relation : relations) {
            String fromId = relation.from().substring("feature:".length());
            features.add(river(fromId, 4, 6));
        }
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "canyon-fixture",
                "canyon",
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
                TerrainIntentV2.Provenance.confirmedManual("canyon-test"));
        return new CanyonPlanCompilerV2().compile(
                canyon,
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
                TerrainIntentV2.Provenance.confirmedManual("canyon-test"));
    }

    private static TerrainIntentV2.Feature canyon(
            String id,
            int floorMin,
            int floorMax,
            int rimMin,
            int rimMax,
            int depthMin,
            int depthMax,
            TerrainIntentV2.CanyonCrossSection crossSection,
            int terraceCount,
            int terraceWidth
    ) {
        return new TerrainIntentV2.Feature(
                id,
                TerrainIntentV2.FeatureKind.CANYON,
                sharedSpline(),
                new TerrainIntentV2.CanyonParameters(
                        new TerrainIntentV2.IntRange(floorMin, floorMax),
                        new TerrainIntentV2.IntRange(rimMin, rimMax),
                        new TerrainIntentV2.IntRange(depthMin, depthMax),
                        crossSection,
                        terraceCount,
                        terraceWidth),
                0,
                TerrainIntentV2.Provenance.confirmedManual("canyon-test"));
    }

    private static TerrainIntentV2.SplineGeometry sharedSpline() {
        return new TerrainIntentV2.SplineGeometry(List.of(
                new TerrainIntentV2.Point2(500_000, 50_000),
                new TerrainIntentV2.Point2(420_000, 350_000),
                new TerrainIntentV2.Point2(580_000, 650_000),
                new TerrainIntentV2.Point2(500_000, 920_000)),
                TerrainIntentV2.Interpolation.POLYLINE);
    }

    private static TerrainIntentV2.Relation within(String id, String riverId, String canyonId) {
        return new TerrainIntentV2.Relation(
                id,
                TerrainIntentV2.RelationKind.WITHIN,
                "feature:" + riverId,
                "feature:" + canyonId,
                TerrainIntentV2.Strength.HARD);
    }

    private static Map<Long, CanyonWindowV2> renderTiles(
            CanyonGeneratorV2 generator,
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
        Map<Long, CanyonWindowV2> rendered = new ConcurrentHashMap<>();
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

    private static CanyonGeneratorV2.CellSource tiledSource(
            CanyonGeneratorV2 generator,
            Map<Long, CanyonWindowV2> tiles,
            int tileSize
    ) {
        return (x, z) -> {
            int originX = (x / tileSize) * tileSize;
            int originZ = (z / tileSize) * tileSize;
            CanyonWindowV2 window = tiles.get(tileKey(originX, originZ));
            CanyonGeneratorV2.CanyonSample sample = generator.sampleAt(x, z, index -> false);
            for (CanyonGeneratorV2.CanyonField field : CanyonGeneratorV2.CanyonField.values()) {
                assertEquals(sample.rawValue(field), window.rawValueAt(field, x, z));
            }
            return sample;
        };
    }

    private static long tileKey(int originX, int originZ) {
        return (((long) originX) << 32) | (originZ & 0xffffffffL);
    }

    private record Fixture(MeanderingRiverPlanV2 river, CanyonPlanV2 canyon) {
    }
}
