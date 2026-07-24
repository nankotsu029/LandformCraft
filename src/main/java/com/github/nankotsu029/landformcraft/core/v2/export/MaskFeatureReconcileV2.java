package com.github.nankotsu029.landformcraft.core.v2.export;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Report of one mask ⇔ feature reconcile pre-pass (V2-19-14, ADR 0043).
 *
 * <p>Report-only: it is never written into the Blueprint, the constraint field index or the Release
 * manifest, so its presence moves no checksum. It exists so the operator can see exactly which rigid
 * translation the pre-pass applied to the declared geometry — the correction is bounded and
 * deterministic, but it must not be invisible.</p>
 *
 * <p>The published {@code source/terrain-intent.json} carries the <em>reconciled</em> geometry (ADR
 * 0043 D5): the Release must describe the terrain it actually contains. The author's own request and
 * intent files are untouched, and this report is what connects the two.</p>
 *
 * @param toleranceBlocks the declared Chebyshev radius of the candidate set
 * @param offsetXBlocks applied translation along X, always within ±{@code toleranceBlocks}
 * @param offsetZBlocks applied translation along Z, always within ±{@code toleranceBlocks}
 * @param evaluatedCells cells the objective was measured over (the coastal-active set)
 * @param disagreementBefore mask disagreements of the declared geometry (offset {@code (0,0)})
 * @param disagreementAfter mask disagreements at the applied offset
 * @param candidateOffsets candidate offsets in the Chebyshev ball, {@code (0,0)} included
 * @param rejectedOffsets candidates rejected as invalid (translation would leave the domain or the
 *        normalized coordinate range)
 * @param translatedFeatures declared features whose geometry the applied offset moved
 */
public record MaskFeatureReconcileV2(
        int toleranceBlocks,
        int offsetXBlocks,
        int offsetZBlocks,
        int evaluatedCells,
        int disagreementBefore,
        int disagreementAfter,
        long candidateOffsets,
        long rejectedOffsets,
        int translatedFeatures
) {
    public static final String CONTRACT_VERSION = "mask-feature-reconcile-v1";

    public MaskFeatureReconcileV2 {
        if (Math.abs(offsetXBlocks) > toleranceBlocks || Math.abs(offsetZBlocks) > toleranceBlocks) {
            throw new IllegalArgumentException("reconcile offset outside the declared tolerance");
        }
        if (evaluatedCells < 0 || disagreementBefore < 0 || disagreementAfter < 0
                || candidateOffsets < 1 || rejectedOffsets < 0 || translatedFeatures < 0) {
            throw new IllegalArgumentException("invalid reconcile report counters");
        }
    }

    /** Whether the pre-pass moved anything at all. Geometry that already agreed is never moved. */
    public boolean applied() {
        return offsetXBlocks != 0 || offsetZBlocks != 0;
    }

    /** Stable, machine-readable summary for the CLI and Paper export surfaces. */
    public Map<String, Object> toSummaryMap() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contract", CONTRACT_VERSION);
        summary.put("applied", applied());
        summary.put("toleranceBlocks", toleranceBlocks);
        summary.put("offsetXBlocks", offsetXBlocks);
        summary.put("offsetZBlocks", offsetZBlocks);
        summary.put("evaluatedCells", evaluatedCells);
        summary.put("maskDisagreementBefore", disagreementBefore);
        summary.put("maskDisagreementAfter", disagreementAfter);
        summary.put("candidateOffsets", candidateOffsets);
        summary.put("rejectedOffsets", rejectedOffsets);
        summary.put("translatedFeatures", translatedFeatures);
        return java.util.Collections.unmodifiableMap(summary);
    }
}
