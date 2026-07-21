package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.field.FieldValueSource;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.CoastalValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.CoastalValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticFieldsV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticPreviewRendererV2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline {@code surface-2_5d} Release builder for V2-11-04／V2-11-05 Paper re-measurement.
 *
 * <p>Produces a solid-only shallow column so the effect envelope stays inside the settle／verify
 * slice budget without fluid／gravity expansion. Tile geometry matches
 * {@link TilePlanV2#of(int, int, ScaleProfileV2)} for the declared scale profile.</p>
 */
public final class MeasurementSurfaceFixtureV2 {
    private static final LandformV2DataCodec DATA = new LandformV2DataCodec();

    private MeasurementSurfaceFixtureV2() {
    }

    public record Fixture(SurfaceReleaseSourceV2 source, WorldBlueprintV2 blueprint, TilePlanV2 tilePlan) {
    }

    public static Fixture build500(Path root) throws Exception {
        return build(root, "v2-11-04-measure-500", 500, 500, 0, 1);
    }

    public static Fixture build1000(Path root) throws Exception {
        return build(root, "v2-11-05-measure-1000", 1000, 1000, 0, 1);
    }

    /**
     * MEDIUM 1024×1024 solid-only Release for the V2-13-04 FAWE placement re-measurement. The
     * horizontal ceiling matches {@code ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING} (1024) and
     * the cell count equals {@code MEDIUM_MAXIMUM_CELLS} (1024²), the offline budget proven E2E by
     * V2-13-03. Tile geometry reuses the same 128-block MEDIUM tiling as {@link #build1000(Path)}.
     */
    public static Fixture build1024(Path root) throws Exception {
        return build(root, "v2-13-04-measure-1024", 1024, 1024, 0, 1);
    }

    public static Fixture build(
            Path root,
            String requestId,
            int width,
            int length,
            int minY,
            int maxY
    ) throws Exception {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("measurement fixture requestId is required");
        }
        if (width < 1 || length < 1 || minY > maxY) {
            throw new IllegalArgumentException("invalid measurement fixture dimensions");
        }
        Files.createDirectories(root);
        ScaleClassV2 scale = ScaleClassV2.forDimensions(width, length);
        ScaleProfileV2 profile = ScaleProfileV2.defaults(scale);
        TilePlanV2 tilePlan = TilePlanV2.of(width, length, profile);
        if (tilePlan.tileCount() > profile.maximumTileCount()) {
            throw new IllegalArgumentException(
                    "measurement fixture tile count exceeds scale profile budget: "
                            + tilePlan.tileCount() + " > " + profile.maximumTileCount());
        }

        // Height encoding uses BLOCKS_ABOVE_REQUEST_MIN_Y with valueScaleMillionths=1000, so the
        // sample ceiling must keep decoded heights inside [minY, maxY] (millionths).
        int heightSampleMax = Math.max(1, Math.multiplyExact(Math.subtractExact(maxY, minY), 1_000));
        String requestJson = Files.readString(Path.of("examples/v2/manual-constraint-island/request-v2.json"))
                .replace("\"manual-constraint-island\"", "\"" + requestId + "\"")
                .replace("\"width\": 4", "\"width\": " + width)
                .replace("\"length\": 4", "\"length\": " + length)
                .replace("\"minY\": 0", "\"minY\": " + minY)
                .replace("\"maxY\": 100", "\"maxY\": " + maxY)
                .replace("\"waterLevel\": 50", "\"waterLevel\": " + minY)
                .replace("\"expectedWidth\": 4", "\"expectedWidth\": " + width)
                .replace("\"expectedLength\": 4", "\"expectedLength\": " + length)
                .replace("\"width\": 4,", "\"width\": " + width + ",")
                .replace("\"length\": 4 }", "\"length\": " + length + " }")
                .replace("\"maximum\": 50000", "\"maximum\": " + heightSampleMax)
                .replace("\"tileSize\": 32", "\"tileSize\": 128");
        var request = DATA.readGenerationRequest(requestJson, "measurement-request");
        Path requestPath = root.resolve("request.json");
        DATA.writeGenerationRequest(requestPath, request);
        String requestChecksum = DATA.generationRequestChecksum(request);

        Path fieldRoot = root.resolve("constraints");
        List<FieldArtifactDescriptorV2> fields = writeFields(fieldRoot, width, length);
        String sourceIntent = Files.readString(Path.of("examples/v2/manual-constraint-island/terrain-intent-v2.json"))
                .replace("\"manual-constraint-island\"", "\"" + requestId + "\"");
        String landChecksum = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER).semanticChecksum();
        String heightChecksum = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT).semanticChecksum();
        String zoneChecksum = field(fields, FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP).semanticChecksum();
        TerrainIntentV2 intent = DATA.readTerrainIntent(sourceIntent
                .replace("a".repeat(64), landChecksum)
                .replace("b".repeat(64), heightChecksum)
                .replace("c".repeat(64), zoneChecksum), "measurement-fixture-intent");
        Path intentPath = root.resolve("intent.json");
        DATA.writeTerrainIntent(intentPath, intent);
        String intentChecksum = DATA.terrainIntentChecksum(intent);

        ConstraintFieldIndexV2 index = new ConstraintFieldIndexV2(
                1, request.requestId(), requestChecksum, intentChecksum, bindings(fields), fields);
        Path indexPath = fieldRoot.resolve("index.json");
        new ConstraintFieldIndexCodecV2().write(indexPath, index);

        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(new DiagnosticCompileRequestV2(
                request.requestId(),
                new GenerationBounds(width, length, minY, maxY, minY),
                profile.tileSizeBlocks(),
                request.generation().globalSeed(),
                requestChecksum,
                DiagnosticCompileRequestV2.defaultBudget()), intent);
        Path blueprintPath = root.resolve("blueprint.json");
        DATA.writeWorldBlueprint(blueprintPath, blueprint);

        Path validationPath = root.resolve("coastal-validation.json");
        new CoastalValidationArtifactCodecV2().write(validationPath, new CoastalValidationArtifactV2(
                blueprint.canonicalChecksum(), "coastal-validator-v1",
                new CoastalValidationArtifactV2.CoastalValidationReport(List.of(), List.of())));

        Path previewRoot = root.resolve("previews");
        new CoastalDiagnosticPreviewRendererV2().render(
                previewRoot, blueprint.canonicalChecksum(), diagnosticFields(width, length, minY, maxY),
                () -> false);

        Path tileRoot = root.resolve("tiles");
        Files.createDirectories(tileRoot);
        List<SurfaceReleaseSourceV2.TileSource> tiles = new ArrayList<>(tilePlan.tileCount());
        TerrainBlockResolver resolver = solidResolver(minY, maxY);
        OfflineTileArtifactCodecV2 tileCodec = new OfflineTileArtifactCodecV2();
        OfflineTileSchematicWriterV2 schematicWriter = new OfflineTileSchematicWriterV2();
        for (int i = 0; i < tilePlan.tileCount(); i++) {
            TilePlanV2.TileV2 tile = tilePlan.tileByIndex(i);
            OfflineTilePlanV2 plan = new OfflineTilePlanV2(
                    OfflineTilePlanV2.VERSION,
                    tile.tileId(),
                    tile.tileX(),
                    tile.tileZ(),
                    tile.coreMinX(),
                    tile.coreMinZ(),
                    tile.coreWidth(),
                    tile.coreLength(),
                    minY,
                    maxY);
            Path schematic = tileRoot.resolve(plan.defaultSchematicFileName());
            OfflineTileArtifactV2 artifact = schematicWriter.write(
                    schematic, plan, blueprint.canonicalChecksum(), resolver, () -> false);
            Path metadata = tileRoot.resolve(tile.tileId() + ".json");
            tileCodec.write(metadata, artifact);
            tiles.add(new SurfaceReleaseSourceV2.TileSource(artifact.tileId(), metadata, schematic));
        }

        SurfaceReleaseSourceV2 source = new SurfaceReleaseSourceV2(
                requestPath, intentPath, blueprintPath, indexPath, fieldRoot,
                validationPath, previewRoot.resolve("index.json"), previewRoot, List.copyOf(tiles));
        return new Fixture(source, blueprint, tilePlan);
    }

    private static List<FieldArtifactDescriptorV2> writeFields(Path root, int width, int length)
            throws Exception {
        LfcGridWriterV1 writer = new LfcGridWriterV1();
        List<FieldArtifactDescriptorV2> fields = new ArrayList<>();
        fields.add(write(writer, root, "fields/land-desired.lfgrid", "constraint.land.desired",
                FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                FieldArtifactDescriptorV2.FieldValueType.U8,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "land-water", width, length,
                (x, z) -> 1));
        fields.add(write(writer, root, "fields/land-actual.lfgrid", "constraint.land.actual",
                FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER,
                FieldArtifactDescriptorV2.FieldValueType.U8,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "land-water", width, length,
                (x, z) -> 1));
        fields.add(write(writer, root, "fields/land-residual.lfgrid", "constraint.land.residual",
                FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER,
                FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "land-water", width, length,
                (x, z) -> 0));
        fields.add(write(writer, root, "fields/height-desired.lfgrid", "constraint.height.desired",
                FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT,
                FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, "height", width, length,
                (x, z) -> 1_000_000));
        fields.add(write(writer, root, "fields/height-actual.lfgrid", "constraint.height.actual",
                FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT,
                FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, "height", width, length,
                (x, z) -> 1_000_000));
        fields.add(write(writer, root, "fields/height-residual.lfgrid", "constraint.height.residual",
                FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT,
                FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, "height", width, length,
                (x, z) -> 0));
        fields.add(write(writer, root, "fields/zone-desired.lfgrid", "constraint.zone.desired",
                FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP,
                FieldArtifactDescriptorV2.FieldValueType.U16,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "zones", width, length,
                (x, z) -> x < width / 2 ? 1 : 2));
        return List.copyOf(fields);
    }

    private static FieldArtifactDescriptorV2 write(
            LfcGridWriterV1 writer,
            Path root,
            String path,
            String id,
            FieldArtifactDescriptorV2.FieldSemantic semantic,
            FieldArtifactDescriptorV2.FieldValueType type,
            FieldArtifactDescriptorV2.Sampling sampling,
            String source,
            int width,
            int length,
            FieldValueSource values
    ) throws Exception {
        long scale = type == FieldArtifactDescriptorV2.FieldValueType.I32 ? 1L : 1_000_000L;
        FieldArtifactDescriptorV2.Definition definition = new FieldArtifactDescriptorV2.Definition(
                id, semantic, type, width, length, FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                sampling, scale, 0L, false, 0);
        String checksum = switch (source) {
            case "land-water" -> "a";
            case "height" -> "b";
            default -> "c";
        };
        return writer.write(root, path, definition, new FieldArtifactDescriptorV2.Provenance(
                FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP, "constraint-source:" + source,
                checksum.repeat(64), "numeric-png", "1", "pixel-center-v1"), values, () -> false);
    }

    private static List<ConstraintFieldIndexV2.AppliedBinding> bindings(
            List<FieldArtifactDescriptorV2> fields
    ) {
        FieldArtifactDescriptorV2 land = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER);
        FieldArtifactDescriptorV2 height = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT);
        FieldArtifactDescriptorV2 zone = field(fields, FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP);
        return List.of(
                new ConstraintFieldIndexV2.AppliedBinding(
                        "land-water-binding", "constraint-source:land-water",
                        TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK, TerrainIntentV2.Strength.HARD,
                        TerrainIntentV2.Sampling.NEAREST, 0, 0,
                        "constraint:land-water:sha256-" + land.semanticChecksum(),
                        land.definition().fieldId(), ids(fields, "constraint.land"), List.of(
                        new ConstraintFieldIndexV2.LabelEntry(0, 0, "water"),
                        new ConstraintFieldIndexV2.LabelEntry(1, 1, "land"))),
                new ConstraintFieldIndexV2.AppliedBinding(
                        "height-binding", "constraint-source:height",
                        TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE, TerrainIntentV2.Strength.SOFT,
                        TerrainIntentV2.Sampling.BILINEAR_FIXED, 2, 800_000,
                        "constraint:height-guide:sha256-" + height.semanticChecksum(),
                        height.definition().fieldId(), ids(fields, "constraint.height"), List.of()),
                new ConstraintFieldIndexV2.AppliedBinding(
                        "zone-binding", "constraint-source:zones",
                        TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP, TerrainIntentV2.Strength.HARD,
                        TerrainIntentV2.Sampling.NEAREST, 0, 0,
                        "constraint:zone-label-map:sha256-" + zone.semanticChecksum(),
                        zone.definition().fieldId(), ids(fields, "constraint.zone"), List.of(
                        new ConstraintFieldIndexV2.LabelEntry(10, 1, "shore"),
                        new ConstraintFieldIndexV2.LabelEntry(20, 2, "upland"))));
    }

    private static List<String> ids(List<FieldArtifactDescriptorV2> fields, String prefix) {
        return fields.stream()
                .map(value -> value.definition().fieldId())
                .filter(value -> value.startsWith(prefix))
                .toList();
    }

    private static FieldArtifactDescriptorV2 field(
            List<FieldArtifactDescriptorV2> fields,
            FieldArtifactDescriptorV2.FieldSemantic semantic
    ) {
        return fields.stream()
                .filter(value -> value.definition().semantic() == semantic)
                .findFirst()
                .orElseThrow();
    }

    private static CoastalDiagnosticFieldsV2 diagnosticFields(
            int width,
            int length,
            int minY,
            int maxY
    ) {
        int minHeight = minY * 1_000_000;
        int maxHeight = Math.max(minHeight + 1, maxY * 1_000_000);
        return new CoastalDiagnosticFieldsV2(
                width, length, minHeight, maxHeight,
                (x, z) -> 0, (x, z) -> 0, (x, z) -> 0, (x, z) -> 0,
                (x, z) -> 1, (x, z) -> 1, (x, z) -> 0,
                (x, z) -> minHeight + 1_000_000, (x, z) -> minHeight + 1_000_000, (x, z) -> 0, (x, z) -> 0);
    }

    /** Solid-only column: bedrock floor + stone fill. No fluid／gravity blocks. */
    private static TerrainBlockResolver solidResolver(int minY, int maxY) {
        return (x, y, z) -> {
            if (y < minY || y > maxY) {
                return "minecraft:air";
            }
            if (y == minY) {
                return "minecraft:bedrock";
            }
            return "minecraft:stone";
        };
    }
}
