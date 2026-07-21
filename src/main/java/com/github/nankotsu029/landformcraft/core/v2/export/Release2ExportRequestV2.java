package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactPublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactsV2;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Trusted application-layer inputs for one production Release 2 export (V2-12-02).
 *
 * <p>{@code terrainIntent} is a design-stage artifact — normally
 * {@code DesignArtifactsV2.directory()/terrain-intent.json}. Nothing here is inferred: the caller
 * states the request, the intent, the surface baseline, and where the Release is published.</p>
 */
public record Release2ExportRequestV2(
        Path generationRequest,
        Path terrainIntent,
        Path workRoot,
        Path exportsRoot,
        String releaseId,
        SurfaceBaselineV2 baseline,
        boolean createZip,
        ExportBudgetV2 budget,
        Optional<CancellationToken> cancellationToken
) {
    private static final Pattern RELEASE_ID = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");

    public Release2ExportRequestV2 {
        Objects.requireNonNull(generationRequest, "generationRequest");
        Objects.requireNonNull(terrainIntent, "terrainIntent");
        Objects.requireNonNull(workRoot, "workRoot");
        Objects.requireNonNull(exportsRoot, "exportsRoot");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (releaseId == null || !RELEASE_ID.matcher(releaseId).matches()) {
            throw new IllegalArgumentException("releaseId must be a lowercase portable slug");
        }
    }

    public Release2ExportRequestV2(
            Path generationRequest,
            Path terrainIntent,
            Path workRoot,
            Path exportsRoot,
            String releaseId,
            SurfaceBaselineV2 baseline
    ) {
        this(generationRequest, terrainIntent, workRoot, exportsRoot, releaseId, baseline, true,
                ExportBudgetV2.defaults(), Optional.empty());
    }

    /**
     * Chains a completed {@code TerrainDesignApplicationServiceV2} run into the export path by
     * reading the sealed intent the design stage published.
     */
    public static Release2ExportRequestV2 fromDesign(
            Path generationRequest,
            DesignArtifactsV2 design,
            Path workRoot,
            Path exportsRoot,
            String releaseId,
            SurfaceBaselineV2 baseline
    ) {
        Objects.requireNonNull(design, "design");
        return new Release2ExportRequestV2(
                generationRequest,
                design.directory().resolve(DesignArtifactPublisherV2.INTENT_FILE),
                workRoot, exportsRoot, releaseId, baseline);
    }
}
