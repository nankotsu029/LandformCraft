package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformFloodplainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformMarshModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove.LandformMangroveModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
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

class FoundationFloodplainMarshSliceCompilerV2Test {
    private static final Path SLICE =
            Path.of("examples/v2/foundation/floodplain-river-marsh-slice.terrain-intent-v2.json");
    private static final Path MANGROVE =
            Path.of("examples/v2/diagnostic/scenarios/mangrove-wetland.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationFloodplainMarshSliceCompilerV2 compiler =
            new FoundationFloodplainMarshSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void floodplainRiverMarshVerticalSlicePassesHydrologicMetrics() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(SLICE);
        FoundationFloodplainMarshSliceCompilerV2.FoundationFloodplainMarshSliceV2 slice =
                compiler.compile(intent, bounds(64, 48), 303L);
        assertEquals("main-stem", slice.river().featureId());
        assertEquals("alluvial-plain", slice.floodplain().featureId());
        assertEquals("river-marsh", slice.marsh().featureId());
        assertTrue(slice.validation().metrics().riverAdjacencyOk());
        assertTrue(slice.validation().metrics().microReliefPresent());
        assertTrue(slice.validation().metrics().marshWetnessOk());
        assertTrue(slice.validation().metrics().openWaterTransitionOk());
        assertTrue(slice.validation().metrics().groundwaterHydroperiodOk());
        assertTrue(slice.validation().metrics().fluidSolidOwnershipOk());
        assertTrue(slice.validation().metrics().floodplainMarshTransitionOk());
        assertFalse(slice.wholeChecksums().isEmpty());
    }

    @Test
    void sealedPlansStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(SLICE);
        FoundationFloodplainMarshSliceCompilerV2.FoundationFloodplainMarshSliceV2 slice =
                compiler.compile(intent, bounds(64, 48), 404L);
        Path floodplainFile = temp.resolve("floodplain-plan-v2.json");
        Path marshFile = temp.resolve("marsh-plan-v2.json");
        Path validationFile = temp.resolve("foundation-floodplain-marsh-validation-artifact-v2.json");
        codec.writeFloodplainPlan(floodplainFile, slice.floodplain());
        codec.writeMarshPlan(marshFile, slice.marsh());
        codec.writeFoundationFloodplainMarshValidationArtifact(validationFile, slice.validation());
        assertEquals(slice.floodplain(), codec.readFloodplainPlan(floodplainFile));
        assertEquals(slice.marsh(), codec.readMarshPlan(marshFile));
        assertEquals(slice.validation(), codec.readFoundationFloodplainMarshValidationArtifact(validationFile));
        Files.copy(floodplainFile, Path.of("examples/v2/foundation/floodplain-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(marshFile, Path.of("examples/v2/foundation/marsh-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void floodplainAndMarshRemainExperimentalWithoutCatalogRegistration() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformFloodplainModuleV2().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformMarshModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.FLOODPLAIN).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.MARSH).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.FLOODPLAIN));
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.MARSH));
    }

    @Test
    void rejectsMissingRiverFloodplainRelation() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(SLICE);
        List<TerrainIntentV2.Relation> relations = intent.relations().stream()
                .filter(relation -> !relation.id().equals("river-floodplain-adjacent"))
                .toList();
        TerrainIntentV2 mutated = withRelations(intent, relations);
        FoundationSliceException exception = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(mutated, bounds(64, 48), 1L));
        assertEquals("v2.floodplain-river-disconnect", exception.ruleId());
    }

    @Test
    void rejectsDryMarshParameters() {
        assertThrows(IllegalArgumentException.class, () -> new TerrainIntentV2.MarshParameters(
                new TerrainIntentV2.IntRange(8, 16),
                new TerrainIntentV2.FixedRange(100_000L, 150_000L),
                new TerrainIntentV2.FixedRange(100_000L, 200_000L),
                new TerrainIntentV2.IntRange(1, 2),
                new TerrainIntentV2.IntRange(2, 6)));
    }

    @Test
    void mangroveWetlandRemainsSupportedWithoutRewrite() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(MANGROVE);
        TerrainIntentV2.Feature mangrove = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.MANGROVE_WETLAND)
                .findFirst()
                .orElseThrow();
        assertTrue(mangrove.parameters() instanceof TerrainIntentV2.MangroveWetlandParameters);
        ModuleDescriptorV2 module = catalog.requireFor(TerrainIntentV2.FeatureKind.MANGROVE_WETLAND);
        assertEquals(LandformMangroveModuleV2.MODULE_ID, module.moduleId());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED, module.lifecycleStatus());
        TerrainIntentV2 reread = codec.readTerrainIntent(MANGROVE);
        assertEquals(intent, reread);
    }

    private static TerrainIntentV2 withRelations(
            TerrainIntentV2 intent,
            List<TerrainIntentV2.Relation> relations
    ) {
        return new TerrainIntentV2(
                intent.intentVersion(),
                intent.intentId(),
                intent.theme(),
                intent.coordinateSystem(),
                intent.features(),
                relations,
                intent.constraints(),
                intent.environment(),
                intent.mapReferences(),
                intent.structures(),
                intent.provenance());
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, 62);
    }
}
