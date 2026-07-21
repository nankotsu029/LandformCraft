package com.github.nankotsu029.landformcraft.model.v2;

/** Per-cell conflict codes written to the conflict sidecar (U8). */
public enum MultiSourceConflictCodeV2 {
    NONE(0),
    HARD_CONFLICT(1),
    SOFT_PEER_CONFLICT(2);

    private final int code;

    MultiSourceConflictCodeV2(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static MultiSourceConflictCodeV2 fromCode(int value) {
        return switch (value) {
            case 0 -> NONE;
            case 1 -> HARD_CONFLICT;
            case 2 -> SOFT_PEER_CONFLICT;
            default -> throw new IllegalArgumentException("unknown multi-source conflict code: " + value);
        };
    }
}
