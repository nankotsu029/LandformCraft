package com.github.nankotsu029.landformcraft.model.v2.foundation;

/**
 * Stable derived component IDs for V2-9-03 mountain-range foundation.
 * These are COMPONENT roles, not {@code FeatureKind} values.
 */
public final class MountainRangeComponentCatalogV2 {
    public enum ComponentRole {
        RIDGE,
        PEAK,
        SADDLE,
        SPUR,
        PASS,
        FOOTHILL
    }

    private MountainRangeComponentCatalogV2() {
    }

    public static String derivedId(ComponentRole role, String featureId, int oneBasedIndex) {
        if (oneBasedIndex < 1) {
            throw new IllegalArgumentException("component index must be at least 1");
        }
        String slug = FoundationValidationV2.slug(featureId, "featureId");
        return switch (role) {
            case RIDGE -> "ridge-" + slug + "-" + oneBasedIndex;
            case PEAK -> "peak-" + slug + "-" + oneBasedIndex;
            case SADDLE -> "saddle-" + slug + "-" + oneBasedIndex;
            case SPUR -> "spur-" + slug + "-" + oneBasedIndex;
            case PASS -> "pass-" + slug + "-" + oneBasedIndex;
            case FOOTHILL -> "foothill-" + slug + "-" + oneBasedIndex;
        };
    }
}
