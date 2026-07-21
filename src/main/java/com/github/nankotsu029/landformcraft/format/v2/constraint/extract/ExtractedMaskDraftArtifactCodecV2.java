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
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskDraftArtifactV2;
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

/** Strict codec and read-back verifier for extracted mask draft artifact bundles. */
public final class ExtractedMaskDraftArtifactCodecV2 {
    public static final String SCHEMA = "extracted-mask-draft-v2.schema.json";
    public static final String INDEX_FILE_NAME = "extracted-mask-draft-v2.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public ExtractedMaskDraftArtifactV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        JsonNode tree;
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            tree = mapper.readTree(input);
        }
        validator.validate(SCHEMA, path.toString(), tree);
        ExtractedMaskDraftArtifactV2 artifact = mapper.treeToValue(tree, ExtractedMaskDraftArtifactV2.class);
        return verifyChecksum(artifact);
    }

    public ExtractedMaskDraftArtifactV2 readAndVerify(
            Path indexPath,
            Path artifactRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(indexPath, "indexPath");
        Objects.requireNonNull(artifactRoot, "artifactRoot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (!indexPath.getFileName().toString().equals(INDEX_FILE_NAME)) {
            throw new IOException("extracted mask draft index filename must be " + INDEX_FILE_NAME);
        }
        requireSafeDirectory(artifactRoot);
        if (Files.isSymbolicLink(indexPath) || !Files.isRegularFile(indexPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted mask draft index must be a regular non-symbolic file");
        }
        ExtractedMaskDraftArtifactV2 artifact = read(indexPath);
        verifyDirectoryEntries(artifactRoot, artifact);
        cancellationToken.throwIfCancellationRequested();
        byte[] classes = readRaster(artifactRoot.resolve(artifact.classesPath()), artifact.classesByteLength());
        byte[] confidence = readRaster(
                artifactRoot.resolve(artifact.confidencePath()), artifact.confidenceByteLength());
        if (!Sha256.bytes(classes).equals(artifact.classesSha256())
                || !Sha256.bytes(confidence).equals(artifact.confidenceSha256())) {
            throw new IOException("extracted mask draft sidecar checksum mismatch");
        }
        ExtractedMaskDraftV2 draft = ExtractedMaskDraftV2.restore(
                artifact.width(),
                artifact.length(),
                artifact.algorithmVersion(),
                artifact.sourceChecksum(),
                artifact.semanticChecksum(),
                classes,
                confidence,
                artifact.waterCells(),
                artifact.landCells(),
                artifact.unknownCells());
        if (!draft.semanticChecksum().equals(artifact.semanticChecksum())) {
            throw new IOException("extracted mask draft semantic checksum mismatch after restore");
        }
        cancellationToken.throwIfCancellationRequested();
        return artifact;
    }

    public ExtractedMaskDraftV2 loadDraft(
            Path indexPath,
            Path artifactRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        ExtractedMaskDraftArtifactV2 artifact = readAndVerify(indexPath, artifactRoot, cancellationToken);
        byte[] classes = Files.readAllBytes(artifactRoot.resolve(artifact.classesPath()));
        byte[] confidence = Files.readAllBytes(artifactRoot.resolve(artifact.confidencePath()));
        return ExtractedMaskDraftV2.restore(
                artifact.width(),
                artifact.length(),
                artifact.algorithmVersion(),
                artifact.sourceChecksum(),
                artifact.semanticChecksum(),
                classes,
                confidence,
                artifact.waterCells(),
                artifact.landCells(),
                artifact.unknownCells());
    }

    public ExtractedMaskDraftArtifactV2 seal(ExtractedMaskDraftArtifactV2 artifact) {
        Objects.requireNonNull(artifact, "artifact");
        String checksum = canonicalChecksum(artifact);
        if (artifact.hasPendingCanonicalChecksum()) {
            return artifact.withCanonicalChecksum(checksum);
        }
        if (!checksum.equals(artifact.canonicalChecksum())) {
            throw new IllegalArgumentException("extracted mask draft artifact canonical checksum mismatch");
        }
        return artifact;
    }

    public void write(Path path, ExtractedMaskDraftArtifactV2 artifact) throws IOException {
        ExtractedMaskDraftArtifactV2 sealed = seal(artifact);
        ObjectNode tree = mapper.valueToTree(sealed);
        if (sealed.sourceRelativePath() == null) {
            tree.remove("sourceRelativePath");
        }
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "extracted mask draft index must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".extracted-mask-draft-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException("extracted mask draft index changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for extracted mask draft index", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private ExtractedMaskDraftArtifactV2 verifyChecksum(ExtractedMaskDraftArtifactV2 artifact)
            throws IOException {
        if (artifact.hasPendingCanonicalChecksum()) {
            throw new IOException("extracted mask draft artifact is not canonically sealed");
        }
        if (!canonicalChecksum(artifact).equals(artifact.canonicalChecksum())) {
            throw new IOException("extracted mask draft artifact canonical checksum mismatch");
        }
        return artifact;
    }

    private String canonicalChecksum(ExtractedMaskDraftArtifactV2 artifact) {
        ObjectNode node = mapper.valueToTree(artifact);
        node.remove("canonicalChecksum");
        if (artifact.sourceRelativePath() == null) {
            node.remove("sourceRelativePath");
        }
        return CanonicalJsonV2.checksum(node);
    }

    private static void requireSafeDirectory(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted mask draft root must be a non-symbolic directory");
        }
    }

    private static void verifyDirectoryEntries(Path root, ExtractedMaskDraftArtifactV2 artifact)
            throws IOException {
        Set<String> expected = Set.of(
                INDEX_FILE_NAME, artifact.classesPath(), artifact.confidencePath());
        try (var paths = Files.list(root)) {
            Set<String> actual = new HashSet<>();
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("extracted mask draft root contains an unsafe entry");
                }
                actual.add(path.getFileName().toString());
            }
            if (!actual.equals(expected)) {
                throw new IOException("extracted mask draft index and directory entries differ");
            }
        }
    }

    private static byte[] readRaster(Path path, long expectedBytes) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted mask draft raster must be a regular non-symbolic file");
        }
        long size = Files.size(path);
        if (size != expectedBytes) {
            throw new IOException("extracted mask draft raster byte length mismatch");
        }
        return Files.readAllBytes(path);
    }
}
