package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.SpongeV3TileInspectorV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.CoastalValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalPreviewIndexCodecV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Semantic verifier for the bounded V2-2 {@code surface-2_5d} Release capability.
 *
 * <p>ReleaseCoreVerifierV2 has already rejected links, path escapes, index-external files,
 * byte/checksum tampering, ZIP bombs, and TOCTOU changes before this class is called. This class
 * therefore owns only the versioned capability's exact artifact set and cross-artifact meaning.</p>
 */
final class SurfaceReleaseCapabilityVerifierV2 {
    static final String REQUEST_TYPE = "generation-request-v2";
    static final String INTENT_TYPE = "terrain-intent-v2";
    static final String BLUEPRINT_TYPE = "world-blueprint-v2";
    static final String FIELD_INDEX_TYPE = "constraint-field-index-v2";
    static final String FIELD_GRID_TYPE = "constraint-field-grid-v1";
    static final String VALIDATION_TYPE = "coastal-validation-artifact-v2";
    static final String PREVIEW_INDEX_TYPE = "coastal-preview-index-v2";
    static final String PREVIEW_PNG_TYPE = "coastal-preview-png-v1";
    static final String TILE_METADATA_TYPE = "offline-tile-artifact-v2";
    static final String SCHEMATIC_TYPE = "sponge-schematic-v3";

    private static final String REQUEST_PATH = "source/generation-request.json";
    private static final String INTENT_PATH = "source/terrain-intent.json";
    private static final String BLUEPRINT_PATH = "blueprint/world-blueprint.json";
    private static final String FIELD_INDEX_PATH = "constraints/index.json";
    private static final String VALIDATION_PATH = "validation/coastal-validation.json";
    private static final String PREVIEW_INDEX_PATH = "previews/index.json";

    private final LandformV2DataCodec dataCodec = new LandformV2DataCodec();
    private final ConstraintFieldIndexCodecV2 fieldCodec = new ConstraintFieldIndexCodecV2();
    private final CoastalValidationArtifactCodecV2 validationCodec = new CoastalValidationArtifactCodecV2();
    private final CoastalPreviewIndexCodecV2 previewCodec = new CoastalPreviewIndexCodecV2();
    private final OfflineTileArtifactCodecV2 tileCodec = new OfflineTileArtifactCodecV2();
    private final SpongeV3TileInspectorV2 schematicInspector = new SpongeV3TileInspectorV2();

