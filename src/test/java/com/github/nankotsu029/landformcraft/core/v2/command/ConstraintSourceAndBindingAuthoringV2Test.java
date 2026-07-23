package com.github.nankotsu029.landformcraft.core.v2.command;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.ExtractedHeightGuidePromotionOptionsV2;
import com.github.nankotsu029.landformcraft.core.v2.ExtractedMaskPromotionOptionsV2;
import com.github.nankotsu029.landformcraft.core.v2.ExtractedZoneLabelPromotionOptionsV2;
import com.github.nankotsu029.landformcraft.core.v2.ImageExtractionWorkflowServiceV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-04 authoring of constraint sources and their intent bindings.
 *
 * <p>Before this task the command surface could declare exactly one land/water source (replacing any
 * previous declaration) and could not write an intent binding at all: the canonical {@code
 * artifactId} embeds the declared input digest, so the only way to get one was to paste a SHA-256
 * into hand-written JSON.</p>
 */
class ConstraintSourceAndBindingAuthoringV2Test {
    private static final CancellationToken NEVER = () -> false;
    private static final GenerationRequestV2.Bounds BOUNDS =
            new GenerationRequestV2.Bounds(8, 8, 32, 96, 50);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final ImageExtractionWorkflowServiceV2 images = new ImageExtractionWorkflowServiceV2();
    private final IntentConstraintBindingServiceV2 bindings = new IntentConstraintBindingServiceV2();
    private int sequence;

    @Test
    void everyRoleIsDeclarableAndDeclarationsAccumulate(@TempDir Path root) throws Exception {
        V2RequestStoreV2 store = new V2RequestStoreV2(root.resolve("requests"));
        store.create("authoring-v2");
        store.bounds("authoring-v2", 8, 8, 32, 96, 50);

        store.constraintSource("authoring-v2", "coast-mask",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                promoteLandWater(root), "maps/land-water.png");
        store.constraintSource("authoring-v2", "relief",
                TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE,
                promoteHeightGuide(root), "maps/height-guide.png");
        GenerationRequestV2 request = store.constraintSource("authoring-v2", "zones",
                TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP,
                promoteZoneLabel(root), "maps/zone-labels.png");

        assertEquals(
                List.of("constraint-source:coast-mask", "constraint-source:relief", "constraint-source:zones"),
                request.constraintMaps().stream()
                        .map(GenerationRequestV2.ConstraintMapSource::sourceId)
                        .sorted()
                        .toList(),
                "declaring a source must add to the set, not replace it");

        GenerationRequestV2.ConstraintMapSource height = source(request, "constraint-source:relief");
        assertEquals(GenerationRequestV2.DecoderKind.HEIGHT_RASTER, height.decoderKind());
        assertTrue(height.encoding() instanceof GenerationRequestV2.HeightEncoding,
                "the height encoding comes from the promotion record, not from a guess");
        GenerationRequestV2.ConstraintMapSource zones = source(request, "constraint-source:zones");
        assertTrue(zones.encoding() instanceof GenerationRequestV2.CategoricalEncoding categorical
                && !categorical.labels().isEmpty(), "the zone legend comes from the promotion record");

        // The stored request stays strictly schema-valid and re-reads identically.
        GenerationRequestV2 reread = codec.readGenerationRequest(
                root.resolve("requests").resolve("authoring-v2.request-v2.json"));
        assertEquals(request, reread);

        // Re-declaring one slug updates it in place and leaves the others untouched.
        GenerationRequestV2 updated = store.constraintSource("authoring-v2", "coast-mask",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                promoteLandWater(root), "maps/coast.png");
        assertEquals(3, updated.constraintMaps().size());
        assertEquals("maps/coast.png", source(updated, "constraint-source:coast-mask").file());
    }

    @Test
    void bindDerivesTheArtifactIdFromTheDeclaredInputDigest(@TempDir Path root) throws Exception {
        Authored authored = authorLandWater(root);
        TerrainIntentV2 bound = bindings.bind(
                authored.request(), authored.intent(), "coast-mask",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0);

        assertEquals(1, bound.mapReferences().size());
        TerrainIntentV2.ConstraintMapBinding binding = bound.mapReferences().getFirst();
        assertEquals("coast-mask", binding.id());
        assertEquals("constraint-source:coast-mask", binding.sourceId());
        assertEquals(TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK, binding.role());
        assertEquals(
                "constraint:land-water:sha256-"
                        + source(authored.request(), "constraint-source:coast-mask").expectedSha256(),
                binding.artifactId(),
                "the digest half is the declared input digest, which is what the Release verifier checks");
        assertEquals(TerrainIntentV2.Strength.HARD, binding.strength());
        assertEquals(0, binding.toleranceBlocks());

        // Binding the same source again replaces its row rather than adding a duplicate.
        TerrainIntentV2 rebound = bindings.bind(
                authored.request(), bound, "coast-mask",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                TerrainIntentV2.Strength.SOFT, TerrainIntentV2.Sampling.NEAREST, 2, 500_000);
        assertEquals(1, rebound.mapReferences().size());
        assertEquals(TerrainIntentV2.Strength.SOFT, rebound.mapReferences().getFirst().strength());
        assertEquals(500_000, rebound.mapReferences().getFirst().weightMillionths());
    }

