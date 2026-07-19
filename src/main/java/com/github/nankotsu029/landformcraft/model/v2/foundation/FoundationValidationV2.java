package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared validation helpers for model.v2.foundation records. */
final class FoundationValidationV2 {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    private FoundationValidationV2() {
    }

    static <T> List<T> immutable(List<T> values, String name, int maximum) {
        Objects.requireNonNull(values, name);
        if (values.size() > maximum || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " is invalid or exceeds " + maximum);
        }
        return List.copyOf(values);
    }

    static <T> List<T> sorted(List<T> values, String name, int maximum, Comparator<? super T> comparator) {
        return immutable(values, name, maximum).stream().sorted(comparator).toList();
    }

    static String slug(String value, String name) {
        value = nonBlank(value, name, 64);
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase slug");
        }
        return value;
    }

    static String optionalSlug(String value, String name) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return slug(value, name);
    }

    static String qualified(String value, String name) {
        value = nonBlank(value, name, 128);
        if (!QUALIFIED.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase qualified ID");
        }
        return value;
    }

    static String checksum(String value, String name) {
        value = nonBlank(value, name, 64);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256");
        }
        return value;
    }

    static String nonBlank(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must be non-blank and bounded");
        }
        return value;
    }
}
