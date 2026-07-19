package com.github.nankotsu029.landformcraft.model.v2.placement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable Release 2 mutation／effect envelope plan (V2-6-02). Conservatively expands
 * per-tile mutation AABBs by frozen fluid／gravity／neighbor support radii into a union
 * effect envelope with overflow-safe world bounds and disk／volume admission.
 */
public record PlacementEnvelopePlanV2(
        int planVersion,
        String envelopeContractVersion,
        PlacementPlanBinding placementPlanBinding,
        PhysicsPolicy physicsPolicy,
        WorldAabbV2 allowedWorldBounds,
        List<TileEnvelopeV2> tiles,
        WorldAabbV2 unionMutationEnvelope,
        WorldAabbV2 unionEffectEnvelope,
        DiskEstimate diskEstimate,
        ResourceBudget budget,
        String mutationEnvelopeChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String ENVELOPE_CONTRACT_VERSION = "release-2-placement-envelope-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 256L * 1024L;
    public static final int MAXIMUM_TILES = PlacementPlanV2.MAXIMUM_TILES;
    public static final long BYTES_PER_SNAPSHOT_BLOCK = 16L;

    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public PlacementEnvelopePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("placement envelope planVersion must be 1");
        }
        envelopeContractVersion = nonBlank(envelopeContractVersion, "envelopeContractVersion", 64);
        if (!ENVELOPE_CONTRACT_VERSION.equals(envelopeContractVersion)) {
            throw new IllegalArgumentException("unknown placement envelope contract version");
        }
        Objects.requireNonNull(placementPlanBinding, "placementPlanBinding");
        Objects.requireNonNull(physicsPolicy, "physicsPolicy");
        Objects.requireNonNull(allowedWorldBounds, "allowedWorldBounds");
        Objects.requireNonNull(tiles, "tiles");
        if (tiles.isEmpty() || tiles.size() > MAXIMUM_TILES) {
            throw new IllegalArgumentException("placement envelope tile count out of range");
        }
        tiles = List.copyOf(tiles);
        Objects.requireNonNull(unionMutationEnvelope, "unionMutationEnvelope");
        Objects.requireNonNull(unionEffectEnvelope, "unionEffectEnvelope");
        Objects.requireNonNull(diskEstimate, "diskEstimate");
        Objects.requireNonNull(budget, "budget");
        mutationEnvelopeChecksum = checksum(mutationEnvelopeChecksum, "mutationEnvelopeChecksum");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateTiles(tiles, unionMutationEnvelope, unionEffectEnvelope, allowedWorldBounds, budget);
        if (!allowedWorldBounds.contains(unionEffectEnvelope)) {
            throw new IllegalArgumentException("union effect envelope exceeds allowed world bounds");
        }
        if (!unionEffectEnvelope.contains(unionMutationEnvelope)) {
            throw new IllegalArgumentException("effect envelope must contain mutation envelope");
        }
        if (tiles.size() > budget.maximumTiles()
                || unionEffectEnvelope.volumeBlocks() > budget.maximumEffectVolumeBlocks()
                || diskEstimate.totalBytes() > budget.maximumDiskEstimateBytes()) {
            throw new IllegalArgumentException("placement envelope exceeds resource budget");
        }
    }

    public PlacementEnvelopePlanV2 withChecksums(String mutationChecksum, String effectChecksum) {
        return new PlacementEnvelopePlanV2(
                planVersion,
                envelopeContractVersion,
                placementPlanBinding,
                physicsPolicy,
                allowedWorldBounds,
                tiles,
                unionMutationEnvelope,
                unionEffectEnvelope,
                diskEstimate,
                budget,
                mutationChecksum,
                effectChecksum);
    }

    public void requirePlacementPlan(PlacementPlanV2 plan) {
        Objects.requireNonNull(plan, "plan");
        if (!placementPlanBinding.sourcePlacementPlanChecksum().equals(plan.canonicalChecksum())
                || placementPlanBinding.tileCount() != plan.tileOrder().tiles().size()) {
            throw new IllegalArgumentException("placement envelope plan binding mismatch");
        }
        for (int i = 0; i < tiles.size(); i++) {
            PlacementPlanV2.TileRefV2 expected = plan.tileOrder().tiles().get(i);
            TileEnvelopeV2 actual = tiles.get(i);
            if (actual.tileIndex() != expected.tileIndex() || !actual.tileId().equals(expected.tileId())) {
                throw new IllegalArgumentException("placement envelope tile order mismatch");
            }
        }
    }

    /** Fails closed when this plan under-approximates an independent oracle. */
    public void requireContainsOracle(PlacementEnvelopePlanV2 oracle) {
        Objects.requireNonNull(oracle, "oracle");
        if (tiles.size() != oracle.tiles().size()) {
            throw new IllegalArgumentException("envelope oracle tile count mismatch");
        }
        for (int i = 0; i < tiles.size(); i++) {
            TileEnvelopeV2 actual = tiles.get(i);
            TileEnvelopeV2 expected = oracle.tiles().get(i);
            if (!actual.mutationAabb().contains(expected.mutationAabb())
                    || !actual.effectAabb().contains(expected.effectAabb())) {
                throw new IllegalArgumentException("envelope under-approximates tile oracle");
            }
        }
        if (!unionMutationEnvelope.contains(oracle.unionMutationEnvelope())
                || !unionEffectEnvelope.contains(oracle.unionEffectEnvelope())) {
            throw new IllegalArgumentException("envelope under-approximates union oracle");
        }
    }

    public record PlacementPlanBinding(
            int bindingVersion,
            String sourcePlacementPlanChecksum,
            int tileCount,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "release-2-placement-envelope-plan-binding-v1";

        public PlacementPlanBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown placement envelope plan binding");
            }
            sourcePlacementPlanChecksum = checksum(sourcePlacementPlanChecksum, "sourcePlacementPlanChecksum");
            if (UNBOUND.equals(sourcePlacementPlanChecksum)) {
                throw new IllegalArgumentException("placement envelope requires sealed placement plan");
            }
            if (tileCount < 1 || tileCount > MAXIMUM_TILES) {
                throw new IllegalArgumentException("placement envelope tileCount out of range");
            }
        }

        private static final String UNBOUND = PlacementPlanV2.UNBOUND_CHECKSUM;
    }

    /**
     * Version-frozen support radii. Changing values is a contract version bump.
     * Gravity expands downward only; fluid／neighbor expand symmetrically on declared axes.
     */
    public record PhysicsPolicy(
            String policyVersion,
            int fluidSupportRadiusXZ,
            int fluidSupportRadiusY,
            int gravityFallBlocks,
            int neighborUpdateRadius
    ) {
        public static final String VERSION = "release-2-placement-physics-policy-v1";
        public static final int MAX_RADIUS = 256;

        public PhysicsPolicy {
            policyVersion = nonBlank(policyVersion, "policyVersion", 64);
            if (!VERSION.equals(policyVersion)) {
                throw new IllegalArgumentException("unknown placement physics policy version");
            }
            if (fluidSupportRadiusXZ < 0 || fluidSupportRadiusXZ > MAX_RADIUS
                    || fluidSupportRadiusY < 0 || fluidSupportRadiusY > MAX_RADIUS
                    || gravityFallBlocks < 0 || gravityFallBlocks > MAX_RADIUS
                    || neighborUpdateRadius < 0 || neighborUpdateRadius > MAX_RADIUS) {
                throw new IllegalArgumentException("physics support radii out of range");
            }
        }

        public static PhysicsPolicy standard() {
            return new PhysicsPolicy(VERSION, 2, 2, 64, 1);
        }

        public ExpansionRadii radiiFor(Set<PlacementPhysicsClassV2> classes) {
            Objects.requireNonNull(classes, "classes");
            if (classes.isEmpty()) {
                throw new IllegalArgumentException("physics class set must not be empty");
            }
            int xz = 0;
            int up = 0;
            int down = 0;
            for (PlacementPhysicsClassV2 physicsClass : classes) {
                switch (physicsClass) {
                    case SOLID, AIR -> {
                        // mutation-only; no effect expansion
                    }
                    case FLUID -> {
                        xz = Math.max(xz, fluidSupportRadiusXZ);
                        up = Math.max(up, fluidSupportRadiusY);
                        down = Math.max(down, fluidSupportRadiusY);
                    }
                    case GRAVITY -> down = Math.max(down, gravityFallBlocks);
                    case NEIGHBOR -> {
                        xz = Math.max(xz, neighborUpdateRadius);
                        up = Math.max(up, neighborUpdateRadius);
                        down = Math.max(down, neighborUpdateRadius);
                    }
                }
            }
            return new ExpansionRadii(xz, up, down);
        }
    }

    public record ExpansionRadii(int radiusXZ, int radiusUp, int radiusDown) {
        public ExpansionRadii {
            if (radiusXZ < 0 || radiusUp < 0 || radiusDown < 0) {
                throw new IllegalArgumentException("expansion radii must be non-negative");
            }
        }
    }

    public record TileEnvelopeV2(
            String tileId,
            int tileIndex,
            List<PlacementPhysicsClassV2> physicsClasses,
            WorldAabbV2 mutationAabb,
            WorldAabbV2 effectAabb
    ) {
        public TileEnvelopeV2 {
            tileId = nonBlank(tileId, "tileId", 64);
            if (tileIndex < 0 || tileIndex >= MAXIMUM_TILES) {
                throw new IllegalArgumentException("tileIndex out of range");
            }
            Objects.requireNonNull(physicsClasses, "physicsClasses");
            if (physicsClasses.isEmpty() || physicsClasses.size() > PlacementPhysicsClassV2.values().length) {
                throw new IllegalArgumentException("physicsClasses size out of range");
            }
            EnumSet<PlacementPhysicsClassV2> seen = EnumSet.noneOf(PlacementPhysicsClassV2.class);
            List<PlacementPhysicsClassV2> sorted = new ArrayList<>(physicsClasses.size());
            for (PlacementPhysicsClassV2 physicsClass : physicsClasses) {
                Objects.requireNonNull(physicsClass, "physicsClasses");
                if (!seen.add(physicsClass)) {
                    throw new IllegalArgumentException("duplicate physics class");
                }
                sorted.add(physicsClass);
            }
            sorted.sort(Comparator.naturalOrder());
            physicsClasses = List.copyOf(sorted);
            Objects.requireNonNull(mutationAabb, "mutationAabb");
            Objects.requireNonNull(effectAabb, "effectAabb");
            if (!effectAabb.contains(mutationAabb)) {
                throw new IllegalArgumentException("tile effect AABB must contain mutation AABB");
            }
        }

        public Set<PlacementPhysicsClassV2> physicsClassSet() {
            return EnumSet.copyOf(physicsClasses);
        }
    }

    public record DiskEstimate(
            long mutationVolumeBlocks,
            long effectVolumeBlocks,
            long snapshotBytes,
            long safetyMarginBytes,
            long totalBytes
    ) {
        public DiskEstimate {
            if (mutationVolumeBlocks < 1 || effectVolumeBlocks < 1
                    || snapshotBytes < 1 || safetyMarginBytes < 0 || totalBytes < 1
                    || effectVolumeBlocks < mutationVolumeBlocks
                    || totalBytes < snapshotBytes) {
                throw new IllegalArgumentException("invalid envelope disk estimate");
            }
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumTiles,
            long maximumEffectVolumeBlocks,
            long maximumDiskEstimateBytes,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "release-2-placement-envelope-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown placement envelope budget version");
            }
            if (maximumTiles < 1 || maximumTiles > MAXIMUM_TILES
                    || maximumEffectVolumeBlocks < 1
                    || maximumDiskEstimateBytes < 1
                    || estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || estimatedCanonicalBytes > maximumCanonicalBytes) {
                throw new IllegalArgumentException("invalid placement envelope budget");
            }
        }
    }

    private static void validateTiles(
            List<TileEnvelopeV2> tiles,
            WorldAabbV2 unionMutation,
            WorldAabbV2 unionEffect,
            WorldAabbV2 allowedWorldBounds,
            ResourceBudget budget
    ) {
        Set<String> ids = new HashSet<>();
        WorldAabbV2 computedMutation = null;
        WorldAabbV2 computedEffect = null;
        for (int i = 0; i < tiles.size(); i++) {
            TileEnvelopeV2 tile = Objects.requireNonNull(tiles.get(i), "tiles[" + i + "]");
            if (tile.tileIndex() != i) {
                throw new IllegalArgumentException("envelope tiles must be canonical index order");
            }
            if (!ids.add(tile.tileId())) {
                throw new IllegalArgumentException("duplicate envelope tile id");
            }
            if (!allowedWorldBounds.contains(tile.effectAabb())) {
                throw new IllegalArgumentException("tile effect AABB exceeds allowed world bounds");
            }
            computedMutation = computedMutation == null
                    ? tile.mutationAabb()
                    : computedMutation.union(tile.mutationAabb());
            computedEffect = computedEffect == null
                    ? tile.effectAabb()
                    : computedEffect.union(tile.effectAabb());
        }
        if (!computedMutation.equals(unionMutation) || !computedEffect.equals(unionEffect)) {
            throw new IllegalArgumentException("union envelopes must equal tile AABB unions");
        }
        if (tiles.size() > budget.maximumTiles()) {
            throw new IllegalArgumentException("envelope tile count exceeds budget");
        }
    }

    private static String checksum(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase sha-256 hex digest");
        }
        return value;
    }

    private static String nonBlank(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and <= " + max);
        }
        return value;
    }
}
