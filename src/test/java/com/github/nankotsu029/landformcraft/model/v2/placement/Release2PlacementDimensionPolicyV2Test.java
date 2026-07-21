package com.github.nankotsu029.landformcraft.model.v2.placement;

import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-11-02: normal-operation dimension clamp and the re-measurement-only escape hatch.
 * V2-11-06: the clamp follows the dimension measured on the detected runtime.
 */
class Release2PlacementDimensionPolicyV2Test {
    private static final String WORLD = "lfc-measurement";

    @Test
    void productionCeilingIsTheCatalogHardLimitAndCannotBeWidenedByConfiguration() {
        Release2MeasuredDimensionGateV2 gate = Release2MeasuredDimensionGateV2.production();
        assertEquals(PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM, gate.maximumWidth());
        assertEquals(PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM, gate.maximumLength());

        // Within the catalog limit a smaller configured ceiling is honoured (tightening is fine).
        assertEquals(32, Release2MeasuredDimensionGateV2.production(32, 32).maximumWidth());

        // The audit finding: configuration used to accept anything up to 10,000. Without the
        // FAWE measurement evidence the ceiling is still the 64x64 smoke size.
        for (int over : new int[] {65, 500, 1_000, 10_000}) {
            assertThrows(IllegalArgumentException.class,
                    () -> Release2MeasuredDimensionGateV2.production(over, 64),
                    "width " + over + " must be rejected at startup");
            assertThrows(IllegalArgumentException.class,
                    () -> Release2MeasuredDimensionGateV2.production(64, over),
                    "length " + over + " must be rejected at startup");
        }
        assertThrows(IllegalArgumentException.class,
                () -> Release2MeasuredDimensionGateV2.production(0, 64));
    }

    @Test
    void faweRuntimeCeilingIsTheMeasuredMaximumAndNothingAboveIt() {
        // V2-11-06: FAWE 2.15.2 carries the V2-11-04 (500x500) and V2-11-05 (1000x1000)
        // measurements, so its production ceiling is 1000 — and stops there.
        assertEquals(PlacementDimensionLimitV2.MEASURED_MAXIMUM,
                Release2MeasuredDimensionGateV2.measuredCeilingFor(true));
        assertEquals(PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM,
                Release2MeasuredDimensionGateV2.measuredCeilingFor(false));

        Release2MeasuredDimensionGateV2 fawe = Release2MeasuredDimensionGateV2.production(true);
        assertEquals(PlacementDimensionLimitV2.MEASURED_MAXIMUM, fawe.maximumWidth());
        assertDoesNotThrow(() -> fawe.requireAdmitted(500, 500));
        assertDoesNotThrow(() -> fawe.requireAdmitted(1_000, 1_000));
        assertThrows(IllegalArgumentException.class, () -> fawe.requireAdmitted(1_001, 1_000));

        assertEquals(500, Release2MeasuredDimensionGateV2.production(true, 500, 500).maximumWidth());
        for (int over : new int[] {1_001, 3_072, 10_000}) {
            assertThrows(IllegalArgumentException.class,
                    () -> Release2MeasuredDimensionGateV2.production(true, over, 1_000),
                    "width " + over + " must be rejected at startup even on FAWE");
        }
        // A WorldEdit-only runtime never inherits the FAWE-only evidence.
        assertThrows(IllegalArgumentException.class,
                () -> Release2MeasuredDimensionGateV2.production(false, 1_000, 1_000));
    }

