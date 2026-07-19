package com.github.nankotsu029.landformcraft.model.v2.placement;

import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-11-02: normal-operation dimension clamp and the re-measurement-only escape hatch. */
class Release2PlacementDimensionPolicyV2Test {
    private static final String WORLD = "lfc-measurement";

    @Test
    void productionCeilingIsTheCatalogHardLimitAndCannotBeWidenedByConfiguration() {
        Release2MeasuredDimensionGateV2 gate = Release2MeasuredDimensionGateV2.production();
        assertEquals(PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM, gate.maximumWidth());
        assertEquals(PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM, gate.maximumLength());

        // Within the catalog limit a smaller configured ceiling is honoured (tightening is fine).
        assertEquals(32, Release2MeasuredDimensionGateV2.production(32, 32).maximumWidth());

        // The audit finding: configuration used to accept anything up to 10,000.
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
    void measurementAdmissionDoesNotPromoteTheDimensionInTheCatalog() {
        // The escape hatch is an admission decision only: the published catalog limit that drives
        // SUPPORTED promotion stays at the smoke-measured 64x64 (promotion is V2-11-06).
        Release2MeasurementProfileV2 profile =
                Release2MeasurementProfileV2.forIsolatedWorld(WORLD, 1_000, 1_000);
        assertTrue(profile.admits(1_000, 1_000, WORLD, PlacementActorKindV2.CONSOLE));
        PlacementDimensionLimitV2 published = PlacementDimensionLimitV2.smokeMeasured();
        assertEquals(PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM, published.maximumWidth());
        assertFalse(published.admits(1_000, 1_000));
        assertFalse(published.admits(500, 500));
    }
}
