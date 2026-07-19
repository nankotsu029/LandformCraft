package com.github.nankotsu029.landformcraft.generator.v2.volume.index;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeAabbIndexPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeAabbIndexV2Test {
    private static final long M = 1_000_000L;
    private static final String ZERO = "0".repeat(64);

    @Test
    void boundaryNestedOverlapAndTranslationMatchOracle() {
        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.a", new VolumeSdfVec3V2(0, 0, 0), 5 * M),
                new VolumeSdfPrimitiveV2.Sphere("prim.b", new VolumeSdfVec3V2(20 * M, 0, 0), 5 * M),
                new VolumeSdfPrimitiveV2.Sphere("prim.nested", new VolumeSdfVec3V2(0, 0, 0), 2 * M),
                new VolumeSdfPrimitiveV2.Sphere("prim.far", new VolumeSdfVec3V2(200 * M, 0, 0), 3 * M)));
        VolumeCsgPlanV2 csg = sealedCsg(primitives, List.of(
                op(0, "op.a", "prim.a"),
                op(1, "op.b", "prim.b"),
                op(2, "op.nested", "prim.nested"),
                op(3, "op.far", "prim.far")));
        VolumeAabbIndexPlanV2 plan = sealedIndex(
                VolumeAabbIndexBuilderV2.buildDraft(csg, primitives, 0, 0));
        VolumeAabbIndexV2 index = new VolumeAabbIndexV2(plan);

        VolumeSdfAabbV2 tile = new VolumeSdfAabbV2(-1 * M, -1 * M, -1 * M, 1 * M, 1 * M, 1 * M);
        assertEquals(oracle(plan, tile, 0, 0), index.queryOverlapping(tile, 0, 0));

        // Boundary touch via halo: tile just outside AABB becomes overlapping with halo=1.
        VolumeSdfAabbV2 outside =
                new VolumeSdfAabbV2(6 * M, -1 * M, -1 * M, 7 * M, 1 * M, 1 * M);
        assertTrue(index.queryOverlapping(outside, 0, 0).isEmpty());
        assertEquals(oracle(plan, outside, 2, 0), index.queryOverlapping(outside, 2, 0));
        assertTrue(index.queryOverlapping(outside, 2, 0).stream()
                .anyMatch(hit -> hit.operatorId().equals("op.a")));

        VolumeSdfAabbV2 large = new VolumeSdfAabbV2(-50 * M, -50 * M, -50 * M, 50 * M, 50 * M, 50 * M);
        List<VolumeAabbIndexHitV2> largeHits = index.queryOverlapping(large, 0, 0);
        assertEquals(oracle(plan, large, 0, 0), largeHits);
        assertEquals(List.of(0, 1, 2), largeHits.stream().map(VolumeAabbIndexHitV2::ordinal).toList());

        VolumeSdfPrimitivePlanV2 translatedPrims = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.a", new VolumeSdfVec3V2(100 * M, 0, 0), 5 * M)));
        VolumeCsgPlanV2 translatedCsg = sealedCsg(translatedPrims, List.of(op(0, "op.a", "prim.a")));
        VolumeAabbIndexPlanV2 translated = sealedIndex(
                VolumeAabbIndexBuilderV2.buildDraft(translatedCsg, translatedPrims, 0, 0));
        VolumeSdfAabbV2 translatedTile =
                new VolumeSdfAabbV2(99 * M, -1 * M, -1 * M, 101 * M, 1 * M, 1 * M);
        assertEquals(1, new VolumeAabbIndexV2(translated).queryOverlapping(translatedTile, 0, 0).size());
    }

    @Test
    void buildIsInvariantToInputOrderAndThreads() throws Exception {
        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.a", new VolumeSdfVec3V2(0, 0, 0), 4 * M),
                new VolumeSdfPrimitiveV2.Sphere("prim.b", new VolumeSdfVec3V2(30 * M, 0, 0), 4 * M)));
        VolumeCsgPlanV2 csg = sealedCsg(primitives, List.of(
                op(0, "op.a", "prim.a"),
                op(1, "op.b", "prim.b")));
        VolumeAabbIndexPlanV2 expected = sealedIndex(
                VolumeAabbIndexBuilderV2.buildDraft(csg, primitives, 1, 2));

        List<VolumeAabbIndexPlanV2.Entry> shuffled = new ArrayList<>(expected.entries());
        Collections.reverse(shuffled);
        VolumeAabbIndexPlanV2 fromShuffled = sealedIndex(
                VolumeAabbIndexBuilderV2.buildFromEntries(csg, shuffled));
        assertEquals(expected.entries(), fromShuffled.entries());
        assertEquals(expected.canonicalChecksum(), fromShuffled.canonicalChecksum());

        try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
            assertEquals(expected.canonicalChecksum(), one.submit(() -> sealedIndex(
                    VolumeAabbIndexBuilderV2.buildDraft(csg, primitives, 1, 2)).canonicalChecksum()).get());
            assertEquals(expected.canonicalChecksum(), four.submit(() -> sealedIndex(
                    VolumeAabbIndexBuilderV2.buildDraft(csg, primitives, 1, 2)).canonicalChecksum()).get());
        }
    }

    @Test
    void rejectsOverflowInvalidAabbFutureKernelAndChecksum() {
        assertThrows(IllegalArgumentException.class,
                () -> new VolumeSdfAabbV2(2, 0, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VolumeAabbIndexPlanV2.Kernel("volume-aabb-index-v2", 8, 8, 4, 4));

        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.a", new VolumeSdfVec3V2(0, 0, 0), 2 * M)));
        VolumeCsgPlanV2 csg = sealedCsg(primitives, List.of(op(0, "op.a", "prim.a")));
        VolumeAabbIndexV2 index = new VolumeAabbIndexV2(sealedIndex(
                VolumeAabbIndexBuilderV2.buildDraft(csg, primitives, 0, 0)));
        VolumeSdfAabbV2 nearMax = new VolumeSdfAabbV2(
                Long.MAX_VALUE - 10, 0, 0, Long.MAX_VALUE - 9, 1, 1);
        VolumeAabbIndexExceptionV2 overflow = assertThrows(VolumeAabbIndexExceptionV2.class,
                () -> index.queryOverlapping(nearMax, 100, 0));
        assertEquals(VolumeAabbIndexFailureCodeV2.ARITHMETIC_OVERFLOW, overflow.failureCode());

        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeAabbIndexPlanV2 sealed = sealedIndex(
                VolumeAabbIndexBuilderV2.buildDraft(csg, primitives, 0, 0));
        VolumeAabbIndexPlanV2 tampered = new VolumeAabbIndexPlanV2(
                sealed.planVersion(), sealed.indexContractVersion(), sealed.csgPlanBinding(),
                sealed.kernel(), sealed.entries(), sealed.budget(), "a".repeat(64));
        assertThrows(Exception.class, () -> codec.writeVolumeAabbIndexPlan(
                Path.of("build/tmp-aabb-tamper.json"), tampered));
    }

    @Test
    void exampleRoundTripsAndFitsThousandBudget() throws Exception {
        Path example = Path.of("examples/v2/volume/volume-aabb-index-plan-v2.json");
        Path csgExample = Path.of("examples/v2/volume/volume-csg-plan-v2.json");
        Path sdfExample = Path.of("examples/v2/volume/volume-sdf-primitive-plan-v2.json");
        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 primitives = codec.readVolumeSdfPrimitivePlan(sdfExample);
        VolumeCsgPlanV2 csg = codec.readVolumeCsgPlan(csgExample);
        VolumeAabbIndexPlanV2 plan = codec.readVolumeAabbIndexPlan(example);
        plan.requireCsgPlan(csg);
        csg.requirePrimitivePlan(primitives);
        VolumeAabbIndexV2 index = new VolumeAabbIndexV2(plan);
        VolumeSdfAabbV2 world =
                new VolumeSdfAabbV2(0, -64 * M, 0, 1000 * M, 320 * M, 1000 * M);
        List<VolumeAabbIndexHitV2> hits = index.queryOverlapping(world, 16, 8);
        assertEquals(oracle(plan, world, 16, 8), hits);
        assertTrue(hits.size() <= plan.budget().maximumQueryResults());
        assertTrue(plan.entries().size() <= plan.budget().maximumIndexNodes());
        assertEquals(plan, codec.readVolumeAabbIndexPlan(codec.canonicalVolumeAabbIndexPlan(plan), "rt"));
        assertTrue(Files.size(example) > 0);
    }

    private static List<VolumeAabbIndexHitV2> oracle(
            VolumeAabbIndexPlanV2 plan,
            VolumeSdfAabbV2 tile,
            int haloXZ,
            int haloY
    ) {
        long scale = VolumeSdfPrimitivePlanV2.Quantization.FIXED_SCALE;
        VolumeSdfAabbV2 query = tile.expandAxes(haloXZ * scale, haloY * scale, haloXZ * scale);
        List<VolumeAabbIndexHitV2> hits = new ArrayList<>();
        for (VolumeAabbIndexPlanV2.Entry entry : plan.entries()) {
            if (entry.aabb().intersects(query)) {
                hits.add(new VolumeAabbIndexHitV2(
                        entry.entryId(), entry.operatorId(), entry.ordinal(), entry.aabb()));
            }
        }
        return List.copyOf(hits);
    }

    private static VolumeCsgPlanV2.Operator op(int ordinal, String id, String primitiveId) {
        return new VolumeCsgPlanV2.Operator(
                id, ordinal, VolumeCsgPlanV2.OperationKind.ADD_SOLID, primitiveId,
                VolumeCsgPlanV2.MaskMode.NONE, "", List.of(), "");
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

    private static VolumeAabbIndexPlanV2 sealedIndex(VolumeAabbIndexPlanV2 draft) {
        return new LandformV2DataCodec().sealVolumeAabbIndexPlan(draft);
    }
}
