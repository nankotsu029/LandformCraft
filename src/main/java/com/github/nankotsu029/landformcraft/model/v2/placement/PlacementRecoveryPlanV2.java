package com.github.nankotsu029.landformcraft.model.v2.placement;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sealed operation-bound Release 2 recovery plan (V2-6-10). Prepared only from a conservative
 * diagnosis ({@link PlacementRecoveryClassificationV2#SAFE_TO_ROLLBACK} or
 * {@link PlacementRecoveryClassificationV2#SAFE_TO_ACCEPT}) and carries the actor-bound
 * RECOVERY_ROLLBACK／RECOVERY_ACCEPT confirmation plus the world evidence checksums the
 * classification was derived from. Preparing a recovery plan never mutates the world or the
 * persisted journal.
 */
public record PlacementRecoveryPlanV2(
        int planVersion,
        String recoveryContractVersion,
        UUID placementId,
        UUID operationId,
        UUID worldId,
        PlacementPlanV2.PlacementActorV2 actor,
        PlacementRecoveryClassificationV2 classification,
        String sourcePlacementPlanChecksum,
        String sourceEnvelopePlanChecksum,
        String sourceReservationChecksum,
        String sourceSnapshotPlanChecksum,
        String sourceJournalChecksum,
        PlacementJournalStateV2 sourceJournalState,
        String baselineChecksum,
        String observedWorldChecksum,
        String expectedAppliedStreamChecksum,
        PlacementConfirmationActionV2 confirmationAction,
        String confirmationHash,
        String confirmationCreatedAt,
        String confirmationExpiresAt,
        RetentionTransitionV2 retentionTransition,
        String createdAt,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String RECOVERY_CONTRACT_VERSION = "release-2-placement-recovery-plan-v1";
    public static final long MAX_CANONICAL_BYTES = 256L * 1024L;

    private static final Pattern CHECKSUM_FULL = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ISO_INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");

    public PlacementRecoveryPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("placement recovery planVersion must be 1");
        }
        recoveryContractVersion = nonBlank(recoveryContractVersion, "recoveryContractVersion", 64);
        if (!RECOVERY_CONTRACT_VERSION.equals(recoveryContractVersion)) {
            throw new IllegalArgumentException("unknown placement recovery contract version");
        }
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(classification, "classification");
        if (classification != PlacementRecoveryClassificationV2.SAFE_TO_ROLLBACK
                && classification != PlacementRecoveryClassificationV2.SAFE_TO_ACCEPT) {
            throw new IllegalArgumentException(
                    "recovery plan classification must be SAFE_TO_ROLLBACK or SAFE_TO_ACCEPT");
        }
        sourcePlacementPlanChecksum = checksum(sourcePlacementPlanChecksum, "sourcePlacementPlanChecksum");
        sourceEnvelopePlanChecksum = checksum(sourceEnvelopePlanChecksum, "sourceEnvelopePlanChecksum");
        sourceReservationChecksum = checksum(sourceReservationChecksum, "sourceReservationChecksum");
        sourceSnapshotPlanChecksum = checksum(sourceSnapshotPlanChecksum, "sourceSnapshotPlanChecksum");
        sourceJournalChecksum = checksum(sourceJournalChecksum, "sourceJournalChecksum");
        Objects.requireNonNull(sourceJournalState, "sourceJournalState");
        baselineChecksum = checksum(baselineChecksum, "baselineChecksum");
        observedWorldChecksum = checksum(observedWorldChecksum, "observedWorldChecksum");
        expectedAppliedStreamChecksum = checksumOrEmpty(
                expectedAppliedStreamChecksum, "expectedAppliedStreamChecksum");
        Objects.requireNonNull(confirmationAction, "confirmationAction");
        switch (confirmationAction) {
            case RECOVERY_ROLLBACK -> {
                // rollback is safe under both preparable classifications
            }
            case RECOVERY_ACCEPT -> {
                if (classification != PlacementRecoveryClassificationV2.SAFE_TO_ACCEPT) {
                    throw new IllegalArgumentException(
                            "RECOVERY_ACCEPT requires a SAFE_TO_ACCEPT classification");
                }
                if (expectedAppliedStreamChecksum.isEmpty()
                        || !expectedAppliedStreamChecksum.equals(observedWorldChecksum)) {
                    throw new IllegalArgumentException(
                            "RECOVERY_ACCEPT requires the observed world to equal the expected"
                                    + " applied stream");
                }
            }
            default -> throw new IllegalArgumentException(
                    "recovery plan confirmationAction must be RECOVERY_ROLLBACK or RECOVERY_ACCEPT");
        }
        confirmationHash = checksum(confirmationHash, "confirmationHash");
        confirmationCreatedAt = instant(confirmationCreatedAt, "confirmationCreatedAt");
        confirmationExpiresAt = instant(confirmationExpiresAt, "confirmationExpiresAt");
        if (!java.time.Instant.parse(confirmationExpiresAt)
                .isAfter(java.time.Instant.parse(confirmationCreatedAt))) {
            throw new IllegalArgumentException(
                    "recovery confirmation expiresAt must be after createdAt");
        }
        Objects.requireNonNull(retentionTransition, "retentionTransition");
        if (retentionTransition != RetentionTransitionV2.KEEP_SNAPSHOTS_FOR_CLEANUP) {
            throw new IllegalArgumentException(
                    "recovery plan retention transition must keep snapshots for cleanup");
        }
        createdAt = instant(createdAt, "createdAt");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
    }

    public PlacementRecoveryPlanV2 withCanonicalChecksum(String checksum) {
        return new PlacementRecoveryPlanV2(
                planVersion,
                recoveryContractVersion,
                placementId,
                operationId,
                worldId,
                actor,
                classification,
                sourcePlacementPlanChecksum,
                sourceEnvelopePlanChecksum,
                sourceReservationChecksum,
                sourceSnapshotPlanChecksum,
                sourceJournalChecksum,
                sourceJournalState,
                baselineChecksum,
                observedWorldChecksum,
                expectedAppliedStreamChecksum,
                confirmationAction,
                confirmationHash,
                confirmationCreatedAt,
                confirmationExpiresAt,
                retentionTransition,
                createdAt,
                checksum);
    }

    public void requireBindings(
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation,
            PlacementSnapshotPlanV2 snapshot,
            PlacementJournalV2 journal
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(journal, "journal");
        if (!placementId.equals(plan.placementId())
                || !operationId.equals(plan.operationId())
                || !worldId.equals(plan.target().worldId())) {
            throw new IllegalArgumentException("recovery plan identity mismatch");
        }
        if (!sourcePlacementPlanChecksum.equals(plan.canonicalChecksum())) {
            throw new IllegalArgumentException("recovery plan placement checksum mismatch");
        }
        if (!sourceEnvelopePlanChecksum.equals(envelope.canonicalChecksum())) {
            throw new IllegalArgumentException("recovery plan envelope checksum mismatch");
        }
        if (!sourceReservationChecksum.equals(reservation.canonicalChecksum())) {
            throw new IllegalArgumentException("recovery plan reservation checksum mismatch");
        }
        if (!sourceSnapshotPlanChecksum.equals(snapshot.canonicalChecksum())) {
            throw new IllegalArgumentException("recovery plan snapshot checksum mismatch");
        }
        if (journal.state() != sourceJournalState
                || !sourceJournalChecksum.equals(journal.journalChecksum())) {
            throw new IllegalArgumentException("recovery plan journal binding mismatch");
        }
        snapshot.requireBindings(plan, envelope, reservation);
    }

    public enum RetentionTransitionV2 {
        KEEP_SNAPSHOTS_FOR_CLEANUP
    }

    private static String checksum(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!CHECKSUM_FULL.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase sha-256 hex digest");
        }
        return value;
    }

    private static String checksumOrEmpty(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isEmpty()) {
            return value;
        }
        return checksum(value, field);
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
