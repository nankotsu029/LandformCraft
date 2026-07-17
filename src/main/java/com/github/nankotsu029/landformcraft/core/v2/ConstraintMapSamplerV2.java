package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;

import java.util.Objects;

/**
 * Integer-only pixel-center mapping for V2-1 raster constraint maps.
 * Transform order is rotation, flip, crop; sampling never uses platform floating point.
 */
public final class ConstraintMapSamplerV2 {
    public static final String CANONICALIZATION_VERSION = "constraint-pixel-center-fixed-v1";
    public static final int FIXED_SCALE = 1_000_000;

    private final int rawWidth;
    private final int rawLength;
    private final int rotatedWidth;
    private final int rotatedLength;
    private final GenerationRequestV2.CoordinateMapping mapping;

    public ConstraintMapSamplerV2(
            int rawWidth,
            int rawLength,
            GenerationRequestV2.CoordinateMapping mapping
    ) {
        if (rawWidth < 1 || rawWidth > 4_096 || rawLength < 1 || rawLength > 4_096) {
            throw new IllegalArgumentException("raw raster dimensions outside 1..4096");
        }
        this.rawWidth = rawWidth;
        this.rawLength = rawLength;
        this.mapping = Objects.requireNonNull(mapping, "mapping");
        this.rotatedWidth = switch (mapping.rotation()) {
            case DEGREES_0, DEGREES_180 -> rawWidth;
            case DEGREES_90, DEGREES_270 -> rawLength;
        };
        this.rotatedLength = switch (mapping.rotation()) {
            case DEGREES_0, DEGREES_180 -> rawLength;
            case DEGREES_90, DEGREES_270 -> rawWidth;
        };
        var crop = mapping.crop();
        if ((long) crop.x() + crop.width() > rotatedWidth
                || (long) crop.z() + crop.length() > rotatedLength) {
            throw new IllegalArgumentException("crop lies outside the transformed raster");
        }
    }

    public int sampleNearest(
            int targetX,
            int targetZ,
            int targetWidth,
            int targetLength,
            RawSampleSource source
    ) {
        requireTarget(targetX, targetZ, targetWidth, targetLength);
        Objects.requireNonNull(source, "source");
        var crop = mapping.crop();
        int localX = nearestPixel(targetX, targetWidth, crop.width());
        int localZ = nearestPixel(targetZ, targetLength, crop.length());
        RawCoordinate raw = rawCoordinate(crop.x() + localX, crop.z() + localZ);
        return source.rawSampleAt(raw.x(), raw.z());
    }

    public SemanticSample sampleFixedBilinear(
            int targetX,
            int targetZ,
            int targetWidth,
            int targetLength,
            SemanticSampleSource source
    ) {
        requireTarget(targetX, targetZ, targetWidth, targetLength);
        Objects.requireNonNull(source, "source");
        var crop = mapping.crop();
        AxisSample x = bilinearAxis(targetX, targetWidth, crop.width());
        AxisSample z = bilinearAxis(targetZ, targetLength, crop.length());

        SemanticSample northWest = sample(source, crop.x() + x.lower(), crop.z() + z.lower());
        if (x.weightMillionths() == 0 && z.weightMillionths() == 0) return northWest;
        SemanticSample northEast = x.weightMillionths() == 0
                ? northWest : sample(source, crop.x() + x.upper(), crop.z() + z.lower());
        SemanticSample southWest = z.weightMillionths() == 0
                ? northWest : sample(source, crop.x() + x.lower(), crop.z() + z.upper());
        SemanticSample southEast;
        if (x.weightMillionths() == 0) {
            southEast = southWest;
        } else if (z.weightMillionths() == 0) {
            southEast = northEast;
        } else {
            southEast = sample(source, crop.x() + x.upper(), crop.z() + z.upper());
        }
        if (northWest.noData() || northEast.noData() || southWest.noData() || southEast.noData()) {
            return SemanticSample.missing();
        }
        long north = interpolate(
                northWest.valueMillionths(), northEast.valueMillionths(), x.weightMillionths());
        long south = interpolate(
                southWest.valueMillionths(), southEast.valueMillionths(), x.weightMillionths());
        return SemanticSample.value(interpolate(north, south, z.weightMillionths()));
    }

