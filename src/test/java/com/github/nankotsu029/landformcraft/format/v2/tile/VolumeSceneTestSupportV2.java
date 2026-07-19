package com.github.nankotsu029.landformcraft.format.v2.tile;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainQuery;
import com.github.nankotsu029.landformcraft.generator.v2.volume.query.VolumeTerrainQueryV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.util.List;
import java.util.OptionalInt;

/**
 * Shared V2-5-16 offline read-back scaffolding. Builds one canonical volume scene over global
 * coordinates: a flat heightfield with a floating sky island (independent solid), a carved cave
 * tunnel (air cavity in solid), and a pool inside the cave (fluid). The CSG plan is expressed in
 * global millionths, so any base window over the same region composes an identical stream.
 */
public final class VolumeSceneTestSupportV2 {
    public static final long M = 1_000_000L;
    public static final String ZERO = "0".repeat(64);

    /** Global scene extent used by the offline read-back tests. */
    public static final int MIN_Y = 0;
    public static final int MAX_Y = 15;
    public static final int SURFACE_Y = 6;

    private static final VolumeSdfPrimitivePlanV2 PRIMITIVES = sealedPrimitives(List.of(
            new VolumeSdfPrimitiveV2.Sphere(
                    "prim.sky", new VolumeSdfVec3V2(8 * M + M / 2, 12 * M + M / 2, 8 * M + M / 2), 2 * M),
            new VolumeSdfPrimitiveV2.Capsule(
                    "prim.cave",
                    new VolumeSdfVec3V2(2 * M + M / 2, 3 * M + M / 2, 8 * M + M / 2),
                    new VolumeSdfVec3V2(13 * M + M / 2, 3 * M + M / 2, 8 * M + M / 2),
                    3 * M / 2),
            new VolumeSdfPrimitiveV2.Sphere(
                    "prim.pool", new VolumeSdfVec3V2(8 * M + M / 2, 3 * M + M / 2, 8 * M + M / 2), 6 * M / 5)));

    private static final VolumeCsgPlanV2 CSG = sealedCsg(PRIMITIVES, List.of(
            op(0, "op.sky", VolumeCsgPlanV2.OperationKind.ADD_SOLID, "prim.sky", ""),
            op(1, "op.cave", VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.cave", ""),
            op(2, "op.pool", VolumeCsgPlanV2.OperationKind.ADD_FLUID, "prim.pool", "fluid.pool")));

    private VolumeSceneTestSupportV2() {
    }

    /** Volume-composed query over a base window; the CSG plan is shared and global. */
    public static VolumeTerrainQueryV2 scene(int originX, int originZ, int width, int length) {
        return new VolumeTerrainQueryV2(flatBase(originX, originZ, width, length), CSG, PRIMITIVES);
    }

    /** The sealed SDF primitive plan backing {@link #scene}. */
    public static VolumeSdfPrimitivePlanV2 sdfPlan() {
        return PRIMITIVES;
    }

    /** The sealed ordered CSG plan backing {@link #scene}. */
    public static VolumeCsgPlanV2 csgPlan() {
        return CSG;
    }

    public static OfflineTilePlanV2 plan(String id, int xIndex, int zIndex, int originX, int originZ,
                                         int width, int length) {
        return new OfflineTilePlanV2(1, id, xIndex, zIndex, originX, originZ, width, length, MIN_Y, MAX_Y);
    }

    private static TerrainQuery flatBase(int originX, int originZ, int width, int length) {
        TerrainQuery.QueryBounds bounds =
                new TerrainQuery.QueryBounds(originX, originZ, width, length, MIN_Y, MAX_Y);
        return new TerrainQuery() {
            @Override
            public QueryBounds bounds() {
                return bounds;
            }

            @Override
            public BlockClass blockClassAt(int x, int y, int z) {
                require(x, y, z);
                return y <= SURFACE_Y ? BlockClass.SOLID : BlockClass.AIR;
            }

            @Override
            public SemanticMaterial semanticMaterialAt(int x, int y, int z) {
                require(x, y, z);
                if (y > SURFACE_Y) {
                    return SemanticMaterial.NONE;
                }
                if (y == MIN_Y) {
                    return SemanticMaterial.BEDROCK;
                }
                return y == SURFACE_Y ? SemanticMaterial.GRASS : SemanticMaterial.STONE;
            }

            @Override
            public FluidBody fluidBodyAt(int x, int y, int z) {
                require(x, y, z);
                return FluidBody.NONE;
            }

            @Override
            public List<VerticalInterval> solidIntervals(int x, int z) {
                requireColumn(x, z);
                return List.of(new VerticalInterval(MIN_Y, SURFACE_Y));
            }

            @Override
            public List<VerticalInterval> fluidIntervals(int x, int z) {
                requireColumn(x, z);
                return List.of();
            }

            @Override
            public OptionalInt topWalkableSurface(int x, int z, WalkableSurfacePolicy policy) {
                requireColumn(x, z);
                return OptionalInt.of(SURFACE_Y);
            }

            @Override
            public OptionalInt surfaceBelow(int x, int y, int z) {
                require(x, y, z);
                return y >= SURFACE_Y ? OptionalInt.of(SURFACE_Y) : OptionalInt.empty();
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

    private static VolumeCsgPlanV2.Operator op(int ordinal, String id, VolumeCsgPlanV2.OperationKind kind,
                                               String primitiveId, String fluidBodyId) {
        return new VolumeCsgPlanV2.Operator(
                id, ordinal, kind, primitiveId, VolumeCsgPlanV2.MaskMode.NONE, "", List.of(), fluidBodyId);
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

    private static VolumeCsgPlanV2 sealedCsg(VolumeSdfPrimitivePlanV2 primitives,
                                             List<VolumeCsgPlanV2.Operator> operators) {
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
