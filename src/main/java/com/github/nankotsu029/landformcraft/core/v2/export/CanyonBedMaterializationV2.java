package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.CanyonGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.CanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalFieldSamplerV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * V2-15-12 {@code CANYON} block materialization.
 *
 * <p>A {@link CanyonPlanV2} is a dry landform cross-section carved along an already-frozen
 * {@code MEANDERING_RIVER} reach it shares a bed with (the HARD {@code WITHIN} binding
 * {@link com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.CanyonPlanCompilerV2}
 * requires). Unlike {@link RiverBedMaterializationV2} or {@link LakeBedMaterializationV2} the canyon
 * never owns fluid: it only lowers the ground from the request's flat background surface down to the
 * wall/floor/rim/terrace profile {@link CanyonGeneratorV2} computes, and the exact channel columns the
 * shared river claims are always the river's own to decide (never the canyon's), so the two
 * materializations are composed with the river's decorator wrapping the canyon's — see
 * {@code HydrologyPlanExportPipelineV2#combinedOverlay}.</p>
 *
 * <h2>Responsibility separation ({@code CARVE_SOLID} only)</h2>
 *
 * <p>Per canyon-mask column, {@code CARVE_SOLID} voids
 * {@code (bedYMillionths + wallHeightMillionths, backgroundSurfaceY]} — it removes solid mass only and
 * never introduces a fluid block. At floor distance the wall height is zero, so the carved surface sits
 * exactly at the shared river bed; the river's own {@code ADD_FLUID} then owns the water in the columns
 * it actually claims as channel, and every other floor/wall/rim/terrace column stays dry (air) down to
 * the carved surface.</p>
 *
 * <h2>Declared interactions ({@link InteractionV2})</h2>
 *
 * <p>The canyon never writes the macro land-water medium: that field stays owned by the HARD
 * {@code LAND_WATER_MASK} (ADR 0038). The carved surface is bounded above by the flat background it
 * carves from (a step down at the declared rim is the intended shape, not a leak — a dry landform holds
 * no water to contain).</p>
 *
 * <p>The freeze is pure and bounded: it allocates only the union AABB of the declared corridors plus a
 * rim margin, scans row-major, and uses integer arithmetic only, so the result is independent of
 * locale, timezone, thread count and tile order.</p>
 */
final class CanyonBedMaterializationV2 {
    static final String CONTRACT_VERSION = "canyon-bed-materialization-v1";

    /** The corridor rasterizes to no cell inside the export domain. */
    static final String RULE_EMPTY_CORRIDOR = "v2.canyon.empty-corridor";
    /** A corridor cell is claimed by a coastal surface modifier (the canyon may only carve foundation). */
    static final String RULE_COASTAL_OWNER_CONFLICT = "v2.canyon.coastal-owner-conflict";
    /** A corridor cell is marine: a canyon opening into the sea is not wired. */
    static final String RULE_MARINE_CONTACT = "v2.canyon.marine-contact";
    /** The carved surface rises above the flat background it carves from. */
    static final String RULE_SURFACE_ABOVE_BACKGROUND = "v2.canyon.surface-above-background";
    /** The bed or the carve top leaves the request's vertical bounds. */
    static final String RULE_VERTICAL_BOUNDS = "v2.canyon.vertical-bounds";
    /** The bounded footprint exceeds the materialization budget. */
    static final String RULE_BUDGET = "v2.canyon.materialization-budget";

    /** MEDIUM-scale ceiling: the bounded footprint may never exceed one 1024×1024 domain. */
    static final long MAXIMUM_FOOTPRINT_CELLS = 1_048_576L;

    private static final String AIR = "minecraft:air";

    private CanyonBedMaterializationV2() {
    }

    /** The interaction contracts this version declares and enforces. */
    enum InteractionV2 {
        /** The shared river bed the corridor's floor rests on; the river alone owns any fluid there. */
        SHARED_RIVER_BED(RULE_VERTICAL_BOUNDS),
        /** The carved surface never rises above the un-carved background: a step at the rim, not a leak. */
        BACKGROUND_ENVELOPE(RULE_SURFACE_ABOVE_BACKGROUND),
        /** A coastal modifier's own cells are off limits to the carve. */
        COASTAL_FOUNDATION(RULE_COASTAL_OWNER_CONFLICT),
        /** The macro land-water medium stays owned by the HARD mask; the canyon only changes blocks. */
        MACRO_MEDIUM(RULE_MARINE_CONTACT);

        private final String ruleId;

        InteractionV2(String ruleId) {
            this.ruleId = ruleId;
        }

        String ruleId() {
            return ruleId;
        }
    }

    /**
     * Freezes the bounded canyon corridor field of one export run, before any tile is written. Returns
     * {@link Optional#empty()} when the frozen Blueprint declares no canyon, in which case the block
     * stream stays byte-identical to the coastal/river-only path.
     */
    static Optional<FrozenCanyonBedV2> freeze(
            List<CanyonPlanV2> plans,
            CoastalFieldSamplerV2 fields,
            GenerationRequestV2.Bounds bounds,
            CancellationToken token
    ) throws CanyonMaterializationRejectedV2 {
        Objects.requireNonNull(plans, "plans");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(token, "token");
        if (plans.isEmpty()) {
            return Optional.empty();
        }

        int width = bounds.width();
        int length = bounds.length();
        requireAdmitted(plans, width, length);
        Footprint footprint = Footprint.of(plans, width, length);
        List<CanyonGeneratorV2> generators = plans.stream().map(CanyonGeneratorV2::new).toList();

        int cells = Math.multiplyExact(footprint.width(), footprint.length());
        byte[] corridor = new byte[cells];
        int[] surfaceY = new int[cells];
        int[] bedY = new int[cells];
        int[] carveTopY = new int[cells];
        long corridorCells = 0;
        for (int localZ = 0; localZ < footprint.length(); localZ++) {
            token.throwIfCancellationRequested();
            for (int localX = 0; localX < footprint.width(); localX++) {
                int globalX = footprint.originX() + localX;
                int globalZ = footprint.originZ() + localZ;
                int index = localZ * footprint.width() + localX;
                Claim claim = claimAt(generators, globalX, globalZ);
                if (claim == null) {
                    continue;
                }
                corridor[index] = 1;
                corridorCells++;
                surfaceY[index] = Math.floorDiv(claim.surfaceMillionths(), 1_000_000);
                bedY[index] = Math.floorDiv(claim.bedMillionths(), 1_000_000);
                carveTopY[index] = surfaceYAt(fields, globalX, globalZ);
            }
        }
        if (corridorCells == 0) {
            throw new CanyonMaterializationRejectedV2(RULE_EMPTY_CORRIDOR, plans.getFirst().featureId(), -1, -1,
                    "the declared canyon rasterizes to no corridor cell inside the export domain");
        }

        FrozenCanyonBedV2 frozen = new FrozenCanyonBedV2(footprint, corridor, surfaceY, carveTopY, corridorCells);
        requireBoundedFootprint(frozen, plans.getFirst().featureId(), width, length);
        requireDeclaredInteractions(frozen, plans, bedY, fields, bounds, token);
        return Optional.of(frozen);
    }

    private static void requireBoundedFootprint(
            FrozenCanyonBedV2 frozen, String featureId, int width, int length
    ) throws CanyonMaterializationRejectedV2 {
        Footprint footprint = frozen.footprint();
        for (int localZ = 0; localZ < footprint.length(); localZ++) {
            for (int localX = 0; localX < footprint.width(); localX++) {
                boolean border = localX == 0 || localZ == 0
                        || localX == footprint.width() - 1 || localZ == footprint.length() - 1;
                if (!border) {
                    continue;
                }
                int globalX = footprint.originX() + localX;
                int globalZ = footprint.originZ() + localZ;
                boolean domainEdge = globalX == 0 || globalZ == 0 || globalX == width - 1 || globalZ == length - 1;
                if (!domainEdge && frozen.corridorAt(globalX, globalZ)) {
                    throw new CanyonMaterializationRejectedV2(RULE_BUDGET, featureId, globalX, globalZ,
                            "the corridor raster reaches the bounded footprint border");
                }
            }
        }
    }

    private static void requireDeclaredInteractions(
            FrozenCanyonBedV2 frozen,
            List<CanyonPlanV2> plans,
            int[] bedY,
            CoastalFieldSamplerV2 fields,
            GenerationRequestV2.Bounds bounds,
            CancellationToken token
    ) throws CanyonMaterializationRejectedV2 {
        String featureId = plans.getFirst().featureId();
        Footprint footprint = frozen.footprint();
        for (int localZ = 0; localZ < footprint.length(); localZ++) {
            token.throwIfCancellationRequested();
            for (int localX = 0; localX < footprint.width(); localX++) {
                int x = footprint.originX() + localX;
                int z = footprint.originZ() + localZ;
                int index = localZ * footprint.width() + localX;
                if (frozen.corridor()[index] != 1) {
                    continue;
                }
                int surface = frozen.surfaceYAt(x, z);
                int carveTop = frozen.carveTopYAt(x, z);
                int bed = bedY[index];
                if (bed <= bounds.minY() || carveTop > bounds.maxY()) {
                    throw new CanyonMaterializationRejectedV2(RULE_VERTICAL_BOUNDS, featureId, x, z,
                            "bed " + bed + " / carve top " + carveTop + " leaves the vertical bounds ("
                                    + bounds.minY() + ".." + bounds.maxY() + "]");
                }
                if (surface > carveTop) {
                    throw new CanyonMaterializationRejectedV2(RULE_SURFACE_ABOVE_BACKGROUND, featureId, x, z,
                            "carved surface " + surface + " rises above the background it carves from "
                                    + carveTop);
                }
                if (fields.valueAt(CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, x, z) != 0) {
                    throw new CanyonMaterializationRejectedV2(RULE_COASTAL_OWNER_CONFLICT, featureId, x, z,
                            "the corridor overlaps a coastal surface modifier's cell");
                }
                if (fields.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z) != 1) {
                    throw new CanyonMaterializationRejectedV2(RULE_MARINE_CONTACT, featureId, x, z,
                            "the corridor enters a marine cell; a canyon opening into the sea is not wired");
                }
            }
        }
    }

    private static int surfaceYAt(CoastalFieldSamplerV2 fields, int x, int z) {
        return Math.floorDiv(
                fields.valueAt(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, x, z), 1_000_000);
    }

    /**
     * Merges the declared canyons at one cell. Among corridors the first plan in Blueprint order owns
     * the surface — the same plan-ordered merge {@link RiverBedMaterializationV2} uses.
     */
    private static Claim claimAt(List<CanyonGeneratorV2> generators, int globalX, int globalZ) {
        for (CanyonGeneratorV2 generator : generators) {
            if (globalX < 0 || globalX >= generator.width() || globalZ < 0 || globalZ >= generator.length()) {
                continue;
            }
            CanyonGeneratorV2.CanyonSample sample = generator.sampleAt(globalX, globalZ, index -> false);
            if (sample.canyonMask() == 1) {
                return new Claim(sample.surfaceHeightMillionths(), sample.bedElevationMillionths());
            }
        }
        return null;
    }

    private record Claim(int surfaceMillionths, int bedMillionths) {
    }

    /** Bounded union AABB of the declared corridors plus their rim margin. */
    record Footprint(int originX, int originZ, int width, int length) {
        Footprint {
            if (width <= 0 || length <= 0) {
                throw new IllegalArgumentException("canyon footprint must be non-empty");
            }
        }

        static Footprint of(List<CanyonPlanV2> plans, int domainWidth, int domainLength) {
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (CanyonPlanV2 plan : plans) {
                int margin = 2 + (plan.selectedRimWidthBlocks() + 1) / 2;
                for (MeanderingRiverPlanV2.CenterlineSample sample : plan.centerline()) {
                    int x = Math.toIntExact(Math.floorDiv(sample.xMillionths(), 1_000_000L));
                    int z = Math.toIntExact(Math.floorDiv(sample.zMillionths(), 1_000_000L));
                    minX = Math.min(minX, x - margin);
                    minZ = Math.min(minZ, z - margin);
                    maxX = Math.max(maxX, x + margin);
                    maxZ = Math.max(maxZ, z + margin);
                }
            }
            minX = Math.max(0, minX);
            minZ = Math.max(0, minZ);
            maxX = Math.min(domainWidth - 1, maxX);
            maxZ = Math.min(domainLength - 1, maxZ);
            return new Footprint(minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
        }

        boolean contains(int globalX, int globalZ) {
            return globalX >= originX && globalZ >= originZ
                    && globalX < originX + width && globalZ < originZ + length;
        }

        int indexOf(int globalX, int globalZ) {
            return (globalZ - originZ) * width + (globalX - originX);
        }
    }

    /**
     * The frozen, bounded canyon corridor field of one export run. Immutable after {@link #freeze}: the
     * whole corridor geometry is resolved before the first tile is written, so every tile sees the same
     * corridor and no tile-local re-derivation can drift at a seam.
     */
    static final class FrozenCanyonBedV2 {
        private final Footprint footprint;
        private final byte[] corridor;
        private final int[] surfaceY;
        private final int[] carveTopY;
        private final long corridorCells;

        private FrozenCanyonBedV2(
                Footprint footprint, byte[] corridor, int[] surfaceY, int[] carveTopY, long corridorCells
        ) {
            this.footprint = footprint;
            this.corridor = corridor;
            this.surfaceY = surfaceY;
            this.carveTopY = carveTopY;
            this.corridorCells = corridorCells;
        }

        Footprint footprint() {
            return footprint;
        }

        byte[] corridor() {
            return corridor;
        }

        long corridorCells() {
            return corridorCells;
        }

        /** Blocks the carve voids, summed over the whole corridor. */
        long carvedBlocks() {
            long carved = 0;
            for (int index = 0; index < corridor.length; index++) {
                if (corridor[index] == 1) {
                    carved += carveTopY[index] - surfaceY[index];
                }
            }
            return carved;
        }

        boolean corridorAt(int globalX, int globalZ) {
            return footprint.contains(globalX, globalZ) && corridor[footprint.indexOf(globalX, globalZ)] == 1;
        }

        int surfaceYAt(int globalX, int globalZ) {
            return surfaceY[footprint.indexOf(globalX, globalZ)];
        }

        int carveTopYAt(int globalX, int globalZ) {
            return carveTopY[footprint.indexOf(globalX, globalZ)];
        }

        /**
         * Wraps the canonical coastal resolver with the frozen corridor. {@code CARVE_SOLID} only: the
         * span {@code (surfaceY, carveTopY]} becomes void air. The surface block itself and everything
         * below it fall straight through to the base resolver, so the canyon never re-lines the ground
         * it carves down to, and a downstream river materialization wrapping this one still owns its
         * own channel fluid untouched.
         */
        TerrainBlockResolver decorate(TerrainBlockResolver base) {
            Objects.requireNonNull(base, "base");
            return (x, y, z) -> {
                if (!footprint.contains(x, z)) {
                    return base.blockStateAt(x, y, z);
                }
                int index = footprint.indexOf(x, z);
                if (corridor[index] != 1 || y <= surfaceY[index] || y > carveTopY[index]) {
                    return base.blockStateAt(x, y, z);
                }
                return AIR;
            };
        }

        long estimatedResidentBytes() {
            return Math.multiplyExact((long) corridor.length, 1L + 2L * Integer.BYTES);
        }
    }

    /** Bounded-footprint admission, checked before the freeze allocates anything. */
    static void requireAdmitted(List<CanyonPlanV2> plans, int width, int length)
            throws CanyonMaterializationRejectedV2 {
        if (plans.isEmpty()) {
            return;
        }
        Footprint footprint = Footprint.of(plans, width, length);
        long cells = Math.multiplyExact((long) footprint.width(), (long) footprint.length());
        if (cells > MAXIMUM_FOOTPRINT_CELLS) {
            throw new CanyonMaterializationRejectedV2(RULE_BUDGET, plans.getFirst().featureId(), -1, -1,
                    "bounded canyon footprint " + cells + " cells exceeds the budget of "
                            + MAXIMUM_FOOTPRINT_CELLS);
        }
    }

    /** Stable, ordered list of the declared interactions, for docs and contract tests. */
    static List<String> declaredInteractions() {
        List<String> declared = new ArrayList<>(InteractionV2.values().length);
        for (InteractionV2 interaction : InteractionV2.values()) {
            declared.add(interaction.name() + "=" + interaction.ruleId());
        }
        return List.copyOf(declared);
    }
}
