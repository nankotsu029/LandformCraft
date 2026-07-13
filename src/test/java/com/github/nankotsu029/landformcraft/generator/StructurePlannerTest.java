package com.github.nankotsu029.landformcraft.generator;

import com.github.nankotsu029.landformcraft.core.BlueprintCompiler;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructurePlannerTest {
    @Test
    void deterministicallyPlacesRequestedPiersAcrossASafeShoreline() throws Exception {
        var intent = new LandformDataCodec().readTerrainIntent(
                Path.of("examples/rocky-coast/terrain-intent.json"));
        var request = new com.github.nankotsu029.landformcraft.model.GenerationRequest(
                1, "structure-planner-test",
                new com.github.nankotsu029.landformcraft.model.GenerationBounds(64, 64, 0, 95, 62),
                "Create a safe test coast.", java.util.List.of(),
                new com.github.nankotsu029.landformcraft.model.GenerationOptions(1, 827413L),
                new com.github.nankotsu029.landformcraft.model.OutputOptions(32, false, false)
        );
        var blueprint = new BlueprintCompiler().compile(request, intent, 0);
        int[] heights = new int[64 * 64];
        int[] depths = new int[heights.length];
        int[] features = new int[heights.length];
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                int index = z * 64 + x;
                heights[index] = x < 32 ? 62 : 59;
                depths[index] = x < 32 ? 0 : 3;
            }
        }
        var planner = new StructurePlanner();
        var first = planner.plan(blueprint, heights, depths, features, () -> false);
        var second = planner.plan(blueprint, heights, depths, features, () -> false);

        assertEquals(2, first.size());
        assertEquals(first, second);
        assertTrue(first.stream().allMatch(placement -> placement.anchorX() < 32
                && placement.anchorX() + placement.sizeX() > 32));
        var left = first.get(0);
        var right = first.get(1);
        assertTrue(left.anchorX() + left.sizeX() < right.anchorX() - 1
                || right.anchorX() + right.sizeX() < left.anchorX() - 1
                || left.anchorZ() + left.sizeZ() < right.anchorZ() - 1
                || right.anchorZ() + right.sizeZ() < left.anchorZ() - 1);
    }
}
