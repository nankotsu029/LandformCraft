package com.github.nankotsu029.landformcraft.generator.v2.volume.csg;

/**
 * Occupancy class produced by ordered CSG. CARVE_SOLID never introduces fluid and never
 * claims ownership of an existing fluid cell.
 */
public enum VolumeCsgOccupancyV2 {
    AIR,
    SOLID,
    FLUID
}
