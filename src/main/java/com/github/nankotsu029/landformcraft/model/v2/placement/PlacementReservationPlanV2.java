package com.github.nankotsu029.landformcraft.model.v2.placement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable Release 2 multi-region／disk reservation plan (V2-6-03). Binds a sealed placement
 * plan and envelope, freezes canonical region order, and carries a reservation checksum used
 * by confirmation binding. Does not snapshot or apply world mutations.
 */
public record PlacementReservationPlanV2(
        int planVersion,
        String reservationContractVersion,
        UUID placementId,
        UUID operationId,
        UUID worldId,
        PlacementReservationOperationV2 operation,
        PlacementPlanV2.PlacementActorV2 actor,
        PlacementPlanBinding placementPlanBinding,
        EnvelopeBinding envelopeBinding,
        List<RegionLeaseV2> regions,
        DiskLeaseV2 diskLease,
        PlacementReservationLeaseStateV2 leaseState,
        String createdAt,
        String expiresAt,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String RESERVATION_CONTRACT_VERSION = "release-2-placement-reservation-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 256L * 1024L;
    public static final int MAXIMUM_REGIONS = PlacementPlanV2.MAXIMUM_TILES;
    public static final int MAXIMUM_ENTRIES = 4_096;

    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ISO_INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");

    public PlacementReservationPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("placement reservation planVersion must be 1");
        }
        reservationContractVersion = nonBlank(reservationContractVersion, "reservationContractVersion", 64);
        if (!RESERVATION_CONTRACT_VERSION.equals(reservationContractVersion)) {
            throw new IllegalArgumentException("unknown placement reservation contract version");
        }
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(placementPlanBinding, "placementPlanBinding");
        Objects.requireNonNull(envelopeBinding, "envelopeBinding");
        Objects.requireNonNull(regions, "regions");
        if (regions.isEmpty() || regions.size() > MAXIMUM_REGIONS) {
            throw new IllegalArgumentException("reservation region count out of range");
        }
        regions = canonicalizeRegions(regions);
        Objects.requireNonNull(diskLease, "diskLease");
        Objects.requireNonNull(leaseState, "leaseState");
        createdAt = instant(createdAt, "createdAt");
        expiresAt = instant(expiresAt, "expiresAt");
        if (!java.time.Instant.parse(expiresAt).isAfter(java.time.Instant.parse(createdAt))) {
            throw new IllegalArgumentException("reservation expiresAt must be after createdAt");
        }
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (regions.size() > budget.maximumRegions()
                || diskLease.reservedBytes() > budget.maximumDiskBytes()) {
            throw new IllegalArgumentException("reservation exceeds budget");
        }
        if (!diskLease.placementId().equals(placementId)) {
            throw new IllegalArgumentException("disk lease placementId mismatch");
        }
    }

    public PlacementReservationPlanV2 withCanonicalChecksum(String checksum) {
        return new PlacementReservationPlanV2(
                planVersion,
                reservationContractVersion,
                placementId,
                operationId,
                worldId,
                operation,
                actor,
                placementPlanBinding,
                envelopeBinding,
                regions,
                diskLease,
                leaseState,
                createdAt,
                expiresAt,
                budget,
                checksum);
    }

    public void requirePlacementAndEnvelope(PlacementPlanV2 plan, PlacementEnvelopePlanV2 envelope) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(envelope, "envelope");
        if (!plan.envelopeReferences().bound()
                || !plan.envelopeReferences().mutationEnvelopePlanChecksum()
                .equals(envelope.mutationEnvelopeChecksum())
                || !plan.envelopeReferences().effectEnvelopePlanChecksum()
                .equals(envelope.canonicalChecksum())
                || !envelopeBinding.sourceEnvelopePlanChecksum().equals(envelope.canonicalChecksum())
                || !envelopeBinding.sourceMutationEnvelopeChecksum()
                .equals(envelope.mutationEnvelopeChecksum())
                || regions.size() != envelope.tiles().size()
                || !placementId.equals(plan.placementId())
                || !operationId.equals(plan.operationId())
                || !worldId.equals(plan.target().worldId())) {
            throw new IllegalArgumentException("reservation plan binding mismatch");
        }
        if (envelope.placementPlanBinding().tileCount() != plan.tileOrder().tiles().size()) {
            throw new IllegalArgumentException("reservation envelope tile count mismatch");
        }
        if (operation == PlacementReservationOperationV2.UNDO) {
            // Undo leases replace the durable APPLY lease without rewriting the sealed apply plan,
            // which remains bound to the APPLY reservation checksum / confirmation.
            if (!placementPlanBinding.sourcePlacementPlanChecksum().equals(plan.canonicalChecksum())) {
                throw new IllegalArgumentException("undo reservation placement-plan checksum mismatch");
            }
        } else if (plan.reservationConfirmationBinding().reservationBound()) {
            if (!plan.reservationConfirmationBinding().reservationChecksum().equals(canonicalChecksum)) {
                throw new IllegalArgumentException("reservation checksum mismatch on placement plan");
            }
        } else if (!placementPlanBinding.sourcePlacementPlanChecksum().equals(plan.canonicalChecksum())) {
            throw new IllegalArgumentException("reservation placement-plan checksum mismatch");
        }
        for (int i = 0; i < regions.size(); i++) {
            RegionLeaseV2 region = regions.get(i);
            PlacementEnvelopePlanV2.TileEnvelopeV2 tile = envelope.tiles().get(i);
            if (region.tileIndex() != tile.tileIndex()
                    || !region.tileId().equals(tile.tileId())
                    || !region.region().equals(tile.effectAabb())) {
                throw new IllegalArgumentException("reservation regions must match envelope effect AABBs");
            }
        }
        if (diskLease.reservedBytes() != envelope.diskEstimate().totalBytes()) {
            throw new IllegalArgumentException("reservation disk bytes must match envelope disk estimate");
        }
    }

    public boolean overlaps(PlacementReservationPlanV2 other) {
        Objects.requireNonNull(other, "other");
        if (!worldId.equals(other.worldId) || placementId.equals(other.placementId)) {
            return false;
        }
        for (RegionLeaseV2 left : regions) {
            for (RegionLeaseV2 right : other.regions) {
                if (left.region().intersects(right.region())) {
                    return true;
                }
            }
        }
        return false;
    }

    public record PlacementPlanBinding(
            int bindingVersion,
            String sourcePlacementPlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "release-2-placement-reservation-plan-binding-v1";

        public PlacementPlanBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown reservation placement-plan binding");
            }
            sourcePlacementPlanChecksum = checksum(sourcePlacementPlanChecksum, "sourcePlacementPlanChecksum");
        }
    }

    public record EnvelopeBinding(
            int bindingVersion,
            String sourceEnvelopePlanChecksum,
            String sourceMutationEnvelopeChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "release-2-placement-reservation-envelope-binding-v1";

        public EnvelopeBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown reservation envelope binding");
            }
            sourceEnvelopePlanChecksum = checksum(sourceEnvelopePlanChecksum, "sourceEnvelopePlanChecksum");
            sourceMutationEnvelopeChecksum = checksum(
                    sourceMutationEnvelopeChecksum, "sourceMutationEnvelopeChecksum");
        }
    }

    public record RegionLeaseV2(
            String tileId,
            int tileIndex,
            WorldAabbV2 region
    ) {
        public RegionLeaseV2 {
            tileId = nonBlank(tileId, "tileId", 64);
            if (tileIndex < 0 || tileIndex >= MAXIMUM_REGIONS) {
                throw new IllegalArgumentException("tileIndex out of range");
            }
            Objects.requireNonNull(region, "region");
        }
    }

    public record DiskLeaseV2(
            UUID placementId,
            String fileStoreKey,
            long reservedBytes
    ) {
        public DiskLeaseV2 {
            Objects.requireNonNull(placementId, "placementId");
            fileStoreKey = nonBlank(fileStoreKey, "fileStoreKey", 512);
            if (reservedBytes < 1) {
                throw new IllegalArgumentException("reservedBytes must be positive");
            }
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumRegions,
            int maximumStoreEntries,
            long maximumDiskBytes,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "release-2-placement-reservation-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown reservation budget version");
            }
            if (maximumRegions < 1 || maximumRegions > MAXIMUM_REGIONS
                    || maximumStoreEntries < 1 || maximumStoreEntries > MAXIMUM_ENTRIES
                    || maximumDiskBytes < 1
                    || estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || estimatedCanonicalBytes > maximumCanonicalBytes) {
                throw new IllegalArgumentException("invalid reservation budget");
            }
        }
    }

    private static List<RegionLeaseV2> canonicalizeRegions(List<RegionLeaseV2> regions) {
        List<RegionLeaseV2> copy = new ArrayList<>(regions.size());
        Set<String> ids = new HashSet<>();
        Set<Integer> indexes = new HashSet<>();
        for (RegionLeaseV2 region : regions) {
            Objects.requireNonNull(region, "regions");
            if (!ids.add(region.tileId()) || !indexes.add(region.tileIndex())) {
                throw new IllegalArgumentException("duplicate reservation region identity");
            }
            copy.add(region);
        }
        copy.sort(Comparator.comparingInt(RegionLeaseV2::tileIndex));
        for (int i = 0; i < copy.size(); i++) {
            if (copy.get(i).tileIndex() != i) {
                throw new IllegalArgumentException("reservation regions must be canonical tile-index order");
            }
        }
        return List.copyOf(copy);
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
