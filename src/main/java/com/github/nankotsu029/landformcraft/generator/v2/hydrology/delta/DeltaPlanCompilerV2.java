package com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta;

import com.github.nankotsu029.landformcraft.model.v2.DeltaPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Compiles one DELTA into a frozen river-apex to marine-boundary distributary DAG. */
public final class DeltaPlanCompilerV2 {
    public DeltaPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            List<MeanderingRiverPlanV2> riverPlans,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(riverPlans, "riverPlans");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.DELTA) {
            throw failure("v2.delta-kind", "feature kind is not DELTA");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)
                || polygon.rings().size() != 1) {
            throw failure("v2.delta-geometry", "delta requires one outer POLYGON ring without holes");
        }
        TerrainIntentV2.DeltaParameters parameters = (TerrainIntentV2.DeltaParameters) feature.parameters();
        try {
            TrunkBinding trunk = requireTrunk(feature, intent, riverPlans);
            OutletBinding outlet = requireMarineOutlet(feature, intent);
            List<DeltaPlanV2.FanPoint> ring = canonicalRing(polygon.rings().getFirst(), bounds);
            requireConvex(ring);
            DeltaPlanV2.FanPoint apex = new DeltaPlanV2.FanPoint(
                    trunk.river().centerline().getLast().xMillionths(),
                    trunk.river().centerline().getLast().zMillionths());
            if (!insideOrOnRing(ring, apex)) {
                throw failure("v2.delta-disconnected-trunk", "river mouth is outside the delta fan");
            }

            BoundarySpan span = boundarySpan(ring, outlet.boundary(), bounds);
            long fanOpening = fanOpeningDegreesMillionths(apex, span.first(), span.second());
            if (fanOpening < parameters.fanOpeningDegrees().minimumMillionths()
                    || fanOpening > parameters.fanOpeningDegrees().maximumMillionths()) {
                throw failure("v2.delta-fan-opening", "delta fan opening is outside the declared range");
            }

            int branchCount = midpoint(parameters.distributaryCount());
            int fanRelief = midpoint(parameters.fanReliefBlocks());
            int sandbarCount = midpoint(parameters.sandbarCount());
            int shallowDepth = midpoint(parameters.shallowSeaDepthBlocks());
            validateHardMetrics(intent, feature.id(), branchCount, fanRelief);

            String apexNodeId = "apex-" + derivedSuffix(feature.id());
            int shallowBand = Math.min(64, Math.max(8, shallowDepth * 4));
            List<DeltaPlanV2.DistributaryBranch> branches = branches(
                    apex, apexNodeId, span, branchCount, trunk.river(), bounds, shallowDepth);
            List<DeltaPlanV2.Sandbar> sandbars = sandbars(
                    span, outlet.boundary(), sandbarCount, shallowBand, bounds);
            long workUnits = estimatedWorkUnits(ring, branches.size());
            int support = Math.max(shallowBand, Math.max(
                    branches.stream().mapToInt(DeltaPlanV2.DistributaryBranch::halfWidthBlocks).max().orElse(1),
                    sandbars.stream().mapToInt(DeltaPlanV2.Sandbar::radiusBlocks).max().orElse(0)));
            if (support > HydrologyDeltaModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.delta-budget", "delta support radius exceeds trusted halo budget");
            }

            return new DeltaPlanV2(
                    DeltaPlanV2.VERSION,
                    feature.id(),
                    trunk.river().featureId(),
                    trunk.river().reachId(),
                    trunk.relationId(),
                    outlet.relationId(),
                    outlet.boundary(),
                    parameters.fanProfile(),
                    apexNodeId,
                    apex,
                    ring,
                    parameters.distributaryCount().minimum(),
                    parameters.distributaryCount().maximum(),
                    branchCount,
                    parameters.fanOpeningDegrees().minimumMillionths(),
                    parameters.fanOpeningDegrees().maximumMillionths(),
                    fanOpening,
                    parameters.fanReliefBlocks().minimum(),
                    parameters.fanReliefBlocks().maximum(),
                    fanRelief,
                    parameters.sandbarCount().minimum(),
                    parameters.sandbarCount().maximum(),
                    sandbarCount,
                    parameters.shallowSeaDepthBlocks().minimum(),
                    parameters.shallowSeaDepthBlocks().maximum(),
                    shallowDepth,
                    shallowBand,
                    branches,
                    sandbars,
                    bounds.minY(), bounds.maxY(), bounds.waterLevel(), bounds.width(), bounds.length(),
                    HydrologyDeltaModuleV2.FAN_MASK_FIELD_ID,
                    HydrologyDeltaModuleV2.CHANNEL_MASK_FIELD_ID,
                    HydrologyDeltaModuleV2.BRANCH_INDEX_FIELD_ID,
                    HydrologyDeltaModuleV2.FAN_SURFACE_FIELD_ID,
                    HydrologyDeltaModuleV2.SANDBAR_MASK_FIELD_ID,
                    HydrologyDeltaModuleV2.SHALLOW_SEA_DEPTH_FIELD_ID,
                    HydrologyDeltaModuleV2.DISCHARGE_SHARE_FIELD_ID,
                    support,
                    workUnits,
                    geometryChecksum,
                    trunk.river().geometryChecksum());
        } catch (DeltaGenerationException exception) {
            throw exception;
        } catch (ArithmeticException exception) {
            throw new DeltaGenerationException("v2.delta-overflow", "delta plan arithmetic overflow", exception);
        }
    }

    private static TrunkBinding requireTrunk(
            TerrainIntentV2.Feature delta,
            TerrainIntentV2 intent,
            List<MeanderingRiverPlanV2> riverPlans
    ) {
        List<TerrainIntentV2.Relation> bindings = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.DRAINS_TO
                        && relation.strength() == TerrainIntentV2.Strength.HARD
                        && relation.to().equals("feature:" + delta.id())
                        && relation.from().startsWith("feature:"))
                .toList();
        if (bindings.size() != 1) {
            throw failure("v2.delta-trunk-relation", "delta requires exactly one HARD incoming DRAINS_TO river");
        }
        String riverId = bindings.getFirst().from().substring("feature:".length());
        MeanderingRiverPlanV2 river = riverPlans.stream()
                .filter(candidate -> candidate.featureId().equals(riverId))
                .findFirst()
                .orElseThrow(() -> failure("v2.delta-trunk-relation", "delta trunk is not a compiled river"));
        return new TrunkBinding(bindings.getFirst().id(), river);
    }

    private static OutletBinding requireMarineOutlet(TerrainIntentV2.Feature delta, TerrainIntentV2 intent) {
        List<TerrainIntentV2.Relation> outlets = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.EMPTIES_INTO
                        && relation.strength() == TerrainIntentV2.Strength.HARD
                        && relation.from().equals("feature:" + delta.id()))
                .toList();
        if (outlets.size() != 1) {
            throw failure("v2.delta-outlet-relation", "delta requires exactly one HARD EMPTIES_INTO boundary");
        }
        TerrainIntentV2.Edge boundary = TerrainIntentV2.Edge.valueOf(
                outlets.getFirst().to().substring("boundary:".length()));
        boolean hardSea = intent.constraints().stream()
                .filter(TerrainIntentV2.EdgeClassificationConstraint.class::isInstance)
                .map(TerrainIntentV2.EdgeClassificationConstraint.class::cast)
                .anyMatch(constraint -> constraint.strength() == TerrainIntentV2.Strength.HARD
                        && constraint.edge() == boundary
                        && constraint.classification() == TerrainIntentV2.EdgeClassification.SEA);
        if (!hardSea) {
            throw failure("v2.delta-hard-outlet-conflict",
                    "delta receiving boundary must have an explicit HARD SEA classification");
        }
        return new OutletBinding(outlets.getFirst().id(), boundary);
    }

    private static void validateHardMetrics(
            TerrainIntentV2 intent,
            String featureId,
            int branchCount,
            int fanRelief
    ) {
        for (TerrainIntentV2.Constraint constraint : intent.constraints()) {
            if (!(constraint instanceof TerrainIntentV2.MetricRangeConstraint metric)
                    || metric.strength() != TerrainIntentV2.Strength.HARD
                    || !metric.subject().equals("feature:" + featureId)) {
                continue;
            }
            long actual = switch (metric.metric()) {
                case "ACTIVE_DISTRIBUTARY_COUNT" -> (long) branchCount * TerrainIntentV2.FIXED_SCALE;
                case "ELEVATION_RANGE_BLOCKS" -> (long) fanRelief * TerrainIntentV2.FIXED_SCALE;
                default -> Long.MIN_VALUE;
            };
            if (actual != Long.MIN_VALUE
                    && (actual < metric.range().minimumMillionths() - metric.toleranceMillionths()
                    || actual > metric.range().maximumMillionths() + metric.toleranceMillionths())) {
                throw failure("v2.delta-hard-metric-conflict", "delta plan conflicts with a HARD metric constraint");
            }
        }
    }

    private static List<DeltaPlanV2.FanPoint> canonicalRing(
            List<TerrainIntentV2.Point2> source,
            WorldBlueprintV2.Bounds bounds
    ) {
        if (source.size() > DeltaPlanV2.MAXIMUM_RING_POINTS) {
            throw failure("v2.delta-budget", "delta fan ring exceeds point budget");
        }
        List<DeltaPlanV2.FanPoint> points = new ArrayList<>(source.size() - 1);
        for (int index = 0; index < source.size() - 1; index++) {
            TerrainIntentV2.Point2 point = source.get(index);
            points.add(new DeltaPlanV2.FanPoint(
                    Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L),
                    Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L)));
        }
        if (signedArea(points).signum() < 0) Collections.reverse(points);
        int first = 0;
        for (int index = 1; index < points.size(); index++) {
            DeltaPlanV2.FanPoint candidate = points.get(index);
            DeltaPlanV2.FanPoint current = points.get(first);
            if (Comparator.comparingLong(DeltaPlanV2.FanPoint::xMillionths)
                    .thenComparingLong(DeltaPlanV2.FanPoint::zMillionths)
                    .compare(candidate, current) < 0) {
                first = index;
            }
        }
        List<DeltaPlanV2.FanPoint> result = new ArrayList<>(points.size() + 1);
        for (int offset = 0; offset < points.size(); offset++) {
            result.add(points.get((first + offset) % points.size()));
        }
        result.add(result.getFirst());
        return List.copyOf(result);
    }

    private static BigInteger signedArea(List<DeltaPlanV2.FanPoint> points) {
        BigInteger area = BigInteger.ZERO;
        for (int index = 0; index < points.size(); index++) {
            DeltaPlanV2.FanPoint a = points.get(index);
            DeltaPlanV2.FanPoint b = points.get((index + 1) % points.size());
            area = area.add(BigInteger.valueOf(a.xMillionths()).multiply(BigInteger.valueOf(b.zMillionths())))
                    .subtract(BigInteger.valueOf(b.xMillionths()).multiply(BigInteger.valueOf(a.zMillionths())));
        }
        return area;
    }

    private static void requireConvex(List<DeltaPlanV2.FanPoint> ring) {
        int sign = 0;
        for (int index = 0; index < ring.size() - 1; index++) {
            DeltaPlanV2.FanPoint a = ring.get(index);
            DeltaPlanV2.FanPoint b = ring.get((index + 1) % (ring.size() - 1));
            DeltaPlanV2.FanPoint c = ring.get((index + 2) % (ring.size() - 1));
            int current = crossBig(a, b, c).signum();
            if (current == 0) continue;
            if (sign != 0 && current != sign) {
                throw failure("v2.delta-geometry", "delta fan polygon must be convex for bounded branch rasterization");
            }
            sign = current;
        }
        if (sign == 0) throw failure("v2.delta-geometry", "delta fan polygon is degenerate");
    }

    private static BoundarySpan boundarySpan(
            List<DeltaPlanV2.FanPoint> ring,
            TerrainIntentV2.Edge boundary,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<DeltaPlanV2.FanPoint> candidates = new ArrayList<>();
        boolean boundaryEdge = false;
        for (int index = 0; index < ring.size() - 1; index++) {
            DeltaPlanV2.FanPoint first = ring.get(index);
            DeltaPlanV2.FanPoint second = ring.get(index + 1);
            boolean firstOn = onBoundary(first, boundary, bounds);
            boolean secondOn = onBoundary(second, boundary, bounds);
            if (firstOn) candidates.add(first);
            if (firstOn && secondOn && !first.equals(second)) boundaryEdge = true;
        }
        candidates = candidates.stream().distinct().sorted(boundaryComparator(boundary)).toList();
        if (!boundaryEdge || candidates.size() < 2) {
            throw failure("v2.delta-landlocked-mouth", "delta fan does not expose an edge on the receiving sea boundary");
        }
        return new BoundarySpan(candidates.getFirst(), candidates.getLast());
    }

    private static Comparator<DeltaPlanV2.FanPoint> boundaryComparator(TerrainIntentV2.Edge boundary) {
        return switch (boundary) {
            case NORTH, SOUTH -> Comparator.comparingLong(DeltaPlanV2.FanPoint::xMillionths)
                    .thenComparingLong(DeltaPlanV2.FanPoint::zMillionths);
            case EAST, WEST -> Comparator.comparingLong(DeltaPlanV2.FanPoint::zMillionths)
                    .thenComparingLong(DeltaPlanV2.FanPoint::xMillionths);
        };
    }

    private static boolean onBoundary(
            DeltaPlanV2.FanPoint point,
            TerrainIntentV2.Edge boundary,
            WorldBlueprintV2.Bounds bounds
    ) {
        long maximumX = Math.multiplyExact((long) bounds.width() - 1L, TerrainIntentV2.FIXED_SCALE);
        long maximumZ = Math.multiplyExact((long) bounds.length() - 1L, TerrainIntentV2.FIXED_SCALE);
        return switch (boundary) {
            case NORTH -> point.zMillionths() == 0L;
            case EAST -> point.xMillionths() == maximumX;
            case SOUTH -> point.zMillionths() == maximumZ;
            case WEST -> point.xMillionths() == 0L;
        };
    }

    private static long fanOpeningDegreesMillionths(
            DeltaPlanV2.FanPoint apex,
            DeltaPlanV2.FanPoint first,
            DeltaPlanV2.FanPoint second
    ) {
        long ax = first.xMillionths() - apex.xMillionths();
        long az = first.zMillionths() - apex.zMillionths();
        long bx = second.xMillionths() - apex.xMillionths();
        long bz = second.zMillionths() - apex.zMillionths();
        double cross = StrictMath.abs((double) ax * bz - (double) az * bx);
        double dot = (double) ax * bx + (double) az * bz;
        double degrees = StrictMath.toDegrees(StrictMath.atan2(cross, dot));
        return Math.round(degrees * TerrainIntentV2.FIXED_SCALE);
    }

    private static List<DeltaPlanV2.DistributaryBranch> branches(
            DeltaPlanV2.FanPoint apex,
            String apexNodeId,
            BoundarySpan span,
            int count,
            MeanderingRiverPlanV2 river,
            WorldBlueprintV2.Bounds bounds,
            int shallowDepth
    ) {
        List<DeltaPlanV2.DistributaryBranch> result = new ArrayList<>(count);
        int baseShare = TerrainIntentV2.FIXED_SCALE / count;
        int remainder = TerrainIntentV2.FIXED_SCALE % count;
        int halfWidth = Math.max(1, (river.selectedBankfullWidthBlocks() + count * 2 - 1) / (count * 2));
        long mouthBed = Math.min(river.mouthBedYMillionths(),
                Math.multiplyExact((long) bounds.waterLevel() - shallowDepth, TerrainIntentV2.FIXED_SCALE));
        for (int index = 0; index < count; index++) {
            long numerator = index + 1L;
            long denominator = count + 1L;
            DeltaPlanV2.FanPoint mouth = interpolate(span.first(), span.second(), numerator, denominator);
            DeltaPlanV2.FanPoint knee = new DeltaPlanV2.FanPoint(
                    DeltaFixedMathV2.roundDivide(apex.xMillionths() + mouth.xMillionths(), 2L),
                    DeltaFixedMathV2.roundDivide(apex.zMillionths() + mouth.zMillionths(), 2L));
            result.add(new DeltaPlanV2.DistributaryBranch(
                    String.format(java.util.Locale.ROOT, "distributary-%04d", index + 1),
                    apexNodeId,
                    String.format(java.util.Locale.ROOT, "mouth-%04d", index + 1),
                    List.of(apex, knee, mouth),
                    river.mouthBedYMillionths(),
                    mouthBed,
                    halfWidth,
                    baseShare + (index < remainder ? 1 : 0)));
        }
        return List.copyOf(result);
    }

    private static List<DeltaPlanV2.Sandbar> sandbars(
            BoundarySpan span,
            TerrainIntentV2.Edge boundary,
            int count,
            int shallowBand,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<DeltaPlanV2.Sandbar> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            DeltaPlanV2.FanPoint boundaryPoint = interpolate(span.first(), span.second(), index + 1L, count + 1L);
            int radius = 1 + index % 3;
            long inward = Math.multiplyExact((long) Math.min(shallowBand / 2, radius + 2),
                    TerrainIntentV2.FIXED_SCALE);
            DeltaPlanV2.FanPoint center = switch (boundary) {
                case NORTH -> new DeltaPlanV2.FanPoint(boundaryPoint.xMillionths(), inward);
                case EAST -> new DeltaPlanV2.FanPoint(
                        Math.multiplyExact((long) bounds.width() - 1L, TerrainIntentV2.FIXED_SCALE) - inward,
                        boundaryPoint.zMillionths());
                case SOUTH -> new DeltaPlanV2.FanPoint(
                        boundaryPoint.xMillionths(),
                        Math.multiplyExact((long) bounds.length() - 1L, TerrainIntentV2.FIXED_SCALE) - inward);
                case WEST -> new DeltaPlanV2.FanPoint(inward, boundaryPoint.zMillionths());
            };
            result.add(new DeltaPlanV2.Sandbar(
                    String.format(java.util.Locale.ROOT, "sandbar-%04d", index + 1), center, radius));
        }
        return List.copyOf(result);
    }

    private static DeltaPlanV2.FanPoint interpolate(
            DeltaPlanV2.FanPoint first,
            DeltaPlanV2.FanPoint second,
            long numerator,
            long denominator
    ) {
        return new DeltaPlanV2.FanPoint(
                Math.addExact(first.xMillionths(), DeltaFixedMathV2.roundDivide(
                        Math.multiplyExact(second.xMillionths() - first.xMillionths(), numerator), denominator)),
                Math.addExact(first.zMillionths(), DeltaFixedMathV2.roundDivide(
                        Math.multiplyExact(second.zMillionths() - first.zMillionths(), numerator), denominator)));
    }

    private static long estimatedWorkUnits(
            List<DeltaPlanV2.FanPoint> ring,
            int branchCount
    ) {
        long minX = ring.stream().mapToLong(DeltaPlanV2.FanPoint::xMillionths).min().orElseThrow();
        long maxX = ring.stream().mapToLong(DeltaPlanV2.FanPoint::xMillionths).max().orElseThrow();
        long minZ = ring.stream().mapToLong(DeltaPlanV2.FanPoint::zMillionths).min().orElseThrow();
        long maxZ = ring.stream().mapToLong(DeltaPlanV2.FanPoint::zMillionths).max().orElseThrow();
        long cells = Math.multiplyExact(
                Math.addExact(Math.floorDiv(maxX - minX, TerrainIntentV2.FIXED_SCALE), 1L),
                Math.addExact(Math.floorDiv(maxZ - minZ, TerrainIntentV2.FIXED_SCALE), 1L));
        long work = Math.multiplyExact(cells, Math.addExact(Math.multiplyExact((long) branchCount, 2L), 4L));
        if (work > DeltaPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
            throw failure("v2.delta-budget", "delta branch/raster CPU budget exceeded");
        }
        return Math.max(1L, work);
    }

    private static boolean insideOrOnRing(List<DeltaPlanV2.FanPoint> ring, DeltaPlanV2.FanPoint point) {
        boolean inside = false;
        for (int current = 0, previous = ring.size() - 2; current < ring.size() - 1; previous = current++) {
            DeltaPlanV2.FanPoint a = ring.get(current);
            DeltaPlanV2.FanPoint b = ring.get(previous);
            if (crossBig(a, b, point).signum() == 0
                    && point.xMillionths() >= Math.min(a.xMillionths(), b.xMillionths())
                    && point.xMillionths() <= Math.max(a.xMillionths(), b.xMillionths())
                    && point.zMillionths() >= Math.min(a.zMillionths(), b.zMillionths())
                    && point.zMillionths() <= Math.max(a.zMillionths(), b.zMillionths())) {
                return true;
            }
            boolean crosses = (a.zMillionths() > point.zMillionths()) != (b.zMillionths() > point.zMillionths());
            if (crosses) {
                BigInteger left = BigInteger.valueOf(point.xMillionths() - a.xMillionths())
                        .multiply(BigInteger.valueOf(b.zMillionths() - a.zMillionths()));
                BigInteger right = BigInteger.valueOf(b.xMillionths() - a.xMillionths())
                        .multiply(BigInteger.valueOf(point.zMillionths() - a.zMillionths()));
                boolean leftOf = b.zMillionths() > a.zMillionths()
                        ? left.compareTo(right) < 0 : left.compareTo(right) > 0;
                if (leftOf) inside = !inside;
            }
        }
        return inside;
    }

    private static BigInteger crossBig(
            DeltaPlanV2.FanPoint a,
            DeltaPlanV2.FanPoint b,
            DeltaPlanV2.FanPoint c
    ) {
        return BigInteger.valueOf(b.xMillionths() - a.xMillionths())
                .multiply(BigInteger.valueOf(c.zMillionths() - a.zMillionths()))
                .subtract(BigInteger.valueOf(b.zMillionths() - a.zMillionths())
                        .multiply(BigInteger.valueOf(c.xMillionths() - a.xMillionths())));
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static String derivedSuffix(String featureId) {
        return featureId.length() <= 48 ? featureId : Integer.toUnsignedString(featureId.hashCode(), 36);
    }

    private static DeltaGenerationException failure(String ruleId, String message) {
        return new DeltaGenerationException(ruleId, message);
    }

    private record TrunkBinding(String relationId, MeanderingRiverPlanV2 river) {
    }

    private record OutletBinding(String relationId, TerrainIntentV2.Edge boundary) {
    }

    private record BoundarySpan(DeltaPlanV2.FanPoint first, DeltaPlanV2.FanPoint second) {
    }
}
