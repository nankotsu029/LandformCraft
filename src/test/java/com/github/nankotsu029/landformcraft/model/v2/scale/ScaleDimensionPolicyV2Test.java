package com.github.nankotsu029.landformcraft.model.v2.scale;

import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentValidationInputV2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-8-02: the unified route ceiling is the MEDIUM scale-class contract (1024/1025 boundary). */
class ScaleDimensionPolicyV2Test {
    private static final String CHECKSUM = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void ceilingIsExactlyTheMediumScaleClassContract() {
        assertEquals(1_024, ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING);
        assertEquals(ScaleClassV2.MEDIUM.maximumHorizontalBlocks(),
                ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING);
    }

    @Test
    void predicateAcceptsMediumBoundaryAndRejectsAbove() {
        assertTrue(ScaleDimensionPolicyV2.withinMediumGrid(1_024, 1_024, 1));
        assertTrue(ScaleDimensionPolicyV2.withinMediumGrid(1, 1_024, 1));
        assertFalse(ScaleDimensionPolicyV2.withinMediumGrid(1_025, 1, 1));
        assertFalse(ScaleDimensionPolicyV2.withinMediumGrid(1, 1_025, 1));
        assertFalse(ScaleDimensionPolicyV2.withinMediumGrid(0, 64, 1));
        assertFalse(ScaleDimensionPolicyV2.withinMediumGrid(1, 64, 2));
    }

    @Test
    void environmentValidationInputFollowsTheUnifiedCeiling() {
        assertDoesNotThrow(() ->
                new EnvironmentValidationInputV2(1_024, 1, CHECKSUM, (x, z) -> null));
        assertThrows(IllegalArgumentException.class, () ->
                new EnvironmentValidationInputV2(1_025, 1, CHECKSUM, (x, z) -> null));
    }

    @Test
    void offlineTilePlanAreaCeilingFollowsTheUnifiedCeiling() {
        assertDoesNotThrow(() -> new OfflineTilePlanV2(
                OfflineTilePlanV2.VERSION, "tile-x7-z7", 7, 7, 1_024 - 128, 1_024 - 128,
                128, 128, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new OfflineTilePlanV2(
                OfflineTilePlanV2.VERSION, "tile-x7-z7", 7, 7, 1_025 - 128, 1_024 - 128,
                128, 128, 0, 0));
    }
}
