package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskDraftArtifactV2;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** Stages, strictly reads back, and atomically publishes one extracted mask draft bundle. */
public final class ExtractedMaskDraftArtifactPublisherV2 {
    private final ExtractedMaskDraftArtifactCodecV2 codec = new ExtractedMaskDraftArtifactCodecV2();

    public ExtractedMaskDraftArtifactV2 publish(
            Path targetDirectory,
            ExtractedMaskDraftV2 draft,
            String sourceRelativePath,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "draft artifact target requires a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("extracted mask draft target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-extracted-mask-draft-" + UUID.randomUUID());
        boolean published = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            byte[] classes = draft.copyClasses();
            byte[] confidence = draft.copyConfidence();
            cancellationToken.throwIfCancellationRequested();
            Path classesPath = staging.resolve(ExtractedMaskDraftArtifactV2.CLASSES_PATH);
            Path confidencePath = staging.resolve(ExtractedMaskDraftArtifactV2.CONFIDENCE_PATH);
            Files.write(classesPath, classes);
            cancellationToken.throwIfCancellationRequested();
            Files.write(confidencePath, confidence);
            ExtractedMaskDraftArtifactV2 draftIndex = codec.seal(new ExtractedMaskDraftArtifactV2(
                    ExtractedMaskDraftArtifactV2.VERSION,
                    draft.algorithmVersion(),
                    draft.sourceChecksum(),
                    draft.semanticChecksum(),
                    draft.width(),
                    draft.length(),
                    draft.waterCells(),
                    draft.landCells(),
                    draft.unknownCells(),
                    ExtractedMaskDraftArtifactV2.CLASSES_PATH,
                    ExtractedMaskDraftArtifactV2.CONFIDENCE_PATH,
                    Sha256.bytes(classes),
                    Sha256.bytes(confidence),
                    classes.length,
                    confidence.length,
                    sourceRelativePath));
            codec.write(staging.resolve(ExtractedMaskDraftArtifactCodecV2.INDEX_FILE_NAME), draftIndex);
            ExtractedMaskDraftArtifactV2 verified = codec.readAndVerify(
                    staging.resolve(ExtractedMaskDraftArtifactCodecV2.INDEX_FILE_NAME),
                    staging,
                    cancellationToken);
            if (!verified.equals(draftIndex)) {
                throw new IOException("extracted mask draft changed during strict read-back");
            }
            // Final cancel observation; the atomic directory move is the commit point.
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for extracted mask draft publication", exception);
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
