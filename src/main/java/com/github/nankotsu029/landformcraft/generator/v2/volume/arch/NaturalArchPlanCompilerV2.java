package com.github.nankotsu029.landformcraft.generator.v2.volume.arch;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.NaturalArchPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.util.List;
import java.util.Objects;

/**
 * Compiles a {@code NATURAL_ARCH}: two-pier {@code ADD_SOLID} mass with ordered through
 * {@code CARVE_SOLID} opening.
 */
public final class NaturalArchPlanCompilerV2 {
    public static final String MODULE_ID = "generate.volume-natural-arch";
    public static final String LIFECYCLE = "SUPPORTED";

    private static final String ZERO = "0".repeat(64);
    private static final long M = 1_000_000L;

    private NaturalArchPlanCompilerV2() {
    }

    public record CompiledNaturalArchV2(
            NaturalArchPlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
    }

    public static CompiledNaturalArchV2 compile(
            String featureId,
            NaturalArchPlanV2.PassageAxis passageAxis,
            VolumeSdfVec3V2 massCenter,
            VolumeSdfVec3V2 massHalfExtents,
            long massCornerRadiusMillionths,
            VolumeSdfVec3V2 openingCenter,
            VolumeSdfVec3V2 openingHalfExtents,
            long openingCornerRadiusMillionths,
            NaturalArchPlanV2.Kernel kernel
    ) {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(passageAxis, "passageAxis");
        Objects.requireNonNull(massCenter, "massCenter");
        Objects.requireNonNull(massHalfExtents, "massHalfExtents");
        Objects.requireNonNull(openingCenter, "openingCenter");
        Objects.requireNonNull(openingHalfExtents, "openingHalfExtents");
        Objects.requireNonNull(kernel, "kernel");
        if (!NaturalArchPlanV2.Kernel.VERSION.equals(kernel.kernelVersion())) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.UNKNOWN_KERNEL, "unsupported natural-arch kernel");
        }
        NaturalArchPlanV2.MassSpec mass = new NaturalArchPlanV2.MassSpec(
                massCenter, massHalfExtents, massCornerRadiusMillionths);
        NaturalArchPlanV2.OpeningSpec opening = new NaturalArchPlanV2.OpeningSpec(
                openingCenter, openingHalfExtents, openingCornerRadiusMillionths);
        requireThroughOpening(passageAxis, mass, opening);
        requireTwoPiers(passageAxis, mass, opening, kernel.minimumPierBlocks());
        requireCrown(mass, opening, kernel.minimumCrownBlocks());
        requireSpan(passageAxis, mass, opening, kernel.minimumSpanBlocks());

        String massId = "prim.arch.mass." + featureId;
        String openingId = "prim.arch.opening." + featureId;
        VolumeSdfPrimitiveV2 massPrim = new VolumeSdfPrimitiveV2.RoundedBox(
                massId, mass.center(), mass.halfExtentsMillionths(), mass.cornerRadiusMillionths());
        VolumeSdfPrimitiveV2 openingPrim = new VolumeSdfPrimitiveV2.RoundedBox(
                openingId,
                opening.center(),
                opening.halfExtentsMillionths(),
                opening.cornerRadiusMillionths());
        VolumeSdfAabbV2 massBounds = massPrim.conservativeBounds();
        VolumeSdfAabbV2 openingBounds = openingPrim.conservativeBounds();
        VolumeSdfAabbV2 featureAabb = new VolumeSdfAabbV2(
                Math.min(massBounds.minXMillionths(), openingBounds.minXMillionths()),
                Math.min(massBounds.minYMillionths(), openingBounds.minYMillionths()),
                Math.min(massBounds.minZMillionths(), openingBounds.minZMillionths()),
                Math.max(massBounds.maxXMillionths(), openingBounds.maxXMillionths()),
                Math.max(massBounds.maxYMillionths(), openingBounds.maxYMillionths()),
                Math.max(massBounds.maxZMillionths(), openingBounds.maxZMillionths()));

        List<VolumeSdfPrimitiveV2> primitives = List.of(massPrim, openingPrim);
        List<VolumeCsgPlanV2.Operator> operators = List.of(
                new VolumeCsgPlanV2.Operator(
                        "op.add.arch." + featureId,
                        0,
                        VolumeCsgPlanV2.OperationKind.ADD_SOLID,
                        massId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of(),
                        ""),
                new VolumeCsgPlanV2.Operator(
                        "op.carve.arch." + featureId,
                        1,
                        VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                        openingId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of("op.add.arch." + featureId),
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

        NaturalArchPlanV2 draft = new NaturalArchPlanV2(
                1,
                NaturalArchPlanV2.NATURAL_ARCH_CONTRACT_VERSION,
                featureId,
                kernel,
                passageAxis,
                mass,
                opening,
                featureAabb,
                new NaturalArchPlanV2.ArtifactBinding(
                        1, sdfPlan.canonicalChecksum(), NaturalArchPlanV2.ArtifactBinding.SDF_CONTRACT),
                new NaturalArchPlanV2.ArtifactBinding(
                        1, csgPlan.canonicalChecksum(), NaturalArchPlanV2.ArtifactBinding.CSG_CONTRACT),
                NaturalArchPlanV2.ResourceBudget.standard(),
                ZERO);
        return new CompiledNaturalArchV2(codec.sealNaturalArchPlan(draft), sdfPlan, csgPlan);
    }

    static void requireThroughOpening(
            NaturalArchPlanV2.PassageAxis axis,
            NaturalArchPlanV2.MassSpec mass,
            NaturalArchPlanV2.OpeningSpec opening
    ) {
        long massMinY = Math.subtractExact(
                mass.center().yMillionths(), mass.halfExtentsMillionths().yMillionths());
        long massMaxY = Math.addExact(
                mass.center().yMillionths(), mass.halfExtentsMillionths().yMillionths());
        long openMinY = Math.subtractExact(
                opening.center().yMillionths(), opening.halfExtentsMillionths().yMillionths());
        long openMaxY = Math.addExact(
                opening.center().yMillionths(), opening.halfExtentsMillionths().yMillionths());
        if (!rangesOverlap(massMinY, massMaxY, openMinY, openMaxY)) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.CLOSED_OPENING,
                    "arch opening must overlap the mass vertically");
        }
        boolean breaksThrough = switch (axis) {
            case Z -> {
                long massHalfZ = mass.halfExtentsMillionths().zMillionths();
                long openHalfZ = opening.halfExtentsMillionths().zMillionths();
                yield openHalfZ > massHalfZ
                        && Math.abs(opening.center().zMillionths() - mass.center().zMillionths())
                        <= Math.subtractExact(openHalfZ, massHalfZ);
            }
            case X -> {
                long massHalfX = mass.halfExtentsMillionths().xMillionths();
                long openHalfX = opening.halfExtentsMillionths().xMillionths();
                yield openHalfX > massHalfX
                        && Math.abs(opening.center().xMillionths() - mass.center().xMillionths())
                        <= Math.subtractExact(openHalfX, massHalfX);
            }
        };
        if (!breaksThrough) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.CLOSED_OPENING,
                    "arch opening does not break through both passage faces");
        }
    }

    static void requireTwoPiers(
            NaturalArchPlanV2.PassageAxis axis,
            NaturalArchPlanV2.MassSpec mass,
            NaturalArchPlanV2.OpeningSpec opening,
            int minimumPierBlocks
    ) {
        long minPier = Math.multiplyExact((long) minimumPierBlocks, M);
        long left;
        long right;
        if (axis == NaturalArchPlanV2.PassageAxis.Z) {
            long massMinX = Math.subtractExact(
                    mass.center().xMillionths(), mass.halfExtentsMillionths().xMillionths());
            long massMaxX = Math.addExact(
                    mass.center().xMillionths(), mass.halfExtentsMillionths().xMillionths());
            long openMinX = Math.subtractExact(
                    opening.center().xMillionths(), opening.halfExtentsMillionths().xMillionths());
            long openMaxX = Math.addExact(
                    opening.center().xMillionths(), opening.halfExtentsMillionths().xMillionths());
            left = Math.subtractExact(openMinX, massMinX);
            right = Math.subtractExact(massMaxX, openMaxX);
        } else {
            long massMinZ = Math.subtractExact(
                    mass.center().zMillionths(), mass.halfExtentsMillionths().zMillionths());
            long massMaxZ = Math.addExact(
                    mass.center().zMillionths(), mass.halfExtentsMillionths().zMillionths());
            long openMinZ = Math.subtractExact(
                    opening.center().zMillionths(), opening.halfExtentsMillionths().zMillionths());
            long openMaxZ = Math.addExact(
                    opening.center().zMillionths(), opening.halfExtentsMillionths().zMillionths());
            left = Math.subtractExact(openMinZ, massMinZ);
            right = Math.subtractExact(massMaxZ, openMaxZ);
        }
        if (left < minPier || right < minPier) {
            if (left < minPier && right < minPier) {
                throw new NaturalArchExceptionV2(
                        NaturalArchFailureCodeV2.THIN_PIER, "both arch piers thinner than minimum");
            }
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.ONE_PIER,
                    "arch has only one pier meeting minimum thickness");
        }
    }

    static void requireCrown(
            NaturalArchPlanV2.MassSpec mass,
            NaturalArchPlanV2.OpeningSpec opening,
            int minimumCrownBlocks
    ) {
        long massMaxY = Math.addExact(
                mass.center().yMillionths(), mass.halfExtentsMillionths().yMillionths());
        long openMaxY = Math.addExact(
                opening.center().yMillionths(), opening.halfExtentsMillionths().yMillionths());
        long crown = Math.subtractExact(massMaxY, openMaxY);
        long minimum = Math.multiplyExact((long) minimumCrownBlocks, M);
        if (crown < minimum) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.THIN_CROWN, "arch crown thinner than minimum");
        }
    }

    static void requireSpan(
            NaturalArchPlanV2.PassageAxis axis,
            NaturalArchPlanV2.MassSpec mass,
            NaturalArchPlanV2.OpeningSpec opening,
            int minimumSpanBlocks
    ) {
        long span = switch (axis) {
            case Z -> Math.multiplyExact(2L, opening.halfExtentsMillionths().xMillionths());
            case X -> Math.multiplyExact(2L, opening.halfExtentsMillionths().zMillionths());
        };
        long minimum = Math.multiplyExact((long) minimumSpanBlocks, M);
        if (span < minimum) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.SHORT_SPAN, "arch span shorter than minimum");
        }
        // Opening must sit inside the mass span axis so both piers remain.
        boolean inside = switch (axis) {
            case Z -> {
                long massMinX = Math.subtractExact(
                        mass.center().xMillionths(), mass.halfExtentsMillionths().xMillionths());
                long massMaxX = Math.addExact(
                        mass.center().xMillionths(), mass.halfExtentsMillionths().xMillionths());
                long openMinX = Math.subtractExact(
                        opening.center().xMillionths(), opening.halfExtentsMillionths().xMillionths());
                long openMaxX = Math.addExact(
                        opening.center().xMillionths(), opening.halfExtentsMillionths().xMillionths());
                yield openMinX > massMinX && openMaxX < massMaxX;
            }
            case X -> {
                long massMinZ = Math.subtractExact(
                        mass.center().zMillionths(), mass.halfExtentsMillionths().zMillionths());
                long massMaxZ = Math.addExact(
                        mass.center().zMillionths(), mass.halfExtentsMillionths().zMillionths());
                long openMinZ = Math.subtractExact(
                        opening.center().zMillionths(), opening.halfExtentsMillionths().zMillionths());
                long openMaxZ = Math.addExact(
                        opening.center().zMillionths(), opening.halfExtentsMillionths().zMillionths());
                yield openMinZ > massMinZ && openMaxZ < massMaxZ;
            }
        };
        if (!inside) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.ONE_PIER,
                    "arch opening must leave pier mass on both span ends");
        }
    }

    static void requireAddThenCarve(List<VolumeCsgPlanV2.Operator> operators) {
        if (operators.size() != 2) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                    "natural arch requires exactly ADD_SOLID then CARVE_SOLID");
        }
        VolumeCsgPlanV2.Operator add = operators.get(0);
        VolumeCsgPlanV2.Operator carve = operators.get(1);
        if (add.kind() != VolumeCsgPlanV2.OperationKind.ADD_SOLID
                || carve.kind() != VolumeCsgPlanV2.OperationKind.CARVE_SOLID
                || !add.fluidBodyId().isEmpty()
                || !carve.fluidBodyId().isEmpty()) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                    "natural arch operators must be ADD_SOLID then CARVE_SOLID without fluid");
        }
        if (carve.dependsOnOperatorIds().isEmpty()
                || !carve.dependsOnOperatorIds().contains(add.operatorId())) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                    "arch CARVE_SOLID must depend on preceding ADD_SOLID (non-commutative)");
        }
    }

    private static boolean rangesOverlap(long aMin, long aMax, long bMin, long bMax) {
        return aMin <= bMax && bMin <= aMax;
    }
}
