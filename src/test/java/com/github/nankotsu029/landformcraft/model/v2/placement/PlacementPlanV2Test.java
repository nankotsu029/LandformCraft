package com.github.nankotsu029.landformcraft.model.v2.placement;

import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlacementPlanV2Test {
    private static final String CHECKSUM =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void rejectsUnknownCapabilityCombinationAndAnchorMismatch() {
        assertThrows(IllegalArgumentException.class, () -> plan(
                List.of(PlacementPlanV2.CAPABILITY_SPARSE_VOLUME),
                target(0, 64, 0, 0, 64, 0, 63, 80, 63),
                tiles()));
        assertThrows(IllegalArgumentException.class, () -> plan(
                List.of(PlacementPlanV2.CAPABILITY_SURFACE_TWO_POINT_FIVE_D),
                target(1, 64, 0, 0, 64, 0, 63, 80, 63),
                tiles()));
        assertThrows(IllegalArgumentException.class, () -> new PlacementPlanV2.TileOrderV2(
                PlacementPlanV2.TileOrderV2.CONTRACT_VERSION,
                List.of(new PlacementPlanV2.TileRefV2("tile-x0-z0", 1, 0, 0, 64, 64))));
    }

    @Test
    void sortsCapabilitiesCanonicallyAndMirrorsReleaseCatalogSets() {
        PlacementPlanV2 plan = plan(
                List.of(
                        PlacementPlanV2.CAPABILITY_SURFACE_TWO_POINT_FIVE_D,
                        PlacementPlanV2.CAPABILITY_HYDROLOGY_PLAN),
                target(0, 64, 0, 0, 64, 0, 63, 80, 63),
                tiles());
        assertEquals(PlacementPlanV2.CAPABILITIES_HYDROLOGY_WITH_SURFACE, plan.requiredCapabilities());
        assertEquals(ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE, plan.requiredCapabilities());
        assertEquals(ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT,
                PlacementPlanV2.CAPABILITIES_SPARSE_VOLUME_WITH_ENVIRONMENT);
    }

    private static PlacementPlanV2 plan(
            List<String> capabilities,
            PlacementPlanV2.PlacementTargetV2 target,
            List<PlacementPlanV2.TileRefV2> tiles
    ) {
        return new PlacementPlanV2(
                PlacementPlanV2.VERSION,
                PlacementPlanV2.PLACEMENT_CONTRACT_VERSION,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "demo",
                PlacementPlanV2.PlacementActorV2.console(),
                target,
                new PlacementPlanV2.ReleaseBindingV2(
                        1, 2, "releases/demo", CHECKSUM,
                        PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION),
                capabilities,
                new PlacementPlanV2.TileOrderV2(PlacementPlanV2.TileOrderV2.CONTRACT_VERSION, tiles),
                PlacementPlanV2.EnvelopeReferencesV2.unbound(),
                PlacementPlanV2.ReservationConfirmationBindingV2.unbound(
                        PlacementPlanV2.PlacementActorV2.console()),
                new PlacementPlanV2.ResourceBudget(
                        PlacementPlanV2.ResourceBudget.VERSION,
                        16, 16, 4_096L, PlacementPlanV2.MAX_CANONICAL_BYTES, 32_768L),
                PlacementPlanV2.UNBOUND_CHECKSUM);
    }

    private static PlacementPlanV2.PlacementTargetV2 target(
            int anchorX, int anchorY, int anchorZ,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ
    ) {
        return new PlacementPlanV2.PlacementTargetV2(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "world",
                PlacementPlanV2.AnchorKind.MINIMUM_CORNER,
                anchorX, anchorY, anchorZ,
                minX, minY, minZ,
                maxX, maxY, maxZ);
    }

    private static List<PlacementPlanV2.TileRefV2> tiles() {
        return List.of(new PlacementPlanV2.TileRefV2("tile-x0-z0", 0, 0, 0, 64, 64));
    }
}
