package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.river.RiverFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one general RIVER spline feature (single source→mouth reach). */
public final class RiverPlanCompilerV2 {
    public RiverPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.RIVER) {
            throw failure("v2.river-kind", "feature kind is not RIVER");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)) {
            throw failure("v2.river-geometry", "river requires SPLINE geometry");
        }
        if (spline.points().size() < 2 || spline.points().size() > 256) {
            throw failure("v2.river-geometry", "river spline point count outside 2..256");
        }
        TerrainIntentV2.RiverParameters parameters =
                (TerrainIntentV2.RiverParameters) feature.parameters();
        if (parameters.maxReachCount() < 1) {
            throw failure("v2.river-budget", "maxReachCount budget violated");
        }
        try {
            List<long[]> points = toBlockPoints(spline.points(), bounds.width(), bounds.length());
            long arc = 0L;
            List<RiverPlanV2.CenterlineSample> centerline = new ArrayList<>();
            long[] previous = null;
            long sourceBed = Math.multiplyExact((long) bounds.waterLevel() + 8L, TerrainIntentV2.FIXED_SCALE);
            for (int index = 0; index < points.size(); index++) {
                long[] point = points.get(index);
                if (previous != null) {
                    arc = Math.addExact(arc, RiverFixedMathV2.hypot(point[0] - previous[0], point[1] - previous[1]));
                }
                centerline.add(new RiverPlanV2.CenterlineSample(index, point[0], point[1], arc, sourceBed));
                previous = point;
            }
            if (arc < TerrainIntentV2.FIXED_SCALE) {
                throw failure("v2.river-geometry", "river centerline is shorter than one block");
            }
            long requiredDrop = RiverFixedMathV2.roundDivide(
                    Math.multiplyExact(arc, parameters.minimumBedSlopeMillionths()),
                    TerrainIntentV2.FIXED_SCALE);
            long mouthBed = Math.subtractExact(sourceBed, Math.max(requiredDrop, TerrainIntentV2.FIXED_SCALE));
            List<RiverPlanV2.CenterlineSample> withBed = new ArrayList<>();
            for (RiverPlanV2.CenterlineSample sample : centerline) {
                long bed = sourceBed - RiverFixedMathV2.roundDivide(
                        Math.multiplyExact(sample.arcLengthMillionths(), Math.subtractExact(sourceBed, mouthBed)),
                        arc);
                withBed.add(new RiverPlanV2.CenterlineSample(
                        sample.sequence(), sample.xMillionths(), sample.zMillionths(),
                        sample.arcLengthMillionths(), bed));
            }
            int width = midpoint(parameters.bankfullWidthBlocks());
            int bank = Math.max(1, (width + 3) / 4);
            int floodplain = midpoint(parameters.floodplainHandoffWidthBlocks());
            int support = Math.min(LandformRiverModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ, floodplain);
            if (support < floodplain) {
                throw failure("v2.river-budget", "river support radius exceeds trusted halo");
            }
            long work = Math.multiplyExact(Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > RiverPlanV2.MAXIMUM_GRAPH_WORK_UNITS) {
                throw failure("v2.river-budget", "river graph/raster budget exceeded");
            }
            RiverPlanV2.CenterlineSample first = withBed.getFirst();
            RiverPlanV2.CenterlineSample last = withBed.getLast();
            String sourceId = "source-" + feature.id() + "-1";
            String mouthId = "mouth-" + feature.id() + "-1";
            String reachId = "reach-" + feature.id() + "-1";
            List<RiverPlanV2.Node> nodes = List.of(
                    new RiverPlanV2.Node(sourceId, RiverPlanV2.NodeKind.SOURCE, RiverPlanV2.NodeRole.HEADWATER,
                            first.xMillionths(), first.zMillionths(), first.bedYMillionths()),
                    new RiverPlanV2.Node(mouthId, RiverPlanV2.NodeKind.MOUTH, RiverPlanV2.NodeRole.NONE,
                            last.xMillionths(), last.zMillionths(), last.bedYMillionths()));
            List<RiverPlanV2.Reach> reaches = List.of(new RiverPlanV2.Reach(
                    reachId, sourceId, mouthId,
                    RiverPlanV2.ReachClass.MAIN_STEM,
                    List.of(),
                    RiverPlanV2.DISCHARGE_SHARE_TOTAL));
            if (reaches.size() > parameters.maxReachCount()) {
                throw failure("v2.river-budget", "reach count exceeds maxReachCount");
            }
            int dischargeIndex = switch (parameters.dischargeClass()) {
                case SMALL -> 1;
                case MEDIUM -> 2;
                case LARGE -> 3;
            };
            return new RiverPlanV2(
                    RiverPlanV2.VERSION,
                    feature.id(),
                    parameters.dischargeClass(),
                    nodes,
                    reaches,
                    List.of(),
                    withBed,
                    arc,
                    first.bedYMillionths(),
                    last.bedYMillionths(),
                    width,
                    bank,
                    floodplain,
                    parameters.minimumBedSlopeMillionths(),
                    dischargeIndex,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    RiverPlanV2.CHANNEL_MASK_FIELD_ID,
                    RiverPlanV2.BANK_MASK_FIELD_ID,
                    RiverPlanV2.FLOODPLAIN_MASK_FIELD_ID,
                    RiverPlanV2.BED_ELEVATION_FIELD_ID,
                    RiverPlanV2.DISCHARGE_INDEX_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException("v2.river-budget", "river arithmetic overflow", exception);
        }
    }

    /** Corruption helpers for graph budget rejection without mutating Intent fixtures. */
    public static void assertRejectsSelfLoopAndOrphan() {
        try {
            List<RiverPlanV2.CenterlineSample> samples = List.of(
                    new RiverPlanV2.CenterlineSample(0, 0, 0, 0, 70_000_000L),
                    new RiverPlanV2.CenterlineSample(1, 2_000_000L, 2_000_000L, 2_000_000L, 69_000_000L));
            new RiverPlanV2(
                    RiverPlanV2.VERSION,
                    "orphan-river",
                    TerrainIntentV2.DischargeClass.SMALL,
                    List.of(
                            new RiverPlanV2.Node("source-a", RiverPlanV2.NodeKind.SOURCE,
                                    RiverPlanV2.NodeRole.HEADWATER, 0, 0, 70_000_000L),
                            new RiverPlanV2.Node("mouth-a", RiverPlanV2.NodeKind.MOUTH,
                                    RiverPlanV2.NodeRole.NONE, 2_000_000L, 2_000_000L, 69_000_000L)),
                    List.of(new RiverPlanV2.Reach(
                            "r1", "source-a", "missing-node",
                            RiverPlanV2.ReachClass.MAIN_STEM, List.of(), RiverPlanV2.DISCHARGE_SHARE_TOTAL)),
                    List.of(),
                    samples,
                    2_000_000L,
                    70_000_000L,
                    69_000_000L,
                    4, 1, 8, 1_000L, 1,
                    0, 256, 62, 32, 32,
                    RiverPlanV2.CHANNEL_MASK_FIELD_ID,
                    RiverPlanV2.BANK_MASK_FIELD_ID,
                    RiverPlanV2.FLOODPLAIN_MASK_FIELD_ID,
                    RiverPlanV2.BED_ELEVATION_FIELD_ID,
                    RiverPlanV2.DISCHARGE_INDEX_FIELD_ID,
                    8, 100L, "0".repeat(64), "0".repeat(64));
            throw new AssertionError("expected orphan rejection");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
        try {
            List<RiverPlanV2.CenterlineSample> samples = List.of(
                    new RiverPlanV2.CenterlineSample(0, 0, 0, 0, 70_000_000L),
                    new RiverPlanV2.CenterlineSample(1, 2_000_000L, 2_000_000L, 2_000_000L, 69_000_000L));
            new RiverPlanV2(
                    RiverPlanV2.VERSION,
                    "loop-river",
                    TerrainIntentV2.DischargeClass.SMALL,
                    List.of(
                            new RiverPlanV2.Node("source-a", RiverPlanV2.NodeKind.SOURCE,
                                    RiverPlanV2.NodeRole.HEADWATER, 0, 0, 70_000_000L),
                            new RiverPlanV2.Node("mouth-a", RiverPlanV2.NodeKind.MOUTH,
                                    RiverPlanV2.NodeRole.NONE, 2_000_000L, 2_000_000L, 69_000_000L)),
                    List.of(new RiverPlanV2.Reach(
                            "r1", "source-a", "source-a",
                            RiverPlanV2.ReachClass.MAIN_STEM, List.of(), RiverPlanV2.DISCHARGE_SHARE_TOTAL)),
                    List.of(),
                    samples,
                    2_000_000L,
                    70_000_000L,
                    69_000_000L,
                    4, 1, 8, 1_000L, 1,
                    0, 256, 62, 32, 32,
                    RiverPlanV2.CHANNEL_MASK_FIELD_ID,
                    RiverPlanV2.BANK_MASK_FIELD_ID,
                    RiverPlanV2.FLOODPLAIN_MASK_FIELD_ID,
                    RiverPlanV2.BED_ELEVATION_FIELD_ID,
                    RiverPlanV2.DISCHARGE_INDEX_FIELD_ID,
                    8, 100L, "0".repeat(64), "0".repeat(64));
            throw new AssertionError("expected self-loop rejection");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }

    private static List<long[]> toBlockPoints(
            List<TerrainIntentV2.Point2> points,
            int width,
            int length
    ) {
        List<long[]> result = new ArrayList<>();
        for (TerrainIntentV2.Point2 point : points) {
            result.add(new long[]{
                    Math.multiplyExact((long) point.xMillionths(), width - 1L),
                    Math.multiplyExact((long) point.zMillionths(), length - 1L)
            });
        }
        return result;
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
