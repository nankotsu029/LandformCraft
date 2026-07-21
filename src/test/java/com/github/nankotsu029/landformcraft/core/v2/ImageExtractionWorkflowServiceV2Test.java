package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapSourceSpec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.DecodedNumericRaster;
import com.github.nankotsu029.landformcraft.format.v2.constraint.LoadedConstraintMapSource;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngDecoder;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoding;
import com.github.nankotsu029.landformcraft.format.v2.constraint.SecureConstraintMapSourceLoader;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskPromotionRecordV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-14-01 wiring evidence for the surface-independent extraction backend: the untrusted image path
 * is split for the secure envelope, the sealed draft is promoted through the V2-7 service, and the
 * promoted map is byte-identical and re-decodable through the exact V2-1 loader the export path uses.
 */
class ImageExtractionWorkflowServiceV2Test {
    private final ImageExtractionWorkflowServiceV2 workflow = new ImageExtractionWorkflowServiceV2();

    @Test
    void extractsPromotesAndProducesAnExportConsumableV21Map(@TempDir Path root) throws Exception {
        Path image = writeLandWaterPng(root.resolve("coast.png"), 8, 8);
        workflow.extractLandWater(image, root.resolve("draft"), () -> false);

        ExtractedMaskPromotionRecordV2 record = workflow.promoteLandWater(
                root.resolve("draft"), root.resolve("promoted"),
                ExtractedMaskPromotionOptionsV2.rejectBelow(1), () -> false);

        Path map = root.resolve("promoted").resolve(ExtractedMaskPromotionRecordV2.MAP_PATH);
        assertTrue(Files.isRegularFile(map));

        // Re-decode through the same V2-1 strict loader/decoder the export path consumes.
        ConstraintMapSourceSpec specification = new ConstraintMapSourceSpec(
                record.sourceId(), record.mapPath(), record.mapSha256(), record.width(), record.length());
        List<LoadedConstraintMapSource> loaded = new SecureConstraintMapSourceLoader().load(
                root.resolve("promoted").resolve("request-v2.json"),
                List.of(specification), ConstraintMapDecodeLimits.defaults(), () -> false);
        DecodedNumericRaster raster = new NumericPngDecoder().decode(
                loaded.getFirst(), specification,
                new NumericPngEncoding(NumericPngEncoding.CURRENT_VERSION,
                        NumericPngEncoding.NumericKind.CATEGORICAL, NumericPngEncoding.SampleType.U8),
                ConstraintMapDecodeLimits.defaults(), () -> false);
        assertEquals(0, raster.sample(0, 0));
        assertEquals(1, raster.sample(7, 0));
        assertEquals(record.mapSha256(), raster.sourceChecksum());
    }

    @Test
    void promotedMapChecksumIsStableAcrossLocaleTimezoneAndThreads(@TempDir Path root) throws Exception {
        Path image = writeLandWaterPng(root.resolve("coast.png"), 8, 8);
        workflow.extractLandWater(image, root.resolve("draft"), () -> false);
        ExtractedMaskPromotionOptionsV2 options = ExtractedMaskPromotionOptionsV2.mapUnknownToWater(1);

        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            String expected = workflow.promoteLandWater(
                    root.resolve("draft"), root.resolve("a"), options, () -> false).mapSha256();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(expected, workflow.promoteLandWater(
                    root.resolve("draft"), root.resolve("b"), options, () -> false).mapSha256());
            try (var pool = Executors.newFixedThreadPool(4)) {
                assertEquals(expected, pool.submit(() -> workflow.promoteLandWater(
                        root.resolve("draft"), root.resolve("c"), options, () -> false)).get().mapSha256());
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    private static Path writeLandWaterPng(Path path, int width, int length) throws IOException {
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int argb = x < width / 2
                        ? (255 << 24) | (10 << 16) | (40 << 8) | 220
                        : (255 << 24) | (70 << 16) | (140 << 8) | 70;
                image.setRGB(x, z, argb);
            }
        }
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("no PNG writer available for the extraction fixture");
        }
        return path;
    }
}
