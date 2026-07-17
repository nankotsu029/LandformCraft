package com.github.nankotsu029.landformcraft.generator.v2.landform.fjord;

import com.github.nankotsu029.landformcraft.model.v2.FjordPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;
import java.util.Objects;

/** Detects frozen fjord wall-hook and profile corruption before canonical raster publish. */
public final class FjordProfileValidatorV2 {
    private FjordProfileValidatorV2() {
    }

    public static void requireValid(FjordPlanV2 plan, TerrainIntentV2 intent) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(intent, "intent");
        FjordPlanV2.GlacialWallPlanHook hook = plan.glacialWallPlanHook();
        if (hook == null) {
            return;
        }
        List<TerrainIntentV2.Relation> flanks = intent.relations().stream()
                .filter(relation -> relation.id().equals(hook.flanksRelationId()))
                .toList();
        if (flanks.size() != 1) {
            throw new FjordGenerationException("v2.fjord-broken-wall", "fjord wall FLANKS relation is missing or duplicated");
        }
        TerrainIntentV2.Relation relation = flanks.getFirst();
        if (relation.kind() != TerrainIntentV2.RelationKind.FLANKS
                || relation.strength() != TerrainIntentV2.Strength.HARD
                || !relation.to().equals("feature:" + plan.featureId())
                || !relation.from().equals("feature:" + hook.wallFeatureId())) {
            throw new FjordGenerationException("v2.fjord-broken-wall", "fjord wall FLANKS relation is corrupted");
        }
        TerrainIntentV2.Feature wall = intent.features().stream()
                .filter(feature -> feature.id().equals(hook.wallFeatureId()))
                .findFirst()
                .orElseThrow(() -> new FjordGenerationException(
                        "v2.fjord-broken-wall", "glacial wall feature is missing"));
        if (wall.kind() != TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE) {
            throw new FjordGenerationException(
                    "v2.fjord-broken-wall", "fjord FLANKS hook must originate at GLACIAL_MOUNTAIN_RANGE");
        }
    }
}
