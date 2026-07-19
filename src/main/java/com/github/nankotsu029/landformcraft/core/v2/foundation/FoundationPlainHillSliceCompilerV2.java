package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.hill.HillRangeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.plain.PlainGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.HillRangePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-02 plain/hill vertical slice compile, merge, validation, and preview export. */
public final class FoundationPlainHillSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final PlainPlanCompilerV2 plainCompiler = new PlainPlanCompilerV2();
    private final HillRangePlanCompilerV2 hillCompiler = new HillRangePlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationPlainHillSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Optional<TerrainIntentV2.Feature> plainFeature = findFeature(intent, TerrainIntentV2.FeatureKind.PLAIN);
        Optional<TerrainIntentV2.Feature> hillFeature = findFeature(intent, TerrainIntentV2.FeatureKind.HILL_RANGE);
        if (plainFeature.isEmpty() && hillFeature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "plain/hill slice requires at least one PLAIN or HILL_RANGE feature");
        }

        PlainPlanV2 plainPlan = null;
        HillRangePlanV2 hillPlan = null;
        if (plainFeature.isPresent()) {
            TerrainIntentV2.Feature feature = plainFeature.get();
            plainPlan = codec.sealPlainPlan(plainCompiler.compile(
                    feature, intent, bounds, codec.geometryChecksum(feature.geometry())));
        }
        if (hillFeature.isPresent()) {
            TerrainIntentV2.Feature feature = hillFeature.get();
            hillPlan = codec.sealHillRangePlan(hillCompiler.compile(
                    feature, intent, bounds, codec.geometryChecksum(feature.geometry())));
        }

        List<SurfaceFoundationPlanCompilerV2.OwnerSpec> owners = new ArrayList<>();
        List<SurfaceFoundationPlanCompilerV2.InteractionSpec> interactions = new ArrayList<>();
        int ownerIndex = 1;
        if (plainPlan != null) {
            owners.add(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                    plainPlan.featureId(), ownerIndex++, 10, 0,
                    SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN));
        }
        if (hillPlan != null) {
            owners.add(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                    hillPlan.featureId(), ownerIndex, 20, plainPlan != null ? 1 : 0,
                    SurfaceFoundationPlanV2.SurfaceClassCode.HILL));
        }
        if (plainPlan != null && hillPlan != null) {
            interactions.add(resolveInteraction(intent, plainPlan.featureId(), hillPlan.featureId(),
                    hillPlan.plainTransitionBandBlocks()));
        }

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed, owners, interactions);
        List<SurfaceFoundationMergeCompilerV2.OwnerLayer> layers = new ArrayList<>();
        PlainGeneratorV2.PlainMetrics plainMetrics = null;
        HillRangeGeneratorV2.HillMetrics hillMetrics = null;
        for (SurfaceFoundationPlanV2.OwnerDescriptor owner : foundation.owners()) {
            if (plainPlan != null && owner.ownerId().equals(plainPlan.featureId())) {
                PlainGeneratorV2 generator = new PlainGeneratorV2(plainPlan);
                plainMetrics = generator.evaluate();
                layers.add(generator.toOwnerLayer(owner));
            } else if (hillPlan != null && owner.ownerId().equals(hillPlan.featureId())) {
                HillRangeGeneratorV2 generator = new HillRangeGeneratorV2(hillPlan);
                hillMetrics = generator.evaluate();
                layers.add(generator.toOwnerLayer(owner));
            }
        }

        SurfaceFoundationMergeCompilerV2 merge = new SurfaceFoundationMergeCompilerV2(foundation, layers);
        Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> wholeChecksums = Map.of();
        boolean mergeOk = false;
        if (plainPlan != null && hillPlan != null) {
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
        }

        boolean transitionOk = plainPlan == null || hillPlan == null || interactions.size() == 1;
        FoundationValidationArtifactV2 validation = codec.sealFoundationValidationArtifact(
                new FoundationValidationArtifactV2(
                        FoundationValidationArtifactV2.VERSION,
                        FoundationValidationArtifactV2.CONTRACT_VERSION,
                        plainPlan != null ? plainPlan.featureId() : "",
                        hillPlan != null ? hillPlan.featureId() : "",
                        new FoundationValidationArtifactV2.Metrics(
                                plainMetrics == null || plainMetrics.microReliefPresent(),
                                hillMetrics == null || hillMetrics.ridgeContinuous(),
                                hillMetrics == null || hillMetrics.saddleBudgetOk(),
                                plainMetrics == null || plainMetrics.groundwaterHandoffPresent(),
                                transitionOk && (mergeOk || plainPlan == null || hillPlan == null)),
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

        return new FoundationPlainHillSliceV2(plainPlan, hillPlan, foundation, validation, preview, wholeChecksums);
    }

    private static Optional<TerrainIntentV2.Feature> findFeature(
            TerrainIntentV2 intent,
            TerrainIntentV2.FeatureKind kind
    ) {
        return intent.features().stream().filter(feature -> feature.kind() == kind).findFirst();
    }

    private static SurfaceFoundationPlanCompilerV2.InteractionSpec resolveInteraction(
            TerrainIntentV2 intent,
            String plainId,
            String hillId,
            int defaultBand
    ) {
        List<TerrainIntentV2.Relation> relations = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ADJACENT_TO
                        || relation.kind() == TerrainIntentV2.RelationKind.OVERLAPS)
                .filter(relation -> references(relation.from(), plainId, hillId)
                        && references(relation.to(), plainId, hillId))
                .toList();
        if (relations.size() != 1) {
            throw new FoundationSliceException("v2.foundation-transition",
                    "plain/hill slice requires exactly one ADJACENT_TO or OVERLAPS relation");
        }
        TerrainIntentV2.Relation relation = relations.getFirst();
        int band = relation.transition() != null
                ? relation.transition().bandBlocks()
                : defaultBand;
        return new SurfaceFoundationPlanCompilerV2.InteractionSpec(
                relation.id(), plainId, hillId, band);
    }

    private static boolean references(String endpoint, String firstId, String secondId) {
        String featureRef = endpoint.startsWith("feature:") ? endpoint.substring("feature:".length()) : "";
        return featureRef.equals(firstId) || featureRef.equals(secondId);
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

    public record FoundationPlainHillSliceV2(
            PlainPlanV2 plain,
            HillRangePlanV2 hill,
            SurfaceFoundationPlanV2 foundation,
            FoundationValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> wholeChecksums
    ) {
        public FoundationPlainHillSliceV2 {
            wholeChecksums = Map.copyOf(Objects.requireNonNull(wholeChecksums, "wholeChecksums"));
        }
    }
}
