package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.HydrologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.DeterministicHydrologyRoutingSolverV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingRequestV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingResultV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.ProvisionalSurfaceV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.BoundedHydrologyReconcilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.WaterfallPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WaterfallPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
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
 * V2-15-13 unit evidence for the waterfall plunge-basin materialization stage.
 *
 * <p>The fixture is a fall on a straight north→south reach compiled by the production
 * {@link MeanderingRiverPlanCompilerV2} and frozen by the production
 * {@link RiverBedMaterializationV2}, because the basin is defined <em>relative to</em> that frozen
 * host channel (the HARD {@code ON_PATH_OF} binding {@link WaterfallPlanCompilerV2} requires). Each
 * declared interaction gets a negative: the surface sampler is varied one property at a time, which
 * is exactly how each contract can be broken in production.</p>
 */
class WaterfallBasinMaterializationV2Test {
    private static final int WIDTH = 48;
    private static final int LENGTH = 48;
    private static final int MIN_Y = 32;
    private static final int MAX_Y = 72;
    private static final int WATER_LEVEL = 50;
    private static final int LAND_SURFACE_Y = 54;
    private static final CancellationToken NEVER = () -> false;

    private static final GenerationRequestV2.Bounds BOUNDS =
            new GenerationRequestV2.Bounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL);

    private final MeanderingRiverPlanV2 river = compileReach();
    private final WaterfallPlanV2 plan = compileFall(river);
    private final RiverBedMaterializationV2.FrozenRiverBedV2 hostBed = freezeHostBed();

    @Test
    void aFallOnFlatLandFreezesIntoABoundedBasinFieldAndCarvesThenFills() throws Exception {
        WaterfallBasinMaterializationV2.FrozenWaterfallBasinV2 frozen =
                freeze(landPlateau()).orElseThrow();

        // Bounded: the frozen field covers the basin's AABB, never the whole domain.
        assertTrue(frozen.basinCells() > 0);
        assertTrue((long) frozen.footprint().width() * frozen.footprint().length()
                < (long) WIDTH * LENGTH, "the frozen basin field is not bounded to the fall");
        assertTrue(frozen.estimatedResidentBytes() > 0);
        // CARVE_SOLID owns the void and ADD_FLUID owns the water strictly inside it.
        assertTrue(frozen.filledBlocks() > 0);
        assertTrue(frozen.carvedBlocks() > frozen.filledBlocks(),
                () -> "carve " + frozen.carvedBlocks() + " must exceed fill " + frozen.filledBlocks());

        TerrainBlockResolver base = flatPlateauResolver();
        TerrainBlockResolver decorated = frozen.decorate(base);
        int[] cell = firstBasinCell(frozen);
        int x = cell[0];
        int z = cell[1];
        int floor = frozen.floorYAt(x, z);
        int water = frozen.waterSurfaceYAt(x, z);
        assertEquals(base.blockStateAt(x, floor, z), decorated.blockStateAt(x, floor, z),
                "the pool floor block was re-lined");
        assertEquals("minecraft:water", decorated.blockStateAt(x, floor + 1, z));
        assertEquals("minecraft:water", decorated.blockStateAt(x, water, z));
        assertEquals("minecraft:air", decorated.blockStateAt(x, water + 1, z));
        assertEquals("minecraft:air", decorated.blockStateAt(x, LAND_SURFACE_Y, z));
        // Outside the basin the base resolver still answers, unchanged.
        assertEquals(base.blockStateAt(WIDTH - 1, LAND_SURFACE_Y, LENGTH - 1),
                decorated.blockStateAt(WIDTH - 1, LAND_SURFACE_Y, LENGTH - 1));
    }

    @Test
    void theBasinNeverClaimsAHostChannelCell() throws Exception {
        // HOST_CHANNEL: the two materializations are disjoint by construction, which is what lets the
        // shared pipeline compose them in a fixed order without either overriding the other.
        WaterfallBasinMaterializationV2.FrozenWaterfallBasinV2 frozen =
                freeze(landPlateau()).orElseThrow();
        for (int z = 0; z < LENGTH; z++) {
            for (int x = 0; x < WIDTH; x++) {
                if (frozen.basinAt(x, z)) {
                    int cellX = x;
                    int cellZ = z;
                    assertTrue(!hostBed.channelAt(cellX, cellZ),
                            () -> "basin claimed host channel cell " + cellX + "," + cellZ);
                }
            }
        }
    }

    @Test
    void theBasinStaysDownstreamOfTheLipCrest() throws Exception {
        // CREST_HALF_PLANE: the pool never cuts under the upstream reach that feeds it.
        WaterfallBasinMaterializationV2.FrozenWaterfallBasinV2 frozen =
                freeze(landPlateau()).orElseThrow();
        long fallX = plan.baseXMillionths() - plan.lipXMillionths();
        long fallZ = plan.baseZMillionths() - plan.lipZMillionths();
        for (int z = 0; z < LENGTH; z++) {
            for (int x = 0; x < WIDTH; x++) {
                if (!frozen.basinAt(x, z)) {
                    continue;
                }
                long offsetX = Math.floorDiv(
                        (long) x * 1_000_000L + 500_000L - plan.lipXMillionths(), 1_000L);
                long offsetZ = Math.floorDiv(
                        (long) z * 1_000_000L + 500_000L - plan.lipZMillionths(), 1_000L);
                int cellX = x;
                int cellZ = z;
                assertTrue(fallX * offsetX + fallZ * offsetZ >= 0L,
                        () -> "basin cell " + cellX + "," + cellZ + " sits upstream of the lip crest");
            }
        }
    }

    @Test
    void aRunWithoutAWaterfallFreezesNothing() throws Exception {
        assertEquals(Optional.empty(), WaterfallBasinMaterializationV2.freeze(
                List.of(), Optional.of(hostBed), landPlateau(), BOUNDS, NEVER));
    }

    @Test
    void aFallWithoutAFrozenHostChannelIsRejected() {
        WaterfallMaterializationRejectedV2 rejected = assertThrows(
                WaterfallMaterializationRejectedV2.class,
                () -> WaterfallBasinMaterializationV2.freeze(
                        List.of(plan), Optional.empty(), landPlateau(), BOUNDS, NEVER));

        assertEquals(WaterfallBasinMaterializationV2.RULE_HOST_CHANNEL_NOT_FROZEN, rejected.ruleId());
        assertEquals(plan.featureId(), rejected.featureId());
        assertEquals(-1, rejected.cellX());
    }

    @Test
    void aBasinOnACoastalModifiersCellIsRejected() {
        // The fall cuts the macro foundation only. A cell a coastal modifier owns keeps its own
        // surface contract, so the overlap is a declared conflict rather than a silent overwrite.
        WaterfallMaterializationRejectedV2 rejected = assertThrows(
                WaterfallMaterializationRejectedV2.class, () -> freeze(sampler(1, 7, LAND_SURFACE_Y)));

        assertEquals(WaterfallBasinMaterializationV2.RULE_COASTAL_OWNER_CONFLICT, rejected.ruleId());
        assertTrue(rejected.getMessage().contains("coastal surface modifier"), rejected::getMessage);
    }

    @Test
    void aBasinInMarineWaterIsRejected() {
        // A fall pouring straight into the sea is not wired.
        WaterfallMaterializationRejectedV2 rejected = assertThrows(
                WaterfallMaterializationRejectedV2.class, () -> freeze(sampler(0, 0, LAND_SURFACE_Y)));

        assertEquals(WaterfallBasinMaterializationV2.RULE_MARINE_CONTACT, rejected.ruleId());
    }

    @Test
    void aSurfaceBelowThePoolFloorIsRejectedAsALeak() {
        // A "basin" sitting on top of the terrain would place water with nothing to hold it.
        WaterfallMaterializationRejectedV2 rejected = assertThrows(
                WaterfallMaterializationRejectedV2.class, () -> freeze(sampler(1, 0, MIN_Y + 2)));

        assertEquals(WaterfallBasinMaterializationV2.RULE_LEAK_ENVELOPE, rejected.ruleId());
        assertTrue(rejected.getMessage().contains("not below the surface"), rejected::getMessage);
    }

    @Test
    void anEnvelopeStandingBelowThePoolWaterSurfaceIsRejectedAsALeak() throws Exception {
        // The basin is cut correctly, but one column of the terrain around it stops below the pool
        // water line: the fall would drain sideways into open air. The column is taken from the
        // frozen basin itself so the negative stays a single-property variation of the positive
        // rather than a coordinate guess that could silently stop touching the basin.
        int[] envelope = firstEnvelopeCell(freeze(landPlateau()).orElseThrow());
        WaterfallMaterializationRejectedV2 rejected = assertThrows(
                WaterfallMaterializationRejectedV2.class,
                () -> freeze(new CoastalFieldSamplerV2() {
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
                            boolean lowered = globalX == envelope[0] && globalZ == envelope[1];
                            return (lowered ? MIN_Y + 4 : LAND_SURFACE_Y) * 1_000_000;
                        }
                        return CoastalTransitionModuleV2.LAND_WATER_FIELD_ID.equals(fieldId) ? 1 : 0;
                    }
                }));

        assertEquals(WaterfallBasinMaterializationV2.RULE_LEAK_ENVELOPE, rejected.ruleId());
        assertTrue(rejected.getMessage().contains("below the pool water"), rejected::getMessage);
    }

    @Test
    void theDeclaredInteractionsAreAStableContract() {
        assertEquals(List.of(
                        "HOST_CHANNEL=v2.waterfall.host-channel-not-frozen",
                        "CREST_HALF_PLANE=v2.waterfall.empty-basin",
                        "BASIN_ENVELOPE=v2.waterfall.leak-envelope",
                        "COASTAL_FOUNDATION=v2.waterfall.coastal-owner-conflict",
                        "MACRO_MEDIUM=v2.waterfall.marine-contact"),
                WaterfallBasinMaterializationV2.declaredInteractions());
        assertEquals("waterfall-basin-materialization-v1",
                WaterfallBasinMaterializationV2.CONTRACT_VERSION);
        assertEquals(1_048_576L, WaterfallBasinMaterializationV2.MAXIMUM_FOOTPRINT_CELLS);
    }

    @Test
    void theFreezeIsIndependentOfLocaleAndTimezone() throws Exception {
        WaterfallBasinMaterializationV2.FrozenWaterfallBasinV2 first =
                freeze(landPlateau()).orElseThrow();
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            WaterfallBasinMaterializationV2.FrozenWaterfallBasinV2 second =
                    freeze(landPlateau()).orElseThrow();
            assertEquals(first.basinCells(), second.basinCells());
            assertEquals(first.carvedBlocks(), second.carvedBlocks());
            assertEquals(first.filledBlocks(), second.filledBlocks());
            assertEquals(first.footprint(), second.footprint());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private Optional<WaterfallBasinMaterializationV2.FrozenWaterfallBasinV2> freeze(
            CoastalFieldSamplerV2 fields) throws WaterfallMaterializationRejectedV2 {
        return WaterfallBasinMaterializationV2.freeze(
                List.of(plan), Optional.of(hostBed), fields, BOUNDS, NEVER);
    }

    private RiverBedMaterializationV2.FrozenRiverBedV2 freezeHostBed() {
        try {
            return RiverBedMaterializationV2.freeze(
                    List.of(river), solveRouting(), reconcile(river), landPlateau(), BOUNDS, NEVER)
                    .orElseThrow();
        } catch (RiverMaterializationRejectedV2 exception) {
            throw new IllegalStateException("the unit host reach must freeze", exception);
        }
    }

    private static int[] firstBasinCell(
            WaterfallBasinMaterializationV2.FrozenWaterfallBasinV2 frozen) {
        WaterfallBasinMaterializationV2.Footprint footprint = frozen.footprint();
        for (int z = footprint.originZ(); z < footprint.originZ() + footprint.length(); z++) {
            for (int x = footprint.originX(); x < footprint.originX() + footprint.width(); x++) {
                if (frozen.basinAt(x, z)
                        && frozen.waterSurfaceYAt(x, z) > frozen.floorYAt(x, z)) {
                    return new int[] {x, z};
                }
            }
        }
        throw new IllegalStateException("the frozen basin field carries no basin cell");
    }

    /** An off-basin, off-host-channel neighbour of the frozen basin: the containment envelope. */
    private int[] firstEnvelopeCell(WaterfallBasinMaterializationV2.FrozenWaterfallBasinV2 frozen) {
        for (int z = 1; z < LENGTH - 1; z++) {
            for (int x = 1; x < WIDTH - 1; x++) {
                if (frozen.basinAt(x, z) || hostBed.channelAt(x, z)) {
                    continue;
                }
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx != 0 || dz != 0) && frozen.basinAt(x + dx, z + dz)) {
                            return new int[] {x, z};
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("the frozen basin field has no containment envelope");
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

    private static MeanderingRiverPlanV2 compileReach() {
        return new MeanderingRiverPlanCompilerV2().compile(
                riverFeature(),
                new WorldBlueprintV2.Bounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL),
                "0".repeat(64));
    }

    private static WaterfallPlanV2 compileFall(MeanderingRiverPlanV2 river) {
        TerrainIntentV2.Feature fall = fallFeature();
        return new WaterfallPlanCompilerV2().compile(
                fall, fallIntent(fall), List.of(river),
                new WorldBlueprintV2.Bounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL),
                "0".repeat(64));
    }

    private static TerrainIntentV2.Feature riverFeature() {
        return new TerrainIntentV2.Feature(
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
    }

    private static TerrainIntentV2.Feature fallFeature() {
        return new TerrainIntentV2.Feature(
                "unit-fall",
                TerrainIntentV2.FeatureKind.WATERFALL,
                new TerrainIntentV2.PointGeometry(new TerrainIntentV2.Point2(500_000, 250_000)),
                new TerrainIntentV2.WaterfallParameters(
                        new TerrainIntentV2.IntRange(4, 8), 3, 8, 0),
                0,
                new TerrainIntentV2.Provenance(
                        TerrainIntentV2.ProvenanceSource.MANUAL, "unit", 1_000_000,
                        TerrainIntentV2.ConfirmationState.CONFIRMED));
    }

    private static TerrainIntentV2 fallIntent(TerrainIntentV2.Feature fall) {
        TerrainIntentV2.Relation onPath = new TerrainIntentV2.Relation(
                "unit-on-path", TerrainIntentV2.RelationKind.ON_PATH_OF,
                "feature:unit-fall", "feature:unit-reach",
                TerrainIntentV2.Strength.HARD);
        return new TerrainIntentV2(
                2, "unit-fall-intent", "unit test waterfall plunge basin",
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                List.of(riverFeature(), fall),
                List.of(onPath), List.of(),
                new TerrainIntentV2.EnvironmentDescriptor(null, null, null),
                List.of(), List.of(),
                new TerrainIntentV2.Provenance(
                        TerrainIntentV2.ProvenanceSource.MANUAL, "unit", 1_000_000,
                        TerrainIntentV2.ConfirmationState.CONFIRMED));
    }

    private static HydrologyRoutingResultV2 solveRouting() {
        return new DeterministicHydrologyRoutingSolverV2().solve(
                HydrologyRoutingRequestV2.create(
                        WIDTH, LENGTH,
                        new HydrologyPlanCompilerV2().compile(
                                new WorldBlueprintV2.Bounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL)),
                        ProvisionalSurfaceV2.routable((x, z) -> 54_000_000 - z * 1_000),
                        List.of(new HydrologyRoutingArtifactV2.Outlet(
                                "south-mouth", 0, LENGTH - 1,
                                HydrologyRoutingArtifactV2.OutletKind.BOUNDARY))),
                HydrologyRoutingRequestV2.ExecutionProfile.canonical(), NEVER);
    }

    private static HydrologyReconciliationArtifactV2 reconcile(MeanderingRiverPlanV2 plan) {
        HydrologyReconciliationPlanCompilerV2 compiler = new HydrologyReconciliationPlanCompilerV2();
        HydrologyReconciliationPlanV2 reconciliationPlan = compiler.compile(
                "0".repeat(64), List.of(plan), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                HydrologyReconciliationPlanV2.MAXIMUM_WORK_UNITS,
                HydrologyReconciliationPlanV2.MAXIMUM_WORKING_BYTES,
                HydrologyReconciliationPlanV2.MAXIMUM_ARTIFACT_BYTES);
        return new BoundedHydrologyReconcilerV2().reconcile(
                "0".repeat(64), reconciliationPlan, compiler.baselineState(reconciliationPlan), NEVER);
    }
}
