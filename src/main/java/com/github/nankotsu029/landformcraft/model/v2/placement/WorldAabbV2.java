package com.github.nankotsu029.landformcraft.model.v2.placement;

import java.util.Objects;

/**
 * Inclusive integer block AABB in world coordinates. Expansion and union use
 * {@link Math#addExact(int, int)}／{@link Math#subtractExact(int, int)} so overflow is hard-rejected.
 */
public record WorldAabbV2(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {
    public WorldAabbV2 {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("AABB min must be <= max");
        }
    }

    public long volumeBlocks() {
        long dx = Math.subtractExact((long) maxX, minX) + 1L;
        long dy = Math.subtractExact((long) maxY, minY) + 1L;
        long dz = Math.subtractExact((long) maxZ, minZ) + 1L;
        return Math.multiplyExact(Math.multiplyExact(dx, dy), dz);
    }

    public boolean contains(WorldAabbV2 other) {
        Objects.requireNonNull(other, "other");
        return minX <= other.minX && minY <= other.minY && minZ <= other.minZ
                && maxX >= other.maxX && maxY >= other.maxY && maxZ >= other.maxZ;
    }

    /** Inclusive point membership used by containment preflight scans. */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(WorldAabbV2 other) {
        Objects.requireNonNull(other, "other");
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public WorldAabbV2 union(WorldAabbV2 other) {
        Objects.requireNonNull(other, "other");
        return new WorldAabbV2(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY),
                Math.max(maxZ, other.maxZ));
    }

    public WorldAabbV2 expand(int radiusXZ, int radiusUp, int radiusDown) {
        if (radiusXZ < 0 || radiusUp < 0 || radiusDown < 0) {
            throw new IllegalArgumentException("expand radii must be non-negative");
        }
        return new WorldAabbV2(
                Math.subtractExact(minX, radiusXZ),
                Math.subtractExact(minY, radiusDown),
                Math.subtractExact(minZ, radiusXZ),
                Math.addExact(maxX, radiusXZ),
                Math.addExact(maxY, radiusUp),
                Math.addExact(maxZ, radiusXZ));
    }

    public static WorldAabbV2 fromTileCore(
            PlacementPlanV2.TileRefV2 tile,
            int minY,
            int maxY,
            int originX,
            int originZ
    ) {
        Objects.requireNonNull(tile, "tile");
        int minX = Math.addExact(originX, tile.coreMinX());
        int minZ = Math.addExact(originZ, tile.coreMinZ());
        int maxX = Math.addExact(minX, Math.subtractExact(tile.coreWidth(), 1));
        int maxZ = Math.addExact(minZ, Math.subtractExact(tile.coreLength(), 1));
        return new WorldAabbV2(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
