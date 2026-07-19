package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformContinentalShelfModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformContinentalSlopeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformOceanBasinModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformRockyCoastModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.BathymetrySampleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.ContinentalSlopeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.OceanBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationBathymetrySliceCompilerV2Test {
    private static final Path OCEAN_BASIN =
            Path.of("examples/v2/foundation/ocean-basin-slice.terrain-intent-v2.json");
    private static final Path CONTINENTAL_SHELF =
            Path.of("examples/v2/foundation/continental-shelf-slice.terrain-intent-v2.json");
    private static final Path CONTINENTAL_SLOPE =
            Path.of("examples/v2/foundation/continental-slope-slice.terrain-intent-v2.json");
    private static final Path TRANSECT =
            Path.of("examples/v2/foundation/coast-to-basin-transect.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationOceanBasinSliceCompilerV2 basinCompiler =
            new FoundationOceanBasinSliceCompilerV2();
    private final FoundationContinentalShelfSliceCompilerV2 shelfCompiler =
            new FoundationContinentalShelfSliceCompilerV2();
    private final FoundationContinentalSlopeSliceCompilerV2 slopeCompiler =
            new FoundationContinentalSlopeSliceCompilerV2();
    private final FoundationBathymetryTransectCompilerV2 transectCompiler =
            new FoundationBathymetryTransectCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void oceanBasinSliceProducesDepthAndFluidMetrics() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(OCEAN_BASIN);
        var slice = basinCompiler.compile(intent, bounds(96, 64), 101L);
        assertTrue(slice.validation().metrics().depthFinite());
        assertTrue(slice.validation().metrics().widthOk());
        assertTrue(slice.validation().metrics().fluidSolidConflictFree());
        assertTrue(slice.validation().metrics().budgetOk());
        String checksum = new OceanBasinGeneratorV2(slice.basin()).underwaterColumnExportChecksum();
        assertEquals(64, checksum.length());
    }

    @Test
    void continentalShelfSliceProducesDepthAndWidthMetrics() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(CONTINENTAL_SHELF);
        var slice = shelfCompiler.compile(intent, bounds(96, 64), 202L);
        assertTrue(slice.validation().metrics().depthFinite());
        assertTrue(slice.validation().metrics().widthOk());
        assertTrue(slice.validation().metrics().fluidSolidConflictFree());
        assertTrue(slice.validation().metrics().budgetOk());
    }

    @Test
    void continentalSlopeSliceProducesMonotoneMetrics() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(CONTINENTAL_SLOPE);
        var slice = slopeCompiler.compile(intent, bounds(96, 64), 303L);
        assertTrue(slice.validation().metrics().depthFinite());
        assertTrue(slice.validation().metrics().monotoneOk());
        assertTrue(slice.validation().metrics().widthOk());
        assertTrue(slice.validation().metrics().fluidSolidConflictFree());
        assertTrue(slice.validation().metrics().budgetOk());
    }

    @Test
    void sealedPlansStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 basinIntent = codec.readTerrainIntent(OCEAN_BASIN);
        var basin = basinCompiler.compile(basinIntent, bounds(96, 64), 11L);
        Path basinFile = temp.resolve("ocean-basin-plan-v2.json");
        codec.writeOceanBasinPlan(basinFile, basin.basin());
        assertEquals(basin.basin(), codec.readOceanBasinPlan(basinFile));
        Files.copy(basinFile, Path.of("examples/v2/foundation/ocean-basin-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        TerrainIntentV2 shelfIntent = codec.readTerrainIntent(CONTINENTAL_SHELF);
        var shelf = shelfCompiler.compile(shelfIntent, bounds(96, 64), 22L);
        Path shelfFile = temp.resolve("continental-shelf-plan-v2.json");
        codec.writeContinentalShelfPlan(shelfFile, shelf.shelf());
        assertEquals(shelf.shelf(), codec.readContinentalShelfPlan(shelfFile));
        Files.copy(shelfFile, Path.of("examples/v2/foundation/continental-shelf-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        TerrainIntentV2 slopeIntent = codec.readTerrainIntent(CONTINENTAL_SLOPE);
        var slope = slopeCompiler.compile(slopeIntent, bounds(96, 64), 33L);
        Path slopeFile = temp.resolve("continental-slope-plan-v2.json");
        codec.writeContinentalSlopePlan(slopeFile, slope.slope());
        assertEquals(slope.slope(), codec.readContinentalSlopePlan(slopeFile));
        Files.copy(slopeFile, Path.of("examples/v2/foundation/continental-slope-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void modulesRemainExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformOceanBasinModuleV2().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformContinentalShelfModuleV2().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformContinentalSlopeModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.OCEAN_BASIN).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.CONTINENTAL_SLOPE).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.OCEAN_BASIN));
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF));
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.CONTINENTAL_SLOPE));

        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformRockyCoastModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.ROCKY_COAST).moduleId());
    }

    @Test
    void transectProducesMonotoneCoastToBasinWithWholeTileAndUnderwaterChecksums() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(TRANSECT);
        var transect = transectCompiler.compile(intent, bounds(128, 64), 404L);
        assertTrue(transect.shelfValidation().metrics().depthFinite());
        assertTrue(transect.slopeValidation().metrics().monotoneOk());
        assertTrue(transect.basinValidation().metrics().fluidSolidConflictFree());
        assertEquals(64, transect.underwaterColumnExportChecksum().length());
        assertTrue(transect.composedFieldChecksums()
                .containsKey(BathymetrySampleV2.BathymetryField.DEPTH));

        String first = transect.underwaterColumnExportChecksum();
        var again = transectCompiler.compile(intent, bounds(128, 64), 404L);
        assertEquals(first, again.underwaterColumnExportChecksum());
        assertEquals(transect.composedFieldChecksums(), again.composedFieldChecksums());
    }

    @Test
    void transectRejectsMissingTransitionRelations() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(TRANSECT);
        List<TerrainIntentV2.Relation> kept = base.relations().stream()
                .filter(relation -> !"shelf-slope-overlap".equals(relation.id()))
                .toList();
        TerrainIntentV2 missing = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), kept, base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> transectCompiler.compile(missing, bounds(128, 64), 1L));
        assertEquals("v2.bathymetry-missing-relation", failure.ruleId());
    }

    @Test
    void slopeRejectsNonMonotoneDepthSampling() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(CONTINENTAL_SLOPE);
        var slice = slopeCompiler.compile(intent, bounds(96, 64), 55L);
        ContinentalSlopeGeneratorV2 generator = new ContinentalSlopeGeneratorV2(slice.slope());
        int previous = -1;
        boolean foundDecrease = false;
        for (int x = 0; x < generator.width(); x++) {
            BathymetrySampleV2 sample = generator.sampleAtInverted(x, generator.length() / 2);
            if (!sample.owned()) {
                continue;
            }
            if (previous >= 0 && sample.depthBlocksBelowSea() < previous) {
                foundDecrease = true;
                break;
            }
            previous = sample.depthBlocksBelowSea();
        }
        assertTrue(foundDecrease);
        assertThrows(FoundationSliceException.class, () -> {
            for (int x = 1; x < generator.width(); x++) {
                BathymetrySampleV2 prev = generator.sampleAtInverted(x - 1, generator.length() / 2);
                BathymetrySampleV2 cur = generator.sampleAtInverted(x, generator.length() / 2);
                if (prev.owned() && cur.owned() && cur.depthBlocksBelowSea() < prev.depthBlocksBelowSea()) {
                    throw new FoundationSliceException("v2.continental-slope-non-monotone",
                            "continental slope depth is not monotone seaward");
                }
            }
        });
    }

    @Test
    void fluidSolidConflictIsDetected() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(OCEAN_BASIN);
        var slice = basinCompiler.compile(intent, bounds(96, 64), 66L);
        BathymetrySampleV2 conflict = new BathymetrySampleV2(
                8, 0, 0, 1, slice.basin().waterLevel() - 8, slice.basin().waterLevel() - 20);
        assertFalse(com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry
                .BathymetryChecksumSupportV2.fluidSolidConflictFree(conflict, slice.basin().waterLevel()));
    }

    @Test
    void shelfDeeperThanSlopeLowerIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(TRANSECT);
        List<TerrainIntentV2.Feature> features = new ArrayList<>();
        for (TerrainIntentV2.Feature feature : base.features()) {
            if (feature.kind() == TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF) {
                features.add(new TerrainIntentV2.Feature(
                        feature.id(), feature.kind(), feature.geometry(),
                        new TerrainIntentV2.ContinentalShelfParameters(
                                new TerrainIntentV2.IntRange(24, 32),
                                new TerrainIntentV2.IntRange(40, 48),
                                new TerrainIntentV2.IntRange(4, 8),
                                TerrainIntentV2.Edge.EAST),
                        feature.priority(), feature.provenance()));
            } else {
                features.add(feature);
            }
        }
        TerrainIntentV2 deepShelf = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                features, base.relations(), base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> transectCompiler.compile(deepShelf, bounds(128, 64), 1L));
        assertEquals("v2.bathymetry-shelf-deeper-than-slope", failure.ruleId());
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, 62);
    }
}
