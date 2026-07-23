package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingResultV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalFieldSamplerV2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * V2-19-05 {@code RIVER} / {@code MEANDERING_RIVER} block materialization.
 *
 * <p>The 2026-07-23 cross-cutting audit found that the V2-15-10 offline production route produced
 * routing, reconciliation and validation artifacts but never touched a block: the published Release
 * was byte-identical to the same coastal contract without the river. This stage closes that gap by
 * turning the <em>reconciled</em> global route into a bounded river bed field and applying it to the
 * final canonical block stream.</p>
 *
 * <h2>Responsibility separation ({@code CARVE_SOLID} / {@code ADD_FLUID})</h2>
 *
 * <p>The same doctrine {@code volumetric-terrain.md} fixes for ordered CSG applies here, at the
 * surface tile level and in this order per channel column:</p>
 * <ol>
 *   <li>{@code CARVE_SOLID} voids {@code [bedY+1, surfaceY]} — it removes solid mass only and never
 *       introduces a fluid block;</li>
 *   <li>{@code ADD_FLUID} owns every water block, filling {@code [bedY+1, waterSurfaceY]} strictly
 *       inside the void the carve just produced. It never replaces solid: the freeze rejects any
 *       route whose water surface would rise above the carved envelope.</li>
 * </ol>
 * <p>The bed block itself ({@code y == bedY}) and everything below it are left to the coastal
 * surface resolver, so the river owns no material re-lining and no bedrock.</p>
 *
 * <h2>Declared interactions ({@link InteractionV2})</h2>
 *
 * <p>Every interaction this version supports is declared and enforced fail-closed; nothing is
 * inferred or silently repaired. In particular the river never writes the macro land-water medium:
 * that field stays owned by the HARD {@code LAND_WATER_MASK} (ADR 0038), so the foundation owner
 * gate, the EDGE evaluation and the coastal conformance measurements are unaffected — the river is a
 * block-level effect inside land.</p>
 *
 * <p>The freeze is pure and bounded: it allocates only the union AABB of the declared channels plus
 * a bank margin, scans row-major, and uses integer arithmetic only, so the result is independent of
 * locale, timezone, thread count and tile order.</p>
 */
final class RiverBedMaterializationV2 {
    static final String CONTRACT_VERSION = "river-bed-materialization-v1";

    /** The reconciled route is not frozen: reconciliation failed, or a bed value diverged. */
    static final String RULE_ROUTE_NOT_FROZEN = "v2.river.route-not-frozen";
    /** A channel cell is claimed by a coastal surface modifier (the river may only carve foundation). */
    static final String RULE_COASTAL_OWNER_CONFLICT = "v2.river.coastal-owner-conflict";
    /** A channel cell or its bank ring is marine: a river mouth into the sea is not wired yet. */
    static final String RULE_MARINE_CONTACT = "v2.river.marine-contact";
    /** The carved water would not be contained by the surrounding terrain. */
    static final String RULE_LEAK_ENVELOPE = "v2.river.leak-envelope";
    /** The bed or the carve top leaves the request's vertical bounds. */
    static final String RULE_VERTICAL_BOUNDS = "v2.river.vertical-bounds";
    /** The bounded footprint exceeds the materialization budget. */
    static final String RULE_BUDGET = "v2.river.materialization-budget";

    /** MEDIUM-scale ceiling: the bounded footprint may never exceed one 1024×1024 domain. */
    static final long MAXIMUM_FOOTPRINT_CELLS = 1_048_576L;

    private static final int SCALE = 1_000_000;
    private static final String AIR = "minecraft:air";
    private static final String WATER = "minecraft:water";
    private static final IntPredicate NO_HARD_ROUTE_CONFLICT = index -> false;

    private RiverBedMaterializationV2() {
    }

