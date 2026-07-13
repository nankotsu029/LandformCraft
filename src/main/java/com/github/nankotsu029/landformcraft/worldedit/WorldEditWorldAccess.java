package com.github.nankotsu029.landformcraft.worldedit;

import com.github.nankotsu029.landformcraft.model.ManifestTile;
import com.github.nankotsu029.landformcraft.model.PlacementPlan;
import com.github.nankotsu029.landformcraft.model.WorldDescriptor;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletionException;

/** Synchronous WorldEdit boundary. Callers decide whether an operation belongs on Paper or I/O execution. */
public final class WorldEditWorldAccess {
    public WorldDescriptor describeWorld(String worldName) {
        World world = requireWorld(worldName);
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double radius = border.getSize() / 2.0D;
        return new WorldDescriptor(
                world.getUID(), world.getName(), world.getMinHeight(), world.getMaxHeight() - 1,
                ceilingToInt(center.getX() - radius), floorToInt(center.getX() + radius),
                ceilingToInt(center.getZ() - radius), floorToInt(center.getZ() + radius)
        );
    }

    public LoadedSchematic capture(PlacementPlan plan, ManifestTile tile) {
        World world = requireWorld(plan.worldName());
        com.sk89q.worldedit.world.World adapted = BukkitAdapter.adapt(world);
        BlockVector3 sourceMinimum = destination(plan, tile);
        BlockVector3 dimensions = dimensions(tile);
        CuboidRegion source = new CuboidRegion(
                sourceMinimum,
                sourceMinimum.add(dimensions.x() - 1, dimensions.y() - 1, dimensions.z() - 1)
        );
        CuboidRegion local = new CuboidRegion(BlockVector3.ZERO, dimensions.subtract(1, 1, 1));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(local);
        clipboard.setOrigin(BlockVector3.ZERO);
        complete(new ForwardExtentCopy(adapted, source, clipboard, BlockVector3.ZERO));
        return new LoadedSchematic(clipboard);
    }

    public void paste(PlacementPlan plan, ManifestTile tile, LoadedSchematic schematic) {
        World world = requireWorld(plan.worldName());
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            Operation operation = new ClipboardHolder(schematic.clipboard()).createPaste(editSession)
                    .to(destination(plan, tile))
                    .ignoreAirBlocks(false)
                    .build();
            complete(operation);
        }
    }

    public boolean verify(PlacementPlan plan, ManifestTile tile, LoadedSchematic schematic) {
        World world = requireWorld(plan.worldName());
        com.sk89q.worldedit.world.World adapted = BukkitAdapter.adapt(world);
        Clipboard expected = schematic.clipboard();
        BlockVector3 destination = destination(plan, tile);
        BlockVector3 origin = expected.getMinimumPoint();
        BlockVector3 dimensions = expected.getDimensions();
        for (int y = 0; y < dimensions.y(); y++) {
            for (int z = 0; z < dimensions.z(); z++) {
                for (int x = 0; x < dimensions.x(); x++) {
                    var expectedBlock = expected.getBlock(origin.add(x, y, z));
                    var actualBlock = adapted.getBlock(destination.add(x, y, z));
                    if (!expectedBlock.equals(actualBlock)) {
                        Bukkit.getLogger().warning("[LandformCraft] Verification mismatch for " + tile.id()
                                + " at local " + x + "," + y + "," + z
                                + ": expected " + expectedBlock.toImmutableState()
                                + ", actual " + actualBlock.toImmutableState());
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Hashes and parses the same immutable byte array, avoiding a checksum/read path race. */
    public LoadedSchematic readVerified(Path file, String expectedChecksum) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("schematic is missing: " + file);
        }
        byte[] bytes = Files.readAllBytes(file);
        if (!Sha256.bytes(bytes).equals(expectedChecksum)) {
            throw new IOException("schematic checksum mismatch: " + file);
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file.toFile());
        if (format == null) {
            throw new IOException("unsupported schematic format: " + file);
        }
        try (InputStream input = new ByteArrayInputStream(bytes);
             ClipboardReader reader = format.getReader(input)) {
            return new LoadedSchematic(reader.read());
        }
    }

    public void writeAtomically(LoadedSchematic schematic, Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("snapshot requires a parent directory");
        }
        Path temporary = parent.resolve(absolute.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary));
                 ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(output)) {
                writer.write(schematic.clipboard());
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw new UncheckedIOException(exception);
        }
    }

    private static void complete(Operation operation) {
        try {
            Operations.complete(operation);
        } catch (WorldEditException exception) {
            throw new CompletionException(exception);
        }
    }

    private static BlockVector3 destination(PlacementPlan plan, ManifestTile tile) {
        return BlockVector3.at(
                Math.addExact(plan.targetX(), tile.originX()),
                plan.targetY(),
                Math.addExact(plan.targetZ(), tile.originZ())
        );
    }

    private static BlockVector3 dimensions(ManifestTile tile) {
        return BlockVector3.at(tile.width(), tile.maxY() - tile.minY() + 1, tile.length());
    }

    private static World requireWorld(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            throw new IllegalArgumentException("unknown world: " + name);
        }
        return world;
    }

    private static int ceilingToInt(double value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("world border is outside integer coordinates");
        }
        return (int) Math.ceil(value);
    }

    private static int floorToInt(double value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("world border is outside integer coordinates");
        }
        return (int) Math.floor(value);
    }
}
