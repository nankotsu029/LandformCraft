package com.github.nankotsu029.landformcraft.format.v2.tile;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Strict canonical JSON codec for standalone V2 offline tile metadata. */
public final class OfflineTileArtifactCodecV2 {
    public static final String SCHEMA = "offline-tile-artifact-v2.schema.json";
    private static final long MAXIMUM_METADATA_BYTES = 256L * 1024L;

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public OfflineTileArtifactV2 seal(OfflineTileArtifactV2 artifact) {
        Objects.requireNonNull(artifact, "artifact");
        String checksum = canonicalChecksum(artifact);
        if (artifact.hasPendingCanonicalChecksum()) return artifact.withCanonicalChecksum(checksum);
        if (!checksum.equals(artifact.canonicalChecksum())) {
            throw new IllegalArgumentException("offline tile canonical checksum mismatch");
        }
        return artifact;
    }

    public OfflineTileArtifactV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) < 2 || Files.size(path) > MAXIMUM_METADATA_BYTES) {
            throw new IOException("offline tile metadata must be a bounded regular non-symbolic file");
        }
        JsonNode tree;
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            tree = mapper.readTree(input);
        }
        validator.validate(SCHEMA, path.toString(), tree);
        OfflineTileArtifactV2 artifact = mapper.treeToValue(tree, OfflineTileArtifactV2.class);
        if (artifact.hasPendingCanonicalChecksum() || !canonicalChecksum(artifact).equals(artifact.canonicalChecksum())) {
            throw new IOException("offline tile canonical checksum mismatch");
        }
        return artifact;
    }

    public void write(Path path, OfflineTileArtifactV2 artifact) throws IOException {
        OfflineTileArtifactV2 sealed = seal(artifact);
        ObjectNode tree = mapper.valueToTree(sealed);
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "offline tile metadata requires a parent");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("offline tile metadata parent must be a non-symbolic directory");
        }
        Path temporary = Files.createTempFile(parent, ".offline-tile-", ".json.tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException("offline tile metadata changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for offline tile metadata", exception);
            }
            published = true;
        } finally {
            if (!published) Files.deleteIfExists(temporary);
        }
    }

    private String canonicalChecksum(OfflineTileArtifactV2 artifact) {
        ObjectNode node = mapper.valueToTree(artifact);
        node.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(node);
    }
}
