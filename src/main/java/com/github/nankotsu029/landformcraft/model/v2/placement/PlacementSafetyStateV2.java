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
 * Atomic Release 2 safety-store document holding active region/disk leases.
 * Distinct from v1 {@code PlacementSafetyState}. Full provenance lives in
 * {@link PlacementReservationPlanV2}; this document is the overlap／disk ledger only.
 */
public record PlacementSafetyStateV2(
        int schemaVersion,
        String safetyContractVersion,
        List<RegionReservationEntryV2> regionReservations,
        List<DiskReservationEntryV2> diskReservations,
        List<String> consumedConfirmationHashes,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String SAFETY_CONTRACT_VERSION = "release-2-placement-safety-state-v1";
    public static final long MAX_CANONICAL_BYTES = 512L * 1024L;
    public static final int MAXIMUM_ENTRIES = PlacementReservationPlanV2.MAXIMUM_ENTRIES;

    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ISO_INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");

    public PlacementSafetyStateV2 {
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("placement safety schemaVersion must be 1");
        }
        safetyContractVersion = nonBlank(safetyContractVersion, "safetyContractVersion", 64);
        if (!SAFETY_CONTRACT_VERSION.equals(safetyContractVersion)) {
            throw new IllegalArgumentException("unknown placement safety contract version");
        }
        Objects.requireNonNull(regionReservations, "regionReservations");
        Objects.requireNonNull(diskReservations, "diskReservations");
        Objects.requireNonNull(consumedConfirmationHashes, "consumedConfirmationHashes");
        if (regionReservations.size() > MAXIMUM_ENTRIES
                || diskReservations.size() > MAXIMUM_ENTRIES
                || consumedConfirmationHashes.size() > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("placement safety store exceeds entry budget");
        }
        regionReservations = canonicalizeRegions(regionReservations);
        diskReservations = canonicalizeDisk(diskReservations);
        consumedConfirmationHashes = canonicalizeHashes(consumedConfirmationHashes);
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
    }

    public PlacementSafetyStateV2 withCanonicalChecksum(String checksum) {
        return new PlacementSafetyStateV2(
                schemaVersion,
                safetyContractVersion,
                regionReservations,
                diskReservations,
                consumedConfirmationHashes,
                checksum);
    }

    public static PlacementSafetyStateV2 empty() {
        return new PlacementSafetyStateV2(
                VERSION,
                SAFETY_CONTRACT_VERSION,
                List.of(),
                List.of(),
                List.of(),
                PlacementPlanV2.UNBOUND_CHECKSUM);
    }

    public record RegionReservationEntryV2(
            UUID placementId,
            UUID worldId,
            List<WorldAabbV2> regions,
            PlacementReservationOperationV2 operation,
            PlacementPlanV2.PlacementActorV2 actor,
            PlacementReservationLeaseStateV2 state,
            String createdAt,
            String expiresAt,
            String reservationPlanChecksum
    ) {
        public RegionReservationEntryV2 {
            Objects.requireNonNull(placementId, "placementId");
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(regions, "regions");
            if (regions.isEmpty() || regions.size() > PlacementReservationPlanV2.MAXIMUM_REGIONS) {
                throw new IllegalArgumentException("region reservation size out of range");
            }
            regions = List.copyOf(regions);
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(state, "state");
            createdAt = instant(createdAt, "createdAt");
            expiresAt = instant(expiresAt, "expiresAt");
            if (!java.time.Instant.parse(expiresAt).isAfter(java.time.Instant.parse(createdAt))) {
                throw new IllegalArgumentException("reservation expiresAt must be after createdAt");
            }
            reservationPlanChecksum = checksum(reservationPlanChecksum, "reservationPlanChecksum");
        }

        public boolean overlaps(RegionReservationEntryV2 other) {
            Objects.requireNonNull(other, "other");
            if (!worldId.equals(other.worldId) || placementId.equals(other.placementId)) {
                return false;
            }
            for (WorldAabbV2 left : regions) {
                for (WorldAabbV2 right : other.regions) {
                    if (left.intersects(right)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public record DiskReservationEntryV2(
            UUID placementId,
            String fileStoreKey,
            long reservedBytes,
            String createdAt,
            String expiresAt
    ) {
        public DiskReservationEntryV2 {
            Objects.requireNonNull(placementId, "placementId");
            fileStoreKey = nonBlank(fileStoreKey, "fileStoreKey", 512);
            if (reservedBytes < 1) {
                throw new IllegalArgumentException("reservedBytes must be positive");
            }
            createdAt = instant(createdAt, "createdAt");
            expiresAt = instant(expiresAt, "expiresAt");
            if (!java.time.Instant.parse(expiresAt).isAfter(java.time.Instant.parse(createdAt))) {
                throw new IllegalArgumentException("disk reservation expiresAt must be after createdAt");
            }
        }
    }

    private static List<RegionReservationEntryV2> canonicalizeRegions(List<RegionReservationEntryV2> entries) {
        List<RegionReservationEntryV2> copy = new ArrayList<>(entries.size());
        Set<UUID> ids = new HashSet<>();
        for (RegionReservationEntryV2 entry : entries) {
            Objects.requireNonNull(entry, "regionReservations");
            if (!ids.add(entry.placementId())) {
                throw new IllegalArgumentException("duplicate region reservation placementId");
            }
            copy.add(entry);
        }
        copy.sort(Comparator.comparing(entry -> entry.placementId().toString()));
        return List.copyOf(copy);
    }

    private static List<DiskReservationEntryV2> canonicalizeDisk(List<DiskReservationEntryV2> entries) {
        List<DiskReservationEntryV2> copy = new ArrayList<>(entries.size());
        Set<UUID> ids = new HashSet<>();
        for (DiskReservationEntryV2 entry : entries) {
            Objects.requireNonNull(entry, "diskReservations");
            if (!ids.add(entry.placementId())) {
                throw new IllegalArgumentException("duplicate disk reservation placementId");
            }
            copy.add(entry);
        }
        copy.sort(Comparator.comparing(entry -> entry.placementId().toString()));
        return List.copyOf(copy);
    }

    private static List<String> canonicalizeHashes(List<String> hashes) {
        List<String> copy = new ArrayList<>(hashes.size());
        Set<String> unique = new HashSet<>();
        for (String hash : hashes) {
            hash = checksum(hash, "consumedConfirmationHashes");
            if (!unique.add(hash)) {
                throw new IllegalArgumentException("duplicate consumed confirmation hash");
            }
            copy.add(hash);
        }
        copy.sort(Comparator.naturalOrder());
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
