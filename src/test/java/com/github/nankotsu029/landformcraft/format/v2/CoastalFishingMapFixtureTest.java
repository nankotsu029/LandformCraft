package com.github.nankotsu029.landformcraft.format.v2;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CoastalFishingMapFixtureTest {
    private static final Path REQUEST =
            Path.of("examples/v2/diagnostic/coastal-fishing-map.request-v2.json");
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/coastal-fishing-map.terrain-intent-v2.json");

    @Test
    void loadsAndCompilesCoastalFishingMapFixture() throws Exception {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        assertEquals("coastal-fishing-map", codec.readGenerationRequest(REQUEST).requestId());
        TerrainIntentV2 intent = codec.readTerrainIntent(INTENT);
        assertEquals("coastal-fishing-map", intent.intentId());
        assertEquals(5, intent.features().size());
        assertEquals(1, intent.structures().size());
        assertEquals(TerrainIntentV2.StructureKind.PATH, intent.structures().getFirst().kind());

        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                new DiagnosticCompileRequestV2(
                        intent.intentId(),
                        new GenerationBounds(400, 400, 32, 72, 50),
                        128,
                        20260722L,
                        "c".repeat(64),
                        DiagnosticCompileRequestV2.defaultBudget()),
                intent);
        assertEquals(1, blueprint.sandyBeachPlans().size());
        assertEquals(1, blueprint.breakwaterHarborPlans().size());
        assertEquals(1, blueprint.harborBasinPlans().size());
        assertEquals(1, blueprint.rockyCapePlans().size());
        assertFalse(blueprint.featurePlans().isEmpty());
    }
}
