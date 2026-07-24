package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationPlainHillSliceCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.material.SurfaceMaterializationV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.format.v2.release.SurfaceReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.CoastalValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.hill.HillRangeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.plain.PlainGeneratorV2;
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
import com.github.nankotsu029.landformcraft.model.v2.foundation.HillRangePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
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
import java.util.Optional;

/**
 * Offline adapter that packages V2-9／V2-10 foundation 2.5D merge output into the existing
 * {@code surface-2_5d} Release source set (V2-15-09, ADR 0037).
 *
 * <p>It does not register as a second {@code ["surface-2_5d"]} production pipeline and does not
 * promote foundation FeatureKinds. Publish／strict verify remain with
 * {@link Release2FoundationSurfaceExportApplicationServiceV2}.</p>
 */
public final class FoundationSurfaceExportAdapterV2 {
    public static final String ADAPTER_ID = "v2.production.surface-2_5d.foundation-adapter";

    private final LandformV2DataCodec data = new LandformV2DataCodec();
    private final ConstraintFieldIndexCodecV2 indexCodec = new ConstraintFieldIndexCodecV2();
    private final FoundationPlainHillSliceCompilerV2 plainHillCompiler = new FoundationPlainHillSliceCompilerV2();
    private final SurfaceFoundationOwnerGateV2 ownerGate = new SurfaceFoundationOwnerGateV2();

    public ProductionExportPipelineV2.GeneratedSurface generatePlainHill(
            GenerationRequestV2 request,
            TerrainIntentV2 draftIntent,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(draftIntent, "draftIntent");
        Objects.requireNonNull(workRoot, "workRoot");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(token, "token");
        token.throwIfCancellationRequested();
        if (!request.requestId().equals(draftIntent.intentId())) {
            throw new IOException("terrain intent does not belong to the generation request");
        }
        requireFoundationSurfaceKinds(draftIntent);

        GenerationRequestV2.Bounds bounds = request.bounds();
        int width = bounds.width();
        int length = bounds.length();
        ScaleClassV2 scale = ScaleClassV2.forDimensions(width, length);
        if (scale == ScaleClassV2.LARGE) {
            throw new IOException("LARGE Release 2 export is not supported until the V2-8 streaming gates close");
        }
        ScaleProfileV2 profile = ScaleProfileV2.defaults(scale);
        TilePlanV2 tilePlan = TilePlanV2.of(width, length, profile);
        budget.requireAdmitted(width, length, tilePlan.tileCount(), profile);

        WorldBlueprintV2.Bounds foundationBounds = new WorldBlueprintV2.Bounds(
                width, length, bounds.minY(), bounds.maxY(), bounds.waterLevel());
        FoundationPlainHillSliceCompilerV2.FoundationPlainHillSliceV2 slice =
                plainHillCompiler.compile(draftIntent, foundationBounds, request.generation().globalSeed());
        SurfaceFoundationMergeCompilerV2 merge = mergeFromSlice(slice);
        MapChecksums.requireWholeEqualsTiled(merge, tilePlan);

        Files.createDirectories(workRoot);
        Path requestPath = workRoot.resolve("generation-request.json");
        data.writeGenerationRequest(requestPath, request);
        String requestChecksum = data.generationRequestChecksum(request);

        FoundationSurfaceFieldsV2 fields = FoundationSurfaceFieldsV2.fromMerge(
                merge, width, length, bounds.minY(), bounds.maxY(), bounds.waterLevel());
        // V2-18-10 (ADR 0038 D7-2): the same fail-closed surface foundation owner gate as the coastal
        // spine, over this path's own merge owners (plain/hill are foundation-eligible, ADR 0038 D4).
        // The merge kernel already rejects an ownerless cell, so this restates the requirement with
        // the shared rule id instead of trusting one code path's invariant.
        ownerGate.requireFullCoverage(ADAPTER_ID, fields.foundationOwnerCells(),
                Math.multiplyExact(width, length),
                "the plain/hill foundation merge left cells of the request domain without an owner");

        Path constraintRoot = workRoot.resolve("constraints");
        List<FieldArtifactDescriptorV2> descriptors = writeConstraintFields(
                constraintRoot, request, fields, width, length, token);
        FieldArtifactDescriptorV2 desired = descriptorFor(
                descriptors, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER);

        // V2-18-07: bind the land-water desired reference to the declared INPUT mask digest instead of the
        // generated field's own checksum, so the provenance is not a self-reference. See the coastal
        // pipeline for the full rationale; mask resolution into the field remains V2-18-09.
        String maskDigest = request.constraintMaps().getFirst().expectedSha256();
        TerrainIntentV2 intent = withLandWaterBinding(draftIntent, maskDigest);
        Path intentPath = workRoot.resolve("terrain-intent.json");
        data.writeTerrainIntent(intentPath, intent);
        String intentChecksum = data.terrainIntentChecksum(intent);

        WorldBlueprintV2 blueprint = compile(request, intent, requestChecksum, bounds);
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
            throw new IOException("foundation-projected coastal HARD validation failed: " + report.issues());
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
                // V2-19-10: the ADR 0037 adapter shares the closed surface role catalog but binds no
                // environment material — it publishes no environment plans to bind one from.
                fields.resolver(bounds.minY(), bounds.waterLevel(), SurfaceMaterializationV2.builtIn()),
                bounds.minY(), bounds.maxY(), token);

