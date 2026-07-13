package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.model.ManifestTile;
import com.github.nankotsu029.landformcraft.model.PlacementPlan;
import com.github.nankotsu029.landformcraft.model.WorldDescriptor;

import java.nio.file.Path;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

/** World mutation boundary. Paper implementations must dispatch every world read/write through the scheduler. */
public interface PlacementWorldGateway {
    CompletionStage<WorldDescriptor> describeWorld(String worldName);

    CompletionStage<SnapshotArtifact> snapshot(
            PlacementPlan plan, ManifestTile tile, Path snapshotFile
    );

    CompletionStage<Void> apply(
            PlacementPlan plan, ManifestTile tile, Path schematicFile
    );

    CompletionStage<Boolean> verify(
            PlacementPlan plan, ManifestTile tile, Path schematicFile
    );

    /** Compares the current world against a checksum-verified snapshot schematic. */
    default CompletionStage<Boolean> verifySnapshot(
            PlacementPlan plan,
            ManifestTile tile,
            Path snapshotFile,
            String expectedChecksum
    ) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("snapshot verification is unavailable"));
    }

    CompletionStage<Void> restore(
            PlacementPlan plan, ManifestTile tile, Path snapshotFile, String expectedChecksum
    );
}
