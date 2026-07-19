package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.rockycoast.RockyCoastGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.seacliff.SeaCliffGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.overhang.OverhangPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.seacave.SeaCavePlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationRockyCoastCliffValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RockyCoastPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SeaCliffPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.OverhangPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.SeaCavePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates V2-9-06 rocky-coast/sea-cliff vertical slice compile, validation, and host handoff. */
public final class FoundationRockyCoastCliffSliceCompilerV2 {
    private static final long M = TerrainIntentV2.FIXED_SCALE;

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final RockyCoastPlanCompilerV2 rockyCoastCompiler = new RockyCoastPlanCompilerV2();
    private final SeaCliffPlanCompilerV2 seaCliffCompiler = new SeaCliffPlanCompilerV2();
    private final SurfaceFoundationPlanCompilerV2 foundationCompiler = new SurfaceFoundationPlanCompilerV2();

    public FoundationRockyCoastCliffSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        TerrainIntentV2.Feature coastFeature =
                requireFeature(intent, TerrainIntentV2.FeatureKind.ROCKY_COAST);
        TerrainIntentV2.Feature cliffFeature =
                requireFeature(intent, TerrainIntentV2.FeatureKind.SEA_CLIFF);

        requireRelation(intent, coastFeature.id(), cliffFeature.id(),
                "v2.rocky-coast-cliff-relation",
                "rocky-coast/sea-cliff slice requires ADJACENT_TO or OVERLAPS relation");

        RockyCoastPlanV2 coastPlan = codec.sealRockyCoastPlan(rockyCoastCompiler.compile(
                coastFeature, intent, bounds, codec.geometryChecksum(coastFeature.geometry())));
        SeaCliffPlanV2 cliffPlan = codec.sealSeaCliffPlan(seaCliffCompiler.compile(
                cliffFeature, intent, bounds, codec.geometryChecksum(cliffFeature.geometry())));

        boolean transitionOk = evaluateTransitionOk(intent, coastFeature.id());

