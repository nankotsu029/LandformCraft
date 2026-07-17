package com.github.nankotsu029.landformcraft.generator.v2.hydrology.river;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compiles one MEANDERING_RIVER Intent feature into a frozen source→mouth reach plan. */
public final class MeanderingRiverPlanCompilerV2 {
    private static final int MAXIMUM_POLYLINE_SEGMENTS = 1_024;

    public MeanderingRiverPlanV2 compile(
            TerrainIntentV2.Feature feature,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.MEANDERING_RIVER) {
            throw failure("v2.river-kind", "feature kind is not MEANDERING_RIVER");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)) {
            throw failure("v2.river-geometry", "river requires SPLINE geometry");
        }
        if (spline.points().size() < 2 || spline.points().size() > 256) {
            throw failure("v2.river-geometry", "river spline point count outside 2..256");
        }
        TerrainIntentV2.MeanderingRiverParameters parameters =
                (TerrainIntentV2.MeanderingRiverParameters) feature.parameters();
        try {
            List<long[]> guide = toBlockPoints(spline.points(), bounds.width(), bounds.length());
            rejectSelfIntersection(guide);
            List<long[]> polyline = flattenGuide(guide);
            long guideLength = polylineLength(polyline);
            if (guideLength < RiverFixedMathV2.FIXED_SCALE) {
                throw failure("v2.river-isolated-reach", "river centerline is shorter than one block");
            }

            int selectedWidth = parameters.bankfullWidthBlocks().minimum()
                    + (parameters.bankfullWidthBlocks().maximum() - parameters.bankfullWidthBlocks().minimum()) / 2;
            int bankWidth = Math.max(1, (selectedWidth + 3) / 4);
            int floodplainWidth = Math.min(128, Math.max(selectedWidth, selectedWidth * 2));
            int amplitude;
            int wavelength;
            if (parameters.variant() == TerrainIntentV2.RiverVariant.RIVER) {
                amplitude = 0;
                wavelength = 0;
            } else {
                amplitude = Math.min(64, Math.max(1, selectedWidth * 2));
                wavelength = Math.min(256, Math.max(selectedWidth * 4, selectedWidth * 8));
            }
            int corridorHalf = Math.min(192, Math.max(selectedWidth, selectedWidth / 2 + amplitude + bankWidth));
            int support = Math.min(HydrologyRiverModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ,
                    Math.max(corridorHalf, floodplainWidth));
            if (support < corridorHalf || support < floodplainWidth) {
                throw failure("v2.river-budget", "river support radius exceeds trusted halo budget");
            }
            if (Math.min(bounds.width(), bounds.length()) < selectedWidth * 2 + 1) {
                throw failure("v2.river-width-conflict", "world bounds cannot contain the bankfull width");
            }

            List<MeanderingRiverPlanV2.CenterlineSample> centerline = shapeCenterline(
                    polyline, guideLength, parameters, selectedWidth, amplitude, wavelength, bounds);
            long totalLength = centerline.getLast().arcLengthMillionths();
            if (totalLength < RiverFixedMathV2.FIXED_SCALE) {
                throw failure("v2.river-isolated-reach", "shaped river centerline collapsed");
            }
            long drop = Math.subtractExact(
                    centerline.getFirst().bedYMillionths(), centerline.getLast().bedYMillionths());
            long requiredDrop = RiverFixedMathV2.roundDivide(
                    Math.multiplyExact(totalLength, parameters.minimumBedSlopeMillionths()),
                    RiverFixedMathV2.FIXED_SCALE);
            if (drop < requiredDrop) {
                throw failure("v2.river-reverse-gradient", "compiled bed slope is below the declared minimum");
            }
            for (int index = 1; index < centerline.size(); index++) {
                if (centerline.get(index).bedYMillionths() > centerline.get(index - 1).bedYMillionths()) {
                    throw failure("v2.river-reverse-gradient", "bed profile increases downstream");
                }
            }

            int dischargeIndex = switch (parameters.dischargeClass()) {
                case SMALL -> 1;
                case MEDIUM -> 2;
                case LARGE -> 3;
            };
            long depth = Math.multiplyExact((long) Math.max(1, selectedWidth / 4),
                    (long) RiverFixedMathV2.FIXED_SCALE);

            return new MeanderingRiverPlanV2(
                    MeanderingRiverPlanV2.VERSION,
                    feature.id(),
                    parameters.variant(),
                    parameters.dischargeClass(),
                    "basin-" + feature.id(),
                    "water-" + feature.id(),
                    "reach-" + feature.id(),
                    "source-" + feature.id(),
                    "mouth-" + feature.id(),
                    centerline,
                    totalLength,
                    centerline.getFirst().bedYMillionths(),
                    centerline.getLast().bedYMillionths(),
                    depth,
                    parameters.bankfullWidthBlocks().minimum(),
                    parameters.bankfullWidthBlocks().maximum(),
                    selectedWidth,
                    bankWidth,
                    floodplainWidth,
                    amplitude,
                    wavelength,
                    corridorHalf,
                    parameters.minimumBedSlopeMillionths(),
                    dischargeIndex,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    HydrologyRiverModuleV2.CHANNEL_MASK_FIELD_ID,
                    HydrologyRiverModuleV2.BANK_MASK_FIELD_ID,
                    HydrologyRiverModuleV2.FLOODPLAIN_MASK_FIELD_ID,
                    HydrologyRiverModuleV2.MEANDER_CORRIDOR_FIELD_ID,
                    HydrologyRiverModuleV2.LOCAL_WIDTH_FIELD_ID,
                    HydrologyRiverModuleV2.DISCHARGE_INDEX_FIELD_ID,
                    HydrologyIrModuleV2.BED_ELEVATION_FIELD,
                    HydrologyIrModuleV2.WATER_SURFACE_FIELD,
                    HydrologyIrModuleV2.WATER_DEPTH_FIELD,
                    HydrologyIrModuleV2.WATER_BODY_ID_FIELD,
                    support,
                    geometryChecksum);
        } catch (ArithmeticException exception) {
            throw new RiverGenerationException("v2.river-overflow", "river plan arithmetic overflow", exception);
        }
    }

    private static List<MeanderingRiverPlanV2.CenterlineSample> shapeCenterline(
            List<long[]> polyline,
            long guideLength,
            TerrainIntentV2.MeanderingRiverParameters parameters,
            int selectedWidth,
            int amplitude,
            int wavelength,
            WorldBlueprintV2.Bounds bounds
    ) {
        long sourceBed = Math.multiplyExact((long) bounds.waterLevel(), RiverFixedMathV2.FIXED_SCALE);
        long requiredDrop = RiverFixedMathV2.roundDivide(
                Math.multiplyExact(guideLength, parameters.minimumBedSlopeMillionths()),
                RiverFixedMathV2.FIXED_SCALE);
        long mouthBed = Math.subtractExact(sourceBed, Math.max(requiredDrop, RiverFixedMathV2.FIXED_SCALE));
        if (mouthBed < Math.multiplyExact((long) bounds.minY(), RiverFixedMathV2.FIXED_SCALE)
                || sourceBed > Math.multiplyExact((long) bounds.maxY(), RiverFixedMathV2.FIXED_SCALE)) {
            throw failure("v2.river-vertical-bounds", "river bed profile exceeds vertical generation bounds");
        }

        List<MeanderingRiverPlanV2.CenterlineSample> samples = new ArrayList<>();
        long travelled = 0L;
        long shapedTravelled = 0L;
        long previousX = Long.MIN_VALUE;
        long previousZ = Long.MIN_VALUE;
        for (int index = 0; index < polyline.size(); index++) {
            long[] point = polyline.get(index);
            if (index > 0) {
                travelled = Math.addExact(travelled,
                        RiverFixedMathV2.hypotMillionths(
                                point[0] - polyline.get(index - 1)[0],
                                point[1] - polyline.get(index - 1)[1]));
            }
            long x = point[0];
            long z = point[1];
            if (amplitude > 0 && wavelength > 0 && index > 0 && index < polyline.size() - 1) {
                long[] tangent = unitTangent(polyline, index);
                long phase = RiverFixedMathV2.roundDivide(travelled, wavelength);
                int sine = RiverFixedMathV2.sinTurnMillionths(phase);
                long offset = RiverFixedMathV2.roundDivide(
                        Math.multiplyExact((long) amplitude * RiverFixedMathV2.FIXED_SCALE, sine),
                        RiverFixedMathV2.FIXED_SCALE);
                // Perpendicular to tangent (tx,tz) is (-tz, tx).
                x = Math.addExact(x, RiverFixedMathV2.roundDivide(
                        Math.multiplyExact(-tangent[1], offset), RiverFixedMathV2.FIXED_SCALE));
                z = Math.addExact(z, RiverFixedMathV2.roundDivide(
                        Math.multiplyExact(tangent[0], offset), RiverFixedMathV2.FIXED_SCALE));
            }
            x = clampCoordinate(x, bounds.width());
            z = clampCoordinate(z, bounds.length());
            if (previousX != Long.MIN_VALUE) {
                shapedTravelled = Math.addExact(shapedTravelled,
                        RiverFixedMathV2.hypotMillionths(x - previousX, z - previousZ));
            }
            long bed = Math.subtractExact(sourceBed, RiverFixedMathV2.roundDivide(
                    Math.multiplyExact(Math.subtractExact(sourceBed, mouthBed), travelled), guideLength));
            int widthAt = parameters.bankfullWidthBlocks().minimum()
                    + RiverFixedMathV2.clampInt(
                    RiverFixedMathV2.roundDivide(
                            Math.multiplyExact(
                                    (long) (selectedWidth - parameters.bankfullWidthBlocks().minimum()),
                                    travelled),
                            guideLength),
                    0,
                    selectedWidth - parameters.bankfullWidthBlocks().minimum());
            int halfWidth = Math.max(1, (widthAt + 1) / 2);
            samples.add(new MeanderingRiverPlanV2.CenterlineSample(
                    index, shapedTravelled, x, z, bed, halfWidth));
            previousX = x;
            previousZ = z;
        }
        if (samples.size() >= 2) {
            MeanderingRiverPlanV2.CenterlineSample last = samples.getLast();
            samples.set(samples.size() - 1, new MeanderingRiverPlanV2.CenterlineSample(
                    last.sequence(), shapedTravelled, last.xMillionths(), last.zMillionths(),
                    mouthBed, last.localHalfWidthBlocks()));
        }
        return List.copyOf(samples);
    }

    private static long[] unitTangent(List<long[]> polyline, int index) {
        long dx = polyline.get(index + 1)[0] - polyline.get(index - 1)[0];
        long dz = polyline.get(index + 1)[1] - polyline.get(index - 1)[1];
        long length = RiverFixedMathV2.hypotMillionths(dx, dz);
        if (length == 0L) {
            dx = polyline.get(index)[0] - polyline.get(index - 1)[0];
            dz = polyline.get(index)[1] - polyline.get(index - 1)[1];
            length = RiverFixedMathV2.hypotMillionths(dx, dz);
        }
        if (length == 0L) return new long[] {RiverFixedMathV2.FIXED_SCALE, 0L};
        return new long[] {
                RiverFixedMathV2.roundDivide(Math.multiplyExact(dx, RiverFixedMathV2.FIXED_SCALE), length),
                RiverFixedMathV2.roundDivide(Math.multiplyExact(dz, RiverFixedMathV2.FIXED_SCALE), length)
        };
    }

    private static List<long[]> toBlockPoints(List<TerrainIntentV2.Point2> points, int width, int length) {
        List<long[]> result = new ArrayList<>(points.size());
        for (TerrainIntentV2.Point2 point : points) {
            result.add(new long[] {
                    Math.multiplyExact((long) point.xMillionths(), width - 1L),
                    Math.multiplyExact((long) point.zMillionths(), length - 1L)
            });
        }
        return result;
    }

    private static List<long[]> flattenGuide(List<long[]> guide) {
        List<long[]> result = new ArrayList<>();
        result.add(guide.getFirst());
        for (int index = 1; index < guide.size(); index++) {
            long[] from = guide.get(index - 1);
            long[] to = guide.get(index);
            long length = RiverFixedMathV2.hypotMillionths(to[0] - from[0], to[1] - from[1]);
            int steps = Math.toIntExact(Math.max(1L, RiverFixedMathV2.roundDivide(length, RiverFixedMathV2.FIXED_SCALE)));
            if (result.size() + steps > MAXIMUM_POLYLINE_SEGMENTS) {
                throw failure("v2.river-budget", "river centerline exceeds polyline sample budget");
            }
            for (int step = 1; step <= steps; step++) {
                long x = from[0] + RiverFixedMathV2.roundDivide(
                        Math.multiplyExact(to[0] - from[0], step), steps);
                long z = from[1] + RiverFixedMathV2.roundDivide(
                        Math.multiplyExact(to[1] - from[1], step), steps);
                result.add(new long[] {x, z});
            }
        }
        return result;
    }

    private static long polylineLength(List<long[]> polyline) {
        long length = 0L;
        for (int index = 1; index < polyline.size(); index++) {
            length = Math.addExact(length, RiverFixedMathV2.hypotMillionths(
                    polyline.get(index)[0] - polyline.get(index - 1)[0],
                    polyline.get(index)[1] - polyline.get(index - 1)[1]));
        }
        return length;
    }

    private static void rejectSelfIntersection(List<long[]> points) {
        for (int first = 0; first < points.size() - 1; first++) {
            for (int second = first + 2; second < points.size() - 1; second++) {
                if (segmentsIntersect(points.get(first), points.get(first + 1),
                        points.get(second), points.get(second + 1))) {
                    throw failure("v2.river-geometry", "river guide spline self-intersects");
                }
            }
        }
    }

    private static boolean segmentsIntersect(long[] a, long[] b, long[] c, long[] d) {
        long abC = cross(a, b, c);
        long abD = cross(a, b, d);
        long cdA = cross(c, d, a);
        long cdB = cross(c, d, b);
        return Long.signum(abC) != Long.signum(abD) && Long.signum(cdA) != Long.signum(cdB);
    }

    private static long cross(long[] a, long[] b, long[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static long clampCoordinate(long value, int extentBlocks) {
        long maximum = Math.multiplyExact((long) extentBlocks - 1L, RiverFixedMathV2.FIXED_SCALE);
        if (value < 0L) return 0L;
        if (value > maximum) return maximum;
        return value;
    }

    private static RiverGenerationException failure(String ruleId, String message) {
        return new RiverGenerationException(ruleId, message);
    }
}
