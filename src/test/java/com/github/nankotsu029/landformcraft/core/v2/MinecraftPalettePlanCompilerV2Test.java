package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;
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

class MinecraftPalettePlanCompilerV2Test {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void bundledExampleMatchesCompilerContract() throws IOException {
        MinecraftPalettePlanV2 expected = fixture(64, 48, 1L).palette;
        assertEquals(expected, codec.readMinecraftPalettePlan(
                Path.of("examples/v2/minecraft/minecraft-palette-plan-v2.json")));
    }

    @Test
    void compilesClosedMappingBoundToMaterialProfileChecksum(@TempDir Path directory) throws IOException {
        Fixture fixture = fixture(128, 96, 827413L);
        MinecraftPalettePlanV2 plan = fixture.palette;

        assertEquals(MinecraftPalettePlanV2.VERSION, plan.planVersion());
        assertEquals(18, plan.catalog().mappings().size());
        assertEquals(MinecraftPalettePlanV2.MINECRAFT_VERSION, plan.compatibility().minecraftVersion());
        assertEquals(MinecraftPalettePlanV2.DATA_VERSION, plan.compatibility().dataVersion());
        assertEquals(MinecraftPalettePlanV2.RESOLVER_VERSION, plan.compatibility().resolverVersion());
        assertEquals(fixture.materialProfile.canonicalChecksum(),
                plan.materialProfileBinding().sourceMaterialProfilePlanChecksum());
        assertEquals(codec.minecraftPalettePlanChecksum(plan), plan.canonicalChecksum());
        plan.requireMaterialProfilePlan(fixture.materialProfile);

        Path artifact = directory.resolve("minecraft-palette-plan-v2.json");
        codec.writeMinecraftPalettePlan(artifact, plan);
        assertEquals(plan, codec.readMinecraftPalettePlan(artifact));
        assertEquals(codec.canonicalMinecraftPalettePlan(plan), Files.readString(artifact));
        assertTrue(codec.canonicalMinecraftPalettePlan(plan).getBytes(StandardCharsets.UTF_8).length
                < plan.budget().maximumCanonicalBytes());
    }

    @Test
    void rejectsMismatchedMaterialProfileAndTamperedChecksum() throws IOException {
        Fixture fixture = fixture(64, 48, 1L);
        MinecraftPalettePlanV2 plan = fixture.palette;
        Fixture other = fixture(64, 48, 2L);
        assertThrows(IllegalArgumentException.class, () -> plan.requireMaterialProfilePlan(other.materialProfile));

        String canonical = codec.canonicalMinecraftPalettePlan(plan);
        assertThrows(StructuredDataValidationException.class, () -> codec.readMinecraftPalettePlan(
                canonical.replace("\"planVersion\":1", "\"planVersion\":2"), "future-palette-plan"));
        assertThrows(IOException.class, () -> codec.readMinecraftPalettePlan(
                canonical.replace(plan.canonicalChecksum(), "0".repeat(64)), "tampered-palette-plan"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readMinecraftPalettePlan(
                canonical.replace(MinecraftPalettePlanV2.RESOLVER_VERSION, "minecraft-palette-resolver-v2"),
                "future-resolver"));
    }

    @Test
    void distinctMaterialProfilesProduceDistinctDeterministicChecksums() {
        MinecraftPalettePlanV2 first = fixture(64, 48, 1L).palette;
        MinecraftPalettePlanV2 repeated = fixture(64, 48, 1L).palette;
        MinecraftPalettePlanV2 different = fixture(64, 48, 2L).palette;

        assertEquals(first.canonicalChecksum(), repeated.canonicalChecksum());
        assertNotEquals(first.materialProfileBinding().sourceMaterialProfilePlanChecksum(),
                different.materialProfileBinding().sourceMaterialProfilePlanChecksum());
        assertNotEquals(first.canonicalChecksum(), different.canonicalChecksum());
    }

    private Fixture fixture(int width, int length, long seed) {
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(width, length, -64, 255, 50);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        GeologyPlanV2 geology = new GeologyPlanCompilerV2().compile(bounds, 128, seed, hydrology.fixedPriors());
        LithologyPlanV2 lithology = new LithologyPlanCompilerV2().compile(geology);
        StrataPlanV2 strata = new StrataPlanCompilerV2().compile(geology, lithology);
        ClimatePlanV2 climate = new ClimatePlanCompilerV2().compile(
                bounds, 128, seed, hydrology, ClimatePlanV2.BaseClimatePreset.TEMPERATE_MARITIME);
        WaterConditionPlanV2 waterCondition = new WaterConditionPlanCompilerV2().compile(
                bounds, 128, seed, hydrology, climate);
        SnowPlanV2 snow = snowPlan(width, length, seed);
        MaterialProfilePlanV2 materialProfile = new MaterialProfilePlanCompilerV2()
                .compile(geology, lithology, strata, waterCondition, snow);
        MinecraftPalettePlanV2 palette = new MinecraftPalettePlanCompilerV2().compile(materialProfile);
        return new Fixture(materialProfile, palette);
    }

    private SnowPlanV2 snowPlan(int width, int length, long seed) {
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
                -64,
                320,
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

    private record Fixture(MaterialProfilePlanV2 materialProfile, MinecraftPalettePlanV2 palette) {
    }
}
