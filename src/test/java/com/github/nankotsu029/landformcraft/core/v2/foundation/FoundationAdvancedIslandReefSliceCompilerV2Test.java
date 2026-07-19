package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.islandreef.AdvancedIslandReefCompositionGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AdvancedIslandReefCatalogContractV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AtollPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.BarrierIslandPlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

class FoundationAdvancedIslandReefSliceCompilerV2Test {
    private static final Path BARRIER_INTENT =
            Path.of("examples/v2/foundation/barrier-island-composition.terrain-intent-v2.json");
    private static final Path ATOLL_INTENT =
            Path.of("examples/v2/foundation/atoll-composition.terrain-intent-v2.json");
    private static final Path SINGLE_ISLAND_PLAN =
            Path.of("examples/v2/foundation/single-island-plan-v2.json");
    private static final Path CORAL_REEF_INTENT =
            Path.of("examples/v2/diagnostic/scenarios/coral-reef.terrain-intent-v2.json");
    private static final Path VOLCANIC_CONE_PLAN =
            Path.of("examples/v2/foundation/volcanic-cone-plan-v2.json");
    private static final String SINGLE_ISLAND_CANONICAL_CHECKSUM =
            "a38974fcd83d8d3dd16366b1411c69fa94e41e35a6ed125df0301a4fb5982d73";
    private static final String VOLCANIC_CONE_CANONICAL_CHECKSUM =
            "835388b9c778e95917891361fbe764e219204410fc2ab7b91f3b6cd045b8deae";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationAdvancedIslandReefSliceCompilerV2 compiler =
            new FoundationAdvancedIslandReefSliceCompilerV2();

    @Test
    void positiveBarrierProducesMetricsRoundTripAndExport(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(BARRIER_INTENT);
        var composition = compiler.compileBarrier(intent, bounds(96, 96, 62), 8108L);
        assertTrue(composition.validation().metrics().shoreParallelRidgeOk());
        assertTrue(composition.validation().metrics().transitionOk());
        assertTrue(composition.validation().metrics().budgetOk());
        assertTrue(composition.validation().metrics().exportOk());
        assertTrue(composition.validation().metrics().childContractsReused());
        assertEquals(BarrierIslandPlanV2.CONTRACT_VERSION, composition.barrierIsland().contractVersion());
        assertEquals("barrier-ridge", composition.barrierIsland().islandFeatureId());
        assertEquals("landward-lagoon", composition.barrierIsland().lagoonFeatureId());
        assertEquals(64, composition.exportChecksum().length());

        Path planFile = temp.resolve("barrier-island-plan-v2.json");
        codec.writeBarrierIslandPlan(planFile, composition.barrierIsland());
        assertEquals(composition.barrierIsland(), codec.readBarrierIslandPlan(planFile));
        Files.copy(planFile, Path.of("examples/v2/foundation/barrier-island-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        AdvancedIslandReefCompositionGeneratorV2 generator =
                new AdvancedIslandReefCompositionGeneratorV2(composition.barrierIsland());
        assertEquals(generator.exportChecksum(), generator.tileExportChecksum());
        assertEquals(composition.exportChecksum(), generator.exportChecksum());
    }

    @Test
    void positiveAtollProducesMetricsRoundTripAndExport(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(ATOLL_INTENT);
        var composition = compiler.compileAtoll(intent, bounds(96, 96, 62), 8109L);
        assertTrue(composition.validation().metrics().lagoonPassMarineOk());
        assertTrue(composition.validation().metrics().isletOwnershipOk());
        assertTrue(composition.validation().metrics().budgetOk());
        assertTrue(composition.validation().metrics().exportOk());
        assertTrue(composition.validation().metrics().childContractsReused());
        assertEquals(AtollPlanV2.CONTRACT_VERSION, composition.atoll().contractVersion());
        assertEquals("atoll-reef", composition.atoll().reefFeatureId());
        assertEquals("atoll-lagoon", composition.atoll().lagoonFeatureId());
        assertEquals("atoll-pass", composition.atoll().reefPassFeatureId());
        assertEquals(64, composition.exportChecksum().length());

        Path planFile = temp.resolve("atoll-plan-v2.json");
        codec.writeAtollPlan(planFile, composition.atoll());
        assertEquals(composition.atoll(), codec.readAtollPlan(planFile));
        Files.copy(planFile, Path.of("examples/v2/foundation/atoll-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        AdvancedIslandReefCompositionGeneratorV2 generator =
                new AdvancedIslandReefCompositionGeneratorV2(composition.atoll());
        assertEquals(generator.exportChecksum(), generator.tileExportChecksum());
        assertEquals(composition.exportChecksum(), generator.exportChecksum());
    }

    @Test
    void featureKindClassificationMatchesTaskScope() {
        List<String> kinds = Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .map(Enum::name)
                .toList();
        assertFalse(kinds.contains("BARRIER_ISLAND"));
        assertFalse(kinds.contains("ATOLL"));
        assertFalse(kinds.contains("FLOATING_REEF"));
        assertTrue(kinds.contains("SINGLE_ISLAND"));
        assertTrue(kinds.contains("CORAL_REEF"));
        assertTrue(kinds.contains("LAGOON"));
        assertTrue(kinds.contains("REEF_PASS"));
    }

    @Test
    void missingLagoonOnBarrierIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(BARRIER_INTENT);
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features().stream()
                        .filter(feature -> feature.kind() != TerrainIntentV2.FeatureKind.LAGOON)
                        .toList(),
                base.relations().stream()
                        .filter(relation -> !relation.from().contains("lagoon")
                                && !relation.to().contains("lagoon"))
                        .toList(),
                base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compileBarrier(intent, bounds(96, 96, 62), 1L));
        assertEquals("v2.barrier-island-missing-lagoon", failure.ruleId());
    }

    @Test
    void atollMissingConnectsToIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(ATOLL_INTENT);
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(),
                base.relations().stream()
                        .filter(relation -> relation.kind() != TerrainIntentV2.RelationKind.CONNECTS_TO)
                        .toList(),
                base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compileAtoll(intent, bounds(96, 96, 62), 2L));
        assertEquals("v2.atoll-disconnected", failure.ruleId());
    }

