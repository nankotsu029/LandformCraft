package com.github.nankotsu029.landformcraft.model.v2.catalog;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared validation helpers for model.v2.catalog records. */
final class CatalogValidationV2 {
    private static final Pattern ENTRY_ID = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern PROFILE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    private CatalogValidationV2() {
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

    static String entryId(String value, String name) {
        value = nonBlank(value, name, 64);
        if (!ENTRY_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be an UPPER_SNAKE entry id");
        }
        return value;
    }

    static String profileId(String value, String name) {
        value = nonBlank(value, name, 32);
        if (!PROFILE.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be an UPPER_SNAKE profile id");
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
            throw new IllegalArgumentException(name + " must be non-blank and at most " + maximumLength);
        }
        return value;
    }

    static String optionalNonBlank(String value, String name, int maximumLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return nonBlank(value, name, maximumLength);
    }
}
