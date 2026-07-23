package com.github.nankotsu029.landformcraft.validation.v2.coast;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalRasterKernelV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.beach.SandyBeachGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater.BreakwaterHarborGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.cape.RockyCapeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.harbor.HarborBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionCompositorV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionLayerSourcesV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalTransitionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MetricResultV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoastalValidatorV2Test {
    private static final Path AZURE = Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");
    private static final int SCALE = 1_000_000;

    @Test
    void validatesFinalFieldsWithoutCallingGeneratorLocalMetrics() throws Exception {
        Fixture fixture = fixture();
        CoastalValidationReportV2 report = new CoastalValidatorV2().validate(
                new CoastalValidationInputV2(fixture.blueprint(), fixture.source(), fixture.source()), () -> false);

        assertTrue(report.passesHardValidation(), () -> report.issues().toString());
        assertTrue(report.metrics().stream().anyMatch(metric -> metric.metricId().equals("coastal.beach.width-p50")));
        assertTrue(report.metrics().stream().anyMatch(metric -> metric.metricId().equals("coastal.harbor.depth-p50")));
        assertTrue(report.metrics().stream().anyMatch(metric -> metric.metricId().equals("coastal.cape.rock-exposure")));
        assertTrue(report.metrics().stream().anyMatch(metric -> metric.metricId().equals("coastal.transition.conflict-cells")));
        // V2-18-05: the breakwater clear-opening metric is now measured from the field. Its actual value
        // is the realized crest-to-crest gap, which sits strictly above the plan's edge-to-edge width —
        // i.e. it is no longer the plan value compared against itself.
        MetricResultV2 opening = report.metrics().stream()
                .filter(metric -> metric.metricId().equals("coastal.breakwater.clear-opening"))
                .findFirst().orElseThrow();
        assertTrue(opening.actualMillionths() > opening.expected().minimumMillionths(),
                opening.actualMillionths() + " vs " + opening.expected().minimumMillionths());

        CoastalValidationReportV2 second = new CoastalValidatorV2().validate(
                new CoastalValidationInputV2(fixture.blueprint(), tiled(fixture.source(), 128), tiled(fixture.source(), 128)),
                () -> false);
        assertEquals(report, second);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(report, new CoastalValidatorV2().validate(
                    new CoastalValidationInputV2(fixture.blueprint(), fixture.source(), fixture.source()), () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousTimeZone);
        }
    }

    @Test
    void detectsWidthDepthConnectionAndCapeCorruptionFromFieldValues() throws Exception {
        Fixture fixture = fixture();
        assertIssue(fixture, replacing(fixture.source(), CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID,
                (value, x, z) -> value == CoastalValidationInputV2.NO_DATA ? value : 64 * SCALE), "coastal.beach.width");
        assertIssue(fixture, replacing(fixture.source(), CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID,
                (value, x, z) -> value == CoastalValidationInputV2.NO_DATA ? value : SCALE), "coastal.harbor.depth");
        assertIssue(fixture, replacing(fixture.source(), CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID,
                (value, x, z) -> fixture.source().valueAt(CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID, x, z) == 2 ? 0 : value),
                "v2.coastal.harbor-opening");
        assertIssue(fixture, replacing(fixture.source(), CoastalFoundationModuleV2.CAPE_ROCK_EXPOSURE_FIELD_ID,
                (value, x, z) -> 0), "coastal.cape.exposure");
        assertIssue(fixture, replacing(fixture.source(), CoastalFoundationModuleV2.CAPE_DESCRIPTOR_INDEX_FIELD_ID,
                (value, x, z) -> 0), "coastal.cape.complexity");
    }

    @Test
    void detectsTransitionConflictAndResidualWithoutGeneratorState() throws Exception {
        Fixture fixture = fixture();
        assertIssue(fixture, replacing(fixture.source(), CoastalTransitionModuleV2.CONFLICT_FIELD_ID,
                (value, x, z) -> x == 100 && z == 100 ? 1 : value), "coastal.transition.conflict");
        CoastalFieldSamplerV2 desired = replacing(fixture.source(), CoastalTransitionModuleV2.LAND_WATER_FIELD_ID,
                (value, x, z) -> x == 100 && z == 100 ? 1 - value : value);
        CoastalValidationReportV2 report = new CoastalValidatorV2().validate(
                new CoastalValidationInputV2(fixture.blueprint(), desired, fixture.source()), () -> false);
        assertTrue(report.issues().stream().anyMatch(issue -> issue.ruleId().equals("coastal.land-water.residual")));
    }

    @Test
    void detectsABlockedBreakwaterOpeningFromTheRegionField() throws Exception {
        Fixture fixture = fixture();
        // Making the whole region crest merges the two arms into a single connected crest structure,
        // so the two-component opening measurement collapses to zero and the metric fails — impossible
        // under the previous plan-versus-itself tautology, which always passed.
        int crest = BreakwaterHarborGeneratorV2.BreakwaterRegion.CREST.rawValue();
        assertIssue(fixture, replacing(fixture.source(), CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID,
                (value, x, z) -> crest), "coastal.breakwater.opening");
    }

    private static void assertIssue(Fixture fixture, CoastalFieldSamplerV2 actual, String ruleId) {
        CoastalValidationReportV2 report = new CoastalValidatorV2().validate(
                new CoastalValidationInputV2(fixture.blueprint(), fixture.source(), actual), () -> false);
        assertFalse(report.passesHardValidation());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.ruleId().equals(ruleId)), () -> report.issues().toString());
    }

    private static CoastalFieldSamplerV2 replacing(CoastalFieldSamplerV2 delegate, String fieldId, Mutation mutation) {
        return new CoastalFieldSamplerV2() {
            @Override public int width() { return delegate.width(); }
            @Override public int length() { return delegate.length(); }
            @Override public int valueAt(String requestedField, int x, int z) {
                int original = delegate.valueAt(requestedField, x, z);
                return requestedField.equals(fieldId) ? mutation.apply(original, x, z) : original;
            }
        };
    }

    private static CoastalFieldSamplerV2 tiled(CoastalFieldSamplerV2 delegate, int tileSize) {
        return new CoastalFieldSamplerV2() {
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

    private static Fixture fixture() throws IOException {
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(AZURE);
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(new DiagnosticCompileRequestV2(
                intent.intentId(), new GenerationBounds(400, 400, -64, 255, 50), 128, 827413L,
                "a".repeat(64), DiagnosticCompileRequestV2.defaultBudget()), intent);
        CoastalTransitionPlanV2 plan = blueprint.coastalTransitionPlans().getFirst();
        List<CoastalTransitionCompositorV2.LayerBinding> bindings = new ArrayList<>();
        SandyBeachGeneratorV2[] beach = new SandyBeachGeneratorV2[1];
        HarborBasinGeneratorV2[] harbor = new HarborBasinGeneratorV2[1];
        BreakwaterHarborGeneratorV2[] breakwater = new BreakwaterHarborGeneratorV2[1];
        RockyCapeGeneratorV2[] cape = new RockyCapeGeneratorV2[1];
        for (CoastalTransitionPlanV2.Contributor contributor : plan.contributors()) {
            CoastalFeaturePlanV2 coastal = blueprint.coastalFeaturePlans().stream()
                    .filter(value -> value.featureId().equals(contributor.featureId())).findFirst().orElseThrow();
            switch (contributor.kind()) {
                case SANDY_BEACH -> {
                    beach[0] = new SandyBeachGeneratorV2(blueprint.sandyBeachPlans().getFirst(),
                            new CoastalRasterKernelV2(coastal, 400, 400));
                    bindings.add(CoastalTransitionLayerSourcesV2.beach(contributor, beach[0], HardLandWaterSourceV2.NONE));
                }
                case HARBOR_BASIN -> {
                    harbor[0] = new HarborBasinGeneratorV2(blueprint.harborBasinPlans().getFirst(), coastal, 400, 400);
                    bindings.add(CoastalTransitionLayerSourcesV2.harbor(contributor, harbor[0], HardLandWaterSourceV2.NONE));
                }
                case BREAKWATER_HARBOR -> {
                    breakwater[0] = new BreakwaterHarborGeneratorV2(blueprint.breakwaterHarborPlans().getFirst(), coastal, 400, 400);
                    bindings.add(CoastalTransitionLayerSourcesV2.breakwater(contributor, breakwater[0]));
                }
                case ROCKY_CAPE -> {
                    cape[0] = new RockyCapeGeneratorV2(blueprint.rockyCapePlans().getFirst(), coastal, 400, 400);
                    bindings.add(CoastalTransitionLayerSourcesV2.cape(contributor, cape[0], HardLandWaterSourceV2.NONE));
                }
                default -> throw new AssertionError("unexpected coastal feature");
            }
        }
        CoastalTransitionCompositorV2 compositor = new CoastalTransitionCompositorV2(plan, 400, 400, bindings);
        return new Fixture(blueprint, sampler(beach[0], harbor[0], breakwater[0], cape[0], compositor));
    }

    private static CoastalFieldSamplerV2 sampler(
            SandyBeachGeneratorV2 beach,
            HarborBasinGeneratorV2 harbor,
            BreakwaterHarborGeneratorV2 breakwater,
            RockyCapeGeneratorV2 cape,
            CoastalTransitionCompositorV2 compositor
    ) {
        return new CoastalFieldSamplerV2() {
            @Override public int width() { return 400; }
            @Override public int length() { return 400; }
            @Override public int valueAt(String field, int x, int z) {
                return switch (field) {
                    case CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID -> beach.sampleAt(x, z, HardLandWaterSourceV2.NONE).localWidthMillionths();
                    case CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID -> beach.sampleAt(x, z, HardLandWaterSourceV2.NONE).band().rawValue();
                    case CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID -> harbor.sampleAt(x, z, HardLandWaterSourceV2.NONE).region().rawValue();
                    case CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID -> harbor.sampleAt(x, z, HardLandWaterSourceV2.NONE).water();
                    case CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID -> harbor.sampleAt(x, z, HardLandWaterSourceV2.NONE).depthMillionths();
                    case CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID -> breakwater.sampleAt(x, z).region().rawValue();
                    case CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID -> cape.sampleAt(x, z, HardLandWaterSourceV2.NONE).region().rawValue();
                    case CoastalFoundationModuleV2.CAPE_ROCK_EXPOSURE_FIELD_ID -> cape.sampleAt(x, z, HardLandWaterSourceV2.NONE).rockExposure();
                    case CoastalFoundationModuleV2.CAPE_DESCRIPTOR_INDEX_FIELD_ID -> cape.sampleAt(x, z, HardLandWaterSourceV2.NONE).descriptorIndex();
                    case CoastalTransitionModuleV2.LAND_WATER_FIELD_ID -> compositor.sampleAt(x, z, HardLandWaterSourceV2.NONE).landWater();
                    case CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID -> compositor.sampleAt(x, z, HardLandWaterSourceV2.NONE).surfaceHeightMillionths();
                    case CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID -> compositor.sampleAt(x, z, HardLandWaterSourceV2.NONE).ownerIndex();
                    case CoastalTransitionModuleV2.CONFLICT_FIELD_ID -> compositor.sampleAt(x, z, HardLandWaterSourceV2.NONE).conflict();
                    default -> CoastalValidationInputV2.NO_DATA;
                };
            }
        };
    }

    private record Fixture(WorldBlueprintV2 blueprint, CoastalFieldSamplerV2 source) { }
    @FunctionalInterface private interface Mutation { int apply(int value, int x, int z); }
}