    void verify(Path root, ReleaseManifestV2 manifest, CancellationToken cancellationToken) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (!manifest.requiredCapabilities().equals(List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D))) {
            throw new IOException("surface-2_5d Release must require exactly its own capability");
        }
        ReleaseCapabilityArtifactIndexV2 index = new ReleaseCapabilityArtifactIndexV2(manifest.artifacts());
        verifySurfacePayload(root, index, cancellationToken);
        index.requireNoUnexpectedArtifacts();
    }

    /**
     * Verifies the surface artifact set without requiring the index to be fully consumed.
     * Used when {@code hydrology-plan} adds further required artifacts beside surface.
     */
    WorldBlueprintV2 verifySurfacePayload(
            Path root,
            ReleaseCapabilityArtifactIndexV2 index,
            CancellationToken cancellationToken
    ) throws IOException {
        ReleaseArtifactDescriptorV2 requestDescriptor = index.singleton(REQUEST_TYPE, REQUEST_PATH);
        ReleaseArtifactDescriptorV2 intentDescriptor = index.singleton(INTENT_TYPE, INTENT_PATH);
        ReleaseArtifactDescriptorV2 blueprintDescriptor = index.singleton(BLUEPRINT_TYPE, BLUEPRINT_PATH);
        ReleaseArtifactDescriptorV2 fieldIndexDescriptor = index.singleton(FIELD_INDEX_TYPE, FIELD_INDEX_PATH);
        ReleaseArtifactDescriptorV2 validationDescriptor = index.singleton(VALIDATION_TYPE, VALIDATION_PATH);
        ReleaseArtifactDescriptorV2 previewIndexDescriptor = index.singleton(PREVIEW_INDEX_TYPE, PREVIEW_INDEX_PATH);

        cancellationToken.throwIfCancellationRequested();
        var request = dataCodec.readGenerationRequest(root.resolve(REQUEST_PATH));
        TerrainIntentV2 intent = dataCodec.readTerrainIntent(root.resolve(INTENT_PATH));
        WorldBlueprintV2 blueprint = dataCodec.readWorldBlueprint(root.resolve(BLUEPRINT_PATH));
        requireSemantic(requestDescriptor, dataCodec.generationRequestChecksum(request), "generation request");
        requireSemantic(intentDescriptor, dataCodec.terrainIntentChecksum(intent), "terrain intent");
        requireSemantic(blueprintDescriptor, blueprint.canonicalChecksum(), "world blueprint");
        if (!request.requestId().equals(intent.intentId())
                || !request.requestId().equals(blueprint.identity().requestId())
                || !blueprint.identity().sourceRequestChecksum().equals(requestDescriptor.semanticChecksum())
                || !blueprint.identity().sourceIntentChecksum().equals(intentDescriptor.semanticChecksum())) {
            throw new IOException("surface Release request, intent, and Blueprint identity binding differs");
        }

        cancellationToken.throwIfCancellationRequested();
        ConstraintFieldIndexV2 fieldIndex = fieldCodec.readAndVerify(
                root.resolve(FIELD_INDEX_PATH), root.resolve("constraints"),
                requestDescriptor.semanticChecksum(), intentDescriptor.semanticChecksum(), cancellationToken);
        requireSemantic(fieldIndexDescriptor, fieldIndex.canonicalChecksum(), "constraint field index");
        verifyFieldArtifacts(index, fieldIndex, blueprint, cancellationToken);
        verifyIntentBindings(request.constraintMaps().stream().collect(java.util.stream.Collectors.toMap(
                source -> source.sourceId(), source -> source.expectedSha256())), intent, fieldIndex);

        cancellationToken.throwIfCancellationRequested();
        CoastalValidationArtifactV2 validation = validationCodec.read(root.resolve(VALIDATION_PATH));
        requireSemantic(validationDescriptor, validation.canonicalChecksum(), "coastal validation artifact");
        if (!validation.sourceBlueprintChecksum().equals(blueprint.canonicalChecksum())
                || !validation.report().passesHardValidation()
                || requiresCoastalMetrics(blueprint) && validation.report().metrics().isEmpty()) {
            throw new IOException("coastal validation artifact does not validate the released Blueprint");
        }

        cancellationToken.throwIfCancellationRequested();
        CoastalPreviewIndexV2 previews = previewCodec.readAndVerify(
                root.resolve(PREVIEW_INDEX_PATH), root.resolve("previews"), cancellationToken);
        requireSemantic(previewIndexDescriptor, previews.canonicalChecksum(), "coastal preview index");
        if (!previews.sourceBlueprintChecksum().equals(blueprint.canonicalChecksum())
                || previews.width() != blueprint.space().bounds().width()
                || previews.length() != blueprint.space().bounds().length()) {
            throw new IOException("coastal previews do not bind to the released Blueprint dimensions");
        }
        for (CoastalPreviewIndexV2.Layer layer : previews.layers()) {
            cancellationToken.throwIfCancellationRequested();
            ReleaseArtifactDescriptorV2 descriptor = index.requirePath("previews/" + layer.path(), PREVIEW_PNG_TYPE);
            requireSemantic(descriptor, layer.sha256(), "coastal preview layer " + layer.layerId());
            if (descriptor.byteLength() != layer.byteLength() || !descriptor.artifactChecksum().equals(layer.sha256())) {
                throw new IOException("coastal preview layer manifest binding differs: " + layer.path());
            }
        }
        if (index.ofType(PREVIEW_PNG_TYPE).size() != previews.layers().size()) {
            throw new IOException("surface Release preview layer set is incomplete or has an extra entry");
        }

        cancellationToken.throwIfCancellationRequested();
        verifyTiles(index, root, blueprint, cancellationToken);
        return blueprint;
    }

    private static void verifyFieldArtifacts(
            ReleaseCapabilityArtifactIndexV2 index,
            ConstraintFieldIndexV2 fieldIndex,
            WorldBlueprintV2 blueprint,
            CancellationToken cancellationToken
    ) throws IOException {
        if (!fieldIndex.requestId().equals(blueprint.identity().requestId())) {
            throw new IOException("constraint field index requestId differs from the released Blueprint");
        }
        for (var field : fieldIndex.fields()) {
            cancellationToken.throwIfCancellationRequested();
            if (field.definition().width() != blueprint.space().bounds().width()
                    || field.definition().length() != blueprint.space().bounds().length()) {
                throw new IOException("constraint field dimensions differ from the released Blueprint");
            }
            ReleaseArtifactDescriptorV2 descriptor = index.requirePath(
                    "constraints/" + field.relativePath(), FIELD_GRID_TYPE);
            requireSemantic(descriptor, field.semanticChecksum(), "constraint field " + field.definition().fieldId());
            if (!descriptor.artifactChecksum().equals(field.artifactChecksum())) {
                throw new IOException("constraint field artifact checksum differs from its field descriptor");
            }
        }
        if (index.ofType(FIELD_GRID_TYPE).size() != fieldIndex.fields().size()) {
            throw new IOException("surface Release field sidecar set is incomplete or has an extra entry");
        }
    }

    private static void verifyIntentBindings(
            Map<String, String> requestSources,
            TerrainIntentV2 intent,
            ConstraintFieldIndexV2 fieldIndex
    )
            throws IOException {
        Map<String, ConstraintFieldIndexV2.AppliedBinding> bySource = new HashMap<>();
        for (ConstraintFieldIndexV2.AppliedBinding binding : fieldIndex.bindings()) {
            bySource.put(binding.sourceId(), binding);
        }
        if (intent.mapReferences().size() != bySource.size() || !requestSources.keySet().equals(bySource.keySet())) {
            throw new IOException("surface Release intent and field-index constraint binding counts differ");
        }
        for (TerrainIntentV2.ConstraintMapBinding binding : intent.mapReferences()) {
            ConstraintFieldIndexV2.AppliedBinding applied = bySource.get(binding.sourceId());
            // V2-18-07 separates the two artifact references that used to be forced equal. The field
            // index's canonicalArtifactId content-addresses the DESIRED field artifact (verified against
            // its own semantic checksum inside ConstraintFieldIndexV2). The intent binding's artifactId is
            // the desired-source provenance and must reference the declared INPUT mask digest, not the
            // generated field's checksum. Every other binding parameter still has to agree.
            if (applied == null || !applied.bindingId().equals(binding.id()) || applied.role() != binding.role()
                    || applied.strength() != binding.strength()
                    || applied.sampling().name().equals(binding.sampling().name()) == false
                    || applied.toleranceBlocks() != binding.toleranceBlocks()
                    || applied.weightMillionths() != binding.weightMillionths()) {
                throw new IOException("surface Release constraint binding differs between intent and field index");
            }
            String expectedSourceChecksum = requestSources.get(binding.sourceId());
            String expectedArtifactId = artifactPrefix(binding.role()) + expectedSourceChecksum;
            if (!binding.artifactId().equals(expectedArtifactId)) {
                throw new IOException("surface Release intent binding does not reference the declared input mask digest");
            }
            boolean provenanceMatches = fieldIndex.fields().stream()
                    .filter(field -> applied.fieldIds().contains(field.definition().fieldId()))
                    .allMatch(field -> field.provenance().sourceChecksum().equals(expectedSourceChecksum));
            if (!provenanceMatches) {
                throw new IOException("surface Release field provenance differs from request source checksum");
            }
        }
    }

    /** Canonical artifact-id prefix per map role, matching {@code TerrainIntentV2.ConstraintMapBinding}. */
    private static String artifactPrefix(TerrainIntentV2.ConstraintMapRole role) {
        return switch (role) {
            case LAND_WATER_MASK -> "constraint:land-water:sha256-";
            case HEIGHT_GUIDE -> "constraint:height-guide:sha256-";
            case ZONE_LABEL_MAP -> "constraint:zone-label-map:sha256-";
        };
    }

    private static boolean requiresCoastalMetrics(WorldBlueprintV2 blueprint) {
        return !blueprint.sandyBeachPlans().isEmpty()
                || !blueprint.harborBasinPlans().isEmpty()
                || !blueprint.breakwaterHarborPlans().isEmpty()
                || !blueprint.rockyCapePlans().isEmpty()
                || !blueprint.coastalTransitionPlans().isEmpty();
    }

    private void verifyTiles(
            ReleaseCapabilityArtifactIndexV2 index,
            Path root,
            WorldBlueprintV2 blueprint,
            CancellationToken cancellationToken
    ) throws IOException {
        List<ReleaseArtifactDescriptorV2> metadataDescriptors = index.ofType(TILE_METADATA_TYPE);
        List<ReleaseArtifactDescriptorV2> schematicDescriptors = index.ofType(SCHEMATIC_TYPE);
        if (metadataDescriptors.isEmpty() || metadataDescriptors.size() != schematicDescriptors.size()) {
            throw new IOException("surface Release must contain matching non-empty tile metadata and schematic sets");
        }
        boolean[] covered = new boolean[Math.multiplyExact(
                blueprint.space().bounds().width(), blueprint.space().bounds().length())];
        Set<String> tileIds = new HashSet<>();
        for (ReleaseArtifactDescriptorV2 metadataDescriptor : metadataDescriptors) {
            cancellationToken.throwIfCancellationRequested();
            if (!metadataDescriptor.path().startsWith("tiles/") || !metadataDescriptor.path().endsWith(".json")) {
                throw new IOException("surface Release tile metadata path is not canonical");
            }
            index.requirePath(metadataDescriptor.path(), TILE_METADATA_TYPE);
            OfflineTileArtifactV2 tile = tileCodec.read(root.resolve(metadataDescriptor.path()));
            requireSemantic(metadataDescriptor, tile.canonicalChecksum(), "offline tile metadata " + tile.tileId());
            if (!tileIds.add(tile.tileId()) || !metadataDescriptor.path().equals("tiles/" + tile.tileId() + ".json")
                    || !tile.schematicPath().equals(tile.tileId() + ".schem")
                    || !tile.sourceBlueprintChecksum().equals(blueprint.canonicalChecksum())) {
                throw new IOException("offline tile metadata does not bind to the surface Release Blueprint");
            }
            ReleaseArtifactDescriptorV2 schematicDescriptor = index.requirePath(
                    "tiles/" + tile.schematicPath(), SCHEMATIC_TYPE);
            requireSemantic(schematicDescriptor, tile.semanticChecksum(), "offline tile schematic " + tile.tileId());
            if (schematicDescriptor.byteLength() != tile.byteLength()
                    || !schematicDescriptor.artifactChecksum().equals(tile.artifactChecksum())) {
                throw new IOException("offline tile schematic descriptor differs from its metadata");
            }
            SpongeV3TileInspectorV2.Inspection inspection = schematicInspector.inspect(
                    root.resolve(schematicDescriptor.path()), tile.tilePlan());
            if (inspection.paletteSize() != tile.paletteSize()
                    || !inspection.semanticChecksum().equals(tile.semanticChecksum())) {
                throw new IOException("offline tile schematic strict read-back differs from its metadata");
            }
            cover(tile, blueprint, covered, cancellationToken);
        }
        for (boolean value : covered) {
            if (!value) throw new IOException("surface Release tiles do not cover the full Blueprint X/Z extent");
        }
    }

    private static void cover(
            OfflineTileArtifactV2 tile,
            WorldBlueprintV2 blueprint,
            boolean[] covered,
            CancellationToken cancellationToken
    ) throws IOException {
        var bounds = blueprint.space().bounds();
        if (tile.minY() != bounds.minY() || tile.maxY() != bounds.maxY()
                || tile.originX() < 0 || tile.originZ() < 0
                || tile.originX() + tile.width() > bounds.width()
                || tile.originZ() + tile.length() > bounds.length()) {
            throw new IOException("offline tile lies outside the released Blueprint bounds");
        }
        for (int z = tile.originZ(); z < tile.originZ() + tile.length(); z++) {
            cancellationToken.throwIfCancellationRequested();
            for (int x = tile.originX(); x < tile.originX() + tile.width(); x++) {
                int offset = z * bounds.width() + x;
                if (covered[offset]) throw new IOException("offline tiles overlap in the released Blueprint");
                covered[offset] = true;
            }
        }
    }

    private static void requireSemantic(
            ReleaseArtifactDescriptorV2 descriptor, String expected, String description
    ) throws IOException {
        if (!descriptor.semanticChecksum().equals(expected)) {
            throw new IOException("surface Release semantic checksum differs for " + description);
        }
    }
}
