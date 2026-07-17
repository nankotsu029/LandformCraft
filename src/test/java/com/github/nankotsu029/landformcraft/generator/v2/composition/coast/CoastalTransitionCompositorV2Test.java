package com.github.nankotsu029.landformcraft.generator.v2.composition.coast;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalRasterKernelV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.beach.SandyBeachGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater.BreakwaterHarborGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.cape.RockyCapeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.harbor.HarborBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalTransitionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoastalTransitionCompositorV2Test {
    private static final int SCALE = 1_000_000;
    private static final Path AZURE = Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");

    @Test
    void azureFixtureBindsAllFourGeneratorsWithoutUncontractedOverlap() throws IOException {
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(AZURE);
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                new DiagnosticCompileRequestV2(
                        intent.intentId(), new GenerationBounds(400, 400, -64, 255, 50),
                        128, 827413L, "a".repeat(64), DiagnosticCompileRequestV2.defaultBudget()), intent);
        CoastalTransitionPlanV2 plan = blueprint.coastalTransitionPlans().getFirst();
        HardLandWaterSourceV2 hard = HardLandWaterSourceV2.NONE;
        List<CoastalTransitionCompositorV2.LayerBinding> bindings = new ArrayList<>();
        for (CoastalTransitionPlanV2.Contributor contributor : plan.contributors()) {
            CoastalFeaturePlanV2 coastal = blueprint.coastalFeaturePlans().stream()
                    .filter(candidate -> candidate.featureId().equals(contributor.featureId()))
                    .findFirst().orElseThrow();
            switch (contributor.kind()) {
                case SANDY_BEACH -> bindings.add(CoastalTransitionLayerSourcesV2.beach(
                        contributor,
                        new SandyBeachGeneratorV2(
                                blueprint.sandyBeachPlans().stream()
                                        .filter(candidate -> candidate.featureId().equals(contributor.featureId()))
                                        .findFirst().orElseThrow(),
                                new CoastalRasterKernelV2(coastal, 400, 400)),
                        hard));
                case HARBOR_BASIN -> bindings.add(CoastalTransitionLayerSourcesV2.harbor(
                        contributor,
                        new HarborBasinGeneratorV2(
                                blueprint.harborBasinPlans().stream()
                                        .filter(candidate -> candidate.featureId().equals(contributor.featureId()))
                                        .findFirst().orElseThrow(), coastal, 400, 400),
                        hard));
                case BREAKWATER_HARBOR -> bindings.add(CoastalTransitionLayerSourcesV2.breakwater(
                        contributor,
                        new BreakwaterHarborGeneratorV2(
                                blueprint.breakwaterHarborPlans().stream()
                                        .filter(candidate -> candidate.featureId().equals(contributor.featureId()))
                                        .findFirst().orElseThrow(), coastal, 400, 400)));
                case ROCKY_CAPE -> bindings.add(CoastalTransitionLayerSourcesV2.cape(
                        contributor,
                        new RockyCapeGeneratorV2(
                                blueprint.rockyCapePlans().stream()
                                        .filter(candidate -> candidate.featureId().equals(contributor.featureId()))
                                        .findFirst().orElseThrow(), coastal, 400, 400),
                        hard));
                default -> throw new AssertionError("unexpected coastal kind");
            }
        }
        CoastalTransitionCompositorV2 compositor = new CoastalTransitionCompositorV2(
                plan, 400, 400, bindings);
        for (int z = 0; z < 400; z++) {
            for (int x = 0; x < 400; x++) {
                assertEquals(null, compositor.diagnoseAt(x, z, hard), "conflict at " + x + ',' + z);
            }
        }
        assertEquals(
                compositor.wholeFieldChecksums(hard),
                new CoastalTransitionCompositorV2(plan, 400, 400, reversed(bindings))
                        .wholeFieldChecksums(hard));
    }

    @Test
    void beachHarborCapePermutationsProduceTheSameFieldsAndChecksum() {
        CoastalTransitionPlanV2 plan = threeWayPlan();
        List<CoastalTransitionCompositorV2.LayerBinding> bindings = threeWayBindings(plan);
        Map<CoastalTransitionCompositorV2.CompositionField, String> expected = null;
        for (List<CoastalTransitionCompositorV2.LayerBinding> permutation : permutations(bindings)) {
            CoastalTransitionCompositorV2 compositor = new CoastalTransitionCompositorV2(
                    plan, 12, 10, permutation);
            Map<CoastalTransitionCompositorV2.CompositionField, String> actual =
                    compositor.wholeFieldChecksums(HardLandWaterSourceV2.NONE);
            if (expected == null) expected = actual;
            else assertEquals(expected, actual);
        }
        assertNotNull(expected);
    }

    @Test
    void overlapBandBlendsAndZeroBandConnectionSeamKeepsStructureOverWater() {
        CoastalTransitionPlanV2 blendPlan = plan(
                List.of(contributor("beach", TerrainIntentV2.FeatureKind.SANDY_BEACH, 10, 1),
                        contributor("cape", TerrainIntentV2.FeatureKind.ROCKY_CAPE, 20, 2)),
                List.of(interaction("beach-cape", "beach", "cape", 6)));
        CoastalTransitionCompositorV2 blend = compositor(blendPlan, List.of(
                binding(blendPlan, "beach", layer(1, 60, 2, false)),
                binding(blendPlan, "cape", layer(1, 72, 4, false))));
        CoastalTransitionCompositorV2.CompositionSample blended = blend.sampleAt(
                2, 2, HardLandWaterSourceV2.NONE);
        assertEquals(1, blended.landWater());
        assertTrue(blended.surfaceHeightMillionths() > 60 * SCALE
                && blended.surfaceHeightMillionths() < 72 * SCALE);
        assertEquals(2, blended.ownerIndex());

        CoastalTransitionPlanV2 seamPlan = new CoastalTransitionPlanV2(
                1, "coastal-transition", CoastalTransitionModuleV2.MODULE_ID,
                CoastalTransitionModuleV2.MODULE_VERSION, ModuleDescriptorV2.MergeOperator.PRIORITY_BLEND,
                CoastalTransitionPlanV2.HardCellPolicy.PROTECT_EXACT,
                CoastalTransitionPlanV2.AmbiguityPolicy.REJECT,
                List.of(contributor("basin", TerrainIntentV2.FeatureKind.HARBOR_BASIN, 15, 1),
                        contributor("breakwater", TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR, 20, 2)),
                List.of(new CoastalTransitionPlanV2.Interaction(
                        "breakwater-encloses-basin", "basin", "breakwater", TerrainIntentV2.Strength.HARD,
                        CoastalTransitionPlanV2.InteractionProfile.STRUCTURE_OVER_WATER, 0)),
                List.of("field.input"), "field.land-water", "field.surface-height", "field.owner-index",
                "field.blend-weight", "field.conflict", 0);
        CoastalTransitionCompositorV2 seam = compositor(seamPlan, List.of(
                binding(seamPlan, "basin", layer(0, 48, 1, false)),
                binding(seamPlan, "breakwater", layer(1, 68, 1, false))));
        CoastalTransitionCompositorV2.CompositionSample sample = seam.sampleAt(
                1, 1, HardLandWaterSourceV2.NONE);
        assertEquals(1, sample.landWater());
        assertEquals(68 * SCALE, sample.surfaceHeightMillionths());
        assertEquals(2, sample.ownerIndex());
    }

    @Test
    void hardCellsAreExactAndHardHardConflictIsDiagnostic() {
        CoastalTransitionPlanV2 plan = plan(
                List.of(contributor("beach", TerrainIntentV2.FeatureKind.SANDY_BEACH, 10, 1),
                        contributor("cape", TerrainIntentV2.FeatureKind.ROCKY_CAPE, 10, 2)),
                List.of(interaction("beach-cape", "beach", "cape", 4)));
        CoastalTransitionCompositorV2 protectedCell = compositor(plan, List.of(
                binding(plan, "beach", layer(1, 64, 2, true)),
                binding(plan, "cape", layer(0, 50, 2, false))));
        CoastalTransitionCompositorV2.CompositionSample sample = protectedCell.sampleAt(
                0, 0, (x, z) -> HardLandWaterSourceV2.Classification.LAND);
        assertEquals(1, sample.landWater());
        assertEquals(64 * SCALE, sample.surfaceHeightMillionths());
        assertTrue(sample.hardProtected());

        CoastalTransitionCompositorV2 conflict = compositor(plan, List.of(
                binding(plan, "beach", layer(1, 64, 2, true)),
                binding(plan, "cape", layer(0, 50, 2, true))));
        CoastalTransitionException exception = assertThrows(CoastalTransitionException.class,
                () -> conflict.sampleAt(0, 0, HardLandWaterSourceV2.NONE));
        assertEquals("v2.coastal-transition-hard-conflict", exception.ruleId());
        CoastalTransitionCompositorV2.ConflictDiagnostic diagnostic = conflict.diagnoseAt(
                0, 0, HardLandWaterSourceV2.NONE);
        assertEquals(exception.ruleId(), diagnostic.ruleId());
        assertEquals(List.of("beach", "cape"), diagnostic.featureIds());
    }

    @Test
    void rejectsUncontractedAndAmbiguousOverlapsInsteadOfLastWriteWins() {
        List<CoastalTransitionPlanV2.Contributor> contributors = List.of(
                contributor("beach", TerrainIntentV2.FeatureKind.SANDY_BEACH, 10, 1),
                contributor("cape", TerrainIntentV2.FeatureKind.ROCKY_CAPE, 10, 2));
        CoastalTransitionPlanV2 uncontracted = plan(contributors, List.of());
        CoastalTransitionCompositorV2 first = compositor(uncontracted, List.of(
                binding(uncontracted, "beach", layer(1, 64, 2, false)),
                binding(uncontracted, "cape", layer(1, 70, 2, false))));
        CoastalTransitionException missing = assertThrows(CoastalTransitionException.class,
                () -> first.sampleAt(0, 0, HardLandWaterSourceV2.NONE));
        assertEquals("v2.coastal-transition-uncontracted-overlap", missing.ruleId());

        CoastalTransitionPlanV2 contracted = plan(
                contributors, List.of(interaction("beach-cape", "beach", "cape", 4)));
        CoastalTransitionCompositorV2 ambiguous = compositor(contracted, List.of(
                binding(contracted, "beach", layer(1, 64, 2, false)),
                binding(contracted, "cape", layer(0, 50, 2, false))));
        CoastalTransitionException tie = assertThrows(CoastalTransitionException.class,
                () -> ambiguous.sampleAt(0, 0, HardLandWaterSourceV2.NONE));
        assertEquals("v2.coastal-transition-ambiguous", tie.ruleId());
    }

    @Test
    void wholeTilesSeamsThreadsLocaleAndTimezoneAreInvariant() throws Exception {
        CoastalTransitionPlanV2 plan = threeWayPlan();
        List<CoastalTransitionCompositorV2.LayerBinding> layers = threeWayBindings(plan);
        CoastalTransitionCompositorV2 compositor = new CoastalTransitionCompositorV2(plan, 16, 12, layers);
        Map<CoastalTransitionCompositorV2.CompositionField, String> baseline =
                compositor.wholeFieldChecksums(HardLandWaterSourceV2.NONE);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            List<int[]> tiles = List.of(new int[] {0, 0}, new int[] {8, 0}, new int[] {0, 6}, new int[] {8, 6});
            List<int[]> reversed = new ArrayList<>(tiles);
            Collections.reverse(reversed);
            List<CoastalTransitionWindowV2> windows = new ArrayList<>();
            for (int[] tile : reversed) {
                CoastalTransitionWindowV2 window = compositor.renderWindow(
                        tile[0], tile[1], 8, 6, 2, HardLandWaterSourceV2.NONE, () -> false);
                windows.add(window);
                for (int z = tile[1]; z < tile[1] + 6; z++) {
                    for (int x = tile[0]; x < tile[0] + 8; x++) {
                        CoastalTransitionCompositorV2.CompositionSample expected = compositor.sampleAt(
                                x, z, HardLandWaterSourceV2.NONE);
                        for (CoastalTransitionCompositorV2.CompositionField field
                                : CoastalTransitionCompositorV2.CompositionField.values()) {
                            assertEquals(expected.rawValue(field), window.rawValueAt(field, x, z));
                        }
                    }
                }
            }
            CoastalTransitionWindowV2 right = windows.get(2);
            CoastalTransitionWindowV2 left = windows.get(3);
            for (int z = 0; z < 8; z++) {
                for (int x = 6; x < 10; x++) {
                    for (CoastalTransitionCompositorV2.CompositionField field
                            : CoastalTransitionCompositorV2.CompositionField.values()) {
                        assertEquals(left.rawValueAt(field, x, z), right.rawValueAt(field, x, z));
                    }
                }
            }
            ExecutorService executor = Executors.newFixedThreadPool(4);
            try {
                List<Future<Map<CoastalTransitionCompositorV2.CompositionField, String>>> futures = new ArrayList<>();
                for (int index = 0; index < 8; index++) {
                    futures.add(executor.submit(() -> compositor.wholeFieldChecksums(HardLandWaterSourceV2.NONE)));
                }
                for (Future<Map<CoastalTransitionCompositorV2.CompositionField, String>> future : futures) {
                    assertEquals(baseline, future.get());
                }
            } finally {
                executor.shutdownNow();
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void haloAndWorkingMemoryAreBoundedAndOversizeWindowIsRejected() {
        CoastalTransitionPlanV2 bandEight = plan(
                List.of(contributor("beach", TerrainIntentV2.FeatureKind.SANDY_BEACH, 10, 1),
                        contributor("cape", TerrainIntentV2.FeatureKind.ROCKY_CAPE, 20, 2)),
                List.of(interaction("beach-cape", "beach", "cape", 8)));
        CoastalTransitionCompositorV2 bounded = new CoastalTransitionCompositorV2(
                bandEight, 192, 192, List.of(
                binding(bandEight, "beach", layer(1, 60, 4, false)),
                binding(bandEight, "cape", layer(1, 72, 4, false))));
        CoastalTransitionWindowV2 window = bounded.renderWindow(
                32, 32, 128, 128, 8, HardLandWaterSourceV2.NONE, () -> false);
        assertTrue(window.estimatedRetainedBytes() <= CoastalTransitionCompositorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        CoastalTransitionException halo = assertThrows(CoastalTransitionException.class,
                () -> bounded.renderWindow(32, 32, 128, 128, 9,
                        HardLandWaterSourceV2.NONE, () -> false));
        assertEquals("v2.coastal-transition-window", halo.ruleId());

        CoastalTransitionPlanV2 bandThirtyTwo = plan(
                bandEight.contributors(), List.of(interaction("beach-cape", "beach", "cape", 32)));
        CoastalTransitionCompositorV2 oversize = new CoastalTransitionCompositorV2(
                bandThirtyTwo, 320, 320, List.of(
                binding(bandThirtyTwo, "beach", layer(1, 60, 4, false)),
                binding(bandThirtyTwo, "cape", layer(1, 72, 4, false))));
        CoastalTransitionException budget = assertThrows(CoastalTransitionException.class,
                () -> oversize.renderWindow(32, 32, 256, 256, 32,
                        HardLandWaterSourceV2.NONE, () -> false));
        assertEquals("v2.coastal-transition-budget", budget.ruleId());
    }

    private static CoastalTransitionPlanV2 threeWayPlan() {
        return plan(
                List.of(contributor("beach", TerrainIntentV2.FeatureKind.SANDY_BEACH, 10, 1),
                        contributor("cape", TerrainIntentV2.FeatureKind.ROCKY_CAPE, 10, 2),
                        contributor("harbor", TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR, 20, 3)),
                List.of(interaction("beach-cape", "beach", "cape", 8),
                        interaction("beach-harbor", "beach", "harbor", 6),
                        interaction("cape-harbor", "cape", "harbor", 4)));
    }

    private static List<CoastalTransitionCompositorV2.LayerBinding> threeWayBindings(
            CoastalTransitionPlanV2 plan
    ) {
        return List.of(
                binding(plan, "beach", (x, z) -> new CoastalTransitionCompositorV2.LayerSample(
                        true, 1, (60 + Math.floorMod(x, 3)) * SCALE, (x + 1) * SCALE, false)),
                binding(plan, "cape", (x, z) -> new CoastalTransitionCompositorV2.LayerSample(
                        true, 1, (72 + Math.floorMod(z, 2)) * SCALE, (z + 1) * SCALE, false)),
                binding(plan, "harbor", layer(1, 68, 3, false)));
    }

    private static CoastalTransitionPlanV2 plan(
            List<CoastalTransitionPlanV2.Contributor> contributors,
            List<CoastalTransitionPlanV2.Interaction> interactions
    ) {
        int support = interactions.stream().mapToInt(CoastalTransitionPlanV2.Interaction::bandBlocks).max().orElse(0);
        return new CoastalTransitionPlanV2(
                1, "coastal-transition", CoastalTransitionModuleV2.MODULE_ID,
                CoastalTransitionModuleV2.MODULE_VERSION, ModuleDescriptorV2.MergeOperator.PRIORITY_BLEND,
                CoastalTransitionPlanV2.HardCellPolicy.PROTECT_EXACT,
                CoastalTransitionPlanV2.AmbiguityPolicy.REJECT,
                contributors, interactions, List.of("field.input"),
                "field.land-water", "field.surface-height", "field.owner-index", "field.blend-weight",
                "field.conflict", support);
    }

    private static CoastalTransitionPlanV2.Contributor contributor(
            String id, TerrainIntentV2.FeatureKind kind, int priority, int index
    ) {
        return new CoastalTransitionPlanV2.Contributor(id, kind, priority, index);
    }

    private static CoastalTransitionPlanV2.Interaction interaction(
            String id, String first, String second, int band
    ) {
        return new CoastalTransitionPlanV2.Interaction(
                id, first, second, TerrainIntentV2.Strength.SOFT,
                CoastalTransitionPlanV2.InteractionProfile.PRIORITY_BLEND, band);
    }

    private static CoastalTransitionCompositorV2.LayerBinding binding(
            CoastalTransitionPlanV2 plan,
            String featureId,
            CoastalTransitionCompositorV2.LayerSource source
    ) {
        CoastalTransitionPlanV2.Contributor contributor = plan.contributors().stream()
                .filter(candidate -> candidate.featureId().equals(featureId)).findFirst().orElseThrow();
        return new CoastalTransitionCompositorV2.LayerBinding(contributor, source);
    }

    private static CoastalTransitionCompositorV2.LayerSource layer(
            int landWater, int heightBlocks, int boundaryBlocks, boolean hard
    ) {
        return (x, z) -> new CoastalTransitionCompositorV2.LayerSample(
                true, landWater, heightBlocks * SCALE, boundaryBlocks * SCALE, hard);
    }

    private static CoastalTransitionCompositorV2 compositor(
            CoastalTransitionPlanV2 plan,
            List<CoastalTransitionCompositorV2.LayerBinding> bindings
    ) {
        return new CoastalTransitionCompositorV2(plan, 8, 8, bindings);
    }

    private static List<List<CoastalTransitionCompositorV2.LayerBinding>> permutations(
            List<CoastalTransitionCompositorV2.LayerBinding> source
    ) {
        List<List<CoastalTransitionCompositorV2.LayerBinding>> result = new ArrayList<>();
        permute(new ArrayList<>(source), 0, result);
        return result;
    }

    private static <T> List<T> reversed(List<T> values) {
        List<T> result = new ArrayList<>(values);
        Collections.reverse(result);
        return List.copyOf(result);
    }

    private static void permute(
            List<CoastalTransitionCompositorV2.LayerBinding> values,
            int index,
            List<List<CoastalTransitionCompositorV2.LayerBinding>> result
    ) {
        if (index == values.size()) {
            result.add(List.copyOf(values));
            return;
        }
        for (int candidate = index; candidate < values.size(); candidate++) {
            Collections.swap(values, index, candidate);
            permute(values, index + 1, result);
            Collections.swap(values, index, candidate);
        }
    }
}
