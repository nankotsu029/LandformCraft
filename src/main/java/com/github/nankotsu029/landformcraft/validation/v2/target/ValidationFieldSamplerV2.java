package com.github.nankotsu029.landformcraft.validation.v2.target;

/**
 * Read-only, bounded field view a target evaluator reads from (V2-18-04).
 *
 * <p>Deliberately identical in shape to {@code CoastalFieldSamplerV2} — the target-driven validation
 * framework depends on this narrow boundary, not on any generator or pipeline-local field object, so
 * a whole-world sampler, a tile sampler, or a future sidecar reader can all feed the same evaluators.
 * {@code valueAt} returns the raw fixed-point field value; {@code NO_DATA} sentinels are the sampler's
 * own concern and are simply not equal to any classification value an evaluator matches on.</p>
 */
public interface ValidationFieldSamplerV2 {
    int width();

    int length();

    int valueAt(String fieldId, int globalX, int globalZ);
}
