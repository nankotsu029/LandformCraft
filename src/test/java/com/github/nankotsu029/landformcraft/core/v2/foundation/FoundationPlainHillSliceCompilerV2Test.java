package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformHillRangeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformPlainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationPlainHillSliceCompilerV2Test {
    private static final Path AZURE = Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");
    private static final Path PLAIN_HILL = Path.of("examples/v2/foundation/plain-hill-slice.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationPlainHillSliceCompilerV2 compiler = new FoundationPlainHillSliceCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void plainOnlyVerticalSliceProducesNonNoopMetrics() throws Exception {
        TerrainIntentV2 intent = plainOnlyIntent();
        WorldBlueprintV2.Bounds bounds = bounds(64, 48);
        FoundationPlainHillSliceCompilerV2.FoundationPlainHillSliceV2 slice =
                compiler.compile(intent, bounds, 101L);
        assertNotNull(slice.plain());
        assertTrue(slice.validation().metrics().microReliefPresent());
        assertTrue(slice.validation().metrics().groundwaterHandoffPresent());
        assertTrue(slice.validation().metrics().plainHillTransitionOk());
    }

    @Test
    void hillOnlyVerticalSliceProducesNonNoopMetrics() throws Exception {
        TerrainIntentV2 intent = hillOnlyIntent();
        WorldBlueprintV2.Bounds bounds = bounds(64, 48);
        FoundationPlainHillSliceCompilerV2.FoundationPlainHillSliceV2 slice =
                compiler.compile(intent, bounds, 202L);
        assertNotNull(slice.hill());
        assertTrue(slice.validation().metrics().ridgeContinuous());
        assertTrue(slice.validation().metrics().saddleBudgetOk());
    }

    @Test
    void plainHillTransitionWholeAndTileChecksumsMatch() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(PLAIN_HILL);
        WorldBlueprintV2.Bounds bounds = bounds(64, 48);
        FoundationPlainHillSliceCompilerV2.FoundationPlainHillSliceV2 slice =
                compiler.compile(intent, bounds, 303L);
        assertNotNull(slice.plain());
        assertNotNull(slice.hill());
        assertTrue(slice.validation().metrics().plainHillTransitionOk());

        TilePlanV2 tiles = TilePlanV2.of(64, 48, ScaleProfileV2.defaults(ScaleClassV2.SMALL));
        SurfaceFoundationMergeCompilerV2 merge = mergeFromSlice(slice);
        assertEquals(merge.wholeFieldChecksums(), merge.tiledFieldChecksums(tiles));
    }

    @Test
    void rejectsUndeclaredOverlapWideBandAndOwnerTie() throws Exception {
        WorldBlueprintV2.Bounds bounds = bounds(32, 32);
        assertThrows(FoundationSliceException.class,
                () -> compiler.compile(plainHillIntentWithoutRelation(), bounds, 4L));

        SurfaceFoundationExceptionV2 band = assertThrows(
                SurfaceFoundationExceptionV2.class,
                () -> foundationCompiler.compile(
                        bounds.width(), bounds.length(), 2L,
                        List.of(
                                new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                        "coastal-plain", 1, 10, 0,
                                        SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN),
                                new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                        "inland-hills", 2, 20, 1,
                                        SurfaceFoundationPlanV2.SurfaceClassCode.HILL)),
                        List.of(new SurfaceFoundationPlanCompilerV2.InteractionSpec(
                                "wide", "coastal-plain", "inland-hills", 33))));
        assertEquals(SurfaceFoundationFailureCodeV2.TRANSITION_OUT_OF_RANGE, band.failureCode());

        TerrainIntentV2 intent = codec.readTerrainIntent(PLAIN_HILL);
        FoundationPlainHillSliceCompilerV2.FoundationPlainHillSliceV2 slice =
                compiler.compile(intent, bounds(64, 48), 5L);
        List<SurfaceFoundationPlanCompilerV2.OwnerSpec> ownerSpecs = slice.foundation().owners().stream()
                .map(owner -> new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        owner.ownerId(), owner.ownerIndex(), owner.priority(),
                        owner.parentOrdinal(), owner.surfaceClass()))
                .toList();
        SurfaceFoundationPlanV2 noInteraction = foundationCompiler.compile(
                64, 48, 6L, ownerSpecs, List.of());
        SurfaceFoundationMergeCompilerV2 undeclared = new SurfaceFoundationMergeCompilerV2(
                noInteraction, mergeLayers(slice, noInteraction));
        SurfaceFoundationExceptionV2 overlap = assertThrows(
                SurfaceFoundationExceptionV2.class, () -> undeclared.sampleAt(20, 20));
        assertEquals(SurfaceFoundationFailureCodeV2.UNDECLARED_OVERLAP, overlap.failureCode());

        SurfaceFoundationPlanV2 tiedPlan = foundationCompiler.compile(
                64, 48, 7L,
                List.of(
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "coastal-plain", 1, 10, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "inland-hills", 2, 10, 1,
                                SurfaceFoundationPlanV2.SurfaceClassCode.HILL)),
                List.of(new SurfaceFoundationPlanCompilerV2.InteractionSpec(
                        "tie", "coastal-plain", "inland-hills", 4)));
        SurfaceFoundationMergeCompilerV2 tied = new SurfaceFoundationMergeCompilerV2(
                tiedPlan, mergeLayers(slice, tiedPlan));
        SurfaceFoundationExceptionV2 tie = assertThrows(
                SurfaceFoundationExceptionV2.class, () -> tied.sampleAt(20, 20));
        assertEquals(SurfaceFoundationFailureCodeV2.OWNER_TIE, tie.failureCode());
    }

    @Test
    void sealedPlansStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(PLAIN_HILL);
        WorldBlueprintV2.Bounds bounds = bounds(64, 48);
        FoundationPlainHillSliceCompilerV2.FoundationPlainHillSliceV2 slice =
                compiler.compile(intent, bounds, 404L);

        Path plainFile = temp.resolve("plain-plan-v2.json");
        Path hillFile = temp.resolve("hill-range-plan-v2.json");
        Path validationFile = temp.resolve("foundation-validation-artifact-v2.json");
        Path previewFile = temp.resolve("foundation-preview-index-v2.json");
        codec.writePlainPlan(plainFile, slice.plain());
        codec.writeHillRangePlan(hillFile, slice.hill());
        codec.writeFoundationValidationArtifact(validationFile, slice.validation());
        codec.writeFoundationPreviewIndex(previewFile, slice.preview());

        assertEquals(slice.plain(), codec.readPlainPlan(plainFile));
        assertEquals(slice.hill(), codec.readHillRangePlan(hillFile));
        assertEquals(slice.validation(), codec.readFoundationValidationArtifact(validationFile));
        assertEquals(slice.preview(), codec.readFoundationPreviewIndex(previewFile));
        assertEquals(codec.canonicalPlainPlan(slice.plain()), Files.readString(plainFile));
        assertEquals(codec.canonicalHillRangePlan(slice.hill()), Files.readString(hillFile));

        Files.copy(plainFile, Path.of("examples/v2/foundation/plain-plan-v2.json"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(hillFile, Path.of("examples/v2/foundation/hill-range-plan-v2.json"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void backshorePlainsStillParsesFromAzureCoastFixture() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(AZURE);
        TerrainIntentV2.Feature backshore = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS)
                .findFirst()
                .orElseThrow();
        assertTrue(backshore.parameters() instanceof TerrainIntentV2.BackshorePlainsParameters);
        TerrainIntentV2.PlainParameters suggested = BackshorePlainsAliasV2.suggestedPlainParameters(
                (TerrainIntentV2.BackshorePlainsParameters) backshore.parameters());
        assertTrue(suggested.microReliefBlocks().minimum() >= 1);
    }

    @Test
    void plainAndHillRemainExperimentalWithoutBlueprintEmbedding() {
        // Dedicated modules exist but are not registered in the global catalog yet so that
        // DiagnosticBlueprintCompiler / WorldBlueprint checksums stay unchanged (V2-9-02).
        ModuleDescriptorV2 plainModule = new LandformPlainModuleV2().descriptor();
        ModuleDescriptorV2 hillModule = new LandformHillRangeModuleV2().descriptor();
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL, plainModule.lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL, hillModule.lifecycleStatus());

        ModuleDescriptorV2 catalogPlain = catalog.requireFor(TerrainIntentV2.FeatureKind.PLAIN);
        ModuleDescriptorV2 catalogHill = catalog.requireFor(TerrainIntentV2.FeatureKind.HILL_RANGE);
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID, catalogPlain.moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID, catalogHill.moduleId());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL, catalogPlain.lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL, catalogHill.lifecycleStatus());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.PLAIN));
        assertFalse(catalog.hasPreviewCapability(TerrainIntentV2.FeatureKind.HILL_RANGE));
    }

    private List<SurfaceFoundationMergeCompilerV2.OwnerLayer> mergeLayers(
            FoundationPlainHillSliceCompilerV2.FoundationPlainHillSliceV2 slice,
            SurfaceFoundationPlanV2 plan
    ) {
        var plainGen = new com.github.nankotsu029.landformcraft.generator.v2.foundation.plain.PlainGeneratorV2(
                slice.plain());
        var hillGen = new com.github.nankotsu029.landformcraft.generator.v2.foundation.hill.HillRangeGeneratorV2(
                slice.hill());
        return List.of(
                plainGen.toOwnerLayer(plan.owners().get(0)),
                hillGen.toOwnerLayer(plan.owners().get(1)));
    }

    private SurfaceFoundationMergeCompilerV2 mergeFromSlice(
            FoundationPlainHillSliceCompilerV2.FoundationPlainHillSliceV2 slice
    ) {
        return new SurfaceFoundationMergeCompilerV2(slice.foundation(), mergeLayers(slice, slice.foundation()));
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, 62);
    }

    private static TerrainIntentV2 plainOnlyIntent() throws Exception {
        TerrainIntentV2 full = new LandformV2DataCodec().readTerrainIntent(PLAIN_HILL);
        return new TerrainIntentV2(
                full.intentVersion(), full.intentId(), full.theme(), full.coordinateSystem(),
                full.features().stream()
                        .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.PLAIN)
                        .toList(),
                List.of(), full.constraints(), full.environment(), full.mapReferences(),
                full.structures(), full.provenance());
    }

    private static TerrainIntentV2 hillOnlyIntent() throws Exception {
        TerrainIntentV2 full = new LandformV2DataCodec().readTerrainIntent(PLAIN_HILL);
        return new TerrainIntentV2(
                full.intentVersion(), full.intentId(), full.theme(), full.coordinateSystem(),
                full.features().stream()
                        .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.HILL_RANGE)
                        .toList(),
                List.of(), full.constraints(), full.environment(), full.mapReferences(),
                full.structures(), full.provenance());
    }

    private static TerrainIntentV2 plainHillIntentWithoutRelation() throws Exception {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        TerrainIntentV2 full = codec.readTerrainIntent(PLAIN_HILL);
        return new TerrainIntentV2(
                full.intentVersion(), full.intentId(), full.theme(), full.coordinateSystem(),
                full.features(), List.of(), full.constraints(), full.environment(),
                full.mapReferences(), full.structures(), full.provenance());
    }
}
