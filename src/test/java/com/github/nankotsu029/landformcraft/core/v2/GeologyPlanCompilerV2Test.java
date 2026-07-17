package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.geology.GeologyFoundationModuleV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeologyPlanCompilerV2Test {
    private static final Path DELTA =
            Path.of("examples/v2/diagnostic/scenarios/delta.terrain-intent-v2.json");
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void compilesTypedUniformPriorWithoutReinterpretingHydrology() {
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(400, 300, -64, 255, 50);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        GeologyPlanV2 plan = new GeologyPlanCompilerV2().compile(
                bounds, 128, 827413L, hydrology.fixedPriors());

        assertEquals(GeologyPlanV2.VERSION, plan.planVersion());
        assertEquals(GeologyFoundationModuleV2.MODULE_ID, plan.moduleId());
        assertEquals(HydrologyPlanV2.FixedPriors.CHECKSUM,
                plan.priorReplacement().sourcePriorChecksum());
        assertEquals(hydrology.fixedPriors().uniformHardnessMillionths(),
                plan.provinces().getFirst().hardnessMillionths());
        assertEquals(hydrology.fixedPriors().uniformPermeabilityMillionths(),
                plan.provinces().getFirst().permeabilityMillionths());
        assertEquals(4, plan.fields().size());
        assertEquals(120_000L, plan.budget().globalCellCount());
        assertEquals(codec.geologyPlanChecksum(plan), plan.canonicalChecksum());
        assertEquals(HydrologyPlanV2.FixedPriors.CHECKSUM, hydrology.fixedPriors().priorChecksum());
    }

    @Test
    void emptyAndMinimalPlansStrictlyRoundTrip(@TempDir Path directory) throws IOException {
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(64, 48, -64, 255, 50);
        GeologyPlanV2 minimal = compile(bounds, 1L);
        GeologyPlanV2 empty = copy(minimal, List.of(), minimal.fields(), minimal.budget());

        Path minimalFile = directory.resolve("minimal-geology-plan-v2.json");
        Path emptyFile = directory.resolve("empty-geology-plan-v2.json");
        codec.writeGeologyPlan(minimalFile, minimal);
        codec.writeGeologyPlan(emptyFile, empty);

        assertEquals(minimal, codec.readGeologyPlan(minimalFile));
        assertEquals(empty, codec.readGeologyPlan(emptyFile));
        assertEquals(codec.canonicalGeologyPlan(minimal), Files.readString(minimalFile));
        assertTrue(empty.provinces().isEmpty());
    }

    @Test
    void rejectsFutureVersionUnknownIdsOwnershipConflictAndChecksumTampering() {
        GeologyPlanV2 plan = compile(new WorldBlueprintV2.Bounds(64, 48, -64, 255, 50), 1L);
        String canonical = codec.canonicalGeologyPlan(plan);

        assertThrows(StructuredDataValidationException.class, () -> codec.readGeologyPlan(
                canonical.replace("\"planVersion\":1", "\"planVersion\":2"), "future-geology-plan"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGeologyPlan(
                canonical.replace("geology-field-contract-v1", "geology-field-contract-v2"),
                "future-geology-contract"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGeologyPlan(
                canonical.replace("province-uniform-prior", "UNKNOWN_PROVINCE"),
                "unknown-geology-id"));
        assertThrows(IOException.class, () -> codec.readGeologyPlan(
                canonical.replace(plan.canonicalChecksum(), "0".repeat(64)), "tampered-geology-plan"));

        List<GeologyPlanV2.FieldBinding> conflict = new ArrayList<>(plan.fields().subList(0, 3));
        conflict.add(new GeologyPlanV2.FieldBinding(
                "geology.province-id",
                GeologyPlanV2.FieldSemantic.PROVINCE_ID,
                GeologyPlanV2.FieldValueType.U16,
                "v2.environment.conflicting-owner",
                GeologyPlanV2.Ownership.SINGLE_OWNER,
                "LFC_GRID_V1"));
        assertThrows(IllegalArgumentException.class,
                () -> copy(plan, plan.provinces(), conflict, plan.budget()));

        GeologyPlanV2.ResourceBudget budget = plan.budget();
        GeologyPlanV2.ResourceBudget understated = new GeologyPlanV2.ResourceBudget(
                budget.budgetVersion(), budget.maximumProvinces(), budget.maximumFields(),
                budget.globalCellCount(), budget.estimatedCpuWorkUnits(), budget.estimatedRetainedBytes(),
                budget.maximumWindowSize(), budget.maximumWorkingBytes() - 1L,
                budget.estimatedArtifactBytes(), budget.maximumSingleArtifactBytes());
        assertThrows(IllegalArgumentException.class,
                () -> copy(plan, plan.provinces(), plan.fields(), understated));
    }

    @Test
    void blueprintBindsStageOwnershipAndPriorReplacement() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(DELTA);
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                request(intent.intentId(), 1_000, 1_000, DiagnosticCompileRequestV2.defaultBudget()), intent);

        assertEquals(HydrologyPlanV2.FixedPriors.CHECKSUM,
                blueprint.geologyPlan().priorReplacement().sourcePriorChecksum());
        assertEquals(1_000_000L, blueprint.geologyPlan().budget().globalCellCount());
        assertTrue(blueprint.geologyPlan().budget().estimatedArtifactBytes() < 8_100_000L);
        assertTrue(blueprint.geologyPlan().budget().maximumWorkingBytes() < 1_100_000L);
        assertTrue(blueprint.geologyPlan().budget().estimatedRetainedBytes() < 128L * 1024L);
        assertTrue(blueprint.stages().stream().anyMatch(stage ->
                stage.stageId().equals(GeologyFoundationModuleV2.STAGE_ID)
                        && stage.dependsOnStageIds().contains("compile.features")));
        assertTrue(blueprint.stages().stream().anyMatch(stage ->
                stage.stageId().equals("compile.hydrology-ir")
                        && stage.dependsOnStageIds().contains(GeologyFoundationModuleV2.STAGE_ID)));
        for (GeologyPlanV2.FieldBinding binding : blueprint.geologyPlan().fields()) {
            assertTrue(blueprint.fieldOwnership().stream().anyMatch(owner ->
                    owner.fieldId().equals(binding.fieldId())
                            && owner.moduleId().equals(GeologyFoundationModuleV2.MODULE_ID)));
        }
    }

    @Test
    void namedSeedCanonicalOrderLocaleAndTimezoneAreStable() {
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(257, 259, -64, 255, 50);
        GeologyPlanV2 first = compile(bounds, 827413L);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            GeologyPlanV2 reordered = copy(
                    compile(bounds, 827413L),
                    List.of(first.provinces().getFirst()),
                    first.fields().reversed(),
                    first.budget());
            assertEquals(first.namedSeed(), reordered.namedSeed());
            assertEquals(first.canonicalChecksum(), reordered.canonicalChecksum());
            assertEquals(first.fields(), reordered.fields());
            assertNotEquals(first.namedSeed(), compile(bounds, 827414L).namedSeed());
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void rejectsCpuResidentAndArtifactAdmissionBeforeAllocation() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(DELTA);
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(400, 400, -64, 255, 50);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        GeologyPlanV2 geology = new GeologyPlanCompilerV2().compile(
                bounds, 128, 827413L, hydrology.fixedPriors());
        WorldBlueprintV2.ResourceBudget defaults = DiagnosticCompileRequestV2.defaultBudget();

        assertRejected(intent, budget(defaults,
                hydrology.budget().estimatedResidentBytes()
                        + geology.budget().estimatedRetainedBytes()
                        + geology.budget().maximumWorkingBytes() - 1L,
                defaults.maximumCpuWorkUnits(), defaults.maximumArtifactBytes()));
        assertRejected(intent, budget(defaults, defaults.maximumResidentBytes(),
                hydrology.budget().estimatedCpuWorkUnits()
                        + geology.budget().estimatedCpuWorkUnits() - 1L,
                defaults.maximumArtifactBytes()));
        assertRejected(intent, budget(defaults, defaults.maximumResidentBytes(),
                defaults.maximumCpuWorkUnits(), geology.budget().estimatedArtifactBytes() - 1L));
    }

    private GeologyPlanV2 compile(WorldBlueprintV2.Bounds bounds, long seed) {
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        return new GeologyPlanCompilerV2().compile(bounds, 128, seed, hydrology.fixedPriors());
    }

    private GeologyPlanV2 copy(
            GeologyPlanV2 source,
            List<GeologyPlanV2.ProvinceDescriptor> provinces,
            List<GeologyPlanV2.FieldBinding> fields,
            GeologyPlanV2.ResourceBudget budget
    ) {
        return codec.sealGeologyPlan(new GeologyPlanV2(
                source.planVersion(), source.fieldContractVersion(), source.moduleId(), source.moduleVersion(),
                source.stageId(), source.priorReplacement(), source.namedSeed(), source.seedNamespace(),
                source.width(), source.length(), provinces, fields, budget, "0".repeat(64)));
    }

    private void assertRejected(TerrainIntentV2 intent, WorldBlueprintV2.ResourceBudget budget) {
        DiagnosticCompilationException exception = assertThrows(
                DiagnosticCompilationException.class,
                () -> new DiagnosticBlueprintCompilerV2().compile(
                        request(intent.intentId(), 400, 400, budget), intent));
        assertEquals("v2.budget-exceeded", exception.ruleId());
    }

    private static DiagnosticCompileRequestV2 request(
            String requestId,
            int width,
            int length,
            WorldBlueprintV2.ResourceBudget budget
    ) {
        return new DiagnosticCompileRequestV2(
                requestId, new GenerationBounds(width, length, -64, 255, 50), 128, 827413L,
                "a".repeat(64), budget);
    }

    private static WorldBlueprintV2.ResourceBudget budget(
            WorldBlueprintV2.ResourceBudget source,
            long resident,
            long cpu,
            long artifact
    ) {
        return new WorldBlueprintV2.ResourceBudget(
                source.maximumFeatures(), source.maximumRelations(), source.maximumConstraints(),
                source.maximumGeometryPoints(), source.maximumModules(), source.maximumFields(),
                source.maximumHaloXZ(), source.maximumHaloY(), resident, cpu, artifact);
    }
}
