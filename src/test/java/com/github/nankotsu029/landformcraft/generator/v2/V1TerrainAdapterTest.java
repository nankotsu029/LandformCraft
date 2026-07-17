package com.github.nankotsu029.landformcraft.generator.v2;

import com.github.nankotsu029.landformcraft.core.BlueprintCompiler;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.generator.TerrainGenerator;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.GenerationOptions;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.IntGrid;
import com.github.nankotsu029.landformcraft.model.OutputOptions;
import com.github.nankotsu029.landformcraft.model.SurfaceMaterial;
import com.github.nankotsu029.landformcraft.model.SurfaceMaterialGrid;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.TerrainTile;
import com.github.nankotsu029.landformcraft.model.TilePlan;
import com.github.nankotsu029.landformcraft.model.WorldBlueprint;
import com.github.nankotsu029.landformcraft.worldedit.BlockColumnMaterializer;
import com.github.nankotsu029.landformcraft.worldedit.MinecraftBlockPalette;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V1TerrainAdapterTest {
    private static final int WIDTH = 35;
    private static final int LENGTH = 34;
    private static final GenerationBounds DEFAULT_BOUNDS = new GenerationBounds(WIDTH, LENGTH, 0, 31, 15);
    private static final GenerationBounds NEGATIVE_BOUNDS = new GenerationBounds(WIDTH, LENGTH, -32, 31, -4);

    @Test
    void resolverMatchesEveryV1BlockAndExposesTheFrozenColumnSemantics() throws Exception {
        assertEquals(BlueprintCompiler.GENERATOR_VERSION,
                V1TerrainQueryAdapter.SUPPORTED_GENERATOR_VERSION);
        TerrainPlan plan = fixturePlan();
        V1TerrainQueryAdapter query = new V1TerrainQueryAdapter(plan);
        TerrainBlockResolver resolver = new V1TerrainBlockResolver(query);
        BlockColumnMaterializer current = new BlockColumnMaterializer();
        var bounds = plan.blueprint().bounds();

        for (int z = 0; z < bounds.length(); z++) {
            for (int x = 0; x < bounds.width(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    assertEquals(
                            current.paletteIdAt(plan, x, y, z),
                            MinecraftBlockPalette.id(resolver.blockStateAt(x, y, z)),
                            "resolver mismatch at " + x + "," + y + "," + z
                    );
                }
            }
        }

        assertEquals(new TerrainQuery.QueryBounds(0, 0, WIDTH, LENGTH, 0, 31), query.bounds());
        int wetIndex = findColumn(plan, true);
        int wetX = wetIndex % WIDTH;
        int wetZ = wetIndex / WIDTH;
        int wetSurface = plan.heightMap().get(wetX, wetZ);
        assertEquals(
                List.of(new TerrainQuery.VerticalInterval(bounds.minY(), wetSurface)),
                query.solidIntervals(wetX, wetZ)
        );
        assertEquals(
                List.of(new TerrainQuery.VerticalInterval(wetSurface + 1, bounds.waterLevel())),
                query.fluidIntervals(wetX, wetZ)
        );
        assertTrue(query.topWalkableSurface(
                wetX, wetZ, TerrainQuery.WalkableSurfacePolicy.DRY_ONLY).isEmpty());
        assertEquals(wetSurface, query.topWalkableSurface(
                wetX, wetZ, TerrainQuery.WalkableSurfacePolicy.ALLOW_SUBMERGED).orElseThrow());
        assertEquals(plan.featureMask().get(wetX, wetZ),
                query.featureMembershipAt(wetX, wetSurface, wetZ));

        int dryIndex = findColumn(plan, false);
        int dryX = dryIndex % WIDTH;
        int dryZ = dryIndex / WIDTH;
        assertTrue(query.fluidIntervals(dryX, dryZ).isEmpty());
        assertFalse(query.topWalkableSurface(
                dryX, dryZ, TerrainQuery.WalkableSurfacePolicy.DRY_ONLY).isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> resolver.blockStateAt(-1, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> resolver.blockStateAt(0, 32, 0));
    }

    @Test
    void resolverMatchesMaterializerForNegativeYWaterEdgesAndEverySurfaceMaterial() throws Exception {
        TerrainPlan rockyCoast = fixturePlan();
        TerrainPlan mountainStream = fixturePlan(
                Path.of("examples/mountain-stream/terrain-intent.json"),
                NEGATIVE_BOUNDS,
                827_414L
        );
        TerrainPlan controlled = controlledColumnPlan(mountainStream);

        for (TerrainPlan plan : List.of(rockyCoast, mountainStream, controlled)) {
            assertResolverMatchesMaterializer(plan);
        }

        assertTrue(controlled.blueprint().bounds().minY() < 0);
        EnumSet<SurfaceMaterial> materials = EnumSet.noneOf(SurfaceMaterial.class);
        boolean hasDry = false;
        boolean hasWet = false;
        boolean hasShallow = false;
        for (int z = 0; z < controlled.waterDepthMap().length(); z++) {
            for (int x = 0; x < controlled.waterDepthMap().width(); x++) {
                materials.add(controlled.surfaceMaterials().get(x, z));
                int depth = controlled.waterDepthMap().get(x, z);
                hasDry |= depth == 0;
                hasWet |= depth > 0;
                hasShallow |= depth >= 1 && depth <= 3;
            }
        }
        assertEquals(EnumSet.allOf(SurfaceMaterial.class), materials);
        assertTrue(hasDry, "controlled fixture must exercise dry columns");
        assertTrue(hasWet, "controlled fixture must exercise submerged columns");
        assertTrue(hasShallow, "controlled fixture must exercise shallow columns");

        String[] whole = sampleWhole(controlled);
        List<TileCoordinate> natural = tileCoordinates(controlled.blueprint());
        List<TileCoordinate> reverse = new ArrayList<>(natural);
        Collections.reverse(reverse);
        assertArrayEquals(whole, samplePlanTiles(controlled, natural, 1));
        assertArrayEquals(whole, samplePlanTiles(controlled, reverse, 4));
    }

    @Test
    void wholeAndTilesAreInvariantToTileOrderThreadCountLocaleAndTimezone() throws Exception {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            TerrainPlan baselinePlan = fixturePlan();
            String[] whole = sampleWhole(baselinePlan);
            List<TileCoordinate> natural = tileCoordinates(baselinePlan.blueprint());
            List<TileCoordinate> reverse = new ArrayList<>(natural);
            Collections.reverse(reverse);

            assertArrayEquals(whole, sampleTiles(baselinePlan.blueprint(), natural, 1));
            assertArrayEquals(whole, sampleTiles(baselinePlan.blueprint(), reverse, 1));
            assertArrayEquals(whole, sampleTiles(baselinePlan.blueprint(), natural, 4));
            assertArrayEquals(whole, sampleTiles(baselinePlan.blueprint(), reverse, 4));

            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            TerrainPlan changedDefaults = fixturePlan();
            assertEquals(baselinePlan.checksum(), changedDefaults.checksum());
            assertArrayEquals(whole, sampleWhole(changedDefaults));
            assertArrayEquals(whole, sampleTiles(changedDefaults.blueprint(), reverse, 3));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    private static TerrainPlan fixturePlan() throws Exception {
        return fixturePlan(
                Path.of("examples/rocky-coast/terrain-intent.json"),
                DEFAULT_BOUNDS,
                827_413L
        );
    }

    private static TerrainPlan fixturePlan(
            Path intentPath,
            GenerationBounds bounds,
            long seed
    ) throws Exception {
        LandformDataCodec codec = new LandformDataCodec();
        var intent = codec.readTerrainIntent(intentPath);
        GenerationRequest request = new GenerationRequest(
                1,
                "v1-query-compatibility",
                bounds,
                "v1 query compatibility",
                List.of(),
                new GenerationOptions(1, seed),
                new OutputOptions(32, true, false)
        );
        return new TerrainGenerator().generate(
                new BlueprintCompiler().compile(request, intent, 0), () -> false
        );
    }

    private static TerrainPlan controlledColumnPlan(TerrainPlan template) {
        var bounds = template.blueprint().bounds();
        int cellCount = Math.multiplyExact(bounds.width(), bounds.length());
        int[] heights = new int[cellCount];
        int[] waterDepths = new int[cellCount];
        int[] features = new int[cellCount];
        SurfaceMaterial[] materials = new SurfaceMaterial[cellCount];
        SurfaceMaterial[] materialValues = SurfaceMaterial.values();
        for (int index = 0; index < cellCount; index++) {
            materials[index] = materialValues[index % materialValues.length];
            features[index] = index & 0x0f;
            switch (index % 4) {
                case 0 -> {
                    heights[index] = bounds.waterLevel() + 6;
                    waterDepths[index] = 0;
                }
                case 1 -> {
                    heights[index] = bounds.waterLevel() - 1;
                    waterDepths[index] = 1;
                }
                case 2 -> {
                    heights[index] = bounds.waterLevel() - 3;
                    waterDepths[index] = 3;
                }
                default -> {
                    heights[index] = bounds.waterLevel() - 8;
                    waterDepths[index] = 8;
                }
            }
        }
        return new TerrainPlan(
                template.blueprint(),
                new IntGrid(bounds.width(), bounds.length(), heights),
                new IntGrid(bounds.width(), bounds.length(), waterDepths),
                new SurfaceMaterialGrid(bounds.width(), bounds.length(), materials),
                new IntGrid(bounds.width(), bounds.length(), features),
                template.tiles(),
                template.structures(),
                template.checksum()
        );
    }

    private static void assertResolverMatchesMaterializer(TerrainPlan plan) {
        TerrainBlockResolver resolver = new V1TerrainBlockResolver(new V1TerrainQueryAdapter(plan));
        BlockColumnMaterializer materializer = new BlockColumnMaterializer();
        var bounds = plan.blueprint().bounds();
        for (int z = 0; z < bounds.length(); z++) {
            for (int x = 0; x < bounds.width(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    assertEquals(
                            materializer.paletteIdAt(plan, x, y, z),
                            MinecraftBlockPalette.id(resolver.blockStateAt(x, y, z)),
                            "resolver mismatch at " + x + "," + y + "," + z
                                    + " for " + plan.blueprint().intent().theme()
                    );
                }
            }
        }
    }

    private static String[] sampleWhole(TerrainPlan plan) {
        return sample(new V1TerrainBlockResolver(new V1TerrainQueryAdapter(plan)), plan.blueprint());
    }

    private static String[] sample(TerrainBlockResolver resolver, WorldBlueprint blueprint) {
        var bounds = blueprint.bounds();
        String[] result = new String[Math.multiplyExact(
                Math.multiplyExact(bounds.width(), bounds.length()), bounds.verticalSpan())];
        for (int z = 0; z < bounds.length(); z++) {
            for (int x = 0; x < bounds.width(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    result[index(blueprint, x, y, z)] = resolver.blockStateAt(x, y, z);
                }
            }
        }
        return result;
    }

    private static String[] sampleTiles(
            WorldBlueprint blueprint,
            List<TileCoordinate> coordinates,
            int threadCount
    ) throws Exception {
        var bounds = blueprint.bounds();
        String[] result = new String[Math.multiplyExact(
                Math.multiplyExact(bounds.width(), bounds.length()), bounds.verticalSpan())];
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (TileCoordinate coordinate : coordinates) {
                futures.add(executor.submit(() -> {
                    var tile = new TerrainGenerator().generateTile(
                            blueprint, coordinate.xIndex(), coordinate.zIndex(), () -> false
                    );
                    TerrainBlockResolver resolver = new V1TerrainBlockResolver(
                            V1TerrainQueryAdapter.forTile(blueprint, tile)
                    );
                    var tileBounds = tile.plan();
                    for (int z = tileBounds.originZ(); z < tileBounds.originZ() + tileBounds.length(); z++) {
                        for (int x = tileBounds.originX(); x < tileBounds.originX() + tileBounds.width(); x++) {
                            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                                result[index(blueprint, x, y, z)] = resolver.blockStateAt(x, y, z);
                            }
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static String[] samplePlanTiles(
            TerrainPlan plan,
            List<TileCoordinate> coordinates,
            int threadCount
    ) throws Exception {
        WorldBlueprint blueprint = plan.blueprint();
        var bounds = blueprint.bounds();
        String[] result = new String[Math.multiplyExact(
                Math.multiplyExact(bounds.width(), bounds.length()), bounds.verticalSpan())];
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (TileCoordinate coordinate : coordinates) {
                futures.add(executor.submit(() -> {
                    TerrainTile tile = sliceTile(plan, coordinate);
                    TerrainBlockResolver resolver = new V1TerrainBlockResolver(
                            V1TerrainQueryAdapter.forTile(blueprint, tile)
                    );
                    TilePlan tileBounds = tile.plan();
                    for (int z = tileBounds.originZ(); z < tileBounds.originZ() + tileBounds.length(); z++) {
                        for (int x = tileBounds.originX(); x < tileBounds.originX() + tileBounds.width(); x++) {
                            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                                result[index(blueprint, x, y, z)] = resolver.blockStateAt(x, y, z);
                            }
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static TerrainTile sliceTile(TerrainPlan plan, TileCoordinate coordinate) {
        TilePlan tilePlan = plan.tiles().stream()
                .filter(candidate -> candidate.xIndex() == coordinate.xIndex()
                        && candidate.zIndex() == coordinate.zIndex())
                .findFirst()
                .orElseThrow();
        int cellCount = Math.multiplyExact(tilePlan.width(), tilePlan.length());
        int[] heights = new int[cellCount];
        int[] waterDepths = new int[cellCount];
        int[] features = new int[cellCount];
        SurfaceMaterial[] materials = new SurfaceMaterial[cellCount];
        for (int localZ = 0; localZ < tilePlan.length(); localZ++) {
            for (int localX = 0; localX < tilePlan.width(); localX++) {
                int index = localZ * tilePlan.width() + localX;
                int x = tilePlan.originX() + localX;
                int z = tilePlan.originZ() + localZ;
                heights[index] = plan.heightMap().get(x, z);
                waterDepths[index] = plan.waterDepthMap().get(x, z);
                features[index] = plan.featureMask().get(x, z);
                materials[index] = plan.surfaceMaterials().get(x, z);
            }
        }
        return new TerrainTile(
                tilePlan,
                new IntGrid(tilePlan.width(), tilePlan.length(), heights),
                new IntGrid(tilePlan.width(), tilePlan.length(), waterDepths),
                new SurfaceMaterialGrid(tilePlan.width(), tilePlan.length(), materials),
                new IntGrid(tilePlan.width(), tilePlan.length(), features)
        );
    }

    private static List<TileCoordinate> tileCoordinates(WorldBlueprint blueprint) {
        List<TileCoordinate> result = new ArrayList<>();
        for (int zIndex = 0; zIndex < blueprint.tileCountZ(); zIndex++) {
            for (int xIndex = 0; xIndex < blueprint.tileCountX(); xIndex++) {
                result.add(new TileCoordinate(xIndex, zIndex));
            }
        }
        return List.copyOf(result);
    }

    private static int index(WorldBlueprint blueprint, int x, int y, int z) {
        var bounds = blueprint.bounds();
        return ((z * bounds.width() + x) * bounds.verticalSpan()) + y - bounds.minY();
    }

    private static int findColumn(TerrainPlan plan, boolean wet) {
        for (int z = 0; z < plan.waterDepthMap().length(); z++) {
            for (int x = 0; x < plan.waterDepthMap().width(); x++) {
                if ((plan.waterDepthMap().get(x, z) > 0) == wet) {
                    return z * plan.waterDepthMap().width() + x;
                }
            }
        }
        throw new AssertionError("fixture does not contain the requested column type");
    }

    private record TileCoordinate(int xIndex, int zIndex) {
    }
}
