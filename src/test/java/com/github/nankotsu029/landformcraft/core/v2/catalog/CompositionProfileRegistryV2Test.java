package com.github.nankotsu029.landformcraft.core.v2.catalog;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.composition.CompositionProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.composition.CompositionStageV2;
import com.github.nankotsu029.landformcraft.model.v2.composition.ParentPolicyV2;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-18-09 composition profile registration per ADR 0038 D3/D4 (NORMATIVE 6 / PROVISIONAL 54). */
class CompositionProfileRegistryV2Test {
    private final CompositionProfileRegistryV2 registry = CompositionProfileRegistryV2.builtIn();

    @Test
    void registersEveryFeatureKind() {
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            CompositionProfileRegistryV2.Registration registration = registry.registration(kind);
            assertEquals(kind, registration.kind());
            assertFalse(registration.profile().stages().isEmpty());
        }
    }

    @Test
    void exactlyTheAdrNormativeSixAreConfirmed() {
        Set<TerrainIntentV2.FeatureKind> normative = EnumSet.noneOf(TerrainIntentV2.FeatureKind.class);
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            if (registry.registration(kind).confidence() == CompositionProfileRegistryV2.Confidence.NORMATIVE) {
                normative.add(kind);
            }
        }
        assertEquals(EnumSet.of(
                TerrainIntentV2.FeatureKind.SANDY_BEACH,
                TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                TerrainIntentV2.FeatureKind.PLAIN,
                TerrainIntentV2.FeatureKind.HILL_RANGE), normative);
    }

    @Test
    void coastalFourAreSurfaceModifiersNotFoundationOwners() {
        for (TerrainIntentV2.FeatureKind kind : EnumSet.of(
                TerrainIntentV2.FeatureKind.SANDY_BEACH,
                TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                TerrainIntentV2.FeatureKind.ROCKY_CAPE)) {
            CompositionProfileV2 profile = registry.profile(kind);
            assertFalse(profile.foundationEligible(), kind.name());
            assertEquals(Set.of(CompositionStageV2.SURFACE_MODIFICATION), profile.stages(), kind.name());
            assertEquals(ParentPolicyV2.STANDALONE, profile.parentPolicy(), kind.name());
        }
    }

    @Test
    void seventeenKindsAreFoundationEligiblePerAdr0038D4() {
        Set<TerrainIntentV2.FeatureKind> eligible = registry.foundationEligibleKinds();
        assertEquals(17, eligible.size());
        assertTrue(eligible.containsAll(EnumSet.of(
                TerrainIntentV2.FeatureKind.PLAIN,
                TerrainIntentV2.FeatureKind.HILL_RANGE,
                TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE,
                TerrainIntentV2.FeatureKind.VALLEY,
                TerrainIntentV2.FeatureKind.PLATEAU,
                TerrainIntentV2.FeatureKind.SINGLE_ISLAND,
                TerrainIntentV2.FeatureKind.ARCHIPELAGO,
                TerrainIntentV2.FeatureKind.VOLCANIC_CONE,
                TerrainIntentV2.FeatureKind.OCEAN_BASIN,
                TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF,
                TerrainIntentV2.FeatureKind.CONTINENTAL_SLOPE,
                TerrainIntentV2.FeatureKind.ICE_CAP,
                TerrainIntentV2.FeatureKind.ICE_SHEET,
                TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS,
                TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE,
                TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE,
                TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO)));
        // ADR 0038 D4: ABYSSAL_PLAIN was reclassified to a modifier (HARD WITHIN presupposes a basin).
        assertFalse(eligible.contains(TerrainIntentV2.FeatureKind.ABYSSAL_PLAIN));
    }

    @Test
    void aliasAndSubtypeKindsInheritTheirCanonicalCarrierProfile() {
        assertCarrier(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS, TerrainIntentV2.FeatureKind.PLAIN);
        assertCarrier(TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE, TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE);
        assertCarrier(TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE, TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE);
        assertCarrier(TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO, TerrainIntentV2.FeatureKind.ARCHIPELAGO);
        assertCarrier(TerrainIntentV2.FeatureKind.MEANDERING_RIVER, TerrainIntentV2.FeatureKind.RIVER);
        assertCarrier(TerrainIntentV2.FeatureKind.BEDROCK_RIVER, TerrainIntentV2.FeatureKind.RIVER);
        assertCarrier(TerrainIntentV2.FeatureKind.OXBOW_LAKE, TerrainIntentV2.FeatureKind.LAKE);
        assertCarrier(TerrainIntentV2.FeatureKind.MANGROVE_WETLAND, TerrainIntentV2.FeatureKind.MARSH);
    }

    @Test
    void multiStageKindsCarryTheirFullStageSets() {
        assertEquals(Set.of(CompositionStageV2.SURFACE_MODIFICATION, CompositionStageV2.VOLUME_OPERATION,
                        CompositionStageV2.FLUID_OPERATION),
                registry.profile(TerrainIntentV2.FeatureKind.WATERFALL).stages());
        assertEquals(Set.of(CompositionStageV2.SURFACE_MODIFICATION, CompositionStageV2.VOLUME_OPERATION),
                registry.profile(TerrainIntentV2.FeatureKind.SINKHOLE).stages());
        assertEquals(Set.of(CompositionStageV2.SURFACE_MODIFICATION, CompositionStageV2.VOLUME_OPERATION),
                registry.profile(TerrainIntentV2.FeatureKind.CAVE_ENTRANCE).stages());
        assertEquals(Set.of(CompositionStageV2.VOLUME_OPERATION, CompositionStageV2.FLUID_OPERATION),
                registry.profile(TerrainIntentV2.FeatureKind.UNDERGROUND_RIVER).stages());
        assertEquals(Set.of(CompositionStageV2.SURFACE_MODIFICATION, CompositionStageV2.VOLUME_OPERATION),
                registry.profile(TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD).stages());
    }

    @Test
    void parentPoliciesFollowTheAdrTable() {
        assertEquals(ParentPolicyV2.PARENT_REQUIRED,
                registry.profile(TerrainIntentV2.FeatureKind.LAGOON).parentPolicy());
        assertEquals(ParentPolicyV2.PARENT_REQUIRED,
                registry.profile(TerrainIntentV2.FeatureKind.REEF_PASS).parentPolicy());
        assertEquals(ParentPolicyV2.PARENT_REQUIRED,
                registry.profile(TerrainIntentV2.FeatureKind.GLACIAL_CIRQUE_FIELD).parentPolicy());
        assertEquals(ParentPolicyV2.PARENT_BOUND_OVERLAY,
                registry.profile(TerrainIntentV2.FeatureKind.FLOODED_CAVE).parentPolicy());
    }

    @Test
    void theProfileContractRejectsInconsistentFoundationEligibility() {
        assertThrows(IllegalArgumentException.class, () -> new CompositionProfileV2(
                true, Set.of(CompositionStageV2.SURFACE_MODIFICATION), ParentPolicyV2.STANDALONE));
        assertThrows(IllegalArgumentException.class, () -> new CompositionProfileV2(
                false, Set.of(CompositionStageV2.FOUNDATION), ParentPolicyV2.STANDALONE));
        assertThrows(IllegalArgumentException.class, () -> new CompositionProfileV2(
                false, Set.of(), ParentPolicyV2.STANDALONE));
    }

    private void assertCarrier(TerrainIntentV2.FeatureKind alias, TerrainIntentV2.FeatureKind carrier) {
        CompositionProfileRegistryV2.Registration registration = registry.registration(alias);
        assertTrue(registration.carrierInherited(), alias.name());
        assertEquals(registry.profile(carrier), registration.profile(), alias.name());
        assertFalse(registry.registration(carrier).carrierInherited(), carrier.name());
    }
}
