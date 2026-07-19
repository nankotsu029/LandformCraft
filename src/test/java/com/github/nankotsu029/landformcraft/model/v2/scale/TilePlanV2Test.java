package com.github.nankotsu029.landformcraft.model.v2.scale;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TilePlanV2Test {
    @Test
    void scaleClassSelectionUsesExplicitThresholds() {
        assertEquals(ScaleClassV2.SMALL, ScaleClassV2.forDimensions(1, 1));
        assertEquals(ScaleClassV2.SMALL, ScaleClassV2.forDimensions(512, 100));
        assertEquals(ScaleClassV2.MEDIUM, ScaleClassV2.forDimensions(513, 1));
        assertEquals(ScaleClassV2.MEDIUM, ScaleClassV2.forDimensions(1_024, 1_024));
        assertEquals(ScaleClassV2.LARGE, ScaleClassV2.forDimensions(1_025, 1));
        assertEquals(ScaleClassV2.LARGE, ScaleClassV2.forDimensions(3_072, 3_072));
        assertThrows(IllegalArgumentException.class, () -> ScaleClassV2.forDimensions(0, 100));
        assertThrows(IllegalArgumentException.class, () -> ScaleClassV2.forDimensions(3_073, 100));
        assertTrue(ScaleClassV2.LARGE.requiresStreamingExecution());
        assertTrue(!ScaleClassV2.SMALL.requiresStreamingExecution()
                && !ScaleClassV2.MEDIUM.requiresStreamingExecution());
    }

    @Test
    void defaultProfilesCoverTheirClassMaximumWithinTheTileBudget() {
        for (ScaleClassV2 scaleClass : ScaleClassV2.values()) {
            ScaleProfileV2 profile = ScaleProfileV2.defaults(scaleClass);
            int side = scaleClass.maximumHorizontalBlocks();
            TilePlanV2 plan = TilePlanV2.of(side, side, profile);
            assertTrue(plan.tileCount() <= profile.maximumTileCount(),
                    scaleClass + " defaults must cover the class maximum area");
        }
        assertEquals(576, TilePlanV2.of(3_072, 3_072, ScaleProfileV2.defaults(ScaleClassV2.LARGE)).tileCount());
    }

    @Test
    void threeThousandSquarePlanDecomposesDeterministically() {
        TilePlanV2 plan = new TilePlanV2(3_000, 3_000, 128, 32);
        assertEquals(24, plan.tileCountX());
        assertEquals(24, plan.tileCountZ());
        assertEquals(576, plan.tileCount());

        TilePlanV2.TileV2 first = plan.tileByIndex(0);
        assertEquals("tile-x0-z0", first.tileId());
        assertEquals(0, first.coreMinX());
        assertEquals(128, first.coreWidth());
        assertEquals(0, first.haloMinX());
        assertEquals(160, first.haloWidth());

        TilePlanV2.TileV2 last = plan.tileByIndex(575);
        assertEquals(23, last.tileX());
        assertEquals(23, last.tileZ());
        assertEquals(2_944, last.coreMinX());
        assertEquals(56, last.coreWidth());
        assertEquals(2_912, last.haloMinX());
        assertEquals(88, last.haloWidth());

        // Canonical row-major identity: index == tileZ * tileCountX + tileX for every tile.
        for (int index = 0; index < plan.tileCount(); index++) {
            TilePlanV2.TileV2 tile = plan.tileByIndex(index);
            assertEquals(index, tile.tileZ() * plan.tileCountX() + tile.tileX());
            assertEquals(tile, plan.tile(tile.tileX(), tile.tileZ()));
        }
    }

    @Test
    void planIsStableAcrossLocaleTimezoneAndThreads() throws Exception {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            TilePlanV2 expected = new TilePlanV2(1_000, 700, 128, 16);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(expected, new TilePlanV2(1_000, 700, 128, 16));
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(expected.tileByIndex(17),
                        one.submit(() -> new TilePlanV2(1_000, 700, 128, 16).tileByIndex(17)).get());
                assertEquals(expected.tileByIndex(17),
                        four.submit(() -> new TilePlanV2(1_000, 700, 128, 16).tileByIndex(17)).get());
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void rejectsInvalidDimensionsTileSizeHaloAndProfiles() {
        assertThrows(IllegalArgumentException.class, () -> new TilePlanV2(0, 100, 128, 16));
        assertThrows(IllegalArgumentException.class, () -> new TilePlanV2(3_073, 100, 128, 16));
        assertThrows(IllegalArgumentException.class, () -> new TilePlanV2(100, 100, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> new TilePlanV2(100, 100, 100, 16));
        assertThrows(IllegalArgumentException.class, () -> new TilePlanV2(100, 100, 640, 16));
        assertThrows(IllegalArgumentException.class, () -> new TilePlanV2(100, 100, 128, -1));
        assertThrows(IllegalArgumentException.class, () -> new TilePlanV2(100, 100, 128, 65));
        assertThrows(IndexOutOfBoundsException.class,
                () -> new TilePlanV2(100, 100, 128, 16).tileByIndex(1));

        assertThrows(IllegalArgumentException.class, () -> new ScaleProfileV2(
                ScaleClassV2.LARGE, 128, 65, 16, 4,
                1L, 1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new ScaleProfileV2(
                ScaleClassV2.SMALL, 128, 16, 3, 16,
                1L, 1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new ScaleProfileV2(
                ScaleClassV2.SMALL, 128, 16, 4, 16,
                0L, 1L, 1L));
        assertThrows(NullPointerException.class, () -> ScaleProfileV2.defaults(null));
    }

    @Test
    void profileBudgetsAreClampedToTrustedCeilings() {
        ScaleProfileV2 profile = new ScaleProfileV2(
                ScaleClassV2.SMALL, 128, 16, 4, Integer.MAX_VALUE,
                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(ScaleProfileV2.TRUSTED_MAXIMUM_TILE_COUNT, profile.maximumTileCount());
        assertEquals(ScaleProfileV2.TRUSTED_MAXIMUM_RETAINED_BYTES, profile.maximumRetainedBytes());
        assertEquals(ScaleProfileV2.TRUSTED_MAXIMUM_WORKING_BYTES, profile.maximumWorkingBytes());
        assertEquals(ScaleProfileV2.TRUSTED_MAXIMUM_ARTIFACT_BYTES, profile.maximumArtifactBytes());
    }
}
