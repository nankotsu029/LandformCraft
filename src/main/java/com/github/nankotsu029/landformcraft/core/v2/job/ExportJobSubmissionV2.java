package com.github.nankotsu029.landformcraft.core.v2.job;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.export.ExportBudgetV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.export.SurfaceBaselineV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobKindV2;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything one asynchronous v2 export job needs (V2-12-09).
 *
 * <p>{@code requestId} is carried explicitly rather than re-read from the request file at every
 * transition: it is what {@code v2 candidate list} groups by, and a job must stay attributable to
 * its request even if the request file is later edited.</p>
 */
public record ExportJobSubmissionV2(
        String requestId,
        String releaseId,
        ExportJobKindV2 kind,
        Path generationRequest,
        Path terrainIntent,
        Path workRoot,
        Path exportsRoot,
        SurfaceBaselineV2 baseline,
        ExportBudgetV2 budget
) {
    public ExportJobSubmissionV2 {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(generationRequest, "generationRequest");
        Objects.requireNonNull(terrainIntent, "terrainIntent");
        Objects.requireNonNull(workRoot, "workRoot");
        Objects.requireNonNull(exportsRoot, "exportsRoot");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(budget, "budget");
    }

    /** Binds this submission to the job's cancellation token. */
    public Release2ExportRequestV2 toExportRequest(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        return new Release2ExportRequestV2(
                generationRequest, terrainIntent, workRoot, exportsRoot, releaseId, baseline,
                kind == ExportJobKindV2.EXPORT, budget, Optional.of(token));
    }
}
