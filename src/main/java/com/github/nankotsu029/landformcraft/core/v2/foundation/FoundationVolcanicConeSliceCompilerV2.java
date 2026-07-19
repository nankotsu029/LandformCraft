package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.cone.VolcanicConeGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationVolcanicConeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.VolcanicConePlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-07 VOLCANIC_CONE vertical slice compile, validation, and preview export. */
public final class FoundationVolcanicConeSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final VolcanicConePlanCompilerV2 coneCompiler = new VolcanicConePlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationVolcanicConeSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Optional<TerrainIntentV2.Feature> coneFeature = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.VOLCANIC_CONE)
                .findFirst();
        if (coneFeature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "volcanic-cone slice requires a VOLCANIC_CONE feature");
        }
        TerrainIntentV2.Feature feature = coneFeature.get();
        VolcanicConePlanV2 conePlan = codec.sealVolcanicConePlan(coneCompiler.compile(
                feature, intent, bounds, codec.geometryChecksum(feature.geometry())));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        conePlan.featureId(), 1, 18, 0,
                        SurfaceFoundationPlanV2.SurfaceClassCode.CONE)),
                List.of());

        VolcanicConeGeneratorV2 generator = new VolcanicConeGeneratorV2(conePlan);
        VolcanicConeGeneratorV2.ConeMetrics metrics = generator.evaluate();
        if (!metrics.craterContained()) {
            throw new FoundationSliceException("v2.volcanic-cone-crater",
                    "crater is not contained inside base radius");
        }
        if (!metrics.radialDrainagePresent()) {
            throw new FoundationSliceException("v2.volcanic-cone-drainage",
                    "radial drainage is missing");
        }
        FoundationVolcanicConeValidationArtifactV2 validation =
                codec.sealFoundationVolcanicConeValidationArtifact(
                        new FoundationVolcanicConeValidationArtifactV2(
                                FoundationVolcanicConeValidationArtifactV2.VERSION,
                                FoundationVolcanicConeValidationArtifactV2.CONTRACT_VERSION,
                                conePlan.featureId(),
                                new FoundationVolcanicConeValidationArtifactV2.Metrics(
                                        metrics.coneMassPresent(),
                                        metrics.craterContained(),
                                        metrics.radialDrainagePresent(),
                                        metrics.supportBudgetOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "cone-mask", VolcanicConePlanV2.CONE_MASK_FIELD_ID, conePlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "crater-mask", VolcanicConePlanV2.CRATER_MASK_FIELD_ID, conePlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationVolcanicConeSliceV2(conePlan, foundation, validation, preview);
    }

    public record FoundationVolcanicConeSliceV2(
            VolcanicConePlanV2 cone,
            SurfaceFoundationPlanV2 foundation,
            FoundationVolcanicConeValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview
    ) {
    }
}
