package com.github.nankotsu029.landformcraft.generator.v2.material;

import com.github.nankotsu029.landformcraft.core.v2.GeologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.HydrologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.LithologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.MaterialProfilePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.StrataPlanCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialProfileResolverV2Test {
    private static final int ROCK_PROVINCE = 1;
    private static final int SEDIMENT_PROVINCE = 2;
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void resolvesWetRockSedimentAndSnowProfiles() {
        Fixture fixture = fixture(128, 96, 827413L);
        MaterialProfileResolverV2 resolver = resolver(fixture);

        assertEquals(
                MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_EXPOSED.compactCode(),
                resolver.classCodeAt(MaterialProfilePlanV2.SurfaceAspect.SURFACE, 0, 0,
                        new MaterialProfileResolverV2.CellInputs(ROCK_PROVINCE, 0, 0)));
        assertEquals(
                MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_WET.compactCode(),
                resolver.classCodeAt(MaterialProfilePlanV2.SurfaceAspect.SURFACE, 0, 0,
                        new MaterialProfileResolverV2.CellInputs(ROCK_PROVINCE, 600, 0)));
        assertEquals(
                MaterialProfilePlanV2.SemanticMaterialClass.SEDIMENT_EXPOSED.compactCode(),
                resolver.classCodeAt(MaterialProfilePlanV2.SurfaceAspect.SURFACE, 5, 5,
                        new MaterialProfileResolverV2.CellInputs(SEDIMENT_PROVINCE, 0, 0)));
        assertEquals(
                MaterialProfilePlanV2.SemanticMaterialClass.SEDIMENT_WET.compactCode(),
                resolver.classCodeAt(MaterialProfilePlanV2.SurfaceAspect.SURFACE, 5, 5,
                        new MaterialProfileResolverV2.CellInputs(SEDIMENT_PROVINCE, 600, 0)));
        assertEquals(
                MaterialProfilePlanV2.SemanticMaterialClass.SNOW_COVERED_ROCK.compactCode(),
                resolver.classCodeAt(MaterialProfilePlanV2.SurfaceAspect.SURFACE, 0, 0,
                        new MaterialProfileResolverV2.CellInputs(ROCK_PROVINCE, 600, 400)));
        assertEquals(
                MaterialProfilePlanV2.SemanticMaterialClass.SNOW_COVERED_SEDIMENT.compactCode(),
                resolver.classCodeAt(MaterialProfilePlanV2.SurfaceAspect.SURFACE, 5, 5,
                        new MaterialProfileResolverV2.CellInputs(SEDIMENT_PROVINCE, 0, 400)));
        assertEquals(GeologyPlanV2.NO_DATA_RAW, resolver.classCodeAt(
                MaterialProfilePlanV2.SurfaceAspect.SURFACE, 0, 0,
                new MaterialProfileResolverV2.CellInputs(GeologyPlanV2.NO_DATA_RAW, 0, 0)));
    }

    @Test
    void surfaceCeilingAndFloorHookChangesSnowResolutionButNotWetness() {
        Fixture fixture = fixture(128, 96, 827413L);
        MaterialProfileResolverV2 resolver = resolver(fixture);
        MaterialProfileResolverV2.CellInputs wetAndSnowy =
                new MaterialProfileResolverV2.CellInputs(ROCK_PROVINCE, 600, 400);

        assertEquals(
                MaterialProfilePlanV2.SemanticMaterialClass.SNOW_COVERED_ROCK.compactCode(),
                resolver.classCodeAt(MaterialProfilePlanV2.SurfaceAspect.SURFACE, 0, 0, wetAndSnowy));
        assertEquals(
                MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_WET.compactCode(),
                resolver.classCodeAt(MaterialProfilePlanV2.SurfaceAspect.CEILING, 0, 0, wetAndSnowy));
        assertEquals(
                MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_WET.compactCode(),
                resolver.classCodeAt(MaterialProfilePlanV2.SurfaceAspect.FLOOR, 0, 0, wetAndSnowy));
    }

    @Test
    void rejectsUnknownProvinceAndOutOfRangeInputs() {
        Fixture fixture = fixture(64, 48, 1L);
        MaterialProfileResolverV2 resolver = resolver(fixture);
        assertThrows(IllegalArgumentException.class, () -> resolver.classCodeAt(
                MaterialProfilePlanV2.SurfaceAspect.SURFACE, 0, 0,
                new MaterialProfileResolverV2.CellInputs(99, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> resolver.classCodeAt(
                MaterialProfilePlanV2.SurfaceAspect.SURFACE, 0, 0,
                new MaterialProfileResolverV2.CellInputs(ROCK_PROVINCE, 1_500, 0)));
        assertThrows(IllegalArgumentException.class, () -> resolver.classCodeAt(
                MaterialProfilePlanV2.SurfaceAspect.SURFACE, 0, 0,
                new MaterialProfileResolverV2.CellInputs(ROCK_PROVINCE, 0, -1)));
    }

    @Test
    void wholeTileThreadLocaleAndTimezoneAreStable() throws Exception {
        Fixture fixture = fixture(257, 259, 827413L);
        MaterialProfileResolverV2 resolver = resolver(fixture);
        int width = 257;
        int length = 259;
        MaterialProfileResolverV2.CellInputSource inputs = (x, z) ->
                new MaterialProfileResolverV2.CellInputs(
                        Math.floorMod(x + z, 2) == 0 ? ROCK_PROVINCE : SEDIMENT_PROVINCE,
                        Math.floorMod(x * 13 + z * 17, 1_001),
                        Math.floorMod(x * 7 + z * 11, 1_001));

        int[] whole = sample(resolver, width, length, 1, 1, false, inputs);
        assertArrayEquals(whole, sample(resolver, width, length, 128, 1, false, inputs));
        assertArrayEquals(whole, sample(resolver, width, length, 128, 1, true, inputs));
        assertArrayEquals(whole, sample(resolver, width, length, 64, 4, false, inputs));
        assertArrayEquals(whole, sample(resolver, width, length, 64, 4, true, inputs));

        String checksum = resolver.checksum(MaterialProfilePlanV2.SurfaceAspect.SURFACE, width, length, inputs);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(checksum, resolver.checksum(
                    MaterialProfilePlanV2.SurfaceAspect.SURFACE, width, length, inputs));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void rejectsOversizedWindowAgainstDeclaredBudget() {
        Fixture fixture = fixture(64, 48, 1L);
        MaterialProfileResolverV2 resolver = resolver(fixture);
        MaterialProfileResolverV2.CellInputSource inputs = (x, z) ->
                new MaterialProfileResolverV2.CellInputs(ROCK_PROVINCE, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> resolver.sampleWindow(
                MaterialProfilePlanV2.SurfaceAspect.SURFACE, 0, 0, 257, 1, inputs));
    }

    private MaterialProfileResolverV2 resolver(Fixture fixture) {
        return new MaterialProfileResolverV2(
                fixture.geology, fixture.lithology, fixture.strata,
                fixture.waterCondition, fixture.snow, fixture.materialProfile);
    }

    private Fixture fixture(int width, int length, long seed) {
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(width, length, -64, 255, 50);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        GeologyPlanV2 baseGeology = new GeologyPlanCompilerV2().compile(bounds, 128, seed, hydrology.fixedPriors());
        List<GeologyPlanV2.ProvinceDescriptor> provinces = List.of(
                baseGeology.provinces().getFirst(),
                new GeologyPlanV2.ProvinceDescriptor(
                        "province-silt-clay", SEDIMENT_PROVINCE, "formation.silt-clay", 2, 100_000, 800_000));
        GeologyPlanV2 geology = codec.sealGeologyPlan(new GeologyPlanV2(
                baseGeology.planVersion(), baseGeology.fieldContractVersion(), baseGeology.moduleId(),
                baseGeology.moduleVersion(), baseGeology.stageId(), baseGeology.priorReplacement(),
                baseGeology.namedSeed(), baseGeology.seedNamespace(), baseGeology.width(), baseGeology.length(),
                provinces, baseGeology.fields(), baseGeology.budget(), "0".repeat(64)));
        LithologyPlanV2 lithology = new LithologyPlanCompilerV2().compile(geology);
        StrataPlanV2 strata = new StrataPlanCompilerV2().compile(geology, lithology);
        WaterConditionPlanV2 waterCondition = waterConditionPlan(bounds, seed, hydrology);
        SnowPlanV2 snow = snowPlan(width, length, seed);
        MaterialProfilePlanV2 materialProfile = new MaterialProfilePlanCompilerV2()
                .compile(geology, lithology, strata, waterCondition, snow);
        return new Fixture(geology, lithology, strata, waterCondition, snow, materialProfile);
    }

    private WaterConditionPlanV2 waterConditionPlan(
            WorldBlueprintV2.Bounds bounds,
            long seed,
            HydrologyPlanV2 hydrology
    ) {
        var climate = new com.github.nankotsu029.landformcraft.core.v2.ClimatePlanCompilerV2().compile(
                bounds, 128, seed, hydrology,
                com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2.BaseClimatePreset
                        .TEMPERATE_MARITIME);
        return new com.github.nankotsu029.landformcraft.core.v2.WaterConditionPlanCompilerV2().compile(
                bounds, 128, seed, hydrology, climate);
    }

    private SnowPlanV2 snowPlan(int width, int length, long seed) {
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
                snowBudget(width, length),
                "0".repeat(64));
        return codec.sealSnowPlan(draft);
    }

    private static SnowPlanV2.ResourceBudget snowBudget(int width, int length) {
        long cells = Math.multiplyExact((long) width, length);
        int windowSize = Math.min(256, Math.max(width, length));
        long workingBytes = Math.multiplyExact(
                Math.multiplyExact((long) Math.min(width, windowSize), Math.min(length, windowSize)),
                2L * Integer.BYTES);
        return new SnowPlanV2.ResourceBudget(
                "snow-field-budget-v1", 2, cells, Math.multiplyExact(cells, 2L), 32_768L, windowSize,
                Math.max(workingBytes, 1L), 131_072L);
    }

    private static int[] sample(
            MaterialProfileResolverV2 resolver,
            int width,
            int length,
            int tileSize,
            int threads,
            boolean reverse,
            MaterialProfileResolverV2.CellInputSource inputs
    ) throws Exception {
        int[] result = new int[width * length];
        List<int[]> tiles = new ArrayList<>();
        for (int z = 0; z < length; z += tileSize) {
            for (int x = 0; x < width; x += tileSize) {
                tiles.add(new int[]{x, z, Math.min(tileSize, width - x), Math.min(tileSize, length - z)});
            }
        }
        if (reverse) {
            Collections.reverse(tiles);
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int[] tile : tiles) {
                futures.add(executor.submit(() -> {
                    for (int localZ = 0; localZ < tile[3]; localZ++) {
                        for (int localX = 0; localX < tile[2]; localX++) {
                            int globalX = tile[0] + localX;
                            int globalZ = tile[1] + localZ;
                            result[globalZ * width + globalX] = resolver.classCodeAt(
                                    MaterialProfilePlanV2.SurfaceAspect.SURFACE, globalX, globalZ,
                                    inputs.at(globalX, globalZ));
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private record Fixture(
            GeologyPlanV2 geology,
            LithologyPlanV2 lithology,
            StrataPlanV2 strata,
            WaterConditionPlanV2 waterCondition,
            SnowPlanV2 snow,
            MaterialProfilePlanV2 materialProfile
    ) {
    }
}
