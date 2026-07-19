package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.ReleasePlacementInputContractV2;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.VerifiedReleaseViewV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.SpongeV3TileInspectorV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Restartable, feature-neutral placement source over one strictly verified Release 2 container.
 * Only the currently opened Sponge tile is decoded; the full block stream is never retained.
 */
public final class VerifiedReleaseCanonicalBlockSourceV2
        implements PlacementCanonicalBlockSourceV2, AutoCloseable {
    private static final String SURFACE_METADATA_TYPE = "offline-tile-artifact-v2";
    private static final String VOLUME_METADATA_TYPE = "volume-offline-tile-artifact-v2";
    private static final String SURFACE_PREFIX = "tiles/";
    private static final String VOLUME_PREFIX = "volume/tiles/";

    private final VerifiedReleaseViewV2 view;
    private final CancellationToken cancellation;
    private final SourceBindingV2 binding;
    private final Map<TileKey, TileBinding> tiles;
    private final boolean volumeFinal;
    private final PlacementLayoutV2 layout;
    private boolean closed;

    private VerifiedReleaseCanonicalBlockSourceV2(
            VerifiedReleaseViewV2 view,
            CancellationToken cancellation,
            SourceBindingV2 binding,
            Map<TileKey, TileBinding> tiles,
            boolean volumeFinal,
            PlacementLayoutV2 layout
    ) {
        this.view = view;
        this.cancellation = cancellation;
        this.binding = binding;
        this.tiles = Map.copyOf(tiles);
        this.volumeFinal = volumeFinal;
        this.layout = layout;
    }

    public static VerifiedReleaseCanonicalBlockSourceV2 open(Path release, CancellationToken cancellation)
            throws IOException {
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(cancellation, "cancellation");
        VerifiedReleaseViewV2 view = new ReleaseCoreVerifierV2().openVerified(release, cancellation);
        boolean complete = false;
        try {
            ReleaseManifestV2 manifest = view.verification().manifest();
            if (manifest.requiredCapabilities().isEmpty()) {
                throw new IOException("Release 2 placement requires a non-empty capability prefix");
            }
            boolean volume = manifest.requiredCapabilities().contains(
                    ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME);
            String metadataType = volume ? VOLUME_METADATA_TYPE : SURFACE_METADATA_TYPE;
            String prefix = volume ? VOLUME_PREFIX : SURFACE_PREFIX;
            Map<String, ReleaseArtifactDescriptorV2> byPath = new LinkedHashMap<>();
            for (ReleaseArtifactDescriptorV2 descriptor : manifest.artifacts()) {
                byPath.put(descriptor.path(), descriptor);
            }
            OfflineTileArtifactCodecV2 codec = new OfflineTileArtifactCodecV2();
            LinkedHashMap<TileKey, TileBinding> tiles = new LinkedHashMap<>();
            ArrayList<String> fingerprints = new ArrayList<>();
            for (ReleaseArtifactDescriptorV2 descriptor : manifest.artifacts()) {
                if (!descriptor.artifactType().equals(metadataType)) continue;
                cancellation.throwIfCancellationRequested();
                OfflineTileArtifactV2 tile = codec.read(view.root().resolve(descriptor.path()));
                String schematicPath = prefix + tile.schematicPath();
                ReleaseArtifactDescriptorV2 schematic = byPath.get(schematicPath);
                if (schematic == null
                        || schematic.byteLength() != tile.byteLength()
                        || !schematic.artifactChecksum().equals(tile.artifactChecksum())
                        || !schematic.semanticChecksum().equals(tile.semanticChecksum())) {
                    throw new IOException("verified Release 2 tile descriptor binding changed");
                }
                TileKey key = new TileKey(tile.originX(), tile.originZ(), tile.width(), tile.length());
                if (tiles.putIfAbsent(key, new TileBinding(tile, schematic)) != null) {
                    throw new IOException("Release 2 placement tiles contain duplicate core bounds");
                }
                fingerprints.add(tile.canonicalChecksum() + ':' + tile.semanticChecksum());
            }
            if (tiles.isEmpty()) throw new IOException("Release 2 contains no placeable final tiles");
            fingerprints.sort(String::compareTo);
            String fingerprintMaterial = manifest.canonicalChecksum() + '\n'
                    + String.join("\n", fingerprints);
            SourceBindingV2 binding = new SourceBindingV2(
                    SOURCE_CONTRACT_VERSION,
                    manifest.canonicalChecksum(),
                    manifest.requiredCapabilities(),
                    ReleasePlacementInputContractV2.overlayOrdinalsFor(manifest.requiredCapabilities()),
                    Sha256.bytes(fingerprintMaterial.getBytes(StandardCharsets.UTF_8)));
            PlacementLayoutV2 layout = placementLayout(tiles);
            VerifiedReleaseCanonicalBlockSourceV2 source = new VerifiedReleaseCanonicalBlockSourceV2(
                    view, cancellation, binding, tiles, volume, layout);
            complete = true;
            return source;
        } finally {
            if (!complete) view.close();
        }
    }

    @Override
    public SourceBindingV2 binding() {
        return binding;
    }

    /** Strictly verified release-local geometry used to compile the placement plan. */
    public PlacementLayoutV2 layout() {
        return layout;
    }

    @Override
    public BlockCursorV2 openTile(
            PlacementPlanV2 plan,
            PlacementPlanV2.TileRefV2 tile,
            WorldAabbV2 mutationRegion
    ) throws IOException {
        requireOpen();
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(mutationRegion, "mutationRegion");
        cancellation.throwIfCancellationRequested();
        if (!plan.releaseBinding().manifestChecksum().equals(binding.releaseManifestChecksum())
                || !plan.requiredCapabilities().equals(binding.requiredCapabilities())) {
            throw new IOException("placement plan differs from verified Release 2 source binding");
        }
        TileBinding selected = tiles.get(new TileKey(
                tile.coreMinX(), tile.coreMinZ(), tile.coreWidth(), tile.coreLength()));
        if (selected == null) throw new IOException("placement plan references an unknown Release 2 tile core");
        OfflineTileArtifactV2 artifact = selected.artifact();
        int worldMaxY = Math.addExact(plan.target().minimumY(), artifact.maxY() - artifact.minY());
        WorldAabbV2 expected = new WorldAabbV2(
                Math.addExact(plan.target().minimumX(), artifact.originX()),
                plan.target().minimumY(),
                Math.addExact(plan.target().minimumZ(), artifact.originZ()),
                Math.addExact(plan.target().minimumX(), artifact.originX() + artifact.width() - 1),
                worldMaxY,
                Math.addExact(plan.target().minimumZ(), artifact.originZ() + artifact.length() - 1));
        if (!expected.equals(mutationRegion)) {
            throw new IOException("placement mutation region differs from Release 2 tile bounds");
        }
        byte[] bytes = readBoundedArtifact(selected.schematic());
        SpongeV3TileInspectorV2.DecodedTile decoded = new SpongeV3TileInspectorV2()
                .decode(bytes, artifact.tilePlan());
        if (decoded.inspection().paletteSize() != artifact.paletteSize()
                || decoded.inspection().blockCount() != artifact.blockCount()
                || !decoded.inspection().semanticChecksum().equals(artifact.semanticChecksum())) {
            throw new IOException("Release 2 tile decode differs from its verified metadata");
        }
        return new Cursor(decoded.openCursor(), expected, tile.tileIndex(), artifact.blockCount(), volumeFinal,
                cancellation);
    }

    private byte[] readBoundedArtifact(ReleaseArtifactDescriptorV2 descriptor) throws IOException {
        Path path = safePath(view.root(), descriptor.path());
        long size = Files.size(path);
        if (size != descriptor.byteLength() || size > OfflineTileArtifactV2.MAXIMUM_ARTIFACT_BYTES) {
            throw new IOException("Release 2 tile byte length changed after verification");
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(Math.toIntExact(OfflineTileArtifactV2.MAXIMUM_ARTIFACT_BYTES + 1L));
        }
        if (bytes.length != size || !Sha256.bytes(bytes).equals(descriptor.artifactChecksum())) {
            throw new IOException("Release 2 tile checksum changed after verification");
        }
        return bytes;
    }

    private static Path safePath(Path root, String relative) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw new IOException("Release 2 tile path escapes verified root");
        Path current = root;
        for (Path segment : Path.of(relative)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) throw new IOException("Release 2 tile path became a symlink");
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Release 2 tile is no longer a regular file");
        }
        return path;
    }

    private void requireOpen() throws IOException {
        if (closed) throw new IOException("verified Release 2 source is closed");
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            view.close();
        }
    }

    private record TileKey(int originX, int originZ, int width, int length) {
    }

    public record PlacementLayoutV2(
            int width,
            int length,
            int minY,
            int maxY,
            List<TileLayoutV2> tiles
    ) {
        public PlacementLayoutV2 {
            if (width < 1 || length < 1 || minY > maxY || tiles.isEmpty()) {
                throw new IllegalArgumentException("invalid verified Release 2 placement layout");
            }
            tiles = List.copyOf(tiles);
        }
    }

    public record TileLayoutV2(
            int xIndex,
            int zIndex,
            int originX,
            int originZ,
            int width,
            int length,
            int minY,
            int maxY
    ) {
    }

    private static PlacementLayoutV2 placementLayout(Map<TileKey, TileBinding> bindings)
            throws IOException {
        ArrayList<TileLayoutV2> layouts = new ArrayList<>();
        int width = 0;
        int length = 0;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (TileBinding binding : bindings.values()) {
            OfflineTileArtifactV2 tile = binding.artifact();
            if (tile.originX() < 0 || tile.originZ() < 0) {
                throw new IOException("Release 2 placement tile origin must be non-negative");
            }
            width = Math.max(width, Math.addExact(tile.originX(), tile.width()));
            length = Math.max(length, Math.addExact(tile.originZ(), tile.length()));
            minY = Math.min(minY, tile.minY());
            maxY = Math.max(maxY, tile.maxY());
            layouts.add(new TileLayoutV2(
                    tile.xIndex(), tile.zIndex(), tile.originX(), tile.originZ(), tile.width(),
                    tile.length(), tile.minY(), tile.maxY()));
        }
        layouts.sort(java.util.Comparator.comparingInt(TileLayoutV2::zIndex)
                .thenComparingInt(TileLayoutV2::xIndex));
        return new PlacementLayoutV2(width, length, minY, maxY, layouts);
    }

    private record TileBinding(OfflineTileArtifactV2 artifact, ReleaseArtifactDescriptorV2 schematic) {
    }

    private static final class Cursor implements BlockCursorV2 {
        private final SpongeV3TileInspectorV2.BlockStateCursor states;
        private final WorldAabbV2 region;
        private final int ownerTileIndex;
        private final int expectedBlocks;
        private final boolean volume;
        private final CancellationToken cancellation;
        private int index;
        private boolean closed;

        private Cursor(
                SpongeV3TileInspectorV2.BlockStateCursor states,
                WorldAabbV2 region,
                int ownerTileIndex,
                int expectedBlocks,
                boolean volume,
                CancellationToken cancellation
        ) {
            this.states = states;
            this.region = region;
            this.ownerTileIndex = ownerTileIndex;
            this.expectedBlocks = expectedBlocks;
            this.volume = volume;
            this.cancellation = cancellation;
        }

        @Override
        public PlacementDesiredBlockV2 next() throws IOException {
            if (closed) throw new IOException("Release 2 block cursor is closed");
            if (index == expectedBlocks) {
                if (states.next() != null) throw new IOException("Release 2 tile has trailing block states");
                return null;
            }
            cancellation.throwIfCancellationRequested();
            String state = states.next();
            if (state == null) throw new IOException("Release 2 tile ended before exact coverage");
            int width = region.maxX() - region.minX() + 1;
            int length = region.maxZ() - region.minZ() + 1;
            int layer = Math.multiplyExact(width, length);
            int offset = index++;
            int localX = offset % width;
            int localZ = (offset / width) % length;
            int localY = offset / layer;
            PlacementApplyPassV2 pass = PlacementDesiredBlockV2.classify(state);
            int ordinal = switch (pass) {
                case SOLID -> volume ? ReleasePlacementInputContractV2.OVERLAY_VOLUME_SOLID
                        : ReleasePlacementInputContractV2.OVERLAY_SURFACE_SOLID;
                case AIR_CARVE -> volume ? ReleasePlacementInputContractV2.OVERLAY_VOLUME_AIR
                        : ReleasePlacementInputContractV2.OVERLAY_SURFACE_AIR;
                case FLUID -> volume ? ReleasePlacementInputContractV2.OVERLAY_VOLUME_FLUID
                        : ReleasePlacementInputContractV2.OVERLAY_SURFACE_FLUID;
            };
            return new PlacementDesiredBlockV2(
                    region.minX() + localX,
                    region.minY() + localY,
                    region.minZ() + localZ,
                    state,
                    pass,
                    ordinal,
                    ownerTileIndex);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                states.close();
            }
        }
    }
}
