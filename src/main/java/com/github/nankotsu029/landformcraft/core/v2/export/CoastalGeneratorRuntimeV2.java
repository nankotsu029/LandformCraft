package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalRasterKernelV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.beach.SandyBeachGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater.BreakwaterHarborGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.cape.RockyCapeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.harbor.HarborBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionCompositorV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionLayerSourcesV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalTransitionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the four V2-2 coastal generators and their transition compositor from a frozen Blueprint.
 * Layer order follows the sealed contributor order, so the runtime never depends on module
 * registration order.
 */
record CoastalGeneratorRuntimeV2(
        SandyBeachGeneratorV2 beach,
        HarborBasinGeneratorV2 harbor,
        BreakwaterHarborGeneratorV2 breakwater,
        RockyCapeGeneratorV2 cape,
        CoastalTransitionCompositorV2 compositor
) {
    private static final Set<TerrainIntentV2.FeatureKind> REQUIRED =
            EnumSet.of(
                    TerrainIntentV2.FeatureKind.SANDY_BEACH,
                    TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                    TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                    TerrainIntentV2.FeatureKind.ROCKY_CAPE);

    static CoastalGeneratorRuntimeV2 create(WorldBlueprintV2 blueprint) {
        Objects.requireNonNull(blueprint, "blueprint");
        if (blueprint.coastalTransitionPlans().size() != 1) {
            throw new IllegalArgumentException(
                    "surface-2_5d export requires exactly one sealed coastal transition plan");
        }
        int width = blueprint.space().bounds().width();
        int length = blueprint.space().bounds().length();
        CoastalTransitionPlanV2 plan = blueprint.coastalTransitionPlans().getFirst();
        SandyBeachGeneratorV2 beach = null;
        HarborBasinGeneratorV2 harbor = null;
        BreakwaterHarborGeneratorV2 breakwater = null;
        RockyCapeGeneratorV2 cape = null;
        List<CoastalTransitionCompositorV2.LayerBinding> bindings = new ArrayList<>();
        var seen = EnumSet.noneOf(TerrainIntentV2.FeatureKind.class);
        for (CoastalTransitionPlanV2.Contributor contributor : plan.contributors()) {
            CoastalFeaturePlanV2 coastal = blueprint.coastalFeaturePlans().stream()
                    .filter(candidate -> candidate.featureId().equals(contributor.featureId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "coastal contributor has no sealed feature plan: " + contributor.featureId()));
            if (!seen.add(contributor.kind())) {
                throw new IllegalArgumentException(
                        "surface-2_5d export supports one contributor per coastal kind: " + contributor.kind());
            }
            switch (contributor.kind()) {
                case SANDY_BEACH -> {
                    beach = new SandyBeachGeneratorV2(blueprint.sandyBeachPlans().getFirst(),
                            new CoastalRasterKernelV2(coastal, width, length));
                    bindings.add(CoastalTransitionLayerSourcesV2.beach(
                            contributor, beach, HardLandWaterSourceV2.NONE));
                }
                case HARBOR_BASIN -> {
                    harbor = new HarborBasinGeneratorV2(
                            blueprint.harborBasinPlans().getFirst(), coastal, width, length);
                    bindings.add(CoastalTransitionLayerSourcesV2.harbor(
                            contributor, harbor, HardLandWaterSourceV2.NONE));
                }
                case BREAKWATER_HARBOR -> {
                    breakwater = new BreakwaterHarborGeneratorV2(
                            blueprint.breakwaterHarborPlans().getFirst(), coastal, width, length);
                    bindings.add(CoastalTransitionLayerSourcesV2.breakwater(contributor, breakwater));
                }
                case ROCKY_CAPE -> {
                    cape = new RockyCapeGeneratorV2(
                            blueprint.rockyCapePlans().getFirst(), coastal, width, length);
                    bindings.add(CoastalTransitionLayerSourcesV2.cape(
                            contributor, cape, HardLandWaterSourceV2.NONE));
                }
                default -> throw new IllegalArgumentException(
                        "surface-2_5d export does not support coastal contributor kind " + contributor.kind());
            }
        }
        if (!seen.containsAll(REQUIRED)) {
            throw new IllegalArgumentException(
                    "surface-2_5d export requires all four V2-2 coastal contributors; missing "
                            + EnumSet.copyOf(REQUIRED).stream().filter(kind -> !seen.contains(kind)).toList());
        }
        return new CoastalGeneratorRuntimeV2(beach, harbor, breakwater, cape,
                new CoastalTransitionCompositorV2(plan, width, length, bindings));
    }
}
