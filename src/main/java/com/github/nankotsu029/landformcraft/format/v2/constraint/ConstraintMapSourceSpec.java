package com.github.nankotsu029.landformcraft.format.v2.constraint;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/** Filesystem-facing source contract. Semantic role and hardness deliberately live elsewhere. */
public record ConstraintMapSourceSpec(
        String sourceId,
        String relativePath,
        String expectedSha256,
        int expectedWidth,
        int expectedLength
) {
    private static final Pattern SOURCE_ID = Pattern.compile(
            "constraint-source:[a-z0-9][a-z0-9._-]{0,63}"
    );
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern PORTABLE_PATH = Pattern.compile("[A-Za-z0-9._/-]{1,512}");

    public ConstraintMapSourceSpec {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(expectedSha256, "expectedSha256");
        if (!SOURCE_ID.matcher(sourceId).matches()) {
            throw failure(ConstraintMapFailureCode.INVALID_DESCRIPTOR, "invalid constraint-map source ID");
        }
        validateRelativePath(relativePath);
        if (!SHA_256.matcher(expectedSha256).matches()) {
            throw failure(ConstraintMapFailureCode.INVALID_DESCRIPTOR,
                    "expectedSha256 must be a lowercase SHA-256");
        }
        if (expectedWidth < 1 || expectedWidth > 4_096
                || expectedLength < 1 || expectedLength > 4_096) {
            throw failure(ConstraintMapFailureCode.INVALID_DESCRIPTOR,
                    "expected source dimensions must be between 1 and 4096");
        }
    }

    private static void validateRelativePath(String value) {
        if (!PORTABLE_PATH.matcher(value).matches() || value.indexOf('\0') >= 0
                || value.startsWith("/") || value.startsWith("\\") || value.indexOf('\\') >= 0
                || value.length() >= 2 && Character.isLetter(value.charAt(0)) && value.charAt(1) == ':') {
            throw failure(ConstraintMapFailureCode.UNSAFE_PATH, "constraint-map path must be portable and relative");
        }
        try {
            if (Path.of(value).isAbsolute()) {
                throw failure(ConstraintMapFailureCode.UNSAFE_PATH,
                        "constraint-map path must be portable and relative");
            }
        } catch (InvalidPathException exception) {
            throw new ConstraintMapInputException(
                    ConstraintMapFailureCode.UNSAFE_PATH, "constraint-map path is invalid", exception);
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw failure(ConstraintMapFailureCode.UNSAFE_PATH,
                        "constraint-map path contains an unsafe segment");
            }
        }
    }

    private static ConstraintMapInputException failure(ConstraintMapFailureCode code, String message) {
        return new ConstraintMapInputException(code, message);
    }
}
