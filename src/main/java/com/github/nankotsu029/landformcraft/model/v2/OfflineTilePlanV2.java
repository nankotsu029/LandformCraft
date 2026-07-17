package com.github.nankotsu029.landformcraft.model.v2;

/** Bounded release-local tile window consumed by the V2 offline block-stream exporter. */
public record OfflineTilePlanV2(
        int tilePlanVersion,
        String tileId,
        int xIndex,
        int zIndex,
        int originX,
        int originZ,
        int width,
        int length,
        int minY,
        int maxY
) {
    public static final int VERSION = 1;
    public static final int MAXIMUM_HORIZONTAL_EXTENT = 256;
    public static final int MAXIMUM_VERTICAL_EXTENT = 512;

    public OfflineTilePlanV2 {
        if (tilePlanVersion != VERSION) throw new IllegalArgumentException("tilePlanVersion must be 1");
        tileId = V2Validation.qualifiedId(tileId, "tileId");
        if (xIndex < 0 || zIndex < 0 || originX < 0 || originZ < 0
                || width < 1 || width > MAXIMUM_HORIZONTAL_EXTENT
                || length < 1 || length > MAXIMUM_HORIZONTAL_EXTENT
                || minY > maxY || (long) maxY - minY + 1L > MAXIMUM_VERTICAL_EXTENT
                || (long) originX + width > 1_000L || (long) originZ + length > 1_000L) {
            throw new IllegalArgumentException("offline tile bounds are invalid or exceed the V2 budget");
        }
        int height = Math.toIntExact((long) maxY - minY + 1L);
        Math.multiplyExact(Math.multiplyExact(width, length), height);
    }

    public int height() {
        return Math.addExact(Math.subtractExact(maxY, minY), 1);
    }

    public int blockCount() {
        return Math.multiplyExact(Math.multiplyExact(width, length), height());
    }

    public boolean contains(int x, int y, int z) {
        return x >= originX && x < originX + width
                && z >= originZ && z < originZ + length
                && y >= minY && y <= maxY;
    }

    public String defaultSchematicFileName() {
        return tileId + ".schem";
    }
}
