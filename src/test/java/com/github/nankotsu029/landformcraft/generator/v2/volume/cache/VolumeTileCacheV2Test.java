package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgEvaluatorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgOccupancyV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeTileCachePlanV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeTileCacheV2Test {
    private static final long M = 1_000_000L;
    private static final String ZERO = "0".repeat(64);

    @Test
    void hitEvictEdgeYOverlapAndCancel() {
        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.center", new VolumeSdfVec3V2(8 * M, 8 * M, 8 * M), 6 * M),
                new VolumeSdfPrimitiveV2.Sphere("prim.fluid", new VolumeSdfVec3V2(8 * M, 4 * M, 8 * M), 3 * M)));
        VolumeCsgPlanV2 csg = sealedCsg(primitives, List.of(
                addSolid(0, "op.solid", "prim.center"),
                addFluid(1, "op.fluid", "prim.fluid", "fluid.pool")));
        VolumeTileCachePlanV2 plan = sealedCache(csg, 16, 2, 2, 1);
        VolumeCsgEvaluatorV2 evaluator = new VolumeCsgEvaluatorV2(csg, primitives);

        try (VolumeTileCacheSessionV2 session = new VolumeTileCacheSessionV2(plan, evaluator)) {
            VolumeChunkKeyV2 key0 = new VolumeChunkKeyV2(0, 0, 0);
            VolumeCachedChunkV2 first = session.getOrFill(key0, () -> false);
            assertEquals(1, session.instrumentation().misses());
            assertEquals(0, session.instrumentation().hits());
            assertTrue(first.column(8, 8).solidIntervals().size() >= 1);

            // Edge Y: solid/fluid intervals abut chunk Y origin and are half-open.
            VolumeColumnIntervalsV2 edge = first.column(8, 8);
            for (VolumeYIntervalV2 solid : edge.solidIntervals()) {
                assertTrue(solid.minYInclusive() < solid.maxYExclusive());
            }

            session.getOrFill(key0, () -> false);
            assertEquals(1, session.instrumentation().hits());

            // Overlap / eviction: retain capacity 2, fill three distinct chunks.
            session.getOrFill(new VolumeChunkKeyV2(1, 0, 0), () -> false);
            session.getOrFill(new VolumeChunkKeyV2(2, 0, 0), () -> false);
            assertTrue(session.instrumentation().evictions() >= 1);
            assertTrue(session.instrumentation().peakRetainedChunks() <= plan.kernel().maximumRetainedChunks());
            assertEquals(0, session.instrumentation().denseAllocationAttempts());

            AtomicInteger checks = new AtomicInteger();
            AtomicBoolean cancel = new AtomicBoolean(false);
            VolumeTileCacheExceptionV2 cancelled = assertThrows(VolumeTileCacheExceptionV2.class,
                    () -> session.getOrFill(new VolumeChunkKeyV2(3, 0, 0), () -> {
                        if (checks.incrementAndGet() > 2) {
                            cancel.set(true);
                        }
                        return cancel.get();
                    }));
            assertEquals(VolumeTileCacheFailureCodeV2.CANCELLED, cancelled.failureCode());
            assertTrue(session.instrumentation().cancelChecks() > 0);
        }
    }

    @Test
    void cacheAndDirectCsgProduceIdenticalChecksumAcrossOrderThreadLocale() throws Exception {
        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.a", new VolumeSdfVec3V2(4 * M, 4 * M, 4 * M), 5 * M)));
        VolumeCsgPlanV2 csg = sealedCsg(primitives, List.of(addSolid(0, "op.a", "prim.a")));
        VolumeTileCachePlanV2 plan = sealedCache(csg, 16, 1, 4, 2);
        VolumeCsgEvaluatorV2 evaluator = new VolumeCsgEvaluatorV2(csg, primitives);

        String direct = VolumeTileCacheSessionV2.directCsgRegionOccupancyChecksum(
                evaluator, 0, 0, 0, 15, 15, 15);

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            try (VolumeTileCacheSessionV2 session = new VolumeTileCacheSessionV2(plan, evaluator)) {
                String cached = session.regionOccupancyChecksum(0, 0, 0, 15, 15, 15, () -> false);
                assertEquals(direct, cached);
            }
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(direct, one.submit(() -> {
                    try (VolumeTileCacheSessionV2 session = new VolumeTileCacheSessionV2(plan, evaluator)) {
                        return session.regionOccupancyChecksum(0, 0, 0, 15, 15, 15, () -> false);
                    }
                }).get());
                assertEquals(direct, four.submit(() -> {
                    try (VolumeTileCacheSessionV2 session = new VolumeTileCacheSessionV2(plan, evaluator)) {
                        return session.regionOccupancyChecksum(0, 0, 0, 15, 15, 15, () -> false);
                    }
                }).get());
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void admissionDenseDetectorAndOversizedRejection() {
        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.a", new VolumeSdfVec3V2(0, 0, 0), 2 * M)));
        VolumeCsgPlanV2 csg = sealedCsg(primitives, List.of(addSolid(0, "op.a", "prim.a")));
        VolumeTileCachePlanV2 plan = sealedCache(csg, 16, 2, 8, 2);
        VolumeTileCacheAdmissionDecisionV2 decision = VolumeTileCacheAdmissionV2.admit(plan);
        assertEquals(VolumeTileCacheAdmissionV2.ADMISSION_VERSION, decision.admissionVersion());
        long required = decision.retainedBytes()
                + decision.concurrency() * decision.peakWorkingBytesPerChunk()
                + decision.cacheBytes();
        assertEquals(required, decision.requiredWorkingBytes());
        assertTrue(required <= plan.budget().maximumWorkingBytes());

        VolumeTileCacheExceptionV2 dense = assertThrows(VolumeTileCacheExceptionV2.class,
                () -> VolumeDenseAllocationDetectorV2.rejectIfDenseWorldArray(1000, 1000, 512));
        assertEquals(VolumeTileCacheFailureCodeV2.DENSE_ALLOCATION_REJECTED, dense.failureCode());

        assertThrows(VolumeTileCacheExceptionV2.class,
                () -> VolumeDenseAllocationDetectorV2.rejectIfOversizedChunk(64));
        assertThrows(VolumeTileCacheExceptionV2.class,
                () -> VolumeDenseAllocationDetectorV2.rejectIfOversizedSupport(17));
        assertThrows(IllegalArgumentException.class,
                () -> new VolumeTileCachePlanV2.Kernel(
                        "volume-tile-cache-v2", 16, 2, 8, 2, 16, 16));
        assertThrows(IllegalArgumentException.class,
                () -> new VolumeTileCachePlanV2.Kernel(
                        VolumeTileCachePlanV2.Kernel.VERSION, 24, 2, 8, 2, 16, 16));
    }

    @Test
    void releaseClearsRetainedChunksAndExampleRoundTrips() throws Exception {
        Path csgExample = Path.of("examples/v2/volume/volume-csg-plan-v2.json");
        Path sdfExample = Path.of("examples/v2/volume/volume-sdf-primitive-plan-v2.json");
        Path cacheExample = Path.of("examples/v2/volume/volume-tile-cache-plan-v2.json");
        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 primitives = codec.readVolumeSdfPrimitivePlan(sdfExample);
        VolumeCsgPlanV2 csg = codec.readVolumeCsgPlan(csgExample);
        VolumeTileCachePlanV2 plan = codec.readVolumeTileCachePlan(cacheExample);
        plan.requireCsgPlan(csg);
        csg.requirePrimitivePlan(primitives);
        VolumeCsgEvaluatorV2 evaluator = new VolumeCsgEvaluatorV2(csg, primitives);
        try (VolumeTileCacheSessionV2 session = new VolumeTileCacheSessionV2(plan, evaluator)) {
            session.getOrFill(new VolumeChunkKeyV2(0, 0, 0), () -> false);
            assertTrue(session.retainedChunkCount() >= 1);
            session.releaseAll();
            assertEquals(0, session.retainedChunkCount());
            assertEquals(0, session.instrumentation().denseAllocationAttempts());
            assertTrue(session.instrumentation().peakRetainedChunks()
                    <= plan.kernel().maximumRetainedChunks());
        }
        assertEquals(plan, codec.readVolumeTileCachePlan(codec.canonicalVolumeTileCachePlan(plan), "rt"));
        assertTrue(Files.size(cacheExample) > 0);

        VolumeTileCachePlanV2 tampered = new VolumeTileCachePlanV2(
                plan.planVersion(), plan.cacheContractVersion(), plan.csgPlanBinding(),
                plan.kernel(), plan.budget(), "b".repeat(64));
        assertThrows(Exception.class, () -> codec.writeVolumeTileCachePlan(
                Path.of("build/tmp-tile-cache-tamper.json"), tampered));
    }

    @Test
    void compressColumnBuildsSolidAndFluidIntervals() {
        VolumeCsgOccupancyV2[] occupancy = {
                VolumeCsgOccupancyV2.AIR,
                VolumeCsgOccupancyV2.SOLID,
                VolumeCsgOccupancyV2.SOLID,
                VolumeCsgOccupancyV2.FLUID,
                VolumeCsgOccupancyV2.AIR
        };
        String[] fluids = {"", "", "", "fluid.a", ""};
        VolumeColumnIntervalsV2 column = VolumeCachedChunkV2.compressColumn(
                occupancy, fluids, 10, 8, 8);
        assertEquals(List.of(new VolumeYIntervalV2(11, 13)), column.solidIntervals());
        assertEquals(List.of(new VolumeFluidIntervalV2(13, 14, "fluid.a")), column.fluidIntervals());
    }

    private static VolumeCsgPlanV2.Operator addSolid(int ordinal, String id, String primitiveId) {
        return new VolumeCsgPlanV2.Operator(
                id, ordinal, VolumeCsgPlanV2.OperationKind.ADD_SOLID, primitiveId,
                VolumeCsgPlanV2.MaskMode.NONE, "", List.of(), "");
    }

    private static VolumeCsgPlanV2.Operator addFluid(
            int ordinal,
            String id,
            String primitiveId,
            String fluidBodyId
    ) {
        return new VolumeCsgPlanV2.Operator(
                id, ordinal, VolumeCsgPlanV2.OperationKind.ADD_FLUID, primitiveId,
                VolumeCsgPlanV2.MaskMode.NONE, "", List.of(), fluidBodyId);
    }

    private static VolumeSdfPrimitivePlanV2 sealedPrimitives(List<VolumeSdfPrimitiveV2> primitives) {
        return new LandformV2DataCodec().sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1, "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                primitives,
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1", 32, 64, 4096, 65536, 65536),
                ZERO));
    }

    private static VolumeCsgPlanV2 sealedCsg(
            VolumeSdfPrimitivePlanV2 primitives,
            List<VolumeCsgPlanV2.Operator> operators
    ) {
        return new LandformV2DataCodec().sealVolumeCsgPlan(new VolumeCsgPlanV2(
                1, "volume-csg-contract-v1",
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, primitives.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                VolumeCsgPlanV2.Kernel.standard(),
                operators,
                new VolumeCsgPlanV2.ResourceBudget(
                        "volume-csg-budget-v1", 64, 8, Math.max(1, operators.size() * 1024L),
                        1_048_576L, 4096, 65536, 65536),
                ZERO));
    }

    private static VolumeTileCachePlanV2 sealedCache(
            VolumeCsgPlanV2 csg,
            int chunkEdge,
            int halo,
            int retained,
            int concurrent
    ) {
        VolumeTileCachePlanV2.Kernel kernel = new VolumeTileCachePlanV2.Kernel(
                VolumeTileCachePlanV2.Kernel.VERSION,
                chunkEdge,
                halo,
                retained,
                concurrent,
                VolumeTileCachePlanV2.MAXIMUM_INTERVALS_PER_COLUMN,
                VolumeTileCachePlanV2.MAXIMUM_INTERVALS_PER_COLUMN);
        return new LandformV2DataCodec().sealVolumeTileCachePlan(new VolumeTileCachePlanV2(
                1,
                VolumeTileCachePlanV2.CACHE_CONTRACT_VERSION,
                new VolumeTileCachePlanV2.CsgPlanBinding(
                        1, csg.canonicalChecksum(), "volume-tile-cache-csg-binding-v1"),
                kernel,
                VolumeTileCachePlanV2.ResourceBudget.forKernel(kernel),
                ZERO));
    }
}
