package com.github.nankotsu029.landformcraft.generator.v2.volume.index;

import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeAabbIndexPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stable bulk builder for V2-5-03 AABB index descriptors. Input entry/operator order does not
 * affect the sealed plan: entries are normalized to CSG ordinal order.
 */
public final class VolumeAabbIndexBuilderV2 {
    public static final String KERNEL_VERSION = VolumeAabbIndexPlanV2.Kernel.VERSION;
    private static final long FIXED_SCALE = VolumeSdfPrimitivePlanV2.Quantization.FIXED_SCALE;

    private VolumeAabbIndexBuilderV2() {
    }

    public static VolumeAabbIndexPlanV2 buildDraft(
            VolumeCsgPlanV2 csgPlan,
            VolumeSdfPrimitivePlanV2 primitivePlan,
            int supportRadiusXZBlocks,
            int supportRadiusYBlocks
    ) {
        Objects.requireNonNull(csgPlan, "csgPlan");
        Objects.requireNonNull(primitivePlan, "primitivePlan");
        csgPlan.requirePrimitivePlan(primitivePlan);
        if (supportRadiusXZBlocks < 0
                || supportRadiusXZBlocks > VolumeAabbIndexPlanV2.MAXIMUM_SUPPORT_BLOCKS
                || supportRadiusYBlocks < 0
                || supportRadiusYBlocks > VolumeAabbIndexPlanV2.MAXIMUM_SUPPORT_BLOCKS) {
            throw new VolumeAabbIndexExceptionV2(
                    VolumeAabbIndexFailureCodeV2.BUDGET_EXCEEDED, "support radius out of range");
        }
        Map<String, VolumeSdfPrimitiveV2> primitives = new HashMap<>();
        for (VolumeSdfPrimitiveV2 primitive : primitivePlan.primitives()) {
            primitives.put(primitive.primitiveId(), primitive);
        }
        List<VolumeAabbIndexPlanV2.Entry> entries = new ArrayList<>();
        try {
            for (VolumeCsgPlanV2.Operator operator : csgPlan.operators()) {
                VolumeSdfAabbV2 aabb = conservativeOperatorAabb(operator, primitives);
                VolumeSdfAabbV2 indexed = aabb.expandAxes(
                        Math.multiplyExact(supportRadiusXZBlocks, FIXED_SCALE),
                        Math.multiplyExact(supportRadiusYBlocks, FIXED_SCALE),
                        Math.multiplyExact(supportRadiusXZBlocks, FIXED_SCALE));
                entries.add(new VolumeAabbIndexPlanV2.Entry(
                        "entry." + operator.operatorId(),
                        operator.operatorId(),
                        operator.ordinal(),
                        indexed,
                        supportRadiusXZBlocks,
                        supportRadiusYBlocks));
            }
        } catch (ArithmeticException exception) {
            throw new VolumeAabbIndexExceptionV2(
                    VolumeAabbIndexFailureCodeV2.ARITHMETIC_OVERFLOW,
                    "AABB index support expand overflow",
                    exception);
        } catch (IllegalArgumentException exception) {
            throw new VolumeAabbIndexExceptionV2(
                    VolumeAabbIndexFailureCodeV2.INVALID_AABB, exception.getMessage(), exception);
        }
        entries.sort(Comparator.comparingInt(VolumeAabbIndexPlanV2.Entry::ordinal));
        return new VolumeAabbIndexPlanV2(
                VolumeAabbIndexPlanV2.VERSION,
                VolumeAabbIndexPlanV2.INDEX_CONTRACT_VERSION,
                new VolumeAabbIndexPlanV2.CsgPlanBinding(
                        1, csgPlan.canonicalChecksum(), "volume-aabb-index-csg-binding-v1"),
                VolumeAabbIndexPlanV2.Kernel.standard(),
                entries,
                new VolumeAabbIndexPlanV2.ResourceBudget(
                        VolumeAabbIndexPlanV2.ResourceBudget.VERSION,
                        VolumeAabbIndexPlanV2.MAXIMUM_ENTRIES,
                        VolumeAabbIndexPlanV2.MAXIMUM_QUERY_RESULTS,
                        4_096L,
                        VolumeAabbIndexPlanV2.MAX_CANONICAL_BYTES,
                        64L * 1024L,
                        VolumeAabbIndexPlanV2.MAXIMUM_ENTRIES),
                "0".repeat(64));
    }

    /**
     * Builds from an arbitrary permutation of operator-derived entries; result is ordinal-sorted
     * and identical for the same CSG/SDF inputs.
     */
    public static VolumeAabbIndexPlanV2 buildFromEntries(
            VolumeCsgPlanV2 csgPlan,
            List<VolumeAabbIndexPlanV2.Entry> unorderedEntries
    ) {
        Objects.requireNonNull(csgPlan, "csgPlan");
        List<VolumeAabbIndexPlanV2.Entry> sorted = List.copyOf(Objects.requireNonNull(
                unorderedEntries, "unorderedEntries")).stream()
                .sorted(Comparator.comparingInt(VolumeAabbIndexPlanV2.Entry::ordinal)
                        .thenComparing(VolumeAabbIndexPlanV2.Entry::entryId))
                .toList();
        return new VolumeAabbIndexPlanV2(
                VolumeAabbIndexPlanV2.VERSION,
                VolumeAabbIndexPlanV2.INDEX_CONTRACT_VERSION,
                new VolumeAabbIndexPlanV2.CsgPlanBinding(
                        1, csgPlan.canonicalChecksum(), "volume-aabb-index-csg-binding-v1"),
                VolumeAabbIndexPlanV2.Kernel.standard(),
                sorted,
                new VolumeAabbIndexPlanV2.ResourceBudget(
                        VolumeAabbIndexPlanV2.ResourceBudget.VERSION,
                        VolumeAabbIndexPlanV2.MAXIMUM_ENTRIES,
                        VolumeAabbIndexPlanV2.MAXIMUM_QUERY_RESULTS,
                        4_096L,
                        VolumeAabbIndexPlanV2.MAX_CANONICAL_BYTES,
                        64L * 1024L,
                        VolumeAabbIndexPlanV2.MAXIMUM_ENTRIES),
                "0".repeat(64));
    }

    static VolumeSdfAabbV2 conservativeOperatorAabb(
            VolumeCsgPlanV2.Operator operator,
            Map<String, VolumeSdfPrimitiveV2> primitives
    ) {
        VolumeSdfPrimitiveV2 primary = primitives.get(operator.primitiveId());
        if (primary == null) {
            throw new VolumeAabbIndexExceptionV2(
                    VolumeAabbIndexFailureCodeV2.BINDING_MISMATCH, "unknown primitive for index");
        }
        VolumeSdfAabbV2 aabb = primary.conservativeBounds();
        if (operator.mask() == VolumeCsgPlanV2.MaskMode.INTERSECTION_WITH_PRIMITIVE) {
            VolumeSdfPrimitiveV2 mask = primitives.get(operator.maskPrimitiveId());
            if (mask == null) {
                throw new VolumeAabbIndexExceptionV2(
                        VolumeAabbIndexFailureCodeV2.BINDING_MISMATCH, "unknown mask primitive");
            }
            return aabb.intersection(mask.conservativeBounds()).orElse(aabb);
        }
        return aabb;
    }
}
