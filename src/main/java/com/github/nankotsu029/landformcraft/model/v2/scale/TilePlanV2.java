package com.github.nankotsu029.landformcraft.model.v2.scale;

/**
 * Deterministic row-major tile decomposition of one generation area. Tile identity is the
 * canonical index {@code tileZ * tileCountX + tileX} with X fastest; iteration order, thread
 * count, and cache state never change the decomposition. Halo windows are clamped to the
 * area so edge tiles stay inside bounds.
 */
public record TilePlanV2(
        int widthBlocks,
        int lengthBlocks,
        int tileSizeBlocks,
        int haloBlocks
) {
    /** Hard ceiling against degenerate plans; profiles enforce their own smaller budget. */
    public static final int MAXIMUM_PLAN_TILES = 16_384;

    public TilePlanV2 {
        if (widthBlocks < 1 || lengthBlocks < 1
                || widthBlocks > ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS
                || lengthBlocks > ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS) {
            throw new IllegalArgumentException("tile plan dimensions must be between 1 and "
                    + ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS + " blocks");
        }
        if (tileSizeBlocks < ScaleProfileV2.MINIMUM_TILE_SIZE_BLOCKS
                || tileSizeBlocks > ScaleProfileV2.MAXIMUM_TILE_SIZE_BLOCKS
                || tileSizeBlocks % ScaleProfileV2.MINIMUM_TILE_SIZE_BLOCKS != 0) {
            throw new IllegalArgumentException("tile size must be a multiple of "
                    + ScaleProfileV2.MINIMUM_TILE_SIZE_BLOCKS + " between "
                    + ScaleProfileV2.MINIMUM_TILE_SIZE_BLOCKS + " and "
                    + ScaleProfileV2.MAXIMUM_TILE_SIZE_BLOCKS);
        }
        if (haloBlocks < 0 || haloBlocks > tileSizeBlocks / 2) {
            throw new IllegalArgumentException("halo must be between 0 and half of the tile size");
        }
        long tiles = (long) ceilDiv(widthBlocks, tileSizeBlocks) * ceilDiv(lengthBlocks, tileSizeBlocks);
        if (tiles > MAXIMUM_PLAN_TILES) {
            throw new IllegalArgumentException("tile plan exceeds " + MAXIMUM_PLAN_TILES + " tiles");
        }
    }

    public static TilePlanV2 of(int widthBlocks, int lengthBlocks, ScaleProfileV2 profile) {
        return new TilePlanV2(widthBlocks, lengthBlocks, profile.tileSizeBlocks(), profile.haloBlocks());
    }

    public int tileCountX() {
        return ceilDiv(widthBlocks, tileSizeBlocks);
    }

    public int tileCountZ() {
        return ceilDiv(lengthBlocks, tileSizeBlocks);
    }

    public int tileCount() {
        return tileCountX() * tileCountZ();
    }

    public TileV2 tile(int tileX, int tileZ) {
        if (tileX < 0 || tileX >= tileCountX() || tileZ < 0 || tileZ >= tileCountZ()) {
            throw new IndexOutOfBoundsException("tile coordinate outside tile plan");
        }
        int coreMinX = tileX * tileSizeBlocks;
        int coreMinZ = tileZ * tileSizeBlocks;
        int coreWidth = Math.min(tileSizeBlocks, widthBlocks - coreMinX);
        int coreLength = Math.min(tileSizeBlocks, lengthBlocks - coreMinZ);
        int haloMinX = Math.max(0, coreMinX - haloBlocks);
        int haloMinZ = Math.max(0, coreMinZ - haloBlocks);
        int haloWidth = Math.min(widthBlocks, coreMinX + coreWidth + haloBlocks) - haloMinX;
        int haloLength = Math.min(lengthBlocks, coreMinZ + coreLength + haloBlocks) - haloMinZ;
        return new TileV2(tileZ * tileCountX() + tileX, tileX, tileZ,
                coreMinX, coreMinZ, coreWidth, coreLength,
                haloMinX, haloMinZ, haloWidth, haloLength);
    }

    public TileV2 tileByIndex(int index) {
        if (index < 0 || index >= tileCount()) {
            throw new IndexOutOfBoundsException("tile index outside tile plan");
        }
        return tile(index % tileCountX(), index / tileCountX());
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    /** One tile of the plan. Core is the exclusive output region; halo is read-only support. */
    public record TileV2(
            int index,
            int tileX,
            int tileZ,
            int coreMinX,
            int coreMinZ,
            int coreWidth,
            int coreLength,
            int haloMinX,
            int haloMinZ,
            int haloWidth,
            int haloLength
    ) {
        /** Stable identity used in artifact names and journals; never derived from ordinal. */
        public String tileId() {
            return "tile-x" + tileX + "-z" + tileZ;
        }
    }
}
