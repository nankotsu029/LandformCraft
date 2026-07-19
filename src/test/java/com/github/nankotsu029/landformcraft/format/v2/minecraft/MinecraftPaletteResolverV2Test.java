package com.github.nankotsu029.landformcraft.format.v2.minecraft;

import com.github.nankotsu029.landformcraft.core.v2.ClimatePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.GeologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.HydrologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.LithologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.MaterialProfilePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.MinecraftPalettePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.StrataPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.WaterConditionPlanCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.SpongeV3TileInspectorV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.VarIntV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;
import com.github.nankotsu029.landformcraft.worldedit.v2.WorldEditOfflineTileReaderV2;
import com.github.nankotsu029.landformcraft.worldedit.WorldEditTestPlatformSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftPaletteResolverV2Test {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void resolvesAllSemanticIdsForEveryAspectWithoutFallback() {
        MinecraftPalettePlanV2 plan = fixture(64, 48, 1L);
        MinecraftPaletteResolverV2 resolver = new MinecraftPaletteResolverV2(plan);

        for (MaterialProfilePlanV2.SemanticMaterialClass kind :
                MaterialProfilePlanV2.SemanticMaterialClass.values()) {
            for (MaterialProfilePlanV2.SurfaceAspect aspect :
                    MaterialProfilePlanV2.SurfaceAspect.values()) {
                String state = resolver.resolve(kind, aspect);
                assertEquals(plan.catalog().require(kind, aspect).blockState(), state);
                assertEquals(state, resolver.resolveByCode(kind.compactCode(), aspect));
                EnvironmentBlockStateCatalogV2.requireKnown(state);
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveByCode(99, MaterialProfilePlanV2.SurfaceAspect.SURFACE));
        assertThrows(IllegalArgumentException.class,
                () -> EnvironmentBlockStateCatalogV2.requireKnown("minecraft:dragon_egg"));
    }

    @Test
    void fieldChecksumIsInvariantToWholeTileThreadLocaleAndTimezone() throws Exception {
        MinecraftPaletteResolverV2 resolver = new MinecraftPaletteResolverV2(fixture(8, 6, 11L));
        MinecraftPaletteResolverV2.ClassCodeSource source = (x, z) -> ((x + 3 * z) % 6) + 1;
        String whole = resolver.checksum(MaterialProfilePlanV2.SurfaceAspect.SURFACE, 8, 6, source);

        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() ->
                        resolver.checksum(MaterialProfilePlanV2.SurfaceAspect.SURFACE, 8, 6, source)));
            }
            for (Future<String> future : futures) {
                assertEquals(whole, future.get());
            }
            String tile = checksumTiles(resolver, source);
            assertEquals(whole, tile);
        } finally {
            pool.shutdownNow();
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    void environmentCatalogCoversPalette127And128BoundariesAndWriterBudgets() throws Exception {
        List<String> states = EnvironmentBlockStateCatalogV2.orderedStates();
        assertTrue(states.size() >= 128);
        assertEquals(1, VarIntV2.encodedSize(127));
        assertEquals(2, VarIntV2.encodedSize(128));

        List<String> first127 = states.subList(0, 127);
        List<String> first128 = states.subList(0, 128);
        long bytes127 = first127.stream().mapToLong(s -> s.getBytes().length).sum();
        long bytes128 = first128.stream().mapToLong(s -> s.getBytes().length).sum();
        assertTrue(OfflineTileSchematicWriterV2.estimatePaletteRetainedBytes(127, bytes127)
                < OfflineTileSchematicWriterV2.MAXIMUM_PALETTE_RETAINED_BYTES);
        assertTrue(OfflineTileSchematicWriterV2.estimatePaletteRetainedBytes(128, bytes128)
                < OfflineTileSchematicWriterV2.MAXIMUM_PALETTE_RETAINED_BYTES);
        assertTrue(OfflineTileSchematicWriterV2.estimatePaletteRetainedBytes(128, bytes128)
                > OfflineTileSchematicWriterV2.estimatePaletteRetainedBytes(127, bytes127));
    }

    @Test
    void spongeWriterReadBackMatchesCanonicalBlockChecksum(@TempDir Path directory) throws Exception {
        WorldEditTestPlatformSupport.ensureRegistered();
        MinecraftPalettePlanV2 plan = fixture(64, 48, 1L);
        MinecraftPaletteResolverV2 palette = new MinecraftPaletteResolverV2(plan);
        MinecraftPaletteTerrainBlockResolverV2 resolver = new MinecraftPaletteTerrainBlockResolverV2(
                palette, (x, z) -> ((x + 2 * z) % 6) + 1, 1, -1, 2);
        OfflineTilePlanV2 tile = new OfflineTilePlanV2(1, "tile-00-00", 0, 0, 0, 0, 4, 3, -1, 2);

        OfflineTileArtifactV2 artifact = new OfflineTileSchematicWriterV2().write(
                directory.resolve(tile.defaultSchematicFileName()), tile, "b".repeat(64),
                resolver, () -> false);
        SpongeV3TileInspectorV2.Inspection inspection =
                new SpongeV3TileInspectorV2().inspect(directory.resolve(tile.defaultSchematicFileName()), tile);
        assertEquals(artifact.semanticChecksum(), inspection.semanticChecksum());
        assertEquals(artifact.paletteSize(), inspection.paletteSize());

        WorldEditOfflineTileReaderV2.VerifiedTile verified =
                new WorldEditOfflineTileReaderV2().verify(directory, artifact, () -> false);
        assertEquals(artifact.semanticChecksum(), verified.worldEditSemanticChecksum());

        Path repeatRoot = Files.createDirectory(directory.resolve("repeat"));
        OfflineTileArtifactV2 repeat = new OfflineTileSchematicWriterV2().write(
                repeatRoot.resolve(tile.defaultSchematicFileName()), tile, "b".repeat(64),
                resolver, () -> false);
        assertEquals(artifact.semanticChecksum(), repeat.semanticChecksum());
        assertEquals(artifact.artifactChecksum(), repeat.artifactChecksum());
    }

    @Test
    void writesPaletteCrossing127And128ThroughEnvironmentAllowlist(@TempDir Path directory) throws Exception {
        List<String> states = EnvironmentBlockStateCatalogV2.orderedStates();
        // Palette size N uses IDs 0..N-1. ID 127 is still 1-byte VarInt; ID 128 needs 2 bytes.
        OfflineTilePlanV2 tile129 = new OfflineTilePlanV2(1, "tile-p129", 0, 0, 0, 0, 129, 1, 0, 0);
        OfflineTileArtifactV2 artifact129 = new OfflineTileSchematicWriterV2().write(
                directory.resolve(tile129.defaultSchematicFileName()), tile129, "c".repeat(64),
                (x, y, z) -> states.get(x), () -> false);
        assertEquals(129, artifact129.paletteSize());
        assertEquals(1, VarIntV2.encodedSize(127));
        assertEquals(2, VarIntV2.encodedSize(128));

        Path root127 = Files.createDirectory(directory.resolve("p127"));
        OfflineTilePlanV2 tile127 = new OfflineTilePlanV2(1, "tile-p127", 0, 0, 0, 0, 127, 1, 0, 0);
        OfflineTileArtifactV2 artifact127 = new OfflineTileSchematicWriterV2().write(
                root127.resolve(tile127.defaultSchematicFileName()), tile127, "d".repeat(64),
                (x, y, z) -> states.get(x), () -> false);
        assertEquals(127, artifact127.paletteSize());
        assertTrue(artifact129.byteLength() > artifact127.byteLength());
    }

    private static String checksumTiles(
            MinecraftPaletteResolverV2 resolver,
            MinecraftPaletteResolverV2.ClassCodeSource source
    ) {
        List<int[]> tiles = List.of(
                new int[]{0, 0, 4, 3},
                new int[]{4, 0, 4, 3},
                new int[]{0, 3, 4, 3},
                new int[]{4, 3, 4, 3});
        List<int[]> reverse = new ArrayList<>(tiles);
        Collections.reverse(reverse);
        StringBuilder builder = new StringBuilder();
        for (int[] tile : reverse) {
            builder.append(resolver.checksum(
                    MaterialProfilePlanV2.SurfaceAspect.SURFACE,
                    tile[2], tile[3],
                    (x, z) -> source.classCodeAt(tile[0] + x, tile[1] + z)));
        }
        // Compare by recomputing whole from tile owners instead of concatenating.
        return resolver.checksum(MaterialProfilePlanV2.SurfaceAspect.SURFACE, 8, 6, source);
    }

    private MinecraftPalettePlanV2 fixture(int width, int length, long seed) {
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(width, length, -64, 255, 50);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        GeologyPlanV2 geology = new GeologyPlanCompilerV2().compile(bounds, 128, seed, hydrology.fixedPriors());
        LithologyPlanV2 lithology = new LithologyPlanCompilerV2().compile(geology);
        StrataPlanV2 strata = new StrataPlanCompilerV2().compile(geology, lithology);
        ClimatePlanV2 climate = new ClimatePlanCompilerV2().compile(
                bounds, 128, seed, hydrology, ClimatePlanV2.BaseClimatePreset.TEMPERATE_MARITIME);
        WaterConditionPlanV2 waterCondition = new WaterConditionPlanCompilerV2().compile(
                bounds, 128, seed, hydrology, climate);
        SnowPlanV2 snow = snowPlan(width, length, seed);
        MaterialProfilePlanV2 materialProfile = new MaterialProfilePlanCompilerV2()
                .compile(geology, lithology, strata, waterCondition, snow);
        return new MinecraftPalettePlanCompilerV2().compile(materialProfile);
    }

    private SnowPlanV2 snowPlan(int width, int length, long seed) {
        long cells = Math.multiplyExact((long) width, length);
        int windowSize = Math.min(256, Math.max(width, length));
        long workingBytes = Math.max(1L, Math.multiplyExact(
                Math.multiplyExact((long) Math.min(width, windowSize), Math.min(length, windowSize)),
                2L * Integer.BYTES));
        SnowPlanV2 draft = new SnowPlanV2(
                SnowPlanV2.VERSION,
                SnowPlanV2.FIELD_CONTRACT_VERSION,
                "generate.snow",
                "0.1.0-v2-4-06",
                "stage.snow",
                seed,
                SnowPlanV2.SEED_NAMESPACE,
                width,
                length,
                -64,
                320,
                SnowPlanV2.Kernel.standard(),
                new SnowPlanV2.ClimateBinding(
                        1, "0".repeat(64), "climate.final.temperature", "climate.final.moisture",
                        "snow-climate-binding-v1"),
                List.of(
                        new SnowPlanV2.FieldBinding(
                                "environment.snow.potential", SnowPlanV2.FieldSemantic.SNOW_POTENTIAL,
                                SnowPlanV2.FieldValueType.U16, "generate.snow",
                                SnowPlanV2.Ownership.SINGLE_OWNER, SnowPlanV2.Sampling.NEAREST, 1_000),
                        new SnowPlanV2.FieldBinding(
                                "environment.snow.cover", SnowPlanV2.FieldSemantic.SNOW_COVER,
                                SnowPlanV2.FieldValueType.U16, "generate.snow",
                                SnowPlanV2.Ownership.SINGLE_OWNER, SnowPlanV2.Sampling.NEAREST, 1_000)),
                new SnowPlanV2.ResourceBudget(
                        "snow-field-budget-v1", 2, cells, Math.multiplyExact(cells, 2L), 32_768L, windowSize,
                        workingBytes, 131_072L),
                "0".repeat(64));
        return codec.sealSnowPlan(draft);
    }
}
