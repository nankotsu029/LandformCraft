package com.github.nankotsu029.landformcraft.generator.v2.volume.skyisland;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.SkyIslandGroupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Compiles a {@code SKY_ISLAND_GROUP}: ordered independent {@code ADD_SOLID} lobes with underside
 * {@code CARVE_SOLID}, ground clearance, and inter-island gap.
 */
public final class SkyIslandGroupPlanCompilerV2 {
    public static final String MODULE_ID = "generate.volume-sky-island-group";
    public static final String LIFECYCLE = "SUPPORTED";

    private static final String ZERO = "0".repeat(64);
    private static final long M = 1_000_000L;

    private SkyIslandGroupPlanCompilerV2() {
    }

    public record CompiledSkyIslandGroupV2(
            SkyIslandGroupPlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
    }

    public static CompiledSkyIslandGroupV2 compile(
            String featureId,
            int groundReferenceYBlocks,
            int minimumAllowedYBlocks,
            int maximumAllowedYBlocks,
            List<SkyIslandGroupPlanV2.IslandComponent> components,
            SkyIslandGroupPlanV2.Kernel kernel
    ) {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(kernel, "kernel");
        if (!SkyIslandGroupPlanV2.Kernel.VERSION.equals(kernel.kernelVersion())) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.UNKNOWN_KERNEL, "unsupported sky-island-group kernel");
        }
        if (components.size() < kernel.minimumComponentCount()
                || components.size() > kernel.maximumComponentCount()) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.BUDGET_EXCEEDED,
                    "sky-island-group component count outside kernel");
        }
        List<SkyIslandGroupPlanV2.IslandComponent> sorted = new ArrayList<>(components);
        sorted.sort(Comparator.comparing(SkyIslandGroupPlanV2.IslandComponent::componentId));
        for (SkyIslandGroupPlanV2.IslandComponent component : sorted) {
            requireComponentGeometry(
                    component, groundReferenceYBlocks, minimumAllowedYBlocks, maximumAllowedYBlocks, kernel);
        }
        requireGaps(sorted, kernel.minimumInterIslandGapBlocks());
        requireNoDenseAirFill(sorted, groundReferenceYBlocks);

        List<VolumeSdfPrimitiveV2> primitives = new ArrayList<>();
        List<VolumeCsgPlanV2.Operator> operators = new ArrayList<>();
        long minX = Long.MAX_VALUE;
        long minY = Long.MAX_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long maxY = Long.MIN_VALUE;
        long maxZ = Long.MIN_VALUE;
        int ordinal = 0;
        for (SkyIslandGroupPlanV2.IslandComponent component : sorted) {
            String lobeId = "prim.sky.lobe." + component.componentId();
            String underId = "prim.sky.underside." + component.componentId();
            VolumeSdfPrimitiveV2 lobePrim = new VolumeSdfPrimitiveV2.RoundedBox(
                    lobeId,
                    component.lobe().center(),
                    component.lobe().halfExtentsMillionths(),
                    component.lobe().cornerRadiusMillionths());
            VolumeSdfPrimitiveV2 underPrim = new VolumeSdfPrimitiveV2.RoundedBox(
                    underId,
                    component.underside().center(),
                    component.underside().halfExtentsMillionths(),
                    component.underside().cornerRadiusMillionths());
            primitives.add(lobePrim);
            primitives.add(underPrim);
            VolumeSdfAabbV2 lobeBounds = lobePrim.conservativeBounds();
            VolumeSdfAabbV2 underBounds = underPrim.conservativeBounds();
            minX = Math.min(minX, Math.min(lobeBounds.minXMillionths(), underBounds.minXMillionths()));
            minY = Math.min(minY, Math.min(lobeBounds.minYMillionths(), underBounds.minYMillionths()));
            minZ = Math.min(minZ, Math.min(lobeBounds.minZMillionths(), underBounds.minZMillionths()));
            maxX = Math.max(maxX, Math.max(lobeBounds.maxXMillionths(), underBounds.maxXMillionths()));
            maxY = Math.max(maxY, Math.max(lobeBounds.maxYMillionths(), underBounds.maxYMillionths()));
            maxZ = Math.max(maxZ, Math.max(lobeBounds.maxZMillionths(), underBounds.maxZMillionths()));

            String addId = "op.add.sky." + component.componentId();
            String carveId = "op.carve.sky." + component.componentId();
            operators.add(new VolumeCsgPlanV2.Operator(
                    addId,
                    ordinal++,
                    VolumeCsgPlanV2.OperationKind.ADD_SOLID,
                    lobeId,
                    VolumeCsgPlanV2.MaskMode.NONE,
                    "",
                    List.of(),
                    ""));
            operators.add(new VolumeCsgPlanV2.Operator(
                    carveId,
                    ordinal++,
                    VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                    underId,
                    VolumeCsgPlanV2.MaskMode.NONE,
                    "",
                    List.of(addId),
                    ""));
        }
        requireAddThenCarvePairs(operators);

        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 sdfPlan = codec.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1,
                "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                primitives,
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1",
                        primitives.size(),
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

        VolumeSdfAabbV2 featureAabb = new VolumeSdfAabbV2(minX, minY, minZ, maxX, maxY, maxZ);
        SkyIslandGroupPlanV2 draft = new SkyIslandGroupPlanV2(
                1,
                SkyIslandGroupPlanV2.SKY_ISLAND_GROUP_CONTRACT_VERSION,
                featureId,
                kernel,
                groundReferenceYBlocks,
                minimumAllowedYBlocks,
                maximumAllowedYBlocks,
                sorted,
                featureAabb,
                new SkyIslandGroupPlanV2.ArtifactBinding(
                        1, sdfPlan.canonicalChecksum(),
                        SkyIslandGroupPlanV2.ArtifactBinding.SDF_CONTRACT),
                new SkyIslandGroupPlanV2.ArtifactBinding(
                        1, csgPlan.canonicalChecksum(),
                        SkyIslandGroupPlanV2.ArtifactBinding.CSG_CONTRACT),
                SkyIslandGroupPlanV2.ResourceBudget.standard(),
                ZERO);
        return new CompiledSkyIslandGroupV2(codec.sealSkyIslandGroupPlan(draft), sdfPlan, csgPlan);
    }

    static void requireComponentGeometry(
            SkyIslandGroupPlanV2.IslandComponent component,
            int groundReferenceYBlocks,
            int minimumAllowedYBlocks,
            int maximumAllowedYBlocks,
            SkyIslandGroupPlanV2.Kernel kernel
    ) {
        SkyIslandGroupPlanV2.BoxSpec lobe = component.lobe();
        SkyIslandGroupPlanV2.BoxSpec underside = component.underside();
        long lobeMinY = Math.subtractExact(
                lobe.center().yMillionths(), lobe.halfExtentsMillionths().yMillionths());
        long lobeMaxY = Math.addExact(
                lobe.center().yMillionths(), lobe.halfExtentsMillionths().yMillionths());
        int lobeMinYBlocks = (int) Math.floorDiv(lobeMinY, M);
        int lobeMaxYBlocks = (int) Math.floorDiv(lobeMaxY, M);
        if (lobeMinYBlocks < minimumAllowedYBlocks || lobeMaxYBlocks > maximumAllowedYBlocks) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.OUT_OF_Y,
                    "sky island component out of allowed Y range");
        }
        long clearance = Math.subtractExact(lobeMinY, Math.multiplyExact((long) groundReferenceYBlocks, M));
        if (clearance < Math.multiplyExact((long) kernel.minimumGroundClearanceBlocks(), M)) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.TOUCHING_GROUND,
                    "sky island ground clearance below minimum");
        }
        long thickness = Math.subtractExact(lobeMaxY, lobeMinY);
        if (thickness < Math.multiplyExact((long) kernel.minimumThicknessBlocks(), M)) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.THIN_COMPONENT,
                    "sky island lobe thinner than minimum");
        }
        long underMinY = Math.subtractExact(
                underside.center().yMillionths(), underside.halfExtentsMillionths().yMillionths());
        long underMaxY = Math.addExact(
                underside.center().yMillionths(), underside.halfExtentsMillionths().yMillionths());
        if (underMaxY > lobeMaxY || underMinY < lobeMinY) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                    "underside carve must sit inside the lobe Y span");
        }
        if (underMaxY >= lobeMaxY) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.THIN_COMPONENT,
                    "underside carve leaves no top class");
        }
    }

    static void requireGaps(List<SkyIslandGroupPlanV2.IslandComponent> components, int minimumGapBlocks) {
        long minGap = Math.multiplyExact((long) minimumGapBlocks, M);
        for (int i = 0; i < components.size(); i++) {
            VolumeSdfAabbV2 a = lobeAabb(components.get(i).lobe());
            for (int j = i + 1; j < components.size(); j++) {
                VolumeSdfAabbV2 b = lobeAabb(components.get(j).lobe());
                if (a.intersects(b)) {
                    throw new SkyIslandGroupExceptionV2(
                            SkyIslandGroupFailureCodeV2.MERGED_COMPONENTS,
                            "sky island components overlap");
                }
                long gap = horizontalGap(a, b);
                if (gap < minGap) {
                    throw new SkyIslandGroupExceptionV2(
                            SkyIslandGroupFailureCodeV2.INSUFFICIENT_GAP,
                            "sky island inter-island gap below minimum");
                }
            }
        }
    }

    static void requireNoDenseAirFill(
            List<SkyIslandGroupPlanV2.IslandComponent> components,
            int groundReferenceYBlocks
    ) {
        // Reject lobes that fill the entire column from ground to island top (dense air fill via solid).
        long groundY = Math.multiplyExact((long) groundReferenceYBlocks, M);
        for (SkyIslandGroupPlanV2.IslandComponent component : components) {
            long lobeMinY = Math.subtractExact(
                    component.lobe().center().yMillionths(),
                    component.lobe().halfExtentsMillionths().yMillionths());
            if (lobeMinY <= groundY) {
                throw new SkyIslandGroupExceptionV2(
                        SkyIslandGroupFailureCodeV2.DENSE_AIR_FILL,
                        "sky island must not solid-fill from ground reference upward");
            }
        }
    }

    static void requireAddThenCarvePairs(List<VolumeCsgPlanV2.Operator> operators) {
        if (operators.isEmpty() || operators.size() % 2 != 0) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                    "sky island requires ADD_SOLID/CARVE_SOLID pairs");
        }
        for (int i = 0; i < operators.size(); i += 2) {
            VolumeCsgPlanV2.Operator add = operators.get(i);
            VolumeCsgPlanV2.Operator carve = operators.get(i + 1);
            if (add.kind() != VolumeCsgPlanV2.OperationKind.ADD_SOLID
                    || carve.kind() != VolumeCsgPlanV2.OperationKind.CARVE_SOLID
                    || !add.fluidBodyId().isEmpty()
                    || !carve.fluidBodyId().isEmpty()
                    || !carve.dependsOnOperatorIds().contains(add.operatorId())) {
                throw new SkyIslandGroupExceptionV2(
                        SkyIslandGroupFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                        "sky island operators must be ordered ADD_SOLID then CARVE_SOLID");
            }
        }
    }

    private static VolumeSdfAabbV2 lobeAabb(SkyIslandGroupPlanV2.BoxSpec lobe) {
        VolumeSdfVec3V2 center = lobe.center();
        VolumeSdfVec3V2 half = lobe.halfExtentsMillionths();
        return new VolumeSdfAabbV2(
                Math.subtractExact(center.xMillionths(), half.xMillionths()),
                Math.subtractExact(center.yMillionths(), half.yMillionths()),
                Math.subtractExact(center.zMillionths(), half.zMillionths()),
                Math.addExact(center.xMillionths(), half.xMillionths()),
                Math.addExact(center.yMillionths(), half.yMillionths()),
                Math.addExact(center.zMillionths(), half.zMillionths()));
    }

    private static long horizontalGap(VolumeSdfAabbV2 a, VolumeSdfAabbV2 b) {
        long dx = 0L;
        if (a.maxXMillionths() < b.minXMillionths()) {
            dx = Math.subtractExact(b.minXMillionths(), a.maxXMillionths());
        } else if (b.maxXMillionths() < a.minXMillionths()) {
            dx = Math.subtractExact(a.minXMillionths(), b.maxXMillionths());
        }
        long dz = 0L;
        if (a.maxZMillionths() < b.minZMillionths()) {
            dz = Math.subtractExact(b.minZMillionths(), a.maxZMillionths());
        } else if (b.maxZMillionths() < a.minZMillionths()) {
            dz = Math.subtractExact(a.minZMillionths(), b.maxZMillionths());
        }
        if (dx == 0L && dz == 0L) {
            // Overlap in both X and Z projections means vertical stack or touch — treat as merged/gap 0.
            return 0L;
        }
        if (dx == 0L) {
            return dz;
        }
        if (dz == 0L) {
            return dx;
        }
        // Chebyshev-like: minimum axis separation when separated in both axes.
        return Math.min(dx, dz);
    }
}
