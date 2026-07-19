package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgEvaluatorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgOccupancyV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgSampleV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeTileCachePlanV2;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Fills one chunk by sampling ordered CSG at block centres, then compresses columns into solid／
 * fluid intervals. Scratch occupancy arrays are discarded after compression.
 */
public final class VolumeTileCacheEvaluatorV2 {
    private static final long MILLIONTHS = 1_000_000L;

    private final VolumeTileCachePlanV2 plan;
    private final VolumeCsgEvaluatorV2 csgEvaluator;

    public VolumeTileCacheEvaluatorV2(VolumeTileCachePlanV2 plan, VolumeCsgEvaluatorV2 csgEvaluator) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.csgEvaluator = Objects.requireNonNull(csgEvaluator, "csgEvaluator");
        try {
            plan.requireCsgPlan(csgEvaluator.plan());
        } catch (IllegalArgumentException exception) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.BINDING_MISMATCH, exception.getMessage());
        }
        if (!VolumeTileCachePlanV2.Kernel.VERSION.equals(plan.kernel().kernelVersion())) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.UNKNOWN_KERNEL, "unsupported tile-cache kernel");
        }
    }

    public VolumeCachedChunkV2 fill(
            VolumeChunkKeyV2 key,
            BooleanSupplier cancelled,
            VolumeTileCacheInstrumentationV2 instrumentation
    ) {
        Objects.requireNonNull(key, "key");
        BooleanSupplier cancel = cancelled == null ? () -> false : cancelled;
        VolumeTileCacheInstrumentationV2 stats = instrumentation == null
                ? new VolumeTileCacheInstrumentationV2()
                : instrumentation;

        int edge = plan.kernel().chunkEdgeBlocks();
        VolumeDenseAllocationDetectorV2.rejectIfOversizedChunk(edge);
        VolumeDenseAllocationDetectorV2.rejectIfDenseWorldArray(edge, edge, edge);

        int originX = key.originBlockX(edge);
        int originY = key.originBlockY(edge);
        int originZ = key.originBlockZ(edge);
        VolumeColumnIntervalsV2[] columns = new VolumeColumnIntervalsV2[edge * edge];
        long retained = 0L;

        VolumeCsgOccupancyV2[] occupancyScratch = new VolumeCsgOccupancyV2[edge];
        String[] fluidScratch = new String[edge];
        try {
            for (int lz = 0; lz < edge; lz++) {
                for (int lx = 0; lx < edge; lx++) {
                    stats.recordCancelCheck();
                    if (cancel.getAsBoolean()) {
                        throw new VolumeTileCacheExceptionV2(
                                VolumeTileCacheFailureCodeV2.CANCELLED, "tile-cache fill cancelled");
                    }
                    for (int ly = 0; ly < edge; ly++) {
                        long x = Math.addExact(Math.multiplyExact(originX + lx, MILLIONTHS), MILLIONTHS / 2L);
                        long y = Math.addExact(Math.multiplyExact(originY + ly, MILLIONTHS), MILLIONTHS / 2L);
                        long z = Math.addExact(Math.multiplyExact(originZ + lz, MILLIONTHS), MILLIONTHS / 2L);
                        VolumeCsgSampleV2 sample = csgEvaluator.sampleAt(x, y, z);
                        occupancyScratch[ly] = sample.occupancy();
                        fluidScratch[ly] = sample.fluidBodyId();
                    }
                    VolumeColumnIntervalsV2 column = VolumeCachedChunkV2.compressColumn(
                            occupancyScratch,
                            fluidScratch,
                            originY,
                            plan.kernel().maximumSolidIntervalsPerColumn(),
                            plan.kernel().maximumFluidIntervalsPerColumn());
                    columns[lz * edge + lx] = column;
                    retained = Math.addExact(retained, column.estimatedBytes());
                }
            }
        } finally {
            // Drop dense scratch references even on cancel / failure.
            for (int i = 0; i < occupancyScratch.length; i++) {
                occupancyScratch[i] = null;
                fluidScratch[i] = null;
            }
        }

        retained = Math.addExact(retained, 64L);
        return new VolumeCachedChunkV2(key, edge, columns, retained);
    }
}
