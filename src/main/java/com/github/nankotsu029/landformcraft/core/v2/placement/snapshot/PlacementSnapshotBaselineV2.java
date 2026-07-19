package com.github.nankotsu029.landformcraft.core.v2.placement.snapshot;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementExpectedBlockResolverV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Strict, admitted snapshot baseline used by containment and full verify. */
public final class PlacementSnapshotBaselineV2 {
    private PlacementSnapshotBaselineV2() { }

    public static PlacementExpectedBlockResolverV2.SnapshotBaselineV2 load(
            Path snapshotsRoot,
            PlacementPlanV2 plan,
            PlacementSnapshotPlanV2 snapshot,
            long maximumBlocks,
            CancellationToken cancellation
    ) throws IOException {
        Objects.requireNonNull(snapshotsRoot, "snapshotsRoot");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(cancellation, "cancellation");
        long admitted = 0L;
        for (PlacementSnapshotPlanV2.TileSnapshotV2 tile : snapshot.tiles()) {
            admitted = Math.addExact(admitted, tile.effectAabb().volumeBlocks());
            if (admitted > maximumBlocks) {
                throw new IOException("snapshot baseline exceeds the configured block admission");
            }
        }
        Map<Coord, String> states = new HashMap<>(Math.toIntExact(Math.min(admitted, 1_000_000L)));
        PlacementSnapshotFileCodecV2 codec = new PlacementSnapshotFileCodecV2();
        Path directory = snapshotsRoot.toAbsolutePath().normalize().resolve(plan.placementId().toString());
        for (PlacementSnapshotPlanV2.TileSnapshotV2 tile : snapshot.tiles()) {
            WorldAabbV2 region = tile.effectAabb();
            var read = codec.readStrict(
                    directory.resolve(tile.snapshotFile()),
                    plan.target().worldId(),
                    tile.tileId(),
                    region,
                    PlacementSnapshotPlanV2.MAXIMUM_PALETTE_ENTRIES_PER_TILE,
                    cancellation,
                    (index, state) -> {
                        Coord coordinate = decode(region, index);
                        String previous = states.putIfAbsent(coordinate, state);
                        if (previous != null && !previous.equals(state)) {
                            throw new IOException("overlapping snapshots disagree in the baseline");
                        }
                    });
            if (!read.artifactChecksum().equals(tile.artifactChecksum())
                    || !read.blockStateStreamChecksum().equals(tile.blockStateStreamChecksum())) {
                throw new IOException("snapshot baseline checksum mismatch");
            }
        }
        return (x, y, z) -> {
            String state = states.get(new Coord(x, y, z));
            if (state == null) throw new IllegalArgumentException("snapshot baseline coverage gap");
            return state;
        };
    }

    private static Coord decode(WorldAabbV2 region, long index) {
        int width = region.maxX() - region.minX() + 1;
        int length = region.maxZ() - region.minZ() + 1;
        long layer = (long) width * length;
        return new Coord(
                region.minX() + (int) (index % width),
                region.minY() + (int) (index / layer),
                region.minZ() + (int) ((index / width) % length));
    }

    private record Coord(int x, int y, int z) { }
}
