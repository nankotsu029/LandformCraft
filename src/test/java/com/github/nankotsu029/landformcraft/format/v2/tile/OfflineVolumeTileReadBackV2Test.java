package com.github.nankotsu029.landformcraft.format.v2.tile;

import com.github.nankotsu029.landformcraft.format.v2.minecraft.EnvironmentBlockStateCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainQuery;
import com.github.nankotsu029.landformcraft.generator.v2.volume.query.VolumeTerrainQueryV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-5-16 offline read-back of volume-composed tiles (cave, floating solid, fluid, air). */
class OfflineVolumeTileReadBackV2Test {
    private static final String BLUEPRINT = "a".repeat(64);

    @TempDir
    Path directory;

    @Test
    void exportsAirCavityFluidAndFloatingSolidWithStrictReadBack() throws Exception {
        VolumeTerrainQueryV2 scene = VolumeSceneTestSupportV2.scene(0, 0, 16, 16);
        TerrainBlockResolver resolver = new VolumeTileBlockResolverV2(scene);
        OfflineTilePlanV2 plan = VolumeSceneTestSupportV2.plan("tile-00-00", 0, 0, 0, 0, 16, 16);

        // The composed scene must actually contain each volume occupancy the task requires.
        assertTrue(hasAirCavityInSolid(scene, plan), "expected a carved air cavity inside solid rock");
        assertTrue(hasFluidCell(scene, plan), "expected a fluid pool cell");
        assertTrue(hasFloatingSolid(scene, plan), "expected an independent solid above air");

        Path target = directory.resolve(plan.defaultSchematicFileName());
        OfflineTileArtifactV2 artifact = new OfflineTileSchematicWriterV2()
                .write(target, plan, BLUEPRINT, resolver, () -> false);

        assertEquals(plan.blockCount(), artifact.blockCount());
        assertEquals(CanonicalBlockStreamV2.checksum(plan, resolver, () -> false), artifact.semanticChecksum());
        SpongeV3TileInspectorV2.Inspection inspection = new SpongeV3TileInspectorV2().inspect(target, plan);
        assertEquals(artifact.semanticChecksum(), inspection.semanticChecksum());
        assertEquals(artifact.paletteSize(), inspection.paletteSize());
        assertEquals(plan.blockCount(), inspection.blockCount());

        // Air, water, and independent solid states all survive the export palette.
        assertTrue(paletteStates(scene, plan).containsAll(List.of(
                "minecraft:air", "minecraft:water", "minecraft:stone",
                "minecraft:bedrock", "minecraft:grass_block")));

        // Determinism: a second independent export produces byte-identical artifacts.
        Path repeatRoot = Files.createDirectory(directory.resolve("repeat"));
        OfflineTileArtifactV2 repeat = new OfflineTileSchematicWriterV2().write(
                repeatRoot.resolve(plan.defaultSchematicFileName()), plan,
                BLUEPRINT, new VolumeTileBlockResolverV2(VolumeSceneTestSupportV2.scene(0, 0, 16, 16)), () -> false);
        assertEquals(artifact.artifactChecksum(), repeat.artifactChecksum());
        assertEquals(artifact.semanticChecksum(), repeat.semanticChecksum());
        assertEquals(artifact.canonicalChecksum(), repeat.canonicalChecksum());
    }

