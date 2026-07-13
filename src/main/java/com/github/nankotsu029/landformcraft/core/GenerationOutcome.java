package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.model.GenerationMetrics;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.ValidationResult;
import com.github.nankotsu029.landformcraft.preview.PreviewArtifacts;

import java.nio.file.Path;

public record GenerationOutcome(
        TerrainPlan terrainPlan,
        ValidationResult validation,
        GenerationMetrics metrics,
        PreviewArtifacts previews,
        Path outputDirectory
) {
}