    /** The interaction contracts this version declares and enforces. */
    enum InteractionV2 {
        /** The upstream terminus keeps the plan's source bed; it is never lowered by the raster. */
        SOURCE_TERMINUS(RULE_ROUTE_NOT_FROZEN),
        /** The downstream terminus ends inland in v1; a marine mouth junction is rejected. */
        MOUTH_TERMINUS(RULE_MARINE_CONTACT),
        /**
         * The off-channel ring (the river's own bank and floodplain cells) is the containment
         * envelope: never carved, never filled, and required to stand at or above the water surface.
         */
        BANK_ENVELOPE(RULE_LEAK_ENVELOPE),
        /** A coastal modifier's own cells are off limits to the carve. */
        COASTAL_FOUNDATION(RULE_COASTAL_OWNER_CONFLICT),
        /** The macro land-water medium stays owned by the HARD mask; the river only changes blocks. */
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
     * Freezes the bounded river bed field of one export run, after routing and reconciliation and
     * before any tile is written. Returns {@link Optional#empty()} when the frozen Blueprint declares
     * no river, in which case the block stream stays byte-identical to the coastal-only path.
     */
    static Optional<FrozenRiverBedV2> freeze(
            List<MeanderingRiverPlanV2> plans,
            HydrologyRoutingResultV2 routing,
            HydrologyReconciliationArtifactV2 reconciliation,
            CoastalFieldSamplerV2 fields,
            GenerationRequestV2.Bounds bounds,
            CancellationToken token
    ) throws RiverMaterializationRejectedV2 {
        Objects.requireNonNull(plans, "plans");
        Objects.requireNonNull(routing, "routing");
        Objects.requireNonNull(reconciliation, "reconciliation");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(token, "token");
        if (plans.isEmpty()) {
            return Optional.empty();
        }
        requireFrozenRoute(plans, routing, reconciliation, bounds);

        int width = bounds.width();
        int length = bounds.length();
        requireAdmitted(plans, width, length);
        Footprint footprint = Footprint.of(plans, width, length);
        List<MeanderingRiverGeneratorV2> generators = plans.stream()
                .map(MeanderingRiverGeneratorV2::new)
                .toList();

        int cells = Math.multiplyExact(footprint.width(), footprint.length());
        byte[] channel = new byte[cells];
        byte[] bank = new byte[cells];
        int[] bedY = new int[cells];
        int[] waterSurfaceY = new int[cells];
        int[] carveTopY = new int[cells];
        long channelCells = 0;
        long bankCells = 0;
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
                if (claim.channel()) {
                    channel[index] = 1;
                    channelCells++;
                    bedY[index] = Math.floorDiv(claim.bedMillionths(), SCALE);
                    waterSurfaceY[index] = Math.floorDiv(claim.waterSurfaceMillionths(), SCALE);
                    carveTopY[index] = surfaceYAt(fields, globalX, globalZ);
                } else {
                    bank[index] = 1;
                    bankCells++;
                }
            }
        }
        if (channelCells == 0) {
            throw new RiverMaterializationRejectedV2(RULE_ROUTE_NOT_FROZEN, plans.getFirst().featureId(),
                    -1, -1, "the declared river rasterizes to no channel cell inside the export domain");
        }

        FrozenRiverBedV2 frozen = new FrozenRiverBedV2(footprint, channel, bank, bedY, waterSurfaceY,
                carveTopY, channelCells, bankCells);
        requireBoundedFootprint(frozen, plans.getFirst().featureId(), width, length);
        requireDeclaredInteractions(frozen, plans, fields, bounds, width, length, token);
        return Optional.of(frozen);
    }

