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
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-05 unit evidence for the river bed materialization stage.
 *
 * <p>The fixture is a straight north→south reach on a flat land plateau, frozen through the real
 * routing solver and the real bounded reconciler so the "global route freeze" this stage depends on
 * is the production one. Each declared interaction gets a negative: the surface sampler is varied
 * one property at a time, which is exactly how each contract can be broken in production.</p>
 */
class RiverBedMaterializationV2Test {
    private static final int WIDTH = 48;
    private static final int LENGTH = 48;
    private static final int MIN_Y = 32;
    private static final int MAX_Y = 72;
    private static final int WATER_LEVEL = 50;
    private static final int LAND_SURFACE_Y = 54;
    private static final CancellationToken NEVER = () -> false;

    private static final GenerationRequestV2.Bounds BOUNDS =
            new GenerationRequestV2.Bounds(WIDTH, LENGTH, MIN_Y, MAX_Y, WATER_LEVEL);

    private final MeanderingRiverPlanV2 plan = compileReach();
    private final HydrologyRoutingResultV2 routing = solveRouting(WIDTH, LENGTH);
    private final HydrologyReconciliationArtifactV2 reconciliation = reconcile(plan);

    @Test
    void aReachOnFlatLandFreezesIntoABoundedBedFieldAndCarvesThenFills() throws Exception {
        RiverBedMaterializationV2.FrozenRiverBedV2 frozen = freeze(landPlateau()).orElseThrow();

        // Bounded: the frozen field covers the reach's AABB, never the whole domain.
        assertTrue(frozen.channelCells() > 0);
        assertTrue(frozen.bankCells() > 0);
        assertTrue((long) frozen.footprint().width() * frozen.footprint().length()
                < (long) WIDTH * LENGTH, "the frozen bed field is not bounded to the reach");
        assertTrue(frozen.estimatedResidentBytes() > 0);

        // CARVE_SOLID owns the void, ADD_FLUID owns the water, and the fill is strictly inside the
        // carve: at 1 block of declared depth per channel cell, carved > filled.
        assertEquals(frozen.channelCells(), frozen.filledBlocks());
        assertTrue(frozen.carvedBlocks() > frozen.filledBlocks(),
                () -> "carve " + frozen.carvedBlocks() + " must exceed fill " + frozen.filledBlocks());

        TerrainBlockResolver base = flatPlateauResolver();
        TerrainBlockResolver decorated = frozen.decorate(base);
        int x = channelCellX(frozen);
        int z = channelCellZ(frozen);
        int bed = frozen.bedYAt(x, z);
        int water = frozen.waterSurfaceYAt(x, z);
        assertEquals("minecraft:stone", decorated.blockStateAt(x, bed, z), "the bed block was re-lined");
        assertEquals("minecraft:water", decorated.blockStateAt(x, bed + 1, z));
        assertEquals("minecraft:water", decorated.blockStateAt(x, water, z));
        assertEquals("minecraft:air", decorated.blockStateAt(x, water + 1, z));
        assertEquals("minecraft:air", decorated.blockStateAt(x, LAND_SURFACE_Y, z));
        // Outside the reach the base resolver still answers, unchanged.
        assertEquals(base.blockStateAt(WIDTH - 1, LAND_SURFACE_Y, LENGTH - 1),
                decorated.blockStateAt(WIDTH - 1, LAND_SURFACE_Y, LENGTH - 1));
    }

    @Test
    void aRunWithoutARiverFreezesNothing() throws Exception {
        assertEquals(Optional.empty(), RiverBedMaterializationV2.freeze(
                List.of(), routing, reconciliation, landPlateau(), BOUNDS, NEVER));
    }

    @Test
    void aChannelOnACoastalModifiersCellIsRejected() {
        // The river carves the macro foundation only. A cell a coastal modifier owns keeps its own
        // surface contract, so the overlap is a declared conflict rather than a silent overwrite.
        RiverMaterializationRejectedV2 rejected = assertThrows(RiverMaterializationRejectedV2.class,
                () -> freeze(sampler(1, 7, LAND_SURFACE_Y)));

        assertEquals(RiverBedMaterializationV2.RULE_COASTAL_OWNER_CONFLICT, rejected.ruleId());
        assertEquals(plan.featureId(), rejected.featureId());
        assertTrue(rejected.getMessage().contains("coastal surface modifier"), rejected::getMessage);
    }

