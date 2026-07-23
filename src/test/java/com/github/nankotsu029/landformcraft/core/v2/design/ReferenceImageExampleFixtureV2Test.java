package com.github.nankotsu029.landformcraft.core.v2.design;

import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the four committed {@code oblique-multi-view} reference images real and self-describing
 * (V2-19-03). Before this task the example declared four files that did not exist, so the design
 * path could never be exercised from it.
 */
class ReferenceImageExampleFixtureV2Test {
    private static final Path REQUEST = Path.of("examples/v2/diagnostic/oblique-multi-view.request-v2.json");

    @Test
    void theCommittedExampleImagesDecodeBackToTheDocumentedRasters() throws Exception {
        for (String imageId : ReferenceImageExampleFixtureV2.IMAGE_IDS) {
            Path file = ReferenceImageExampleFixtureV2.EXAMPLE_DIRECTORY.resolve(imageId + ".png");
            assertTrue(Files.isRegularFile(file), "missing example reference image: " + file);
            BufferedImage decoded = ImageIO.read(file.toFile());
            assertEquals(ReferenceImageExampleFixtureV2.WIDTH, decoded.getWidth(), imageId);
            assertEquals(ReferenceImageExampleFixtureV2.HEIGHT, decoded.getHeight(), imageId);
            int[] actual = decoded.getRGB(
                    0, 0, decoded.getWidth(), decoded.getHeight(), null, 0, decoded.getWidth());
            assertArrayEquals(ReferenceImageExampleFixtureV2.argb(imageId), actual, imageId);
        }
    }

    @Test
    void theExampleRequestDeclaresEveryImageAndPinsTheDigestItDeclares() throws Exception {
        GenerationRequestV2 request = new LandformV2DataCodec().readGenerationRequest(REQUEST);
        assertEquals(
                ReferenceImageExampleFixtureV2.IMAGE_IDS,
                request.referenceImages().stream()
                        .map(GenerationRequestV2.ReferenceImageSource::id)
                        .toList());

        List<GenerationRequestV2.ReferenceImageSource> declaredDigests = request.referenceImages().stream()
                .filter(image -> image.expectedSha256().isPresent())
                .toList();
        assertEquals(1, declaredDigests.size(),
                "the example documents both branches of the optional digest, so exactly one is pinned");
        for (GenerationRequestV2.ReferenceImageSource image : declaredDigests) {
            Path file = REQUEST.getParent().resolve(image.file());
            assertEquals(image.expectedSha256().orElseThrow(), Sha256.file(file), image.id());
        }
    }

    @Test
    @Disabled("manual fixture regeneration helper (V2-19-03)")
    void rewriteExampleReferenceImages(@TempDir Path unused) throws Exception {
        ReferenceImageExampleFixtureV2.writeExamples(ReferenceImageExampleFixtureV2.EXAMPLE_DIRECTORY);
        for (String imageId : ReferenceImageExampleFixtureV2.IMAGE_IDS) {
            Path file = ReferenceImageExampleFixtureV2.EXAMPLE_DIRECTORY.resolve(imageId + ".png");
            System.out.println("[V2-19-03] " + file + " sha256=" + Sha256.file(file));
        }
    }
}
