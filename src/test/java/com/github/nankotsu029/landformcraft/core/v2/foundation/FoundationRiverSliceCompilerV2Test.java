package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.HydrologyRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationRiverSliceCompilerV2Test {
    private static final Path GENERAL_RIVER =
            Path.of("examples/v2/foundation/general-river-slice.terrain-intent-v2.json");
    private static final Path MEANDERING =
            Path.of("examples/v2/hydrology/meandering-river.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationRiverSliceCompilerV2 compiler = new FoundationRiverSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void generalRiverVerticalSliceProducesSourceMouthAndBedMetrics() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(GENERAL_RIVER);
        FoundationRiverSliceCompilerV2.FoundationRiverSliceV2 slice =
                compiler.compile(intent, bounds(64, 48), 101L);
        assertNotNull(slice.river());
        assertEquals(1, slice.river().reaches().size());
        assertTrue(slice.validation().metrics().sourceMouthReachable());
        assertTrue(slice.validation().metrics().bedMonotonic());
        assertTrue(slice.validation().metrics().graphBudgetOk());
        assertTrue(slice.validation().metrics().cycleFree());
    }

    @Test
    void rejectsOrphanAndSelfLoopGraphs() {
        RiverPlanCompilerV2.assertRejectsSelfLoopAndOrphan();
    }

    @Test
    void sealedRiverPlanStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(GENERAL_RIVER);
        FoundationRiverSliceCompilerV2.FoundationRiverSliceV2 slice =
                compiler.compile(intent, bounds(64, 48), 202L);
        Path riverFile = temp.resolve("river-plan-v2.json");
        Path validationFile = temp.resolve("foundation-river-validation-artifact-v2.json");
        codec.writeRiverPlan(riverFile, slice.river());
        codec.writeFoundationRiverValidationArtifact(validationFile, slice.validation());
        assertEquals(slice.river(), codec.readRiverPlan(riverFile));
        assertEquals(slice.validation(), codec.readFoundationRiverValidationArtifact(validationFile));
        Files.copy(riverFile, Path.of("examples/v2/foundation/river-plan-v2.json"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void riverRemainsExperimentalWithoutBlueprintEmbedding() {
        ModuleDescriptorV2 riverModule = new LandformRiverModuleV2().descriptor();
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL, riverModule.lifecycleStatus());
        ModuleDescriptorV2 catalogRiver = catalog.requireFor(TerrainIntentV2.FeatureKind.RIVER);
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID, catalogRiver.moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.RIVER));
    }

    @Test
    void legacyMeanderingStillCompilesOnSupportedHydrologyModule() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(MEANDERING);
        TerrainIntentV2.Feature meander = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.MEANDERING_RIVER)
                .findFirst()
                .orElseThrow();
        assertTrue(meander.parameters() instanceof TerrainIntentV2.MeanderingRiverParameters);
        assertEquals(HydrologyRiverModuleV2.MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.MEANDERING_RIVER).moduleId());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                catalog.requireFor(TerrainIntentV2.FeatureKind.MEANDERING_RIVER).lifecycleStatus());

        MeanderingRiverPlanV2 plan = new MeanderingRiverPlanCompilerV2().compile(
                meander, bounds(64, 96), codec.geometryChecksum(meander.geometry()));
        assertEquals(TerrainIntentV2.RiverVariant.MEANDERING_RIVER, plan.variant());
        assertTrue(plan.meanderAmplitudeBlocks() > 0);

        TerrainIntentV2.RiverParameters suggested = MeanderingRiverSubtypeBridgeV2.suggestedRiverParameters(
                (TerrainIntentV2.MeanderingRiverParameters) meander.parameters());
        assertEquals(meander.parameters() instanceof TerrainIntentV2.MeanderingRiverParameters
                        ? ((TerrainIntentV2.MeanderingRiverParameters) meander.parameters()).dischargeClass()
                        : null,
                suggested.dischargeClass());
        // Bridge must not alter legacy fixture serialization.
        TerrainIntentV2 reread = codec.readTerrainIntent(MEANDERING);
        assertEquals(intent, reread);
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, 62);
    }
}