    @Test
    void bindRefusesRolesThatContradictTheDeclarationAndUnknownSources(@TempDir Path root) throws Exception {
        Authored authored = authorLandWater(root);

        IllegalArgumentException wrongRole = assertThrows(IllegalArgumentException.class,
                () -> bindings.bind(authored.request(), authored.intent(), "coast-mask",
                        TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE,
                        TerrainIntentV2.Strength.SOFT, TerrainIntentV2.Sampling.NEAREST, 0, 1_000));
        assertTrue(wrongRole.getMessage().contains("does not match"), wrongRole.getMessage());

        // A categorical map whose legend is not the water/land pair cannot be a LAND_WATER_MASK.
        V2RequestStoreV2 store = new V2RequestStoreV2(authored.requestPath().getParent());
        GenerationRequestV2 withZones = store.constraintSource("authoring-v2", "zones",
                TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP,
                promoteZoneLabel(root), "maps/zone-labels.png");
        IllegalArgumentException zoneAsMask = assertThrows(IllegalArgumentException.class,
                () -> bindings.bind(withZones, authored.intent(), "zones",
                        TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                        TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0));
        assertTrue(zoneAsMask.getMessage().contains("water/land"), zoneAsMask.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> bindings.bind(authored.request(), authored.intent(), "not-declared",
                        TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                        TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0));

        TerrainIntentV2 otherSubject = withIntentId(authored.intent(), "some-other-request");
        assertThrows(IllegalArgumentException.class,
                () -> bindings.bind(authored.request(), otherSubject, "coast-mask",
                        TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                        TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0));
    }

    @Test
    void verifyAcceptsAConsistentBindingAndNamesUnboundSources(@TempDir Path root) throws Exception {
        Authored authored = authorLandWater(root);
        TerrainIntentV2 bound = bindings.bind(
                authored.request(), authored.intent(), "coast-mask",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0);

        IntentConstraintBindingServiceV2.Report report =
                bindings.verify(authored.requestPath(), authored.request(), bound, NEVER);
        assertTrue(report.consistent(), String.valueOf(report.bindings()));
        assertEquals(List.of(), report.unboundSourceIds());

        // A declared-but-unbound source is reported without failing the bound ones.
        V2RequestStoreV2 store = new V2RequestStoreV2(authored.requestPath().getParent());
        GenerationRequestV2 withZones = store.constraintSource("authoring-v2", "zones",
                TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP,
                promoteZoneLabel(root), "maps/zone-labels.png");
        IntentConstraintBindingServiceV2.Report partial =
                bindings.verify(authored.requestPath(), withZones, bound, NEVER);
        assertTrue(partial.consistent());
        assertEquals(List.of("constraint-source:zones"), partial.unboundSourceIds());
    }

    @Test
    void verifyDetectsSwappedBytesAndHandEditedArtifactIds(@TempDir Path root) throws Exception {
        Authored authored = authorLandWater(root);
        TerrainIntentV2 bound = bindings.bind(
                authored.request(), authored.intent(), "coast-mask",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0);

        TerrainIntentV2.ConstraintMapBinding original = bound.mapReferences().getFirst();
        TerrainIntentV2 tampered = withBinding(bound, new TerrainIntentV2.ConstraintMapBinding(
                original.id(), original.sourceId(), original.role(),
                "constraint:land-water:sha256-" + "0".repeat(64),
                original.strength(), original.sampling(),
                original.toleranceBlocks(), original.weightMillionths()));
        IntentConstraintBindingServiceV2.Report edited =
                bindings.verify(authored.requestPath(), authored.request(), tampered, NEVER);
        assertFalse(edited.consistent());
        assertTrue(edited.bindings().getFirst().problems().stream()
                .anyMatch(problem -> problem.contains("artifactId")), edited.bindings().toString());

        // Swapping the map file after authoring keeps the declaration but breaks the digest.
        writeLandWaterPng(authored.requestPath().getParent().resolve("maps/land-water.png"), 8, 8, true);
        IntentConstraintBindingServiceV2.Report swapped =
                bindings.verify(authored.requestPath(), authored.request(), bound, NEVER);
        assertFalse(swapped.consistent());
        assertFalse(swapped.bindings().getFirst().problems().isEmpty());
    }

    private record Authored(Path requestPath, GenerationRequestV2 request, TerrainIntentV2 intent) {
    }

