package com.github.nankotsu029.landformcraft.validation;

import com.github.nankotsu029.landformcraft.model.GenerationMetrics;
import com.github.nankotsu029.landformcraft.model.GridPosition;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.ValidationIssue;
import com.github.nankotsu029.landformcraft.model.ValidationResult;
import com.github.nankotsu029.landformcraft.model.ValidationSeverity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Explicit Phase 1 budgets for retained terrain data and measured CPU generation time. */
public final class TerrainPerformanceValidator {
    public static final long MAX_ESTIMATED_PEAK_BYTES = 96L * 1024L * 1024L;
    public static final Duration MAX_500_DURATION = Duration.ofSeconds(30);
    public static final Duration MAX_1000_DURATION = Duration.ofSeconds(60);

    public GenerationMetrics metrics(TerrainPlan plan, Duration generationDuration) {
        long columns = plan.blueprint().bounds().columnCount();
        long retainedBytes = columns * (Integer.BYTES * 3L + Byte.BYTES);
        long estimatedPeakBytes = columns * 48L
                + (long) plan.blueprint().logicalResolution() * plan.blueprint().logicalResolution()
                * Double.BYTES * 2L;
        return new GenerationMetrics(
                Math.max(0L, generationDuration.toMillis()),
                retainedBytes,
                estimatedPeakBytes
        );
    }

    public ValidationResult validate(TerrainPlan plan, Duration generationDuration) {
        GenerationMetrics metrics = metrics(plan, generationDuration);
        List<ValidationIssue> issues = new ArrayList<>();
        if (metrics.estimatedPeakWorkingBytes() > MAX_ESTIMATED_PEAK_BYTES) {
            issues.add(error(
                    "memory-budget-exceeded",
                    "estimated peak terrain memory exceeds " + MAX_ESTIMATED_PEAK_BYTES + " bytes"
            ));
        }
        Duration limit = Math.max(plan.blueprint().bounds().width(), plan.blueprint().bounds().length()) <= 500
                ? MAX_500_DURATION : MAX_1000_DURATION;
        if (generationDuration.compareTo(limit) > 0) {
            issues.add(error(
                    "generation-time-budget-exceeded",
                    "terrain generation exceeded the " + limit.toSeconds() + " second budget"
            ));
        }
        return new ValidationResult(issues);
    }

    private static ValidationIssue error(String code, String message) {
        return new ValidationIssue(ValidationSeverity.ERROR, code, message, GridPosition.GLOBAL);
    }
}
