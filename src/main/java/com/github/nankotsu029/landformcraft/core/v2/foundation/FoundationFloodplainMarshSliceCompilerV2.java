package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.floodplain.FloodplainGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.marsh.MarshGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.river.RiverGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FloodplainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationFloodplainMarshValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MarshPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-05 floodplain/river/marsh vertical slice compile, validation, and preview. */
public final class FoundationFloodplainMarshSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final RiverPlanCompilerV2 riverCompiler = new RiverPlanCompilerV2();
    private final FloodplainPlanCompilerV2 floodplainCompiler = new FloodplainPlanCompilerV2();
    private final MarshPlanCompilerV2 marshCompiler = new MarshPlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationFloodplainMarshSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        TerrainIntentV2.Feature riverFeature = requireFeature(intent, TerrainIntentV2.FeatureKind.RIVER);
        TerrainIntentV2.Feature floodplainFeature =
                requireFeature(intent, TerrainIntentV2.FeatureKind.FLOODPLAIN);
        TerrainIntentV2.Feature marshFeature = requireFeature(intent, TerrainIntentV2.FeatureKind.MARSH);

        requireRelation(intent, riverFeature.id(), floodplainFeature.id(), "v2.floodplain-river-disconnect",
                "floodplain/river slice requires ADJACENT_TO or OVERLAPS relation");
        requireRelation(intent, floodplainFeature.id(), marshFeature.id(), "v2.floodplain-marsh-transition",
                "floodplain/marsh slice requires ADJACENT_TO or OVERLAPS relation");

        RiverPlanV2 riverPlan = codec.sealRiverPlan(riverCompiler.compile(
                riverFeature, intent, bounds, codec.geometryChecksum(riverFeature.geometry())));
        FloodplainPlanV2 floodplainPlan = codec.sealFloodplainPlan(floodplainCompiler.compile(
                floodplainFeature, intent, bounds, codec.geometryChecksum(floodplainFeature.geometry())));
        MarshPlanV2 marshPlan = codec.sealMarshPlan(marshCompiler.compile(
                marshFeature, intent, bounds, codec.geometryChecksum(marshFeature.geometry())));

        if (marshPlan.groundwaterMinDepthBlocks()
                > floodplainPlan.groundwaterHandoffDepthBlocks() + marshPlan.selectedHydroperiodBlocks()) {
            throw new FoundationSliceException("v2.marsh-groundwater-hydroperiod",
                    "marsh groundwater/hydroperiod conflicts with floodplain handoff");
        }

        // Surface merge covers FLOODPLAIN+MARSH only. RIVER stays a separate hydrologic graph
        // owner so channel cells do not require an undeclared river↔marsh merge contract.
        List<SurfaceFoundationPlanCompilerV2.OwnerSpec> owners = List.of(
                new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        floodplainPlan.featureId(), 1, 12, 0,
                        SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN),
                new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        marshPlan.featureId(), 2, 18, 1,
                        SurfaceFoundationPlanV2.SurfaceClassCode.WETLAND));
        List<SurfaceFoundationPlanCompilerV2.InteractionSpec> interactions = List.of(
                resolveInteraction(intent, floodplainPlan.featureId(), marshPlan.featureId(),
                        Math.max(1, marshPlan.selectedMicroReliefBlocks())));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed, owners, interactions);

        RiverGeneratorV2 riverGenerator = new RiverGeneratorV2(riverPlan);
        FloodplainGeneratorV2 floodplainGenerator = new FloodplainGeneratorV2(floodplainPlan);
        MarshGeneratorV2 marshGenerator = new MarshGeneratorV2(marshPlan);
        FloodplainGeneratorV2.FloodplainMetrics floodplainMetrics = floodplainGenerator.evaluate();
        MarshGeneratorV2.MarshMetrics marshMetrics = marshGenerator.evaluate();

        SpatialChecks spatial = evaluateSpatial(
                riverGenerator, floodplainGenerator, marshGenerator, floodplainPlan);

        List<SurfaceFoundationMergeCompilerV2.OwnerLayer> layers = new ArrayList<>();
        for (SurfaceFoundationPlanV2.OwnerDescriptor owner : foundation.owners()) {
            if (owner.ownerId().equals(floodplainPlan.featureId())) {
                layers.add(floodplainGenerator.toOwnerLayer(owner));
            } else if (owner.ownerId().equals(marshPlan.featureId())) {
                layers.add(marshGenerator.toOwnerLayer(owner));
            }
        }
        SurfaceFoundationMergeCompilerV2 merge = new SurfaceFoundationMergeCompilerV2(foundation, layers);
        Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> wholeChecksums =
                merge.wholeFieldChecksums();
        TilePlanV2 tiles = TilePlanV2.of(bounds.width(), bounds.length(),
                ScaleProfileV2.defaults(ScaleClassV2.forDimensions(bounds.width(), bounds.length())));
        Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> tiledChecksums =
                merge.tiledFieldChecksums(tiles);
        if (!wholeChecksums.equals(tiledChecksums)) {
            throw new FoundationSliceException("v2.foundation-merge-mismatch",
                    "whole and tiled foundation merge checksums differ");
        }

        FoundationFloodplainMarshValidationArtifactV2 validation =
                codec.sealFoundationFloodplainMarshValidationArtifact(
                        new FoundationFloodplainMarshValidationArtifactV2(
                                FoundationFloodplainMarshValidationArtifactV2.VERSION,
                                FoundationFloodplainMarshValidationArtifactV2.CONTRACT_VERSION,
                                riverPlan.featureId(),
                                floodplainPlan.featureId(),
                                marshPlan.featureId(),
                                new FoundationFloodplainMarshValidationArtifactV2.Metrics(
                                        spatial.riverAdjacencyOk(),
                                        floodplainMetrics.microReliefPresent(),
                                        marshMetrics.wetnessOk(),
                                        marshMetrics.openWaterTransitionOk() && spatial.openWaterTransitionOk(),
                                        spatial.groundwaterHydroperiodOk(),
                                        marshMetrics.fluidSolidOwnershipOk(),
                                        spatial.floodplainMarshTransitionOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        wholeChecksums.forEach((field, checksum) -> previewLayers.add(
                new FoundationPreviewIndexV2.Layer(
                        previewLayerId(field),
                        previewFieldId(field),
                        checksum)));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationFloodplainMarshSliceV2(
                riverPlan, floodplainPlan, marshPlan, foundation, validation, preview, wholeChecksums);
    }

    private static TerrainIntentV2.Feature requireFeature(
            TerrainIntentV2 intent,
            TerrainIntentV2.FeatureKind kind
    ) {
        Optional<TerrainIntentV2.Feature> feature = intent.features().stream()
                .filter(candidate -> candidate.kind() == kind)
                .findFirst();
        if (feature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "floodplain/marsh slice requires " + kind.name() + " feature");
        }
        return feature.get();
    }

    private static void requireRelation(
            TerrainIntentV2 intent,
            String firstId,
            String secondId,
            String ruleId,
            String message
    ) {
        long count = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ADJACENT_TO
                        || relation.kind() == TerrainIntentV2.RelationKind.OVERLAPS)
                .filter(relation -> references(relation.from(), firstId, secondId)
                        && references(relation.to(), firstId, secondId))
                .count();
        if (count != 1L) {
            throw new FoundationSliceException(ruleId, message);
        }
    }

    private static SurfaceFoundationPlanCompilerV2.InteractionSpec resolveInteraction(
            TerrainIntentV2 intent,
            String firstId,
            String secondId,
            int defaultBand
    ) {
        List<TerrainIntentV2.Relation> relations = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ADJACENT_TO
                        || relation.kind() == TerrainIntentV2.RelationKind.OVERLAPS)
                .filter(relation -> references(relation.from(), firstId, secondId)
                        && references(relation.to(), firstId, secondId))
                .toList();
        TerrainIntentV2.Relation relation = relations.getFirst();
        int band = relation.transition() != null
                ? relation.transition().bandBlocks()
                : defaultBand;
        return new SurfaceFoundationPlanCompilerV2.InteractionSpec(
                relation.id(), firstId, secondId, band);
    }

    private static boolean references(String endpoint, String firstId, String secondId) {
        String featureRef = endpoint.startsWith("feature:") ? endpoint.substring("feature:".length()) : "";
        return featureRef.equals(firstId) || featureRef.equals(secondId);
    }

    private static SpatialChecks evaluateSpatial(
            RiverGeneratorV2 river,
            FloodplainGeneratorV2 floodplain,
            MarshGeneratorV2 marsh,
            FloodplainPlanV2 floodplainPlan
    ) {
        int width = floodplain.width();
        int length = floodplain.length();
        boolean[][] channel = new boolean[width][length];
        long channelCells = 0L;
        long filledChannel = 0L;
        long floodplainActive = 0L;
        long floodplainNearRiver = 0L;
        long marshActive = 0L;
        long sharedFloodplainMarsh = 0L;
        int band = floodplainPlan.riverAdjacencyBandBlocks();

        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                if (river.sampleAt(x, z).channelMask() == 1) {
                    channel[x][z] = true;
                    channelCells++;
                }
            }
        }

        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                boolean flood = floodplain.sampleAt(x, z).active();
                boolean marshCell = marsh.sampleAt(x, z).active();
                if (flood) {
                    floodplainActive++;
                    if (nearChannel(channel, x, z, band)) {
                        floodplainNearRiver++;
                    }
                }
                if (marshCell) {
                    marshActive++;
                    if (channel[x][z]) {
                        filledChannel++;
                    }
                    if (flood) {
                        sharedFloodplainMarsh++;
                    }
                }
            }
        }

        if (channelCells > 0L && filledChannel * 2L > channelCells) {
            throw new FoundationSliceException("v2.marsh-filled-channel",
                    "marsh fills river channel ownership");
        }
        if (floodplainActive < 1L || floodplainNearRiver < 1L) {
            throw new FoundationSliceException("v2.floodplain-river-disconnect",
                    "floodplain is disconnected from river adjacency band");
        }
        if (marshActive < 1L || sharedFloodplainMarsh < 1L) {
            throw new FoundationSliceException("v2.floodplain-marsh-transition",
                    "floodplain/marsh transition cells are missing");
        }
        if (marsh.plan().selectedWetnessMillionths() < 200_000L) {
            throw new FoundationSliceException("v2.marsh-dry", "dry marsh rejected");
        }

        return new SpatialChecks(
                floodplainNearRiver > 0L,
                true,
                marsh.plan().groundwaterMinDepthBlocks()
                        <= floodplainPlan.groundwaterHandoffDepthBlocks()
                        + marsh.plan().selectedHydroperiodBlocks(),
                sharedFloodplainMarsh > 0L);
    }

    private static boolean nearChannel(boolean[][] channel, int x, int z, int band) {
        int width = channel.length;
        int length = channel[0].length;
        for (int dz = -band; dz <= band; dz++) {
            for (int dx = -band; dx <= band; dx++) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= width || nz >= length) {
                    continue;
                }
                if (channel[nx][nz]) {
                    return true;
                }
            }
        }
        return false;
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

    private record SpatialChecks(
            boolean riverAdjacencyOk,
            boolean openWaterTransitionOk,
            boolean groundwaterHydroperiodOk,
            boolean floodplainMarshTransitionOk
    ) {
    }

    public record FoundationFloodplainMarshSliceV2(
            RiverPlanV2 river,
            FloodplainPlanV2 floodplain,
            MarshPlanV2 marsh,
            SurfaceFoundationPlanV2 foundation,
            FoundationFloodplainMarshValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> wholeChecksums
    ) {
        public FoundationFloodplainMarshSliceV2 {
            wholeChecksums = Map.copyOf(Objects.requireNonNull(wholeChecksums, "wholeChecksums"));
        }
    }
}
