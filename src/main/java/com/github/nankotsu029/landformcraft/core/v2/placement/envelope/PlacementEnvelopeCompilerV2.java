package com.github.nankotsu029.landformcraft.core.v2.placement.envelope;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPhysicsClassV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles a sealed mutation／effect envelope from a sealed {@link PlacementPlanV2} and
 * per-tile physics-sensitive content AABBs. Does not reserve regions, snapshot, or apply.
 */
public final class PlacementEnvelopeCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public CompiledEnvelopeV2 compile(EnvelopeCompileRequestV2 request) {
        Objects.requireNonNull(request, "request");
        PlacementPlanV2 plan = request.placementPlan();
        if (plan.envelopeReferences().bound()) {
            throw new PlacementEnvelopeExceptionV2(
                    PlacementEnvelopeFailureCodeV2.PLACEMENT_PLAN_BINDING_MISMATCH,
                    "placement plan envelope refs are already bound");
        }
        if (request.tileContents().size() != plan.tileOrder().tiles().size()) {
            throw new PlacementEnvelopeExceptionV2(
                    PlacementEnvelopeFailureCodeV2.ENVELOPE_COUNT_EXCEEDED,
                    "tile content count must match placement tile order");
        }
        if (request.tileContents().size() > request.budget().maximumTiles()) {
            throw new PlacementEnvelopeExceptionV2(
                    PlacementEnvelopeFailureCodeV2.ENVELOPE_COUNT_EXCEEDED,
                    "tile content count exceeds envelope budget");
        }

        PlacementEnvelopePlanV2.PhysicsPolicy policy = request.physicsPolicy();
        List<PlacementEnvelopePlanV2.TileEnvelopeV2> tiles = new ArrayList<>(request.tileContents().size());
        WorldAabbV2 unionMutation = null;
        WorldAabbV2 unionEffect = null;
        int originX = plan.target().minimumX();
        int originZ = plan.target().minimumZ();

        for (int i = 0; i < request.tileContents().size(); i++) {
            TileMutationContentV2 content = request.tileContents().get(i);
            PlacementPlanV2.TileRefV2 tileRef = plan.tileOrder().tiles().get(i);
            if (content.tileIndex() != tileRef.tileIndex() || !content.tileId().equals(tileRef.tileId())) {
                throw new PlacementEnvelopeExceptionV2(
                        PlacementEnvelopeFailureCodeV2.TILE_ORDER_MISMATCH,
                        "tile content order must match placement tile order");
            }
            Set<PlacementPhysicsClassV2> classes = normalizePhysics(content.physicsClasses());
            WorldAabbV2 tileCore = WorldAabbV2.fromTileCore(
                    tileRef,
                    content.mutationAabb().minY(),
                    content.mutationAabb().maxY(),
                    originX,
                    originZ);
            if (!tileCore.intersects(content.mutationAabb())
                    || content.mutationAabb().minX() < tileCore.minX()
                    || content.mutationAabb().maxX() > tileCore.maxX()
                    || content.mutationAabb().minZ() < tileCore.minZ()
                    || content.mutationAabb().maxZ() > tileCore.maxZ()) {
                throw new PlacementEnvelopeExceptionV2(
                        PlacementEnvelopeFailureCodeV2.MUTATION_OUTSIDE_TILE_CORE,
                        "mutation AABB must lie inside tile core XZ");
            }

            WorldAabbV2 mutation = content.mutationAabb();
            PlacementEnvelopePlanV2.ExpansionRadii radii = policy.radiiFor(classes);
            WorldAabbV2 effect;
            try {
                effect = mutation.expand(radii.radiusXZ(), radii.radiusUp(), radii.radiusDown());
            } catch (ArithmeticException overflow) {
                throw new PlacementEnvelopeExceptionV2(
                        PlacementEnvelopeFailureCodeV2.Y_OVERFLOW,
                        "effect envelope expansion overflowed int bounds");
            }
            if (!request.allowedWorldBounds().contains(effect)) {
                throw new PlacementEnvelopeExceptionV2(
                        PlacementEnvelopeFailureCodeV2.WORLD_BOUNDS_OVERFLOW,
                        "effect envelope exceeds allowed world bounds");
            }

            tiles.add(new PlacementEnvelopePlanV2.TileEnvelopeV2(
                    content.tileId(),
                    content.tileIndex(),
                    List.copyOf(classes),
                    mutation,
                    effect));
            unionMutation = unionMutation == null ? mutation : unionMutation.union(mutation);
            unionEffect = unionEffect == null ? effect : unionEffect.union(effect);
        }

