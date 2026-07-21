package com.github.nankotsu029.landformcraft.format.v2.job;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobSnapshotV2;
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

/** Strict canonical codec for v2 export job snapshots (V2-12-09). */
public final class ExportJobCodecV2 {
    public static final String SCHEMA = "generation-job-v2.schema.json";
    public static final long MAXIMUM_BYTES = 8L * 1024L;

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public ExportJobSnapshotV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) < 2 || Files.size(path) > MAXIMUM_BYTES) {
            throw new IOException("v2 export job must be a bounded regular non-symbolic file: " + path);
        }
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            JsonNode tree = readTree(input);
            validator.validate(SCHEMA, path.toString(), tree);
            return mapper.treeToValue(tree, ExportJobSnapshotV2.class);
        }
    }

    /**
     * Publishes one snapshot through a staged read-back, so a torn write can never be observed as a
     * job state. Every transition replaces the whole file.
     */
    public void write(Path path, ExportJobSnapshotV2 snapshot) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(snapshot, "snapshot");
        JsonNode tree = mapper.valueToTree(snapshot);
        validator.validate(SCHEMA, path.toString(), tree);
        byte[] bytes = CanonicalJsonV2.bytes(tree);
        if (bytes.length > MAXIMUM_BYTES) {
            throw new IOException("v2 export job exceeds byte budget: " + path);
        }
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "v2 export job requires a parent");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("v2 export job parent must be a non-symbolic directory");
        }
        Path temporary = Files.createTempFile(parent, ".export-job-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try (InputStream input = Files.newInputStream(temporary)) {
                JsonNode readBack = readTree(input);
                validator.validate(SCHEMA, temporary.toString(), readBack);
                if (!readBack.equals(tree)) {
                    throw new IOException("v2 export job failed strict read-back: " + path);
                }
            }
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private JsonNode readTree(InputStream input) throws IOException {
        return mapper.readTree(input);
    }
}
