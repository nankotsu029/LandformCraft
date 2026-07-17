package com.github.nankotsu029.landformcraft.validation.v2.hydrology;

/**
 * Read-only, bounded view of finalized hydrology and landform fields.
 *
 * <p>The validator depends on this field boundary rather than on a hydrology generator or its
 * task-local metric objects. Sidecar readers, whole-world samplers, and tile samplers can all
 * implement the same contract without changing validation rules.</p>
 */
public interface HydrologyFieldSamplerV2 {
    int width();

    int length();

    int valueAt(String fieldId, int globalX, int globalZ);
}
