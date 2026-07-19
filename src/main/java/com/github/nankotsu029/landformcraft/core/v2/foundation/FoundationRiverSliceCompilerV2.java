package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.river.RiverGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationRiverValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-04 general river vertical slice compile, validation, and preview export. */
public final class FoundationRiverSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final RiverPlanCompilerV2 riverCompiler = new RiverPlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationRiverSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Optional<TerrainIntentV2.Feature> riverFeature =
                intent.features().stream()
                        .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.RIVER)
                        .findFirst();
        if (riverFeature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "river slice requires a RIVER feature");
        }
        TerrainIntentV2.Feature feature = riverFeature.get();
        RiverPlanV2 riverPlan = codec.sealRiverPlan(riverCompiler.compile(
                feature, intent, bounds, codec.geometryChecksum(feature.geometry())));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        riverPlan.featureId(), 1, 15, 0,
                        SurfaceFoundationPlanV2.SurfaceClassCode.RIVER)),
                List.of());

        RiverGeneratorV2 generator = new RiverGeneratorV2(riverPlan);
        RiverGeneratorV2.RiverMetrics metrics = generator.evaluate();
        FoundationRiverValidationArtifactV2 validation = codec.sealFoundationRiverValidationArtifact(
                new FoundationRiverValidationArtifactV2(
                        FoundationRiverValidationArtifactV2.VERSION,
                        FoundationRiverValidationArtifactV2.CONTRACT_VERSION,
                        riverPlan.featureId(),
                        new FoundationRiverValidationArtifactV2.Metrics(
                                metrics.sourceMouthReachable(),
                                metrics.bedMonotonic(),
                                metrics.confluenceFlowOk(),
                                metrics.graphBudgetOk(),
                                metrics.cycleFree()),
                        List.of(),
                        "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "channel-mask", RiverPlanV2.CHANNEL_MASK_FIELD_ID, riverPlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "bed-elevation", RiverPlanV2.BED_ELEVATION_FIELD_ID, riverPlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationRiverSliceV2(riverPlan, foundation, validation, preview);
    }

    public record FoundationRiverSliceV2(
            RiverPlanV2 river,
            SurfaceFoundationPlanV2 foundation,
            FoundationRiverValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview
    ) {
    }
}
