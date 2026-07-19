package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.oxbow.OxbowLakeGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationOxbowLakeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OxbowLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-10-11 OXBOW_LAKE vertical slice compile, validation, and preview export. */
public final class FoundationOxbowLakeSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final OxbowLakePlanCompilerV2 oxbowCompiler = new OxbowLakePlanCompilerV2();
    private final RiverPlanCompilerV2 riverCompiler = new RiverPlanCompilerV2();
    private final PlainPlanCompilerV2 plainCompiler = new PlainPlanCompilerV2();
    private final FloodplainPlanCompilerV2 floodplainCompiler = new FloodplainPlanCompilerV2();
    private final MarshPlanCompilerV2 marshCompiler = new MarshPlanCompilerV2();
    private final ValleyPlanCompilerV2 valleyCompiler = new ValleyPlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationOxbowLakeSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        Optional<TerrainIntentV2.Feature> oxbowFeature = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.OXBOW_LAKE)
                .findFirst();
        if (oxbowFeature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "oxbow slice requires an OXBOW_LAKE feature");
        }

        OxbowLakePlanCompilerV2.RiverRelationBinding riverBinding =
                resolveRiverBinding(oxbowFeature.get(), intent);
        TerrainIntentV2.Feature riverFeature = requireFeature(intent, riverBinding.riverFeatureId());
        if (riverFeature.kind() != TerrainIntentV2.FeatureKind.RIVER) {
            throw new FoundationSliceException("v2.oxbow-missing-river",
                    "positive slice fixture requires general RIVER parent");
        }
        RiverPlanV2 riverPlan = codec.sealRiverPlan(riverCompiler.compile(
                riverFeature, intent, bounds, codec.geometryChecksum(riverFeature.geometry())));

        OxbowLakePlanCompilerV2.HostBinding hostBinding =
                resolveHostBinding(oxbowFeature.get(), intent);
        String hostGeometryChecksum = compileHostGeometryChecksum(intent, bounds, hostBinding.hostSurfaceFeatureId());

        OxbowLakePlanCompilerV2.ParentRiverBinding parentBinding = new OxbowLakePlanCompilerV2.ParentRiverBinding(
                riverPlan.featureId(),
                OxbowLakePlanV2.ParentRiverKind.RIVER,
                riverPlan.canonicalChecksum(),
                riverPlan);
        OxbowLakePlanV2 oxbowPlan = codec.sealOxbowLakePlan(oxbowCompiler.compile(
                oxbowFeature.get(),
                intent,
                bounds,
                codec.geometryChecksum(oxbowFeature.get().geometry()),
                hostGeometryChecksum,
                parentBinding));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed,
                List.of(
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                hostBinding.hostSurfaceFeatureId(), 1, 10, 0,
                                hostSurfaceClass(intent, hostBinding.hostSurfaceFeatureId())),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                riverPlan.featureId(), 2, 15, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.RIVER),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                oxbowPlan.featureId(), 3, 16, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.WETLAND)),
                List.of());

        OxbowLakeGeneratorV2 generator = new OxbowLakeGeneratorV2(oxbowPlan);
        OxbowLakeGeneratorV2.OxbowMetrics metrics = generator.evaluate(riverPlan);
        if (!metrics.cutoffOwnershipOk() || !metrics.parentRiverBindOk() || !metrics.stagnantLevelOk()
                || !metrics.rimClosedOk() || !metrics.wetlandHandoffOk() || !metrics.budgetOk()
                || !metrics.wholeTileOk() || !metrics.exportOk() || !metrics.orphanFree()) {
            throw new FoundationSliceException("v2.oxbow-budget",
                    "oxbow metrics failed ownership/cutoff/stagnant/budget/export checks");
        }

        FoundationOxbowLakeValidationArtifactV2 validation =
                codec.sealFoundationOxbowLakeValidationArtifact(
                        new FoundationOxbowLakeValidationArtifactV2(
                                FoundationOxbowLakeValidationArtifactV2.VERSION,
                                FoundationOxbowLakeValidationArtifactV2.CONTRACT_VERSION,
                                oxbowPlan.featureId(),
                                new FoundationOxbowLakeValidationArtifactV2.Metrics(
                                        metrics.cutoffOwnershipOk(),
                                        metrics.parentRiverBindOk(),
                                        metrics.stagnantLevelOk(),
                                        metrics.rimClosedOk(),
                                        metrics.wetlandHandoffOk(),
                                        metrics.budgetOk(),
                                        metrics.wholeTileOk(),
                                        metrics.exportOk(),
                                        metrics.orphanFree()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "basin-mask", OxbowLakePlanV2.BASIN_MASK_FIELD_ID, oxbowPlan.geometryChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "rim-mask", OxbowLakePlanV2.RIM_MASK_FIELD_ID, oxbowPlan.canonicalChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "wetland-handoff", OxbowLakePlanV2.WETLAND_HANDOFF_FIELD_ID, oxbowPlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationOxbowLakeSliceV2(
                oxbowPlan,
                riverPlan,
                foundation,
                validation,
                preview,
                generator.exportChecksum(riverPlan));
    }

    private String compileHostGeometryChecksum(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String hostFeatureId
    ) {
        TerrainIntentV2.Feature host = requireFeature(intent, hostFeatureId);
        String geometryChecksum = codec.geometryChecksum(host.geometry());
        return switch (host.kind()) {
            case PLAIN -> codec.sealPlainPlan(plainCompiler.compile(
                    host, intent, bounds, geometryChecksum)).geometryChecksum();
            case FLOODPLAIN -> codec.sealFloodplainPlan(floodplainCompiler.compile(
                    host, intent, bounds, geometryChecksum)).geometryChecksum();
            case MARSH -> codec.sealMarshPlan(marshCompiler.compile(
                    host, intent, bounds, geometryChecksum)).geometryChecksum();
            case VALLEY -> codec.sealValleyPlan(valleyCompiler.compile(
                    host, intent, bounds, geometryChecksum)).geometryChecksum();
            default -> throw new FoundationSliceException("v2.oxbow-missing-host",
                    "unsupported host surface kind: " + host.kind());
        };
    }

    private static SurfaceFoundationPlanV2.SurfaceClassCode hostSurfaceClass(
            TerrainIntentV2 intent,
            String hostFeatureId
    ) {
        TerrainIntentV2.FeatureKind kind = intent.features().stream()
                .filter(feature -> feature.id().equals(hostFeatureId))
                .map(TerrainIntentV2.Feature::kind)
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException("v2.oxbow-missing-host",
                        "host feature missing: " + hostFeatureId));
        return switch (kind) {
            case PLAIN, FLOODPLAIN, MARSH -> SurfaceFoundationPlanV2.SurfaceClassCode.WETLAND;
            case VALLEY -> SurfaceFoundationPlanV2.SurfaceClassCode.VALLEY;
            default -> SurfaceFoundationPlanV2.SurfaceClassCode.UNSPECIFIED;
        };
    }

    private static OxbowLakePlanCompilerV2.HostBinding resolveHostBinding(
            TerrainIntentV2.Feature oxbow,
            TerrainIntentV2 intent
    ) {
        String endpoint = "feature:" + oxbow.id();
        List<TerrainIntentV2.Relation> hosts = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.SUPPORTED_BY)
                .filter(relation -> endpoint.equals(relation.from()))
                .toList();
        if (hosts.isEmpty()) {
            throw new FoundationSliceException("v2.oxbow-missing-host",
                    "oxbow requires HARD SUPPORTED_BY host");
        }
        TerrainIntentV2.Relation host = hosts.getFirst();
        return new OxbowLakePlanCompilerV2.HostBinding(
                host.id(), host.to().substring("feature:".length()));
    }

    private static OxbowLakePlanCompilerV2.RiverRelationBinding resolveRiverBinding(
            TerrainIntentV2.Feature oxbow,
            TerrainIntentV2 intent
    ) {
        String endpoint = "feature:" + oxbow.id();
        List<TerrainIntentV2.Relation> rivers = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ORIGINATES_AT)
                .filter(relation -> endpoint.equals(relation.from()))
                .toList();
        if (rivers.isEmpty()) {
            throw new FoundationSliceException("v2.oxbow-missing-river",
                    "oxbow requires HARD ORIGINATES_AT parent river");
        }
        TerrainIntentV2.Relation river = rivers.getFirst();
        return new OxbowLakePlanCompilerV2.RiverRelationBinding(
                river.id(), river.to().substring("feature:".length()));
    }

    private static TerrainIntentV2.Feature requireFeature(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(feature -> feature.id().equals(featureId))
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException(
                        "v2.oxbow-orphan",
                        "required feature is missing: " + featureId));
    }

    public record FoundationOxbowLakeSliceV2(
            OxbowLakePlanV2 oxbow,
            RiverPlanV2 river,
            SurfaceFoundationPlanV2 foundation,
            FoundationOxbowLakeValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String exportChecksum
    ) {
    }
}
