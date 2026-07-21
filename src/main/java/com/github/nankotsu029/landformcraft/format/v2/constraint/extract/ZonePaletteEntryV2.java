package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import java.util.List;
import java.util.Objects;

/**
 * One frozen sketch palette entry used by {@link ImageZoneLabelExtractorV2}. Labels are proposal
 * slugs only — they never invent semantics via AI.
 */
public record ZonePaletteEntryV2(int sample, String label, int red, int green, int blue) {
    public ZonePaletteEntryV2 {
        if (sample < 1 || sample > 254) {
            throw new IllegalArgumentException("palette sample must be within 1..254");
        }
        label = Objects.requireNonNull(label, "label");
        if (label.isBlank() || label.length() > 64 || !label.equals(label.toLowerCase())
                || !label.chars().allMatch(ch -> ch == '-' || ch == '_'
                || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9'))) {
            throw new IllegalArgumentException("palette label must be a lowercase slug");
        }
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
            throw new IllegalArgumentException("palette RGB must be within 0..255");
        }
    }

    public int squaredDistance(int red, int green, int blue) {
        int dr = this.red - red;
        int dg = this.green - green;
        int db = this.blue - blue;
        return dr * dr + dg * dg + db * db;
    }

    /** Frozen sketch palette for {@code image-zone-label-extract-v1}. */
    public static List<ZonePaletteEntryV2> sketchPaletteV1() {
        return List.of(
                new ZonePaletteEntryV2(10, "shore", 210, 180, 120),
                new ZonePaletteEntryV2(20, "upland", 70, 140, 60),
                new ZonePaletteEntryV2(30, "wetland", 40, 90, 70),
                new ZonePaletteEntryV2(40, "rock", 120, 120, 125)
        );
    }
}
