package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Content of the committed {@code harbor-cove-64-honored-guided} elevation guide (V2-19-06).
 *
 * <p>The guide is derived from the land-water mask of the same fixture rather than invented, so the
 * two explicit foundation inputs agree by construction: land cells terrace upward toward the northern
 * mainland and water cells deepen toward the open sea in the south. A small south-west patch is the
 * declared no-data sentinel, which is what makes the fixture exercise the documented fallback — those
 * cells take the request's per-medium base level, not a guessed height.</p>
 *
 * <p>Everything is integer arithmetic over a committed input, so the example can be regenerated
 * byte-for-byte. {@link HeightGuideExampleFixtureV2Test} asserts the committed PNG decodes back to
 * exactly these samples.</p>
 */
public final class HeightGuideExampleFixtureV2 {
    public static final int WIDTH = 64;
    public static final int LENGTH = 64;
    public static final Path MASK_FILE =
            Path.of("examples/v2/diagnostic/maps/harbor-cove-64-honored-land-water-u8.png");
    public static final Path GUIDE_FILE =
            Path.of("examples/v2/diagnostic/maps/harbor-cove-64-honored-height-guide-u8.png");

    /** Declared no-data sentinel of the example guide; outside the valid sample range on purpose. */
    public static final int NO_DATA_SAMPLE = 255;
    public static final int MINIMUM_VALID_SAMPLE = 40;
    public static final int MAXIMUM_VALID_SAMPLE = 60;
    /** Highest land terrace (north) and the deepest sea step (south), in absolute block Y. */
    public static final int LAND_CREST_SAMPLE = 57;
    public static final int WATER_SHALLOW_SAMPLE = 46;

    private HeightGuideExampleFixtureV2() {
    }

    /** True where the example declares no-data: the four south-west corner columns of the last rows. */
    public static boolean isNoDataCell(int x, int z) {
        return x < 4 && z >= LENGTH - 4;
    }

    /** Row-major U8 samples of the example guide, derived from the committed land-water mask. */
    public static byte[] samples() throws IOException {
        int[] mask = maskSamples();
        byte[] guide = new byte[WIDTH * LENGTH];
        for (int z = 0; z < LENGTH; z++) {
            for (int x = 0; x < WIDTH; x++) {
                int index = z * WIDTH + x;
                guide[index] = (byte) sampleAt(x, z, mask[index]);
            }
        }
        return guide;
    }

    /** Absolute block Y the guide declares at one cell, or the no-data sentinel. */
    public static int sampleAt(int x, int z, int maskSample) {
        if (isNoDataCell(x, z)) {
            return NO_DATA_SAMPLE;
        }
        // Four 16-row terraces: land rises toward the northern mainland, water deepens toward the
        // southern open sea. Both stay inside the request's 32..72 vertical extent.
        int terrace = Math.min(3, z / 16);
        return maskSample == 1 ? LAND_CREST_SAMPLE - terrace : WATER_SHALLOW_SAMPLE - terrace;
    }

    /** Row-major U8 samples of the committed land-water mask (1 = land, 0 = water). */
    public static int[] maskSamples() throws IOException {
        BufferedImage decoded = ImageIO.read(MASK_FILE.toFile());
        if (decoded == null || decoded.getWidth() != WIDTH || decoded.getHeight() != LENGTH) {
            throw new IOException("the committed land-water mask is missing or has other dimensions");
        }
        int[] samples = new int[WIDTH * LENGTH];
        for (int z = 0; z < LENGTH; z++) {
            for (int x = 0; x < WIDTH; x++) {
                samples[z * WIDTH + x] = decoded.getRaster().getSample(x, z, 0);
            }
        }
        return samples;
    }

    /** Overwrites the committed example guide from {@link #samples()}. */
    public static void writeExample(Path file) throws IOException {
        new NumericPngEncoder().writeU8(file, WIDTH, LENGTH, samples());
    }
}
