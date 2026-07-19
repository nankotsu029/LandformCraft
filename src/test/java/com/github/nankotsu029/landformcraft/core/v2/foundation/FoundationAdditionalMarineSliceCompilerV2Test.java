package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformAbyssalPlainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSeamountModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.AbyssalPlainGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.SeamountGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AbyssalPlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SeamountPlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationAdditionalMarineSliceCompilerV2Test {
    private static final Path ABYSSAL =
            Path.of("examples/v2/foundation/abyssal-plain-positive.terrain-intent-v2.json");
    private static final Path SEAMOUNT =
            Path.of("examples/v2/foundation/seamount-positive.terrain-intent-v2.json");
    private static final Path OCEAN_BASIN_PLAN =
            Path.of("examples/v2/foundation/ocean-basin-plan-v2.json");
    private static final String HOST_BASIN_CHECKSUM =
            "51b0acafa65953d09bacc3734e8ef18c5b43af29135f00999d2abe7cf5d0eb22";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationAdditionalMarineSliceCompilerV2 compiler =
            new FoundationAdditionalMarineSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void abyssalSliceProducesMetricsRoundTripAndExport() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(ABYSSAL);
        var slice = compiler.compileAbyssal(intent, bounds(96, 64), 2001L);
        assertTrue(slice.validation().metrics().basinContainmentOk());
        assertTrue(slice.validation().metrics().depthReliefOk());
        assertTrue(slice.validation().metrics().slopeOk());
        assertTrue(slice.validation().metrics().transitionOk());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().budgetOk());
        assertTrue(slice.validation().metrics().exportOk());
        assertEquals("deep-basin", slice.abyssal().basinFeatureId());
        assertEquals(HOST_BASIN_CHECKSUM, slice.hostBasinChecksum());

        Path planFile = Path.of("examples/v2/foundation/abyssal-plain-plan-v2.json");
        codec.writeAbyssalPlainPlan(planFile, slice.abyssal());
        assertEquals(slice.abyssal(), codec.readAbyssalPlainPlan(planFile));
        assertEquals(slice.exportChecksum(),
                new AbyssalPlainGeneratorV2(slice.abyssal(), slice.basin()).exportChecksum());
    }

    @Test
    void seamountSliceProducesMetricsRoundTripAndExport() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(SEAMOUNT);
        var slice = compiler.compileSeamount(intent, bounds(96, 64), 2002L);
        assertTrue(slice.validation().metrics().basinContainmentOk());
        assertTrue(slice.validation().metrics().depthReliefOk());
        assertTrue(slice.validation().metrics().slopeOk());
        assertTrue(slice.validation().metrics().transitionOk());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().budgetOk());
        assertTrue(slice.validation().metrics().exportOk());
        assertEquals("deep-basin", slice.seamount().basinFeatureId());
        assertEquals(HOST_BASIN_CHECKSUM, slice.hostBasinChecksum());

        Path planFile = Path.of("examples/v2/foundation/seamount-plan-v2.json");
        codec.writeSeamountPlan(planFile, slice.seamount());
        assertEquals(slice.seamount(), codec.readSeamountPlan(planFile));
        assertEquals(slice.exportChecksum(),
                new SeamountGeneratorV2(slice.seamount(), slice.basin()).exportChecksum());
    }

    @Test
    void featureKindClassificationMatchesTaskScope() {
        List<String> kinds = Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .map(Enum::name)
                .toList();
        assertTrue(kinds.contains("ABYSSAL_PLAIN"));
        assertTrue(kinds.contains("SEAMOUNT"));
        assertFalse(kinds.contains("OCEAN_TRENCH"));
        assertFalse(kinds.contains("MID_OCEAN_RIDGE"));
        assertFalse(kinds.contains("SUBMARINE_VOLCANO"));
    }

    @Test
    void missingBasinWithinIsRejectedForAbyssal() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(ABYSSAL);
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), List.of(), base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compileAbyssal(intent, bounds(96, 64), 2003L));
        assertEquals("v2.abyssal-missing-basin", failure.ruleId());
    }

    @Test
    void missingBasinWithinIsRejectedForSeamount() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(SEAMOUNT);
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), List.of(), base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compileSeamount(intent, bounds(96, 64), 2004L));
        assertEquals("v2.seamount-missing-basin", failure.ruleId());
    }

    @Test
    void outOfBasinAbyssalPolygonIsRejected() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(
                Path.of("examples/v2/foundation/abyssal-plain-out-of-basin.terrain-intent-v2.json"));
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compileAbyssal(intent, bounds(96, 64), 2005L));
        assertEquals("v2.abyssal-out-of-basin", failure.ruleId());
    }

    @Test
    void outOfBasinSeamountPointIsRejected() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(
                Path.of("examples/v2/foundation/seamount-out-of-basin.terrain-intent-v2.json"));
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compileSeamount(intent, bounds(96, 64), 2006L));
        assertEquals("v2.seamount-out-of-basin", failure.ruleId());
    }

    @Test
    void modulesRemainExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformAbyssalPlainModuleV2().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformSeamountModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.ABYSSAL_PLAIN).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.SEAMOUNT).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.ABYSSAL_PLAIN));
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.SEAMOUNT));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformAbyssalPlainModuleV2.MODULE_ID.equals(module.moduleId())));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformSeamountModuleV2.MODULE_ID.equals(module.moduleId())));
    }

    @Test
    void wholeTileThreadAndLocaleChecksumsAreStable() throws Exception {
        TerrainIntentV2 abyssalIntent = codec.readTerrainIntent(ABYSSAL);
        var firstAbyssal = compiler.compileAbyssal(abyssalIntent, bounds(96, 64), 2007L);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var secondAbyssal = compiler.compileAbyssal(abyssalIntent, bounds(96, 64), 2007L);
            assertEquals(firstAbyssal.abyssal().canonicalChecksum(), secondAbyssal.abyssal().canonicalChecksum());
            assertEquals(firstAbyssal.exportChecksum(), secondAbyssal.exportChecksum());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        TerrainIntentV2 seamountIntent = codec.readTerrainIntent(SEAMOUNT);
        var firstSeamount = compiler.compileSeamount(seamountIntent, bounds(96, 64), 2008L);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                tasks.add(() -> compiler.compileSeamount(seamountIntent, bounds(96, 64), 2008L).exportChecksum());
            }
            List<Future<String>> futures = pool.invokeAll(tasks);
            for (Future<String> future : futures) {
                assertEquals(firstSeamount.exportChecksum(), future.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void marineRegressionHostPlansRemainUnchanged() throws Exception {
        OceanBasinPlanV2 basin = codec.readOceanBasinPlan(OCEAN_BASIN_PLAN);
        assertEquals(HOST_BASIN_CHECKSUM, basin.canonicalChecksum());
        assertEquals("deep-basin", basin.featureId());
        codec.readContinentalShelfPlan(Path.of("examples/v2/foundation/continental-shelf-plan-v2.json"));
        codec.readContinentalSlopePlan(Path.of("examples/v2/foundation/continental-slope-plan-v2.json"));
        codec.readSubmarineCanyonPlan(Path.of("examples/v2/foundation/submarine-canyon-plan-v2.json"));
    }

    @Test
    void sealedPlansStrictRoundTrip(@TempDir Path temp) throws Exception {
        var abyssal = compiler.compileAbyssal(codec.readTerrainIntent(ABYSSAL), bounds(96, 64), 2009L);
        Path abyssalFile = temp.resolve("abyssal-plain-plan-v2.json");
        codec.writeAbyssalPlainPlan(abyssalFile, abyssal.abyssal());
        assertEquals(abyssal.abyssal(), codec.readAbyssalPlainPlan(abyssalFile));
        Files.copy(abyssalFile, Path.of("examples/v2/foundation/abyssal-plain-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        var seamount = compiler.compileSeamount(codec.readTerrainIntent(SEAMOUNT), bounds(96, 64), 2010L);
        Path seamountFile = temp.resolve("seamount-plan-v2.json");
        codec.writeSeamountPlan(seamountFile, seamount.seamount());
        assertEquals(seamount.seamount(), codec.readSeamountPlan(seamountFile));
        Files.copy(seamountFile, Path.of("examples/v2/foundation/seamount-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, 62);
    }
}
