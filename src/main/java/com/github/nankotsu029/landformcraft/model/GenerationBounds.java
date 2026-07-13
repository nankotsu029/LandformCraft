package com.github.nankotsu029.landformcraft.model;

/** Horizontal and vertical limits of one generated artifact. */
public record GenerationBounds(int width, int length, int minY, int maxY, int waterLevel) {
    public static final int MAX_HORIZONTAL_SIZE = 1_000;
    public static final int MAX_VERTICAL_SPAN = 512;

    public GenerationBounds {
        if (width < 1 || width > MAX_HORIZONTAL_SIZE) {
            throw new IllegalArgumentException("width must be between 1 and " + MAX_HORIZONTAL_SIZE);
        }
        if (length < 1 || length > MAX_HORIZONTAL_SIZE) {
            throw new IllegalArgumentException("length must be between 1 and " + MAX_HORIZONTAL_SIZE);
        }
        if (minY >= maxY) {
            throw new IllegalArgumentException("minY must be lower than maxY");
        }
        long verticalSpan = (long) maxY - minY + 1L;
        if (verticalSpan > MAX_VERTICAL_SPAN) {
            throw new IllegalArgumentException("inclusive vertical span must not exceed " + MAX_VERTICAL_SPAN);
        }
        if (waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("waterLevel must be inside the vertical bounds");
        }
    }

    public long columnCount() {
        return (long) width * length;
    }

    public int verticalSpan() {
        return maxY - minY + 1;
    }
}
