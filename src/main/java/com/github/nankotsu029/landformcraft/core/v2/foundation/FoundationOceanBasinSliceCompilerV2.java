package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.OceanBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationOceanBasinValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-08 OCEAN_BASIN vertical slice compile, validation, and preview export. */
public final class FoundationOceanBasinSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final OceanBasinPlanCompilerV2 basinCompiler = new OceanBasinPlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationOceanBasinSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Optional<TerrainIntentV2.Feature> feature = intent.features().stream()
                .filter(item -> item.kind() == TerrainIntentV2.FeatureKind.OCEAN_BASIN)
                .findFirst();
        if (feature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "ocean-basin slice requires an OCEAN_BASIN feature");
        }
        OceanBasinPlanV2 basinPlan = codec.sealOceanBasinPlan(basinCompiler.compile(
                feature.get(), intent, bounds, codec.geometryChecksum(feature.get().geometry())));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        basinPlan.featureId(), 1, 12, 0,
                        SurfaceFoundationPlanV2.SurfaceClassCode.OCEAN)),
                List.of());

        OceanBasinGeneratorV2 generator = new OceanBasinGeneratorV2(basinPlan);
        OceanBasinGeneratorV2.OceanBasinMetrics metrics = generator.evaluate();
        if (!metrics.depthFinite() || !metrics.fluidSolidConflictFree() || !metrics.budgetOk()) {
            throw new FoundationSliceException("v2.ocean-basin-metrics",
                    "ocean basin metrics failed depth/fluid/budget checks");
        }

        FoundationOceanBasinValidationArtifactV2 validation =
                codec.sealFoundationOceanBasinValidationArtifact(
                        new FoundationOceanBasinValidationArtifactV2(
                                FoundationOceanBasinValidationArtifactV2.VERSION,
                                FoundationOceanBasinValidationArtifactV2.CONTRACT_VERSION,
                                basinPlan.featureId(),
                                new FoundationOceanBasinValidationArtifactV2.Metrics(
                                        metrics.depthFinite(),
                                        metrics.widthOk(),
                                        metrics.fluidSolidConflictFree(),
                                        metrics.budgetOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "depth", OceanBasinPlanV2.DEPTH_FIELD_ID, basinPlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "ownership", OceanBasinPlanV2.OWNERSHIP_FIELD_ID, basinPlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationOceanBasinSliceV2(basinPlan, foundation, validation, preview);
    }

    public record FoundationOceanBasinSliceV2(
            OceanBasinPlanV2 basin,
            SurfaceFoundationPlanV2 foundation,
            FoundationOceanBasinValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview
    ) {
    }
}
