package com.github.nankotsu029.landformcraft.format.v2.field;

/** One sampled fixed-point value, or an explicit no-data result. */
public record FieldSample(boolean noData, long valueMillionths) {
    public FieldSample {
        if (noData && valueMillionths != 0L) {
            throw new IllegalArgumentException("no-data sample must use the canonical zero payload");
        }
    }

    public static FieldSample value(long valueMillionths) {
        return new FieldSample(false, valueMillionths);
    }

    public static FieldSample missing() {
        return new FieldSample(true, 0L);
    }
}
