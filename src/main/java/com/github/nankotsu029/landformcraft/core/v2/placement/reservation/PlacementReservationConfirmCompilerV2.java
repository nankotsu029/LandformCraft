package com.github.nankotsu029.landformcraft.core.v2.placement.reservation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementConfirmationActionV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationLeaseStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationOperationV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Atomically reserves all effect-envelope regions／disk and issues a bound confirmation.
 * On any failure after reserve, releases the lease. Does not snapshot or apply.
 */
public final class PlacementReservationConfirmCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final PlacementConfirmationBinderV2 binder = new PlacementConfirmationBinderV2();
    private final FilePlacementSafetyStoreV2 safetyStore;
    private final Clock clock;

    public PlacementReservationConfirmCompilerV2(FilePlacementSafetyStoreV2 safetyStore, Clock clock) {
        this.safetyStore = Objects.requireNonNull(safetyStore, "safetyStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PreparedReservationV2 prepare(ReservationConfirmRequestV2 request) {
        Objects.requireNonNull(request, "request");
        PlacementPlanV2 plan = request.envelopeBoundPlan();
        PlacementEnvelopePlanV2 envelope = request.envelopePlan();
        if (!plan.envelopeReferences().bound()
                || plan.reservationConfirmationBinding().reservationBound()
                || plan.reservationConfirmationBinding().confirmationIssued()) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.STATE_MISMATCH,
                    "prepare requires envelope-bound plan without reservation/confirmation");
        }
        if (!plan.envelopeReferences().mutationEnvelopePlanChecksum()
                .equals(envelope.mutationEnvelopeChecksum())
                || !plan.envelopeReferences().effectEnvelopePlanChecksum()
                .equals(envelope.canonicalChecksum())) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.CHECKSUM_MISMATCH,
                    "placement plan envelope refs do not match envelope plan");
        }
        if (request.actor() != null && !request.actor().equals(plan.actor())) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.ACTOR_MISMATCH,
                    "request actor does not match placement plan actor");
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(PlacementConfirmationBinderV2.CONFIRMATION_TTL);
        String storeKey;
        try {
            storeKey = safetyStore.fileStoreKey();
        } catch (IOException exception) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.DISK_SHORTAGE,
                    "unable to probe snapshot FileStore: " + exception.getMessage());
        }

        List<PlacementReservationPlanV2.RegionLeaseV2> regions = new ArrayList<>(envelope.tiles().size());
        for (PlacementEnvelopePlanV2.TileEnvelopeV2 tile : envelope.tiles()) {
            regions.add(new PlacementReservationPlanV2.RegionLeaseV2(
                    tile.tileId(), tile.tileIndex(), tile.effectAabb()));
        }
        PlacementReservationPlanV2 draft = new PlacementReservationPlanV2(
                PlacementReservationPlanV2.VERSION,
                PlacementReservationPlanV2.RESERVATION_CONTRACT_VERSION,
                plan.placementId(),
                plan.operationId(),
                plan.target().worldId(),
                PlacementReservationOperationV2.APPLY,
                plan.actor(),
                new PlacementReservationPlanV2.PlacementPlanBinding(
                        PlacementReservationPlanV2.PlacementPlanBinding.VERSION,
                        plan.canonicalChecksum(),
                        PlacementReservationPlanV2.PlacementPlanBinding.CONTRACT_VERSION),
                new PlacementReservationPlanV2.EnvelopeBinding(
                        PlacementReservationPlanV2.EnvelopeBinding.VERSION,
                        envelope.canonicalChecksum(),
                        envelope.mutationEnvelopeChecksum(),
                        PlacementReservationPlanV2.EnvelopeBinding.CONTRACT_VERSION),
                regions,
                new PlacementReservationPlanV2.DiskLeaseV2(
                        plan.placementId(), storeKey, envelope.diskEstimate().totalBytes()),
                PlacementReservationLeaseStateV2.PLANNED,
                now.toString(),
                expiresAt.toString(),
                request.budget(),
                PlacementPlanV2.UNBOUND_CHECKSUM);
        PlacementReservationPlanV2 sealedReservation = codec.sealPlacementReservationPlan(draft);
        sealedReservation.requirePlacementAndEnvelope(plan, envelope);

        boolean reserved = false;
        try {
            safetyStore.reserve(
                    sealedReservation,
                    PlacementReservationLeaseStateV2.PLANNED,
                    safetyStore.reservationFloorBytes());
            reserved = true;

            PlacementPlanV2 reservationBound = codec.sealPlacementPlan(plan.withReservationConfirmationBinding(
                    PlacementPlanV2.ReservationConfirmationBindingV2.reservationBound(
                            sealedReservation.canonicalChecksum(),
                            plan.actor())));
            sealedReservation.requirePlacementAndEnvelope(reservationBound, envelope);

            PlacementConfirmationBinderV2.IssuedConfirmationV2 issued = binder.issue(
                    reservationBound,
                    envelope,
                    sealedReservation,
                    PlacementConfirmationActionV2.APPLY,
                    now,
                    request.plaintextToken() == null
                            ? PlacementConfirmationBinderV2.newPlaintextToken()
                            : request.plaintextToken());

            PlacementPlanV2 confirmed = codec.sealPlacementPlan(
                    reservationBound.withReservationConfirmationBinding(
                            PlacementPlanV2.ReservationConfirmationBindingV2.confirmationIssued(
                                    sealedReservation.canonicalChecksum(),
                                    issued.action(),
                                    issued.actor(),
                                    issued.confirmationHash(),
                                    issued.createdAt(),
                                    issued.expiresAt())));

            PlacementJournalV2 journal = sealJournal(
                    confirmed,
                    PlacementJournalStateV2.CONFIRMATION_ISSUED,
                    sealedReservation.diskLease().reservedBytes(),
                    now,
                    "regions and disk reserved; confirmation issued");
            return new PreparedReservationV2(
                    confirmed, journal, sealedReservation, issued.plaintextToken());
        } catch (PlacementReservationExceptionV2 exception) {
            if (reserved) {
                rollbackReservation(plan.placementId(), exception);
            }
            throw exception;
        } catch (RuntimeException | IOException exception) {
            if (reserved) {
                rollbackReservation(plan.placementId(), exception);
            }
            if (exception instanceof PlacementReservationExceptionV2 reservationException) {
                throw reservationException;
            }
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.PARTIAL_RESERVATION_ROLLED_BACK,
                    "reservation/confirmation failed and leases were released: " + exception.getMessage());
        }
    }

    public void verifyAndConsume(
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation,
            PlacementPlanV2.PlacementActorV2 actor,
            String plaintextToken
    ) throws IOException {
        binder.verify(
                plan,
                envelope,
                reservation,
                PlacementConfirmationActionV2.APPLY,
                actor,
                clock.instant(),
                plaintextToken);
        safetyStore.assertOwned(plan.placementId(), actor);
        safetyStore.markConfirmationConsumed(plan.reservationConfirmationBinding().confirmationHash());
    }

    private void rollbackReservation(java.util.UUID placementId, Throwable cause) {
        try {
            safetyStore.release(placementId);
        } catch (IOException releaseFailure) {
            cause.addSuppressed(releaseFailure);
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.PARTIAL_RESERVATION_ROLLED_BACK,
                    "reservation failed and lease release also failed");
        }
    }

    private PlacementJournalV2 sealJournal(
            PlacementPlanV2 plan,
            PlacementJournalStateV2 state,
            long reservedBytes,
            Instant updatedAt,
            String message
    ) {
        List<PlacementJournalV2.PlacementTileEntryV2> tiles = new ArrayList<>(plan.tileOrder().tiles().size());
        for (PlacementPlanV2.TileRefV2 tile : plan.tileOrder().tiles()) {
            tiles.add(new PlacementJournalV2.PlacementTileEntryV2(
                    tile.tileId(),
                    tile.tileIndex(),
                    PlacementTileStateV2.PENDING,
                    "",
                    ""));
        }
        return codec.sealPlacementJournal(new PlacementJournalV2(
                PlacementJournalV2.VERSION,
                PlacementJournalV2.JOURNAL_CONTRACT_VERSION,
                plan,
                plan.canonicalChecksum(),
                state,
                tiles,
                reservedBytes,
                0L,
                updatedAt.toString(),
                message,
                PlacementPlanV2.UNBOUND_CHECKSUM));
    }

    public record ReservationConfirmRequestV2(
            PlacementPlanV2 envelopeBoundPlan,
            PlacementEnvelopePlanV2 envelopePlan,
            PlacementReservationPlanV2.ResourceBudget budget,
            PlacementPlanV2.PlacementActorV2 actor,
            String plaintextToken
    ) {
        public ReservationConfirmRequestV2 {
            Objects.requireNonNull(envelopeBoundPlan, "envelopeBoundPlan");
            Objects.requireNonNull(envelopePlan, "envelopePlan");
            Objects.requireNonNull(budget, "budget");
        }

        public static ReservationConfirmRequestV2 of(
                PlacementPlanV2 envelopeBoundPlan,
                PlacementEnvelopePlanV2 envelopePlan,
                PlacementReservationPlanV2.ResourceBudget budget
        ) {
            return new ReservationConfirmRequestV2(envelopeBoundPlan, envelopePlan, budget, null, null);
        }
    }

    public record PreparedReservationV2(
            PlacementPlanV2 plan,
            PlacementJournalV2 journal,
            PlacementReservationPlanV2 reservationPlan,
            String plaintextToken
    ) {
        public PreparedReservationV2 {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(journal, "journal");
            Objects.requireNonNull(reservationPlan, "reservationPlan");
            Objects.requireNonNull(plaintextToken, "plaintextToken");
            if (journal.state() != PlacementJournalStateV2.CONFIRMATION_ISSUED) {
                throw new IllegalArgumentException("prepared journal must be CONFIRMATION_ISSUED");
            }
            if (!plan.reservationConfirmationBinding().confirmationIssued()) {
                throw new IllegalArgumentException("prepared plan must have issued confirmation");
            }
        }
    }
}
