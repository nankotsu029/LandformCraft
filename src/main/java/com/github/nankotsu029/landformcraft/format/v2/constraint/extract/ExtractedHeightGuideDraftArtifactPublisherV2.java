package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedHeightGuideDraftArtifactV2;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** Stages, strictly reads back, and atomically publishes one extracted height-guide draft bundle. */
public final class ExtractedHeightGuideDraftArtifactPublisherV2 {
    private final ExtractedHeightGuideDraftArtifactCodecV2 codec = new ExtractedHeightGuideDraftArtifactCodecV2();

    public ExtractedHeightGuideDraftArtifactV2 publish(
            Path targetDirectory,
            ExtractedHeightGuideDraftV2 draft,
            String sourceRelativePath,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "height guide draft artifact target requires a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("extracted height guide draft target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-extracted-height-guide-draft-" + UUID.randomUUID());
        boolean published = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            byte[] samples = draft.copySamples();
            byte[] confidence = draft.copyConfidence();
            cancellationToken.throwIfCancellationRequested();
            Path samplesPath = staging.resolve(ExtractedHeightGuideDraftArtifactV2.SAMPLES_PATH);
            Path confidencePath = staging.resolve(ExtractedHeightGuideDraftArtifactV2.CONFIDENCE_PATH);
            Files.write(samplesPath, samples);
            cancellationToken.throwIfCancellationRequested();
            Files.write(confidencePath, confidence);
            ExtractedHeightGuideDraftArtifactV2 draftIndex = codec.seal(new ExtractedHeightGuideDraftArtifactV2(
                    ExtractedHeightGuideDraftArtifactV2.VERSION,
                    draft.algorithmVersion(),
                    draft.sampleSpaceDeclaration(),
                    draft.sourceChecksum(),
                    draft.semanticChecksum(),
                    draft.width(),
                    draft.length(),
                    draft.validCells(),
                    draft.noDataCells(),
                    ExtractedHeightGuideDraftArtifactV2.SAMPLES_PATH,
                    ExtractedHeightGuideDraftArtifactV2.CONFIDENCE_PATH,
                    Sha256.bytes(samples),
                    Sha256.bytes(confidence),
                    samples.length,
                    confidence.length,
                    sourceRelativePath));
            codec.write(staging.resolve(ExtractedHeightGuideDraftArtifactCodecV2.INDEX_FILE_NAME), draftIndex);
            ExtractedHeightGuideDraftArtifactV2 verified = codec.readAndVerify(
                    staging.resolve(ExtractedHeightGuideDraftArtifactCodecV2.INDEX_FILE_NAME),
                    staging,
                    cancellationToken);
            if (!verified.equals(draftIndex)) {
                throw new IOException("extracted height guide draft changed during strict read-back");
            }
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for extracted height guide draft publication", exception);
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
