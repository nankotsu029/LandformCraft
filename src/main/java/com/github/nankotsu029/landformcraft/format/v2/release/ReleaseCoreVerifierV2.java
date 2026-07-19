package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Strict, bounded reader for the capability-neutral Release format 2 core. */
public final class ReleaseCoreVerifierV2 {
    private final ReleaseManifestCodecV2 manifestCodec = new ReleaseManifestCodecV2();
    private final ReleaseArtifactCatalogV2 catalog = new ReleaseArtifactCatalogV2();
    private final ReleaseV2Limits limits;

    public ReleaseCoreVerifierV2() {
        this(ReleaseV2Limits.defaults());
    }

    public ReleaseCoreVerifierV2(ReleaseV2Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public ReleaseCoreVerificationV2 verify(Path path) throws IOException {
        return verify(path, () -> false);
    }

    public ReleaseCoreVerificationV2 verify(Path path, CancellationToken cancellationToken) throws IOException {
        try (VerifiedReleaseViewV2 view = openVerified(path, cancellationToken)) {
            return view.verification();
        }
    }

    public VerifiedReleaseViewV2 openVerified(Path path, CancellationToken cancellationToken) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(normalized)) {
            ReleaseCoreVerificationV2 verification = verifyDirectory(normalized, cancellationToken);
            return new VerifiedReleaseViewV2(normalized, verification, false);
        }
        if (Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(normalized)
                && normalized.getFileName().toString().endsWith(".zip")) {
            return openVerifiedZip(normalized, cancellationToken);
        }
        throw new IOException("Release format 2 input must be a non-symbolic directory or .zip file: " + path);
    }

    public ReleaseCoreVerificationV2 verifyDirectory(Path releaseDirectory) throws IOException {
        return verifyDirectory(releaseDirectory, () -> false);
    }

    public ReleaseCoreVerificationV2 verifyDirectory(Path releaseDirectory, CancellationToken cancellationToken)
            throws IOException {
        Objects.requireNonNull(releaseDirectory, "releaseDirectory");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        Path root = releaseDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("Release format 2 root must be a non-symbolic directory");
        }
        CollectedFiles files = collectFiles(root, cancellationToken);
        Path manifestPath = files.paths().get("manifest.json");
        if (manifestPath == null) {
            throw new IOException("Release format 2 manifest.json is missing");
        }
        FileFingerprint manifestBefore = fingerprint(manifestPath);
        ReleaseManifestV2 manifest = manifestCodec.read(manifestPath);
        requireStable(manifestPath, manifestBefore);
        verifyIndexedFiles(files, manifest, cancellationToken);
        catalog.verifyManifest(manifest, root, cancellationToken);
        cancellationToken.throwIfCancellationRequested();
        return new ReleaseCoreVerificationV2(root, manifest, files.paths().size(), files.totalBytes());
    }

    private void verifyIndexedFiles(
            CollectedFiles files,
            ReleaseManifestV2 manifest,
            CancellationToken cancellationToken
    ) throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("manifest.json");
        for (ReleaseArtifactDescriptorV2 artifact : manifest.artifacts()) {
            if (!expected.add(artifact.path())) {
                throw new IOException("Release format 2 artifact index has a duplicate path");
            }
        }
        if (!expected.equals(files.paths().keySet())) {
            throw new IOException("Release format 2 directory contains a missing or index-external file");
        }
        for (ReleaseArtifactDescriptorV2 artifact : manifest.artifacts()) {
            cancellationToken.throwIfCancellationRequested();
            Path file = files.paths().get(artifact.path());
            if (file == null) {
                throw new IOException("Release format 2 indexed artifact is missing: " + artifact.artifactId());
            }
            FileFingerprint before = fingerprint(file);
            boolean matches = before.size() == artifact.byteLength()
                    && Sha256.file(file).equals(artifact.artifactChecksum());
            requireStable(file, before);
            if (!matches) {
                throw new IOException("Release format 2 artifact checksum or byte length mismatch: "
                        + artifact.artifactId());
            }
        }
    }

    private VerifiedReleaseViewV2 openVerifiedZip(Path zip, CancellationToken cancellationToken) throws IOException {
        if (Files.size(zip) > limits.maximumZipBytes()) {
            throw new IOException("Release format 2 ZIP exceeds its compressed byte budget");
        }
        Path parent = Objects.requireNonNull(zip.getParent(), "ZIP must have a parent directory");
        Path staging = Files.createTempDirectory(parent, ".release-v2-verify-");
        boolean opened = false;
        try {
            extractZip(zip, staging, cancellationToken);
            ReleaseCoreVerificationV2 result = verifyDirectory(staging, cancellationToken);
            opened = true;
            return VerifiedReleaseViewV2.owned(zip, staging, result);
        } finally {
            if (!opened) {
                deleteTree(staging);
            }
        }
    }

    private void extractZip(Path zip, Path target, CancellationToken cancellationToken) throws IOException {
        Set<String> names = new HashSet<>();
        Set<String> foldedNames = new HashSet<>();
        int entries = 0;
        long expanded = 0;
        byte[] buffer = new byte[limits.copyBufferBytes()];
        try (InputStream file = Files.newInputStream(zip);
             ZipInputStream input = new ZipInputStream(new BufferedInputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                cancellationToken.throwIfCancellationRequested();
                if (++entries > limits.maximumFileCount()) {
                    throw new IOException("Release format 2 ZIP contains too many entries");
                }
                if (entry.isDirectory()) {
                    throw new IOException("Release format 2 ZIP must not contain directory entries");
                }
                String name = ReleaseV2Paths.canonicalRelativePath(entry.getName());
                if (!names.add(name) || !foldedNames.add(name.toLowerCase(Locale.ROOT))) {
                    throw new IOException("Release format 2 ZIP contains duplicate or case-colliding entries");
                }
                long declaredSize = entry.getSize();
                if (declaredSize > limits.maximumArtifactBytes() || declaredSize > limits.maximumExpandedBytes()) {
                    throw new IOException("Release format 2 ZIP entry exceeds its declared byte budget");
                }
                Path output = target.resolve(name).normalize();
                if (!output.startsWith(target)) {
                    throw new IOException("Release format 2 ZIP entry escapes extraction root");
                }
                Files.createDirectories(output.getParent());
                long entryBytes = 0;
                try (var destination = Files.newOutputStream(output)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        cancellationToken.throwIfCancellationRequested();
                        if (read == 0) {
                            continue;
                        }
                        entryBytes = addBounded(entryBytes, read, limits.maximumArtifactBytes(),
                                "Release format 2 ZIP entry exceeds its byte budget");
                        expanded = addBounded(expanded, read, limits.maximumExpandedBytes(),
                                "Release format 2 ZIP expands beyond its byte budget");
                        destination.write(buffer, 0, read);
                    }
                }
                if (declaredSize >= 0 && declaredSize != entryBytes) {
                    throw new IOException("Release format 2 ZIP entry size changed while decoding");
                }
                input.closeEntry();
            }
        }
        if (entries == 0) {
            throw new IOException("Release format 2 ZIP is empty");
        }
    }

    private CollectedFiles collectFiles(Path root, CancellationToken cancellationToken) throws IOException {
        Map<String, Path> paths = new TreeMap<>();
        Set<String> folded = new HashSet<>();
        long[] total = {0L};
        int[] count = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                cancellationToken.throwIfCancellationRequested();
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("Release format 2 contains a symbolic-link directory");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                cancellationToken.throwIfCancellationRequested();
                if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) {
                    throw new IOException("Release format 2 contains a non-regular or symbolic-link file");
                }
                if (++count[0] > limits.maximumFileCount()) {
                    throw new IOException("Release format 2 contains too many files");
                }
                if (attributes.size() > limits.maximumArtifactBytes()) {
                    throw new IOException("Release format 2 artifact exceeds its byte budget");
                }
                total[0] = addBounded(total[0], attributes.size(), limits.maximumDirectoryBytes(),
                        "Release format 2 directory exceeds its byte budget");
                String relative = ReleaseV2Paths.canonicalRelativePath(
                        root.relativize(file).toString().replace('\\', '/'));
                if (paths.putIfAbsent(relative, file) != null || !folded.add(relative.toLowerCase(Locale.ROOT))) {
                    throw new IOException("Release format 2 contains duplicate or case-colliding files");
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return new CollectedFiles(Map.copyOf(paths), total[0]);
    }

    private static long addBounded(long current, long addition, long maximum, String message) throws IOException {
        if (addition < 0 || current > maximum - addition) {
            throw new IOException(message);
        }
        return current + addition;
    }

    private static FileFingerprint fingerprint(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Release format 2 file became a symbolic link");
        }
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("Release format 2 file is not regular");
        }
        return new FileFingerprint(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime());
    }

    private static void requireStable(Path path, FileFingerprint before) throws IOException {
        FileFingerprint after = fingerprint(path);
        if (!before.equals(after)) {
            throw new IOException("Release format 2 file changed while it was being verified: " + path);
        }
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<Path> paths = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.forEach(paths::add);
        }
        paths.sort(java.util.Comparator.reverseOrder());
        IOException failure = null;
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private record CollectedFiles(Map<String, Path> paths, long totalBytes) {
    }

    private record FileFingerprint(Object fileKey, long size, FileTime lastModifiedTime) {
    }
}
