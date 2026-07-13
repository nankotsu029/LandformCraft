package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.PlacementWorldGateway;
import com.github.nankotsu029.landformcraft.core.SnapshotArtifact;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.ManifestTile;
import com.github.nankotsu029.landformcraft.model.PlacementPlan;
import com.github.nankotsu029.landformcraft.model.WorldDescriptor;
import com.github.nankotsu029.landformcraft.worldedit.LoadedSchematic;
import com.github.nankotsu029.landformcraft.worldedit.WorldEditWorldAccess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Schedules world access on Paper and keeps schematic file I/O on admitted virtual threads. */
public final class PaperWorldEditPlacementGateway implements PlacementWorldGateway {
    private final PaperMainThreadDispatcher dispatcher;
    private final GenerationExecutors executors;
    private final WorldEditWorldAccess worldEdit = new WorldEditWorldAccess();

    public PaperWorldEditPlacementGateway(
            PaperMainThreadDispatcher dispatcher,
            GenerationExecutors executors
    ) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.executors = Objects.requireNonNull(executors, "executors");
    }

    @Override
    public CompletionStage<WorldDescriptor> describeWorld(String worldName) {
        return dispatcher.supply(() -> worldEdit.describeWorld(worldName));
    }

    @Override
    public CompletionStage<SnapshotArtifact> snapshot(
            PlacementPlan plan, ManifestTile tile, Path snapshotFile
    ) {
        return dispatcher.supply(() -> worldEdit.capture(plan, tile)).thenCompose(schematic ->
                executors.supplyIo(() -> {
                    worldEdit.writeAtomically(schematic, snapshotFile);
                    try {
                        return new SnapshotArtifact(snapshotFile, Sha256.file(snapshotFile));
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                })
        );
    }

    @Override
    public CompletionStage<Void> apply(PlacementPlan plan, ManifestTile tile, Path schematicFile) {
        return readVerifiedAsync(schematicFile, tile.checksum()).thenCompose(schematic -> dispatcher.run(
                () -> worldEdit.paste(plan, tile, schematic)
        ));
    }

    @Override
    public CompletionStage<Boolean> verify(PlacementPlan plan, ManifestTile tile, Path schematicFile) {
        return readVerifiedAsync(schematicFile, tile.checksum()).thenCompose(schematic -> dispatcher.supply(
                () -> worldEdit.verify(plan, tile, schematic)
        ));
    }

    @Override
    public CompletionStage<Boolean> verifySnapshot(
            PlacementPlan plan, ManifestTile tile, Path snapshotFile, String expectedChecksum
    ) {
        return readVerifiedAsync(snapshotFile, expectedChecksum).thenCompose(schematic -> dispatcher.supply(
                () -> worldEdit.verify(plan, tile, schematic)
        ));
    }

    @Override
    public CompletionStage<Void> restore(
            PlacementPlan plan, ManifestTile tile, Path snapshotFile, String expectedChecksum
    ) {
        return executors.supplyIo(() -> {
            try {
                return worldEdit.readVerified(snapshotFile, expectedChecksum);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }).thenCompose(schematic -> dispatcher.run(() -> worldEdit.paste(plan, tile, schematic)));
    }

    private CompletableFuture<LoadedSchematic> readVerifiedAsync(Path file, String expectedChecksum) {
        return executors.supplyIo(() -> {
            try {
                return worldEdit.readVerified(file, expectedChecksum);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }
}
