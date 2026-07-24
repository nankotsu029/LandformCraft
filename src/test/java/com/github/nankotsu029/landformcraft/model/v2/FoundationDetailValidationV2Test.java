package com.github.nankotsu029.landformcraft.model.v2;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V2-19-12 (ADR 0041 D5) declaration-time fail-closed for {@link GenerationRequestV2.FoundationDetail}.
 * The record's own range checks and the whole-request datum checks are both exercised, because a
 * detail that would sink a land surface below the water level or leave the vertical extent must be
 * rejected — never clamped — at the single request-construction gate.
 */
class FoundationDetailValidationV2Test {
    private static final GenerationRequestV2.Bounds BOUNDS =
            new GenerationRequestV2.Bounds(64, 64, 32, 72, 50);
    private static final GenerationRequestV2.FoundationBaseLevels BASE =
            new GenerationRequestV2.FoundationBaseLevels(54, 46);

    @Test
    void aValidDetailIsAccepted() {
        assertDoesNotThrow(() -> requestWith(BASE,
                new GenerationRequestV2.FoundationDetail(3, 2, 16, 3)));
    }

    @Test
    void zeroAmplitudeOnBothMediumsIsRejected() {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> new GenerationRequestV2.FoundationDetail(0, 0, 16, 3));
        assertEquals(true, rejected.getMessage().contains("zero amplitude"));
    }

    @Test
    void oneMediumMayCarryZeroAmplitude() {
        assertDoesNotThrow(() -> new GenerationRequestV2.FoundationDetail(3, 0, 16, 3));
        assertDoesNotThrow(() -> new GenerationRequestV2.FoundationDetail(0, 2, 16, 3));
    }

    @Test
    void aNonPowerOfTwoWavelengthIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationRequestV2.FoundationDetail(3, 2, 24, 3));
    }

    @Test
    void aWavelengthOutsideRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationRequestV2.FoundationDetail(3, 2, 4, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationRequestV2.FoundationDetail(3, 2, 2048, 3));
    }

    @Test
    void octavesOutsideRangeAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationRequestV2.FoundationDetail(3, 2, 16, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationRequestV2.FoundationDetail(3, 2, 16, 7));
    }

    @Test
    void tooManyOctavesForTheWavelengthAreRejected() {
        // 16 >> 3 = 2 < the minimum grid spacing of 4.
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationRequestV2.FoundationDetail(3, 2, 16, 4));
    }

    @Test
    void anAmplitudeAboveTheMaximumIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationRequestV2.FoundationDetail(33, 2, 16, 3));
    }

    @Test
    void detailWithoutFoundationBaseLevelsIsRejected() {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> requestWith(null, new GenerationRequestV2.FoundationDetail(3, 2, 16, 3)));
        assertEquals(true, rejected.getMessage().contains("foundation base levels"));
    }

    @Test
    void landDetailThatSinksBelowTheWaterLevelIsRejected() {
        // landSurfaceY 54, water level 50: an amplitude of 5 reaches 49 < 50 (a dry pit).
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> requestWith(BASE, new GenerationRequestV2.FoundationDetail(5, 2, 16, 3)));
        assertEquals(true, rejected.getMessage().contains("below the water level"));
    }

    @Test
    void waterDetailThatRisesIntoTheWaterSurfaceIsRejected() {
        // waterBedY 46, water level 50: an amplitude of 4 reaches 50 > 49 (a vanished water column).
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> requestWith(BASE, new GenerationRequestV2.FoundationDetail(3, 4, 16, 3)));
        assertEquals(true, rejected.getMessage().contains("water surface"));
    }

    @Test
    void detailThatLeavesTheVerticalExtentIsRejected() {
        // A shallow band close to the ceiling: base 71 + amplitude 2 = 73 > maxY 72.
        GenerationRequestV2.FoundationBaseLevels high =
                new GenerationRequestV2.FoundationBaseLevels(71, 46);
        assertThrows(IllegalArgumentException.class,
                () -> requestWith(high, new GenerationRequestV2.FoundationDetail(2, 2, 16, 3)));
    }

    private static GenerationRequestV2 requestWith(
            GenerationRequestV2.FoundationBaseLevels baseLevels,
            GenerationRequestV2.FoundationDetail detail
    ) {
        return new GenerationRequestV2(
                GenerationRequestV2.VERSION,
                "foundation-detail-validation",
                BOUNDS,
                "foundation detail validation fixture",
                List.of(),
                List.of(),
                new GenerationRequestV2.GenerationSettings(827_413L, 64),
                GenerationRequestV2.ConstraintMapBudget.defaults(),
                Optional.ofNullable(baseLevels),
                Optional.of(detail),
                java.util.Optional.empty());
    }
}
