package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.cave.UndergroundRiverGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.CaveEntrancePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationUndergroundRiverValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.UndergroundRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.UndergroundLakePlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-11 UNDERGROUND_RIVER vertical slice compile, validation, and preview export. */
public final class FoundationUndergroundRiverSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final UndergroundRiverPlanCompilerV2 riverCompiler = new UndergroundRiverPlanCompilerV2();

    public FoundationUndergroundRiverSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed,
            CaveNetworkPlanV2 hostCave,
            UndergroundLakePlanV2 hostLake
    ) {
        return compile(intent, bounds, globalSeed, hostCave, hostLake, null);
    }

    public FoundationUndergroundRiverSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed,
            CaveNetworkPlanV2 hostCave,
            UndergroundLakePlanV2 hostLake,
            CaveEntrancePlanV2 optionalEntrance
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(hostCave, "hostCave");
        Objects.requireNonNull(hostLake, "hostLake");
        // globalSeed reserved for future foundation ownership merge; unused in this slice.
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        Optional<TerrainIntentV2.Feature> feature = intent.features().stream()
                .filter(item -> item.kind() == TerrainIntentV2.FeatureKind.UNDERGROUND_RIVER)
                .findFirst();
        if (feature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "underground-river slice requires an UNDERGROUND_RIVER feature");
        }

        UndergroundRiverPlanCompilerV2.HostBinding binding =
                riverCompiler.resolveHostBinding(feature.get(), intent);
        if (!binding.caveNetworkFeatureId().equals(hostCave.featureId())) {
            throw new FoundationSliceException("v2.underground-river-missing-relation",
                    "WITHIN target does not match frozen host cave featureId");
        }

        UndergroundRiverPlanV2 riverPlan = codec.sealUndergroundRiverPlan(riverCompiler.compile(
                feature.get(),
                intent,
                bounds,
                codec.geometryChecksum(feature.get().geometry()),
                hostCave,
                hostLake,
                optionalEntrance));

        UndergroundRiverGeneratorV2 generator = new UndergroundRiverGeneratorV2(
                riverPlan, hostCave, hostLake);
        UndergroundRiverGeneratorV2.UndergroundRiverMetrics metrics = generator.evaluate();
        if (!metrics.reachable() || !metrics.downGradientOk() || !metrics.singleFluidOwner()
                || !metrics.leakFree() || !metrics.airPocketOk() || !metrics.fluidOrderOk()
                || !metrics.wholeTileOk() || !metrics.budgetOk() || !metrics.sceneExportOk()) {
            throw new FoundationSliceException("v2.underground-river-budget",
                    "underground river metrics failed reachability/gradient/fluid/leak/budget/export checks");
        }

        FoundationUndergroundRiverValidationArtifactV2 validation =
                codec.sealFoundationUndergroundRiverValidationArtifact(
                        new FoundationUndergroundRiverValidationArtifactV2(
                                FoundationUndergroundRiverValidationArtifactV2.VERSION,
                                FoundationUndergroundRiverValidationArtifactV2.CONTRACT_VERSION,
                                riverPlan.featureId(),
                                new FoundationUndergroundRiverValidationArtifactV2.Metrics(
                                        metrics.reachable(),
                                        metrics.downGradientOk(),
                                        metrics.singleFluidOwner(),
                                        metrics.leakFree(),
                                        metrics.airPocketOk(),
                                        metrics.fluidOrderOk(),
                                        metrics.wholeTileOk(),
                                        metrics.budgetOk(),
                                        metrics.sceneExportOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "channel-mask", UndergroundRiverPlanV2.CHANNEL_MASK_FIELD_ID,
                riverPlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "fluid-mask", UndergroundRiverPlanV2.FLUID_MASK_FIELD_ID,
                riverPlan.canonicalChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "ownership", UndergroundRiverPlanV2.OWNERSHIP_FIELD_ID,
                riverPlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationUndergroundRiverSliceV2(
                riverPlan,
                hostCave,
                hostLake,
                validation,
                preview,
                generator.sceneChecksum(),
                generator.exportChecksum());
    }

    public record FoundationUndergroundRiverSliceV2(
            UndergroundRiverPlanV2 river,
            CaveNetworkPlanV2 hostCave,
            UndergroundLakePlanV2 hostLake,
            FoundationUndergroundRiverValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String sceneExportChecksum,
            String exportChecksum
    ) {
    }
}
