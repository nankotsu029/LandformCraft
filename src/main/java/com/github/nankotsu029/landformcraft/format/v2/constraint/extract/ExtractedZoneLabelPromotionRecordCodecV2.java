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
import com.github.nankotsu029.landformcraft.model.v2.ExtractedZoneLabelPromotionRecordV2;
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

/** Strict codec and read-back verifier for extracted zone-label promotion provenance bundles. */
public final class ExtractedZoneLabelPromotionRecordCodecV2 {
    public static final String SCHEMA = "extracted-zone-label-promotion-v2.schema.json";
    public static final String INDEX_FILE_NAME = "extracted-zone-label-promotion-v2.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public ExtractedZoneLabelPromotionRecordV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        JsonNode tree;
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            tree = mapper.readTree(input);
        }
        validator.validate(SCHEMA, path.toString(), tree);
        ExtractedZoneLabelPromotionRecordV2 record =
                mapper.treeToValue(tree, ExtractedZoneLabelPromotionRecordV2.class);
        return verifyChecksum(record);
    }

    public ExtractedZoneLabelPromotionRecordV2 readAndVerify(
            Path indexPath,
            Path artifactRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(indexPath, "indexPath");
        Objects.requireNonNull(artifactRoot, "artifactRoot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (!indexPath.getFileName().toString().equals(INDEX_FILE_NAME)) {
            throw new IOException("extracted zone label promotion index filename must be " + INDEX_FILE_NAME);
        }
        requireSafeDirectory(artifactRoot);
        if (Files.isSymbolicLink(indexPath) || !Files.isRegularFile(indexPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted zone label promotion index must be a regular non-symbolic file");
        }
        ExtractedZoneLabelPromotionRecordV2 record = read(indexPath);
        verifyDirectoryEntries(artifactRoot, record);
        cancellationToken.throwIfCancellationRequested();
        Path mapPath = artifactRoot.resolve(record.mapPath());
        if (Files.isSymbolicLink(mapPath) || !Files.isRegularFile(mapPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("promoted zone label map must be a regular non-symbolic file");
        }
        if (Files.size(mapPath) != record.mapByteLength()) {
            throw new IOException("promoted zone label map byte length mismatch");
        }
        byte[] bytes = Files.readAllBytes(mapPath);
        if (!Sha256.bytes(bytes).equals(record.mapSha256())) {
            throw new IOException("promoted zone label map checksum mismatch");
        }
        cancellationToken.throwIfCancellationRequested();
        return record;
    }

    public ExtractedZoneLabelPromotionRecordV2 seal(ExtractedZoneLabelPromotionRecordV2 record) {
        Objects.requireNonNull(record, "record");
        String checksum = canonicalChecksum(record);
        if (record.hasPendingCanonicalChecksum()) {
            return record.withCanonicalChecksum(checksum);
        }
        if (!checksum.equals(record.canonicalChecksum())) {
            throw new IllegalArgumentException("extracted zone label promotion canonical checksum mismatch");
        }
        return record;
    }

    public void write(Path path, ExtractedZoneLabelPromotionRecordV2 record) throws IOException {
        ExtractedZoneLabelPromotionRecordV2 sealed = seal(record);
        ObjectNode tree = mapper.valueToTree(sealed);
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "promotion index must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".extracted-zone-label-promotion-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException("extracted zone label promotion index changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for extracted zone label promotion index", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private ExtractedZoneLabelPromotionRecordV2 verifyChecksum(ExtractedZoneLabelPromotionRecordV2 record)
            throws IOException {
        if (record.hasPendingCanonicalChecksum()) {
            throw new IOException("extracted zone label promotion record is not canonically sealed");
        }
        if (!canonicalChecksum(record).equals(record.canonicalChecksum())) {
            throw new IOException("extracted zone label promotion canonical checksum mismatch");
        }
        return record;
    }

    private String canonicalChecksum(ExtractedZoneLabelPromotionRecordV2 record) {
        ObjectNode node = mapper.valueToTree(record);
        node.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(node);
    }

    private static void requireSafeDirectory(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted zone label promotion root must be a non-symbolic directory");
        }
    }

    private static void verifyDirectoryEntries(Path root, ExtractedZoneLabelPromotionRecordV2 record)
            throws IOException {
        Set<String> expected = Set.of(INDEX_FILE_NAME, record.mapPath());
        try (var paths = Files.list(root)) {
            Set<String> actual = new HashSet<>();
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("extracted zone label promotion root contains an unsafe entry");
                }
                actual.add(path.getFileName().toString());
            }
            if (!actual.equals(expected)) {
                throw new IOException("extracted zone label promotion index and directory entries differ");
            }
        }
    }
}