    /**
     * The materialized bed must be the reconciled bed. Reconciliation may legitimately move a
     * {@code REACH_BED} variable; rasterizing the plan's untouched profile in that case would publish
     * a channel that disagrees with the frozen global route, so it fails closed instead.
     */
    private static void requireFrozenRoute(
            List<MeanderingRiverPlanV2> plans,
            HydrologyRoutingResultV2 routing,
            HydrologyReconciliationArtifactV2 reconciliation,
            GenerationRequestV2.Bounds bounds
    ) throws RiverMaterializationRejectedV2 {
        String firstFeature = plans.getFirst().featureId();
        if (routing.width() != bounds.width() || routing.length() != bounds.length()) {
            throw new RiverMaterializationRejectedV2(RULE_ROUTE_NOT_FROZEN, firstFeature, -1, -1,
                    "routing result covers " + routing.width() + "x" + routing.length()
                            + " but the export domain is " + bounds.width() + "x" + bounds.length());
        }
        if (reconciliation.status() != HydrologyReconciliationArtifactV2.Status.SATISFIED) {
            throw new RiverMaterializationRejectedV2(RULE_ROUTE_NOT_FROZEN, firstFeature, -1, -1,
                    "hydrology reconciliation status is " + reconciliation.status()
                            + "; the global route is not frozen");
        }
        Map<String, Long> reconciled = new LinkedHashMap<>();
        for (HydrologyReconciliationArtifactV2.FinalValue value : reconciliation.finalValues()) {
            reconciled.put(value.variableId(), value.valueMillionths());
        }
        for (MeanderingRiverPlanV2 plan : plans) {
            for (int index = 0; index < plan.centerline().size(); index++) {
                String variableId = reachBedVariableId(plan.featureId(), index);
                Long value = reconciled.get(variableId);
                if (value == null) {
                    throw new RiverMaterializationRejectedV2(RULE_ROUTE_NOT_FROZEN, plan.featureId(), -1, -1,
                            "reconciliation artifact carries no bed variable " + variableId);
                }
                long planned = plan.centerline().get(index).bedYMillionths();
                if (value != planned) {
                    throw new RiverMaterializationRejectedV2(RULE_ROUTE_NOT_FROZEN, plan.featureId(), -1, -1,
                            "reconciliation moved " + variableId + " from " + planned + " to " + value
                                    + "; the raster would not describe the reconciled route");
                }
            }
        }
    }

    /**
     * Mirrors {@code HydrologyReconciliationPlanCompilerV2}'s {@code REACH_BED} variable naming. It is
     * duplicated rather than exposed because the compiler owns the id, and this stage only reads it.
     */
    private static String reachBedVariableId(String featureId, int index) {
        return "river." + featureId + ".bed." + String.format(Locale.ROOT, "%04d", index);
    }

