package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.lavatube.LavaTubeGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationLavaTubeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.LavaTubePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.VolcanicConePlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-10-07 LAVA_TUBE vertical slice compile, validation, and preview export. */
public final class FoundationLavaTubeSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final LavaTubePlanCompilerV2 tubeCompiler = new LavaTubePlanCompilerV2();
    private final VolcanicConePlanCompilerV2 coneCompiler = new VolcanicConePlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationLavaTubeSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        Optional<TerrainIntentV2.Feature> tubeFeature = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.LAVA_TUBE)
                .findFirst();
        if (tubeFeature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "lava-tube slice requires a LAVA_TUBE feature");
        }

        LavaTubePlanCompilerV2.HostBinding binding =
                tubeCompiler.resolveHostBinding(tubeFeature.get(), intent);

        TerrainIntentV2.Feature coneFeature = require(
                intent, binding.volcanicConeFeatureId(), TerrainIntentV2.FeatureKind.VOLCANIC_CONE);
        VolcanicConePlanV2 conePlan = codec.sealVolcanicConePlan(coneCompiler.compile(
                coneFeature, intent, bounds, codec.geometryChecksum(coneFeature.geometry())));

        LavaTubePlanV2 tubePlan = codec.sealLavaTubePlan(tubeCompiler.compile(
                tubeFeature.get(),
                intent,
                bounds,
                codec.geometryChecksum(tubeFeature.get().geometry()),
                conePlan));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                conePlan.featureId(), 1, 18, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.CONE),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                tubePlan.featureId(), 2, 20, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.ENTRANCE)),
                List.of());

        LavaTubeGeneratorV2 generator = new LavaTubeGeneratorV2(tubePlan, conePlan);
        LavaTubeGeneratorV2.LavaTubeMetrics metrics = generator.evaluate();
        if (!metrics.hostRelationOk() || !metrics.tubeContinuityOk() || !metrics.roofSupportOk()
                || !metrics.fluidConflictFree() || !metrics.aabbBudgetOk() || !metrics.wholeTileOk()
                || !metrics.exportOk() || !metrics.orphanFree()) {
            throw new FoundationSliceException("v2.lava-tube-budget",
                    "lava tube metrics failed host/continuity/roof/fluid/budget/export checks");
        }

        FoundationLavaTubeValidationArtifactV2 validation =
                codec.sealFoundationLavaTubeValidationArtifact(
                        new FoundationLavaTubeValidationArtifactV2(
                                FoundationLavaTubeValidationArtifactV2.VERSION,
                                FoundationLavaTubeValidationArtifactV2.CONTRACT_VERSION,
                                tubePlan.featureId(),
                                new FoundationLavaTubeValidationArtifactV2.Metrics(
                                        metrics.hostRelationOk(),
                                        metrics.tubeContinuityOk(),
                                        metrics.roofSupportOk(),
                                        metrics.fluidConflictFree(),
                                        metrics.aabbBudgetOk(),
                                        metrics.wholeTileOk(),
                                        metrics.exportOk(),
                                        metrics.orphanFree()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "tube-mask", LavaTubePlanV2.TUBE_MASK_FIELD_ID, tubePlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "roof-clearance", LavaTubePlanV2.ROOF_CLEARANCE_FIELD_ID, tubePlan.canonicalChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "ownership", LavaTubePlanV2.OWNERSHIP_FIELD_ID, tubePlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationLavaTubeSliceV2(
                tubePlan,
                conePlan,
                foundation,
                validation,
                preview,
                generator.exportChecksum());
    }

    private static TerrainIntentV2.Feature require(
            TerrainIntentV2 intent,
            String featureId,
            TerrainIntentV2.FeatureKind kind
    ) {
        return intent.features().stream()
                .filter(feature -> feature.id().equals(featureId) && feature.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException(
                        "v2.lava-tube-missing-cone",
                        "required host feature is missing: " + featureId));
    }

    public record FoundationLavaTubeSliceV2(
            LavaTubePlanV2 tube,
            VolcanicConePlanV2 cone,
            SurfaceFoundationPlanV2 foundation,
            FoundationLavaTubeValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String exportChecksum
    ) {
    }
}
