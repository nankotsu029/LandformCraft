package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.WaterfallGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.WaterfallPlanV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalFieldSamplerV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * V2-15-13 {@code WATERFALL} block materialization: the plunge basin at the foot of the fall.
 *
 * <p>A {@link WaterfallPlanV2} is compiled against a HARD {@code ON_PATH_OF} {@code MEANDERING_RIVER}
 * binding ({@link com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall
 * .WaterfallPlanCompilerV2}), so a waterfall never exists without a host river whose own channel is
 * already frozen by {@link RiverBedMaterializationV2} in the same run. This stage materializes the
 * part of the fall the 2.5D surface owns — the plunge basin cut <em>below</em> the host bed and the
 * water that basin holds — and deliberately leaves the host's channel columns to the host.</p>
 *
 * <h2>Responsibility separation ({@code CARVE_SOLID} / {@code ADD_FLUID})</h2>
 *
 * <p>Per basin column, in this order:</p>
 * <ol>
 *   <li>{@code CARVE_SOLID} voids {@code (plungePoolFloorY, backgroundSurfaceY]} — it removes solid
 *       mass only and never introduces a fluid block;</li>
 *   <li>{@code ADD_FLUID} owns every water block, filling {@code (plungePoolFloorY, baseBedY]}
 *       strictly inside the void the carve just produced.</li>
 * </ol>
 * <p>The pool floor block itself and everything below it are left to the coastal surface resolver, so
 * the waterfall owns no material re-lining and no bedrock.</p>
 *
 * <h2>Declared interactions ({@link InteractionV2})</h2>
 *
 * <p>{@code HOST_CHANNEL} — the basin footprint excludes every cell the host river's frozen channel
 * claims, so the two materializations are disjoint by construction and neither overrides the other.
 * {@code CREST_HALF_PLANE} — the basin is clipped to the downstream half-plane at the lip crest, so
 * the upstream reach the fall pours from is never undercut. {@code BASIN_ENVELOPE} — every
 * off-basin, off-channel neighbour is the containment surface and must stand at or above the pool's
 * water surface; the boundary the basin shares with the host channel is the opening the fall arrives
 * through and is excluded from containment by design (the host bed sits a full drop above the pool
 * water surface, so the pool still cannot rise into it). {@code COASTAL_FOUNDATION} — a coastal
 * modifier's cells are off limits. {@code MACRO_MEDIUM} — the macro land-water medium stays owned by
 * the HARD {@code LAND_WATER_MASK} (ADR 0038); the waterfall only changes blocks.</p>
 *
 * <p>The falling water column and the behind-fall cavity are <em>not</em> in this stage: they are the
 * bounded sparse volume of {@code WaterfallVolumePlanV2}, which needs the {@code sparse-volume}
 * capability prefix and is wired by its own Task.</p>
 *
 * <p>The freeze is pure and bounded: it allocates only the union AABB of the declared plunge basins,
 * scans row-major, and uses integer arithmetic only, so the result is independent of locale,
 * timezone, thread count and tile order.</p>
 */
final class WaterfallBasinMaterializationV2 {
    static final String CONTRACT_VERSION = "waterfall-basin-materialization-v1";

    /** The host river's channel is not frozen; the basin cannot be separated from it. */
    static final String RULE_HOST_CHANNEL_NOT_FROZEN = "v2.waterfall.host-channel-not-frozen";
    /** The basin rasterizes to no cell inside the export domain. */
    static final String RULE_EMPTY_BASIN = "v2.waterfall.empty-basin";
    /** A basin cell is claimed by a coastal surface modifier (the fall may only cut foundation). */
    static final String RULE_COASTAL_OWNER_CONFLICT = "v2.waterfall.coastal-owner-conflict";
    /** A basin cell or its containment ring is marine: a fall into the sea is not wired. */
    static final String RULE_MARINE_CONTACT = "v2.waterfall.marine-contact";
    /** The pool water would not be contained by the surrounding terrain. */
    static final String RULE_LEAK_ENVELOPE = "v2.waterfall.leak-envelope";
    /** The pool floor or the carve top leaves the request's vertical bounds. */
    static final String RULE_VERTICAL_BOUNDS = "v2.waterfall.vertical-bounds";
    /** The bounded footprint exceeds the materialization budget. */
    static final String RULE_BUDGET = "v2.waterfall.materialization-budget";

