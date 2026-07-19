package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.SpongeV3TileInspectorV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.VolumeValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.VolumeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeAabbIndexPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;

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
 * Stages and atomically publishes Release 2 {@code sparse-volume} with its required
 * {@code environment-fields}, {@code hydrology-plan}, and {@code surface-2_5d} dependencies.
 */
public final class ReleaseSparseVolumePublisherV2 {
    private final LandformV2DataCodec dataCodec = new LandformV2DataCodec();
    private final VolumeValidationArtifactCodecV2 validationCodec = new VolumeValidationArtifactCodecV2();
    private final OfflineTileArtifactCodecV2 tileCodec = new OfflineTileArtifactCodecV2();
    private final SpongeV3TileInspectorV2 schematicInspector = new SpongeV3TileInspectorV2();
    private final ReleaseManifestCodecV2 manifestCodec = new ReleaseManifestCodecV2();
    private final ReleaseSurfacePublisherV2 surfacePublisher;
    private final ReleaseHydrologyPublisherV2 hydrologyPublisher;
    private final ReleaseEnvironmentPublisherV2 environmentPublisher;
    private final ReleaseSparseVolumeVerifierV2 verifier;
    private final ReleaseV2Limits limits;

    public ReleaseSparseVolumePublisherV2() {
        this(ReleaseV2Limits.defaults());
    }

    public ReleaseSparseVolumePublisherV2(ReleaseV2Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.surfacePublisher = new ReleaseSurfacePublisherV2(limits);
        this.hydrologyPublisher = new ReleaseHydrologyPublisherV2(limits);
        this.environmentPublisher = new ReleaseEnvironmentPublisherV2(limits);
        this.verifier = new ReleaseSparseVolumeVerifierV2(limits);
    }

