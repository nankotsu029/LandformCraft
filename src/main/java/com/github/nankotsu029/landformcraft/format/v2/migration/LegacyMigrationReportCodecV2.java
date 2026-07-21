package com.github.nankotsu029.landformcraft.format.v2.migration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationReportV2;
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

/** Strict canonical codec for the v1 → v2 migration report (V2-12-04). */
public final class LegacyMigrationReportCodecV2 {
    public static final String SCHEMA = "migration-report-v2.schema.json";
    public static final long MAXIMUM_BYTES = 256L * 1024L;

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public LegacyMigrationReportV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) < 2 || Files.size(path) > MAXIMUM_BYTES) {
            throw new IOException("migration report must be a bounded regular non-symbolic file: " + path);
        }
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            JsonNode tree = readTree(input);
            validator.validate(SCHEMA, path.toString(), tree);
            return mapper.treeToValue(tree, LegacyMigrationReportV2.class);
        }
    }

    /** Writes the report through a staged read-back so a partially written file is never published. */
    public void write(Path path, LegacyMigrationReportV2 report) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(report, "report");
        JsonNode tree = mapper.valueToTree(report);
        validator.validate(SCHEMA, path.toString(), tree);
        byte[] bytes = CanonicalJsonV2.bytes(tree);
        if (bytes.length > MAXIMUM_BYTES) {
            throw new IOException("migration report exceeds byte budget: " + path);
        }
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "migration report requires a parent");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("migration report parent must be a non-symbolic directory");
        }
        Path temporary = Files.createTempFile(parent, ".migration-report-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try (InputStream input = Files.newInputStream(temporary)) {
                JsonNode readBack = readTree(input);
                validator.validate(SCHEMA, temporary.toString(), readBack);
                if (!mapper.treeToValue(readBack, LegacyMigrationReportV2.class).equals(report)) {
                    throw new IOException("migration report changed during staged read-back");
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for migration report publication", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private JsonNode readTree(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(Math.toIntExact(MAXIMUM_BYTES + 1L));
        if (bytes.length > MAXIMUM_BYTES) {
            throw new IOException("migration report exceeds byte budget");
        }
        JsonNode tree = mapper.readTree(bytes);
        if (tree == null) {
            throw new IOException("migration report is empty");
        }
        return tree;
    }
}
