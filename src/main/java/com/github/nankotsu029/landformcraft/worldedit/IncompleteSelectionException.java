package com.github.nankotsu029.landformcraft.worldedit;

/** Indicates that WorldEdit is available but the player has not completed a selection. */
public final class IncompleteSelectionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IncompleteSelectionException(Throwable cause) {
        super("WorldEdit selection is incomplete", cause);
    }
}
