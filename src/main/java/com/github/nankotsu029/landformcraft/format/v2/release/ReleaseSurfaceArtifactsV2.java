package com.github.nankotsu029.landformcraft.format.v2.release;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Paths atomically published for one {@code surface-2_5d} Release 2 capability bundle. */
public record ReleaseSurfaceArtifactsV2(String releaseId, Path releaseDirectory, Optional<Path> zip) {
    public ReleaseSurfaceArtifactsV2 {
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(releaseDirectory, "releaseDirectory");
        Objects.requireNonNull(zip, "zip");
    }
}
