package com.github.nankotsu029.landformcraft.model.v2.placement;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable Release 2 snapshot-all index artifact (V2-6-04). Freezes the canonical effect-envelope
 * snapshot order, per-tile snapshot file／artifact checksum／semantic block-state stream checksum,
 * and binds the confirmed placement plan, envelope plan, reservation, and issued confirmation.
 * Sealed only after every snapshot file passed strict read-back; does not apply, settle, or roll
 * back world mutations.
 */
public record PlacementSnapshotPlanV2(
        int planVersion,
        String snapshotContractVersion,
        UUID placementId,
        UUID operationId,
        UUID worldId,
        PlacementPlanBinding placementPlanBinding,
        EnvelopeBinding envelopeBinding,
        ReservationBinding reservationBinding,
        String snapshotFileFormatVersion,
        List<TileSnapshotV2> tiles,
        long totalBlocks,
        long totalFileBytes,
        ResourceBudget budget,
        String createdAt,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String SNAPSHOT_CONTRACT_VERSION = "release-2-placement-snapshot-v1";
    public static final String SNAPSHOT_FILE_FORMAT_VERSION = "release-2-placement-snapshot-file-v1";
    public static final String SNAPSHOT_FILE_SUFFIX = ".lfcsnap";
    public static final String INDEX_FILE_NAME = "snapshot-plan.json";
    public static final long MAX_CANONICAL_BYTES = 256L * 1024L;
    public static final int MAXIMUM_TILES = PlacementPlanV2.MAXIMUM_TILES;
    public static final int MAXIMUM_PALETTE_ENTRIES_PER_TILE = 4_096;

    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern TILE_ID = Pattern.compile("tile-x[0-9]+-z[0-9]+");
    private static final Pattern ISO_INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");

    public PlacementSnapshotPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("placement snapshot planVersion must be 1");
        }
        snapshotContractVersion = nonBlank(snapshotContractVersion, "snapshotContractVersion", 64);
        if (!SNAPSHOT_CONTRACT_VERSION.equals(snapshotContractVersion)) {
            throw new IllegalArgumentException("unknown placement snapshot contract version");
        }
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(placementPlanBinding, "placementPlanBinding");
        Objects.requireNonNull(envelopeBinding, "envelopeBinding");
        Objects.requireNonNull(reservationBinding, "reservationBinding");
        snapshotFileFormatVersion = nonBlank(snapshotFileFormatVersion, "snapshotFileFormatVersion", 64);
        if (!SNAPSHOT_FILE_FORMAT_VERSION.equals(snapshotFileFormatVersion)) {
            throw new IllegalArgumentException("unknown placement snapshot file format version");
        }
        Objects.requireNonNull(tiles, "tiles");
        if (tiles.isEmpty() || tiles.size() > MAXIMUM_TILES) {
            throw new IllegalArgumentException("placement snapshot tile count out of range");
        }
        tiles = List.copyOf(tiles);
        Objects.requireNonNull(budget, "budget");
        createdAt = instant(createdAt, "createdAt");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateTiles(tiles, totalBlocks, totalFileBytes, budget);
    }

    public PlacementSnapshotPlanV2 withCanonicalChecksum(String checksum) {
        return new PlacementSnapshotPlanV2(
                planVersion,
                snapshotContractVersion,
                placementId,
                operationId,
                worldId,
                placementPlanBinding,
                envelopeBinding,
                reservationBinding,
                snapshotFileFormatVersion,
                tiles,
                totalBlocks,
                totalFileBytes,
                budget,
                createdAt,
                checksum);
    }

    /**
     * Fails closed unless this snapshot index is bound to exactly the given confirmed placement
     * plan, envelope plan, and reservation, and the snapshot tiles mirror the envelope's canonical
     * effect AABB order.
     */
    public void requireBindings(
            PlacementPlanV2 confirmedPlan,
            PlacementEnvelopePlanV2 envelopePlan,
            PlacementReservationPlanV2 reservationPlan
    ) {
        Objects.requireNonNull(confirmedPlan, "confirmedPlan");
        Objects.requireNonNull(envelopePlan, "envelopePlan");
        Objects.requireNonNull(reservationPlan, "reservationPlan");
        if (!confirmedPlan.reservationConfirmationBinding().confirmationIssued()) {
            throw new IllegalArgumentException("snapshot requires an issued confirmation");
        }
        if (!placementId.equals(confirmedPlan.placementId())
                || !operationId.equals(confirmedPlan.operationId())
                || !worldId.equals(confirmedPlan.target().worldId())) {
            throw new IllegalArgumentException("snapshot placement identity mismatch");
        }
        if (!placementPlanBinding.sourcePlacementPlanChecksum().equals(confirmedPlan.canonicalChecksum())) {
            throw new IllegalArgumentException("snapshot placement-plan checksum mismatch");
        }
        if (!envelopeBinding.sourceEnvelopePlanChecksum().equals(envelopePlan.canonicalChecksum())
                || !envelopeBinding.sourceMutationEnvelopeChecksum()
                .equals(envelopePlan.mutationEnvelopeChecksum())) {
            throw new IllegalArgumentException("snapshot envelope checksum mismatch");
        }
        if (!reservationBinding.reservationChecksum().equals(reservationPlan.canonicalChecksum())
                || !reservationBinding.confirmationHash()
                .equals(confirmedPlan.reservationConfirmationBinding().confirmationHash())) {
            throw new IllegalArgumentException("snapshot reservation/confirmation binding mismatch");
        }
        if (tiles.size() != envelopePlan.tiles().size()) {
            throw new IllegalArgumentException("snapshot tile count must match envelope tiles");
        }
        for (int i = 0; i < tiles.size(); i++) {
            TileSnapshotV2 tile = tiles.get(i);
            PlacementEnvelopePlanV2.TileEnvelopeV2 envelopeTile = envelopePlan.tiles().get(i);
            if (tile.tileIndex() != envelopeTile.tileIndex()
                    || !tile.tileId().equals(envelopeTile.tileId())
                    || !tile.effectAabb().equals(envelopeTile.effectAabb())) {
                throw new IllegalArgumentException("snapshot tiles must match envelope effect AABBs");
            }
        }
    }

    public record PlacementPlanBinding(
            int bindingVersion,
            String sourcePlacementPlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "release-2-placement-snapshot-plan-binding-v1";

        public PlacementPlanBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown snapshot placement-plan binding");
            }
            sourcePlacementPlanChecksum = checksum(sourcePlacementPlanChecksum, "sourcePlacementPlanChecksum");
            if (PlacementPlanV2.UNBOUND_CHECKSUM.equals(sourcePlacementPlanChecksum)) {
                throw new IllegalArgumentException("snapshot requires a sealed placement plan");
            }
        }
    }

    public record EnvelopeBinding(
            int bindingVersion,
            String sourceEnvelopePlanChecksum,
            String sourceMutationEnvelopeChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "release-2-placement-snapshot-envelope-binding-v1";

        public EnvelopeBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown snapshot envelope binding");
            }
            sourceEnvelopePlanChecksum = checksum(sourceEnvelopePlanChecksum, "sourceEnvelopePlanChecksum");
            sourceMutationEnvelopeChecksum = checksum(
                    sourceMutationEnvelopeChecksum, "sourceMutationEnvelopeChecksum");
        }
    }

    public record ReservationBinding(
            int bindingVersion,
            String reservationChecksum,
            String confirmationHash,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "release-2-placement-snapshot-reservation-binding-v1";

        public ReservationBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown snapshot reservation binding");
            }
            reservationChecksum = checksum(reservationChecksum, "reservationChecksum");
            confirmationHash = checksum(confirmationHash, "confirmationHash");
            if (PlacementPlanV2.UNBOUND_CHECKSUM.equals(reservationChecksum)) {
                throw new IllegalArgumentException("snapshot requires a bound reservation");
            }
        }
    }

    public record TileSnapshotV2(
            String tileId,
            int tileIndex,
            WorldAabbV2 effectAabb,
            String snapshotFile,
            long fileBytes,
            long blockCount,
            String artifactChecksum,
            String blockStateStreamChecksum
    ) {
        public TileSnapshotV2 {
            tileId = nonBlank(tileId, "tileId", 64);
            if (!TILE_ID.matcher(tileId).matches()) {
                throw new IllegalArgumentException("snapshot tileId must match tile-xN-zN");
            }
            if (tileIndex < 0 || tileIndex >= MAXIMUM_TILES) {
                throw new IllegalArgumentException("snapshot tileIndex out of range");
            }
            Objects.requireNonNull(effectAabb, "effectAabb");
            snapshotFile = nonBlank(snapshotFile, "snapshotFile", 128);
            if (!snapshotFile.equals(tileId + SNAPSHOT_FILE_SUFFIX)) {
                throw new IllegalArgumentException(
                        "snapshotFile must be the canonical <tileId>" + SNAPSHOT_FILE_SUFFIX + " name");
            }
            if (fileBytes < 1) {
                throw new IllegalArgumentException("snapshot fileBytes must be positive");
            }
            if (blockCount != effectAabb.volumeBlocks()) {
                throw new IllegalArgumentException("snapshot blockCount must equal effect AABB volume");
            }
            artifactChecksum = checksum(artifactChecksum, "artifactChecksum");
            blockStateStreamChecksum = checksum(blockStateStreamChecksum, "blockStateStreamChecksum");
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumTiles,
            long maximumSnapshotBytes,
            int maximumPaletteEntriesPerTile,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "release-2-placement-snapshot-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown placement snapshot budget version");
            }
            if (maximumTiles < 1 || maximumTiles > MAXIMUM_TILES
                    || maximumSnapshotBytes < 1
                    || maximumPaletteEntriesPerTile < 1
                    || maximumPaletteEntriesPerTile > MAXIMUM_PALETTE_ENTRIES_PER_TILE
                    || estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || estimatedCanonicalBytes > maximumCanonicalBytes) {
                throw new IllegalArgumentException("invalid placement snapshot budget");
            }
        }
    }

    private static void validateTiles(
            List<TileSnapshotV2> tiles,
            long totalBlocks,
            long totalFileBytes,
            ResourceBudget budget
    ) {
        Set<String> ids = new HashSet<>();
        long computedBlocks = 0L;
        long computedBytes = 0L;
        for (int i = 0; i < tiles.size(); i++) {
            TileSnapshotV2 tile = Objects.requireNonNull(tiles.get(i), "tiles[" + i + "]");
            if (tile.tileIndex() != i) {
                throw new IllegalArgumentException("snapshot tiles must be canonical index order");
            }
            if (!ids.add(tile.tileId())) {
                throw new IllegalArgumentException("duplicate snapshot tile id");
            }
            computedBlocks = Math.addExact(computedBlocks, tile.blockCount());
            computedBytes = Math.addExact(computedBytes, tile.fileBytes());
        }
        if (computedBlocks != totalBlocks || computedBytes != totalFileBytes) {
            throw new IllegalArgumentException("snapshot totals must equal per-tile sums");
        }
        if (tiles.size() > budget.maximumTiles() || totalFileBytes > budget.maximumSnapshotBytes()) {
            throw new IllegalArgumentException("snapshot exceeds resource budget");
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
