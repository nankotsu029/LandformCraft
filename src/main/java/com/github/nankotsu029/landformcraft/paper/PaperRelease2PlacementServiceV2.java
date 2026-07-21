package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.LandformErrorCode;
import com.github.nankotsu029.landformcraft.core.LandformException;
import com.github.nankotsu029.landformcraft.core.WorldAccessPolicy;
import com.github.nankotsu029.landformcraft.core.v2.placement.Release2PlacementApplicationServiceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.undo.PlacementUndoPrepareCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.undo.PlacementUndoServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRollbackServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryDiagnosisV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryServiceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryActionV2;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** Explicit Paper adapter for Release 2; no call can dispatch to the v1 placement service. */
public final class PaperRelease2PlacementServiceV2 implements AutoCloseable {
    private static final CancellationToken NEVER = () -> false;
    private final Release2PlacementApplicationServiceV2 application;
    private final WorldAccessPolicy worldAccessPolicy;

    public PaperRelease2PlacementServiceV2(Release2PlacementApplicationServiceV2 application) {
        this(application, new WorldAccessPolicy(List.of(), List.of()));
    }

    public PaperRelease2PlacementServiceV2(
            Release2PlacementApplicationServiceV2 application,
            WorldAccessPolicy worldAccessPolicy
    ) {
        this.application = Objects.requireNonNull(application, "application");
        this.worldAccessPolicy = Objects.requireNonNull(worldAccessPolicy, "worldAccessPolicy");
        if (!application.isRelease2Path()) {
            throw new IllegalArgumentException("Paper adapter requires the explicit Release 2 path");
        }
    }

    public boolean isRelease2Path() {
        return application.isRelease2Path();
    }

    /** Recovery-backed cleanup port for retention wiring (V2-12-10). */
    public com.github.nankotsu029.landformcraft.core.v2.operations.RetentionCleanupPortV2
            retentionCleanupPort() {
        return application.retentionCleanupPort();
    }

    public CompletionStage<Release2PlacementApplicationServiceV2.PreparedPlanV2> plan(
            String releasePath,
            String worldName,
            int minimumX,
            int minimumY,
            int minimumZ,
            PlacementPlanV2.PlacementActorV2 actor
    ) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Release 2 Paper world planning must start on the main thread");
        }
        World world = requireAllowedWorld(worldAccessPolicy, worldName, Bukkit::getWorld);
        var border = world.getWorldBorder();
        double half = border.getSize() / 2.0d;
        int minX = floorBound(border.getCenter().getX() - half);
        int maxX = ceilBound(border.getCenter().getX() + half) - 1;
        int minZ = floorBound(border.getCenter().getZ() - half);
        int maxZ = ceilBound(border.getCenter().getZ() + half) - 1;
        WorldAabbV2 allowed = new WorldAabbV2(
                minX, world.getMinHeight(), minZ,
                maxX, world.getMaxHeight() - 1, maxZ);
        return application.plan(new Release2PlacementApplicationServiceV2.PlanRequestV2(
                releasePath, world.getUID(), world.getName(), minimumX, minimumY, minimumZ,
                allowed, actor, NEVER));
    }

    public CompletionStage<Release2PlacementApplicationServiceV2.ConfirmedPlanV2> confirm(
            UUID placementId,
            String token,
            PlacementPlanV2.PlacementActorV2 actor
    ) {
        return application.confirm(new Release2PlacementApplicationServiceV2.ConfirmRequestV2(
                placementId, token, actor, NEVER));
    }

    public CompletionStage<Release2PlacementApplicationServiceV2.ExecutionResultV2> execute(
            UUID placementId,
            PlacementPlanV2.PlacementActorV2 actor
    ) {
        return application.execute(new Release2PlacementApplicationServiceV2.ExecuteRequestV2(
                placementId, actor, NEVER));
    }

    public boolean allowsWorld(String worldName) {
        return worldAccessPolicy.allows(worldName);
    }

    static World requireAllowedWorld(
            WorldAccessPolicy policy,
            String worldName,
            Function<String, World> worldLookup
    ) {
        Objects.requireNonNull(policy, "policy").requireAllowed(worldName);
        World world = Objects.requireNonNull(worldLookup, "worldLookup").apply(worldName);
        if (world == null) {
            throw new LandformException(
                    LandformErrorCode.NOT_FOUND,
                    "The requested Release 2 target world does not exist.",
                    "release2-placement-plan",
                    worldName,
                    "world-lookup",
                    "Use an existing allowed test world and retry.");
        }
        return world;
    }

    public CompletionStage<PlacementJournalV2> status(UUID placementId) {
        return application.status(placementId);
    }

    public CompletionStage<PlacementUndoPrepareCompilerV2.PreparedUndoV2> prepareUndo(
            UUID placementId,
            PlacementPlanV2.PlacementActorV2 actor
    ) {
        return application.prepareUndo(placementId, actor);
    }

    public CompletionStage<PlacementUndoServiceV2.UndoResultV2> executeUndo(
            UUID placementId,
            String token,
            PlacementPlanV2.PlacementActorV2 actor
    ) {
        return application.executeUndo(placementId, token, actor, NEVER);
    }

    public CompletionStage<PlacementRecoveryDiagnosisV2> diagnoseRecovery(UUID placementId) {
        return application.diagnoseRecovery(placementId, NEVER);
    }

    public CompletionStage<PlacementRecoveryServiceV2.PreparedRecoveryV2> prepareRecovery(
            UUID placementId,
            PlacementRecoveryActionV2 action,
            PlacementPlanV2.PlacementActorV2 actor
    ) {
        return application.prepareRecovery(placementId, action, actor, NEVER);
    }

    public CompletionStage<PlacementRollbackServiceV2.RollbackResultV2> executeRecoveryRollback(
            UUID placementId,
            String token,
            PlacementPlanV2.PlacementActorV2 actor
    ) {
        return application.executeRecoveryRollback(placementId, token, actor, NEVER);
    }

    public CompletionStage<PlacementRecoveryServiceV2.AcceptResultV2> executeRecoveryAccept(
            UUID placementId,
            String token,
            PlacementPlanV2.PlacementActorV2 actor
    ) {
        return application.executeRecoveryAccept(placementId, token, actor, NEVER);
    }

    public CompletionStage<List<PlacementJournalV2>> inspectRestartState() {
        return application.inspectRestartState();
    }

    @Override
    public void close() {
        application.close();
    }

    private static int floorBound(double value) {
        if (!Double.isFinite(value) || value <= Integer.MIN_VALUE || value >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("world border cannot be represented safely");
        }
        return (int) Math.floor(value);
    }

    private static int ceilBound(double value) {
        if (!Double.isFinite(value) || value <= Integer.MIN_VALUE || value >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("world border cannot be represented safely");
        }
        return (int) Math.ceil(value);
    }
}
