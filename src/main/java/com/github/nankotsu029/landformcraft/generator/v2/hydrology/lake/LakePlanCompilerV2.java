package com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Compiles one LAKE Intent feature into a frozen independent basin / rim / spillway plan. */
public final class LakePlanCompilerV2 {
    private static final long MAXIMUM_BASIN_CELLS = 250_000L;
    private static final long MAXIMUM_FILL_WORK = 1_000_000L;

    public LakePlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.LAKE) {
            throw failure("v2.lake-kind", "feature kind is not LAKE");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)
                || polygon.rings().size() != 1) {
            throw failure("v2.lake-geometry", "lake requires a single-ring POLYGON basin");
        }
        TerrainIntentV2.LakeParameters parameters = (TerrainIntentV2.LakeParameters) feature.parameters();
        try {
            List<LakePlanV2.RingPoint> ring = toRing(polygon.rings().getFirst(), bounds.width(), bounds.length());
            long areaEstimate = estimateInteriorCells(ring, bounds);
            if (areaEstimate > MAXIMUM_BASIN_CELLS
                    || Math.multiplyExact(areaEstimate, parameters.targetDepthBlocks().maximum()) > MAXIMUM_FILL_WORK) {
                throw failure("v2.lake-budget", "lake basin fill exceeds trusted queue/depth budget");
            }

            int selectedDepth = parameters.targetDepthBlocks().minimum()
                    + (parameters.targetDepthBlocks().maximum() - parameters.targetDepthBlocks().minimum()) / 2;
            long surfaceY = Math.multiplyExact((long) bounds.waterLevel(), LakeFixedMathV2.FIXED_SCALE);
            long maxDepth = Math.multiplyExact((long) selectedDepth, LakeFixedMathV2.FIXED_SCALE);
            long floorMin = Math.subtractExact(surfaceY, maxDepth);
            if (floorMin < Math.multiplyExact((long) bounds.minY(), LakeFixedMathV2.FIXED_SCALE)
                    || surfaceY > Math.multiplyExact((long) bounds.maxY(), LakeFixedMathV2.FIXED_SCALE)) {
                throw failure("v2.lake-vertical-bounds", "lake surface/depth exceeds vertical bounds");
            }
            long rimMinimum = surfaceY;

            SpillGeometry spill = resolveSpill(parameters, ring);
            List<String> inletIds = resolveInlets(feature, intent);
            String outletId = "outlet-" + feature.id();
            int support = Math.min(HydrologyLakeModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ,
                    Math.max(parameters.shoreWidthBlocks(),
                            Math.max(1, parameters.spillwayCorridorLengthBlocks())));

            return new LakePlanV2(
                    LakePlanV2.VERSION,
                    feature.id(),
                    parameters.terminalPolicy(),
                    parameters.spillSelection(),
                    parameters.floorProfile(),
                    "basin-" + feature.id(),
                    "water-" + feature.id(),
                    outletId,
                    inletIds,
                    ring,
                    surfaceY,
                    rimMinimum,
                    maxDepth,
                    parameters.targetDepthBlocks().minimum(),
                    parameters.targetDepthBlocks().maximum(),
                    selectedDepth,
                    parameters.shoreWidthBlocks(),
                    spill.edgeStartIndex(),
                    spill.first(),
                    spill.second(),
                    spill.outwardX(),
                    spill.outwardZ(),
                    parameters.spillwayWidthBlocks(),
                    parameters.spillwayCorridorLengthBlocks(),
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    HydrologyLakeModuleV2.BASIN_MASK_FIELD_ID,
                    HydrologyLakeModuleV2.RIM_MASK_FIELD_ID,
                    HydrologyLakeModuleV2.SPILLWAY_MASK_FIELD_ID,
                    HydrologyLakeModuleV2.DEPTH_FIELD_ID,
                    HydrologyLakeModuleV2.FLOOR_HEIGHT_FIELD_ID,
                    HydrologyLakeModuleV2.SURFACE_FIELD_ID,
                    HydrologyIrModuleV2.BED_ELEVATION_FIELD,
                    HydrologyIrModuleV2.WATER_SURFACE_FIELD,
                    HydrologyIrModuleV2.WATER_DEPTH_FIELD,
                    HydrologyIrModuleV2.WATER_BODY_ID_FIELD,
                    support,
                    geometryChecksum);
        } catch (ArithmeticException exception) {
            throw new LakeGenerationException("v2.lake-overflow", "lake plan arithmetic overflow", exception);
        }
    }

    private static SpillGeometry resolveSpill(
            TerrainIntentV2.LakeParameters parameters,
            List<LakePlanV2.RingPoint> ring
    ) {
        if (parameters.terminalPolicy() == TerrainIntentV2.LakeTerminalPolicy.CLOSED) {
            return new SpillGeometry(-1, null, null, 0L, 0L);
        }
        int edgeIndex;
        if (parameters.spillSelection() == TerrainIntentV2.LakeSpillSelection.DECLARED_EDGE) {
            edgeIndex = parameters.spillEdgeStartIndex();
            if (edgeIndex < 0 || edgeIndex >= ring.size() - 1) {
                throw failure("v2.lake-spill-edge", "declared spill edge is outside the basin ring");
            }
        } else {
            edgeIndex = lowestUniqueRimEdge(ring);
        }
        LakePlanV2.RingPoint first = ring.get(edgeIndex);
        LakePlanV2.RingPoint second = ring.get(edgeIndex + 1);
        long dx = Math.subtractExact(second.xMillionths(), first.xMillionths());
        long dz = Math.subtractExact(second.zMillionths(), first.zMillionths());
        long width = LakeFixedMathV2.hypotMillionths(dx, dz);
        if (width < 2L * LakeFixedMathV2.FIXED_SCALE) {
            throw failure("v2.lake-spill-width", "spill edge must be at least two blocks wide");
        }
        long normalX = LakeFixedMathV2.roundDivide(Math.multiplyExact(-dz, LakeFixedMathV2.FIXED_SCALE), width);
        long normalZ = LakeFixedMathV2.roundDivide(Math.multiplyExact(dx, LakeFixedMathV2.FIXED_SCALE), width);
        long midX = LakeFixedMathV2.roundDivide(Math.addExact(first.xMillionths(), second.xMillionths()), 2L);
        long midZ = LakeFixedMathV2.roundDivide(Math.addExact(first.zMillionths(), second.zMillionths()), 2L);
        boolean firstInside = LakeFixedMathV2.pointInRing(ring,
                Math.addExact(midX, normalX), Math.addExact(midZ, normalZ));
        boolean secondInside = LakeFixedMathV2.pointInRing(ring,
                Math.subtractExact(midX, normalX), Math.subtractExact(midZ, normalZ));
        if (firstInside == secondInside) {
            throw failure("v2.lake-spill-orientation", "spill edge has no unique outward side");
        }
        long outwardX = firstInside ? -normalX : normalX;
        long outwardZ = firstInside ? -normalZ : normalZ;
        // Reverse-flow guard: outward sample just outside the rim must leave the basin.
        long outsideX = Math.addExact(midX, outwardX);
        long outsideZ = Math.addExact(midZ, outwardZ);
        if (LakeFixedMathV2.pointInRing(ring, outsideX, outsideZ)) {
            throw failure("v2.lake-reverse-flow", "spill corridor would flow back into the basin");
        }
        return new SpillGeometry(edgeIndex, first, second, outwardX, outwardZ);
    }

    private static int lowestUniqueRimEdge(List<LakePlanV2.RingPoint> ring) {
        // Without a DEM, rim "elevation" is approximated by midpoint Z. Flat ties across
        // multiple edges are ambiguous and must not be guessed by X or registration order.
        record Candidate(int index, long midZ) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < ring.size() - 1; index++) {
            LakePlanV2.RingPoint a = ring.get(index);
            LakePlanV2.RingPoint b = ring.get(index + 1);
            long midZ = LakeFixedMathV2.roundDivide(Math.addExact(a.zMillionths(), b.zMillionths()), 2L);
            candidates.add(new Candidate(index, midZ));
        }
        candidates.sort(Comparator.comparingLong(Candidate::midZ).thenComparingInt(Candidate::index));
        if (candidates.size() >= 2 && candidates.get(0).midZ() == candidates.get(1).midZ()) {
            throw failure("v2.lake-ambiguous-spill", "multiple rim edges share the lowest spill score");
        }
        return candidates.getFirst().index();
    }

    private static List<String> resolveInlets(TerrainIntentV2.Feature lake, TerrainIntentV2 intent) {
        String lakeEndpoint = "feature:" + lake.id();
        List<String> inlets = new ArrayList<>();
        for (TerrainIntentV2.Relation relation : intent.relations()) {
            if (relation.strength() != TerrainIntentV2.Strength.HARD) continue;
            boolean intoLake = (relation.kind() == TerrainIntentV2.RelationKind.DRAINS_TO
                    || relation.kind() == TerrainIntentV2.RelationKind.EMPTIES_INTO)
                    && relation.to().equals(lakeEndpoint)
                    && relation.from().startsWith("feature:");
            if (!intoLake) continue;
            String fromId = relation.from().substring("feature:".length());
            boolean river = intent.features().stream().anyMatch(candidate ->
                    candidate.id().equals(fromId)
                            && candidate.kind() == TerrainIntentV2.FeatureKind.MEANDERING_RIVER);
            if (!river) {
                throw failure("v2.lake-inlet-relation", "lake inlet must come from a MEANDERING_RIVER feature");
            }
            inlets.add("inlet-" + fromId);
        }
        return inlets.stream().sorted().toList();
    }

    private static List<LakePlanV2.RingPoint> toRing(List<TerrainIntentV2.Point2> points, int width, int length) {
        List<LakePlanV2.RingPoint> ring = new ArrayList<>(points.size());
        for (TerrainIntentV2.Point2 point : points) {
            ring.add(new LakePlanV2.RingPoint(
                    Math.multiplyExact((long) point.xMillionths(), width - 1L),
                    Math.multiplyExact((long) point.zMillionths(), length - 1L)));
        }
        return List.copyOf(ring);
    }

    private static long estimateInteriorCells(List<LakePlanV2.RingPoint> ring, WorldBlueprintV2.Bounds bounds) {
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        for (LakePlanV2.RingPoint point : ring) {
            minX = Math.min(minX, point.xMillionths());
            maxX = Math.max(maxX, point.xMillionths());
            minZ = Math.min(minZ, point.zMillionths());
            maxZ = Math.max(maxZ, point.zMillionths());
        }
        int originX = Math.toIntExact(Math.max(0L, Math.floorDiv(minX, LakeFixedMathV2.FIXED_SCALE) - 1));
        int originZ = Math.toIntExact(Math.max(0L, Math.floorDiv(minZ, LakeFixedMathV2.FIXED_SCALE) - 1));
        int endX = Math.toIntExact(Math.min(bounds.width() - 1L, Math.floorDiv(maxX, LakeFixedMathV2.FIXED_SCALE) + 1));
        int endZ = Math.toIntExact(Math.min(bounds.length() - 1L, Math.floorDiv(maxZ, LakeFixedMathV2.FIXED_SCALE) + 1));
        long cells = 0L;
        for (int z = originZ; z <= endZ; z++) {
            for (int x = originX; x <= endX; x++) {
                long cx = Math.addExact(Math.multiplyExact((long) x, LakeFixedMathV2.FIXED_SCALE),
                        LakeFixedMathV2.FIXED_SCALE / 2L);
                long cz = Math.addExact(Math.multiplyExact((long) z, LakeFixedMathV2.FIXED_SCALE),
                        LakeFixedMathV2.FIXED_SCALE / 2L);
                if (LakeFixedMathV2.pointInRing(ring, cx, cz)) {
                    cells = Math.addExact(cells, 1L);
                    if (cells > MAXIMUM_BASIN_CELLS) return cells;
                }
            }
        }
        if (cells < 4L) throw failure("v2.lake-geometry", "lake basin interior is empty");
        return cells;
    }

    private static LakeGenerationException failure(String ruleId, String message) {
        return new LakeGenerationException(ruleId, message);
    }

    private record SpillGeometry(
            int edgeStartIndex,
            LakePlanV2.RingPoint first,
            LakePlanV2.RingPoint second,
            long outwardX,
            long outwardZ
    ) {
    }
}
