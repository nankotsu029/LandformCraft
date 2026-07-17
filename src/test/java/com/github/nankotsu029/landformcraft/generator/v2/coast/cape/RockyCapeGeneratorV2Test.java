package com.github.nankotsu029.landformcraft.generator.v2.coast.cape;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompilationException;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.RockyCapePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RockyCapeGeneratorV2Test {
    private static final Path COASTAL = Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");
    private static final Path VOLUME_REQUIRED = Path.of(
            "examples/v2/diagnostic/negative/rocky-cape-volume-required.terrain-intent-v2.json");

    @Test
    void generatesBoundedReliefExposureChannelsStacksAndCoastComplexity() throws IOException {
        RockyCapeGeneratorV2 generator = generator();
        RockyCapePlanV2 plan = generator.plan();
        RockyCapeGeneratorV2.CapeMetrics metrics = generator.evaluate(() -> false);

        assertEquals(20, plan.supportRadiusXZ());
        assertTrue(plan.channels().size() >= plan.minimumChannelCount()
                && plan.channels().size() <= plan.maximumChannelCount());
        assertTrue(plan.seaStacks().size() >= plan.minimumSeaStackCount()
                && plan.seaStacks().size() <= plan.maximumSeaStackCount());
        assertTrue(plan.channels().size() + plan.seaStacks().size() <= RockyCapePlanV2.MAXIMUM_DESCRIPTORS);
        assertEquals(plan.localReliefAboveSeaBlocks(), metrics.reliefAboveSeaBlocks());
        assertTrue(metrics.rockExposureMillionths() >= plan.minimumRockExposureMillionths() - 75_000);
        assertTrue(metrics.rockExposureMillionths() <= plan.maximumRockExposureMillionths() + 75_000);
        assertEquals(plan.coastlineTurningCount(), metrics.coastlineTurningCount());
        assertTrue(metrics.coastlineTurningCount() >= 3);
        assertTrue(metrics.cliffCells() > 0 && metrics.channelCells() > 0 && metrics.seaStackCells() > 0);
    }

    @Test
    void wholeAndTilesMatchAcrossOrderThreadsLocaleTimezoneAndSeams() throws Exception {
        RockyCapeGeneratorV2 generator = generator();
        Map<RockyCapeGeneratorV2.CapeField, String> whole = generator.fieldChecksums(
                HardLandWaterSourceV2.NONE, () -> false);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Map<Long, RockyCapeWindowV2> forward = renderTiles(generator, 64, false, 1);
            Map<Long, RockyCapeWindowV2> reverseParallel = renderTiles(generator, 64, true, 4);
            assertEquals(whole, generator.fieldChecksumsFrom(
                    tiledSource(forward, 64), () -> false));
            assertEquals(whole, generator.fieldChecksumsFrom(
                    tiledSource(reverseParallel, 64), () -> false));
            for (int seam : List.of(64, 128, 192, 256, 320, 384)) {
                for (int z = 0; z < generator.length(); z += 13) {
                    assertEquals(generator.sampleAt(seam - 1, z, HardLandWaterSourceV2.NONE),
                            tiledSource(reverseParallel, 64).sampleAt(seam - 1, z));
                    if (seam < generator.width()) {
                        assertEquals(generator.sampleAt(seam, z, HardLandWaterSourceV2.NONE),
                                tiledSource(reverseParallel, 64).sampleAt(seam, z));
                    }
                }
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void boundedWindowAndDescriptorsStayInsideBudgets() throws IOException {
        RockyCapeGeneratorV2 generator = generator();
        RockyCapeWindowV2 window = generator.renderWindow(
                72, 72, 256, 256, generator.plan().supportRadiusXZ(),
                HardLandWaterSourceV2.NONE, () -> false);

        assertEquals(RockyCapeGeneratorV2.estimateWindowRetainedBytes(
                window.bounds().width(), window.bounds().length()), window.estimatedRetainedBytes());
        assertTrue(window.estimatedRetainedBytes() < RockyCapeGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        assertTrue(generator.plan().supportRadiusXZ() <= RockyCapeGeneratorV2.MAXIMUM_HALO_XZ);
        RockyCapeGenerationException failure = assertThrows(RockyCapeGenerationException.class,
                () -> generator.renderWindow(0, 0, 64, 64,
                        generator.plan().supportRadiusXZ() - 1,
                        HardLandWaterSourceV2.NONE, () -> false));
        assertEquals("v2.cape-support", failure.ruleId());
    }

    @Test
    void hardMaskCannotOverwriteCapeLandOrChannel() throws IOException {
        RockyCapeGeneratorV2 generator = generator();
        Cell land = find(generator, false);
        Cell channel = find(generator, true);

        RockyCapeGenerationException landFailure = assertThrows(RockyCapeGenerationException.class,
                () -> generator.sampleAt(land.x(), land.z(), (x, z) ->
                        HardLandWaterSourceV2.Classification.WATER));
        assertEquals("v2.cape-hard-mask-conflict", landFailure.ruleId());
        RockyCapeGenerationException channelFailure = assertThrows(RockyCapeGenerationException.class,
                () -> generator.sampleAt(channel.x(), channel.z(), (x, z) ->
                        HardLandWaterSourceV2.Classification.LAND));
        assertEquals("v2.cape-hard-mask-conflict", channelFailure.ruleId());
    }

    @Test
    void rejectsThinLandBridgeAndCapeWithoutIsolatedStackSpace() throws IOException {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        TerrainIntentV2.Feature cape = cape(intent);

        TerrainIntentV2 thin = replaceCape(intent, replaceGeometry(cape, polygon(
                point(.7000, .30), point(.7075, .30), point(.7075, .70), point(.7000, .70))));
        DiagnosticCompilationException thinFailure = assertThrows(DiagnosticCompilationException.class,
                () -> compileBlueprint(thin));
        assertEquals("v2.cape-thin-land-bridge", thinFailure.ruleId());

        TerrainIntentV2 noOffshore = replaceCape(intent, replaceGeometry(cape, polygon(
                point(.9600, .20), point(1.0000, .20), point(1.0000, .80), point(.9600, .80))));
        DiagnosticCompilationException stackFailure = assertThrows(DiagnosticCompilationException.class,
                () -> compileBlueprint(noOffshore));
        assertEquals("v2.cape-isolated-stack", stackFailure.ruleId());
    }

    @Test
    void diagnosesVolumeRequirementAndRejectsUnknownCapeParameter() throws IOException {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        TerrainIntentV2 volumeIntent = codec.readTerrainIntent(VOLUME_REQUIRED);
        DiagnosticCompilationException volumeFailure = assertThrows(DiagnosticCompilationException.class,
                () -> compileBlueprint(volumeIntent));
        assertEquals("v2.cape-volume-required", volumeFailure.ruleId());

        String valid = Files.readString(COASTAL);
        String unknown = valid.replace("\"capeMode\": \"TWO_POINT_FIVE_D_ONLY\"",
                "\"capeMode\": \"TWO_POINT_FIVE_D_ONLY\", \"unknownCapeParameter\": true");
        assertThrows(StructuredDataValidationException.class,
                () -> codec.readTerrainIntent(unknown, "unknown-cape-parameter"));
    }

    private static Cell find(RockyCapeGeneratorV2 generator, boolean channel) {
        for (int z = 0; z < generator.length(); z++) {
            for (int x = 0; x < generator.width(); x++) {
                RockyCapeGeneratorV2.CapeRegion region = generator.sampleAt(
                        x, z, HardLandWaterSourceV2.NONE).region();
                if (channel == (region == RockyCapeGeneratorV2.CapeRegion.CHANNEL)
                        && region != RockyCapeGeneratorV2.CapeRegion.OUTSIDE) {
                    return new Cell(x, z);
                }
            }
        }
        throw new AssertionError("required cape cell not found");
    }

    private static Map<Long, RockyCapeWindowV2> renderTiles(
            RockyCapeGeneratorV2 generator, int tileSize, boolean reverse, int threads
    ) throws Exception {
        List<Tile> tiles = new ArrayList<>();
        for (int z = 0; z < generator.length(); z += tileSize) {
            for (int x = 0; x < generator.width(); x += tileSize) {
                tiles.add(new Tile(x, z, Math.min(tileSize, generator.width() - x),
                        Math.min(tileSize, generator.length() - z)));
            }
        }
        if (reverse) Collections.reverse(tiles);
        Map<Long, RockyCapeWindowV2> result = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Tile tile : tiles) {
                futures.add(executor.submit(() -> {
                    result.put(key(tile.x(), tile.z()), generator.renderWindow(
                            tile.x(), tile.z(), tile.width(), tile.length(),
                            generator.plan().supportRadiusXZ(), HardLandWaterSourceV2.NONE, () -> false));
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return Map.copyOf(result);
    }

    private static RockyCapeGeneratorV2.CellSource tiledSource(
            Map<Long, RockyCapeWindowV2> windows, int tileSize
    ) {
        return (x, z) -> {
            RockyCapeWindowV2 window = windows.get(key(
                    x / tileSize * tileSize, z / tileSize * tileSize));
            int region = window.rawValueAt(RockyCapeGeneratorV2.CapeField.REGION, x, z);
            return new RockyCapeGeneratorV2.CapeSample(
                    RockyCapeGeneratorV2.CapeRegion.values()[region],
                    window.rawValueAt(RockyCapeGeneratorV2.CapeField.SURFACE_HEIGHT, x, z),
                    window.rawValueAt(RockyCapeGeneratorV2.CapeField.ROCK_EXPOSURE, x, z),
                    window.rawValueAt(RockyCapeGeneratorV2.CapeField.DESCRIPTOR_INDEX, x, z), false);
        };
    }

    private static RockyCapeGeneratorV2 generator() throws IOException {
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(COASTAL);
        WorldBlueprintV2 blueprint = compileBlueprint(intent);
        RockyCapePlanV2 plan = blueprint.rockyCapePlans().getFirst();
        CoastalFeaturePlanV2 coastalPlan = blueprint.coastalFeaturePlans().stream()
                .filter(candidate -> candidate.featureId().equals(plan.featureId())).findFirst().orElseThrow();
        return new RockyCapeGeneratorV2(plan, coastalPlan, 400, 400);
    }

    private static WorldBlueprintV2 compileBlueprint(TerrainIntentV2 intent) {
        return new DiagnosticBlueprintCompilerV2().compile(
                new DiagnosticCompileRequestV2(
                        intent.intentId(), new GenerationBounds(400, 400, -64, 255, 50),
                        128, 827413L, "a".repeat(64), DiagnosticCompileRequestV2.defaultBudget()), intent);
    }

    private static TerrainIntentV2.Feature cape(TerrainIntentV2 intent) {
        return intent.features().stream().filter(
                feature -> feature.kind() == TerrainIntentV2.FeatureKind.ROCKY_CAPE).findFirst().orElseThrow();
    }

    private static TerrainIntentV2 replaceCape(TerrainIntentV2 intent, TerrainIntentV2.Feature replacement) {
        List<TerrainIntentV2.Feature> features = intent.features().stream()
                .map(feature -> feature.id().equals(replacement.id()) ? replacement : feature).toList();
        return new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(), features,
                intent.relations(), intent.constraints(), intent.environment(), intent.mapReferences(),
                intent.structures(), intent.provenance());
    }

    private static TerrainIntentV2.Feature replaceGeometry(
            TerrainIntentV2.Feature original, TerrainIntentV2.PolygonGeometry geometry
    ) {
        return new TerrainIntentV2.Feature(
                original.id(), original.kind(), geometry, original.parameters(),
                original.priority(), original.provenance());
    }

    private static TerrainIntentV2.PolygonGeometry polygon(TerrainIntentV2.Point2... points) {
        List<TerrainIntentV2.Point2> ring = new ArrayList<>(List.of(points));
        ring.add(points[0]);
        return new TerrainIntentV2.PolygonGeometry(List.of(List.copyOf(ring)));
    }

    private static TerrainIntentV2.Point2 point(double x, double z) {
        return new TerrainIntentV2.Point2(
                Math.toIntExact(Math.round(x * TerrainIntentV2.FIXED_SCALE)),
                Math.toIntExact(Math.round(z * TerrainIntentV2.FIXED_SCALE)));
    }

    private static long key(int x, int z) {
        return ((long) z << 32) | (x & 0xffff_ffffL);
    }

    private record Tile(int x, int z, int width, int length) { }
    private record Cell(int x, int z) { }
}
