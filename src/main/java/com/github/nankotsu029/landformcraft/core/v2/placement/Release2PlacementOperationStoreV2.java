package com.github.nankotsu029.landformcraft.core.v2.placement;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentEvidenceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementUndoPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementVerifyEvidenceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryPlanV2;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable Release 2 application context composed only of the already-versioned placement
 * contracts. It is operational state, not a Release artifact, and never invents a new schema.
 */
public final class Release2PlacementOperationStoreV2 {
    private static final String ENVELOPE = "envelope.json";
    private static final String RESERVATION = "reservation.json";
    private static final String PREPARED_JOURNAL = "prepared-journal.json";
    private static final String SNAPSHOT = "snapshot.json";
    private static final String CONTAINMENT = "containment.json";
    private static final String CONFIRMED_JOURNAL = "confirmed-journal.json";
    private static final String APPLY_COMPLETE = "apply-complete-journal.json";
    private static final String VERIFY = "verify-evidence.json";
    private static final String APPLIED_JOURNAL = "applied-journal.json";
    private static final String UNDO_PLAN = "undo-plan.json";
    private static final String UNDO_RESERVATION = "undo-reservation.json";
    private static final String RECOVERY_PLAN = "recovery-plan.json";

    private final Path root;
    private final WriteFaultInjectorV2 faultInjector;
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public Release2PlacementOperationStoreV2(Path root) throws IOException {
        this(root, WriteFaultInjectorV2.none());
    }

    public Release2PlacementOperationStoreV2(
            Path root,
            WriteFaultInjectorV2 faultInjector
    ) throws IOException {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.faultInjector = Objects.requireNonNull(faultInjector, "faultInjector");
        Files.createDirectories(this.root);
        requireDirectory(this.root);
    }

    public synchronized void savePrepared(
            UUID placementId,
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation,
            PlacementJournalV2 preparedJournal
    ) throws IOException {
        Objects.requireNonNull(preparedJournal, "preparedJournal");
        if (preparedJournal.state()
                != com.github.nankotsu029.landformcraft.model.v2.placement
                .PlacementJournalStateV2.CONFIRMATION_ISSUED
                || !preparedJournal.plan().placementId().equals(placementId)) {
            throw new IOException("prepared operation journal has the wrong identity or state");
        }
        Path directory = operationDirectory(placementId, true);
        writeStrict(placementId, WritePointV2.PREPARED_ENVELOPE, directory.resolve(ENVELOPE),
                path -> codec.writePlacementEnvelopePlan(path, envelope),
                codec::readPlacementEnvelopePlan, envelope);
        writeStrict(placementId, WritePointV2.PREPARED_RESERVATION, directory.resolve(RESERVATION),
                path -> codec.writePlacementReservationPlan(path, reservation),
                codec::readPlacementReservationPlan, reservation);
        writeStrict(placementId, WritePointV2.PREPARED_JOURNAL, directory.resolve(PREPARED_JOURNAL),
                path -> codec.writePlacementJournal(path, preparedJournal),
                codec::readPlacementJournal, preparedJournal);
    }

    public synchronized void saveConfirmed(
            UUID placementId,
            PlacementSnapshotPlanV2 snapshot,
            PlacementContainmentEvidenceV2 containment,
            PlacementJournalV2 confirmedJournal
    ) throws IOException {
        Objects.requireNonNull(confirmedJournal, "confirmedJournal");
        if (confirmedJournal.state()
                != com.github.nankotsu029.landformcraft.model.v2.placement
                .PlacementJournalStateV2.SNAPSHOT_COMPLETE
                || !confirmedJournal.plan().placementId().equals(placementId)) {
            throw new IOException("confirmed operation journal has the wrong identity or state");
        }
        Path directory = operationDirectory(placementId, false);
        writeStrict(placementId, WritePointV2.CONFIRMED_SNAPSHOT, directory.resolve(SNAPSHOT),
                path -> codec.writePlacementSnapshotPlan(path, snapshot),
                codec::readPlacementSnapshotPlan, snapshot);
        writeStrict(placementId, WritePointV2.CONFIRMED_CONTAINMENT, directory.resolve(CONTAINMENT),
                path -> codec.writePlacementContainmentEvidence(path, containment),
                codec::readPlacementContainmentEvidence, containment);
        writeStrict(placementId, WritePointV2.CONFIRMED_JOURNAL, directory.resolve(CONFIRMED_JOURNAL),
                path -> codec.writePlacementJournal(path, confirmedJournal),
                codec::readPlacementJournal, confirmedJournal);
    }

