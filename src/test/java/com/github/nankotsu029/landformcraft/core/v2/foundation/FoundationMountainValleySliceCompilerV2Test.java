package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformMountainRangeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformValleyModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.mountain.MountainRangeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.valley.ValleyGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.FjordGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mountain.MountainGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MountainRangeComponentCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationMountainValleySliceCompilerV2Test {
    private static final Path MOUNTAIN_VALLEY =
            Path.of("examples/v2/foundation/mountain-valley-slice.terrain-intent-v2.json");
    private static final Path ALPINE =
            Path.of("examples/v2/hydrology/alpine-ridge-skeleton.terrain-intent-v2.json");
    private static final Path FJORD =
            Path.of("examples/v2/hydrology/fjord-glacial-u.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationMountainValleySliceCompilerV2 compiler =
            new FoundationMountainValleySliceCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void mountainOnlyVerticalSliceProducesRidgeAndPeakBudgetMetrics() throws Exception {
        TerrainIntentV2 intent = mountainOnlyIntent();
        FoundationMountainValleySliceCompilerV2.FoundationMountainValleySliceV2 slice =
                compiler.compile(intent, sliceBounds(), 101L);
        assertNotNull(slice.mountain());
        assertTrue(slice.validation().metrics().ridgeGraphOk());
        assertTrue(slice.validation().metrics().peakPassBudgetOk());
        assertEquals(
                MountainRangeComponentCatalogV2.derivedId(
                        MountainRangeComponentCatalogV2.ComponentRole.PEAK, "central-range", 1),
                slice.mountain().peaks().getFirst().peakId());
    }

    @Test
    void valleyOnlyVerticalSliceProducesFloorMetrics() throws Exception {
        TerrainIntentV2 intent = valleyOnlyIntent();
        FoundationMountainValleySliceCompilerV2.FoundationMountainValleySliceV2 slice =
                compiler.compile(intent, sliceBounds(), 202L);
        assertNotNull(slice.valley());
        assertTrue(slice.validation().metrics().valleyConnectionOk());
        assertEquals(TerrainIntentV2.ValleyCrossSection.U_PROFILE, slice.valley().crossSection());
    }

    @Test
    void mountainValleyTransitionWholeAndTileChecksumsMatch() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(MOUNTAIN_VALLEY);
        FoundationMountainValleySliceCompilerV2.FoundationMountainValleySliceV2 slice =
                compiler.compile(intent, sliceBounds(), 303L);
        assertNotNull(slice.mountain());
        assertNotNull(slice.valley());
        assertTrue(slice.validation().metrics().valleyMountainTransitionOk());
        assertTrue(slice.validation().metrics().floorOwnerConflictFree());

        TilePlanV2 tiles = TilePlanV2.of(48, 48, ScaleProfileV2.defaults(ScaleClassV2.SMALL));
        SurfaceFoundationMergeCompilerV2 merge = mergeFromSlice(slice);
        assertEquals(merge.wholeFieldChecksums(), merge.tiledFieldChecksums(tiles));
    }

    @Test
    void rejectsUndeclaredOverlapAndMissingValleyConnection() throws Exception {
        WorldBlueprintV2.Bounds bounds = bounds(32, 32);
        assertThrows(FoundationSliceException.class,
                () -> compiler.compile(mountainValleyIntentWithoutRelation(), bounds, 4L));

        TerrainIntentV2 connected = valleyWithMissingRiverConnection();
        FoundationSliceException missing = assertThrows(
                FoundationSliceException.class,
                () -> compiler.compile(connected, sliceBounds(), 5L));
        assertEquals("v2.valley-connection", missing.ruleId());

        TerrainIntentV2 intent = codec.readTerrainIntent(MOUNTAIN_VALLEY);
        FoundationMountainValleySliceCompilerV2.FoundationMountainValleySliceV2 slice =
                compiler.compile(intent, sliceBounds(), 6L);
        List<SurfaceFoundationPlanCompilerV2.OwnerSpec> ownerSpecs = slice.foundation().owners().stream()
                .map(owner -> new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        owner.ownerId(), owner.ownerIndex(), owner.priority(),
                        owner.parentOrdinal(), owner.surfaceClass()))
                .toList();
        SurfaceFoundationPlanV2 noInteraction = foundationCompiler.compile(
                48, 48, 7L, ownerSpecs, List.of());
        SurfaceFoundationMergeCompilerV2 undeclared = new SurfaceFoundationMergeCompilerV2(
                noInteraction, mergeLayers(slice, noInteraction));
        SurfaceFoundationExceptionV2 overlap = assertThrows(
                SurfaceFoundationExceptionV2.class, () -> undeclared.sampleAt(24, 24));
        assertEquals(SurfaceFoundationFailureCodeV2.UNDECLARED_OVERLAP, overlap.failureCode());
    }

    @Test
    void sealedPlansStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(MOUNTAIN_VALLEY);
        FoundationMountainValleySliceCompilerV2.FoundationMountainValleySliceV2 slice =
                compiler.compile(intent, sliceBounds(), 404L);

        Path mountainFile = temp.resolve("mountain-range-plan-v2.json");
        Path valleyFile = temp.resolve("valley-plan-v2.json");
        Path validationFile = temp.resolve("foundation-range-valley-validation-artifact-v2.json");
        Path previewFile = temp.resolve("foundation-preview-index-v2.json");
        codec.writeMountainRangePlan(mountainFile, slice.mountain());
        codec.writeValleyPlan(valleyFile, slice.valley());
        codec.writeFoundationRangeValleyValidationArtifact(validationFile, slice.validation());
        codec.writeFoundationPreviewIndex(previewFile, slice.preview());

        assertEquals(slice.mountain(), codec.readMountainRangePlan(mountainFile));
        assertEquals(slice.valley(), codec.readValleyPlan(valleyFile));
        assertEquals(slice.validation(), codec.readFoundationRangeValleyValidationArtifact(validationFile));
        assertEquals(slice.preview(), codec.readFoundationPreviewIndex(previewFile));

        Files.copy(mountainFile, Path.of("examples/v2/foundation/mountain-range-plan-v2.json"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(valleyFile, Path.of("examples/v2/foundation/valley-plan-v2.json"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void mountainAndValleyRemainExperimentalWithoutBlueprintEmbedding() {
        ModuleDescriptorV2 mountainModule = new LandformMountainRangeModuleV2().descriptor();
        ModuleDescriptorV2 valleyModule = new LandformValleyModuleV2().descriptor();
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL, mountainModule.lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL, valleyModule.lifecycleStatus());

        ModuleDescriptorV2 catalogMountain = catalog.requireFor(TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE);
        ModuleDescriptorV2 catalogValley = catalog.requireFor(TerrainIntentV2.FeatureKind.VALLEY);
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID, catalogMountain.moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID, catalogValley.moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE));
        assertFalse(catalog.hasPreviewCapability(TerrainIntentV2.FeatureKind.VALLEY));
    }

    @Test
    void alpineAndFjordSpecializedFixturesStillParseWithoutFoundationKinds() throws Exception {
        // Guard: V2-9-03 must not silently reinterpret ALPINE/GLACIAL/FJORD seeds.
        // Full field checksum golden remains in MountainGeneratorV2Test / FjordGeneratorV2Test.
        TerrainIntentV2 alpine = codec.readTerrainIntent(ALPINE);
        assertTrue(alpine.features().stream()
                .anyMatch(feature -> feature.kind() == TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE));
        assertTrue(alpine.features().stream()
                .noneMatch(feature -> feature.kind() == TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE));
        TerrainIntentV2.Feature alpineFeature = alpine.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE)
                .findFirst()
                .orElseThrow();
        assertTrue(alpineFeature.parameters() instanceof TerrainIntentV2.MountainParameters);

        TerrainIntentV2 fjord = codec.readTerrainIntent(FJORD);
        assertTrue(fjord.features().stream()
                .anyMatch(feature -> feature.kind() == TerrainIntentV2.FeatureKind.FJORD));
        assertTrue(fjord.features().stream()
                .noneMatch(feature -> feature.kind() == TerrainIntentV2.FeatureKind.VALLEY));

        assertEquals("v2.landform.mountain",
                catalog.requireFor(TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE).moduleId());
        assertEquals("v2.landform.fjord",
                catalog.requireFor(TerrainIntentV2.FeatureKind.FJORD).moduleId());
        assertNotNull(MountainGeneratorV2.VERSION);
        assertNotNull(FjordGeneratorV2.VERSION);
    }

    private List<SurfaceFoundationMergeCompilerV2.OwnerLayer> mergeLayers(
            FoundationMountainValleySliceCompilerV2.FoundationMountainValleySliceV2 slice,
            SurfaceFoundationPlanV2 plan
    ) {
        var mountainGen = new MountainRangeGeneratorV2(slice.mountain());
        var valleyGen = new ValleyGeneratorV2(slice.valley());
        return List.of(
                mountainGen.toOwnerLayer(plan.owners().get(0)),
                valleyGen.toOwnerLayer(plan.owners().get(1)));
    }

    private SurfaceFoundationMergeCompilerV2 mergeFromSlice(
            FoundationMountainValleySliceCompilerV2.FoundationMountainValleySliceV2 slice
    ) {
        return new SurfaceFoundationMergeCompilerV2(slice.foundation(), mergeLayers(slice, slice.foundation()));
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, 62);
    }

    private static WorldBlueprintV2.Bounds sliceBounds() {
        return bounds(48, 48);
    }

    private static TerrainIntentV2 mountainOnlyIntent() throws Exception {
        TerrainIntentV2 full = new LandformV2DataCodec().readTerrainIntent(MOUNTAIN_VALLEY);
        return new TerrainIntentV2(
                full.intentVersion(), full.intentId(), full.theme(), full.coordinateSystem(),
                full.features().stream()
                        .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE)
                        .toList(),
                List.of(), full.constraints(), full.environment(), full.mapReferences(),
                full.structures(), full.provenance());
    }

    private static TerrainIntentV2 valleyOnlyIntent() throws Exception {
        TerrainIntentV2 full = new LandformV2DataCodec().readTerrainIntent(MOUNTAIN_VALLEY);
        return new TerrainIntentV2(
                full.intentVersion(), full.intentId(), full.theme(), full.coordinateSystem(),
                full.features().stream()
                        .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.VALLEY)
                        .toList(),
                List.of(), full.constraints(), full.environment(), full.mapReferences(),
                full.structures(), full.provenance());
    }

    private static TerrainIntentV2 mountainValleyIntentWithoutRelation() throws Exception {
        TerrainIntentV2 full = new LandformV2DataCodec().readTerrainIntent(MOUNTAIN_VALLEY);
        return new TerrainIntentV2(
                full.intentVersion(), full.intentId(), full.theme(), full.coordinateSystem(),
                full.features(), List.of(), full.constraints(), full.environment(),
                full.mapReferences(), full.structures(), full.provenance());
    }

    private static TerrainIntentV2 valleyWithMissingRiverConnection() throws Exception {
        TerrainIntentV2 full = new LandformV2DataCodec().readTerrainIntent(MOUNTAIN_VALLEY);
        TerrainIntentV2.Feature valley = full.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.VALLEY)
                .findFirst()
                .orElseThrow();
        TerrainIntentV2.ValleyParameters parameters = (TerrainIntentV2.ValleyParameters) valley.parameters();
        TerrainIntentV2.Feature rewritten = new TerrainIntentV2.Feature(
                valley.id(),
                valley.kind(),
                valley.geometry(),
                new TerrainIntentV2.ValleyParameters(
                        parameters.crossSection(),
                        parameters.floorHalfWidthBlocks(),
                        parameters.shoulderWidthBlocks(),
                        parameters.maxDepthBlocks(),
                        parameters.mountainTransitionBandBlocks(),
                        TerrainIntentV2.ValleyConnectionRole.RIVER_CORRIDOR),
                valley.priority(),
                valley.provenance());
        return new TerrainIntentV2(
                full.intentVersion(), full.intentId(), full.theme(), full.coordinateSystem(),
                List.of(rewritten), List.of(), full.constraints(), full.environment(),
                full.mapReferences(), full.structures(), full.provenance());
    }
}
