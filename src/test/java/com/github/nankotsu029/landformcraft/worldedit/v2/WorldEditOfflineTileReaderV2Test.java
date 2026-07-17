package com.github.nankotsu029.landformcraft.worldedit.v2;

import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.worldedit.WorldEditTestPlatformSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldEditOfflineTileReaderV2Test {
    @TempDir
    Path directory;

    @Test
    void worldEdit7319ReadsBackTheCanonicalBlockStream() throws Exception {
        WorldEditTestPlatformSupport.ensureRegistered();
        OfflineTilePlanV2 plan = plan();
        OfflineTileArtifactV2 artifact = write(plan);

        WorldEditOfflineTileReaderV2.VerifiedTile verified = new WorldEditOfflineTileReaderV2().verify(
                directory, artifact, () -> false);
        assertEquals(artifact.semanticChecksum(), verified.worldEditSemanticChecksum());
        assertEquals(artifact.paletteSize(), verified.inspection().paletteSize());
        assertEquals(artifact.blockCount(), verified.inspection().blockCount());
    }

    @Test
    void rejectsChecksumMutationTruncationAndSymlinkBeforeWorldEditRead() throws Exception {
        WorldEditTestPlatformSupport.ensureRegistered();
        OfflineTilePlanV2 plan = plan();
        OfflineTileArtifactV2 artifact = write(plan);
        Path schematic = directory.resolve(artifact.schematicPath());
        byte[] complete = Files.readAllBytes(schematic);

        Files.write(schematic, new byte[]{0}, java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> new WorldEditOfflineTileReaderV2().verify(
                directory, artifact, () -> false));

        byte[] truncated = Arrays.copyOf(complete, Math.max(2, complete.length / 2));
        Files.write(schematic, truncated);
        OfflineTileArtifactV2 rewritten = payloadMetadata(artifact, truncated);
        assertThrows(IOException.class, () -> new WorldEditOfflineTileReaderV2().verify(
                directory, rewritten, () -> false));

        Files.delete(schematic);
        Path outside = directory.resolve("outside.schem");
        Files.write(outside, complete);
        Files.createSymbolicLink(schematic, Path.of("outside.schem"));
        assertThrows(IOException.class, () -> new WorldEditOfflineTileReaderV2().verify(
                directory, artifact, () -> false));
    }

    private OfflineTileArtifactV2 write(OfflineTilePlanV2 plan) throws IOException {
        return new OfflineTileSchematicWriterV2().write(
                directory.resolve(plan.defaultSchematicFileName()), plan, "a".repeat(64),
                (x, y, z) -> {
                    if (y == -1) return "minecraft:bedrock";
                    if (y == 0) return x == 1 && z == 1 ? "minecraft:oak_planks" : "minecraft:sand";
                    if (y == 1) return x < 2 ? "minecraft:water" : "minecraft:air";
                    return "minecraft:air";
                }, () -> false);
    }

    private static OfflineTilePlanV2 plan() {
        return new OfflineTilePlanV2(1, "tile-00-00", 0, 0, 0, 0, 4, 3, -1, 2);
    }

    private static OfflineTileArtifactV2 payloadMetadata(OfflineTileArtifactV2 original, byte[] bytes) {
        return new OfflineTileArtifactCodecV2().seal(new OfflineTileArtifactV2(
                original.tileArtifactVersion(), original.tileId(), original.sourceBlueprintChecksum(),
                original.xIndex(), original.zIndex(), original.originX(), original.originZ(),
                original.width(), original.length(), original.minY(), original.maxY(),
                original.coordinateSpace(), original.blockOrder(), original.minecraftVersion(),
                original.dataVersion(), original.schematicVersion(), original.schematicPath(),
                original.blockCount(), original.paletteSize(), bytes.length, Sha256.bytes(bytes),
                original.semanticChecksum(), OfflineTileArtifactV2.PENDING_CANONICAL_CHECKSUM));
    }
}
