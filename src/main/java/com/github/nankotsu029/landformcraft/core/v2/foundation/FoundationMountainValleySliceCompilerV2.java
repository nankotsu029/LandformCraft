package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.mountain.MountainRangeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.valley.ValleyGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationRangeValleyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MountainRangePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ValleyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Orchestrates V2-9-03 mountain/valley vertical slice compile, merge, validation, and preview export. */
public final class FoundationMountainValleySliceCompilerV2 {
    private static final Set<TerrainIntentV2.FeatureKind> CONNECTION_TARGETS = Set.of(
            TerrainIntentV2.FeatureKind.FJORD,
            TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
            TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE,
            TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE,
            TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final MountainRangePlanCompilerV2 mountainCompiler = new MountainRangePlanCompilerV2();
    private final ValleyPlanCompilerV2 valleyCompiler = new ValleyPlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationMountainValleySliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Optional<TerrainIntentV2.Feature> mountainFeature =
                findFeature(intent, TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE);
        Optional<TerrainIntentV2.Feature> valleyFeature =
                findFeature(intent, TerrainIntentV2.FeatureKind.VALLEY);
        if (mountainFeature.isEmpty() && valleyFeature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "mountain/valley slice requires at least one MOUNTAIN_RANGE or VALLEY feature");
        }

        MountainRangePlanV2 mountainPlan = null;
        ValleyPlanV2 valleyPlan = null;
        if (mountainFeature.isPresent()) {
            TerrainIntentV2.Feature feature = mountainFeature.get();
            mountainPlan = codec.sealMountainRangePlan(mountainCompiler.compile(
                    feature, intent, bounds, codec.geometryChecksum(feature.geometry())));
        }
        if (valleyFeature.isPresent()) {
            TerrainIntentV2.Feature feature = valleyFeature.get();
            valleyPlan = codec.sealValleyPlan(valleyCompiler.compile(
                    feature, intent, bounds, codec.geometryChecksum(feature.geometry())));
            validateValleyConnection(intent, feature, valleyPlan);
        }

