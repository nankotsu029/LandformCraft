package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.SubmarineCanyonGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalShelfPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalSlopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationSubmarineCanyonValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SubmarineCanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-09 SUBMARINE_CANYON vertical slice compile, validation, and preview export. */
public final class FoundationSubmarineCanyonSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final SubmarineCanyonPlanCompilerV2 canyonCompiler = new SubmarineCanyonPlanCompilerV2();
    private final OceanBasinPlanCompilerV2 basinCompiler = new OceanBasinPlanCompilerV2();
    private final ContinentalShelfPlanCompilerV2 shelfCompiler = new ContinentalShelfPlanCompilerV2();
    private final ContinentalSlopePlanCompilerV2 slopeCompiler = new ContinentalSlopePlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationSubmarineCanyonSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Optional<TerrainIntentV2.Feature> feature = intent.features().stream()
                .filter(item -> item.kind() == TerrainIntentV2.FeatureKind.SUBMARINE_CANYON)
                .findFirst();
        if (feature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "submarine-canyon slice requires a SUBMARINE_CANYON feature");
        }
        SubmarineCanyonPlanCompilerV2.HostBinding binding =
                canyonCompiler.resolveHostBinding(feature.get(), intent);

        TerrainIntentV2.Feature shelfFeature = require(intent, binding.shelfFeatureId(),
                TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF);
        TerrainIntentV2.Feature slopeFeature = require(intent, binding.slopeFeatureId(),
                TerrainIntentV2.FeatureKind.CONTINENTAL_SLOPE);
        TerrainIntentV2.Feature basinFeature = require(intent, binding.basinFeatureId(),
                TerrainIntentV2.FeatureKind.OCEAN_BASIN);

        ContinentalShelfPlanV2 shelfPlan = codec.sealContinentalShelfPlan(shelfCompiler.compile(
                shelfFeature, intent, bounds, codec.geometryChecksum(shelfFeature.geometry())));
        ContinentalSlopePlanV2 slopePlan = codec.sealContinentalSlopePlan(slopeCompiler.compile(
                slopeFeature, intent, bounds, codec.geometryChecksum(slopeFeature.geometry())));
        OceanBasinPlanV2 basinPlan = codec.sealOceanBasinPlan(basinCompiler.compile(
                basinFeature, intent, bounds, codec.geometryChecksum(basinFeature.geometry())));

        SubmarineCanyonPlanV2 canyonPlan = codec.sealSubmarineCanyonPlan(canyonCompiler.compile(
                feature.get(),
                intent,
                bounds,
                codec.geometryChecksum(feature.get().geometry()),
                shelfPlan,
                slopePlan,
                basinPlan,
                shelfPlan.geometryChecksum(),
                slopePlan.geometryChecksum(),
                basinPlan.geometryChecksum()));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        canyonPlan.featureId(), 1, 15, 0,
                        SurfaceFoundationPlanV2.SurfaceClassCode.SUBMARINE_CANYON)),
                List.of());

        SubmarineCanyonGeneratorV2 generator = new SubmarineCanyonGeneratorV2(canyonPlan);
        SubmarineCanyonGeneratorV2.SubmarineCanyonMetrics metrics = generator.evaluate();
        if (!metrics.wholeTileOk()) {
            throw new FoundationSliceException("v2.submarine-canyon-whole-tile-mismatch",
                    "submarine canyon whole and tiled field checksums diverge");
        }
        if (!metrics.headContained() || !metrics.outletContained() || !metrics.slopeCrossingOk()
                || !metrics.downGradientOk() || !metrics.floorDepthOk()
                || !metrics.fluidSolidConflictFree() || !metrics.budgetOk()) {
            throw new FoundationSliceException("v2.submarine-canyon-metrics",
                    "submarine canyon metrics failed containment/gradient/floor/fluid/budget checks");
        }

        FoundationSubmarineCanyonValidationArtifactV2 validation =
                codec.sealFoundationSubmarineCanyonValidationArtifact(
                        new FoundationSubmarineCanyonValidationArtifactV2(
                                FoundationSubmarineCanyonValidationArtifactV2.VERSION,
                                FoundationSubmarineCanyonValidationArtifactV2.CONTRACT_VERSION,
                                canyonPlan.featureId(),
                                new FoundationSubmarineCanyonValidationArtifactV2.Metrics(
                                        metrics.headContained(),
                                        metrics.outletContained(),
                                        metrics.slopeCrossingOk(),
                                        metrics.downGradientOk(),
                                        metrics.floorDepthOk(),
                                        metrics.fluidSolidConflictFree(),
                                        metrics.wholeTileOk(),
                                        metrics.budgetOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "mask", SubmarineCanyonPlanV2.MASK_FIELD_ID, canyonPlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "floor-depth", SubmarineCanyonPlanV2.FLOOR_DEPTH_FIELD_ID, canyonPlan.canonicalChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "ownership", SubmarineCanyonPlanV2.OWNERSHIP_FIELD_ID, canyonPlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationSubmarineCanyonSliceV2(
                canyonPlan, shelfPlan, slopePlan, basinPlan, foundation, validation, preview,
                generator.underwaterColumnExportChecksum());
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
                        "v2.submarine-canyon-missing-relation",
                        "required host feature is missing: " + featureId));
    }

    public record FoundationSubmarineCanyonSliceV2(
            SubmarineCanyonPlanV2 canyon,
            ContinentalShelfPlanV2 shelf,
            ContinentalSlopePlanV2 slope,
            OceanBasinPlanV2 basin,
            SurfaceFoundationPlanV2 foundation,
            FoundationSubmarineCanyonValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String underwaterColumnExportChecksum
    ) {
    }
}
