package com.github.nankotsu029.landformcraft.generator.v2.landform.reef;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.CoralReefPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
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
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoralReefGeneratorV2Test {
    private static final String GEOMETRY_CHECKSUM = "c".repeat(64);
    private static final Path CORAL_SCENARIO =
            Path.of("examples/v2/diagnostic/scenarios/coral-reef.terrain-intent-v2.json");

    @Test
    void compilesReefWithLagoonPassAndMarineConnection() throws Exception {
        Fixture fixture = fixture(129, 193, false, false);
        CoralReefGeneratorV2 generator = new CoralReefGeneratorV2(fixture.plan());
        CoralReefGeneratorV2.CoralReefMetrics metrics = generator.evaluate(index -> false, () -> false);

        assertFalse(fixture.plan().passHooks().isEmpty());
        assertTrue(metrics.marineConnected());
        assertFalse(metrics.sealedLagoon());
        assertEquals(1, metrics.passCount());
        assertTrue(metrics.reefCellCount() > 0L);
        assertTrue(metrics.lagoonCellCount() > 0L);
        assertTrue(metrics.passCorridorCellCount() > 0L);
        assertTrue(metrics.crestDepthMinBlocks() >= fixture.plan().minimumCrestDepthBlocks());
        assertTrue(metrics.lagoonDepthBlocks() >= fixture.plan().minimumLagoonDepthBlocks());
    }

    @Test
    void wholeAndTileFieldsMatchAcrossSeamsOrderThreadsPathOrderLocaleAndTimezone() throws Exception {
        Fixture fixture = fixture(129, 193, false, false);
        CoralReefGeneratorV2 generator = new CoralReefGeneratorV2(fixture.plan());
        IntPredicate noConflict = index -> false;
        Map<CoralReefGeneratorV2.CoralReefField, String> direct =
                generator.fieldChecksums(noConflict, () -> false);
        Map<Long, CoralReefWindowV2> forward = renderTiles(generator, 64, false, 1, noConflict);
        Map<Long, CoralReefWindowV2> reverse = renderTiles(generator, 64, true, 1, noConflict);
        Map<Long, CoralReefWindowV2> parallel = renderTiles(generator, 64, true, 4, noConflict);

        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(forward, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(reverse, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(parallel, 64), () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Fixture reordered = fixture(129, 193, true, false);
            assertEquals(fixture.plan(), reordered.plan());
            assertEquals(direct, new CoralReefGeneratorV2(reordered.plan()).fieldChecksums(noConflict, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void rejectsSoftPassRelationsHardConflictAndSealedLagoonMetrics() throws Exception {
        CoralReefGenerationException softPass = assertThrows(CoralReefGenerationException.class,
                () -> compileReef(fixture(129, 193, false, true).intent(), 129, 193));
        assertEquals("v2.reef-pass-hook", softPass.ruleId());

        Fixture fixture = fixture(129, 193, false, false);
        CoralReefGeneratorV2 generator = new CoralReefGeneratorV2(fixture.plan());
        int blocked = findReefIndex(generator);
        int x = blocked % fixture.plan().width();
        int z = blocked / fixture.plan().width();
        CoralReefGenerationException conflict = assertThrows(CoralReefGenerationException.class,
                () -> generator.sampleAt(x, z, index -> index == blocked));
        assertEquals("v2.reef-hard-boundary-conflict", conflict.ruleId());

        Fixture sealed = sealedFixture();
        CoralReefGeneratorV2.CoralReefMetrics sealedMetrics =
                new CoralReefGeneratorV2(sealed.plan()).evaluate(index -> false, () -> false);
        assertTrue(sealedMetrics.sealedLagoon());
        assertEquals(0, sealedMetrics.passCount());
    }

    @Test
    void rejectsUnknownFutureParametersAtParse() throws Exception {
        String valid = Files.readString(CORAL_SCENARIO);
        LandformV2DataCodec codec = new LandformV2DataCodec();
        assertThrows(Exception.class, () -> codec.readTerrainIntent(valid.replace(
                "\"reefWidthBlocks\": { \"min\": 18, \"max\": 46 }",
                "\"reefWidthBlocks\": { \"min\": 18, \"max\": 46 }, \"futureParam\": 1"), "future-param"));
    }

    private static int findReefIndex(CoralReefGeneratorV2 generator) {
        for (int z = 0; z < generator.length(); z++) {
            for (int x = 0; x < generator.width(); x++) {
                if (generator.sampleAt(x, z, index -> false).reefMask() == 1) {
                    return z * generator.width() + x;
                }
            }
        }
        throw new AssertionError("no reef cell found");
    }

    private static Map<Long, CoralReefWindowV2> renderTiles(
            CoralReefGeneratorV2 generator,
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
        if (reverse) {
            Collections.reverse(tiles);
        }
        Map<Long, CoralReefWindowV2> result = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (long[] tile : tiles) {
                futures.add(executor.submit(() -> {
                    CoralReefWindowV2 window = generator.renderWindow(
                            Math.toIntExact(tile[0]),
                            Math.toIntExact(tile[1]),
                            Math.toIntExact(tile[2]),
                            Math.toIntExact(tile[3]),
                            0,
                            hardLandConflict,
                            () -> false);
                    result.put((tile[0] << 32) | (tile[1] & 0xffffffffL), window);
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static CoralReefGeneratorV2.CellSource tiledSource(Map<Long, CoralReefWindowV2> tiles, int tileSize) {
        return (x, z) -> {
            int originX = (x / tileSize) * tileSize;
            int originZ = (z / tileSize) * tileSize;
            CoralReefWindowV2 window = tiles.get((((long) originX) << 32) | (originZ & 0xffffffffL));
            if (window == null) {
                throw new IllegalStateException("missing coral reef tile");
            }
            return new CoralReefGeneratorV2.CoralReefSample(
                    window.rawValueAt(CoralReefGeneratorV2.CoralReefField.REEF_MASK, x, z),
                    window.rawValueAt(CoralReefGeneratorV2.CoralReefField.CREST_DEPTH, x, z),
                    window.rawValueAt(CoralReefGeneratorV2.CoralReefField.LAGOON_DEPTH, x, z),
                    window.rawValueAt(CoralReefGeneratorV2.CoralReefField.PASS_CORRIDOR, x, z),
                    window.rawValueAt(CoralReefGeneratorV2.CoralReefField.MARINE_CONNECTION, x, z));
        };
    }

    private static Fixture fixture(int width, int length, boolean reverseFeatureOrder, boolean softPassOnly)
            throws Exception {
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(CORAL_SCENARIO);
        List<TerrainIntentV2.Feature> features = new ArrayList<>(intent.features());
        List<TerrainIntentV2.Relation> relations = new ArrayList<>(intent.relations());
        if (softPassOnly) {
            relations.removeIf(relation -> "pass-cuts-reef".equals(relation.id()));
            relations.add(new TerrainIntentV2.Relation(
                    "pass-cuts-reef",
                    TerrainIntentV2.RelationKind.CARVES_THROUGH,
                    "feature:reef-pass",
                    "feature:ring-reef",
                    TerrainIntentV2.Strength.SOFT,
                    TerrainIntentV2.TransitionPolicy.NONE));
        }
        if (reverseFeatureOrder) {
            Collections.reverse(features);
        }
        intent = new TerrainIntentV2(
                intent.intentVersion(),
                intent.intentId(),
                intent.theme(),
                intent.coordinateSystem(),
                features,
                relations,
                intent.constraints(),
                intent.environment(),
                intent.mapReferences(),
                intent.structures(),
                intent.provenance());
        CoralReefPlanV2 plan = softPassOnly
                ? null
                : compileReef(intent, width, length);
        return new Fixture(intent, plan);
    }

    private static Fixture sealedFixture() throws Exception {
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(CORAL_SCENARIO);
        List<TerrainIntentV2.Feature> features = intent.features().stream()
                .filter(feature -> feature.kind() != TerrainIntentV2.FeatureKind.REEF_PASS)
                .toList();
        List<TerrainIntentV2.Relation> relations = intent.relations().stream()
                .filter(relation -> !relation.from().equals("feature:reef-pass"))
                .toList();
        intent = new TerrainIntentV2(
                intent.intentVersion(),
                intent.intentId(),
                intent.theme(),
                intent.coordinateSystem(),
                features,
                relations,
                intent.constraints(),
                intent.environment(),
                intent.mapReferences(),
                intent.structures(),
                intent.provenance());
        return new Fixture(intent, compileReef(intent, 129, 193));
    }

    private static CoralReefPlanV2 compileReef(TerrainIntentV2 intent, int width, int length) {
        return new CoralReefPlanCompilerV2().compile(
                intent.features().stream()
                        .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.CORAL_REEF)
                        .findFirst()
                        .orElseThrow(),
                intent,
                new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                GEOMETRY_CHECKSUM);
    }

    private record Fixture(TerrainIntentV2 intent, CoralReefPlanV2 plan) {
    }
}
