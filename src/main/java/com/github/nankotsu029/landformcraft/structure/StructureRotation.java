package com.github.nankotsu029.landformcraft.structure;

import com.github.nankotsu029.landformcraft.model.QuarterTurn;

/** Version-neutral rotation used by the retained custom-asset catalog and readers. */
public final class StructureRotation {
    private StructureRotation() {
    }

    public static Rotated rotate(StructureAsset asset, QuarterTurn rotation, int x, int z) {
        return switch (rotation) {
            case NONE -> new Rotated(x, z);
            case CLOCKWISE_90 -> new Rotated(asset.length() - 1 - z, x);
            case CLOCKWISE_180 -> new Rotated(asset.width() - 1 - x, asset.length() - 1 - z);
            case CLOCKWISE_270 -> new Rotated(z, asset.width() - 1 - x);
        };
    }

    public record Rotated(int x, int z) {
    }
}
