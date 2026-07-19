package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSubmarineCanyonModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.LandformCanyonModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationSubmarineCanyonSliceCompilerV2Test {
    private static final Path POSITIVE =
            Path.of("examples/v2/foundation/submarine-canyon-positive.terrain-intent-v2.json");
    private static final Path OUT_OF_HOST =
            Path.of("examples/v2/foundation/submarine-canyon-out-of-host.terrain-intent-v2.json");
    private static final Path TRANSECT =
            Path.of("examples/v2/foundation/coast-to-basin-transect.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationSubmarineCanyonSliceCompilerV2 canyonCompiler =
            new FoundationSubmarineCanyonSliceCompilerV2();
    private final FoundationBathymetryTransectCompilerV2 transectCompiler =
            new FoundationBathymetryTransectCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void positiveSliceProducesMetricsRoundTripAndUnderwaterChecksum() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var slice = canyonCompiler.compile(intent, bounds(128, 64), 909L);
        assertTrue(slice.validation().metrics().headContained());
        assertTrue(slice.validation().metrics().outletContained());
        assertTrue(slice.validation().metrics().slopeCrossingOk());
        assertTrue(slice.validation().metrics().downGradientOk());
        assertTrue(slice.validation().metrics().floorDepthOk());
        assertTrue(slice.validation().metrics().fluidSolidConflictFree());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().budgetOk());
        assertEquals(64, slice.underwaterColumnExportChecksum().length());

        Path planFile = Path.of("examples/v2/foundation/submarine-canyon-plan-v2.json");
        codec.writeSubmarineCanyonPlan(planFile, slice.canyon());
        assertEquals(slice.canyon(), codec.readSubmarineCanyonPlan(planFile));
    }

    @Test
    void sealedPlanStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var slice = canyonCompiler.compile(intent, bounds(128, 64), 101L);
        Path planFile = temp.resolve("submarine-canyon-plan-v2.json");
        codec.writeSubmarineCanyonPlan(planFile, slice.canyon());
        assertEquals(slice.canyon(), codec.readSubmarineCanyonPlan(planFile));
        Files.copy(planFile, Path.of("examples/v2/foundation/submarine-canyon-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void moduleRemainsExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformSubmarineCanyonModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.SUBMARINE_CANYON).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.SUBMARINE_CANYON));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformSubmarineCanyonModuleV2.MODULE_ID.equals(module.moduleId())));
        assertEquals(LandformCanyonModuleV2.MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.CANYON).moduleId());
        assertEquals("v2.landform.canyon", LandformCanyonModuleV2.MODULE_ID);
        assertEquals("0.1.0-v2-3-05", LandformCanyonModuleV2.MODULE_VERSION);
    }

    @Test
    void blockedMissingCrossingRelation() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        List<TerrainIntentV2.Relation> kept = base.relations().stream()
                .filter(relation -> !"canyon-carves-slope".equals(relation.id()))
                .toList();
        TerrainIntentV2 missing = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), kept, base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> canyonCompiler.compile(missing, bounds(128, 64), 1L));
        assertEquals("v2.submarine-canyon-missing-relation", failure.ruleId());
    }

    @Test
    void outOfHostSplineIsRejected() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(OUT_OF_HOST);
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> canyonCompiler.compile(intent, bounds(128, 64), 2L));
        assertEquals("v2.submarine-canyon-out-of-host", failure.ruleId());
    }

    @Test
    void bathymetryTransectRegressionStillPasses() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(TRANSECT);
        var transect = transectCompiler.compile(intent, bounds(128, 64), 404L);
        assertTrue(transect.shelfValidation().metrics().depthFinite());
        assertTrue(transect.slopeValidation().metrics().monotoneOk());
        assertTrue(transect.basinValidation().metrics().fluidSolidConflictFree());
        assertEquals(64, transect.underwaterColumnExportChecksum().length());
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, 62);
    }
}
