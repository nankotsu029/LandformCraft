package com.github.nankotsu029.landformcraft.generator.v2.volume.overhang;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.OverhangPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.util.List;
import java.util.Objects;

/**
 * Compiles an {@code OVERHANG}: host-supported seaward {@code ADD_SOLID} lobe with underside
 * {@code CARVE_SOLID} recess.
 */
public final class OverhangPlanCompilerV2 {
    public static final String MODULE_ID = "generate.volume-overhang";
    public static final String LIFECYCLE = "SUPPORTED";

    private static final String ZERO = "0".repeat(64);
    private static final long M = 1_000_000L;

    private OverhangPlanCompilerV2() {
    }

    public record CompiledOverhangV2(
            OverhangPlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
    }

    public static CompiledOverhangV2 compile(
            String featureId,
            String hostCliffFeatureId,
            OverhangPlanV2.CardinalFace seawardFace,
            VolumeSdfAabbV2 hostAabb,
            VolumeSdfVec3V2 lobeCenter,
            VolumeSdfVec3V2 lobeHalfExtents,
            long lobeCornerRadiusMillionths,
            VolumeSdfVec3V2 recessCenter,
            VolumeSdfVec3V2 recessHalfExtents,
            long recessCornerRadiusMillionths,
            OverhangPlanV2.Kernel kernel
    ) {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(hostCliffFeatureId, "hostCliffFeatureId");
        Objects.requireNonNull(seawardFace, "seawardFace");
        Objects.requireNonNull(hostAabb, "hostAabb");
        Objects.requireNonNull(lobeCenter, "lobeCenter");
        Objects.requireNonNull(lobeHalfExtents, "lobeHalfExtents");
        Objects.requireNonNull(recessCenter, "recessCenter");
        Objects.requireNonNull(recessHalfExtents, "recessHalfExtents");
        Objects.requireNonNull(kernel, "kernel");
        if (!OverhangPlanV2.Kernel.VERSION.equals(kernel.kernelVersion())) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.UNKNOWN_KERNEL, "unsupported overhang kernel");
        }
        OverhangPlanV2.LobeSpec lobe = new OverhangPlanV2.LobeSpec(
                lobeCenter, lobeHalfExtents, lobeCornerRadiusMillionths);
        OverhangPlanV2.RecessSpec recess = new OverhangPlanV2.RecessSpec(
                recessCenter, recessHalfExtents, recessCornerRadiusMillionths);
        requireLobeTouchesHostFace(hostAabb, seawardFace, lobe);
        requireLobeProjectsSeaward(hostAabb, seawardFace, lobe, kernel.minimumProjectionBlocks());
        requireRecessUnderLobe(lobe, recess, kernel.minimumRoofBlocks());
        requireRecessOpensSeaward(hostAabb, seawardFace, recess);

        String lobeId = "prim.overhang.lobe." + featureId;
        String recessId = "prim.overhang.recess." + featureId;
        VolumeSdfPrimitiveV2 lobePrim = new VolumeSdfPrimitiveV2.RoundedBox(
                lobeId, lobe.center(), lobe.halfExtentsMillionths(), lobe.cornerRadiusMillionths());
        VolumeSdfPrimitiveV2 recessPrim = new VolumeSdfPrimitiveV2.RoundedBox(
                recessId,
                recess.center(),
                recess.halfExtentsMillionths(),
                recess.cornerRadiusMillionths());
        VolumeSdfAabbV2 lobeBounds = lobePrim.conservativeBounds();
        VolumeSdfAabbV2 recessBounds = recessPrim.conservativeBounds();
        VolumeSdfAabbV2 featureAabb = new VolumeSdfAabbV2(
                Math.min(lobeBounds.minXMillionths(), recessBounds.minXMillionths()),
                Math.min(lobeBounds.minYMillionths(), recessBounds.minYMillionths()),
                Math.min(lobeBounds.minZMillionths(), recessBounds.minZMillionths()),
                Math.max(lobeBounds.maxXMillionths(), recessBounds.maxXMillionths()),
                Math.max(lobeBounds.maxYMillionths(), recessBounds.maxYMillionths()),
                Math.max(lobeBounds.maxZMillionths(), recessBounds.maxZMillionths()));

        List<VolumeSdfPrimitiveV2> primitives = List.of(lobePrim, recessPrim);
        List<VolumeCsgPlanV2.Operator> operators = List.of(
                new VolumeCsgPlanV2.Operator(
                        "op.add.overhang." + featureId,
                        0,
                        VolumeCsgPlanV2.OperationKind.ADD_SOLID,
                        lobeId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of(),
                        ""),
                new VolumeCsgPlanV2.Operator(
                        "op.carve.overhang." + featureId,
                        1,
                        VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                        recessId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of("op.add.overhang." + featureId),
                        ""));
        requireAddThenCarve(operators);

        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 sdfPlan = codec.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1,
                "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                primitives,
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1",
                        2,
                        64,
                        2048,
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
                        2048L,
                        1_048_576L,
                        4096,
                        65536,
                        65536),
                ZERO));

        OverhangPlanV2 draft = new OverhangPlanV2(
                1,
                OverhangPlanV2.OVERHANG_CONTRACT_VERSION,
                featureId,
                kernel,
                new OverhangPlanV2.HostCliffBinding(
                        OverhangPlanV2.HostCliffBinding.SUPPORTS_FROM,
                        hostCliffFeatureId,
                        seawardFace,
                        hostAabb),
                lobe,
                recess,
                featureAabb,
                new OverhangPlanV2.ArtifactBinding(
                        1, sdfPlan.canonicalChecksum(), OverhangPlanV2.ArtifactBinding.SDF_CONTRACT),
                new OverhangPlanV2.ArtifactBinding(
                        1, csgPlan.canonicalChecksum(), OverhangPlanV2.ArtifactBinding.CSG_CONTRACT),
                OverhangPlanV2.ResourceBudget.standard(),
                ZERO);
        return new CompiledOverhangV2(codec.sealOverhangPlan(draft), sdfPlan, csgPlan);
    }

    static void requireLobeTouchesHostFace(
            VolumeSdfAabbV2 hostAabb,
            OverhangPlanV2.CardinalFace face,
            OverhangPlanV2.LobeSpec lobe
    ) {
        long minX = Math.subtractExact(lobe.center().xMillionths(), lobe.halfExtentsMillionths().xMillionths());
        long maxX = Math.addExact(lobe.center().xMillionths(), lobe.halfExtentsMillionths().xMillionths());
        long minY = Math.subtractExact(lobe.center().yMillionths(), lobe.halfExtentsMillionths().yMillionths());
        long maxY = Math.addExact(lobe.center().yMillionths(), lobe.halfExtentsMillionths().yMillionths());
        long minZ = Math.subtractExact(lobe.center().zMillionths(), lobe.halfExtentsMillionths().zMillionths());
        long maxZ = Math.addExact(lobe.center().zMillionths(), lobe.halfExtentsMillionths().zMillionths());
        long tolerance = M;
        boolean touches = switch (face) {
            case WEST -> Math.abs(maxX - hostAabb.minXMillionths()) <= tolerance
                    && rangesOverlap(minY, maxY, hostAabb.minYMillionths(), hostAabb.maxYMillionths())
                    && rangesOverlap(minZ, maxZ, hostAabb.minZMillionths(), hostAabb.maxZMillionths());
            case EAST -> Math.abs(minX - hostAabb.maxXMillionths()) <= tolerance
                    && rangesOverlap(minY, maxY, hostAabb.minYMillionths(), hostAabb.maxYMillionths())
                    && rangesOverlap(minZ, maxZ, hostAabb.minZMillionths(), hostAabb.maxZMillionths());
            case NORTH -> Math.abs(maxZ - hostAabb.minZMillionths()) <= tolerance
                    && rangesOverlap(minX, maxX, hostAabb.minXMillionths(), hostAabb.maxXMillionths())
                    && rangesOverlap(minY, maxY, hostAabb.minYMillionths(), hostAabb.maxYMillionths());
            case SOUTH -> Math.abs(minZ - hostAabb.maxZMillionths()) <= tolerance
                    && rangesOverlap(minX, maxX, hostAabb.minXMillionths(), hostAabb.maxXMillionths())
                    && rangesOverlap(minY, maxY, hostAabb.minYMillionths(), hostAabb.maxYMillionths());
        };
        if (!touches) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.FLOATING_SLAB,
                    "overhang lobe does not touch the host seaward face");
        }
    }

    static void requireLobeProjectsSeaward(
            VolumeSdfAabbV2 hostAabb,
            OverhangPlanV2.CardinalFace face,
            OverhangPlanV2.LobeSpec lobe,
            int minimumProjectionBlocks
    ) {
        long minProjection = Math.multiplyExact((long) minimumProjectionBlocks, M);
        long projection = switch (face) {
            case WEST -> Math.subtractExact(hostAabb.minXMillionths(),
                    Math.subtractExact(lobe.center().xMillionths(), lobe.halfExtentsMillionths().xMillionths()));
            case EAST -> Math.subtractExact(
                    Math.addExact(lobe.center().xMillionths(), lobe.halfExtentsMillionths().xMillionths()),
                    hostAabb.maxXMillionths());
            case NORTH -> Math.subtractExact(hostAabb.minZMillionths(),
                    Math.subtractExact(lobe.center().zMillionths(), lobe.halfExtentsMillionths().zMillionths()));
            case SOUTH -> Math.subtractExact(
                    Math.addExact(lobe.center().zMillionths(), lobe.halfExtentsMillionths().zMillionths()),
                    hostAabb.maxZMillionths());
        };
        if (projection < minProjection) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.SHORT_PROJECTION,
                    "overhang projection shorter than minimum");
        }
    }

    static void requireRecessUnderLobe(
            OverhangPlanV2.LobeSpec lobe,
            OverhangPlanV2.RecessSpec recess,
            int minimumRoofBlocks
    ) {
        long lobeMinY = Math.subtractExact(
                lobe.center().yMillionths(), lobe.halfExtentsMillionths().yMillionths());
        long lobeMaxY = Math.addExact(
                lobe.center().yMillionths(), lobe.halfExtentsMillionths().yMillionths());
        long recessMinY = Math.subtractExact(
                recess.center().yMillionths(), recess.halfExtentsMillionths().yMillionths());
        long recessMaxY = Math.addExact(
                recess.center().yMillionths(), recess.halfExtentsMillionths().yMillionths());
        if (recessMaxY > lobeMaxY || recessMinY < lobeMinY) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.BLOCKED_CORRIDOR,
                    "overhang recess must sit inside the lobe Y span");
        }
        long roof = Math.subtractExact(lobeMaxY, recessMaxY);
        long minimumRoof = Math.multiplyExact((long) minimumRoofBlocks, M);
        if (roof < minimumRoof) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.THIN_ROOF,
                    "overhang roof thinner than minimum");
        }
        // Recess must overlap the lobe in XZ so the carve creates underside clearance.
        long lobeMinX = Math.subtractExact(
                lobe.center().xMillionths(), lobe.halfExtentsMillionths().xMillionths());
        long lobeMaxX = Math.addExact(
                lobe.center().xMillionths(), lobe.halfExtentsMillionths().xMillionths());
        long lobeMinZ = Math.subtractExact(
                lobe.center().zMillionths(), lobe.halfExtentsMillionths().zMillionths());
        long lobeMaxZ = Math.addExact(
                lobe.center().zMillionths(), lobe.halfExtentsMillionths().zMillionths());
        long recessMinX = Math.subtractExact(
                recess.center().xMillionths(), recess.halfExtentsMillionths().xMillionths());
        long recessMaxX = Math.addExact(
                recess.center().xMillionths(), recess.halfExtentsMillionths().xMillionths());
        long recessMinZ = Math.subtractExact(
                recess.center().zMillionths(), recess.halfExtentsMillionths().zMillionths());
        long recessMaxZ = Math.addExact(
                recess.center().zMillionths(), recess.halfExtentsMillionths().zMillionths());
        if (!rangesOverlap(lobeMinX, lobeMaxX, recessMinX, recessMaxX)
                || !rangesOverlap(lobeMinZ, lobeMaxZ, recessMinZ, recessMaxZ)) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.BLOCKED_CORRIDOR,
                    "overhang recess must overlap the lobe footprint");
        }
    }

    static void requireRecessOpensSeaward(
            VolumeSdfAabbV2 hostAabb,
            OverhangPlanV2.CardinalFace face,
            OverhangPlanV2.RecessSpec recess
    ) {
        long tip = switch (face) {
            case WEST -> Math.subtractExact(
                    recess.center().xMillionths(), recess.halfExtentsMillionths().xMillionths());
            case EAST -> Math.addExact(
                    recess.center().xMillionths(), recess.halfExtentsMillionths().xMillionths());
            case NORTH -> Math.subtractExact(
                    recess.center().zMillionths(), recess.halfExtentsMillionths().zMillionths());
            case SOUTH -> Math.addExact(
                    recess.center().zMillionths(), recess.halfExtentsMillionths().zMillionths());
        };
        boolean opens = switch (face) {
            case WEST -> tip < hostAabb.minXMillionths();
            case EAST -> tip > hostAabb.maxXMillionths();
            case NORTH -> tip < hostAabb.minZMillionths();
            case SOUTH -> tip > hostAabb.maxZMillionths();
        };
        if (!opens) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.MISSING_SEAWARD_OPENING,
                    "overhang recess does not open seaward of the host face");
        }
    }

    static void requireAddThenCarve(List<VolumeCsgPlanV2.Operator> operators) {
        if (operators.size() != 2) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                    "overhang requires exactly ADD_SOLID then CARVE_SOLID");
        }
        VolumeCsgPlanV2.Operator add = operators.get(0);
        VolumeCsgPlanV2.Operator carve = operators.get(1);
        if (add.kind() != VolumeCsgPlanV2.OperationKind.ADD_SOLID
                || carve.kind() != VolumeCsgPlanV2.OperationKind.CARVE_SOLID
                || !add.fluidBodyId().isEmpty()
                || !carve.fluidBodyId().isEmpty()) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                    "overhang operators must be ADD_SOLID then CARVE_SOLID without fluid");
        }
    }

    private static boolean rangesOverlap(long aMin, long aMax, long bMin, long bMax) {
        return aMin <= bMax && bMin <= aMax;
    }
}
