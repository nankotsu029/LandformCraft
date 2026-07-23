package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticGateContractV2;
import com.github.nankotsu029.landformcraft.core.v2.catalog.CompositionProfileRegistryV2;
import com.github.nankotsu029.landformcraft.core.v2.catalog.FeatureSupportCatalogConsistencyVerifierV2;
import com.github.nankotsu029.landformcraft.core.v2.export.HardPreflightGateV2;
import com.github.nankotsu029.landformcraft.core.v2.export.HardPreflightRejectedV2;
import com.github.nankotsu029.landformcraft.core.v2.export.HardPreflightResultV2;
import com.github.nankotsu029.landformcraft.core.v2.export.IntentContributionCoverageV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportResultV2;
import com.github.nankotsu029.landformcraft.core.v2.export.SurfaceBaselineV2;
import com.github.nankotsu029.landformcraft.core.v2.export.SurfaceFoundationOwnerGateV2;
import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportLevelV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;
import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetDrivenValidatorV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetEvaluationV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-18-12 Macro foundation / intent conformance Phase gate evidence.
 *
 * <p>Pins the configuration V2-18 exists to establish, as executable claims: every surface cell has
 * an effective foundation owner behind a fail-closed export spine (preflight → target-driven EDGE
 * validation → owner-coverage gate), and the intent-conformance portfolio — measurement-only under
 * {@code V2-18-11} — is promoted here into the Phase gate. From this gate on, a portfolio case may
 * no longer pin a known shape non-conformance as an annotated expectation: the declared arm set and
 * the shore-connected arm set must be identical, and every conformance measurement must hold, or
 * the Phase gate fails and the defect goes to a new Task id (as {@code coastal-honored-400}'s east
 * arm went to {@code V2-18-13}).</p>
 *
 * <p>The leaf corpora (diagnostic gate contract golden, coverage report, preflight negatives,
 * EDGE evaluator, breakwater metric, constraint-map binding, conformance residual contract, macro
 * foundation kernel invariants, owner gate fail-closed negatives, portfolio determinism) re-run in
 * this gate's full clean suite; this class re-verifies the phase-distinctive facts end to end.</p>
 */
class MacroFoundationPhaseGateV2Test {
    @TempDir
    static Path root;

    private static GenerationExecutors executors;
    private static final Map<String, IntentConformancePortfolioV2.MeasurementsV2> MEASUREMENTS =
            new LinkedHashMap<>();
    private static final Map<String, TerrainIntentV2> SEALED_INTENTS = new LinkedHashMap<>();
    private static final Map<String, WorldBlueprintV2> FROZEN_BLUEPRINTS = new LinkedHashMap<>();

    static Stream<IntentConformancePortfolioV2.CaseV2> cases() {
        return IntentConformancePortfolioV2.cases().stream();
    }

    @BeforeAll
    static void exportAndMeasureThePortfolio() throws Exception {
        executors = GenerationExecutors.createDefault(2);
        // The gate exports every portfolio case itself, through the public production export
        // service, and measures only the published Release — its own end-to-end evidence rather
        // than a re-statement of the portfolio test's.
        for (IntentConformancePortfolioV2.CaseV2 portfolioCase : IntentConformancePortfolioV2.cases()) {
            Path run = root.resolve(portfolioCase.id());
            Release2ExportResultV2 result = new Release2ExportApplicationServiceV2(executors).exportNow(
                    new Release2ExportRequestV2(
                            portfolioCase.request(), portfolioCase.intent(),
                            run.resolve("work"), run.resolve("exports"),
                            portfolioCase.id(), portfolioCase.baseline()));
            MEASUREMENTS.put(portfolioCase.id(),
                    IntentConformancePortfolioV2.measure(result.releaseDirectory()));
            SEALED_INTENTS.put(portfolioCase.id(),
                    IntentConformancePortfolioV2.intentOf(result.releaseDirectory()));
            FROZEN_BLUEPRINTS.put(portfolioCase.id(),
                    IntentConformancePortfolioV2.blueprintOf(result.releaseDirectory()));
        }
    }

