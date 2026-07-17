package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta.HydrologyDeltaModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.HydrologyLakeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.HydrologyRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.HydrologyTidalModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.LandformCanyonModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.LandformFjordModuleV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.DiagnosticIssueV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationPreviewModuleV2;
import com.github.nankotsu029.landformcraft.validation.v2.hydrology.HydrologyValidationPreviewModuleV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticBlueprintCompilerV2Test {
    private static final Path COASTAL = Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");
    private static final Path DELTA = Path.of("examples/v2/diagnostic/scenarios/delta.terrain-intent-v2.json");
    private static final Set<TerrainIntentV2.FeatureKind> SUPPORTED_HYDROLOGY_KINDS = Set.of(
            TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
            TerrainIntentV2.FeatureKind.LAKE,
            TerrainIntentV2.FeatureKind.CANYON,
            TerrainIntentV2.FeatureKind.DELTA,
            TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK,
            TerrainIntentV2.FeatureKind.FJORD);
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void compilesDiagnosticBlueprintAndRoundTripsWithoutGeneratingTerrain(@TempDir Path directory) throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        DiagnosticCompileRequestV2 request = request(intent.intentId(), DiagnosticCompileRequestV2.defaultBudget());

        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(request, intent);
        Path artifact = directory.resolve("world-blueprint-v2.json");
        codec.writeWorldBlueprint(artifact, blueprint);
        WorldBlueprintV2 roundTrip = codec.readWorldBlueprint(artifact);

        assertEquals(blueprint, roundTrip);
        assertEquals(codec.worldBlueprintChecksum(blueprint), blueprint.canonicalChecksum());
        assertEquals(HydrologyIrModuleV2.MODULE_ID, blueprint.hydrologyPlan().moduleId());
        assertTrue(blueprint.hydrologyPlan().basins().isEmpty());
        assertEquals(6, blueprint.hydrologyPlan().fields().size());
        assertEquals(blueprint.hydrologyPlan().canonicalChecksum(),
                codec.hydrologyPlanChecksum(blueprint.hydrologyPlan()));
        assertEquals(HydrologyReconciliationModuleV2.MODULE_ID,
                blueprint.hydrologyReconciliationPlan().moduleId());
        assertEquals(blueprint.hydrologyPlan().canonicalChecksum(),
                blueprint.hydrologyReconciliationPlan().sourceHydrologyPlanChecksum());
        assertEquals(blueprint.hydrologyReconciliationPlan().canonicalChecksum(),
                codec.hydrologyReconciliationPlanChecksum(blueprint.hydrologyReconciliationPlan()));
        assertTrue(blueprint.hydrologyReconciliationPlan().variables().isEmpty());
        assertTrue(blueprint.stages().stream().anyMatch(stage ->
                stage.stageId().equals(HydrologyReconciliationModuleV2.STAGE_ID)));
        assertEquals(5, blueprint.featurePlans().size());
        assertEquals(1, blueprint.coastalTransitionPlans().size());
        assertEquals(5, blueprint.validationTargets().stream()
                .filter(target -> target.ruleId().startsWith("coastal.")).count());
        assertTrue(blueprint.featurePlans().stream()
                .filter(plan -> switch (plan.kind()) {
                    case SANDY_BEACH, HARBOR_BASIN, BREAKWATER_HARBOR, ROCKY_CAPE -> true;
                    default -> false;
                })
                .allMatch(plan -> !plan.validationTargetIds().isEmpty()));
        var transition = blueprint.coastalTransitionPlans().getFirst();
        assertEquals(ModuleDescriptorV2.MergeOperator.PRIORITY_BLEND, transition.mergeOperator());
        assertEquals(4, transition.contributors().size());
        assertEquals(4, transition.interactions().size());
        assertEquals(8, transition.supportRadiusXZ());
        assertEquals(20, transition.contributors().stream()
                .filter(contributor -> contributor.featureId().equals("central-breakwater"))
                .findFirst().orElseThrow().priority());
        assertTrue(blueprint.modules().stream().anyMatch(
                module -> module.moduleId().equals(CoastalTransitionModuleV2.MODULE_ID)));
        assertTrue(blueprint.fields().stream().allMatch(
                field -> field.storage() == WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY));
        assertEquals(Set.of(
                        CoastalFoundationModuleV2.MODULE_ID,
                        CoastalTransitionModuleV2.MODULE_ID,
                        CoastalValidationPreviewModuleV2.MODULE_ID,
                        HydrologyIrModuleV2.MODULE_ID,
                        HydrologyRiverModuleV2.MODULE_ID,
                        HydrologyLakeModuleV2.MODULE_ID,
                        LandformCanyonModuleV2.MODULE_ID,
                        HydrologyDeltaModuleV2.MODULE_ID,
                        HydrologyTidalModuleV2.MODULE_ID,
                        LandformFjordModuleV2.MODULE_ID,
                        HydrologyReconciliationModuleV2.MODULE_ID,
                        HydrologyValidationPreviewModuleV2.MODULE_ID),
                blueprint.modules().stream()
                        .filter(module -> module.lifecycleStatus() == ModuleDescriptorV2.LifecycleStatus.SUPPORTED)
                        .map(ModuleDescriptorV2::moduleId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        assertTrue(blueprint.diagnosticIssues().stream().anyMatch(
                issue -> issue.ruleId().equals("v2.unsupported-capability")));
        assertTrue(blueprint.diagnosticIssues().stream().anyMatch(
                issue -> issue.ruleId().equals("v2.missing-validator-capability")));
        assertTrue(blueprint.diagnosticIssues().stream().anyMatch(
                issue -> issue.ruleId().equals("v2.missing-preview-capability")));
        assertTrue(blueprint.diagnosticIssues().stream().noneMatch(issue ->
                (issue.ruleId().equals("v2.missing-validator-capability")
                        || issue.ruleId().equals("v2.missing-preview-capability"))
                        && issue.references().stream().anyMatch(reference -> reference.id().equals("main-beach")
                        || reference.id().equals("harbor-basin")
                        || reference.id().equals("central-breakwater")
                        || reference.id().equals("east-cape"))));
        assertEquals(intent.features().size(), blueprint.diagnosticIssues().stream()
                .filter(issue -> issue.ruleId().equals("v2.unsupported-capability")).count());
        assertFalse(blueprint.diagnosticIssues().stream().anyMatch(
                issue -> issue.severity() == DiagnosticIssueV2.Severity.INFO));
    }

    @Test
    void scenarioLifecycleMatchesTheCompletedV2_3Subset() throws IOException {
        BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();
        List<Path> fixtures;
        try (var files = Files.list(Path.of("examples/v2/diagnostic/scenarios"))) {
            fixtures = files.sorted().toList();
        }
        assertEquals(10, fixtures.size());
        for (Path fixture : fixtures) {
            TerrainIntentV2 intent = codec.readTerrainIntent(fixture);
            WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                    request(intent.intentId(), DiagnosticCompileRequestV2.defaultBudget()), intent);
            assertEquals(intent.features().size(), blueprint.diagnosticIssues().stream()
                    .filter(issue -> issue.ruleId().equals("v2.unsupported-capability")).count(), fixture.toString());
            for (TerrainIntentV2.Feature feature : intent.features()) {
                ModuleDescriptorV2.LifecycleStatus expected = SUPPORTED_HYDROLOGY_KINDS.contains(feature.kind())
                        ? ModuleDescriptorV2.LifecycleStatus.SUPPORTED
                        : ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL;
                assertEquals(expected, catalog.requireFor(feature.kind()).lifecycleStatus(),
                        fixture + " " + feature.kind());
            }
        }
    }

    @Test
    void compilesAndRoundTripsFrozenDeltaDistributaryPlan(@TempDir Path directory) throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(DELTA);
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                request(intent.intentId(), DiagnosticCompileRequestV2.defaultBudget()), intent);

        assertEquals(1, blueprint.deltaPlans().size());
        var delta = blueprint.deltaPlans().getFirst();
        assertEquals("river-delta", delta.featureId());
        assertEquals("main-river", delta.trunkRiverFeatureId());
        assertEquals(6, delta.selectedDistributaryCount());
        assertEquals(7, delta.selectedSandbarCount());
        assertEquals(TerrainIntentV2.FIXED_SCALE,
                delta.branches().stream().mapToInt(branch -> branch.dischargeShareMillionths()).sum());
        assertTrue(blueprint.hydrologyReconciliationPlan().constraints().stream().anyMatch(constraint ->
                constraint.kind() == HydrologyReconciliationPlanV2.ConstraintKind.DELTA_MOUTH
                        && constraint.featureId().equals(delta.featureId())));
        assertTrue(blueprint.modules().stream().anyMatch(
                module -> module.moduleId().equals(HydrologyDeltaModuleV2.MODULE_ID)
                        && module.lifecycleStatus() == ModuleDescriptorV2.LifecycleStatus.SUPPORTED));
        assertTrue(blueprint.fields().stream().anyMatch(
                field -> field.semantic() == WorldBlueprintV2.FieldSemantic.HYDROLOGY_DELTA_FAN_MASK));
        assertTrue(blueprint.diagnosticIssues().stream().noneMatch(issue ->
                issue.ruleId().equals("v2.missing-validator-capability")
                        && issue.references().stream().anyMatch(reference -> reference.id().equals("river-delta"))));

        Path artifact = directory.resolve("delta-world-blueprint-v2.json");
        codec.writeWorldBlueprint(artifact, blueprint);
        assertEquals(blueprint, codec.readWorldBlueprint(artifact));
    }

    @Test
    void canonicalBlueprintDoesNotDependOnModuleOrDescriptorRegistrationOrder() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                request(intent.intentId(), DiagnosticCompileRequestV2.defaultBudget()), intent);
        WorldBlueprintV2 reordered = new WorldBlueprintV2(
                blueprint.identity(), blueprint.space(), blueprint.determinism(), reversed(blueprint.modules()),
                reversed(blueprint.stages()), reversed(blueprint.fields()), reversed(blueprint.fieldOwnership()),
                reversed(blueprint.featurePlans()), reversed(blueprint.coastalFeaturePlans()),
                reversed(blueprint.sandyBeachPlans()), reversed(blueprint.harborBasinPlans()),
                reversed(blueprint.breakwaterHarborPlans()),
                reversed(blueprint.rockyCapePlans()),
                reversed(blueprint.coastalTransitionPlans()),
                reversed(blueprint.meanderingRiverPlans()),
                reversed(blueprint.lakePlans()),
                reversed(blueprint.canyonPlans()),
                reversed(blueprint.waterfallPlans()),
                reversed(blueprint.deltaPlans()),
                reversed(blueprint.tidalChannelPlans()),
                reversed(blueprint.fjordPlans()),
                reversed(blueprint.mountainPlans()),
                reversed(blueprint.volcanicPlans()),
                blueprint.geologyPlan(),
                blueprint.lithologyPlan(),
                blueprint.strataPlan(),
                blueprint.climatePlan(),
                blueprint.waterConditionPlan(),
                blueprint.hydrologyPlan(),
                blueprint.hydrologyReconciliationPlan(),
                reversed(blueprint.validationTargets()), blueprint.budgets(), reversed(blueprint.diagnosticIssues()),
                "0".repeat(64));

        assertEquals(blueprint.canonicalChecksum(), codec.worldBlueprintChecksum(reordered));
        assertEquals(blueprint.modules(), reordered.modules());
        assertEquals(blueprint.featurePlans(), reordered.featurePlans());
    }

    @Test
    void blueprintDomainRejectsDuplicateModuleDescriptors() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                request(intent.intentId(), DiagnosticCompileRequestV2.defaultBudget()), intent);
        List<ModuleDescriptorV2> duplicateModules = new ArrayList<>(blueprint.modules());
        duplicateModules.add(blueprint.modules().getFirst());

        assertThrows(IllegalArgumentException.class, () -> new WorldBlueprintV2(
                blueprint.identity(), blueprint.space(), blueprint.determinism(), duplicateModules,
                blueprint.stages(), blueprint.fields(), blueprint.fieldOwnership(), blueprint.featurePlans(),
                blueprint.coastalFeaturePlans(), blueprint.sandyBeachPlans(),
                blueprint.harborBasinPlans(),
                blueprint.breakwaterHarborPlans(),
                blueprint.rockyCapePlans(),
                blueprint.coastalTransitionPlans(),
                blueprint.meanderingRiverPlans(),
                blueprint.lakePlans(),
                blueprint.canyonPlans(),
                blueprint.waterfallPlans(),
                blueprint.deltaPlans(),
                blueprint.tidalChannelPlans(),
                blueprint.fjordPlans(),
                blueprint.mountainPlans(),
                blueprint.volcanicPlans(),
                blueprint.geologyPlan(),
                blueprint.lithologyPlan(),
                blueprint.strataPlan(),
                blueprint.climatePlan(),
                blueprint.waterConditionPlan(),
                blueprint.hydrologyPlan(),
                blueprint.hydrologyReconciliationPlan(),
                blueprint.validationTargets(), blueprint.budgets(),
                blueprint.diagnosticIssues(), "0".repeat(64)));
    }

    @Test
    void blueprintCodecRejectsUnknownFieldsAndChecksumTampering() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                request(intent.intentId(), DiagnosticCompileRequestV2.defaultBudget()), intent);
        String canonical = codec.canonicalWorldBlueprint(blueprint);

        assertThrows(StructuredDataValidationException.class, () -> codec.readWorldBlueprint(
                canonical.replaceFirst("\\{", "{\"unknown\":true,"), "unknown-blueprint-field"));
        assertThrows(IOException.class, () -> codec.readWorldBlueprint(
                canonical.replace(blueprint.canonicalChecksum(), "0".repeat(64)), "tampered-blueprint"));
    }

    @Test
    void coastalAdjacencyRequiresExplicitCurrentTransitionPolicy() throws IOException {
        String source = Files.readString(COASTAL);
        String policy = ", \"transition\": { \"transitionVersion\": 1, \"profile\": \"PRIORITY_BLEND\", \"bandBlocks\": 6 }";
        TerrainIntentV2 missing = codec.readTerrainIntent(
                source.replace(policy, ""), "coastal-missing-transition-policy");
        DiagnosticCompilationException exception = assertThrows(DiagnosticCompilationException.class,
                () -> new DiagnosticBlueprintCompilerV2().compile(
                        request(missing.intentId(), DiagnosticCompileRequestV2.defaultBudget()), missing));
        assertEquals("v2.coastal-transition-missing-policy", exception.ruleId());

        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                source.replaceFirst("\\\"transitionVersion\\\": 1", "\\\"transitionVersion\\\": 2"),
                "coastal-future-transition-policy"));
    }

    @Test
    void rejectsBudgetExceededBeforeCreatingBlueprint() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        WorldBlueprintV2.ResourceBudget tiny = new WorldBlueprintV2.ResourceBudget(
                1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1);

        DiagnosticCompilationException exception = assertThrows(DiagnosticCompilationException.class,
                () -> new DiagnosticBlueprintCompilerV2().compile(request(intent.intentId(), tiny), intent));
        assertEquals("v2.budget-exceeded", exception.ruleId());
    }

    @Test
    void catalogRejectsUnknownModuleFieldOwnerCollisionAndDependencyCycles() {
        ModuleDescriptorV2 first = module("module.first", "field.first", List.of());
        ModuleDescriptorV2 secondSameOwner = module("module.second", "field.first", List.of());
        List<WorldBlueprintV2.StageDescriptor> oneStage = List.of(
                new WorldBlueprintV2.StageDescriptor("stage.main", List.of()));

        EnumMap<TerrainIntentV2.FeatureKind, String> unknownBinding = new EnumMap<>(TerrainIntentV2.FeatureKind.class);
        unknownBinding.put(TerrainIntentV2.FeatureKind.SANDY_BEACH, "module.missing");
        assertThrows(IllegalArgumentException.class,
                () -> BuiltInLandformModuleCatalogV2.validateCatalog(List.of(first), unknownBinding, oneStage));
        assertThrows(IllegalArgumentException.class,
                () -> BuiltInLandformModuleCatalogV2.validateCatalog(List.of(first, secondSameOwner), Map.of(), oneStage));

        ModuleDescriptorV2 cycleA = module("module.a", "field.a", List.of("field.b"));
        ModuleDescriptorV2 cycleB = module("module.b", "field.b", List.of("field.a"));
        assertThrows(IllegalArgumentException.class,
                () -> BuiltInLandformModuleCatalogV2.validateCatalog(List.of(cycleA, cycleB), Map.of(), oneStage));

        List<WorldBlueprintV2.StageDescriptor> cyclicStages = List.of(
                new WorldBlueprintV2.StageDescriptor("stage.a", List.of("stage.b")),
                new WorldBlueprintV2.StageDescriptor("stage.b", List.of("stage.a")));
        assertThrows(IllegalArgumentException.class,
                () -> BuiltInLandformModuleCatalogV2.validateCatalog(List.of(), Map.of(), cyclicStages));
    }

    @Test
    void sidecarFieldRequiresAnExactlyMatchingArtifactDefinition() {
        FieldArtifactDescriptorV2 artifact = new FieldArtifactDescriptorV2(
                "fields/desired-height.lfgrid",
                new FieldArtifactDescriptorV2.Definition(
                        "constraint.desired-height", FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT,
                        FieldArtifactDescriptorV2.FieldValueType.U16, 400, 400,
                        FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                        FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED,
                        1_000, 0, true, 65_535),
                FieldArtifactDescriptorV2.ENCODING_VERSION, "a".repeat(64), "b".repeat(64),
                new FieldArtifactDescriptorV2.Provenance(
                        FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP, "constraint-source:height",
                        "c".repeat(64), "constraint.png", "1", "mapping.pixel-center-v1"));

        WorldBlueprintV2.FieldDescriptor descriptor = new WorldBlueprintV2.FieldDescriptor(
                "constraint.desired-height", WorldBlueprintV2.FieldSemantic.DESIRED_HEIGHT,
                WorldBlueprintV2.FieldValueType.U16, 400, 400, WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                WorldBlueprintV2.FieldSampling.BILINEAR_FIXED, WorldBlueprintV2.FieldStorage.SIDECAR, artifact);

        assertEquals(artifact, descriptor.artifact());
        assertThrows(IllegalArgumentException.class, () -> new WorldBlueprintV2.FieldDescriptor(
                "constraint.actual-height", WorldBlueprintV2.FieldSemantic.ACTUAL_HEIGHT,
                WorldBlueprintV2.FieldValueType.U16, 400, 400, WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                WorldBlueprintV2.FieldSampling.BILINEAR_FIXED, WorldBlueprintV2.FieldStorage.SIDECAR, artifact));
        assertThrows(IllegalArgumentException.class, () -> new WorldBlueprintV2.FieldDescriptor(
                "constraint.desired-height", WorldBlueprintV2.FieldSemantic.DESIRED_HEIGHT,
                WorldBlueprintV2.FieldValueType.U16, 400, 400, WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY, artifact));
    }

    private static DiagnosticCompileRequestV2 request(String requestId, WorldBlueprintV2.ResourceBudget budget) {
        return new DiagnosticCompileRequestV2(
                requestId, new GenerationBounds(400, 400, -64, 255, 50), 128, 827413L, "a".repeat(64), budget);
    }

    private static ModuleDescriptorV2 module(String id, String provided, List<String> required) {
        return new ModuleDescriptorV2(
                id, "test-v1", ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL, List.of(), required,
                List.of(provided), List.of(new ModuleDescriptorV2.FieldWrite(
                        provided, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)),
                "stage.main", 0, 0, ModuleDescriptorV2.ResourceClass.DIAGNOSTIC_LOW,
                List.of("diagnostic.contract"), List.of("diagnostic.geometry"));
    }

    private static <T> List<T> reversed(List<T> values) {
        List<T> result = new ArrayList<>(values);
        Collections.reverse(result);
        return result;
    }
}
