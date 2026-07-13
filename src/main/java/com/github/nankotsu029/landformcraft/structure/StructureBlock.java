package com.github.nankotsu029.landformcraft.structure;

import java.util.Objects;
import java.util.regex.Pattern;

/** One non-air block in an asset-local coordinate system. */
public record StructureBlock(int x, int y, int z, String blockState) {
    private static final Pattern STATE = Pattern.compile("minecraft:[a-z0-9_]+(?:\\[[a-z0-9_=,]+])?");

    public StructureBlock {
        if (x < 0 || y < 0 || z < 0) {
            throw new IllegalArgumentException("structure block coordinates must be non-negative");
        }
        Objects.requireNonNull(blockState, "blockState");
        if (!STATE.matcher(blockState).matches()) {
            throw new IllegalArgumentException("invalid vanilla block state: " + blockState);
        }
    }
}
