package com.github.nankotsu029.landformcraft.core.v2.design;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Synthetic content of the four {@code oblique-multi-view} reference images (V2-19-03).
 *
 * <p>The example request declares four images with different roles, so the shipped files have to
 * exist for the design path to be exercisable at all. They are generated from integer arithmetic
 * only — no photographs, no external assets — which keeps them small, license-free and reproducible.
 * {@link ReferenceImageExampleFixtureV2Test} asserts the committed PNGs decode back to exactly these
 * rasters; comparing pixels rather than file bytes keeps the check independent of the PNG encoder
 * build in use.</p>
 */
public final class ReferenceImageExampleFixtureV2 {
    public static final int WIDTH = 64;
    public static final int HEIGHT = 64;
    public static final Path EXAMPLE_DIRECTORY = Path.of("examples/v2/diagnostic/references");
    public static final List<String> IMAGE_IDS = List.of("cove-east", "cove-north", "mood", "oblique-ridge");

    private ReferenceImageExampleFixtureV2() {
    }

    /** ARGB raster for one example image id, in row-major order. */
    public static int[] argb(String imageId) {
        int[] pixels = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                pixels[y * WIDTH + x] = switch (imageId) {
                    case "mood" -> mood(x, y);
                    case "oblique-ridge" -> obliqueRidge(x, y);
                    case "cove-north" -> cove(x, y);
                    // The east view is the same cove observed from the other side.
                    case "cove-east" -> cove(WIDTH - 1 - x, y);
                    default -> throw new IllegalArgumentException("unknown example image id: " + imageId);
                };
            }
        }
        return pixels;
    }

    /** Overwrites the committed example PNGs from {@link #argb}. */
    public static void writeExamples(Path directory) throws IOException {
        Files.createDirectories(directory);
        for (String imageId : IMAGE_IDS) {
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, WIDTH, HEIGHT, argb(imageId), 0, WIDTH);
            ImageIO.write(image, "png", directory.resolve(imageId + ".png").toFile());
        }
    }

    /** Atmosphere cue: overcast sky fading into a cold sea across the horizon. */
    private static int mood(int x, int y) {
        int horizon = HEIGHT * 5 / 8;
        if (y < horizon) {
            int shade = 150 + y * 60 / horizon;
            return rgb(shade, shade + 4, shade + 12);
        }
        int depth = (y - horizon) * 40 / (HEIGHT - horizon);
        return rgb(24 + x / 8, 60 - depth, 96 - depth);
    }

    /** Oblique terrain cue: a ridge line rising to the east with sky above and slope below. */
    private static int obliqueRidge(int x, int y) {
        int crest = HEIGHT / 2 - x / 4 + (x % 8) / 4;
        if (y < crest) {
            return rgb(168, 186, 204);
        }
        int slope = (y - crest) * 70 / HEIGHT;
        return rgb(72 + slope, 92 + slope, 58 + slope / 2);
    }

    /** Multi-view cue: a rounded bay biting into the land from the south. */
    private static int cove(int x, int y) {
        int dx = x - WIDTH / 2;
        int dy = y - HEIGHT;
        boolean water = dx * dx + dy * dy < (WIDTH * 5 / 8) * (WIDTH * 5 / 8);
        if (water) {
            return rgb(30, 78, 122);
        }
        int grain = (x * 3 + y * 5) % 17;
        return rgb(96 + grain, 128 + grain, 82 + grain);
    }

    private static int rgb(int red, int green, int blue) {
        return 0xFF000000 | clamp(red) << 16 | clamp(green) << 8 | clamp(blue);
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }
}
