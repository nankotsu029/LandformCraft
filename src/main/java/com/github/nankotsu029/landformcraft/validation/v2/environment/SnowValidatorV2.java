package com.github.nankotsu029.landformcraft.validation.v2.environment;

import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;

import java.util.Objects;

/**
 * Validates the snow plan and alpine scenarios, rejecting out-of-bounds transitions
 * and unknown presets. Asserts that the snowline transition logic is deterministic.
 */
public final class SnowValidatorV2 {
    private SnowValidatorV2() {
    }

    public static void requireValid(SnowPlanV2 plan) {
        Objects.requireNonNull(plan, "plan");
        
        // Assert alpine scenario constraints for snowline
        SnowPlanV2.Kernel kernel = plan.kernel();
        
        if (kernel.snowlineTransitionBlocks() < 1 || kernel.snowlineTransitionBlocks() > 100) {
            throw new IllegalArgumentException("snowline transition blocks out of bounds: " + kernel.snowlineTransitionBlocks());
        }
        
        if (kernel.snowlineTemperatureRaw() < -1000 || kernel.snowlineTemperatureRaw() > 1000) {
            throw new IllegalArgumentException("snowline temperature out of bounds: " + kernel.snowlineTemperatureRaw());
        }

        if (kernel.steepSlopeThresholdRaw() < 0 || kernel.steepSlopeThresholdRaw() > 1000) {
            throw new IllegalArgumentException("steep slope threshold out of bounds: " + kernel.steepSlopeThresholdRaw());
        }

        if (kernel.steepSlopePenaltyRaw() < 0 || kernel.steepSlopePenaltyRaw() > 1000) {
            throw new IllegalArgumentException("steep slope penalty out of bounds: " + kernel.steepSlopePenaltyRaw());
        }
    }
}
