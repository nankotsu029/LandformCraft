package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.ContinentalSlopeGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalSlopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationContinentalSlopeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-08 CONTINENTAL_SLOPE vertical slice compile, validation, and preview export. */
public final class FoundationContinentalSlopeSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final ContinentalSlopePlanCompilerV2 slopeCompiler = new ContinentalSlopePlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationContinentalSlopeSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Optional<TerrainIntentV2.Feature> feature = intent.features().stream()
                .filter(item -> item.kind() == TerrainIntentV2.FeatureKind.CONTINENTAL_SLOPE)
                .findFirst();
        if (feature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "continental-slope slice requires a CONTINENTAL_SLOPE feature");
        }
        ContinentalSlopePlanV2 slopePlan = codec.sealContinentalSlopePlan(slopeCompiler.compile(
                feature.get(), intent, bounds, codec.geometryChecksum(feature.get().geometry())));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        slopePlan.featureId(), 1, 13, 0,
                        SurfaceFoundationPlanV2.SurfaceClassCode.SLOPE)),
                List.of());

        ContinentalSlopeGeneratorV2 generator = new ContinentalSlopeGeneratorV2(slopePlan);
        ContinentalSlopeGeneratorV2.ContinentalSlopeMetrics metrics = generator.evaluate();
        if (!metrics.monotoneOk()) {
            throw new FoundationSliceException("v2.continental-slope-non-monotone",
                    "continental slope depth is not monotone seaward");
        }
        if (!metrics.depthFinite() || !metrics.fluidSolidConflictFree() || !metrics.budgetOk()) {
            throw new FoundationSliceException("v2.continental-slope-metrics",
                    "continental slope metrics failed depth/fluid/budget checks");
        }

        FoundationContinentalSlopeValidationArtifactV2 validation =
                codec.sealFoundationContinentalSlopeValidationArtifact(
                        new FoundationContinentalSlopeValidationArtifactV2(
                                FoundationContinentalSlopeValidationArtifactV2.VERSION,
                                FoundationContinentalSlopeValidationArtifactV2.CONTRACT_VERSION,
                                slopePlan.featureId(),
                                new FoundationContinentalSlopeValidationArtifactV2.Metrics(
                                        metrics.depthFinite(),
                                        metrics.monotoneOk(),
                                        metrics.widthOk(),
                                        metrics.fluidSolidConflictFree(),
                                        metrics.budgetOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "depth", ContinentalSlopePlanV2.DEPTH_FIELD_ID, slopePlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "slope", ContinentalSlopePlanV2.SLOPE_FIELD_ID, slopePlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationContinentalSlopeSliceV2(slopePlan, foundation, validation, preview);
    }

    public record FoundationContinentalSlopeSliceV2(
            ContinentalSlopePlanV2 slope,
            SurfaceFoundationPlanV2 foundation,
            FoundationContinentalSlopeValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview
    ) {
    }
}
