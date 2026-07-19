package com.github.nankotsu029.landformcraft.model.v2.placement;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable Release 2 placement journal contract. Embeds a sealed {@link PlacementPlanV2} and
 * tracks format-2 journal/tile states. V2-6-06 advances only the canonical APPLIED tile prefix
 * while the journal remains {@code APPLYING}. V2-6-07 advances {@code SETTLING}/{@code VERIFYING}
 * and terminal {@code APPLIED} with every tile {@code VERIFIED}. V2-6-08 advances
 * {@code ROLLING_BACK} with a canonical RESTORED tile suffix (reverse-order restore) and terminal
 * {@code ROLLED_BACK} with every tile {@code RESTORED}. V2-6-09 advances {@code UNDOING} with a
 * canonical RESTORED tile suffix from {@code VERIFIED} tiles and terminal {@code UNDONE} with
 * every tile {@code RESTORED}. Recovery remains a separate Task.
 * Distinct from v1 {@code PlacementJournal}.
 */
public record PlacementJournalV2(
        int journalVersion,
        String journalContractVersion,
        PlacementPlanV2 plan,
        String planChecksum,
        PlacementJournalStateV2 state,
        List<PlacementTileEntryV2> tiles,
        long reservedBytes,
        long snapshotBytesUsed,
        String updatedAt,
        String message,
        String journalChecksum
) {
    public static final int VERSION = 1;
    public static final String JOURNAL_CONTRACT_VERSION = "release-2-placement-journal-v1";
    public static final long MAX_CANONICAL_BYTES = PlacementPlanV2.MAX_CANONICAL_BYTES;

    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ISO_INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");
    private static final Pattern SAFE_RELATIVE_OR_EMPTY = Pattern.compile(
            "^(?:|(?!/)(?![A-Za-z]:)(?!.*\\\\)(?!.*(?:^|/)\\.\\.?(/|$))(?!.*//).+)$");

    public PlacementJournalV2 {
        if (journalVersion != VERSION) {
            throw new IllegalArgumentException("placement journalVersion must be 1");
        }
        journalContractVersion = nonBlank(journalContractVersion, "journalContractVersion", 64);
        if (!JOURNAL_CONTRACT_VERSION.equals(journalContractVersion)) {
            throw new IllegalArgumentException("unknown placement journal contract version");
        }
        Objects.requireNonNull(plan, "plan");
        planChecksum = checksum(planChecksum, "planChecksum");
        if (!planChecksum.equals(plan.canonicalChecksum())) {
            throw new IllegalArgumentException("placement journal planChecksum mismatch");
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(tiles, "tiles");
        if (tiles.isEmpty() || tiles.size() > plan.budget().maximumJournalEntries()) {
            throw new IllegalArgumentException("placement journal tile entry count out of range");
        }
        if (tiles.size() != plan.tileOrder().tiles().size()) {
            throw new IllegalArgumentException("placement journal tiles must match plan tile order");
        }
        tiles = List.copyOf(tiles);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < tiles.size(); i++) {
            PlacementTileEntryV2 entry = Objects.requireNonNull(tiles.get(i), "tiles[" + i + "]");
            PlacementPlanV2.TileRefV2 expected = plan.tileOrder().tiles().get(i);
            if (entry.tileIndex() != expected.tileIndex() || !entry.tileId().equals(expected.tileId())) {
                throw new IllegalArgumentException("placement journal tile order mismatch");
            }
            if (!ids.add(entry.tileId())) {
                throw new IllegalArgumentException("duplicate placement journal tile");
            }
            entry.validateAgainstState(state);
        }
        validateCanonicalApplyPrefix(state, tiles);
        validateCanonicalRestoreSuffix(state, tiles);
        validateCanonicalUndoRestoreSuffix(state, tiles);
        if (reservedBytes < 0 || snapshotBytesUsed < 0 || snapshotBytesUsed > reservedBytes) {
            throw new IllegalArgumentException("invalid placement journal byte accounting");
        }
        updatedAt = instant(updatedAt, "updatedAt");
        message = nonBlank(message, "message", 2_000);
        journalChecksum = checksum(journalChecksum, "journalChecksum");
        validateStateInvariants(state, plan);
        if (state == PlacementJournalStateV2.SNAPSHOT_COMPLETE && snapshotBytesUsed < 1) {
            throw new IllegalArgumentException(
                    "SNAPSHOT_COMPLETE requires positive snapshotBytesUsed");
        }
    }

    public PlacementJournalV2 withJournalChecksum(String checksum) {
        return new PlacementJournalV2(
                journalVersion,
                journalContractVersion,
                plan,
                planChecksum,
                state,
                tiles,
                reservedBytes,
                snapshotBytesUsed,
                updatedAt,
                message,
                checksum);
    }

    public record PlacementTileEntryV2(
            String tileId,
            int tileIndex,
            PlacementTileStateV2 state,
            String snapshotFile,
            String snapshotChecksum
    ) {
        public PlacementTileEntryV2 {
            tileId = nonBlank(tileId, "tileId", 64);
            Objects.requireNonNull(state, "state");
            snapshotFile = Objects.requireNonNull(snapshotFile, "snapshotFile");
            snapshotChecksum = Objects.requireNonNull(snapshotChecksum, "snapshotChecksum");
            if (!SAFE_RELATIVE_OR_EMPTY.matcher(snapshotFile).matches()) {
                throw new IllegalArgumentException("snapshotFile must be empty or a portable relative path");
            }
            if (snapshotChecksum.isEmpty()) {
                // allowed only for PENDING
            } else if (!CHECKSUM.matcher(snapshotChecksum).matches()) {
                throw new IllegalArgumentException("snapshotChecksum must be empty or sha-256");
            }
            boolean pending = state == PlacementTileStateV2.PENDING;
            if (pending != snapshotFile.isEmpty() || pending != snapshotChecksum.isEmpty()) {
                throw new IllegalArgumentException("invalid placement tile snapshot state");
            }
            if (tileIndex < 0 || tileIndex >= PlacementPlanV2.MAXIMUM_TILES) {
                throw new IllegalArgumentException("tileIndex out of range");
            }
        }

        void validateAgainstState(PlacementJournalStateV2 journalState) {
            if (journalState == PlacementJournalStateV2.PLANNED
                    && state != PlacementTileStateV2.PENDING) {
                throw new IllegalArgumentException("PLANNED journal requires PENDING tiles");
            }
            if (journalState == PlacementJournalStateV2.SNAPSHOTTING
                    && state != PlacementTileStateV2.PENDING) {
                throw new IllegalArgumentException(
                        "SNAPSHOTTING journal requires all tiles PENDING; snapshot-all publishes"
                                + " atomically before any tile becomes SNAPSHOTTED");
            }
            if (journalState == PlacementJournalStateV2.SNAPSHOT_COMPLETE
                    && state != PlacementTileStateV2.SNAPSHOTTED) {
                throw new IllegalArgumentException(
                        "SNAPSHOT_COMPLETE journal requires all tiles SNAPSHOTTED");
            }
            if ((journalState == PlacementJournalStateV2.SETTLING
                    || journalState == PlacementJournalStateV2.VERIFYING)
                    && state != PlacementTileStateV2.APPLIED) {
                throw new IllegalArgumentException(
                        "SETTLING/VERIFYING journals require all tiles APPLIED until full verify");
            }
            if (journalState == PlacementJournalStateV2.APPLIED
                    && state != PlacementTileStateV2.VERIFIED) {
                throw new IllegalArgumentException(
                        "terminal APPLIED journal requires all tiles VERIFIED");
            }
            if (journalState == PlacementJournalStateV2.ROLLED_BACK
                    && state != PlacementTileStateV2.RESTORED) {
                throw new IllegalArgumentException(
                        "terminal ROLLED_BACK journal requires all tiles RESTORED");
            }
            if (journalState == PlacementJournalStateV2.UNDONE
                    && state != PlacementTileStateV2.RESTORED) {
                throw new IllegalArgumentException(
                        "terminal UNDONE journal requires all tiles RESTORED");
            }
        }
    }

    private static void validateStateInvariants(PlacementJournalStateV2 state, PlacementPlanV2 plan) {
        switch (state) {
            case PLANNED -> {
                if (plan.envelopeReferences().bound()
                        || plan.reservationConfirmationBinding().reservationBound()
                        || plan.reservationConfirmationBinding().confirmationIssued()) {
                    throw new IllegalArgumentException("PLANNED journal requires unbound plan slots");
                }
            }
            case ENVELOPE_BOUND -> {
                if (!plan.envelopeReferences().bound()
                        || plan.reservationConfirmationBinding().reservationBound()
                        || plan.reservationConfirmationBinding().confirmationIssued()) {
                    throw new IllegalArgumentException(
                            "ENVELOPE_BOUND requires bound envelope and unbound reservation/confirmation");
                }
            }
            case RESERVATION_BOUND -> {
                if (!plan.envelopeReferences().bound()
                        || !plan.reservationConfirmationBinding().reservationBound()
                        || plan.reservationConfirmationBinding().confirmationIssued()) {
                    throw new IllegalArgumentException(
                            "RESERVATION_BOUND requires bound envelope/reservation and unissued confirmation");
                }
            }
            case CONFIRMATION_ISSUED -> {
                if (!plan.envelopeReferences().bound()
                        || !plan.reservationConfirmationBinding().reservationBound()
                        || !plan.reservationConfirmationBinding().confirmationIssued()) {
                    throw new IllegalArgumentException(
                            "CONFIRMATION_ISSUED requires bound envelope/reservation and issued confirmation");
                }
            }
            case SNAPSHOTTING, SNAPSHOT_COMPLETE -> {
                if (!plan.envelopeReferences().bound()
                        || !plan.reservationConfirmationBinding().reservationBound()
                        || !plan.reservationConfirmationBinding().confirmationIssued()) {
                    throw new IllegalArgumentException(
                            "snapshot states require bound envelope/reservation and issued confirmation");
                }
            }
            case APPLYING, SETTLING, VERIFYING, APPLIED,
                    ROLLING_BACK, ROLLED_BACK, UNDOING, UNDONE, RECOVERY_REQUIRED, RELEASE_VALIDATED -> {
                if (state == PlacementJournalStateV2.SETTLING
                        || state == PlacementJournalStateV2.VERIFYING
                        || state == PlacementJournalStateV2.APPLIED
                        || state == PlacementJournalStateV2.UNDOING
                        || state == PlacementJournalStateV2.UNDONE) {
                    if (!plan.envelopeReferences().bound()
                            || !plan.reservationConfirmationBinding().reservationBound()
                            || !plan.reservationConfirmationBinding().confirmationIssued()) {
                        throw new IllegalArgumentException(
                                "settle/verify/applied/undo journals require bound"
                                        + " envelope/reservation/confirmation");
                    }
                }
            }
        }
    }

    private static void validateCanonicalApplyPrefix(
            PlacementJournalStateV2 state,
            List<PlacementTileEntryV2> tiles
    ) {
        if (state != PlacementJournalStateV2.APPLYING) {
            return;
        }
        boolean reachedSnapshotSuffix = false;
        for (PlacementTileEntryV2 tile : tiles) {
            if (tile.state() == PlacementTileStateV2.SNAPSHOTTED) {
                reachedSnapshotSuffix = true;
            } else if (tile.state() == PlacementTileStateV2.APPLIED) {
                if (reachedSnapshotSuffix) {
                    throw new IllegalArgumentException(
                            "APPLYING journal requires a canonical APPLIED prefix");
                }
            } else {
                throw new IllegalArgumentException(
                        "APPLYING journal permits only APPLIED prefix and SNAPSHOTTED suffix");
            }
        }
    }

    private static void validateCanonicalRestoreSuffix(
            PlacementJournalStateV2 state,
            List<PlacementTileEntryV2> tiles
    ) {
        if (state != PlacementJournalStateV2.ROLLING_BACK) {
            return;
        }
        boolean reachedRestoredSuffix = false;
        for (PlacementTileEntryV2 tile : tiles) {
            if (tile.state() == PlacementTileStateV2.RESTORED) {
                reachedRestoredSuffix = true;
            } else if (tile.state() == PlacementTileStateV2.SNAPSHOTTED
                    || tile.state() == PlacementTileStateV2.APPLIED) {
                if (reachedRestoredSuffix) {
                    throw new IllegalArgumentException(
                            "ROLLING_BACK journal requires a canonical RESTORED suffix"
                                    + " (reverse-order restore)");
                }
            } else {
                throw new IllegalArgumentException(
                        "ROLLING_BACK journal permits only SNAPSHOTTED/APPLIED prefix"
                                + " and RESTORED suffix");
            }
        }
    }

    private static void validateCanonicalUndoRestoreSuffix(
            PlacementJournalStateV2 state,
            List<PlacementTileEntryV2> tiles
    ) {
        if (state != PlacementJournalStateV2.UNDOING) {
            return;
        }
        boolean reachedRestoredSuffix = false;
        for (PlacementTileEntryV2 tile : tiles) {
            if (tile.state() == PlacementTileStateV2.RESTORED) {
                reachedRestoredSuffix = true;
            } else if (tile.state() == PlacementTileStateV2.VERIFIED) {
                if (reachedRestoredSuffix) {
                    throw new IllegalArgumentException(
                            "UNDOING journal requires a canonical RESTORED suffix"
                                    + " (reverse-order undo restore)");
                }
            } else {
                throw new IllegalArgumentException(
                        "UNDOING journal permits only VERIFIED prefix and RESTORED suffix");
            }
        }
    }

    private static String checksum(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase sha-256 hex digest");
        }
        return value;
    }

    private static String instant(String value, String field) {
        value = nonBlank(value, field, 40);
        if (!ISO_INSTANT.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 UTC instant");
        }
        java.time.Instant.parse(value);
        return value;
    }

    private static String nonBlank(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and <= " + max);
        }
        return value;
    }
}
