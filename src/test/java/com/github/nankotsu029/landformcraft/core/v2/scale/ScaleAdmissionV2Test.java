package com.github.nankotsu029.landformcraft.core.v2.scale;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaleAdmissionV2Test {
    @Test
    void admitsMediumThousandSquareWithinDefaultBudgets() {
        ScaleProfileV2 profile = ScaleProfileV2.defaults(ScaleClassV2.MEDIUM);
        ScaleAdmissionDecisionV2 decision = ScaleAdmissionV2.admit(1_000, 1_000, profile);
        assertEquals(ScaleAdmissionV2.ADMISSION_VERSION, decision.admissionVersion());
        assertEquals(ScaleClassV2.MEDIUM, decision.scaleClass());
        assertEquals(64, decision.tilePlan().tileCount());
        assertTrue(!decision.streamingExecutionRequired());
        assertTrue(decision.estimatedWorkingBytesPerTile() > 0
                && decision.estimatedWorkingBytesPerTile() <= profile.maximumWorkingBytes());
        assertTrue(decision.estimatedRetainedBytes() > 0
                && decision.estimatedRetainedBytes() <= profile.maximumRetainedBytes());
        assertTrue(decision.estimatedArtifactBytes() <= profile.maximumArtifactBytes());
    }

    @Test
    void admitsLargeThreeThousandSquareAsStreamingExecution() {
        ScaleProfileV2 profile = ScaleProfileV2.defaults(ScaleClassV2.LARGE);
        ScaleAdmissionDecisionV2 decision = ScaleAdmissionV2.admit(3_000, 3_000, profile);
        assertEquals(ScaleClassV2.LARGE, decision.scaleClass());
        assertEquals(576, decision.tilePlan().tileCount());
        assertTrue(decision.streamingExecutionRequired());
        assertTrue(decision.estimatedRetainedBytes() <= profile.maximumRetainedBytes());
    }

    @Test
    void rejectsAreasBeyondTheProfileScaleClass() {
        ScaleProfileV2 medium = ScaleProfileV2.defaults(ScaleClassV2.MEDIUM);
        ScaleAdmissionExceptionV2 classExceeded = assertThrows(ScaleAdmissionExceptionV2.class,
                () -> ScaleAdmissionV2.admit(1_500, 100, medium));
        assertEquals(ScaleAdmissionFailureCodeV2.SCALE_CLASS_EXCEEDED, classExceeded.failureCode());

        ScaleProfileV2 large = ScaleProfileV2.defaults(ScaleClassV2.LARGE);
        ScaleAdmissionExceptionV2 outOfRange = assertThrows(ScaleAdmissionExceptionV2.class,
                () -> ScaleAdmissionV2.admit(3_100, 3_100, large));
        assertEquals(ScaleAdmissionFailureCodeV2.DIMENSIONS_OUT_OF_RANGE, outOfRange.failureCode());
        ScaleAdmissionExceptionV2 zero = assertThrows(ScaleAdmissionExceptionV2.class,
                () -> ScaleAdmissionV2.admit(0, 100, large));
        assertEquals(ScaleAdmissionFailureCodeV2.DIMENSIONS_OUT_OF_RANGE, zero.failureCode());
    }

    @Test
    void rejectsTileWorkingAndRetainedBudgetViolations() {
        ScaleProfileV2 fourTileBudget = new ScaleProfileV2(ScaleClassV2.SMALL, 128, 16, 4, 4,
                ScaleProfileV2.TRUSTED_MAXIMUM_RETAINED_BYTES,
                ScaleProfileV2.TRUSTED_MAXIMUM_WORKING_BYTES,
                ScaleProfileV2.TRUSTED_MAXIMUM_ARTIFACT_BYTES);
        // 256x256 at tile 128 needs exactly 4 tiles: admitted.
        assertEquals(4, ScaleAdmissionV2.admit(256, 256, fourTileBudget).tilePlan().tileCount());
        // 512x512 needs 16 tiles: rejected by the profile tile budget.
        ScaleAdmissionExceptionV2 tiles = assertThrows(ScaleAdmissionExceptionV2.class,
                () -> ScaleAdmissionV2.admit(512, 512, fourTileBudget));
        assertEquals(ScaleAdmissionFailureCodeV2.TILE_BUDGET_EXCEEDED, tiles.failureCode());

        ScaleAdmissionExceptionV2 working = assertThrows(ScaleAdmissionExceptionV2.class,
                () -> ScaleAdmissionV2.admit(100, 100,
                        new ScaleProfileV2(ScaleClassV2.SMALL, 128, 16, 4, 16,
                                ScaleProfileV2.TRUSTED_MAXIMUM_RETAINED_BYTES, 1_024L,
                                ScaleProfileV2.TRUSTED_MAXIMUM_ARTIFACT_BYTES)));
        assertEquals(ScaleAdmissionFailureCodeV2.WORKING_BUDGET_EXCEEDED, working.failureCode());

        ScaleAdmissionExceptionV2 retained = assertThrows(ScaleAdmissionExceptionV2.class,
                () -> ScaleAdmissionV2.admit(512, 512,
                        new ScaleProfileV2(ScaleClassV2.SMALL, 128, 16, 4, 16,
                                4_096L,
                                ScaleProfileV2.TRUSTED_MAXIMUM_WORKING_BYTES,
                                ScaleProfileV2.TRUSTED_MAXIMUM_ARTIFACT_BYTES)));
        assertEquals(ScaleAdmissionFailureCodeV2.RETAINED_BUDGET_EXCEEDED, retained.failureCode());

        ScaleAdmissionExceptionV2 artifact = assertThrows(ScaleAdmissionExceptionV2.class,
                () -> ScaleAdmissionV2.admit(512, 512,
                        new ScaleProfileV2(ScaleClassV2.SMALL, 128, 16, 4, 16,
                                ScaleProfileV2.TRUSTED_MAXIMUM_RETAINED_BYTES,
                                ScaleProfileV2.TRUSTED_MAXIMUM_WORKING_BYTES,
                                1_024L)));
        assertEquals(ScaleAdmissionFailureCodeV2.ARTIFACT_BUDGET_EXCEEDED, artifact.failureCode());
    }

    @Test
    void decisionIsDeterministicAcrossRepeatsAndThreads() throws Exception {
        ScaleProfileV2 profile = ScaleProfileV2.defaults(ScaleClassV2.LARGE);
        ScaleAdmissionDecisionV2 expected = ScaleAdmissionV2.admit(3_000, 2_500, profile);
        assertEquals(expected, ScaleAdmissionV2.admit(3_000, 2_500, profile));
        try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
            assertEquals(expected, one.submit(() -> ScaleAdmissionV2.admit(3_000, 2_500, profile)).get());
            assertEquals(expected, four.submit(() -> ScaleAdmissionV2.admit(3_000, 2_500, profile)).get());
        }
    }
}
