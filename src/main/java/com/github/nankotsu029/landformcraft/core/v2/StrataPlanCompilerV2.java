package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;

import java.util.ArrayList;
import java.util.List;

/** Compiles province-bound strata profiles and the explicit V2-3 hydrology geology-input handoff. */
public final class StrataPlanCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public StrataPlanV2 compile(GeologyPlanV2 geologyPlan, LithologyPlanV2 lithologyPlan) {
        lithologyPlan.requireGeologyPlan(geologyPlan);
        List<StrataPlanV2.Profile> profiles = new ArrayList<>();
        for (LithologyPlanV2.ProvinceAssignment assignment : lithologyPlan.provinceAssignments()) {
            profiles.add(defaultProfile(assignment, lithologyPlan.catalog()));
        }
        int totalLayers = profiles.stream().mapToInt(profile -> profile.layers().size()).sum();
        long cells = Math.multiplyExact((long) geologyPlan.width(), geologyPlan.length());
        long cpu = Math.multiplyExact((long) Math.max(1, totalLayers), cells);
        long retained = Math.addExact(2_048L, Math.multiplyExact(
                (long) profiles.size() + totalLayers, 192L));
        StrataPlanV2 draft = new StrataPlanV2(
                StrataPlanV2.VERSION,
                StrataPlanV2.PROFILE_CONTRACT_VERSION,
                geologyPlan.canonicalChecksum(),
                lithologyPlan.canonicalChecksum(),
                profiles,
                new StrataPlanV2.HydrologyGeologyInputHandoff(
                        StrataPlanV2.HydrologyGeologyInputHandoff.VERSION,
                        StrataPlanV2.SourcePriorKind.UNIFORM_GEOLOGY_PRIOR,
                        HydrologyPlanV2.FixedPriors.CHECKSUM,
                        HydrologyReconciliationPlanV2.ALGORITHM_VERSION,
                        geologyPlan.canonicalChecksum(),
                        lithologyPlan.canonicalChecksum(),
                        StrataPlanV2.InputMode.SURFACE_EXPOSED_STRATA_SCALARS,
                        StrataPlanV2.TransitionMode.EXPLICIT_VERSION_TRANSITION),
                new StrataPlanV2.ResourceBudget(
                        StrataPlanV2.ResourceBudget.VERSION,
                        StrataPlanV2.MAX_PROFILES,
                        StrataPlanV2.MAX_LAYERS_PER_PROFILE,
                        StrataPlanV2.MAX_TOTAL_LAYERS,
                        StrataPlanV2.MAX_STACK_THICKNESS_BLOCKS,
                        cells,
                        cpu,
                        retained,
                        128L * 1024L),
                "0".repeat(64));
        draft.requireLithologyPlan(geologyPlan, lithologyPlan);
        return codec.sealStrataPlan(draft);
    }

    private static StrataPlanV2.Profile defaultProfile(
            LithologyPlanV2.ProvinceAssignment assignment,
            LithologyPlanV2.Catalog catalog
    ) {
        LithologyPlanV2.Entry entry = catalog.requireByCode(assignment.lithologyCode());
        if (!entry.lithologyId().equals(assignment.lithologyId())) {
            throw new IllegalArgumentException("strata default profile lithology mismatch");
        }
        // Bottom basement + province lithology cover keeps ordered multi-layer exposure testable
        // without allocating a dense 3D stack. HARD_INTRUSIVE provinces stay single-layer.
        List<StrataPlanV2.Layer> layers;
        if (entry.kind() == LithologyPlanV2.SemanticLithology.HARD_INTRUSIVE) {
            layers = List.of(new StrataPlanV2.Layer(
                    0, entry.lithologyId(), entry.lithologyCode(),
                    StrataPlanV2.DEFAULT_STACK_THICKNESS_BLOCKS));
        } else {
            LithologyPlanV2.Entry basement = catalog.requireByCode(
                    LithologyPlanV2.SemanticLithology.HARD_INTRUSIVE.compactCode());
            layers = List.of(
                    new StrataPlanV2.Layer(0, basement.lithologyId(), basement.lithologyCode(), 48),
                    new StrataPlanV2.Layer(1, entry.lithologyId(), entry.lithologyCode(), 16));
        }
        return new StrataPlanV2.Profile(
                "strata-" + assignment.provinceId(),
                assignment.provinceId(),
                assignment.provinceCode(),
                assignment.formationId(),
                assignment.formationCode(),
                StrataPlanV2.LayerOrder.BOTTOM_TO_TOP,
                layers,
                StrataPlanV2.FoldTilt.flat());
    }
}
