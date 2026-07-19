package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.deposition.MoraineFieldGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.deposition.OutwashPlainGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationGlacialDepositionValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MoraineFieldPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OutwashPlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PermafrostPlainProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlainPlanV2;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-10-02 glacial deposition slices and permafrost plain profiles. */
public final class FoundationGlacialDepositionSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final MoraineFieldPlanCompilerV2 moraineCompiler = new MoraineFieldPlanCompilerV2();
    private final OutwashPlainPlanCompilerV2 outwashCompiler = new OutwashPlainPlanCompilerV2();
    private final PermafrostPlainProfileCompilerV2 permafrostCompiler = new PermafrostPlainProfileCompilerV2();
    private final PlainPlanCompilerV2 plainCompiler = new PlainPlanCompilerV2();

    public FoundationMoraineSliceV2 compileMoraine(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        TerrainIntentV2.Feature feature = requireFeature(intent, TerrainIntentV2.FeatureKind.MORAINE_FIELD);
        MoraineFieldPlanV2 plan = codec.sealMoraineFieldPlan(moraineCompiler.compile(
                feature, intent, bounds, codec.geometryChecksum(feature.geometry())));

        MoraineFieldGeneratorV2 generator = new MoraineFieldGeneratorV2(plan);
        MoraineFieldGeneratorV2.MoraineFieldMetrics metrics = generator.evaluate();
        if (!allMoraineMetrics(metrics)) {
            throw metricsFailure("moraine", metrics);
        }

        FoundationGlacialDepositionValidationArtifactV2 validation = sealValidation(
                plan.featureId(), toDepositionMetrics(metrics));
        return new FoundationMoraineSliceV2(plan, validation, generator.sceneChecksum(), generator.exportChecksum());
    }

    public FoundationOutwashSliceV2 compileOutwash(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        TerrainIntentV2.Feature feature = requireFeature(intent, TerrainIntentV2.FeatureKind.OUTWASH_PLAIN);
        OutwashPlainPlanV2 plan = codec.sealOutwashPlainPlan(outwashCompiler.compile(
                feature, intent, bounds, codec.geometryChecksum(feature.geometry())));

        OutwashPlainGeneratorV2 generator = new OutwashPlainGeneratorV2(plan);
        OutwashPlainGeneratorV2.OutwashPlainMetrics metrics = generator.evaluate();
        if (!allOutwashMetrics(metrics)) {
            throw metricsFailure("outwash", metrics);
        }

        FoundationGlacialDepositionValidationArtifactV2 validation = sealValidation(
                plan.featureId(), toDepositionMetrics(metrics));
        return new FoundationOutwashSliceV2(plan, validation, generator.sceneChecksum(), generator.exportChecksum());
    }

    public PermafrostPlainProfileV2 compilePermafrostProfile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        TerrainIntentV2.Feature plainFeature = requireFeature(intent, TerrainIntentV2.FeatureKind.PLAIN);
        String geometryChecksum = codec.geometryChecksum(plainFeature.geometry());
        PlainPlanV2 plainPlan = codec.sealPlainPlan(plainCompiler.compile(
                plainFeature, intent, bounds, geometryChecksum));
        return codec.sealPermafrostPlainProfile(permafrostCompiler.compile(
                intent, plainPlan, geometryChecksum));
    }

    private FoundationGlacialDepositionValidationArtifactV2 sealValidation(
            String featureId,
            FoundationGlacialDepositionValidationArtifactV2.Metrics metrics
    ) {
        return codec.sealFoundationGlacialDepositionValidationArtifact(
                new FoundationGlacialDepositionValidationArtifactV2(
                        FoundationGlacialDepositionValidationArtifactV2.VERSION,
                        FoundationGlacialDepositionValidationArtifactV2.CONTRACT_VERSION,
                        featureId,
                        metrics,
                        List.of(),
                        "0".repeat(64)));
    }

    private static TerrainIntentV2.Feature requireFeature(
            TerrainIntentV2 intent,
            TerrainIntentV2.FeatureKind kind
    ) {
        Optional<TerrainIntentV2.Feature> feature = intent.features().stream()
                .filter(item -> item.kind() == kind)
                .findFirst();
        if (feature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "glacial deposition slice requires a " + kind + " feature");
        }
        return feature.get();
    }

    private static boolean allMoraineMetrics(MoraineFieldGeneratorV2.MoraineFieldMetrics metrics) {
        return metrics.glacialParentOk() && metrics.sedimentOwnerOk() && metrics.ridgeOrFlowOk()
                && metrics.profileKindSeparated() && metrics.wholeTileOk() && metrics.budgetOk()
                && metrics.exportOk();
    }

    private static boolean allOutwashMetrics(OutwashPlainGeneratorV2.OutwashPlainMetrics metrics) {
        return metrics.glacialParentOk() && metrics.sedimentOwnerOk() && metrics.ridgeOrFlowOk()
                && metrics.profileKindSeparated() && metrics.wholeTileOk() && metrics.budgetOk()
                && metrics.exportOk();
    }

    private static FoundationGlacialDepositionValidationArtifactV2.Metrics toDepositionMetrics(
            MoraineFieldGeneratorV2.MoraineFieldMetrics metrics
    ) {
        return new FoundationGlacialDepositionValidationArtifactV2.Metrics(
                metrics.glacialParentOk(),
                metrics.sedimentOwnerOk(),
                metrics.ridgeOrFlowOk(),
                metrics.profileKindSeparated(),
                metrics.wholeTileOk(),
                metrics.budgetOk(),
                metrics.exportOk());
    }

    private static FoundationGlacialDepositionValidationArtifactV2.Metrics toDepositionMetrics(
            OutwashPlainGeneratorV2.OutwashPlainMetrics metrics
    ) {
        return new FoundationGlacialDepositionValidationArtifactV2.Metrics(
                metrics.glacialParentOk(),
                metrics.sedimentOwnerOk(),
                metrics.ridgeOrFlowOk(),
                metrics.profileKindSeparated(),
                metrics.wholeTileOk(),
                metrics.budgetOk(),
                metrics.exportOk());
    }

    private static FoundationSliceException metricsFailure(
            String kind,
            Object metrics
    ) {
        return new FoundationSliceException("v2.glacial-deposition-metrics",
                kind + " metrics failed: " + metrics);
    }

    public record FoundationMoraineSliceV2(
            MoraineFieldPlanV2 moraine,
            FoundationGlacialDepositionValidationArtifactV2 validation,
            String sceneExportChecksum,
            String exportChecksum
    ) {
    }

    public record FoundationOutwashSliceV2(
            OutwashPlainPlanV2 outwash,
            FoundationGlacialDepositionValidationArtifactV2 validation,
            String sceneExportChecksum,
            String exportChecksum
    ) {
    }
}
