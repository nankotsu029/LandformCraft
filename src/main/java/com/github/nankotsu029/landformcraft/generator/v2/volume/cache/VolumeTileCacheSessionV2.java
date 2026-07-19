package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgEvaluatorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgSampleV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeTileCachePlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Bounded LRU session for tile-local volume chunks. Explicit {@link #releaseAll()} clears retained
 * chunks after the central tile commits. Never allocates a world-scale dense voxel field.
 */
public final class VolumeTileCacheSessionV2 implements AutoCloseable {
    private final VolumeTileCachePlanV2 plan;
    private final VolumeTileCacheEvaluatorV2 evaluator;
    private final VolumeTileCacheAdmissionDecisionV2 admission;
    private final VolumeTileCacheInstrumentationV2 instrumentation;
    private final LinkedHashMap<VolumeChunkKeyV2, VolumeCachedChunkV2> lru;
    private boolean closed;

    public VolumeTileCacheSessionV2(VolumeTileCachePlanV2 plan, VolumeCsgEvaluatorV2 csgEvaluator) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (!VolumeTileCachePlanV2.Kernel.VERSION.equals(plan.kernel().kernelVersion())) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.UNKNOWN_KERNEL, "unsupported tile-cache kernel");
        }
        this.admission = VolumeTileCacheAdmissionV2.admit(plan);
        this.evaluator = new VolumeTileCacheEvaluatorV2(plan, csgEvaluator);
        this.instrumentation = new VolumeTileCacheInstrumentationV2();
        int capacity = plan.kernel().maximumRetainedChunks();
        this.lru = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<VolumeChunkKeyV2, VolumeCachedChunkV2> eldest) {
                if (size() <= capacity) {
                    return false;
                }
                instrumentation.recordEviction();
                return true;
            }
        };
    }

    public VolumeTileCachePlanV2 plan() {
        return plan;
    }

    public VolumeTileCacheAdmissionDecisionV2 admission() {
        return admission;
    }

    public VolumeTileCacheInstrumentationV2 instrumentation() {
        return instrumentation;
    }

    public synchronized VolumeCachedChunkV2 getOrFill(VolumeChunkKeyV2 key, BooleanSupplier cancelled) {
        requireOpen();
        Objects.requireNonNull(key, "key");
        VolumeCachedChunkV2 cached = lru.get(key);
        if (cached != null) {
            instrumentation.recordHit();
            return cached;
        }
        instrumentation.recordMiss();
        VolumeCachedChunkV2 filled = evaluator.fill(key, cancelled, instrumentation);
        lru.put(key, filled);
        instrumentation.recordFill(lru.size(), retainedBytesLocked());
        if (lru.size() > plan.kernel().maximumRetainedChunks()) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.BUDGET_EXCEEDED, "LRU retained-chunk budget exceeded");
        }
        if (instrumentation.peakRetainedChunks() > plan.kernel().maximumRetainedChunks()) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.BUDGET_EXCEEDED, "peak retained chunks exceeded");
        }
        return filled;
    }

    public synchronized VolumeCsgSampleV2 sampleBlock(
            int worldX,
            int worldY,
            int worldZ,
            BooleanSupplier cancelled
    ) {
        requireOpen();
        VolumeChunkKeyV2 key = VolumeChunkKeyV2.ofBlock(
                worldX, worldY, worldZ, plan.kernel().chunkEdgeBlocks());
        return getOrFill(key, cancelled).sampleBlock(worldX, worldY, worldZ);
    }

    /**
     * Occupancy fingerprint over an inclusive block AABB. Used to prove cache-on and cache-off
     * (direct CSG) paths produce identical checksums.
     */
    public String regionOccupancyChecksum(
            int minX,
            int minY,
            int minZ,
            int maxXInclusive,
            int maxYInclusive,
            int maxZInclusive,
            BooleanSupplier cancelled
    ) {
        requireOpen();
        if (minX > maxXInclusive || minY > maxYInclusive || minZ > maxZInclusive) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.INVALID_INTERVAL, "region AABB empty");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(plan.kernel().kernelVersion().getBytes(StandardCharsets.UTF_8));
            for (int z = minZ; z <= maxZInclusive; z++) {
                for (int y = minY; y <= maxYInclusive; y++) {
                    for (int x = minX; x <= maxXInclusive; x++) {
                        VolumeCsgSampleV2 sample = sampleBlock(x, y, z, cancelled);
                        digest.update((byte) sample.occupancy().ordinal());
                        digest.update(sample.fluidBodyId().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                    }
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static String directCsgRegionOccupancyChecksum(
            VolumeCsgEvaluatorV2 evaluator,
            int minX,
            int minY,
            int minZ,
            int maxXInclusive,
            int maxYInclusive,
            int maxZInclusive
    ) {
        Objects.requireNonNull(evaluator, "evaluator");
        if (minX > maxXInclusive || minY > maxYInclusive || minZ > maxZInclusive) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.INVALID_INTERVAL, "region AABB empty");
        }
        final long half = 500_000L;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(VolumeTileCachePlanV2.Kernel.VERSION.getBytes(StandardCharsets.UTF_8));
            for (int z = minZ; z <= maxZInclusive; z++) {
                for (int y = minY; y <= maxYInclusive; y++) {
                    for (int x = minX; x <= maxXInclusive; x++) {
                        VolumeCsgSampleV2 sample = evaluator.sampleAt(
                                Math.multiplyExact(x, 1_000_000L) + half,
                                Math.multiplyExact(y, 1_000_000L) + half,
                                Math.multiplyExact(z, 1_000_000L) + half);
                        digest.update((byte) sample.occupancy().ordinal());
                        digest.update(sample.fluidBodyId().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                    }
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public synchronized void releaseAll() {
        lru.clear();
    }

    public synchronized int retainedChunkCount() {
        return lru.size();
    }

    @Override
    public synchronized void close() {
        releaseAll();
        closed = true;
    }

    private void requireOpen() {
        if (closed) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.BUDGET_EXCEEDED, "tile-cache session closed");
        }
    }

    private long retainedBytesLocked() {
        long total = 0L;
        for (VolumeCachedChunkV2 chunk : lru.values()) {
            total = Math.addExact(total, chunk.retainedBytes());
        }
        return total;
    }

    private static String hex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
