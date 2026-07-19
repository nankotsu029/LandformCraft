package com.github.nankotsu029.landformcraft.core.v2.placement;

import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleasePlacementInputContractV2Test {
    private static final String CHECKSUM = "a".repeat(64);

    @Test
    void mapsAllValidPrefixesOntoSharedOverlayOrdinalStream() {
        assertEquals(List.of(0, 1, 2), ReleasePlacementInputContractV2.overlayOrdinalsFor(
                ReleaseCapabilityDependencyMatrixV2.CORE_ONLY));
        assertEquals(List.of(0, 1, 2), ReleasePlacementInputContractV2.overlayOrdinalsFor(
                ReleaseCapabilityDependencyMatrixV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE));
        assertEquals(List.of(0, 1, 2, 3, 4, 5), ReleasePlacementInputContractV2.overlayOrdinalsFor(
                ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME_WITH_ENVIRONMENT));
    }

    @Test
    void foundationAndBathymetryHostsBindOnlyThroughSharedCanonicalStream() {
        for (String kind : List.of(
                "PLAIN", "OCEAN_BASIN", "CONTINENTAL_SHELF", "ABYSSAL_PLAIN", "SEAMOUNT", "SUBMARINE_CANYON")) {
            assertTrue(ReleasePlacementInputContractV2.admitsFoundationHostKind(kind));
            PlacementCanonicalBlockSourceV2.SourceBindingV2 binding =
                    ReleasePlacementInputContractV2.foundationBathymetryBinding(
                            CHECKSUM,
                            ReleaseCapabilityDependencyMatrixV2.SURFACE_ONLY,
                            kind,
                            CHECKSUM);
            assertEquals(ReleasePlacementInputContractV2.CONTRACT_VERSION,
                    ReleasePlacementInputContractV2.CONTRACT_VERSION);
            assertEquals(PlacementCanonicalBlockSourceV2.SOURCE_CONTRACT_VERSION, binding.sourceContractVersion());
            assertEquals(ReleaseCapabilityDependencyMatrixV2.SURFACE_ONLY, binding.requiredCapabilities());
            assertEquals(List.of(0, 1, 2), binding.overlayOrdinals());
        }
        assertFalse(ReleasePlacementInputContractV2.admitsFoundationHostKind("FEATURE_SPECIFIC_PLACEMENT"));
        assertThrows(IllegalArgumentException.class, () ->
                ReleasePlacementInputContractV2.foundationBathymetryBinding(
                        CHECKSUM,
                        ReleaseCapabilityDependencyMatrixV2.SURFACE_ONLY,
                        "FEATURE_SPECIFIC_PLACEMENT",
                        CHECKSUM));
        assertThrows(IllegalArgumentException.class, () ->
                ReleasePlacementInputContractV2.foundationBathymetryBinding(
                        CHECKSUM,
                        List.of(ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME),
                        "PLAIN",
                        CHECKSUM));
    }
}
