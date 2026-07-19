package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcologyPlanCompilerV2Test {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void bundledExampleMatchesCompilerContract() throws IOException {
        EcologyPlanV2 expected = fixture(64, 48, 1L, EcologyPlanV2.EcologyPreset.MANGROVE_ESTUARY).ecology;
        assertEquals(expected, codec.readEcologyPlan(Path.of("examples/v2/ecology/ecology-plan-v2.json")));
    }

    @Test
    void compilesClosedCatalogBoundToUpstreamChecksums(@TempDir Path directory) throws IOException {
        Fixture fixture = fixture(128, 96, 827413L, EcologyPlanV2.EcologyPreset.ALPINE_TREELINE);
        EcologyPlanV2 plan = fixture.ecology;

        assertEquals(EcologyPlanV2.VERSION, plan.planVersion());
        assertEquals(5, plan.catalog().entries().size());
        assertEquals(List.of(
                EcologyPlanV2.AssemblageKind.ALPINE_SHRUB,
                EcologyPlanV2.AssemblageKind.ALPINE_MEADOW), plan.activeAssemblages());
        assertEquals(fixture.climate.canonicalChecksum(), plan.climateBinding().sourceClimatePlanChecksum());
        assertEquals(fixture.waterCondition.canonicalChecksum(),
                plan.waterConditionBinding().sourceWaterConditionPlanChecksum());
        assertEquals(fixture.snow.canonicalChecksum(), plan.snowBinding().sourceSnowPlanChecksum());
        assertEquals(codec.ecologyPlanChecksum(plan), plan.canonicalChecksum());
        plan.requireClimatePlan(fixture.climate);
        plan.requireWaterConditionPlan(fixture.waterCondition);
        plan.requireSnowPlan(fixture.snow);

        Path artifact = directory.resolve("ecology-plan-v2.json");
        codec.writeEcologyPlan(artifact, plan);
        assertEquals(plan, codec.readEcologyPlan(artifact));
        assertEquals(codec.canonicalEcologyPlan(plan), Files.readString(artifact));
        assertTrue(codec.canonicalEcologyPlan(plan).getBytes(StandardCharsets.UTF_8).length
                < plan.budget().maximumCanonicalBytes());
    }

    @Test
    void rejectsUnknownPresetAndTamperedChecksum() throws IOException {
        Fixture fixture = fixture(64, 48, 1L, EcologyPlanV2.EcologyPreset.SHALLOW_CORAL_REEF);
        assertThrows(IllegalArgumentException.class, () -> EcologyPlanCompilerV2.requirePreset("EXTERNAL_SCRIPT"));
        assertThrows(IllegalArgumentException.class, () -> EcologyPlanCompilerV2.requirePreset(""));

        EcologyPlanV2 plan = fixture.ecology;
        Fixture other = fixture(64, 48, 2L, EcologyPlanV2.EcologyPreset.SHALLOW_CORAL_REEF);
        assertThrows(IllegalArgumentException.class, () -> plan.requireClimatePlan(other.climate));
        assertThrows(IllegalArgumentException.class, () -> plan.requireWaterConditionPlan(other.waterCondition));
        assertThrows(IllegalArgumentException.class, () -> plan.requireSnowPlan(other.snow));

        String canonical = codec.canonicalEcologyPlan(plan);
        assertThrows(StructuredDataValidationException.class, () -> codec.readEcologyPlan(
                canonical.replace("\"planVersion\":1", "\"planVersion\":2"), "future-ecology-plan"));
        assertThrows(IOException.class, () -> codec.readEcologyPlan(
                canonical.replace(plan.canonicalChecksum(), "0".repeat(64)), "tampered-ecology-plan"));
    }

    @Test
    void distinctSeedsProduceDistinctDeterministicChecksums() {
        EcologyPlanV2 first = fixture(64, 48, 1L, EcologyPlanV2.EcologyPreset.MANGROVE_ESTUARY).ecology;
        EcologyPlanV2 repeated = fixture(64, 48, 1L, EcologyPlanV2.EcologyPreset.MANGROVE_ESTUARY).ecology;
        EcologyPlanV2 different = fixture(64, 48, 2L, EcologyPlanV2.EcologyPreset.MANGROVE_ESTUARY).ecology;

        assertEquals(first.canonicalChecksum(), repeated.canonicalChecksum());
        assertNotEquals(first.canonicalChecksum(), different.canonicalChecksum());
    }

    private Fixture fixture(int width, int length, long seed, EcologyPlanV2.EcologyPreset preset) {
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(width, length, -64, 255, 50);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        GeologyPlanV2 geology = new GeologyPlanCompilerV2().compile(bounds, 128, seed, hydrology.fixedPriors());
        ClimatePlanV2 climate = new ClimatePlanCompilerV2().compile(
                bounds, 128, seed, hydrology, ClimatePlanV2.BaseClimatePreset.TEMPERATE_MARITIME);
        WaterConditionPlanV2 waterCondition = new WaterConditionPlanCompilerV2().compile(
                bounds, 128, seed, hydrology, climate);
        SnowPlanV2 snow = snowPlan(width, length, climate.minY(), climate.maxY(), seed);
        EcologyPlanV2 ecology = new EcologyPlanCompilerV2().compile(climate, waterCondition, snow, preset);
        return new Fixture(climate, waterCondition, snow, ecology);
    }

    private SnowPlanV2 snowPlan(int width, int length, int minY, int maxY, long seed) {
        long cells = Math.multiplyExact((long) width, length);
        int windowSize = Math.min(256, Math.max(width, length));
        long workingBytes = Math.max(1L, Math.multiplyExact(
                Math.multiplyExact((long) Math.min(width, windowSize), Math.min(length, windowSize)),
                2L * Integer.BYTES));
        SnowPlanV2 draft = new SnowPlanV2(
                SnowPlanV2.VERSION,
                SnowPlanV2.FIELD_CONTRACT_VERSION,
                "generate.snow",
                "0.1.0-v2-4-06",
                "stage.snow",
                seed,
                SnowPlanV2.SEED_NAMESPACE,
                width,
                length,
                minY,
                maxY,
                SnowPlanV2.Kernel.standard(),
                new SnowPlanV2.ClimateBinding(
                        1, "0".repeat(64), "climate.final.temperature", "climate.final.moisture",
                        "snow-climate-binding-v1"),
                List.of(
                        new SnowPlanV2.FieldBinding(
                                "environment.snow.potential", SnowPlanV2.FieldSemantic.SNOW_POTENTIAL,
                                SnowPlanV2.FieldValueType.U16, "generate.snow",
                                SnowPlanV2.Ownership.SINGLE_OWNER, SnowPlanV2.Sampling.NEAREST, 1_000),
                        new SnowPlanV2.FieldBinding(
                                "environment.snow.cover", SnowPlanV2.FieldSemantic.SNOW_COVER,
                                SnowPlanV2.FieldValueType.U16, "generate.snow",
                                SnowPlanV2.Ownership.SINGLE_OWNER, SnowPlanV2.Sampling.NEAREST, 1_000)),
                new SnowPlanV2.ResourceBudget(
                        "snow-field-budget-v1", 2, cells, Math.multiplyExact(cells, 2L), 32_768L, windowSize,
                        workingBytes, 131_072L),
                "0".repeat(64));
        return codec.sealSnowPlan(draft);
    }

    private record Fixture(
            ClimatePlanV2 climate,
            WaterConditionPlanV2 waterCondition,
            SnowPlanV2 snow,
            EcologyPlanV2 ecology
    ) {
    }
}
