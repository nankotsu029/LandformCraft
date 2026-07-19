package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.archipelago.ArchipelagoGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ArchipelagoPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationArchipelagoValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-07 ARCHIPELAGO vertical slice compile, validation, and preview export. */
public final class FoundationArchipelagoSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final ArchipelagoPlanCompilerV2 archipelagoCompiler = new ArchipelagoPlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationArchipelagoSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Optional<TerrainIntentV2.Feature> archipelagoFeature = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.ARCHIPELAGO)
                .findFirst();
        if (archipelagoFeature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "archipelago slice requires an ARCHIPELAGO feature");
        }
        TerrainIntentV2.Feature feature = archipelagoFeature.get();
        ArchipelagoPlanV2 archipelagoPlan = codec.sealArchipelagoPlan(archipelagoCompiler.compile(
                feature, intent, bounds, codec.geometryChecksum(feature.geometry())));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        archipelagoPlan.featureId(), 1, 16, 0,
                        SurfaceFoundationPlanV2.SurfaceClassCode.ISLAND)),
                List.of());

        ArchipelagoGeneratorV2 generator = new ArchipelagoGeneratorV2(archipelagoPlan);
        ArchipelagoGeneratorV2.ArchipelagoMetrics metrics = generator.evaluate();
        FoundationArchipelagoValidationArtifactV2 validation =
                codec.sealFoundationArchipelagoValidationArtifact(
                        new FoundationArchipelagoValidationArtifactV2(
                                FoundationArchipelagoValidationArtifactV2.VERSION,
                                FoundationArchipelagoValidationArtifactV2.CONTRACT_VERSION,
                                archipelagoPlan.featureId(),
                                new FoundationArchipelagoValidationArtifactV2.Metrics(
                                        metrics.componentCountOk(),
                                        metrics.dryLandGapOk(),
                                        metrics.noOverlap(),
                                        metrics.saddlesPresent(),
                                        metrics.dominanceOk(),
                                        metrics.supportBudgetOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "mass-mask", ArchipelagoPlanV2.MASS_MASK_FIELD_ID, archipelagoPlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "saddle", ArchipelagoPlanV2.SADDLE_FIELD_ID, archipelagoPlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationArchipelagoSliceV2(archipelagoPlan, foundation, validation, preview);
    }

    public record FoundationArchipelagoSliceV2(
            ArchipelagoPlanV2 archipelago,
            SurfaceFoundationPlanV2 foundation,
            FoundationArchipelagoValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview
    ) {
    }
}
