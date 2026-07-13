package com.github.nankotsu029.landformcraft.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerationRequestTest {
    @Test
    void rejectsBoundsLargerThanTheSupportedMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new GenerationBounds(1_001, 128, -32, 160, 62));
    }

    @Test
    void rejectsAnUnboundedVerticalSpan() {
        assertThrows(IllegalArgumentException.class, () -> new GenerationBounds(128, 128, -512, 512, 62));
    }

    @Test
    void rejectsUnsupportedFutureSchemaVersions() {
        assertThrows(IllegalArgumentException.class, () -> new GenerationRequest(
                2,
                "future-request",
                new GenerationBounds(128, 128, -32, 160, 62),
                "Future schema",
                List.of(),
                new GenerationOptions(1, 1L),
                new OutputOptions(128, true, true)
        ));
    }

    @Test
    void rejectsUnsafeReferenceImagePaths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReferenceImage("../secrets.yml", ReferenceImageRole.MOOD_REFERENCE)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReferenceImage("C:\\secrets.yml", ReferenceImageRole.MOOD_REFERENCE)
        );
    }

    @Test
    void defensivelyCopiesReferenceImages() {
        List<ReferenceImage> images = new ArrayList<>();
        images.add(new ReferenceImage("images/coast.png", ReferenceImageRole.TOP_DOWN_SKETCH));

        GenerationRequest request = new GenerationRequest(
                1,
                "rocky-coast-001",
                new GenerationBounds(500, 500, -32, 160, 62),
                "Create a rocky coast.",
                images,
                new GenerationOptions(4, 827_413L),
                new OutputOptions(128, true, true)
        );
        images.clear();

        assertEquals(1, request.images().size());
    }

    @Test
    void rejectsUnknownStructureZonesAndInconsistentTopology() {
        TerrainZone zone = new TerrainZone("coast", TerrainZoneType.ROCKY_COAST, PreferredArea.EAST, 0.5);

        assertThrows(IllegalArgumentException.class, () -> new TerrainIntent(
                1,
                "Rocky coast",
                Topology.COAST_WITH_RIVER,
                Set.of(CardinalDirection.EAST),
                0.5,
                new ReliefIntent(0.1, 0.4, 0.8),
                new CoastlineIntent(0.5, 2, 2),
                new WaterIntent(0, 0, 24, 32),
                List.of(zone),
                List.of(new StructureIntent(StructureType.SMALL_PIER, 1, "missing-zone"))
        ));
    }
}
