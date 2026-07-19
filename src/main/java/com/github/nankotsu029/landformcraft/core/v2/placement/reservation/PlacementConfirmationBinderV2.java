package com.github.nankotsu029.landformcraft.core.v2.placement.reservation;

import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementConfirmationActionV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationOperationV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Issues and verifies actor-bound one-time confirmation tokens for Release 2 placement.
 * Plaintext tokens are never persisted; only SHA-256 hashes are stored on the plan/journal.
 */
public final class PlacementConfirmationBinderV2 {
    public static final Duration CONFIRMATION_TTL = Duration.ofMinutes(10);
    public static final String BINDING_CONTRACT_VERSION = "release-2-placement-confirmation-binding-v1";

    public IssuedConfirmationV2 issue(
            PlacementPlanV2 reservationBoundPlan,
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation,
            PlacementConfirmationActionV2 action,
            Instant createdAt,
            String plaintextToken
    ) {
        Objects.requireNonNull(reservationBoundPlan, "reservationBoundPlan");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(createdAt, "createdAt");
        plaintextToken = nonBlank(plaintextToken, "plaintextToken");
        if (action == PlacementConfirmationActionV2.NONE) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.CONFIRMATION_INVALID,
                    "confirmation action must not be NONE");
        }
        if (!reservationBoundPlan.envelopeReferences().bound()
                || !reservationBoundPlan.reservationConfirmationBinding().reservationBound()
                || reservationBoundPlan.reservationConfirmationBinding().confirmationIssued()) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.STATE_MISMATCH,
                    "confirmation requires reservation-bound plan without prior confirmation");
        }
        if (!reservationBoundPlan.reservationConfirmationBinding().reservationChecksum()
                .equals(reservation.canonicalChecksum())) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.CHECKSUM_MISMATCH,
                    "confirmation reservation checksum mismatch");
        }
        reservation.requirePlacementAndEnvelope(reservationBoundPlan, envelope);
        Instant expiresAt = createdAt.plus(CONFIRMATION_TTL);
        String hash = confirmationHash(
                action,
                reservationBoundPlan,
                envelope,
                reservation,
                reservationBoundPlan.actor(),
                createdAt,
                expiresAt,
                plaintextToken);
        return new IssuedConfirmationV2(
                action,
                reservationBoundPlan.actor(),
                hash,
                createdAt.toString(),
                expiresAt.toString(),
                plaintextToken);
    }

    /**
     * Issues an actor-bound UNDO confirmation for a terminal applied Release 2 placement.
     * Does not rewrite the sealed apply plan binding (V2-6-09); the hash binds the immutable
     * apply plan identity, envelope, and the UNDO reservation only.
     */
    public IssuedConfirmationV2 issueUndo(
            PlacementPlanV2 sealedApplyPlan,
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 undoReservation,
            PlacementPlanV2.PlacementActorV2 actor,
            Instant createdAt,
            String plaintextToken
    ) {
        Objects.requireNonNull(sealedApplyPlan, "sealedApplyPlan");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(undoReservation, "undoReservation");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(createdAt, "createdAt");
        plaintextToken = nonBlank(plaintextToken, "plaintextToken");
        if (undoReservation.operation() != PlacementReservationOperationV2.UNDO) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.STATE_MISMATCH,
                    "undo confirmation requires an UNDO reservation plan");
        }
        if (!sealedApplyPlan.envelopeReferences().bound()
                || !sealedApplyPlan.reservationConfirmationBinding().reservationBound()
                || !sealedApplyPlan.reservationConfirmationBinding().confirmationIssued()) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.STATE_MISMATCH,
                    "undo confirmation requires a fully bound applied placement plan");
        }
        undoReservation.requirePlacementAndEnvelope(sealedApplyPlan, envelope);
        Instant expiresAt = createdAt.plus(CONFIRMATION_TTL);
        String hash = confirmationHash(
                PlacementConfirmationActionV2.UNDO,
                sealedApplyPlan,
                envelope,
                undoReservation,
                actor,
                createdAt,
                expiresAt,
                plaintextToken);
        return new IssuedConfirmationV2(
                PlacementConfirmationActionV2.UNDO,
                actor,
                hash,
                createdAt.toString(),
                expiresAt.toString(),
                plaintextToken);
    }

    public void verifyUndo(
            PlacementPlanV2 sealedApplyPlan,
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 undoReservation,
            PlacementPlanV2.PlacementActorV2 actor,
            String confirmationHash,
            Instant createdAt,
            Instant expiresAt,
            Instant now,
            String plaintextToken
    ) {
        Objects.requireNonNull(sealedApplyPlan, "sealedApplyPlan");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(undoReservation, "undoReservation");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(confirmationHash, "confirmationHash");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(now, "now");
        plaintextToken = nonBlank(plaintextToken, "plaintextToken");
        if (undoReservation.operation() != PlacementReservationOperationV2.UNDO) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.STATE_MISMATCH,
                    "undo confirmation requires an UNDO reservation plan");
        }
        if (!now.isBefore(expiresAt)) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.CONFIRMATION_EXPIRED,
                    "confirmation token has expired");
        }
        undoReservation.requirePlacementAndEnvelope(sealedApplyPlan, envelope);
        String expected = confirmationHash(
                PlacementConfirmationActionV2.UNDO,
                sealedApplyPlan,
                envelope,
                undoReservation,
                actor,
                createdAt,
                expiresAt,
                plaintextToken);
        if (!constantTimeEquals(confirmationHash, expected)) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.CONFIRMATION_INVALID,
                    "confirmation token is invalid");
        }
    }

    public void verify(
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation,
            PlacementConfirmationActionV2 action,
            PlacementPlanV2.PlacementActorV2 actor,
            Instant now,
            String plaintextToken
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(now, "now");
        plaintextToken = nonBlank(plaintextToken, "plaintextToken");
        PlacementPlanV2.ReservationConfirmationBindingV2 binding = plan.reservationConfirmationBinding();
        if (!binding.confirmationIssued()) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.CONFIRMATION_INVALID,
                    "confirmation has not been issued");
        }
        if (!binding.confirmationActor().equals(actor)) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.ACTOR_MISMATCH,
                    "confirmation belongs to a different actor");
        }
        if (binding.confirmationAction() != action) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.CONFIRMATION_INVALID,
                    "confirmation action mismatch");
        }
        Instant expiresAt = Instant.parse(binding.confirmationExpiresAt());
        if (!now.isBefore(expiresAt)) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.CONFIRMATION_EXPIRED,
                    "confirmation token has expired");
        }
        reservation.requirePlacementAndEnvelope(plan, envelope);
        if (!binding.reservationChecksum().equals(reservation.canonicalChecksum())) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.CHECKSUM_MISMATCH,
                    "confirmation reservation checksum mismatch");
        }
        String expected = confirmationHash(
                action,
                plan,
                envelope,
                reservation,
                actor,
                Instant.parse(binding.confirmationCreatedAt()),
                expiresAt,
                plaintextToken);
        if (!constantTimeEquals(binding.confirmationHash(), expected)) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.CONFIRMATION_INVALID,
                    "confirmation token is invalid");
        }
    }

    public static String confirmationHash(
            PlacementConfirmationActionV2 action,
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation,
            PlacementPlanV2.PlacementActorV2 actor,
            Instant createdAt,
            Instant expiresAt,
            String plaintextToken
    ) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        plaintextToken = nonBlank(plaintextToken, "plaintextToken");
        PlacementPlanV2.PlacementTargetV2 target = plan.target();
        String binding = BINDING_CONTRACT_VERSION + '\n'
                + action.name() + '\n'
                + plan.releaseBinding().manifestChecksum() + '\n'
                + envelope.placementPlanBinding().sourcePlacementPlanChecksum() + '\n'
                + envelope.canonicalChecksum() + '\n'
                + envelope.mutationEnvelopeChecksum() + '\n'
                + reservation.canonicalChecksum() + '\n'
                + plan.operationId() + '\n'
                + plan.placementId() + '\n'
                + target.worldId() + '\n'
                + target.anchorX() + ',' + target.anchorY() + ',' + target.anchorZ() + '\n'
                + target.minimumX() + ',' + target.minimumY() + ',' + target.minimumZ() + ':'
                + target.maximumX() + ',' + target.maximumY() + ',' + target.maximumZ() + '\n'
                + actor.canonical() + '\n'
                + createdAt + '\n'
                + expiresAt + '\n'
                + plaintextToken;
        return sha256(binding);
    }

    public static String newPlaintextToken() {
        return UUID.randomUUID().toString();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String nonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " must be non-blank and <= 128");
        }
        return value;
    }

    public record IssuedConfirmationV2(
            PlacementConfirmationActionV2 action,
            PlacementPlanV2.PlacementActorV2 actor,
            String confirmationHash,
            String createdAt,
            String expiresAt,
            String plaintextToken
    ) {
        public IssuedConfirmationV2 {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(confirmationHash, "confirmationHash");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
            Objects.requireNonNull(plaintextToken, "plaintextToken");
        }
    }
}