    /** MEDIUM-scale ceiling: the bounded footprint may never exceed one 1024×1024 domain. */
    static final long MAXIMUM_FOOTPRINT_CELLS = 1_048_576L;

    private static final int SCALE = 1_000_000;
    private static final String AIR = "minecraft:air";
    private static final String WATER = "minecraft:water";

    private WaterfallBasinMaterializationV2() {
    }

    /** The interaction contracts this version declares and enforces. */
    enum InteractionV2 {
        /** The host river's frozen channel keeps its own columns; the basin excludes every one of them. */
        HOST_CHANNEL(RULE_HOST_CHANNEL_NOT_FROZEN),
        /** The basin is clipped downstream of the lip crest; the upstream reach is never undercut. */
        CREST_HALF_PLANE(RULE_EMPTY_BASIN),
        /** The off-basin, off-channel ring contains the pool water at or above its surface. */
        BASIN_ENVELOPE(RULE_LEAK_ENVELOPE),
        /** A coastal modifier's own cells are off limits to the carve. */
        COASTAL_FOUNDATION(RULE_COASTAL_OWNER_CONFLICT),
        /** The macro land-water medium stays owned by the HARD mask; the fall only changes blocks. */
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
     * Freezes the bounded plunge basin field of one export run, after the host river bed freeze and
     * before any tile is written. Returns {@link Optional#empty()} when the frozen Blueprint declares
     * no waterfall, in which case the block stream stays byte-identical to the path without it.
     */
    static Optional<FrozenWaterfallBasinV2> freeze(
            List<WaterfallPlanV2> plans,
            Optional<RiverBedMaterializationV2.FrozenRiverBedV2> hostBed,
            CoastalFieldSamplerV2 fields,
            GenerationRequestV2.Bounds bounds,
            CancellationToken token
    ) throws WaterfallMaterializationRejectedV2 {
        Objects.requireNonNull(plans, "plans");
        Objects.requireNonNull(hostBed, "hostBed");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(token, "token");
        if (plans.isEmpty()) {
            return Optional.empty();
        }
        if (hostBed.isEmpty()) {
            throw new WaterfallMaterializationRejectedV2(RULE_HOST_CHANNEL_NOT_FROZEN,
                    plans.getFirst().featureId(), -1, -1,
                    "the HARD ON_PATH_OF host river produced no frozen channel in this run");
        }
        RiverBedMaterializationV2.FrozenRiverBedV2 host = hostBed.get();

        int width = bounds.width();
        int length = bounds.length();
        requireAdmitted(plans, width, length);
        Footprint footprint = Footprint.of(plans, width, length);
        List<WaterfallGeneratorV2> generators = plans.stream().map(WaterfallGeneratorV2::new).toList();

        int cells = Math.multiplyExact(footprint.width(), footprint.length());
        byte[] basin = new byte[cells];
        int[] floorY = new int[cells];
        int[] waterSurfaceY = new int[cells];
        int[] carveTopY = new int[cells];
        long basinCells = 0;
        for (int localZ = 0; localZ < footprint.length(); localZ++) {
            token.throwIfCancellationRequested();
            for (int localX = 0; localX < footprint.width(); localX++) {
                int globalX = footprint.originX() + localX;
                int globalZ = footprint.originZ() + localZ;
                if (host.channelAt(globalX, globalZ)) {
                    continue;
                }
                Claim claim = claimAt(generators, plans, globalX, globalZ);
                if (claim == null) {
                    continue;
                }
                int index = localZ * footprint.width() + localX;
                basin[index] = 1;
                basinCells++;
                floorY[index] = Math.floorDiv(claim.floorMillionths(), SCALE);
                waterSurfaceY[index] = Math.floorDiv(claim.waterSurfaceMillionths(), SCALE);
                carveTopY[index] = surfaceYAt(fields, globalX, globalZ);
            }
        }
        if (basinCells == 0) {
            throw new WaterfallMaterializationRejectedV2(RULE_EMPTY_BASIN, plans.getFirst().featureId(),
                    -1, -1, "the declared waterfall rasterizes to no plunge basin cell outside the host"
                    + " channel inside the export domain");
        }

        FrozenWaterfallBasinV2 frozen = new FrozenWaterfallBasinV2(
                footprint, basin, floorY, waterSurfaceY, carveTopY, basinCells);
        requireBoundedFootprint(frozen, plans.getFirst().featureId(), width, length);
        requireDeclaredInteractions(frozen, plans, host, fields, bounds, width, length, token);
        return Optional.of(frozen);
    }

    /**
     * Proves the bounded footprint really contains the whole basin: an interior border cell that is
     * still basin would mean the raster escaped the AABB the freeze allocated.
     */
    private static void requireBoundedFootprint(
            FrozenWaterfallBasinV2 frozen, String featureId, int width, int length
    ) throws WaterfallMaterializationRejectedV2 {
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
                if (!domainEdge && frozen.basinAt(globalX, globalZ)) {
                    throw new WaterfallMaterializationRejectedV2(RULE_BUDGET, featureId, globalX, globalZ,
                            "the plunge basin raster reaches the bounded footprint border");
                }
            }
        }
    }

