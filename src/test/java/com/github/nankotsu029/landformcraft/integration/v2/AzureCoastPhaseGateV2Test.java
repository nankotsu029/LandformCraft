package com.github.nankotsu029.landformcraft.integration.v2;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridReaderV1;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerificationV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfaceArtifactsV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfacePublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfaceVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseV2Limits;
import com.github.nankotsu029.landformcraft.format.v2.release.SurfaceReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.CanonicalBlockStreamV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalRasterKernelV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.beach.SandyBeachGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater.BreakwaterHarborGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.cape.RockyCapeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.harbor.HarborBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionCompositorV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionLayerSourcesV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalTransitionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticFieldFactoryV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticPreviewRendererV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalFieldSamplerV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationInputV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationPreviewModuleV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationReportV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidatorV2;
import com.github.nankotsu029.landformcraft.worldedit.WorldEditTestPlatformSupport;
import com.github.nankotsu029.landformcraft.worldedit.v2.WorldEditOfflineTileReaderV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-2-12 parent-phase evidence; this fixture does not enable Paper mutation. */
class AzureCoastPhaseGateV2Test {
    private static final Path REQUEST = Path.of("examples/v2/diagnostic/azure-coast.request-v2.json");
    private static final Path INTENT = Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");
    private static final int WIDTH = 400;
    private static final int LENGTH = 400;
    private static final int MIN_Y = 32;
    private static final int MAX_Y = 72;
    private static final int WATER_LEVEL = 50;
    private static final int SCALE = 1_000_000;

    @Test
    void azureCoastPassesTheOfflinePhaseGate(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root.resolve("fixture"));

