package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.CanyonPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.CanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalFieldSamplerV2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-15-12 unit evidence for the canyon bed materialization stage.
 *
 * <p>The fixture is a straight north→south corridor sharing a bed with a real, compiled
 * {@link MeanderingRiverPlanV2} reach (the HARD {@code WITHIN} binding {@link CanyonPlanCompilerV2}
 * requires). Each declared interaction gets a negative: the surface sampler is varied one property at
 * a time, which is exactly how each contract can be broken in production.</p>
 */
class CanyonBedMaterializationV2Test {
    private static final int WIDTH = 48;
    private static final int LENGTH = 48;
    private static final int MIN_Y = 32;
    private static final int MAX_Y = 72;
    private static final int WATER_LEVEL = 50;
    private static final int LAND_SURFACE_Y = 54;
    private static final CancellationToken NEVER = () -> false;

    private static final GenerationRequestV2.Bounds BOUNDS =
            new GenerationRequestV2.Bounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL);

    private final MeanderingRiverPlanV2 river = compileRiver();
    private final CanyonPlanV2 plan = compileCanyon(river);

    @Test
    void aCorridorOnFlatLandFreezesIntoABoundedFieldAndCarves() throws Exception {
        CanyonBedMaterializationV2.FrozenCanyonBedV2 frozen = freeze(landPlateau()).orElseThrow();

        // Bounded: the frozen field covers the corridor's AABB, never the whole domain.
        assertTrue(frozen.corridorCells() > 0);
        assertTrue((long) frozen.footprint().width() * frozen.footprint().length()
                < (long) WIDTH * LENGTH, "the frozen field is not bounded to the corridor");
        assertTrue(frozen.estimatedResidentBytes() > 0);
        assertTrue(frozen.carvedBlocks() > 0);

        TerrainBlockResolver base = flatPlateauResolver();
        TerrainBlockResolver decorated = frozen.decorate(base);
        int x = corridorCellX(frozen);
        int z = corridorCellZ(frozen);
        int surface = frozen.surfaceYAt(x, z);
        assertEquals("minecraft:air", decorated.blockStateAt(x, surface + 1, z));
        assertEquals("minecraft:air", decorated.blockStateAt(x, LAND_SURFACE_Y, z));
        assertEquals(base.blockStateAt(x, surface, z), decorated.blockStateAt(x, surface, z),
                "the surface block itself was re-lined");
        // Outside the corridor the base resolver still answers, unchanged.
        assertEquals(base.blockStateAt(WIDTH - 1, LAND_SURFACE_Y, LENGTH - 1),
                decorated.blockStateAt(WIDTH - 1, LAND_SURFACE_Y, LENGTH - 1));
    }

    @Test
    void aRunWithoutACanyonFreezesNothing() throws Exception {
        assertEquals(Optional.empty(), CanyonBedMaterializationV2.freeze(
                List.of(), landPlateau(), BOUNDS, NEVER));
    }

    @Test
    void aCorridorOnACoastalModifiersCellIsRejected() {
        // The canyon carves the macro foundation only. A cell a coastal modifier owns keeps its own
        // surface contract, so the overlap is a declared conflict rather than a silent overwrite.
        CanyonMaterializationRejectedV2 rejected = assertThrows(CanyonMaterializationRejectedV2.class,
                () -> freeze(sampler(1, 7, LAND_SURFACE_Y)));

        assertEquals(CanyonBedMaterializationV2.RULE_COASTAL_OWNER_CONFLICT, rejected.ruleId());
        assertEquals(plan.featureId(), rejected.featureId());
        assertTrue(rejected.getMessage().contains("coastal surface modifier"), rejected::getMessage);
    }

    @Test
    void aCorridorInMarineWaterIsRejected() {
        // A canyon opening straight into the sea is not wired.
        CanyonMaterializationRejectedV2 rejected = assertThrows(CanyonMaterializationRejectedV2.class,
                () -> freeze(sampler(0, 0, LAND_SURFACE_Y)));

        assertEquals(CanyonBedMaterializationV2.RULE_MARINE_CONTACT, rejected.ruleId());
    }

    @Test
    void aBackgroundBelowTheCarvedSurfaceIsRejected() {
        // A background surface lower than the canyon's own carved surface would mean the canyon rises
        // above the terrain it carves from — a leak in the opposite direction from river/lake.
        CanyonMaterializationRejectedV2 rejected = assertThrows(CanyonMaterializationRejectedV2.class,
                () -> freeze(sampler(1, 0, MIN_Y + 2)));

        assertEquals(CanyonBedMaterializationV2.RULE_SURFACE_ABOVE_BACKGROUND, rejected.ruleId());
        assertTrue(rejected.getMessage().contains("rises above the background"), rejected::getMessage);
    }

    @Test
    void theDeclaredInteractionsAreAStableContract() {
        assertEquals(List.of(
                        "SHARED_RIVER_BED=v2.canyon.vertical-bounds",
                        "BACKGROUND_ENVELOPE=v2.canyon.surface-above-background",
                        "COASTAL_FOUNDATION=v2.canyon.coastal-owner-conflict",
                        "MACRO_MEDIUM=v2.canyon.marine-contact"),
                CanyonBedMaterializationV2.declaredInteractions());
        assertEquals("canyon-bed-materialization-v1", CanyonBedMaterializationV2.CONTRACT_VERSION);
        assertEquals(1_048_576L, CanyonBedMaterializationV2.MAXIMUM_FOOTPRINT_CELLS);
    }

    @Test
    void theFreezeIsIndependentOfLocaleAndTimezone() throws Exception {
        CanyonBedMaterializationV2.FrozenCanyonBedV2 first = freeze(landPlateau()).orElseThrow();
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            CanyonBedMaterializationV2.FrozenCanyonBedV2 second = freeze(landPlateau()).orElseThrow();
            assertEquals(first.corridorCells(), second.corridorCells());
            assertEquals(first.carvedBlocks(), second.carvedBlocks());
            assertEquals(first.footprint(), second.footprint());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private Optional<CanyonBedMaterializationV2.FrozenCanyonBedV2> freeze(CoastalFieldSamplerV2 fields)
            throws CanyonMaterializationRejectedV2 {
        return CanyonBedMaterializationV2.freeze(List.of(plan), fields, BOUNDS, NEVER);
    }

    private static CoastalFieldSamplerV2 landPlateau() {
        return sampler(1, 0, LAND_SURFACE_Y);
    }

    /** Constant surface sampler: one land-water medium, one owner index, one surface height. */
    private static CoastalFieldSamplerV2 sampler(int landWater, int ownerIndex, int surfaceY) {
        return new CoastalFieldSamplerV2() {
            @Override
            public int width() {
                return WIDTH;
            }

            @Override
            public int length() {
                return LENGTH;
            }

            @Override
            public int valueAt(String fieldId, int globalX, int globalZ) {
                if (CoastalTransitionModuleV2.LAND_WATER_FIELD_ID.equals(fieldId)) {
                    return landWater;
                }
                if (CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID.equals(fieldId)) {
                    return ownerIndex;
                }
                if (CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID.equals(fieldId)) {
                    return Math.multiplyExact(surfaceY, 1_000_000);
                }
                return 0;
            }
        };
    }

    /** The canonical shape of the coastal resolver over a flat land plateau. */
    private static TerrainBlockResolver flatPlateauResolver() {
        return (x, y, z) -> {
            if (y == MIN_Y) {
                return "minecraft:bedrock";
            }
            if (y > LAND_SURFACE_Y) {
                return "minecraft:air";
            }
            return y == LAND_SURFACE_Y ? "minecraft:grass_block"
                    : y >= LAND_SURFACE_Y - 2 ? "minecraft:dirt" : "minecraft:stone";
        };
    }

    private static int corridorCellX(CanyonBedMaterializationV2.FrozenCanyonBedV2 frozen) {
        return firstCorridorCell(frozen)[0];
    }

    private static int corridorCellZ(CanyonBedMaterializationV2.FrozenCanyonBedV2 frozen) {
        return firstCorridorCell(frozen)[1];
    }

    private static int[] firstCorridorCell(CanyonBedMaterializationV2.FrozenCanyonBedV2 frozen) {
        CanyonBedMaterializationV2.Footprint footprint = frozen.footprint();
        for (int z = footprint.originZ(); z < footprint.originZ() + footprint.length(); z++) {
            for (int x = footprint.originX(); x < footprint.originX() + footprint.width(); x++) {
                if (frozen.corridorAt(x, z) && frozen.surfaceYAt(x, z) < LAND_SURFACE_Y) {
                    return new int[] {x, z};
                }
            }
        }
        throw new IllegalStateException("the frozen field carries no corridor cell");
    }

    private static MeanderingRiverPlanV2 compileRiver() {
        TerrainIntentV2.Feature feature = new TerrainIntentV2.Feature(
                "unit-reach",
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                new TerrainIntentV2.SplineGeometry(
                        List.of(new TerrainIntentV2.Point2(500_000, 100_000),
                                new TerrainIntentV2.Point2(500_000, 400_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.MeanderingRiverParameters(
                        new TerrainIntentV2.IntRange(4, 6),
                        TerrainIntentV2.DischargeClass.MEDIUM,
                        1_000,
                        TerrainIntentV2.RiverVariant.RIVER),
                0,
                new TerrainIntentV2.Provenance(
                        TerrainIntentV2.ProvenanceSource.MANUAL, "unit", 1_000_000,
                        TerrainIntentV2.ConfirmationState.CONFIRMED));
        return new MeanderingRiverPlanCompilerV2().compile(
                feature,
                new WorldBlueprintV2.Bounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL),
                "0".repeat(64));
    }

    private static CanyonPlanV2 compileCanyon(MeanderingRiverPlanV2 river) {
        TerrainIntentV2.Feature canyonFeature = canyonFeature();
        TerrainIntentV2 intent = canyonIntent(canyonFeature);
        return new CanyonPlanCompilerV2().compile(
                canyonFeature, intent, List.of(river),
                new WorldBlueprintV2.Bounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL),
                "0".repeat(64));
    }

    private static TerrainIntentV2.Feature canyonFeature() {
        return new TerrainIntentV2.Feature(
                "unit-canyon",
                TerrainIntentV2.FeatureKind.CANYON,
                new TerrainIntentV2.SplineGeometry(
                        List.of(new TerrainIntentV2.Point2(500_000, 100_000),
                                new TerrainIntentV2.Point2(500_000, 400_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.CanyonParameters(
                        new TerrainIntentV2.IntRange(6, 6),
                        new TerrainIntentV2.IntRange(9, 9),
                        new TerrainIntentV2.IntRange(3, 3),
                        TerrainIntentV2.CanyonCrossSection.V,
                        0, 0),
                0,
                new TerrainIntentV2.Provenance(
                        TerrainIntentV2.ProvenanceSource.MANUAL, "unit", 1_000_000,
                        TerrainIntentV2.ConfirmationState.CONFIRMED));
    }

    private static TerrainIntentV2 canyonIntent(TerrainIntentV2.Feature canyonFeature) {
        TerrainIntentV2.Feature riverFeature = new TerrainIntentV2.Feature(
                "unit-reach",
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                new TerrainIntentV2.SplineGeometry(
                        List.of(new TerrainIntentV2.Point2(500_000, 100_000),
                                new TerrainIntentV2.Point2(500_000, 400_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.MeanderingRiverParameters(
                        new TerrainIntentV2.IntRange(4, 6),
                        TerrainIntentV2.DischargeClass.MEDIUM,
                        1_000,
                        TerrainIntentV2.RiverVariant.RIVER),
                0,
                new TerrainIntentV2.Provenance(
                        TerrainIntentV2.ProvenanceSource.MANUAL, "unit", 1_000_000,
                        TerrainIntentV2.ConfirmationState.CONFIRMED));
        TerrainIntentV2.Relation within = new TerrainIntentV2.Relation(
                "unit-within", TerrainIntentV2.RelationKind.WITHIN,
                "feature:unit-reach", "feature:unit-canyon",
                TerrainIntentV2.Strength.HARD);
        return new TerrainIntentV2(
                2, "unit-canyon-intent", "unit test canyon corridor",
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                List.of(riverFeature, canyonFeature),
                List.of(within), List.of(),
                new TerrainIntentV2.EnvironmentDescriptor(null, null, null),
                List.of(), List.of(),
                new TerrainIntentV2.Provenance(
                        TerrainIntentV2.ProvenanceSource.MANUAL, "unit", 1_000_000,
                        TerrainIntentV2.ConfirmationState.CONFIRMED));
    }
}