    public PreparedContextV2 loadPrepared(UUID placementId) throws IOException {
        Path directory = operationDirectory(placementId, false);
        return new PreparedContextV2(
                codec.readPlacementEnvelopePlan(requireFile(directory.resolve(ENVELOPE))),
                codec.readPlacementReservationPlan(requireFile(directory.resolve(RESERVATION))),
                codec.readPlacementJournal(requireFile(directory.resolve(PREPARED_JOURNAL))));
    }

    public ConfirmedContextV2 loadConfirmed(UUID placementId) throws IOException {
        PreparedContextV2 prepared = loadPrepared(placementId);
        Path directory = operationDirectory(placementId, false);
        return new ConfirmedContextV2(
                prepared.envelope(), prepared.reservation(),
                codec.readPlacementSnapshotPlan(requireFile(directory.resolve(SNAPSHOT))),
                codec.readPlacementContainmentEvidence(requireFile(directory.resolve(CONTAINMENT))),
                codec.readPlacementJournal(requireFile(directory.resolve(CONFIRMED_JOURNAL))));
    }

    public synchronized void saveApplied(
            UUID placementId,
            PlacementJournalV2 applyComplete,
            PlacementJournalV2 appliedJournal,
            PlacementVerifyEvidenceV2 verifyEvidence
    ) throws IOException {
        Objects.requireNonNull(appliedJournal, "appliedJournal");
        if (appliedJournal.state()
                != com.github.nankotsu029.landformcraft.model.v2.placement
                .PlacementJournalStateV2.APPLIED
                || !appliedJournal.plan().placementId().equals(placementId)) {
            throw new IOException("applied operation journal has the wrong identity or state");
        }
        ConfirmedContextV2 confirmed = loadConfirmed(placementId);
        try {
            verifyEvidence.requireBindings(
                    appliedJournal.plan(), confirmed.envelope(), confirmed.snapshot(), applyComplete);
        } catch (IllegalArgumentException mismatch) {
            throw new IOException("applied operation evidence binding mismatch", mismatch);
        }
        Path directory = operationDirectory(placementId, false);
        writeStrict(placementId, WritePointV2.APPLIED_VERIFY_EVIDENCE, directory.resolve(VERIFY),
                path -> codec.writePlacementVerifyEvidence(path, verifyEvidence),
                codec::readPlacementVerifyEvidence, verifyEvidence);
        writeStrict(placementId, WritePointV2.APPLIED_APPLY_COMPLETE_JOURNAL,
                directory.resolve(APPLY_COMPLETE), path -> codec.writePlacementJournal(path, applyComplete),
                codec::readPlacementJournal, applyComplete);
        writeStrict(placementId, WritePointV2.APPLIED_TERMINAL_JOURNAL,
                directory.resolve(APPLIED_JOURNAL), path -> codec.writePlacementJournal(path, appliedJournal),
                codec::readPlacementJournal, appliedJournal);
    }

    public AppliedContextV2 loadApplied(UUID placementId) throws IOException {
        ConfirmedContextV2 confirmed = loadConfirmed(placementId);
        Path directory = operationDirectory(placementId, false);
        return new AppliedContextV2(
                confirmed.envelope(), confirmed.reservation(), confirmed.snapshot(), confirmed.containment(),
                codec.readPlacementJournal(requireFile(directory.resolve(APPLY_COMPLETE))),
                codec.readPlacementVerifyEvidence(requireFile(directory.resolve(VERIFY))),
                codec.readPlacementJournal(requireFile(directory.resolve(APPLIED_JOURNAL))));
    }

    public synchronized void saveUndo(
            UUID placementId,
            PlacementUndoPlanV2 undoPlan,
            PlacementReservationPlanV2 undoReservation
    ) throws IOException {
        Path directory = operationDirectory(placementId, false);
        writeStrict(placementId, WritePointV2.UNDO_PLAN, directory.resolve(UNDO_PLAN),
                path -> codec.writePlacementUndoPlan(path, undoPlan),
                codec::readPlacementUndoPlan, undoPlan);
        writeStrict(placementId, WritePointV2.UNDO_RESERVATION, directory.resolve(UNDO_RESERVATION),
                path -> codec.writePlacementReservationPlan(path, undoReservation),
                codec::readPlacementReservationPlan, undoReservation);
    }

    public UndoContextV2 loadUndo(UUID placementId) throws IOException {
        AppliedContextV2 applied = loadApplied(placementId);
        Path directory = operationDirectory(placementId, false);
        return new UndoContextV2(applied,
                codec.readPlacementUndoPlan(requireFile(directory.resolve(UNDO_PLAN))),
                codec.readPlacementReservationPlan(requireFile(directory.resolve(UNDO_RESERVATION))));
    }

