package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the committed {@code harbor-cove-64-honored-guided} elevation guide real and self-describing
 * (V2-19-06): the shipped PNG, the digest and dimensions the request declares, and the encoding the
 * macro foundation reads it through all have to describe one and the same map.
 */
class HeightGuideExampleFixtureV2Test {
    private static final Path REQUEST =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored-guided.request-v2.json");
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored-guided.terrain-intent-v2.json");
    private static final String GUIDE_SOURCE_ID = "constraint-source:coast-height";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void theCommittedGuideDecodesBackToTheDocumentedSamples() throws Exception {
        assertTrue(Files.isRegularFile(HeightGuideExampleFixtureV2.GUIDE_FILE),
                "missing example height guide: " + HeightGuideExampleFixtureV2.GUIDE_FILE);
        BufferedImage decoded = ImageIO.read(HeightGuideExampleFixtureV2.GUIDE_FILE.toFile());
        assertEquals(HeightGuideExampleFixtureV2.WIDTH, decoded.getWidth());
        assertEquals(HeightGuideExampleFixtureV2.LENGTH, decoded.getHeight());

        byte[] expected = HeightGuideExampleFixtureV2.samples();
        int noDataCells = 0;
        for (int z = 0; z < HeightGuideExampleFixtureV2.LENGTH; z++) {
            for (int x = 0; x < HeightGuideExampleFixtureV2.WIDTH; x++) {
                int sample = decoded.getRaster().getSample(x, z, 0);
                assertEquals(Byte.toUnsignedInt(expected[z * HeightGuideExampleFixtureV2.WIDTH + x]),
                        sample, "guide sample at " + x + ',' + z);
                if (sample == HeightGuideExampleFixtureV2.NO_DATA_SAMPLE) {
                    noDataCells++;
                } else {
                    // Every specified sample must be inside the declared valid range, or the strict
                    // canonical registration would reject the map at bind time.
                    assertTrue(sample >= HeightGuideExampleFixtureV2.MINIMUM_VALID_SAMPLE
                                    && sample <= HeightGuideExampleFixtureV2.MAXIMUM_VALID_SAMPLE,
                            "guide sample outside the declared valid range at " + x + ',' + z);
                }
            }
        }
        assertEquals(16, noDataCells, "the example must keep its documented no-data patch");
    }

    @Test
    void theExampleRequestAndIntentDescribeTheCommittedGuide() throws Exception {
        GenerationRequestV2 request = codec.readGenerationRequest(REQUEST);
        TerrainIntentV2 intent = codec.readTerrainIntent(INTENT);

        GenerationRequestV2.ConstraintMapSource guide = request.constraintMaps().stream()
                .filter(source -> source.sourceId().equals(GUIDE_SOURCE_ID))
                .findFirst()
                .orElseThrow();
        assertEquals(GenerationRequestV2.DecoderKind.HEIGHT_RASTER, guide.decoderKind());
        assertEquals(Sha256.file(HeightGuideExampleFixtureV2.GUIDE_FILE), guide.expectedSha256());
        assertEquals(HeightGuideExampleFixtureV2.WIDTH, guide.expectedWidth());
        assertEquals(HeightGuideExampleFixtureV2.LENGTH, guide.expectedLength());
        GenerationRequestV2.HeightEncoding encoding =
                (GenerationRequestV2.HeightEncoding) guide.encoding();
        // ABSOLUTE_BLOCK_Y with a one-block scale keeps a sample readable as the block Y it declares.
        assertEquals(GenerationRequestV2.HeightValueMeaning.ABSOLUTE_BLOCK_Y, encoding.valueMeaning());
        assertEquals(1_000_000L, encoding.valueScaleMillionths());
        assertEquals(0L, encoding.valueOffsetMillionths());
        assertEquals(HeightGuideExampleFixtureV2.NO_DATA_SAMPLE,
                ((GenerationRequestV2.NoDataSentinel) encoding.noData()).sample());

        TerrainIntentV2.ConstraintMapBinding binding = intent.mapReferences().stream()
                .filter(reference -> reference.sourceId().equals(GUIDE_SOURCE_ID))
                .findFirst()
                .orElseThrow();
        assertEquals(TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE, binding.role());
        // SOFT: the coastal modifiers own the height of the cells they claim, so a HARD guide over the
        // same cells would be two declared sources for one height (V2-19-06 rejects that).
        assertEquals(TerrainIntentV2.Strength.SOFT, binding.strength());
        assertEquals(TerrainIntentV2.Sampling.NEAREST, binding.sampling());
    }

    @Test
    @Disabled("manual fixture regeneration helper (V2-19-06)")
    void rewriteExampleHeightGuide(@TempDir Path unused) throws Exception {
        HeightGuideExampleFixtureV2.writeExample(HeightGuideExampleFixtureV2.GUIDE_FILE);
        System.out.println("[V2-19-06] " + HeightGuideExampleFixtureV2.GUIDE_FILE
                + " sha256=" + Sha256.file(HeightGuideExampleFixtureV2.GUIDE_FILE));
    }
}
