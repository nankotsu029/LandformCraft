package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.NamedSeedDeriverV2;
import com.github.nankotsu029.landformcraft.generator.v2.climate.ClimateFieldModulesV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;

/** Compiles an explicit built-in climate preset into separate prior and final field contracts. */
public final class ClimatePlanCompilerV2 {
    public static final int COARSE_CELL_SIZE = 32;
    private final ClimateFieldModulesV2 modules = new ClimateFieldModulesV2();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public ClimatePlanV2 compile(
            WorldBlueprintV2.Bounds bounds,
            int tileSize,
            long globalSeed,
            HydrologyPlanV2 hydrologyPlan,
            ClimatePlanV2.BaseClimatePreset preset
    ) {
        if (preset == null) {
            throw new IllegalArgumentException("climate preset must be explicit");
        }
        long cells = Math.multiplyExact((long) bounds.width(), bounds.length());
        int coarseWidth = Math.addExact(divideCeiling(bounds.width(), COARSE_CELL_SIZE), 1);
        int coarseLength = Math.addExact(divideCeiling(bounds.length(), COARSE_CELL_SIZE), 1);
        long coarseCells = Math.multiplyExact((long) coarseWidth, coarseLength);
        ClimatePlanV2.CoarsePrior prior = ClimatePlanV2.CoarsePrior.create(
                COARSE_CELL_SIZE, coarseWidth, coarseLength, preset);

        int maximumWindowSize = Math.min(tileSize, 256);
        int windowWidth = Math.min(bounds.width(), maximumWindowSize);
        int windowLength = Math.min(bounds.length(), maximumWindowSize);
        long maximumWorkingBytes = Math.multiplyExact(
                Math.multiplyExact((long) windowWidth, windowLength),
                ClimatePlanV2.MAX_FIELDS * (long) Integer.BYTES);
        long retainedBytes = Math.addExact(
                Math.multiplyExact(coarseCells, 2L * Integer.BYTES), 64L * 1024L);
        long cpuWorkUnits = Math.addExact(
                Math.multiplyExact(cells, ClimatePlanV2.MAX_FIELDS),
                Math.multiplyExact(coarseCells, 2L));
        long namedSeed = NamedSeedDeriverV2.derive(
                globalSeed,
                ClimateFieldModulesV2.PRIOR_MODULE_ID,
                ClimateFieldModulesV2.PRIOR_MODULE_VERSION,
                preset.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                ClimatePlanV2.SEED_NAMESPACE,
                ClimateFieldModulesV2.PRIOR_GENERATOR_VERSION);

        ClimatePlanV2 draft = new ClimatePlanV2(
                ClimatePlanV2.VERSION,
                ClimatePlanV2.FIELD_CONTRACT_VERSION,
                preset,
                ClimateFieldModulesV2.PRIOR_MODULE_ID,
                ClimateFieldModulesV2.PRIOR_MODULE_VERSION,
                ClimateFieldModulesV2.PRIOR_STAGE_ID,
                ClimateFieldModulesV2.FINAL_MODULE_ID,
                ClimateFieldModulesV2.FINAL_MODULE_VERSION,
                ClimateFieldModulesV2.FINAL_STAGE_ID,
                namedSeed,
                ClimatePlanV2.SEED_NAMESPACE,
                bounds.width(),
                bounds.length(),
                bounds.minY(),
                bounds.maxY(),
                bounds.waterLevel(),
                prior,
                ClimatePlanV2.FinalKernel.forPreset(preset),
                new ClimatePlanV2.HydrologyRunoffHandoff(
                        ClimatePlanV2.HydrologyRunoffHandoff.VERSION,
                        ClimatePlanV2.SourcePriorKind.CONSTANT_RUNOFF_PRIOR,
                        hydrologyPlan.canonicalChecksum(),
                        hydrologyPlan.fixedPriors().priorChecksum(),
                        hydrologyPlan.fixedPriors().constantRunoffMillionths(),
                        prior.priorChecksum(),
                        ClimatePlanV2.HydrologyRunoffHandoff.SOURCE_GENERATOR_VERSION,
                        ClimatePlanV2.HydrologyRunoffHandoff.TARGET_GENERATOR_VERSION,
                        ClimatePlanV2.HydrologyRunoffHandoff.TRANSITION_CONTRACT_VERSION,
                        ClimatePlanV2.TransitionMode.EXPLICIT_VERSION_TRANSITION),
                modules.fieldBindings(),
                new ClimatePlanV2.ResourceBudget(
                        ClimatePlanV2.ResourceBudget.VERSION,
                        ClimatePlanV2.MAX_FIELDS,
                        cells,
                        coarseCells,
                        cpuWorkUnits,
                        retainedBytes,
                        maximumWindowSize,
                        maximumWorkingBytes,
                        ClimatePlanV2.MAX_CANONICAL_BYTES),
                "0".repeat(64));
        ClimatePlanV2 sealed = codec.sealClimatePlan(draft);
        sealed.requireHydrologyPlan(hydrologyPlan);
        return sealed;
    }

    public static ClimatePlanV2.BaseClimatePreset requirePreset(String preset) {
        if (preset == null || preset.isBlank()) {
            throw new IllegalArgumentException("climate preset must be explicit");
        }
        try {
            return ClimatePlanV2.BaseClimatePreset.valueOf(preset);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown climate preset: " + preset, exception);
        }
    }

    private static int divideCeiling(int value, int divisor) {
        return Math.floorDiv(Math.addExact(value, divisor - 1), divisor);
    }
}
