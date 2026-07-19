package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

/**
 * Mutable instrumentation counters for one cache session. Used by tests to prove dense allocation
 * never occurs and peak retained chunks stay within the sealed kernel.
 */
public final class VolumeTileCacheInstrumentationV2 {
    private long hits;
    private long misses;
    private long evictions;
    private long fills;
    private long peakRetainedChunks;
    private long peakRetainedBytes;
    private long denseAllocationAttempts;
    private long cancelChecks;

    public synchronized void recordHit() {
        hits++;
    }

    public synchronized void recordMiss() {
        misses++;
    }

    public synchronized void recordEviction() {
        evictions++;
    }

    public synchronized void recordFill(long retainedChunksAfter, long retainedBytesAfter) {
        fills++;
        peakRetainedChunks = Math.max(peakRetainedChunks, retainedChunksAfter);
        peakRetainedBytes = Math.max(peakRetainedBytes, retainedBytesAfter);
    }

    public synchronized void recordDenseAllocationAttempt() {
        denseAllocationAttempts++;
    }

    public synchronized void recordCancelCheck() {
        cancelChecks++;
    }

    public synchronized long hits() {
        return hits;
    }

    public synchronized long misses() {
        return misses;
    }

    public synchronized long evictions() {
        return evictions;
    }

    public synchronized long fills() {
        return fills;
    }

    public synchronized long peakRetainedChunks() {
        return peakRetainedChunks;
    }

    public synchronized long peakRetainedBytes() {
        return peakRetainedBytes;
    }

    public synchronized long denseAllocationAttempts() {
        return denseAllocationAttempts;
    }

    public synchronized long cancelChecks() {
        return cancelChecks;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                hits, misses, evictions, fills, peakRetainedChunks, peakRetainedBytes,
                denseAllocationAttempts, cancelChecks);
    }

    public record Snapshot(
            long hits,
            long misses,
            long evictions,
            long fills,
            long peakRetainedChunks,
            long peakRetainedBytes,
            long denseAllocationAttempts,
            long cancelChecks
    ) {
    }
}
