package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.MultiSourceReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.ImageFidelityStrengthV2;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceConflictCodeV2;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceLayerDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceReconciliationStatusV2;
import com.github.nankotsu029.landformcraft.model.v2.SourceToResultDiffMetricV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit multi-source reconciliation. Priority table is frozen under
 * {@link MultiSourceReconciliationArtifactV2#ALGORITHM_VERSION}; HARD/HARD and same-rank SOFT
 * disagreements fail closed (no last-write-wins).
 */
public final class MultiSourceReconciliationServiceV2 {
    private final MultiSourceReconciliationArtifactCodecV2 codec =
            new MultiSourceReconciliationArtifactCodecV2();

    public MultiSourceReconciliationArtifactV2 reconcileAndPublish(
            Path targetDirectory,
            int width,
            int length,
            MultiSourceReconciliationOptionsV2 options,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (width < 1 || length < 1
                || width > MultiSourceReconciliationArtifactV2.MAXIMUM_DIMENSION
                || length > MultiSourceReconciliationArtifactV2.MAXIMUM_DIMENSION) {
            throw new MultiSourceReconciliationExceptionV2(
                    MultiSourceReconciliationFailureCodeV2.INVALID_OPTIONS,
                    "dimensions must be within 1..4096");
        }
        long pixels = (long) width * length;
        if (pixels > MultiSourceReconciliationArtifactV2.MAXIMUM_PIXELS) {
            throw new MultiSourceReconciliationExceptionV2(
                    MultiSourceReconciliationFailureCodeV2.PIXEL_BUDGET_EXCEEDED,
                    "reconciliation exceeds pixel budget");
        }
        if (options.sources().getFirst().samples().length != pixels) {
            throw new MultiSourceReconciliationExceptionV2(
                    MultiSourceReconciliationFailureCodeV2.DIMENSION_MISMATCH,
                    "source sample length does not match width*length");
        }

        ReconcileResult computed = reconcile(width, length, options, cancellationToken);
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "reconciliation target requires a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("multi-source reconciliation target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-multisource-reconcile-" + UUID.randomUUID());
        boolean published = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            Files.write(staging.resolve(MultiSourceReconciliationArtifactV2.RESULT_PATH), computed.result());
            Files.write(staging.resolve(MultiSourceReconciliationArtifactV2.CONFLICT_PATH), computed.conflict());
            Files.write(staging.resolve(MultiSourceReconciliationArtifactV2.SOURCE_DIFF_PATH), computed.sourceDiff());
            MultiSourceReconciliationArtifactV2 pending = new MultiSourceReconciliationArtifactV2(
                    MultiSourceReconciliationArtifactV2.VERSION,
                    MultiSourceReconciliationArtifactV2.ALGORITHM_VERSION,
                    MultiSourceReconciliationArtifactV2.PRIORITY_TABLE_VERSION,
                    options.role(),
                    computed.status(),
                    width,
                    length,
                    options.resultNoDataSample(),
                    MultiSourceReconciliationArtifactV2.RESULT_PATH,
                    MultiSourceReconciliationArtifactV2.CONFLICT_PATH,
                    MultiSourceReconciliationArtifactV2.SOURCE_DIFF_PATH,
                    Sha256.bytes(computed.result()),
                    Sha256.bytes(computed.conflict()),
                    Sha256.bytes(computed.sourceDiff()),
                    computed.result().length,
                    computed.conflict().length,
                    computed.sourceDiff().length,
                    computed.resolvedCells(),
                    computed.emptyCells(),
                    computed.hardConflictCells(),
                    computed.softPeerConflictCells(),
                    computed.absoluteDiffSum(),
                    computed.sources(),
                    computed.sourceDiffs(),
                    computed.semanticChecksum());
            MultiSourceReconciliationArtifactV2 sealed = codec.seal(pending);
            codec.write(staging.resolve(MultiSourceReconciliationArtifactCodecV2.INDEX_FILE_NAME), sealed);
            MultiSourceReconciliationArtifactV2 verified = codec.readAndVerify(
                    staging.resolve(MultiSourceReconciliationArtifactCodecV2.INDEX_FILE_NAME),
                    staging,
                    cancellationToken);
            if (!verified.equals(sealed)) {
                throw new MultiSourceReconciliationExceptionV2(
                        MultiSourceReconciliationFailureCodeV2.ARTIFACT_TAMPERED,
                        "multi-source reconciliation changed during strict read-back");
            }
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for multi-source reconciliation", exception);
            }
            published = true;
            return sealed;
        } finally {
            if (!published) {
                deleteTree(staging);
            }
        }
    }

    /** Pure reconcile without publish — used by tests and preview. */
    public ReconcileResult reconcile(
            int width,
            int length,
            MultiSourceReconciliationOptionsV2 options,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        int pixels = Math.multiplyExact(width, length);
        List<MultiSourceProposalLayerV2> ordered = options.sources().stream()
                .sorted(Comparator
                        .comparingInt((MultiSourceProposalLayerV2 layer) -> layer.kind().rank())
                        .thenComparing(MultiSourceProposalLayerV2::sourceId))
                .toList();

        byte[] result = new byte[pixels];
        byte[] conflict = new byte[pixels];
        byte[] sourceDiff = new byte[pixels];
        int resolved = 0;
        int empty = 0;
        int hardConflicts = 0;
        int softPeers = 0;
        long absoluteDiffSum = 0L;

        int sourceCount = ordered.size();
        int[] agreed = new int[sourceCount];
        int[] suppressed = new int[sourceCount];
        int[] conflicted = new int[sourceCount];
        int[] present = new int[sourceCount];
        int[] mismatch = new int[sourceCount];
        long[] sourceAbs = new long[sourceCount];

        for (int index = 0; index < pixels; index++) {
            if ((index & 1_023) == 0) {
                cancellationToken.throwIfCancellationRequested();
            }
            List<Integer> candidateIndexes = new ArrayList<>(sourceCount);
            for (int s = 0; s < sourceCount; s++) {
                if (ordered.get(s).isPresent(index)) {
                    candidateIndexes.add(s);
                }
            }
            if (candidateIndexes.isEmpty()) {
                result[index] = (byte) options.resultNoDataSample();
                conflict[index] = (byte) MultiSourceConflictCodeV2.NONE.code();
                sourceDiff[index] = 0;
                empty++;
                continue;
            }

            int winnerIdx = candidateIndexes.getFirst();
            MultiSourceProposalLayerV2 winner = ordered.get(winnerIdx);
            int winnerSample = winner.sampleAt(index);
            boolean hardConflict = false;
            boolean softPeer = false;
            for (int c = 1; c < candidateIndexes.size(); c++) {
                int otherIdx = candidateIndexes.get(c);
                MultiSourceProposalLayerV2 other = ordered.get(otherIdx);
                int otherSample = other.sampleAt(index);
                if (otherSample == winnerSample) {
                    continue;
                }
                if (winner.strength() == ImageFidelityStrengthV2.HARD
                        && other.strength() == ImageFidelityStrengthV2.HARD) {
                    hardConflict = true;
                } else if (other.kind().rank() == winner.kind().rank()) {
                    softPeer = true;
                }
            }

            MultiSourceConflictCodeV2 code;
            int resultSample;
            if (hardConflict) {
                code = MultiSourceConflictCodeV2.HARD_CONFLICT;
                resultSample = options.resultNoDataSample();
                hardConflicts++;
            } else if (softPeer) {
                code = MultiSourceConflictCodeV2.SOFT_PEER_CONFLICT;
                resultSample = options.resultNoDataSample();
                softPeers++;
            } else {
                code = MultiSourceConflictCodeV2.NONE;
                resultSample = winnerSample;
                resolved++;
            }
            result[index] = (byte) resultSample;
            conflict[index] = (byte) code.code();

            int maxDiff = 0;
            for (int s = 0; s < sourceCount; s++) {
                MultiSourceProposalLayerV2 layer = ordered.get(s);
                if (!layer.isPresent(index)) {
                    continue;
                }
                present[s]++;
                int sample = layer.sampleAt(index);
                int diff = Math.abs(sample - resultSample);
                absoluteDiffSum += diff;
                sourceAbs[s] += diff;
                if (diff > maxDiff) {
                    maxDiff = Math.min(255, diff);
                }
                if (code != MultiSourceConflictCodeV2.NONE) {
                    conflicted[s]++;
                    mismatch[s]++;
                } else if (sample == resultSample) {
                    agreed[s]++;
                } else {
                    suppressed[s]++;
                    mismatch[s]++;
                }
            }
            sourceDiff[index] = (byte) (code == MultiSourceConflictCodeV2.NONE ? maxDiff : 255);
        }

        MultiSourceReconciliationStatusV2 status;
        if (hardConflicts > 0) {
            status = MultiSourceReconciliationStatusV2.UNRESOLVED_HARD_CONFLICT;
        } else if (softPeers > 0) {
            status = MultiSourceReconciliationStatusV2.UNRESOLVED_SOFT_PEER_CONFLICT;
        } else {
            status = MultiSourceReconciliationStatusV2.RESOLVED;
        }

        List<MultiSourceLayerDescriptorV2> descriptors = new ArrayList<>(sourceCount);
        List<SourceToResultDiffMetricV2> diffs = new ArrayList<>(sourceCount);
        // Publish descriptors in stable sourceId order (not rank order) for canonical JSON.
        List<MultiSourceProposalLayerV2> byId = options.sources().stream()
                .sorted(Comparator.comparing(MultiSourceProposalLayerV2::sourceId))
                .toList();
        for (MultiSourceProposalLayerV2 layer : byId) {
            int orderedIndex = ordered.indexOf(layer);
            descriptors.add(new MultiSourceLayerDescriptorV2(
                    layer.sourceId(),
                    layer.kind(),
                    layer.strength(),
                    layer.noDataSample(),
                    Sha256.bytes(layer.samples()),
                    layer.samples().length));
            diffs.add(new SourceToResultDiffMetricV2(
                    layer.sourceId(),
                    layer.kind(),
                    present[orderedIndex],
                    agreed[orderedIndex],
                    suppressed[orderedIndex],
                    conflicted[orderedIndex],
                    mismatch[orderedIndex],
                    sourceAbs[orderedIndex]));
        }

        String semantic = semanticChecksum(
                options, width, length, result, conflict, sourceDiff, descriptors, status);
        return new ReconcileResult(
                status, result, conflict, sourceDiff,
                resolved, empty, hardConflicts, softPeers, absoluteDiffSum,
                List.copyOf(descriptors), List.copyOf(diffs), semantic);
    }

    private static String semanticChecksum(
            MultiSourceReconciliationOptionsV2 options,
            int width,
            int length,
            byte[] result,
            byte[] conflict,
            byte[] sourceDiff,
            List<MultiSourceLayerDescriptorV2> sources,
            MultiSourceReconciliationStatusV2 status
    ) {
        MessageDigest digest = sha256();
        updateBytes(digest, MultiSourceReconciliationArtifactV2.ALGORITHM_VERSION.getBytes(StandardCharsets.UTF_8));
        updateBytes(digest, MultiSourceReconciliationArtifactV2.PRIORITY_TABLE_VERSION.getBytes(StandardCharsets.UTF_8));
        updateBytes(digest, options.role().name().getBytes(StandardCharsets.UTF_8));
        updateBytes(digest, status.name().getBytes(StandardCharsets.UTF_8));
        updateInt(digest, width);
        updateInt(digest, length);
        updateInt(digest, options.resultNoDataSample());
        updateInt(digest, sources.size());
        for (MultiSourceLayerDescriptorV2 source : sources) {
            updateBytes(digest, source.sourceId().getBytes(StandardCharsets.UTF_8));
            updateBytes(digest, source.kind().name().getBytes(StandardCharsets.UTF_8));
            updateBytes(digest, source.strength().name().getBytes(StandardCharsets.UTF_8));
            updateInt(digest, source.noDataSample());
            updateBytes(digest, source.samplesSha256().getBytes(StandardCharsets.UTF_8));
        }
        updateBytes(digest, result);
        updateBytes(digest, conflict);
        updateBytes(digest, sourceDiff);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateBytes(MessageDigest digest, byte[] bytes) {
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
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

    public record ReconcileResult(
            MultiSourceReconciliationStatusV2 status,
            byte[] result,
            byte[] conflict,
            byte[] sourceDiff,
            int resolvedCells,
            int emptyCells,
            int hardConflictCells,
            int softPeerConflictCells,
            long absoluteDiffSum,
            List<MultiSourceLayerDescriptorV2> sources,
            List<SourceToResultDiffMetricV2> sourceDiffs,
            String semanticChecksum
    ) {
        public ReconcileResult {
            result = Arrays.copyOf(result, result.length);
            conflict = Arrays.copyOf(conflict, conflict.length);
            sourceDiff = Arrays.copyOf(sourceDiff, sourceDiff.length);
            sources = List.copyOf(sources);
            sourceDiffs = List.copyOf(sourceDiffs);
        }
    }
}