    private SemanticSample sample(SemanticSampleSource source, int transformedX, int transformedZ) {
        RawCoordinate raw = rawCoordinate(transformedX, transformedZ);
        return Objects.requireNonNull(source.semanticSampleAt(raw.x(), raw.z()), "semantic sample");
    }

    private RawCoordinate rawCoordinate(int transformedX, int transformedZ) {
        if (transformedX < 0 || transformedX >= rotatedWidth
                || transformedZ < 0 || transformedZ >= rotatedLength) {
            throw new IndexOutOfBoundsException("coordinate outside transformed raster");
        }
        int rotatedX = mapping.flipX() ? rotatedWidth - 1 - transformedX : transformedX;
        int rotatedZ = mapping.flipZ() ? rotatedLength - 1 - transformedZ : transformedZ;
        return switch (mapping.rotation()) {
            case DEGREES_0 -> new RawCoordinate(rotatedX, rotatedZ);
            case DEGREES_90 -> new RawCoordinate(rotatedZ, rawLength - 1 - rotatedX);
            case DEGREES_180 -> new RawCoordinate(rawWidth - 1 - rotatedX, rawLength - 1 - rotatedZ);
            case DEGREES_270 -> new RawCoordinate(rawWidth - 1 - rotatedZ, rotatedX);
        };
    }

    private static int nearestPixel(int target, int targetExtent, int sourceExtent) {
        long numerator = Math.multiplyExact(2L * target + 1L, sourceExtent);
        return (int) Math.min(sourceExtent - 1L, numerator / (2L * targetExtent));
    }

    private static AxisSample bilinearAxis(int target, int targetExtent, int sourceExtent) {
        long denominator = 2L * targetExtent;
        long numerator = Math.subtractExact(
                Math.multiplyExact(2L * target + 1L, sourceExtent), targetExtent);
        if (numerator <= 0L) return new AxisSample(0, 0, 0);
        long maximum = Math.multiplyExact((long) sourceExtent - 1L, denominator);
        if (numerator >= maximum) {
            int last = sourceExtent - 1;
            return new AxisSample(last, last, 0);
        }
        int lower = (int) (numerator / denominator);
        long remainder = numerator % denominator;
        int weight = (int) ((remainder * FIXED_SCALE + denominator / 2L) / denominator);
        if (weight == FIXED_SCALE) return new AxisSample(lower + 1, lower + 1, 0);
        return new AxisSample(lower, lower + 1, weight);
    }

    private static long interpolate(long first, long second, int weightMillionths) {
        if (weightMillionths == 0 || first == second) return first;
        long product = Math.multiplyExact(Math.subtractExact(second, first), weightMillionths);
        long adjustment = divideRoundHalfAwayFromZero(product, FIXED_SCALE);
        return Math.addExact(first, adjustment);
    }

    private static long divideRoundHalfAwayFromZero(long numerator, long denominator) {
        long magnitude = Math.abs(numerator);
        long rounded = (magnitude + denominator / 2L) / denominator;
        return numerator < 0 ? -rounded : rounded;
    }

    private static void requireTarget(int x, int z, int width, int length) {
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000
                || x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("coordinate outside target field");
        }
    }

    @FunctionalInterface
    public interface RawSampleSource {
        int rawSampleAt(int x, int z);
    }

    @FunctionalInterface
    public interface SemanticSampleSource {
        SemanticSample semanticSampleAt(int x, int z);
    }

    public record SemanticSample(boolean noData, long valueMillionths) {
        public SemanticSample {
            if (noData && valueMillionths != 0L) {
                throw new IllegalArgumentException("no-data sample requires canonical zero payload");
            }
        }

        public static SemanticSample value(long valueMillionths) {
            return new SemanticSample(false, valueMillionths);
        }

        public static SemanticSample missing() {
            return new SemanticSample(true, 0L);
        }
    }

    private record AxisSample(int lower, int upper, int weightMillionths) { }

    private record RawCoordinate(int x, int z) { }
}
