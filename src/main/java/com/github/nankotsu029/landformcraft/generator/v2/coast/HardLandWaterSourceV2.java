package com.github.nankotsu029.landformcraft.generator.v2.coast;

/** Optional release-local HARD land/water classification. A specified cell always wins. */
@FunctionalInterface
public interface HardLandWaterSourceV2 {
    HardLandWaterSourceV2 NONE = (x, z) -> Classification.UNSPECIFIED;

    Classification classificationAt(int globalX, int globalZ);

    enum Classification {
        UNSPECIFIED(-1),
        WATER(0),
        LAND(1);

        private final int rawValue;

        Classification(int rawValue) {
            this.rawValue = rawValue;
        }

        public int rawValue() {
            if (this == UNSPECIFIED) {
                throw new IllegalStateException("UNSPECIFIED has no categorical raw value");
            }
            return rawValue;
        }
    }
}
