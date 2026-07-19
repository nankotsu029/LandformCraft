package com.github.nankotsu029.landformcraft.model.v2.volume;

/**
 * Release-local millionths XYZ used by volume SDF primitives.
 */
public record VolumeSdfVec3V2(long xMillionths, long yMillionths, long zMillionths) {
    public static final long TRUSTED_COORD_ABS_LIMIT =
            100_000L * VolumeSdfPrimitivePlanV2.Quantization.FIXED_SCALE;

    public VolumeSdfVec3V2 {
        requireFinite(xMillionths, "xMillionths");
        requireFinite(yMillionths, "yMillionths");
        requireFinite(zMillionths, "zMillionths");
    }

    public VolumeSdfVec3V2 translated(long dxMillionths, long dyMillionths, long dzMillionths) {
        return new VolumeSdfVec3V2(
                Math.addExact(xMillionths, dxMillionths),
                Math.addExact(yMillionths, dyMillionths),
                Math.addExact(zMillionths, dzMillionths));
    }

    private static void requireFinite(long value, String field) {
        if (Math.abs(value) > TRUSTED_COORD_ABS_LIMIT) {
            throw new IllegalArgumentException(field + " exceeds trusted coordinate range");
        }
    }
}
