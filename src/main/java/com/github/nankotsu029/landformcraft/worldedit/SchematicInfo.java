package com.github.nankotsu029.landformcraft.worldedit;

import java.util.Map;

public record SchematicInfo(
        int version,
        int dataVersion,
        int width,
        int height,
        int length,
        int offsetX,
        int offsetY,
        int offsetZ,
        Map<String, Integer> palette,
        int blockEntryCount
) {
    public SchematicInfo {
        palette = Map.copyOf(palette);
    }
}