    public synchronized void saveRecovery(UUID placementId, PlacementRecoveryPlanV2 recoveryPlan)
            throws IOException {
        Path directory = operationDirectory(placementId, false);
        writeStrict(placementId, WritePointV2.RECOVERY_PLAN, directory.resolve(RECOVERY_PLAN),
                path -> codec.writePlacementRecoveryPlan(path, recoveryPlan),
                codec::readPlacementRecoveryPlan, recoveryPlan);
    }

    public PlacementRecoveryPlanV2 loadRecovery(UUID placementId) throws IOException {
        Path directory = operationDirectory(placementId, false);
        return codec.readPlacementRecoveryPlan(requireFile(directory.resolve(RECOVERY_PLAN)));
    }

    private Path operationDirectory(UUID placementId, boolean create) throws IOException {
        Objects.requireNonNull(placementId, "placementId");
        Path directory = root.resolve(placementId.toString()).normalize();
        if (!directory.startsWith(root)) throw new IOException("Release 2 operation path escaped root");
        if (create && !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(directory);
            forceDirectory(root);
        }
        requireDirectory(directory);
        return directory;
    }

    private static Path requireFile(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IOException("Release 2 operation state file is missing or unsafe: " + file.getFileName());
        }
        return file;
    }

    private static void requireDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw new IOException("Release 2 operation state root must be a non-symbolic directory");
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private <T> void writeStrict(
            UUID placementId,
            WritePointV2 writePoint,
            Path target,
            IoWriter writer,
            IoReader<T> reader,
            T expected
    ) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(target))) {
            throw new IOException("Release 2 operation target is unsafe: " + target.getFileName());
        }
        faultInjector.check(placementId, writePoint, WritePhaseV2.BEFORE_WRITE);
        Path staging = Files.createTempFile(target.getParent(), ".operation-v2-", ".tmp");
        try {
            writer.write(staging);
            T staged = reader.read(staging);
            if (!expected.equals(staged)) throw new IOException("operation state strict read-back changed");
            try (FileChannel channel = FileChannel.open(staging, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Release 2 operation store requires atomic move", exception);
            }
            forceDirectory(target.getParent());
            T published = reader.read(target);
            if (!expected.equals(published)) throw new IOException("published operation state changed");
            faultInjector.check(placementId, writePoint, WritePhaseV2.AFTER_PUBLISH);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    @FunctionalInterface private interface IoWriter { void write(Path path) throws IOException; }
    @FunctionalInterface private interface IoReader<T> { T read(Path path) throws IOException; }

    public record PreparedContextV2(
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation,
            PlacementJournalV2 preparedJournal
    ) { }

    public record ConfirmedContextV2(
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation,
            PlacementSnapshotPlanV2 snapshot,
            PlacementContainmentEvidenceV2 containment,
            PlacementJournalV2 confirmedJournal
    ) { }

    public record AppliedContextV2(
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation,
            PlacementSnapshotPlanV2 snapshot,
            PlacementContainmentEvidenceV2 containment,
            PlacementJournalV2 applyCompleteJournal,
            PlacementVerifyEvidenceV2 verifyEvidence,
            PlacementJournalV2 appliedJournal
    ) { }

    public record UndoContextV2(
            AppliedContextV2 applied,
            PlacementUndoPlanV2 undoPlan,
            PlacementReservationPlanV2 undoReservation
    ) { }

    public enum WritePointV2 {
        PREPARED_ENVELOPE,
        PREPARED_RESERVATION,
        PREPARED_JOURNAL,
        CONFIRMED_SNAPSHOT,
        CONFIRMED_CONTAINMENT,
        CONFIRMED_JOURNAL,
        APPLIED_VERIFY_EVIDENCE,
        APPLIED_APPLY_COMPLETE_JOURNAL,
        APPLIED_TERMINAL_JOURNAL,
        UNDO_PLAN,
        UNDO_RESERVATION,
        RECOVERY_PLAN
    }

    public enum WritePhaseV2 {
        BEFORE_WRITE,
        AFTER_PUBLISH
    }

    @FunctionalInterface
    public interface WriteFaultInjectorV2 {
        void check(UUID placementId, WritePointV2 writePoint, WritePhaseV2 phase) throws IOException;

        static WriteFaultInjectorV2 none() {
            return (placementId, writePoint, phase) -> { };
        }
    }
}
