package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.model.ManifestTile;
import com.github.nankotsu029.landformcraft.model.PlacementPlan;
import com.github.nankotsu029.landformcraft.model.WorldDescriptor;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Placement gateway decorator enforcing the startup-captured world policy on every operation. */
public final class PolicyPlacementWorldGateway implements PlacementWorldGateway {
    private final PlacementWorldGateway delegate;
    private final WorldAccessPolicy policy;

    public PolicyPlacementWorldGateway(PlacementWorldGateway delegate, WorldAccessPolicy policy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public CompletionStage<WorldDescriptor> describeWorld(String worldName) {
        policy.requireAllowed(worldName);
        return delegate.describeWorld(worldName);
    }

    @Override
    public CompletionStage<SnapshotArtifact> snapshot(PlacementPlan plan, ManifestTile tile, Path snapshotFile) {
        policy.requireAllowed(plan.worldName());
        return delegate.snapshot(plan, tile, snapshotFile);
    }

    @Override
    public CompletionStage<Void> apply(PlacementPlan plan, ManifestTile tile, Path schematicFile) {
        policy.requireAllowed(plan.worldName());
        return delegate.apply(plan, tile, schematicFile);
    }

    @Override
    public CompletionStage<Boolean> verify(PlacementPlan plan, ManifestTile tile, Path schematicFile) {
        policy.requireAllowed(plan.worldName());
        return delegate.verify(plan, tile, schematicFile);
    }

    @Override
    public CompletionStage<Boolean> verifySnapshot(
            PlacementPlan plan, ManifestTile tile, Path snapshotFile, String expectedChecksum
    ) {
        policy.requireAllowed(plan.worldName());
        return delegate.verifySnapshot(plan, tile, snapshotFile, expectedChecksum);
    }

    @Override
    public CompletionStage<Void> restore(
            PlacementPlan plan, ManifestTile tile, Path snapshotFile, String expectedChecksum
    ) {
        policy.requireAllowed(plan.worldName());
        return delegate.restore(plan, tile, snapshotFile, expectedChecksum);
    }
}
