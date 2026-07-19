package com.github.nankotsu029.landformcraft.generator.v2.material.feature;

import com.github.nankotsu029.landformcraft.core.v2.ClimatePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.FeatureMaterialProfilePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.GeologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.HydrologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.LithologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.MaterialProfilePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.StrataPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.WaterConditionPlanCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic.VolcanicGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic.VolcanicPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;
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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureMaterialProfileResolverV2Test {
    private static final int SCALE = 1_000_000;
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void resolvesVolcanicBasaltTuffAshAndShoreZones() {
        Fixture fixture = fixture(64, 48, 11L);
        FeatureMaterialProfileResolverV2 resolver = resolver(fixture);

        assertEquals(
                FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_BASALT_EXPOSED.compactCode(),
                resolver.featureClassCodeAt(0, 0, FeatureMaterialProfileResolverV2.CellInputs.volcanicOnly(
                        1, 50 * SCALE, 0, 100 * SCALE, 64)));
        assertEquals(
                FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_TUFF_EXPOSED.compactCode(),
                resolver.featureClassCodeAt(0, 0, FeatureMaterialProfileResolverV2.CellInputs.volcanicOnly(
                        1, 20 * SCALE, 0, 100 * SCALE, 64)));
        assertEquals(
                FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_ASH_EXPOSED.compactCode(),
                resolver.featureClassCodeAt(0, 0, FeatureMaterialProfileResolverV2.CellInputs.volcanicOnly(
                        1, 4 * SCALE, 0, 100 * SCALE, 64)));
        assertEquals(
                FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_ASH_EXPOSED.compactCode(),
                resolver.featureClassCodeAt(0, 0, FeatureMaterialProfileResolverV2.CellInputs.volcanicOnly(
                        0, 0, 1, 50 * SCALE, 64)));
        assertEquals(
                FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_ASH_EXPOSED.compactCode(),
                resolver.featureClassCodeAt(0, 0, FeatureMaterialProfileResolverV2.CellInputs.volcanicOnly(
                        1, 50 * SCALE, 0, 64 * SCALE, 64)));
        assertEquals(FeatureMaterialProfileResolverV2.NO_FEATURE_OVERRIDE,
                resolver.featureClassCodeAt(0, 0, FeatureMaterialProfileResolverV2.CellInputs.volcanicOnly(
                        0, 0, 0, 0, 64)));
    }

    @Test
    void resolvesCanyonFloorTalusAndStrataExposure() {
        Fixture fixture = fixture(64, 48, 17L);
        FeatureMaterialProfileResolverV2 resolver = resolver(fixture);
        int province = 1;

        assertEquals(
                FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.CANYON_FLOOR_SEDIMENT.compactCode(),
                resolver.featureClassCodeAt(2, 2, FeatureMaterialProfileResolverV2.CellInputs.canyonOnly(
                        1, 1, 0, province, 100)));
        assertEquals(
                FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.CANYON_TALUS.compactCode(),
                resolver.featureClassCodeAt(2, 2, FeatureMaterialProfileResolverV2.CellInputs.canyonOnly(
                        1, 0, 4 * SCALE, province, 100)));
        assertEquals(
                FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.CANYON_STRATA_EXPOSED.compactCode(),
                resolver.featureClassCodeAt(2, 2, FeatureMaterialProfileResolverV2.CellInputs.canyonOnly(
                        1, 0, 20 * SCALE, province, 100)));
    }

    @Test
    void canyonWinsOverVolcanicAndRejectsUnknownLithology() {
        Fixture fixture = fixture(64, 48, 23L);
        FeatureMaterialProfileResolverV2 resolver = resolver(fixture);

        FeatureMaterialProfileResolverV2.CellInputs dual = new FeatureMaterialProfileResolverV2.CellInputs(
                1, 50 * SCALE, 0, 100 * SCALE, 64,
                1, 1, 0, 1, 100);
        assertEquals(
                FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.CANYON_FLOOR_SEDIMENT.compactCode(),
                resolver.featureClassCodeAt(1, 1, dual));

        assertThrows(IllegalArgumentException.class, () -> resolver.featureClassCodeAt(
                1, 1, FeatureMaterialProfileResolverV2.CellInputs.canyonOnly(
                        1, 0, 20 * SCALE, GeologyPlanV2.NO_DATA_RAW, 0)));
    }

    @Test
    void wholeTileThreadLocaleDeterminismAndResolvedBaseFallback() throws Exception {
        Fixture fixture = fixture(48, 32, 99L);
        FeatureMaterialProfileResolverV2 resolver = resolver(fixture);
        FeatureMaterialProfileResolverV2.CellInputSource source = (x, z) -> {
            if ((x + z) % 7 == 0) {
                return FeatureMaterialProfileResolverV2.CellInputs.volcanicOnly(
                        1, 45 * SCALE, 0, 120 * SCALE, 64);
            }
            if ((x + z) % 5 == 0) {
                return FeatureMaterialProfileResolverV2.CellInputs.canyonOnly(
                        1, 0, 16 * SCALE, 1, 200);
            }
            return FeatureMaterialProfileResolverV2.CellInputs.volcanicOnly(0, 0, 0, 0, 64);
        };

        String checksum = resolver.featureChecksum(48, 32, source);
        int[] left = resolver.sampleFeatureWindow(0, 0, 24, 32, source);
        int[] right = resolver.sampleFeatureWindow(24, 0, 24, 32, source);
        int[] reconstructed = new int[48 * 32];
        for (int z = 0; z < 32; z++) {
            System.arraycopy(left, z * 24, reconstructed, z * 48, 24);
            System.arraycopy(right, z * 24, reconstructed, z * 48 + 24, 24);
        }
        assertEquals(
                java.util.Arrays.toString(resolver.sampleFeatureWindow(0, 0, 48, 32, source)),
                java.util.Arrays.toString(reconstructed));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals(checksum, resolver.featureChecksum(48, 32, source));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> resolver.featureChecksum(48, 32, source)));
            }
            for (Future<String> future : futures) {
                assertEquals(checksum, future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(3, resolver.resolvedClassCodeAt(
                0, 0, FeatureMaterialProfileResolverV2.CellInputs.volcanicOnly(0, 0, 0, 0, 64), 3));
        assertEquals(
                FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_BASALT_EXPOSED.compactCode(),
                resolver.resolvedClassCodeAt(
                        0, 0,
                        FeatureMaterialProfileResolverV2.CellInputs.volcanicOnly(
                                1, 50 * SCALE, 0, 120 * SCALE, 64),
                        3));
        assertThrows(IllegalArgumentException.class, () -> resolver.sampleFeatureWindow(
                0, 0, 257, 16, source));
    }

    @Test
    void volcanicShapeChecksumsRemainUnchangedByFeatureMaterialOverlay() {
        VolcanicPlanV2 volcanicPlan = compileVolcanicArchipelago(257, 257);
        Map<VolcanicGeneratorV2.VolcanicField, String> before =
                new VolcanicGeneratorV2(volcanicPlan).fieldChecksums(index -> false, () -> false);

        Fixture materialFixture = fixture(64, 48, 3L);
        FeatureMaterialProfilePlanV2 bound = new FeatureMaterialProfilePlanCompilerV2().compile(
                materialFixture.material,
                materialFixture.geology,
                materialFixture.lithology,
                materialFixture.strata,
                List.of(volcanicPlan),
                List.of());
        FeatureMaterialProfileResolverV2 overlay = new FeatureMaterialProfileResolverV2(
                materialFixture.material,
                materialFixture.geology,
                materialFixture.lithology,
                materialFixture.strata,
                bound);
        overlay.bindVolcanicPlan(volcanicPlan);
        assertEquals(FeatureMaterialProfileResolverV2.NO_FEATURE_OVERRIDE,
                overlay.featureClassCodeAt(0, 0,
                        FeatureMaterialProfileResolverV2.CellInputs.volcanicOnly(0, 0, 0, 0, 64)));

        Map<VolcanicGeneratorV2.VolcanicField, String> after =
                new VolcanicGeneratorV2(volcanicPlan).fieldChecksums(index -> false, () -> false);
        assertEquals(before, after);
        assertTrue(before.size() >= 6);
    }

    private static VolcanicPlanV2 compileVolcanicArchipelago(int width, int length) {
        List<TerrainIntentV2.NamedPoint> points = List.of(
                new TerrainIntentV2.NamedPoint("west-island", new TerrainIntentV2.Point2(150_000, 520_000)),
                new TerrainIntentV2.NamedPoint("main-island", new TerrainIntentV2.Point2(500_000, 420_000)),
                new TerrainIntentV2.NamedPoint("east-island", new TerrainIntentV2.Point2(850_000, 540_000)));
        TerrainIntentV2.Feature archipelago = new TerrainIntentV2.Feature(
                "island-arc",
                TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO,
                new TerrainIntentV2.MultiPointGeometry(points),
                new TerrainIntentV2.VolcanicArchipelagoParameters(
                        List.of(
                                new TerrainIntentV2.IslandSpec("east-island", 26, 44),
                                new TerrainIntentV2.IslandSpec("main-island", 48, 96),
                                new TerrainIntentV2.IslandSpec("west-island", 28, 48)),
                        new TerrainIntentV2.IntRange(10, 16)),
                0,
                TerrainIntentV2.Provenance.confirmedManual("feature-material-test"));
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION, "feature-material-volcanic", "arc",
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                List.of(archipelago), List.of(), List.of(),
                new TerrainIntentV2.EnvironmentDescriptor("BASALTIC_VOLCANIC", "WARM_MARITIME", "VOLCANIC_SUCCESSION"),
                List.of(), List.of(), TerrainIntentV2.Provenance.confirmedManual("feature-material-test"));
        return new VolcanicPlanCompilerV2().compile(
                archipelago, intent, new WorldBlueprintV2.Bounds(width, length, -64, 255, 63), "a".repeat(64));
    }

    private FeatureMaterialProfileResolverV2 resolver(Fixture fixture) {
        return new FeatureMaterialProfileResolverV2(
                fixture.material, fixture.geology, fixture.lithology, fixture.strata, fixture.featureMaterial);
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
