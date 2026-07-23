package com.github.nankotsu029.landformcraft.validation.v2.coast;

import com.github.nankotsu029.landformcraft.validation.v2.target.ValidationFieldSamplerV2;

/**
 * Read-only, bounded view of finalized coastal fields.
 *
 * <p>The validator deliberately depends on this field boundary rather than on a coastal generator
 * or its task-local metric objects. A future sidecar reader, a whole-world sampler, and a tile
 * sampler can all implement the same contract without changing validation rules. It extends the
 * generic {@link ValidationFieldSamplerV2} (V2-18-04, identical shape) so a coastal field set can
 * feed the target-driven validation framework without an adapter.</p>
 */
public interface CoastalFieldSamplerV2 extends ValidationFieldSamplerV2 {
}
