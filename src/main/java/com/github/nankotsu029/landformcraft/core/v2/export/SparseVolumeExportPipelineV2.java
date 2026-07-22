package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.release.SparseVolumeReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.VolumeTileBlockResolverV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.VolumeValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainQuery;
import com.github.nankotsu029.landformcraft.generator.v2.volume.index.VolumeAabbIndexBuilderV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.query.VolumeTerrainQueryV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.VolumeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeAabbIndexPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import com.github.nankotsu029.landformcraft.validation.v2.volume.VolumeValidationInputV2;
import com.github.nankotsu029.landformcraft.validation.v2.volume.VolumeValidationReportV2;
import com.github.nankotsu029.landformcraft.validation.v2.volume.VolumeValidatorV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * V2-15-08 production pipeline for the shared {@code sparse-volume} capability.
 *
 * <p>The pipeline reuses the complete environment chain and adds a sealed identity volume plan.
 * Its sole ordered operator targets the guaranteed bedrock cell at release-local (0,minY,0):
 * {@code ADD_FLUID} cannot replace SOLID, so the operator executes without inventing a volume
 * Feature or changing the canonical surface stream. The result still traverses the production
 * ordered-CSG query and is exported as bounded 3D volume tiles for strict Sponge read-back.</p>
 */
final class SparseVolumeExportPipelineV2 implements ProductionExportPipelineV2 {
    static final String PIPELINE_ID = "v2.production.sparse-volume.shared";
    static final String GENERATOR_HANDLER_ID = "v2.volume.shared-ordered-csg";
    static final String VALIDATOR_HANDLER_ID = "v2.volume.descriptor-validator";
    static final String PREVIEW_HANDLER_ID = "v2.volume.tile-readback-diagnostic";
    static final String EXPORT_HANDLER_ID = "v2.release.sparse-volume-export";

    private static final long MILLIONTHS = VolumeSdfPrimitivePlanV2.Quantization.FIXED_SCALE;
    private static final String IDENTITY_PRIMITIVE_ID = "shared-volume.identity-cell";
    private static final String IDENTITY_OPERATOR_ID = "shared-volume.identity-operator";
    private static final String IDENTITY_FLUID_ID = "fluid.shared-volume-identity";

