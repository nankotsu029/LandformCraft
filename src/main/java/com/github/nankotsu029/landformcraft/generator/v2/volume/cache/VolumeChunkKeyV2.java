package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

/**
 * Canonical chunk coordinate key for the V2-5-04 tile cache. Keys use floor division by the
 * sealed chunk edge and are comparable for deterministic LRU tie-breaks.
 */
public record VolumeChunkKeyV2(int chunkX, int chunkY, int chunkZ) implements Comparable<VolumeChunkKeyV2> {
    public static VolumeChunkKeyV2 ofBlock(int blockX, int blockY, int blockZ, int chunkEdgeBlocks) {
        if (chunkEdgeBlocks < 1) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.OVERSIZED_CHUNK, "chunk edge must be positive");
        }
        return new VolumeChunkKeyV2(
                Math.floorDiv(blockX, chunkEdgeBlocks),
                Math.floorDiv(blockY, chunkEdgeBlocks),
                Math.floorDiv(blockZ, chunkEdgeBlocks));
    }

    public int originBlockX(int chunkEdgeBlocks) {
        return Math.multiplyExact(chunkX, chunkEdgeBlocks);
    }

    public int originBlockY(int chunkEdgeBlocks) {
        return Math.multiplyExact(chunkY, chunkEdgeBlocks);
    }

    public int originBlockZ(int chunkEdgeBlocks) {
        return Math.multiplyExact(chunkZ, chunkEdgeBlocks);
    }

    @Override
    public int compareTo(VolumeChunkKeyV2 other) {
        int cmp = Integer.compare(chunkX, other.chunkX);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(chunkY, other.chunkY);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(chunkZ, other.chunkZ);
    }
}