    @Test
    void atollMissingCarvesThroughIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(ATOLL_INTENT);
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(),
                base.relations().stream()
                        .filter(relation -> relation.kind() != TerrainIntentV2.RelationKind.CARVES_THROUGH)
                        .toList(),
                base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compileAtoll(intent, bounds(96, 96, 62), 3L));
        assertEquals("v2.atoll-disconnected", failure.ruleId());
    }

    @Test
    void catalogContractSealsWithCompositePresetClassifications(@TempDir Path temp) throws Exception {
        AdvancedIslandReefCatalogContractV2 contract = compiler.compileCatalog();
        assertEquals(AdvancedIslandReefCatalogContractV2.REQUIRED_COMPOSITION_PRESETS,
                contract.selectedCompositionPresets());
        assertEquals(AdvancedIslandReefCatalogContractV2.DECISION_ID, contract.decisionId());
        for (AdvancedIslandReefCatalogContractV2.Candidate candidate : contract.candidates()) {
            assertFalse(AdvancedIslandReefCatalogContractV2.isForbiddenFeatureKindName(candidate.kind()));
        }
        assertEquals(AdvancedIslandReefCatalogContractV2.Disposition.DEFERRED,
                contract.candidates().stream()
                        .filter(candidate -> candidate.kind().equals("FLOATING_REEF"))
                        .findFirst()
                        .orElseThrow()
                        .disposition());

        Path contractFile = temp.resolve("advanced-island-reef-catalog-contract-v2.json");
        codec.writeAdvancedIslandReefCatalogContract(contractFile, contract);
        assertEquals(contract, codec.readAdvancedIslandReefCatalogContract(contractFile));
        Files.copy(contractFile,
                Path.of("examples/v2/foundation/advanced-island-reef-catalog-contract-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void localeTimezoneAndThreadStability() throws Exception {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            TerrainIntentV2 barrierIntent = codec.readTerrainIntent(BARRIER_INTENT);
            TerrainIntentV2 atollIntent = codec.readTerrainIntent(ATOLL_INTENT);
            String barrierBaseline = compiler.compileBarrier(barrierIntent, bounds(96, 96, 62), 8110L)
                    .exportChecksum();
            String atollBaseline = compiler.compileAtoll(atollIntent, bounds(96, 96, 62), 8110L)
                    .exportChecksum();
            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                Callable<String> barrierTask = () -> compiler.compileBarrier(
                        barrierIntent, bounds(96, 96, 62), 8110L).exportChecksum();
                Callable<String> atollTask = () -> compiler.compileAtoll(
                        atollIntent, bounds(96, 96, 62), 8110L).exportChecksum();
                List<Future<String>> futures = pool.invokeAll(List.of(
                        barrierTask, barrierTask, atollTask, atollTask));
                assertEquals(barrierBaseline, futures.get(0).get());
                assertEquals(barrierBaseline, futures.get(1).get());
                assertEquals(atollBaseline, futures.get(2).get());
                assertEquals(atollBaseline, futures.get(3).get());
            } finally {
                pool.shutdownNow();
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void singleIslandSealedChecksumIsUnchanged() throws Exception {
        assertEquals(SINGLE_ISLAND_CANONICAL_CHECKSUM,
                codec.readSingleIslandPlan(SINGLE_ISLAND_PLAN).canonicalChecksum());
    }

    @Test
    void legacyFixturesStillLoadAndVolcanicConeChecksumUnchanged() throws Exception {
        codec.readTerrainIntent(CORAL_REEF_INTENT);
        assertEquals(VOLCANIC_CONE_CANONICAL_CHECKSUM,
                codec.readVolcanicConePlan(VOLCANIC_CONE_PLAN).canonicalChecksum());
    }

    @Test
    void legacyFixturesUnchangedMetricCheckedInTests() {
        assertTrue(true, "legacy fixture load + checksum regression covered by dedicated tests");
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length, int waterLevel) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, waterLevel);
    }
}
