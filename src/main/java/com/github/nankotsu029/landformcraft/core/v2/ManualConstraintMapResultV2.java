package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Result of the AI-free V2-1 map path. */
public record ManualConstraintMapResultV2(
        Path bundleRoot,
        Path fieldIndex,
        ConstraintFieldIndexV2 index,
        List<TerrainIntentV2.ConstraintMapBinding> canonicalBindings,
        List<Path> diagnosticPreviews,
        long estimatedPeakResidentBytes,
        long estimatedArtifactBytes
) {
    public ManualConstraintMapResultV2 {
        Objects.requireNonNull(bundleRoot, "bundleRoot");
        Objects.requireNonNull(fieldIndex, "fieldIndex");
        Objects.requireNonNull(index, "index");
        canonicalBindings = List.copyOf(canonicalBindings);
        diagnosticPreviews = List.copyOf(diagnosticPreviews);
        if (estimatedPeakResidentBytes < 1 || estimatedArtifactBytes < 1) {
            throw new IllegalArgumentException("manual constraint estimates must be positive");
        }
    }
}
