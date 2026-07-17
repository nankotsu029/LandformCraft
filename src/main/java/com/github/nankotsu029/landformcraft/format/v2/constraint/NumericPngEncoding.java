package com.github.nankotsu029.landformcraft.format.v2.constraint;

import java.util.Objects;

/** Exact numeric interpretation of one grayscale PNG. No color or semantic-label inference occurs here. */
public record NumericPngEncoding(int decoderVersion, NumericKind kind, SampleType sampleType) {
    public static final int CURRENT_VERSION = 1;

    public NumericPngEncoding {
        if (decoderVersion > CURRENT_VERSION) {
            throw new ConstraintMapInputException(
                    ConstraintMapFailureCode.FUTURE_VERSION, "future numeric PNG decoder version");
        }
        if (decoderVersion != CURRENT_VERSION) {
            throw new ConstraintMapInputException(
                    ConstraintMapFailureCode.INVALID_DESCRIPTOR, "invalid numeric PNG decoder version");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sampleType, "sampleType");
    }

    public enum NumericKind {
        CATEGORICAL,
        HEIGHT
    }

    public enum SampleType {
        U8(1, 8),
        U16(2, 16);

        private final int bytes;
        private final int bits;

        SampleType(int bytes, int bits) {
            this.bytes = bytes;
            this.bits = bits;
        }

        public int bytes() {
            return bytes;
        }

        public int bits() {
            return bits;
        }
    }
}
