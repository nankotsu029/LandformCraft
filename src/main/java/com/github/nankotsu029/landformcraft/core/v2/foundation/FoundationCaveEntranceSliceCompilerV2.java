package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.cave.CaveEntranceGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.CaveEntrancePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationCaveEntranceValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MountainRangePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ValleyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-10 CAVE_ENTRANCE vertical slice compile, validation, and preview export. */
public final class FoundationCaveEntranceSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final CaveEntrancePlanCompilerV2 entranceCompiler = new CaveEntrancePlanCompilerV2();
    private final MountainRangePlanCompilerV2 mountainCompiler = new MountainRangePlanCompilerV2();
    private final ValleyPlanCompilerV2 valleyCompiler = new ValleyPlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationCaveEntranceSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed,
            CaveNetworkPlanV2 hostCave
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(hostCave, "hostCave");

        Optional<TerrainIntentV2.Feature> feature = intent.features().stream()
                .filter(item -> item.kind() == TerrainIntentV2.FeatureKind.CAVE_ENTRANCE)
                .findFirst();
        if (feature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "cave-entrance slice requires a CAVE_ENTRANCE feature");
        }

        CaveEntrancePlanCompilerV2.HostBinding binding =
                entranceCompiler.resolveHostBinding(feature.get(), intent);
        if (!binding.caveNetworkFeatureId().equals(hostCave.featureId())) {
            throw new FoundationSliceException("v2.cave-entrance-orphan",
                    "ENTRANCE_OF target does not match frozen host cave featureId");
        }

        MountainRangePlanV2 mountainPlan = null;
        ValleyPlanV2 valleyPlan = null;
        String surfaceGeometryChecksum;
        SurfaceFoundationPlanV2.SurfaceClassCode hostClass;
        if (binding.surfaceHostKind() == CaveEntrancePlanV2.SurfaceHostKind.MOUNTAIN_RANGE) {
            TerrainIntentV2.Feature mountainFeature = require(
                    intent, binding.surfaceHostFeatureId(), TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE);
            mountainPlan = codec.sealMountainRangePlan(mountainCompiler.compile(
                    mountainFeature, intent, bounds, codec.geometryChecksum(mountainFeature.geometry())));
            surfaceGeometryChecksum = mountainPlan.geometryChecksum();
            hostClass = SurfaceFoundationPlanV2.SurfaceClassCode.MOUNTAIN;
        } else {
            TerrainIntentV2.Feature valleyFeature = require(
                    intent, binding.surfaceHostFeatureId(), TerrainIntentV2.FeatureKind.VALLEY);
            valleyPlan = codec.sealValleyPlan(valleyCompiler.compile(
                    valleyFeature, intent, bounds, codec.geometryChecksum(valleyFeature.geometry())));
            surfaceGeometryChecksum = valleyPlan.geometryChecksum();
            hostClass = SurfaceFoundationPlanV2.SurfaceClassCode.VALLEY;
        }

        CaveEntrancePlanV2 entrancePlan = codec.sealCaveEntrancePlan(entranceCompiler.compile(
                feature.get(),
                intent,
                bounds,
                codec.geometryChecksum(feature.get().geometry()),
                mountainPlan,
                valleyPlan,
                hostCave));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                binding.surfaceHostFeatureId(), 1, 30, 0, hostClass),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                entrancePlan.featureId(), 2, 35, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.ENTRANCE)),
                List.of());

        CaveEntranceGeneratorV2 generator = new CaveEntranceGeneratorV2(
                entrancePlan, hostCave, surfaceGeometryChecksum);
        CaveEntranceGeneratorV2.CaveEntranceMetrics metrics = generator.evaluate();
        if (!metrics.singleSurfaceHost() || !metrics.singleCaveTarget() || !metrics.reachable()
                || !metrics.roofOk() || !metrics.floodLeakFree() || !metrics.ownerConflictFree()
                || !metrics.aabbBudgetOk() || !metrics.seamlessExportOk()) {
            throw new FoundationSliceException("v2.cave-entrance-budget",
                    "cave entrance metrics failed reachability/roof/flood/owner/budget/export checks");
        }

        FoundationCaveEntranceValidationArtifactV2 validation =
                codec.sealFoundationCaveEntranceValidationArtifact(
                        new FoundationCaveEntranceValidationArtifactV2(
                                FoundationCaveEntranceValidationArtifactV2.VERSION,
                                FoundationCaveEntranceValidationArtifactV2.CONTRACT_VERSION,
                                entrancePlan.featureId(),
                                new FoundationCaveEntranceValidationArtifactV2.Metrics(
                                        metrics.singleSurfaceHost(),
                                        metrics.singleCaveTarget(),
                                        metrics.reachable(),
                                        metrics.roofOk(),
                                        metrics.floodLeakFree(),
                                        metrics.ownerConflictFree(),
                                        metrics.aabbBudgetOk(),
                                        metrics.seamlessExportOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "opening-mask", CaveEntrancePlanV2.OPENING_MASK_FIELD_ID, entrancePlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "approach-mask", CaveEntrancePlanV2.APPROACH_MASK_FIELD_ID,
                entrancePlan.canonicalChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "ownership", CaveEntrancePlanV2.OWNERSHIP_FIELD_ID, entrancePlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationCaveEntranceSliceV2(
                entrancePlan,
                mountainPlan,
                valleyPlan,
                hostCave,
                foundation,
                validation,
                preview,
                generator.seamlessQueryChecksum(),
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
                        "v2.cave-entrance-missing-relation",
                        "required host feature is missing: " + featureId));
    }

    public record FoundationCaveEntranceSliceV2(
            CaveEntrancePlanV2 entrance,
            MountainRangePlanV2 mountain,
            ValleyPlanV2 valley,
            CaveNetworkPlanV2 hostCave,
            SurfaceFoundationPlanV2 foundation,
            FoundationCaveEntranceValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String seamlessQueryChecksum,
            String exportChecksum
    ) {
    }
}
