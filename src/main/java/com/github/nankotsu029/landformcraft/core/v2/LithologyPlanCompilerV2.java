package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;

import java.util.List;

/** Compiles the closed V2-4-02 catalog and explicitly assigns every frozen geology province. */
public final class LithologyPlanCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public LithologyPlanV2 compile(GeologyPlanV2 geologyPlan) {
        LithologyPlanV2.Catalog catalog = sealBuiltInCatalog();
        List<LithologyPlanV2.ProvinceAssignment> assignments = geologyPlan.provinces().stream()
                .map(province -> assignmentFor(province, catalog))
                .toList();
        long cpu = Math.max(1L, Math.addExact(catalog.entries().size(), assignments.size()));
        long retained = Math.addExact(1_024L, Math.multiplyExact(
                (long) catalog.entries().size() + assignments.size(), 128L));
        LithologyPlanV2 draft = new LithologyPlanV2(
                LithologyPlanV2.VERSION,
                LithologyPlanV2.ASSIGNMENT_CONTRACT_VERSION,
                geologyPlan.canonicalChecksum(),
                catalog,
                assignments,
                new LithologyPlanV2.ResourceBudget(
                        LithologyPlanV2.ResourceBudget.VERSION,
                        LithologyPlanV2.MAX_ASSIGNMENTS,
                        LithologyPlanV2.COMPACT_CODE_BITS,
                        cpu,
                        retained,
                        64L * 1024L),
                "0".repeat(64));
        draft.requireGeologyPlan(geologyPlan);
        return codec.sealLithologyPlan(draft);
    }

    private static LithologyPlanV2.ProvinceAssignment assignmentFor(
            GeologyPlanV2.ProvinceDescriptor province,
            LithologyPlanV2.Catalog catalog
    ) {
        LithologyPlanV2.Entry match = catalog.entries().stream()
                .filter(entry -> entry.hardnessMillionths() == province.hardnessMillionths()
                        && entry.permeabilityMillionths() == province.permeabilityMillionths())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "no built-in lithology matches geology province " + province.provinceId()));
        return new LithologyPlanV2.ProvinceAssignment(
                province.provinceId(), province.provinceCode(), province.formationId(), province.formationCode(),
                match.lithologyId(), match.lithologyCode());
    }

    private LithologyPlanV2.Catalog sealBuiltInCatalog() {
        List<LithologyPlanV2.Entry> entries = java.util.Arrays.stream(LithologyPlanV2.SemanticLithology.values())
                .map(kind -> new LithologyPlanV2.Entry(
                        kind, kind.lithologyId(), kind.compactCode(), kind.hardnessMillionths(),
                        kind.permeabilityMillionths(), kind.erosionResponse()))
                .toList();
        return codec.sealLithologyCatalog(new LithologyPlanV2.Catalog(
                LithologyPlanV2.Catalog.VERSION,
                LithologyPlanV2.Catalog.ID,
                LithologyPlanV2.Catalog.CONTRACT_VERSION,
                entries,
                new LithologyPlanV2.CatalogBudget(
                        LithologyPlanV2.CatalogBudget.VERSION,
                        LithologyPlanV2.SemanticLithology.values().length,
                        LithologyPlanV2.COMPACT_CODE_BITS,
                        32L * 1024L),
                "0".repeat(64)));
    }
}