    private static void requireDeclaredInteractions(
            FrozenWaterfallBasinV2 frozen,
            List<WaterfallPlanV2> plans,
            RiverBedMaterializationV2.FrozenRiverBedV2 host,
            CoastalFieldSamplerV2 fields,
            GenerationRequestV2.Bounds bounds,
            int width,
            int length,
            CancellationToken token
    ) throws WaterfallMaterializationRejectedV2 {
        String featureId = plans.getFirst().featureId();
        Footprint footprint = frozen.footprint();
        for (int localZ = 0; localZ < footprint.length(); localZ++) {
            token.throwIfCancellationRequested();
            for (int localX = 0; localX < footprint.width(); localX++) {
                int x = footprint.originX() + localX;
                int z = footprint.originZ() + localZ;
                if (!frozen.basinAt(x, z)) {
                    continue;
                }
                int floor = frozen.floorYAt(x, z);
                int water = frozen.waterSurfaceYAt(x, z);
                int carveTop = frozen.carveTopYAt(x, z);
                if (floor <= bounds.minY() || carveTop > bounds.maxY()) {
                    throw new WaterfallMaterializationRejectedV2(RULE_VERTICAL_BOUNDS, featureId, x, z,
                            "pool floor " + floor + " / carve top " + carveTop
                                    + " leaves the vertical bounds (" + bounds.minY() + ".."
                                    + bounds.maxY() + "]");
                }
                if (floor >= carveTop) {
                    throw new WaterfallMaterializationRejectedV2(RULE_LEAK_ENVELOPE, featureId, x, z,
                            "pool floor " + floor + " is not below the surface " + carveTop
                                    + "; the basin would sit on top of the terrain");
                }
                if (water > carveTop) {
                    throw new WaterfallMaterializationRejectedV2(RULE_LEAK_ENVELOPE, featureId, x, z,
                            "pool water surface " + water + " rises above the carved envelope top "
                                    + carveTop);
                }
                if (fields.valueAt(CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, x, z) != 0) {
                    throw new WaterfallMaterializationRejectedV2(RULE_COASTAL_OWNER_CONFLICT, featureId, x, z,
                            "the plunge basin overlaps a coastal surface modifier's cell");
                }
                if (fields.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z) != 1) {
                    throw new WaterfallMaterializationRejectedV2(RULE_MARINE_CONTACT, featureId, x, z,
                            "the plunge basin enters a marine cell; a fall into the sea is not wired");
                }
                // BASIN_ENVELOPE: every off-basin neighbour inside the domain must be land and stand
                // at least as high as the pool water surface. The host channel is the declared
                // opening the fall arrives through and is excluded — its own bed stands a full drop
                // above this water surface, so the pool still cannot rise into it. The release
                // boundary is deliberately not a containment surface.
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx >= width || nz >= length) {
                            continue;
                        }
                        if (frozen.basinAt(nx, nz)) {
                            continue;
                        }
                        if (host.channelAt(nx, nz)) {
                            if (host.bedYAt(nx, nz) < water) {
                                throw new WaterfallMaterializationRejectedV2(RULE_LEAK_ENVELOPE,
                                        featureId, nx, nz, "host channel bed " + host.bedYAt(nx, nz)
                                        + " is below the pool water surface " + water + " at "
                                        + x + "," + z + "; the fall would drain upstream");
                            }
                            continue;
                        }
                        if (fields.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, nx, nz) != 1) {
                            throw new WaterfallMaterializationRejectedV2(RULE_MARINE_CONTACT, featureId,
                                    nx, nz, "a marine cell adjoins the plunge basin at " + x + "," + z);
                        }
                        int envelope = surfaceYAt(fields, nx, nz);
                        if (envelope < water) {
                            throw new WaterfallMaterializationRejectedV2(RULE_LEAK_ENVELOPE, featureId,
                                    nx, nz, "envelope surface " + envelope + " is below the pool water"
                                    + " surface " + water + " of the basin at " + x + "," + z);
                        }
                    }
                }
            }
        }
    }

    private static int surfaceYAt(CoastalFieldSamplerV2 fields, int x, int z) {
        return Math.floorDiv(
                fields.valueAt(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, x, z), SCALE);
    }

    /**
     * Merges the declared waterfalls at one cell. A cell counts as basin when the generator's own
     * raster claims the plunge pool, the lip crest does not claim it (the crest keeps the host bed),
     * and it lies in the downstream half-plane at the crest. Among basins the first plan in Blueprint
     * order owns the floor — the same plan-ordered merge {@link RiverBedMaterializationV2} uses.
     */
    private static Claim claimAt(
            List<WaterfallGeneratorV2> generators,
            List<WaterfallPlanV2> plans,
            int globalX,
            int globalZ
    ) {
        for (int index = 0; index < generators.size(); index++) {
            WaterfallGeneratorV2 generator = generators.get(index);
            if (globalX < 0 || globalX >= generator.width() || globalZ < 0 || globalZ >= generator.length()) {
                continue;
            }
            WaterfallGeneratorV2.WaterfallSample sample = generator.sampleAt(globalX, globalZ, cell -> false);
            if (sample.plungePoolMask() != 1 || sample.lipMask() == 1) {
                continue;
            }
            if (!downstreamOfCrest(plans.get(index), globalX, globalZ)) {
                continue;
            }
            return new Claim(sample.plungePoolFloor(), sample.baseElevation());
        }
        return null;
    }

    /**
     * The declared fall direction is {@code base - lip} in the XZ plane. A cell belongs to the basin
     * only when its offset from the lip crest has a non-negative projection on that direction, so the
     * pool never cuts under the upstream reach that feeds it.
     */
    private static boolean downstreamOfCrest(WaterfallPlanV2 plan, int globalX, int globalZ) {
        long cellX = Math.multiplyExact((long) globalX, SCALE) + SCALE / 2L;
        long cellZ = Math.multiplyExact((long) globalZ, SCALE) + SCALE / 2L;
        long fallX = Math.subtractExact(plan.baseXMillionths(), plan.lipXMillionths());
        long fallZ = Math.subtractExact(plan.baseZMillionths(), plan.lipZMillionths());
        // The offsets are reduced to thousandths of a block before the dot product, so a millionths ×
        // millionths multiplication cannot overflow at the MEDIUM-scale ceiling. The reduction is a
        // deterministic floor division, and the fall vector keeps its full millionths precision: a
        // fall whose lip and base are less than one block apart in XZ still has a signed direction.
        long offsetX = Math.floorDiv(Math.subtractExact(cellX, plan.lipXMillionths()), 1_000L);
        long offsetZ = Math.floorDiv(Math.subtractExact(cellZ, plan.lipZMillionths()), 1_000L);
        return Math.addExact(
                Math.multiplyExact(fallX, offsetX),
                Math.multiplyExact(fallZ, offsetZ)) >= 0L;
    }

    private record Claim(int floorMillionths, int waterSurfaceMillionths) {
    }

    /** Bounded union AABB of the declared plunge basins. */
    record Footprint(int originX, int originZ, int width, int length) {
        Footprint {
            if (width <= 0 || length <= 0) {
                throw new IllegalArgumentException("waterfall footprint must be non-empty");
            }
        }

        static Footprint of(List<WaterfallPlanV2> plans, int domainWidth, int domainLength) {
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (WaterfallPlanV2 plan : plans) {
                int reach = plan.plungePoolRadiusBlocks() + 2;
                int baseX = Math.toIntExact(Math.floorDiv(plan.baseXMillionths(), (long) SCALE));
                int baseZ = Math.toIntExact(Math.floorDiv(plan.baseZMillionths(), (long) SCALE));
                minX = Math.min(minX, baseX - reach);
                minZ = Math.min(minZ, baseZ - reach);
                maxX = Math.max(maxX, baseX + reach);
                maxZ = Math.max(maxZ, baseZ + reach);
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
     * The frozen, bounded plunge basin field of one export run. Immutable after {@link #freeze}: the
     * whole basin is resolved before the first tile is written, so every tile sees the same basin and
     * no tile-local re-derivation can drift at a seam.
     */
    static final class FrozenWaterfallBasinV2 {
        private final Footprint footprint;
        private final byte[] basin;
        private final int[] floorY;
        private final int[] waterSurfaceY;
        private final int[] carveTopY;
        private final long basinCells;

        private FrozenWaterfallBasinV2(
                Footprint footprint,
                byte[] basin,
                int[] floorY,
                int[] waterSurfaceY,
                int[] carveTopY,
                long basinCells
        ) {
            this.footprint = footprint;
            this.basin = basin;
            this.floorY = floorY;
            this.waterSurfaceY = waterSurfaceY;
            this.carveTopY = carveTopY;
            this.basinCells = basinCells;
        }

        Footprint footprint() {
            return footprint;
        }

        long basinCells() {
            return basinCells;
        }

        /** Blocks the carve voids, summed over the whole basin. */
        long carvedBlocks() {
            long carved = 0;
            for (int index = 0; index < basin.length; index++) {
                if (basin[index] == 1) {
                    carved += carveTopY[index] - floorY[index];
                }
            }
            return carved;
        }

        /** Blocks {@code ADD_FLUID} owns, summed over the whole basin. */
        long filledBlocks() {
            long filled = 0;
            for (int index = 0; index < basin.length; index++) {
                if (basin[index] == 1) {
                    filled += waterSurfaceY[index] - floorY[index];
                }
            }
            return filled;
        }

        boolean basinAt(int globalX, int globalZ) {
            return footprint.contains(globalX, globalZ) && basin[footprint.indexOf(globalX, globalZ)] == 1;
        }

        int floorYAt(int globalX, int globalZ) {
            return floorY[footprint.indexOf(globalX, globalZ)];
        }

        int waterSurfaceYAt(int globalX, int globalZ) {
            return waterSurfaceY[footprint.indexOf(globalX, globalZ)];
        }

        int carveTopYAt(int globalX, int globalZ) {
            return carveTopY[footprint.indexOf(globalX, globalZ)];
        }

        /**
         * Wraps the canonical resolver with the frozen basin. Ordered exactly as declared:
         * {@code CARVE_SOLID} first (the whole {@code (floorY, carveTopY]} span becomes void), then
         * {@code ADD_FLUID} inside that void up to the pool water surface. Cells the basin does not
         * claim, the pool floor block, and everything below it fall straight through to the base
         * resolver.
         */
        TerrainBlockResolver decorate(TerrainBlockResolver base) {
            Objects.requireNonNull(base, "base");
            return (x, y, z) -> {
                if (!footprint.contains(x, z)) {
                    return base.blockStateAt(x, y, z);
                }
                int index = footprint.indexOf(x, z);
                if (basin[index] != 1 || y <= floorY[index] || y > carveTopY[index]) {
                    return base.blockStateAt(x, y, z);
                }
                return y <= waterSurfaceY[index] ? WATER : AIR;
            };
        }

        long estimatedResidentBytes() {
            return Math.multiplyExact((long) basin.length, 1L + 3L * Integer.BYTES);
        }
    }

    /** Bounded-footprint admission, checked before the freeze allocates anything. */
    static void requireAdmitted(List<WaterfallPlanV2> plans, int width, int length)
            throws WaterfallMaterializationRejectedV2 {
        if (plans.isEmpty()) {
            return;
        }
        Footprint footprint = Footprint.of(plans, width, length);
        long cells = Math.multiplyExact((long) footprint.width(), (long) footprint.length());
        if (cells > MAXIMUM_FOOTPRINT_CELLS) {
            throw new WaterfallMaterializationRejectedV2(RULE_BUDGET, plans.getFirst().featureId(), -1, -1,
                    "bounded waterfall footprint " + cells + " cells exceeds the budget of "
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