    @Test
    void wholeAndTileStreamsMatchAcrossBoundariesThreadsLocaleAndTimezone() throws Exception {
        OfflineTilePlanV2 whole = VolumeSceneTestSupportV2.plan("tile-whole", 0, 0, 0, 0, 16, 16);
        List<OfflineTilePlanV2> tiles = List.of(
                VolumeSceneTestSupportV2.plan("tile-00-00", 0, 0, 0, 0, 8, 8),
                VolumeSceneTestSupportV2.plan("tile-01-00", 1, 0, 8, 0, 8, 8),
                VolumeSceneTestSupportV2.plan("tile-00-01", 0, 1, 0, 8, 8, 8),
                VolumeSceneTestSupportV2.plan("tile-01-01", 1, 1, 8, 8, 8, 8));

        // The global CSG plan makes each tile window compose the same stream: whole == tile dispatch.
        TerrainBlockResolver whole0 = new VolumeTileBlockResolverV2(VolumeSceneTestSupportV2.scene(0, 0, 16, 16));
        TerrainBlockResolver dispatch = (x, y, z) -> {
            long owners = tiles.stream().filter(tile -> tile.contains(x, y, z)).count();
            if (owners != 1) throw new IllegalStateException("tile dispatch must have exactly one owner");
            return whole0.blockStateAt(x, y, z);
        };
        assertEquals(CanonicalBlockStreamV2.checksum(whole, whole0, () -> false),
                CanonicalBlockStreamV2.checksum(whole, dispatch, () -> false));

        // A window-restricted volume query yields the identical per-tile stream (true independence).
        OfflineTilePlanV2 corner = tiles.get(3);
        assertEquals(
                CanonicalBlockStreamV2.checksum(corner, whole0, () -> false),
                CanonicalBlockStreamV2.checksum(corner,
                        new VolumeTileBlockResolverV2(VolumeSceneTestSupportV2.scene(8, 8, 8, 8)), () -> false));

        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Map<String, String> sequential = exportTiles(directory.resolve("sequential"), tiles, 1);
            List<OfflineTilePlanV2> reversed = new ArrayList<>(tiles);
            Collections.reverse(reversed);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Map<String, String> parallel = exportTiles(directory.resolve("parallel"), reversed, 4);
            assertEquals(sequential, parallel);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void readsBackLargePaletteAcrossVarIntBoundaryInThreeDimensions() throws Exception {
        List<String> states = EnvironmentBlockStateCatalogV2.orderedStates();
        assertTrue(states.size() >= 129, "palette-boundary read-back needs the 128-ID VarInt boundary");
        OfflineTilePlanV2 plan = new OfflineTilePlanV2(1, "tile-palette", 0, 0, 0, 0, 16, 16, 0, 1);
        // Spread all catalog states across the 3D window so palette IDs cross the 127/128 boundary.
        TerrainBlockResolver resolver = (x, y, z) -> states.get(
                Math.floorMod(x + z * 16 + y * 256, states.size()));

        Path target = directory.resolve(plan.defaultSchematicFileName());
        OfflineTileArtifactV2 artifact = new OfflineTileSchematicWriterV2()
                .write(target, plan, BLUEPRINT, resolver, () -> false);
        assertEquals(states.size(), artifact.paletteSize());
        assertTrue(artifact.paletteSize() > 128);

        SpongeV3TileInspectorV2.Inspection inspection = new SpongeV3TileInspectorV2().inspect(target, plan);
        assertEquals(artifact.semanticChecksum(), inspection.semanticChecksum());
        assertEquals(artifact.paletteSize(), inspection.paletteSize());
        assertEquals(CanonicalBlockStreamV2.checksum(plan, resolver, () -> false), inspection.semanticChecksum());
    }

    @Test
    void rejectsNonVolumeQueryTruncationAndCorruptBytes() throws Exception {
        // The volume adapter only accepts a volume-composed query kernel.
        TerrainQuery base = VolumeSceneTestSupportV2.scene(0, 0, 4, 4).base();
        assertThrows(IllegalArgumentException.class,
                () -> new VolumeTileBlockResolverV2(new VolumeTerrainQueryV2(base)));

        OfflineTilePlanV2 plan = VolumeSceneTestSupportV2.plan("tile-tamper", 0, 0, 0, 0, 16, 16);
        Path target = directory.resolve(plan.defaultSchematicFileName());
        new OfflineTileSchematicWriterV2().write(
                target, plan, BLUEPRINT,
                new VolumeTileBlockResolverV2(VolumeSceneTestSupportV2.scene(0, 0, 16, 16)), () -> false);
        byte[] complete = Files.readAllBytes(target);

        // Truncated GZIP/NBT is rejected before any WorldEdit read.
        byte[] truncated = Arrays.copyOf(complete, Math.max(2, complete.length / 2));
        assertThrows(IOException.class, () -> new SpongeV3TileInspectorV2().inspect(truncated, plan));

        // A flipped byte breaks the GZIP integrity / NBT structure and is rejected.
        byte[] corrupt = complete.clone();
        corrupt[corrupt.length / 2] ^= 0x5a;
        assertThrows(IOException.class, () -> new SpongeV3TileInspectorV2().inspect(corrupt, plan));
    }

    private Map<String, String> exportTiles(Path root, List<OfflineTilePlanV2> tiles, int threads) throws Exception {
        Files.createDirectories(root);
        Map<String, String> result = Collections.synchronizedMap(new LinkedHashMap<>());
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (OfflineTilePlanV2 tile : tiles) {
                futures.add(executor.submit(() -> {
                    try {
                        OfflineTileArtifactV2 artifact = new OfflineTileSchematicWriterV2().write(
                                root.resolve(tile.defaultSchematicFileName()), tile, BLUEPRINT,
                                new VolumeTileBlockResolverV2(VolumeSceneTestSupportV2.scene(
                                        tile.originX(), tile.originZ(), tile.width(), tile.length())),
                                () -> false);
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

    private static List<String> paletteStates(VolumeTerrainQueryV2 scene, OfflineTilePlanV2 plan) {
        TerrainBlockResolver resolver = new VolumeTileBlockResolverV2(scene);
        List<String> present = new ArrayList<>();
        for (int y = plan.minY(); y <= plan.maxY(); y++) {
            for (int z = plan.originZ(); z < plan.originZ() + plan.length(); z++) {
                for (int x = plan.originX(); x < plan.originX() + plan.width(); x++) {
                    String state = resolver.blockStateAt(x, y, z);
                    if (!present.contains(state)) present.add(state);
                }
            }
        }
        return present;
    }

    private static boolean hasAirCavityInSolid(VolumeTerrainQueryV2 scene, OfflineTilePlanV2 plan) {
        for (int y = plan.minY() + 1; y < VolumeSceneTestSupportV2.SURFACE_Y; y++) {
            for (int z = plan.originZ(); z < plan.originZ() + plan.length(); z++) {
                for (int x = plan.originX(); x < plan.originX() + plan.width(); x++) {
                    if (scene.blockClassAt(x, y, z) == TerrainQuery.BlockClass.AIR
                            && scene.blockClassAt(x, y - 1, z) == TerrainQuery.BlockClass.SOLID
                            && scene.blockClassAt(x, y + 1, z) == TerrainQuery.BlockClass.SOLID) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasFluidCell(VolumeTerrainQueryV2 scene, OfflineTilePlanV2 plan) {
        for (int y = plan.minY(); y <= plan.maxY(); y++) {
            for (int z = plan.originZ(); z < plan.originZ() + plan.length(); z++) {
                for (int x = plan.originX(); x < plan.originX() + plan.width(); x++) {
                    if (scene.blockClassAt(x, y, z) == TerrainQuery.BlockClass.FLUID) return true;
                }
            }
        }
        return false;
    }

    private static boolean hasFloatingSolid(VolumeTerrainQueryV2 scene, OfflineTilePlanV2 plan) {
        for (int y = VolumeSceneTestSupportV2.SURFACE_Y + 2; y <= plan.maxY(); y++) {
            for (int z = plan.originZ(); z < plan.originZ() + plan.length(); z++) {
                for (int x = plan.originX(); x < plan.originX() + plan.width(); x++) {
                    if (scene.blockClassAt(x, y, z) == TerrainQuery.BlockClass.SOLID
                            && scene.blockClassAt(x, y - 1, z) == TerrainQuery.BlockClass.AIR) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
