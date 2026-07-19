package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.ContinentalShelfGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalShelfPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationContinentalShelfValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-08 CONTINENTAL_SHELF vertical slice compile, validation, and preview export. */
public final class FoundationContinentalShelfSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final ContinentalShelfPlanCompilerV2 shelfCompiler = new ContinentalShelfPlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationContinentalShelfSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Optional<TerrainIntentV2.Feature> feature = intent.features().stream()
                .filter(item -> item.kind() == TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF)
                .findFirst();
        if (feature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "continental-shelf slice requires a CONTINENTAL_SHELF feature");
        }
        ContinentalShelfPlanV2 shelfPlan = codec.sealContinentalShelfPlan(shelfCompiler.compile(
                feature.get(), intent, bounds, codec.geometryChecksum(feature.get().geometry())));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        shelfPlan.featureId(), 1, 14, 0,
                        SurfaceFoundationPlanV2.SurfaceClassCode.SHELF)),
                List.of());

        ContinentalShelfGeneratorV2 generator = new ContinentalShelfGeneratorV2(shelfPlan);
        ContinentalShelfGeneratorV2.ContinentalShelfMetrics metrics = generator.evaluate();
        if (!metrics.depthFinite() || !metrics.fluidSolidConflictFree() || !metrics.budgetOk()) {
            throw new FoundationSliceException("v2.continental-shelf-metrics",
                    "continental shelf metrics failed depth/fluid/budget checks");
        }

        FoundationContinentalShelfValidationArtifactV2 validation =
                codec.sealFoundationContinentalShelfValidationArtifact(
                        new FoundationContinentalShelfValidationArtifactV2(
                                FoundationContinentalShelfValidationArtifactV2.VERSION,
                                FoundationContinentalShelfValidationArtifactV2.CONTRACT_VERSION,
                                shelfPlan.featureId(),
                                new FoundationContinentalShelfValidationArtifactV2.Metrics(
                                        metrics.depthFinite(),
                                        metrics.widthOk(),
                                        metrics.fluidSolidConflictFree(),
                                        metrics.budgetOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "depth", ContinentalShelfPlanV2.DEPTH_FIELD_ID, shelfPlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "coast-distance", ContinentalShelfPlanV2.COAST_DISTANCE_FIELD_ID,
                shelfPlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationContinentalShelfSliceV2(shelfPlan, foundation, validation, preview);
    }

    public record FoundationContinentalShelfSliceV2(
            ContinentalShelfPlanV2 shelf,
            SurfaceFoundationPlanV2 foundation,
            FoundationContinentalShelfValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview
    ) {
    }
}
