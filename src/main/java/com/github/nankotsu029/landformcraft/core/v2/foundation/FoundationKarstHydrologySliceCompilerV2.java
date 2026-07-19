package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.karst.KarstHydrologyGraphGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.CenotePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationKarstHydrologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstHydrologyGraphPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstSpringPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SinkholePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-10-03 karst hydrology graph slice compile, validation, and preview export. */
public final class FoundationKarstHydrologySliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final SinkholePlanCompilerV2 sinkholeCompiler = new SinkholePlanCompilerV2();
    private final KarstSpringPlanCompilerV2 springCompiler = new KarstSpringPlanCompilerV2();
    private final KarstHydrologyGraphCompilerV2 graphCompiler = new KarstHydrologyGraphCompilerV2();
    private final CenotePlanCompilerV2 cenoteCompiler = new CenotePlanCompilerV2();

    public FoundationKarstHydrologySliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed,
            CaveNetworkPlanV2 hostCave
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(hostCave, "hostCave");

        TerrainIntentV2.Feature sinkholeFeature = requireFeature(intent, TerrainIntentV2.FeatureKind.SINKHOLE);
        TerrainIntentV2.Feature springFeature = requireFeature(intent, TerrainIntentV2.FeatureKind.KARST_SPRING);

        String sinkholeSurfaceChecksum = surfaceHostGeometryChecksum(intent, sinkholeFeature);
        SinkholePlanV2 sinkhole = codec.sealSinkholePlan(sinkholeCompiler.compile(
                sinkholeFeature,
                intent,
                bounds,
                codec.geometryChecksum(sinkholeFeature.geometry()),
                sinkholeSurfaceChecksum,
                hostCave));

        String springOutletChecksum = springOutletGeometryChecksum(intent, springFeature);
        KarstSpringPlanV2 spring = codec.sealKarstSpringPlan(springCompiler.compile(
                springFeature,
                intent,
                bounds,
                codec.geometryChecksum(springFeature.geometry()),
                springOutletChecksum,
                hostCave));

        Optional<TerrainIntentV2.Feature> riverFeature = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.UNDERGROUND_RIVER)
                .findFirst();
        String undergroundRiverFeatureId = "";
        String undergroundRiverPlanChecksum = "";
        if (riverFeature.isPresent()) {
            requireWithinCave(intent, riverFeature.get(), hostCave.featureId());
            undergroundRiverFeatureId = riverFeature.get().id();
            undergroundRiverPlanChecksum = codec.geometryChecksum(riverFeature.get().geometry());
        }

        KarstHydrologyGraphPlanV2 graph = codec.sealKarstHydrologyGraphPlan(graphCompiler.compile(
                intent,
                sinkhole,
                spring,
                hostCave,
                undergroundRiverFeatureId,
                undergroundRiverPlanChecksum));

        Optional<CenotePlanV2> cenote = cenoteCompiler.compileOptional(intent, sinkhole, hostCave)
                .map(codec::sealCenotePlan);

        KarstHydrologyGraphGeneratorV2 generator = new KarstHydrologyGraphGeneratorV2(
                graph, sinkhole, spring, cenote);
        KarstHydrologyGraphGeneratorV2.KarstHydrologyMetrics metrics = generator.evaluate();
        if (!allMetrics(metrics)) {
            throw new FoundationSliceException("v2.karst-hydrology-metrics",
                    "karst hydrology metrics failed: " + metrics);
        }

        FoundationKarstHydrologyValidationArtifactV2 validation =
                codec.sealFoundationKarstHydrologyValidationArtifact(
                        new FoundationKarstHydrologyValidationArtifactV2(
                                FoundationKarstHydrologyValidationArtifactV2.VERSION,
                                FoundationKarstHydrologyValidationArtifactV2.CONTRACT_VERSION,
                                graph.graphId(),
                                toValidationMetrics(metrics),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "collapse-mask", SinkholePlanV2.COLLAPSE_MASK_FIELD_ID, sinkhole.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "discharge-mask", KarstSpringPlanV2.DISCHARGE_MASK_FIELD_ID, spring.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "graph-reachability", SinkholePlanV2.REACHABILITY_FIELD_ID, graph.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationKarstHydrologySliceV2(
                sinkhole,
                spring,
                graph,
                cenote.orElse(null),
                hostCave,
                validation,
                preview,
                generator.sceneChecksum(),
                generator.exportChecksum());
    }

    private static String sinkholeSurfaceHostGeometryChecksum(TerrainIntentV2 intent) {
        TerrainIntentV2.Feature sinkhole = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.SINKHOLE)
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException("v2.foundation-slice-empty",
                        "karst hydrology slice requires a SINKHOLE feature"));
        return surfaceHostGeometryChecksum(intent, sinkhole);
    }

    private static String surfaceHostGeometryChecksum(TerrainIntentV2 intent, TerrainIntentV2.Feature sinkhole) {
        String endpoint = "feature:" + sinkhole.id();
        TerrainIntentV2.Relation host = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.SUPPORTED_BY)
                .filter(relation -> endpoint.equals(relation.from()))
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException("v2.sinkhole-missing-host",
                        "sinkhole SUPPORTED_BY relation missing"));
        String hostId = host.to().substring("feature:".length());
        TerrainIntentV2.Feature hostFeature = intent.features().stream()
                .filter(feature -> feature.id().equals(hostId))
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException("v2.sinkhole-orphan",
                        "surface host feature missing: " + hostId));
        return new LandformV2DataCodec().geometryChecksum(hostFeature.geometry());
    }

    private static String springOutletGeometryChecksum(TerrainIntentV2 intent, TerrainIntentV2.Feature spring) {
        String springEndpoint = "feature:" + spring.id();
        Optional<TerrainIntentV2.Relation> emptiesInto = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.EMPTIES_INTO)
                .filter(relation -> springEndpoint.equals(relation.from()))
                .findFirst();
        if (emptiesInto.isPresent()) {
            if (emptiesInto.get().to().startsWith("boundary:")) {
                return sinkholeSurfaceHostGeometryChecksum(intent);
            }
            String hostId = emptiesInto.get().to().substring("feature:".length());
            return hostGeometryChecksum(intent, hostId);
        }
        Optional<TerrainIntentV2.Relation> upstream = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.UPSTREAM_OF)
                .filter(relation -> springEndpoint.equals(relation.to()))
                .findFirst();
        if (upstream.isEmpty()) {
            throw new FoundationSliceException("v2.karst-spring-missing-outlet",
                    "karst spring outlet surface relation missing");
        }
        String sinkholeId = upstream.get().from().substring("feature:".length());
        TerrainIntentV2.Relation sinkHost = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.SUPPORTED_BY)
                .filter(relation -> ("feature:" + sinkholeId).equals(relation.from()))
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException("v2.karst-spring-missing-outlet",
                        "sinkhole surface host missing for spring outlet"));
        return hostGeometryChecksum(intent, sinkHost.to().substring("feature:".length()));
    }

    private static String hostGeometryChecksum(TerrainIntentV2 intent, String hostId) {
        TerrainIntentV2.Feature hostFeature = intent.features().stream()
                .filter(feature -> feature.id().equals(hostId))
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException("v2.karst-spring-orphan",
                        "outlet surface host missing: " + hostId));
        return new LandformV2DataCodec().geometryChecksum(hostFeature.geometry());
    }

    private static void requireWithinCave(TerrainIntentV2 intent, TerrainIntentV2.Feature river, String caveId) {
        String endpoint = "feature:" + river.id();
        boolean within = intent.relations().stream()
                .anyMatch(relation -> relation.strength() == TerrainIntentV2.Strength.HARD
                        && relation.kind() == TerrainIntentV2.RelationKind.WITHIN
                        && endpoint.equals(relation.from())
                        && ("feature:" + caveId).equals(relation.to()));
        if (!within) {
            throw new FoundationSliceException("v2.karst-river-orphan",
                    "UNDERGROUND_RIVER requires HARD WITHIN host cave");
        }
    }

    private static TerrainIntentV2.Feature requireFeature(
            TerrainIntentV2 intent,
            TerrainIntentV2.FeatureKind kind
    ) {
        return intent.features().stream()
                .filter(item -> item.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException("v2.foundation-slice-empty",
                        "karst hydrology slice requires a " + kind + " feature"));
    }

    private static boolean allMetrics(KarstHydrologyGraphGeneratorV2.KarstHydrologyMetrics metrics) {
        return metrics.drainageReachable() && metrics.lossSpringBalanced() && metrics.collapseRoofOk()
                && metrics.fluidOwnerOk() && metrics.graphAcyclic() && metrics.csgBudgetOk()
                && metrics.leakFree() && metrics.wholeTileOk() && metrics.budgetOk() && metrics.exportOk();
    }

    private static FoundationKarstHydrologyValidationArtifactV2.Metrics toValidationMetrics(
            KarstHydrologyGraphGeneratorV2.KarstHydrologyMetrics metrics
    ) {
        return new FoundationKarstHydrologyValidationArtifactV2.Metrics(
                metrics.drainageReachable(),
                metrics.lossSpringBalanced(),
                metrics.collapseRoofOk(),
                metrics.fluidOwnerOk(),
                metrics.graphAcyclic(),
                metrics.csgBudgetOk(),
                metrics.leakFree(),
                metrics.wholeTileOk(),
                metrics.budgetOk(),
                metrics.exportOk());
    }

    public record FoundationKarstHydrologySliceV2(
            SinkholePlanV2 sinkhole,
            KarstSpringPlanV2 spring,
            KarstHydrologyGraphPlanV2 graph,
            CenotePlanV2 cenote,
            CaveNetworkPlanV2 hostCave,
            FoundationKarstHydrologyValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String sceneExportChecksum,
            String exportChecksum
    ) {
    }
}