    @AfterAll
    static void stopExecutors() {
        executors.shutdown(Duration.ofSeconds(30));
        executors.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void theConformancePortfolioGatesThePhaseWithNoRegisteredNonConformance(
            IntentConformancePortfolioV2.CaseV2 portfolioCase) {
        // Gate promotion: a case entry pinning a disconnected arm was legitimate measurement
        // documentation under V2-18-11; under the Phase gate it is a failure.
        assertEquals(portfolioCase.declaredArmIds(), portfolioCase.shoreConnectedArmIds(),
                "portfolio case registers a shape non-conformance; split it to a new Task id instead");

        IntentConformancePortfolioV2.MeasurementsV2 measurements = MEASUREMENTS.get(portfolioCase.id());
        TerrainIntentV2 intent = SEALED_INTENTS.get(portfolioCase.id());

        // Non-vacuity: every production-connected coastal kind (plus the backshore hinterland the
        // continuity assertions read) must actually exist in the sealed intent, so an emptied case
        // can never pass as "everything present was conformant".
        Set<TerrainIntentV2.FeatureKind> declaredKinds = intent.features().stream()
                .map(TerrainIntentV2.Feature::kind)
                .collect(Collectors.toCollection(
                        () -> EnumSet.noneOf(TerrainIntentV2.FeatureKind.class)));
        assertTrue(declaredKinds.containsAll(EnumSet.of(
                        TerrainIntentV2.FeatureKind.SANDY_BEACH,
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                        TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS)),
                () -> "case does not exercise the production-connected coastal set: " + declaredKinds);

        // EDGE conformance: the declared macro composition is HARD again and measured as satisfied,
        // and no HARD EDGE constraint of the sealed intent was dropped on the way into the frozen
        // blueprint — the measured target id set must equal the declared HARD constraint id set.
        Set<String> declaredHardEdgeIds = intent.constraints().stream()
                .filter(constraint -> constraint instanceof TerrainIntentV2.EdgeClassificationConstraint
                        && constraint.strength() == TerrainIntentV2.Strength.HARD)
                .map(TerrainIntentV2.Constraint::id)
                .collect(Collectors.toSet());
        assertFalse(declaredHardEdgeIds.isEmpty(), "case declares no HARD EDGE constraint");
        assertEquals(declaredHardEdgeIds,
                measurements.edgeEvaluations().stream()
                        .map(TargetEvaluationV2::targetId).collect(Collectors.toSet()),
                "HARD EDGE constraints were dropped or invented between intent and blueprint");
        for (TargetEvaluationV2 edge : measurements.edgeEvaluations()) {
            assertEquals(TerrainIntentV2.Strength.HARD, edge.hardness(), edge.targetId());
            assertTrue(edge.satisfied(), edge::detail);
        }

        // Beach ↔ backshore land continuity: one mainland land mass carries both.
        IntentConformancePortfolioV2.BeachContinuityV2 beach = measurements.beach();
        IntentConformancePortfolioV2.HinterlandV2 hinterland = measurements.backshorePlains();
        assertTrue(beach.landBandCells() > 0, "the beach owns no foreshore/backshore cell");
        assertEquals(beach.landBandCells(), beach.landBandOnLand());
        assertEquals(1, beach.landBandComponentCount());
        assertEquals(beach.landBandCells(), beach.landBandInMainland());
        assertTrue(beach.nearshoreCells() > 0, "the beach owns no nearshore cell");
        assertEquals(beach.nearshoreCells(), beach.nearshoreOnWater());
        assertTrue(hinterland.polygonCells() > 0, "the case declares no backshore hinterland area");
        assertEquals(hinterland.polygonCells(), hinterland.onLand());
        assertEquals(hinterland.polygonCells(), hinterland.inMainland());

        // Every declared breakwater arm lands: the measured arm id set equals the declared one
        // (never empty), footprint touches off-structure mainland and the declared landfall cell
        // sits on the mainland.
        assertFalse(portfolioCase.declaredArmIds().isEmpty(), "case declares no breakwater arm");
        assertEquals(portfolioCase.declaredArmIds(),
                measurements.arms().stream()
                        .map(IntentConformancePortfolioV2.ArmLandfallV2::armId)
                        .collect(Collectors.toSet()));
        for (IntentConformancePortfolioV2.ArmLandfallV2 arm : measurements.arms()) {
            assertTrue(arm.ownedCells() > 0, arm.armId());
            assertTrue(arm.connectedToShore(), arm::toString);
        }

        // Land-mass accounting: besides the one mainland, the only land the release may carry
        // offshore is the rocky cape's planned sea stacks. A planned stack may merge into the shore
        // (the measured component count can be lower), but there may never be MORE components than
        // planned stacks, and the total off-mainland land must fit inside the planned stack
        // footprints — a stranded beach or breakwater arm (V2-18-13's defect was 2163 cells) can
        // satisfy neither bound.
        var seaStacks = FROZEN_BLUEPRINTS.get(portfolioCase.id())
                .rockyCapePlans().getFirst().seaStacks();
        assertFalse(seaStacks.isEmpty(), "case plans no sea stack");
        assertTrue(measurements.landComponentCount() - 1 <= seaStacks.size(),
                () -> "more off-mainland land components than planned sea stacks: "
                        + (measurements.landComponentCount() - 1) + " > " + seaStacks.size());
        long plannedStackFootprint = seaStacks.stream()
                .mapToLong(stack -> (long) (2 * stack.radiusBlocks() + 1)
                        * (2 * stack.radiusBlocks() + 1))
                .sum();
        long offMainlandLand = measurements.landCells() - measurements.mainlandCells();
        assertTrue(offMainlandLand <= plannedStackFootprint,
                () -> "off-mainland land exceeds the planned sea stack footprint: "
                        + offMainlandLand + " > " + plannedStackFootprint);
    }

    @Test
    void theFailClosedExportSpineContractsAreUnchanged() {
        // V2-18-01: diagnostic issues are classified against a versioned gate contract, and exactly
        // the four production-connected coastal kinds are exempt from the blanket unsupported ERROR.
        assertEquals("diagnostic-gate-contract-v1", DiagnosticGateContractV2.CONTRACT_VERSION);
        assertEquals(EnumSet.of(
                        TerrainIntentV2.FeatureKind.SANDY_BEACH,
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE),
                DiagnosticGateContractV2.builtIn().productionConnectedKinds());

        // V2-18-02: the coverage report stays a versioned, report-only contract.
        assertEquals("intent-contribution-coverage-v1", IntentContributionCoverageV2.CONTRACT_VERSION);

        // V2-18-03: the HARD preflight gate keeps its three stable rejection rule ids.
        assertEquals("v2.preflight.hard-constraint-unevaluated",
                HardPreflightGateV2.RULE_HARD_CONSTRAINT_UNEVALUATED);
        assertEquals("v2.preflight.hard-relation-unconsumed",
                HardPreflightGateV2.RULE_HARD_RELATION_UNCONSUMED);
        assertEquals("v2.preflight.map-reference-unresolved",
                HardPreflightGateV2.RULE_MAP_REFERENCE_UNRESOLVED);

        // V2-18-04: EDGE_CLASSIFICATION is evaluated post-generation by the target-driven framework,
        // which is why preflight no longer rejects it.
        assertTrue(TargetDrivenValidatorV2.BUILT_IN_EVALUATED_CONSTRAINT_RULES
                .contains("v2.edge-classification"));

        // V2-18-10: surface foundation owner coverage below 100% is fail-closed, with no override.
        assertEquals("surface-foundation-owner-gate-v1", SurfaceFoundationOwnerGateV2.CONTRACT_VERSION);
        assertEquals("v2.export.foundation-owner-coverage-incomplete",
                SurfaceFoundationOwnerGateV2.RULE_FOUNDATION_OWNER_COVERAGE_INCOMPLETE);
        assertEquals(1_000_000, SurfaceFoundationOwnerGateV2.REQUIRED_COVERAGE_MILLIONTHS);
    }

    @Test
    void aNonHonorableLegacyIntentNeverReachesArtifactPublication(@TempDir Path run) {
        // The audit's original fixture still declares HARD requirements nothing honors (an
        // unevaluated HARD METRIC_RANGE and a HARD relation to contract-only BACKSHORE_PLAINS), so
        // the full production export service must reject it before any artifact exists.
        Path exports = run.resolve("exports");
        HardPreflightRejectedV2 rejected = assertThrows(HardPreflightRejectedV2.class,
                () -> new Release2ExportApplicationServiceV2(executors).exportNow(
                        new Release2ExportRequestV2(
                                Path.of("examples/v2/diagnostic/coastal-fishing-map.request-v2.json"),
                                Path.of("examples/v2/diagnostic/coastal-fishing-map.terrain-intent-v2.json"),
                                run.resolve("work"), exports,
                                "coastal-fishing-map-phase-gate",
                                new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 42))));

        // Pin the full finding set (rule id → subjects), not just the exception type: losing either
        // detection, or drifting to another subject, must fail this gate.
        Map<String, Set<String>> findings = rejected.rejections().stream()
                .collect(Collectors.groupingBy(
                        HardPreflightResultV2.Finding::ruleId,
                        Collectors.mapping(HardPreflightResultV2.Finding::subjectId,
                                Collectors.toSet())));
        assertEquals(Map.of(
                        HardPreflightGateV2.RULE_HARD_CONSTRAINT_UNEVALUATED, Set.of("beach-width"),
                        HardPreflightGateV2.RULE_HARD_RELATION_UNCONSUMED,
                        Set.of("backshore-adjoins-beach")),
                findings);
        assertTrue(Files.notExists(exports) || isEmptyDirectory(exports),
                "a preflight-rejected export published something");
    }

