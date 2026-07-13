package com.github.nankotsu029.landformcraft.format;

import java.nio.file.Path;
import java.util.Optional;

public record ReleaseArtifacts(
        String releaseId,
        Path releaseDirectory,
        Optional<Path> zip,
        Optional<Path> zipChecksum
) {
}
