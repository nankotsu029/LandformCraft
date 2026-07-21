package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;

/**
 * Diagnostic-preview cell budget shared by coastal／hydrology／environment preview index codecs
 * (V2-13-03). Raised from the original 1_000_000-cell literal to the MEDIUM horizontal ceiling's
 * full-resolution cell count so 1024×1024 = 1_048_576 cells is admitted (cell-budget follow-up to
 * V2-13-03/V2-13-04).
 */
public final class PreviewDimensionBudgetV2 {
    /** Maximum admitted width×length for one diagnostic preview layer. */
    public static final long MAXIMUM_CELLS = ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS;

    private PreviewDimensionBudgetV2() {
    }

    /** True when both edges are within the MEDIUM ceiling and the cell product is within budget. */
    public static boolean admits(int width, int length) {
        return width >= 1
                && length >= 1
                && width <= ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING
                && length <= ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING
                && (long) width * (long) length <= MAXIMUM_CELLS;
    }

    /**
     * Fail-closed admission used by preview PNG read-back. Callers must not allocate decoded
     * pixels when this throws.
     */
    public static void requireAdmitted(int width, int length, String what) {
        if (!admits(width, length)) {
            throw new IllegalArgumentException(
                    what + " dimensions exceed the preview cell budget ("
                            + MAXIMUM_CELLS + " cells; MEDIUM ceiling "
                            + ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING + "): "
                            + width + "x" + length);
        }
    }
}
