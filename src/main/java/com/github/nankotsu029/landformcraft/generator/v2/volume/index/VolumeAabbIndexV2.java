package com.github.nankotsu029.landformcraft.generator.v2.volume.index;

import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeAabbIndexPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable AABB overlap index for V2-5-03. Queries expand the tile by XZ/Y halo and return
 * overlapping operators in ascending ordinal order. Does not evaluate voxels or CSG samples.
 */
public final class VolumeAabbIndexV2 {
    public static final String KERNEL_VERSION = VolumeAabbIndexPlanV2.Kernel.VERSION;
    private static final long FIXED_SCALE = VolumeSdfPrimitivePlanV2.Quantization.FIXED_SCALE;

    private final VolumeAabbIndexPlanV2 plan;
    private final List<IndexedEntry> entries;

    public VolumeAabbIndexV2(VolumeAabbIndexPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (!KERNEL_VERSION.equals(plan.kernel().kernelVersion())) {
            throw new VolumeAabbIndexExceptionV2(
                    VolumeAabbIndexFailureCodeV2.UNKNOWN_KERNEL, "unsupported AABB index kernel");
        }
        List<IndexedEntry> built = new ArrayList<>(plan.entries().size());
        for (VolumeAabbIndexPlanV2.Entry entry : plan.entries()) {
            built.add(new IndexedEntry(entry));
        }
        this.entries = List.copyOf(built);
    }

    public VolumeAabbIndexPlanV2 plan() {
        return plan;
    }

    public void requireCsgPlan(VolumeCsgPlanV2 csgPlan) {
        try {
            plan.requireCsgPlan(csgPlan);
        } catch (IllegalArgumentException exception) {
            throw new VolumeAabbIndexExceptionV2(
                    VolumeAabbIndexFailureCodeV2.BINDING_MISMATCH, exception.getMessage());
        }
    }

    /**
     * Returns operators whose indexed AABB intersects the tile expanded by halo.
     * Result order is ascending ordinal (canonical CSG order among candidates).
     */
    public List<VolumeAabbIndexHitV2> queryOverlapping(
            VolumeSdfAabbV2 tileAabb,
            int haloXZBlocks,
            int haloYBlocks
    ) {
        Objects.requireNonNull(tileAabb, "tileAabb");
        if (haloXZBlocks < 0 || haloYBlocks < 0) {
            throw new VolumeAabbIndexExceptionV2(
                    VolumeAabbIndexFailureCodeV2.BUDGET_EXCEEDED, "halo must be non-negative");
        }
        VolumeSdfAabbV2 query;
        try {
            query = tileAabb.expandAxes(
                    Math.multiplyExact(haloXZBlocks, FIXED_SCALE),
                    Math.multiplyExact(haloYBlocks, FIXED_SCALE),
                    Math.multiplyExact(haloXZBlocks, FIXED_SCALE));
        } catch (ArithmeticException exception) {
            throw new VolumeAabbIndexExceptionV2(
                    VolumeAabbIndexFailureCodeV2.ARITHMETIC_OVERFLOW,
                    "tile halo expand overflow",
                    exception);
        }
        List<VolumeAabbIndexHitV2> hits = new ArrayList<>();
        for (IndexedEntry entry : entries) {
            if (entry.aabb().intersects(query)) {
                hits.add(new VolumeAabbIndexHitV2(
                        entry.entryId(), entry.operatorId(), entry.ordinal(), entry.aabb()));
                if (hits.size() > plan.kernel().maximumQueryResults()
                        || hits.size() > plan.budget().maximumQueryResults()) {
                    throw new VolumeAabbIndexExceptionV2(
                            VolumeAabbIndexFailureCodeV2.BUDGET_EXCEEDED,
                            "AABB index query-result budget exceeded");
                }
            }
        }
        return List.copyOf(hits);
    }

    private record IndexedEntry(
            String entryId,
            String operatorId,
            int ordinal,
            VolumeSdfAabbV2 aabb
    ) {
        IndexedEntry(VolumeAabbIndexPlanV2.Entry entry) {
            this(entry.entryId(), entry.operatorId(), entry.ordinal(), entry.aabb());
        }
    }
}
