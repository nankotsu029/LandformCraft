package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformArchipelagoModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSingleIslandModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformVolcanicConeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic.VolcanicGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic.VolcanicPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationIslandConeSliceCompilerV2Test {
    private static final Path SINGLE_ISLAND =
            Path.of("examples/v2/foundation/single-island-slice.terrain-intent-v2.json");
    private static final Path ARCHIPELAGO =
            Path.of("examples/v2/foundation/archipelago-slice.terrain-intent-v2.json");
    private static final Path VOLCANIC_CONE =
            Path.of("examples/v2/foundation/volcanic-cone-slice.terrain-intent-v2.json");
    private static final Path VOLCANIC =
            Path.of("examples/v2/diagnostic/scenarios/volcanic-archipelago.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationSingleIslandSliceCompilerV2 islandCompiler =
            new FoundationSingleIslandSliceCompilerV2();
    private final FoundationArchipelagoSliceCompilerV2 archipelagoCompiler =
            new FoundationArchipelagoSliceCompilerV2();
    private final FoundationVolcanicConeSliceCompilerV2 coneCompiler =
            new FoundationVolcanicConeSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void singleIslandSliceProducesMassShoreDrainageAndApronMetrics() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(SINGLE_ISLAND);
        FoundationSingleIslandSliceCompilerV2.FoundationSingleIslandSliceV2 slice =
                islandCompiler.compile(intent, bounds(96, 96), 101L);
        assertTrue(slice.validation().metrics().islandMassPresent());
        assertTrue(slice.validation().metrics().shoreBandPresent());
        assertTrue(slice.validation().metrics().radialDrainagePresent());
        assertTrue(slice.validation().metrics().apronPresent());
        assertTrue(slice.validation().metrics().supportBudgetOk());
    }

    @Test
    void archipelagoSliceProducesGapDominanceAndSaddleMetrics() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(ARCHIPELAGO);
        FoundationArchipelagoSliceCompilerV2.FoundationArchipelagoSliceV2 slice =
                archipelagoCompiler.compile(intent, bounds(128, 96), 202L);
        assertEquals(3, slice.archipelago().islands().size());
        assertTrue(slice.validation().metrics().componentCountOk());
        assertTrue(slice.validation().metrics().dryLandGapOk());
        assertTrue(slice.validation().metrics().noOverlap());
        assertTrue(slice.validation().metrics().saddlesPresent());
        assertTrue(slice.validation().metrics().dominanceOk());
    }

    @Test
    void volcanicConeSliceProducesCraterAndDrainageMetrics() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(VOLCANIC_CONE);
        FoundationVolcanicConeSliceCompilerV2.FoundationVolcanicConeSliceV2 slice =
                coneCompiler.compile(intent, bounds(96, 96), 303L);
        assertTrue(slice.validation().metrics().coneMassPresent());
        assertTrue(slice.validation().metrics().craterContained());
        assertTrue(slice.validation().metrics().radialDrainagePresent());
        assertTrue(slice.validation().metrics().supportBudgetOk());
    }

    @Test
    void sealedPlansStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 islandIntent = codec.readTerrainIntent(SINGLE_ISLAND);
        FoundationSingleIslandSliceCompilerV2.FoundationSingleIslandSliceV2 island =
                islandCompiler.compile(islandIntent, bounds(96, 96), 11L);
        Path islandFile = temp.resolve("single-island-plan-v2.json");
        codec.writeSingleIslandPlan(islandFile, island.island());
        assertEquals(island.island(), codec.readSingleIslandPlan(islandFile));
        Files.copy(islandFile, Path.of("examples/v2/foundation/single-island-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        TerrainIntentV2 archipelagoIntent = codec.readTerrainIntent(ARCHIPELAGO);
        FoundationArchipelagoSliceCompilerV2.FoundationArchipelagoSliceV2 archipelago =
                archipelagoCompiler.compile(archipelagoIntent, bounds(128, 96), 22L);
        Path archipelagoFile = temp.resolve("archipelago-plan-v2.json");
        codec.writeArchipelagoPlan(archipelagoFile, archipelago.archipelago());
        assertEquals(archipelago.archipelago(), codec.readArchipelagoPlan(archipelagoFile));
        Files.copy(archipelagoFile, Path.of("examples/v2/foundation/archipelago-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        TerrainIntentV2 coneIntent = codec.readTerrainIntent(VOLCANIC_CONE);
        FoundationVolcanicConeSliceCompilerV2.FoundationVolcanicConeSliceV2 cone =
                coneCompiler.compile(coneIntent, bounds(96, 96), 33L);
        Path coneFile = temp.resolve("volcanic-cone-plan-v2.json");
        codec.writeVolcanicConePlan(coneFile, cone.cone());
        assertEquals(cone.cone(), codec.readVolcanicConePlan(coneFile));
        Files.copy(coneFile, Path.of("examples/v2/foundation/volcanic-cone-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void modulesRemainExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformSingleIslandModuleV2().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformArchipelagoModuleV2().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformVolcanicConeModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.SINGLE_ISLAND).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.ARCHIPELAGO).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.VOLCANIC_CONE).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.SINGLE_ISLAND));
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.ARCHIPELAGO));
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.VOLCANIC_CONE));
    }

    @Test
    void archipelagoRejectsOverlappingIslandsAndInsufficientDryLandGap() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(ARCHIPELAGO);
        TerrainIntentV2.Feature feature = base.features().getFirst();
        TerrainIntentV2.ArchipelagoParameters params =
                (TerrainIntentV2.ArchipelagoParameters) feature.parameters();

        TerrainIntentV2 overlapping = withArchipelagoParams(base, feature, new TerrainIntentV2.ArchipelagoParameters(
                List.of(
                        new TerrainIntentV2.IslandSpec("west-isle", 28, 14),
                        new TerrainIntentV2.IslandSpec("main-isle", 28, 22),
                        new TerrainIntentV2.IslandSpec("east-isle", 28, 16)),
                params.submarineSaddleDepthBlocks(),
                12));
        FoundationSliceException overlap = assertThrows(FoundationSliceException.class,
                () -> archipelagoCompiler.compile(overlapping, bounds(128, 96), 1L));
        assertEquals("v2.archipelago-overlap", overlap.ruleId());

        TerrainIntentV2 tightGap = withArchipelagoParams(base, feature, new TerrainIntentV2.ArchipelagoParameters(
                List.of(
                        new TerrainIntentV2.IslandSpec("west-isle", 14, 14),
                        new TerrainIntentV2.IslandSpec("main-isle", 14, 22),
                        new TerrainIntentV2.IslandSpec("east-isle", 10, 16)),
                params.submarineSaddleDepthBlocks(),
                40));
        FoundationSliceException gap = assertThrows(FoundationSliceException.class,
                () -> archipelagoCompiler.compile(tightGap, bounds(128, 96), 1L));
        assertEquals("v2.archipelago-dry-gap", gap.ruleId());
    }

    @Test
    void volcanicConeParametersRejectCraterOutsideBase() {
        assertThrows(IllegalArgumentException.class, () -> new TerrainIntentV2.VolcanicConeParameters(
                new TerrainIntentV2.IntRange(20, 24),
                new TerrainIntentV2.IntRange(16, 20),
                new TerrainIntentV2.IntRange(20, 22),
                new TerrainIntentV2.IntRange(4, 6),
                new TerrainIntentV2.FixedRange(300_000L, 600_000L)));
    }

    @Test
    void volcanicAdapterSuggestsParamsWithoutChangingVolcanicFixture() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(VOLCANIC);
        TerrainIntentV2.Feature volcanic = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO)
                .findFirst()
                .orElseThrow();
        TerrainIntentV2.VolcanicArchipelagoParameters parameters =
                (TerrainIntentV2.VolcanicArchipelagoParameters) volcanic.parameters();
        TerrainIntentV2.ArchipelagoParameters suggested =
                VolcanicIslandConeAdapterV2.suggestedArchipelagoParameters(parameters);
        assertEquals(parameters.islands(), suggested.islands());
        assertEquals(parameters.submarineSaddleDepthBlocks(), suggested.submarineSaddleDepthBlocks());
        assertEquals(12, suggested.minDryLandGapBlocks());

        TerrainIntentV2.IslandSpec dominant = parameters.islands().stream()
                .max((a, b) -> Integer.compare(a.summitHeightBlocksAboveSea(), b.summitHeightBlocksAboveSea()))
                .orElseThrow();
        TerrainIntentV2.VolcanicConeParameters cone =
                VolcanicIslandConeAdapterV2.suggestedVolcanicConeParameters(dominant);
        assertEquals(dominant.radiusBlocks(), cone.baseRadiusBlocks().minimum());
        assertEquals(dominant.summitHeightBlocksAboveSea(), cone.summitHeightBlocksAboveSea().minimum());
        assertTrue(cone.craterRadiusBlocks().maximum() < dominant.radiusBlocks());

        TerrainIntentV2 reread = codec.readTerrainIntent(VOLCANIC);
        assertEquals(intent, reread);

        VolcanicPlanV2 plan = new VolcanicPlanCompilerV2().compile(
                volcanic, intent, bounds(257, 257), codec.geometryChecksum(volcanic.geometry()));
        VolcanicGeneratorV2.VolcanicMetrics metrics = new VolcanicGeneratorV2(plan).evaluate(() -> false);
        assertTrue(metrics.componentCount() >= 2);
        assertTrue(metrics.dryGapOk());
    }

    private static TerrainIntentV2 withArchipelagoParams(
            TerrainIntentV2 base,
            TerrainIntentV2.Feature feature,
            TerrainIntentV2.ArchipelagoParameters parameters
    ) {
        TerrainIntentV2.Feature replaced = new TerrainIntentV2.Feature(
                feature.id(), feature.kind(), feature.geometry(), parameters,
                feature.priority(), feature.provenance());
        return new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                List.of(replaced), base.relations(), base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, 62);
    }
}
