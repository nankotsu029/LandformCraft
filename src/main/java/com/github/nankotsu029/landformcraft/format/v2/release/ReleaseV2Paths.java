package com.github.nankotsu029.landformcraft.format.v2.release;

import java.io.IOException;
import java.nio.file.Path;

/** Portable, strict path rules shared by the Release format 2 publisher and verifier. */
final class ReleaseV2Paths {
    private ReleaseV2Paths() {
    }

    static String canonicalRelativePath(String input) throws IOException {
        if (input == null || input.isBlank() || input.startsWith("/") || input.startsWith("\\")
                || input.contains("\\") || input.contains("//") || input.matches("^[A-Za-z]:.*")) {
            throw new IOException("unsafe Release format 2 path: " + input);
        }
        Path normalized;
        try {
            normalized = Path.of(input).normalize();
        } catch (RuntimeException exception) {
            throw new IOException("invalid Release format 2 path", exception);
        }
        String canonical = normalized.toString().replace('\\', '/');
        if (canonical.equals(".") || canonical.equals("..") || canonical.startsWith("../")
                || !canonical.equals(input)) {
            throw new IOException("non-canonical Release format 2 path: " + input);
        }
        return canonical;
    }
}
