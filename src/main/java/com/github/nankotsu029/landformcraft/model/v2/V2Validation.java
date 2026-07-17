package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class V2Validation {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern QUALIFIED_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    private V2Validation() {
    }

    static String slug(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase slug");
        }
        return value;
    }

    static String qualifiedId(String value, String field) {
        value = nonBlank(value, field, 128);
        if (!QUALIFIED_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase qualified id");
        }
        return value;
    }

    static String checksum(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256");
        }
        return value;
    }

    static String safeRelativePath(String value, String field) {
        value = nonBlank(value, field, 512);
        if (value.indexOf('\0') >= 0
                || value.startsWith("/")
                || value.startsWith("\\")
                || value.indexOf('\\') >= 0
                || (value.length() >= 2 && Character.isLetter(value.charAt(0)) && value.charAt(1) == ':')) {
            throw new IllegalArgumentException(field + " must be a portable relative path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(field + " contains an unsafe path segment");
            }
        }
        return value;
    }

    static String nonBlank(String value, String field, int maximumLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + maximumLength);
        }
        return value;
    }

    static <T> List<T> immutable(List<T> value, String field, int maximumSize) {
        Objects.requireNonNull(value, field);
        if (value.size() > maximumSize || value.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " is invalid or exceeds " + maximumSize);
        }
        return List.copyOf(value);
    }

    static <T> List<T> sorted(List<T> value, String field, int maximumSize, Comparator<T> comparator) {
        return immutable(value, field, maximumSize).stream().sorted(comparator).toList();
    }
}
