package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.NamedSeedDeriverV2;
import com.github.nankotsu029.landformcraft.generator.v2.environment.water.WaterConditionFieldModulesV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;

import java.util.Objects;

/** Compiles the built-in regional water-condition field contract for V2-4-05. */
public final class WaterConditionPlanCompilerV2 {
    private final WaterConditionFieldModulesV2 modules = new WaterConditionFieldModulesV2();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public WaterConditionPlanV2 compile(
            WorldBlueprintV2.Bounds bounds,
            int tileSize,
            long globalSeed,
            HydrologyPlanV2 hydrologyPlan,
            ClimatePlanV2 climatePlan
    ) {
        Objects.requireNonNull(hydrologyPlan, "hydrologyPlan");
        Objects.requireNonNull(climatePlan, "climatePlan");
        long cells = Math.multiplyExact((long) bounds.width(), bounds.length());
        WaterConditionPlanV2.Kernel kernel = WaterConditionPlanV2.Kernel.standard();
        int maximumWindowSize = Math.min(tileSize, 256);
        int windowWidth = Math.min(bounds.width(), maximumWindowSize);
        int windowLength = Math.min(bounds.length(), maximumWindowSize);
        long maximumWorkingBytes = Math.multiplyExact(
                Math.multiplyExact((long) windowWidth, windowLength),
                WaterConditionPlanV2.MAX_FIELDS * (long) Integer.BYTES);
        long retainedBytes = 48L * 1024L;
        long cpuWorkUnits = Math.multiplyExact(cells, WaterConditionPlanV2.MAX_FIELDS);
        long namedSeed = NamedSeedDeriverV2.derive(
                globalSeed,
                WaterConditionFieldModulesV2.MODULE_ID,
                WaterConditionFieldModulesV2.MODULE_VERSION,
                "regional",
                WaterConditionPlanV2.SEED_NAMESPACE,
                WaterConditionFieldModulesV2.GENERATOR_VERSION);

        WaterConditionPlanV2 draft = new WaterConditionPlanV2(
                WaterConditionPlanV2.VERSION,
                WaterConditionPlanV2.FIELD_CONTRACT_VERSION,
                WaterConditionFieldModulesV2.MODULE_ID,
                WaterConditionFieldModulesV2.MODULE_VERSION,
                WaterConditionFieldModulesV2.STAGE_ID,
                namedSeed,
                WaterConditionPlanV2.SEED_NAMESPACE,
                bounds.width(),
                bounds.length(),
                bounds.minY(),
                bounds.maxY(),
                bounds.waterLevel(),
                kernel,
                new WaterConditionPlanV2.HydrologyBinding(
                        WaterConditionPlanV2.HydrologyBinding.VERSION,
                        hydrologyPlan.canonicalChecksum(),
                        kernel.maximumDistanceBlocks(),
                        WaterConditionPlanV2.HydrologyBinding.CONTRACT_VERSION),
                new WaterConditionPlanV2.ClimateMoistureBinding(
                        WaterConditionPlanV2.ClimateMoistureBinding.VERSION,
                        climatePlan.canonicalChecksum(),
                        WaterConditionPlanV2.ClimateMoistureBinding.MOISTURE_FIELD_ID,
                        WaterConditionPlanV2.ClimateMoistureBinding.CONTRACT_VERSION),
                modules.fieldBindings(),
                new WaterConditionPlanV2.ResourceBudget(
                        WaterConditionPlanV2.ResourceBudget.VERSION,
                        WaterConditionPlanV2.MAX_FIELDS,
                        cells,
                        cpuWorkUnits,
                        retainedBytes,
                        maximumWindowSize,
                        kernel.maximumDistanceBlocks(),
                        maximumWorkingBytes,
                        WaterConditionPlanV2.MAX_CANONICAL_BYTES),
                "0".repeat(64));
        WaterConditionPlanV2 sealed = codec.sealWaterConditionPlan(draft);
        sealed.requireHydrologyPlan(hydrologyPlan);
        sealed.requireClimatePlan(climatePlan);
        return sealed;
    }
}
