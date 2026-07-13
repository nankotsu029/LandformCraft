package com.github.nankotsu029.landformcraft.model;

import java.util.regex.Pattern;

public record PlacementTileCheckpoint(
        String tileId,
        PlacementTileState state,
        String snapshotFile,
        String snapshotChecksum
) {
    private static final Pattern TILE_ID = Pattern.compile("tile-[0-9]{2,}-[0-9]{2,}");
    private static final Pattern SHA_256_OR_EMPTY = Pattern.compile("(?:|[0-9a-f]{64})");

    public PlacementTileCheckpoint {
        tileId = ModelValidation.requireNonBlank(tileId, "tileId");
        java.util.Objects.requireNonNull(state, "state");
        snapshotFile = java.util.Objects.requireNonNull(snapshotFile, "snapshotFile");
        snapshotFile = snapshotFile.isEmpty()
                ? snapshotFile : ModelValidation.requireSafeRelativePath(snapshotFile, "snapshotFile");
        snapshotChecksum = java.util.Objects.requireNonNull(snapshotChecksum, "snapshotChecksum");
        if (!TILE_ID.matcher(tileId).matches()
                || !SHA_256_OR_EMPTY.matcher(snapshotChecksum).matches()
                || state != PlacementTileState.PENDING && (snapshotFile.isEmpty() || snapshotChecksum.isEmpty())) {
            throw new IllegalArgumentException("invalid placement tile checkpoint");
        }
    }

    public static PlacementTileCheckpoint pending(String tileId) {
        return new PlacementTileCheckpoint(tileId, PlacementTileState.PENDING, "", "");
    }
}
