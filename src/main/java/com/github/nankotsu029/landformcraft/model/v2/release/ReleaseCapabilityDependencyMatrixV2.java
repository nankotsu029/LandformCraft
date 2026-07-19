package com.github.nankotsu029.landformcraft.model.v2.release;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Authoritative Release format 2 capability dependency matrix (V2-6-12).
 * Placement plans and the Release catalog must both refuse any set outside
 * {@link #validPrefixes()}.
 */
public final class ReleaseCapabilityDependencyMatrixV2 {
    public static final String CONTRACT_VERSION = "release-2-capability-dependency-matrix-v1";

    public static final String SURFACE_TWO_POINT_FIVE_D = "surface-2_5d";
    public static final String HYDROLOGY_PLAN = "hydrology-plan";
    public static final String ENVIRONMENT_FIELDS = "environment-fields";
    public static final String SPARSE_VOLUME = "sparse-volume";

    public static final List<String> CORE_ONLY = List.of();
    public static final List<String> SURFACE_ONLY = List.of(SURFACE_TWO_POINT_FIVE_D);
    public static final List<String> HYDROLOGY_WITH_SURFACE = List.of(HYDROLOGY_PLAN, SURFACE_TWO_POINT_FIVE_D);
    public static final List<String> ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE =
            List.of(ENVIRONMENT_FIELDS, HYDROLOGY_PLAN, SURFACE_TWO_POINT_FIVE_D);
    public static final List<String> SPARSE_VOLUME_WITH_ENVIRONMENT = List.of(
            ENVIRONMENT_FIELDS, HYDROLOGY_PLAN, SPARSE_VOLUME, SURFACE_TWO_POINT_FIVE_D);

    private static final Set<String> KNOWN = Set.of(
            SURFACE_TWO_POINT_FIVE_D, HYDROLOGY_PLAN, ENVIRONMENT_FIELDS, SPARSE_VOLUME);
    private static final Set<List<String>> VALID_PREFIXES = Set.of(
            CORE_ONLY,
            SURFACE_ONLY,
            HYDROLOGY_WITH_SURFACE,
            ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE,
            SPARSE_VOLUME_WITH_ENVIRONMENT);

    private ReleaseCapabilityDependencyMatrixV2() {
    }

    public static Set<String> knownCapabilities() {
        return KNOWN;
    }

    /** Canonical sorted capability prefixes that Release format 2 may publish or place. */
    public static Set<List<String>> validPrefixes() {
        return VALID_PREFIXES;
    }

    public static List<List<String>> validPrefixesInStableOrder() {
        List<List<String>> ordered = new ArrayList<>(VALID_PREFIXES);
        ordered.sort(Comparator
                .<List<String>>comparingInt(List::size)
                .thenComparing(list -> String.join(",", list)));
        return List.copyOf(ordered);
    }

    public static boolean isKnownCapability(String capability) {
        return capability != null && KNOWN.contains(capability);
    }

    public static boolean isValidPrefix(List<String> capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        return VALID_PREFIXES.contains(normalize(capabilities));
    }

    /**
     * Normalizes to sorted unique capability ids. Does not accept unknown ids —
     * callers must reject those separately via {@link #requireKnownCapabilities}.
     */
    public static List<String> normalize(List<String> capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        return capabilities.stream()
                .map(value -> {
                    Objects.requireNonNull(value, "capability");
                    return value;
                })
                .distinct()
                .sorted()
                .toList();
    }

    public static void requireKnownCapabilities(List<String> capabilities) {
        for (String capability : Objects.requireNonNull(capabilities, "capabilities")) {
            if (!isKnownCapability(capability)) {
                throw new IllegalArgumentException("unknown Release format 2 capability: " + capability);
            }
        }
    }

    public static void requireValidPrefix(List<String> capabilities) {
        requireKnownCapabilities(capabilities);
        List<String> normalized = normalize(capabilities);
        if (!VALID_PREFIXES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "unsupported Release format 2 capability combination: "
                            + String.join(",", normalized));
        }
    }

    /** Dependency explanation for diagnostics; never used to silently repair a set. */
    public static String dependencyFailureMessage(List<String> capabilities) {
        List<String> normalized = normalize(capabilities);
        if (normalized.equals(List.of(HYDROLOGY_PLAN))
                || (normalized.contains(HYDROLOGY_PLAN) && !normalized.contains(SURFACE_TWO_POINT_FIVE_D))) {
            return "hydrology-plan requires surface-2_5d";
        }
        if (normalized.contains(ENVIRONMENT_FIELDS)
                && (!normalized.contains(HYDROLOGY_PLAN) || !normalized.contains(SURFACE_TWO_POINT_FIVE_D))) {
            return "environment-fields requires hydrology-plan and surface-2_5d";
        }
        if (normalized.contains(SPARSE_VOLUME)
                && (!normalized.contains(ENVIRONMENT_FIELDS)
                || !normalized.contains(HYDROLOGY_PLAN)
                || !normalized.contains(SURFACE_TWO_POINT_FIVE_D))) {
            return "sparse-volume requires environment-fields, hydrology-plan, and surface-2_5d";
        }
        return "Release format 2 capability combination is unsupported: "
                + String.join(",", normalized).toLowerCase(Locale.ROOT);
    }
}
