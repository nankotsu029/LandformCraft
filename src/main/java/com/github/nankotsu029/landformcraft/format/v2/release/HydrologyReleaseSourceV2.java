package com.github.nankotsu029.landformcraft.format.v2.release;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Trusted application-layer inputs used to assemble one portable {@code hydrology-plan} release.
 * Raw paths stop at this publisher boundary and are never stored in the Release manifest.
 */
public record HydrologyReleaseSourceV2(
        SurfaceReleaseSourceV2 surface,
        Path hydrologyPlan,
        Path routingIndex,
        Path routingRoot,
        Path reconciliationPlan,
        Path reconciliationArtifact,
        Path hydrologyValidationArtifact,
        Path hydrologyPreviewIndex,
        Path hydrologyPreviewRoot
) {
    public HydrologyReleaseSourceV2 {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(hydrologyPlan, "hydrologyPlan");
        Objects.requireNonNull(routingIndex, "routingIndex");
        Objects.requireNonNull(routingRoot, "routingRoot");
        Objects.requireNonNull(reconciliationPlan, "reconciliationPlan");
        Objects.requireNonNull(reconciliationArtifact, "reconciliationArtifact");
        Objects.requireNonNull(hydrologyValidationArtifact, "hydrologyValidationArtifact");
        Objects.requireNonNull(hydrologyPreviewIndex, "hydrologyPreviewIndex");
        Objects.requireNonNull(hydrologyPreviewRoot, "hydrologyPreviewRoot");
    }
}
