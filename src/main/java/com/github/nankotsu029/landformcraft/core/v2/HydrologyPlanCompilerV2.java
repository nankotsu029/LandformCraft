package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;

import java.util.List;

/** Compiles the empty graph envelope plus the bounded V2-3-02 global-routing admission budget. */
public final class HydrologyPlanCompilerV2 {
    private final HydrologyIrModuleV2 module = new HydrologyIrModuleV2();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public HydrologyPlanV2 compile(WorldBlueprintV2.Bounds bounds) {
        long cells = Math.multiplyExact((long) bounds.width(), bounds.length());
        int maximumBasins = Math.toIntExact(Math.min(256L, Math.max(1L, divideCeiling(cells, 4_096L))));
        int maximumNodes = Math.toIntExact(Math.min(4_096L, Math.max(2L, divideCeiling(cells, 256L))));
        int maximumReaches = Math.min(8_192, Math.multiplyExact(maximumNodes, 2));
        int maximumWaterBodies = Math.min(1_024, Math.multiplyExact(maximumBasins, 2));
        int maximumFalls = Math.min(256, Math.max(1, maximumNodes / 16));

        long cpu = Math.addExact(
                Math.multiplyExact(cells, 64L),
                Math.addExact(Math.multiplyExact((long) maximumNodes, 64L),
                        Math.multiplyExact((long) maximumReaches, 48L)));
        long resident = Math.addExact(
                Math.addExact(Math.multiplyExact(cells, 32L), 4L * 1024L * 1024L),
                Math.addExact(
                        Math.multiplyExact((long) maximumNodes, 96L),
                        Math.addExact(
                                Math.multiplyExact((long) maximumReaches, 80L),
                                Math.addExact(
                                        Math.multiplyExact((long) maximumBasins, 64L),
                                        Math.addExact(
                                                Math.multiplyExact((long) maximumWaterBodies, 64L),
                                                Math.multiplyExact((long) maximumFalls, 64L))))));

        HydrologyPlanV2 draft = new HydrologyPlanV2(
                HydrologyPlanV2.VERSION,
                HydrologyPlanV2.GRAPH_CONTRACT_VERSION,
                HydrologyIrModuleV2.MODULE_ID,
                HydrologyIrModuleV2.MODULE_VERSION,
                HydrologyPlanV2.FixedPriors.v2Phase3Defaults(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                module.fieldBindings(),
                new HydrologyPlanV2.GraphWorkBudget(
                        HydrologyPlanV2.GraphWorkBudget.VERSION,
                        maximumBasins,
                        maximumNodes,
                        maximumReaches,
                        maximumWaterBodies,
                        maximumFalls,
                        module.fieldBindings().size(),
                        cells,
                        cpu,
                        resident),
                "0".repeat(64));
        return codec.sealHydrologyPlan(draft);
    }

    private static long divideCeiling(long value, long divisor) {
        return Math.floorDiv(Math.addExact(value, divisor - 1L), divisor);
    }
}