    @Test
    void aChannelInMarineWaterIsRejected() {
        // v1 declares an inland terminus: a mouth junction with the sea needs the delta/estuary leaf.
        RiverMaterializationRejectedV2 rejected = assertThrows(RiverMaterializationRejectedV2.class,
                () -> freeze(sampler(0, 0, LAND_SURFACE_Y)));

        assertEquals(RiverBedMaterializationV2.RULE_MARINE_CONTACT, rejected.ruleId());
    }

    @Test
    void aSurfaceBelowTheBedIsRejectedAsALeak() {
        // A "channel" sitting on top of the terrain would place water with nothing to hold it.
        RiverMaterializationRejectedV2 rejected = assertThrows(RiverMaterializationRejectedV2.class,
                () -> freeze(sampler(1, 0, MIN_Y + 2)));

        assertEquals(RiverBedMaterializationV2.RULE_LEAK_ENVELOPE, rejected.ruleId());
        assertTrue(rejected.getMessage().contains("not below the surface"), rejected::getMessage);
    }

    @Test
    void anEnvelopeStandingBelowTheWaterSurfaceIsRejectedAsALeak() {
        // The channel is cut correctly, but one off-channel neighbour column stops below the water
        // line: the reach would drain sideways into open air.
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
                    // x=27 is never a channel cell of this reach (half width tops out at 3 around
                    // x=23.5), so it is purely the envelope column next to the eastern bank.
                    return (globalX == 27 ? WATER_LEVEL - 4 : LAND_SURFACE_Y) * 1_000_000;
                }
                return CoastalTransitionModuleV2.LAND_WATER_FIELD_ID.equals(fieldId) ? 1 : 0;
            }
        };

        RiverMaterializationRejectedV2 rejected =
                assertThrows(RiverMaterializationRejectedV2.class, () -> freeze(fields));

        assertEquals(RiverBedMaterializationV2.RULE_LEAK_ENVELOPE, rejected.ruleId());
        assertTrue(rejected.getMessage().contains("below the water surface"), rejected::getMessage);
    }

    @Test
    void aReconciledBedThatMovedAwayFromThePlanIsRejected() {
        // The materialized bed must be the reconciled bed. Here the reconciliation artifact belongs
        // to a steeper profile of the same reach, so its REACH_BED values disagree with the plan the
        // raster would be cut from: the run fails closed instead of publishing a channel that does
        // not describe the frozen global route.
        MeanderingRiverPlanV2 steeper = compileReach(100_000);
        HydrologyReconciliationArtifactV2 steeperReconciliation = reconcile(steeper);

        RiverMaterializationRejectedV2 rejected = assertThrows(RiverMaterializationRejectedV2.class,
                () -> RiverBedMaterializationV2.freeze(
                        List.of(plan), routing, steeperReconciliation, landPlateau(), BOUNDS, NEVER));

        assertEquals(RiverBedMaterializationV2.RULE_ROUTE_NOT_FROZEN, rejected.ruleId());
        assertTrue(rejected.getMessage().contains("reconciliation moved"), rejected::getMessage);
        assertEquals(-1, rejected.cellX());
    }

    @Test
    void aRoutingResultOverAnotherDomainIsRejected() {
        HydrologyRoutingResultV2 other = solveRouting(WIDTH, LENGTH - 8);

        RiverMaterializationRejectedV2 rejected = assertThrows(RiverMaterializationRejectedV2.class,
                () -> RiverBedMaterializationV2.freeze(
                        List.of(plan), other, reconciliation, landPlateau(), BOUNDS, NEVER));

        assertEquals(RiverBedMaterializationV2.RULE_ROUTE_NOT_FROZEN, rejected.ruleId());
        assertTrue(rejected.getMessage().contains("export domain"), rejected::getMessage);
    }

    @Test
    void theDeclaredInteractionsAreAStableContract() {
        assertEquals(List.of(
                        "SOURCE_TERMINUS=v2.river.route-not-frozen",
                        "MOUTH_TERMINUS=v2.river.marine-contact",
                        "BANK_ENVELOPE=v2.river.leak-envelope",
                        "COASTAL_FOUNDATION=v2.river.coastal-owner-conflict",
                        "MACRO_MEDIUM=v2.river.marine-contact"),
                RiverBedMaterializationV2.declaredInteractions());
        assertEquals("river-bed-materialization-v1", RiverBedMaterializationV2.CONTRACT_VERSION);
        assertEquals(1_048_576L, RiverBedMaterializationV2.MAXIMUM_FOOTPRINT_CELLS);
    }

    @Test
    void theFreezeIsIndependentOfLocaleAndTimezone() throws Exception {
        RiverBedMaterializationV2.FrozenRiverBedV2 first = freeze(landPlateau()).orElseThrow();
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            RiverBedMaterializationV2.FrozenRiverBedV2 second = freeze(landPlateau()).orElseThrow();
            assertEquals(first.channelCells(), second.channelCells());
            assertEquals(first.bankCells(), second.bankCells());
            assertEquals(first.carvedBlocks(), second.carvedBlocks());
            assertEquals(first.filledBlocks(), second.filledBlocks());
            assertEquals(first.footprint(), second.footprint());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private Optional<RiverBedMaterializationV2.FrozenRiverBedV2> freeze(CoastalFieldSamplerV2 fields)
            throws RiverMaterializationRejectedV2 {
        return RiverBedMaterializationV2.freeze(
                List.of(plan), routing, reconciliation, fields, BOUNDS, NEVER);
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

    private static int channelCellX(RiverBedMaterializationV2.FrozenRiverBedV2 frozen) {
        return firstChannelCell(frozen)[0];
    }

    private static int channelCellZ(RiverBedMaterializationV2.FrozenRiverBedV2 frozen) {
        return firstChannelCell(frozen)[1];
    }

    private static int[] firstChannelCell(RiverBedMaterializationV2.FrozenRiverBedV2 frozen) {
        RiverBedMaterializationV2.Footprint footprint = frozen.footprint();
        for (int z = footprint.originZ(); z < footprint.originZ() + footprint.length(); z++) {
            for (int x = footprint.originX(); x < footprint.originX() + footprint.width(); x++) {
                if (frozen.channelAt(x, z) && frozen.bedYAt(x, z) < LAND_SURFACE_Y - 1) {
                    return new int[] {x, z};
                }
            }
        }
        throw new IllegalStateException("the frozen bed field carries no channel cell");
    }

    private static MeanderingRiverPlanV2 compileReach() {
        return compileReach(1_000);
    }

    private static MeanderingRiverPlanV2 compileReach(int minimumBedSlopeMillionths) {
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
                        minimumBedSlopeMillionths,
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

    private static HydrologyRoutingResultV2 solveRouting(int width, int length) {
        return new DeterministicHydrologyRoutingSolverV2().solve(
                HydrologyRoutingRequestV2.create(
                        width, length,
                        new HydrologyPlanCompilerV2().compile(
                                new WorldBlueprintV2.Bounds(width, length, MIN_Y, MAX_Y, WATER_LEVEL)),
                        ProvisionalSurfaceV2.routable((x, z) -> 54_000_000 - z * 1_000),
                        List.of(new HydrologyRoutingArtifactV2.Outlet(
                                "south-mouth", 0, length - 1,
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
        HydrologyReconciliationArtifactV2 artifact = new BoundedHydrologyReconcilerV2().reconcile(
                "0".repeat(64), reconciliationPlan, compiler.baselineState(reconciliationPlan), NEVER);
        assertNotNull(artifact);
        assertEquals(HydrologyReconciliationArtifactV2.Status.SATISFIED, artifact.status());
        assertFalse(artifact.finalValues().isEmpty());
        return artifact;
    }
}