        long mutationVolume = unionMutation.volumeBlocks();
        long effectVolume = unionEffect.volumeBlocks();
        if (effectVolume > request.budget().maximumEffectVolumeBlocks()) {
            throw new PlacementEnvelopeExceptionV2(
                    PlacementEnvelopeFailureCodeV2.EFFECT_VOLUME_EXCEEDED,
                    "union effect volume exceeds budget");
        }
        long snapshotBytes = Math.multiplyExact(effectVolume, PlacementEnvelopePlanV2.BYTES_PER_SNAPSHOT_BLOCK);
        long safetyMargin = Math.max(4_096L, snapshotBytes / 10L);
        long totalBytes = Math.addExact(snapshotBytes, safetyMargin);
        if (totalBytes > request.budget().maximumDiskEstimateBytes()) {
            throw new PlacementEnvelopeExceptionV2(
                    PlacementEnvelopeFailureCodeV2.DISK_ESTIMATE_EXCEEDED,
                    "envelope disk estimate exceeds budget");
        }

        PlacementEnvelopePlanV2 draft = new PlacementEnvelopePlanV2(
                PlacementEnvelopePlanV2.VERSION,
                PlacementEnvelopePlanV2.ENVELOPE_CONTRACT_VERSION,
                new PlacementEnvelopePlanV2.PlacementPlanBinding(
                        PlacementEnvelopePlanV2.PlacementPlanBinding.VERSION,
                        plan.canonicalChecksum(),
                        tiles.size(),
                        PlacementEnvelopePlanV2.PlacementPlanBinding.CONTRACT_VERSION),
                policy,
                request.allowedWorldBounds(),
                tiles,
                unionMutation,
                unionEffect,
                new PlacementEnvelopePlanV2.DiskEstimate(
                        mutationVolume, effectVolume, snapshotBytes, safetyMargin, totalBytes),
                request.budget(),
                PlacementPlanV2.UNBOUND_CHECKSUM,
                PlacementPlanV2.UNBOUND_CHECKSUM);
        PlacementEnvelopePlanV2 sealed = codec.sealPlacementEnvelopePlan(draft);
        sealed.requirePlacementPlan(plan);

        PlacementPlanV2 rebound = codec.sealPlacementPlan(plan.withEnvelopeReferences(
                PlacementPlanV2.EnvelopeReferencesV2.bound(
                        sealed.mutationEnvelopeChecksum(),
                        sealed.canonicalChecksum())));
        return new CompiledEnvelopeV2(sealed, rebound);
    }

    /** Independent oracle used by tests to detect under-approximation. */
    public PlacementEnvelopePlanV2 oracle(EnvelopeCompileRequestV2 request) {
        return compile(request).envelopePlan();
    }

    private static Set<PlacementPhysicsClassV2> normalizePhysics(List<PlacementPhysicsClassV2> classes) {
        Objects.requireNonNull(classes, "physicsClasses");
        if (classes.isEmpty()) {
            throw new PlacementEnvelopeExceptionV2(
                    PlacementEnvelopeFailureCodeV2.UNKNOWN_PHYSICS_CLASS,
                    "physics class set must not be empty");
        }
        EnumSet<PlacementPhysicsClassV2> set = EnumSet.noneOf(PlacementPhysicsClassV2.class);
        for (PlacementPhysicsClassV2 physicsClass : classes) {
            if (physicsClass == null) {
                throw new PlacementEnvelopeExceptionV2(
                        PlacementEnvelopeFailureCodeV2.UNKNOWN_PHYSICS_CLASS,
                        "unknown physics class");
            }
            set.add(physicsClass);
        }
        return set;
    }

    public record TileMutationContentV2(
            String tileId,
            int tileIndex,
            WorldAabbV2 mutationAabb,
            List<PlacementPhysicsClassV2> physicsClasses
    ) {
        public TileMutationContentV2 {
            Objects.requireNonNull(tileId, "tileId");
            Objects.requireNonNull(mutationAabb, "mutationAabb");
            Objects.requireNonNull(physicsClasses, "physicsClasses");
        }
    }

    public record EnvelopeCompileRequestV2(
            PlacementPlanV2 placementPlan,
            WorldAabbV2 allowedWorldBounds,
            PlacementEnvelopePlanV2.PhysicsPolicy physicsPolicy,
            PlacementEnvelopePlanV2.ResourceBudget budget,
            List<TileMutationContentV2> tileContents
    ) {
        public EnvelopeCompileRequestV2 {
            Objects.requireNonNull(placementPlan, "placementPlan");
            Objects.requireNonNull(allowedWorldBounds, "allowedWorldBounds");
            Objects.requireNonNull(physicsPolicy, "physicsPolicy");
            Objects.requireNonNull(budget, "budget");
            Objects.requireNonNull(tileContents, "tileContents");
            tileContents = List.copyOf(tileContents);
        }
    }

    public record CompiledEnvelopeV2(PlacementEnvelopePlanV2 envelopePlan, PlacementPlanV2 boundPlacementPlan) {
        public CompiledEnvelopeV2 {
            Objects.requireNonNull(envelopePlan, "envelopePlan");
            Objects.requireNonNull(boundPlacementPlan, "boundPlacementPlan");
            if (!boundPlacementPlan.envelopeReferences().bound()) {
                throw new IllegalArgumentException("bound placement plan must have bound envelope refs");
            }
        }
    }
}
