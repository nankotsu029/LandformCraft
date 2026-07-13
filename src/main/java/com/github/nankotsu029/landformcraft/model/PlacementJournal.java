package com.github.nankotsu029.landformcraft.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record PlacementJournal(
        int schemaVersion,
        PlacementPlan plan,
        PlacementState state,
        ConfirmationAction confirmationAction,
        ActorIdentity confirmationActor,
        String confirmationHash,
        Instant confirmationCreatedAt,
        Instant confirmationExpiresAt,
        List<PlacementTileCheckpoint> tiles,
        long reservedBytes,
        long snapshotBytesUsed,
        Instant updatedAt,
        String message
) {
    private static final Pattern SHA_256_OR_EMPTY = Pattern.compile("(?:|[0-9a-f]{64})");

    public PlacementJournal {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(confirmationAction, "confirmationAction");
        Objects.requireNonNull(confirmationActor, "confirmationActor");
        Objects.requireNonNull(confirmationCreatedAt, "confirmationCreatedAt");
        Objects.requireNonNull(confirmationExpiresAt, "confirmationExpiresAt");
        tiles = ModelValidation.immutableList(tiles, "tiles", 1_024);
        Objects.requireNonNull(updatedAt, "updatedAt");
        message = ModelValidation.requireNonBlank(message, "message", 2_000);
        confirmationHash = Objects.requireNonNull(confirmationHash, "confirmationHash");
        if (reservedBytes < 0 || snapshotBytesUsed < 0 || snapshotBytesUsed > reservedBytes
                || !SHA_256_OR_EMPTY.matcher(confirmationHash).matches()
                || confirmationAction == ConfirmationAction.NONE && !confirmationHash.isEmpty()
                || confirmationAction != ConfirmationAction.NONE && confirmationHash.isEmpty()
                || confirmationAction == ConfirmationAction.APPLY && state != PlacementState.PLANNED
                || confirmationAction == ConfirmationAction.UNDO && state != PlacementState.APPLIED
                || (confirmationAction == ConfirmationAction.RECOVERY_ROLLBACK
                || confirmationAction == ConfirmationAction.RECOVERY_ACCEPT)
                && state != PlacementState.RECOVERY_REQUIRED
                || state == PlacementState.PLANNED && confirmationAction != ConfirmationAction.APPLY
                || state != PlacementState.PLANNED && state != PlacementState.APPLIED
                && state != PlacementState.RECOVERY_REQUIRED
                && confirmationAction != ConfirmationAction.NONE
                || confirmationAction == ConfirmationAction.NONE
                && (!confirmationCreatedAt.equals(Instant.EPOCH) || !confirmationExpiresAt.equals(Instant.EPOCH))
                || confirmationAction != ConfirmationAction.NONE
                && (!confirmationExpiresAt.isAfter(confirmationCreatedAt))) {
            throw new IllegalArgumentException("invalid placement confirmation state");
        }
        Set<String> tileIds = new HashSet<>();
        for (PlacementTileCheckpoint tile : tiles) {
            if (!tileIds.add(tile.tileId())) {
                throw new IllegalArgumentException("duplicate placement tile checkpoint");
            }
            boolean pending = tile.state() == PlacementTileState.PENDING;
            if (pending != tile.snapshotFile().isEmpty() || pending != tile.snapshotChecksum().isEmpty()) {
                throw new IllegalArgumentException("invalid placement tile snapshot state");
            }
        }
    }
}
