package com.github.nankotsu029.landformcraft.core.v2.export;

import java.util.Objects;

/**
 * V2-18-10 surface foundation owner gate (ADR 0038 D7-2).
 *
 * <p>The V2-18 macro foundation audit found that the surface production path had no basement owner:
 * every cell no coastal feature claimed (measured 73.0%) was filled from the {@code SurfaceBaselineV2}
 * argument, which is a constant fill and not an owner. {@code V2-18-02} made that observable as the
 * report-only {@code surfaceFoundationOwnerCoverage} metric and {@code V2-18-09} wired the macro
 * foundation stage that makes the metric reach 100% from an explicit foundation input. This gate is
 * the last step of that sequence: it promotes the metric to a fail-closed export requirement, so a
 * surface Release can no longer be published over cells that have no effective foundation owner.</p>
 *
 * <p><b>Scope (task-index §18.2 {@code V2-18-10}).</b> The gate measures the surface domain's
 * <em>foundation</em> owner coverage only:</p>
 * <ul>
 *   <li>modifier (active contributor), volume, and material coverage are <em>not</em> gated — surface
 *       modifiers legitimately claim only their footprints;</li>
 *   <li>cells outside the request domain are not measured: the metric counts exactly the
 *       width × length cells of the run;</li>
 *   <li>input no-data is not a gate subject: a mask no-data cell with no producer is already rejected
 *       at generation time by the ADR 0038 D7-1 kernel invariant ({@code OWNERLESS_CELL}), so it never
 *       reaches this measurement;</li>
 *   <li>multiple owners over one cell are allowed — resolution to exactly one effective owner is the
 *       merge contract's job (ADR 0038 D1-4), and this gate only asks whether an effective owner
 *       exists.</li>
 * </ul>
 *
 * <p>There is deliberately no override flag, matching {@link HardPreflightGateV2}: the surface
 * baseline argument cannot buy back coverage (ADR 0038 D8-2). The gate is a pure measurement check —
 * it writes nothing, mutates nothing, and never runs before the coverage metric exists, so terrain
 * field, tile, and block semantic checksums are unaffected by it.</p>
 */
public final class SurfaceFoundationOwnerGateV2 {
    public static final String CONTRACT_VERSION = "surface-foundation-owner-gate-v1";

    /** Stable rule id of a surface export rejected for incomplete foundation owner coverage. */
    public static final String RULE_FOUNDATION_OWNER_COVERAGE_INCOMPLETE =
            "v2.export.foundation-owner-coverage-incomplete";

    /** Required coverage: every cell of the surface domain, expressed on the metric's fixed scale. */
    public static final int REQUIRED_COVERAGE_MILLIONTHS = 1_000_000;

    /**
     * Rejects the export when the V2-18-02 foundation owner metric of this run is below 100%.
     * {@code detail} is a short, redaction-safe operator hint about the missing input.
     */
    public void requireFullCoverage(
            String pipelineId,
            IntentContributionCoverageV2 coverage,
            String detail
    ) throws SurfaceFoundationOwnerRejectedV2 {
        Objects.requireNonNull(coverage, "coverage");
        requireFullCoverage(pipelineId, coverage.surfaceFoundationOwnerCells(), coverage.totalCells(), detail);
    }

    /**
     * Same gate over a directly counted owner cell total, for surface paths that own their own
     * foundation merge instead of the coastal composition fields.
     */
    public void requireFullCoverage(
            String pipelineId,
            int ownedCells,
            int totalCells,
            String detail
    ) throws SurfaceFoundationOwnerRejectedV2 {
        Objects.requireNonNull(pipelineId, "pipelineId");
        Objects.requireNonNull(detail, "detail");
        if (totalCells < 1 || ownedCells < 0 || ownedCells > totalCells) {
            throw new IllegalArgumentException("surface foundation owner coverage counts are invalid");
        }
        if (ownedCells == totalCells) {
            return;
        }
        throw new SurfaceFoundationOwnerRejectedV2(pipelineId, ownedCells, totalCells, detail);
    }

    /** Coverage of one run on the metric's fixed millionths scale, floor-rounded like V2-18-02. */
    public static int coverageMillionths(int ownedCells, int totalCells) {
        if (totalCells < 1) {
            return 0;
        }
        return Math.toIntExact(Math.floorDiv((long) ownedCells * REQUIRED_COVERAGE_MILLIONTHS, totalCells));
    }
}
