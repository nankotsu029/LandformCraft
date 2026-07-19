package com.github.nankotsu029.landformcraft.model.v2.environment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnowPlanV2Test {

    private SnowPlanV2.Kernel standardKernel() {
        return SnowPlanV2.Kernel.standard();
    }

    private SnowPlanV2.ClimateBinding standardClimateBinding() {
        return new SnowPlanV2.ClimateBinding(
                1,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "climate.final.temperature",
                "climate.final.moisture",
                "snow-climate-binding-v1"
        );
    }

    private List<SnowPlanV2.FieldBinding> standardFields() {
        return List.of(
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
        );
    }

    private SnowPlanV2.ResourceBudget standardBudget() {
        return new SnowPlanV2.ResourceBudget(
                "snow-field-budget-v1",
                2,
                1000000L,
                2000000L,
                32768L,
                256,
                524288L,
                131072L
        );
    }

    @Test
    void testValidPlan() {
        assertDoesNotThrow(() -> new SnowPlanV2(
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
                standardKernel(),
                standardClimateBinding(),
                standardFields(),
                standardBudget(),
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        ));
    }
}
