package com.github.nankotsu029.landformcraft.validation.v2.hydrology;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta.DeltaGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.LakeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.TidalChannelGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.WaterfallGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.FjordGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mountain.MountainGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic.VolcanicGeneratorV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.DeltaPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.FjordPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MountainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TidalChannelPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WaterfallPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyFlowDirectionV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HydrologyValidatorV2Test {
    private static final Path RIVER = Path.of("examples/v2/hydrology/meandering-river.terrain-intent-v2.json");
    private static final Path DELTA = Path.of("examples/v2/hydrology/delta-distributary-fan.terrain-intent-v2.json");
    private static final Path TIDAL = Path.of("examples/v2/hydrology/tidal-channel-network.terrain-intent-v2.json");
    private static final Path FJORD = Path.of("examples/v2/hydrology/fjord-glacial-u.terrain-intent-v2.json");
    private static final Path WATERFALL = Path.of("examples/v2/hydrology/waterfall-2_5d-skeleton.terrain-intent-v2.json");
    private static final Path MOUNTAIN = Path.of("examples/v2/hydrology/alpine-ridge-skeleton.terrain-intent-v2.json");
    private static final IntPredicate NONE = index -> false;

    @Test
    void validatesRiverFieldsWithoutCallingGeneratorLocalMetrics() throws Exception {
        Fixture fixture = riverFixture(129, 193);
        HydrologyValidationReportV2 report = new HydrologyValidatorV2().validate(
                new HydrologyValidationInputV2(fixture.blueprint(), fixture.fields()), () -> false);
        assertTrue(report.passesHardValidation(), () -> report.issues().toString());
        assertTrue(report.metrics().stream().anyMatch(metric ->
                metric.metricId().equals("hydrology.river.source-mouth-reachable")));
        assertTrue(fixture.blueprint().validationTargets().stream().anyMatch(target ->
                target.ruleId().equals("hydrology.river.reachability")));

        HydrologyValidationReportV2 tiled = new HydrologyValidatorV2().validate(
                new HydrologyValidationInputV2(fixture.blueprint(), tiled(fixture.fields(), 64)), () -> false);
        assertEquals(report, tiled);

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(report, new HydrologyValidatorV2().validate(
                    new HydrologyValidationInputV2(fixture.blueprint(), fixture.fields()), () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void detectsIsolatedReachReverseGradientAndFlowCycleFromFieldValues() throws Exception {
        Fixture fixture = riverFixture(129, 193);
        MeanderingRiverPlanV2 plan = fixture.blueprint().meanderingRiverPlans().getFirst();
        MeanderingRiverPlanV2.CenterlineSample mid = plan.centerline().get(plan.centerline().size() / 2);
        int midX = Math.toIntExact(mid.xMillionths() / 1_000_000);
        int midZ = Math.toIntExact(mid.zMillionths() / 1_000_000);
        assertIssue(fixture, replacing(fixture.fields(), plan.channelMaskFieldId(),
                (value, x, z) -> x == midX && z == midZ ? 0 : value), "hydrology.river.isolated-reach");
        assertIssue(fixture, replacing(fixture.fields(), plan.bedElevationFieldId(),
                (value, x, z) -> {
                    if (value == HydrologyValidationInputV2.NO_DATA) return value;
                    long arc = 0;
                    for (MeanderingRiverPlanV2.CenterlineSample sample : plan.centerline()) {
                        if (Math.toIntExact(sample.xMillionths() / 1_000_000) == x
                                && Math.toIntExact(sample.zMillionths() / 1_000_000) == z) {
                            arc = sample.arcLengthMillionths();
                            break;
                        }
                    }
                    return Math.toIntExact(plan.sourceBedYMillionths() + arc);
                }), "hydrology.river.reverse-gradient");
        assertIssue(fixture, replacing(fixture.fields(), HydrologyIrModuleV2.FLOW_DIRECTION_FIELD,
                (value, x, z) -> {
                    if (x == 10 && z == 10) return HydrologyFlowDirectionV2.EAST.code();
                    if (x == 11 && z == 10) return HydrologyFlowDirectionV2.WEST.code();
                    return HydrologyValidationInputV2.NO_DATA;
                }), "hydrology.flow.cycle");
    }

    @Test
    void detectsLeakingLakeDeadDeltaBrokenFjordAndFallMismatch() throws Exception {
        Fixture lake = lakeFixture();
        LakePlanV2 lakePlan = lake.blueprint().lakePlans().getFirst();
        assertIssue(lake, replacing(lake.fields(), lakePlan.spillwayMaskFieldId(), (value, x, z) -> 0),
                "hydrology.lake.leaking");

        Fixture delta = deltaFixture();
        DeltaPlanV2 deltaPlan = delta.blueprint().deltaPlans().getFirst();
        // mouth connection also consults shallow-sea depth; clear both for an isolated distributary.
        HydrologyFieldSamplerV2 deadDelta = replacing(
                replacing(delta.fields(), deltaPlan.channelMaskFieldId(), (value, x, z) -> 0),
                deltaPlan.shallowSeaDepthFieldId(), (value, x, z) -> 0);
        assertIssue(delta, deadDelta, "hydrology.delta.dead-branch");

        Fixture fjord = fjordFixture();
        FjordPlanV2 fjordPlan = fjord.blueprint().fjordPlans().getFirst();
        assertIssue(fjord, replacing(fjord.fields(), fjordPlan.channelMaskFieldId(), (value, x, z) -> 0),
                "hydrology.fjord.broken-outlet");

        Fixture waterfall = waterfallFixture();
        WaterfallPlanV2 waterfallPlan = waterfall.blueprint().waterfallPlans().getFirst();
        assertIssue(waterfall, replacing(waterfall.fields(), waterfallPlan.lipElevationFieldId(),
                (value, x, z) -> waterfall.fields().valueAt(waterfallPlan.baseElevationFieldId(), x, z)),
                "hydrology.waterfall.fall-mismatch");
    }

    @Test
    void detectsTidalMarineMountainVolcanicAndReconciliationCorruption() throws Exception {
        Fixture tidal = tidalFixture();
        TidalChannelPlanV2 tidalPlan = tidal.blueprint().tidalChannelPlans().getFirst();
        assertIssue(tidal, replacing(tidal.fields(), tidalPlan.marineConnectionFieldId(), (value, x, z) -> 0),
                "hydrology.tidal.marine-connection");

        Fixture mountain = mountainFixture();
        MountainPlanV2 mountainPlan = mountain.blueprint().mountainPlans().getFirst();
        assertIssue(mountain, replacing(mountain.fields(), mountainPlan.ridgeMaskFieldId(), (value, x, z) -> 0),
                "hydrology.mountain.ridge");

        Fixture volcanic = volcanicFixture();
        VolcanicPlanV2 volcanicPlan = volcanic.blueprint().volcanicPlans().getFirst();
        assertIssue(volcanic, replacing(volcanic.fields(), volcanicPlan.islandMaskFieldId(), (value, x, z) -> 0),
                "hydrology.volcanic.island");

        Fixture river = riverFixture(97, 129);
        HydrologyReconciliationArtifactV2 failed = new HydrologyReconciliationArtifactCodecV2().read(
                Path.of("examples/v2/hydrology/hydrology-reconciliation-artifact-v2.json"));
        HydrologyValidationReportV2 report = new HydrologyValidatorV2().validate(
                new HydrologyValidationInputV2(river.blueprint(), river.fields(), failed), () -> false);
        assertTrue(report.issues().stream().anyMatch(issue ->
                issue.ruleId().equals("hydrology.reconcile.residual")
                        || issue.ruleId().equals("hydrology.reconcile.status")),
                () -> report.issues().toString());
    }

    private static void assertIssue(Fixture fixture, HydrologyFieldSamplerV2 actual, String ruleId) {
        HydrologyValidationReportV2 report = new HydrologyValidatorV2().validate(
                new HydrologyValidationInputV2(fixture.blueprint(), actual), () -> false);
        assertFalse(report.passesHardValidation());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.ruleId().equals(ruleId)),
                () -> report.issues().toString());
    }

    private static HydrologyFieldSamplerV2 replacing(
            HydrologyFieldSamplerV2 delegate, String fieldId, Mutation mutation
    ) {
        return new HydrologyFieldSamplerV2() {
            @Override public int width() { return delegate.width(); }
            @Override public int length() { return delegate.length(); }
            @Override public int valueAt(String requestedField, int x, int z) {
                int original = delegate.valueAt(requestedField, x, z);
                return requestedField.equals(fieldId) ? mutation.apply(original, x, z) : original;
            }
        };
    }

    private static HydrologyFieldSamplerV2 tiled(HydrologyFieldSamplerV2 delegate, int tileSize) {
        return new HydrologyFieldSamplerV2() {
            @Override public int width() { return delegate.width(); }
            @Override public int length() { return delegate.length(); }
            @Override public int valueAt(String fieldId, int x, int z) {
                int tileX = x / tileSize;
                int tileZ = z / tileSize;
                int localX = x - tileX * tileSize;
                int localZ = z - tileZ * tileSize;
                return delegate.valueAt(fieldId, tileX * tileSize + localX, tileZ * tileSize + localZ);
            }
        };
    }

    private static Fixture riverFixture(int width, int length) throws IOException {
        WorldBlueprintV2 blueprint = compile(RIVER, width, length);
        MeanderingRiverPlanV2 plan = blueprint.meanderingRiverPlans().getFirst();
        MeanderingRiverGeneratorV2 generator = new MeanderingRiverGeneratorV2(plan);
        return new Fixture(blueprint, (field, x, z) -> sampleRiver(generator, field, x, z));
    }

    private static Fixture lakeFixture() throws IOException {
        TerrainIntentV2.Feature lake = new TerrainIntentV2.Feature(
                "basin-lake",
                TerrainIntentV2.FeatureKind.LAKE,
                new TerrainIntentV2.PolygonGeometry(List.of(List.of(
                        new TerrainIntentV2.Point2(250_000, 250_000),
                        new TerrainIntentV2.Point2(750_000, 250_000),
                        new TerrainIntentV2.Point2(750_000, 750_000),
                        new TerrainIntentV2.Point2(250_000, 750_000),
                        new TerrainIntentV2.Point2(250_000, 250_000)))),
                new TerrainIntentV2.LakeParameters(
                        new TerrainIntentV2.IntRange(4, 8),
                        2,
                        TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL,
                        TerrainIntentV2.LakeSpillSelection.DECLARED_EDGE,
                        0,
                        4,
                        6,
                        TerrainIntentV2.LakeFloorProfile.EDGE_TO_CENTER_LINEAR),
                0,
                TerrainIntentV2.Provenance.confirmedManual("lake-validation"));
        WorldBlueprintV2 blueprint = compileIntent("open-spill-lake-validation", "lake", List.of(lake), List.of(), 97, 97);
        LakePlanV2 plan = blueprint.lakePlans().getFirst();
        LakeGeneratorV2 generator = new LakeGeneratorV2(plan);
        return new Fixture(blueprint, (field, x, z) -> {
            var sample = generator.sampleAt(x, z, NONE);
            if (field.equals(plan.basinMaskFieldId())) return sample.basinMask();
            if (field.equals(plan.rimMaskFieldId())) return sample.rimMask();
            if (field.equals(plan.spillwayMaskFieldId())) return sample.spillwayMask();
            if (field.equals(plan.depthFieldId())) return sample.depthMillionths();
            if (field.equals(plan.floorHeightFieldId())) return sample.floorHeightMillionths();
            if (field.equals(plan.surfaceFieldId())) return sample.surfaceMillionths();
            return HydrologyValidationInputV2.NO_DATA;
        });
    }

    private static Fixture deltaFixture() throws IOException {
        WorldBlueprintV2 blueprint = compile(DELTA, 160, 160);
        DeltaPlanV2 plan = blueprint.deltaPlans().getFirst();
        DeltaGeneratorV2 generator = new DeltaGeneratorV2(plan);
        MeanderingRiverGeneratorV2 river = new MeanderingRiverGeneratorV2(blueprint.meanderingRiverPlans().getFirst());
        return new Fixture(blueprint, (field, x, z) -> {
            int riverValue = sampleRiver(river, field, x, z);
            if (riverValue != HydrologyValidationInputV2.NO_DATA) return riverValue;
            var sample = generator.sampleAt(x, z, NONE);
            if (field.equals(plan.channelMaskFieldId())) return sample.channelMask();
            if (field.equals(plan.branchIndexFieldId())) return sample.branchIndex();
            if (field.equals(plan.shallowSeaDepthFieldId())) return sample.shallowSeaDepthMillionths();
            if (field.equals(plan.fanMaskFieldId())) return sample.fanMask();
            return HydrologyValidationInputV2.NO_DATA;
        });
    }

    private static Fixture tidalFixture() throws IOException {
        WorldBlueprintV2 blueprint = compile(TIDAL, 128, 128);
        TidalChannelPlanV2 plan = blueprint.tidalChannelPlans().getFirst();
        TidalChannelGeneratorV2 generator = new TidalChannelGeneratorV2(plan);
        return new Fixture(blueprint, (field, x, z) -> {
            var sample = generator.sampleAt(x, z, NONE);
            if (field.equals(plan.channelMaskFieldId())) return sample.channelMask();
            if (field.equals(plan.marineConnectionFieldId())) return sample.marineConnection();
            if (field.equals(plan.branchIndexFieldId())) return sample.branchIndex();
            return HydrologyValidationInputV2.NO_DATA;
        });
    }

    private static Fixture fjordFixture() throws IOException {
        WorldBlueprintV2 blueprint = compile(FJORD, 160, 192);
        FjordPlanV2 plan = blueprint.fjordPlans().getFirst();
        FjordGeneratorV2 generator = new FjordGeneratorV2(plan);
        return new Fixture(blueprint, (field, x, z) -> {
            var sample = generator.sampleAt(x, z, NONE);
            if (field.equals(plan.channelMaskFieldId())) return sample.channelMask();
            if (field.equals(plan.thalwegDepthFieldId())) return sample.thalwegDepthMillionths();
            if (field.equals(plan.floorMaskFieldId())) return sample.floorMask();
            return HydrologyValidationInputV2.NO_DATA;
        });
    }

    private static Fixture waterfallFixture() throws IOException {
        WorldBlueprintV2 blueprint = compile(WATERFALL, 129, 193);
        WaterfallPlanV2 plan = blueprint.waterfallPlans().getFirst();
        WaterfallGeneratorV2 generator = new WaterfallGeneratorV2(plan);
        MeanderingRiverGeneratorV2 river = new MeanderingRiverGeneratorV2(blueprint.meanderingRiverPlans().getFirst());
        return new Fixture(blueprint, (field, x, z) -> {
            int riverValue = sampleRiver(river, field, x, z);
            if (riverValue != HydrologyValidationInputV2.NO_DATA) return riverValue;
            var sample = generator.sampleAt(x, z, NONE);
            if (field.equals(plan.lipMaskFieldId())) return sample.lipMask();
            if (field.equals(plan.baseMaskFieldId())) return sample.baseMask();
            if (field.equals(plan.plungePoolMaskFieldId())) return sample.plungePoolMask();
            if (field.equals(plan.lipElevationFieldId())) return sample.lipElevation();
            if (field.equals(plan.baseElevationFieldId())) return sample.baseElevation();
            return HydrologyValidationInputV2.NO_DATA;
        });
    }

    private static Fixture mountainFixture() throws IOException {
        WorldBlueprintV2 blueprint = compile(MOUNTAIN, 160, 160);
        MountainPlanV2 plan = blueprint.mountainPlans().getFirst();
        MountainGeneratorV2 generator = new MountainGeneratorV2(plan);
        return new Fixture(blueprint, (field, x, z) -> {
            var sample = generator.sampleAt(x, z, NONE);
            if (field.equals(plan.ridgeMaskFieldId())) return sample.ridgeMask();
            if (field.equals(plan.peakMaskFieldId())) return sample.peakMask();
            return HydrologyValidationInputV2.NO_DATA;
        });
    }

    private static Fixture volcanicFixture() throws IOException {
        TerrainIntentV2.Feature archipelago = new TerrainIntentV2.Feature(
                "island-arc",
                TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO,
                new TerrainIntentV2.MultiPointGeometry(List.of(
                        new TerrainIntentV2.NamedPoint("west-island", new TerrainIntentV2.Point2(150_000, 520_000)),
                        new TerrainIntentV2.NamedPoint("main-island", new TerrainIntentV2.Point2(500_000, 420_000)),
                        new TerrainIntentV2.NamedPoint("east-island", new TerrainIntentV2.Point2(850_000, 540_000)))),
                new TerrainIntentV2.VolcanicArchipelagoParameters(
                        List.of(
                                new TerrainIntentV2.IslandSpec("west-island", 28, 44),
                                new TerrainIntentV2.IslandSpec("main-island", 48, 96),
                                new TerrainIntentV2.IslandSpec("east-island", 26, 44)),
                        new TerrainIntentV2.IntRange(10, 16)),
                0,
                TerrainIntentV2.Provenance.confirmedManual("volcanic-validation"));
        WorldBlueprintV2 blueprint = compileIntent(
                "volcanic-validation", "volcanic", List.of(archipelago), List.of(), 257, 257);
        VolcanicPlanV2 plan = blueprint.volcanicPlans().getFirst();
        VolcanicGeneratorV2 generator = new VolcanicGeneratorV2(plan);
        return new Fixture(blueprint, (field, x, z) -> {
            var sample = generator.sampleAt(x, z, NONE);
            if (field.equals(plan.islandMaskFieldId())) return sample.islandMask();
            if (field.equals(plan.islandIndexFieldId())) return sample.islandIndex();
            return HydrologyValidationInputV2.NO_DATA;
        });
    }

    private static int sampleRiver(MeanderingRiverGeneratorV2 generator, String field, int x, int z) {
        var sample = generator.sampleAt(x, z, NONE);
        MeanderingRiverPlanV2 plan = generator.plan();
        if (field.equals(plan.channelMaskFieldId())) return sample.channelMask();
        if (field.equals(plan.bedElevationFieldId())) return sample.bedElevationMillionths();
        if (field.equals(plan.bankMaskFieldId())) return sample.bankMask();
        if (field.equals(plan.waterSurfaceFieldId())) return sample.waterSurfaceMillionths();
        if (field.equals(HydrologyIrModuleV2.FLOW_DIRECTION_FIELD)) return HydrologyValidationInputV2.NO_DATA;
        return HydrologyValidationInputV2.NO_DATA;
    }

    private static WorldBlueprintV2 compile(Path intentPath, int width, int length) throws IOException {
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(intentPath);
        return compileReady(intent, width, length);
    }

    private static WorldBlueprintV2 compileIntent(
            String intentId,
            String theme,
            List<TerrainIntentV2.Feature> features,
            List<TerrainIntentV2.Relation> relations,
            int width,
            int length
    ) {
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                intentId,
                theme,
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                features,
                relations,
                List.of(),
                new TerrainIntentV2.EnvironmentDescriptor("ALLUVIAL_SEDIMENT", "TEMPERATE_HUMID", "RIVER_CORRIDOR"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("hydrology-validation"));
        return compileReady(intent, width, length);
    }

    private static WorldBlueprintV2 compileReady(TerrainIntentV2 intent, int width, int length) {
        return new DiagnosticBlueprintCompilerV2().compile(new DiagnosticCompileRequestV2(
                intent.intentId(), new GenerationBounds(width, length, -64, 255, 50), 64, 827413L,
                "a".repeat(64), DiagnosticCompileRequestV2.defaultBudget()), intent);
    }

    private record Fixture(WorldBlueprintV2 blueprint, HydrologyFieldSamplerV2 fields) {
        Fixture(WorldBlueprintV2 blueprint, FieldFn fieldFn) {
            this(blueprint, new HydrologyFieldSamplerV2() {
                @Override public int width() { return blueprint.space().bounds().width(); }
                @Override public int length() { return blueprint.space().bounds().length(); }
                @Override public int valueAt(String fieldId, int globalX, int globalZ) {
                    return fieldFn.valueAt(fieldId, globalX, globalZ);
                }
            });
        }
    }

    @FunctionalInterface private interface Mutation { int apply(int value, int x, int z); }
    @FunctionalInterface private interface FieldFn { int valueAt(String fieldId, int x, int z); }
}
