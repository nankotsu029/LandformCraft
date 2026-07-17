package com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.LakePlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationStateV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedHydrologyReconcilerV2Test {
    private static final String HYDROLOGY_CHECKSUM = "a".repeat(64);
    private static final String BLUEPRINT_CHECKSUM = "b".repeat(64);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final HydrologyReconciliationPlanCompilerV2 compiler =
            new HydrologyReconciliationPlanCompilerV2();
    private final BoundedHydrologyReconcilerV2 reconciler = new BoundedHydrologyReconcilerV2();
    private final HydrologyReconciliationArtifactCodecV2 artifactCodec =
            new HydrologyReconciliationArtifactCodecV2();

    @Test
    void blueprintFreezesEveryRegionalTargetAndBaselineReconciles() throws IOException {
        List<TargetFixture> fixtures = List.of(
                fixture("hydrology/meandering-river.terrain-intent-v2.json",
                        HydrologyReconciliationPlanV2.ConstraintKind.REACH_BED),
                fixture("diagnostic/scenarios/delta.terrain-intent-v2.json",
                        HydrologyReconciliationPlanV2.ConstraintKind.DELTA_MOUTH),
                fixture("diagnostic/scenarios/mangrove-wetland.terrain-intent-v2.json",
                        HydrologyReconciliationPlanV2.ConstraintKind.TIDAL_CONNECTION),
                fixture("diagnostic/scenarios/fjord.terrain-intent-v2.json",
                        HydrologyReconciliationPlanV2.ConstraintKind.FJORD_CONNECTION),
                fixture("diagnostic/scenarios/canyon-waterfall.terrain-intent-v2.json",
                        HydrologyReconciliationPlanV2.ConstraintKind.WATERFALL_LIP_BASE));

        for (TargetFixture fixture : fixtures) {
            WorldBlueprintV2 blueprint = compileBlueprint(fixture.path());
            HydrologyReconciliationPlanV2 plan = blueprint.hydrologyReconciliationPlan();
            Set<HydrologyReconciliationPlanV2.ConstraintKind> kinds = plan.constraints().stream()
                    .map(HydrologyReconciliationPlanV2.Constraint::kind)
                    .collect(java.util.stream.Collectors.toCollection(
                            () -> EnumSet.noneOf(HydrologyReconciliationPlanV2.ConstraintKind.class)));

            assertTrue(kinds.contains(fixture.expectedKind()), fixture.path().toString());
            assertEquals(blueprint.hydrologyPlan().canonicalChecksum(), plan.sourceHydrologyPlanChecksum());
            assertEquals(HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS, plan.maximumIterations());
            assertTrue(blueprint.modules().stream().anyMatch(module ->
                    module.moduleId().equals(HydrologyReconciliationModuleV2.MODULE_ID)
                            && module.lifecycleStatus() == ModuleDescriptorV2.LifecycleStatus.SUPPORTED));
            assertTrue(blueprint.stages().stream().anyMatch(stage ->
                    stage.stageId().equals(HydrologyReconciliationModuleV2.STAGE_ID)
                            && !stage.dependsOnStageIds().isEmpty()));

            HydrologyReconciliationArtifactV2 artifact = reconciler.reconcile(
                    blueprint.canonicalChecksum(), plan, compiler.baselineState(plan), () -> false);
            assertEquals(HydrologyReconciliationArtifactV2.Status.SATISFIED, artifact.status());
            assertTrue(artifact.residuals().stream().allMatch(
                    HydrologyReconciliationArtifactV2.Residual::satisfied));
            assertEquals(plan.budget().estimatedWorkUnits(), artifact.resources().workUnits());
        }

        LakePlanV2 lake = openLakePlan();
        HydrologyReconciliationPlanV2 lakePlan = codec.sealHydrologyReconciliationPlan(compiler.compile(
                HYDROLOGY_CHECKSUM, List.of(), List.of(lake), List.of(), List.of(), List.of(), List.of(),
                HydrologyReconciliationPlanV2.MAXIMUM_WORK_UNITS,
                HydrologyReconciliationPlanV2.MAXIMUM_WORKING_BYTES,
                HydrologyReconciliationPlanV2.MAXIMUM_ARTIFACT_BYTES));
        assertEquals(List.of(HydrologyReconciliationPlanV2.ConstraintKind.LAKE_SPILL),
                lakePlan.constraints().stream().map(HydrologyReconciliationPlanV2.Constraint::kind).toList());
        assertEquals(HydrologyReconciliationArtifactV2.Status.SATISFIED, reconciler.reconcile(
                BLUEPRINT_CHECKSUM, lakePlan, compiler.baselineState(lakePlan), () -> false).status());
    }

    @Test
    void fixedPassRecoversReachBedAndLakeSpillResiduals() {
        HydrologyReconciliationPlanV2 plan = sealPlan(
                List.of(
                        variable("river.recover.bed.0000", "river-recover",
                                HydrologyReconciliationPlanV2.VariableKind.REACH_BED, 100_000_000L),
                        variable("river.recover.bed.0001", "river-recover",
                                HydrologyReconciliationPlanV2.VariableKind.REACH_BED, 90_000_000L),
                        variable("lake.recover.surface", "lake-recover",
                                HydrologyReconciliationPlanV2.VariableKind.LAKE_SURFACE, 63_000_000L),
                        variable("lake.recover.spill", "lake-recover",
                                HydrologyReconciliationPlanV2.VariableKind.LAKE_SPILL, 63_000_000L)),
                List.of(
                        adjustable("reconcile.reach.river-recover.0001",
                                HydrologyReconciliationPlanV2.ConstraintKind.REACH_BED,
                                "river-recover", "river.recover.bed.0000", "river.recover.bed.0001",
                                -512_000_000L, 0L, 16_000_000L),
                        adjustable("reconcile.lake.lake-recover.spill",
                                HydrologyReconciliationPlanV2.ConstraintKind.LAKE_SPILL,
                                "lake-recover", "lake.recover.surface", "lake.recover.spill",
                                0L, 0L, 16_000_000L)));
        HydrologyReconciliationStateV2 state = state(plan, List.of(
                value("river.recover.bed.0000", 100_000_000L, false),
                value("river.recover.bed.0001", 104_000_000L, false),
                value("lake.recover.surface", 63_000_000L, false),
                value("lake.recover.spill", 66_000_000L, false)));

        HydrologyReconciliationArtifactV2 artifact = reconciler.reconcile(
                BLUEPRINT_CHECKSUM, plan, state, () -> false);

        assertEquals(HydrologyReconciliationArtifactV2.Status.SATISFIED, artifact.status());
        assertEquals(HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS, artifact.iterationsExecuted());
        assertEquals(100_000_000L, finalValue(artifact, "river.recover.bed.0001"));
        assertEquals(63_000_000L, finalValue(artifact, "lake.recover.spill"));
        assertTrue(artifact.residuals().stream().allMatch(residual ->
                residual.satisfied()
                        && residual.failureReason() == HydrologyReconciliationArtifactV2.FailureReason.NONE
                        && residual.correctionCount() == 1));
    }

    @Test
    void emitsCanonicalFailureReasonsForUnrecoverableHardAndAdjustmentConflicts() {
        HydrologyReconciliationPlanV2 connectionPlan = sealPlan(
                List.of(new HydrologyReconciliationPlanV2.VariableDescriptor(
                        "delta.blocked.mouth.0000", "delta-blocked",
                        HydrologyReconciliationPlanV2.VariableKind.MARINE_CONNECTION, 1L, 0L, 1L)),
                List.of(new HydrologyReconciliationPlanV2.Constraint(
                        "reconcile.delta.delta-blocked.mouth.0000",
                        HydrologyReconciliationPlanV2.ConstraintKind.DELTA_MOUTH,
                        "delta-blocked", null, "delta.blocked.mouth.0000", 1L, 1L,
                        HydrologyReconciliationPlanV2.CorrectionPolicy.VERIFY_ONLY, 0L)));
        HydrologyReconciliationArtifactV2 blocked = reconciler.reconcile(
                BLUEPRINT_CHECKSUM, connectionPlan,
                state(connectionPlan, List.of(value("delta.blocked.mouth.0000", 0L, true))), () -> false);
        assertFailure(blocked, HydrologyReconciliationArtifactV2.FailureReason.UNRECOVERABLE_CONNECTION);

        HydrologyReconciliationPlanV2 reachPlan = exactReachPlan(1_000_000L);
        HydrologyReconciliationArtifactV2 hard = reconciler.reconcile(
                BLUEPRINT_CHECKSUM, reachPlan,
                state(reachPlan, List.of(
                        value("river.failure.left", 0L, false),
                        value("river.failure.right", 1_000_000L, true))), () -> false);
        assertFailure(hard, HydrologyReconciliationArtifactV2.FailureReason.HARD_CONFLICT);

        HydrologyReconciliationArtifactV2 limited = reconciler.reconcile(
                BLUEPRINT_CHECKSUM, reachPlan,
                state(reachPlan, List.of(
                        value("river.failure.left", 0L, false),
                        value("river.failure.right", 2_000_000L, false))), () -> false);
        assertFailure(limited, HydrologyReconciliationArtifactV2.FailureReason.ADJUSTMENT_LIMIT);
    }

    @Test
    void stopsAtVersionedIterationBoundaryAndReportsNonConvergence() {
        HydrologyReconciliationPlanV2 plan = sealPlan(
                List.of(
                        variable("river.cycle.a", "river-cycle",
                                HydrologyReconciliationPlanV2.VariableKind.REACH_BED, 0L),
                        variable("river.cycle.b", "river-cycle",
                                HydrologyReconciliationPlanV2.VariableKind.REACH_BED, 0L)),
                List.of(
                        adjustable("reconcile.reach.river-cycle.0001",
                                HydrologyReconciliationPlanV2.ConstraintKind.REACH_BED,
                                "river-cycle", "river.cycle.a", "river.cycle.b",
                                0L, 0L, 1_000_000L),
                        adjustable("reconcile.reach.river-cycle.0002",
                                HydrologyReconciliationPlanV2.ConstraintKind.REACH_BED,
                                "river-cycle", "river.cycle.b", "river.cycle.a",
                                -1_000_000L, -1_000_000L, 1_000_000L)));

        HydrologyReconciliationArtifactV2 artifact = reconciler.reconcile(
                BLUEPRINT_CHECKSUM, plan, compiler.baselineState(plan), () -> false);

        assertEquals(HydrologyReconciliationArtifactV2.Status.FAILED, artifact.status());
        assertEquals(HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS, artifact.iterationsExecuted());
        assertTrue(artifact.residuals().stream().anyMatch(residual ->
                !residual.satisfied()
                        && residual.failureReason()
                        == HydrologyReconciliationArtifactV2.FailureReason.NON_CONVERGENCE));
    }

    @Test
    void featureTileThreadCandidateLocaleAndTimezoneOrderDoNotChangeResult() throws Exception {
        List<HydrologyReconciliationPlanV2.VariableDescriptor> variables = List.of(
                variable("river.order.a", "river-order",
                        HydrologyReconciliationPlanV2.VariableKind.REACH_BED, 10_000_000L),
                variable("river.order.b", "river-order",
                        HydrologyReconciliationPlanV2.VariableKind.REACH_BED, 9_000_000L),
                new HydrologyReconciliationPlanV2.VariableDescriptor(
                        "tidal.order.connection", "tidal-order",
                        HydrologyReconciliationPlanV2.VariableKind.MARINE_CONNECTION, 1L, 0L, 1L));
        List<HydrologyReconciliationPlanV2.Constraint> constraints = List.of(
                adjustable("reconcile.reach.river-order.0001",
                        HydrologyReconciliationPlanV2.ConstraintKind.REACH_BED,
                        "river-order", "river.order.a", "river.order.b",
                        -512_000_000L, 0L, 16_000_000L),
                new HydrologyReconciliationPlanV2.Constraint(
                        "reconcile.tidal.tidal-order.connection",
                        HydrologyReconciliationPlanV2.ConstraintKind.TIDAL_CONNECTION,
                        "tidal-order", null, "tidal.order.connection", 1L, 1L,
                        HydrologyReconciliationPlanV2.CorrectionPolicy.VERIFY_ONLY, 0L));
        HydrologyReconciliationPlanV2 forward = sealPlan(variables, constraints);
        HydrologyReconciliationPlanV2 reversed = sealPlan(reversed(variables), reversed(constraints));
        HydrologyReconciliationStateV2 state = state(forward, List.of(
                value("tidal.order.connection", 1L, false),
                value("river.order.b", 12_000_000L, false),
                value("river.order.a", 10_000_000L, false)));

        assertEquals(forward.canonicalChecksum(), reversed.canonicalChecksum());
        HydrologyReconciliationArtifactV2 expected = artifactCodec.seal(reconciler.reconcile(
                BLUEPRINT_CHECKSUM, forward, state, () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            List<Future<HydrologyReconciliationArtifactV2>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                HydrologyReconciliationPlanV2 candidate = index % 2 == 0 ? forward : reversed;
                HydrologyReconciliationStateV2 candidateState = state(candidate, reversed(state.values()));
                futures.add(executor.submit(() -> artifactCodec.seal(reconciler.reconcile(
                        BLUEPRINT_CHECKSUM, candidate, candidateState, () -> false))));
            }
            for (Future<HydrologyReconciliationArtifactV2> future : futures) {
                HydrologyReconciliationArtifactV2 actual = future.get();
                assertEquals(expected.resultChecksum(), actual.resultChecksum());
                assertEquals(expected.canonicalChecksum(), actual.canonicalChecksum());
                assertEquals(expected.residuals(), actual.residuals());
            }
        } finally {
            executor.shutdownNow();
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void iterationWorkingSetArtifactBudgetsAndCancellationAreBounded() {
        assertEquals(2L + 4L * 3L, HydrologyReconciliationPlanV2.estimateWorkUnits(2, 3));
        assertEquals(8_192L + 2L * 384L + 3L * 512L,
                HydrologyReconciliationPlanV2.estimateWorkingBytes(2, 3));
        assertEquals(4_096L + 2L * 640L + 3L * 1_024L,
                HydrologyReconciliationPlanV2.estimateArtifactBytes(2, 3));

        HydrologyReconciliationException budget = assertThrows(HydrologyReconciliationException.class,
                () -> compiler.compile(HYDROLOGY_CHECKSUM, List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), 100L, 8_191L, 4_096L));
        assertEquals("v2.hydrology-reconciliation-budget", budget.ruleId());
        assertThrows(IllegalArgumentException.class, () -> new HydrologyReconciliationPlanV2(
                HydrologyReconciliationPlanV2.VERSION,
                HydrologyReconciliationPlanV2.ALGORITHM_VERSION,
                HydrologyReconciliationPlanV2.SCAN_ORDER_VERSION,
                HydrologyReconciliationModuleV2.MODULE_ID,
                HydrologyReconciliationModuleV2.MODULE_VERSION,
                HydrologyReconciliationModuleV2.STAGE_ID,
                HYDROLOGY_CHECKSUM,
                2,
                List.of(),
                List.of(),
                budget(0, 0),
                "0".repeat(64)));

        HydrologyReconciliationPlanV2 plan = exactReachPlan(16_000_000L);
        AtomicInteger observations = new AtomicInteger();
        assertThrows(CancellationException.class, () -> reconciler.reconcile(
                BLUEPRINT_CHECKSUM,
                plan,
                state(plan, List.of(
                        value("river.failure.left", 0L, false),
                        value("river.failure.right", 1_000_000L, false))),
                () -> observations.incrementAndGet() >= 3));
        assertEquals(3, observations.get());
    }

    @Test
    void planAndResidualArtifactStrictlyRoundTripAndRejectCorruption(@TempDir Path directory) throws IOException {
        HydrologyReconciliationPlanV2 plan = exactReachPlan(16_000_000L);
        Path planPath = directory.resolve("hydrology-reconciliation-plan-v2.json");
        codec.writeHydrologyReconciliationPlan(planPath, plan);
        assertEquals(plan, codec.readHydrologyReconciliationPlan(planPath));
        String canonicalPlan = Files.readString(planPath);
        assertThrows(StructuredDataValidationException.class, () -> codec.readHydrologyReconciliationPlan(
                canonicalPlan.replaceFirst("\\{", "{\"unexpected\":true,"), "unknown-plan-field"));
        assertThrows(IOException.class, () -> codec.readHydrologyReconciliationPlan(
                replaceChecksum(canonicalPlan, plan.canonicalChecksum()), "tampered-plan-checksum"));

        HydrologyReconciliationArtifactV2 artifact = artifactCodec.seal(reconciler.reconcile(
                BLUEPRINT_CHECKSUM, plan, compiler.baselineState(plan), () -> false));
        Path artifactPath = directory.resolve("hydrology-reconciliation-artifact-v2.json");
        artifactCodec.write(artifactPath, artifact);
        assertEquals(artifact, artifactCodec.read(artifactPath));
        String canonicalArtifact = Files.readString(artifactPath);
        assertThrows(StructuredDataValidationException.class, () -> artifactCodec.read(
                canonicalArtifact.replaceFirst("\\{", "{\"unexpected\":true,"), "unknown-artifact-field"));
        assertThrows(IOException.class, () -> artifactCodec.read(
                replaceChecksum(canonicalArtifact, artifact.canonicalChecksum()), "tampered-artifact-checksum"));
        assertThrows(IOException.class, () -> artifactCodec.read(
                canonicalArtifact.replaceFirst(
                        "\\{\"algorithmVersion\"", "{\"artifactVersion\":1,\"artifactVersion\":1,\"algorithmVersion\""),
                "duplicate-artifact-field"));
    }

    private WorldBlueprintV2 compileBlueprint(Path path) throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(path);
        return new DiagnosticBlueprintCompilerV2().compile(
                new DiagnosticCompileRequestV2(
                        intent.intentId(), new GenerationBounds(400, 400, -64, 255, 63),
                        128, 827_413L, "c".repeat(64), DiagnosticCompileRequestV2.defaultBudget()),
                intent);
    }

    private static LakePlanV2 openLakePlan() {
        TerrainIntentV2.Feature lake = new TerrainIntentV2.Feature(
                "lake-reconcile",
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
                TerrainIntentV2.Provenance.confirmedManual("reconciliation-test"));
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "lake-reconciliation-fixture",
                "lake reconciliation",
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                List.of(lake),
                List.of(),
                List.of(),
                new TerrainIntentV2.EnvironmentDescriptor(
                        "ALLUVIAL_SEDIMENT", "TEMPERATE_HUMID", "LAKE_BASIN"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("reconciliation-test"));
        return new LakePlanCompilerV2().compile(
                lake, intent, new WorldBlueprintV2.Bounds(400, 400, -64, 255, 63), "d".repeat(64));
    }

    private HydrologyReconciliationPlanV2 exactReachPlan(long maximumAdjustment) {
        return sealPlan(
                List.of(
                        variable("river.failure.left", "river-failure",
                                HydrologyReconciliationPlanV2.VariableKind.REACH_BED, 0L),
                        variable("river.failure.right", "river-failure",
                                HydrologyReconciliationPlanV2.VariableKind.REACH_BED, 0L)),
                List.of(adjustable(
                        "reconcile.reach.river-failure.0001",
                        HydrologyReconciliationPlanV2.ConstraintKind.REACH_BED,
                        "river-failure", "river.failure.left", "river.failure.right",
                        0L, 0L, maximumAdjustment)));
    }

    private HydrologyReconciliationPlanV2 sealPlan(
            List<HydrologyReconciliationPlanV2.VariableDescriptor> variables,
            List<HydrologyReconciliationPlanV2.Constraint> constraints
    ) {
        HydrologyReconciliationPlanV2 draft = new HydrologyReconciliationPlanV2(
                HydrologyReconciliationPlanV2.VERSION,
                HydrologyReconciliationPlanV2.ALGORITHM_VERSION,
                HydrologyReconciliationPlanV2.SCAN_ORDER_VERSION,
                HydrologyReconciliationModuleV2.MODULE_ID,
                HydrologyReconciliationModuleV2.MODULE_VERSION,
                HydrologyReconciliationModuleV2.STAGE_ID,
                HYDROLOGY_CHECKSUM,
                HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS,
                variables,
                constraints,
                budget(variables.size(), constraints.size()),
                "0".repeat(64));
        return codec.sealHydrologyReconciliationPlan(draft);
    }

    private static HydrologyReconciliationPlanV2.WorkBudget budget(int variables, int constraints) {
        return new HydrologyReconciliationPlanV2.WorkBudget(
                HydrologyReconciliationPlanV2.BUDGET_VERSION,
                HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS,
                variables,
                constraints,
                HydrologyReconciliationPlanV2.estimateWorkUnits(variables, constraints),
                HydrologyReconciliationPlanV2.MAXIMUM_WORK_UNITS,
                HydrologyReconciliationPlanV2.estimateWorkingBytes(variables, constraints),
                HydrologyReconciliationPlanV2.MAXIMUM_WORKING_BYTES,
                HydrologyReconciliationPlanV2.estimateArtifactBytes(variables, constraints),
                HydrologyReconciliationPlanV2.MAXIMUM_ARTIFACT_BYTES);
    }

    private static HydrologyReconciliationPlanV2.VariableDescriptor variable(
            String id,
            String featureId,
            HydrologyReconciliationPlanV2.VariableKind kind,
            long baseline
    ) {
        return new HydrologyReconciliationPlanV2.VariableDescriptor(
                id, featureId, kind, baseline,
                HydrologyReconciliationPlanV2.MINIMUM_FIXED_VALUE,
                HydrologyReconciliationPlanV2.MAXIMUM_FIXED_VALUE);
    }

    private static HydrologyReconciliationPlanV2.Constraint adjustable(
            String id,
            HydrologyReconciliationPlanV2.ConstraintKind kind,
            String featureId,
            String left,
            String right,
            long minimum,
            long maximum,
            long adjustment
    ) {
        return new HydrologyReconciliationPlanV2.Constraint(
                id, kind, featureId, left, right, minimum, maximum,
                HydrologyReconciliationPlanV2.CorrectionPolicy.ADJUST_RIGHT, adjustment);
    }

    private static HydrologyReconciliationStateV2 state(
            HydrologyReconciliationPlanV2 plan,
            List<HydrologyReconciliationStateV2.VariableValue> values
    ) {
        return new HydrologyReconciliationStateV2(plan.canonicalChecksum(), values);
    }

    private static HydrologyReconciliationStateV2.VariableValue value(
            String id,
            long value,
            boolean hardLocked
    ) {
        return new HydrologyReconciliationStateV2.VariableValue(id, value, hardLocked);
    }

    private static long finalValue(HydrologyReconciliationArtifactV2 artifact, String variableId) {
        return artifact.finalValues().stream()
                .filter(value -> value.variableId().equals(variableId))
                .findFirst().orElseThrow().valueMillionths();
    }

    private static void assertFailure(
            HydrologyReconciliationArtifactV2 artifact,
            HydrologyReconciliationArtifactV2.FailureReason expected
    ) {
        assertEquals(HydrologyReconciliationArtifactV2.Status.FAILED, artifact.status());
        assertFalse(artifact.residuals().getFirst().satisfied());
        assertEquals(expected, artifact.residuals().getFirst().failureReason());
    }

    private static TargetFixture fixture(
            String file,
            HydrologyReconciliationPlanV2.ConstraintKind expected
    ) {
        return new TargetFixture(Path.of("examples/v2").resolve(file), expected);
    }

    private static String replaceChecksum(String canonical, String checksum) {
        return canonical.replace(
                "\"canonicalChecksum\":\"" + checksum + "\"",
                "\"canonicalChecksum\":\"" + "0".repeat(64) + "\"");
    }

    private static <T> List<T> reversed(List<T> values) {
        List<T> result = new ArrayList<>(values);
        Collections.reverse(result);
        return result;
    }

    private record TargetFixture(
            Path path,
            HydrologyReconciliationPlanV2.ConstraintKind expectedKind
    ) {
    }
}