    @Test
    void theCompositionProfileRegistryMatchesTheAcceptedAdr0038Table() {
        CompositionProfileRegistryV2 registry = CompositionProfileRegistryV2.builtIn();
        List<TerrainIntentV2.FeatureKind> kinds = List.of(TerrainIntentV2.FeatureKind.values());
        assertEquals(60, kinds.size());

        // ADR 0038 D4: NORMATIVE 6 / PROVISIONAL 54. Every PROVISIONAL kind is confirmed only by
        // its own V2-15 wiring Task's field audit — the per-kind obligation the stage-gate release
        // decision converts the blanket hold into.
        Set<TerrainIntentV2.FeatureKind> normative = kinds.stream()
                .filter(kind -> registry.registration(kind).confidence()
                        == CompositionProfileRegistryV2.Confidence.NORMATIVE)
                .collect(Collectors.toCollection(
                        () -> EnumSet.noneOf(TerrainIntentV2.FeatureKind.class)));
        assertEquals(EnumSet.of(
                        TerrainIntentV2.FeatureKind.SANDY_BEACH,
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                        TerrainIntentV2.FeatureKind.PLAIN,
                        TerrainIntentV2.FeatureKind.HILL_RANGE),
                normative);
        assertEquals(54, kinds.size() - normative.size());
        assertEquals(17, registry.foundationEligibleKinds().size());

        // D4 reclassification: ABYSSAL_PLAIN is a basin-floor modifier, never a foundation producer.
        assertFalse(registry.profile(TerrainIntentV2.FeatureKind.ABYSSAL_PLAIN).foundationEligible());

        // D3 lookup rule: alias/subtype kinds inherit their canonical carrier's profile.
        CompositionProfileRegistryV2.Registration backshore =
                registry.registration(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS);
        assertTrue(backshore.carrierInherited());
        assertEquals(registry.profile(TerrainIntentV2.FeatureKind.PLAIN), backshore.profile());
    }

