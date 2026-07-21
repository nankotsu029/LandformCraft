package com.github.nankotsu029.landformcraft.model.v2;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict index for one multi-source reconciliation artifact bundle
 * ({@code result.u8} / {@code conflict.u8} / {@code source-diff.u8}).
 */
public record MultiSourceReconciliationArtifactV2(
        int schemaVersion,
        String algorithmVersion,
        String priorityTableVersion,
        ImageFidelityReconcileRoleV2 role,
        MultiSourceReconciliationStatusV2 status,
        int width,
        int length,
        int resultNoDataSample,
        String resultPath,
        String conflictPath,
        String sourceDiffPath,
        String resultSha256,
        String conflictSha256,
        String sourceDiffSha256,
        long resultByteLength,
        long conflictByteLength,
        long sourceDiffByteLength,
        int resolvedCells,
        int emptyCells,
        int hardConflictCells,
        int softPeerConflictCells,
        long absoluteDiffSum,
        List<MultiSourceLayerDescriptorV2> sources,
        List<SourceToResultDiffMetricV2> sourceDiffs,
        String semanticChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String ALGORITHM_VERSION = "image-fidelity-multisource-reconcile-v1";
    public static final String PRIORITY_TABLE_VERSION = "image-constraint-priority-v1";
    public static final String RESULT_PATH = "result.u8";
    public static final String CONFLICT_PATH = "conflict.u8";
    public static final String SOURCE_DIFF_PATH = "source-diff.u8";
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);
    public static final int MAXIMUM_DIMENSION = 4_096;
    public static final long MAXIMUM_PIXELS = 4_000_000L;
    public static final int MAXIMUM_SOURCES = 32;

    public MultiSourceReconciliationArtifactV2 {
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("multi-source reconciliation schemaVersion must be 1");
        }
        algorithmVersion = V2Validation.nonBlank(algorithmVersion, "algorithmVersion", 64);
        if (!ALGORITHM_VERSION.equals(algorithmVersion)) {
            throw new IllegalArgumentException("unsupported multi-source reconciliation algorithmVersion");
        }
        priorityTableVersion = V2Validation.nonBlank(priorityTableVersion, "priorityTableVersion", 64);
        if (!PRIORITY_TABLE_VERSION.equals(priorityTableVersion)) {
            throw new IllegalArgumentException("unsupported priorityTableVersion");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (width < 1 || width > MAXIMUM_DIMENSION || length < 1 || length > MAXIMUM_DIMENSION) {
            throw new IllegalArgumentException("reconciliation dimensions must be within 1..4096");
        }
        long pixels = (long) width * length;
        if (pixels > MAXIMUM_PIXELS) {
            throw new IllegalArgumentException("reconciliation pixel count exceeds the limit");
        }
        if (resultNoDataSample < 0 || resultNoDataSample > 255) {
            throw new IllegalArgumentException("resultNoDataSample must be within 0..255");
        }
        if (!RESULT_PATH.equals(resultPath)
                || !CONFLICT_PATH.equals(conflictPath)
                || !SOURCE_DIFF_PATH.equals(sourceDiffPath)) {
            throw new IllegalArgumentException("reconciliation sidecar paths are fixed");
        }
        resultSha256 = V2Validation.checksum(resultSha256, "resultSha256");
        conflictSha256 = V2Validation.checksum(conflictSha256, "conflictSha256");
        sourceDiffSha256 = V2Validation.checksum(sourceDiffSha256, "sourceDiffSha256");
        if (resultByteLength != pixels || conflictByteLength != pixels || sourceDiffByteLength != pixels) {
            throw new IllegalArgumentException("reconciliation raster byte lengths are invalid");
        }
        if (resolvedCells < 0 || emptyCells < 0 || hardConflictCells < 0 || softPeerConflictCells < 0
                || absoluteDiffSum < 0
                || resolvedCells + emptyCells + hardConflictCells + softPeerConflictCells != pixels) {
            throw new IllegalArgumentException("reconciliation cell counts do not match dimensions");
        }
        sources = V2Validation.immutable(sources, "sources", MAXIMUM_SOURCES);
        if (sources.size() < 2) {
            throw new IllegalArgumentException("multi-source reconciliation requires at least two sources");
        }
        Set<String> ids = new HashSet<>();
        for (MultiSourceLayerDescriptorV2 source : sources) {
            if (!ids.add(source.sourceId())) {
                throw new IllegalArgumentException("duplicate reconciliation sourceId");
            }
            if (source.samplesByteLength() != pixels) {
                throw new IllegalArgumentException("source samplesByteLength must match dimensions");
            }
        }
        sourceDiffs = V2Validation.immutable(sourceDiffs, "sourceDiffs", MAXIMUM_SOURCES);
        if (sourceDiffs.size() != sources.size()) {
            throw new IllegalArgumentException("sourceDiffs must align 1:1 with sources");
        }
        for (int i = 0; i < sources.size(); i++) {
            if (!sources.get(i).sourceId().equals(sourceDiffs.get(i).sourceId())) {
                throw new IllegalArgumentException("sourceDiffs order must match sources");
            }
        }
        semanticChecksum = V2Validation.checksum(semanticChecksum, "semanticChecksum");
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
        validateStatus(status, hardConflictCells, softPeerConflictCells);
    }

    public MultiSourceReconciliationArtifactV2(
            int schemaVersion,
            String algorithmVersion,
            String priorityTableVersion,
            ImageFidelityReconcileRoleV2 role,
            MultiSourceReconciliationStatusV2 status,
            int width,
            int length,
            int resultNoDataSample,
            String resultPath,
            String conflictPath,
            String sourceDiffPath,
            String resultSha256,
            String conflictSha256,
            String sourceDiffSha256,
            long resultByteLength,
            long conflictByteLength,
            long sourceDiffByteLength,
            int resolvedCells,
            int emptyCells,
            int hardConflictCells,
            int softPeerConflictCells,
            long absoluteDiffSum,
            List<MultiSourceLayerDescriptorV2> sources,
            List<SourceToResultDiffMetricV2> sourceDiffs,
            String semanticChecksum
    ) {
        this(schemaVersion, algorithmVersion, priorityTableVersion, role, status, width, length,
                resultNoDataSample, resultPath, conflictPath, sourceDiffPath,
                resultSha256, conflictSha256, sourceDiffSha256,
                resultByteLength, conflictByteLength, sourceDiffByteLength,
                resolvedCells, emptyCells, hardConflictCells, softPeerConflictCells, absoluteDiffSum,
                sources, sourceDiffs, semanticChecksum, PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public MultiSourceReconciliationArtifactV2 withCanonicalChecksum(String checksum) {
        return new MultiSourceReconciliationArtifactV2(
                schemaVersion, algorithmVersion, priorityTableVersion, role, status, width, length,
                resultNoDataSample, resultPath, conflictPath, sourceDiffPath,
                resultSha256, conflictSha256, sourceDiffSha256,
                resultByteLength, conflictByteLength, sourceDiffByteLength,
                resolvedCells, emptyCells, hardConflictCells, softPeerConflictCells, absoluteDiffSum,
                sources, sourceDiffs, semanticChecksum, checksum);
    }

    private static void validateStatus(
            MultiSourceReconciliationStatusV2 status,
            int hardConflictCells,
            int softPeerConflictCells
    ) {
        switch (status) {
            case RESOLVED -> {
                if (hardConflictCells != 0 || softPeerConflictCells != 0) {
                    throw new IllegalArgumentException("RESOLVED status requires zero conflict cells");
                }
            }
            case UNRESOLVED_HARD_CONFLICT -> {
                if (hardConflictCells < 1) {
                    throw new IllegalArgumentException("UNRESOLVED_HARD_CONFLICT requires hard conflict cells");
                }
            }
            case UNRESOLVED_SOFT_PEER_CONFLICT -> {
                if (hardConflictCells != 0 || softPeerConflictCells < 1) {
                    throw new IllegalArgumentException(
                            "UNRESOLVED_SOFT_PEER_CONFLICT requires soft peer conflicts only");
                }
            }
        }
    }
}