        List<SurfaceFoundationPlanCompilerV2.OwnerSpec> owners = List.of(
                new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        coastPlan.featureId(), 1, 14, 0,
                        SurfaceFoundationPlanV2.SurfaceClassCode.COAST),
                new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        cliffPlan.featureId(), 2, 18, 1,
                        SurfaceFoundationPlanV2.SurfaceClassCode.CLIFF));
        List<SurfaceFoundationPlanCompilerV2.InteractionSpec> interactions = List.of(
                resolveInteraction(intent, coastPlan.featureId(), cliffPlan.featureId(),
                        Math.max(1, Math.min(
                                coastPlan.capeOrBeachTransitionBandBlocks(),
                                cliffPlan.coastTransitionBandBlocks()))));

        SurfaceFoundationPlanV2 foundation = foundationCompiler.compile(
                bounds.width(), bounds.length(), globalSeed, owners, interactions);

        RockyCoastGeneratorV2 coastGenerator = new RockyCoastGeneratorV2(coastPlan);
        SeaCliffGeneratorV2 cliffGenerator = new SeaCliffGeneratorV2(cliffPlan);
        RockyCoastGeneratorV2.RockyCoastMetrics coastMetrics = coastGenerator.evaluate();
        SeaCliffGeneratorV2.SeaCliffMetrics cliffMetrics = cliffGenerator.evaluate();
        SpatialChecks spatial = evaluateSpatial(coastGenerator, cliffGenerator, cliffPlan, transitionOk);

        List<SurfaceFoundationMergeCompilerV2.OwnerLayer> layers = new ArrayList<>();
        for (SurfaceFoundationPlanV2.OwnerDescriptor owner : foundation.owners()) {
            if (owner.ownerId().equals(coastPlan.featureId())) {
                layers.add(coastGenerator.toOwnerLayer(owner));
            } else if (owner.ownerId().equals(cliffPlan.featureId())) {
                layers.add(cliffGenerator.toOwnerLayer(owner));
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

        FoundationRockyCoastCliffValidationArtifactV2 validation =
                codec.sealFoundationRockyCoastCliffValidationArtifact(
                        new FoundationRockyCoastCliffValidationArtifactV2(
                                FoundationRockyCoastCliffValidationArtifactV2.VERSION,
                                FoundationRockyCoastCliffValidationArtifactV2.CONTRACT_VERSION,
                                coastPlan.featureId(),
                                cliffPlan.featureId(),
                                new FoundationRockyCoastCliffValidationArtifactV2.Metrics(
                                        coastMetrics.shelfPresent() && spatial.rockShelfPresent(),
                                        cliffMetrics.facePresent() && spatial.cliffFacePresent(),
                                        cliffMetrics.talusPresent() && coastMetrics.talusHandoffPresent(),
                                        cliffMetrics.notchPresent(),
                                        spatial.shoreSideOk(),
                                        spatial.hostAabbOk(),
                                        spatial.coastTransitionOk(),
                                        spatial.surfaceVolumeOwnershipOk(),
                                        spatial.haloBudgetOk()),
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

        return new FoundationRockyCoastCliffSliceV2(
                coastPlan, cliffPlan, foundation, validation, preview, wholeChecksums);
    }

    /** Host handoff for SEA_CAVE against the sealed sea-cliff support AABB. */
    public SeaCavePlanCompilerV2.CompiledSeaCaveV2 compileSeaCaveHostHandoff(SeaCliffPlanV2 cliff) {
        Objects.requireNonNull(cliff, "cliff");
        VolumeSdfAabbV2 host = cliff.hostSupportAabb();
        SeaCavePlanV2.CardinalFace face = toSeaCaveFace(cliff.seawardSide());
        long openingY = Math.multiplyExact((long) cliff.waterLevel(), M);
        long midZ = (host.minZMillionths() + host.maxZMillionths()) / 2L;
        long midX = (host.minXMillionths() + host.maxXMillionths()) / 2L;
        VolumeSdfVec3V2 opening;
        VolumeSdfVec3V2 inland;
        switch (face) {
            case WEST -> {
                opening = new VolumeSdfVec3V2(host.minXMillionths(), openingY, midZ);
                inland = new VolumeSdfVec3V2(
                        Math.min(host.maxXMillionths(), host.minXMillionths() + 20 * M), openingY, midZ);
            }
            case EAST -> {
                opening = new VolumeSdfVec3V2(host.maxXMillionths(), openingY, midZ);
                inland = new VolumeSdfVec3V2(
                        Math.max(host.minXMillionths(), host.maxXMillionths() - 20 * M), openingY, midZ);
            }
            case NORTH -> {
                opening = new VolumeSdfVec3V2(midX, openingY, host.minZMillionths());
                inland = new VolumeSdfVec3V2(
                        midX, openingY, Math.min(host.maxZMillionths(), host.minZMillionths() + 20 * M));
            }
            case SOUTH -> {
                opening = new VolumeSdfVec3V2(midX, openingY, host.maxZMillionths());
                inland = new VolumeSdfVec3V2(
                        midX, openingY, Math.max(host.minZMillionths(), host.maxZMillionths() - 20 * M));
            }
            default -> throw new FoundationSliceException(
                    "v2.sea-cliff-host-face", "unsupported sea-cave seaward face");
        }
        return SeaCavePlanCompilerV2.compile(
                "sea-cave-host-handoff",
                cliff.featureId(),
                face,
                host,
                opening,
                inland,
                4 * M,
                cliff.waterLevel(),
                "fluid.sea-cave-handoff",
                cliff.waterLevel() + cliff.selectedCliffHeightBlocks(),
                SeaCavePlanV2.Kernel.standard());
    }

    /** Host handoff for OVERHANG against the sealed sea-cliff support AABB. */
    public OverhangPlanCompilerV2.CompiledOverhangV2 compileOverhangHostHandoff(SeaCliffPlanV2 cliff) {
        Objects.requireNonNull(cliff, "cliff");
        VolumeSdfAabbV2 host = cliff.hostSupportAabb();
        OverhangPlanV2.CardinalFace face = toOverhangFace(cliff.seawardSide());
        long midY = (host.minYMillionths() + host.maxYMillionths()) / 2L;
        long midZ = (host.minZMillionths() + host.maxZMillionths()) / 2L;
        long midX = (host.minXMillionths() + host.maxXMillionths()) / 2L;
        VolumeSdfVec3V2 lobeCenter;
        VolumeSdfVec3V2 recessCenter;
        switch (face) {
            case WEST -> {
                lobeCenter = new VolumeSdfVec3V2(host.minXMillionths() - 6 * M, midY, midZ);
                recessCenter = new VolumeSdfVec3V2(host.minXMillionths() - 6 * M, midY - 4 * M, midZ);
            }
            case EAST -> {
                lobeCenter = new VolumeSdfVec3V2(host.maxXMillionths() + 6 * M, midY, midZ);
                recessCenter = new VolumeSdfVec3V2(host.maxXMillionths() + 6 * M, midY - 4 * M, midZ);
            }
            case NORTH -> {
                lobeCenter = new VolumeSdfVec3V2(midX, midY, host.minZMillionths() - 6 * M);
                recessCenter = new VolumeSdfVec3V2(midX, midY - 4 * M, host.minZMillionths() - 6 * M);
            }
            case SOUTH -> {
                lobeCenter = new VolumeSdfVec3V2(midX, midY, host.maxZMillionths() + 6 * M);
                recessCenter = new VolumeSdfVec3V2(midX, midY - 4 * M, host.maxZMillionths() + 6 * M);
            }
            default -> throw new FoundationSliceException(
                    "v2.sea-cliff-host-face", "unsupported overhang seaward face");
        }
        VolumeSdfVec3V2 lobeHalf = new VolumeSdfVec3V2(6 * M, 8 * M, 6 * M);
        VolumeSdfVec3V2 recessHalf = new VolumeSdfVec3V2(6 * M, 4 * M, 6 * M);
        return OverhangPlanCompilerV2.compile(
                "overhang-host-handoff",
                cliff.featureId(),
                face,
                host,
                lobeCenter,
                lobeHalf,
                0L,
                recessCenter,
                recessHalf,
                0L,
                OverhangPlanV2.Kernel.standard());
    }

    private static boolean evaluateTransitionOk(TerrainIntentV2 intent, String coastFeatureId) {
        boolean beachOrCape = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.SANDY_BEACH
                        || feature.kind() == TerrainIntentV2.FeatureKind.ROCKY_CAPE)
                .anyMatch(feature -> intent.relations().stream()
                        .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ADJACENT_TO)
                        .anyMatch(relation -> references(relation.from(), coastFeatureId, feature.id())
                                && references(relation.to(), coastFeatureId, feature.id())));
        return beachOrCape || intent.relations().stream()
                .anyMatch(relation -> (relation.kind() == TerrainIntentV2.RelationKind.ADJACENT_TO
                        || relation.kind() == TerrainIntentV2.RelationKind.OVERLAPS)
                        && (relation.from().contains(coastFeatureId)
                        || relation.to().contains(coastFeatureId)));
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
                    "rocky-coast/cliff slice requires " + kind.name() + " feature");
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
            RockyCoastGeneratorV2 coast,
            SeaCliffGeneratorV2 cliff,
            SeaCliffPlanV2 cliffPlan,
            boolean transitionOk
    ) {
        int width = coast.width();
        int length = coast.length();
        long shelfCells = 0L;
        long faceCells = 0L;
        long shared = 0L;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                boolean shelf = coast.sampleAt(x, z).active();
                boolean face = cliff.sampleAt(x, z).faceMask() == 1;
                if (shelf) {
                    shelfCells++;
                }
                if (face) {
                    faceCells++;
                }
                if (shelf && face) {
                    shared++;
                }
            }
        }
        if (shelfCells < 1L || faceCells < 1L) {
            throw new FoundationSliceException("v2.rocky-coast-cliff-spatial",
                    "rocky coast shelf or sea cliff face cells are missing");
        }
        VolumeSdfAabbV2 host = cliffPlan.hostSupportAabb();
        boolean hostAabbOk = host.minYMillionths()
                == Math.multiplyExact((long) cliffPlan.waterLevel(), M)
                && host.maxYMillionths() == Math.multiplyExact(
                (long) cliffPlan.waterLevel() + cliffPlan.selectedCliffHeightBlocks(), M)
                && host.extentXBlocks() > 0
                && host.extentZBlocks() > 0;
        boolean haloBudgetOk = cliffPlan.supportRadiusXZ() <= 64
                && coast.plan().supportRadiusXZ() <= 64;
        boolean coastTransitionOk = transitionOk || shared > 0L;
        if (!coastTransitionOk) {
            throw new FoundationSliceException("v2.rocky-coast-transition",
                    "rocky coast cape/beach or coast-cliff transition is missing");
        }
        return new SpatialChecks(
                shelfCells > 0L,
                faceCells > 0L,
                true,
                hostAabbOk,
                coastTransitionOk,
                shared > 0L || faceCells > 0L,
                haloBudgetOk);
    }

    private static SeaCavePlanV2.CardinalFace toSeaCaveFace(TerrainIntentV2.Edge edge) {
        return switch (edge) {
            case NORTH -> SeaCavePlanV2.CardinalFace.NORTH;
            case EAST -> SeaCavePlanV2.CardinalFace.EAST;
            case SOUTH -> SeaCavePlanV2.CardinalFace.SOUTH;
            case WEST -> SeaCavePlanV2.CardinalFace.WEST;
        };
    }

    private static OverhangPlanV2.CardinalFace toOverhangFace(TerrainIntentV2.Edge edge) {
        return switch (edge) {
            case NORTH -> OverhangPlanV2.CardinalFace.NORTH;
            case EAST -> OverhangPlanV2.CardinalFace.EAST;
            case SOUTH -> OverhangPlanV2.CardinalFace.SOUTH;
            case WEST -> OverhangPlanV2.CardinalFace.WEST;
        };
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
            boolean rockShelfPresent,
            boolean cliffFacePresent,
            boolean shoreSideOk,
            boolean hostAabbOk,
            boolean coastTransitionOk,
            boolean surfaceVolumeOwnershipOk,
            boolean haloBudgetOk
    ) {
    }

    public record FoundationRockyCoastCliffSliceV2(
            RockyCoastPlanV2 rockyCoast,
            SeaCliffPlanV2 seaCliff,
            SurfaceFoundationPlanV2 foundation,
            FoundationRockyCoastCliffValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> wholeChecksums
    ) {
        public FoundationRockyCoastCliffSliceV2 {
            wholeChecksums = Map.copyOf(Objects.requireNonNull(wholeChecksums, "wholeChecksums"));
        }
    }
}
