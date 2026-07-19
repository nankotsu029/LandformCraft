package com.github.nankotsu029.landformcraft.generator.v2.volume.csg;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeCsgEvaluatorV2Test {
    private static final long M = 1_000_000L;
    private static final String ZERO = "0".repeat(64);

    @Test
    void addThenCarveDiffersFromCarveThenAddAndCarveLeavesFluid() {
        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.solid", new VolumeSdfVec3V2(0, 0, 0), 5 * M),
                new VolumeSdfPrimitiveV2.Sphere("prim.carve", new VolumeSdfVec3V2(0, 0, 0), 3 * M)));

        VolumeCsgPlanV2 addCarve = sealedCsg(primitives, List.of(
                op(0, "op.add", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.solid"),
                op(1, "op.carve", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.carve")));
        VolumeCsgPlanV2 carveAdd = sealedCsg(primitives, List.of(
                op(0, "op.carve", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.carve"),
                op(1, "op.add", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.solid")));

        VolumeCsgEvaluatorV2 addCarveEval = new VolumeCsgEvaluatorV2(addCarve, primitives);
        VolumeCsgEvaluatorV2 carveAddEval = new VolumeCsgEvaluatorV2(carveAdd, primitives);

        assertEquals(VolumeCsgOccupancyV2.AIR, addCarveEval.sampleAt(0, 0, 0).occupancy());
        assertEquals(VolumeCsgOccupancyV2.SOLID, carveAddEval.sampleAt(0, 0, 0).occupancy());
        assertNotEquals(
                addCarveEval.goldenChecksum(M, 2),
                carveAddEval.goldenChecksum(M, 2));

        VolumeCsgPlanV2 fluidThenCarve = sealedCsg(primitives, List.of(
                op(0, "op.add", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.solid"),
                fluid(1, "op.fluid", "prim.carve", "fluid.lake"),
                op(2, "op.carve", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.carve")));
        // After ADD_SOLID the carve region is SOLID; ADD_FLUID only fills AIR, so carve region
        // stays SOLID until CARVE makes AIR. Verify CARVE does not invent fluid.
        VolumeCsgEvaluatorV2 fluidCarve = new VolumeCsgEvaluatorV2(fluidThenCarve, primitives);
        VolumeCsgSampleV2 center = fluidCarve.sampleAt(0, 0, 0);
        assertEquals(VolumeCsgOccupancyV2.AIR, center.occupancy());
        assertEquals("", center.fluidBodyId());

        VolumeCsgPlanV2 carveThenFluid = sealedCsg(primitives, List.of(
                op(0, "op.add", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.solid"),
                op(1, "op.carve", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.carve"),
                fluid(2, "op.fluid", "prim.carve", "fluid.lake")));
        VolumeCsgSampleV2 flooded = new VolumeCsgEvaluatorV2(carveThenFluid, primitives).sampleAt(0, 0, 0);
        assertEquals(VolumeCsgOccupancyV2.FLUID, flooded.occupancy());
        assertEquals("fluid.lake", flooded.fluidBodyId());

        // CARVE applied to an already-fluid cell must leave fluid ownership untouched.
        VolumeCsgPlanV2 fluidProtected = sealedCsg(primitives, List.of(
                op(0, "op.add", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.solid"),
                op(1, "op.carve", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.carve"),
                fluid(2, "op.fluid", "prim.carve", "fluid.lake"),
                op(3, "op.carve-again", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.carve")));
        VolumeCsgSampleV2 protectedFluid = new VolumeCsgEvaluatorV2(fluidProtected, primitives)
                .sampleAt(0, 0, 0);
        assertEquals(VolumeCsgOccupancyV2.FLUID, protectedFluid.occupancy());
        assertEquals("fluid.lake", protectedFluid.fluidBodyId());
    }

    @Test
    void intersectionMaskAndDeterminismHold() throws Exception {
        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.a", new VolumeSdfVec3V2(0, 0, 0), 5 * M),
                new VolumeSdfPrimitiveV2.Sphere("prim.b", new VolumeSdfVec3V2(4 * M, 0, 0), 5 * M)));
        VolumeCsgPlanV2 plan = sealedCsg(primitives, List.of(
                new VolumeCsgPlanV2.Operator(
                        "op.masked", 0, VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.a",
                        VolumeCsgPlanV2.MaskMode.INTERSECTION_WITH_PRIMITIVE, "prim.b",
                        List.of(), "")));
        VolumeCsgEvaluatorV2 evaluator = new VolumeCsgEvaluatorV2(plan, primitives);
        assertEquals(VolumeCsgOccupancyV2.SOLID, evaluator.sampleAt(2 * M, 0, 0).occupancy());
        assertEquals(VolumeCsgOccupancyV2.AIR, evaluator.sampleAt(-4 * M, 0, 0).occupancy());

        String expected = evaluator.goldenChecksum(M, 2);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals(expected, new VolumeCsgEvaluatorV2(plan, primitives).goldenChecksum(M, 2));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
        try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
            assertEquals(expected, one.submit(() ->
                    new VolumeCsgEvaluatorV2(plan, primitives).goldenChecksum(M, 2)).get());
            assertEquals(expected, four.submit(() ->
                    new VolumeCsgEvaluatorV2(plan, primitives).goldenChecksum(M, 2)).get());
        }
    }

    @Test
    void rejectsAmbiguousOrderUnknownOpCycleAndBudget() {
        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.a", new VolumeSdfVec3V2(0, 0, 0), 2 * M)));

        assertThrows(IllegalArgumentException.class, () -> sealedCsg(primitives, List.of(
                op(1, "op.late", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.a"),
                op(0, "op.early", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.a"))));

        assertThrows(IllegalArgumentException.class,
                () -> new VolumeCsgPlanV2.Kernel("volume-csg-ordered-v2", 8, 4, 1024));

        assertThrows(IllegalArgumentException.class, () -> new VolumeCsgPlanV2.Operator(
                "op.a", 0, VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.a",
                VolumeCsgPlanV2.MaskMode.NONE, "", List.of("op.a"), ""));

        assertThrows(IllegalArgumentException.class, () -> sealedCsg(primitives, List.of(
                new VolumeCsgPlanV2.Operator(
                        "op.a", 0, VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.a",
                        VolumeCsgPlanV2.MaskMode.NONE, "", List.of("op.missing"), ""))));

        VolumeCsgPlanV2.Kernel shallow = new VolumeCsgPlanV2.Kernel(
                VolumeCsgPlanV2.Kernel.VERSION, 64, 2, VolumeCsgPlanV2.Kernel.MAXIMUM_CPU_WORK_UNITS);
        assertThrows(IllegalArgumentException.class, () -> new VolumeCsgPlanV2(
                1, "volume-csg-contract-v1",
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, primitives.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                shallow,
                List.of(
                        op(0, "op.0", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.a"),
                        new VolumeCsgPlanV2.Operator(
                                "op.1", 1, VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.a",
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of("op.0"), ""),
                        new VolumeCsgPlanV2.Operator(
                                "op.2", 2, VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.a",
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of("op.1"), "")),
                new VolumeCsgPlanV2.ResourceBudget(
                        "volume-csg-budget-v1", 64, 8, 3072, 1_048_576L, 1024, 65536, 65536),
                ZERO));

        assertThrows(IllegalArgumentException.class, () -> new VolumeCsgPlanV2(
                1, "volume-csg-contract-v1",
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, primitives.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                VolumeCsgPlanV2.Kernel.standard(),
                List.of(op(0, "op.add", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.a")),
                new VolumeCsgPlanV2.ResourceBudget(
                        "volume-csg-budget-v1", 64, 8, 2_000_000L, 1_048_576L, 1024, 65536, 65536),
                ZERO));
    }

    @Test
    void exampleRoundTripsThroughSchemaAndCodec() throws Exception {
        Path example = Path.of("examples/v2/volume/volume-csg-plan-v2.json");
        Path primitivesExample = Path.of("examples/v2/volume/volume-sdf-primitive-plan-v2.json");
        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 primitives = codec.readVolumeSdfPrimitivePlan(primitivesExample);
        VolumeCsgPlanV2 plan = codec.readVolumeCsgPlan(example);
        plan.requirePrimitivePlan(primitives);
        VolumeCsgEvaluatorV2 evaluator = new VolumeCsgEvaluatorV2(plan, primitives);
        assertTrue(evaluator.sampleAt(0, 0, 0).occupancy() != null);
        assertEquals(plan.canonicalChecksum(), codec.volumeCsgPlanChecksum(plan));
        VolumeCsgPlanV2 again = codec.readVolumeCsgPlan(codec.canonicalVolumeCsgPlan(plan), "round-trip");
        assertEquals(plan, again);
        assertTrue(Files.size(example) > 0);
    }

    private static VolumeCsgPlanV2.Operator op(
            int ordinal,
            String id,
            VolumeCsgPlanV2.OperationKind kind,
            String primitiveId
    ) {
        return new VolumeCsgPlanV2.Operator(
                id, ordinal, kind, primitiveId, VolumeCsgPlanV2.MaskMode.NONE, "", List.of(), "");
    }

    private static VolumeCsgPlanV2.Operator fluid(
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
        VolumeSdfPrimitivePlanV2 draft = new VolumeSdfPrimitivePlanV2(
                VolumeSdfPrimitivePlanV2.VERSION,
                VolumeSdfPrimitivePlanV2.PRIMITIVE_CONTRACT_VERSION,
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                primitives,
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        VolumeSdfPrimitivePlanV2.ResourceBudget.VERSION,
                        VolumeSdfPrimitivePlanV2.MAXIMUM_PRIMITIVES,
                        VolumeSdfPrimitivePlanV2.MAXIMUM_SWEPT_CONTROL_POINTS,
                        4_096L,
                        VolumeSdfPrimitivePlanV2.MAX_CANONICAL_BYTES,
                        64L * 1024L),
                ZERO);
        return new LandformV2DataCodec().sealVolumeSdfPrimitivePlan(draft);
    }

    private static VolumeCsgPlanV2 sealedCsg(
            VolumeSdfPrimitivePlanV2 primitives,
            List<VolumeCsgPlanV2.Operator> operators
    ) {
        VolumeCsgPlanV2 draft = new VolumeCsgPlanV2(
                VolumeCsgPlanV2.VERSION,
                VolumeCsgPlanV2.CSG_CONTRACT_VERSION,
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, primitives.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                VolumeCsgPlanV2.Kernel.standard(),
                operators,
                new VolumeCsgPlanV2.ResourceBudget(
                        VolumeCsgPlanV2.ResourceBudget.VERSION,
                        VolumeCsgPlanV2.MAXIMUM_OPERATORS,
                        VolumeCsgPlanV2.MAXIMUM_DEPENDENCY_DEPTH,
                        Math.max(1L, operators.size() * 1_024L),
                        VolumeCsgPlanV2.Kernel.MAXIMUM_CPU_WORK_UNITS,
                        4_096L,
                        VolumeCsgPlanV2.MAX_CANONICAL_BYTES,
                        64L * 1024L),
                ZERO);
        return new LandformV2DataCodec().sealVolumeCsgPlan(draft);
    }
}
