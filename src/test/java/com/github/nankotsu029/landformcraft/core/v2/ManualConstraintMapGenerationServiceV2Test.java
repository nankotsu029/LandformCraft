package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridReaderV1;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.preview.v2.ConstraintMapPreviewRendererV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualConstraintMapGenerationServiceV2Test {
    private final ManualConstraintMapGenerationServiceV2 service =
            new ManualConstraintMapGenerationServiceV2();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void manualMaskHeightAndLabelsProduceCanonicalFieldsAndEightPreviews(@TempDir Path root)
            throws Exception {
        Fixture fixture = fixture(root, false);

        ManualConstraintMapResultV2 prepared = service.prepareManual(
                fixture.requestPath(), fixture.request(), fixture.intent(),
                root.resolve("prepared"), () -> false);
        TerrainIntentV2 frozen = withBindings(fixture.intent(), prepared.canonicalBindings());
        ManualConstraintMapResultV2 result = service.generateFrozen(
                fixture.requestPath(), fixture.request(), frozen,
                root.resolve("frozen"), () -> false);

        assertEquals(3, result.canonicalBindings().size());
        assertEquals(8, result.diagnosticPreviews().size());
        assertEquals(ConstraintMapPreviewRendererV2.FILE_NAMES,
                result.diagnosticPreviews().stream().map(path -> path.getFileName().toString()).toList());
        assertTrue(result.diagnosticPreviews().stream().allMatch(Files::isRegularFile));
        assertTrue(result.index().bindings().stream()
                .filter(binding -> binding.role() == TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP)
                .findFirst().orElseThrow().labels().stream()
                .anyMatch(label -> label.label().equals("shore")));

        var desiredLand = field(result, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER);
        var actualLand = field(result, FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER);
        var residualHeight = field(result, FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT);
        try (LfcGridReaderV1 desiredReader = LfcGridReaderV1.open(result.bundleRoot(), desiredLand);
             LfcGridReaderV1 actualReader = LfcGridReaderV1.open(result.bundleRoot(), actualLand);
             LfcGridReaderV1 residualReader = LfcGridReaderV1.open(result.bundleRoot(), residualHeight)) {
            assertArrayEquals(
                    desiredReader.readWindow(0, 0, 4, 4).toRawArray(),
                    actualReader.readWindow(0, 0, 4, 4).toRawArray(),
                    "hard land-water must be copied exactly into actual");
            int[] residual = residualReader.readWindow(0, 0, 4, 4).toRawArray();
            assertTrue(java.util.Arrays.stream(residual).anyMatch(value -> value != 0),
                    "soft height guide must retain a measurable residual");
        }
        assertEquals(
                prepared.canonicalBindings().stream().map(TerrainIntentV2.ConstraintMapBinding::artifactId).toList(),
                result.canonicalBindings().stream().map(TerrainIntentV2.ConstraintMapBinding::artifactId).toList());
    }

    @Test
    void frozenChecksumMismatchBudgetAndCancellationPublishNothing(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root, false);
        Path checksumTarget = root.resolve("bad-checksum");
        ConstraintCompilationExceptionV2 checksum = assertThrows(
                ConstraintCompilationExceptionV2.class,
                () -> service.generateFrozen(
                        fixture.requestPath(), fixture.request(), fixture.intent(), checksumTarget, () -> false));
        assertEquals(ConstraintCompilationFailureCodeV2.CHECKSUM_MISMATCH, checksum.code());
        assertFalse(Files.exists(checksumTarget));

        GenerationRequestV2 tinyBudget = copyWithBudget(
                fixture.request(),
                new GenerationRequestV2.ConstraintMapBudget(
                        8, 32L * 1024 * 1024, 32L * 1024 * 1024, 16_000_000,
                        1_024, 96L * 1024 * 1024));
        Path budgetTarget = root.resolve("over-budget");
        ConstraintCompilationExceptionV2 budget = assertThrows(
                ConstraintCompilationExceptionV2.class,
                () -> service.prepareManual(
                        fixture.requestPath(), tinyBudget, fixture.intent(), budgetTarget, () -> false));
        assertEquals(ConstraintCompilationFailureCodeV2.BUDGET_EXCEEDED, budget.code());
        assertFalse(Files.exists(budgetTarget));

        Path cancelledTarget = root.resolve("cancelled");
        assertThrows(CancellationException.class, () -> service.prepareManual(
                fixture.requestPath(), fixture.request(), fixture.intent(), cancelledTarget, () -> true));
        assertFalse(Files.exists(cancelledTarget));
    }

    @Test
    void unknownLabelAndHardNoDataAreRejectedWithoutFallback(@TempDir Path root) throws Exception {
        Fixture unknown = fixture(root.resolve("unknown"), true);
        Path unknownTarget = root.resolve("unknown-result");
        ConstraintCompilationExceptionV2 unknownFailure = assertThrows(
                ConstraintCompilationExceptionV2.class,
                () -> service.prepareManual(
                        unknown.requestPath(), unknown.request(), unknown.intent(), unknownTarget, () -> false));
        assertEquals(ConstraintCompilationFailureCodeV2.UNKNOWN_LABEL, unknownFailure.code());
        assertFalse(Files.exists(unknownTarget));

        Fixture valid = fixture(root.resolve("no-data"), false);
        Path zone = valid.requestPath().getParent().resolve("maps/zones-u16.png");
        writeGray(zone, true, new int[][]{{0, 10, 20, 20}, {10, 10, 20, 20},
                {10, 10, 20, 20}, {10, 10, 20, 20}});
        GenerationRequestV2 changed = replaceChecksum(valid.request(),
                "constraint-source:zones", Sha256.file(zone));
        Path noDataTarget = root.resolve("no-data-result");
        ConstraintCompilationExceptionV2 noDataFailure = assertThrows(
                ConstraintCompilationExceptionV2.class,
                () -> service.prepareManual(
                        valid.requestPath(), changed, valid.intent(), noDataTarget, () -> false));
        assertEquals(ConstraintCompilationFailureCodeV2.INVALID_NO_DATA, noDataFailure.code());
        assertFalse(Files.exists(noDataTarget));
    }

    @Test
    void conflictingHardMasksAreRejected(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root, false);
        Path secondPath = writeGray(root.resolve("maps/land-water-two.png"), false,
                new int[][]{{1, 1, 1, 1}, {1, 0, 0, 1}, {1, 0, 0, 1}, {1, 1, 1, 1}});
        GenerationRequestV2.ConstraintMapSource original = fixture.request().constraintMaps().stream()
                .filter(source -> source.sourceId().equals("constraint-source:land-water"))
                .findFirst().orElseThrow();
        GenerationRequestV2.ConstraintMapSource second = new GenerationRequestV2.ConstraintMapSource(
                "constraint-source:land-water-two",
                "maps/land-water-two.png",
                Sha256.file(secondPath),
                4, 4,
                original.decoderKind(), original.coordinateMapping(), original.encoding());
        List<GenerationRequestV2.ConstraintMapSource> sources = new ArrayList<>(fixture.request().constraintMaps());
        sources.add(second);
        GenerationRequestV2 request = new GenerationRequestV2(
                fixture.request().requestVersion(), fixture.request().requestId(), fixture.request().bounds(),
                fixture.request().prompt(), fixture.request().referenceImages(), sources,
                fixture.request().generation(), fixture.request().constraintMapBudget(),
                fixture.request().foundationBaseLevels(), fixture.request().foundationDetail(),
                java.util.Optional.empty());
        List<TerrainIntentV2.ConstraintMapBinding> bindings = new ArrayList<>(fixture.intent().mapReferences());
        bindings.add(new TerrainIntentV2.ConstraintMapBinding(
                "land-water-two-binding", "constraint-source:land-water-two",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                "constraint:land-water:sha256-" + "d".repeat(64),
                TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0));
        TerrainIntentV2 intent = withBindings(fixture.intent(), bindings);
        Path target = root.resolve("hard-conflict");

        ConstraintCompilationExceptionV2 conflict = assertThrows(
                ConstraintCompilationExceptionV2.class,
                () -> service.prepareManual(fixture.requestPath(), request, intent, target, () -> false));

        assertEquals(ConstraintCompilationFailureCodeV2.HARD_CONSTRAINT_CONFLICT, conflict.code());
        assertFalse(Files.exists(target));
    }

    @Test
    void aSoftMapCannotShadowTheSingleCanonicalRole(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root, false);
        Path secondPath = writeGray(root.resolve("maps/land-water-soft.png"), false,
                new int[][]{{0, 0, 0, 0}, {0, 1, 1, 0}, {0, 1, 1, 0}, {0, 0, 0, 0}});
        GenerationRequestV2.ConstraintMapSource original = fixture.request().constraintMaps().stream()
                .filter(source -> source.sourceId().equals("constraint-source:land-water"))
                .findFirst().orElseThrow();
        GenerationRequestV2.ConstraintMapSource second = new GenerationRequestV2.ConstraintMapSource(
                "constraint-source:land-water-soft",
                "maps/land-water-soft.png",
                Sha256.file(secondPath),
                4, 4,
                original.decoderKind(), original.coordinateMapping(), original.encoding());
        List<GenerationRequestV2.ConstraintMapSource> sources = new ArrayList<>(fixture.request().constraintMaps());
        sources.add(second);
        GenerationRequestV2 request = new GenerationRequestV2(
                fixture.request().requestVersion(), fixture.request().requestId(), fixture.request().bounds(),
                fixture.request().prompt(), fixture.request().referenceImages(), sources,
                fixture.request().generation(), fixture.request().constraintMapBudget(),
                fixture.request().foundationBaseLevels(), fixture.request().foundationDetail(),
                java.util.Optional.empty());
        List<TerrainIntentV2.ConstraintMapBinding> bindings = new ArrayList<>(fixture.intent().mapReferences());
        bindings.add(new TerrainIntentV2.ConstraintMapBinding(
                "land-water-soft-binding", "constraint-source:land-water-soft",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                "constraint:land-water:sha256-" + "e".repeat(64),
                TerrainIntentV2.Strength.SOFT, TerrainIntentV2.Sampling.NEAREST, 0, 500_000));
        Path target = root.resolve("soft-shadow");

        ConstraintCompilationExceptionV2 failure = assertThrows(
                ConstraintCompilationExceptionV2.class,
                () -> service.prepareManual(
                        fixture.requestPath(), request, withBindings(fixture.intent(), bindings),
                        target, () -> false));

        assertEquals(ConstraintCompilationFailureCodeV2.INVALID_BINDING, failure.code());
        assertFalse(Files.exists(target));
    }

    @Test
    void softNoDataIsRenderedAsMagentaAndDoesNotBecomeAConstraintError(@TempDir Path root)
            throws Exception {
        Fixture fixture = fixture(root, false);
        Path zone = fixture.requestPath().getParent().resolve("maps/zones-u16.png");
        writeGray(zone, true, new int[][]{{0, 10, 10, 10}, {10, 20, 20, 10},
                {10, 20, 20, 10}, {10, 10, 10, 10}});
        GenerationRequestV2 request = replaceChecksum(
                fixture.request(), "constraint-source:zones", Sha256.file(zone));
        List<TerrainIntentV2.ConstraintMapBinding> bindings = fixture.intent().mapReferences().stream()
                .map(binding -> binding.role() == TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP
                        ? new TerrainIntentV2.ConstraintMapBinding(
                                binding.id(), binding.sourceId(), binding.role(), binding.artifactId(),
                                TerrainIntentV2.Strength.SOFT, binding.sampling(),
                                binding.toleranceBlocks(), 500_000)
                        : binding)
                .toList();

        ManualConstraintMapResultV2 result = service.prepareManual(
                fixture.requestPath(), request, withBindings(fixture.intent(), bindings),
                root.resolve("soft-no-data"), () -> false);

        BufferedImage zones = ImageIO.read(result.bundleRoot().resolve("previews/zone-label-map.png").toFile());
        BufferedImage errors = ImageIO.read(result.bundleRoot().resolve("previews/constraint-errors.png").toFile());
        try {
            assertEquals(0xffff00ff, zones.getRGB(0, 0));
            assertEquals(0xff202020, errors.getRGB(0, 0));
        } finally {
            zones.flush();
            errors.flush();
        }
    }

    @Test
    void everyHeightMeaningIsExplicitlyAppliedWithoutInference(@TempDir Path root) throws Exception {
        assertHeightMeaning(
                root.resolve("absolute"),
                GenerationRequestV2.HeightValueMeaning.ABSOLUTE_BLOCK_Y,
                20_000_000);
        assertHeightMeaning(
                root.resolve("above-min"),
                GenerationRequestV2.HeightValueMeaning.BLOCKS_ABOVE_REQUEST_MIN_Y,
                0);
        assertHeightMeaning(
                root.resolve("relative-water"),
                GenerationRequestV2.HeightValueMeaning.BLOCKS_RELATIVE_TO_WATER_LEVEL,
                70_000_000);
    }

    @Test
    void cancellationDuringCompilationCleansEveryStagingDirectory(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root, false);
        AtomicInteger checks = new AtomicInteger();
        Path target = root.resolve("mid-operation-cancelled");

        assertThrows(CancellationException.class, () -> service.prepareManual(
                fixture.requestPath(), fixture.request(), fixture.intent(), target,
                () -> checks.incrementAndGet() >= 80));

        assertFalse(Files.exists(target));
        try (var paths = Files.list(root)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString()
                    .startsWith(".tmp-constraint-bundle-")));
        }
    }

    @Test
    void thousandSquareManualPathStaysWithinDeclaredMemoryBudget(@TempDir Path root) throws Exception {
        Path requestPath = root.resolve("request-v2.json");
        Files.writeString(requestPath, "{}");
        Path land = writeConstantGray(root.resolve("maps/land.png"), false, 1_000, 1_000, 1);
        Path height = writeConstantGray(root.resolve("maps/height.png"), true, 1_000, 1_000, 40_500);
        Path zones = writeConstantGray(root.resolve("maps/zones.png"), true, 1_000, 1_000, 10);
        GenerationRequestV2 request = request(
                1_000, 1_000, land, height, zones, GenerationRequestV2.ConstraintMapBudget.defaults());
        TerrainIntentV2 intent = emptyIntent(request.requestId(), draftBindings());

        ManualConstraintMapResultV2 result = service.prepareManual(
                requestPath, request, intent, root.resolve("thousand"), () -> false);

        assertTrue(result.estimatedPeakResidentBytes() < request.constraintMapBudget().maximumResidentBytes());
        assertTrue(result.estimatedArtifactBytes() < request.constraintMapBudget().maximumArtifactBytes());
        assertEquals(1_000, result.index().fields().getFirst().definition().width());
        assertTrue(Files.size(result.fieldIndex()) < 64L * 1024L,
                "1000x1000 values must remain in sidecars, not Blueprint/index JSON");
    }

    private Fixture fixture(Path root, boolean unknownLandLabel) throws Exception {
        Files.createDirectories(root);
        Path land = writeGray(root.resolve("maps/land-water-u8.png"), false,
                new int[][]{{0, 0, 0, 0}, {0, 1, unknownLandLabel ? 2 : 1, 0},
                        {0, 1, 1, 0}, {0, 0, 0, 0}});
        Path height = writeGray(root.resolve("maps/height-u16.png"), true,
                new int[][]{{38_250, 38_750, 39_250, 39_750}, {39_250, 40_500, 41_500, 39_250},
                        {39_750, 41_250, 42_500, 39_750}, {38_250, 38_750, 39_250, 39_750}});
        Path zones = writeGray(root.resolve("maps/zones-u16.png"), true,
                new int[][]{{10, 10, 10, 10}, {10, 20, 20, 10},
                        {10, 20, 20, 10}, {10, 10, 10, 10}});
        String requestJson = Files.readString(Path.of(
                        "examples/v2/manual-constraint-island/request-v2.json"))
                .replace("a".repeat(64), Sha256.file(land))
                .replace("b".repeat(64), Sha256.file(height))
                .replace("c".repeat(64), Sha256.file(zones));
        Path requestPath = root.resolve("request-v2.json");
        Files.writeString(requestPath, requestJson);
        GenerationRequestV2 request = codec.readGenerationRequest(requestPath);
        TerrainIntentV2 intent = codec.readTerrainIntent(
                Path.of("examples/v2/manual-constraint-island/terrain-intent-v2.json"));
        return new Fixture(requestPath, request, intent);
    }

    private void assertHeightMeaning(
            Path root,
            GenerationRequestV2.HeightValueMeaning meaning,
            int expectedMillionths
    ) throws Exception {
        Files.createDirectories(root.resolve("maps"));
        Path requestPath = root.resolve("request-v2.json");
        Files.writeString(requestPath, "{}");
        Path height = writeGray(root.resolve("maps/height.png"), true, new int[][]{{20}});
        String id = "height-" + meaning.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        GenerationRequestV2.ConstraintMapSource source = new GenerationRequestV2.ConstraintMapSource(
                "constraint-source:height", "maps/height.png", Sha256.file(height), 1, 1,
                GenerationRequestV2.DecoderKind.HEIGHT_RASTER, mapping(1, 1),
                new GenerationRequestV2.HeightEncoding(
                        1,
                        GenerationRequestV2.SampleType.U16,
                        GenerationRequestV2.RasterChannel.GRAY,
                        meaning,
                        1_000_000,
                        0,
                        new GenerationRequestV2.IntRange(20, 20),
                        new GenerationRequestV2.NoDataSentinel(65_535)));
        GenerationRequestV2 request = new GenerationRequestV2(
                2, id, new GenerationRequestV2.Bounds(1, 1, -20, 100, 50),
                "explicit height meaning", List.of(), List.of(source),
                new GenerationRequestV2.GenerationSettings(1L, 32),
                GenerationRequestV2.ConstraintMapBudget.defaults(),
                java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty());
        TerrainIntentV2.ConstraintMapBinding binding = new TerrainIntentV2.ConstraintMapBinding(
                "height-binding", source.sourceId(), TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE,
                "constraint:height-guide:sha256-" + "f".repeat(64),
                TerrainIntentV2.Strength.SOFT, TerrainIntentV2.Sampling.NEAREST, 0, 1_000_000);

        ManualConstraintMapResultV2 result = service.prepareManual(
                requestPath, request, emptyIntent(id, List.of(binding)), root.resolve("result"), () -> false);
        FieldArtifactDescriptorV2 desired = field(
                result, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT);
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(result.bundleRoot(), desired)) {
            assertEquals(expectedMillionths, reader.readWindow(0, 0, 1, 1).toRawArray()[0]);
        }
    }

    private static GenerationRequestV2 request(
            int width,
            int length,
            Path land,
            Path height,
            Path zones,
            GenerationRequestV2.ConstraintMapBudget budget
    ) throws Exception {
        var mapping = mapping(width, length);
        List<GenerationRequestV2.ConstraintMapSource> sources = List.of(
                new GenerationRequestV2.ConstraintMapSource(
                        "constraint-source:land-water", "maps/land.png", Sha256.file(land), width, length,
                        GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER, mapping,
                        new GenerationRequestV2.CategoricalEncoding(
                                1,
                                GenerationRequestV2.SampleType.U8, GenerationRequestV2.RasterChannel.GRAY,
                                List.of(new GenerationRequestV2.LabelMapping(0, "water"),
                                        new GenerationRequestV2.LabelMapping(1, "land")),
                                new GenerationRequestV2.NoDataForbidden())),
                new GenerationRequestV2.ConstraintMapSource(
                        "constraint-source:height", "maps/height.png", Sha256.file(height), width, length,
                        GenerationRequestV2.DecoderKind.HEIGHT_RASTER, mapping,
                        new GenerationRequestV2.HeightEncoding(
                                1,
                                GenerationRequestV2.SampleType.U16, GenerationRequestV2.RasterChannel.GRAY,
                                GenerationRequestV2.HeightValueMeaning.BLOCKS_ABOVE_REQUEST_MIN_Y,
                                1_000, 0, new GenerationRequestV2.IntRange(0, 50_000),
                                new GenerationRequestV2.NoDataSentinel(65_535))),
                new GenerationRequestV2.ConstraintMapSource(
                        "constraint-source:zones", "maps/zones.png", Sha256.file(zones), width, length,
                        GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER, mapping,
                        new GenerationRequestV2.CategoricalEncoding(
                                1,
                                GenerationRequestV2.SampleType.U16, GenerationRequestV2.RasterChannel.GRAY,
                                List.of(new GenerationRequestV2.LabelMapping(10, "shore")),
                                new GenerationRequestV2.NoDataSentinel(0))));
        return new GenerationRequestV2(
                2, "manual-constraint-island", new GenerationRequestV2.Bounds(width, length, 0, 100, 50),
                "AI-free constraint fixture", List.of(), sources,
                new GenerationRequestV2.GenerationSettings(827_413L, 32), budget,
                java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty());
    }

    private static List<TerrainIntentV2.ConstraintMapBinding> draftBindings() {
        return List.of(
                new TerrainIntentV2.ConstraintMapBinding(
                        "height-binding", "constraint-source:height",
                        TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE,
                        "constraint:height-guide:sha256-" + "b".repeat(64),
                        TerrainIntentV2.Strength.SOFT, TerrainIntentV2.Sampling.BILINEAR_FIXED,
                        2, 800_000),
                new TerrainIntentV2.ConstraintMapBinding(
                        "land-water-binding", "constraint-source:land-water",
                        TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                        "constraint:land-water:sha256-" + "a".repeat(64),
                        TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0),
                new TerrainIntentV2.ConstraintMapBinding(
                        "zone-binding", "constraint-source:zones",
                        TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP,
                        "constraint:zone-label-map:sha256-" + "c".repeat(64),
                        TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0));
    }

    private static TerrainIntentV2 emptyIntent(
            String id,
            List<TerrainIntentV2.ConstraintMapBinding> bindings
    ) {
        return new TerrainIntentV2(
                2, id, "AI-free manual constraint fixture",
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                List.of(), List.of(), List.of(),
                new TerrainIntentV2.EnvironmentDescriptor(null, null, null),
                bindings, List.of(), TerrainIntentV2.Provenance.confirmedManual("manual-fixture"));
    }

    private static TerrainIntentV2 withBindings(
            TerrainIntentV2 source,
            List<TerrainIntentV2.ConstraintMapBinding> bindings
    ) {
        return new TerrainIntentV2(
                source.intentVersion(), source.intentId(), source.theme(), source.coordinateSystem(),
                source.features(), source.relations(), source.constraints(), source.environment(),
                bindings, source.structures(), source.provenance());
    }

    private static GenerationRequestV2 copyWithBudget(
            GenerationRequestV2 source,
            GenerationRequestV2.ConstraintMapBudget budget
    ) {
        return new GenerationRequestV2(
                source.requestVersion(), source.requestId(), source.bounds(), source.prompt(),
                source.referenceImages(), source.constraintMaps(), source.generation(), budget,
                source.foundationBaseLevels(), source.foundationDetail(),
                java.util.Optional.empty());
    }

    private static GenerationRequestV2 replaceChecksum(
            GenerationRequestV2 request,
            String sourceId,
            String checksum
    ) {
        List<GenerationRequestV2.ConstraintMapSource> sources = request.constraintMaps().stream()
                .map(source -> source.sourceId().equals(sourceId)
                        ? new GenerationRequestV2.ConstraintMapSource(
                                source.sourceId(), source.file(), checksum,
                                source.expectedWidth(), source.expectedLength(), source.decoderKind(),
                                source.coordinateMapping(), source.encoding())
                        : source)
                .toList();
        return new GenerationRequestV2(
                request.requestVersion(), request.requestId(), request.bounds(), request.prompt(),
                request.referenceImages(), sources, request.generation(), request.constraintMapBudget(),
                request.foundationBaseLevels(), request.foundationDetail(),
                java.util.Optional.empty());
    }

    private static GenerationRequestV2.CoordinateMapping mapping(int width, int length) {
        return new GenerationRequestV2.CoordinateMapping(
                GenerationRequestV2.CoordinateOrigin.NORTH_WEST,
                GenerationRequestV2.XAxis.EAST,
                GenerationRequestV2.ZAxis.SOUTH,
                GenerationRequestV2.PixelReference.PIXEL_CENTER,
                GenerationRequestV2.AspectMismatchPolicy.REJECT,
                GenerationRequestV2.QuarterTurn.DEGREES_0,
                false, false,
                new GenerationRequestV2.PixelCrop(0, 0, width, length));
    }

    private static FieldArtifactDescriptorV2 field(
            ManualConstraintMapResultV2 result,
            FieldArtifactDescriptorV2.FieldSemantic semantic
    ) {
        return result.index().fields().stream()
                .filter(field -> field.definition().semantic() == semantic)
                .findFirst().orElseThrow();
    }

    private static Path writeGray(Path path, boolean u16, int[][] values) throws Exception {
        Files.createDirectories(path.getParent());
        int length = values.length;
        int width = values[0].length;
        BufferedImage image = new BufferedImage(
                width, length, u16 ? BufferedImage.TYPE_USHORT_GRAY : BufferedImage.TYPE_BYTE_GRAY);
        try {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    image.getRaster().setSample(x, z, 0, values[z][x]);
                }
            }
            assertTrue(ImageIO.write(image, "png", path.toFile()));
        } finally {
            image.flush();
        }
        return path;
    }

    private static Path writeConstantGray(
            Path path,
            boolean u16,
            int width,
            int length,
            int value
    ) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(
                width, length, u16 ? BufferedImage.TYPE_USHORT_GRAY : BufferedImage.TYPE_BYTE_GRAY);
        try {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    image.getRaster().setSample(x, z, 0, value);
                }
            }
            assertTrue(ImageIO.write(image, "png", path.toFile()));
        } finally {
            image.flush();
        }
        return path;
    }

    private record Fixture(
            Path requestPath,
            GenerationRequestV2 request,
            TerrainIntentV2 intent
    ) { }
}
