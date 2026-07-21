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
import com.github.nankotsu029.landformcraft.model.v2.ExtractedHeightGuideDraftArtifactV2;
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

/** Strict codec and read-back verifier for extracted height-guide draft artifact bundles. */
public final class ExtractedHeightGuideDraftArtifactCodecV2 {
    public static final String SCHEMA = "extracted-height-guide-draft-v2.schema.json";
    public static final String INDEX_FILE_NAME = "extracted-height-guide-draft-v2.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public ExtractedHeightGuideDraftArtifactV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        JsonNode tree;
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            tree = mapper.readTree(input);
        }
        validator.validate(SCHEMA, path.toString(), tree);
        ExtractedHeightGuideDraftArtifactV2 artifact =
                mapper.treeToValue(tree, ExtractedHeightGuideDraftArtifactV2.class);
        return verifyChecksum(artifact);
    }

    public ExtractedHeightGuideDraftArtifactV2 readAndVerify(
            Path indexPath,
            Path artifactRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(indexPath, "indexPath");
        Objects.requireNonNull(artifactRoot, "artifactRoot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (!indexPath.getFileName().toString().equals(INDEX_FILE_NAME)) {
            throw new IOException("extracted height guide draft index filename must be " + INDEX_FILE_NAME);
        }
        requireSafeDirectory(artifactRoot);
        if (Files.isSymbolicLink(indexPath) || !Files.isRegularFile(indexPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted height guide draft index must be a regular non-symbolic file");
        }
        ExtractedHeightGuideDraftArtifactV2 artifact = read(indexPath);
        verifyDirectoryEntries(artifactRoot, artifact);
        cancellationToken.throwIfCancellationRequested();
        byte[] samples = readRaster(artifactRoot.resolve(artifact.samplesPath()), artifact.samplesByteLength());
        byte[] confidence = readRaster(
                artifactRoot.resolve(artifact.confidencePath()), artifact.confidenceByteLength());
        if (!Sha256.bytes(samples).equals(artifact.samplesSha256())
                || !Sha256.bytes(confidence).equals(artifact.confidenceSha256())) {
            throw new IOException("extracted height guide draft sidecar checksum mismatch");
        }
        ExtractedHeightGuideDraftV2 draft = ExtractedHeightGuideDraftV2.restore(
                artifact.width(),
                artifact.length(),
                artifact.algorithmVersion(),
                artifact.sourceChecksum(),
                artifact.semanticChecksum(),
                artifact.sampleSpaceDeclaration(),
                samples,
                confidence,
                artifact.validCells(),
                artifact.noDataCells());
        if (!draft.semanticChecksum().equals(artifact.semanticChecksum())) {
            throw new IOException("extracted height guide draft semantic checksum mismatch after restore");
        }
        cancellationToken.throwIfCancellationRequested();
        return artifact;
    }

    public ExtractedHeightGuideDraftV2 loadDraft(
            Path indexPath,
            Path artifactRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        ExtractedHeightGuideDraftArtifactV2 artifact = readAndVerify(indexPath, artifactRoot, cancellationToken);
        byte[] samples = Files.readAllBytes(artifactRoot.resolve(artifact.samplesPath()));
        byte[] confidence = Files.readAllBytes(artifactRoot.resolve(artifact.confidencePath()));
        return ExtractedHeightGuideDraftV2.restore(
                artifact.width(),
                artifact.length(),
                artifact.algorithmVersion(),
                artifact.sourceChecksum(),
                artifact.semanticChecksum(),
                artifact.sampleSpaceDeclaration(),
                samples,
                confidence,
                artifact.validCells(),
                artifact.noDataCells());
    }

    public ExtractedHeightGuideDraftArtifactV2 seal(ExtractedHeightGuideDraftArtifactV2 artifact) {
        Objects.requireNonNull(artifact, "artifact");
        String checksum = canonicalChecksum(artifact);
        if (artifact.hasPendingCanonicalChecksum()) {
            return artifact.withCanonicalChecksum(checksum);
        }
        if (!checksum.equals(artifact.canonicalChecksum())) {
            throw new IllegalArgumentException(
                    "extracted height guide draft artifact canonical checksum mismatch");
        }
        return artifact;
    }

    public void write(Path path, ExtractedHeightGuideDraftArtifactV2 artifact) throws IOException {
        ExtractedHeightGuideDraftArtifactV2 sealed = seal(artifact);
        ObjectNode tree = mapper.valueToTree(sealed);
        if (sealed.sourceRelativePath() == null) {
            tree.remove("sourceRelativePath");
        }
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(
                target.getParent(), "extracted height guide draft index must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".extracted-height-guide-draft-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException("extracted height guide draft index changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "atomic move is required for extracted height guide draft index", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private ExtractedHeightGuideDraftArtifactV2 verifyChecksum(ExtractedHeightGuideDraftArtifactV2 artifact)
            throws IOException {
        if (artifact.hasPendingCanonicalChecksum()) {
            throw new IOException("extracted height guide draft artifact is not canonically sealed");
        }
        if (!canonicalChecksum(artifact).equals(artifact.canonicalChecksum())) {
            throw new IOException("extracted height guide draft artifact canonical checksum mismatch");
        }
        return artifact;
    }

    private String canonicalChecksum(ExtractedHeightGuideDraftArtifactV2 artifact) {
        ObjectNode node = mapper.valueToTree(artifact);
        node.remove("canonicalChecksum");
        if (artifact.sourceRelativePath() == null) {
            node.remove("sourceRelativePath");
        }
        return CanonicalJsonV2.checksum(node);
    }

    private static void requireSafeDirectory(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted height guide draft root must be a non-symbolic directory");
        }
    }

    private static void verifyDirectoryEntries(Path root, ExtractedHeightGuideDraftArtifactV2 artifact)
            throws IOException {
        Set<String> expected = Set.of(
                INDEX_FILE_NAME, artifact.samplesPath(), artifact.confidencePath());
        try (var paths = Files.list(root)) {
            Set<String> actual = new HashSet<>();
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("extracted height guide draft root contains an unsafe entry");
                }
                actual.add(path.getFileName().toString());
            }
            if (!actual.equals(expected)) {
                throw new IOException("extracted height guide draft index and directory entries differ");
            }
        }
    }

    private static byte[] readRaster(Path path, long expectedBytes) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted height guide draft raster must be a regular non-symbolic file");
        }
        long size = Files.size(path);
        if (size != expectedBytes) {
            throw new IOException("extracted height guide draft raster byte length mismatch");
        }
        return Files.readAllBytes(path);
    }
}
