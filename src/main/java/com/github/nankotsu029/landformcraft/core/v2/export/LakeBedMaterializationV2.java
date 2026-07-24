package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.LakeGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalFieldSamplerV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * V2-15-11 {@code LAKE} block materialization.
 *
 * <p>Mirrors the V2-19-05 {@code RIVER} materialization doctrine: a compiled {@link LakePlanV2} plan
 * describes basin geometry only, so this stage turns that geometry into a bounded basin field and
 * applies it to the final canonical block stream through the same
 * {@code CARVE_SOLID} → {@code ADD_FLUID} ordering, per basin column.</p>
 *
 * <h2>Scope</h2>
 *
 * <p>Only {@link TerrainIntentV2.LakeTerminalPolicy#CLOSED} basins are materialized. An
 * {@code OPEN_SPILL} lake compiles and validates (V2-3-13 plan-only metrics), but its spillway
 * corridor exit onto the macro foundation background is not wired to a block effect yet — exactly
 * the same "not wired yet" posture {@link RiverBedMaterializationV2} takes for a marine river mouth —
 * so the freeze rejects it fail closed rather than silently materializing only the basin.</p>
 *
 * <h2>Responsibility separation ({@code CARVE_SOLID} / {@code ADD_FLUID})</h2>
 *
 * <ol>
 *   <li>{@code CARVE_SOLID} voids {@code [bedY+1, surfaceY]} per basin column — it removes solid
 *       mass only and never introduces a fluid block;</li>
 *   <li>{@code ADD_FLUID} owns every water block, filling {@code [bedY+1, waterSurfaceY]} strictly
 *       inside the void the carve just produced. It never replaces solid: the freeze rejects any
 *       basin whose water surface would rise above the carved envelope.</li>
 * </ol>
 * <p>The bed block itself and everything below it are left to the coastal surface resolver, so the
 * lake owns no material re-lining and no bedrock.</p>
 *
 * <h2>Declared interactions ({@link InteractionV2})</h2>
 *
 * <p>Every interaction this version supports is declared and enforced fail-closed. The lake never
 * writes the macro land-water medium: that field stays owned by the HARD {@code LAND_WATER_MASK}
 * (ADR 0038), so the foundation owner gate, the EDGE evaluation and the coastal conformance
 * measurements are unaffected — the lake is a block-level effect inside land.</p>
 *
 * <p>The freeze is pure and bounded: it allocates only the union AABB of the declared basins plus a
 * shore margin, scans row-major, and uses integer arithmetic only, so the result is independent of
 * locale, timezone, thread count and tile order.</p>
 */
final class LakeBedMaterializationV2 {
    static final String CONTRACT_VERSION = "lake-bed-materialization-v1";

    /** The basin rasterizes to no cell inside the export domain. */
    static final String RULE_EMPTY_BASIN = "v2.lake.empty-basin";
    /** An {@code OPEN_SPILL} lake's spillway exit is not wired to a block effect yet. */
    static final String RULE_SPILL_NOT_WIRED = "v2.lake.spill-not-wired";
    /** A basin cell is claimed by a coastal surface modifier (the lake may only carve foundation). */
    static final String RULE_COASTAL_OWNER_CONFLICT = "v2.lake.coastal-owner-conflict";
    /** A basin cell or its rim ring is marine: a lake fed by the sea is not wired. */
    static final String RULE_MARINE_CONTACT = "v2.lake.marine-contact";
    /** The carved water would not be contained by the surrounding terrain. */
    static final String RULE_LEAK_ENVELOPE = "v2.lake.leak-envelope";
    /** The bed or the carve top leaves the request's vertical bounds. */
    static final String RULE_VERTICAL_BOUNDS = "v2.lake.vertical-bounds";
    /** The bounded footprint exceeds the materialization budget. */
    static final String RULE_BUDGET = "v2.lake.materialization-budget";

    /** MEDIUM-scale ceiling: the bounded footprint may never exceed one 1024×1024 domain. */
    static final long MAXIMUM_FOOTPRINT_CELLS = 1_048_576L;

    private static final String AIR = "minecraft:air";
    private static final String WATER = "minecraft:water";

    private LakeBedMaterializationV2() {
    }

    /** The interaction contracts this version declares and enforces. */
    enum InteractionV2 {
        /** Only a closed basin is materialized; an open spillway exit is rejected. */
        SPILL_TERMINUS(RULE_SPILL_NOT_WIRED),
        /**
         * The off-basin ring (the lake's own shore cells) is the containment envelope: never
         * carved, never filled, and required to stand at or above the water surface.
         */
        RIM_ENVELOPE(RULE_LEAK_ENVELOPE),
        /** A coastal modifier's own cells are off limits to the carve. */
        COASTAL_FOUNDATION(RULE_COASTAL_OWNER_CONFLICT),
        /** The macro land-water medium stays owned by the HARD mask; the lake only changes blocks. */
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
     * Freezes the bounded lake basin field of one export run, before any tile is written. Returns
     * {@link Optional#empty()} when the frozen Blueprint declares no lake, in which case the block
     * stream stays byte-identical to the coastal-only path.
     */
    static Optional<FrozenLakeBedV2> freeze(
            List<LakePlanV2> plans,
            CoastalFieldSamplerV2 fields,
            GenerationRequestV2.Bounds bounds,
            CancellationToken token
    ) throws LakeMaterializationRejectedV2 {
        Objects.requireNonNull(plans, "plans");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(token, "token");
        if (plans.isEmpty()) {
            return Optional.empty();
        }
        for (LakePlanV2 plan : plans) {
            if (plan.terminalPolicy() != TerrainIntentV2.LakeTerminalPolicy.CLOSED) {
                throw new LakeMaterializationRejectedV2(RULE_SPILL_NOT_WIRED, plan.featureId(), -1, -1,
                        "OPEN_SPILL lakes are not materialized yet; the spillway exit has no block effect");
            }
        }

        int width = bounds.width();
        int length = bounds.length();
        requireAdmitted(plans, width, length);
        Footprint footprint = Footprint.of(plans, width, length);
        List<LakeGeneratorV2> generators = plans.stream().map(LakeGeneratorV2::new).toList();

        int cells = Math.multiplyExact(footprint.width(), footprint.length());
        byte[] basin = new byte[cells];
        int[] bedY = new int[cells];
        int[] waterSurfaceY = new int[cells];
        int[] carveTopY = new int[cells];
        long basinCells = 0;
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
                basin[index] = 1;
                basinCells++;
                bedY[index] = Math.floorDiv(claim.floorMillionths(), 1_000_000);
                waterSurfaceY[index] = Math.floorDiv(claim.surfaceMillionths(), 1_000_000);
                carveTopY[index] = surfaceYAt(fields, globalX, globalZ);
            }
        }
        if (basinCells == 0) {
            throw new LakeMaterializationRejectedV2(RULE_EMPTY_BASIN, plans.getFirst().featureId(), -1, -1,
                    "the declared lake rasterizes to no basin cell inside the export domain");
        }

        FrozenLakeBedV2 frozen = new FrozenLakeBedV2(footprint, basin, bedY, waterSurfaceY, carveTopY, basinCells);
        requireBoundedFootprint(frozen, plans.getFirst().featureId(), width, length);
        requireDeclaredInteractions(frozen, plans, fields, bounds, width, length, token);
        return Optional.of(frozen);
    }

    private static void requireBoundedFootprint(
            FrozenLakeBedV2 frozen, String featureId, int width, int length
    ) throws LakeMaterializationRejectedV2 {
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
                    throw new LakeMaterializationRejectedV2(RULE_BUDGET, featureId, globalX, globalZ,
                            "the basin raster reaches the bounded footprint border");
                }
            }
        }
    }

    private static void requireDeclaredInteractions(
            FrozenLakeBedV2 frozen,
            List<LakePlanV2> plans,
            CoastalFieldSamplerV2 fields,
            GenerationRequestV2.Bounds bounds,
            int width,
            int length,
            CancellationToken token
    ) throws LakeMaterializationRejectedV2 {
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
                int bed = frozen.bedYAt(x, z);
                int water = frozen.waterSurfaceYAt(x, z);
                int carveTop = frozen.carveTopYAt(x, z);
                if (bed <= bounds.minY() || carveTop > bounds.maxY()) {
                    throw new LakeMaterializationRejectedV2(RULE_VERTICAL_BOUNDS, featureId, x, z,
                            "bed " + bed + " / carve top " + carveTop + " leaves the vertical bounds ("
                                    + bounds.minY() + ".." + bounds.maxY() + "]");
                }
                if (bed >= carveTop) {
                    throw new LakeMaterializationRejectedV2(RULE_LEAK_ENVELOPE, featureId, x, z,
                            "bed " + bed + " is not below the surface " + carveTop
                                    + "; the basin would sit on top of the terrain");
                }
                if (water > carveTop) {
                    throw new LakeMaterializationRejectedV2(RULE_LEAK_ENVELOPE, featureId, x, z,
                            "water surface " + water + " rises above the carved envelope top " + carveTop);
                }
                if (fields.valueAt(CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, x, z) != 0) {
                    throw new LakeMaterializationRejectedV2(RULE_COASTAL_OWNER_CONFLICT, featureId, x, z,
                            "the basin overlaps a coastal surface modifier's cell");
                }
                if (fields.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z) != 1) {
                    throw new LakeMaterializationRejectedV2(RULE_MARINE_CONTACT, featureId, x, z,
                            "the basin enters a marine cell; a lake fed by the sea is not wired");
                }
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
                        if (fields.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, nx, nz) != 1) {
                            throw new LakeMaterializationRejectedV2(RULE_MARINE_CONTACT, featureId, nx, nz,
                                    "a marine cell adjoins the basin at " + x + "," + z);
                        }
                        int envelope = surfaceYAt(fields, nx, nz);
                        if (envelope < water) {
                            throw new LakeMaterializationRejectedV2(RULE_LEAK_ENVELOPE, featureId, nx, nz,
                                    "envelope surface " + envelope + " is below the water surface "
                                            + water + " of the basin at " + x + "," + z);
                        }
                    }
                }
            }
        }
    }

    private static int surfaceYAt(CoastalFieldSamplerV2 fields, int x, int z) {
        return Math.floorDiv(
                fields.valueAt(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, x, z), 1_000_000);
    }

    /**
     * Merges the declared lakes at one cell. Among basins the first plan in Blueprint order owns the
     * bed — the same plan-ordered merge {@link RiverBedMaterializationV2} uses.
     */
    private static Claim claimAt(List<LakeGeneratorV2> generators, int globalX, int globalZ) {
        for (LakeGeneratorV2 generator : generators) {
            if (globalX < 0 || globalX >= generator.width() || globalZ < 0 || globalZ >= generator.length()) {
                continue;
            }
            LakeGeneratorV2.LakeSample sample = generator.sampleAt(globalX, globalZ, index -> false);
            if (sample.basinMask() == 1) {
                return new Claim(sample.floorHeightMillionths(), sample.surfaceMillionths());
            }
        }
        return null;
    }

    private record Claim(int floorMillionths, int surfaceMillionths) {
    }

    /** Bounded union AABB of the declared basin rings plus their shore margin. */
    record Footprint(int originX, int originZ, int width, int length) {
        Footprint {
            if (width <= 0 || length <= 0) {
                throw new IllegalArgumentException("lake footprint must be non-empty");
            }
        }

        static Footprint of(List<LakePlanV2> plans, int domainWidth, int domainLength) {
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (LakePlanV2 plan : plans) {
                int margin = 2 + plan.shoreWidthBlocks();
                for (LakePlanV2.RingPoint point : plan.basinRing()) {
                    int x = Math.toIntExact(Math.floorDiv(point.xMillionths(), 1_000_000L));
                    int z = Math.toIntExact(Math.floorDiv(point.zMillionths(), 1_000_000L));
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
     * The frozen, bounded lake basin field of one export run. Immutable after {@link #freeze}: the
     * whole basin geometry is resolved before the first tile is written, so every tile sees the same
     * basin and no tile-local re-derivation can drift at a seam.
     */
    static final class FrozenLakeBedV2 {
        private final Footprint footprint;
        private final byte[] basin;
        private final int[] bedY;
        private final int[] waterSurfaceY;
        private final int[] carveTopY;
        private final long basinCells;

        private FrozenLakeBedV2(
                Footprint footprint, byte[] basin, int[] bedY, int[] waterSurfaceY, int[] carveTopY, long basinCells
        ) {
            this.footprint = footprint;
            this.basin = basin;
            this.bedY = bedY;
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
                    carved += carveTopY[index] - bedY[index];
                }
            }
            return carved;
        }

        /** Blocks {@code ADD_FLUID} owns, summed over the whole basin. */
        long filledBlocks() {
            long filled = 0;
            for (int index = 0; index < basin.length; index++) {
                if (basin[index] == 1) {
                    filled += waterSurfaceY[index] - bedY[index];
                }
            }
            return filled;
        }

        boolean basinAt(int globalX, int globalZ) {
            return footprint.contains(globalX, globalZ) && basin[footprint.indexOf(globalX, globalZ)] == 1;
        }

        int bedYAt(int globalX, int globalZ) {
            return bedY[footprint.indexOf(globalX, globalZ)];
        }

        int waterSurfaceYAt(int globalX, int globalZ) {
            return waterSurfaceY[footprint.indexOf(globalX, globalZ)];
        }

        int carveTopYAt(int globalX, int globalZ) {
            return carveTopY[footprint.indexOf(globalX, globalZ)];
        }

        /**
         * Wraps the canonical coastal resolver with the frozen basin. Ordered exactly as declared:
         * {@code CARVE_SOLID} first (the whole {@code [bedY+1, surfaceY]} span becomes void), then
         * {@code ADD_FLUID} inside that void up to the water surface. Cells the basin does not claim,
         * the bed block, and everything below it fall straight through to the base resolver.
         */
        TerrainBlockResolver decorate(TerrainBlockResolver base) {
            Objects.requireNonNull(base, "base");
            return (x, y, z) -> {
                if (!footprint.contains(x, z)) {
                    return base.blockStateAt(x, y, z);
                }
                int index = footprint.indexOf(x, z);
                if (basin[index] != 1 || y <= bedY[index] || y > carveTopY[index]) {
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
    static void requireAdmitted(List<LakePlanV2> plans, int width, int length)
            throws LakeMaterializationRejectedV2 {
        if (plans.isEmpty()) {
            return;
        }
        Footprint footprint = Footprint.of(plans, width, length);
        long cells = Math.multiplyExact((long) footprint.width(), (long) footprint.length());
        if (cells > MAXIMUM_FOOTPRINT_CELLS) {
            throw new LakeMaterializationRejectedV2(RULE_BUDGET, plans.getFirst().featureId(), -1, -1,
                    "bounded lake footprint " + cells + " cells exceeds the budget of "
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
