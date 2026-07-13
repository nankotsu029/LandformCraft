package com.github.nankotsu029.landformcraft.worldedit;

import com.github.nankotsu029.landformcraft.core.BlueprintCompiler;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.generator.TerrainGenerator;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockColumnMaterializerTest {
    @Test
    void materializesConnectedFenceStatesWithoutAThreeDimensionalTerrainArray() throws Exception {
        LandformDataCodec codec = new LandformDataCodec();
        TerrainPlan plan = new TerrainGenerator().generate(new BlueprintCompiler().compile(
                codec.readGenerationRequest(Path.of("examples/phase6-structures/request.yml")),
                codec.readTerrainIntent(Path.of("examples/phase6-structures/terrain-intent.json")), 0), () -> false);
        boolean connected = false;
        for (var tile : plan.tiles()) {
            StructureBlockIndex index = new StructureBlockIndex(plan, tile);
            for (int z = tile.originZ(); z < tile.originZ() + tile.length() && !connected; z++) {
                for (int x = tile.originX(); x < tile.originX() + tile.width() && !connected; x++) {
                    int y = plan.heightMap().get(x, z) + 1;
                    int value = index.paletteIdAt(x, y, z, MinecraftBlockPalette.AIR);
                    connected = value > MinecraftBlockPalette.OAK_FENCE
                            && value <= MinecraftBlockPalette.OAK_FENCE + 15;
                }
            }
        }
        assertTrue(connected, "at least one fence segment must encode a deterministic connection");
    }

    @Test
    void materializesBedrockStoneSubsoilSurfaceWaterAndAir(@TempDir Path directory) throws IOException {
        String requestYaml = Files.readString(Path.of("examples/rocky-coast/request.yml"))
                .replace("width: 500", "width: 64")
                .replace("length: 500", "length: 64");
        Path requestPath = directory.resolve("request.yml");
        Files.writeString(requestPath, requestYaml);
        LandformDataCodec codec = new LandformDataCodec();
        var request = codec.readGenerationRequest(requestPath);
        var intent = codec.readTerrainIntent(Path.of("examples/rocky-coast/terrain-intent.json"));
        TerrainPlan plan = new TerrainGenerator().generate(
                new BlueprintCompiler().compile(request, intent, 0), () -> false
        );
        BlockColumnMaterializer materializer = new BlockColumnMaterializer();
        int minY = plan.blueprint().bounds().minY();
        assertEquals(MinecraftBlockPalette.BEDROCK, materializer.paletteIdAt(plan, 0, minY, 0));

        boolean checkedWater = false;
        boolean checkedLand = false;
        for (int z = 0; z < 64 && (!checkedWater || !checkedLand); z++) {
            for (int x = 0; x < 64 && (!checkedWater || !checkedLand); x++) {
                int surfaceY = plan.heightMap().get(x, z);
                if (!checkedWater && plan.waterDepthMap().get(x, z) > 0) {
                    assertEquals(MinecraftBlockPalette.surface(plan.surfaceMaterials().get(x, z)),
                            materializer.paletteIdAt(plan, x, surfaceY, z));
                    assertEquals(MinecraftBlockPalette.WATER,
                            materializer.paletteIdAt(plan, x, surfaceY + 1, z));
                    assertEquals(MinecraftBlockPalette.AIR,
                            materializer.paletteIdAt(plan, x, plan.blueprint().bounds().waterLevel() + 1, z));
                    checkedWater = true;
                }
                if (!checkedLand && plan.waterDepthMap().get(x, z) == 0 && surfaceY >= minY + 5) {
                    assertEquals(MinecraftBlockPalette.surface(plan.surfaceMaterials().get(x, z)),
                            materializer.paletteIdAt(plan, x, surfaceY, z));
                    assertEquals(MinecraftBlockPalette.subsoil(plan.surfaceMaterials().get(x, z)),
                            materializer.paletteIdAt(plan, x, surfaceY - 1, z));
                    assertEquals(MinecraftBlockPalette.STONE,
                            materializer.paletteIdAt(plan, x, surfaceY - 4, z));
                    assertEquals(MinecraftBlockPalette.AIR,
                            materializer.paletteIdAt(plan, x, surfaceY + 1, z));
                    checkedLand = true;
                }
            }
        }
        assertTrue(checkedWater, "fixture must contain a water column");
        assertTrue(checkedLand, "fixture must contain a land column");
    }
}