    /**
     * Proves the bounded footprint really contains the whole channel: an interior border cell that is
     * still channel would mean the raster escaped the AABB the freeze allocated.
     */
    private static void requireBoundedFootprint(
            FrozenRiverBedV2 frozen, String featureId, int width, int length
    ) throws RiverMaterializationRejectedV2 {
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
                if (!domainEdge && frozen.channelAt(globalX, globalZ)) {
                    throw new RiverMaterializationRejectedV2(RULE_BUDGET, featureId, globalX, globalZ,
                            "the channel raster reaches the bounded footprint border");
                }
            }
        }
    }

    private static void requireDeclaredInteractions(
            FrozenRiverBedV2 frozen,
            List<MeanderingRiverPlanV2> plans,
            CoastalFieldSamplerV2 fields,
            GenerationRequestV2.Bounds bounds,
            int width,
            int length,
            CancellationToken token
    ) throws RiverMaterializationRejectedV2 {
        String featureId = plans.getFirst().featureId();
        Footprint footprint = frozen.footprint();
        for (int localZ = 0; localZ < footprint.length(); localZ++) {
            token.throwIfCancellationRequested();
            for (int localX = 0; localX < footprint.width(); localX++) {
                int x = footprint.originX() + localX;
                int z = footprint.originZ() + localZ;
                if (!frozen.channelAt(x, z)) {
                    continue;
                }
                int bed = frozen.bedYAt(x, z);
                int water = frozen.waterSurfaceYAt(x, z);
                int carveTop = frozen.carveTopYAt(x, z);
                // Vertical bounds: the bed must stay above the bedrock floor and the carve inside the run.
                if (bed <= bounds.minY() || carveTop > bounds.maxY()) {
                    throw new RiverMaterializationRejectedV2(RULE_VERTICAL_BOUNDS, featureId, x, z,
                            "bed " + bed + " / carve top " + carveTop + " leaves the vertical bounds ("
                                    + bounds.minY() + ".." + bounds.maxY() + "]");
                }
                // Leak envelope, part 1: the channel must be cut into the terrain, and the water it
                // carries must stay inside the carved envelope (so ADD_FLUID never replaces solid).
                if (bed >= carveTop) {
                    throw new RiverMaterializationRejectedV2(RULE_LEAK_ENVELOPE, featureId, x, z,
                            "bed " + bed + " is not below the surface " + carveTop
                                    + "; the channel would sit on top of the terrain");
                }
                if (water > carveTop) {
                    throw new RiverMaterializationRejectedV2(RULE_LEAK_ENVELOPE, featureId, x, z,
                            "water surface " + water + " rises above the carved envelope top " + carveTop);
                }
                // Coastal foundation: the river carves the macro foundation, never a modifier's cells.
                if (fields.valueAt(CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, x, z) != 0) {
                    throw new RiverMaterializationRejectedV2(RULE_COASTAL_OWNER_CONFLICT, featureId, x, z,
                            "the channel overlaps a coastal surface modifier's cell");
                }
                if (fields.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z) != 1) {
                    throw new RiverMaterializationRejectedV2(RULE_MARINE_CONTACT, featureId, x, z,
                            "the channel enters a marine cell; a river mouth into the sea is not wired");
                }
                // Leak envelope, part 2 (bank envelope, mouth): every off-channel neighbour inside the
                // domain must be land and stand at least as high as the channel's water surface. The
                // release boundary is deliberately not a containment surface — a neighbour outside the
                // domain is out of contract, not a leak.
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx >= width || nz >= length) {
                            continue;
                        }
                        if (frozen.channelAt(nx, nz)) {
                            continue;
                        }
                        if (fields.valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, nx, nz) != 1) {
                            throw new RiverMaterializationRejectedV2(RULE_MARINE_CONTACT, featureId, nx, nz,
                                    "a marine cell adjoins the channel at " + x + "," + z);
                        }
                        int envelope = surfaceYAt(fields, nx, nz);
                        if (envelope < water) {
                            throw new RiverMaterializationRejectedV2(RULE_LEAK_ENVELOPE, featureId, nx, nz,
                                    "envelope surface " + envelope + " is below the water surface "
                                            + water + " of the channel at " + x + "," + z);
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
     * Merges the declared rivers at one cell. Channel wins over bank (a cell another river's bank
     * covers is still a channel), and among channels the first plan in Blueprint order owns the bed —
     * the same plan-ordered merge the shared validation sampler already uses.
     */
    private static Claim claimAt(
            List<MeanderingRiverGeneratorV2> generators,
            int globalX,
            int globalZ
    ) {
        boolean bank = false;
        for (MeanderingRiverGeneratorV2 generator : generators) {
            if (globalX < 0 || globalX >= generator.width() || globalZ < 0 || globalZ >= generator.length()) {
                continue;
            }
            MeanderingRiverGeneratorV2.RiverSample sample =
                    generator.sampleAt(globalX, globalZ, NO_HARD_ROUTE_CONFLICT);
            if (sample.channelMask() == 1) {
                return new Claim(true, sample.bedElevationMillionths(), sample.waterSurfaceMillionths());
            }
            bank = bank || sample.bankMask() == 1;
        }
        return bank ? new Claim(false, 0, 0) : null;
    }

    private record Claim(boolean channel, int bedMillionths, int waterSurfaceMillionths) {
    }

    /** Bounded union AABB of the declared channels plus their bank ring. */
    record Footprint(int originX, int originZ, int width, int length) {
        Footprint {
            if (width <= 0 || length <= 0) {
                throw new IllegalArgumentException("river footprint must be non-empty");
            }
        }

        static Footprint of(List<MeanderingRiverPlanV2> plans, int domainWidth, int domainLength) {
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (MeanderingRiverPlanV2 plan : plans) {
                int margin = 2 + plan.bankWidthBlocks();
                for (MeanderingRiverPlanV2.CenterlineSample sample : plan.centerline()) {
                    int x = Math.toIntExact(Math.floorDiv(sample.xMillionths(), (long) SCALE));
                    int z = Math.toIntExact(Math.floorDiv(sample.zMillionths(), (long) SCALE));
                    int reach = margin + sample.localHalfWidthBlocks();
                    minX = Math.min(minX, x - reach);
                    minZ = Math.min(minZ, z - reach);
                    maxX = Math.max(maxX, x + reach);
                    maxZ = Math.max(maxZ, z + reach);
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
     * The frozen, bounded river bed field of one export run. Immutable after {@link #freeze}: the
     * whole route is resolved before the first tile is written, so every tile sees the same route and
     * no tile-local re-derivation can drift at a seam.
     */
    static final class FrozenRiverBedV2 {
        private final Footprint footprint;
        private final byte[] channel;
        private final byte[] bank;
        private final int[] bedY;
        private final int[] waterSurfaceY;
        private final int[] carveTopY;
        private final long channelCells;
        private final long bankCells;

        private FrozenRiverBedV2(
                Footprint footprint,
                byte[] channel,
                byte[] bank,
                int[] bedY,
                int[] waterSurfaceY,
                int[] carveTopY,
                long channelCells,
                long bankCells
        ) {
            this.footprint = footprint;
            this.channel = channel;
            this.bank = bank;
            this.bedY = bedY;
            this.waterSurfaceY = waterSurfaceY;
            this.carveTopY = carveTopY;
            this.channelCells = channelCells;
            this.bankCells = bankCells;
        }

        Footprint footprint() {
            return footprint;
        }

        long channelCells() {
            return channelCells;
        }

        long bankCells() {
            return bankCells;
        }

        /** Blocks the carve voids, summed over the whole route. */
        long carvedBlocks() {
            long carved = 0;
            for (int index = 0; index < channel.length; index++) {
                if (channel[index] == 1) {
                    carved += carveTopY[index] - bedY[index];
                }
            }
            return carved;
        }

        /** Blocks {@code ADD_FLUID} owns, summed over the whole route. */
        long filledBlocks() {
            long filled = 0;
            for (int index = 0; index < channel.length; index++) {
                if (channel[index] == 1) {
                    filled += waterSurfaceY[index] - bedY[index];
                }
            }
            return filled;
        }

        boolean channelAt(int globalX, int globalZ) {
            return footprint.contains(globalX, globalZ) && channel[footprint.indexOf(globalX, globalZ)] == 1;
        }

        boolean bankAt(int globalX, int globalZ) {
            return footprint.contains(globalX, globalZ) && bank[footprint.indexOf(globalX, globalZ)] == 1;
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
         * Wraps the canonical coastal resolver with the frozen route. Ordered exactly as declared:
         * {@code CARVE_SOLID} first (the whole {@code [bedY+1, surfaceY]} span becomes void), then
         * {@code ADD_FLUID} inside that void up to the water surface. Cells the route does not claim,
         * the bed block, and everything below it fall straight through to the base resolver.
         */
        TerrainBlockResolver decorate(TerrainBlockResolver base) {
            Objects.requireNonNull(base, "base");
            return (x, y, z) -> {
                if (!footprint.contains(x, z)) {
                    return base.blockStateAt(x, y, z);
                }
                int index = footprint.indexOf(x, z);
                if (channel[index] != 1 || y <= bedY[index] || y > carveTopY[index]) {
                    return base.blockStateAt(x, y, z);
                }
                return y <= waterSurfaceY[index] ? WATER : AIR;
            };
        }

        long estimatedResidentBytes() {
            return Math.multiplyExact((long) channel.length, 2L + 3L * Integer.BYTES);
        }
    }

    /** Bounded-footprint admission, checked before the freeze allocates anything. */
    static void requireAdmitted(List<MeanderingRiverPlanV2> plans, int width, int length)
            throws RiverMaterializationRejectedV2 {
        if (plans.isEmpty()) {
            return;
        }
        Footprint footprint = Footprint.of(plans, width, length);
        long cells = Math.multiplyExact((long) footprint.width(), (long) footprint.length());
        if (cells > MAXIMUM_FOOTPRINT_CELLS) {
            throw new RiverMaterializationRejectedV2(RULE_BUDGET, plans.getFirst().featureId(), -1, -1,
                    "bounded river footprint " + cells + " cells exceeds the budget of "
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
