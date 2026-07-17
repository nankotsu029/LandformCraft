package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.harbor.HarborBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater.BreakwaterHarborGeneratorV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.BreakwaterHarborPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.SandyBeachPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.HarborBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoastalFoundationCompilerV2Test {
    private static final Path COASTAL = Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");
    private static final Path ENDPOINT_CORRUPTION = Path.of(
            "examples/v2/diagnostic/negative/sandy-beach-endpoint-corruption.terrain-intent-v2.json");
    private static final Path CLOSED_HARBOR = Path.of(
            "examples/v2/diagnostic/negative/harbor-basin-closed-entrance.terrain-intent-v2.json");
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void compilesFourCanonicalCoastalPlansAndAnExplicitFieldDag() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        WorldBlueprintV2 blueprint = compile(intent, DiagnosticCompileRequestV2.defaultBudget());

        assertEquals(List.of("central-breakwater", "east-cape", "harbor-basin", "main-beach"),
                blueprint.coastalFeaturePlans().stream().map(CoastalFeaturePlanV2::featureId).toList());
        assertEquals(4, blueprint.coastalFeaturePlans().size());
        assertEquals(64, blueprint.modules().stream()
                .filter(item -> item.moduleId().equals(CoastalFoundationModuleV2.MODULE_ID))
                .findFirst().orElseThrow().requiredHaloXZ());
        assertEquals(256, blueprint.space().tilePolicy().maximumHaloXZ());
        assertEquals(0, blueprint.space().tilePolicy().maximumHaloY());

        ModuleDescriptorV2 module = blueprint.modules().stream()
                .filter(item -> item.moduleId().equals(CoastalFoundationModuleV2.MODULE_ID))
                .findFirst().orElseThrow();
        assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED, module.lifecycleStatus());
        assertEquals(CoastalFoundationModuleV2.STAGE_ID, module.stageId());
        assertTrue(module.fieldWrites().stream().allMatch(
                write -> write.mergeOperator() == ModuleDescriptorV2.MergeOperator.SINGLE_OWNER));
        assertEquals(List.of("compile.inputs"), blueprint.stages().stream()
                .filter(stage -> stage.stageId().equals(CoastalFoundationModuleV2.STAGE_ID))
                .findFirst().orElseThrow().dependsOnStageIds());

        CoastalFeaturePlanV2 beach = blueprint.coastalFeaturePlans().stream()
                .filter(plan -> plan.kind() == TerrainIntentV2.FeatureKind.SANDY_BEACH)
                .findFirst().orElseThrow();
        assertEquals(CoastalFeaturePlanV2.CoastSide.LAND_LEFT, beach.coastSide());
        assertEquals(CoastalFeaturePlanV2.DistanceSign.POSITIVE_ON_LAND_SIDE,
                beach.signedDistance().sign());
        assertEquals(40, beach.nearshoreProfile().distanceBlocks());
        assertEquals(6, beach.nearshoreProfile().targetDepthBlocks());
        assertEquals(new CoastalFeaturePlanV2.BlockPoint(7_980_000L, 167_580_000L),
                beach.geometry().paths().getFirst().points().getFirst());
        assertTrue(blueprint.fields().stream()
                .filter(field -> field.fieldId().startsWith("coastal."))
                .allMatch(field -> field.storage() == WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY));
        assertEquals(List.of(
                        "coastal.actual-land-water", "coastal.beach.band", "coastal.beach.local-width",
                        "coastal.beach.semantic-sand", "coastal.beach.surface-height",
                        "coastal.breakwater.arm-index", "coastal.breakwater.bottom-height",
                        "coastal.breakwater.region", "coastal.breakwater.top-height",
                        "coastal.cape.descriptor-index", "coastal.cape.region",
                        "coastal.cape.rock-exposure", "coastal.cape.surface-height",
                        "coastal.coast-side",
                        "coastal.composed.blend-weight", "coastal.composed.conflict",
                        "coastal.composed.land-water", "coastal.composed.owner-index",
                        "coastal.composed.surface-height",
                        "coastal.harbor-basin.bottom-height", "coastal.harbor-basin.region",
                        "coastal.harbor-basin.water", "coastal.harbor-basin.water-depth",
                        "coastal.nearshore-profile", "coastal.normal-x", "coastal.normal-z",
                        "coastal.signed-distance"),
                blueprint.fields().stream().map(WorldBlueprintV2.FieldDescriptor::fieldId)
                        .filter(fieldId -> fieldId.startsWith("coastal.")).toList());
        SandyBeachPlanV2 beachPlan = blueprint.sandyBeachPlans().getFirst();
        assertEquals("main-beach", beachPlan.featureId());
        assertEquals(SandyBeachPlanV2.WidthProfileKind.ENDPOINT_TAPER, beachPlan.widthProfileKind());
        assertEquals(20, beachPlan.minimumWidthBlocks());
        assertEquals(55, beachPlan.maximumWidthBlocks());
        assertEquals(64, beachPlan.endpointTaperBlocks());
        assertEquals(600_000, beachPlan.foreshoreShareMillionths());
        assertEquals(5_000_000L, beachPlan.selectedShoreSlopeDegreesMillionths());
        HarborBasinPlanV2 harborPlan = blueprint.harborBasinPlans().getFirst();
        assertEquals("harbor-basin", harborPlan.featureId());
        assertEquals(HarborBasinPlanV2.BottomProfileKind.EDGE_TO_CENTER_LINEAR,
                harborPlan.bottomProfileKind());
        assertEquals(8, harborPlan.minimumDepthBlocks());
        assertEquals(10, harborPlan.maximumDepthBlocks());
        assertEquals(8, harborPlan.profileTransitionBlocks());
        assertEquals(24, harborPlan.entranceCorridorLengthBlocks());
        assertEquals(List.of("east-opening", "west-opening"), harborPlan.entranceEndpointIds());
        CoastalFeaturePlanV2 harborCoast = blueprint.coastalFeaturePlans().stream()
                .filter(plan -> plan.featureId().equals(harborPlan.featureId())).findFirst().orElseThrow();
        HarborBasinGeneratorV2.HarborMetrics harborMetrics =
                new HarborBasinGeneratorV2(harborPlan, harborCoast, 400, 400).evaluate(() -> false);
        assertTrue(harborMetrics.navigableDepthP50Blocks() >= 8
                && harborMetrics.navigableDepthP50Blocks() <= 10);
        BreakwaterHarborPlanV2 breakwaterPlan = blueprint.breakwaterHarborPlans().getFirst();
        assertEquals("central-breakwater", breakwaterPlan.featureId());
        assertEquals("harbor-basin", breakwaterPlan.basinFeatureId());
        assertEquals("breakwater-encloses-basin", breakwaterPlan.enclosureRelationId());
        assertEquals(List.of("east-arm", "west-arm"), breakwaterPlan.arms().stream()
                .map(BreakwaterHarborPlanV2.ArmPlan::armId).toList());
        assertEquals(List.of(1, 2), breakwaterPlan.arms().stream()
                .map(BreakwaterHarborPlanV2.ArmPlan::armOrder).toList());
        assertEquals(25, breakwaterPlan.supportRadiusXZ());
        assertTrue(Math.abs(breakwaterPlan.actualClearOpeningWidthMillionths() - 28_000_000L) <= 500_000L);
        CoastalFeaturePlanV2 breakwaterCoast = blueprint.coastalFeaturePlans().stream()
                .filter(plan -> plan.featureId().equals(breakwaterPlan.featureId())).findFirst().orElseThrow();
        BreakwaterHarborGeneratorV2.BreakwaterMetrics breakwaterMetrics =
                new BreakwaterHarborGeneratorV2(breakwaterPlan, breakwaterCoast, 400, 400)
                        .evaluate(() -> false);
        assertEquals(2, breakwaterMetrics.armLengthsMillionths().size());
        assertTrue(breakwaterMetrics.crestCells() > 0
                && breakwaterMetrics.innerFoundationCells() > 0
                && breakwaterMetrics.outerFoundationCells() > 0);
        assertTrue(breakwaterMetrics.solidBlocks() <= BreakwaterHarborGeneratorV2.MAXIMUM_SOLID_BLOCKS);

        String canonical = codec.canonicalWorldBlueprint(blueprint);
        assertEquals(blueprint, codec.readWorldBlueprint(canonical, "coastal-round-trip"));
    }

    @Test
    void canonicalPlanAndNamedSeedsIgnoreLocaleTimezoneAndWorkerCount() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        WorldBlueprintV2 baseline = compile(intent, DiagnosticCompileRequestV2.defaultBudget());
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            WorldBlueprintV2 changedDefaults = compile(intent, DiagnosticCompileRequestV2.defaultBudget());
            assertEquals(baseline.canonicalChecksum(), changedDefaults.canonicalChecksum());
            assertEquals(baseline.featurePlans().stream().map(WorldBlueprintV2.FeaturePlan::namedSeed).toList(),
                    changedDefaults.featurePlans().stream().map(WorldBlueprintV2.FeaturePlan::namedSeed).toList());

            try (var executor = Executors.newFixedThreadPool(4)) {
                List<String> checksums = executor.invokeAll(List.<java.util.concurrent.Callable<String>>of(
                                () -> compile(intent, DiagnosticCompileRequestV2.defaultBudget()).canonicalChecksum(),
                                () -> compile(intent, DiagnosticCompileRequestV2.defaultBudget()).canonicalChecksum(),
                                () -> compile(intent, DiagnosticCompileRequestV2.defaultBudget()).canonicalChecksum(),
                                () -> compile(intent, DiagnosticCompileRequestV2.defaultBudget()).canonicalChecksum()))
                        .stream().map(future -> {
                            try {
                                return future.get();
                            } catch (Exception exception) {
                                throw new AssertionError(exception);
                            }
                        }).toList();
                assertEquals(List.of(
                        baseline.canonicalChecksum(), baseline.canonicalChecksum(),
                        baseline.canonicalChecksum(), baseline.canonicalChecksum()), checksums);
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
        assertNotEquals(baseline.featurePlans().getFirst().namedSeed(),
                baseline.featurePlans().getLast().namedSeed());
    }

    @Test
    void rejectsMissingSideUnsupportedRelationSelfIntersectionAndOutOfRangePoint() throws IOException {
        String valid = Files.readString(COASTAL);
        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace(",\n        \"landSide\": \"LEFT\"", ""), "missing-land-side"));

        String unsupportedRelation = valid.replace(
                "\"kind\": \"ADJACENT_TO\", \"from\": \"feature:central-breakwater\", \"to\": \"feature:main-beach\", \"strength\": \"HARD\", \"transition\": { \"transitionVersion\": 1, \"profile\": \"PRIORITY_BLEND\", \"bandBlocks\": 6 }",
                "\"kind\": \"DRAINS_TO\", \"from\": \"feature:central-breakwater\", \"to\": \"feature:main-beach\", \"strength\": \"HARD\"");
        TerrainIntentV2 badRelation = codec.readTerrainIntent(
                unsupportedRelation, "unsupported-coastal-relation");
        DiagnosticCompilationException relationFailure = assertThrows(DiagnosticCompilationException.class,
                () -> compile(badRelation, DiagnosticCompileRequestV2.defaultBudget()));
        assertEquals("v2.coastal-relation", relationFailure.ruleId());

        TerrainIntentV2 crossing = codec.readTerrainIntent(valid.replace(
                "[[0.02, 0.42], [0.20, 0.35], [0.42, 0.41]]",
                "[[0.10, 0.10], [0.90, 0.90], [0.90, 0.10], [0.10, 0.90]]"), "crossing-coast");
        DiagnosticCompilationException crossingFailure = assertThrows(DiagnosticCompilationException.class,
                () -> compile(crossing, DiagnosticCompileRequestV2.defaultBudget()));
        assertEquals("v2.coastal-self-intersection", crossingFailure.ruleId());

        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace("[0.02, 0.42]", "[1.01, 0.42]"), "out-of-range-coast"));
    }

    @Test
    void rejectsFieldPointHaloArtifactAndDepthBudgetsBeforePublishingAPlan() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        WorldBlueprintV2.ResourceBudget defaults = DiagnosticCompileRequestV2.defaultBudget();

        assertBudgetFailure(intent, budget(defaults, defaults.maximumGeometryPoints(), 15, 128));
        assertBudgetFailure(intent, budget(defaults, 1, defaults.maximumFields(), 128));
        assertBudgetFailure(intent, budget(defaults, defaults.maximumGeometryPoints(),
                defaults.maximumFields(), 63));
        assertBudgetFailure(intent, new WorldBlueprintV2.ResourceBudget(
                defaults.maximumFeatures(), defaults.maximumRelations(), defaults.maximumConstraints(),
                defaults.maximumGeometryPoints(), defaults.maximumModules(), defaults.maximumFields(),
                defaults.maximumHaloXZ(), defaults.maximumHaloY(), defaults.maximumResidentBytes(),
                defaults.maximumCpuWorkUnits(), 1));

        TerrainIntentV2 tooWide = codec.readTerrainIntent(Files.readString(COASTAL).replace(
                "\"widthBlocks\": { \"min\": 20, \"max\": 55 }",
                "\"widthBlocks\": { \"min\": 20, \"max\": 65 }"), "wide-beach");
        DiagnosticCompilationException failure = assertThrows(DiagnosticCompilationException.class,
                () -> compile(tooWide, defaults));
        assertEquals("v2.coastal-halo-exceeded", failure.ruleId());

        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                Files.readString(COASTAL).replace(
                        "\"waterDepthBlocks\": { \"min\": 8, \"max\": 10 }",
                        "\"waterDepthBlocks\": { \"min\": 8, \"max\": 65 }"),
                "harbor-depth-over-budget"));
    }

    @Test
    void negativeEndpointCorruptionFixtureFailsBeforeBlueprintPublication() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(ENDPOINT_CORRUPTION);
        DiagnosticCompilationException failure = assertThrows(DiagnosticCompilationException.class,
                () -> compile(intent, DiagnosticCompileRequestV2.defaultBudget()));
        assertEquals("v2.sandy-beach-endpoint-profile", failure.ruleId());
    }

    @Test
    void rejectsClosedEntranceFixtureAndInsufficientBottomProfileDimensions() throws IOException {
        TerrainIntentV2 closed = codec.readTerrainIntent(CLOSED_HARBOR);
        DiagnosticCompilationException closedFailure = assertThrows(DiagnosticCompilationException.class,
                () -> compile(closed, DiagnosticCompileRequestV2.defaultBudget()));
        assertEquals("v2.harbor-basin-closed-entrance", closedFailure.ruleId());

        TerrainIntentV2 tooSmall = codec.readTerrainIntent(
                Files.readString(COASTAL).replace(
                        "\"profileTransitionBlocks\": 8", "\"profileTransitionBlocks\": 64"),
                "harbor-profile-too-wide");
        DiagnosticCompilationException dimensionFailure = assertThrows(DiagnosticCompilationException.class,
                () -> compile(tooSmall, DiagnosticCompileRequestV2.defaultBudget()));
        assertEquals("v2.harbor-basin-dimensions", dimensionFailure.ruleId());
    }

    @Test
    void rejectsCrossingArmsClosedOpeningUnsupportedFoundationAndUnknownSubgeometry() throws IOException {
        String valid = Files.readString(COASTAL);
        TerrainIntentV2 crossing = codec.readTerrainIntent(valid
                .replace("[[0.43, 0.39], [0.46, 0.48], [0.475, 0.53]]",
                        "[[0.43, 0.39], [0.59, 0.51], [0.475, 0.53]]")
                .replace("[[0.61, 0.47], [0.59, 0.51], [0.5625, 0.53]]",
                        "[[0.61, 0.47], [0.46, 0.48], [0.5625, 0.53]]"), "crossing-breakwater");
        DiagnosticCompilationException crossingFailure = assertThrows(DiagnosticCompilationException.class,
                () -> compile(crossing, DiagnosticCompileRequestV2.defaultBudget()));
        assertEquals("v2.breakwater-crossing-arms", crossingFailure.ruleId());

        TerrainIntentV2 closed = codec.readTerrainIntent(
                valid.replace("\"widthBlocks\": 28", "\"widthBlocks\": 20"), "closed-breakwater");
        DiagnosticCompilationException closedFailure = assertThrows(DiagnosticCompilationException.class,
                () -> compile(closed, DiagnosticCompileRequestV2.defaultBudget()));
        assertEquals("v2.breakwater-opening-width", closedFailure.ruleId());

        TerrainIntentV2 unsupported = codec.readTerrainIntent(
                valid.replace("\"outerDepthBlocks\": 14", "\"outerDepthBlocks\": 64"),
                "unsupported-breakwater-depth");
        DiagnosticCompilationException unsupportedFailure = assertThrows(DiagnosticCompilationException.class,
                () -> compile(unsupported, DiagnosticCompileRequestV2.defaultBudget()));
        assertEquals("v2.coastal-halo-exceeded", unsupportedFailure.ruleId());

        TerrainIntentV2 unnamed = codec.readTerrainIntent(
                valid.replace("            \"startEndpointId\": \"west-landfall\",\n", ""),
                "unnamed-breakwater-endpoint");
        DiagnosticCompilationException unnamedFailure = assertThrows(DiagnosticCompilationException.class,
                () -> compile(unnamed, DiagnosticCompileRequestV2.defaultBudget()));
        assertEquals("v2.breakwater-subgeometry", unnamedFailure.ruleId());
    }

    @Test
    void strictBlueprintSchemaRejectsUnknownMergeAndFutureCoastalPlanVersion() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        WorldBlueprintV2 blueprint = compile(intent, DiagnosticCompileRequestV2.defaultBudget());
        String canonical = codec.canonicalWorldBlueprint(blueprint);

        assertThrows(StructuredDataValidationException.class, () -> codec.readWorldBlueprint(
                canonical.replaceFirst("\"mergeOperator\":\"SINGLE_OWNER\"",
                        "\"mergeOperator\":\"LAST_WRITE_WINS\""), "unknown-merge"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readWorldBlueprint(
                canonical.replaceFirst("\"planVersion\":1", "\"planVersion\":2"),
                "future-coastal-plan"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                Files.readString(COASTAL).replaceFirst("\"kind\": \"SANDY_BEACH\"",
                        "\"kind\": \"UNKNOWN_COAST\""), "unknown-coastal-kind"));
    }

    private WorldBlueprintV2 compile(TerrainIntentV2 intent, WorldBlueprintV2.ResourceBudget budget) {
        return new DiagnosticBlueprintCompilerV2().compile(new DiagnosticCompileRequestV2(
                intent.intentId(), new GenerationBounds(400, 400, -64, 255, 50), 128, 827413L,
                "a".repeat(64), budget), intent);
    }

    private void assertBudgetFailure(TerrainIntentV2 intent, WorldBlueprintV2.ResourceBudget budget) {
        DiagnosticCompilationException failure = assertThrows(DiagnosticCompilationException.class,
                () -> compile(intent, budget));
        assertEquals("v2.budget-exceeded", failure.ruleId());
    }

    private static WorldBlueprintV2.ResourceBudget budget(
            WorldBlueprintV2.ResourceBudget source,
            int maximumGeometryPoints,
            int maximumFields,
            int maximumHaloXZ
    ) {
        return new WorldBlueprintV2.ResourceBudget(
                source.maximumFeatures(), source.maximumRelations(), source.maximumConstraints(),
                maximumGeometryPoints, source.maximumModules(), maximumFields, maximumHaloXZ,
                source.maximumHaloY(), source.maximumResidentBytes(), source.maximumCpuWorkUnits(),
                source.maximumArtifactBytes());
    }
}
