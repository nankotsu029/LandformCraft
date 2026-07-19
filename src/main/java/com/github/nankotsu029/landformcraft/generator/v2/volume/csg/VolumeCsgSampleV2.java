package com.github.nankotsu029.landformcraft.generator.v2.volume.csg;

/**
 * Sample result after applying the ordered CSG operator sequence at one point.
 * {@code fluidBodyId} is empty unless occupancy is {@link VolumeCsgOccupancyV2#FLUID}.
 */
public record VolumeCsgSampleV2(VolumeCsgOccupancyV2 occupancy, String fluidBodyId) {
    public VolumeCsgSampleV2 {
        if (occupancy == null) {
            throw new IllegalArgumentException("occupancy");
        }
        if (fluidBodyId == null) {
            throw new IllegalArgumentException("fluidBodyId");
        }
        if (occupancy == VolumeCsgOccupancyV2.FLUID) {
            if (fluidBodyId.isEmpty()) {
                throw new IllegalArgumentException("FLUID requires fluidBodyId");
            }
        } else if (!fluidBodyId.isEmpty()) {
            throw new IllegalArgumentException("non-FLUID forbids fluidBodyId");
        }
    }

    public static VolumeCsgSampleV2 air() {
        return new VolumeCsgSampleV2(VolumeCsgOccupancyV2.AIR, "");
    }

    public static VolumeCsgSampleV2 solid() {
        return new VolumeCsgSampleV2(VolumeCsgOccupancyV2.SOLID, "");
    }

    public static VolumeCsgSampleV2 fluid(String fluidBodyId) {
        return new VolumeCsgSampleV2(VolumeCsgOccupancyV2.FLUID, fluidBodyId);
    }
}
