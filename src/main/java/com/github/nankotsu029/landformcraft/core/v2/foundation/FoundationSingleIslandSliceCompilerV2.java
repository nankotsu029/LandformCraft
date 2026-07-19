package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.island.SingleIslandGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationSingleIslandValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SingleIslandPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-07 SINGLE_ISLAND vertical slice compile, validation, and preview export. */
public final class FoundationSingleIslandSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final SingleIslandPlanCompilerV2 islandCompiler = new SingleIslandPlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationSingleIslandSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Optional<TerrainIntentV2.Feature> islandFeature = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.SINGLE_ISLAND)
                .findFirst();
        if (islandFeature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "single-island slice requires a SINGLE_ISLAND feature");
        }
        TerrainIntentV2.Feature feature = islandFeature.get();
        SingleIslandPlanV2 islandPlan = codec.sealSingleIslandPlan(islandCompiler.compile(
                feature, intent, bounds, codec.geometryChecksum(feature.geometry())));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        islandPlan.featureId(), 1, 16, 0,
                        SurfaceFoundationPlanV2.SurfaceClassCode.ISLAND)),
                List.of());

        SingleIslandGeneratorV2 generator = new SingleIslandGeneratorV2(islandPlan);
        SingleIslandGeneratorV2.IslandMetrics metrics = generator.evaluate();
        FoundationSingleIslandValidationArtifactV2 validation =
                codec.sealFoundationSingleIslandValidationArtifact(
                        new FoundationSingleIslandValidationArtifactV2(
                                FoundationSingleIslandValidationArtifactV2.VERSION,
                                FoundationSingleIslandValidationArtifactV2.CONTRACT_VERSION,
                                islandPlan.featureId(),
                                new FoundationSingleIslandValidationArtifactV2.Metrics(
                                        metrics.islandMassPresent(),
                                        metrics.shoreBandPresent(),
                                        metrics.radialDrainagePresent(),
                                        metrics.apronPresent(),
                                        metrics.supportBudgetOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "island-mask", SingleIslandPlanV2.ISLAND_MASK_FIELD_ID, islandPlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "shore", SingleIslandPlanV2.SHORE_FIELD_ID, islandPlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationSingleIslandSliceV2(islandPlan, foundation, validation, preview);
    }

    public record FoundationSingleIslandSliceV2(
            SingleIslandPlanV2 island,
            SurfaceFoundationPlanV2 foundation,
            FoundationSingleIslandValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview
    ) {
    }
}
