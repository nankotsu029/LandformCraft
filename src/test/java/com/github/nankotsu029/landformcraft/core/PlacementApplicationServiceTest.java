package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.ReleaseArtifacts;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.ManifestTile;
import com.github.nankotsu029.landformcraft.model.PlacementJournal;
import com.github.nankotsu029.landformcraft.model.PlacementPlan;
import com.github.nankotsu029.landformcraft.model.PlacementState;
import com.github.nankotsu029.landformcraft.model.PlacementTileState;
import com.github.nankotsu029.landformcraft.model.WorldDescriptor;
import com.github.nankotsu029.landformcraft.model.ActorIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementApplicationServiceTest {
    private static final UUID WORLD_ID = UUID.fromString("a7eeab99-4e61-4b9a-a39e-bc0fe855850b");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void confirmationGatePreventsMutationAndSuccessfulApplyCanBeUndone(@TempDir Path directory) throws Exception {
        try (Fixture fixture = fixture(directory, -1)) {
            PreparedPlacement prepared = fixture.service().plan(fixture.releaseName(), "world", 10, 20, 30).join();

            assertEquals(PlacementState.PLANNED, prepared.journal().state());
            assertEquals(List.of(fixture.releaseName()), fixture.service().releaseDirectories().join());
            assertEquals(List.of(prepared.journal().plan().placementId()), fixture.service().placementIds().join());
            assertEquals(List.of("describe"), fixture.gateway().operations());
            assertThrows(CompletionException.class,
                    () -> fixture.service().execute(prepared.journal().plan().placementId(), "wrong").join());
            assertEquals(List.of("describe"), fixture.gateway().operations());

            PlacementJournal applied = fixture.service()
                    .execute(prepared.journal().plan().placementId(), prepared.confirmationToken()).join();
            assertEquals(PlacementState.APPLIED, applied.state());
            assertTrue(applied.tiles().stream().allMatch(tile -> tile.state() == PlacementTileState.VERIFIED));
            assertTrue(fixture.gateway().operations().contains("verify:tile-00-00"));

            PreparedPlacement undo = fixture.service().prepareUndo(applied.plan().placementId()).join();
            PlacementJournal undone = fixture.service().undo(applied.plan().placementId(), undo.confirmationToken()).join();
            assertEquals(PlacementState.UNDONE, undone.state());
            assertTrue(undone.tiles().stream().allMatch(tile -> tile.state() == PlacementTileState.RESTORED));
            assertReverseRestoreOrder(fixture.gateway().operations());
        }
    }

    @Test
    void partialFailureRestoresEverySnapshotInReverseOrder(@TempDir Path directory) throws Exception {
        try (Fixture fixture = fixture(directory, 1)) {
            PreparedPlacement prepared = fixture.service().plan(fixture.releaseName(), "world", 0, 0, 0).join();

            assertThrows(CompletionException.class, () -> fixture.service()
                    .execute(prepared.journal().plan().placementId(), prepared.confirmationToken()).join());

            PlacementJournal journal = fixture.service().status(prepared.journal().plan().placementId()).join();
            assertEquals(PlacementState.ROLLED_BACK, journal.state());
            assertEquals(PlacementTileState.RESTORED, journal.tiles().get(0).state());
            assertEquals(PlacementTileState.RESTORED, journal.tiles().get(1).state());
            assertReverseRestoreOrder(fixture.gateway().operations());

            Path persisted = directory.resolve("placements")
                    .resolve(prepared.journal().plan().placementId() + ".json");
            assertTrue(Files.isRegularFile(persisted));
            assertEquals(PlacementState.ROLLED_BACK,
                    new com.github.nankotsu029.landformcraft.format.LandformDataCodec()
                            .readPlacementJournal(persisted).state());
        }
    }

    @Test
    void tamperedSnapshotMakesUndoRecoveryRequiredAndInterruptedJournalIsRecovered(@TempDir Path directory)
            throws Exception {
        try (Fixture fixture = fixture(directory, -1)) {
            PreparedPlacement prepared = fixture.service().plan(fixture.releaseName(), "world", 0, 0, 0).join();
            PlacementJournal applied = fixture.service()
                    .execute(prepared.journal().plan().placementId(), prepared.confirmationToken()).join();
            PreparedPlacement undo = fixture.service().prepareUndo(applied.plan().placementId()).join();
            Path snapshot = directory.resolve("snapshots").resolve(applied.tiles().get(0).snapshotFile());
            Files.writeString(snapshot, "tampered", StandardCharsets.UTF_8);

            assertThrows(CompletionException.class,
                    () -> fixture.service().undo(applied.plan().placementId(), undo.confirmationToken()).join());
            assertEquals(PlacementState.RECOVERY_REQUIRED,
                    fixture.service().status(applied.plan().placementId()).join().state());

            PlacementJournal interrupted = new PlacementJournal(
                    applied.schemaVersion(), applied.plan(), PlacementState.APPLYING,
                    com.github.nankotsu029.landformcraft.model.ConfirmationAction.NONE,
                    applied.plan().actor(), "", Instant.EPOCH, Instant.EPOCH,
                    applied.tiles(), applied.reservedBytes(), applied.snapshotBytesUsed(),
                    CLOCK.instant(), "simulated stop"
            );
            fixture.repository().save(interrupted).join();
            List<PlacementJournal> recovered = fixture.service().recoverInterrupted().join();
            assertEquals(1, recovered.size());
            assertEquals(PlacementState.RECOVERY_REQUIRED, recovered.get(0).state());
        }
    }

    @Test
    void worldDriftRejectsUndoBeforeAnySnapshotIsRestored(@TempDir Path directory) throws Exception {
        try (Fixture fixture = fixture(directory, -1)) {
            PreparedPlacement prepared = fixture.service().plan(fixture.releaseName(), "world", 0, 0, 0).join();
            PlacementJournal applied = fixture.service()
                    .execute(prepared.journal().plan().placementId(), prepared.confirmationToken()).join();
            PreparedPlacement undo = fixture.service().prepareUndo(applied.plan().placementId()).join();
            fixture.gateway().driftOnVerify(true);

            CompletionException failure = assertThrows(CompletionException.class,
                    () -> fixture.service().undo(applied.plan().placementId(), undo.confirmationToken()).join());

            assertTrue(failure.getCause().getMessage().contains("world drift detected"));
            assertFalse(fixture.gateway().operations().stream().anyMatch(operation -> operation.startsWith("restore:")));
            assertEquals(PlacementState.APPLIED,
                    fixture.service().status(applied.plan().placementId()).join().state());
        }
    }

    @Test
    void rejectsPlacementOutsideWorldBorderBeforeSnapshot(@TempDir Path directory) throws Exception {
        try (Fixture fixture = fixture(directory, -1)) {
            CompletionException failure = assertThrows(CompletionException.class,
                    () -> fixture.service().plan(fixture.releaseName(), "world", 990, 0, 0).join());
            assertTrue(failure.getCause().getMessage().contains("outside world"));
            assertEquals(List.of("describe"), fixture.gateway().operations());
        }
    }

    @Test
    void callerCannotCancelWorldMutationAndShutdownRejectsNewMutations(@TempDir Path directory) throws Exception {
        try (Fixture fixture = fixture(directory, -1)) {
            PreparedPlacement prepared = fixture.service().plan(fixture.releaseName(), "world", 0, 0, 0).join();
            fixture.gateway().blockApply();
            CompletableFuture<PlacementJournal> execution = fixture.service()
                    .execute(prepared.journal().plan().placementId(), prepared.confirmationToken());
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (fixture.gateway().operations().stream().noneMatch(value -> value.startsWith("apply:"))
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertTrue(fixture.gateway().operations().stream().anyMatch(value -> value.startsWith("apply:")));
            assertFalse(execution.cancel(true));

            fixture.service().stopAcceptingMutations();
            assertThrows(IllegalStateException.class,
                    () -> fixture.service().plan(fixture.releaseName(), "world", 0, 0, 0));
            fixture.gateway().releaseApply();
            assertEquals(PlacementState.APPLIED, execution.join().state());
        }
    }

    @Test
    void confirmationIsBoundToActorAndMismatchNeverSnapshots(@TempDir Path directory) throws Exception {
        try (Fixture fixture = fixture(directory, -1)) {
            ActorIdentity owner = ActorIdentity.player(UUID.fromString("11111111-2222-4333-8444-555555555555"));
            ActorIdentity other = ActorIdentity.player(UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"));
            PreparedPlacement prepared = fixture.service().plan(
                    fixture.releaseName(), "world", 0, 0, 0, owner).join();

            CompletionException failure = assertThrows(CompletionException.class, () -> fixture.service()
                    .execute(prepared.journal().plan().placementId(), prepared.confirmationToken(), other).join());

            assertEquals(LandformErrorCode.CONFIRM_ACTOR_MISMATCH,
                    ((LandformException) failure.getCause()).code());
            assertFalse(fixture.gateway().operations().stream().anyMatch(value -> value.startsWith("snapshot:")));
        }
    }

    @Test
    void concurrentOverlappingPlansAllowExactlyOneAndAdjacentRegionIsAllowed(@TempDir Path directory)
            throws Exception {
        try (Fixture fixture = fixture(directory, -1)) {
            ActorIdentity actor = ActorIdentity.console();
            CompletableFuture<PreparedPlacement> first = fixture.service().plan(
                    fixture.releaseName(), "world", 0, 0, 0, actor);
            CompletableFuture<PreparedPlacement> second = fixture.service().plan(
                    fixture.releaseName(), "world", 0, 0, 0, actor);
            List<CompletableFuture<PreparedPlacement>> values = List.of(first, second);
            long successes = values.stream().filter(value -> {
                try {
                    value.join();
                    return true;
                } catch (CompletionException ignored) {
                    return false;
                }
            }).count();

            assertEquals(1L, successes);
            Throwable overlap = values.stream().filter(CompletableFuture::isCompletedExceptionally)
                    .findFirst().orElseThrow().handle((value, failure) -> failure).join();
            Throwable cause = overlap instanceof CompletionException ? overlap.getCause() : overlap;
            assertEquals(LandformErrorCode.PLACEMENT_OVERLAP, ((LandformException) cause).code());

            PreparedPlacement adjacent = fixture.service().plan(
                    fixture.releaseName(), "world", 64, 0, 0, actor).join();
            assertEquals(64, adjacent.journal().plan().minimumX());
        }
    }

    @Test
    void snapshotEstimateLimitRejectsBeforeWorldMutation(@TempDir Path directory) throws Exception {
        try (Fixture fixture = fixture(directory, -1)) {
            PlacementApplicationService constrained = new PlacementApplicationService(
                    directory.resolve("exports"), directory.resolve("snapshots"), fixture.executors(),
                    fixture.repository(), fixture.gateway(), CLOCK, new DiskBudgetPolicy(0L, 1L, 0L));

            CompletionException failure = assertThrows(CompletionException.class, () -> constrained.plan(
                    fixture.releaseName(), "world", 0, 0, 0, ActorIdentity.console()).join());

            assertEquals(LandformErrorCode.SNAPSHOT_NO_SPACE,
                    ((LandformException) failure.getCause()).code());
            assertFalse(fixture.gateway().operations().stream().anyMatch(value -> value.startsWith("snapshot:")));
        }
    }

    @Test
    void recoveryAcceptRequiresFullWorldMatchAndActorBoundToken(@TempDir Path directory) throws Exception {
        try (Fixture fixture = fixture(directory, -1)) {
            ActorIdentity owner = ActorIdentity.console();
            PreparedPlacement prepared = fixture.service().plan(
                    fixture.releaseName(), "world", 0, 0, 0, owner).join();
            PlacementJournal applied = fixture.service().execute(
                    prepared.journal().plan().placementId(), prepared.confirmationToken(), owner).join();
            PlacementJournal interrupted = new PlacementJournal(
                    applied.schemaVersion(), applied.plan(), PlacementState.RECOVERY_REQUIRED,
                    com.github.nankotsu029.landformcraft.model.ConfirmationAction.NONE,
                    applied.plan().actor(), "", Instant.EPOCH, Instant.EPOCH, applied.tiles(),
                    applied.reservedBytes(), applied.snapshotBytesUsed(), CLOCK.instant(), "simulated verify crash");
            fixture.repository().save(interrupted).join();

            PreparedRecovery diagnosis = fixture.service().diagnoseRecovery(
                    applied.plan().placementId(), owner).join();

            assertEquals(com.github.nankotsu029.landformcraft.model.RecoveryClassification.SAFE_TO_ACCEPT,
                    diagnosis.report().classification());
            CompletionException mismatch = assertThrows(CompletionException.class, () -> fixture.service()
                    .recoverAccept(applied.plan().placementId(), diagnosis.confirmationToken(),
                            ActorIdentity.system("OTHER")).join());
            assertEquals(LandformErrorCode.CONFIRM_ACTOR_MISMATCH,
                    ((LandformException) mismatch.getCause()).code());
            assertEquals(PlacementState.APPLIED, fixture.service().recoverAccept(
                    applied.plan().placementId(), diagnosis.confirmationToken(), owner).join().state());
            assertTrue(Files.isRegularFile(directory.resolve("recovery-audit.jsonl")));
        }
    }

    @Test
    void ambiguousRecoveryNeverOffersSuccessToken(@TempDir Path directory) throws Exception {
        try (Fixture fixture = fixture(directory, -1)) {
            PreparedPlacement prepared = fixture.service().plan(
                    fixture.releaseName(), "world", 0, 0, 0).join();
            PlacementJournal applied = fixture.service().execute(
                    prepared.journal().plan().placementId(), prepared.confirmationToken()).join();
            fixture.repository().save(new PlacementJournal(
                    applied.schemaVersion(), applied.plan(), PlacementState.RECOVERY_REQUIRED,
                    com.github.nankotsu029.landformcraft.model.ConfirmationAction.NONE,
                    applied.plan().actor(), "", Instant.EPOCH, Instant.EPOCH, applied.tiles(),
                    applied.reservedBytes(), applied.snapshotBytesUsed(), CLOCK.instant(), "ambiguous fixture")).join();
            fixture.gateway().driftOnVerify(true);

            PreparedRecovery diagnosis = fixture.service().diagnoseRecovery(
                    applied.plan().placementId(), ActorIdentity.system("LEGACY")).join();

            assertEquals(com.github.nankotsu029.landformcraft.model.RecoveryClassification.MANUAL_INTERVENTION_REQUIRED,
                    diagnosis.report().classification());
            assertTrue(diagnosis.confirmationToken().isEmpty());
        }
    }

    private static void assertReverseRestoreOrder(List<String> operations) {
        int second = operations.indexOf("restore:tile-01-00");
        int first = operations.indexOf("restore:tile-00-00");
        assertTrue(second >= 0, operations.toString());
        assertTrue(first > second, operations.toString());
    }

    private static Fixture fixture(Path directory, int failApplyIndex) throws IOException {
        Path exports = directory.resolve("exports");
        GenerationExecutors executors = GenerationExecutors.create(4, 2, 16);
        ReleaseArtifacts release;
        try {
            String request = Files.readString(Path.of("examples/rocky-coast/request.yml"))
                    .replace("rocky-coast-001", "placement-test")
                    .replace("width: 500", "width: 64")
                    .replace("length: 500", "length: 32")
                    .replace("min-y: -32", "min-y: 0")
                    .replace("max-y: 160", "max-y: 31")
                    .replace("water-level: 62", "water-level: 15")
                    .replace("tile-size: 128", "tile-size: 32")
                    .replace("create-zip: true", "create-zip: false");
            String intent = Files.readString(Path.of("examples/rocky-coast/terrain-intent.json"))
                    .replace("rocky-coast-001", "placement-test");
            Path requestPath = directory.resolve("request.yml");
            Path intentPath = directory.resolve("terrain-intent.json");
            Files.writeString(requestPath, request);
            Files.writeString(intentPath, intent);
            release = new ReleaseApplicationService(executors)
                    .export(requestPath, intentPath, exports, 0).join();
        } catch (RuntimeException | IOException failure) {
            executors.close();
            throw failure;
        }
        FakeWorldGateway gateway = new FakeWorldGateway(failApplyIndex);
        FilePlacementJournalRepository repository = new FilePlacementJournalRepository(
                directory.resolve("placements"), executors
        );
        PlacementApplicationService service = new PlacementApplicationService(
                exports, directory.resolve("snapshots"), executors, repository, gateway, CLOCK
        );
        return new Fixture(
                service, repository, gateway, executors,
                exports.relativize(release.releaseDirectory()).toString().replace('\\', '/')
        );
    }

    private record Fixture(
            PlacementApplicationService service,
            FilePlacementJournalRepository repository,
            FakeWorldGateway gateway,
            GenerationExecutors executors,
            String releaseName
    ) implements AutoCloseable {
        @Override
        public void close() {
            executors.close();
        }
    }

    private static final class FakeWorldGateway implements PlacementWorldGateway {
        private final int failApplyIndex;
        private final List<String> operations = new CopyOnWriteArrayList<>();
        private int applyIndex;
        private boolean driftOnVerify;
        private CompletableFuture<Void> applyBarrier;

        private FakeWorldGateway(int failApplyIndex) {
            this.failApplyIndex = failApplyIndex;
        }

        List<String> operations() {
            return List.copyOf(operations);
        }

        void driftOnVerify(boolean value) {
            driftOnVerify = value;
        }

        void blockApply() {
            applyBarrier = new CompletableFuture<>();
        }

        void releaseApply() {
            applyBarrier.complete(null);
        }

        @Override
        public CompletionStage<WorldDescriptor> describeWorld(String worldName) {
            operations.add("describe");
            return CompletableFuture.completedFuture(
                    new WorldDescriptor(WORLD_ID, worldName, -64, 319, -1000, 1000, -1000, 1000)
            );
        }

        @Override
        public CompletionStage<SnapshotArtifact> snapshot(
                PlacementPlan plan, ManifestTile tile, Path snapshotFile
        ) {
            operations.add("snapshot:" + tile.id());
            try {
                Files.createDirectories(snapshotFile.getParent());
                Files.writeString(snapshotFile, "snapshot:" + tile.id(), StandardCharsets.UTF_8);
                return CompletableFuture.completedFuture(
                        new SnapshotArtifact(snapshotFile, Sha256.file(snapshotFile))
                );
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        @Override
        public CompletionStage<Void> apply(PlacementPlan plan, ManifestTile tile, Path schematicFile) {
            operations.add("apply:" + tile.id());
            if (applyIndex++ == failApplyIndex) {
                return CompletableFuture.failedFuture(new IllegalStateException("simulated apply failure"));
            }
            if (applyBarrier != null) {
                return applyBarrier;
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Boolean> verify(PlacementPlan plan, ManifestTile tile, Path schematicFile) {
            operations.add("verify:" + tile.id());
            return CompletableFuture.completedFuture(!driftOnVerify);
        }

        @Override
        public CompletionStage<Void> restore(
                PlacementPlan plan, ManifestTile tile, Path snapshotFile, String expectedChecksum
        ) {
            operations.add("restore:" + tile.id());
            try {
                if (!Files.isRegularFile(snapshotFile) || !Sha256.file(snapshotFile).equals(expectedChecksum)) {
                    return CompletableFuture.failedFuture(new IllegalStateException("invalid snapshot"));
                }
                return CompletableFuture.completedFuture(null);
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
    }
}
