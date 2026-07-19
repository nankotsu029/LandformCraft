package com.github.nankotsu029.landformcraft.generator.v2.environment.snow;

import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowFieldSamplerV2Test {

    private SnowPlanV2 createPlan() {
        return new SnowPlanV2(
                1,
                "snow-field-contract-v1",
                "generate.snow",
                "1.0.0",
                "stage.snow",
                12345L,
                "terrain.v2.snow",
                1000,
                1000,
                -64,
                320,
                SnowPlanV2.Kernel.standard(),
                new SnowPlanV2.ClimateBinding(
                        1,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        "climate.final.temperature",
                        "climate.final.moisture",
                        "snow-climate-binding-v1"
                ),
                List.of(
                        new SnowPlanV2.FieldBinding(
                                "environment.snow.potential",
                                SnowPlanV2.FieldSemantic.SNOW_POTENTIAL,
                                SnowPlanV2.FieldValueType.U16,
                                "generate.snow",
                                SnowPlanV2.Ownership.SINGLE_OWNER,
                                SnowPlanV2.Sampling.NEAREST,
                                1_000
                        ),
                        new SnowPlanV2.FieldBinding(
                                "environment.snow.cover",
                                SnowPlanV2.FieldSemantic.SNOW_COVER,
                                SnowPlanV2.FieldValueType.U16,
                                "generate.snow",
                                SnowPlanV2.Ownership.SINGLE_OWNER,
                                SnowPlanV2.Sampling.NEAREST,
                                1_000
                        )
                ),
                new SnowPlanV2.ResourceBudget(
                        "snow-field-budget-v1",
                        2,
                        1000000L,
                        2000000L,
                        32768L,
                        256,
                        524288L,
                        131072L
                ),
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );
    }

    @Test
    void testSnowPotentialAndCover() {
        SnowPlanV2 plan = createPlan();
        SnowFieldSamplerV2 sampler = new SnowFieldSamplerV2(plan);

        // Cold temp, flat slope -> high potential, high cover
        SnowFieldSamplerV2.CellInputs coldFlat = new SnowFieldSamplerV2.CellInputs(
                100, -200, 500, 0, 0, 0
        );
        int potentialColdFlat = sampler.rawValueAt(SnowPlanV2.FieldSemantic.SNOW_POTENTIAL, 0, 0, coldFlat);
        int coverColdFlat = sampler.rawValueAt(SnowPlanV2.FieldSemantic.SNOW_COVER, 0, 0, coldFlat);
        assertTrue(potentialColdFlat > 0);
        assertEquals(potentialColdFlat, coverColdFlat); // Flat slope, cover = potential

        // Warm temp -> zero potential, zero cover
        SnowFieldSamplerV2.CellInputs warm = new SnowFieldSamplerV2.CellInputs(
                100, 500, 500, 0, 0, 0
        );
        int potentialWarm = sampler.rawValueAt(SnowPlanV2.FieldSemantic.SNOW_POTENTIAL, 0, 0, warm);
        int coverWarm = sampler.rawValueAt(SnowPlanV2.FieldSemantic.SNOW_COVER, 0, 0, warm);
        assertEquals(0, potentialWarm);
        assertEquals(0, coverWarm);

        // Cold temp, steep slope -> high potential, low/zero cover
        SnowFieldSamplerV2.CellInputs coldSteep = new SnowFieldSamplerV2.CellInputs(
                100, -200, 500, 900, 0, 0
        );
        int potentialColdSteep = sampler.rawValueAt(SnowPlanV2.FieldSemantic.SNOW_POTENTIAL, 0, 0, coldSteep);
        int coverColdSteep = sampler.rawValueAt(SnowPlanV2.FieldSemantic.SNOW_COVER, 0, 0, coldSteep);
        assertTrue(potentialColdSteep > 0);
        assertTrue(coverColdSteep < potentialColdSteep); // Steep slope penalizes cover
    }

    @Test
    void testChecksumIsDeterministic() {
        SnowPlanV2 plan = createPlan();
        SnowFieldSamplerV2 sampler = new SnowFieldSamplerV2(plan);
        
        SnowFieldSamplerV2.CellInputSource source = (x, z) -> new SnowFieldSamplerV2.CellInputs(
                100, -200, 500, 0, 0, 0
        );
        
        String hash1 = sampler.checksum(SnowPlanV2.FieldSemantic.SNOW_POTENTIAL, source);
        String hash2 = sampler.checksum(SnowPlanV2.FieldSemantic.SNOW_POTENTIAL, source);
        assertEquals(hash1, hash2);
    }
}
