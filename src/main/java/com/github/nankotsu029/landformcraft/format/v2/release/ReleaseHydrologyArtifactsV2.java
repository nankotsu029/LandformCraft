package com.github.nankotsu029.landformcraft.format.v2.release;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Paths atomically published for one {@code hydrology-plan} Release 2 capability bundle. */
public record ReleaseHydrologyArtifactsV2(String releaseId, Path releaseDirectory, Optional<Path> zip) {
    public ReleaseHydrologyArtifactsV2 {
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(releaseDirectory, "releaseDirectory");
        Objects.requireNonNull(zip, "zip");
    }
}
