package com.github.nankotsu029.landformcraft.format.v2.release;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Trusted application-layer inputs used to assemble one portable {@code sparse-volume} release on
 * top of a complete {@code environment-fields} source. Raw paths stop at the publisher boundary and
 * are never stored in the Release manifest.
 */
public record SparseVolumeReleaseSourceV2(
        EnvironmentReleaseSourceV2 environment,
        Path sdfPrimitivePlan,
        Path csgPlan,
        Path aabbIndexPlan,
        Path volumeValidationArtifact,
        List<TileSource> tiles
) {
    public SparseVolumeReleaseSourceV2 {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(sdfPrimitivePlan, "sdfPrimitivePlan");
        Objects.requireNonNull(csgPlan, "csgPlan");
        Objects.requireNonNull(aabbIndexPlan, "aabbIndexPlan");
        Objects.requireNonNull(volumeValidationArtifact, "volumeValidationArtifact");
        tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles"));
        if (tiles.isEmpty()) {
            throw new IllegalArgumentException("sparse-volume Release requires at least one volume tile");
        }
    }

    /** One volume tile as a metadata JSON plus its Sponge v3 schematic. */
    public record TileSource(String tileId, Path metadata, Path schematic) {
        public TileSource {
            Objects.requireNonNull(tileId, "tileId");
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(schematic, "schematic");
        }
    }
}
