package com.github.nankotsu029.landformcraft.generator.v2.volume.waterfall;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import com.github.nankotsu029.landformcraft.model.v2.volume.WaterfallVolumePlanV2;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles a waterfall volume overlay: behind-fall {@code CARVE_SOLID}, plunge basin carve,
 * falling column {@code ADD_FLUID}, and plunge-pool fluid continuity bound to a fall geometry checksum.
 */
public final class WaterfallVolumePlanCompilerV2 {
    public static final String MODULE_ID = "generate.volume-waterfall";
    public static final String LIFECYCLE = "SUPPORTED";

    private static final String ZERO = "0".repeat(64);
    private static final long M = 1_000_000L;

    private WaterfallVolumePlanCompilerV2() {
    }

    public record CompiledWaterfallVolumeV2(
            WaterfallVolumePlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
    }

    public static CompiledWaterfallVolumeV2 compile(
            String featureId,
            String fallNodeId,
            String waterfallFeatureId,
            String sourceGeometryChecksum,
            VolumeSdfVec3V2 fallLipAnchor,
            VolumeSdfVec3V2 fallBaseAnchor,
            VolumeSdfVec3V2 lipCenter,
            VolumeSdfVec3V2 baseCenter,
            long columnRadiusMillionths,
            WaterfallVolumePlanV2.BehindFallSpec behindFall,
            WaterfallVolumePlanV2.PlungePoolSpec plungePool,
            String fluidBodyId,
            WaterfallVolumePlanV2.Kernel kernel
    ) {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(fallNodeId, "fallNodeId");
        Objects.requireNonNull(waterfallFeatureId, "waterfallFeatureId");
        Objects.requireNonNull(sourceGeometryChecksum, "sourceGeometryChecksum");
        Objects.requireNonNull(fallLipAnchor, "fallLipAnchor");
        Objects.requireNonNull(fallBaseAnchor, "fallBaseAnchor");
        Objects.requireNonNull(lipCenter, "lipCenter");
        Objects.requireNonNull(baseCenter, "baseCenter");
        Objects.requireNonNull(behindFall, "behindFall");
        Objects.requireNonNull(plungePool, "plungePool");
        Objects.requireNonNull(fluidBodyId, "fluidBodyId");
        Objects.requireNonNull(kernel, "kernel");
        if (!WaterfallVolumePlanV2.Kernel.VERSION.equals(kernel.kernelVersion())) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.UNKNOWN_KERNEL, "unsupported waterfall-volume kernel");
        }
        if (columnRadiusMillionths > kernel.maximumRadiusMillionths()) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.BUDGET_EXCEEDED, "waterfall-volume column radius exceeds kernel");
        }
        requireColumnAnchored(fallLipAnchor, fallBaseAnchor, lipCenter, baseCenter);
        requireBehindClearanceGeometry(lipCenter, baseCenter, behindFall);
        requirePoolAnchored(baseCenter, plungePool);

        String columnId = "prim.waterfall.column." + featureId;
        String behindId = "prim.waterfall.behind." + featureId;
        String poolId = "prim.waterfall.pool." + featureId;
        String planeId = "prim.waterfall.pool-plane." + featureId;

        VolumeSdfPrimitiveV2 column = new VolumeSdfPrimitiveV2.Capsule(
                columnId, lipCenter, baseCenter, columnRadiusMillionths);
        VolumeSdfPrimitiveV2 behind = new VolumeSdfPrimitiveV2.RoundedBox(
                behindId,
                behindFall.center(),
                behindFall.halfExtentsMillionths(),
                behindFall.cornerRadiusMillionths());
        VolumeSdfPrimitiveV2 pool = new VolumeSdfPrimitiveV2.Sphere(
                poolId, plungePool.center(), plungePool.radiusMillionths());
        VolumeSdfAabbV2 poolBounds = pool.conservativeBounds();
        VolumeSdfPrimitiveV2 poolPlane = new VolumeSdfPrimitiveV2.Plane(
                planeId,
                new VolumeSdfVec3V2(
                        plungePool.center().xMillionths(),
                        Math.multiplyExact((long) plungePool.waterSurfaceYBlocks() + 1L, M),
                        plungePool.center().zMillionths()),
                new VolumeSdfVec3V2(0, M, 0),
                poolBounds);

        List<VolumeSdfPrimitiveV2> primitives = List.of(column, behind, pool, poolPlane);
        VolumeSdfAabbV2 aabb = unionBounds(primitives);

        String carveBehindId = "op.carve.waterfall.behind." + featureId;
        String carvePoolId = "op.carve.waterfall.pool." + featureId;
        String carveColumnId = "op.carve.waterfall.column." + featureId;
        String fluidColumnId = "op.fluid.waterfall.column." + featureId;
        String fluidPoolId = "op.fluid.waterfall.pool." + featureId;
        List<VolumeCsgPlanV2.Operator> operators = List.of(
                new VolumeCsgPlanV2.Operator(
                        carveBehindId,
                        0,
                        VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                        behindId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of(),
                        ""),
                new VolumeCsgPlanV2.Operator(
                        carvePoolId,
                        1,
                        VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                        poolId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of(carveBehindId),
                        ""),
                new VolumeCsgPlanV2.Operator(
                        carveColumnId,
                        2,
                        VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                        columnId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of(carvePoolId),
                        ""),
                new VolumeCsgPlanV2.Operator(
                        fluidColumnId,
                        3,
                        VolumeCsgPlanV2.OperationKind.ADD_FLUID,
                        columnId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of(carveColumnId),
                        fluidBodyId),
                new VolumeCsgPlanV2.Operator(
                        fluidPoolId,
                        4,
                        VolumeCsgPlanV2.OperationKind.ADD_FLUID,
                        poolId,
                        VolumeCsgPlanV2.MaskMode.INTERSECTION_WITH_PRIMITIVE,
                        planeId,
                        List.of(fluidColumnId),
                        fluidBodyId));
        requireOperatorOrder(operators);
        requireSingleFluidOwner(operators);

        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 sdfPlan = codec.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1,
                "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                primitives,
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1",
                        4,
                        64,
                        4096,
                        65536,
                        65536),
                ZERO));
        VolumeCsgPlanV2 csgPlan = codec.sealVolumeCsgPlan(new VolumeCsgPlanV2(
                1,
                "volume-csg-contract-v1",
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, sdfPlan.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                VolumeCsgPlanV2.Kernel.standard(),
                operators,
                new VolumeCsgPlanV2.ResourceBudget(
                        "volume-csg-budget-v1",
                        64,
                        8,
                        4096L,
                        1_048_576L,
                        4096,
                        65536,
                        65536),
                ZERO));

        WaterfallVolumePlanV2 draft = new WaterfallVolumePlanV2(
                1,
                WaterfallVolumePlanV2.WATERFALL_VOLUME_CONTRACT_VERSION,
                featureId,
                kernel,
                new WaterfallVolumePlanV2.FallNodeBinding(
                        WaterfallVolumePlanV2.FallNodeBinding.BOUND_TO_FALL,
                        fallNodeId,
                        waterfallFeatureId,
                        sourceGeometryChecksum),
                lipCenter,
                baseCenter,
                columnRadiusMillionths,
                behindFall,
                plungePool,
                fluidBodyId,
                aabb,
                new WaterfallVolumePlanV2.ArtifactBinding(
                        1, sdfPlan.canonicalChecksum(), WaterfallVolumePlanV2.ArtifactBinding.SDF_CONTRACT),
                new WaterfallVolumePlanV2.ArtifactBinding(
                        1, csgPlan.canonicalChecksum(), WaterfallVolumePlanV2.ArtifactBinding.CSG_CONTRACT),
                WaterfallVolumePlanV2.ResourceBudget.standard(),
                ZERO);
        return new CompiledWaterfallVolumeV2(codec.sealWaterfallVolumePlan(draft), sdfPlan, csgPlan);
    }

    public static void requireGeometryChecksum(String expectedChecksum, String declaredChecksum) {
        Objects.requireNonNull(expectedChecksum, "expectedChecksum");
        Objects.requireNonNull(declaredChecksum, "declaredChecksum");
        if (!expectedChecksum.equals(declaredChecksum)) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.GRAPH_CHECKSUM_MISMATCH,
                    "waterfall-volume fall geometry checksum mismatch");
        }
    }

    static void requireColumnAnchored(
            VolumeSdfVec3V2 fallLip,
            VolumeSdfVec3V2 fallBase,
            VolumeSdfVec3V2 columnLip,
            VolumeSdfVec3V2 columnBase
    ) {
        if (!fallLip.equals(columnLip) || !fallBase.equals(columnBase)) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.OFFSET_COLUMN,
                    "waterfall-volume column is offset from fall lip/base anchors");
        }
        if (columnLip.yMillionths() <= columnBase.yMillionths()) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.OFFSET_COLUMN,
                    "waterfall-volume lip must be above base");
        }
    }

    static void requireBehindClearanceGeometry(
            VolumeSdfVec3V2 lip,
            VolumeSdfVec3V2 base,
            WaterfallVolumePlanV2.BehindFallSpec behind
    ) {
        long dx = Math.subtractExact(base.xMillionths(), lip.xMillionths());
        long dz = Math.subtractExact(base.zMillionths(), lip.zMillionths());
        long behindDx = Math.subtractExact(behind.center().xMillionths(), lip.xMillionths());
        long behindDz = Math.subtractExact(behind.center().zMillionths(), lip.zMillionths());
        // Behind must retreat opposite the horizontal fall direction (or stay behind lip when vertical).
        long dot = Math.addExact(Math.multiplyExact(dx, behindDx), Math.multiplyExact(dz, behindDz));
        if (dot > 0L) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.MISSING_BEHIND_CLEARANCE,
                    "behind-fall carve is not behind the fall face");
        }
        long halfX = behind.halfExtentsMillionths().xMillionths();
        long halfY = behind.halfExtentsMillionths().yMillionths();
        long halfZ = behind.halfExtentsMillionths().zMillionths();
        long cy = behind.center().yMillionths();
        if (cy + halfY < base.yMillionths() || cy - halfY > lip.yMillionths()) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.MISSING_BEHIND_CLEARANCE,
                    "behind-fall carve does not span lip-to-base height");
        }
        if (halfX < M || halfY < M || halfZ < M) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.MISSING_BEHIND_CLEARANCE,
                    "behind-fall carve too thin");
        }
    }

    static void requirePoolAnchored(VolumeSdfVec3V2 base, WaterfallVolumePlanV2.PlungePoolSpec pool) {
        long dx = Math.subtractExact(pool.center().xMillionths(), base.xMillionths());
        long dy = Math.subtractExact(pool.center().yMillionths(), base.yMillionths());
        long dz = Math.subtractExact(pool.center().zMillionths(), base.zMillionths());
        long dist2 = Math.addExact(
                Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dy, dy)),
                Math.multiplyExact(dz, dz));
        long max = Math.addExact(pool.radiusMillionths(), M);
        if (dist2 > Math.multiplyExact(max, max)) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.MISSING_POOL,
                    "plunge pool is disconnected from fall base");
        }
        int surfaceY = pool.waterSurfaceYBlocks();
        int baseY = (int) Math.floorDiv(base.yMillionths(), M);
        if (surfaceY < baseY - 1 || surfaceY > baseY + 2) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.MISSING_POOL,
                    "plunge pool water surface is not continuous with fall base");
        }
    }

    static void requireOperatorOrder(List<VolumeCsgPlanV2.Operator> operators) {
        int carveCount = 0;
        boolean sawColumnFluid = false;
        for (VolumeCsgPlanV2.Operator operator : operators) {
            if (operator.kind() == VolumeCsgPlanV2.OperationKind.CARVE_SOLID) {
                if (!operator.fluidBodyId().isEmpty()) {
                    throw new WaterfallVolumeExceptionV2(
                            WaterfallVolumeFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                            "CARVE_SOLID must not own fluidBodyId");
                }
                carveCount++;
            }
            if (operator.kind() == VolumeCsgPlanV2.OperationKind.ADD_FLUID) {
                if (carveCount < 3) {
                    throw new WaterfallVolumeExceptionV2(
                            WaterfallVolumeFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                            "ADD_FLUID must follow behind, pool, and column CARVE_SOLID");
                }
                if (!sawColumnFluid) {
                    sawColumnFluid = true;
                }
            }
        }
        if (carveCount < 3 || !sawColumnFluid) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                    "waterfall-volume operator sequence incomplete");
        }
    }

    static void requireSingleFluidOwner(List<VolumeCsgPlanV2.Operator> operators) {
        Set<String> fluidIds = new HashSet<>();
        int fluidOps = 0;
        for (VolumeCsgPlanV2.Operator operator : operators) {
            if (operator.kind() == VolumeCsgPlanV2.OperationKind.ADD_FLUID) {
                fluidOps++;
                fluidIds.add(operator.fluidBodyId());
            }
        }
        if (fluidOps < 2 || fluidIds.size() != 1) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                    "waterfall-volume requires one fluid owner across column and pool");
        }
    }

    private static VolumeSdfAabbV2 unionBounds(List<VolumeSdfPrimitiveV2> primitives) {
        long minX = Long.MAX_VALUE;
        long minY = Long.MAX_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long maxY = Long.MIN_VALUE;
        long maxZ = Long.MIN_VALUE;
        for (VolumeSdfPrimitiveV2 primitive : primitives) {
            VolumeSdfAabbV2 bounds = primitive.conservativeBounds();
            minX = Math.min(minX, bounds.minXMillionths());
            minY = Math.min(minY, bounds.minYMillionths());
            minZ = Math.min(minZ, bounds.minZMillionths());
            maxX = Math.max(maxX, bounds.maxXMillionths());
            maxY = Math.max(maxY, bounds.maxYMillionths());
            maxZ = Math.max(maxZ, bounds.maxZMillionths());
        }
        return new VolumeSdfAabbV2(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