        assertEquals(List.of(
                TerrainIntentV2.FeatureKind.SANDY_BEACH,
                TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                TerrainIntentV2.FeatureKind.ROCKY_CAPE),
                List.of(
                        TerrainIntentV2.FeatureKind.SANDY_BEACH,
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE).stream()
                        .filter(kind -> new BuiltInLandformModuleCatalogV2().requireFor(kind).lifecycleStatus()
                                == ModuleDescriptorV2.LifecycleStatus.SUPPORTED)
                        .toList());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                module(fixture.blueprint(), CoastalTransitionModuleV2.MODULE_ID).lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                module(fixture.blueprint(), CoastalValidationPreviewModuleV2.MODULE_ID).lifecycleStatus());

        CoastalValidationReportV2 report = fixture.report();
        assertTrue(report.passesHardValidation(), () -> report.issues().toString());
        assertTrue(report.metrics().stream().anyMatch(metric -> metric.metricId().equals("coastal.beach.width-p50")));
        assertTrue(report.metrics().stream().anyMatch(metric -> metric.metricId().equals("coastal.harbor.depth-p50")));
        assertTrue(report.metrics().stream().anyMatch(metric -> metric.metricId().equals("coastal.cape.rock-exposure")));
        assertEquals(11, fixture.preview().layers().size());
        assertTrue(fixture.fields().hardProtectedCells() > 0);

        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(
                fixture.constraintRoot(), fixture.desiredLandWater())) {
            assertArrayEquals(fixture.fields().landWater(),
                    reader.readWindow(0, 0, WIDTH, LENGTH).toRawArray());
        }

        CoastalFieldSamplerV2 corrupted = new CoastalFieldSamplerV2() {
            @Override public int width() { return WIDTH; }
            @Override public int length() { return LENGTH; }
            @Override public int valueAt(String fieldId, int x, int z) {
                int value = fixture.fields().valueAt(fieldId, x, z);
                return fieldId.equals(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID) && x == 0 && z == 0
                        ? 1 - value : value;
            }
        };
        CoastalValidationReportV2 corruptedReport = new CoastalValidatorV2().validate(
                new CoastalValidationInputV2(fixture.blueprint(), fixture.fields(), corrupted), () -> false);
        assertTrue(corruptedReport.issues().stream()
                .anyMatch(issue -> issue.ruleId().equals("coastal.land-water.residual")));

        OfflineTilePlanV2 whole = new OfflineTilePlanV2(
                1, "whole-north-west", 0, 0, 0, 0, 256, 256, MIN_Y, MAX_Y);
        List<OfflineTilePlanV2> dispatchTiles = tilePlans(256, 256, 128, MIN_Y, MAX_Y);
        TerrainBlockResolver tileDispatch = (x, y, z) -> {
            long owners = dispatchTiles.stream().filter(tile -> tile.contains(x, y, z)).count();
            if (owners != 1) throw new IllegalStateException("block must have exactly one tile owner");
            return fixture.resolver().blockStateAt(x, y, z);
        };
        assertEquals(CanonicalBlockStreamV2.checksum(whole, fixture.resolver(), () -> false),
                CanonicalBlockStreamV2.checksum(whole, tileDispatch, () -> false));

        RuntimeGenerators reversed = RuntimeGenerators.create(fixture.blueprint(), true);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(fixture.compositionChecksums(),
                    reversed.compositor().wholeFieldChecksums(HardLandWaterSourceV2.NONE));
            List<OfflineTilePlanV2> reversePlans = new ArrayList<>(fixture.tilePlans());
            Collections.reverse(reversePlans);
            TileExport parallel = writeTiles(
                    root.resolve("parallel"), reversePlans, fixture.blueprint(), fixture.resolver(), 4);
            assertEquals(fixture.tileChecksums(), parallel.checksums());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        WorldEditTestPlatformSupport.ensureRegistered();
        for (String representative : List.of("tile-00-01", "tile-01-01", "tile-02-01")) {
            TileFile tile = fixture.tiles().stream()
                    .filter(candidate -> candidate.artifact().tileId().equals(representative))
                    .findFirst().orElseThrow();
            var verified = new WorldEditOfflineTileReaderV2().verify(
                    tile.schematic().getParent(), tile.artifact(), () -> false);
            assertEquals(tile.artifact().semanticChecksum(), verified.worldEditSemanticChecksum());
        }

        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                root.resolve("release"), "azure-coast-v2", fixture.releaseSource(), true, () -> false);
        ReleaseCoreVerificationV2 directory = new ReleaseSurfaceVerifierV2().verify(release.releaseDirectory());
        ReleaseCoreVerificationV2 zip = new ReleaseSurfaceVerifierV2().verify(release.zip().orElseThrow());
        assertEquals(directory.manifest(), zip.manifest());
        assertEquals(List.of("surface-2_5d"), directory.manifest().requiredCapabilities());

        assertThrows(IOException.class, () -> new ReleaseSurfaceVerifierV2(new ReleaseV2Limits(
                257, 1_024, 1_024, 128L * 1024L * 1024L,
                128L * 1024L * 1024L, 64 * 1024)).verify(release.releaseDirectory()));

        Path cancelRoot = root.resolve("cancel-release");
        assertThrows(CancellationException.class, () -> new ReleaseSurfacePublisherV2().publish(
                cancelRoot, "azure-coast-cancel", fixture.releaseSource(), true, () -> true));
        assertFalse(Files.exists(cancelRoot.resolve("azure-coast-cancel")));
        if (Files.exists(cancelRoot)) {
            try (var paths = Files.list(cancelRoot)) {
                assertTrue(paths.noneMatch(path -> path.getFileName().toString().startsWith(".release-v2-surface-")));
            }
        }

        Path preview = release.releaseDirectory().resolve(
                "previews/" + fixture.preview().layers().getFirst().path());
        byte[] tampered = Files.readAllBytes(preview);
        tampered[tampered.length - 1] ^= 1;
        Files.write(preview, tampered);
        assertThrows(IOException.class, () -> new ReleaseSurfaceVerifierV2().verify(release.releaseDirectory()));
    }

    @Test
    void admitsOneThousandSquareWindowsWithoutDenseWorldAllocation() {
        int regionalWindow = 128 + 2 * 64;
        int transitionWindow = 128 + 2 * 32;
        long raster = CoastalRasterKernelV2.estimateWindowRetainedBytes(regionalWindow, regionalWindow);
        long beach = SandyBeachGeneratorV2.estimateWindowRetainedBytes(regionalWindow, regionalWindow);
        long harbor = HarborBasinGeneratorV2.estimateWindowRetainedBytes(regionalWindow, regionalWindow);
        long breakwater = BreakwaterHarborGeneratorV2.estimateWindowRetainedBytes(
                regionalWindow, regionalWindow);
        long cape = RockyCapeGeneratorV2.estimateWindowRetainedBytes(regionalWindow, regionalWindow);
        long transition = CoastalTransitionCompositorV2.estimateWindowRetainedBytes(
                transitionWindow, transitionWindow);
        long simultaneousGenerationUpperBound = raster + beach + harbor + breakwater + cape + transition;
        long onePreviewArgb = 1_000L * 1_000L * Integer.BYTES;
        long oneTileBlocks = 128L * 128L * (MAX_Y - MIN_Y + 1L);
        long tileWorkingUpperBound = oneTileBlocks * 2L
                + OfflineTileSchematicWriterV2.estimatePaletteRetainedBytes(16, 16L * 512L);
        long admittedPeak = Math.max(simultaneousGenerationUpperBound,
                Math.max(onePreviewArgb + 1024L * 1024L, tileWorkingUpperBound));

        assertTrue(raster <= CoastalRasterKernelV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        assertTrue(beach <= SandyBeachGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        assertTrue(harbor <= HarborBasinGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        assertTrue(breakwater <= BreakwaterHarborGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        assertTrue(cape <= RockyCapeGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        assertTrue(transition <= CoastalTransitionCompositorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        assertTrue(admittedPeak <= GenerationRequestV2.MAX_RESIDENT_BYTES);
        assertTrue(oneTileBlocks <= OfflineTileSchematicWriterV2.MAXIMUM_ENCODED_BLOCK_BYTES);
        assertEquals(64, tilePlans(1_000, 1_000, 128, MIN_Y, MAX_Y).size());
    }

    private static ModuleDescriptorV2 module(WorldBlueprintV2 blueprint, String moduleId) {
        return blueprint.modules().stream().filter(module -> module.moduleId().equals(moduleId))
                .findFirst().orElseThrow();
    }

    private static List<OfflineTilePlanV2> tilePlans(
            int width, int length, int tileSize, int minY, int maxY
    ) {
        List<OfflineTilePlanV2> plans = new ArrayList<>();
        int xCount = (width + tileSize - 1) / tileSize;
        int zCount = (length + tileSize - 1) / tileSize;
        for (int zIndex = 0; zIndex < zCount; zIndex++) {
            for (int xIndex = 0; xIndex < xCount; xIndex++) {
                int originX = xIndex * tileSize;
                int originZ = zIndex * tileSize;
                plans.add(new OfflineTilePlanV2(
                        1, String.format(Locale.ROOT, "tile-%02d-%02d", xIndex, zIndex),
                        xIndex, zIndex, originX, originZ,
                        Math.min(tileSize, width - originX), Math.min(tileSize, length - originZ),
                        minY, maxY));
            }
        }
        return List.copyOf(plans);
    }

    private static TileExport writeTiles(
            Path root,
            List<OfflineTilePlanV2> plans,
            WorldBlueprintV2 blueprint,
            TerrainBlockResolver resolver,
            int threads
    ) throws Exception {
        Files.createDirectories(root);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<TileFile>> futures = new ArrayList<>();
            for (OfflineTilePlanV2 plan : plans) {
                futures.add(executor.submit(() -> {
                    Path schematic = root.resolve(plan.defaultSchematicFileName());
                    OfflineTileArtifactV2 artifact = new OfflineTileSchematicWriterV2().write(
                            schematic, plan, blueprint.canonicalChecksum(), resolver, () -> false);
                    Path metadata = root.resolve(plan.tileId() + ".json");
                    new OfflineTileArtifactCodecV2().write(metadata, artifact);
                    return new TileFile(artifact, metadata, schematic);
                }));
            }
            List<TileFile> files = new ArrayList<>();
            for (Future<TileFile> future : futures) files.add(future.get());
            files.sort(java.util.Comparator.comparing(tile -> tile.artifact().tileId()));
            return new TileExport(files);
        } finally {
            executor.shutdownNow();
        }
    }

    private record Fixture(
            WorldBlueprintV2 blueprint,
            AzureFields fields,
            TerrainBlockResolver resolver,
            CoastalValidationReportV2 report,
            CoastalPreviewIndexV2 preview,
            FieldArtifactDescriptorV2 desiredLandWater,
            Path constraintRoot,
            List<OfflineTilePlanV2> tilePlans,
            List<TileFile> tiles,
            Map<CoastalTransitionCompositorV2.CompositionField, String> compositionChecksums,
            SurfaceReleaseSourceV2 releaseSource
    ) {
        static Fixture create(Path root) throws Exception {
            Files.createDirectories(root);
            LandformV2DataCodec data = new LandformV2DataCodec();
            GenerationRequestV2 request = data.readGenerationRequest(REQUEST);
            TerrainIntentV2 draftIntent = data.readTerrainIntent(INTENT);
            assertEquals(request.requestId(), draftIntent.intentId());
            Path requestPath = root.resolve("generation-request.json");
            data.writeGenerationRequest(requestPath, request);
            String requestChecksum = data.generationRequestChecksum(request);

            WorldBlueprintV2 draftBlueprint = compile(request, draftIntent, requestChecksum);
            RuntimeGenerators draftRuntime = RuntimeGenerators.create(draftBlueprint, false);
            AzureFields draftFields = AzureFields.generate(draftRuntime, draftBlueprint);

            Path constraintRoot = root.resolve("constraints");
            List<FieldArtifactDescriptorV2> descriptors = writeConstraintFields(
                    constraintRoot, request, draftFields);
            FieldArtifactDescriptorV2 desired = field(
                    descriptors, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER);
            // V2-18-07: the sealed intent binding references the declared INPUT mask digest (its desired
            // source), not the generated field's own checksum. The field index below keeps the field's
            // content-address as its canonicalArtifactId.
            String inputMaskDigest = request.constraintMaps().getFirst().expectedSha256();
            String frozenJson = Files.readString(INTENT)
                    .replace("0".repeat(64), inputMaskDigest);
            TerrainIntentV2 intent = data.readTerrainIntent(frozenJson, "azure-coast-phase-gate-intent");
            Path intentPath = root.resolve("terrain-intent.json");
            data.writeTerrainIntent(intentPath, intent);
            String intentChecksum = data.terrainIntentChecksum(intent);

            ConstraintFieldIndexV2 index = new ConstraintFieldIndexV2(
                    1, request.requestId(), requestChecksum, intentChecksum,
                    List.of(new ConstraintFieldIndexV2.AppliedBinding(
                            "coast-mask-binding", "constraint-source:coast-mask",
                            TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                            TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST,
                            0, 0, "constraint:land-water:sha256-" + desired.semanticChecksum(),
                            desired.definition().fieldId(),
                            descriptors.stream().map(field -> field.definition().fieldId()).toList(),
                            List.of(new ConstraintFieldIndexV2.LabelEntry(0, 0, "water"),
                                    new ConstraintFieldIndexV2.LabelEntry(1, 1, "land")))),
                    descriptors);
            Path indexPath = constraintRoot.resolve("index.json");
            ConstraintFieldIndexCodecV2 indexCodec = new ConstraintFieldIndexCodecV2();
            indexCodec.write(indexPath, index);
            indexCodec.readAndVerify(indexPath, constraintRoot, requestChecksum, intentChecksum);

            WorldBlueprintV2 blueprint = compile(request, intent, requestChecksum);
            Path blueprintPath = root.resolve("world-blueprint.json");
            data.writeWorldBlueprint(blueprintPath, blueprint);
            RuntimeGenerators runtime = RuntimeGenerators.create(blueprint, false);
            AzureFields fields = AzureFields.generate(runtime, blueprint);
            assertArrayEquals(draftFields.landWater(), fields.landWater());
            assertArrayEquals(draftFields.surfaceHeight(), fields.surfaceHeight());

            CoastalValidationInputV2 validationInput = new CoastalValidationInputV2(
                    blueprint, fields, fields);
            CoastalValidationReportV2 report = new CoastalValidatorV2().validate(
                    validationInput, () -> false);
            assertTrue(report.passesHardValidation(), () -> report.issues().toString());
            Path validationPath = root.resolve("coastal-validation.json");
            new com.github.nankotsu029.landformcraft.format.v2.validation.CoastalValidationArtifactCodecV2()
                    .write(validationPath, new CoastalValidationArtifactV2(
                            blueprint.canonicalChecksum(), CoastalValidationArtifactV2.VALIDATOR_VERSION,
                            new CoastalValidationArtifactV2.CoastalValidationReport(
                                    report.metrics(), report.issues())));

            Path previewRoot = root.resolve("previews");
            CoastalPreviewIndexV2 preview = new CoastalDiagnosticPreviewRendererV2().render(
                    previewRoot, blueprint.canonicalChecksum(),
                    CoastalDiagnosticFieldFactoryV2.create(validationInput, report), () -> false);

            TerrainBlockResolver resolver = fields.resolver(MIN_Y, WATER_LEVEL);
            List<OfflineTilePlanV2> tilePlans = AzureCoastPhaseGateV2Test.tilePlans(
                    WIDTH, LENGTH, request.generation().tileSize(), MIN_Y, MAX_Y);
            TileExport tileExport = writeTiles(root.resolve("tiles"), tilePlans, blueprint, resolver, 1);
            List<SurfaceReleaseSourceV2.TileSource> releaseTiles = tileExport.files().stream()
                    .map(tile -> new SurfaceReleaseSourceV2.TileSource(
                            tile.artifact().tileId(), tile.metadata(), tile.schematic()))
                    .toList();
            SurfaceReleaseSourceV2 source = new SurfaceReleaseSourceV2(
                    requestPath, intentPath, blueprintPath, indexPath, constraintRoot,
                    validationPath, previewRoot.resolve("index.json"), previewRoot, releaseTiles);
            return new Fixture(
                    blueprint, fields, resolver, report, preview, desired, constraintRoot,
                    tilePlans, tileExport.files(),
                    runtime.compositor().wholeFieldChecksums(HardLandWaterSourceV2.NONE), source);
        }

        Map<String, String> tileChecksums() {
            return new TileExport(tiles).checksums();
        }

        private static WorldBlueprintV2 compile(
                GenerationRequestV2 request, TerrainIntentV2 intent, String requestChecksum
        ) {
            return new DiagnosticBlueprintCompilerV2().compile(new DiagnosticCompileRequestV2(
                    request.requestId(),
                    new GenerationBounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL),
                    request.generation().tileSize(), request.generation().globalSeed(), requestChecksum,
                    DiagnosticCompileRequestV2.defaultBudget()), intent);
        }

        private static List<FieldArtifactDescriptorV2> writeConstraintFields(
                Path root, GenerationRequestV2 request, AzureFields fields
        ) throws IOException {
            String sourceChecksum = request.constraintMaps().getFirst().expectedSha256();
            FieldArtifactDescriptorV2.Provenance provenance = new FieldArtifactDescriptorV2.Provenance(
                    FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP,
                    "constraint-source:coast-mask", sourceChecksum,
                    "numeric-png", "1", "pixel-center-v1");
            LfcGridWriterV1 writer = new LfcGridWriterV1();
            List<FieldArtifactDescriptorV2> result = new ArrayList<>();
            result.add(writer.write(root, "fields/azure-land-desired.lfgrid",
                    definition("constraint.azure-coast.land.desired",
                            FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                            FieldArtifactDescriptorV2.FieldValueType.U8, 1_000_000L),
                    provenance, (x, z) -> fields.landWater()[z * WIDTH + x], () -> false));
            result.add(writer.write(root, "fields/azure-land-actual.lfgrid",
                    definition("constraint.azure-coast.land.actual",
                            FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER,
                            FieldArtifactDescriptorV2.FieldValueType.U8, 1_000_000L),
                    provenance, (x, z) -> fields.landWater()[z * WIDTH + x], () -> false));
            result.add(writer.write(root, "fields/azure-land-residual.lfgrid",
                    definition("constraint.azure-coast.land.residual",
                            FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER,
                            FieldArtifactDescriptorV2.FieldValueType.I32, 1L),
                    provenance, (x, z) -> 0, () -> false));
            return List.copyOf(result);
        }

        private static FieldArtifactDescriptorV2.Definition definition(
                String id,
                FieldArtifactDescriptorV2.FieldSemantic semantic,
                FieldArtifactDescriptorV2.FieldValueType type,
                long scale
        ) {
            return new FieldArtifactDescriptorV2.Definition(
                    id, semantic, type, WIDTH, LENGTH,
                    FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                    FieldArtifactDescriptorV2.Sampling.NEAREST,
                    scale, 0L, false, 0);
        }

        private static FieldArtifactDescriptorV2 field(
                List<FieldArtifactDescriptorV2> fields,
                FieldArtifactDescriptorV2.FieldSemantic semantic
        ) {
            return fields.stream().filter(field -> field.definition().semantic() == semantic)
                    .findFirst().orElseThrow();
        }
    }

    private record RuntimeGenerators(
            SandyBeachGeneratorV2 beach,
            HarborBasinGeneratorV2 harbor,
            BreakwaterHarborGeneratorV2 breakwater,
            RockyCapeGeneratorV2 cape,
            CoastalTransitionCompositorV2 compositor
    ) {
        static RuntimeGenerators create(WorldBlueprintV2 blueprint, boolean reverseBindings) {
            CoastalTransitionPlanV2 plan = blueprint.coastalTransitionPlans().getFirst();
            SandyBeachGeneratorV2 beach = null;
            HarborBasinGeneratorV2 harbor = null;
            BreakwaterHarborGeneratorV2 breakwater = null;
            RockyCapeGeneratorV2 cape = null;
            List<CoastalTransitionCompositorV2.LayerBinding> bindings = new ArrayList<>();
            for (CoastalTransitionPlanV2.Contributor contributor : plan.contributors()) {
                CoastalFeaturePlanV2 coastal = blueprint.coastalFeaturePlans().stream()
                        .filter(candidate -> candidate.featureId().equals(contributor.featureId()))
                        .findFirst().orElseThrow();
                switch (contributor.kind()) {
                    case SANDY_BEACH -> {
                        beach = new SandyBeachGeneratorV2(blueprint.sandyBeachPlans().getFirst(),
                                new CoastalRasterKernelV2(coastal, WIDTH, LENGTH));
                        bindings.add(CoastalTransitionLayerSourcesV2.beach(
                                contributor, beach, HardLandWaterSourceV2.NONE));
                    }
                    case HARBOR_BASIN -> {
                        harbor = new HarborBasinGeneratorV2(
                                blueprint.harborBasinPlans().getFirst(), coastal, WIDTH, LENGTH);
                        bindings.add(CoastalTransitionLayerSourcesV2.harbor(
                                contributor, harbor, HardLandWaterSourceV2.NONE));
                    }
                    case BREAKWATER_HARBOR -> {
                        breakwater = new BreakwaterHarborGeneratorV2(
                                blueprint.breakwaterHarborPlans().getFirst(), coastal, WIDTH, LENGTH);
                        bindings.add(CoastalTransitionLayerSourcesV2.breakwater(contributor, breakwater));
                    }
                    case ROCKY_CAPE -> {
                        cape = new RockyCapeGeneratorV2(
                                blueprint.rockyCapePlans().getFirst(), coastal, WIDTH, LENGTH);
                        bindings.add(CoastalTransitionLayerSourcesV2.cape(
                                contributor, cape, HardLandWaterSourceV2.NONE));
                    }
                    default -> throw new AssertionError("unexpected coastal contributor");
                }
            }
            if (reverseBindings) Collections.reverse(bindings);
            return new RuntimeGenerators(beach, harbor, breakwater, cape,
                    new CoastalTransitionCompositorV2(plan, WIDTH, LENGTH, bindings));
        }
    }

    private static final class AzureFields implements CoastalFieldSamplerV2 {
        private final Map<String, int[]> values;
        private final int[] landWater;
        private final int[] surfaceHeight;
        private final boolean[] active;
        private final int hardProtectedCells;

        private AzureFields(
                Map<String, int[]> values,
                int[] landWater,
                int[] surfaceHeight,
                boolean[] active,
                int hardProtectedCells
        ) {
            this.values = Map.copyOf(values);
            this.landWater = landWater;
            this.surfaceHeight = surfaceHeight;
            this.active = active;
            this.hardProtectedCells = hardProtectedCells;
        }

        static AzureFields generate(RuntimeGenerators runtime, WorldBlueprintV2 blueprint) {
            Map<String, int[]> fields = new LinkedHashMap<>();
            for (String id : List.of(
                    CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID,
                    CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID,
                    CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID,
                    CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID,
                    CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID,
                    CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID,
                    CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID,
                    CoastalFoundationModuleV2.CAPE_ROCK_EXPOSURE_FIELD_ID,
                    CoastalFoundationModuleV2.CAPE_DESCRIPTOR_INDEX_FIELD_ID,
                    CoastalTransitionModuleV2.LAND_WATER_FIELD_ID,
                    CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID,
                    CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID,
                    CoastalTransitionModuleV2.CONFLICT_FIELD_ID)) {
                fields.put(id, new int[WIDTH * LENGTH]);
            }
            boolean[] active = new boolean[WIDTH * LENGTH];
            int[] desiredLandWater = new int[WIDTH * LENGTH];
            int[] desiredHeight = new int[WIDTH * LENGTH];
            for (int z = 0; z < LENGTH; z++) {
                for (int x = 0; x < WIDTH; x++) {
                    int index = z * WIDTH + x;
                    CoastalTransitionCompositorV2.CompositionSample sample =
                            runtime.compositor().sampleAt(x, z, HardLandWaterSourceV2.NONE);
                    active[index] = sample.active();
                    desiredLandWater[index] = sample.active() ? sample.landWater() : z < 120 ? 1 : 0;
                    desiredHeight[index] = sample.active() ? sample.surfaceHeightMillionths()
                            : (z < 120 ? WATER_LEVEL + 4 : WATER_LEVEL - 8) * SCALE;
                }
            }
            HardLandWaterSourceV2 hard = (x, z) -> desiredLandWater[z * WIDTH + x] == 1
                    ? HardLandWaterSourceV2.Classification.LAND
                    : HardLandWaterSourceV2.Classification.WATER;
            int protectedCells = 0;
            for (int z = 0; z < LENGTH; z++) {
                for (int x = 0; x < WIDTH; x++) {
                    int index = z * WIDTH + x;
                    SandyBeachGeneratorV2.BeachSample beach = runtime.beach().sampleAt(
                            x, z, HardLandWaterSourceV2.NONE);
                    HarborBasinGeneratorV2.HarborSample harbor = runtime.harbor().sampleAt(
                            x, z, HardLandWaterSourceV2.NONE);
                    BreakwaterHarborGeneratorV2.BreakwaterSample breakwater = runtime.breakwater().sampleAt(x, z);
                    RockyCapeGeneratorV2.CapeSample cape = runtime.cape().sampleAt(
                            x, z, HardLandWaterSourceV2.NONE);
                    put(fields, CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID, index,
                            beach.localWidthMillionths());
                    put(fields, CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID, index,
                            beach.band().rawValue());
                    put(fields, CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID, index,
                            harbor.region().rawValue());
                    put(fields, CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID, index, harbor.water());
                    put(fields, CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID, index,
                            harbor.depthMillionths());
                    put(fields, CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID, index,
                            breakwater.region().rawValue());
                    put(fields, CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID, index,
                            cape.region().rawValue());
                    put(fields, CoastalFoundationModuleV2.CAPE_ROCK_EXPOSURE_FIELD_ID, index,
                            cape.rockExposure());
                    put(fields, CoastalFoundationModuleV2.CAPE_DESCRIPTOR_INDEX_FIELD_ID, index,
                            cape.descriptorIndex());

                    if (active[index]) {
                        CoastalTransitionCompositorV2.CompositionSample composed =
                                runtime.compositor().sampleAt(x, z, hard);
                        if (!composed.hardProtected()
                                || composed.landWater() != desiredLandWater[index]) {
                            throw new AssertionError("hard land-water was not preserved at " + x + ',' + z);
                        }
                        protectedCells++;
                        put(fields, CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, index,
                                composed.landWater());
                        put(fields, CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, index,
                                composed.surfaceHeightMillionths());
                        put(fields, CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, index,
                                composed.ownerIndex());
                        put(fields, CoastalTransitionModuleV2.CONFLICT_FIELD_ID, index,
                                composed.conflict());
                    } else {
                        put(fields, CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, index,
                                desiredLandWater[index]);
                        put(fields, CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, index,
                                desiredHeight[index]);
                        put(fields, CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, index, 0);
                        put(fields, CoastalTransitionModuleV2.CONFLICT_FIELD_ID, index, 0);
                    }
                }
            }
            assertEquals(blueprint.space().bounds().width(), WIDTH);
            return new AzureFields(fields,
                    fields.get(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID),
                    fields.get(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID),
                    active, protectedCells);
        }

        private static void put(Map<String, int[]> fields, String id, int index, int value) {
            fields.get(id)[index] = value;
        }

        @Override public int width() { return WIDTH; }
        @Override public int length() { return LENGTH; }

        @Override
        public int valueAt(String fieldId, int globalX, int globalZ) {
            int[] field = values.get(fieldId);
            if (field == null) return CoastalValidationInputV2.NO_DATA;
            if (globalX < 0 || globalX >= WIDTH || globalZ < 0 || globalZ >= LENGTH) {
                throw new IndexOutOfBoundsException("coordinate outside Azure Coast fields");
            }
            return field[globalZ * WIDTH + globalX];
        }

        int[] landWater() { return landWater; }
        int[] surfaceHeight() { return surfaceHeight; }
        int hardProtectedCells() { return hardProtectedCells; }

        TerrainBlockResolver resolver(int minY, int waterLevel) {
            int[] beach = values.get(CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID);
            int[] breakwater = values.get(CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID);
            int[] cape = values.get(CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID);
            return (x, y, z) -> {
                int index = z * WIDTH + x;
                if (y == minY) return "minecraft:bedrock";
                int surface = Math.floorDiv(surfaceHeight[index], SCALE);
                int breakwaterRegion = breakwater[index];
                boolean submergedBreakwater = breakwaterRegion == 2 || breakwaterRegion == 3;
                if (landWater[index] == 0 || submergedBreakwater) {
                    if (y <= surface) {
                        if (breakwaterRegion != 0 && y >= surface - 1) return "minecraft:stone_bricks";
                        return y == surface ? "minecraft:gravel" : "minecraft:stone";
                    }
                    if (y <= waterLevel) return "minecraft:water";
                    return "minecraft:air";
                }
                if (y > surface) return "minecraft:air";
                if (breakwaterRegion != 0) {
                    return y >= surface - 1 ? "minecraft:stone_bricks" : "minecraft:cobblestone";
                }
                if (beach[index] == 2 || beach[index] == 3) {
                    return y == surface ? "minecraft:sand"
                            : y >= surface - 2 ? "minecraft:sandstone" : "minecraft:stone";
                }
                if (cape[index] != 0) {
                    return y == surface ? "minecraft:cobblestone" : "minecraft:stone";
                }
                return y == surface ? "minecraft:grass_block"
                        : y >= surface - 2 ? "minecraft:dirt" : "minecraft:stone";
            };
        }
    }

    private record TileFile(OfflineTileArtifactV2 artifact, Path metadata, Path schematic) { }

    private record TileExport(List<TileFile> files) {
        TileExport {
            files = files.stream().sorted(java.util.Comparator.comparing(
                    tile -> tile.artifact().tileId())).toList();
        }

        Map<String, String> checksums() {
            Map<String, String> result = new HashMap<>();
            for (TileFile tile : files) {
                result.put(tile.artifact().tileId(),
                        tile.artifact().semanticChecksum() + ':' + tile.artifact().artifactChecksum());
            }
            return Map.copyOf(result);
        }
    }
}
