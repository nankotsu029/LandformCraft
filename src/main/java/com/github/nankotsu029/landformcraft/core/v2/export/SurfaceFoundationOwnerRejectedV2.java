package com.github.nankotsu029.landformcraft.core.v2.export;

import java.io.IOException;
import java.util.Objects;

/**
 * Thrown by {@link SurfaceFoundationOwnerGateV2} when a surface export run leaves cells of the
 * request domain without an effective foundation owner (V2-18-10, ADR 0038 D7-2). Extends
 * {@link IOException} so the export application services propagate it exactly like every other
 * generation-blocking failure, while still carrying the stable rule id and the measured counts.
 *
 * <p>Nothing is published when this is thrown: the gate runs after generation, on work-root
 * artifacts only, before the publisher stages or moves anything.</p>
 */
public final class SurfaceFoundationOwnerRejectedV2 extends IOException {
    private static final long serialVersionUID = 1L;

    private final String pipelineId;
    private final int ownedCells;
    private final int totalCells;
    private final String detail;

    public SurfaceFoundationOwnerRejectedV2(
            String pipelineId,
            int ownedCells,
            int totalCells,
            String detail
    ) {
        super(message(pipelineId, ownedCells, totalCells, detail));
        this.pipelineId = pipelineId;
        this.ownedCells = ownedCells;
        this.totalCells = totalCells;
        this.detail = detail;
    }

    public String ruleId() {
        return SurfaceFoundationOwnerGateV2.RULE_FOUNDATION_OWNER_COVERAGE_INCOMPLETE;
    }

    public String pipelineId() {
        return pipelineId;
    }

    public int ownedCells() {
        return ownedCells;
    }

    public int totalCells() {
        return totalCells;
    }

    public int coverageMillionths() {
        return SurfaceFoundationOwnerGateV2.coverageMillionths(ownedCells, totalCells);
    }

    public String detail() {
        return detail;
    }

    private static String message(String pipelineId, int ownedCells, int totalCells, String detail) {
        return SurfaceFoundationOwnerGateV2.RULE_FOUNDATION_OWNER_COVERAGE_INCOMPLETE
                + " [" + Objects.requireNonNull(pipelineId, "pipelineId") + "] surface foundation owner "
                + "coverage " + ownedCells + "/" + totalCells + " cells ("
                + SurfaceFoundationOwnerGateV2.coverageMillionths(ownedCells, totalCells)
                + " millionths) is below the required "
                + SurfaceFoundationOwnerGateV2.REQUIRED_COVERAGE_MILLIONTHS + ": "
                + Objects.requireNonNull(detail, "detail");
    }
}
