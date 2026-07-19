package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.islandreef.AdvancedIslandReefCompositionGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AdvancedIslandReefCatalogContractV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AtollPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.BarrierIslandPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationAdvancedIslandReefValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SingleIslandPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Orchestrates V2-10-08 barrier/atoll COMPOSITE_PRESET slices and catalog contract sealing. */
public final class FoundationAdvancedIslandReefSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final SingleIslandPlanCompilerV2 islandCompiler = new SingleIslandPlanCompilerV2();
    private final BarrierIslandPlanCompilerV2 barrierCompiler = new BarrierIslandPlanCompilerV2();
    private final AtollPlanCompilerV2 atollCompiler = new AtollPlanCompilerV2();

    public BarrierIslandCompositionV2 compileBarrier(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        TerrainIntentV2.Feature islandFeature = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.SINGLE_ISLAND)
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException("v2.barrier-island-missing-island",
                        "barrier island slice requires a SINGLE_ISLAND feature"));

        SingleIslandPlanV2 islandPlan = codec.sealSingleIslandPlan(islandCompiler.compile(
                islandFeature, intent, bounds, codec.geometryChecksum(islandFeature.geometry())));
        BarrierIslandPlanV2 preset = codec.sealBarrierIslandPlan(
                barrierCompiler.compile(intent, bounds, islandPlan));

        AdvancedIslandReefCompositionGeneratorV2 generator =
                new AdvancedIslandReefCompositionGeneratorV2(preset);
        AdvancedIslandReefCompositionGeneratorV2.CompositionMetrics metrics = generator.evaluate();
        FoundationAdvancedIslandReefValidationArtifactV2.Metrics barrierMetrics =
                new FoundationAdvancedIslandReefValidationArtifactV2.Metrics(
                        shoreParallelRidgeOk(preset),
                        false,
                        true,
                        preset.marineConnectivityOk(),
                        metrics.budgetOk() && preset.estimatedWorkUnits() <= BarrierIslandPlanV2.MAXIMUM_WORK_UNITS,
                        metrics.exportOk(),
                        true,
                        true);
        if (!allMetrics(barrierMetrics)) {
            throw metricsFailure(barrierMetrics);
        }
        FoundationAdvancedIslandReefValidationArtifactV2 validation = buildValidation(
                preset.islandFeatureId(), barrierMetrics, metrics);

        FoundationPreviewIndexV2 preview = buildPreview(bounds, preset.geometryChecksum(), preset.canonicalChecksum());
        return new BarrierIslandCompositionV2(preset, islandPlan, validation, preview, metrics.exportChecksum());
    }

    public AtollCompositionV2 compileAtoll(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        AtollPlanV2 preset = codec.sealAtollPlan(atollCompiler.compile(intent, bounds));
        AdvancedIslandReefCompositionGeneratorV2 generator =
                new AdvancedIslandReefCompositionGeneratorV2(preset);
        AdvancedIslandReefCompositionGeneratorV2.CompositionMetrics metrics = generator.evaluate();

        FoundationAdvancedIslandReefValidationArtifactV2 validation = buildValidation(
                preset.reefFeatureId(),
                new FoundationAdvancedIslandReefValidationArtifactV2.Metrics(
                        false,
                        lagoonPassMarineOk(preset, intent),
                        isletOwnershipOk(preset),
                        true,
                        metrics.budgetOk() && preset.estimatedWorkUnits() <= AtollPlanV2.MAXIMUM_WORK_UNITS,
                        metrics.exportOk(),
                        true,
                        true),
                metrics);

        if (!allMetrics(validation.metrics())) {
            throw metricsFailure(validation.metrics());
        }

        FoundationPreviewIndexV2 preview = buildPreview(bounds, preset.geometryChecksum(), preset.canonicalChecksum());
        return new AtollCompositionV2(preset, validation, preview, metrics.exportChecksum());
    }

    public AdvancedIslandReefCatalogContractV2 compileCatalog() {
        return codec.sealAdvancedIslandReefCatalogContract(
                AdvancedIslandReefCatalogContractV2.decisionV21008());
    }

    private FoundationAdvancedIslandReefValidationArtifactV2 buildValidation(
            String presetFeatureId,
            FoundationAdvancedIslandReefValidationArtifactV2.Metrics metrics,
            AdvancedIslandReefCompositionGeneratorV2.CompositionMetrics generatorMetrics
    ) {
        if (!metrics.exportOk() || !generatorMetrics.wholeTileOk()) {
            throw metricsFailure(metrics);
        }
        return codec.sealFoundationAdvancedIslandReefValidationArtifact(
                new FoundationAdvancedIslandReefValidationArtifactV2(
                        FoundationAdvancedIslandReefValidationArtifactV2.VERSION,
                        FoundationAdvancedIslandReefValidationArtifactV2.CONTRACT_VERSION,
                        presetFeatureId,
                        metrics,
                        List.of(),
                        "0".repeat(64)));
    }

    private static boolean shoreParallelRidgeOk(BarrierIslandPlanV2 preset) {
        return !preset.islandGeometryChecksum().isBlank()
                && !preset.lagoonGeometryChecksum().isBlank()
                && preset.marineConnectivityOk();
    }

    private static boolean lagoonPassMarineOk(AtollPlanV2 preset, TerrainIntentV2 intent) {
        try {
            AtollPlanCompilerV2.validateLagoonEnclosedByReef(intent, preset.lagoonFeatureId(), preset.reefFeatureId());
            AtollPlanCompilerV2.validatePassConnectivity(
                    intent, preset.reefPassFeatureId(), preset.reefFeatureId(), preset.lagoonFeatureId());
            return true;
        } catch (FoundationSliceException exception) {
            return false;
        }
    }

    private static boolean isletOwnershipOk(AtollPlanV2 preset) {
        if (preset.optionalIsletFeatureId().isBlank()) {
            return true;
        }
        return !AtollPlanV2.EMPTY_CHECKSUM.equals(preset.isletGeometryChecksum());
    }

    private static boolean allMetrics(FoundationAdvancedIslandReefValidationArtifactV2.Metrics metrics) {
        return (metrics.shoreParallelRidgeOk() || metrics.lagoonPassMarineOk())
                && metrics.isletOwnershipOk()
                && metrics.transitionOk()
                && metrics.budgetOk()
                && metrics.exportOk()
                && metrics.childContractsReused();
    }

    private FoundationPreviewIndexV2 buildPreview(
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum,
            String canonicalChecksum
    ) {
        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "composition-geometry", "foundation.island-reef.geometry", geometryChecksum));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "composition-canonical", "foundation.island-reef.canonical", canonicalChecksum));
        return codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));
    }

    private static FoundationSliceException metricsFailure(
            FoundationAdvancedIslandReefValidationArtifactV2.Metrics metrics
    ) {
        return new FoundationSliceException("v2.advanced-island-reef-metrics",
                "advanced island/reef metrics failed: " + metrics);
    }

    public record BarrierIslandCompositionV2(
            BarrierIslandPlanV2 barrierIsland,
            SingleIslandPlanV2 island,
            FoundationAdvancedIslandReefValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String exportChecksum
    ) {
    }

    public record AtollCompositionV2(
            AtollPlanV2 atoll,
            FoundationAdvancedIslandReefValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String exportChecksum
    ) {
    }
}