    @Test
    void thePhaseGatePromotesNoCapabilityOrDimension() throws Exception {
        // V2-18 is an offline conformance phase: no Paper capability, dimension, or Release 2
        // capability may have moved. The sealed catalog stays byte-stable.
        FeatureSupportCatalogV2 catalog =
                new FeatureSupportCatalogConsistencyVerifierV2().requireConsistentBuiltIn();
        assertEquals(Set.of("SANDY_BEACH", "BREAKWATER_HARBOR", "HARBOR_BASIN", "ROCKY_CAPE"),
                catalog.entries().stream()
                        .filter(entry -> entry.support().level(FeatureSupportCapabilityV2.PAPER_APPLY)
                                == FeatureSupportLevelV2.SUPPORTED)
                        .map(entry -> entry.entryId())
                        .collect(Collectors.toSet()),
                "exactly the four surface-2_5d features may be paper_apply SUPPORTED");
        assertEquals(PlacementDimensionLimitV2.measured(), catalog.placementDimensionLimit());
        assertTrue(catalog.placementDimensionLimit().admits(1_000, 1_000));
        assertTrue(catalog.rejectsUnmeasuredPaperPromotion(1_001, 1_000));

        FeatureSupportCatalogCodecV2 catalogCodec = new FeatureSupportCatalogCodecV2();
        FeatureSupportCatalogV2 sealed =
                catalogCodec.read(Path.of("examples/v2/catalog/feature-support-catalog-v2.json"));
        catalogCodec.verifyChecksum(sealed);
        assertEquals(catalogCodec.builtInSealed().canonicalChecksum(), sealed.canonicalChecksum());

        assertEquals(List.of(
                        ReleaseArtifactCatalogV2.ENVIRONMENT_FIELDS,
                        ReleaseArtifactCatalogV2.HYDROLOGY_PLAN,
                        ReleaseArtifactCatalogV2.SPARSE_VOLUME,
                        ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT);
        assertEquals(Set.of(
                        ReleaseCapabilityDependencyMatrixV2.CORE_ONLY,
                        ReleaseCapabilityDependencyMatrixV2.SURFACE_ONLY,
                        ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_WITH_SURFACE,
                        ReleaseCapabilityDependencyMatrixV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE,
                        ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME_WITH_ENVIRONMENT),
                ReleaseCapabilityDependencyMatrixV2.validPrefixes());
    }

    private static boolean isEmptyDirectory(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
