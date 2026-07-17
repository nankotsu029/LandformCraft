package com.github.nankotsu029.landformcraft.format.v2.validation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyValidationArtifactV2;
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

/** Strict canonical codec for the bounded V2-3 hydrology validation evidence artifact. */
public final class HydrologyValidationArtifactCodecV2 {
    public static final String SCHEMA = "hydrology-validation-artifact-v2.schema.json";
    public static final long MAXIMUM_BYTES = 512L * 1024L;

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public HydrologyValidationArtifactV2 seal(HydrologyValidationArtifactV2 artifact) {
        Objects.requireNonNull(artifact, "artifact");
        String checksum = canonicalChecksum(artifact);
        if (artifact.hasPendingCanonicalChecksum()) return artifact.withCanonicalChecksum(checksum);
        if (!checksum.equals(artifact.canonicalChecksum())) {
            throw new IllegalArgumentException("hydrology validation artifact canonical checksum mismatch");
        }
        return artifact;
    }

    public HydrologyValidationArtifactV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) < 2 || Files.size(path) > MAXIMUM_BYTES) {
            throw new IOException("hydrology validation artifact must be a bounded regular non-symbolic file");
        }
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            return parse(mapper.readTree(input), path.toString());
        }
    }

    public void write(Path path, HydrologyValidationArtifactV2 artifact) throws IOException {
        HydrologyValidationArtifactV2 sealed = seal(artifact);
        ObjectNode tree = mapper.valueToTree(sealed);
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "hydrology validation artifact requires a parent");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("hydrology validation artifact parent must be a non-symbolic directory");
        }
        Path temporary = Files.createTempFile(parent, ".hydrology-validation-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException("hydrology validation artifact changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for hydrology validation artifact", exception);
            }
            published = true;
        } finally {
            if (!published) Files.deleteIfExists(temporary);
        }
    }

    private HydrologyValidationArtifactV2 parse(JsonNode tree, String documentName) throws IOException {
        validator.validate(SCHEMA, documentName, tree);
        HydrologyValidationArtifactV2 artifact = mapper.treeToValue(tree, HydrologyValidationArtifactV2.class);
        if (artifact.hasPendingCanonicalChecksum()
                || !canonicalChecksum(artifact).equals(artifact.canonicalChecksum())) {
            throw new IOException("hydrology validation artifact canonical checksum mismatch: " + documentName);
        }
        return artifact;
    }

    private String canonicalChecksum(HydrologyValidationArtifactV2 artifact) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }
}
