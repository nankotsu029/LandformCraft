package com.github.nankotsu029.landformcraft.core.v2.operations;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Operator-safe redaction helpers for diagnostics and audit (V2-6-13). Absolute paths, URI,
 * Authorization headers, and API-key shaped tokens are refused or replaced.
 */
public final class OperationalRedactionV2 {
    private static final Pattern ABSOLUTE_PATH = Pattern.compile(
            "(?i)(/Users/|/home/|/var/|/tmp/|[A-Za-z]:\\\\|\\\\\\\\)");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(authorization\\s*[:=]|api[_-]?key\\s*[:=]|bearer\\s+[A-Za-z0-9._\\-+/=]{8,}|sk-[A-Za-z0-9]{8,})");

    private OperationalRedactionV2() {
    }

    public static String requireSafeDetail(String value, int maximumChars) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() > maximumChars) {
            trimmed = trimmed.substring(0, maximumChars);
        }
        if (containsSensitive(trimmed)) {
            throw new IllegalArgumentException("value contains path or secret material");
        }
        return trimmed;
    }

    public static String redactOrEmpty(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (containsSensitive(value)) {
            return "[redacted]";
        }
        return value.trim();
    }

    public static boolean containsSensitive(String value) {
        Objects.requireNonNull(value, "value");
        String lower = value.toLowerCase(Locale.ROOT);
        return ABSOLUTE_PATH.matcher(value).find()
                || SECRET.matcher(value).find()
                || lower.contains("://")
                || value.contains("\\");
    }
}
