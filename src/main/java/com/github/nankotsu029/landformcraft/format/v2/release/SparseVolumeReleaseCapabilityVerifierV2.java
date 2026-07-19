package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.SpongeV3TileInspectorV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.VolumeValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.VolumeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeAabbIndexPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Semantic verifier for Release 2 {@code sparse-volume}, which always depends on
 * {@code environment-fields}, {@code hydrology-plan}, and {@code surface-2_5d}.
 *
 * <p>The volume tile uses dedicated {@code volume-offline-tile-artifact-v2} /
 * {@code volume-sponge-schematic-v3} artifact types so it never mixes with the surface tile set.</p>
 */
final class SparseVolumeReleaseCapabilityVerifierV2 {
    static final String SDF_TYPE = "volume-sdf-primitive-plan-v2";
    static final String CSG_TYPE = "volume-csg-plan-v2";
    static final String AABB_TYPE = "volume-aabb-index-plan-v2";
    static final String VALIDATION_TYPE = "volume-validation-artifact-v2";
    static final String TILE_METADATA_TYPE = "volume-offline-tile-artifact-v2";
    static final String SCHEMATIC_TYPE = "volume-sponge-schematic-v3";

    static final String SDF_PATH = "volume/sdf-primitive-plan.json";
    static final String CSG_PATH = "volume/csg-plan.json";
    static final String AABB_PATH = "volume/aabb-index-plan.json";
    static final String VALIDATION_PATH = "volume/validation.json";

    private final EnvironmentReleaseCapabilityVerifierV2 environmentVerifier =
            new EnvironmentReleaseCapabilityVerifierV2();
    private final LandformV2DataCodec dataCodec = new LandformV2DataCodec();
    private final VolumeValidationArtifactCodecV2 validationCodec = new VolumeValidationArtifactCodecV2();
    private final OfflineTileArtifactCodecV2 tileCodec = new OfflineTileArtifactCodecV2();
    private final SpongeV3TileInspectorV2 schematicInspector = new SpongeV3TileInspectorV2();

