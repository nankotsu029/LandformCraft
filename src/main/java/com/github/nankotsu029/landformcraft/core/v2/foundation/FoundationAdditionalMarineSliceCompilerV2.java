package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.AbyssalPlainGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.SeamountGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AbyssalPlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationAdditionalMarineValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SeamountPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-10-04 ABYSSAL_PLAIN and SEAMOUNT marine foundation slices. */
public final class FoundationAdditionalMarineSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final OceanBasinPlanCompilerV2 basinCompiler = new OceanBasinPlanCompilerV2();
    private final AbyssalPlainPlanCompilerV2 abyssalCompiler = new AbyssalPlainPlanCompilerV2();
    private final SeamountPlanCompilerV2 seamountCompiler = new SeamountPlanCompilerV2();

    public FoundationAbyssalSliceV2 compileAbyssal(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        TerrainIntentV2.Feature basinFeature = requireFeature(intent, TerrainIntentV2.FeatureKind.OCEAN_BASIN);
        TerrainIntentV2.Feature abyssalFeature = requireFeature(intent, TerrainIntentV2.FeatureKind.ABYSSAL_PLAIN);
        OceanBasinPlanV2 basinPlan = codec.sealOceanBasinPlan(basinCompiler.compile(
                basinFeature, intent, bounds, codec.geometryChecksum(basinFeature.geometry())));
        AbyssalPlainPlanV2 abyssalPlan = codec.sealAbyssalPlainPlan(abyssalCompiler.compile(
                abyssalFeature, intent, bounds, codec.geometryChecksum(abyssalFeature.geometry()), basinPlan));

        AbyssalPlainGeneratorV2 generator = new AbyssalPlainGeneratorV2(abyssalPlan, basinPlan);
        AbyssalPlainGeneratorV2.AbyssalPlainMetrics metrics = generator.evaluate();
        if (!allMarineMetrics(metrics)) {
            throw metricsFailure("abyssal", metrics);
        }

        FoundationAdditionalMarineValidationArtifactV2 validation = sealValidation(
                abyssalPlan.featureId(), toMarineMetrics(metrics));
        FoundationPreviewIndexV2 preview = sealPreview(abyssalPlan.featureId(), abyssalPlan.canonicalChecksum(),
                AbyssalPlainPlanV2.DEPTH_FIELD_ID, AbyssalPlainPlanV2.OWNERSHIP_FIELD_ID, bounds);
        return new FoundationAbyssalSliceV2(
                basinPlan, abyssalPlan, validation, preview,
                generator.exportChecksum(), basinPlan.canonicalChecksum());
    }

    public FoundationSeamountSliceV2 compileSeamount(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        TerrainIntentV2.Feature basinFeature = requireFeature(intent, TerrainIntentV2.FeatureKind.OCEAN_BASIN);
        TerrainIntentV2.Feature seamountFeature = requireFeature(intent, TerrainIntentV2.FeatureKind.SEAMOUNT);
        OceanBasinPlanV2 basinPlan = codec.sealOceanBasinPlan(basinCompiler.compile(
                basinFeature, intent, bounds, codec.geometryChecksum(basinFeature.geometry())));
        SeamountPlanV2 seamountPlan = codec.sealSeamountPlan(seamountCompiler.compile(
                seamountFeature, intent, bounds, codec.geometryChecksum(seamountFeature.geometry()), basinPlan));

        SeamountGeneratorV2 generator = new SeamountGeneratorV2(seamountPlan, basinPlan);
        SeamountGeneratorV2.SeamountMetrics metrics = generator.evaluate();
        if (!allMarineMetrics(metrics)) {
            throw metricsFailure("seamount", metrics);
        }

        FoundationAdditionalMarineValidationArtifactV2 validation = sealValidation(
                seamountPlan.featureId(), toMarineMetrics(metrics));
        FoundationPreviewIndexV2 preview = sealPreview(seamountPlan.featureId(), seamountPlan.canonicalChecksum(),
                SeamountPlanV2.RELIEF_FIELD_ID, SeamountPlanV2.OWNERSHIP_FIELD_ID, bounds);
        return new FoundationSeamountSliceV2(
                basinPlan, seamountPlan, validation, preview,
                generator.exportChecksum(), basinPlan.canonicalChecksum());
    }

    private FoundationAdditionalMarineValidationArtifactV2 sealValidation(
            String featureId,
            FoundationAdditionalMarineValidationArtifactV2.Metrics metrics
    ) {
        return codec.sealFoundationAdditionalMarineValidationArtifact(
                new FoundationAdditionalMarineValidationArtifactV2(
                        FoundationAdditionalMarineValidationArtifactV2.VERSION,
                        FoundationAdditionalMarineValidationArtifactV2.CONTRACT_VERSION,
                        featureId,
                        metrics,
                        List.of(),
                        "0".repeat(64)));
    }

    private FoundationPreviewIndexV2 sealPreview(
            String featureId,
            String planChecksum,
            String primaryFieldId,
            String ownershipFieldId,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "primary", primaryFieldId, planChecksum));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "ownership", ownershipFieldId, planChecksum));
        return codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
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
                    "additional marine slice requires a " + kind + " feature");
        }
        return feature.get();
    }

    private static boolean allMarineMetrics(AbyssalPlainGeneratorV2.AbyssalPlainMetrics metrics) {
        return metrics.basinContainmentOk() && metrics.depthReliefOk() && metrics.slopeOk()
                && metrics.transitionOk() && metrics.wholeTileOk() && metrics.budgetOk() && metrics.exportOk();
    }

    private static boolean allMarineMetrics(SeamountGeneratorV2.SeamountMetrics metrics) {
        return metrics.basinContainmentOk() && metrics.depthReliefOk() && metrics.slopeOk()
                && metrics.transitionOk() && metrics.wholeTileOk() && metrics.budgetOk() && metrics.exportOk();
    }

    private static FoundationAdditionalMarineValidationArtifactV2.Metrics toMarineMetrics(
            AbyssalPlainGeneratorV2.AbyssalPlainMetrics metrics
    ) {
        return new FoundationAdditionalMarineValidationArtifactV2.Metrics(
                metrics.basinContainmentOk(),
                metrics.depthReliefOk(),
                metrics.slopeOk(),
                metrics.transitionOk(),
                metrics.wholeTileOk(),
                metrics.budgetOk(),
                metrics.exportOk());
    }

    private static FoundationAdditionalMarineValidationArtifactV2.Metrics toMarineMetrics(
            SeamountGeneratorV2.SeamountMetrics metrics
    ) {
        return new FoundationAdditionalMarineValidationArtifactV2.Metrics(
                metrics.basinContainmentOk(),
                metrics.depthReliefOk(),
                metrics.slopeOk(),
                metrics.transitionOk(),
                metrics.wholeTileOk(),
                metrics.budgetOk(),
                metrics.exportOk());
    }

    private static FoundationSliceException metricsFailure(String kind, Object metrics) {
        return new FoundationSliceException("v2.additional-marine-metrics",
                kind + " metrics failed: " + metrics);
    }

    public record FoundationAbyssalSliceV2(
            OceanBasinPlanV2 basin,
            AbyssalPlainPlanV2 abyssal,
            FoundationAdditionalMarineValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String exportChecksum,
            String hostBasinChecksum
    ) {
    }

    public record FoundationSeamountSliceV2(
            OceanBasinPlanV2 basin,
            SeamountPlanV2 seamount,
            FoundationAdditionalMarineValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String exportChecksum,
            String hostBasinChecksum
    ) {
    }
}
