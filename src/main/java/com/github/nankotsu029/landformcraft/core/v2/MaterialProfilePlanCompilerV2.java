package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;

/** Compiles the frozen V2-4-07 semantic material profile: closed catalog, fixed rule order, checksum bindings. */
public final class MaterialProfilePlanCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public MaterialProfilePlanV2 compile(
            GeologyPlanV2 geologyPlan,
            LithologyPlanV2 lithologyPlan,
            StrataPlanV2 strataPlan,
            WaterConditionPlanV2 waterConditionPlan,
            SnowPlanV2 snowPlan
    ) {
        strataPlan.requireLithologyPlan(geologyPlan, lithologyPlan);
        long cells = Math.multiplyExact((long) geologyPlan.width(), geologyPlan.length());
        MaterialProfilePlanV2.Kernel kernel = MaterialProfilePlanV2.Kernel.standard();
        int maximumWindowSize = Math.min(256, Math.max(geologyPlan.width(), geologyPlan.length()));
        int windowWidth = Math.min(geologyPlan.width(), maximumWindowSize);
        int windowLength = Math.min(geologyPlan.length(), maximumWindowSize);
        long maximumWorkingBytes = Math.multiplyExact(
                Math.multiplyExact((long) windowWidth, windowLength), (long) Integer.BYTES);
        long retainedBytes = 8L * 1024L;
        long cpuWorkUnits = Math.multiplyExact(cells, MaterialProfilePlanV2.RESOLUTION_RULE_COUNT);

        MaterialProfilePlanV2 draft = new MaterialProfilePlanV2(
                MaterialProfilePlanV2.VERSION,
                MaterialProfilePlanV2.PROFILE_CONTRACT_VERSION,
                new MaterialProfilePlanV2.GeologyBinding(
                        MaterialProfilePlanV2.GeologyBinding.VERSION,
                        geologyPlan.canonicalChecksum(),
                        lithologyPlan.canonicalChecksum(),
                        strataPlan.canonicalChecksum(),
                        MaterialProfilePlanV2.GeologyBinding.CONTRACT_VERSION),
                new MaterialProfilePlanV2.WaterConditionBinding(
                        MaterialProfilePlanV2.WaterConditionBinding.VERSION,
                        waterConditionPlan.canonicalChecksum(),
                        MaterialProfilePlanV2.WaterConditionBinding.WETNESS_FIELD_ID,
                        MaterialProfilePlanV2.WaterConditionBinding.CONTRACT_VERSION),
                new MaterialProfilePlanV2.SnowBinding(
                        MaterialProfilePlanV2.SnowBinding.VERSION,
                        snowPlan.canonicalChecksum(),
                        MaterialProfilePlanV2.SnowBinding.SNOW_COVER_FIELD_ID,
                        MaterialProfilePlanV2.SnowBinding.CONTRACT_VERSION),
                builtInCatalog(),
                MaterialProfilePlanV2.ResolutionRule.standardOrder(),
                kernel,
                new MaterialProfilePlanV2.ResourceBudget(
                        MaterialProfilePlanV2.ResourceBudget.VERSION,
                        cells,
                        MaterialProfilePlanV2.RESOLUTION_RULE_COUNT,
                        cpuWorkUnits,
                        retainedBytes,
                        maximumWindowSize,
                        maximumWorkingBytes,
                        MaterialProfilePlanV2.MAX_CANONICAL_BYTES),
                "0".repeat(64));
        draft.requireGeologyPlan(geologyPlan, lithologyPlan, strataPlan);
        draft.requireWaterConditionPlan(waterConditionPlan);
        draft.requireSnowPlan(snowPlan);
        return codec.sealMaterialProfilePlan(draft);
    }

    private static MaterialProfilePlanV2.Catalog builtInCatalog() {
        java.util.List<MaterialProfilePlanV2.Entry> entries = java.util.Arrays
                .stream(MaterialProfilePlanV2.SemanticMaterialClass.values())
                .map(kind -> new MaterialProfilePlanV2.Entry(
                        kind, kind.classId(), kind.compactCode(), kind.substrate(),
                        kind.wetVariant(), kind.snowVariant()))
                .toList();
        return new MaterialProfilePlanV2.Catalog(
                MaterialProfilePlanV2.Catalog.VERSION,
                MaterialProfilePlanV2.Catalog.ID,
                MaterialProfilePlanV2.Catalog.CONTRACT_VERSION,
                entries,
                new MaterialProfilePlanV2.CatalogBudget(
                        MaterialProfilePlanV2.CatalogBudget.VERSION,
                        MaterialProfilePlanV2.SemanticMaterialClass.values().length,
                        16L * 1024L));
    }
}