    private static final PipelineDescriptor DESCRIPTOR = new PipelineDescriptor(
            PIPELINE_ID,
            new HandlerSet(
                    GENERATOR_HANDLER_ID,
                    VALIDATOR_HANDLER_ID,
                    PREVIEW_HANDLER_ID,
                    EXPORT_HANDLER_ID),
            List.of(
                    TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                    TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                    TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                    TerrainIntentV2.FeatureKind.SANDY_BEACH),
            List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS),
            ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT);

    private final EnvironmentFieldsExportPipelineV2 environment = new EnvironmentFieldsExportPipelineV2();
    private final LandformV2DataCodec data = new LandformV2DataCodec();
    private final OfflineTileArtifactCodecV2 tileCodec = new OfflineTileArtifactCodecV2();
    private final OfflineTileSchematicWriterV2 tileWriter = new OfflineTileSchematicWriterV2();
    private final VolumeValidatorV2 validator = new VolumeValidatorV2();

    @Override
    public PipelineDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public GeneratedSurface generate(
            GenerationRequestV2 request,
            TerrainIntentV2 intent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        throw new IOException("sparse-volume pipeline requires the complete capability prefix; "
                + "use generateSparseVolume");
    }

    @Override
    public GeneratedHydrology generateHydrology(
            GenerationRequestV2 request,
            TerrainIntentV2 intent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        throw new IOException("sparse-volume pipeline cannot publish hydrology-plan alone; "
                + "use generateSparseVolume");
    }

    @Override
    public GeneratedEnvironment generateEnvironment(
            GenerationRequestV2 request,
            TerrainIntentV2 intent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        throw new IOException("sparse-volume pipeline cannot publish environment-fields alone; "
                + "use generateSparseVolume");
    }

    @Override
    public GeneratedSparseVolume generateSparseVolume(
            GenerationRequestV2 request,
            TerrainIntentV2 draftIntent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(draftIntent, "draftIntent");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(workRoot, "workRoot");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(token, "token");
        token.throwIfCancellationRequested();

        Path environmentRoot = workRoot.resolve("environment");
        Path volumeRoot = workRoot.resolve("volume-work");
        Files.createDirectories(volumeRoot);

        GeneratedEnvironment generatedEnvironment = environment.generateEnvironment(
                request, draftIntent, baseline, environmentRoot, budget, token);
        WorldBlueprintV2 blueprint = generatedEnvironment.blueprint();
        TerrainQuery base = generatedEnvironment.baseTerrain();
        requireIdentityCell(base, request.bounds().minY());

        VolumeSdfPrimitivePlanV2 sdf = identitySdf(request.bounds().minY());
        Path sdfPath = volumeRoot.resolve("sdf-primitive-plan.json");
        data.writeVolumeSdfPrimitivePlan(sdfPath, sdf);

        VolumeCsgPlanV2 csg = identityCsg(sdf);
        Path csgPath = volumeRoot.resolve("csg-plan.json");
        data.writeVolumeCsgPlan(csgPath, csg);

        VolumeAabbIndexPlanV2 aabb = data.sealVolumeAabbIndexPlan(
                VolumeAabbIndexBuilderV2.buildDraft(csg, sdf, 0, 0));
        Path aabbPath = volumeRoot.resolve("aabb-index-plan.json");
        data.writeVolumeAabbIndexPlan(aabbPath, aabb);

        VolumeValidationReportV2 report = validator.validate(
                new VolumeValidationInputV2(
                        Math.min(request.bounds().width(), 256),
                        Math.min(request.bounds().length(), 256),
                        csg.canonicalChecksum(),
                        List::of),
                token);
        if (!report.passesHardValidation()) {
            throw new IOException("shared sparse-volume HARD validation failed: " + report.issues());
        }
        Path validationPath = volumeRoot.resolve("validation.json");
        new VolumeValidationArtifactCodecV2().write(
                validationPath, validator.toArtifact(csg.canonicalChecksum(), report));

        VolumeTerrainQueryV2 volumeQuery = new VolumeTerrainQueryV2(base, csg, sdf);
        TerrainBlockResolver blockResolver = preservingResolver(base, volumeQuery);
        List<SparseVolumeReleaseSourceV2.TileSource> tiles = writeVolumeTiles(
                volumeRoot.resolve("tiles"),
                generatedEnvironment.source().hydrology().surface().tiles(),
                blueprint,
                blockResolver,
                token);

        SparseVolumeReleaseSourceV2 source = new SparseVolumeReleaseSourceV2(
                generatedEnvironment.source(), sdfPath, csgPath, aabbPath, validationPath, tiles);
        return new GeneratedSparseVolume(source, blueprint);
    }

    private VolumeSdfPrimitivePlanV2 identitySdf(int minY) {
        long cellCenterY = Math.addExact(Math.multiplyExact((long) minY, MILLIONTHS), MILLIONTHS / 2L);
        VolumeSdfPrimitiveV2 primitive = new VolumeSdfPrimitiveV2.Sphere(
                IDENTITY_PRIMITIVE_ID,
                new VolumeSdfVec3V2(MILLIONTHS / 2L, cellCenterY, MILLIONTHS / 2L),
                MILLIONTHS / 4L);
        return data.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                VolumeSdfPrimitivePlanV2.VERSION,
                VolumeSdfPrimitivePlanV2.PRIMITIVE_CONTRACT_VERSION,
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                List.of(primitive),
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        VolumeSdfPrimitivePlanV2.ResourceBudget.VERSION,
                        1,
                        VolumeSdfPrimitivePlanV2.MAXIMUM_SWEPT_CONTROL_POINTS,
                        4_096L,
                        VolumeSdfPrimitivePlanV2.MAX_CANONICAL_BYTES,
                        64L * 1024L),
                "0".repeat(64)));
    }

    private VolumeCsgPlanV2 identityCsg(VolumeSdfPrimitivePlanV2 sdf) {
        VolumeCsgPlanV2.Operator operator = new VolumeCsgPlanV2.Operator(
                IDENTITY_OPERATOR_ID,
                0,
                VolumeCsgPlanV2.OperationKind.ADD_FLUID,
                IDENTITY_PRIMITIVE_ID,
                VolumeCsgPlanV2.MaskMode.NONE,
                "",
                List.of(),
                IDENTITY_FLUID_ID);
        return data.sealVolumeCsgPlan(new VolumeCsgPlanV2(
                VolumeCsgPlanV2.VERSION,
                VolumeCsgPlanV2.CSG_CONTRACT_VERSION,
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        VolumeCsgPlanV2.PrimitivePlanBinding.VERSION,
                        sdf.canonicalChecksum(),
                        VolumeCsgPlanV2.PrimitivePlanBinding.CONTRACT_VERSION),
                VolumeCsgPlanV2.Kernel.standard(),
                List.of(operator),
                new VolumeCsgPlanV2.ResourceBudget(
                        VolumeCsgPlanV2.ResourceBudget.VERSION,
                        1,
                        1,
                        1_024L,
                        VolumeCsgPlanV2.Kernel.MAXIMUM_CPU_WORK_UNITS,
                        4_096L,
                        VolumeCsgPlanV2.MAX_CANONICAL_BYTES,
                        64L * 1024L),
                "0".repeat(64)));
    }

    private List<SparseVolumeReleaseSourceV2.TileSource> writeVolumeTiles(
            Path root,
            List<com.github.nankotsu029.landformcraft.format.v2.release.SurfaceReleaseSourceV2.TileSource>
                    surfaceTiles,
            WorldBlueprintV2 blueprint,
            TerrainBlockResolver resolver,
            CancellationToken token
    ) throws IOException {
        Files.createDirectories(root);
        List<com.github.nankotsu029.landformcraft.format.v2.release.SurfaceReleaseSourceV2.TileSource> stable =
                surfaceTiles.stream().sorted(Comparator.comparing(
                        com.github.nankotsu029.landformcraft.format.v2.release.SurfaceReleaseSourceV2.TileSource
                                ::tileId)).toList();
        List<SparseVolumeReleaseSourceV2.TileSource> result = new ArrayList<>(stable.size());
        for (var surface : stable) {
            token.throwIfCancellationRequested();
            OfflineTileArtifactV2 surfaceArtifact = tileCodec.read(surface.metadata());
            Path schematic = root.resolve(surfaceArtifact.tilePlan().defaultSchematicFileName());
            OfflineTileArtifactV2 volumeArtifact = tileWriter.write(
                    schematic,
                    surfaceArtifact.tilePlan(),
                    blueprint.canonicalChecksum(),
                    resolver,
                    token);
            Path metadata = root.resolve(volumeArtifact.tileId() + ".json");
            tileCodec.write(metadata, volumeArtifact);
            result.add(new SparseVolumeReleaseSourceV2.TileSource(
                    volumeArtifact.tileId(), metadata, schematic));
        }
        return List.copyOf(result);
    }

    private static TerrainBlockResolver preservingResolver(
            TerrainQuery base,
            VolumeTerrainQueryV2 volume
    ) throws IOException {
        if (!(base instanceof CoastalSurfaceTerrainQueryV2 coastal)) {
            throw new IOException("shared sparse-volume pipeline requires the coastal base resolver");
        }
        TerrainBlockResolver volumeResolver = new VolumeTileBlockResolverV2(volume);
        return (x, y, z) -> {
            if (volume.blockClassAt(x, y, z) == base.blockClassAt(x, y, z)
                    && volume.semanticMaterialAt(x, y, z) == base.semanticMaterialAt(x, y, z)
                    && volume.fluidBodyAt(x, y, z) == base.fluidBodyAt(x, y, z)) {
                return coastal.blockResolver().blockStateAt(x, y, z);
            }
            return volumeResolver.blockStateAt(x, y, z);
        };
    }

    private static void requireIdentityCell(TerrainQuery base, int minY) throws IOException {
        if (base.blockClassAt(0, minY, 0) != TerrainQuery.BlockClass.SOLID
                || base.semanticMaterialAt(0, minY, 0) != TerrainQuery.SemanticMaterial.BEDROCK) {
            throw new IOException("shared sparse-volume identity operator requires the canonical bedrock floor");
        }
    }
}