    @Test
    void faweProfileAdmitsMeasuredDimensionsForEveryActorAndRejectsAbove() {
        Release2PlacementDimensionPolicyV2 policy = Release2PlacementDimensionPolicyV2.of(
                Release2MeasuredDimensionGateV2.production(true));
        assertDoesNotThrow(() -> policy.requireAdmitted(
                500, 500, "world", PlacementActorKindV2.PLAYER));
        assertDoesNotThrow(() -> policy.requireAdmitted(
                1_000, 1_000, "world", PlacementActorKindV2.CONSOLE));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAdmitted(
                1_001, 1_000, "world", PlacementActorKindV2.CONSOLE));
    }

    @Test
    void normalProfileAdmitsTheSmokeBoundaryAndRejectsEverythingAbove() {
        Release2PlacementDimensionPolicyV2 policy = Release2PlacementDimensionPolicyV2.production();
        assertDoesNotThrow(() -> policy.requireAdmitted(
                64, 64, "world", PlacementActorKindV2.CONSOLE));
        assertDoesNotThrow(() -> policy.requireAdmitted(
                1, 1, "world", PlacementActorKindV2.PLAYER));

        assertThrows(IllegalArgumentException.class, () -> policy.requireAdmitted(
                65, 64, "world", PlacementActorKindV2.CONSOLE));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAdmitted(
                64, 65, "world", PlacementActorKindV2.CONSOLE));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAdmitted(
                500, 500, "world", PlacementActorKindV2.CONSOLE));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAdmitted(
                1_000, 1_000, "world", PlacementActorKindV2.CONSOLE));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAdmitted(
                0, 64, "world", PlacementActorKindV2.CONSOLE));
    }

    @Test
    void measurementProfileIsDisabledByDefaultAndAdmitsNothingAboveTheCatalogLimit() {
        Release2MeasurementProfileV2 disabled = Release2MeasurementProfileV2.disabled();
        assertFalse(disabled.enabled());
        assertEquals("measurement-profile=disabled", disabled.describe());
        assertFalse(disabled.admits(500, 500, WORLD, PlacementActorKindV2.CONSOLE));

        // A disabled profile must not carry a world or ceiling that could be silently honoured.
        assertThrows(IllegalArgumentException.class,
                () -> new Release2MeasurementProfileV2(false, WORLD, 500, 500));
    }

    @Test
    void measurementProfileRequiresFlagIsolatedWorldAndConsoleOperatorTogether() {
        Release2PlacementDimensionPolicyV2 policy = new Release2PlacementDimensionPolicyV2(
                Release2MeasuredDimensionGateV2.production(),
                Release2MeasurementProfileV2.forIsolatedWorld(WORLD, 500, 500));

        // All three conditions satisfied.
        assertDoesNotThrow(() -> policy.requireAdmitted(
                500, 500, WORLD, PlacementActorKindV2.CONSOLE));

        // No flag: the same layout on the same world and actor is rejected.
        assertThrows(IllegalArgumentException.class,
                () -> Release2PlacementDimensionPolicyV2.production().requireAdmitted(
                        500, 500, WORLD, PlacementActorKindV2.CONSOLE));

        // Not the isolated world (any other world, including a denied/normal one).
        assertThrows(IllegalArgumentException.class, () -> policy.requireAdmitted(
                500, 500, "world", PlacementActorKindV2.CONSOLE));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAdmitted(
                500, 500, null, PlacementActorKindV2.CONSOLE));

        // Not a CONSOLE/RCON operator: an in-game Player can never reach an unmeasured size.
        assertThrows(IllegalArgumentException.class, () -> policy.requireAdmitted(
                500, 500, WORLD, PlacementActorKindV2.PLAYER));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAdmitted(
                500, 500, WORLD, PlacementActorKindV2.SYSTEM));

        // Above the declared measurement ceiling.
        assertThrows(IllegalArgumentException.class, () -> policy.requireAdmitted(
                1_000, 1_000, WORLD, PlacementActorKindV2.CONSOLE));

        // The normal ceiling still applies unchanged on every other world.
        assertDoesNotThrow(() -> policy.requireAdmitted(
                64, 64, "world", PlacementActorKindV2.PLAYER));
        assertTrue(policy.measurementProfile().describe().contains(WORLD));
    }

    @Test
    void measurementProfileConfigurationIsValidatedAndBoundedByTheCatalogBudget() {
        assertThrows(IllegalArgumentException.class,
                () -> Release2MeasurementProfileV2.forIsolatedWorld("", 500, 500));
        assertThrows(IllegalArgumentException.class,
                () -> Release2MeasurementProfileV2.forIsolatedWorld("   ", 500, 500));
        assertThrows(IllegalArgumentException.class,
                () -> Release2MeasurementProfileV2.forIsolatedWorld(WORLD, 0, 500));
        assertThrows(IllegalArgumentException.class,
                () -> Release2MeasurementProfileV2.forIsolatedWorld(
                        WORLD, PlacementDimensionLimitV2.CATALOG_BUDGET_MAXIMUM + 1, 500));
        assertDoesNotThrow(() -> Release2MeasurementProfileV2.forIsolatedWorld(
                WORLD, PlacementDimensionLimitV2.CATALOG_BUDGET_MAXIMUM,
                PlacementDimensionLimitV2.CATALOG_BUDGET_MAXIMUM));
    }

    @Test
    void measurementAdmissionStillDoesNotPromoteUnmeasuredDimensions() {
        // The escape hatch remains an admission decision only. V2-11-06 published the measured
        // 1000x1000; the profile may still admit larger sizes for a future measurement Task, and
        // that admission must never widen the published catalog limit.
        Release2MeasurementProfileV2 profile =
                Release2MeasurementProfileV2.forIsolatedWorld(WORLD, 3_000, 3_000);
        assertTrue(profile.admits(3_000, 3_000, WORLD, PlacementActorKindV2.CONSOLE));
        PlacementDimensionLimitV2 published = PlacementDimensionLimitV2.measured();
        assertEquals(PlacementDimensionLimitV2.MEASURED_MAXIMUM, published.maximumWidth());
        assertTrue(published.admits(500, 500));
        assertTrue(published.admits(1_000, 1_000));
        assertFalse(published.admits(3_000, 3_000));
    }
}