    void verify(Path root, ReleaseManifestV2 manifest, CancellationToken cancellationToken) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (!manifest.requiredCapabilities().equals(ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT)) {
            throw new IOException("sparse-volume Release must require sparse-volume, environment-fields, "
                    + "hydrology-plan, and surface-2_5d");
        }
        ReleaseCapabilityArtifactIndexV2 index = new ReleaseCapabilityArtifactIndexV2(manifest.artifacts());
        WorldBlueprintV2 blueprint = environmentVerifier.verifyEnvironmentStack(root, index, cancellationToken);
        verifyVolumePayload(root, index, blueprint, cancellationToken);
        index.requireNoUnexpectedArtifacts();
    }

    private void verifyVolumePayload(
            Path root,
            ReleaseCapabilityArtifactIndexV2 index,
            WorldBlueprintV2 blueprint,
            CancellationToken cancellationToken
    ) throws IOException {
        ReleaseArtifactDescriptorV2 sdfDescriptor = index.singleton(SDF_TYPE, SDF_PATH);
        ReleaseArtifactDescriptorV2 csgDescriptor = index.singleton(CSG_TYPE, CSG_PATH);
        ReleaseArtifactDescriptorV2 aabbDescriptor = index.singleton(AABB_TYPE, AABB_PATH);
        ReleaseArtifactDescriptorV2 validationDescriptor = index.singleton(VALIDATION_TYPE, VALIDATION_PATH);

        cancellationToken.throwIfCancellationRequested();
        VolumeSdfPrimitivePlanV2 sdf = dataCodec.readVolumeSdfPrimitivePlan(root.resolve(SDF_PATH));
        requireSemantic(sdfDescriptor, sdf.canonicalChecksum(), "volume sdf primitive plan");

        cancellationToken.throwIfCancellationRequested();
        VolumeCsgPlanV2 csg = dataCodec.readVolumeCsgPlan(root.resolve(CSG_PATH));
        requireSemantic(csgDescriptor, csg.canonicalChecksum(), "volume csg plan");
        requireBinding(() -> csg.requirePrimitivePlan(sdf), "volume csg plan does not bind to the sdf plan");

        cancellationToken.throwIfCancellationRequested();
        VolumeAabbIndexPlanV2 aabb = dataCodec.readVolumeAabbIndexPlan(root.resolve(AABB_PATH));
        requireSemantic(aabbDescriptor, aabb.canonicalChecksum(), "volume aabb index plan");
        requireBinding(() -> aabb.requireCsgPlan(csg), "volume aabb index does not bind to the csg plan");

        cancellationToken.throwIfCancellationRequested();
        VolumeValidationArtifactV2 validation = validationCodec.read(root.resolve(VALIDATION_PATH));
        requireSemantic(validationDescriptor, validation.canonicalChecksum(), "volume validation artifact");
        if (!validation.sourcePlanChecksum().equals(csg.canonicalChecksum())
                || !validation.report().passesHardValidation()) {
            throw new IOException("volume validation artifact does not validate the released volume plan");
        }

        cancellationToken.throwIfCancellationRequested();
        verifyVolumeTiles(root, index, blueprint, cancellationToken);
    }

    private void verifyVolumeTiles(
            Path root,
            ReleaseCapabilityArtifactIndexV2 index,
            WorldBlueprintV2 blueprint,
            CancellationToken cancellationToken
    ) throws IOException {
        List<ReleaseArtifactDescriptorV2> metadataDescriptors = index.ofType(TILE_METADATA_TYPE);
        List<ReleaseArtifactDescriptorV2> schematicDescriptors = index.ofType(SCHEMATIC_TYPE);
        if (metadataDescriptors.isEmpty() || metadataDescriptors.size() != schematicDescriptors.size()) {
            throw new IOException("sparse-volume Release must contain matching non-empty volume tile sets");
        }
        Set<String> tileIds = new HashSet<>();
        for (ReleaseArtifactDescriptorV2 metadataDescriptor : metadataDescriptors) {
            cancellationToken.throwIfCancellationRequested();
            if (!metadataDescriptor.path().startsWith("volume/tiles/")
                    || !metadataDescriptor.path().endsWith(".json")) {
                throw new IOException("sparse-volume Release volume tile metadata path is not canonical");
            }
            index.requirePath(metadataDescriptor.path(), TILE_METADATA_TYPE);
            OfflineTileArtifactV2 tile = tileCodec.read(root.resolve(metadataDescriptor.path()));
            requireSemantic(metadataDescriptor, tile.canonicalChecksum(), "volume tile metadata " + tile.tileId());
            if (!tileIds.add(tile.tileId())
                    || !metadataDescriptor.path().equals("volume/tiles/" + tile.tileId() + ".json")
                    || !tile.schematicPath().equals(tile.tileId() + ".schem")
                    || !tile.sourceBlueprintChecksum().equals(blueprint.canonicalChecksum())) {
                throw new IOException("volume tile metadata does not bind to the sparse-volume Release Blueprint");
            }
            ReleaseArtifactDescriptorV2 schematicDescriptor = index.requirePath(
                    "volume/tiles/" + tile.schematicPath(), SCHEMATIC_TYPE);
            requireSemantic(schematicDescriptor, tile.semanticChecksum(), "volume tile schematic " + tile.tileId());
            if (schematicDescriptor.byteLength() != tile.byteLength()
                    || !schematicDescriptor.artifactChecksum().equals(tile.artifactChecksum())) {
                throw new IOException("volume tile schematic descriptor differs from its metadata");
            }
            SpongeV3TileInspectorV2.Inspection inspection = schematicInspector.inspect(
                    root.resolve(schematicDescriptor.path()), tile.tilePlan());
            if (inspection.paletteSize() != tile.paletteSize()
                    || inspection.blockCount() != tile.blockCount()
                    || !inspection.semanticChecksum().equals(tile.semanticChecksum())) {
                throw new IOException("volume tile schematic strict read-back differs from its metadata");
            }
        }
    }

    private static void requireBinding(Binding binding, String message) throws IOException {
        try {
            binding.check();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IOException(message, exception);
        }
    }

    private static void requireSemantic(
            ReleaseArtifactDescriptorV2 descriptor, String expected, String description
    ) throws IOException {
        if (!descriptor.semanticChecksum().equals(expected)) {
            throw new IOException("sparse-volume Release semantic checksum differs for " + description);
        }
    }

    @FunctionalInterface
    private interface Binding {
        void check();
    }
}
