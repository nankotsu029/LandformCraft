package com.github.nankotsu029.landformcraft.preview.v2;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapSourceSpec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.LoadedConstraintMapSource;
import com.github.nankotsu029.landformcraft.format.v2.constraint.SecureConstraintMapSourceLoader;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskDraftPreviewIndexV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Strict codec/read-back verifier for extracted mask draft diagnostic preview bundles. */
public final class ExtractedMaskDraftPreviewIndexCodecV2 {
    public static final String SCHEMA = "extracted-mask-draft-preview-index-v2.schema.json";
    public static final String INDEX_FILE_NAME = "index.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public ExtractedMaskDraftPreviewIndexV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        JsonNode tree;
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            tree = mapper.readTree(input);
        }
        validator.validate(SCHEMA, path.toString(), tree);
        ExtractedMaskDraftPreviewIndexV2 index = mapper.treeToValue(tree, ExtractedMaskDraftPreviewIndexV2.class);
        return verifyChecksum(index);
    }

    public ExtractedMaskDraftPreviewIndexV2 readAndVerify(
            Path indexPath,
            Path previewRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(indexPath, "indexPath");
        Objects.requireNonNull(previewRoot, "previewRoot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (!indexPath.getFileName().toString().equals(INDEX_FILE_NAME)) {
            throw new IOException("extracted mask draft preview index filename must be index.json");
        }
        requireSafeDirectory(previewRoot);
        if (Files.isSymbolicLink(indexPath) || !Files.isRegularFile(indexPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted mask draft preview index must be a regular non-symbolic file");
        }
        ExtractedMaskDraftPreviewIndexV2 index = read(indexPath);
        verifyDirectoryEntries(previewRoot, index);
        List<ConstraintMapSourceSpec> specs = index.layers().stream().map(layer ->
                new ConstraintMapSourceSpec(
                        "constraint-source:preview-" + layer.layerId().name().toLowerCase(Locale.ROOT)
                                .replace('_', '-'),
                        layer.path(),
                        layer.sha256(),
                        layer.width(),
                        layer.length())).toList();
        List<LoadedConstraintMapSource> loaded = new SecureConstraintMapSourceLoader().load(
                previewRoot.resolve("preview-request.json"),
                specs,
                ConstraintMapDecodeLimits.defaults(),
                cancellationToken::isCancellationRequested);
        for (int i = 0; i < index.layers().size(); i++) {
            cancellationToken.throwIfCancellationRequested();
            ExtractedMaskDraftPreviewIndexV2.Layer layer = index.layers().get(i);
            LoadedConstraintMapSource source = loaded.get(i);
            if (source.sourceBytes() != layer.byteLength()) {
                throw new IOException("extracted mask draft preview byte length mismatch: " + layer.path());
            }
            verifyPng(source.contentCopy(), layer);
        }
        cancellationToken.throwIfCancellationRequested();
        return index;
    }

    public ExtractedMaskDraftPreviewIndexV2 seal(ExtractedMaskDraftPreviewIndexV2 index) {
        Objects.requireNonNull(index, "index");
        String checksum = canonicalChecksum(index);
        if (index.hasPendingCanonicalChecksum()) {
            return index.withCanonicalChecksum(checksum);
        }
        if (!checksum.equals(index.canonicalChecksum())) {
            throw new IllegalArgumentException(
                    "extracted mask draft preview index canonical checksum mismatch");
        }
        return index;
    }

    public void write(Path path, ExtractedMaskDraftPreviewIndexV2 index) throws IOException {
        ExtractedMaskDraftPreviewIndexV2 sealed = seal(index);
        ObjectNode tree = mapper.valueToTree(sealed);
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(
                target.getParent(), "extracted mask draft preview index must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".extracted-mask-draft-preview-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException(
                        "extracted mask draft preview index changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for extracted mask draft preview index", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private ExtractedMaskDraftPreviewIndexV2 verifyChecksum(ExtractedMaskDraftPreviewIndexV2 index)
            throws IOException {
        if (index.hasPendingCanonicalChecksum()) {
            throw new IOException("extracted mask draft preview index is not canonically sealed");
        }
        if (!canonicalChecksum(index).equals(index.canonicalChecksum())) {
            throw new IOException("extracted mask draft preview index canonical checksum mismatch");
        }
        return index;
    }

    private String canonicalChecksum(ExtractedMaskDraftPreviewIndexV2 index) {
        ObjectNode node = mapper.valueToTree(index);
        node.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(node);
    }

    private static void requireSafeDirectory(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted mask draft preview root must be a non-symbolic directory");
        }
    }

    private static void verifyDirectoryEntries(Path root, ExtractedMaskDraftPreviewIndexV2 index)
            throws IOException {
        Set<String> expected = new HashSet<>(
                index.layers().stream().map(ExtractedMaskDraftPreviewIndexV2.Layer::path).toList());
        expected.add(INDEX_FILE_NAME);
        try (var paths = Files.list(root)) {
            Set<String> actual = new HashSet<>();
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("extracted mask draft preview root contains an unsafe entry");
                }
                actual.add(path.getFileName().toString());
            }
            if (!actual.equals(expected)) {
                throw new IOException("extracted mask draft preview index and directory entries differ");
            }
        }
    }

    private static void verifyPng(byte[] bytes, ExtractedMaskDraftPreviewIndexV2.Layer layer)
            throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IOException("extracted mask draft preview image input is unavailable");
            }
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("extracted mask draft preview PNG has no decoder: " + layer.path());
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                if (reader.getNumImages(true) != 1) {
                    throw new IOException("extracted mask draft preview PNG must be single-frame");
                }
                if (reader.getWidth(0) != layer.width() || reader.getHeight(0) != layer.length()) {
                    throw new IOException("extracted mask draft preview PNG dimensions mismatch");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException("extracted mask draft preview PNG decode returned null");
                }
                image.flush();
            } finally {
                reader.dispose();
            }
        }
    }
}
