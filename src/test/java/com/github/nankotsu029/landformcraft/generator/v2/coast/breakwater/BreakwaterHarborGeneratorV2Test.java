package com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.BreakwaterHarborPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

class BreakwaterHarborGeneratorV2Test {
    private static final Path COASTAL = Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");

    @Test
    void generatesStableArmsCrestFoundationAndClearOpening() throws IOException {
        BreakwaterHarborGeneratorV2 generator = generator();
        BreakwaterHarborPlanV2 plan = generator.plan();

        assertEquals(List.of("east-arm", "west-arm"), plan.arms().stream()
                .map(BreakwaterHarborPlanV2.ArmPlan::armId).toList());
        assertEquals(List.of(1, 2), plan.arms().stream()
                .map(BreakwaterHarborPlanV2.ArmPlan::armOrder).toList());
        assertEquals(25, plan.supportRadiusXZ());
        assertTrue(Math.abs(plan.actualClearOpeningWidthMillionths() - 28_000_000L) <= 500_000L);

        BreakwaterHarborGeneratorV2.BreakwaterSample westArm = generator.sampleAt(184, 192);
        assertEquals(BreakwaterHarborGeneratorV2.BreakwaterRegion.CREST, westArm.region());
        assertEquals(2, westArm.armIndex());
        assertEquals(53_000_000, westArm.topHeightMillionths());

        BreakwaterHarborGeneratorV2.BreakwaterSample opening = generator.sampleAt(207, 212);
        assertEquals(BreakwaterHarborGeneratorV2.BreakwaterRegion.OUTSIDE, opening.region());

        BreakwaterHarborGeneratorV2.BreakwaterMetrics metrics = generator.evaluate(() -> false);
        assertTrue(metrics.crestCells() > 0);
        assertTrue(metrics.innerFoundationCells() > 0);
        assertTrue(metrics.outerFoundationCells() > 0);
        assertTrue(metrics.solidBlocks() > 0
                && metrics.solidBlocks() <= BreakwaterHarborGeneratorV2.MAXIMUM_SOLID_BLOCKS);
    }