        SurfaceReleaseSourceV2 source = new SurfaceReleaseSourceV2(
                requestPath, intentPath, blueprintPath, indexPath, constraintRoot,
                validationPath, previewRoot.resolve("index.json"), previewRoot, tiles);
        return new ProductionExportPipelineV2.GeneratedSurface(
                source, blueprint, Optional.empty(), Optional.empty(), List.of());
    }

    private static void requireFoundationSurfaceKinds(TerrainIntentV2 intent) throws IOException {
        boolean plain = intent.features().stream()
                .anyMatch(feature -> feature.kind() == TerrainIntentV2.FeatureKind.PLAIN);
        boolean hill = intent.features().stream()
                .anyMatch(feature -> feature.kind() == TerrainIntentV2.FeatureKind.HILL_RANGE);
        if (!plain && !hill) {
            throw new IOException("foundation surface adapter requires PLAIN and/or HILL_RANGE features");
        }
        for (TerrainIntentV2.Feature feature : intent.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.PLAIN
                    && feature.kind() != TerrainIntentV2.FeatureKind.HILL_RANGE) {
                throw new IOException("foundation surface adapter rejects non plain/hill feature: "
                        + feature.kind());
            }
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

    private static TerrainIntentV2 withLandWaterBinding(TerrainIntentV2 intent, String maskDigest) {
        List<TerrainIntentV2.ConstraintMapBinding> existing = intent.mapReferences();
        TerrainIntentV2.ConstraintMapBinding template;
        if (existing.isEmpty()) {
            template = new TerrainIntentV2.ConstraintMapBinding(
                    "foundation-land-water",
                    "constraint-source:foundation-mask",
                    TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                    "constraint:land-water:sha256-" + "0".repeat(64),
                    TerrainIntentV2.Strength.HARD,
                    TerrainIntentV2.Sampling.NEAREST,
                    0,
                    0);
        } else if (existing.size() == 1
                && existing.getFirst().role() == TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK) {
            template = existing.getFirst();
        } else {
            throw new IllegalArgumentException(
                    "foundation surface export requires zero or one LAND_WATER_MASK map binding");
        }
        TerrainIntentV2.ConstraintMapBinding bound = new TerrainIntentV2.ConstraintMapBinding(
                template.id(),
                template.sourceId(),
                template.role(),
                "constraint:land-water:sha256-" + maskDigest,
                template.strength(),
                template.sampling(),
                template.toleranceBlocks(),
                template.weightMillionths());
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
        // V2-18-07: the field index keeps referencing the DESIRED field by its own checksum (integrity),
        // while the intent binding's artifactId now records the input mask provenance. See the coastal
        // pipeline for the rationale.
        String canonicalArtifactId = "constraint:land-water:sha256-" + desired.semanticChecksum();
        return new ConstraintFieldIndexV2.AppliedBinding(
                binding.id(), binding.sourceId(), binding.role(), binding.strength(), binding.sampling(),
                binding.toleranceBlocks(), binding.weightMillionths(), canonicalArtifactId,
                desired.definition().fieldId(),
                descriptors.stream().map(field -> field.definition().fieldId()).toList(),
                List.of(new ConstraintFieldIndexV2.LabelEntry(0, 0, "water"),
                        new ConstraintFieldIndexV2.LabelEntry(1, 1, "land")));
    }

    private static List<FieldArtifactDescriptorV2> writeConstraintFields(
            Path root,
            GenerationRequestV2 request,
            FoundationSurfaceFieldsV2 fields,
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
        result.add(writer.write(root, "fields/foundation-land-desired.lfgrid",
                definition("constraint.foundation.land.desired",
                        FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldValueType.U8, 1_000_000L, width, length),
                provenance, (x, z) -> fields.landWaterAt(z * width + x), token));
        result.add(writer.write(root, "fields/foundation-land-actual.lfgrid",
                definition("constraint.foundation.land.actual",
                        FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldValueType.U8, 1_000_000L, width, length),
                provenance, (x, z) -> fields.landWaterAt(z * width + x), token));
        result.add(writer.write(root, "fields/foundation-land-residual.lfgrid",
                definition("constraint.foundation.land.residual",
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

    static SurfaceFoundationMergeCompilerV2 mergeFromSlice(
            FoundationPlainHillSliceCompilerV2.FoundationPlainHillSliceV2 slice
    ) {
        SurfaceFoundationPlanV2 plan = slice.foundation();
        List<SurfaceFoundationMergeCompilerV2.OwnerLayer> layers = new ArrayList<>();
        PlainPlanV2 plain = slice.plain();
        HillRangePlanV2 hill = slice.hill();
        for (SurfaceFoundationPlanV2.OwnerDescriptor owner : plan.owners()) {
            if (plain != null && owner.ownerId().equals(plain.featureId())) {
                layers.add(new PlainGeneratorV2(plain).toOwnerLayer(owner));
            } else if (hill != null && owner.ownerId().equals(hill.featureId())) {
                layers.add(new HillRangeGeneratorV2(hill).toOwnerLayer(owner));
            } else {
                throw new IllegalStateException("foundation owner has no matching plain/hill plan: "
                        + owner.ownerId());
            }
        }
        return new SurfaceFoundationMergeCompilerV2(plan, layers);
    }

    /** Local helper so whole／tile checksum agreement fails closed before artifact write. */
    private static final class MapChecksums {
        private MapChecksums() {
        }

        static void requireWholeEqualsTiled(SurfaceFoundationMergeCompilerV2 merge, TilePlanV2 tiles)
                throws IOException {
            if (!merge.wholeFieldChecksums().equals(merge.tiledFieldChecksums(tiles))) {
                throw new IOException("foundation whole and tiled merge checksums differ");
            }
        }
    }
}
