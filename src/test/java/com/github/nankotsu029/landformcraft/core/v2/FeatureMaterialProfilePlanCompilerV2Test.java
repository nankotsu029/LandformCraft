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
import com.github.nankotsu029.landformcraft.model.v2.material.feature.FeatureMaterialProfilePlanV2;
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

class FeatureMaterialProfilePlanCompilerV2Test {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void bundledExampleMatchesCompilerContract() throws IOException {
        FeatureMaterialProfilePlanV2 expected = fixture(64, 48, 1L).featureMaterial;
        assertEquals(expected, codec.readFeatureMaterialProfilePlan(
                Path.of("examples/v2/material/feature-material-profile-plan-v2.json")));
    }

    @Test
    void compilesClosedCatalogBoundToUpstreamChecksums(@TempDir Path directory) throws IOException {
        Fixture fixture = fixture(128, 96, 827413L);
        FeatureMaterialProfilePlanV2 plan = fixture.featureMaterial;

        assertEquals(FeatureMaterialProfilePlanV2.VERSION, plan.planVersion());
        assertEquals(6, plan.catalog().entries().size());
        assertEquals(FeatureMaterialProfilePlanV2.ResolutionRule.standardOrder(), plan.resolutionRules());
        assertEquals(FeatureMaterialProfilePlanV2.ConflictRule.standardOrder(), plan.conflictRules());
        assertEquals(fixture.material.canonicalChecksum(),
                plan.materialProfileBinding().sourceMaterialProfilePlanChecksum());
        assertEquals(fixture.geology.canonicalChecksum(), plan.geologyBinding().sourceGeologyPlanChecksum());
        assertEquals(codec.featureMaterialProfilePlanChecksum(plan), plan.canonicalChecksum());
        plan.requireMaterialProfilePlan(fixture.material);
        plan.requireGeologyPlan(fixture.geology, fixture.lithology, fixture.strata);

        Path artifact = directory.resolve("feature-material-profile-plan-v2.json");
        codec.writeFeatureMaterialProfilePlan(artifact, plan);
        assertEquals(plan, codec.readFeatureMaterialProfilePlan(artifact));
        assertEquals(codec.canonicalFeatureMaterialProfilePlan(plan), Files.readString(artifact));
        assertTrue(codec.canonicalFeatureMaterialProfilePlan(plan).getBytes(StandardCharsets.UTF_8).length
                < plan.budget().maximumCanonicalBytes());
    }

    @Test
    void rejectsMismatchedUpstreamAndTamperedChecksum() throws IOException {
        Fixture fixture = fixture(64, 48, 1L);
        FeatureMaterialProfilePlanV2 plan = fixture.featureMaterial;
        Fixture other = fixture(64, 48, 2L);
        assertThrows(IllegalArgumentException.class, () -> plan.requireMaterialProfilePlan(other.material));
        assertThrows(IllegalArgumentException.class, () -> plan.requireGeologyPlan(
                other.geology, fixture.lithology, fixture.strata));

        String canonical = codec.canonicalFeatureMaterialProfilePlan(plan);
        assertThrows(StructuredDataValidationException.class, () -> codec.readFeatureMaterialProfilePlan(
                canonical.replace("\"planVersion\":1", "\"planVersion\":2"), "future-feature-material"));
        assertThrows(IOException.class, () -> codec.readFeatureMaterialProfilePlan(
                canonical.replace(plan.canonicalChecksum(), "0".repeat(64)), "tampered-feature-material"));
    }

    @Test
    void distinctSeedsProduceDistinctDeterministicChecksums() {
        FeatureMaterialProfilePlanV2 first = fixture(64, 48, 1L).featureMaterial;
        FeatureMaterialProfilePlanV2 repeated = fixture(64, 48, 1L).featureMaterial;
        FeatureMaterialProfilePlanV2 different = fixture(64, 48, 2L).featureMaterial;
        assertEquals(first.canonicalChecksum(), repeated.canonicalChecksum());
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
        SnowPlanV2 snow = snowPlan(width, length, climate.minY(), climate.maxY(), seed);
        MaterialProfilePlanV2 material = new MaterialProfilePlanCompilerV2()
                .compile(geology, lithology, strata, waterCondition, snow);
        FeatureMaterialProfilePlanV2 featureMaterial = new FeatureMaterialProfilePlanCompilerV2()
                .compile(material, geology, lithology, strata, List.of(), List.of());
        return new Fixture(geology, lithology, strata, material, featureMaterial);
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
            GeologyPlanV2 geology,
            LithologyPlanV2 lithology,
            StrataPlanV2 strata,
            MaterialProfilePlanV2 material,
            FeatureMaterialProfilePlanV2 featureMaterial
    ) {
    }
}
