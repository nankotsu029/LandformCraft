package com.github.nankotsu029.landformcraft.model;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class ModelValidation {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private ModelValidation() {
    }

    static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    static String requireNonBlank(String value, String fieldName, int maximumLength) {
        value = requireNonBlank(value, fieldName);
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maximumLength + " characters");
        }
        return value;
    }

    static String requireSlug(String value, String fieldName) {
        value = requireNonBlank(value, fieldName);
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must match " + SLUG);
        }
        return value;
    }

    static String requireSafeRelativePath(String value, String fieldName) {
        value = requireNonBlank(value, fieldName, 512);
        if (value.indexOf('\0') >= 0
                || value.startsWith("/")
                || value.startsWith("\\")
                || value.indexOf('\\') >= 0
                || (value.length() >= 2 && Character.isLetter(value.charAt(0)) && value.charAt(1) == ':')) {
            throw new IllegalArgumentException(fieldName + " must be a portable relative path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(fieldName + " contains an unsafe path segment");
            }
        }
        return value;
    }

    static int requireSchemaVersion(int value, String fieldName) {
        if (value != 1) {
            throw new IllegalArgumentException(fieldName + " must be 1");
        }
        return value;
    }

    static double requireUnitInterval(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and between 0.0 and 1.0");
        }
        return value == 0.0 ? 0.0 : value;
    }

    static <T> List<T> immutableList(List<T> value, String fieldName, int maximumSize) {
        Objects.requireNonNull(value, fieldName);
        if (value.size() > maximumSize) {
            throw new IllegalArgumentException(fieldName + " must not contain more than " + maximumSize + " elements");
        }
        if (value.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(fieldName + " must not contain null elements");
        }
        return List.copyOf(value);
    }
}
