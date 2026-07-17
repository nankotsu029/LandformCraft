package com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta;

import com.github.nankotsu029.landformcraft.model.v2.DeltaPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Independent EXPERIMENTAL graph-integrity hook for V2-3-07 corruption fixtures. */
public final class DeltaGraphValidatorV2 {
    private DeltaGraphValidatorV2() {
    }

    public static void requireValid(
            DeltaPlanV2 plan,
            List<DeltaPlanV2.DistributaryBranch> branches
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(branches, "branches");
        Set<String> branchIds = new HashSet<>();
        Set<String> mouthNodes = new HashSet<>();
        long discharge = 0L;
        for (DeltaPlanV2.DistributaryBranch branch : branches) {
            if (!branchIds.add(branch.branchId()) || !mouthNodes.add(branch.toNodeId())) {
                throw failure("v2.delta-dead-branch", "duplicate delta branch or mouth node");
            }
            if (!branch.fromNodeId().equals(plan.apexNodeId())) {
                throw failure("v2.delta-dead-branch", "delta branch is disconnected from the apex");
            }
            if (branch.toNodeId().equals(plan.apexNodeId())) {
                throw failure("v2.delta-loop", "delta distributary graph contains a loop");
            }
            if (!branch.path().getFirst().equals(plan.apex())) {
                throw failure("v2.delta-dead-branch", "delta branch path does not begin at the apex");
            }
            if (!onBoundary(branch.path().getLast(), plan)) {
                throw failure("v2.delta-landlocked-mouth", "delta branch mouth is not on the receiving sea boundary");
            }
            discharge = Math.addExact(discharge, branch.dischargeShareMillionths());
        }
        if (branches.size() != plan.selectedDistributaryCount()) {
            throw failure("v2.delta-dead-branch", "delta active branch count does not match the frozen plan");
        }
        if (discharge != TerrainIntentV2.FIXED_SCALE) {
            throw failure("v2.delta-flow-conservation", "delta branch discharge is not conserved");
        }
    }

    private static boolean onBoundary(DeltaPlanV2.FanPoint point, DeltaPlanV2 plan) {
        long maximumX = Math.multiplyExact((long) plan.width() - 1L, TerrainIntentV2.FIXED_SCALE);
        long maximumZ = Math.multiplyExact((long) plan.length() - 1L, TerrainIntentV2.FIXED_SCALE);
        return switch (plan.receivingSeaBoundary()) {
            case NORTH -> point.zMillionths() == 0L;
            case EAST -> point.xMillionths() == maximumX;
            case SOUTH -> point.zMillionths() == maximumZ;
            case WEST -> point.xMillionths() == 0L;
        };
    }

    private static DeltaGenerationException failure(String ruleId, String message) {
        return new DeltaGenerationException(ruleId, message);
    }
}
