package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementActorKindV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2MeasurementProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2PlacementDimensionPolicyV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Offline gate for V2-11-04／V2-11-05 measurement fixtures: solid surface Releases publish under
 * {@code surface-2_5d}, and the measurement profile is the only admission path for those sizes.
 */
class MeasurementSurfaceFixtureV2Test {
    @Test
    void fiveHundredSolidFixturePublishesAndNeedsMeasurementProfile(@TempDir Path root) throws Exception {
        MeasurementSurfaceFixtureV2.Fixture fixture = MeasurementSurfaceFixtureV2.build500(root.resolve("source"));
        assertEquals(16, fixture.tilePlan().tileCount());
        assertEquals(500, fixture.blueprint().space().bounds().width());
        assertEquals(500, fixture.blueprint().space().bounds().length());
        assertEquals(0, fixture.blueprint().space().bounds().minY());
        assertEquals(1, fixture.blueprint().space().bounds().maxY());

        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                root.resolve("releases"), "measure-500-offline", fixture.source(), false, () -> false);
        ReleaseCoreVerificationV2 verified = new ReleaseSurfaceVerifierV2().verify(release.releaseDirectory());
        assertEquals(
                java.util.List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                verified.manifest().requiredCapabilities());

        Release2PlacementDimensionPolicyV2 production = Release2PlacementDimensionPolicyV2.production();
        assertThrows(IllegalArgumentException.class,
                () -> production.requireAdmitted(500, 500, "world", PlacementActorKindV2.CONSOLE));

        Release2PlacementDimensionPolicyV2 measurement = new Release2PlacementDimensionPolicyV2(
                production.productionGate(),
                Release2MeasurementProfileV2.forIsolatedWorld("world", 500, 500));
        assertDoesNotThrow(
                () -> measurement.requireAdmitted(500, 500, "world", PlacementActorKindV2.CONSOLE));
        assertThrows(IllegalArgumentException.class,
                () -> measurement.requireAdmitted(500, 500, "world", PlacementActorKindV2.PLAYER));
        assertThrows(IllegalArgumentException.class,
                () -> measurement.requireAdmitted(500, 500, "other", PlacementActorKindV2.CONSOLE));
    }

    @Test
    void oneThousandSolidFixturePublishesAndNeedsMeasurementProfile(@TempDir Path root) throws Exception {
        MeasurementSurfaceFixtureV2.Fixture fixture = MeasurementSurfaceFixtureV2.build1000(root.resolve("source"));
        assertEquals(64, fixture.tilePlan().tileCount());
        assertEquals(1000, fixture.blueprint().space().bounds().width());
        assertEquals(1000, fixture.blueprint().space().bounds().length());
        assertEquals(0, fixture.blueprint().space().bounds().minY());
        assertEquals(1, fixture.blueprint().space().bounds().maxY());

        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                root.resolve("releases"), "measure-1000-offline", fixture.source(), false, () -> false);
        ReleaseCoreVerificationV2 verified = new ReleaseSurfaceVerifierV2().verify(release.releaseDirectory());
        assertEquals(
                java.util.List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                verified.manifest().requiredCapabilities());

        Release2PlacementDimensionPolicyV2 production = Release2PlacementDimensionPolicyV2.production();
        assertThrows(IllegalArgumentException.class,
                () -> production.requireAdmitted(1000, 1000, "world", PlacementActorKindV2.CONSOLE));

        Release2PlacementDimensionPolicyV2 measurement = new Release2PlacementDimensionPolicyV2(
                production.productionGate(),
                Release2MeasurementProfileV2.forIsolatedWorld("world", 1000, 1000));
        assertDoesNotThrow(
                () -> measurement.requireAdmitted(1000, 1000, "world", PlacementActorKindV2.CONSOLE));
        assertThrows(IllegalArgumentException.class,
                () -> measurement.requireAdmitted(1000, 1000, "world", PlacementActorKindV2.PLAYER));
        // 500 ceiling must not admit 1000.
        Release2PlacementDimensionPolicyV2 fiveHundredCeiling = new Release2PlacementDimensionPolicyV2(
                production.productionGate(),
                Release2MeasurementProfileV2.forIsolatedWorld("world", 500, 500));
        assertThrows(IllegalArgumentException.class,
                () -> fiveHundredCeiling.requireAdmitted(1000, 1000, "world", PlacementActorKindV2.CONSOLE));
    }
}
