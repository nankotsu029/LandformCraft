package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Publishes an empty-capability Release format 2 core after strict staged directory and ZIP verification. */
public final class ReleaseCorePublisherV2 {
    private final ReleaseManifestCodecV2 manifestCodec = new ReleaseManifestCodecV2();
    private final ReleaseArtifactCatalogV2 catalog = new ReleaseArtifactCatalogV2();
    private final ReleaseV2Limits limits;
    private final ReleaseCoreVerifierV2 verifier;

    public ReleaseCorePublisherV2() {
        this(ReleaseV2Limits.defaults());
    }

    public ReleaseCorePublisherV2(ReleaseV2Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.verifier = new ReleaseCoreVerifierV2(limits);
    }

    public ReleaseCoreArtifactsV2 publish(
            Path exportsRoot,
            ReleaseManifestV2 manifest,
            boolean createZip,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(exportsRoot, "exportsRoot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        ReleaseManifestV2 sealed = manifestCodec.seal(Objects.requireNonNull(manifest, "manifest"));
        catalog.verifyCoreManifest(sealed);
        Path root = exportsRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Release format 2 export root must be a non-symbolic directory");
        }
        Path finalDirectory = root.resolve(sealed.releaseId());
        Path finalZip = root.resolve(sealed.releaseId() + ".zip");
        if (Files.exists(finalDirectory, LinkOption.NOFOLLOW_LINKS)
                || createZip && Files.exists(finalZip, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Release format 2 target already exists");
        }
        long manifestBytes = canonicalManifestByteLength(sealed);
        ensureDiskBudget(root, manifestBytes, createZip);

        Path stagingDirectory = Files.createTempDirectory(root, ".release-v2-stage-");
        Path stagingZip = root.resolve(".release-v2-" + sealed.releaseId() + ".tmp.zip");
        boolean directoryPublished = false;
        boolean zipPublished = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            manifestCodec.write(stagingDirectory.resolve("manifest.json"), sealed);
            verifier.verifyDirectory(stagingDirectory, cancellationToken);
            forceTree(stagingDirectory);
            cancellationToken.throwIfCancellationRequested();
            moveAtomically(stagingDirectory, finalDirectory);
            directoryPublished = true;

            if (!createZip) {
                return new ReleaseCoreArtifactsV2(sealed.releaseId(), finalDirectory, Optional.empty());
            }
            createZip(finalDirectory, stagingZip, cancellationToken);
            verifier.verify(stagingZip, cancellationToken);
            forceFile(stagingZip);
            cancellationToken.throwIfCancellationRequested();
            moveAtomically(stagingZip, finalZip);
            zipPublished = true;
            return new ReleaseCoreArtifactsV2(sealed.releaseId(), finalDirectory, Optional.of(finalZip));
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

    private void createZip(Path releaseDirectory, Path stagingZip, CancellationToken cancellationToken)
            throws IOException {
        List<Path> files;
        try (var stream = Files.walk(releaseDirectory)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(java.util.Comparator.comparing(
                            path -> releaseDirectory.relativize(path).toString().replace('\\', '/')))
                    .toList();
        }
        try (var file = Files.newOutputStream(stagingZip, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             var output = new ZipOutputStream(new BufferedOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
            for (Path path : files) {
                cancellationToken.throwIfCancellationRequested();
                String relative = releaseDirectory.relativize(path).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(relative);
                entry.setTime(0L);
                output.putNextEntry(entry);
                Files.copy(path, output);
                output.closeEntry();
            }
        }
        if (Files.size(stagingZip) > limits.maximumZipBytes()) {
            throw new IOException("Release format 2 ZIP exceeds its compressed byte budget");
        }
    }

    private void ensureDiskBudget(Path root, long manifestBytes, boolean createZip) throws IOException {
        long expected = Math.addExact(manifestBytes * (createZip ? 4L : 2L), 1024L * 1024L);
        if (expected > limits.maximumDirectoryBytes() + limits.maximumZipBytes()
                || Files.getFileStore(root).getUsableSpace() < expected) {
            throw new IOException("insufficient disk budget for Release format 2 staging and publish");
        }
    }

    private long canonicalManifestByteLength(ReleaseManifestV2 manifest) throws IOException {
        Path temporary = Files.createTempFile("release-v2-manifest-size-", ".json");
        try {
            manifestCodec.write(temporary, manifest);
            return Files.size(temporary);
        } finally {
            Files.deleteIfExists(temporary);
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
            throw new IOException("filesystem does not support required Release format 2 atomic publish", exception);
        }
    }
}
