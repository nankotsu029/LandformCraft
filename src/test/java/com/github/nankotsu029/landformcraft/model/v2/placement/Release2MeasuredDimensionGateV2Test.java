package com.github.nankotsu029.landformcraft.model.v2.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Release2MeasuredDimensionGateV2Test {
    @Test
    void admitsAtOrBelowMeasuredCandidateCeiling() {
        Release2MeasuredDimensionGateV2 gate = new Release2MeasuredDimensionGateV2(64, 64);
        assertDoesNotThrow(() -> gate.requireAdmitted(64, 64));
        assertDoesNotThrow(() -> gate.requireAdmitted(4, 4));
    }

    @Test
    void rejectsAboveMeasuredCandidateCeiling() {
        Release2MeasuredDimensionGateV2 gate = new Release2MeasuredDimensionGateV2(64, 64);
        assertThrows(IllegalArgumentException.class, () -> gate.requireAdmitted(65, 64));
        assertThrows(IllegalArgumentException.class, () -> gate.requireAdmitted(64, 500));
    }
}
