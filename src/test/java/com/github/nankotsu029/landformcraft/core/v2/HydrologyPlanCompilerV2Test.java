package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HydrologyPlanCompilerV2Test {
    private static final WorldBlueprintV2.Bounds BOUNDS = new WorldBlueprintV2.Bounds(400, 400, -64, 255, 50);
    private static final Path DELTA = Path.of("examples/v2/diagnostic/scenarios/delta.terrain-intent-v2.json");
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void compilesEmptyPlanWithFixedPriorsFieldsAndBoundedWork() {
        HydrologyPlanV2 plan = new HydrologyPlanCompilerV2().compile(BOUNDS);

        assertEquals(HydrologyPlanV2.VERSION, plan.planVersion());
        assertEquals(HydrologyPlanV2.GRAPH_CONTRACT_VERSION, plan.graphContractVersion());
        assertEquals(HydrologyIrModuleV2.MODULE_ID, plan.moduleId());
        assertEquals(HydrologyPlanV2.FixedPriors.CHECKSUM, plan.fixedPriors().priorChecksum());
        assertTrue(plan.basins().isEmpty());
        assertTrue(plan.nodes().isEmpty());
        assertTrue(plan.reaches().isEmpty());
        assertEquals(6, plan.fields().size());
        assertEquals(160_000L, plan.budget().globalCellCount());
        assertTrue(plan.budget().estimatedCpuWorkUnits() < 11_000_000L);
        assertTrue(plan.budget().estimatedResidentBytes() < 256L * 1024L * 1024L);
        assertEquals(codec.hydrologyPlanChecksum(plan), plan.canonicalChecksum());
    }

    @Test
    void admitsMaximumHorizontalBoundsWithoutDenseVoxelOrUnboundedGraphBudget() throws IOException {
        WorldBlueprintV2.Bounds maximum = new WorldBlueprintV2.Bounds(1_000, 1_000, -64, 255, 50);
        HydrologyPlanV2 plan = new HydrologyPlanCompilerV2().compile(maximum);

        assertEquals(1_000_000L, plan.budget().globalCellCount());
        assertTrue(plan.budget().maximumBasins() <= 256);
        assertTrue(plan.budget().maximumNodes() <= 4_096);
        assertTrue(plan.budget().maximumReaches() <= 8_192);
        assertTrue(plan.budget().estimatedCpuWorkUnits() <= 65_000_000L);
        assertTrue(plan.budget().estimatedResidentBytes() < 40L * 1024L * 1024L);
        assertTrue(plan.basins().isEmpty());
        assertTrue(plan.nodes().isEmpty());

        TerrainIntentV2 intent = codec.readTerrainIntent(DELTA);
        DiagnosticCompileRequestV2 request = new DiagnosticCompileRequestV2(
                intent.intentId(), new GenerationBounds(1_000, 1_000, -64, 255, 50), 128, 827413L,
                "a".repeat(64), DiagnosticCompileRequestV2.defaultBudget());
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(request, intent);
        assertEquals(plan.budget(), blueprint.hydrologyPlan().budget());
    }

    @Test
    void minimalGraphAndFallPlanCanonicallyRoundTrip(@TempDir Path directory) throws IOException {
        HydrologyPlanV2 plan = fallPlan();
        Path artifact = directory.resolve("hydrology-plan-v2.json");

        codec.writeHydrologyPlan(artifact, plan);
        HydrologyPlanV2 roundTrip = codec.readHydrologyPlan(artifact);

        assertEquals(plan, roundTrip);
        assertEquals(codec.canonicalHydrologyPlan(plan), Files.readString(artifact));
        assertEquals(1, roundTrip.fallPlans().size());
        assertEquals(List.of("reach-downstream", "reach-upstream"),
                roundTrip.reaches().stream().map(HydrologyPlanV2.HydrologyReach::reachId).toList());
    }

    @Test
    void rejectsUnknownEndpointsCyclesRangesAndGraphBudgets() {
        HydrologyPlanV2 minimal = minimalPlan();
        HydrologyPlanV2.HydrologyReach reach = minimal.reaches().getFirst();
        HydrologyPlanV2.HydrologyReach missingEndpoint = new HydrologyPlanV2.HydrologyReach(
                reach.reachId(), reach.basinId(), reach.kind(), reach.fromNodeId(), "missing-node",
                reach.waterBodyId(), reach.rasterSupportRadiusXZ());
        assertThrows(IllegalArgumentException.class, () -> copy(
                minimal, minimal.basins(), minimal.nodes(), List.of(missingEndpoint), minimal.waterBodies(),
                minimal.fallPlans(), minimal.fields(), minimal.budget()));

        assertThrows(IllegalArgumentException.class, this::cyclicPlan);
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlanV2.HydrologyNode(
                "outside", "basin-main", HydrologyPlanV2.NodeKind.SOURCE,
                1_000_000_000L, 0, 50_000_000L, 50_000_000L, List.of(), List.of()));

        HydrologyPlanV2.GraphWorkBudget tooSmall = budget(minimal.budget(), 1, 6);
        assertThrows(IllegalArgumentException.class, () -> copy(
                minimal, minimal.basins(), minimal.nodes(), minimal.reaches(), minimal.waterBodies(),
                minimal.fallPlans(), minimal.fields(), tooSmall));
    }

    @Test
    void rejectsFutureVersionUnknownPriorChecksumAndCanonicalChecksumTampering() {
        HydrologyPlanV2 plan = minimalPlan();
        String canonical = codec.canonicalHydrologyPlan(plan);

        assertThrows(StructuredDataValidationException.class, () -> codec.readHydrologyPlan(
                canonical.replace("\"planVersion\":1", "\"planVersion\":2"), "future-plan"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readHydrologyPlan(
                canonical.replace("UNIFORM_GEOLOGY_PRIOR", "FUTURE_GEOLOGY_PRIOR"), "future-prior"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readHydrologyPlan(
                canonical.replace("hydrology-budget-v1", "hydrology-budget-v2"), "future-budget"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readHydrologyPlan(
                canonical.replace(HydrologyPlanV2.FixedPriors.CHECKSUM, "0".repeat(64)), "prior-tamper"));
        assertThrows(IOException.class, () -> codec.readHydrologyPlan(
                canonical.replace(plan.canonicalChecksum(), "0".repeat(64)), "plan-tamper"));
    }

    @Test
    void rejectsConflictingFieldOwnershipAndCpuOrResidentAdmission() throws IOException {
        HydrologyPlanV2 plan = minimalPlan();
        List<HydrologyPlanV2.FieldBinding> conflicting = new ArrayList<>(plan.fields());
        conflicting.add(new HydrologyPlanV2.FieldBinding(
                "hydrology.second-water-body-id",
                HydrologyPlanV2.FieldSemantic.WATER_BODY_ID,
                HydrologyPlanV2.FieldValueType.I32,
                HydrologyIrModuleV2.MODULE_ID,
                HydrologyPlanV2.Ownership.SINGLE_OWNER));
        HydrologyPlanV2.GraphWorkBudget sevenFields = budget(plan.budget(), plan.budget().maximumNodes(), 7);
        assertThrows(IllegalArgumentException.class, () -> copy(
                plan, plan.basins(), plan.nodes(), plan.reaches(), plan.waterBodies(), plan.fallPlans(),
                conflicting, sevenFields));

        TerrainIntentV2 intent = codec.readTerrainIntent(DELTA);
        HydrologyPlanV2 empty = new HydrologyPlanCompilerV2().compile(BOUNDS);
        WorldBlueprintV2.ResourceBudget defaults = DiagnosticCompileRequestV2.defaultBudget();
        WorldBlueprintV2.ResourceBudget cpuTooSmall = new WorldBlueprintV2.ResourceBudget(
                defaults.maximumFeatures(), defaults.maximumRelations(), defaults.maximumConstraints(),
                defaults.maximumGeometryPoints(), defaults.maximumModules(), defaults.maximumFields(),
                defaults.maximumHaloXZ(), defaults.maximumHaloY(), defaults.maximumResidentBytes(),
                empty.budget().estimatedCpuWorkUnits() - 1L, defaults.maximumArtifactBytes());
        assertBudgetRejected(intent, cpuTooSmall);

        WorldBlueprintV2.ResourceBudget residentTooSmall = new WorldBlueprintV2.ResourceBudget(
                defaults.maximumFeatures(), defaults.maximumRelations(), defaults.maximumConstraints(),
                defaults.maximumGeometryPoints(), defaults.maximumModules(), defaults.maximumFields(),
                defaults.maximumHaloXZ(), defaults.maximumHaloY(), empty.budget().estimatedResidentBytes() - 1L,
                defaults.maximumCpuWorkUnits(), defaults.maximumArtifactBytes());
        assertBudgetRejected(intent, residentTooSmall);
    }

    @Test
    void canonicalOrderAndChecksumIgnoreInputOrderLocaleAndTimezone() {
        HydrologyPlanV2 plan = fallPlan();
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            HydrologyPlanV2 reordered = copy(
                    plan,
                    reversed(plan.basins()),
                    reversed(plan.nodes()),
                    reversed(plan.reaches()),
                    reversed(plan.waterBodies()),
                    reversed(plan.fallPlans()),
                    reversed(plan.fields()),
                    plan.budget());
            assertEquals(plan.canonicalChecksum(), reordered.canonicalChecksum());
            assertEquals(plan.nodes(), reordered.nodes());
            assertEquals(plan.fields(), reordered.fields());
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    private void assertBudgetRejected(TerrainIntentV2 intent, WorldBlueprintV2.ResourceBudget budget) {
        DiagnosticCompileRequestV2 request = new DiagnosticCompileRequestV2(
                intent.intentId(), new GenerationBounds(400, 400, -64, 255, 50), 128, 827413L,
                "a".repeat(64), budget);
        DiagnosticCompilationException exception = assertThrows(
                DiagnosticCompilationException.class,
                () -> new DiagnosticBlueprintCompilerV2().compile(request, intent));
        assertEquals("v2.budget-exceeded", exception.ruleId());
    }

    private HydrologyPlanV2 minimalPlan() {
        HydrologyPlanV2 empty = new HydrologyPlanCompilerV2().compile(BOUNDS);
        List<HydrologyPlanV2.HydrologyNode> nodes = List.of(
                new HydrologyPlanV2.HydrologyNode(
                        "mouth", "basin-main", HydrologyPlanV2.NodeKind.MOUTH,
                        390_000_000L, 390_000_000L, 50_000_000L, 50_000_000L,
                        List.of("reach-main"), List.of()),
                new HydrologyPlanV2.HydrologyNode(
                        "source", "basin-main", HydrologyPlanV2.NodeKind.SOURCE,
                        10_000_000L, 10_000_000L, 60_000_000L, 60_000_000L,
                        List.of(), List.of("reach-main")));
        List<HydrologyPlanV2.HydrologyReach> reaches = List.of(new HydrologyPlanV2.HydrologyReach(
                "reach-main", "basin-main", HydrologyPlanV2.ReachKind.RIVER,
                "source", "mouth", "river-main", 32));
        List<HydrologyPlanV2.DrainageBasin> basins = List.of(new HydrologyPlanV2.DrainageBasin(
                "basin-main", "mouth", List.of("source"), 160_000L,
                HydrologyPlanV2.RunoffClass.FIXED_PRIOR, HydrologyPlanV2.TerminalType.SEA, "river-main"));
        List<HydrologyPlanV2.WaterBodyPlan> waterBodies = List.of(new HydrologyPlanV2.WaterBodyPlan(
                "river-main", HydrologyPlanV2.WaterBodyKind.RIVER, "basin-main",
                List.of("source", "mouth"), 50_000_000L, 60_000_000L));
        return copy(empty, basins, nodes, reaches, waterBodies, List.of(), empty.fields(), empty.budget());
    }

    private HydrologyPlanV2 fallPlan() {
        HydrologyPlanV2 empty = new HydrologyPlanCompilerV2().compile(BOUNDS);
        List<HydrologyPlanV2.HydrologyNode> nodes = List.of(
                new HydrologyPlanV2.HydrologyNode(
                        "source", "basin-fall", HydrologyPlanV2.NodeKind.SOURCE,
                        10_000_000L, 10_000_000L, 80_000_000L, 80_000_000L,
                        List.of(), List.of("reach-upstream")),
                new HydrologyPlanV2.HydrologyNode(
                        "fall-lip", "basin-fall", HydrologyPlanV2.NodeKind.WATERFALL_LIP,
                        180_000_000L, 180_000_000L, 80_000_000L, 80_000_000L,
                        List.of("reach-upstream"), List.of()),
                new HydrologyPlanV2.HydrologyNode(
                        "fall-base", "basin-fall", HydrologyPlanV2.NodeKind.WATERFALL_BASE,
                        180_000_000L, 180_000_000L, 50_000_000L, 50_000_000L,
                        List.of(), List.of("reach-downstream")),
                new HydrologyPlanV2.HydrologyNode(
                        "mouth", "basin-fall", HydrologyPlanV2.NodeKind.MOUTH,
                        390_000_000L, 390_000_000L, 50_000_000L, 50_000_000L,
                        List.of("reach-downstream"), List.of()));
        List<HydrologyPlanV2.HydrologyReach> reaches = List.of(
                new HydrologyPlanV2.HydrologyReach(
                        "reach-upstream", "basin-fall", HydrologyPlanV2.ReachKind.RIVER,
                        "source", "fall-lip", "river-fall", 32),
                new HydrologyPlanV2.HydrologyReach(
                        "reach-downstream", "basin-fall", HydrologyPlanV2.ReachKind.RIVER,
                        "fall-base", "mouth", "river-fall", 32));
        List<HydrologyPlanV2.DrainageBasin> basins = List.of(new HydrologyPlanV2.DrainageBasin(
                "basin-fall", "mouth", List.of("source"), 160_000L,
                HydrologyPlanV2.RunoffClass.FIXED_PRIOR, HydrologyPlanV2.TerminalType.SEA, "river-fall"));
        List<HydrologyPlanV2.WaterBodyPlan> waterBodies = List.of(new HydrologyPlanV2.WaterBodyPlan(
                "river-fall", HydrologyPlanV2.WaterBodyKind.RIVER, "basin-fall",
                List.of("source", "fall-lip", "fall-base", "mouth"), 50_000_000L, 80_000_000L));
        List<HydrologyPlanV2.FallPlan> falls = List.of(new HydrologyPlanV2.FallPlan(
                "main-fall", "basin-fall", "river-fall", "fall-lip", "fall-base",
                "reach-upstream", "reach-downstream", 30_000_000L));
        return copy(empty, basins, nodes, reaches, waterBodies, falls, empty.fields(), empty.budget());
    }

    private HydrologyPlanV2 cyclicPlan() {
        HydrologyPlanV2 empty = new HydrologyPlanCompilerV2().compile(BOUNDS);
        List<HydrologyPlanV2.HydrologyNode> nodes = List.of(
                node("source", HydrologyPlanV2.NodeKind.SOURCE, List.of(), List.of("reach-source-a")),
                node("a", HydrologyPlanV2.NodeKind.CONFLUENCE,
                        List.of("reach-b-a", "reach-source-a"), List.of("reach-a-b")),
                node("b", HydrologyPlanV2.NodeKind.BIFURCATION,
                        List.of("reach-a-b"), List.of("reach-b-a", "reach-b-mouth")),
                node("mouth", HydrologyPlanV2.NodeKind.MOUTH, List.of("reach-b-mouth"), List.of()));
        List<HydrologyPlanV2.HydrologyReach> reaches = List.of(
                reach("reach-source-a", "source", "a"),
                reach("reach-a-b", "a", "b"),
                reach("reach-b-a", "b", "a"),
                reach("reach-b-mouth", "b", "mouth"));
        List<HydrologyPlanV2.DrainageBasin> basins = List.of(new HydrologyPlanV2.DrainageBasin(
                "basin-main", "mouth", List.of("source"), 160_000L,
                HydrologyPlanV2.RunoffClass.FIXED_PRIOR, HydrologyPlanV2.TerminalType.SEA, "river-main"));
        List<HydrologyPlanV2.WaterBodyPlan> waterBodies = List.of(new HydrologyPlanV2.WaterBodyPlan(
                "river-main", HydrologyPlanV2.WaterBodyKind.RIVER, "basin-main",
                List.of("source", "a", "b", "mouth"), 50_000_000L, 60_000_000L));
        return copy(empty, basins, nodes, reaches, waterBodies, List.of(), empty.fields(), empty.budget());
    }

    private static HydrologyPlanV2.HydrologyNode node(
            String id,
            HydrologyPlanV2.NodeKind kind,
            List<String> incoming,
            List<String> outgoing
    ) {
        return new HydrologyPlanV2.HydrologyNode(
                id, "basin-main", kind, 10_000_000L, 10_000_000L,
                50_000_000L, 50_000_000L, incoming, outgoing);
    }

    private static HydrologyPlanV2.HydrologyReach reach(String id, String from, String to) {
        return new HydrologyPlanV2.HydrologyReach(
                id, "basin-main", HydrologyPlanV2.ReachKind.RIVER, from, to, "river-main", 16);
    }

    private HydrologyPlanV2 copy(
            HydrologyPlanV2 source,
            List<HydrologyPlanV2.DrainageBasin> basins,
            List<HydrologyPlanV2.HydrologyNode> nodes,
            List<HydrologyPlanV2.HydrologyReach> reaches,
            List<HydrologyPlanV2.WaterBodyPlan> waterBodies,
            List<HydrologyPlanV2.FallPlan> falls,
            List<HydrologyPlanV2.FieldBinding> fields,
            HydrologyPlanV2.GraphWorkBudget budget
    ) {
        HydrologyPlanV2 draft = new HydrologyPlanV2(
                source.planVersion(), source.graphContractVersion(), source.moduleId(), source.moduleVersion(),
                source.fixedPriors(), basins, nodes, reaches, waterBodies, falls, fields, budget, "0".repeat(64));
        return codec.sealHydrologyPlan(draft);
    }

    private static HydrologyPlanV2.GraphWorkBudget budget(
            HydrologyPlanV2.GraphWorkBudget source,
            int maximumNodes,
            int maximumFields
    ) {
        return new HydrologyPlanV2.GraphWorkBudget(
                source.budgetVersion(), source.maximumBasins(), maximumNodes,
                source.maximumReaches(), source.maximumWaterBodies(),
                source.maximumFallPlans(), maximumFields, source.globalCellCount(),
                source.estimatedCpuWorkUnits(), source.estimatedResidentBytes());
    }

    private static <T> List<T> reversed(List<T> values) {
        List<T> result = new ArrayList<>(values);
        Collections.reverse(result);
        return result;
    }
}