    /** Authors a request with one declared land/water source and a matching map file beside it. */
    private Authored authorLandWater(Path root) throws IOException {
        Path requests = root.resolve("requests");
        V2RequestStoreV2 store = new V2RequestStoreV2(requests);
        store.create("authoring-v2");
        store.bounds("authoring-v2", 8, 8, 32, 96, 50);
        Path promotion = promoteLandWater(root);
        GenerationRequestV2 request = store.constraintSource("authoring-v2", "coast-mask",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK, promotion, "maps/land-water.png");
        Files.createDirectories(requests.resolve("maps"));
        Files.copy(promotion.resolve("land-water.png"), requests.resolve("maps/land-water.png"));
        TerrainIntentV2 intent = withIntentId(
                codec.readTerrainIntent(Path.of(
                        "examples/v2/diagnostic/oblique-multi-view.terrain-intent-v2.json")),
                "authoring-v2");
        return new Authored(requests.resolve("authoring-v2.request-v2.json"), request, intent);
    }

    private Path promoteLandWater(Path root) throws IOException {
        Path image = root.resolve("images/coast.png");
        writeLandWaterPng(image, 8, 8, false);
        Path draft = uniqueDirectory(root, "draft-land-water");
        Path promoted = uniqueDirectory(root, "promoted-land-water");
        images.extractLandWater(image, draft, NEVER);
        images.promoteLandWater(draft, promoted, ExtractedMaskPromotionOptionsV2.rejectBelow(1), NEVER);
        return promoted;
    }

    private Path promoteHeightGuide(Path root) throws IOException {
        Path image = root.resolve("images/relief.png");
        writeGrayPng(image, 8, 8);
        Path draft = uniqueDirectory(root, "draft-height");
        Path promoted = uniqueDirectory(root, "promoted-height");
        images.extractHeightGuide(image, draft, NEVER);
        images.promoteHeightGuide(draft, promoted,
                // The declared sample space is the full 0..254, so the scale has to map it inside
                // the request's 64-block vertical range or the request contract rejects the source.
                ExtractedHeightGuidePromotionOptionsV2.of(
                        1, GenerationRequestV2.HeightValueMeaning.BLOCKS_ABOVE_REQUEST_MIN_Y,
                        250_000L, 0L),
                BOUNDS, NEVER);
        return promoted;
    }

    private Path promoteZoneLabel(Path root) throws IOException {
        Path image = root.resolve("images/zones.png");
        writeZonePng(image, 8, 8);
        Path draft = uniqueDirectory(root, "draft-zone");
        Path promoted = uniqueDirectory(root, "promoted-zone");
        images.extractZoneLabel(image, draft, NEVER);
        images.promoteZoneLabel(draft, promoted, new ExtractedZoneLabelPromotionOptionsV2(1, 0), BOUNDS, NEVER);
        return promoted;
    }

    /** The publishers refuse to write into an existing directory, so each run gets a fresh name. */
    private Path uniqueDirectory(Path root, String prefix) {
        return root.resolve(prefix + "-" + (++sequence));
    }

    private static GenerationRequestV2.ConstraintMapSource source(
            GenerationRequestV2 request,
            String sourceId
    ) {
        return request.constraintMaps().stream()
                .filter(candidate -> candidate.sourceId().equals(sourceId))
                .findFirst()
                .orElseThrow();
    }

    private static TerrainIntentV2 withIntentId(TerrainIntentV2 intent, String intentId) {
        return new TerrainIntentV2(
                intent.intentVersion(), intentId, intent.theme(), intent.coordinateSystem(),
                intent.features(), intent.relations(), intent.constraints(), intent.environment(),
                intent.mapReferences(), intent.structures(), intent.provenance());
    }

    private static TerrainIntentV2 withBinding(
            TerrainIntentV2 intent,
            TerrainIntentV2.ConstraintMapBinding binding
    ) {
        return new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                intent.features(), intent.relations(), intent.constraints(), intent.environment(),
                List.of(binding), intent.structures(), intent.provenance());
    }

    private static void writeLandWaterPng(Path path, int width, int length, boolean mirrored)
            throws IOException {
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                boolean land = mirrored ? x >= width / 2 : x < width / 2;
                image.setRGB(x, z, land ? 0x46_8C_46 : 0x0A_28_DC);
            }
        }
        writePng(image, path);
    }

    private static void writeGrayPng(Path path, int width, int length) throws IOException {
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                // Samples are read as blocks above minY, so they must stay inside the bounds.
                int shade = 2 + x + z;
                image.setRGB(x, z, shade << 16 | shade << 8 | shade);
            }
        }
        writePng(image, path);
    }

    private static void writeZonePng(Path path, int width, int length) throws IOException {
        int[] palette = {0xD2_B4_78, 0x46_8C_3C, 0x28_5A_46, 0x78_78_7D};
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, z, palette[(x / 2 + z / 2) % palette.length]);
            }
        }
        writePng(image, path);
    }

    private static void writePng(BufferedImage image, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("no PNG writer available for the authoring fixture");
        }
    }
}
