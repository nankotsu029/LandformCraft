package com.github.nankotsu029.landformcraft.generator.v2.volume.query;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainQuery;
import com.github.nankotsu029.landformcraft.generator.v2.V1TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.V1TerrainQueryAdapter;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeTerrainQueryV2Test {
    private static final long M = 1_000_000L;
    private static final String ZERO = "0".repeat(64);

    @Test
    void baseOnlyMatchesUnderlyingQueryAndV1AdapterContract() throws Exception {
        TerrainQuery base = flatBase(0, 0, 8, 8, 0, 31, 10, 15);
        VolumeTerrainQueryV2 query = new VolumeTerrainQueryV2(base);
        assertEquals(TerrainQuery.QUERY_KERNEL_COLUMN_V1, query.queryKernelVersion());
        assertFalse(query.volumeEnabled());

        for (int z = 0; z < 8; z++) {
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y <= 31; y++) {
                    assertEquals(base.blockClassAt(x, y, z), query.blockClassAt(x, y, z));
                    assertEquals(base.semanticMaterialAt(x, y, z), query.semanticMaterialAt(x, y, z));
                    assertEquals(base.fluidBodyAt(x, y, z), query.fluidBodyAt(x, y, z));
                }
                assertEquals(base.solidIntervals(x, z), query.solidIntervals(x, z));
                assertEquals(base.fluidIntervals(x, z), query.fluidIntervals(x, z));
            }
        }

        // v1 adapter regression: wrapping does not change resolver stream for base-only.
        Path request = Path.of("examples/request.valid.json");
        if (java.nio.file.Files.exists(request)) {
            // Keep optional; primary v1 golden suite remains V1TerrainAdapterTest / CompatibilityGolden.
        }
        TerrainBlockResolver resolver = new V1TerrainBlockResolver(query);
        assertEquals("minecraft:stone", resolver.blockStateAt(0, 5, 0));
        assertEquals("minecraft:grass_block", resolver.blockStateAt(0, 10, 0));
        assertEquals("minecraft:water", resolver.blockStateAt(0, 12, 0));
        assertEquals("minecraft:air", resolver.blockStateAt(0, 20, 0));
        assertEquals(V1TerrainQueryAdapter.SUPPORTED_GENERATOR_VERSION, "3.0.0-phase6");
    }

    @Test
    void addCarveFluidIntervalsCeilingAndSurface() {
        TerrainQuery base = flatBase(0, 0, 16, 16, 0, 31, 8, -100);
        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.add", new VolumeSdfVec3V2(8 * M, 20 * M, 8 * M), 4 * M),
                new VolumeSdfPrimitiveV2.Capsule(
                        "prim.carve",
                        new VolumeSdfVec3V2(8 * M, 6 * M, 8 * M),
                        new VolumeSdfVec3V2(8 * M, 10 * M, 8 * M),
                        2 * M),
                new VolumeSdfPrimitiveV2.Sphere("prim.fluid", new VolumeSdfVec3V2(8 * M, 7 * M, 8 * M), 2 * M)));
        VolumeCsgPlanV2 csg = sealedCsg(primitives, List.of(
                op(0, "op.add", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.add", ""),
                op(1, "op.carve", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.carve", ""),
                op(2, "op.fluid", VolumeCsgPlanV2.OperationKind.ADD_FLUID, "prim.fluid", "fluid.pool")));
        VolumeTerrainQueryV2 query = new VolumeTerrainQueryV2(base, csg, primitives);
        assertEquals(TerrainQuery.QUERY_KERNEL_VOLUME_V1, query.queryKernelVersion());

        assertEquals(TerrainQuery.BlockClass.SOLID, query.blockClassAt(8, 20, 8));
        assertEquals(TerrainQuery.SemanticMaterial.STONE, query.semanticMaterialAt(8, 20, 8));
        // Carve opens the base column; fluid sphere then fills the lower cavity.
        assertEquals(TerrainQuery.BlockClass.FLUID, query.blockClassAt(8, 7, 8));
        assertEquals(TerrainQuery.FluidBody.WATER, query.fluidBodyAt(8, 7, 8));
        // Above the fluid sphere but still inside the carve capsule remains air.
        assertEquals(TerrainQuery.BlockClass.AIR, query.blockClassAt(8, 10, 8));

        List<TerrainQuery.VerticalInterval> solids = query.solidIntervals(8, 8);
        assertTrue(solids.size() >= 2);
        assertEquals(0, solids.get(0).minY());
        assertTrue(query.ceilingAbove(8, 8, 8).isPresent());
        assertTrue(query.ceilingAbove(8, 8, 8).getAsInt() > 8);
        OptionalInt floor = query.surfaceBelow(8, 8, 8);
        assertTrue(floor.isPresent());
        assertTrue(floor.getAsInt() < 8);
        assertTrue(query.topWalkableSurface(8, 8, TerrainQuery.WalkableSurfacePolicy.ALLOW_SUBMERGED)
                .isPresent());
        assertTrue(query.fluidIntervals(8, 8).size() >= 1);
    }

    @Test
    void wholeTileThreadLocaleChecksumStableAndSeamConsistent() throws Exception {
        TerrainQuery base = flatBase(0, 0, 16, 16, 0, 31, 5, -100);
        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.a", new VolumeSdfVec3V2(8 * M, 12 * M, 8 * M), 5 * M)));
        VolumeCsgPlanV2 csg = sealedCsg(primitives, List.of(
                op(0, "op.a", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.a", "")));
        VolumeTerrainQueryV2 whole = new VolumeTerrainQueryV2(base, csg, primitives);
        String expected = whole.regionOccupancyChecksum(0, 0, 0, 15, 31, 15);

        TerrainQuery tileBase = flatBase(0, 0, 8, 16, 0, 31, 5, -100);
        VolumeTerrainQueryV2 tileA = new VolumeTerrainQueryV2(tileBase, csg, primitives);
        TerrainQuery tileBaseB = flatBase(8, 0, 8, 16, 0, 31, 5, -100);
        VolumeTerrainQueryV2 tileB = new VolumeTerrainQueryV2(tileBaseB, csg, primitives);
        for (int z = 0; z < 16; z++) {
            for (int y = 0; y <= 31; y++) {
                for (int x = 0; x < 8; x++) {
                    assertEquals(whole.blockClassAt(x, y, z), tileA.blockClassAt(x, y, z));
                }
                for (int x = 8; x < 16; x++) {
                    assertEquals(whole.blockClassAt(x, y, z), tileB.blockClassAt(x, y, z));
                }
            }
        }

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals(expected, new VolumeTerrainQueryV2(base, csg, primitives)
                    .regionOccupancyChecksum(0, 0, 0, 15, 31, 15));
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(expected, one.submit(() -> new VolumeTerrainQueryV2(base, csg, primitives)
                        .regionOccupancyChecksum(0, 0, 0, 15, 31, 15)).get());
                assertEquals(expected, four.submit(() -> new VolumeTerrainQueryV2(base, csg, primitives)
                        .regionOccupancyChecksum(0, 0, 0, 15, 31, 15)).get());
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void rejectsBudgetOwnerConflictInvalidIntervalAndOutOfBounds() {
        TerrainQuery base = flatBase(0, 0, 4, 4, 0, 31, 10, -100);
        VolumeSdfPrimitivePlanV2 primitives = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.a", new VolumeSdfVec3V2(2 * M, 16 * M, 2 * M), 20 * M)));
        VolumeCsgPlanV2 csg = sealedCsg(primitives, List.of(
                op(0, "op.a", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.a", "")));
        VolumeTerrainCompositionKernelV2 kernel =
                new VolumeTerrainCompositionKernelV2(csg, primitives);
        VolumeTerrainQueryV2 tight = new VolumeTerrainQueryV2(base, kernel, 1, 1);
        // Full-column solid plus sky island add may still be one interval; carve to force many.
        VolumeSdfPrimitivePlanV2 carvePrims = sealedPrimitives(List.of(
                new VolumeSdfPrimitiveV2.Sphere("prim.host", new VolumeSdfVec3V2(2 * M, 16 * M, 2 * M), 20 * M),
                new VolumeSdfPrimitiveV2.Capsule(
                        "prim.c1", new VolumeSdfVec3V2(2 * M, 4 * M, 2 * M),
                        new VolumeSdfVec3V2(2 * M, 5 * M, 2 * M), 3 * M),
                new VolumeSdfPrimitiveV2.Capsule(
                        "prim.c2", new VolumeSdfVec3V2(2 * M, 10 * M, 2 * M),
                        new VolumeSdfVec3V2(2 * M, 11 * M, 2 * M), 3 * M),
                new VolumeSdfPrimitiveV2.Capsule(
                        "prim.c3", new VolumeSdfVec3V2(2 * M, 16 * M, 2 * M),
                        new VolumeSdfVec3V2(2 * M, 17 * M, 2 * M), 3 * M)));
        VolumeCsgPlanV2 carveCsg = sealedCsg(carvePrims, List.of(
                op(0, "op.host", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.host", ""),
                op(1, "op.c1", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.c1", ""),
                op(2, "op.c2", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.c2", ""),
                op(3, "op.c3", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.c3", "")));
        VolumeTerrainQueryV2 multi = new VolumeTerrainQueryV2(
                flatBase(0, 0, 4, 4, 0, 31, -100, -100),
                new VolumeTerrainCompositionKernelV2(carveCsg, carvePrims),
                1,
                1);
        VolumeTerrainQueryExceptionV2 budget = assertThrows(VolumeTerrainQueryExceptionV2.class,
                () -> multi.solidIntervals(2, 2));
        assertEquals(VolumeTerrainQueryFailureCodeV2.BUDGET_EXCEEDED, budget.failureCode());

        VolumeTerrainQueryExceptionV2 overlap = assertThrows(VolumeTerrainQueryExceptionV2.class,
                () -> VolumeTerrainQueryV2.rejectSolidFluidOverlap(
                        List.of(new TerrainQuery.VerticalInterval(0, 5)),
                        List.of(new TerrainQuery.VerticalInterval(5, 8))));
        assertEquals(VolumeTerrainQueryFailureCodeV2.OWNER_CONFLICT, overlap.failureCode());

        assertThrows(IllegalArgumentException.class,
                () -> new TerrainQuery.VerticalInterval(3, 2));
        assertThrows(VolumeTerrainQueryExceptionV2.class, () -> tight.blockClassAt(99, 0, 0));
        VolumeTerrainQueryExceptionV2 binding = assertThrows(VolumeTerrainQueryExceptionV2.class,
                () -> new VolumeTerrainCompositionKernelV2(
                        new VolumeCsgPlanV2(
                                1, "volume-csg-contract-v1",
                                new VolumeCsgPlanV2.PrimitivePlanBinding(
                                        1, "a".repeat(64), "volume-csg-primitive-binding-v1"),
                                VolumeCsgPlanV2.Kernel.standard(),
                                List.of(op(0, "op.a", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.a", "")),
                                new VolumeCsgPlanV2.ResourceBudget(
                                        "volume-csg-budget-v1", 64, 8, 1024, 1_048_576L, 4096, 65536, 65536),
                                ZERO),
                        primitives));
        assertEquals(VolumeTerrainQueryFailureCodeV2.BINDING_MISMATCH, binding.failureCode());
    }

    private static TerrainQuery flatBase(
            int originX,
            int originZ,
            int width,
            int length,
            int minY,
            int maxY,
            int surfaceY,
            int waterLevel
    ) {
        TerrainQuery.QueryBounds bounds =
                new TerrainQuery.QueryBounds(originX, originZ, width, length, minY, maxY);
        return new TerrainQuery() {
            @Override
            public QueryBounds bounds() {
                return bounds;
            }

            @Override
            public BlockClass blockClassAt(int x, int y, int z) {
                require(x, y, z);
                if (y <= surfaceY) {
                    return BlockClass.SOLID;
                }
                if (waterLevel >= y && y > surfaceY) {
                    return BlockClass.FLUID;
                }
                return BlockClass.AIR;
            }

            @Override
            public SemanticMaterial semanticMaterialAt(int x, int y, int z) {
                if (blockClassAt(x, y, z) != BlockClass.SOLID) {
                    return SemanticMaterial.NONE;
                }
                if (y == minY) {
                    return SemanticMaterial.BEDROCK;
                }
                if (y < surfaceY) {
                    return SemanticMaterial.STONE;
                }
                return SemanticMaterial.GRASS;
            }

            @Override
            public FluidBody fluidBodyAt(int x, int y, int z) {
                return blockClassAt(x, y, z) == BlockClass.FLUID ? FluidBody.WATER : FluidBody.NONE;
            }

            @Override
            public List<VerticalInterval> solidIntervals(int x, int z) {
                requireColumn(x, z);
                if (surfaceY < minY) {
                    return List.of();
                }
                return List.of(new VerticalInterval(minY, Math.min(surfaceY, maxY)));
            }

            @Override
            public List<VerticalInterval> fluidIntervals(int x, int z) {
                requireColumn(x, z);
                int start = surfaceY + 1;
                if (waterLevel < start || start > maxY) {
                    return List.of();
                }
                return List.of(new VerticalInterval(start, Math.min(waterLevel, maxY)));
            }

            @Override
            public OptionalInt topWalkableSurface(int x, int z, WalkableSurfacePolicy policy) {
                requireColumn(x, z);
                if (surfaceY < minY) {
                    return OptionalInt.empty();
                }
                if (policy == WalkableSurfacePolicy.DRY_ONLY && waterLevel >= surfaceY + 1) {
                    return OptionalInt.empty();
                }
                return OptionalInt.of(surfaceY);
            }

            @Override
            public OptionalInt surfaceBelow(int x, int y, int z) {
                require(x, y, z);
                return y >= surfaceY ? OptionalInt.of(surfaceY) : OptionalInt.empty();
            }

            @Override
            public OptionalInt ceilingAbove(int x, int y, int z) {
                require(x, y, z);
                return OptionalInt.empty();
            }

            @Override
            public int featureMembershipAt(int x, int y, int z) {
                require(x, y, z);
                return 0;
            }

            private void requireColumn(int x, int z) {
                if (!bounds.containsColumn(x, z)) {
                    throw new IndexOutOfBoundsException();
                }
            }

            private void require(int x, int y, int z) {
                if (!bounds.contains(x, y, z)) {
                    throw new IndexOutOfBoundsException();
                }
            }
        };
    }

    private static VolumeCsgPlanV2.Operator op(
            int ordinal,
            String id,
            VolumeCsgPlanV2.OperationKind kind,
            String primitiveId,
            String fluidBodyId
    ) {
        return new VolumeCsgPlanV2.Operator(
                id, ordinal, kind, primitiveId,
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
}
