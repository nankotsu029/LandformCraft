package com.github.nankotsu029.landformcraft.format.v2.tile;

import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineTileSchematicWriterV2Test {
    private static final String BLUEPRINT_CHECKSUM = "a".repeat(64);

    @TempDir
    Path directory;

    @Test
    void writesGeneralVarIntsAcrossRequiredPaletteBoundaries() throws Exception {
        assertVarInt(127, 0x7f);
        assertVarInt(128, 0x80, 0x01);
        assertVarInt(16_383, 0xff, 0x7f);
        assertEquals(1, VarIntV2.encodedSize(127));
        assertEquals(2, VarIntV2.encodedSize(128));
        assertEquals(2, VarIntV2.encodedSize(16_383));
        assertThrows(IllegalArgumentException.class, () -> VarIntV2.encodedSize(16_384));
    }

    @Test
    void streamsAirWaterAndStructureStatesAndStrictlyReadsBackMetadata() throws Exception {
        OfflineTilePlanV2 plan = plan("tile-00-00", 0, 0, 4, 3);
        Path target = directory.resolve(plan.defaultSchematicFileName());
        OfflineTileArtifactV2 artifact = new OfflineTileSchematicWriterV2().write(
                target, plan, BLUEPRINT_CHECKSUM, resolver(), () -> false);

        assertEquals(plan.blockCount(), artifact.blockCount());
        assertTrue(artifact.paletteSize() >= 5);
        assertEquals(CanonicalBlockStreamV2.checksum(plan, resolver(), () -> false), artifact.semanticChecksum());
        SpongeV3TileInspectorV2.Inspection inspection = new SpongeV3TileInspectorV2().inspect(target, plan);
        assertEquals(artifact.semanticChecksum(), inspection.semanticChecksum());
        Path metadata = directory.resolve("tile-00-00.json");
        OfflineTileArtifactCodecV2 codec = new OfflineTileArtifactCodecV2();
        codec.write(metadata, artifact);
        assertEquals(artifact, codec.read(metadata));

        Path repeatRoot = Files.createDirectory(directory.resolve("repeat"));
        OfflineTileArtifactV2 repeat = new OfflineTileSchematicWriterV2().write(
                repeatRoot.resolve(plan.defaultSchematicFileName()), plan,
                BLUEPRINT_CHECKSUM, resolver(), () -> false);
        assertEquals(artifact.artifactChecksum(), repeat.artifactChecksum());
        assertEquals(artifact.semanticChecksum(), repeat.semanticChecksum());
        assertEquals(artifact.canonicalChecksum(), repeat.canonicalChecksum());
    }

    @Test
    void wholeAndTileDispatchAreInvariantToOrderThreadsLocaleAndTimezone() throws Exception {
        OfflineTilePlanV2 whole = plan("tile-whole", 0, 0, 8, 6);
        List<OfflineTilePlanV2> tiles = List.of(
                plan("tile-00-00", 0, 0, 4, 3),
                plan("tile-01-00", 4, 0, 4, 3),
                plan("tile-00-01", 0, 3, 4, 3),
                plan("tile-01-01", 4, 3, 4, 3));
        TerrainBlockResolver source = resolver();
        TerrainBlockResolver tileDispatch = (x, y, z) -> {
            long matches = tiles.stream().filter(tile -> tile.contains(x, y, z)).count();
            if (matches != 1) throw new IllegalStateException("tile dispatch must have one owner");
            return source.blockStateAt(x, y, z);
        };
        assertEquals(CanonicalBlockStreamV2.checksum(whole, source, () -> false),
                CanonicalBlockStreamV2.checksum(whole, tileDispatch, () -> false));

        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            Map<String, String> sequential = exportTiles(directory.resolve("sequential"), tiles, 1);
            List<OfflineTilePlanV2> reverse = new ArrayList<>(tiles);
            Collections.reverse(reverse);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Map<String, String> parallel = exportTiles(directory.resolve("parallel"), reverse, 4);
            assertEquals(sequential, parallel);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    void rejectsUnknownOrChangingResolverAndCleansCancelledStaging() throws Exception {
        OfflineTilePlanV2 unknownPlan = plan("tile-unknown", 0, 0, 2, 2);
        Path unknown = directory.resolve(unknownPlan.defaultSchematicFileName());
        assertThrows(IllegalArgumentException.class, () -> new OfflineTileSchematicWriterV2().write(
                unknown, unknownPlan, BLUEPRINT_CHECKSUM,
                (x, y, z) -> "minecraft:not_a_real_block", () -> false));
        assertFalse(Files.exists(unknown));

        OfflineTilePlanV2 changingPlan = plan("tile-changing", 0, 0, 2, 2);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IOException.class, () -> new OfflineTileSchematicWriterV2().write(
                directory.resolve(changingPlan.defaultSchematicFileName()), changingPlan, BLUEPRINT_CHECKSUM,
                (x, y, z) -> calls.incrementAndGet() <= changingPlan.blockCount()
                        ? "minecraft:stone" : "minecraft:sand", () -> false));
        assertFalse(Files.exists(directory.resolve(changingPlan.defaultSchematicFileName())));

        OfflineTilePlanV2 cancelPlan = plan("tile-cancelled", 0, 0, 64, 64);
        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> new OfflineTileSchematicWriterV2().write(
                directory.resolve(cancelPlan.defaultSchematicFileName()), cancelPlan, BLUEPRINT_CHECKSUM,
                resolver(), () -> checks.incrementAndGet() > 3));
        assertFalse(Files.exists(directory.resolve(cancelPlan.defaultSchematicFileName())));
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".offline-tile-")));
        }
    }

    @Test
    void enforcesPathArtifactAndPaletteMemoryBudgets() {
        OfflineTilePlanV2 plan = plan("tile-budget", 0, 0, 2, 2);
        assertThrows(IOException.class, () -> new OfflineTileSchematicWriterV2().write(
                directory.resolve("wrong-name.schem"), plan, BLUEPRINT_CHECKSUM, resolver(), () -> false));
        assertThrows(IllegalArgumentException.class, () -> new OfflineTileArtifactV2(
                plan, BLUEPRINT_CHECKSUM, "../tile-budget.schem", 1, 1,
                "b".repeat(64), "c".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new OfflineTileArtifactV2(
                plan, BLUEPRINT_CHECKSUM, "tile-budget.schem", 1,
                OfflineTileArtifactV2.MAXIMUM_ARTIFACT_BYTES + 1,
                "b".repeat(64), "c".repeat(64)));
        assertTrue(OfflineTileSchematicWriterV2.estimatePaletteRetainedBytes(
                16_384, 16_384L * 512L) <= OfflineTileSchematicWriterV2.MAXIMUM_PALETTE_RETAINED_BYTES);
    }

    private Map<String, String> exportTiles(Path root, List<OfflineTilePlanV2> tiles, int threads) throws Exception {
        Files.createDirectories(root);
        Map<String, String> result = java.util.Collections.synchronizedMap(new LinkedHashMap<>());
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (OfflineTilePlanV2 tile : tiles) {
                futures.add(executor.submit(() -> {
                    try {
                        OfflineTileArtifactV2 artifact = new OfflineTileSchematicWriterV2().write(
                                root.resolve(tile.defaultSchematicFileName()), tile,
                                BLUEPRINT_CHECKSUM, resolver(), () -> false);
                        result.put(tile.tileId(), artifact.semanticChecksum() + ':' + artifact.artifactChecksum());
                    } catch (IOException exception) {
                        throw new java.io.UncheckedIOException(exception);
                    }
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return Map.copyOf(result);
    }

    private static OfflineTilePlanV2 plan(String id, int originX, int originZ, int width, int length) {
        return new OfflineTilePlanV2(1, id, originX / 4, originZ / 3,
                originX, originZ, width, length, -1, 2);
    }

    private static TerrainBlockResolver resolver() {
        return (x, y, z) -> {
            if (y == -1) return "minecraft:bedrock";
            if (y == 0) return ((x + z) & 1) == 0 ? "minecraft:sand" : "minecraft:stone";
            if (y == 1) return x % 4 == 0 ? "minecraft:oak_planks" : "minecraft:water";
            return "minecraft:air";
        };
    }

    private static void assertVarInt(int value, int... expected) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        VarIntV2.write(output, value);
        byte[] bytes = new byte[expected.length];
        for (int index = 0; index < expected.length; index++) bytes[index] = (byte) expected[index];
        assertArrayEquals(bytes, output.toByteArray());
    }
}
