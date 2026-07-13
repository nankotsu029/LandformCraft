package com.github.nankotsu029.landformcraft.model;

public record OutputOptions(int tileSize, boolean createSchematics, boolean createZip) {
    public OutputOptions {
        if (tileSize < 32 || tileSize > 256 || Integer.bitCount(tileSize) != 1) {
            throw new IllegalArgumentException("tileSize must be a power of two between 32 and 256");
        }
    }
}
