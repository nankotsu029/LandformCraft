package com.github.nankotsu029.landformcraft.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Single atomically replaced state document for region and disk reservations. */
public record PlacementSafetyState(
        int schemaVersion,
        List<PlacementReservation> placementReservations,
        List<DiskReservation> diskReservations
) {
    public PlacementSafetyState {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        placementReservations = ModelValidation.immutableList(
                placementReservations, "placementReservations", 4_096);
        diskReservations = ModelValidation.immutableList(diskReservations, "diskReservations", 4_096);
        Set<UUID> placements = new HashSet<>();
        for (PlacementReservation reservation : placementReservations) {
            if (!placements.add(reservation.placementId())) {
                throw new IllegalArgumentException("duplicate placement reservation");
            }
        }
        Set<UUID> disks = new HashSet<>();
        for (DiskReservation reservation : diskReservations) {
            if (!disks.add(reservation.placementId())) {
                throw new IllegalArgumentException("duplicate disk reservation");
            }
        }
    }

    public static PlacementSafetyState empty() {
        return new PlacementSafetyState(1, List.of(), List.of());
    }
}
