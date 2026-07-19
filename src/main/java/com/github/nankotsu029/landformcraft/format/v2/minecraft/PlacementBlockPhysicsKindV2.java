package com.github.nankotsu029.landformcraft.format.v2.minecraft;

/**
 * Closed physics classification for a Minecraft block identifier (properties stripped).
 * Unknown identifiers are not represented here — callers must hard-reject them.
 */
public enum PlacementBlockPhysicsKindV2 {
    SOLID,
    AIR,
    FLUID,
    GRAVITY,
    NEIGHBOR,
    UNSUPPORTED
}
