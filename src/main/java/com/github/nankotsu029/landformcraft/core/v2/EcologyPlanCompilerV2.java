package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Compiles the frozen V2-4-11 ecology placement plan: closed assemblage catalog, preset-selected
 * active descriptors, checksum-bound climate/water/snow inputs, and sparse placement budgets.
 */
public final class EcologyPlanCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public EcologyPlanV2 compile(
            ClimatePlanV2 climatePlan,
            WaterConditionPlanV2 waterConditionPlan,
            SnowPlanV2 snowPlan,
            EcologyPlanV2.EcologyPreset ecologyPreset
    ) {
        Objects.requireNonNull(climatePlan, "climatePlan");
        Objects.requireNonNull(waterConditionPlan, "waterConditionPlan");
        Objects.requireNonNull(snowPlan, "snowPlan");
        Objects.requireNonNull(ecologyPreset, "ecologyPreset");
        EcologyPlanV2.EcologyPreset preset = ecologyPreset;
        if (climatePlan.width() != waterConditionPlan.width()
                || climatePlan.length() != waterConditionPlan.length()
                || climatePlan.width() != snowPlan.width()
                || climatePlan.length() != snowPlan.length()) {
            throw new IllegalArgumentException("ecology upstream plans must share dimensions");
        }

        long cells = Math.multiplyExact((long) climatePlan.width(), climatePlan.length());
        int activeCount = preset.assemblages().size();
        int maximumWindowSize = Math.min(256, Math.max(climatePlan.width(), climatePlan.length()));
        int windowWidth = Math.min(climatePlan.width(), maximumWindowSize);
        int windowLength = Math.min(climatePlan.length(), maximumWindowSize);
        long maximumWorkingBytes = Math.multiplyExact(
                Math.multiplyExact((long) windowWidth, windowLength), (long) Integer.BYTES);
        long placementCap = Math.min(
                65_536L,
                Math.max(1L, Math.multiplyExact((long) windowWidth, windowLength) / 4L));
        long cpuWorkUnits = Math.multiplyExact(cells, (long) Math.max(1, activeCount));

        EcologyPlanV2 draft = new EcologyPlanV2(
                EcologyPlanV2.VERSION,
                EcologyPlanV2.PLACEMENT_CONTRACT_VERSION,
                EcologyPlanV2.MODULE_ID,
                EcologyPlanV2.MODULE_VERSION,
                EcologyPlanV2.STAGE_ID,
                climatePlan.namedSeed(),
                EcologyPlanV2.SEED_NAMESPACE,
                climatePlan.width(),
                climatePlan.length(),
                climatePlan.minY(),
                climatePlan.maxY(),
                preset,
                new EcologyPlanV2.ClimateBinding(
                        EcologyPlanV2.ClimateBinding.VERSION,
                        climatePlan.canonicalChecksum(),
                        EcologyPlanV2.ClimateBinding.TEMPERATURE_FIELD_ID,
                        EcologyPlanV2.ClimateBinding.CONTRACT_VERSION),
                new EcologyPlanV2.WaterConditionBinding(
                        EcologyPlanV2.WaterConditionBinding.VERSION,
                        waterConditionPlan.canonicalChecksum(),
                        EcologyPlanV2.WaterConditionBinding.WETNESS_FIELD_ID,
                        EcologyPlanV2.WaterConditionBinding.SALINITY_FIELD_ID,
                        EcologyPlanV2.WaterConditionBinding.HYDROPERIOD_FIELD_ID,
                        EcologyPlanV2.WaterConditionBinding.CONTRACT_VERSION),
                new EcologyPlanV2.SnowBinding(
                        EcologyPlanV2.SnowBinding.VERSION,
                        snowPlan.canonicalChecksum(),
                        EcologyPlanV2.SnowBinding.SNOW_COVER_FIELD_ID,
                        EcologyPlanV2.SnowBinding.CONTRACT_VERSION),
                builtInCatalog(),
                preset.assemblages(),
                EcologyPlanV2.Kernel.standard(),
                new EcologyPlanV2.ResourceBudget(
                        EcologyPlanV2.ResourceBudget.VERSION,
                        cells,
                        EcologyPlanV2.ASSEMBLAGE_COUNT,
                        activeCount,
                        cpuWorkUnits,
                        12L * 1024L,
                        maximumWindowSize,
                        maximumWorkingBytes,
                        placementCap,
                        EcologyPlanV2.MAX_CANONICAL_BYTES),
                "0".repeat(64));
        draft.requireClimatePlan(climatePlan);
        draft.requireWaterConditionPlan(waterConditionPlan);
        draft.requireSnowPlan(snowPlan);
        return codec.sealEcologyPlan(draft);
    }

    public EcologyPlanV2 compile(
            ClimatePlanV2 climatePlan,
            WaterConditionPlanV2 waterConditionPlan,
            SnowPlanV2 snowPlan,
            String ecologyPreset
    ) {
        return compile(climatePlan, waterConditionPlan, snowPlan, requirePreset(ecologyPreset));
    }

    public static EcologyPlanV2.EcologyPreset requirePreset(String preset) {
        if (preset == null || preset.isBlank()) {
            throw new IllegalArgumentException("ecology preset must be explicit");
        }
        try {
            return EcologyPlanV2.EcologyPreset.valueOf(preset);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown ecology preset: " + preset, exception);
        }
    }

    private static EcologyPlanV2.Catalog builtInCatalog() {
        List<EcologyPlanV2.Entry> entries = Arrays.stream(EcologyPlanV2.AssemblageKind.values())
                .map(kind -> new EcologyPlanV2.Entry(
                        kind,
                        kind.assemblageId(),
                        kind.compactCode(),
                        kind.habitatClass(),
                        kind.layer(),
                        kind.supportRule(),
                        kind.densityMillionths(),
                        kind.minSpacingBlocks(),
                        kind.clusterScaleBlocks()))
                .toList();
        return new EcologyPlanV2.Catalog(
                EcologyPlanV2.Catalog.VERSION,
                EcologyPlanV2.Catalog.ID,
                EcologyPlanV2.Catalog.CONTRACT_VERSION,
                entries,
                new EcologyPlanV2.CatalogBudget(
                        EcologyPlanV2.CatalogBudget.VERSION,
                        EcologyPlanV2.ASSEMBLAGE_COUNT,
                        16L * 1024L));
    }
}
