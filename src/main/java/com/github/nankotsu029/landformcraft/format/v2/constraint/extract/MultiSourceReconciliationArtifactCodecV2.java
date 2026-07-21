package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Strict codec for multi-source reconciliation artifact bundles. */
public final class MultiSourceReconciliationArtifactCodecV2 {
    public static final String SCHEMA = "multi-source-reconciliation-v2.schema.json";
    public static final String INDEX_FILE_NAME = "multi-source-reconciliation-v2.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public MultiSourceReconciliationArtifactV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        JsonNode tree;
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            tree = mapper.readTree(input);
        }
        validator.validate(SCHEMA, path.toString(), tree);
        MultiSourceReconciliationArtifactV2 artifact =
                mapper.treeToValue(tree, MultiSourceReconciliationArtifactV2.class);
        return verifyChecksum(artifact);
    }

    public MultiSourceReconciliationArtifactV2 readAndVerify(
            Path indexPath,
            Path artifactRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(indexPath, "indexPath");
        Objects.requireNonNull(artifactRoot, "artifactRoot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (!indexPath.getFileName().toString().equals(INDEX_FILE_NAME)) {
            throw new IOException("multi-source reconciliation index filename must be " + INDEX_FILE_NAME);
        }
        requireSafeDirectory(artifactRoot);
        if (Files.isSymbolicLink(indexPath) || !Files.isRegularFile(indexPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("multi-source reconciliation index must be a regular non-symbolic file");
        }
        MultiSourceReconciliationArtifactV2 artifact = read(indexPath);
        verifyDirectoryEntries(artifactRoot, artifact);
        cancellationToken.throwIfCancellationRequested();
        verifyRaster(artifactRoot.resolve(artifact.resultPath()), artifact.resultByteLength(), artifact.resultSha256());
        verifyRaster(artifactRoot.resolve(artifact.conflictPath()), artifact.conflictByteLength(), artifact.conflictSha256());
        verifyRaster(
                artifactRoot.resolve(artifact.sourceDiffPath()),
                artifact.sourceDiffByteLength(),
                artifact.sourceDiffSha256());
        cancellationToken.throwIfCancellationRequested();
        return artifact;
    }

    public MultiSourceReconciliationArtifactV2 seal(MultiSourceReconciliationArtifactV2 artifact) {
        Objects.requireNonNull(artifact, "artifact");
        String checksum = canonicalChecksum(artifact);
        if (artifact.hasPendingCanonicalChecksum()) {
            return artifact.withCanonicalChecksum(checksum);
        }
        if (!checksum.equals(artifact.canonicalChecksum())) {
            throw new IllegalArgumentException("multi-source reconciliation canonical checksum mismatch");
        }
        return artifact;
    }

    public void write(Path path, MultiSourceReconciliationArtifactV2 artifact) throws IOException {
        MultiSourceReconciliationArtifactV2 sealed = seal(artifact);
        ObjectNode tree = mapper.valueToTree(sealed);
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "index must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".multi-source-reconciliation-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException("multi-source reconciliation index changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for multi-source reconciliation index", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private MultiSourceReconciliationArtifactV2 verifyChecksum(MultiSourceReconciliationArtifactV2 artifact)
            throws IOException {
        if (artifact.hasPendingCanonicalChecksum()) {
            throw new IOException("multi-source reconciliation artifact is not canonically sealed");
        }
        if (!canonicalChecksum(artifact).equals(artifact.canonicalChecksum())) {
            throw new IOException("multi-source reconciliation canonical checksum mismatch");
        }
        return artifact;
    }

    private String canonicalChecksum(MultiSourceReconciliationArtifactV2 artifact) {
        ObjectNode node = mapper.valueToTree(artifact);
        node.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(node);
    }

    private static void requireSafeDirectory(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("multi-source reconciliation root must be a non-symbolic directory");
        }
    }

    private static void verifyDirectoryEntries(Path root, MultiSourceReconciliationArtifactV2 artifact)
            throws IOException {
        Set<String> expected = Set.of(
                INDEX_FILE_NAME, artifact.resultPath(), artifact.conflictPath(), artifact.sourceDiffPath());
        try (var paths = Files.list(root)) {
            Set<String> actual = new HashSet<>();
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("multi-source reconciliation root contains an unsafe entry");
                }
                actual.add(path.getFileName().toString());
            }
            if (!actual.equals(expected)) {
                throw new IOException("multi-source reconciliation index and directory entries differ");
            }
        }
    }

    private static void verifyRaster(Path path, long expectedBytes, String expectedSha) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("multi-source reconciliation raster must be a regular non-symbolic file");
        }
        if (Files.size(path) != expectedBytes) {
            throw new IOException("multi-source reconciliation raster byte length mismatch");
        }
        if (!Sha256.bytes(Files.readAllBytes(path)).equals(expectedSha)) {
            throw new IOException("multi-source reconciliation raster checksum mismatch");
        }
    }
}
