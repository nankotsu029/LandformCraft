package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FilePlacementSafetyStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementSnapshotAllCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerificationV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerifierV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Filesystem-backed strict gate executed before the first scheduler submission. Re-verifies the
 * Release directory, every sealed binding, durable reservation／confirmation consumption,
 * published snapshots, and containment evidence.
 */
public final class StrictPlacementApplyPrerequisiteVerifierV2
        implements PlacementApplyPrerequisiteVerifierV2 {
    private final Path releasesRoot;
    private final FilePlacementSafetyStoreV2 safetyStore;
    private final PlacementSnapshotAllCompilerV2 snapshotCompiler;
    private final ReleaseCoreVerifierV2 releaseVerifier;
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public StrictPlacementApplyPrerequisiteVerifierV2(
            Path releasesRoot,
            FilePlacementSafetyStoreV2 safetyStore,
            PlacementSnapshotAllCompilerV2 snapshotCompiler
    ) {
        this(releasesRoot, safetyStore, snapshotCompiler, new ReleaseCoreVerifierV2());
    }

    StrictPlacementApplyPrerequisiteVerifierV2(
            Path releasesRoot,
            FilePlacementSafetyStoreV2 safetyStore,
            PlacementSnapshotAllCompilerV2 snapshotCompiler,
            ReleaseCoreVerifierV2 releaseVerifier
    ) {
        this.releasesRoot = Objects.requireNonNull(releasesRoot, "releasesRoot")
                .toAbsolutePath().normalize();
        this.safetyStore = Objects.requireNonNull(safetyStore, "safetyStore");
        this.snapshotCompiler = Objects.requireNonNull(snapshotCompiler, "snapshotCompiler");
        this.releaseVerifier = Objects.requireNonNull(releaseVerifier, "releaseVerifier");
    }

    @Override
    public void verify(PlacementApplyRequestV2 request) {
        Objects.requireNonNull(request, "request");
        var plan = request.placementPlan();
        var envelope = request.envelopePlan();
        var reservation = request.reservationPlan();
        var snapshot = request.snapshotPlan();
        var evidence = request.containmentEvidence();
        var journal = request.journal();

        if (request.cancellation().isCancellationRequested()) {
            throw new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.CANCELLED_BEFORE_COMMIT,
                    "Release 2 apply was cancelled before prerequisite verification",
                    false);
        }
        if (journal.state() != PlacementJournalStateV2.SNAPSHOT_COMPLETE) {
            throw failure(PlacementApplyFailureCodeV2.STATE_MISMATCH,
                    "apply requires a SNAPSHOT_COMPLETE journal", null);
        }
        requireSealed(plan.canonicalChecksum(), codec.placementPlanChecksum(plan), "placement plan");
        requireSealed(envelope.canonicalChecksum(), codec.placementEnvelopePlanChecksum(envelope),
                "envelope plan");
        requireSealed(envelope.mutationEnvelopeChecksum(),
                codec.placementEnvelopeMutationChecksum(envelope), "mutation envelope");
        requireSealed(reservation.canonicalChecksum(),
                codec.placementReservationPlanChecksum(reservation), "reservation plan");
        requireSealed(snapshot.canonicalChecksum(),
                codec.placementSnapshotPlanChecksum(snapshot), "snapshot plan");
        requireSealed(evidence.canonicalChecksum(),
                codec.placementContainmentEvidenceChecksum(evidence), "containment evidence");
        requireSealed(journal.journalChecksum(), codec.placementJournalChecksum(journal), "journal");

        try {
            var envelopeSourcePlan = codec.sealPlacementPlan(
                    plan.withReservationConfirmationBinding(
                                    com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2
                                            .ReservationConfirmationBindingV2.unbound(plan.actor()))
                            .withEnvelopeReferences(
                                    com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2
                                            .EnvelopeReferencesV2.unbound()));
            envelope.requirePlacementPlan(envelopeSourcePlan);
            var reservationSourcePlan = codec.sealPlacementPlan(
                    plan.withReservationConfirmationBinding(
                            com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2
                                    .ReservationConfirmationBindingV2.unbound(plan.actor())));
            if (!reservation.placementPlanBinding().sourcePlacementPlanChecksum()
                    .equals(reservationSourcePlan.canonicalChecksum())) {
                throw new IllegalArgumentException("reservation source placement-plan checksum mismatch");
            }
            reservation.requirePlacementAndEnvelope(plan, envelope);
            snapshot.requireBindings(plan, envelope, reservation);
            evidence.requireBindings(plan, envelope, snapshot);
            if (!journal.plan().equals(plan) || !journal.planChecksum().equals(plan.canonicalChecksum())) {
                throw new IllegalArgumentException("journal is bound to a different placement plan");
            }
        } catch (IllegalArgumentException exception) {
            throw failure(PlacementApplyFailureCodeV2.BINDING_MISMATCH,
                    "Release 2 apply binding verification failed", exception);
        }

        Path release = resolveReleaseContainer(plan.releaseBinding().releaseDirectory());
        try {
            ReleaseCoreVerificationV2 verified = releaseVerifier.verify(
                    release, request.cancellation());
            if (!verified.manifest().canonicalChecksum()
                    .equals(plan.releaseBinding().manifestChecksum())
                    || !verified.manifest().requiredCapabilities().equals(plan.requiredCapabilities())) {
                throw new IOException("Release manifest checksum or capability binding mismatch");
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw failure(PlacementApplyFailureCodeV2.RELEASE_VERIFICATION_FAILED,
                    "strict Release 2 verification failed", exception);
        }

        try {
            safetyStore.assertOwned(plan.placementId(), plan.actor());
            if (!safetyStore.isConfirmationConsumed(
                    plan.reservationConfirmationBinding().confirmationHash())) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.CONFIRMATION_NOT_CONSUMED,
                        "bound confirmation has not been durably consumed",
                        false);
            }
        } catch (PlacementApplyExceptionV2 known) {
            throw known;
        } catch (IOException | RuntimeException exception) {
            throw failure(PlacementApplyFailureCodeV2.BINDING_MISMATCH,
                    "durable reservation ownership verification failed", exception);
        }

        PlacementSnapshotPlanV2 strictSnapshot;
        try {
            strictSnapshot = snapshotCompiler.loadPublished(
                    plan, envelope, reservation, request.cancellation());
        } catch (RuntimeException exception) {
            throw failure(PlacementApplyFailureCodeV2.SNAPSHOT_VERIFICATION_FAILED,
                    "published snapshot strict read-back failed", exception);
        }
        if (!strictSnapshot.equals(snapshot)) {
            throw failure(PlacementApplyFailureCodeV2.SNAPSHOT_VERIFICATION_FAILED,
                    "published snapshot differs from the apply request", null);
        }

        var source = request.blockSource().binding();
        if (!source.releaseManifestChecksum().equals(plan.releaseBinding().manifestChecksum())
                || !source.requiredCapabilities().equals(plan.requiredCapabilities())) {
            throw failure(PlacementApplyFailureCodeV2.SOURCE_INVALID,
                    "canonical block source is not bound to the verified Release", null);
        }
    }

    private static void requireSealed(String recorded, String computed, String label) {
        if (!recorded.equals(computed)) {
            throw failure(PlacementApplyFailureCodeV2.BINDING_MISMATCH,
                    label + " checksum mismatch", null);
        }
    }

    private Path resolveReleaseContainer(String relativeDirectory) {
        if (!Files.isDirectory(releasesRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(releasesRoot)) {
            throw failure(PlacementApplyFailureCodeV2.RELEASE_VERIFICATION_FAILED,
                    "Release 2 root is missing or symbolic", null);
        }
        Path relative = Path.of(relativeDirectory);
        Path cursor = releasesRoot;
        for (Path component : relative) {
            cursor = cursor.resolve(component);
            if (Files.isSymbolicLink(cursor)) {
                throw failure(PlacementApplyFailureCodeV2.RELEASE_VERIFICATION_FAILED,
                        "Release 2 placement path contains a symbolic component", null);
            }
        }
        Path release = releasesRoot.resolve(relative).normalize();
        if (!release.startsWith(releasesRoot)
                || (!Files.isDirectory(release, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(release, LinkOption.NOFOLLOW_LINKS))) {
            throw failure(PlacementApplyFailureCodeV2.RELEASE_VERIFICATION_FAILED,
                    "Release 2 placement container is missing or outside its root", null);
        }
        return release;
    }

    private static PlacementApplyExceptionV2 failure(
            PlacementApplyFailureCodeV2 code,
            String message,
            Throwable cause
    ) {
        return cause == null
                ? new PlacementApplyExceptionV2(code, message, false)
                : new PlacementApplyExceptionV2(code, message + ": " + safeMessage(cause), false, cause);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
