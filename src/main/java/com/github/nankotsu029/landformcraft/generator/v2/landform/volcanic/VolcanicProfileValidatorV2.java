package com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic;

import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Detects frozen volcanic island-id and child-hook corruption before raster publish. */
public final class VolcanicProfileValidatorV2 {
    private VolcanicProfileValidatorV2() {
    }

    public static void requireValid(VolcanicPlanV2 plan) {
        Objects.requireNonNull(plan, "plan");
        Set<String> pointIds = new HashSet<>();
        Set<Integer> indices = new HashSet<>();
        for (VolcanicPlanV2.IslandMass island : plan.islands()) {
            if (!pointIds.add(island.pointId()) || !indices.add(island.islandIndex())) {
                throw new VolcanicGenerationException(
                        "v2.volcanic-duplicate-island", "duplicate volcanic island pointId or index");
            }
        }
        for (VolcanicPlanV2.SubmarineSaddle saddle : plan.saddles()) {
            if (!pointIds.contains(saddle.fromPointId()) || !pointIds.contains(saddle.toPointId())
                    || saddle.fromPointId().equals(saddle.toPointId())) {
                throw new VolcanicGenerationException(
                        "v2.volcanic-unknown-point", "submarine saddle has an invalid island endpoint");
            }
        }
        if (plan.calderaPlanHook() == null && plan.lavaPlanHook() != null) {
            throw new VolcanicGenerationException(
                    "v2.volcanic-unknown-child", "lava hook exists without a caldera hook");
        }
        if (plan.calderaPlanHook() != null) {
            if (!pointIds.contains(plan.calderaPlanHook().hostPointId())) {
                throw new VolcanicGenerationException(
                        "v2.volcanic-orphan-caldera", "caldera host island does not exist");
            }
            if (plan.lavaPlanHook() != null
                    && !plan.lavaPlanHook().calderaFeatureId().equals(plan.calderaPlanHook().calderaFeatureId())) {
                throw new VolcanicGenerationException(
                        "v2.volcanic-unknown-child", "lava hook targets a different caldera");
            }
        }
    }
}
