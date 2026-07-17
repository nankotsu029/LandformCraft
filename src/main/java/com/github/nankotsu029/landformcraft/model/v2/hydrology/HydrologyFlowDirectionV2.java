package com.github.nankotsu029.landformcraft.model.v2.hydrology;

/** Stable D8 codes for V2-3-02; each non-terminal code points from a cell to its downstream cell. */
public enum HydrologyFlowDirectionV2 {
    TERMINAL(0, 0, 0),
    NORTH(1, 0, -1),
    NORTH_EAST(2, 1, -1),
    EAST(3, 1, 0),
    SOUTH_EAST(4, 1, 1),
    SOUTH(5, 0, 1),
    SOUTH_WEST(6, -1, 1),
    WEST(7, -1, 0),
    NORTH_WEST(8, -1, -1),
    NO_DATA(255, 0, 0);

    private final int code;
    private final int deltaX;
    private final int deltaZ;

    HydrologyFlowDirectionV2(int code, int deltaX, int deltaZ) {
        this.code = code;
        this.deltaX = deltaX;
        this.deltaZ = deltaZ;
    }

    public int code() {
        return code;
    }

    public int deltaX() {
        return deltaX;
    }

    public int deltaZ() {
        return deltaZ;
    }

    public HydrologyFlowDirectionV2 opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case NORTH_EAST -> SOUTH_WEST;
            case EAST -> WEST;
            case SOUTH_EAST -> NORTH_WEST;
            case SOUTH -> NORTH;
            case SOUTH_WEST -> NORTH_EAST;
            case WEST -> EAST;
            case NORTH_WEST -> SOUTH_EAST;
            case TERMINAL -> TERMINAL;
            case NO_DATA -> NO_DATA;
        };
    }

    public static HydrologyFlowDirectionV2 fromCode(int code) {
        for (HydrologyFlowDirectionV2 value : values()) {
            if (value.code == code) return value;
        }
        throw new IllegalArgumentException("unknown hydrology flow-direction code: " + code);
    }
}
