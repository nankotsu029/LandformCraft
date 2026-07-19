package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.CanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.feature.FeatureMaterialProfilePlanV2;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Compiles the frozen V2-4-12 feature material overlay bound to base material and feature geometry. */
public final class FeatureMaterialProfilePlanCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public FeatureMaterialProfilePlanV2 compile(
            MaterialProfilePlanV2 materialProfilePlan,
            GeologyPlanV2 geologyPlan,
            LithologyPlanV2 lithologyPlan,
            StrataPlanV2 strataPlan,
            List<VolcanicPlanV2> volcanicPlans,
            List<CanyonPlanV2> canyonPlans
    ) {
        Objects.requireNonNull(materialProfilePlan, "materialProfilePlan");
        Objects.requireNonNull(geologyPlan, "geologyPlan");
        Objects.requireNonNull(lithologyPlan, "lithologyPlan");
        Objects.requireNonNull(strataPlan, "strataPlan");
        Objects.requireNonNull(volcanicPlans, "volcanicPlans");
        Objects.requireNonNull(canyonPlans, "canyonPlans");
        strataPlan.requireLithologyPlan(geologyPlan, lithologyPlan);

        long cells = Math.multiplyExact((long) geologyPlan.width(), geologyPlan.length());
        int maximumWindowSize = Math.min(256, Math.max(geologyPlan.width(), geologyPlan.length()));
        int windowWidth = Math.min(geologyPlan.width(), maximumWindowSize);
        int windowLength = Math.min(geologyPlan.length(), maximumWindowSize);
        long maximumWorkingBytes = Math.multiplyExact(
                Math.multiplyExact((long) windowWidth, windowLength), (long) Integer.BYTES);
        long cpuWorkUnits = Math.multiplyExact(cells, FeatureMaterialProfilePlanV2.RESOLUTION_RULE_COUNT);

        List<FeatureMaterialProfilePlanV2.FeatureGeometryBinding> volcanicBindings = volcanicPlans.stream()
                .map(plan -> new FeatureMaterialProfilePlanV2.FeatureGeometryBinding(
                        FeatureMaterialProfilePlanV2.FeatureKind.VOLCANIC,
                        plan.featureId(),
                        plan.geometryChecksum()))
                .toList();
        List<FeatureMaterialProfilePlanV2.FeatureGeometryBinding> canyonBindings = canyonPlans.stream()
                .map(plan -> new FeatureMaterialProfilePlanV2.FeatureGeometryBinding(
                        FeatureMaterialProfilePlanV2.FeatureKind.CANYON,
                        plan.featureId(),
                        plan.geometryChecksum()))
                .toList();

        FeatureMaterialProfilePlanV2 draft = new FeatureMaterialProfilePlanV2(
                FeatureMaterialProfilePlanV2.VERSION,
                FeatureMaterialProfilePlanV2.PROFILE_CONTRACT_VERSION,
                new FeatureMaterialProfilePlanV2.MaterialProfileBinding(
                        FeatureMaterialProfilePlanV2.MaterialProfileBinding.VERSION,
                        materialProfilePlan.canonicalChecksum(),
                        FeatureMaterialProfilePlanV2.MaterialProfileBinding.CONTRACT_VERSION),
                new FeatureMaterialProfilePlanV2.GeologyBinding(
                        FeatureMaterialProfilePlanV2.GeologyBinding.VERSION,
                        geologyPlan.canonicalChecksum(),
                        lithologyPlan.canonicalChecksum(),
                        strataPlan.canonicalChecksum(),
                        FeatureMaterialProfilePlanV2.GeologyBinding.CONTRACT_VERSION),
                volcanicBindings,
                canyonBindings,
                builtInCatalog(),
                FeatureMaterialProfilePlanV2.ResolutionRule.standardOrder(),
                FeatureMaterialProfilePlanV2.ConflictRule.standardOrder(),
                FeatureMaterialProfilePlanV2.Kernel.standard(),
                new FeatureMaterialProfilePlanV2.ResourceBudget(
                        FeatureMaterialProfilePlanV2.ResourceBudget.VERSION,
                        cells,
                        FeatureMaterialProfilePlanV2.RESOLUTION_RULE_COUNT,
                        FeatureMaterialProfilePlanV2.CONFLICT_RULE_COUNT,
                        volcanicBindings.size(),
                        canyonBindings.size(),
                        cpuWorkUnits,
                        12L * 1024L,
                        maximumWindowSize,
                        maximumWorkingBytes,
                        FeatureMaterialProfilePlanV2.MAX_CANONICAL_BYTES),
                "0".repeat(64));
        draft.requireMaterialProfilePlan(materialProfilePlan);
        draft.requireGeologyPlan(geologyPlan, lithologyPlan, strataPlan);
        for (VolcanicPlanV2 volcanicPlan : volcanicPlans) {
            draft.requireVolcanicPlan(volcanicPlan);
        }
        for (CanyonPlanV2 canyonPlan : canyonPlans) {
            draft.requireCanyonPlan(canyonPlan);
        }
        return codec.sealFeatureMaterialProfilePlan(draft);
    }

    private static FeatureMaterialProfilePlanV2.Catalog builtInCatalog() {
        List<FeatureMaterialProfilePlanV2.Entry> entries = Arrays
                .stream(FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.values())
                .map(kind -> new FeatureMaterialProfilePlanV2.Entry(
                        kind, kind.classId(), kind.compactCode(), kind.featureKind()))
                .toList();
        return new FeatureMaterialProfilePlanV2.Catalog(
                FeatureMaterialProfilePlanV2.Catalog.VERSION,
                FeatureMaterialProfilePlanV2.Catalog.ID,
                FeatureMaterialProfilePlanV2.Catalog.CONTRACT_VERSION,
                entries,
                new FeatureMaterialProfilePlanV2.CatalogBudget(
                        FeatureMaterialProfilePlanV2.CatalogBudget.VERSION,
                        FeatureMaterialProfilePlanV2.FEATURE_CLASS_COUNT,
                        16L * 1024L));
    }
}
