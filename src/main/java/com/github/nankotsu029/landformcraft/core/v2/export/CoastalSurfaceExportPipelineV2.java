package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.format.v2.release.SurfaceReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.CoastalValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.CoastalPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticFieldFactoryV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticPreviewRendererV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationInputV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationReportV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidatorV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Production generation stage of the Release 2 export path (V2-12-02).
 *
 * <p>Turns a sealed {@code GenerationRequestV2} plus a design-stage {@code TerrainIntentV2} into
 * the complete sealed input set of one {@code surface-2_5d} Release: constraint field sidecars,
 * frozen Blueprint, field-only coastal validation, the fixed diagnostic preview set, and the
 * offline Sponge v3 tiles. It publishes nothing; {@link Release2ExportApplicationServiceV2} owns
 * staging, strict read-back, and atomic publish.</p>
 *
 * <p>Tile geometry comes from {@link TilePlanV2}, so a published Release opens directly through the
 * V2-6-20 verified canonical block source without any re-tiling.</p>
 */
final class CoastalSurfaceExportPipelineV2 implements ProductionExportPipelineV2 {
    static final String PIPELINE_ID = "v2.production.surface-2_5d.coastal";
    static final String GENERATOR_HANDLER_ID = "v2.coast.surface-generator";
    static final String VALIDATOR_HANDLER_ID = "v2.coast.field-validator";
    static final String PREVIEW_HANDLER_ID = "v2.coast.diagnostic-preview";
    static final String EXPORT_HANDLER_ID = "v2.release.surface-2_5d-export";
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
            List.of("surface-2_5d"));

    private final LandformV2DataCodec data = new LandformV2DataCodec();
    private final ConstraintFieldIndexCodecV2 indexCodec = new ConstraintFieldIndexCodecV2();

    @Override
    public PipelineDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public GeneratedSurface generate(
            GenerationRequestV2 request,
            TerrainIntentV2 draftIntent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        return generateWithFields(request, draftIntent, baseline, workRoot, budget, token).surface();
    }

    /**
     * Same coastal generation as {@link #generate}, also retaining the release-local fields so a
     * hydrology overlay can sample provisional surface height without regenerating the coast.
     */
    GeneratedCoastalSurface generateWithFields(
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
        token.throwIfCancellationRequested();
        if (!request.requestId().equals(draftIntent.intentId())) {
            throw new IOException("terrain intent does not belong to the generation request");
        }
        GenerationRequestV2.Bounds bounds = request.bounds();
        baseline.requireWithin(bounds.minY(), bounds.maxY());
        int width = bounds.width();
        int length = bounds.length();
        ScaleClassV2 scale = ScaleClassV2.forDimensions(width, length);
        if (scale == ScaleClassV2.LARGE) {
            throw new IOException("LARGE Release 2 export is not supported until the V2-8 streaming gates close");
        }
        ScaleProfileV2 profile = ScaleProfileV2.defaults(scale);
        TilePlanV2 tilePlan = TilePlanV2.of(width, length, profile);
        budget.requireAdmitted(width, length, tilePlan.tileCount(), profile);

        Files.createDirectories(workRoot);
        Path requestPath = workRoot.resolve("generation-request.json");
        data.writeGenerationRequest(requestPath, request);
        String requestChecksum = data.generationRequestChecksum(request);

        WorldBlueprintV2 draftBlueprint = compile(request, draftIntent, requestChecksum, bounds);
        CoastalGeneratorRuntimeV2 runtime = CoastalGeneratorRuntimeV2.create(draftBlueprint);
        CoastalSurfaceFieldsV2 fields = CoastalSurfaceFieldsV2.generate(
                runtime, width, length, baseline, token);
        if (fields.hardProtectedCells() < 1) {
            throw new IOException("coastal export produced no HARD-protected land-water cells");
        }

        Path constraintRoot = workRoot.resolve("constraints");
        List<FieldArtifactDescriptorV2> descriptors = writeConstraintFields(
                constraintRoot, request, fields, width, length, token);
        FieldArtifactDescriptorV2 desired = descriptorFor(
                descriptors, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER);

        TerrainIntentV2 intent = withLandWaterBinding(draftIntent, desired.semanticChecksum());
        Path intentPath = workRoot.resolve("terrain-intent.json");
        data.writeTerrainIntent(intentPath, intent);
        String intentChecksum = data.terrainIntentChecksum(intent);

        WorldBlueprintV2 blueprint = compile(request, intent, requestChecksum, bounds);
        requireSameCoastalGeometry(draftBlueprint, blueprint);
        Path blueprintPath = workRoot.resolve("world-blueprint.json");
        data.writeWorldBlueprint(blueprintPath, blueprint);

        ConstraintFieldIndexV2 index = new ConstraintFieldIndexV2(
                1, request.requestId(), requestChecksum, intentChecksum,
                List.of(landWaterBinding(intent, desired, descriptors)), descriptors);
        Path indexPath = constraintRoot.resolve("index.json");
        indexCodec.write(indexPath, index);
        indexCodec.readAndVerify(indexPath, constraintRoot, requestChecksum, intentChecksum, token);

        CoastalValidationInputV2 validationInput = new CoastalValidationInputV2(blueprint, fields, fields);
        CoastalValidationReportV2 report = new CoastalValidatorV2().validate(validationInput, token);
        if (!report.passesHardValidation()) {
            throw new IOException("coastal HARD validation failed: " + report.issues());
        }
        Path validationPath = workRoot.resolve("coastal-validation.json");
        new CoastalValidationArtifactCodecV2().write(validationPath, new CoastalValidationArtifactV2(
                blueprint.canonicalChecksum(), CoastalValidationArtifactV2.VALIDATOR_VERSION,
                new CoastalValidationArtifactV2.CoastalValidationReport(report.metrics(), report.issues())));

        Path previewRoot = workRoot.resolve("previews");
        CoastalPreviewIndexV2 previews = new CoastalDiagnosticPreviewRendererV2().render(
                previewRoot, blueprint.canonicalChecksum(),
                CoastalDiagnosticFieldFactoryV2.create(validationInput, report), token);

        List<SurfaceReleaseSourceV2.TileSource> tiles = writeTiles(
                workRoot.resolve("tiles"), tilePlan, blueprint,
                fields.resolver(bounds.minY(), bounds.waterLevel()),
                bounds.minY(), bounds.maxY(), token);

        SurfaceReleaseSourceV2 source = new SurfaceReleaseSourceV2(
                requestPath, intentPath, blueprintPath, indexPath, constraintRoot,
                validationPath, previewRoot.resolve("index.json"), previewRoot, tiles);
        return new GeneratedCoastalSurface(new GeneratedSurface(source, blueprint), fields);
    }

    record GeneratedCoastalSurface(GeneratedSurface surface, CoastalSurfaceFieldsV2 fields) {
        GeneratedCoastalSurface {
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(fields, "fields");
        }
    }

    private WorldBlueprintV2 compile(
            GenerationRequestV2 request,
            TerrainIntentV2 intent,
            String requestChecksum,
            GenerationRequestV2.Bounds bounds
    ) {
        return new DiagnosticBlueprintCompilerV2().compile(new DiagnosticCompileRequestV2(
                request.requestId(),
                new GenerationBounds(bounds.width(), bounds.length(), bounds.minY(), bounds.maxY(),
                        bounds.waterLevel()),
                request.generation().tileSize(),
                request.generation().globalSeed(),
                requestChecksum,
                DiagnosticCompileRequestV2.defaultBudget()), intent);
    }

    /**
     * The land-water binding is rebound to the generated field artifact, so the sealed Blueprint is
     * recompiled. Any change to the coastal geometry would silently invalidate the already sampled
     * fields, so it fails closed instead.
     */
    private static void requireSameCoastalGeometry(WorldBlueprintV2 draft, WorldBlueprintV2 sealed) {
        if (!draft.coastalTransitionPlans().equals(sealed.coastalTransitionPlans())
                || !draft.coastalFeaturePlans().equals(sealed.coastalFeaturePlans())
                || !draft.sandyBeachPlans().equals(sealed.sandyBeachPlans())
                || !draft.harborBasinPlans().equals(sealed.harborBasinPlans())
                || !draft.breakwaterHarborPlans().equals(sealed.breakwaterHarborPlans())
                || !draft.rockyCapePlans().equals(sealed.rockyCapePlans())) {
            throw new IllegalStateException("constraint rebinding changed the sealed coastal geometry");
        }
    }

    private static TerrainIntentV2 withLandWaterBinding(TerrainIntentV2 intent, String semanticChecksum) {
        List<TerrainIntentV2.ConstraintMapBinding> bindings = intent.mapReferences();
        if (bindings.size() != 1
                || bindings.getFirst().role() != TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK) {
            throw new IllegalArgumentException(
                    "surface-2_5d export requires exactly one LAND_WATER_MASK map binding");
        }
        TerrainIntentV2.ConstraintMapBinding binding = bindings.getFirst();
        TerrainIntentV2.ConstraintMapBinding bound = new TerrainIntentV2.ConstraintMapBinding(
                binding.id(),
                binding.sourceId(),
                binding.role(),
                "constraint:land-water:sha256-" + semanticChecksum,
                binding.strength(),
                binding.sampling(),
                binding.toleranceBlocks(),
                binding.weightMillionths());
        return new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                intent.features(), intent.relations(), intent.constraints(), intent.environment(),
                List.of(bound), intent.structures(), intent.provenance());
    }

    private static ConstraintFieldIndexV2.AppliedBinding landWaterBinding(
            TerrainIntentV2 intent,
            FieldArtifactDescriptorV2 desired,
            List<FieldArtifactDescriptorV2> descriptors
    ) {
        TerrainIntentV2.ConstraintMapBinding binding = intent.mapReferences().getFirst();
        return new ConstraintFieldIndexV2.AppliedBinding(
                binding.id(), binding.sourceId(), binding.role(), binding.strength(), binding.sampling(),
                binding.toleranceBlocks(), binding.weightMillionths(), binding.artifactId(),
                desired.definition().fieldId(),
                descriptors.stream().map(field -> field.definition().fieldId()).toList(),
                List.of(new ConstraintFieldIndexV2.LabelEntry(0, 0, "water"),
                        new ConstraintFieldIndexV2.LabelEntry(1, 1, "land")));
    }

    private static List<FieldArtifactDescriptorV2> writeConstraintFields(
            Path root,
            GenerationRequestV2 request,
            CoastalSurfaceFieldsV2 fields,
            int width,
            int length,
            CancellationToken token
    ) throws IOException {
        if (request.constraintMaps().size() != 1) {
            throw new IOException("surface-2_5d export requires exactly one declared constraint map source");
        }
        GenerationRequestV2.ConstraintMapSource map = request.constraintMaps().getFirst();
        FieldArtifactDescriptorV2.Provenance provenance = new FieldArtifactDescriptorV2.Provenance(
                FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP,
                map.sourceId(), map.expectedSha256(), "numeric-png", "1", "pixel-center-v1");
        LfcGridWriterV1 writer = new LfcGridWriterV1();
        List<FieldArtifactDescriptorV2> result = new ArrayList<>(3);
        result.add(writer.write(root, "fields/coast-land-desired.lfgrid",
                definition("constraint.coast.land.desired",
                        FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldValueType.U8, 1_000_000L, width, length),
                provenance, (x, z) -> fields.landWaterAt(z * width + x), token));
        result.add(writer.write(root, "fields/coast-land-actual.lfgrid",
                definition("constraint.coast.land.actual",
                        FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldValueType.U8, 1_000_000L, width, length),
                provenance, (x, z) -> fields.landWaterAt(z * width + x), token));
        result.add(writer.write(root, "fields/coast-land-residual.lfgrid",
                definition("constraint.coast.land.residual",
                        FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldValueType.I32, 1L, width, length),
                provenance, (x, z) -> 0, token));
        return List.copyOf(result);
    }

    private static FieldArtifactDescriptorV2.Definition definition(
            String id,
            FieldArtifactDescriptorV2.FieldSemantic semantic,
            FieldArtifactDescriptorV2.FieldValueType type,
            long scale,
            int width,
            int length
    ) {
        return new FieldArtifactDescriptorV2.Definition(
                id, semantic, type, width, length,
                FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                FieldArtifactDescriptorV2.Sampling.NEAREST, scale, 0L, false, 0);
    }

    private static FieldArtifactDescriptorV2 descriptorFor(
            List<FieldArtifactDescriptorV2> descriptors,
            FieldArtifactDescriptorV2.FieldSemantic semantic
    ) {
        return descriptors.stream()
                .filter(descriptor -> descriptor.definition().semantic() == semantic)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing constraint field " + semantic));
    }

    private static List<SurfaceReleaseSourceV2.TileSource> writeTiles(
            Path root,
            TilePlanV2 tilePlan,
            WorldBlueprintV2 blueprint,
            TerrainBlockResolver resolver,
            int minY,
            int maxY,
            CancellationToken token
    ) throws IOException {
        Files.createDirectories(root);
        OfflineTileArtifactCodecV2 codec = new OfflineTileArtifactCodecV2();
        OfflineTileSchematicWriterV2 schematicWriter = new OfflineTileSchematicWriterV2();
        List<SurfaceReleaseSourceV2.TileSource> tiles = new ArrayList<>(tilePlan.tileCount());
        for (int index = 0; index < tilePlan.tileCount(); index++) {
            token.throwIfCancellationRequested();
            TilePlanV2.TileV2 tile = tilePlan.tileByIndex(index);
            OfflineTilePlanV2 plan = new OfflineTilePlanV2(
                    OfflineTilePlanV2.VERSION, tile.tileId(), tile.tileX(), tile.tileZ(),
                    tile.coreMinX(), tile.coreMinZ(), tile.coreWidth(), tile.coreLength(), minY, maxY);
            Path schematic = root.resolve(plan.defaultSchematicFileName());
            OfflineTileArtifactV2 artifact = schematicWriter.write(
                    schematic, plan, blueprint.canonicalChecksum(), resolver, token);
            Path metadata = root.resolve(tile.tileId() + ".json");
            codec.write(metadata, artifact);
            tiles.add(new SurfaceReleaseSourceV2.TileSource(artifact.tileId(), metadata, schematic));
        }
        return List.copyOf(tiles);
    }
}
