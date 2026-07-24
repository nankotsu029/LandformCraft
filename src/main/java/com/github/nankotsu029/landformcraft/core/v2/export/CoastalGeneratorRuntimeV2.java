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
 * Builds the declared V2-2 coastal generators and their transition compositor from a frozen
 * Blueprint. Layer order follows the sealed contributor order, so the runtime never depends on
 * module registration order.
 *
 * <p>V2-19-09 (ADR 0040 D1): the contributor set is <em>any</em> subset of the four coastal kinds,
 * including none at all. The former "all four" check was a coverage requirement placed on the
 * modifier tier, which ADR 0038 D5-3 forbids — surface modifiers are not foundation owners and carry
 * no coverage obligation. Domain coverage is enforced once, by the V2-18-10 foundation owner gate
 * ({@link SurfaceFoundationOwnerGateV2}); a request with no explicit macro foundation input is
 * rejected there whatever its contributor count, so no second coverage rule lives here (D2).</p>
 *
 * <p>An absent kind contributes nothing: its generator is {@code null}, it binds no compositor layer,
 * and {@link CoastalSurfaceFieldsV2} writes the canonical OUTSIDE values of that kind's descriptor
 * fields (D3). {@code compositor} is {@code null} only when the intent declares no coastal feature at
 * all, because {@code CoastalTransitionPlanV2} requires at least one contributor and the blueprint
 * compiler therefore seals no transition plan in that case.</p>
 */
record CoastalGeneratorRuntimeV2(
        SandyBeachGeneratorV2 beach,
        HarborBasinGeneratorV2 harbor,
        BreakwaterHarborGeneratorV2 breakwater,
        RockyCapeGeneratorV2 cape,
        CoastalTransitionCompositorV2 compositor
) {
    /**
     * The coastal kinds this runtime can build a contributor for. It is the domain of the subset rule,
     * not a requirement: {@link ProductionRoutePreconditionsV2} reports the (now empty) runtime
     * companion requirement separately, and nothing here demands that any of these be declared.
     */
    static Set<TerrainIntentV2.FeatureKind> supportedKinds() {
        return EnumSet.of(
                TerrainIntentV2.FeatureKind.SANDY_BEACH,
                TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                TerrainIntentV2.FeatureKind.ROCKY_CAPE);
    }

    static CoastalGeneratorRuntimeV2 create(WorldBlueprintV2 blueprint) {
        Objects.requireNonNull(blueprint, "blueprint");
        if (blueprint.coastalTransitionPlans().size() > 1) {
            throw new IllegalArgumentException(
                    "surface-2_5d export requires at most one sealed coastal transition plan");
        }
        if (blueprint.coastalTransitionPlans().isEmpty()) {
            // ADR 0040 D1, size 0: no coastal modifier at all. The macro foundation owns every cell.
            return new CoastalGeneratorRuntimeV2(null, null, null, null, null);
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
        return new CoastalGeneratorRuntimeV2(beach, harbor, breakwater, cape,
                new CoastalTransitionCompositorV2(plan, width, length, bindings));
    }

    /**
     * Composition at one cell, or the canonical inactive sample when no coastal modifier exists at
     * all. This is the same answer the compositor gives for a cell no declared contributor claims, so
     * the size-0 case needs no separate field semantics (ADR 0040 D3).
     */
    CoastalTransitionCompositorV2.CompositionSample composeAt(
            int globalX,
            int globalZ,
            HardLandWaterSourceV2 hardSource
    ) {
        return compositor == null
                ? CoastalTransitionCompositorV2.CompositionSample.outside()
                : compositor.sampleAt(globalX, globalZ, hardSource);
    }

    /** Beach descriptor sample, or the canonical OUTSIDE values when the kind is not declared. */
    SandyBeachGeneratorV2.BeachSample beachSampleOrNull(int globalX, int globalZ) {
        return beach == null ? null : beach.sampleAt(globalX, globalZ, HardLandWaterSourceV2.NONE);
    }

    /** Harbor descriptor sample, or {@code null} when the kind is not declared. */
    HarborBasinGeneratorV2.HarborSample harborSampleOrNull(int globalX, int globalZ) {
        return harbor == null ? null : harbor.sampleAt(globalX, globalZ, HardLandWaterSourceV2.NONE);
    }

    /** Breakwater descriptor sample, or {@code null} when the kind is not declared. */
    BreakwaterHarborGeneratorV2.BreakwaterSample breakwaterSampleOrNull(int globalX, int globalZ) {
        return breakwater == null ? null : breakwater.sampleAt(globalX, globalZ);
    }

    /** Cape descriptor sample, or {@code null} when the kind is not declared. */
    RockyCapeGeneratorV2.CapeSample capeSampleOrNull(int globalX, int globalZ) {
        return cape == null ? null : cape.sampleAt(globalX, globalZ, HardLandWaterSourceV2.NONE);
    }
}
