package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.spring.SpringGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationSpringValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SpringPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-10-10 surface SPRING vertical slice compile, validation, and preview export. */
public final class FoundationSpringSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final SpringPlanCompilerV2 springCompiler = new SpringPlanCompilerV2();
    private final RiverPlanCompilerV2 riverCompiler = new RiverPlanCompilerV2();
    private final PlainPlanCompilerV2 plainCompiler = new PlainPlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationSpringSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        Optional<TerrainIntentV2.Feature> springFeature = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.SPRING)
                .findFirst();
        if (springFeature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "spring slice requires a SPRING feature");
        }

        SpringPlanCompilerV2.RiverBinding riverBinding =
                resolveRiverBinding(springFeature.get(), intent);
        TerrainIntentV2.Feature riverFeature = require(
                intent, riverBinding.riverFeatureId(), TerrainIntentV2.FeatureKind.RIVER);
        RiverPlanV2 riverPlan = codec.sealRiverPlan(riverCompiler.compile(
                riverFeature, intent, bounds, codec.geometryChecksum(riverFeature.geometry())));

        SpringPlanCompilerV2.HostBinding hostBinding =
                resolveHostBinding(springFeature.get(), intent);
        PlainPlanV2 plainPlan = compilePlainHost(intent, bounds, hostBinding.outletSurfaceFeatureId());

        SpringPlanV2 springPlan = codec.sealSpringPlan(springCompiler.compile(
                springFeature.get(),
                intent,
                bounds,
                codec.geometryChecksum(springFeature.get().geometry()),
                plainPlan.geometryChecksum(),
                riverPlan));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                plainPlan.featureId(), 1, 10, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                riverPlan.featureId(), 2, 15, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.RIVER),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                springPlan.featureId(), 3, 16, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.ENTRANCE)),
                List.of());

        SpringGeneratorV2 generator = new SpringGeneratorV2(springPlan, riverPlan);
        SpringGeneratorV2.SpringMetrics metrics = generator.evaluate();
        if (!metrics.sourceOwnershipOk() || !metrics.riverSourceBindOk() || !metrics.outflowContinuityOk()
                || !metrics.hydrologySpringNodeOk() || !metrics.graphReachableOk() || !metrics.budgetOk()
                || !metrics.wholeTileOk() || !metrics.exportOk() || !metrics.orphanFree()) {
            throw new FoundationSliceException("v2.spring-budget",
                    "spring metrics failed ownership/continuity/graph/budget/export checks");
        }

        FoundationSpringValidationArtifactV2 validation =
                codec.sealFoundationSpringValidationArtifact(
                        new FoundationSpringValidationArtifactV2(
                                FoundationSpringValidationArtifactV2.VERSION,
                                FoundationSpringValidationArtifactV2.CONTRACT_VERSION,
                                springPlan.featureId(),
                                new FoundationSpringValidationArtifactV2.Metrics(
                                        metrics.sourceOwnershipOk(),
                                        metrics.riverSourceBindOk(),
                                        metrics.outflowContinuityOk(),
                                        metrics.hydrologySpringNodeOk(),
                                        metrics.graphReachableOk(),
                                        metrics.budgetOk(),
                                        metrics.wholeTileOk(),
                                        metrics.exportOk(),
                                        metrics.orphanFree()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "source-mask", SpringPlanV2.SOURCE_MASK_FIELD_ID, springPlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "outflow-mask", SpringPlanV2.OUTFLOW_MASK_FIELD_ID, springPlan.canonicalChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "ownership", SpringPlanV2.OWNERSHIP_FIELD_ID, springPlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationSpringSliceV2(
                springPlan,
                riverPlan,
                plainPlan,
                foundation,
                validation,
                preview,
                generator.exportChecksum());
    }

    private PlainPlanV2 compilePlainHost(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String hostFeatureId
    ) {
        TerrainIntentV2.Feature plainFeature = require(
                intent, hostFeatureId, TerrainIntentV2.FeatureKind.PLAIN);
        return codec.sealPlainPlan(plainCompiler.compile(
                plainFeature, intent, bounds, codec.geometryChecksum(plainFeature.geometry())));
    }

    private static SpringPlanCompilerV2.HostBinding resolveHostBinding(
            TerrainIntentV2.Feature spring,
            TerrainIntentV2 intent
    ) {
        String endpoint = "feature:" + spring.id();
        List<TerrainIntentV2.Relation> hosts = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.SUPPORTED_BY)
                .filter(relation -> endpoint.equals(relation.from()))
                .toList();
        if (hosts.isEmpty()) {
            throw new FoundationSliceException("v2.spring-missing-host",
                    "spring requires HARD SUPPORTED_BY host");
        }
        TerrainIntentV2.Relation host = hosts.getFirst();
        return new SpringPlanCompilerV2.HostBinding(
                host.id(), host.to().substring("feature:".length()));
    }

    private static SpringPlanCompilerV2.RiverBinding resolveRiverBinding(
            TerrainIntentV2.Feature spring,
            TerrainIntentV2 intent
    ) {
        String endpoint = "feature:" + spring.id();
        List<TerrainIntentV2.Relation> rivers = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.EMPTIES_INTO)
                .filter(relation -> endpoint.equals(relation.from()))
                .toList();
        if (rivers.isEmpty()) {
            throw new FoundationSliceException("v2.spring-missing-river",
                    "spring requires HARD EMPTIES_INTO river");
        }
        TerrainIntentV2.Relation river = rivers.getFirst();
        return new SpringPlanCompilerV2.RiverBinding(
                river.id(), river.to().substring("feature:".length()));
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
                        "v2.spring-orphan",
                        "required feature is missing: " + featureId));
    }

    public record FoundationSpringSliceV2(
            SpringPlanV2 spring,
            RiverPlanV2 river,
            PlainPlanV2 plain,
            SurfaceFoundationPlanV2 foundation,
            FoundationSpringValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String exportChecksum
    ) {
    }
}
