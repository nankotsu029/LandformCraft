package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.NamedSeedDeriverV2;
import com.github.nankotsu029.landformcraft.generator.v2.geology.GeologyFoundationModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;

import java.util.List;

/** Compiles the V2-3 uniform geology prior into the typed V2-4-01 field foundation. */
public final class GeologyPlanCompilerV2 {
    private static final int FIELD_COUNT = GeologyPlanV2.FieldSemantic.values().length;
    private final GeologyFoundationModuleV2 module = new GeologyFoundationModuleV2();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public GeologyPlanV2 compile(
            WorldBlueprintV2.Bounds bounds,
            int tileSize,
            long globalSeed,
            HydrologyPlanV2.FixedPriors fixedPriors
    ) {
        if (!HydrologyPlanV2.FixedPriors.v2Phase3Defaults().equals(fixedPriors)) {
            throw new IllegalArgumentException("unsupported source hydrology geology prior");
        }
        long cells = Math.multiplyExact((long) bounds.width(), bounds.length());
        int maximumWindowSize = Math.min(tileSize, 256);
        int windowWidth = Math.min(bounds.width(), maximumWindowSize);
        int windowLength = Math.min(bounds.length(), maximumWindowSize);
        long windowCells = Math.multiplyExact((long) windowWidth, windowLength);
        long readerWorkingBytes = Math.addExact(
                Math.multiplyExact(windowCells, FIELD_COUNT * (long) Integer.BYTES),
                Math.multiplyExact(windowWidth, FIELD_COUNT * (long) Short.BYTES));
        long writerWorkingBytes = Math.addExact(
                GeologyPlanV2.STRICT_READ_BACK_BUFFER_BYTES,
                GeologyPlanV2.MAX_HEADER_BYTES_PER_FIELD);
        long maximumWorkingBytes = Math.max(readerWorkingBytes, writerWorkingBytes);
        long singleArtifactBytes = Math.addExact(
                Math.multiplyExact(cells, Short.BYTES), GeologyPlanV2.MAX_HEADER_BYTES_PER_FIELD);
        long totalArtifactBytes = Math.multiplyExact(singleArtifactBytes, FIELD_COUNT);
        long cpuWorkUnits = Math.multiplyExact(cells, FIELD_COUNT);
        long retainedBytes = GeologyPlanV2.ESTIMATED_RETAINED_BYTES;
        long namedSeed = NamedSeedDeriverV2.derive(
                globalSeed,
                GeologyFoundationModuleV2.MODULE_ID,
                GeologyFoundationModuleV2.MODULE_VERSION,
                "uniform-prior",
                GeologyFoundationModuleV2.SEED_NAMESPACE,
                GeologyFoundationModuleV2.GENERATOR_VERSION);

        GeologyPlanV2 draft = new GeologyPlanV2(
                GeologyPlanV2.VERSION,
                GeologyPlanV2.FIELD_CONTRACT_VERSION,
                GeologyFoundationModuleV2.MODULE_ID,
                GeologyFoundationModuleV2.MODULE_VERSION,
                GeologyFoundationModuleV2.STAGE_ID,
                GeologyPlanV2.PriorReplacement.v2Phase3UniformPrior(),
                namedSeed,
                GeologyFoundationModuleV2.SEED_NAMESPACE,
                bounds.width(),
                bounds.length(),
                List.of(new GeologyPlanV2.ProvinceDescriptor(
                        "province-uniform-prior",
                        1,
                        "formation.uniform-prior",
                        1,
                        fixedPriors.uniformHardnessMillionths(),
                        fixedPriors.uniformPermeabilityMillionths())),
                module.fieldBindings(),
                new GeologyPlanV2.ResourceBudget(
                        GeologyPlanV2.ResourceBudget.VERSION,
                        GeologyPlanV2.MAX_PROVINCES,
                        FIELD_COUNT,
                        cells,
                        cpuWorkUnits,
                        retainedBytes,
                        maximumWindowSize,
                        maximumWorkingBytes,
                        totalArtifactBytes,
                        singleArtifactBytes),
                "0".repeat(64));
        return codec.sealGeologyPlan(draft);
    }
}
