package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.ActorIdentity;
import com.github.nankotsu029.landformcraft.model.ConfirmationAction;
import com.github.nankotsu029.landformcraft.model.PlacementJournal;
import com.github.nankotsu029.landformcraft.model.PlacementPlan;
import com.github.nankotsu029.landformcraft.model.PlacementState;
import com.github.nankotsu029.landformcraft.model.PlacementTileCheckpoint;
import com.github.nankotsu029.landformcraft.model.PlacementTileState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotCleanupServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void planIsDryRunAndExecuteRechecksActorAndPreservesRecoverySnapshots(@TempDir Path directory)
            throws Exception {
        try (GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            Path snapshots = directory.resolve("snapshots");
            var repository = new FilePlacementJournalRepository(directory.resolve("placements"), executors);
            PlacementJournal base = new LandformDataCodec().readPlacementJournal(
                    Path.of("examples/placement-journal.json"));
            PlacementJournal applied = terminal(base, snapshots, UUID.randomUUID(), PlacementState.APPLIED);
            PlacementJournal recovery = terminal(base, snapshots, UUID.randomUUID(), PlacementState.RECOVERY_REQUIRED);
            repository.save(applied).join();
            repository.save(recovery).join();
            SnapshotCleanupService service = new SnapshotCleanupService(
                    snapshots, directory.resolve("cleanup-plans"), repository, executors, CLOCK, 30);

            PreparedCleanup prepared = service.plan(ActorIdentity.console()).join();

            assertEquals(List.of(applied.plan().placementId()), prepared.plan().entries().stream()
                    .map(value -> value.placementId()).toList());
            Path appliedSnapshot = snapshots.resolve(applied.tiles().get(0).snapshotFile());
            Path recoverySnapshot = snapshots.resolve(recovery.tiles().get(0).snapshotFile());
            assertTrue(Files.exists(appliedSnapshot), "planning must be a dry run");
            CompletionException mismatch = assertThrows(CompletionException.class, () -> service.execute(
                    prepared.plan().planId(), prepared.confirmationToken(),
                    ActorIdentity.system("OTHER")).join());
            assertEquals(LandformErrorCode.CONFIRM_ACTOR_MISMATCH,
                    ((LandformException) mismatch.getCause()).code());

            service.execute(prepared.plan().planId(), prepared.confirmationToken(), ActorIdentity.console()).join();

            assertFalse(Files.exists(appliedSnapshot));
            assertTrue(Files.exists(recoverySnapshot), "RECOVERY_REQUIRED snapshots must never be selected");
            assertTrue(Files.isRegularFile(directory.resolve("cleanup-plans/cleanup-audit.jsonl")));
            assertThrows(CompletionException.class, () -> service.execute(
                    prepared.plan().planId(), prepared.confirmationToken(), ActorIdentity.console()).join());
        }
    }

    private static PlacementJournal terminal(
            PlacementJournal example, Path snapshots, UUID id, PlacementState state
    ) throws Exception {
        PlacementPlan plan = example.plan();
        PlacementPlan copied = new PlacementPlan(
                plan.schemaVersion(), id, plan.releaseDirectory(), plan.releaseChecksum(), plan.requestId(),
                plan.worldId(), plan.worldName(), plan.actor(), plan.targetX(), plan.targetY(), plan.targetZ(),
                plan.minimumX(), plan.minimumY(), plan.minimumZ(), plan.maximumX(), plan.maximumY(),
                plan.maximumZ(), plan.createdAt());
        Path file = snapshots.resolve(id.toString()).resolve("tile-00-00.schem");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "snapshot:" + id);
        String relative = snapshots.relativize(file).toString().replace('\\', '/');
        long bytes = Files.size(file);
        return new PlacementJournal(
                1, copied, state, ConfirmationAction.NONE, ActorIdentity.console(), "",
                Instant.EPOCH, Instant.EPOCH,
                List.of(new PlacementTileCheckpoint(
                        "tile-00-00", PlacementTileState.VERIFIED, relative, Sha256.file(file))),
                bytes, bytes, Instant.parse("2026-07-01T00:00:00Z"), "terminal fixture");
    }
}