        List<SurfaceFoundationPlanCompilerV2.OwnerSpec> owners = new ArrayList<>();
        List<SurfaceFoundationPlanCompilerV2.InteractionSpec> interactions = new ArrayList<>();
        int ownerIndex = 1;
        if (mountainPlan != null) {
            owners.add(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                    mountainPlan.featureId(), ownerIndex++, 30, 0,
                    SurfaceFoundationPlanV2.SurfaceClassCode.MOUNTAIN));
        }
        if (valleyPlan != null) {
            owners.add(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                    valleyPlan.featureId(), ownerIndex, 25, mountainPlan != null ? 1 : 0,
                    SurfaceFoundationPlanV2.SurfaceClassCode.VALLEY));
        }
        if (mountainPlan != null && valleyPlan != null) {
            interactions.add(resolveInteraction(intent, mountainPlan.featureId(), valleyPlan.featureId(),
                    Math.min(mountainPlan.valleyTransitionBandBlocks(),
                            valleyPlan.mountainTransitionBandBlocks())));
        }

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed, owners, interactions);
        List<SurfaceFoundationMergeCompilerV2.OwnerLayer> layers = new ArrayList<>();
        MountainRangeGeneratorV2.MountainMetrics mountainMetrics = null;
        ValleyGeneratorV2.ValleyMetrics valleyMetrics = null;
        for (SurfaceFoundationPlanV2.OwnerDescriptor owner : foundation.owners()) {
            if (mountainPlan != null && owner.ownerId().equals(mountainPlan.featureId())) {
                MountainRangeGeneratorV2 generator = new MountainRangeGeneratorV2(mountainPlan);
                mountainMetrics = generator.evaluate();
                layers.add(generator.toOwnerLayer(owner));
            } else if (valleyPlan != null && owner.ownerId().equals(valleyPlan.featureId())) {
                ValleyGeneratorV2 generator = new ValleyGeneratorV2(valleyPlan);
                valleyMetrics = generator.evaluate();
                layers.add(generator.toOwnerLayer(owner));
            }
        }

        SurfaceFoundationMergeCompilerV2 merge = new SurfaceFoundationMergeCompilerV2(foundation, layers);
        Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> wholeChecksums = Map.of();
        boolean mergeOk = false;
        boolean floorConflictFree = true;
        if (mountainPlan != null && valleyPlan != null) {
            wholeChecksums = merge.wholeFieldChecksums();
            TilePlanV2 tiles = TilePlanV2.of(bounds.width(), bounds.length(),
                    ScaleProfileV2.defaults(ScaleClassV2.forDimensions(bounds.width(), bounds.length())));
            Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> tiledChecksums =
                    merge.tiledFieldChecksums(tiles);
            if (!wholeChecksums.equals(tiledChecksums)) {
                throw new FoundationSliceException("v2.foundation-merge-mismatch",
                        "whole and tiled foundation merge checksums differ");
            }
            mergeOk = true;
            floorConflictFree = true;
        }

        boolean transitionOk = mountainPlan == null || valleyPlan == null || interactions.size() == 1;
        boolean connectionOk = valleyPlan == null
                || valleyPlan.connectionRole() == TerrainIntentV2.ValleyConnectionRole.NONE
                || !valleyPlan.connectionAnchors().isEmpty();
        FoundationRangeValleyValidationArtifactV2 validation = codec.sealFoundationRangeValleyValidationArtifact(
                new FoundationRangeValleyValidationArtifactV2(
                        FoundationRangeValleyValidationArtifactV2.VERSION,
                        FoundationRangeValleyValidationArtifactV2.CONTRACT_VERSION,
                        mountainPlan != null ? mountainPlan.featureId() : "",
                        valleyPlan != null ? valleyPlan.featureId() : "",
                        new FoundationRangeValleyValidationArtifactV2.Metrics(
                                mountainMetrics == null || mountainMetrics.ridgeGraphOk(),
                                mountainMetrics == null || mountainMetrics.peakPassBudgetOk(),
                                floorConflictFree && (mergeOk || mountainPlan == null || valleyPlan == null),
                                transitionOk && (mergeOk || mountainPlan == null || valleyPlan == null),
                                connectionOk && (valleyMetrics == null || valleyMetrics.floorPresent())),
                        List.of(),
                        "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        if (!wholeChecksums.isEmpty()) {
            wholeChecksums.forEach((field, checksum) -> previewLayers.add(
                    new FoundationPreviewIndexV2.Layer(
                            previewLayerId(field),
                            previewFieldId(field),
                            checksum)));
        }
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationMountainValleySliceV2(
                mountainPlan, valleyPlan, foundation, validation, preview, wholeChecksums);
    }

    private static void validateValleyConnection(
            TerrainIntentV2 intent,
            TerrainIntentV2.Feature valleyFeature,
            ValleyPlanV2 valleyPlan
    ) {
        if (valleyPlan.connectionRole() == TerrainIntentV2.ValleyConnectionRole.NONE) {
            return;
        }
        List<TerrainIntentV2.Relation> connections = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.CONNECTS_TO
                        || relation.kind() == TerrainIntentV2.RelationKind.FLANKS
                        || relation.kind() == TerrainIntentV2.RelationKind.DRAINS_TO
                        || relation.kind() == TerrainIntentV2.RelationKind.ORIGINATES_AT)
                .filter(relation -> references(relation.from(), valleyFeature.id())
                        || references(relation.to(), valleyFeature.id()))
                .toList();
        if (connections.isEmpty()) {
            throw new FoundationSliceException("v2.valley-connection",
                    "valley connection role requires CONNECTS_TO, FLANKS, DRAINS_TO, or ORIGINATES_AT");
        }
        for (TerrainIntentV2.Relation relation : connections) {
            String otherId = otherEndpoint(relation, valleyFeature.id());
            TerrainIntentV2.Feature other = intent.features().stream()
                    .filter(feature -> feature.id().equals(otherId))
                    .findFirst()
                    .orElseThrow(() -> new FoundationSliceException("v2.valley-connection",
                            "valley connection references missing feature " + otherId));
            if (!CONNECTION_TARGETS.contains(other.kind())) {
                throw new FoundationSliceException("v2.valley-connection",
                        "valley connection target kind is not fjord/river/mountain");
            }
            if (valleyPlan.connectionRole() == TerrainIntentV2.ValleyConnectionRole.FJORD_HEAD
                    && other.kind() != TerrainIntentV2.FeatureKind.FJORD) {
                throw new FoundationSliceException("v2.valley-connection",
                        "FJORD_HEAD connection requires a FJORD target");
            }
            if (valleyPlan.connectionRole() == TerrainIntentV2.ValleyConnectionRole.RIVER_CORRIDOR
                    && other.kind() != TerrainIntentV2.FeatureKind.MEANDERING_RIVER) {
                throw new FoundationSliceException("v2.valley-connection",
                        "RIVER_CORRIDOR connection requires a MEANDERING_RIVER target");
            }
        }
    }

    private static Optional<TerrainIntentV2.Feature> findFeature(
            TerrainIntentV2 intent,
            TerrainIntentV2.FeatureKind kind
    ) {
        return intent.features().stream().filter(feature -> feature.kind() == kind).findFirst();
    }

    private static SurfaceFoundationPlanCompilerV2.InteractionSpec resolveInteraction(
            TerrainIntentV2 intent,
            String mountainId,
            String valleyId,
            int defaultBand
    ) {
        List<TerrainIntentV2.Relation> relations = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ADJACENT_TO
                        || relation.kind() == TerrainIntentV2.RelationKind.OVERLAPS
                        || relation.kind() == TerrainIntentV2.RelationKind.FLANKS)
                .filter(relation -> references(relation.from(), mountainId, valleyId)
                        && references(relation.to(), mountainId, valleyId))
                .toList();
        if (relations.size() != 1) {
            throw new FoundationSliceException("v2.foundation-transition",
                    "mountain/valley slice requires exactly one ADJACENT_TO, OVERLAPS, or FLANKS relation");
        }
        TerrainIntentV2.Relation relation = relations.getFirst();
        int band = relation.transition() != null
                ? relation.transition().bandBlocks()
                : defaultBand;
        return new SurfaceFoundationPlanCompilerV2.InteractionSpec(
                relation.id(), mountainId, valleyId, band);
    }

    private static boolean references(String endpoint, String firstId, String secondId) {
        String featureRef = endpoint.startsWith("feature:") ? endpoint.substring("feature:".length()) : "";
        return featureRef.equals(firstId) || featureRef.equals(secondId);
    }

    private static boolean references(String endpoint, String featureId) {
        String featureRef = endpoint.startsWith("feature:") ? endpoint.substring("feature:".length()) : "";
        return featureRef.equals(featureId);
    }

    private static String otherEndpoint(TerrainIntentV2.Relation relation, String valleyId) {
        if (references(relation.from(), valleyId)) {
            return relation.to().startsWith("feature:")
                    ? relation.to().substring("feature:".length()) : relation.to();
        }
        return relation.from().startsWith("feature:")
                ? relation.from().substring("feature:".length()) : relation.from();
    }

    private static String previewLayerId(SurfaceFoundationMergeCompilerV2.CompositionField field) {
        return switch (field) {
            case SURFACE_CLASS -> "surface-class";
            case ELEVATION -> "elevation";
            case RESIDUAL -> "residual";
            case OWNER_INDEX -> "owner-index";
            case TRANSITION_WEIGHT -> "transition-weight";
        };
    }

    private static String previewFieldId(SurfaceFoundationMergeCompilerV2.CompositionField field) {
        return switch (field) {
            case SURFACE_CLASS -> SurfaceFoundationPlanV2.SURFACE_CLASS_FIELD_ID;
            case ELEVATION -> SurfaceFoundationPlanV2.ELEVATION_FIELD_ID;
            case RESIDUAL -> SurfaceFoundationPlanV2.RESIDUAL_FIELD_ID;
            case OWNER_INDEX -> SurfaceFoundationPlanV2.OWNER_INDEX_FIELD_ID;
            case TRANSITION_WEIGHT -> SurfaceFoundationPlanV2.TRANSITION_WEIGHT_FIELD_ID;
        };
    }

    public record FoundationMountainValleySliceV2(
            MountainRangePlanV2 mountain,
            ValleyPlanV2 valley,
            SurfaceFoundationPlanV2 foundation,
            FoundationRangeValleyValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> wholeChecksums
    ) {
        public FoundationMountainValleySliceV2 {
            wholeChecksums = Map.copyOf(Objects.requireNonNull(wholeChecksums, "wholeChecksums"));
        }
    }
}
