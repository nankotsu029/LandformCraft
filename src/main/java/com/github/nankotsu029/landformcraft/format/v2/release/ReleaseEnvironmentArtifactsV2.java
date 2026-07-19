package com.github.nankotsu029.landformcraft.format.v2.release;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Paths atomically published for one {@code environment-fields} Release 2 capability bundle. */
public record ReleaseEnvironmentArtifactsV2(String releaseId, Path releaseDirectory, Optional<Path> zip) {
    public ReleaseEnvironmentArtifactsV2 {
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(releaseDirectory, "releaseDirectory");
        Objects.requireNonNull(zip, "zip");
    }
}
