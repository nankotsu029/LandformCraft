package com.github.nankotsu029.landformcraft.validation.v2.coast;

/**
 * Read-only, bounded view of finalized coastal fields.
 *
 * <p>The validator deliberately depends on this field boundary rather than on a coastal generator
 * or its task-local metric objects. A future sidecar reader, a whole-world sampler, and a tile
 * sampler can all implement the same contract without changing validation rules.</p>
 */
public interface CoastalFieldSamplerV2 {
    int width();

    int length();

    int valueAt(String fieldId, int globalX, int globalZ);
}
