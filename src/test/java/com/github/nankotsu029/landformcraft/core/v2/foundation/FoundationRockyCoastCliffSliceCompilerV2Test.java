package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.cape.RockyCapeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformRockyCoastModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSeaCliffModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.overhang.OverhangGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.seacave.SeaCaveGeneratorV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.RockyCapePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationRockyCoastCliffSliceCompilerV2Test {
    private static final Path SLICE =
            Path.of("examples/v2/foundation/rocky-coast-cliff-slice.terrain-intent-v2.json");
    private static final Path AZURE =
            Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationRockyCoastCliffSliceCompilerV2 compiler =
            new FoundationRockyCoastCliffSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void rockyCoastCliffVerticalSlicePassesMetrics() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(SLICE);
        FoundationRockyCoastCliffSliceCompilerV2.FoundationRockyCoastCliffSliceV2 slice =
                compiler.compile(intent, bounds(64, 48), 606L);
        assertEquals("rocky-shore", slice.rockyCoast().featureId());
        assertEquals("host-cliff", slice.seaCliff().featureId());
        assertTrue(slice.validation().metrics().rockShelfPresent());
        assertTrue(slice.validation().metrics().cliffFacePresent());
        assertTrue(slice.validation().metrics().talusPresent());
        assertTrue(slice.validation().metrics().notchPresent());
        assertTrue(slice.validation().metrics().shoreSideOk());
        assertTrue(slice.validation().metrics().hostAabbOk());
        assertTrue(slice.validation().metrics().coastTransitionOk());
        assertTrue(slice.validation().metrics().surfaceVolumeOwnershipOk());
        assertTrue(slice.validation().metrics().haloBudgetOk());
        assertFalse(slice.wholeChecksums().isEmpty());
    }

    @Test
    void sealedPlansStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(SLICE);
        FoundationRockyCoastCliffSliceCompilerV2.FoundationRockyCoastCliffSliceV2 slice =
                compiler.compile(intent, bounds(64, 48), 707L);
        Path coastFile = temp.resolve("rocky-coast-plan-v2.json");
        Path cliffFile = temp.resolve("sea-cliff-plan-v2.json");
        Path validationFile = temp.resolve("foundation-rocky-coast-cliff-validation-artifact-v2.json");
        codec.writeRockyCoastPlan(coastFile, slice.rockyCoast());
        codec.writeSeaCliffPlan(cliffFile, slice.seaCliff());
        codec.writeFoundationRockyCoastCliffValidationArtifact(validationFile, slice.validation());
        assertEquals(slice.rockyCoast(), codec.readRockyCoastPlan(coastFile));
        assertEquals(slice.seaCliff(), codec.readSeaCliffPlan(cliffFile));
        assertEquals(slice.validation(),
                codec.readFoundationRockyCoastCliffValidationArtifact(validationFile));
        Files.copy(coastFile, Path.of("examples/v2/foundation/rocky-coast-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(cliffFile, Path.of("examples/v2/foundation/sea-cliff-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void rockyCoastAndSeaCliffRemainExperimentalWithoutCatalogRegistration() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformRockyCoastModuleV2().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformSeaCliffModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.ROCKY_COAST).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.SEA_CLIFF).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.ROCKY_COAST));
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.SEA_CLIFF));
    }

    @Test
    void seaCaveAndOverhangHostHandoffCompileAgainstCliffAabb() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(SLICE);
        FoundationRockyCoastCliffSliceCompilerV2.FoundationRockyCoastCliffSliceV2 slice =
                compiler.compile(intent, bounds(64, 48), 808L);
        var seaCave = compiler.compileSeaCaveHostHandoff(slice.seaCliff());
        SeaCaveGeneratorV2.SeaCaveMetricsV2 seaCaveMetrics = new SeaCaveGeneratorV2(
                seaCave.plan(), seaCave.sdfPlan(), seaCave.csgPlan()).validate();
        assertTrue(seaCaveMetrics.carvedSamples() > 0);
        assertTrue(seaCaveMetrics.openingSamples() >= 2);

        var overhang = compiler.compileOverhangHostHandoff(slice.seaCliff());
        OverhangGeneratorV2.OverhangMetricsV2 overhangMetrics = new OverhangGeneratorV2(
                overhang.plan(), overhang.sdfPlan(), overhang.csgPlan()).validate();
        assertTrue(overhangMetrics.solidSamples() > 0);
        assertTrue(overhangMetrics.clearanceSamples() > 0);
    }

    @Test
    void rockyCapeAzureCoastFieldChecksumsRemainRunnable() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(AZURE);
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                new DiagnosticCompileRequestV2(
                        intent.intentId(),
                        new GenerationBounds(400, 400, -64, 255, 50),
                        128,
                        827413L,
                        "a".repeat(64),
                        DiagnosticCompileRequestV2.defaultBudget()),
                intent);
        RockyCapePlanV2 plan = blueprint.rockyCapePlans().getFirst();
        CoastalFeaturePlanV2 coastalPlan = blueprint.coastalFeaturePlans().stream()
                .filter(candidate -> candidate.featureId().equals(plan.featureId()))
                .findFirst()
                .orElseThrow();
        RockyCapeGeneratorV2 generator = new RockyCapeGeneratorV2(plan, coastalPlan, 400, 400);
        Map<RockyCapeGeneratorV2.CapeField, String> checksums =
                generator.fieldChecksums(HardLandWaterSourceV2.NONE, () -> false);
        assertFalse(checksums.isEmpty());
        assertTrue(generator.evaluate(() -> false).cliffCells() > 0);
    }

    @Test
    void rejectsMissingCoastCliffRelation() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(SLICE);
        List<TerrainIntentV2.Relation> relations = intent.relations().stream()
                .filter(relation -> !relation.id().equals("coast-cliff-overlap"))
                .toList();
        TerrainIntentV2 mutated = withRelations(intent, relations);
        FoundationSliceException exception = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(mutated, bounds(64, 48), 1L));
        assertEquals("v2.rocky-coast-cliff-relation", exception.ruleId());
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
