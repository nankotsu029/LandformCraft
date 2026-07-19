package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgOccupancyV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgSampleV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Sealed tile-local chunk holding compressed solid/fluid column intervals. Dense voxel arrays are
 * never retained after fill.
 */
public final class VolumeCachedChunkV2 {
    private final VolumeChunkKeyV2 key;
    private final int chunkEdgeBlocks;
    private final VolumeColumnIntervalsV2[] columns;
    private final long retainedBytes;

    VolumeCachedChunkV2(
            VolumeChunkKeyV2 key,
            int chunkEdgeBlocks,
            VolumeColumnIntervalsV2[] columns,
            long retainedBytes
    ) {
        this.key = Objects.requireNonNull(key, "key");
        if (chunkEdgeBlocks < 1) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.OVERSIZED_CHUNK, "invalid chunk edge");
        }
        this.chunkEdgeBlocks = chunkEdgeBlocks;
        this.columns = Objects.requireNonNull(columns, "columns").clone();
        if (this.columns.length != chunkEdgeBlocks * chunkEdgeBlocks) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.BUDGET_EXCEEDED, "column array size mismatch");
        }
        this.retainedBytes = retainedBytes;
    }

    public VolumeChunkKeyV2 key() {
        return key;
    }

    public int chunkEdgeBlocks() {
        return chunkEdgeBlocks;
    }

    public long retainedBytes() {
        return retainedBytes;
    }

    public VolumeColumnIntervalsV2 column(int localX, int localZ) {
        requireLocal(localX, localZ);
        return columns[localZ * chunkEdgeBlocks + localX];
    }

    public VolumeCsgSampleV2 sampleBlock(int worldX, int worldY, int worldZ) {
        int originX = key.originBlockX(chunkEdgeBlocks);
        int originY = key.originBlockY(chunkEdgeBlocks);
        int originZ = key.originBlockZ(chunkEdgeBlocks);
        int localX = worldX - originX;
        int localY = worldY - originY;
        int localZ = worldZ - originZ;
        if (localX < 0 || localX >= chunkEdgeBlocks
                || localY < 0 || localY >= chunkEdgeBlocks
                || localZ < 0 || localZ >= chunkEdgeBlocks) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.BUDGET_EXCEEDED, "block outside cached chunk");
        }
        VolumeColumnIntervalsV2 column = column(localX, localZ);
        for (VolumeYIntervalV2 solid : column.solidIntervals()) {
            if (worldY >= solid.minYInclusive() && worldY < solid.maxYExclusive()) {
                return VolumeCsgSampleV2.solid();
            }
        }
        for (VolumeFluidIntervalV2 fluid : column.fluidIntervals()) {
            if (worldY >= fluid.minYInclusive() && worldY < fluid.maxYExclusive()) {
                return VolumeCsgSampleV2.fluid(fluid.fluidBodyId());
            }
        }
        return VolumeCsgSampleV2.air();
    }

    public String occupancyChecksum() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(intBytes(key.chunkX()));
            digest.update(intBytes(key.chunkY()));
            digest.update(intBytes(key.chunkZ()));
            digest.update((byte) chunkEdgeBlocks);
            int originX = key.originBlockX(chunkEdgeBlocks);
            int originY = key.originBlockY(chunkEdgeBlocks);
            int originZ = key.originBlockZ(chunkEdgeBlocks);
            for (int lz = 0; lz < chunkEdgeBlocks; lz++) {
                for (int lx = 0; lx < chunkEdgeBlocks; lx++) {
                    VolumeColumnIntervalsV2 column = column(lx, lz);
                    for (int ly = 0; ly < chunkEdgeBlocks; ly++) {
                        VolumeCsgSampleV2 sample = sampleBlock(
                                originX + lx, originY + ly, originZ + lz);
                        digest.update((byte) sample.occupancy().ordinal());
                        digest.update(sample.fluidBodyId().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                    }
                    digest.update((byte) column.solidIntervals().size());
                    digest.update((byte) column.fluidIntervals().size());
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    List<VolumeColumnIntervalsV2> columnsView() {
        return List.of(columns);
    }

    private void requireLocal(int localX, int localZ) {
        if (localX < 0 || localX >= chunkEdgeBlocks || localZ < 0 || localZ >= chunkEdgeBlocks) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.BUDGET_EXCEEDED, "local column out of chunk");
        }
    }

    private static byte[] intBytes(int value) {
        return new byte[] {
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    private static String hex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    /** Builder that compresses a column occupancy scan into intervals then discards the scan. */
    static VolumeColumnIntervalsV2 compressColumn(
            VolumeCsgOccupancyV2[] occupancy,
            String[] fluidBodyIds,
            int originY,
            int maxSolidIntervals,
            int maxFluidIntervals
    ) {
        Objects.requireNonNull(occupancy, "occupancy");
        Objects.requireNonNull(fluidBodyIds, "fluidBodyIds");
        if (occupancy.length != fluidBodyIds.length) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.INVALID_INTERVAL, "column scan length mismatch");
        }
        java.util.ArrayList<VolumeYIntervalV2> solids = new java.util.ArrayList<>();
        java.util.ArrayList<VolumeFluidIntervalV2> fluids = new java.util.ArrayList<>();
        int index = 0;
        while (index < occupancy.length) {
            VolumeCsgOccupancyV2 kind = occupancy[index];
            if (kind == VolumeCsgOccupancyV2.AIR) {
                index++;
                continue;
            }
            int start = index;
            String fluidId = fluidBodyIds[index];
            index++;
            while (index < occupancy.length
                    && occupancy[index] == kind
                    && Objects.equals(fluidBodyIds[index], fluidId)) {
                index++;
            }
            int minY = Math.addExact(originY, start);
            int maxY = Math.addExact(originY, index);
            if (kind == VolumeCsgOccupancyV2.SOLID) {
                solids.add(new VolumeYIntervalV2(minY, maxY));
                if (solids.size() > maxSolidIntervals) {
                    throw new VolumeTileCacheExceptionV2(
                            VolumeTileCacheFailureCodeV2.BUDGET_EXCEEDED,
                            "solid interval budget exceeded");
                }
            } else if (kind == VolumeCsgOccupancyV2.FLUID) {
                fluids.add(new VolumeFluidIntervalV2(minY, maxY, fluidId));
                if (fluids.size() > maxFluidIntervals) {
                    throw new VolumeTileCacheExceptionV2(
                            VolumeTileCacheFailureCodeV2.BUDGET_EXCEEDED,
                            "fluid interval budget exceeded");
                }
            }
        }
        // Clear scratch arrays so callers cannot retain dense column occupancy.
        Arrays.fill(occupancy, null);
        Arrays.fill(fluidBodyIds, null);
        return new VolumeColumnIntervalsV2(solids, fluids);
    }
}
