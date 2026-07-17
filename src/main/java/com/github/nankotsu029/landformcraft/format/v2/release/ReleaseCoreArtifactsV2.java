package com.github.nankotsu029.landformcraft.format.v2.release;

import java.nio.file.Path;
import java.util.Optional;

/** Published paths for a Release format 2 core-only release. */
public record ReleaseCoreArtifactsV2(String releaseId, Path releaseDirectory, Optional<Path> zip) {
}
