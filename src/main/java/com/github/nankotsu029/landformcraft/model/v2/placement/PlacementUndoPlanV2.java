package com.github.nankotsu029.landformcraft.model.v2.placement;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sealed operation-bound Undo plan (V2-6-09). Carries the UNDO confirmation and retention
 * transition without mutating the sealed apply {@link PlacementPlanV2} (whose checksum remains
 * bound to snapshot／verify evidence). Force overwrite is out of scope.
 */
public record PlacementUndoPlanV2(
        int planVersion,
        String undoContractVersion,
        UUID placementId,
        UUID operationId,
        UUID worldId,
        PlacementPlanV2.PlacementActorV2 actor,
        String sourcePlacementPlanChecksum,
        String sourceEnvelopePlanChecksum,
        String sourceApplyReservationChecksum,
        String undoReservationChecksum,
        String sourceSnapshotPlanChecksum,
        String sourceVerifyEvidenceChecksum,
        String sourceAppliedJournalChecksum,
        String sourceApplyCompleteJournalChecksum,
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
    public static final String UNDO_CONTRACT_VERSION = "release-2-placement-undo-plan-v1";
    public static final long MAX_CANONICAL_BYTES = 256L * 1024L;

    private static final Pattern CHECKSUM_FULL = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ISO_INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");

    public PlacementUndoPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("placement undo planVersion must be 1");
        }
        undoContractVersion = nonBlank(undoContractVersion, "undoContractVersion", 64);
        if (!UNDO_CONTRACT_VERSION.equals(undoContractVersion)) {
            throw new IllegalArgumentException("unknown placement undo contract version");
        }
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(actor, "actor");
        sourcePlacementPlanChecksum = checksum(sourcePlacementPlanChecksum, "sourcePlacementPlanChecksum");
        sourceEnvelopePlanChecksum = checksum(sourceEnvelopePlanChecksum, "sourceEnvelopePlanChecksum");
        sourceApplyReservationChecksum = checksum(
                sourceApplyReservationChecksum, "sourceApplyReservationChecksum");
        undoReservationChecksum = checksum(undoReservationChecksum, "undoReservationChecksum");
        sourceSnapshotPlanChecksum = checksum(sourceSnapshotPlanChecksum, "sourceSnapshotPlanChecksum");
        sourceVerifyEvidenceChecksum = checksum(
                sourceVerifyEvidenceChecksum, "sourceVerifyEvidenceChecksum");
        sourceAppliedJournalChecksum = checksum(
                sourceAppliedJournalChecksum, "sourceAppliedJournalChecksum");
        sourceApplyCompleteJournalChecksum = checksum(
                sourceApplyCompleteJournalChecksum, "sourceApplyCompleteJournalChecksum");
        expectedAppliedStreamChecksum = checksum(
                expectedAppliedStreamChecksum, "expectedAppliedStreamChecksum");
        Objects.requireNonNull(confirmationAction, "confirmationAction");
        if (confirmationAction != PlacementConfirmationActionV2.UNDO) {
            throw new IllegalArgumentException("undo plan confirmationAction must be UNDO");
        }
        confirmationHash = checksum(confirmationHash, "confirmationHash");
        confirmationCreatedAt = instant(confirmationCreatedAt, "confirmationCreatedAt");
        confirmationExpiresAt = instant(confirmationExpiresAt, "confirmationExpiresAt");
        if (!java.time.Instant.parse(confirmationExpiresAt)
                .isAfter(java.time.Instant.parse(confirmationCreatedAt))) {
            throw new IllegalArgumentException("undo confirmation expiresAt must be after createdAt");
        }
        Objects.requireNonNull(retentionTransition, "retentionTransition");
        if (retentionTransition != RetentionTransitionV2.KEEP_SNAPSHOTS_FOR_CLEANUP) {
            throw new IllegalArgumentException(
                    "undo plan retention transition must keep snapshots for cleanup");
        }
        createdAt = instant(createdAt, "createdAt");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
    }

    public PlacementUndoPlanV2 withCanonicalChecksum(String checksum) {
        return new PlacementUndoPlanV2(
                planVersion,
                undoContractVersion,
                placementId,
                operationId,
                worldId,
                actor,
                sourcePlacementPlanChecksum,
                sourceEnvelopePlanChecksum,
                sourceApplyReservationChecksum,
                undoReservationChecksum,
                sourceSnapshotPlanChecksum,
                sourceVerifyEvidenceChecksum,
                sourceAppliedJournalChecksum,
                sourceApplyCompleteJournalChecksum,
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
            PlacementReservationPlanV2 applyReservation,
            PlacementReservationPlanV2 undoReservation,
            PlacementSnapshotPlanV2 snapshot,
            PlacementVerifyEvidenceV2 evidence,
            PlacementJournalV2 appliedJournal,
            PlacementJournalV2 applyCompleteJournal
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(applyReservation, "applyReservation");
        Objects.requireNonNull(undoReservation, "undoReservation");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(appliedJournal, "appliedJournal");
        Objects.requireNonNull(applyCompleteJournal, "applyCompleteJournal");
        if (!placementId.equals(plan.placementId())
                || !operationId.equals(plan.operationId())
                || !worldId.equals(plan.target().worldId())) {
            throw new IllegalArgumentException("undo plan identity mismatch");
        }
        if (!sourcePlacementPlanChecksum.equals(plan.canonicalChecksum())) {
            throw new IllegalArgumentException("undo plan placement checksum mismatch");
        }
        if (!sourceEnvelopePlanChecksum.equals(envelope.canonicalChecksum())) {
            throw new IllegalArgumentException("undo plan envelope checksum mismatch");
        }
        if (!sourceApplyReservationChecksum.equals(applyReservation.canonicalChecksum())
                || applyReservation.operation() != PlacementReservationOperationV2.APPLY) {
            throw new IllegalArgumentException("undo plan apply-reservation binding mismatch");
        }
        if (!undoReservationChecksum.equals(undoReservation.canonicalChecksum())
                || undoReservation.operation() != PlacementReservationOperationV2.UNDO) {
            throw new IllegalArgumentException("undo plan undo-reservation binding mismatch");
        }
        if (!sourceSnapshotPlanChecksum.equals(snapshot.canonicalChecksum())) {
            throw new IllegalArgumentException("undo plan snapshot checksum mismatch");
        }
        if (!sourceVerifyEvidenceChecksum.equals(evidence.canonicalChecksum())
                || !expectedAppliedStreamChecksum.equals(evidence.expectedStreamChecksum())) {
            throw new IllegalArgumentException("undo plan verify-evidence binding mismatch");
        }
        if (appliedJournal.state() != PlacementJournalStateV2.APPLIED
                || !sourceAppliedJournalChecksum.equals(appliedJournal.journalChecksum())) {
            throw new IllegalArgumentException("undo plan applied journal binding mismatch");
        }
        if (applyCompleteJournal.state() != PlacementJournalStateV2.APPLYING
                || !sourceApplyCompleteJournalChecksum.equals(applyCompleteJournal.journalChecksum())) {
            throw new IllegalArgumentException("undo plan apply-complete journal binding mismatch");
        }
        evidence.requireBindings(plan, envelope, snapshot, applyCompleteJournal);
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
