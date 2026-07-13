package com.github.nankotsu029.landformcraft.generator;

import com.github.nankotsu029.landformcraft.core.BlueprintCompiler;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.GenerationOptions;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.IntGrid;
import com.github.nankotsu029.landformcraft.model.OutputOptions;
import com.github.nankotsu029.landformcraft.model.SurfaceMaterial;
import com.github.nankotsu029.landformcraft.model.TerrainFeature;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.TerrainTile;
import com.github.nankotsu029.landformcraft.model.TilePlan;
import com.github.nankotsu029.landformcraft.validation.TerrainPerformanceValidator;
import com.github.nankotsu029.landformcraft.validation.TerrainValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainGeneratorTest {
    private static TerrainIntent intent;

    @BeforeAll
    static void loadIntent() throws IOException {
        intent = new LandformDataCodec().readTerrainIntent(Path.of("examples/rocky-coast/terrain-intent.json"));
    }

    @Test
    void sameBlueprintProducesSameTerrainAndChecksum() {
        GenerationRequest request = request(130, 129, 2);
        var blueprint = new BlueprintCompiler().compile(request, intent, 0);
        TerrainGenerator generator = new TerrainGenerator();

        TerrainPlan first = generator.generate(blueprint, () -> false);
        TerrainPlan second = generator.generate(blueprint, () -> false);

        assertEquals(first.checksum(), second.checksum());
        assertEquals(first.heightMap(), second.heightMap());
        assertEquals(first.waterDepthMap(), second.waterDepthMap());
        assertTrue(new TerrainValidator().validate(first).isValid());
    }

    @Test
    void createsDeterministicLogicalLayoutAtTheBlueprintResolution() {
        var blueprint = new BlueprintCompiler().compile(request(500, 500, 1), intent, 0);
        LogicalLayoutGenerator generator = new LogicalLayoutGenerator();

        LogicalTerrainLayout first = generator.generate(blueprint, () -> false);
        LogicalTerrainLayout second = generator.generate(blueprint, () -> false);

        assertEquals(64, first.resolution());
        assertEquals(first, second);
        assertNotEquals(first.reliefAt(0.82, 0.18), first.reliefAt(0.50, 0.82));
    }

    @Test
    void candidateSeedChangesTerrainAndEdgeTilesAreTrimmed() {
        GenerationRequest request = request(130, 129, 2);
        BlueprintCompiler compiler = new BlueprintCompiler();
        TerrainGenerator generator = new TerrainGenerator();

        TerrainPlan first = generator.generate(compiler.compile(request, intent, 0), () -> false);
        TerrainPlan second = generator.generate(compiler.compile(request, intent, 1), () -> false);
        TilePlan southEast = first.tiles().get(3);

        assertNotEquals(first.checksum(), second.checksum());
        assertEquals(4, first.tiles().size());
        assertEquals(2, southEast.width());
        assertEquals(1, southEast.length());
        assertEquals((long) 130 * 129, first.tiles().stream()
                .mapToLong(tile -> (long) tile.width() * tile.length())
                .sum());
    }

    @Test
    void marginGeneratedTilesExactlyMatchWholeTerrainAtEverySeam() {
        GenerationRequest request = request(260, 259, 1);
        var blueprint = new BlueprintCompiler().compile(request, intent, 0);
        TerrainGenerator generator = new TerrainGenerator();
        TerrainPlan whole = generator.generate(blueprint, () -> false);

        for (int zIndex = 0; zIndex < blueprint.tileCountZ(); zIndex++) {
            for (int xIndex = 0; xIndex < blueprint.tileCountX(); xIndex++) {
                TerrainTile tile = generator.generateTile(blueprint, xIndex, zIndex, () -> false);
                TilePlan expectedPlan = whole.tiles().get(zIndex * blueprint.tileCountX() + xIndex);
                assertEquals(expectedPlan.checksum(), tile.plan().checksum());
                for (int z = 0; z < tile.plan().length(); z++) {
                    for (int x = 0; x < tile.plan().width(); x++) {
                        int globalX = tile.plan().originX() + x;
                        int globalZ = tile.plan().originZ() + z;
                        assertEquals(whole.heightMap().get(globalX, globalZ), tile.heightMap().get(x, z));
                        assertEquals(whole.waterDepthMap().get(globalX, globalZ), tile.waterDepthMap().get(x, z));
                        assertEquals(whole.surfaceMaterials().get(globalX, globalZ), tile.surfaceMaterials().get(x, z));
                        assertEquals(whole.featureMask().get(globalX, globalZ), tile.featureMask().get(x, z));
                    }
                }
            }
        }
    }

    @Test
    void validatorRejectsAnIsolatedWaterCell() {
        TerrainPlan plan = new TerrainGenerator().generate(
                new BlueprintCompiler().compile(request(130, 129, 1), intent, 0),
                () -> false
        );
        int isolatedIndex = findDryInteriorCell(plan);
        int x = isolatedIndex % plan.heightMap().width();
        int z = isolatedIndex / plan.heightMap().width();
        int[] heights = plan.heightMap().toArray();
        int[] depths = plan.waterDepthMap().toArray();
        heights[isolatedIndex] = plan.blueprint().bounds().waterLevel() - 1;
        depths[isolatedIndex] = 1;
        TerrainPlan corrupted = copyWith(plan, heights, depths, plan.featureMask().toArray());

        var result = new TerrainValidator().validate(corrupted);

        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("isolated-water")),
                () -> result.issues().toString());
        assertTrue(x > 0 && z > 0);
    }

    @Test
    void validatorRejectsHeightOutsideBlueprintBounds() {
        TerrainPlan plan = new TerrainGenerator().generate(
                new BlueprintCompiler().compile(request(64, 64, 1), intent, 0),
                () -> false
        );
        int[] heights = plan.heightMap().toArray();
        heights[0] = plan.blueprint().bounds().maxY() + 1;
        TerrainPlan corrupted = copyWith(
                plan,
                heights,
                plan.waterDepthMap().toArray(),
                plan.featureMask().toArray()
        );

        var result = new TerrainValidator().validate(corrupted);

        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("height-out-of-bounds")),
                () -> result.issues().toString());
    }

    @Test
    void validatorRejectsARiverPitThatWouldRequireReverseFlow() {
        TerrainPlan plan = new TerrainGenerator().generate(
                new BlueprintCompiler().compile(request(130, 129, 1), intent, 0),
                () -> false
        );
        int pitIndex = findInteriorRiverCell(plan);
        int[] heights = plan.heightMap().toArray();
        int[] depths = plan.waterDepthMap().toArray();
        heights[pitIndex] = plan.blueprint().bounds().waterLevel() - 12;
        depths[pitIndex] = 12;
        TerrainPlan corrupted = copyWith(plan, heights, depths, plan.featureMask().toArray());

        var result = new TerrainValidator().validate(corrupted);

        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("river-flow-reversal")),
                () -> result.issues().toString());
    }

    @Test
    void generatesFiveHundredSquareWithinThePhaseOneBudget() {
        var blueprint = new BlueprintCompiler().compile(request(500, 500, 1), intent, 0);
        long startedAt = System.nanoTime();
        TerrainPlan plan = new TerrainGenerator().generate(blueprint, () -> false);
        Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
        TerrainPerformanceValidator validator = new TerrainPerformanceValidator();
        EnumSet<SurfaceMaterial> materials = EnumSet.noneOf(SurfaceMaterial.class);
        for (int z = 0; z < plan.surfaceMaterials().length(); z++) {
            for (int x = 0; x < plan.surfaceMaterials().width(); x++) {
                materials.add(plan.surfaceMaterials().get(x, z));
            }
        }

        assertTrue(validator.validate(plan, duration).isValid(),
                () -> "duration=" + duration + ", metrics=" + validator.metrics(plan, duration));
        assertTrue(validator.metrics(plan, duration).estimatedPeakWorkingBytes()
                <= TerrainPerformanceValidator.MAX_ESTIMATED_PEAK_BYTES);
        assertTrue(materials.containsAll(List.of(
                SurfaceMaterial.GRASS,
                SurfaceMaterial.SAND,
                SurfaceMaterial.STONE,
                SurfaceMaterial.GRAVEL
        )), () -> materials.toString());
    }

    private static TerrainPlan copyWith(TerrainPlan plan, int[] heights, int[] depths, int[] features) {
        return new TerrainPlan(
                plan.blueprint(),
                new IntGrid(plan.heightMap().width(), plan.heightMap().length(), heights),
                new IntGrid(plan.waterDepthMap().width(), plan.waterDepthMap().length(), depths),
                plan.surfaceMaterials(),
                new IntGrid(plan.featureMask().width(), plan.featureMask().length(), features),
                plan.tiles(),
                plan.structures(),
                plan.checksum()
        );
    }

    private static int findDryInteriorCell(TerrainPlan plan) {
        int width = plan.heightMap().width();
        int length = plan.heightMap().length();
        for (int z = 2; z < length - 2; z++) {
            for (int x = 2; x < width - 2; x++) {
                if (plan.waterDepthMap().get(x, z) == 0
                        && plan.waterDepthMap().get(x - 1, z) == 0
                        && plan.waterDepthMap().get(x + 1, z) == 0
                        && plan.waterDepthMap().get(x, z - 1) == 0
                        && plan.waterDepthMap().get(x, z + 1) == 0) {
                    return z * width + x;
                }
            }
        }
        throw new AssertionError("no dry interior cell found");
    }

    private static int findInteriorRiverCell(TerrainPlan plan) {
        int width = plan.heightMap().width();
        int length = plan.heightMap().length();
        for (int z = 2; z < length - 2; z++) {
            for (int x = 2; x < width - 2; x++) {
                if (TerrainFeature.RIVER.isPresent(plan.featureMask().get(x, z))
                        && TerrainFeature.RIVER.isPresent(plan.featureMask().get(x - 1, z))
                        && TerrainFeature.RIVER.isPresent(plan.featureMask().get(x + 1, z))) {
                    return z * width + x;
                }
            }
        }
        throw new AssertionError("no interior river cell found");
    }

    private static GenerationRequest request(int width, int length, int candidates) {
        return new GenerationRequest(
                1,
                "generator-test",
                new GenerationBounds(width, length, -32, 160, 62),
                "Generate a deterministic coast.",
                List.of(),
                new GenerationOptions(candidates, 827413L),
                new OutputOptions(128, false, false)
        );
    }
}
