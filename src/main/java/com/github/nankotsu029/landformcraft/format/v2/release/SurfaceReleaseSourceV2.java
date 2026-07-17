package com.github.nankotsu029.landformcraft.format.v2.release;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Trusted application-layer inputs used to assemble one portable {@code surface-2_5d} release.
 * Raw paths stop at this publisher boundary and are never stored in the Release manifest.
 */
public record SurfaceReleaseSourceV2(
        Path generationRequest,
        Path terrainIntent,
        Path worldBlueprint,
        Path constraintFieldIndex,
        Path constraintFieldRoot,
        Path coastalValidationArtifact,
        Path coastalPreviewIndex,
        Path coastalPreviewRoot,
        List<TileSource> tiles
) {
    public SurfaceReleaseSourceV2 {
        Objects.requireNonNull(generationRequest, "generationRequest");
        Objects.requireNonNull(terrainIntent, "terrainIntent");
        Objects.requireNonNull(worldBlueprint, "worldBlueprint");
        Objects.requireNonNull(constraintFieldIndex, "constraintFieldIndex");
        Objects.requireNonNull(constraintFieldRoot, "constraintFieldRoot");
        Objects.requireNonNull(coastalValidationArtifact, "coastalValidationArtifact");
        Objects.requireNonNull(coastalPreviewIndex, "coastalPreviewIndex");
        Objects.requireNonNull(coastalPreviewRoot, "coastalPreviewRoot");
        tiles = tiles.stream().sorted(Comparator.comparing(TileSource::tileId)).toList();
        if (tiles.isEmpty() || tiles.size() > 64) {
            throw new IllegalArgumentException("surface Release requires between 1 and 64 tile sources");
        }
        for (int index = 1; index < tiles.size(); index++) {
            if (tiles.get(index - 1).tileId().equals(tiles.get(index).tileId())) {
                throw new IllegalArgumentException("surface Release tile IDs must be unique");
            }
        }
    }

    public record TileSource(String tileId, Path metadata, Path schematic) {
        public TileSource {
            if (tileId == null || !tileId.matches("^[a-z0-9][a-z0-9._-]{0,127}$")) {
                throw new IllegalArgumentException("surface Release tileId is invalid");
            }
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(schematic, "schematic");
        }
    }
}
