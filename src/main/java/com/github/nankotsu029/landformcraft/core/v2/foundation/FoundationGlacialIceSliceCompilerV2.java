package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.glacial.GlacialIceGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationGlacialIceValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.GlacialIcePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.IceFjordPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-10-01 glacial-ice vertical slices and ICE_FJORD preset composition. */
public final class FoundationGlacialIceSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final GlacialIcePlanCompilerV2 iceCompiler = new GlacialIcePlanCompilerV2();
    private final IceFjordPlanCompilerV2 iceFjordCompiler = new IceFjordPlanCompilerV2();

    public FoundationGlacialIceSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed,
            TerrainIntentV2.FeatureKind iceKind
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");
        Objects.requireNonNull(iceKind, "iceKind");

        Optional<TerrainIntentV2.Feature> feature = intent.features().stream()
                .filter(item -> item.kind() == iceKind)
                .findFirst();
        if (feature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "glacial-ice slice requires a " + iceKind + " feature");
        }

        TerrainIntentV2.Feature iceFeature = feature.get();
        GlacialIcePlanCompilerV2.HostBinding host = iceCompiler.resolveHostBinding(iceFeature, intent);
        TerrainIntentV2.Feature bedHost = intent.features().stream()
                .filter(item -> item.id().equals(host.bedHostFeatureId()))
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException(
                        "v2.glacial-ice-missing-feature", "bed host feature missing"));

        GlacialIcePlanV2 icePlan = codec.sealGlacialIcePlan(iceCompiler.compile(
                iceFeature,
                intent,
                bounds,
                codec.geometryChecksum(iceFeature.geometry()),
                codec.geometryChecksum(bedHost.geometry())));

        GlacialIceGeneratorV2 generator = new GlacialIceGeneratorV2(icePlan);
        GlacialIceGeneratorV2.GlacialIceMetrics metrics = generator.evaluate();
        if (!metrics.coldClimateOk() || !metrics.bedContactOk() || !metrics.flowTerminusOk()
                || !metrics.sparseIceOk() || !metrics.meltwaterHandoffOk() || !metrics.leakFree()
                || !metrics.wholeTileOk() || !metrics.budgetOk() || !metrics.exportOk()) {
            throw new FoundationSliceException("v2.glacial-ice-metrics",
                    "glacial ice metrics failed"
                            + " cold=" + metrics.coldClimateOk()
                            + " bed=" + metrics.bedContactOk()
                            + " flow=" + metrics.flowTerminusOk()
                            + " sparse=" + metrics.sparseIceOk()
                            + " melt=" + metrics.meltwaterHandoffOk()
                            + " leak=" + metrics.leakFree()
                            + " wholeTile=" + metrics.wholeTileOk()
                            + " budget=" + metrics.budgetOk()
                            + " export=" + metrics.exportOk());
        }

        FoundationGlacialIceValidationArtifactV2 validation =
                codec.sealFoundationGlacialIceValidationArtifact(
                        new FoundationGlacialIceValidationArtifactV2(
                                FoundationGlacialIceValidationArtifactV2.VERSION,
                                FoundationGlacialIceValidationArtifactV2.CONTRACT_VERSION,
                                icePlan.featureId(),
                                new FoundationGlacialIceValidationArtifactV2.Metrics(
                                        metrics.coldClimateOk(),
                                        metrics.bedContactOk(),
                                        metrics.flowTerminusOk(),
                                        metrics.sparseIceOk(),
                                        metrics.meltwaterHandoffOk(),
                                        metrics.leakFree(),
                                        metrics.wholeTileOk(),
                                        metrics.budgetOk(),
                                        metrics.exportOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "surface-ownership", GlacialIcePlanV2.SURFACE_OWNERSHIP_FIELD_ID,
                icePlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "thickness", GlacialIcePlanV2.THICKNESS_FIELD_ID, icePlan.canonicalChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "flow-direction", GlacialIcePlanV2.FLOW_DIRECTION_FIELD_ID,
                icePlan.canonicalChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "bed-contact", GlacialIcePlanV2.BED_CONTACT_FIELD_ID, icePlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationGlacialIceSliceV2(
                icePlan, validation, preview, generator.sceneChecksum(), generator.exportChecksum());
    }

    public IceFjordCompositionV2 compileIceFjord(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        FoundationGlacialIceSliceV2 glacier = compile(
                intent, bounds, globalSeed, TerrainIntentV2.FeatureKind.VALLEY_GLACIER);
        IceFjordPlanV2 preset = codec.sealIceFjordPlan(
                iceFjordCompiler.compile(intent, glacier.ice()));
        return new IceFjordCompositionV2(glacier, preset);
    }

    public record FoundationGlacialIceSliceV2(
            GlacialIcePlanV2 ice,
            FoundationGlacialIceValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String sceneExportChecksum,
            String exportChecksum
    ) {
    }

    public record IceFjordCompositionV2(
            FoundationGlacialIceSliceV2 valleyGlacier,
            IceFjordPlanV2 iceFjord
    ) {
    }
}
