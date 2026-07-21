package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedZoneLabelDraftArtifactV2;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** Stages, strictly reads back, and atomically publishes one extracted zone-label draft bundle. */
public final class ExtractedZoneLabelDraftArtifactPublisherV2 {
    private final ExtractedZoneLabelDraftArtifactCodecV2 codec = new ExtractedZoneLabelDraftArtifactCodecV2();

    public ExtractedZoneLabelDraftArtifactV2 publish(
            Path targetDirectory,
            ExtractedZoneLabelDraftV2 draft,
            String sourceRelativePath,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(
                target.getParent(), "zone label draft artifact target requires a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("extracted zone label draft target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-extracted-zone-label-draft-" + UUID.randomUUID());
        boolean published = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            byte[] labels = draft.copyLabelIndices();
            byte[] confidence = draft.copyConfidence();
            cancellationToken.throwIfCancellationRequested();
            Files.write(staging.resolve(ExtractedZoneLabelDraftArtifactV2.LABELS_PATH), labels);
            cancellationToken.throwIfCancellationRequested();
            Files.write(staging.resolve(ExtractedZoneLabelDraftArtifactV2.CONFIDENCE_PATH), confidence);
            ExtractedZoneLabelDraftArtifactV2 draftIndex = codec.seal(new ExtractedZoneLabelDraftArtifactV2(
                    ExtractedZoneLabelDraftArtifactV2.VERSION,
                    draft.algorithmVersion(),
                    draft.sampleSpaceDeclaration(),
                    draft.sourceChecksum(),
                    draft.semanticChecksum(),
                    draft.width(),
                    draft.length(),
                    draft.labeledCells(),
                    draft.unknownCells(),
                    ExtractedZoneLabelDraftArtifactV2.LABELS_PATH,
                    ExtractedZoneLabelDraftArtifactV2.CONFIDENCE_PATH,
                    Sha256.bytes(labels),
                    Sha256.bytes(confidence),
                    labels.length,
                    confidence.length,
                    ExtractedZoneLabelDraftArtifactCodecV2.toProposals(draft.proposedLabels()),
                    sourceRelativePath));
            codec.write(staging.resolve(ExtractedZoneLabelDraftArtifactCodecV2.INDEX_FILE_NAME), draftIndex);
            ExtractedZoneLabelDraftArtifactV2 verified = codec.readAndVerify(
                    staging.resolve(ExtractedZoneLabelDraftArtifactCodecV2.INDEX_FILE_NAME),
                    staging,
                    cancellationToken);
            if (!verified.equals(draftIndex)) {
                throw new IOException("extracted zone label draft changed during strict read-back");
            }
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "atomic move is required for extracted zone label draft publication", exception);
            }
            published = true;
            return draftIndex;
        } finally {
            if (!published) {
                deleteTree(staging);
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
