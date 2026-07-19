package com.github.nankotsu029.landformcraft.format.v2.release;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Strict canonical JSON codec for the Release format 2 manifest and artifact index. */
public final class ReleaseManifestCodecV2 {
    public static final String SCHEMA = "release-manifest-v2.schema.json";
    public static final long MAXIMUM_MANIFEST_BYTES =
            ReleaseArtifactLimitsCatalogV2.MAXIMUM_MANIFEST_BYTES;

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public ReleaseManifestV2 seal(ReleaseManifestV2 manifest) {
        Objects.requireNonNull(manifest, "manifest");
        String checksum = canonicalChecksum(manifest);
        if (manifest.hasPendingCanonicalChecksum()) {
            return manifest.withCanonicalChecksum(checksum);
        }
        if (!checksum.equals(manifest.canonicalChecksum())) {
            throw new IllegalArgumentException("Release format 2 manifest canonical checksum mismatch");
        }
        return manifest;
    }

    public ReleaseManifestV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) < 2 || Files.size(path) > MAXIMUM_MANIFEST_BYTES) {
            throw new IOException("Release format 2 manifest must be a bounded regular non-symbolic file");
        }
        JsonNode tree;
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            tree = mapper.readTree(input);
        }
        return parse(tree, path.toString());
    }

    public ReleaseManifestV2 read(String input, String documentName) throws IOException {
        Objects.requireNonNull(input, "input");
        if (input.length() > MAXIMUM_MANIFEST_BYTES) {
            throw new IOException("Release format 2 manifest exceeds its byte budget");
        }
        try {
            return parse(mapper.readTree(input), documentName);
        } catch (RuntimeException exception) {
            throw new IOException("invalid Release format 2 manifest: " + documentName, exception);
        }
    }

    public void write(Path path, ReleaseManifestV2 manifest) throws IOException {
        ReleaseManifestV2 sealed = seal(manifest);
        ObjectNode tree = mapper.valueToTree(sealed);
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "manifest requires a parent directory");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Release format 2 manifest parent must be a non-symbolic directory");
        }
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Release format 2 manifest target must not be a symbolic link");
        }
        Path temporary = Files.createTempFile(parent, ".release-v2-manifest-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException("Release format 2 manifest changed during staged read-back");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for Release format 2 manifest", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private ReleaseManifestV2 parse(JsonNode tree, String documentName) throws IOException {
        try {
            validator.validate(SCHEMA, documentName, tree);
            ReleaseManifestV2 manifest = mapper.treeToValue(tree, ReleaseManifestV2.class);
            ReleaseCrossVersionReaderPolicyV2.requireSupportedVersions(
                    manifest.releaseFormatVersion(), manifest.manifestVersion());
            if (manifest.hasPendingCanonicalChecksum()
                    || !canonicalChecksum(manifest).equals(manifest.canonicalChecksum())) {
                throw new IOException("Release format 2 manifest canonical checksum mismatch: " + documentName);
            }
            return manifest;
        } catch (IllegalArgumentException | StructuredDataValidationException exception) {
            throw new IOException("invalid Release format 2 manifest: " + documentName, exception);
        }
    }

    private String canonicalChecksum(ReleaseManifestV2 manifest) {
        ObjectNode tree = mapper.valueToTree(manifest);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }
}
