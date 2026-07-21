package com.github.nankotsu029.landformcraft.preview.v2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewDimensionBudgetV2Test {
    @Test
    void admitsUpToMediumCeilingSquareAndRejectsBeyondIt() {
        assertTrue(PreviewDimensionBudgetV2.admits(1_000, 1_000));
        assertTrue(PreviewDimensionBudgetV2.admits(1_024, 976));
        assertTrue(PreviewDimensionBudgetV2.admits(1_024, 977));
        assertTrue(PreviewDimensionBudgetV2.admits(1_024, 1_024));
        assertFalse(PreviewDimensionBudgetV2.admits(0, 1));
        assertFalse(PreviewDimensionBudgetV2.admits(1_025, 1));
        assertFalse(PreviewDimensionBudgetV2.admits(1_025, 1_024));
    }
}
