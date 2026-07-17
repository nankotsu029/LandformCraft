package com.github.nankotsu029.landformcraft.format.v2.tile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/** Unsigned Sponge palette VarInt codec, bounded to the V2 tile palette range 0..16383. */
public final class VarIntV2 {
    public static final int MAXIMUM_VALUE = 16_383;

    private VarIntV2() {
    }

    public static int encodedSize(int value) {
        requireValue(value);
        return value < 128 ? 1 : 2;
    }

    public static void write(OutputStream output, int value) throws IOException {
        Objects.requireNonNull(output, "output");
        requireValue(value);
        int remaining = value;
        do {
            int current = remaining & 0x7f;
            remaining >>>= 7;
            if (remaining != 0) current |= 0x80;
            output.write(current);
        } while (remaining != 0);
    }

    private static void requireValue(int value) {
        if (value < 0 || value > MAXIMUM_VALUE) {
            throw new IllegalArgumentException("V2 tile palette index must be within 0..16383");
        }
    }
}
