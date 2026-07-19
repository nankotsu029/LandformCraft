package com.github.nankotsu029.landformcraft.worldedit.v2;

import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.VolumeSceneTestSupportV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.VolumeTileBlockResolverV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.worldedit.WorldEditTestPlatformSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-5-16 WorldEdit 7.3.19 offline read-back of a 3D volume tile (cave, floating solid, fluid, air).
 * The emitted Sponge v3 uses only general-specification features (Version 3, DataVersion 4671,
 * Offset [0,0,0], general VarInt palette, no proprietary tags), which is what makes the same file
 * FAWE-readable offline; a running-server FAWE smoke stays a separate V2-6 environment task.
 */
class WorldEditOfflineVolumeTileReaderV2Test {
    @TempDir
    Path directory;

    @Test
    void worldEdit7319ReadsBackVolumeTileAtEveryXyz() throws Exception {
        WorldEditTestPlatformSupport.ensureRegistered();
        OfflineTilePlanV2 plan = plan();
        OfflineTileArtifactV2 artifact = write(plan);

        WorldEditOfflineTileReaderV2.VerifiedTile verified =
                new WorldEditOfflineTileReaderV2().verify(directory, artifact, () -> false);
        // WorldEdit reconstructs the full Y span, so an exact match proves every 3D XYZ read back.
        assertEquals(artifact.semanticChecksum(), verified.worldEditSemanticChecksum());
        assertEquals(artifact.paletteSize(), verified.inspection().paletteSize());
        assertEquals(plan.blockCount(), verified.inspection().blockCount());
        assertEquals(plan.height(), plan.maxY() - plan.minY() + 1);
        assertTrue(verified.inspection().paletteSize() >= 5);
    }

    @Test
    void rejectsChecksumMutationAndTruncationBeforeWorldEditRead() throws Exception {
        WorldEditTestPlatformSupport.ensureRegistered();
        OfflineTilePlanV2 plan = plan();
        OfflineTileArtifactV2 artifact = write(plan);
        Path schematic = directory.resolve(artifact.schematicPath());
        byte[] complete = Files.readAllBytes(schematic);

        Files.write(schematic, new byte[]{0}, StandardOpenOption.APPEND);
        assertThrows(IOException.class,
                () -> new WorldEditOfflineTileReaderV2().verify(directory, artifact, () -> false));

        byte[] truncated = Arrays.copyOf(complete, Math.max(2, complete.length / 2));
        Files.write(schematic, truncated);
        OfflineTileArtifactV2 rewritten = payloadMetadata(artifact, truncated);
        assertThrows(IOException.class,
                () -> new WorldEditOfflineTileReaderV2().verify(directory, rewritten, () -> false));
    }

    private OfflineTileArtifactV2 write(OfflineTilePlanV2 plan) throws IOException {
        return new OfflineTileSchematicWriterV2().write(
                directory.resolve(plan.defaultSchematicFileName()), plan, "a".repeat(64),
                new VolumeTileBlockResolverV2(VolumeSceneTestSupportV2.scene(0, 0, 16, 16)), () -> false);
    }

    private static OfflineTilePlanV2 plan() {
        return VolumeSceneTestSupportV2.plan("tile-00-00", 0, 0, 0, 0, 16, 16);
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
