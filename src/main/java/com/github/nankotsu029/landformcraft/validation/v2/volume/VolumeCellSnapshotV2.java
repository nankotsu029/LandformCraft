package com.github.nankotsu029.landformcraft.validation.v2.volume;

/**
 * Public XZ cell snapshot projected from sealed volume descriptors for diagnostic preview.
 * Values are compact codes — never decoded dense voxels.
 */
public record VolumeCellSnapshotV2(
        int aabbMask,
        int operatorOrdinal,
        int ySliceOccupancy,
        int solidFluidCode,
        int surfaceClassCode
) {
    public static final int SOLID_FLUID_EMPTY = 0;
    public static final int SOLID_FLUID_SOLID = 1;
    public static final int SOLID_FLUID_FLUID = 2;
    public static final int SOLID_FLUID_CONFLICT = 3;

    public VolumeCellSnapshotV2 {
        if (aabbMask < 0 || aabbMask > 1
                || operatorOrdinal < 0 || operatorOrdinal > 255
                || ySliceOccupancy < 0 || ySliceOccupancy > 1
                || solidFluidCode < 0 || solidFluidCode > 3
                || surfaceClassCode < 0 || surfaceClassCode > 255) {
            throw new IllegalArgumentException("volume cell snapshot fields out of range");
        }
    }
}