    public ReleaseSparseVolumeArtifactsV2 publish(
            Path exportsRoot,
            String releaseId,
            SparseVolumeReleaseSourceV2 source,
            boolean createZip,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(exportsRoot, "exportsRoot");
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();

        ReleaseSurfacePublisherV2.SourceSnapshot surfaceSnapshot =
                surfacePublisher.inspectSource(source.environment().hydrology().surface(), cancellationToken);
        ReleaseHydrologyPublisherV2.HydrologySnapshot hydrologySnapshot = hydrologyPublisher.inspectHydrology(
                source.environment().hydrology(), surfaceSnapshot.blueprintChecksum(), cancellationToken);
        ReleaseEnvironmentPublisherV2.EnvironmentSnapshot environmentSnapshot =
                environmentPublisher.inspectEnvironment(
                        source.environment(), surfaceSnapshot.blueprintChecksum(), cancellationToken);
        VolumeSnapshot volumeSnapshot = inspectVolume(
                source, surfaceSnapshot.blueprintChecksum(), cancellationToken);
        long sourceBytes = add(add(add(surfaceSnapshot.sourceBytes(), hydrologySnapshot.sourceBytes()),
                environmentSnapshot.sourceBytes()), volumeSnapshot.sourceBytes());

        Path root = exportsRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("sparse-volume Release export root must be a non-symbolic directory");
        }
        ReleaseManifestV2 identity = manifestCodec.seal(new ReleaseManifestV2(releaseId));
        Path finalDirectory = root.resolve(identity.releaseId());
        Path finalZip = root.resolve(identity.releaseId() + ".zip");
        if (Files.exists(finalDirectory, LinkOption.NOFOLLOW_LINKS)
                || createZip && Files.exists(finalZip, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("sparse-volume Release target already exists");
        }
        ensureDiskBudget(root, sourceBytes, createZip);

        Path stagingDirectory = Files.createTempDirectory(root, ".release-v2-sparse-volume-stage-");
        Path stagingZip = root.resolve(".release-v2-sparse-volume-" + identity.releaseId() + ".tmp.zip");
        boolean directoryPublished = false;
        boolean zipPublished = false;
        try {
            List<ReleaseArtifactDescriptorV2> descriptors = new ArrayList<>(surfacePublisher.copyAndDescribe(
                    stagingDirectory, source.environment().hydrology().surface(), surfaceSnapshot, cancellationToken));
            Set<String> paths = new HashSet<>();
            for (ReleaseArtifactDescriptorV2 descriptor : descriptors) {
                paths.add(descriptor.path());
            }
            descriptors.addAll(hydrologyPublisher.copyHydrology(
                    stagingDirectory, source.environment().hydrology(), hydrologySnapshot, paths, cancellationToken));
            descriptors.addAll(environmentPublisher.copyEnvironment(
                    stagingDirectory, source.environment(), environmentSnapshot, paths, cancellationToken));
            descriptors.addAll(copyVolume(stagingDirectory, source, volumeSnapshot, paths, cancellationToken));
            ReleaseManifestV2 manifest = manifestCodec.seal(new ReleaseManifestV2(
                    ReleaseManifestV2.RELEASE_FORMAT_VERSION, ReleaseManifestV2.MANIFEST_VERSION, identity.releaseId(),
                    ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT, descriptors,
                    ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
            manifestCodec.write(stagingDirectory.resolve("manifest.json"), manifest);
            verifier.verify(stagingDirectory, cancellationToken);
            forceTree(stagingDirectory);
            cancellationToken.throwIfCancellationRequested();
            moveAtomically(stagingDirectory, finalDirectory);
            directoryPublished = true;
            if (!createZip) {
                return new ReleaseSparseVolumeArtifactsV2(manifest.releaseId(), finalDirectory, Optional.empty());
            }

            createZip(finalDirectory, stagingZip, cancellationToken);
            verifier.verify(stagingZip, cancellationToken);
            forceFile(stagingZip);
            cancellationToken.throwIfCancellationRequested();
            moveAtomically(stagingZip, finalZip);
            zipPublished = true;
            return new ReleaseSparseVolumeArtifactsV2(manifest.releaseId(), finalDirectory, Optional.of(finalZip));
        } catch (IOException | RuntimeException exception) {
            if (!zipPublished) {
                Files.deleteIfExists(stagingZip);
            }
            if (directoryPublished) {
                ReleaseCoreVerifierV2.deleteTree(finalDirectory);
            }
            throw exception;
        } finally {
            ReleaseCoreVerifierV2.deleteTree(stagingDirectory);
            if (!zipPublished) {
                Files.deleteIfExists(stagingZip);
            }
        }
    }

    private VolumeSnapshot inspectVolume(
            SparseVolumeReleaseSourceV2 source,
            String blueprintChecksum,
            CancellationToken token
    ) throws IOException {
        requireSafeRegular(source.sdfPrimitivePlan());
        requireSafeRegular(source.csgPlan());
        requireSafeRegular(source.aabbIndexPlan());
        requireSafeRegular(source.volumeValidationArtifact());

        VolumeSdfPrimitivePlanV2 sdf = dataCodec.readVolumeSdfPrimitivePlan(source.sdfPrimitivePlan());
        VolumeCsgPlanV2 csg = dataCodec.readVolumeCsgPlan(source.csgPlan());
        csg.requirePrimitivePlan(sdf);
        VolumeAabbIndexPlanV2 aabb = dataCodec.readVolumeAabbIndexPlan(source.aabbIndexPlan());
        aabb.requireCsgPlan(csg);
        VolumeValidationArtifactV2 validation = validationCodec.read(source.volumeValidationArtifact());
        if (!validation.sourcePlanChecksum().equals(csg.canonicalChecksum())
                || !validation.report().passesHardValidation()) {
            throw new IOException("volume validation source does not validate the released volume plan");
        }

        Set<String> tileIds = new HashSet<>();
        List<TileSnapshot> tiles = new ArrayList<>();
        long bytes = add(add(add(Files.size(source.sdfPrimitivePlan()), Files.size(source.csgPlan())),
                Files.size(source.aabbIndexPlan())), Files.size(source.volumeValidationArtifact()));
        for (SparseVolumeReleaseSourceV2.TileSource tileSource : source.tiles()) {
            token.throwIfCancellationRequested();
            requireSafeRegular(tileSource.metadata());
            requireSafeRegular(tileSource.schematic());
            OfflineTileArtifactV2 tile = tileCodec.read(tileSource.metadata());
            if (!tile.tileId().equals(tileSource.tileId())) {
                throw new IOException("sparse-volume Release tile source ID differs from metadata");
            }
            if (!tileIds.add(tile.tileId())
                    || !tile.schematicPath().equals(tile.tileId() + ".schem")
                    || !tile.sourceBlueprintChecksum().equals(blueprintChecksum)) {
                throw new IOException("volume tile metadata does not bind to the sparse-volume Release Blueprint");
            }
            if (Files.size(tileSource.schematic()) != tile.byteLength()
                    || !Sha256.file(tileSource.schematic()).equals(tile.artifactChecksum())) {
                throw new IOException("volume tile schematic differs from its metadata byte binding");
            }
            SpongeV3TileInspectorV2.Inspection inspection =
                    schematicInspector.inspect(tileSource.schematic(), tile.tilePlan());
            if (inspection.paletteSize() != tile.paletteSize()
                    || inspection.blockCount() != tile.blockCount()
                    || !inspection.semanticChecksum().equals(tile.semanticChecksum())) {
                throw new IOException("volume tile schematic strict read-back differs from its metadata");
            }
            tiles.add(new TileSnapshot(tileSource, tile));
            bytes = add(add(bytes, Files.size(tileSource.metadata())), Files.size(tileSource.schematic()));
        }
        return new VolumeSnapshot(sdf.canonicalChecksum(), csg.canonicalChecksum(), aabb.canonicalChecksum(),
                validation.canonicalChecksum(), List.copyOf(tiles), bytes);
    }

    private List<ReleaseArtifactDescriptorV2> copyVolume(
            Path staging,
            SparseVolumeReleaseSourceV2 source,
            VolumeSnapshot snapshot,
            Set<String> paths,
            CancellationToken token
    ) throws IOException {
        List<ReleaseArtifactDescriptorV2> result = new ArrayList<>();
        copyAndAdd(result, paths, "volume-sdf-primitive", SparseVolumeReleaseCapabilityVerifierV2.SDF_TYPE,
                SparseVolumeReleaseCapabilityVerifierV2.SDF_PATH, source.sdfPrimitivePlan(),
                snapshot.sdfChecksum(), staging, token);
        copyAndAdd(result, paths, "volume-csg", SparseVolumeReleaseCapabilityVerifierV2.CSG_TYPE,
                SparseVolumeReleaseCapabilityVerifierV2.CSG_PATH, source.csgPlan(),
                snapshot.csgChecksum(), staging, token);
        copyAndAdd(result, paths, "volume-aabb-index", SparseVolumeReleaseCapabilityVerifierV2.AABB_TYPE,
                SparseVolumeReleaseCapabilityVerifierV2.AABB_PATH, source.aabbIndexPlan(),
                snapshot.aabbChecksum(), staging, token);
        copyAndAdd(result, paths, "volume-validation", SparseVolumeReleaseCapabilityVerifierV2.VALIDATION_TYPE,
                SparseVolumeReleaseCapabilityVerifierV2.VALIDATION_PATH, source.volumeValidationArtifact(),
                snapshot.validationChecksum(), staging, token);
        for (TileSnapshot tile : snapshot.tiles()) {
            copyAndAdd(result, paths, "volume-tile." + tile.artifact().tileId(),
                    SparseVolumeReleaseCapabilityVerifierV2.TILE_METADATA_TYPE,
                    "volume/tiles/" + tile.artifact().tileId() + ".json", tile.source().metadata(),
                    tile.artifact().canonicalChecksum(), staging, token);
            copyAndAdd(result, paths, "volume-schematic." + tile.artifact().tileId(),
                    SparseVolumeReleaseCapabilityVerifierV2.SCHEMATIC_TYPE,
                    "volume/tiles/" + tile.artifact().schematicPath(), tile.source().schematic(),
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
        if (!paths.add(canonical)) {
            throw new IOException("sparse-volume Release source maps multiple artifacts to one path");
        }
        Path target = staging.resolve(canonical).normalize();
        if (!target.startsWith(staging)) {
            throw new IOException("sparse-volume Release artifact path escapes staging root");
        }
        Files.createDirectories(Objects.requireNonNull(target.getParent()));
        FileFingerprint before = fingerprint(source);
        Files.copy(source, target, LinkOption.NOFOLLOW_LINKS);
        requireStable(source, before);
        requireSafeRegular(target);
        descriptors.add(new ReleaseArtifactDescriptorV2(id, type, 1, canonical, Files.size(target),
                Sha256.file(target), semanticChecksum));
    }

    private void ensureDiskBudget(Path root, long sourceBytes, boolean createZip) throws IOException {
        long multiplier = createZip ? 3L : 2L;
        long expected;
        try {
            expected = Math.addExact(Math.multiplyExact(sourceBytes, multiplier), 1024L * 1024L);
        } catch (ArithmeticException exception) {
            throw new IOException("sparse-volume Release disk estimate overflow", exception);
        }
        if (sourceBytes > limits.maximumDirectoryBytes()
                || expected > limits.maximumDirectoryBytes() + limits.maximumZipBytes()
                || Files.getFileStore(root).getUsableSpace() < expected) {
            throw new IOException("insufficient disk budget for sparse-volume Release staging and publish");
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
            throw new IOException("sparse-volume Release ZIP exceeds its compressed byte budget");
        }
    }

    private static void forceTree(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                forceFile(file);
            }
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
            throw new IOException("filesystem does not support required sparse-volume Release atomic publish", exception);
        }
    }

    private static void requireSafeRegular(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("sparse-volume Release source artifact must be a regular non-symbolic file");
        }
    }

    private static FileFingerprint fingerprint(Path path) throws IOException {
        requireSafeRegular(path);
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return new FileFingerprint(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime());
    }

    private static void requireStable(Path path, FileFingerprint before) throws IOException {
        if (!before.equals(fingerprint(path))) {
            throw new IOException("sparse-volume Release source changed while it was being staged");
        }
    }

    private static long add(long current, long addition) throws IOException {
        try {
            return Math.addExact(current, addition);
        } catch (ArithmeticException exception) {
            throw new IOException("sparse-volume Release source byte total overflow", exception);
        }
    }

    private record VolumeSnapshot(
            String sdfChecksum,
            String csgChecksum,
            String aabbChecksum,
            String validationChecksum,
            List<TileSnapshot> tiles,
            long sourceBytes
    ) {
    }

    private record TileSnapshot(SparseVolumeReleaseSourceV2.TileSource source, OfflineTileArtifactV2 artifact) {
    }

    private record FileFingerprint(Object fileKey, long size, java.nio.file.attribute.FileTime modified) {
    }
}
