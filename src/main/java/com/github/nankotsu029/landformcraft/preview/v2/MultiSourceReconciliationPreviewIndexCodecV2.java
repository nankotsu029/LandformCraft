package com.github.nankotsu029.landformcraft.preview.v2;

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
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceReconciliationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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

/** Strict codec for multi-source reconciliation diagnostic preview indexes. */
public final class MultiSourceReconciliationPreviewIndexCodecV2 {
    public static final String SCHEMA = "multi-source-reconciliation-preview-index-v2.schema.json";
    public static final String INDEX_FILE_NAME = "multi-source-reconciliation-preview-index-v2.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public MultiSourceReconciliationPreviewIndexV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        JsonNode tree;
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            tree = mapper.readTree(input);
        }
        validator.validate(SCHEMA, path.toString(), tree);
        return verifyChecksum(mapper.treeToValue(tree, MultiSourceReconciliationPreviewIndexV2.class));
    }

    public MultiSourceReconciliationPreviewIndexV2 readAndVerify(
            Path indexPath,
            Path previewRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(indexPath, "indexPath");
        Objects.requireNonNull(previewRoot, "previewRoot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (!indexPath.getFileName().toString().equals(INDEX_FILE_NAME)) {
            throw new IOException("preview index filename must be " + INDEX_FILE_NAME);
        }
        if (Files.isSymbolicLink(previewRoot) || !Files.isDirectory(previewRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("preview root must be a non-symbolic directory");
        }
        if (Files.isSymbolicLink(indexPath) || !Files.isRegularFile(indexPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("preview index must be a regular non-symbolic file");
        }
        MultiSourceReconciliationPreviewIndexV2 index = read(indexPath);
        Set<String> expected = new HashSet<>();
        expected.add(INDEX_FILE_NAME);
        for (var layer : index.layers()) {
            expected.add(layer.fileName());
        }
        try (var paths = Files.list(previewRoot)) {
            Set<String> actual = new HashSet<>();
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("preview root contains an unsafe entry");
                }
                actual.add(path.getFileName().toString());
            }
            if (!actual.equals(expected)) {
                throw new IOException("preview index and directory entries differ");
            }
        }
        for (var layer : index.layers()) {
            cancellationToken.throwIfCancellationRequested();
            Path file = previewRoot.resolve(layer.fileName());
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length != layer.byteLength() || !Sha256.bytes(bytes).equals(layer.sha256())) {
                throw new IOException("preview layer checksum/length mismatch: " + layer.fileName());
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() != layer.width() || image.getHeight() != layer.length()) {
                throw new IOException("preview PNG dimensions mismatch: " + layer.fileName());
            }
        }
        return index;
    }

    public MultiSourceReconciliationPreviewIndexV2 seal(MultiSourceReconciliationPreviewIndexV2 index) {
        Objects.requireNonNull(index, "index");
        String checksum = canonicalChecksum(index);
        if (index.hasPendingCanonicalChecksum()) {
            return index.withCanonicalChecksum(checksum);
        }
        if (!checksum.equals(index.canonicalChecksum())) {
            throw new IllegalArgumentException("preview canonical checksum mismatch");
        }
        return index;
    }

    public void write(Path path, MultiSourceReconciliationPreviewIndexV2 index) throws IOException {
        MultiSourceReconciliationPreviewIndexV2 sealed = seal(index);
        ObjectNode tree = mapper.valueToTree(sealed);
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "preview index requires a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".multi-source-preview-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException("preview index changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for preview index", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private MultiSourceReconciliationPreviewIndexV2 verifyChecksum(MultiSourceReconciliationPreviewIndexV2 index)
            throws IOException {
        if (index.hasPendingCanonicalChecksum()) {
            throw new IOException("preview index is not canonically sealed");
        }
        if (!canonicalChecksum(index).equals(index.canonicalChecksum())) {
            throw new IOException("preview canonical checksum mismatch");
        }
        return index;
    }

    private String canonicalChecksum(MultiSourceReconciliationPreviewIndexV2 index) {
        ObjectNode node = mapper.valueToTree(index);
        node.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(node);
    }
}
