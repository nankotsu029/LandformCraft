package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
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
import com.github.nankotsu029.landformcraft.preview.v2.CoastalPreviewIndexCodecV2;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Stages and atomically publishes the only V2-2 Release 2 capability: {@code surface-2_5d}.
 * It copies sealed, independently verified inputs; their raw source paths do not enter the
 * manifest or any portable artifact.
 */
public final class ReleaseSurfacePublisherV2 {
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
    private final ReleaseManifestCodecV2 manifestCodec = new ReleaseManifestCodecV2();
    private final ReleaseV2Limits limits;
    private final ReleaseSurfaceVerifierV2 verifier;

    public ReleaseSurfacePublisherV2() {
        this(ReleaseV2Limits.defaults());
    }

    public ReleaseSurfacePublisherV2(ReleaseV2Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.verifier = new ReleaseSurfaceVerifierV2(limits);
    }

    public ReleaseSurfaceArtifactsV2 publish(
            Path exportsRoot,
            String releaseId,
            SurfaceReleaseSourceV2 source,
            boolean createZip,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(exportsRoot, "exportsRoot");
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();

        SourceSnapshot snapshot = inspectSource(source, cancellationToken);
        Path root = exportsRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("surface Release export root must be a non-symbolic directory");
        }
        ReleaseManifestV2 identity = manifestCodec.seal(new ReleaseManifestV2(releaseId));
        Path finalDirectory = root.resolve(identity.releaseId());
        Path finalZip = root.resolve(identity.releaseId() + ".zip");
        if (Files.exists(finalDirectory, LinkOption.NOFOLLOW_LINKS)
                || createZip && Files.exists(finalZip, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("surface Release target already exists");
        }
        ensureDiskBudget(root, snapshot.sourceBytes(), createZip);

        Path stagingDirectory = Files.createTempDirectory(root, ".release-v2-surface-stage-");
        Path stagingZip = root.resolve(".release-v2-surface-" + identity.releaseId() + ".tmp.zip");
        boolean directoryPublished = false;
        boolean zipPublished = false;
        try {
            List<ReleaseArtifactDescriptorV2> descriptors = copyAndDescribe(stagingDirectory, source, snapshot,
                    cancellationToken);
            ReleaseManifestV2 manifest = manifestCodec.seal(new ReleaseManifestV2(
                    ReleaseManifestV2.RELEASE_FORMAT_VERSION, ReleaseManifestV2.MANIFEST_VERSION, identity.releaseId(),
                    List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D), descriptors,
                    ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
            manifestCodec.write(stagingDirectory.resolve("manifest.json"), manifest);
            verifier.verify(stagingDirectory, cancellationToken);
            forceTree(stagingDirectory);
            cancellationToken.throwIfCancellationRequested();
            moveAtomically(stagingDirectory, finalDirectory);
            directoryPublished = true;
            if (!createZip) return new ReleaseSurfaceArtifactsV2(manifest.releaseId(), finalDirectory, Optional.empty());

            createZip(finalDirectory, stagingZip, cancellationToken);
            verifier.verify(stagingZip, cancellationToken);
            forceFile(stagingZip);
            cancellationToken.throwIfCancellationRequested();
            moveAtomically(stagingZip, finalZip);
            zipPublished = true;
            return new ReleaseSurfaceArtifactsV2(manifest.releaseId(), finalDirectory, Optional.of(finalZip));
        } catch (IOException | RuntimeException exception) {
            if (!zipPublished) Files.deleteIfExists(stagingZip);
            if (directoryPublished) ReleaseCoreVerifierV2.deleteTree(finalDirectory);
            throw exception;
        } finally {
            ReleaseCoreVerifierV2.deleteTree(stagingDirectory);
            if (!zipPublished) Files.deleteIfExists(stagingZip);
        }
    }

    SourceSnapshot inspectSource(SurfaceReleaseSourceV2 source, CancellationToken token) throws IOException {
        requireSafeRegular(source.generationRequest());
        requireSafeRegular(source.terrainIntent());
        requireSafeRegular(source.worldBlueprint());
        requireSafeRegular(source.constraintFieldIndex());
        requireSafeRegular(source.coastalValidationArtifact());
        requireSafeRegular(source.coastalPreviewIndex());
        requireSafeDirectory(source.constraintFieldRoot());
        requireSafeDirectory(source.coastalPreviewRoot());
        var request = dataCodec.readGenerationRequest(source.generationRequest());
        var intent = dataCodec.readTerrainIntent(source.terrainIntent());
        var blueprint = dataCodec.readWorldBlueprint(source.worldBlueprint());
        String requestChecksum = dataCodec.generationRequestChecksum(request);
        String intentChecksum = dataCodec.terrainIntentChecksum(intent);
        ConstraintFieldIndexV2 fields = fieldCodec.readAndVerify(source.constraintFieldIndex(), source.constraintFieldRoot(),
                requestChecksum, intentChecksum, token);
        CoastalValidationArtifactV2 validation = validationCodec.read(source.coastalValidationArtifact());
        CoastalPreviewIndexV2 previews = previewCodec.readAndVerify(
                source.coastalPreviewIndex(), source.coastalPreviewRoot(), token);
        List<TileSnapshot> tiles = new ArrayList<>();
        for (SurfaceReleaseSourceV2.TileSource tileSource : source.tiles()) {
            token.throwIfCancellationRequested();
            requireSafeRegular(tileSource.metadata());
            requireSafeRegular(tileSource.schematic());
            OfflineTileArtifactV2 tile = tileCodec.read(tileSource.metadata());
            if (!tile.tileId().equals(tileSource.tileId())) {
                throw new IOException("surface Release tile source ID differs from metadata");
            }
            new SpongeV3TileInspectorV2().inspect(tileSource.schematic(), tile.tilePlan());
            tiles.add(new TileSnapshot(tileSource, tile));
        }
        long bytes = totalSourceBytes(source, fields, previews, tiles);
        return new SourceSnapshot(requestChecksum, intentChecksum, blueprint.canonicalChecksum(), fields, validation,
                previews, List.copyOf(tiles), bytes);
    }

    List<ReleaseArtifactDescriptorV2> copyAndDescribe(
            Path staging,
            SurfaceReleaseSourceV2 source,
            SourceSnapshot snapshot,
            CancellationToken token
    ) throws IOException {
        List<ReleaseArtifactDescriptorV2> result = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        copyAndAdd(result, paths, "request", SurfaceReleaseCapabilityVerifierV2.REQUEST_TYPE, REQUEST_PATH,
                source.generationRequest(), snapshot.requestChecksum(), staging, token);
        copyAndAdd(result, paths, "intent", SurfaceReleaseCapabilityVerifierV2.INTENT_TYPE, INTENT_PATH,
                source.terrainIntent(), snapshot.intentChecksum(), staging, token);
        copyAndAdd(result, paths, "blueprint", SurfaceReleaseCapabilityVerifierV2.BLUEPRINT_TYPE, BLUEPRINT_PATH,
                source.worldBlueprint(), snapshot.blueprintChecksum(), staging, token);
        copyAndAdd(result, paths, "constraint-index", SurfaceReleaseCapabilityVerifierV2.FIELD_INDEX_TYPE,
                FIELD_INDEX_PATH, source.constraintFieldIndex(), snapshot.fields().canonicalChecksum(), staging, token);
        for (var field : snapshot.fields().fields()) {
            copyAndAdd(result, paths, "field." + field.definition().fieldId(),
                    SurfaceReleaseCapabilityVerifierV2.FIELD_GRID_TYPE,
                    "constraints/" + field.relativePath(), source.constraintFieldRoot().resolve(field.relativePath()),
                    field.semanticChecksum(), staging, token);
        }
        copyAndAdd(result, paths, "coastal-validation", SurfaceReleaseCapabilityVerifierV2.VALIDATION_TYPE,
                VALIDATION_PATH, source.coastalValidationArtifact(), snapshot.validation().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "preview-index", SurfaceReleaseCapabilityVerifierV2.PREVIEW_INDEX_TYPE,
                PREVIEW_INDEX_PATH, source.coastalPreviewIndex(), snapshot.previews().canonicalChecksum(), staging, token);
        for (CoastalPreviewIndexV2.Layer layer : snapshot.previews().layers()) {
            copyAndAdd(result, paths, "preview." + layer.layerId().name().toLowerCase(java.util.Locale.ROOT),
                    SurfaceReleaseCapabilityVerifierV2.PREVIEW_PNG_TYPE, "previews/" + layer.path(),
                    source.coastalPreviewRoot().resolve(layer.path()), layer.sha256(), staging, token);
        }
        for (TileSnapshot tile : snapshot.tiles()) {
            copyAndAdd(result, paths, "tile." + tile.artifact().tileId(),
                    SurfaceReleaseCapabilityVerifierV2.TILE_METADATA_TYPE,
                    "tiles/" + tile.artifact().tileId() + ".json", tile.source().metadata(),
                    tile.artifact().canonicalChecksum(), staging, token);
            copyAndAdd(result, paths, "schematic." + tile.artifact().tileId(),
                    SurfaceReleaseCapabilityVerifierV2.SCHEMATIC_TYPE,
                    "tiles/" + tile.artifact().schematicPath(), tile.source().schematic(),
                    tile.artifact().semanticChecksum(), staging, token);
        }
        return List.copyOf(result);
    }

    private static void copyAndAdd(
            List<ReleaseArtifactDescriptorV2> descriptors,
            Set<String> paths,
            String id,
            String type,
            String targetPath,
            Path source,
            String semanticChecksum,
            Path staging,
            CancellationToken token
    ) throws IOException {
        token.throwIfCancellationRequested();
        String canonical = ReleaseV2Paths.canonicalRelativePath(targetPath);
        if (!paths.add(canonical)) throw new IOException("surface Release source maps multiple artifacts to one path");
        Path target = staging.resolve(canonical).normalize();
        if (!target.startsWith(staging)) throw new IOException("surface Release artifact path escapes staging root");
        Files.createDirectories(Objects.requireNonNull(target.getParent()));
        FileFingerprint before = fingerprint(source);
        Files.copy(source, target, LinkOption.NOFOLLOW_LINKS);
        requireStable(source, before);
        requireSafeRegular(target);
        descriptors.add(new ReleaseArtifactDescriptorV2(id, type, 1, canonical, Files.size(target),
                Sha256.file(target), semanticChecksum));
    }

    private long totalSourceBytes(
            SurfaceReleaseSourceV2 source,
            ConstraintFieldIndexV2 fields,
            CoastalPreviewIndexV2 previews,
            List<TileSnapshot> tiles
    ) throws IOException {
        long total = 0;
        total = add(total, Files.size(source.generationRequest()));
        total = add(total, Files.size(source.terrainIntent()));
        total = add(total, Files.size(source.worldBlueprint()));
        total = add(total, Files.size(source.constraintFieldIndex()));
        total = add(total, Files.size(source.coastalValidationArtifact()));
        total = add(total, Files.size(source.coastalPreviewIndex()));
        for (var field : fields.fields()) total = add(total, Files.size(source.constraintFieldRoot().resolve(field.relativePath())));
        for (var layer : previews.layers()) total = add(total, Files.size(source.coastalPreviewRoot().resolve(layer.path())));
        for (TileSnapshot tile : tiles) {
            total = add(total, Files.size(tile.source().metadata()));
            total = add(total, Files.size(tile.source().schematic()));
        }
        return total;
    }

    private void ensureDiskBudget(Path root, long sourceBytes, boolean createZip) throws IOException {
        long multiplier = createZip ? 3L : 2L;
        long expected;
        try {
            expected = Math.addExact(Math.multiplyExact(sourceBytes, multiplier), 1024L * 1024L);
        } catch (ArithmeticException exception) {
            throw new IOException("surface Release disk estimate overflow", exception);
        }
        if (sourceBytes > limits.maximumDirectoryBytes()
                || expected > limits.maximumDirectoryBytes() + limits.maximumZipBytes()
                || Files.getFileStore(root).getUsableSpace() < expected) {
            throw new IOException("insufficient disk budget for surface Release staging and publish");
        }
    }

    private void createZip(Path releaseDirectory, Path stagingZip, CancellationToken token) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(releaseDirectory)) {
            files = stream.filter(Files::isRegularFile).sorted(java.util.Comparator.comparing(
                    path -> releaseDirectory.relativize(path).toString().replace('\\', '/'))).toList();
        }
        try (var file = Files.newOutputStream(stagingZip, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             var output = new ZipOutputStream(new BufferedOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
            for (Path path : files) {
                token.throwIfCancellationRequested();
                ZipEntry entry = new ZipEntry(releaseDirectory.relativize(path).toString().replace('\\', '/'));
                entry.setTime(0L);
                output.putNextEntry(entry);
                Files.copy(path, output);
                output.closeEntry();
            }
        }
        if (Files.size(stagingZip) > limits.maximumZipBytes()) {
            throw new IOException("surface Release ZIP exceeds its compressed byte budget");
        }
    }

    private static void forceTree(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) forceFile(file);
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("filesystem does not support required surface Release atomic publish", exception);
        }
    }

    private static void requireSafeDirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("surface Release source directory must be a non-symbolic directory");
        }
    }

    private static void requireSafeRegular(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("surface Release source artifact must be a regular non-symbolic file");
        }
    }

    private static FileFingerprint fingerprint(Path path) throws IOException {
        requireSafeRegular(path);
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return new FileFingerprint(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime());
    }

    private static void requireStable(Path path, FileFingerprint before) throws IOException {
        if (!before.equals(fingerprint(path))) {
            throw new IOException("surface Release source changed while it was being staged");
        }
    }

    private static long add(long current, long addition) throws IOException {
        try {
            return Math.addExact(current, addition);
        } catch (ArithmeticException exception) {
            throw new IOException("surface Release source byte total overflow", exception);
        }
    }

    record SourceSnapshot(
            String requestChecksum,
            String intentChecksum,
            String blueprintChecksum,
            ConstraintFieldIndexV2 fields,
            CoastalValidationArtifactV2 validation,
            CoastalPreviewIndexV2 previews,
            List<TileSnapshot> tiles,
            long sourceBytes
    ) { }

    record TileSnapshot(SurfaceReleaseSourceV2.TileSource source, OfflineTileArtifactV2 artifact) { }
    private record FileFingerprint(Object fileKey, long size, java.nio.file.attribute.FileTime modified) { }
}