    @Test
    void wholeAndTilesMatchAcrossOrderThreadsLocaleAndTimezone() throws Exception {
        BreakwaterHarborGeneratorV2 generator = generator();
        Map<BreakwaterHarborGeneratorV2.BreakwaterField, String> whole =
                generator.fieldChecksums(() -> false);

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Map<Long, BreakwaterWindowV2> forward = renderTiles(generator, 64, false, 1);
            Map<Long, BreakwaterWindowV2> reverseParallel = renderTiles(generator, 64, true, 4);
            assertEquals(whole, generator.fieldChecksumsFrom(
                    tiledSource(generator, forward, 64), () -> false));
            assertEquals(whole, generator.fieldChecksumsFrom(
                    tiledSource(generator, reverseParallel, 64), () -> false));

            for (int seam : List.of(64, 128, 192, 256, 320, 384)) {
                for (int z = 0; z < generator.length(); z += 13) {
                    assertEquals(generator.sampleAt(seam - 1, z),
                            tiledSource(generator, reverseParallel, 64).sampleAt(seam - 1, z));
                    if (seam < generator.width()) {
                        assertEquals(generator.sampleAt(seam, z),
                                tiledSource(generator, reverseParallel, 64).sampleAt(seam, z));
                    }
                }
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void boundedWindowStaysInsideMemoryAndFoundationHaloBudgets() throws IOException {
        BreakwaterHarborGeneratorV2 generator = generator();
        BreakwaterWindowV2 window = generator.renderWindow(
                72, 72, 256, 256, generator.plan().supportRadiusXZ(), () -> false);

        assertEquals(BreakwaterHarborGeneratorV2.estimateWindowRetainedBytes(
                window.bounds().width(), window.bounds().length()), window.estimatedRetainedBytes());
        assertTrue(window.estimatedRetainedBytes() < BreakwaterHarborGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        assertTrue(generator.plan().supportRadiusXZ() <= BreakwaterHarborGeneratorV2.MAXIMUM_HALO_XZ);
        BreakwaterGenerationException failure = assertThrows(BreakwaterGenerationException.class,
                () -> BreakwaterHarborGeneratorV2.addSolidBlocks(
                        BreakwaterHarborGeneratorV2.MAXIMUM_SOLID_BLOCKS, 1L));
        assertEquals("v2.breakwater-block-budget", failure.ruleId());
    }

    @Test
    void rejectsAStableSubgeometryIdThatIsNotAnEndpointOwnedByTheBreakwater() throws IOException {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        WorldBlueprintV2 blueprint = compileBlueprint(intent);
        TerrainIntentV2.Feature original = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR)
                .findFirst().orElseThrow();
        TerrainIntentV2.BreakwaterHarborParameters parameters =
                (TerrainIntentV2.BreakwaterHarborParameters) original.parameters();
        TerrainIntentV2.Feature invalid = new TerrainIntentV2.Feature(
                original.id(), original.kind(), original.geometry(),
                new TerrainIntentV2.BreakwaterHarborParameters(
                        parameters.crestWidthBlocks(), parameters.crestAboveWaterBlocks(),
                        parameters.outerDepthBlocks(), parameters.crestProfile(), parameters.foundationProfile(),
                        parameters.foundationSideSlopeRunPerRiseMillionths(),
                        new TerrainIntentV2.HarborOpening(
                                List.of("west-arm", "east-opening"), parameters.opening().widthBlocks(),
                                parameters.opening().measurement()),
                        parameters.innerSide()),
                original.priority(), original.provenance());
        CoastalFeaturePlanV2 coastalPlan = blueprint.coastalFeaturePlans().stream()
                .filter(candidate -> candidate.featureId().equals(original.id())).findFirst().orElseThrow();

        BreakwaterGenerationException failure = assertThrows(BreakwaterGenerationException.class,
                () -> new BreakwaterHarborPlanCompilerV2().compile(
                        invalid, intent, coastalPlan, blueprint.harborBasinPlans().getFirst(),
                        blueprint.space().bounds()));
        assertEquals("v2.breakwater-subgeometry", failure.ruleId());
    }

    private static Map<Long, BreakwaterWindowV2> renderTiles(
            BreakwaterHarborGeneratorV2 generator,
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
        Map<Long, BreakwaterWindowV2> result = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Tile tile : tiles) {
                futures.add(executor.submit(() -> {
                    result.put(key(tile.x(), tile.z()), generator.renderWindow(
                            tile.x(), tile.z(), tile.width(), tile.length(),
                            generator.plan().supportRadiusXZ(), () -> false));
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return Map.copyOf(result);
    }

    private static BreakwaterHarborGeneratorV2.CellSource tiledSource(
            BreakwaterHarborGeneratorV2 generator,
            Map<Long, BreakwaterWindowV2> windows,
            int tileSize
    ) {
        return (x, z) -> {
            BreakwaterWindowV2 window = windows.get(key(
                    x / tileSize * tileSize, z / tileSize * tileSize));
            int region = window.rawValueAt(BreakwaterHarborGeneratorV2.BreakwaterField.REGION, x, z);
            return new BreakwaterHarborGeneratorV2.BreakwaterSample(
                    BreakwaterHarborGeneratorV2.BreakwaterRegion.values()[region],
                    window.rawValueAt(BreakwaterHarborGeneratorV2.BreakwaterField.ARM_INDEX, x, z),
                    window.rawValueAt(BreakwaterHarborGeneratorV2.BreakwaterField.TOP_HEIGHT, x, z),
                    window.rawValueAt(BreakwaterHarborGeneratorV2.BreakwaterField.BOTTOM_HEIGHT, x, z));
        };
    }

    private static BreakwaterHarborGeneratorV2 generator() throws IOException {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        WorldBlueprintV2 blueprint = compileBlueprint(intent);
        BreakwaterHarborPlanV2 plan = blueprint.breakwaterHarborPlans().getFirst();
        CoastalFeaturePlanV2 coastalPlan = blueprint.coastalFeaturePlans().stream()
                .filter(candidate -> candidate.featureId().equals(plan.featureId())).findFirst().orElseThrow();
        return new BreakwaterHarborGeneratorV2(plan, coastalPlan, 400, 400);
    }

    private static WorldBlueprintV2 compileBlueprint(TerrainIntentV2 intent) {
        return new DiagnosticBlueprintCompilerV2().compile(
                new DiagnosticCompileRequestV2(
                        intent.intentId(), new GenerationBounds(400, 400, -64, 255, 50),
                        128, 827413L, "a".repeat(64), DiagnosticCompileRequestV2.defaultBudget()), intent);
    }

    private static long key(int x, int z) {
        return ((long) z << 32) | (x & 0xffff_ffffL);
    }

    private record Tile(int x, int z, int width, int length) { }
}
