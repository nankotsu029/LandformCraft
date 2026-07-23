package com.github.nankotsu029.landformcraft.core.v2.export;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Thrown by {@link HardPreflightGateV2#requireHonorable} before any artifact is generated when an
 * intent declares a HARD requirement the current engine cannot honor (V2-18-03). Extends
 * {@link IOException} so the export application services propagate it exactly like every other
 * generation-blocking failure, while still carrying the structured {@link #rejections()} and their
 * stable rule ids.
 */
public final class HardPreflightRejectedV2 extends IOException {
    private static final long serialVersionUID = 1L;

    private final transient HardPreflightResultV2 result;

    public HardPreflightRejectedV2(HardPreflightResultV2 result) {
        super(message(Objects.requireNonNull(result, "result")));
        this.result = result;
    }

    public List<HardPreflightResultV2.Finding> rejections() {
        return result.rejections();
    }

    public List<HardPreflightResultV2.Finding> warnings() {
        return result.warnings();
    }

    private static String message(HardPreflightResultV2 result) {
        return "HARD preflight gate rejected the terrain intent before generation: "
                + result.rejections().stream()
                .map(finding -> finding.ruleId() + " [" + finding.subjectId() + "] " + finding.detail())
                .collect(Collectors.joining("; "));
    }
}
