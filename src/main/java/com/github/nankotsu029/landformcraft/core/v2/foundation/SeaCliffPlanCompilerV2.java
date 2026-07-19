package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSeaCliffModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.seacliff.SeaCliffFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SeaCliffPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one SEA_CLIFF spline feature. */
public final class SeaCliffPlanCompilerV2 {
    public SeaCliffPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.SEA_CLIFF) {
            throw failure("v2.sea-cliff-kind", "feature kind is not SEA_CLIFF");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)) {
            throw failure("v2.sea-cliff-geometry", "sea cliff requires SPLINE geometry");
        }
        TerrainIntentV2.SeaCliffParameters parameters =
                (TerrainIntentV2.SeaCliffParameters) feature.parameters();
        try {
            List<SeaCliffPlanV2.CenterlinePoint> centerline = flattenSpline(spline, bounds);
            if (centerline.size() < 2) {
                throw failure("v2.sea-cliff-geometry", "sea cliff centerline is too short");
            }
            int cliffHeight = midpoint(parameters.cliffHeightBlocks());
            int talusWidth = midpoint(parameters.talusWidthBlocks());
            int notchDepth = midpoint(parameters.notchDepthBlocks());
            int supportHalf = midpoint(parameters.supportHalfExtentXZBlocks());
            int support = Math.max(supportHalf, talusWidth);
            if (support > LandformSeaCliffModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.sea-cliff-budget", "sea cliff support radius exceeds trusted halo");
            }
            VolumeSdfAabbV2 hostSupportAabb = buildHostSupportAabb(
                    centerline, supportHalf, cliffHeight, bounds.waterLevel());
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > SeaCliffPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.sea-cliff-budget", "sea cliff profile/raster budget exceeded");
            }
            return new SeaCliffPlanV2(
                    SeaCliffPlanV2.VERSION,
                    feature.id(),
                    centerline,
                    cliffHeight,
                    talusWidth,
                    notchDepth,
                    parameters.seawardSide(),
                    supportHalf,
                    parameters.coastTransitionBandBlocks(),
                    hostSupportAabb,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    SeaCliffPlanV2.CLIFF_FACE_MASK_FIELD_ID,
                    SeaCliffPlanV2.TALUS_MASK_FIELD_ID,
                    SeaCliffPlanV2.NOTCH_MASK_FIELD_ID,
                    SeaCliffPlanV2.SOLID_OWNERSHIP_FIELD_ID,
                    SeaCliffPlanV2.VOLUME_HOST_OWNERSHIP_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.sea-cliff-budget", "sea cliff arithmetic overflow", exception);
        }
    }

    private static VolumeSdfAabbV2 buildHostSupportAabb(
            List<SeaCliffPlanV2.CenterlinePoint> centerline,
            int supportHalfExtentXZ,
            int cliffHeight,
            int waterLevel
    ) {
        SeaCliffPlanV2.CenterlinePoint mid = centerline.get(centerline.size() / 2);
        long half = Math.multiplyExact((long) supportHalfExtentXZ, TerrainIntentV2.FIXED_SCALE);
        long minY = Math.multiplyExact((long) waterLevel, TerrainIntentV2.FIXED_SCALE);
        long maxY = Math.multiplyExact(
                (long) waterLevel + cliffHeight, TerrainIntentV2.FIXED_SCALE);
        return new VolumeSdfAabbV2(
                Math.subtractExact(mid.xMillionths(), half),
                minY,
                Math.subtractExact(mid.zMillionths(), half),
                Math.addExact(mid.xMillionths(), half),
                maxY,
                Math.addExact(mid.zMillionths(), half));
    }

    private static List<SeaCliffPlanV2.CenterlinePoint> flattenSpline(
            TerrainIntentV2.SplineGeometry spline,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<SeaCliffPlanV2.CenterlinePoint> result = new ArrayList<>();
        long arc = 0L;
        long[] previous = null;
        for (TerrainIntentV2.Point2 point : spline.points()) {
            long x = Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L);
            long z = Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L);
            if (previous != null) {
                arc = Math.addExact(arc, SeaCliffFixedMathV2.hypot(x - previous[0], z - previous[1]));
            }
            result.add(new SeaCliffPlanV2.CenterlinePoint(x, z, arc));
            previous = new long[]{x, z};
        }
        if (result.getLast().arcLengthMillionths()
                < Math.multiplyExact(8L, TerrainIntentV2.FIXED_SCALE)) {
            throw failure("v2.sea-cliff-geometry", "sea cliff centerline is shorter than 8 blocks");
        }
        return List.copyOf(result);
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
