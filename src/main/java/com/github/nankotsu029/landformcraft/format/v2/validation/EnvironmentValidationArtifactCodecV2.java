package com.github.nankotsu029.landformcraft.format.v2.validation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.EnvironmentValidationArtifactV2;
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

/** Strict canonical codec for the bounded V2-4-13 environment validation evidence artifact. */
public final class EnvironmentValidationArtifactCodecV2 {
    public static final String SCHEMA = "environment-validation-artifact-v2.schema.json";
    public static final long MAXIMUM_BYTES = 512L * 1024L;

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public EnvironmentValidationArtifactV2 seal(EnvironmentValidationArtifactV2 artifact) {
        Objects.requireNonNull(artifact, "artifact");
        String checksum = canonicalChecksum(artifact);
        if (artifact.hasPendingCanonicalChecksum()) {
            return artifact.withCanonicalChecksum(checksum);
        }
        if (!checksum.equals(artifact.canonicalChecksum())) {
            throw new IllegalArgumentException("environment validation artifact canonical checksum mismatch");
        }
        return artifact;
    }

    public EnvironmentValidationArtifactV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) < 2 || Files.size(path) > MAXIMUM_BYTES) {
            throw new IOException("environment validation artifact must be a bounded regular non-symbolic file");
        }
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            return parse(mapper.readTree(input), path.toString());
        }
    }

    public void write(Path path, EnvironmentValidationArtifactV2 artifact) throws IOException {
        EnvironmentValidationArtifactV2 sealed = seal(artifact);
        ObjectNode tree = mapper.valueToTree(sealed);
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "environment validation artifact requires a parent");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("environment validation artifact parent must be a non-symbolic directory");
        }
        Path temporary = Files.createTempFile(parent, ".environment-validation-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException("environment validation artifact changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for environment validation artifact", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private EnvironmentValidationArtifactV2 parse(JsonNode tree, String documentName) throws IOException {
        validator.validate(SCHEMA, documentName, tree);
        EnvironmentValidationArtifactV2 artifact = mapper.treeToValue(tree, EnvironmentValidationArtifactV2.class);
        if (artifact.hasPendingCanonicalChecksum()
                || !canonicalChecksum(artifact).equals(artifact.canonicalChecksum())) {
            throw new IOException("environment validation artifact canonical checksum mismatch: " + documentName);
        }
        return artifact;
    }

    private String canonicalChecksum(EnvironmentValidationArtifactV2 artifact) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }
}
