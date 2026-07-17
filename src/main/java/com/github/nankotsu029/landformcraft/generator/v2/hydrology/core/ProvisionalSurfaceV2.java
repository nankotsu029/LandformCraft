package com.github.nankotsu029.landformcraft.generator.v2.hydrology.core;

import java.util.Objects;
import java.util.function.IntBinaryOperator;

/** Pure global-X/Z provisional elevation and routability source for the hydrology solver. */
public interface ProvisionalSurfaceV2 {
    long elevationMillionthsAt(int globalX, int globalZ);

    boolean routableAt(int globalX, int globalZ);

    static ProvisionalSurfaceV2 routable(IntBinaryOperator elevationMillionths) {
        Objects.requireNonNull(elevationMillionths, "elevationMillionths");
        return new ProvisionalSurfaceV2() {
            @Override
            public long elevationMillionthsAt(int globalX, int globalZ) {
                return elevationMillionths.applyAsInt(globalX, globalZ);
            }

            @Override
            public boolean routableAt(int globalX, int globalZ) {
                return true;
            }
        };
    }
}
