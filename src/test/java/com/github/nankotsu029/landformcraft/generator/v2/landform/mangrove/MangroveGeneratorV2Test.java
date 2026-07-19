package com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.TidalChannelGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.TidalChannelPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.MangroveWetlandPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TidalChannelPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MangroveGeneratorV2Test {
    private static final String GEOMETRY_CHECKSUM = "b".repeat(64);
    private static final Path MANGROVE_SCENARIO =
            Path.of("examples/v2/diagnostic/scenarios/mangrove-wetland.terrain-intent-v2.json");

    @Test
    void compilesWetlandWithTidalHookAndProtectsChannels() {
        Fixture fixture = fixture(129, 193, false, false, false);
        MangroveWetlandPlanV2 plan = fixture.mangrovePlan();
        TidalChannelGeneratorV2 tidal = new TidalChannelGeneratorV2(fixture.tidalPlan());
        MangroveGeneratorV2 generator = new MangroveGeneratorV2(plan);
        BiPredicate<Integer, Integer> channelAt = channelMask(tidal);

        assertNotNull(plan.tidalNetworkHook());
        MangroveGeneratorV2.MangroveMetrics metrics =
                generator.evaluate(index -> false, channelAt, () -> false);
        assertTrue(metrics.marineConnected());
        assertTrue(metrics.wetlandCellCount() > 0L);
        assertTrue(metrics.openWaterGapCount() > 0L);

        for (int z = 0; z < plan.length(); z++) {
            for (int x = 0; x < plan.width(); x++) {
                if (!channelAt.test(x, z)) continue;
                final int sampleX = x;
                final int sampleZ = z;
                MangroveGeneratorV2.MangroveSample sample =
                        generator.sampleAt(sampleX, sampleZ, index -> false, () -> channelAt.test(sampleX, sampleZ));
                if (sample.wetlandMask() != 1) continue;
                assertEquals(1, sample.openWaterGap());
                assertEquals(plan.waterLevel(), sample.surfaceHeightBlocks());
                assertEquals(0, sample.substrateClass());
                assertEquals(0, sample.microReliefBlocks());
            }
        }

        MangroveGeneratorV2.MangroveSample land = findLandCell(generator, channelAt);
        assertEquals(MangroveWetlandPlanV2.SUBSTRATE_SEDIMENT_WET, land.substrateClass());
        assertEquals(0, land.openWaterGap());
    }

    @Test
    void wholeAndTileFieldsMatchAcrossSeamsOrderThreadsPathOrderLocaleAndTimezone() throws Exception {
        Fixture fixture = fixture(257, 193, false, false, false);
        TidalChannelGeneratorV2 tidal = new TidalChannelGeneratorV2(fixture.tidalPlan());
        BiPredicate<Integer, Integer> channelAt = channelMask(tidal);
        MangroveGeneratorV2 generator = new MangroveGeneratorV2(fixture.mangrovePlan());
        IntPredicate noConflict = index -> false;
        Map<MangroveGeneratorV2.MangroveField, String> direct =
                generator.fieldChecksums(noConflict, channelAt, () -> false);
        Map<Long, MangroveWindowV2> forward = renderTiles(generator, 64, false, 1, noConflict, channelAt);
        Map<Long, MangroveWindowV2> reverse = renderTiles(generator, 64, true, 1, noConflict, channelAt);
        Map<Long, MangroveWindowV2> parallel = renderTiles(generator, 64, true, 4, noConflict, channelAt);

        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(forward, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(reverse, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(parallel, 64), () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Fixture reordered = fixture(257, 193, true, false, false);
            assertEquals(fixture.mangrovePlan(), reordered.mangrovePlan());
            assertEquals(direct, new MangroveGeneratorV2(reordered.mangrovePlan())
                    .fieldChecksums(noConflict, channelAt, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void rejectsUnknownTidalSoftWithinAndHardMaskConflict() {
        MangroveGenerationException softWithin = assertThrows(MangroveGenerationException.class,
                () -> compileMangrove(fixture(129, 193, false, true, false).intent(), 129, 193));
        assertEquals("v2.mangrove-tidal-hook", softWithin.ruleId());

        MangroveGenerationException unknownTidal = assertThrows(MangroveGenerationException.class,
                () -> compileMangrove(fixture(129, 193, false, false, true).intent(), 129, 193));
        assertEquals("v2.mangrove-tidal-hook", unknownTidal.ruleId());

        Fixture fixture = fixture(129, 193, false, false, false);
        MangroveGeneratorV2 generator = new MangroveGeneratorV2(fixture.mangrovePlan());
        BiPredicate<Integer, Integer> channelAt = channelMask(new TidalChannelGeneratorV2(fixture.tidalPlan()));
        int blocked = findWetlandIndex(generator, channelAt);
        int x = blocked % fixture.mangrovePlan().width();
        int z = blocked / fixture.mangrovePlan().width();
        MangroveGenerationException conflict = assertThrows(MangroveGenerationException.class,
                () -> generator.sampleAt(x, z, index -> index == blocked, () -> channelAt.test(x, z)));
        assertEquals("v2.mangrove-hard-boundary-conflict", conflict.ruleId());
    }

    @Test
    void rejectsDryWetlandWithoutTidalHookOrOpenWater() {
        MangroveGenerationException dry = null;
        for (int attempt = 0; attempt < 2_048; attempt++) {
            Fixture fixture = dryFixture(attempt);
            MangroveGeneratorV2 generator = new MangroveGeneratorV2(fixture.mangrovePlan());
            try {
                generator.evaluate(index -> false, (x, z) -> false, () -> false);
            } catch (MangroveGenerationException exception) {
                if ("v2.mangrove-dry-wetland".equals(exception.ruleId())) {
                    dry = exception;
                    break;
                }
                throw exception;
            }
        }
        assertNotNull(dry);
        assertEquals("v2.mangrove-dry-wetland", dry.ruleId());
    }

    @Test
    void rejectsFilledChannelSurfaceRaiseAttempt() {
        Fixture fixture = fixture(129, 193, false, false, false);
        MangroveGeneratorV2 generator = new MangroveGeneratorV2(fixture.mangrovePlan());
        BiPredicate<Integer, Integer> channelAt =
                channelMask(new TidalChannelGeneratorV2(fixture.tidalPlan()));
        for (int z = 0; z < fixture.mangrovePlan().length(); z++) {
            for (int x = 0; x < fixture.mangrovePlan().width(); x++) {
                final int sampleX = x;
                final int sampleZ = z;
                MangroveGeneratorV2.MangroveSample protectedSample =
                        generator.sampleAt(sampleX, sampleZ, index -> false, () -> channelAt.test(sampleX, sampleZ));
                if (protectedSample.wetlandMask() == 1 && protectedSample.openWaterGap() == 1) {
                    assertTrue(protectedSample.surfaceHeightBlocks() <= fixture.mangrovePlan().waterLevel());
                    assertEquals(0, protectedSample.microReliefBlocks());
                }
            }
        }
    }

    @Test
    void rejectsUnknownFutureParametersAtParse() throws Exception {
        String valid = Files.readString(MANGROVE_SCENARIO);
        LandformV2DataCodec codec = new LandformV2DataCodec();
        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(valid.replace(
                "\"microReliefBlocks\": { \"min\": 2, \"max\": 3 }",
                "\"microReliefBlocks\": { \"min\": 2, \"max\": 3 }, \"futureParam\": 1"), "future-param"));
    }

    private static MangroveGeneratorV2.MangroveSample findLandCell(
            MangroveGeneratorV2 generator,
            BiPredicate<Integer, Integer> channelAt
    ) {
        for (int z = 0; z < generator.length(); z++) {
            for (int x = 0; x < generator.width(); x++) {
                final int sampleX = x;
                final int sampleZ = z;
                MangroveGeneratorV2.MangroveSample sample =
                        generator.sampleAt(sampleX, sampleZ, index -> false, () -> channelAt.test(sampleX, sampleZ));
                if (sample.wetlandMask() == 1 && sample.openWaterGap() == 0 && sample.substrateClass() == 4) {
                    return sample;
                }
            }
        }
        throw new AssertionError("no land mangrove cell found");
    }

    private static int findWetlandIndex(MangroveGeneratorV2 generator, BiPredicate<Integer, Integer> channelAt) {
        for (int z = 0; z < generator.length(); z++) {
            for (int x = 0; x < generator.width(); x++) {
                final int sampleX = x;
                final int sampleZ = z;
                if (generator.sampleAt(sampleX, sampleZ, index -> false, () -> channelAt.test(sampleX, sampleZ))
                        .wetlandMask() == 1) {
                    return sampleZ * generator.width() + sampleX;
                }
            }
        }
        throw new AssertionError("no wetland cell found");
    }

    private static BiPredicate<Integer, Integer> channelMask(TidalChannelGeneratorV2 tidal) {
        return (x, z) -> tidal.sampleAt(x, z, index -> false).channelMask() == 1;
    }

    private static Map<Long, MangroveWindowV2> renderTiles(
            MangroveGeneratorV2 generator,
            int tileSize,
            boolean reverse,
            int threads,
            IntPredicate hardLandConflict,
            BiPredicate<Integer, Integer> channelAt
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
        Map<Long, MangroveWindowV2> result = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (long[] tile : tiles) {
                futures.add(executor.submit(() -> {
                    MangroveWindowV2 window = generator.renderWindow(
                            Math.toIntExact(tile[0]),
                            Math.toIntExact(tile[1]),
                            Math.toIntExact(tile[2]),
                            Math.toIntExact(tile[3]),
                            0,
                            hardLandConflict,
                            channelAt,
                            () -> false);
                    result.put((tile[0] << 32) | (tile[1] & 0xffffffffL), window);
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static MangroveGeneratorV2.CellSource tiledSource(Map<Long, MangroveWindowV2> tiles, int tileSize) {
        return (x, z) -> {
            int originX = (x / tileSize) * tileSize;
            int originZ = (z / tileSize) * tileSize;
            MangroveWindowV2 window = tiles.get((((long) originX) << 32) | (originZ & 0xffffffffL));
            if (window == null) {
                throw new IllegalStateException("missing mangrove tile");
            }
            return new MangroveGeneratorV2.MangroveSample(
                    window.rawValueAt(MangroveGeneratorV2.MangroveField.WETLAND_MASK, x, z),
                    window.rawValueAt(MangroveGeneratorV2.MangroveField.SURFACE_HEIGHT, x, z),
                    window.rawValueAt(MangroveGeneratorV2.MangroveField.OPEN_WATER_GAP, x, z),
                    window.rawValueAt(MangroveGeneratorV2.MangroveField.SUBSTRATE_CLASS, x, z),
                    window.rawValueAt(MangroveGeneratorV2.MangroveField.MICRO_RELIEF, x, z));
        };
    }

    private static Fixture fixture(
            int width,
            int length,
            boolean reverseFeatureOrder,
            boolean softWithinOnly,
            boolean unknownTidalParent
    ) {
        List<TerrainIntentV2.NamedPath> paths = List.of(
                new TerrainIntentV2.NamedPath(
                        "main-channel", "", "",
                        List.of(
                                new TerrainIntentV2.Point2(480_000, 1_000_000),
                                new TerrainIntentV2.Point2(510_000, 680_000),
                                new TerrainIntentV2.Point2(360_000, 200_000))),
                new TerrainIntentV2.NamedPath(
                        "east-branch", "", "",
                        List.of(
                                new TerrainIntentV2.Point2(480_000, 1_000_000),
                                new TerrainIntentV2.Point2(700_000, 520_000),
                                new TerrainIntentV2.Point2(880_000, 340_000))));
        TerrainIntentV2.Feature tidal = new TerrainIntentV2.Feature(
                "tidal-channels",
                TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK,
                new TerrainIntentV2.MultiSplineGeometry(paths, TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.TidalChannelParameters(
                        new TerrainIntentV2.IntRange(6, 12),
                        2,
                        TerrainIntentV2.TidalEdgeKind.BIDIRECTIONAL),
                0,
                TerrainIntentV2.Provenance.confirmedManual("mangrove-test"));
        TerrainIntentV2.Feature mangrove = new TerrainIntentV2.Feature(
                "mangrove-wetland",
                TerrainIntentV2.FeatureKind.MANGROVE_WETLAND,
                wetlandPolygon(),
                defaultMangroveParameters(),
                0,
                TerrainIntentV2.Provenance.confirmedManual("mangrove-test"));
        TerrainIntentV2.Feature lakeParent = new TerrainIntentV2.Feature(
                "not-tidal",
                TerrainIntentV2.FeatureKind.LAKE,
                wetlandPolygon(),
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
                TerrainIntentV2.Provenance.confirmedManual("mangrove-test"));
        List<TerrainIntentV2.Feature> features = new ArrayList<>(List.of(tidal, mangrove));
        if (unknownTidalParent) {
            features.add(lakeParent);
        }
        if (reverseFeatureOrder) {
            Collections.reverse(features);
        }
        List<TerrainIntentV2.Relation> relations = new ArrayList<>();
        relations.add(new TerrainIntentV2.Relation(
                "tidal-empties-south",
                TerrainIntentV2.RelationKind.EMPTIES_INTO,
                "feature:tidal-channels",
                "boundary:SOUTH",
                TerrainIntentV2.Strength.HARD));
        if (unknownTidalParent) {
            relations.add(new TerrainIntentV2.Relation(
                    "bad-within",
                    TerrainIntentV2.RelationKind.WITHIN,
                    "feature:not-tidal",
                    "feature:mangrove-wetland",
                    TerrainIntentV2.Strength.HARD));
        } else if (softWithinOnly) {
            relations.add(new TerrainIntentV2.Relation(
                    "channels-within-wetland",
                    TerrainIntentV2.RelationKind.WITHIN,
                    "feature:tidal-channels",
                    "feature:mangrove-wetland",
                    TerrainIntentV2.Strength.SOFT,
                    TerrainIntentV2.TransitionPolicy.NONE));
        } else {
            relations.add(new TerrainIntentV2.Relation(
                    "channels-within-wetland",
                    TerrainIntentV2.RelationKind.WITHIN,
                    "feature:tidal-channels",
                    "feature:mangrove-wetland",
                    TerrainIntentV2.Strength.HARD));
        }
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "mangrove-fixture",
                "bounded mangrove wetland fixture",
                coordinateSystem(),
                features,
                relations,
                List.of(new TerrainIntentV2.EdgeClassificationConstraint(
                        "south-is-sea",
                        TerrainIntentV2.Strength.HARD,
                        "world",
                        TerrainIntentV2.Edge.SOUTH,
                        TerrainIntentV2.EdgeClassification.SEA,
                        100_000,
                        0)),
                new TerrainIntentV2.EnvironmentDescriptor(
                        "TIDAL_MUD_AND_SILT", "WARM_HUMID_MARITIME", "MANGROVE_ESTUARY"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("mangrove-test"));
        MangroveWetlandPlanV2 mangrovePlan = softWithinOnly || unknownTidalParent
                ? null
                : compileMangrove(intent, width, length);
        TidalChannelPlanV2 tidalPlan = softWithinOnly || unknownTidalParent
                ? null
                : new TidalChannelPlanCompilerV2().compile(
                        tidal,
                        intent,
                        new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                        GEOMETRY_CHECKSUM);
        return new Fixture(intent, mangrovePlan, tidalPlan);
    }

    private static Fixture dryFixture(int attempt) {
        String featureId = String.format(Locale.ROOT, "dry-mangrove-%04d", attempt);
        TerrainIntentV2.Feature mangrove = new TerrainIntentV2.Feature(
                featureId,
                TerrainIntentV2.FeatureKind.MANGROVE_WETLAND,
                singleCellPolygon(),
                new TerrainIntentV2.MangroveWetlandParameters(
                        new TerrainIntentV2.IntRange(1, 1),
                        new TerrainIntentV2.FixedRange(100_000, 100_000)),
                0,
                TerrainIntentV2.Provenance.confirmedManual("mangrove-test"));
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "dry-mangrove-fixture",
                "dry mangrove wetland fixture",
                coordinateSystem(),
                List.of(mangrove),
                List.of(),
                List.of(),
                new TerrainIntentV2.EnvironmentDescriptor(
                        "TIDAL_MUD_AND_SILT", "WARM_HUMID_MARITIME", "MANGROVE_ESTUARY"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("mangrove-test"));
        return new Fixture(intent, compileMangrove(intent, 8, 8), null);
    }

    private static TerrainIntentV2.PolygonGeometry singleCellPolygon() {
        return new TerrainIntentV2.PolygonGeometry(List.of(List.of(
                new TerrainIntentV2.Point2(428_571, 428_571),
                new TerrainIntentV2.Point2(571_428, 428_571),
                new TerrainIntentV2.Point2(571_428, 571_428),
                new TerrainIntentV2.Point2(428_571, 571_428),
                new TerrainIntentV2.Point2(428_571, 428_571))));
    }

    private static MangroveWetlandPlanV2 compileMangrove(TerrainIntentV2 intent, int width, int length) {
        return new MangrovePlanCompilerV2().compile(
                intent.features().stream()
                        .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.MANGROVE_WETLAND)
                        .findFirst()
                        .orElseThrow(),
                intent,
                new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                GEOMETRY_CHECKSUM);
    }

    private static TerrainIntentV2.PolygonGeometry wetlandPolygon() {
        return new TerrainIntentV2.PolygonGeometry(List.of(List.of(
                new TerrainIntentV2.Point2(50_000, 100_000),
                new TerrainIntentV2.Point2(950_000, 100_000),
                new TerrainIntentV2.Point2(960_000, 1_000_000),
                new TerrainIntentV2.Point2(80_000, 1_000_000),
                new TerrainIntentV2.Point2(50_000, 100_000))));
    }

    private static TerrainIntentV2.MangroveWetlandParameters defaultMangroveParameters() {
        return new TerrainIntentV2.MangroveWetlandParameters(
                new TerrainIntentV2.IntRange(2, 3),
                new TerrainIntentV2.FixedRange(200_000, 400_000));
    }

    private static TerrainIntentV2.CoordinateSystem coordinateSystem() {
        return new TerrainIntentV2.CoordinateSystem(
                TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                TerrainIntentV2.XAxis.EAST,
                TerrainIntentV2.ZAxis.SOUTH,
                TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET);
    }

    private record Fixture(TerrainIntentV2 intent, MangroveWetlandPlanV2 mangrovePlan, TidalChannelPlanV2 tidalPlan) {
    }
}
