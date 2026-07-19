package com.github.nankotsu029.landformcraft.worldedit.v2;

import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementDesiredBlockV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceV2;
import com.github.nankotsu029.landformcraft.format.v2.minecraft.EnvironmentBlockStateCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.CanonicalBlockStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Synchronous public-WorldEdit-API boundary for Release 2. Every method asserts the Paper primary
 * thread; {@code PaperPlacementWorldGatewayV2} owns scheduler dispatch and bounded slicing.
 */
public final class WorldEditBlockMutationAccessV2 implements PlacementWorldMutationAccessV2 {
    @Override
    public List<ReadBlockV2> readCanonicalSlice(
            UUID worldId,
            WorldAabbV2 region,
            long startIndex,
            int blockCount
    ) {
        requireMainThread();
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(region, "region");
        if (startIndex < 0 || blockCount < 1 || blockCount > 4_096
                || startIndex > region.volumeBlocks() - blockCount) {
            throw new IllegalArgumentException("world read slice is outside its bounded region");
        }
        World world = requireWorld(worldId);
        List<ReadBlockV2> result = new ArrayList<>(blockCount);
        for (int offset = 0; offset < blockCount; offset++) {
            Coordinates coordinates = coordinates(region, startIndex + offset);
            String state = toReleaseCanonical(
                    world.getBlockAt(coordinates.x(), coordinates.y(), coordinates.z()).getBlockData());
            result.add(new ReadBlockV2(
                    coordinates.x(), coordinates.y(), coordinates.z(), state));
        }
        return List.copyOf(result);
    }

    /**
     * Map live Bukkit block data onto the Release tile allowlist form. Bukkit／WorldEdit often emit
     * default properties ({@code minecraft:water[level=0]}); Release tiles store the identifier-only
     * catalog form ({@code minecraft:water}). Non-default property sets are preserved so flowing
     * fluids still fail exact verify.
     */
    static String toReleaseCanonical(BlockData observed) {
        Objects.requireNonNull(observed, "observed");
        String fullySpecified = CanonicalBlockStateV2.requireCanonical(observed.getAsString(false));
        if (EnvironmentBlockStateCatalogV2.contains(fullySpecified)) {
            return fullySpecified;
        }
        String identifier = fullySpecified.indexOf('[') < 0
                ? fullySpecified
                : fullySpecified.substring(0, fullySpecified.indexOf('['));
        if (!EnvironmentBlockStateCatalogV2.contains(identifier)) {
            return CanonicalBlockStateV2.requireCanonical(observed.getAsString(true));
        }
        String catalogDefault = CanonicalBlockStateV2.requireCanonical(
                Bukkit.createBlockData(identifier).getAsString(false));
        if (catalogDefault.equals(fullySpecified)) {
            return identifier;
        }
        return CanonicalBlockStateV2.requireCanonical(observed.getAsString(true));
    }

    @Override
    public AppliedSliceV2 apply(PlacementApplySliceV2 slice) {
        requireMainThread();
        Objects.requireNonNull(slice, "slice");
        World world = requireWorld(slice.worldId());
        boolean closed = false;
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            for (PlacementDesiredBlockV2 mutation : slice.mutations()) {
                editSession.setBlock(
                        BlockVector3.at(mutation.x(), mutation.y(), mutation.z()),
                        BukkitAdapter.adapt(Bukkit.createBlockData(mutation.blockState())));
            }
        } catch (WorldEditException exception) {
            throw new CompletionException("WorldEdit Release 2 mutation failed", exception);
        }
        closed = true;
        return new AppliedSliceV2(slice.mutations().size(), closed);
    }

    @Override
    public AppliedSliceV2 restore(PlacementRestoreSliceV2 slice) {
        requireMainThread();
        Objects.requireNonNull(slice, "slice");
        World world = requireWorld(slice.worldId());
        boolean closed = false;
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            for (PlacementRestoreSliceV2.RestoreBlockV2 block : slice.blocks()) {
                editSession.setBlock(
                        BlockVector3.at(block.x(), block.y(), block.z()),
                        BukkitAdapter.adapt(Bukkit.createBlockData(
                                CanonicalBlockStateV2.requireCanonical(block.blockState()))));
            }
        } catch (WorldEditException exception) {
            throw new CompletionException("WorldEdit Release 2 restore failed", exception);
        }
        closed = true;
        return new AppliedSliceV2(slice.blocks().size(), closed);
    }

    private static Coordinates coordinates(WorldAabbV2 region, long index) {
        long width = (long) region.maxX() - region.minX() + 1L;
        long length = (long) region.maxZ() - region.minZ() + 1L;
        long layer = Math.multiplyExact(width, length);
        long yOffset = index / layer;
        long remainder = index % layer;
        long zOffset = remainder / width;
        long xOffset = remainder % width;
        return new Coordinates(
                Math.toIntExact(region.minX() + xOffset),
                Math.toIntExact(region.minY() + yOffset),
                Math.toIntExact(region.minZ() + zOffset));
    }

    private static World requireWorld(UUID worldId) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            throw new IllegalArgumentException("unknown world: " + worldId);
        }
        return world;
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("WorldEdit Release 2 mutations require the Paper primary thread");
        }
    }

    private record Coordinates(int x, int y, int z) {
    }
}
