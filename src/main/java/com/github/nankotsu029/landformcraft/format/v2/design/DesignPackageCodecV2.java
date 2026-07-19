package com.github.nankotsu029.landformcraft.format.v2.design;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignAuditV2;
import com.github.nankotsu029.landformcraft.model.v2.design.ImageDraftEvidenceV2;
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

/** Strict canonical codec for Release 2 design audit and image draft evidence artifacts. */
public final class DesignPackageCodecV2 {
    public static final String AUDIT_SCHEMA = "design-audit-v2.schema.json";
    public static final String DRAFT_EVIDENCE_SCHEMA = "image-draft-evidence-v2.schema.json";
    public static final long MAXIMUM_AUDIT_BYTES = 64L * 1024L;
    public static final long MAXIMUM_DRAFT_EVIDENCE_BYTES = 32L * 1024L;

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public DesignAuditV2 readAudit(Path path) throws IOException {
        return read(path, AUDIT_SCHEMA, MAXIMUM_AUDIT_BYTES, DesignAuditV2.class);
    }

    public void writeAudit(Path path, DesignAuditV2 audit) throws IOException {
        write(path, audit, AUDIT_SCHEMA, MAXIMUM_AUDIT_BYTES);
    }

    public ImageDraftEvidenceV2 readDraftEvidence(Path path) throws IOException {
        return read(path, DRAFT_EVIDENCE_SCHEMA, MAXIMUM_DRAFT_EVIDENCE_BYTES, ImageDraftEvidenceV2.class);
    }

    public void writeDraftEvidence(Path path, ImageDraftEvidenceV2 evidence) throws IOException {
        write(path, evidence, DRAFT_EVIDENCE_SCHEMA, MAXIMUM_DRAFT_EVIDENCE_BYTES);
    }

    private <T> T read(Path path, String schema, long maximumBytes, Class<T> type) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) < 2 || Files.size(path) > maximumBytes) {
            throw new IOException("design artifact must be a bounded regular non-symbolic file: " + path);
        }
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            JsonNode tree = readTree(input, maximumBytes);
            validator.validate(schema, path.toString(), tree);
            return mapper.treeToValue(tree, type);
        }
    }

    private void write(Path path, Object value, String schema, long maximumBytes) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(value, "value");
        JsonNode tree = mapper.valueToTree(value);
        if (tree instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            java.util.List<String> nullFields = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, JsonNode> entry : object.properties()) {
                if (entry.getValue().isNull()) {
                    nullFields.add(entry.getKey());
                }
            }
            nullFields.forEach(object::remove);
        }
        validator.validate(schema, path.toString(), tree);
        byte[] bytes = CanonicalJsonV2.bytes(tree);
        if (bytes.length > maximumBytes) {
            throw new IOException("design artifact exceeds byte budget: " + path);
        }
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "design artifact requires a parent");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("design artifact parent must be a non-symbolic directory");
        }
        Path temporary = Files.createTempFile(parent, ".design-artifact-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            JsonNode readBack = readTree(Files.newInputStream(temporary), maximumBytes);
            validator.validate(schema, temporary.toString(), readBack);
            if (!mapper.treeToValue(readBack, value.getClass()).equals(value)) {
                throw new IOException("design artifact changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for design artifact publication", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private JsonNode readTree(InputStream input, long maximumBytes) throws IOException {
        byte[] bytes = input.readNBytes(Math.toIntExact(maximumBytes + 1L));
        if (bytes.length > maximumBytes) {
            throw new IOException("design artifact exceeds byte budget");
        }
        JsonNode tree = mapper.readTree(bytes);
        if (tree == null) {
            throw new IOException("design artifact is empty");
        }
        return tree;
    }
}
