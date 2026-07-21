package com.github.nankotsu029.landformcraft.model.v2;

/**
 * Stable source-to-result reduction for one input layer. {@code mismatchCells} is the categorical
 * disagree count; {@code absoluteDiffSum} sums |source−result| over present cells (integer-only).
 */
public record SourceToResultDiffMetricV2(
        String sourceId,
        ImageFidelitySourceKindV2 kind,
        int presentCells,
        int agreedCells,
        int suppressedCells,
        int conflictCells,
        int mismatchCells,
        long absoluteDiffSum
) {
    public SourceToResultDiffMetricV2 {
        sourceId = V2Validation.qualifiedId(sourceId, "sourceId");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (presentCells < 0 || agreedCells < 0 || suppressedCells < 0 || conflictCells < 0
                || mismatchCells < 0 || absoluteDiffSum < 0) {
            throw new IllegalArgumentException("source-to-result metric counts must be non-negative");
        }
        if (agreedCells + suppressedCells + conflictCells != presentCells) {
            throw new IllegalArgumentException("source-to-result cell partitions do not sum to presentCells");
        }
    }
}
