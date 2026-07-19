package com.github.nankotsu029.landformcraft.format.v2.release;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Paths atomically published for one {@code sparse-volume} Release 2 capability bundle. */
public record ReleaseSparseVolumeArtifactsV2(String releaseId, Path releaseDirectory, Optional<Path> zip) {
    public ReleaseSparseVolumeArtifactsV2 {
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(releaseDirectory, "releaseDirectory");
        Objects.requireNonNull(zip, "zip");
    }
}
