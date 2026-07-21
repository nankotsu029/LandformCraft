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
import com.github.nankotsu029.landformcraft.model.v2.ExtractedZoneLabelDraftArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ZoneLabelProposalV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict codec and read-back verifier for extracted zone-label draft artifact bundles. */
public final class ExtractedZoneLabelDraftArtifactCodecV2 {
    public static final String SCHEMA = "extracted-zone-label-draft-v2.schema.json";
    public static final String INDEX_FILE_NAME = "extracted-zone-label-draft-v2.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public ExtractedZoneLabelDraftArtifactV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        JsonNode tree;
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            tree = mapper.readTree(input);
        }
        validator.validate(SCHEMA, path.toString(), tree);
        ExtractedZoneLabelDraftArtifactV2 artifact =
                mapper.treeToValue(tree, ExtractedZoneLabelDraftArtifactV2.class);
        return verifyChecksum(artifact);
    }

    public ExtractedZoneLabelDraftArtifactV2 readAndVerify(
            Path indexPath,
            Path artifactRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(indexPath, "indexPath");
        Objects.requireNonNull(artifactRoot, "artifactRoot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (!indexPath.getFileName().toString().equals(INDEX_FILE_NAME)) {
            throw new IOException("extracted zone label draft index filename must be " + INDEX_FILE_NAME);
        }
        requireSafeDirectory(artifactRoot);
        if (Files.isSymbolicLink(indexPath) || !Files.isRegularFile(indexPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted zone label draft index must be a regular non-symbolic file");
        }
        ExtractedZoneLabelDraftArtifactV2 artifact = read(indexPath);
        verifyDirectoryEntries(artifactRoot, artifact);
        cancellationToken.throwIfCancellationRequested();
        byte[] labels = readRaster(artifactRoot.resolve(artifact.labelsPath()), artifact.labelsByteLength());
        byte[] confidence = readRaster(
                artifactRoot.resolve(artifact.confidencePath()), artifact.confidenceByteLength());
        if (!Sha256.bytes(labels).equals(artifact.labelsSha256())
                || !Sha256.bytes(confidence).equals(artifact.confidenceSha256())) {
            throw new IOException("extracted zone label draft sidecar checksum mismatch");
        }
        List<ZonePaletteEntryV2> palette = toPalette(artifact.proposedLabels());
        ExtractedZoneLabelDraftV2 draft = ExtractedZoneLabelDraftV2.restore(
                artifact.width(),
                artifact.length(),
                artifact.algorithmVersion(),
                artifact.sourceChecksum(),
                artifact.semanticChecksum(),
                artifact.sampleSpaceDeclaration(),
                palette,
                labels,
                confidence,
                artifact.labeledCells(),
                artifact.unknownCells());
        if (!draft.semanticChecksum().equals(artifact.semanticChecksum())) {
            throw new IOException("extracted zone label draft semantic checksum mismatch after restore");
        }
        cancellationToken.throwIfCancellationRequested();
        return artifact;
    }

    public ExtractedZoneLabelDraftV2 loadDraft(
            Path indexPath,
            Path artifactRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        ExtractedZoneLabelDraftArtifactV2 artifact = readAndVerify(indexPath, artifactRoot, cancellationToken);
        byte[] labels = Files.readAllBytes(artifactRoot.resolve(artifact.labelsPath()));
        byte[] confidence = Files.readAllBytes(artifactRoot.resolve(artifact.confidencePath()));
        return ExtractedZoneLabelDraftV2.restore(
                artifact.width(),
                artifact.length(),
                artifact.algorithmVersion(),
                artifact.sourceChecksum(),
                artifact.semanticChecksum(),
                artifact.sampleSpaceDeclaration(),
                toPalette(artifact.proposedLabels()),
                labels,
                confidence,
                artifact.labeledCells(),
                artifact.unknownCells());
    }

    public ExtractedZoneLabelDraftArtifactV2 seal(ExtractedZoneLabelDraftArtifactV2 artifact) {
        Objects.requireNonNull(artifact, "artifact");
        String checksum = canonicalChecksum(artifact);
        if (artifact.hasPendingCanonicalChecksum()) {
            return artifact.withCanonicalChecksum(checksum);
        }
        if (!checksum.equals(artifact.canonicalChecksum())) {
            throw new IllegalArgumentException(
                    "extracted zone label draft artifact canonical checksum mismatch");
        }
        return artifact;
    }

    public void write(Path path, ExtractedZoneLabelDraftArtifactV2 artifact) throws IOException {
        ExtractedZoneLabelDraftArtifactV2 sealed = seal(artifact);
        ObjectNode tree = mapper.valueToTree(sealed);
        if (sealed.sourceRelativePath() == null) {
            tree.remove("sourceRelativePath");
        }
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(
                target.getParent(), "extracted zone label draft index must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".extracted-zone-label-draft-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, CanonicalJsonV2.bytes(tree), StandardOpenOption.TRUNCATE_EXISTING);
            if (!read(temporary).equals(sealed)) {
                throw new IOException("extracted zone label draft index changed during staged read-back");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "atomic move is required for extracted zone label draft index", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static List<ZoneLabelProposalV2> toProposals(List<ZonePaletteEntryV2> palette) {
        List<ZoneLabelProposalV2> proposals = new ArrayList<>(palette.size());
        for (ZonePaletteEntryV2 entry : palette) {
            proposals.add(new ZoneLabelProposalV2(
                    entry.sample(), entry.label(), entry.red(), entry.green(), entry.blue()));
        }
        return List.copyOf(proposals);
    }

    static List<ZonePaletteEntryV2> toPalette(List<ZoneLabelProposalV2> proposals) {
        List<ZonePaletteEntryV2> palette = new ArrayList<>(proposals.size());
        for (ZoneLabelProposalV2 proposal : proposals) {
            palette.add(new ZonePaletteEntryV2(
                    proposal.sample(), proposal.label(),
                    proposal.red(), proposal.green(), proposal.blue()));
        }
        return List.copyOf(palette);
    }

    private ExtractedZoneLabelDraftArtifactV2 verifyChecksum(ExtractedZoneLabelDraftArtifactV2 artifact)
            throws IOException {
        if (artifact.hasPendingCanonicalChecksum()) {
            throw new IOException("extracted zone label draft artifact is not canonically sealed");
        }
        if (!canonicalChecksum(artifact).equals(artifact.canonicalChecksum())) {
            throw new IOException("extracted zone label draft artifact canonical checksum mismatch");
        }
        return artifact;
    }

    private String canonicalChecksum(ExtractedZoneLabelDraftArtifactV2 artifact) {
        ObjectNode node = mapper.valueToTree(artifact);
        node.remove("canonicalChecksum");
        if (artifact.sourceRelativePath() == null) {
            node.remove("sourceRelativePath");
        }
        return CanonicalJsonV2.checksum(node);
    }

    private static void requireSafeDirectory(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted zone label draft root must be a non-symbolic directory");
        }
    }

    private static void verifyDirectoryEntries(Path root, ExtractedZoneLabelDraftArtifactV2 artifact)
            throws IOException {
        Set<String> expected = Set.of(
                INDEX_FILE_NAME, artifact.labelsPath(), artifact.confidencePath());
        try (var paths = Files.list(root)) {
            Set<String> actual = new HashSet<>();
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("extracted zone label draft root contains an unsafe entry");
                }
                actual.add(path.getFileName().toString());
            }
            if (!actual.equals(expected)) {
                throw new IOException("extracted zone label draft index and directory entries differ");
            }
        }
    }

    private static byte[] readRaster(Path path, long expectedBytes) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted zone label draft raster must be a regular non-symbolic file");
        }
        long size = Files.size(path);
        if (size != expectedBytes) {
            throw new IOException("extracted zone label draft raster byte length mismatch");
        }
        return Files.readAllBytes(path);
    }
}
