package com.github.nankotsu029.landformcraft.core.v2.export;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * V2-18-03 preflight classification of an intent's HARD (rejection) and SOFT (warning) requirements
 * the current engine cannot honor. Never written into a sealed artifact; the export application
 * services throw on a non-empty {@link #rejections()} and may surface {@link #warnings()} to operators
 * through the CLI JSON summary.
 */
public record HardPreflightResultV2(List<Finding> rejections, List<Finding> warnings) {
    public HardPreflightResultV2 {
        rejections = List.copyOf(Objects.requireNonNull(rejections, "rejections"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public boolean rejected() {
        return !rejections.isEmpty();
    }

    /** The kind of un-honorable requirement a finding describes. Each maps 1:1 to a stable rule id. */
    public enum Category {
        HARD_CONSTRAINT_UNEVALUATED,
        HARD_RELATION_UNCONSUMED,
        MAP_REFERENCE_UNRESOLVED
    }

    /**
     * One un-honorable requirement. {@code subjectId} is the intent-authored id (constraint id,
     * relation id, or map-reference binding id); {@code detail} is a short, redaction-safe reason.
     */
    public record Finding(String ruleId, Category category, String subjectId, String detail) {
        public Finding {
            ruleId = Objects.requireNonNull(ruleId, "ruleId");
            category = Objects.requireNonNull(category, "category");
            subjectId = Objects.requireNonNull(subjectId, "subjectId");
            detail = Objects.requireNonNull(detail, "detail");
        }

        /** Flattened, JSON-serializable view for the CLI summary (never written to a sealed artifact). */
        public Map<String, Object> toSummaryMap() {
            return Map.of(
                    "ruleId", ruleId,
                    "category", category.name(),
                    "subjectId", subjectId,
                    "detail", detail);
        }
    }
}
