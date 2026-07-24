package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.LakePlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
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
 * V2-15-11 unit evidence for the lake bed materialization stage.
 *
 * <p>The fixture is a closed square basin on a flat land plateau, frozen through the real
 * {@link LakePlanCompilerV2}. Each declared interaction gets a negative: the surface sampler is
 * varied one property at a time, which is exactly how each contract can be broken in production.</p>
 */
class LakeBedMaterializationV2Test {
    private static final int WIDTH = 48;
    private static final int LENGTH = 48;
    private static final int MIN_Y = 32;
    private static final int MAX_Y = 72;
    private static final int WATER_LEVEL = 50;
    private static final int LAND_SURFACE_Y = 54;
    private static final CancellationToken NEVER = () -> false;

    private static final GenerationRequestV2.Bounds BOUNDS =
            new GenerationRequestV2.Bounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL);

    private final LakePlanV2 plan = compileBasin();

    @Test
    void aClosedBasinOnFlatLandFreezesIntoABoundedBedFieldAndCarvesThenFills() throws Exception {
        LakeBedMaterializationV2.FrozenLakeBedV2 frozen = freeze(landPlateau()).orElseThrow();

        // Bounded: the frozen field covers the basin's AABB, never the whole domain.
        assertTrue(frozen.basinCells() > 0);
        assertTrue((long) frozen.footprint().width() * frozen.footprint().length()
                < (long) WIDTH * LENGTH, "the frozen bed field is not bounded to the basin");
        assertTrue(frozen.estimatedResidentBytes() > 0);

        // CARVE_SOLID owns the void, ADD_FLUID owns the water, and the fill is strictly inside the
        // carve.
        assertTrue(frozen.carvedBlocks() >= frozen.filledBlocks(),
                () -> "carve " + frozen.carvedBlocks() + " must be at least fill " + frozen.filledBlocks());

        TerrainBlockResolver base = flatPlateauResolver();
        TerrainBlockResolver decorated = frozen.decorate(base);
        int x = basinCellX(frozen);
        int z = basinCellZ(frozen);
        int bed = frozen.bedYAt(x, z);
        int water = frozen.waterSurfaceYAt(x, z);
        assertEquals("minecraft:stone", decorated.blockStateAt(x, bed, z), "the bed block was re-lined");
        assertEquals("minecraft:water", decorated.blockStateAt(x, bed + 1, z));
        assertEquals("minecraft:water", decorated.blockStateAt(x, water, z));
        assertEquals("minecraft:air", decorated.blockStateAt(x, water + 1, z));
        assertEquals("minecraft:air", decorated.blockStateAt(x, LAND_SURFACE_Y, z));
        // Outside the basin the base resolver still answers, unchanged.
        assertEquals(base.blockStateAt(WIDTH - 1, LAND_SURFACE_Y, LENGTH - 1),
                decorated.blockStateAt(WIDTH - 1, LAND_SURFACE_Y, LENGTH - 1));
    }

    @Test
    void aRunWithoutALakeFreezesNothing() throws Exception {
        assertEquals(Optional.empty(), LakeBedMaterializationV2.freeze(
                List.of(), landPlateau(), BOUNDS, NEVER));
    }

    @Test
    void anOpenSpillLakeIsRejectedAsNotWiredYet() {
        LakePlanV2 openSpill = compileOpenSpillBasin();

        LakeMaterializationRejectedV2 rejected = assertThrows(LakeMaterializationRejectedV2.class,
                () -> LakeBedMaterializationV2.freeze(List.of(openSpill), landPlateau(), BOUNDS, NEVER));

        assertEquals(LakeBedMaterializationV2.RULE_SPILL_NOT_WIRED, rejected.ruleId());
    }

    @Test
    void aBasinOnACoastalModifiersCellIsRejected() {
        // The lake carves the macro foundation only. A cell a coastal modifier owns keeps its own
        // surface contract, so the overlap is a declared conflict rather than a silent overwrite.
        LakeMaterializationRejectedV2 rejected = assertThrows(LakeMaterializationRejectedV2.class,
                () -> freeze(sampler(1, 7, LAND_SURFACE_Y)));

        assertEquals(LakeBedMaterializationV2.RULE_COASTAL_OWNER_CONFLICT, rejected.ruleId());
        assertEquals(plan.featureId(), rejected.featureId());
        assertTrue(rejected.getMessage().contains("coastal surface modifier"), rejected::getMessage);
    }

    @Test
    void aBasinInMarineWaterIsRejected() {
        // A lake fed straight by the sea is not wired.
        LakeMaterializationRejectedV2 rejected = assertThrows(LakeMaterializationRejectedV2.class,
                () -> freeze(sampler(0, 0, LAND_SURFACE_Y)));

        assertEquals(LakeBedMaterializationV2.RULE_MARINE_CONTACT, rejected.ruleId());
    }

    @Test
    void aSurfaceBelowTheBedIsRejectedAsALeak() {
        // A "basin" sitting on top of the terrain would place water with nothing to hold it.
        LakeMaterializationRejectedV2 rejected = assertThrows(LakeMaterializationRejectedV2.class,
                () -> freeze(sampler(1, 0, MIN_Y + 2)));

        assertEquals(LakeBedMaterializationV2.RULE_LEAK_ENVELOPE, rejected.ruleId());
        assertTrue(rejected.getMessage().contains("not below the surface"), rejected::getMessage);
    }

    @Test
    void anEnvelopeStandingBelowTheWaterSurfaceIsRejectedAsALeak() {
        // The basin is cut correctly, but one off-basin neighbour column stops below the water line:
        // the basin would drain sideways into open air.
        CoastalFieldSamplerV2 fields = new CoastalFieldSamplerV2() {
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
                if (CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID.equals(fieldId)) {
                    // x=13 sits just west of the basin ring (x in [14.1, 23.5]), so it is purely the
                    // envelope column next to the western shore.
                    return (globalX == 13 ? WATER_LEVEL - 4 : LAND_SURFACE_Y) * 1_000_000;
                }
                return CoastalTransitionModuleV2.LAND_WATER_FIELD_ID.equals(fieldId) ? 1 : 0;
            }
        };

        LakeMaterializationRejectedV2 rejected =
                assertThrows(LakeMaterializationRejectedV2.class, () -> freeze(fields));

        assertEquals(LakeBedMaterializationV2.RULE_LEAK_ENVELOPE, rejected.ruleId());
        assertTrue(rejected.getMessage().contains("below the water surface"), rejected::getMessage);
    }

    @Test
    void theDeclaredInteractionsAreAStableContract() {
        assertEquals(List.of(
                        "SPILL_TERMINUS=v2.lake.spill-not-wired",
                        "RIM_ENVELOPE=v2.lake.leak-envelope",
                        "COASTAL_FOUNDATION=v2.lake.coastal-owner-conflict",
                        "MACRO_MEDIUM=v2.lake.marine-contact"),
                LakeBedMaterializationV2.declaredInteractions());
        assertEquals("lake-bed-materialization-v1", LakeBedMaterializationV2.CONTRACT_VERSION);
        assertEquals(1_048_576L, LakeBedMaterializationV2.MAXIMUM_FOOTPRINT_CELLS);
    }

    @Test
    void theFreezeIsIndependentOfLocaleAndTimezone() throws Exception {
        LakeBedMaterializationV2.FrozenLakeBedV2 first = freeze(landPlateau()).orElseThrow();
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            LakeBedMaterializationV2.FrozenLakeBedV2 second = freeze(landPlateau()).orElseThrow();
            assertEquals(first.basinCells(), second.basinCells());
            assertEquals(first.carvedBlocks(), second.carvedBlocks());
            assertEquals(first.filledBlocks(), second.filledBlocks());
            assertEquals(first.footprint(), second.footprint());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private Optional<LakeBedMaterializationV2.FrozenLakeBedV2> freeze(CoastalFieldSamplerV2 fields)
            throws LakeMaterializationRejectedV2 {
        return LakeBedMaterializationV2.freeze(List.of(plan), fields, BOUNDS, NEVER);
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

    private static int basinCellX(LakeBedMaterializationV2.FrozenLakeBedV2 frozen) {
        return firstBasinCell(frozen)[0];
    }

    private static int basinCellZ(LakeBedMaterializationV2.FrozenLakeBedV2 frozen) {
        return firstBasinCell(frozen)[1];
    }

    private static int[] firstBasinCell(LakeBedMaterializationV2.FrozenLakeBedV2 frozen) {
        LakeBedMaterializationV2.Footprint footprint = frozen.footprint();
        for (int z = footprint.originZ(); z < footprint.originZ() + footprint.length(); z++) {
            for (int x = footprint.originX(); x < footprint.originX() + footprint.width(); x++) {
                if (frozen.basinAt(x, z) && frozen.bedYAt(x, z) < LAND_SURFACE_Y - 1) {
                    return new int[] {x, z};
                }
            }
        }
        throw new IllegalStateException("the frozen bed field carries no basin cell");
    }

    private static LakePlanV2 compileBasin() {
        return new LakePlanCompilerV2().compile(basinFeature(TerrainIntentV2.LakeTerminalPolicy.CLOSED),
                basinIntent(), new WorldBlueprintV2.Bounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL),
                "0".repeat(64));
    }

    private static LakePlanV2 compileOpenSpillBasin() {
        return new LakePlanCompilerV2().compile(
                basinFeature(TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL), basinIntent(),
                new WorldBlueprintV2.Bounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL), "0".repeat(64));
    }

    private static TerrainIntentV2.Feature basinFeature(TerrainIntentV2.LakeTerminalPolicy terminalPolicy) {
        List<TerrainIntentV2.Point2> ring = List.of(
                new TerrainIntentV2.Point2(300_000, 300_000),
                new TerrainIntentV2.Point2(500_000, 300_000),
                new TerrainIntentV2.Point2(500_000, 500_000),
                new TerrainIntentV2.Point2(300_000, 500_000),
                new TerrainIntentV2.Point2(300_000, 300_000));
        boolean closed = terminalPolicy == TerrainIntentV2.LakeTerminalPolicy.CLOSED;
        return new TerrainIntentV2.Feature(
                "unit-basin",
                TerrainIntentV2.FeatureKind.LAKE,
                new TerrainIntentV2.PolygonGeometry(List.of(ring)),
                new TerrainIntentV2.LakeParameters(
                        new TerrainIntentV2.IntRange(3, 5),
                        2,
                        terminalPolicy,
                        TerrainIntentV2.LakeSpillSelection.DECLARED_EDGE,
                        closed ? -1 : 0,
                        closed ? 0 : 3,
                        closed ? 0 : 4,
                        TerrainIntentV2.LakeFloorProfile.EDGE_TO_CENTER_LINEAR),
                0,
                new TerrainIntentV2.Provenance(
                        TerrainIntentV2.ProvenanceSource.MANUAL, "unit", 1_000_000,
                        TerrainIntentV2.ConfirmationState.CONFIRMED));
    }

    private static TerrainIntentV2 basinIntent() {
        return new TerrainIntentV2(
                2, "unit-basin-intent", "unit test lake basin",
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                List.of(basinFeature(TerrainIntentV2.LakeTerminalPolicy.CLOSED)),
                List.of(), List.of(),
                new TerrainIntentV2.EnvironmentDescriptor(null, null, null),
                List.of(), List.of(),
                new TerrainIntentV2.Provenance(
                        TerrainIntentV2.ProvenanceSource.MANUAL, "unit", 1_000_000,
                        TerrainIntentV2.ConfirmationState.CONFIRMED));
    }
}
