package com.github.nankotsu029.landformcraft.generator.v2.landform.mountain;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompilationException;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.ClimatePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.WaterConditionPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.GeologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.HydrologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.LithologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.StrataPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.MountainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountainGeneratorV2Test {
    @Test
    void compilesContinuousRidgePeakOrderReliefAndDrainageHandoff() {
        Fixture fixture = alpine(129, 193, false, false, 4, 2);
        MountainProfileValidatorV2.requireValid(fixture.plan());
        MountainGeneratorV2.MountainMetrics metrics = new MountainGeneratorV2(fixture.plan()).evaluate(() -> false);
        assertTrue(metrics.ridgeContinuous());
        assertTrue(metrics.peakOrderOk());
        assertTrue(metrics.reliefOk());
        assertTrue(metrics.drainageHandoffOk());
        assertEquals(4, fixture.plan().peaks().size());
        assertEquals(3, fixture.plan().saddles().size());
        assertEquals(2, fixture.plan().spurs().size());
    }

    @Test
    void compilesGlacialPolygonMajorAxisRidge() {
        Fixture fixture = glacial(129, 193);
        assertEquals(TerrainIntentV2.MountainVariant.GLACIAL, fixture.plan().variant());
        assertTrue(fixture.plan().ridge().size() >= 2);
        MountainGeneratorV2.MountainMetrics metrics = new MountainGeneratorV2(fixture.plan()).evaluate(() -> false);
        assertTrue(metrics.ridgeContinuous());
        assertTrue(metrics.peakOrderOk());
    }

    @Test
    void rejectsSelfCrossBoundsAndHardCoastConflict() {
        MountainGenerationException selfCross = assertThrows(MountainGenerationException.class,
                () -> compile(selfCrossingIntent(), 129, 193));
        assertEquals("v2.mountain-self-cross", selfCross.ruleId());

        MountainGenerationException coast = assertThrows(MountainGenerationException.class,
                () -> compile(alpine(129, 193, true, false, 4, 0).intent(), 129, 193));
        assertEquals("v2.mountain-hard-coast-conflict", coast.ruleId());

        MountainPlanV2 plan = alpine(129, 193, false, false, 4, 0).plan();
        MountainGeneratorV2 generator = new MountainGeneratorV2(plan);
        MountainPlanV2.RidgePoint first = plan.ridge().getFirst();
        int x = Math.toIntExact(first.xMillionths() / TerrainIntentV2.FIXED_SCALE);
        int z = Math.toIntExact(first.zMillionths() / TerrainIntentV2.FIXED_SCALE);
        MountainGenerationException raster = assertThrows(MountainGenerationException.class,
                () -> generator.sampleAt(x, z, index -> true));
        assertEquals("v2.mountain-hard-coast-conflict", raster.ruleId());
    }

    @Test
    void rejectsCorruptedDuplicateRidgeIds() {
        Fixture fixture = alpine(129, 193, false, false, 4, 0);
        MountainProfileValidatorV2.requireValid(fixture.plan());
        List<MountainPlanV2.NamedStation> peaks = fixture.plan().peaks();
        MountainPlanV2.NamedStation first = peaks.getFirst();
        List<MountainPlanV2.NamedStation> duplicated = new ArrayList<>(peaks);
        duplicated.set(1, new MountainPlanV2.NamedStation(
                first.stationId(),
                peaks.get(1).xMillionths(),
                peaks.get(1).zMillionths(),
                peaks.get(1).arcLengthMillionths(),
                peaks.get(1).reliefBlocks()));
        MountainPlanV2 corrupted = new MountainPlanV2(
                fixture.plan().planVersion(),
                fixture.plan().featureId(),
                fixture.plan().variant(),
                fixture.plan().ridge(),
                duplicated,
                fixture.plan().saddles(),
                fixture.plan().spurs(),
                fixture.plan().selectedPeakCount(),
                fixture.plan().selectedRidgeHalfWidthBlocks(),
                fixture.plan().selectedMaxReliefBlocks(),
                fixture.plan().spurCount(),
                fixture.plan().ridgeSharpnessMillionths(),
                fixture.plan().minY(),
                fixture.plan().maxY(),
                fixture.plan().waterLevel(),
                fixture.plan().width(),
                fixture.plan().length(),
                fixture.plan().ridgeMaskFieldId(),
                fixture.plan().peakMaskFieldId(),
                fixture.plan().saddleMaskFieldId(),
                fixture.plan().spurMaskFieldId(),
                fixture.plan().provisionalSurfaceFieldId(),
                fixture.plan().ridgeSegmentIdFieldId(),
                fixture.plan().supportRadiusXZ(),
                fixture.plan().estimatedRasterWorkUnits(),
                fixture.plan().geometryChecksum());
        MountainGenerationException duplicate = assertThrows(MountainGenerationException.class,
                () -> MountainProfileValidatorV2.requireValid(corrupted));
        assertEquals("v2.mountain-duplicate-ridge-id", duplicate.ruleId());
    }

    @Test
    void wholeTileSeamThreadAndLocaleChecksumsMatch() throws Exception {
        Fixture fixture = alpine(129, 193, false, false, 4, 2);
        MountainGeneratorV2 generator = new MountainGeneratorV2(fixture.plan());
        IntPredicate noConflict = index -> false;
        Map<MountainGeneratorV2.MountainField, String> direct = generator.fieldChecksums(noConflict, () -> false);
        Map<Long, MountainWindowV2> forward = renderTiles(generator, 64, false, 1, noConflict);
        Map<Long, MountainWindowV2> reverse = renderTiles(generator, 64, true, 1, noConflict);
        Map<Long, MountainWindowV2> parallel = renderTiles(generator, 64, true, 4, noConflict);
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(forward, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(reverse, 64), () -> false));
        assertEquals(direct, generator.fieldChecksumsFrom(tiledSource(parallel, 64), () -> false));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Fixture reordered = alpine(129, 193, false, false, 4, 2);
            assertEquals(fixture.plan(), reordered.plan());
            assertEquals(direct, new MountainGeneratorV2(reordered.plan()).fieldChecksums(noConflict, () -> false));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void enforcesRasterMemoryCpuAdmissionAndCooperativeCancellation() {
        Fixture maximum = alpine(1_000, 1_000, false, false, 4, 2);
        MountainGeneratorV2 generator = new MountainGeneratorV2(maximum.plan());
        assertTrue(maximum.plan().estimatedRasterWorkUnits() <= MountainPlanV2.MAXIMUM_RASTER_WORK_UNITS);
        assertTrue(MountainGeneratorV2.estimateWindowRetainedBytes(1_000, 1_000)
                > MountainGeneratorV2.MAXIMUM_WINDOW_RETAINED_BYTES);
        MountainGenerationException memory = assertThrows(MountainGenerationException.class,
                () -> generator.renderWindow(0, 0, 1_000, 1_000, 0, index -> false, () -> false));
        assertEquals("v2.mountain-budget", memory.ruleId());

        Fixture small = alpine(129, 193, false, false, 4, 0);
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(129, 193, -64, 255, 63);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        GeologyPlanV2 geology = new GeologyPlanCompilerV2().compile(
                bounds, 64, 827413L, hydrology.fixedPriors());
        LithologyPlanV2 lithology = new LithologyPlanCompilerV2().compile(geology);
        StrataPlanV2 strata = new StrataPlanCompilerV2().compile(geology, lithology);
        ClimatePlanV2 climate = new ClimatePlanCompilerV2().compile(
                bounds, 64, 827413L, hydrology, ClimatePlanV2.BaseClimatePreset.TEMPERATE_MARITIME);
        WaterConditionPlanV2 water = new WaterConditionPlanCompilerV2().compile(
                bounds, 64, 827413L, hydrology, climate);
        long priorCpu = hydrology.budget().estimatedCpuWorkUnits()
                + geology.budget().estimatedCpuWorkUnits()
                + lithology.budget().estimatedCpuWorkUnits()
                + strata.budget().estimatedCpuWorkUnits()
                + climate.budget().estimatedCpuWorkUnits()
                + water.budget().estimatedCpuWorkUnits();
        WorldBlueprintV2.ResourceBudget defaults = DiagnosticCompileRequestV2.defaultBudget();
        WorldBlueprintV2.ResourceBudget cpuTooSmall = new WorldBlueprintV2.ResourceBudget(
                defaults.maximumFeatures(), defaults.maximumRelations(), defaults.maximumConstraints(),
                defaults.maximumGeometryPoints(), defaults.maximumModules(), defaults.maximumFields(),
                defaults.maximumHaloXZ(), defaults.maximumHaloY(), defaults.maximumResidentBytes(),
                priorCpu + small.plan().estimatedRasterWorkUnits() - 1L,
                defaults.maximumArtifactBytes());
        DiagnosticCompileRequestV2 request = new DiagnosticCompileRequestV2(
                small.intent().intentId(), new GenerationBounds(129, 193, -64, 255, 63), 64, 827413L,
                "c".repeat(64), cpuTooSmall);
        DiagnosticCompilationException cpu = assertThrows(DiagnosticCompilationException.class,
                () -> new DiagnosticBlueprintCompilerV2().compile(request, small.intent()));
        assertEquals("v2.mountain-budget", cpu.ruleId());

        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> new MountainGeneratorV2(small.plan())
                .renderWindow(0, 0, 64, 64, 0, index -> false, () -> checks.incrementAndGet() > 1));
        assertTrue(checks.get() > 1);
    }

    private static Map<Long, MountainWindowV2> renderTiles(
            MountainGeneratorV2 generator,
            int tileSize,
            boolean reverse,
            int threads,
            IntPredicate hardSeaConflict
    ) throws Exception {
        List<int[]> tiles = new ArrayList<>();
        for (int z = 0; z < generator.length(); z += tileSize) {
            for (int x = 0; x < generator.width(); x += tileSize) {
                tiles.add(new int[]{
                        x, z,
                        Math.min(tileSize, generator.width() - x),
                        Math.min(tileSize, generator.length() - z)});
            }
        }
        if (reverse) {
            tiles = tiles.reversed();
        }
        Map<Long, MountainWindowV2> result = new HashMap<>();
        if (threads <= 1) {
            for (int[] tile : tiles) {
                result.put(pack(tile[0], tile[1]), generator.renderWindow(
                        tile[0], tile[1], tile[2], tile[3], 0, hardSeaConflict, () -> false));
            }
            return result;
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Map.Entry<Long, MountainWindowV2>>> futures = new ArrayList<>();
            for (int[] tile : tiles) {
                futures.add(executor.submit(() -> Map.entry(
                        pack(tile[0], tile[1]),
                        generator.renderWindow(tile[0], tile[1], tile[2], tile[3], 0, hardSeaConflict, () -> false))));
            }
            for (Future<Map.Entry<Long, MountainWindowV2>> future : futures) {
                Map.Entry<Long, MountainWindowV2> entry = future.get();
                result.put(entry.getKey(), entry.getValue());
            }
        } catch (ExecutionException exception) {
            throw exception.getCause() instanceof Exception cause ? cause : exception;
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static MountainGeneratorV2.CellSource tiledSource(Map<Long, MountainWindowV2> tiles, int tileSize) {
        return (x, z) -> {
            int originX = (x / tileSize) * tileSize;
            int originZ = (z / tileSize) * tileSize;
            MountainWindowV2 window = tiles.get(pack(originX, originZ));
            return new MountainGeneratorV2.MountainSample(
                    window.rawValueAt(MountainGeneratorV2.MountainField.RIDGE_MASK, x, z),
                    window.rawValueAt(MountainGeneratorV2.MountainField.PEAK_MASK, x, z),
                    window.rawValueAt(MountainGeneratorV2.MountainField.SADDLE_MASK, x, z),
                    window.rawValueAt(MountainGeneratorV2.MountainField.SPUR_MASK, x, z),
                    window.rawValueAt(MountainGeneratorV2.MountainField.PROVISIONAL_SURFACE, x, z),
                    window.rawValueAt(MountainGeneratorV2.MountainField.RIDGE_SEGMENT_ID, x, z));
        };
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) | (z & 0xffff_ffffL);
    }

    private static Fixture alpine(
            int width,
            int length,
            boolean hardSeaNorth,
            boolean unused,
            int peakCount,
            int spurCount
    ) {
        TerrainIntentV2.Feature mountain = new TerrainIntentV2.Feature(
                "alpine-ridge",
                TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE,
                new TerrainIntentV2.SplineGeometry(List.of(
                        new TerrainIntentV2.Point2(100_000, hardSeaNorth ? 0 : 800_000),
                        new TerrainIntentV2.Point2(300_000, hardSeaNorth ? 50_000 : 550_000),
                        new TerrainIntentV2.Point2(550_000, hardSeaNorth ? 120_000 : 400_000),
                        new TerrainIntentV2.Point2(800_000, hardSeaNorth ? 220_000 : 220_000),
                        new TerrainIntentV2.Point2(920_000, hardSeaNorth ? 320_000 : 120_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.MountainParameters(
                        new TerrainIntentV2.IntRange(peakCount, peakCount),
                        new TerrainIntentV2.IntRange(8, 8),
                        new TerrainIntentV2.IntRange(48, 48),
                        spurCount,
                        700_000),
                0,
                TerrainIntentV2.Provenance.confirmedManual("mountain-test"));
        List<TerrainIntentV2.Constraint> constraints = new ArrayList<>();
        if (hardSeaNorth) {
            constraints.add(new TerrainIntentV2.EdgeClassificationConstraint(
                    "north-sea",
                    TerrainIntentV2.Strength.HARD,
                    "world",
                    TerrainIntentV2.Edge.NORTH,
                    TerrainIntentV2.EdgeClassification.SEA,
                    100_000,
                    0));
        }
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "mountain-test",
                "alpine",
                coordinateSystem(),
                List.of(mountain),
                List.of(),
                constraints,
                new TerrainIntentV2.EnvironmentDescriptor("ALPINE_GRANITIC", "COLD_ALPINE", "ALPINE_TREELINE"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("mountain-test"));
        MountainPlanV2 plan = hardSeaNorth ? null : compile(intent, width, length);
        return new Fixture(intent, plan);
    }

    private static Fixture glacial(int width, int length) {
        TerrainIntentV2.Feature mountain = new TerrainIntentV2.Feature(
                "walls",
                TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE,
                new TerrainIntentV2.PolygonGeometry(List.of(List.of(
                        new TerrainIntentV2.Point2(100_000, 10_000),
                        new TerrainIntentV2.Point2(900_000, 10_000),
                        new TerrainIntentV2.Point2(900_000, 990_000),
                        new TerrainIntentV2.Point2(100_000, 990_000),
                        new TerrainIntentV2.Point2(100_000, 10_000)))),
                new TerrainIntentV2.MountainParameters(
                        new TerrainIntentV2.IntRange(3, 3),
                        new TerrainIntentV2.IntRange(12, 12),
                        new TerrainIntentV2.IntRange(64, 64),
                        0,
                        500_000),
                0,
                TerrainIntentV2.Provenance.confirmedManual("mountain-test"));
        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "glacial-mountain-test",
                "glacial",
                coordinateSystem(),
                List.of(mountain),
                List.of(),
                List.of(),
                new TerrainIntentV2.EnvironmentDescriptor("GLACIATED_HARD_ROCK", "COLD_MARITIME", "SUBALPINE_FJORD"),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("mountain-test"));
        return new Fixture(intent, compile(intent, width, length));
    }

    private static TerrainIntentV2 selfCrossingIntent() {
        TerrainIntentV2.Feature mountain = new TerrainIntentV2.Feature(
                "crossed-ridge",
                TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE,
                new TerrainIntentV2.SplineGeometry(List.of(
                        new TerrainIntentV2.Point2(100_000, 100_000),
                        new TerrainIntentV2.Point2(900_000, 900_000),
                        new TerrainIntentV2.Point2(100_000, 900_000),
                        new TerrainIntentV2.Point2(900_000, 100_000)), TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.MountainParameters(
                        new TerrainIntentV2.IntRange(2, 2),
                        new TerrainIntentV2.IntRange(8, 8),
                        new TerrainIntentV2.IntRange(32, 32),
                        0,
                        700_000),
                0,
                TerrainIntentV2.Provenance.confirmedManual("mountain-test"));
        return new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                "mountain-cross",
                "cross",
                coordinateSystem(),
                List.of(mountain),
                List.of(),
                List.of(),
                new TerrainIntentV2.EnvironmentDescriptor("", "", ""),
                List.of(),
                List.of(),
                TerrainIntentV2.Provenance.confirmedManual("mountain-test"));
    }

    private static TerrainIntentV2.CoordinateSystem coordinateSystem() {
        return new TerrainIntentV2.CoordinateSystem(
                TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                TerrainIntentV2.XAxis.EAST,
                TerrainIntentV2.ZAxis.SOUTH,
                TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET);
    }

    private static MountainPlanV2 compile(TerrainIntentV2 intent, int width, int length) {
        TerrainIntentV2.Feature feature = intent.features().getFirst();
        return new MountainPlanCompilerV2().compile(
                feature,
                intent,
                new WorldBlueprintV2.Bounds(width, length, -64, 255, 63),
                "a".repeat(64));
    }

    private record Fixture(TerrainIntentV2 intent, MountainPlanV2 plan) {
    }
}
