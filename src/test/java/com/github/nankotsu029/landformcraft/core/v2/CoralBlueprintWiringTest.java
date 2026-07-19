package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.reef.LandformCoralReefModuleV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoralBlueprintWiringTest {
    private static final Path CORAL_SCENARIO =
            Path.of("examples/v2/diagnostic/scenarios/coral-reef.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void compilesCoralScenarioIntoBlueprintWithReconciliationLink() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(CORAL_SCENARIO);
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                new DiagnosticCompileRequestV2(
                        intent.intentId(),
                        new GenerationBounds(129, 193, -64, 255, 63),
                        64,
                        827_413L,
                        "c".repeat(64),
                        DiagnosticCompileRequestV2.defaultBudget()),
                intent);

        assertEquals(1, blueprint.coralReefPlans().size());
        assertFalse(blueprint.coralReefPlans().getFirst().passHooks().isEmpty());
        assertEquals(
                LandformCoralReefModuleV2.MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.CORAL_REEF).moduleId());
        assertTrue(blueprint.modules().stream().anyMatch(module ->
                module.moduleId().equals(LandformCoralReefModuleV2.MODULE_ID)
                        && module.lifecycleStatus() == ModuleDescriptorV2.LifecycleStatus.SUPPORTED));
        assertTrue(blueprint.hydrologyReconciliationPlan().constraints().stream().anyMatch(constraint ->
                constraint.kind() == HydrologyReconciliationPlanV2.ConstraintKind.REEF_LAGOON_PASS
                        && constraint.featureId().equals("ring-reef")));
        assertEquals(
                HydrologyReconciliationPlanV2.SCAN_ORDER_VERSION,
                blueprint.hydrologyReconciliationPlan().scanOrderVersion());
    }
}
