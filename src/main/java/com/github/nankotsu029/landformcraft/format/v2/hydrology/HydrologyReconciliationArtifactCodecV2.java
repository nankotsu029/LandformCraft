package com.github.nankotsu029.landformcraft.format.v2.hydrology;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Strict canonical JSON codec for V2-3-12 residual/failure artifacts. */
public final class HydrologyReconciliationArtifactCodecV2 {
    public static final String SCHEMA = "hydrology-reconciliation-artifact-v2.schema.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public HydrologyReconciliationArtifactV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("hydrology reconciliation artifact must be a regular file");
        }
        if (Files.size(path) > LandformDataCodec.MAX_DOCUMENT_BYTES) {
            throw new IOException("hydrology reconciliation artifact exceeds its byte budget");
        }
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(LandformDataCodec.MAX_DOCUMENT_BYTES + 1L));
            if (bytes.length > LandformDataCodec.MAX_DOCUMENT_BYTES) {
                throw new IOException("hydrology reconciliation artifact exceeds its byte budget");
            }
            return parse(mapper.readTree(bytes), path.toString());
        }
    }

    public HydrologyReconciliationArtifactV2 read(String input, String documentName) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(documentName, "documentName");
        if (input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > LandformDataCodec.MAX_DOCUMENT_BYTES) {
            throw new IOException("hydrology reconciliation artifact exceeds its byte budget");
        }
        return parse(mapper.readTree(input), documentName);
    }

    public String canonical(HydrologyReconciliationArtifactV2 artifact) {
        return CanonicalJsonV2.string(mapper.valueToTree(seal(artifact)));
    }

    public String checksum(HydrologyReconciliationArtifactV2 artifact) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public HydrologyReconciliationArtifactV2 seal(HydrologyReconciliationArtifactV2 artifact) {
        return artifact.withCanonicalChecksum(checksum(artifact));
    }

    public void write(Path path, HydrologyReconciliationArtifactV2 artifact) throws IOException {
        Objects.requireNonNull(path, "path");
        HydrologyReconciliationArtifactV2 sealed = seal(artifact);
        verifyCanonicalChecksum(sealed);
        JsonNode tree = mapper.valueToTree(sealed);
        validator.validate(SCHEMA, path.toString(), tree);
        byte[] bytes = CanonicalJsonV2.bytes(tree);
        if (bytes.length > LandformDataCodec.MAX_DOCUMENT_BYTES
                || bytes.length > sealed.resources().maximumArtifactBytes()) {
            throw new IOException("hydrology reconciliation artifact exceeds its byte budget");
        }
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(absolute.getParent(), "output path must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "lfc-hydrology-reconciliation-", ".tmp");
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private HydrologyReconciliationArtifactV2 parse(JsonNode node, String documentName) throws IOException {
        if (node == null) throw new IOException("hydrology reconciliation artifact is empty: " + documentName);
        validator.validate(SCHEMA, documentName, node);
        HydrologyReconciliationArtifactV2 artifact =
                mapper.treeToValue(node, HydrologyReconciliationArtifactV2.class);
        verifyCanonicalChecksum(artifact);
        return artifact;
    }

    private void verifyCanonicalChecksum(HydrologyReconciliationArtifactV2 artifact) throws IOException {
        if (!checksum(artifact).equals(artifact.canonicalChecksum())) {
            throw new IOException("hydrology reconciliation canonical checksum mismatch");
        }
    }
}
