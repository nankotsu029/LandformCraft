package com.github.nankotsu029.landformcraft.core.v2.export;

import java.io.IOException;
import java.util.Objects;

/**
 * Thrown by {@link WaterfallBasinMaterializationV2} when a {@code WATERFALL} plunge basin cannot be
 * materialized into the final canonical block stream without breaking one of the declared
 * interaction contracts (V2-15-13).
 *
 * <p>Extends {@link IOException} so the export application services propagate it exactly like every
 * other generation-blocking failure, while carrying the stable rule id and the offending cell.
 * Nothing is published when this is thrown: the freeze runs on work-root state before any tile is
 * written, so a rejected run leaves no artifact behind.</p>
 */
public final class WaterfallMaterializationRejectedV2 extends IOException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;
    private final String featureId;
    private final int cellX;
    private final int cellZ;
    private final String detail;

    WaterfallMaterializationRejectedV2(String ruleId, String featureId, int cellX, int cellZ, String detail) {
        super(message(ruleId, featureId, cellX, cellZ, detail));
        this.ruleId = ruleId;
        this.featureId = featureId;
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.detail = detail;
    }

    public String ruleId() {
        return ruleId;
    }

    public String featureId() {
        return featureId;
    }

    /** Offending release-local cell, or {@code -1} for a whole-basin rejection. */
    public int cellX() {
        return cellX;
    }

    public int cellZ() {
        return cellZ;
    }

    public String detail() {
        return detail;
    }

    private static String message(String ruleId, String featureId, int cellX, int cellZ, String detail) {
        String cell = cellX < 0 || cellZ < 0 ? "basin" : cellX + "," + cellZ;
        return Objects.requireNonNull(ruleId, "ruleId")
                + " [" + WaterfallBasinMaterializationV2.CONTRACT_VERSION + "] waterfall "
                + Objects.requireNonNull(featureId, "featureId") + " at " + cell + ": "
                + Objects.requireNonNull(detail, "detail");
    }
}
