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
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskPromotionRecordV2;
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

/** Strict codec and read-back verifier for extracted-mask promotion provenance bundles. */
public final class ExtractedMaskPromotionRecordCodecV2 {
    public static final String SCHEMA = "extracted-mask-promotion-v2.schema.json";
    public static final String INDEX_FILE_NAME = "extracted-mask-promotion-v2.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public ExtractedMaskPromotionRecordV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        JsonNode tree;
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            tree = mapper.readTree(input);
        }
        validator.validate(SCHEMA, path.toString(), tree);
        ExtractedMaskPromotionRecordV2 record = mapper.treeToValue(tree, ExtractedMaskPromotionRecordV2.class);
        return verifyChecksum(record);
    }

    public ExtractedMaskPromotionRecordV2 readAndVerify(
            Path indexPath,
            Path artifactRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(indexPath, "indexPath");
        Objects.requireNonNull(artifactRoot, "artifactRoot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (!indexPath.getFileName().toString().equals(INDEX_FILE_NAME)) {
            throw new IOException("extracted mask promotion index filename must be " + INDEX_FILE_NAME);
        }
        requireSafeDirectory(artifactRoot);
        if (Files.isSymbolicLink(indexPath) || !Files.isRegularFile(indexPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted mask promotion index must be a regular non-symbolic file");
        }
        ExtractedMaskPromotionRecordV2 record = read(indexPath);
        verifyDirectoryEntries(artifactRoot, record);
        cancellationToken.throwIfCancellationRequested();
        Path mapPath = artifactRoot.resolve(record.mapPath());
        if (Files.isSymbolicLink(mapPath) || !Files.isRegularFile(mapPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("promoted land-water map must be a regular non-symbolic file");
        }
        long size = Files.size(mapPath);
        if (size != record.mapByteLength()) {
            throw new IOException("promoted land-water map byte length mismatch");
        }
        byte[] bytes = Files.readAllBytes(mapPath);
        if (!Sha256.bytes(bytes).equals(record.mapSha256())) {
            throw new IOException("promoted land-water map checksum mismatch");
        }
        cancellationToken.throwIfCancellationRequested();
        return record;
    }

    public ExtractedMaskPromotionRecordV2 seal(ExtractedMaskPromotionRecordV2 record) {
        Objects.requireNonNull(record, "record");
        String checksum = canonicalChecksum(record);
        if (record.hasPendingCanonicalChecksum()) {
            return record.withCanonicalChecksum(checksum);
        }
        if (!checksum.equals(record.canonicalChecksum())) {
            throw new IllegalArgumentException("extracted mask promotion canonical checksum mismatch");
        }
        return record;
    }

    public void write(Path path, ExtractedMaskPromotionRecordV2 record) throws IOException {
        ExtractedMaskPromotionRecordV2 sealed = seal(record);
        ObjectNode tree = mapper.valueToTree(sealed);
        if (sealed.noDataSample() == null) {
            tree.remove("noDataSample");
        }
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "promotion index must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".extracted-mask-promotion-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException("extracted mask promotion index changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for extracted mask promotion index", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private ExtractedMaskPromotionRecordV2 verifyChecksum(ExtractedMaskPromotionRecordV2 record)
            throws IOException {
        if (record.hasPendingCanonicalChecksum()) {
            throw new IOException("extracted mask promotion record is not canonically sealed");
        }
        if (!canonicalChecksum(record).equals(record.canonicalChecksum())) {
            throw new IOException("extracted mask promotion canonical checksum mismatch");
        }
        return record;
    }

    private String canonicalChecksum(ExtractedMaskPromotionRecordV2 record) {
        ObjectNode node = mapper.valueToTree(record);
        node.remove("canonicalChecksum");
        if (record.noDataSample() == null) {
            node.remove("noDataSample");
        }
        return CanonicalJsonV2.checksum(node);
    }

    private static void requireSafeDirectory(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted mask promotion root must be a non-symbolic directory");
        }
    }

    private static void verifyDirectoryEntries(Path root, ExtractedMaskPromotionRecordV2 record)
            throws IOException {
        Set<String> expected = Set.of(INDEX_FILE_NAME, record.mapPath());
        try (var paths = Files.list(root)) {
            Set<String> actual = new HashSet<>();
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("extracted mask promotion root contains an unsafe entry");
                }
                actual.add(path.getFileName().toString());
            }
            if (!actual.equals(expected)) {
                throw new IOException("extracted mask promotion index and directory entries differ");
            }
        }
    }
}
