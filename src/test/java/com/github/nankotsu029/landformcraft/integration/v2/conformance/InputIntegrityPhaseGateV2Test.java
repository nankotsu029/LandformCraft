package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticGateContractV2;
import com.github.nankotsu029.landformcraft.core.v2.catalog.CompositionProfileRegistryV2;
import com.github.nankotsu029.landformcraft.core.v2.catalog.FeatureSupportCatalogConsistencyVerifierV2;
import com.github.nankotsu029.landformcraft.core.v2.catalog.PublicDispatchReachabilityV2;
import com.github.nankotsu029.landformcraft.core.v2.design.DesignSupportLintServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.HardPreflightGateV2;
import com.github.nankotsu029.landformcraft.core.v2.export.MaskFeatureReconcileV2;
import com.github.nankotsu029.landformcraft.core.v2.export.ProductionDispatchRegistryV2;
import com.github.nankotsu029.landformcraft.core.v2.export.ProductionRoutePreconditionsV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportResultV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2HydrologyExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.SurfaceFoundationOwnerGateV2;
import com.github.nankotsu029.landformcraft.core.v2.material.EnvironmentSurfaceMaterialV2;
import com.github.nankotsu029.landformcraft.core.v2.material.SurfaceMaterialProfileV2;
import com.github.nankotsu029.landformcraft.core.v2.material.SurfaceMaterializationV2;
import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.detail.CoherentDetailKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportEntryV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportLevelV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignSupportSurfaceV2;
import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetDrivenValidatorV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetEvaluationV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
 * V2-19-16 Input integrity / block materialization Phase gate evidence.
 *
 * <p>Pins the configuration V2-19 exists to establish, as executable claims: every publicly routed
 * kind materializes blocks (the 2026-07-23 audit's plan-only wiring can no longer close a leaf), the
 * V2-19-01 semantic materialization gate is in effect and rejects every no-op shape, the ADR 0040
 * contributor-subset cases are promoted into the Phase gate (the handoff
 * {@link MacroFoundationPhaseGateV2Test} recorded), and the reopened Track E leaf surface — dispatch
 * registry, reachability projection, design-time support lint, composition-profile obligations — is
 * exactly what {@code V2-15-11} restarts against. No capability or dimension moves.</p>
 *
 * <p>The leaf corpora (materialization gate contract, river bed/materialization negatives, height
 * guide consumer, producer tier kernel invariants, support lint, subset runtime, material profile,
 * detail kernel, reconcile pre-pass, reference-image design E2E, constraint-source binding E2E,
 * Gradle input drift guard) re-run in this gate's full clean suite; this class re-verifies the
 * phase-distinctive facts end to end over Releases it exports itself.</p>
 */
class InputIntegrityPhaseGateV2Test {
    @TempDir
    static Path root;

    private static GenerationExecutors executors;
    private static final Map<String, Path> RELEASES = new LinkedHashMap<>();
    private static final Map<String, IntentConformancePortfolioV2.MeasurementsV2> MEASUREMENTS =
            new LinkedHashMap<>();
    private static final Map<String, TerrainIntentV2> SEALED_INTENTS = new LinkedHashMap<>();
    private static final Map<String, WorldBlueprintV2> FROZEN_BLUEPRINTS = new LinkedHashMap<>();

    /**
     * Every portfolio case, with no four-contributor filter: this gate is where the V2-19-09
     * contributor-subset cases ({@code harbor-cove-64-honored-beach},
     * {@code harbor-cove-64-honored-coastless}) are promoted from portfolio-test coverage into a
     * Phase gate, closing the handoff the V2-18-12 gate documented.
     */
    static Stream<IntentConformancePortfolioV2.CaseV2> cases() {
        return IntentConformancePortfolioV2.cases().stream();
    }

    @Test
    void theGateCoversTheWholePortfolioIncludingContributorSubsets() {
        List<String> gated = cases().map(IntentConformancePortfolioV2.CaseV2::id).toList();
        assertEquals(IntentConformancePortfolioV2.cases().stream()
                        .map(IntentConformancePortfolioV2.CaseV2::id).toList(),
                gated, "the V2-19 gate must cover the whole portfolio, unfiltered");
        // V2-15-11 added the harbor-cove-64-honored-lake case, V2-15-12 added
        // harbor-cove-64-honored-canyon and V2-15-13 added harbor-cove-64-honored-waterfall to the
        // permanent portfolio; this gate covers them automatically since it re-exports every
        // registered case.
        assertEquals(13, gated.size(), () -> "V2-19-16 gated cases changed: " + gated);
        // The promotion this gate performs: both ADR 0040 subset cases are gated here, and the
        // four-contributor cases the V2-18-12 gate keeps gating are all still present.
        assertTrue(gated.containsAll(List.of(
                        "harbor-cove-64-honored-beach", "harbor-cove-64-honored-coastless")),
                () -> "an ADR 0040 subset case fell out of the Phase gate: " + gated);
        // V2-15-11 added harbor-cove-64-honored-lake, V2-15-12 added harbor-cove-64-honored-canyon
        // and V2-15-13 added harbor-cove-64-honored-waterfall, all also declaring the
        // four-contributor set.
        assertEquals(11, cases().filter(portfolioCase -> portfolioCase.declaredCoastalKinds()
                        .equals(IntentConformancePortfolioV2.CaseV2.coastalFourAndBackshore()))
                .count());
    }

    @BeforeAll
    static void exportAndMeasureThePortfolio() throws Exception {
        executors = GenerationExecutors.createDefault(2);
        // The gate exports every portfolio case itself, through the public production export
        // services, and measures only the published Releases — its own end-to-end evidence rather
        // than a re-statement of the portfolio test's.
        for (IntentConformancePortfolioV2.CaseV2 portfolioCase : IntentConformancePortfolioV2.cases()) {
            Path run = root.resolve(portfolioCase.id());
            Release2ExportRequestV2 request = new Release2ExportRequestV2(
                    portfolioCase.request(), portfolioCase.intent(),
                    run.resolve("work"), run.resolve("exports"),
                    portfolioCase.id(), portfolioCase.baseline());
            Release2ExportResultV2 result = portfolioCase.exportRoute()
                    == IntentConformancePortfolioV2.ExportRouteV2.HYDROLOGY
                    ? new Release2HydrologyExportApplicationServiceV2(executors).exportNow(request)
                    : new Release2ExportApplicationServiceV2(executors).exportNow(request);
            RELEASES.put(portfolioCase.id(), result.releaseDirectory());
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
        // Gate promotion over the whole portfolio: a case entry pinning a disconnected arm stays a
        // failure (the V2-18-12 rule), now with no four-contributor filter in front of it.
        assertEquals(portfolioCase.declaredArmIds(), portfolioCase.shoreConnectedArmIds(),
                "portfolio case registers a shape non-conformance; split it to a new Task id instead");

        IntentConformancePortfolioV2.MeasurementsV2 measurements = MEASUREMENTS.get(portfolioCase.id());
        TerrainIntentV2 intent = SEALED_INTENTS.get(portfolioCase.id());

        // Non-vacuity under ADR 0040: what the case says it declares must be exactly what the sealed
        // intent declares, over the coastal contributor set — a silently dropped (or smuggled-in)
        // contributor can never pass as "everything present was conformant".
        Set<TerrainIntentV2.FeatureKind> sealedCoastalKinds = intent.features().stream()
                .map(TerrainIntentV2.Feature::kind)
                .filter(IntentConformancePortfolioV2.CaseV2.coastalFourAndBackshore()::contains)
                .collect(Collectors.toSet());
        assertEquals(portfolioCase.declaredCoastalKinds(), sealedCoastalKinds,
                "the sealed intent's coastal contributor set drifted from the case declaration");

        // Measurement presence mirrors the declaration: absence is reported, never substituted with
        // zeroes that would read as a passing measurement (ADR 0040 D1 sizes 0..4).
        assertEquals(portfolioCase.declaredCoastalKinds()
                        .contains(TerrainIntentV2.FeatureKind.SANDY_BEACH),
                measurements.beach().isPresent(), "beach measurement presence");
        assertEquals(portfolioCase.declaredCoastalKinds()
                        .contains(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS),
                measurements.backshorePlains().isPresent(), "backshore measurement presence");
        assertEquals(portfolioCase.declaredArmIds(),
                measurements.arms().stream()
                        .map(IntentConformancePortfolioV2.ArmLandfallV2::armId)
                        .collect(Collectors.toSet()),
                "arm measurement presence");

        // EDGE conformance: every case — including size 0, where the macro foundation must satisfy
        // the declared composition on its own — declares a non-empty HARD EDGE contract, nothing is
        // dropped or invented between intent and blueprint, and every target measures satisfied.
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

        // Beach ↔ backshore land continuity, whenever the case declares a beach: one mainland land
        // mass carries both bands and the declared hinterland.
        if (measurements.beach().isPresent()) {
            IntentConformancePortfolioV2.BeachContinuityV2 beach = measurements.beach().orElseThrow();
            IntentConformancePortfolioV2.HinterlandV2 hinterland =
                    measurements.backshorePlains().orElseThrow();
            assertTrue(beach.landBandCells() > 0, "the beach owns no foreshore/backshore cell");
            assertEquals(beach.landBandCells(), beach.landBandOnLand());
            assertEquals(1, beach.landBandComponentCount());
            assertEquals(beach.landBandCells(), beach.landBandInMainland());
            assertTrue(beach.nearshoreCells() > 0, "the beach owns no nearshore cell");
            assertEquals(beach.nearshoreCells(), beach.nearshoreOnWater());
            assertTrue(hinterland.polygonCells() > 0, "the case declares no backshore hinterland area");
            assertEquals(hinterland.polygonCells(), hinterland.onLand());
            assertEquals(hinterland.polygonCells(), hinterland.inMainland());
        }

        // Every declared breakwater arm lands; a case that declares none has none to land.
        for (IntentConformancePortfolioV2.ArmLandfallV2 arm : measurements.arms()) {
            assertTrue(arm.ownedCells() > 0, arm.armId());
            assertTrue(arm.connectedToShore(), arm::toString);
        }

        // Land-mass accounting. With a declared rocky cape, off-mainland land stays inside the
        // planned sea-stack budget (the V2-18-12 rule). Without one, there is still land and a
        // mainland — the declared HARD mask, honored by the macro foundation alone.
        assertTrue(measurements.landCells() > 0, "the published release carries no land at all");
        assertTrue(measurements.mainlandCells() > 0, "the published release has no mainland");
        if (portfolioCase.declaredCoastalKinds().contains(TerrainIntentV2.FeatureKind.ROCKY_CAPE)) {
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
    }

    @Test
    void theSemanticMaterializationGateIsInEffectForEveryPubliclyRoutedKind() throws Exception {
        // The phase's core claim, displayed: after V2-19-05/07, no publicly routed kind is plan-only.
        // The audit's defect class — a route that publishes validation JSON and changes no block —
        // has no remaining instance, and the three display axes stay separate.
        PublicDispatchReachabilityV2 reachability = PublicDispatchReachabilityV2.builtIn();
        assertEquals("public-dispatch-reachability-v1", PublicDispatchReachabilityV2.CONTRACT_VERSION);
        assertEquals(EnumSet.of(
                        TerrainIntentV2.FeatureKind.SANDY_BEACH,
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE),
                reachability.kindsWith(
                        PublicDispatchReachabilityV2.ReachabilityV2.PRODUCTION_CONNECTED));
        assertEquals(EnumSet.of(
                        TerrainIntentV2.FeatureKind.RIVER,
                        TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                        TerrainIntentV2.FeatureKind.PLAIN,
                        TerrainIntentV2.FeatureKind.LAKE,
                        TerrainIntentV2.FeatureKind.CANYON,
                        TerrainIntentV2.FeatureKind.WATERFALL),
                reachability.kindsWith(
                        PublicDispatchReachabilityV2.ReachabilityV2.OFFLINE_PRODUCTION));
        assertEquals(EnumSet.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS),
                reachability.kindsWith(PublicDispatchReachabilityV2.ReachabilityV2.CONTRACT_ONLY));
        assertEquals(reachability.kindsWith(
                        PublicDispatchReachabilityV2.ReachabilityV2.OFFLINE_PRODUCTION),
                reachability.offlineKindsWith(
                        PublicDispatchReachabilityV2.BlockMaterializationV2.MATERIALIZED),
                "an offline-production route lost its block-materialization evidence");
        assertEquals(EnumSet.noneOf(TerrainIntentV2.FeatureKind.class),
                reachability.offlineKindsWith(
                        PublicDispatchReachabilityV2.BlockMaterializationV2.PLAN_ONLY),
                "a publicly routed kind is still plan-only at the V2-19 Phase gate");

        // MATERIALIZED is not a label: re-measure each routed kind's block effect from this gate's
        // own published Releases against the shared baseline, and hold it to its declared classes.
        Path baseline = RELEASES.get("harbor-cove-64-honored");
        FeatureMaterializationV2.BlockEffectV2 river = FeatureMaterializationV2.measureBlockEffect(
                baseline, RELEASES.get("harbor-cove-64-honored-river"));
        FeatureMaterializationV2.BlockEffectV2 meander = FeatureMaterializationV2.measureBlockEffect(
                baseline, RELEASES.get("harbor-cove-64-honored-meander"));
        assertEquals(river, meander,
                "RIVER and MEANDERING_RIVER share one declared reach and must share one block effect");
        for (Map.Entry<TerrainIntentV2.FeatureKind, String> riverCase
                : IntentConformancePortfolioV2.riverCaseIdsByKind().entrySet()) {
            FeatureMaterializationV2.requireMaterialized(
                    new FeatureMaterializationV2.MaterializationClaimV2(
                            IntentConformancePortfolioV2.RIVER_FEATURE_ID,
                            riverCase.getKey(),
                            Set.of("hydrology.river.channel-mask", "hydrology.bed.elevation",
                                    "hydrology.water.surface"),
                            Set.of(FeatureMaterializationV2.EffectClassV2.SOLID_SHAPE,
                                    FeatureMaterializationV2.EffectClassV2.FLUID)),
                    river);
        }
        FeatureMaterializationV2.requireMaterialized(
                new FeatureMaterializationV2.MaterializationClaimV2(
                        IntentConformancePortfolioV2.PLAIN_FEATURE_ID,
                        TerrainIntentV2.FeatureKind.PLAIN,
                        Set.of("foundation.plain.mask", "foundation.plain.base-elevation",
                                "foundation.plain.micro-relief"),
                        Set.of(FeatureMaterializationV2.EffectClassV2.SOLID_SHAPE,
                                FeatureMaterializationV2.EffectClassV2.MATERIAL)),
                FeatureMaterializationV2.measureBlockEffect(
                        baseline, RELEASES.get("harbor-cove-64-honored-plain")));
        FeatureMaterializationV2.requireMaterialized(
                new FeatureMaterializationV2.MaterializationClaimV2(
                        IntentConformancePortfolioV2.LAKE_FEATURE_ID,
                        TerrainIntentV2.FeatureKind.LAKE,
                        Set.of("hydrology.lake.basin-mask", "hydrology.bed.elevation",
                                "hydrology.water.surface"),
                        Set.of(FeatureMaterializationV2.EffectClassV2.SOLID_SHAPE,
                                FeatureMaterializationV2.EffectClassV2.FLUID)),
                FeatureMaterializationV2.measureBlockEffect(
                        baseline, RELEASES.get("harbor-cove-64-honored-lake")));
        // A CANYON cannot be declared without its shared MEANDERING_RIVER (HARD WITHIN), so this
        // route's own block diff also carries the companion river's SOLID_SHAPE/FLUID effect.
        FeatureMaterializationV2.requireMaterialized(
                new FeatureMaterializationV2.MaterializationClaimV2(
                        IntentConformancePortfolioV2.CANYON_FEATURE_ID,
                        TerrainIntentV2.FeatureKind.CANYON,
                        Set.of("landform.canyon.canyon-mask", "landform.canyon.surface-height",
                                "hydrology.bed.elevation", "hydrology.river.channel-mask",
                                "hydrology.water.surface"),
                        Set.of(FeatureMaterializationV2.EffectClassV2.SOLID_SHAPE,
                                FeatureMaterializationV2.EffectClassV2.FLUID)),
                FeatureMaterializationV2.measureBlockEffect(
                        baseline, RELEASES.get("harbor-cove-64-honored-canyon")));
        // A WATERFALL cannot be declared without its host MEANDERING_RIVER (HARD ON_PATH_OF), so
        // this route's own block diff also carries the host reach's SOLID_SHAPE/FLUID effect.
        FeatureMaterializationV2.requireMaterialized(
                new FeatureMaterializationV2.MaterializationClaimV2(
                        IntentConformancePortfolioV2.WATERFALL_FEATURE_ID,
                        TerrainIntentV2.FeatureKind.WATERFALL,
                        Set.of("hydrology.waterfall.plunge-pool-mask",
                                "hydrology.waterfall.plunge-pool-floor",
                                "hydrology.waterfall.base-elevation",
                                "hydrology.bed.elevation"),
                        Set.of(FeatureMaterializationV2.EffectClassV2.SOLID_SHAPE,
                                FeatureMaterializationV2.EffectClassV2.FLUID)),
                FeatureMaterializationV2.measureBlockEffect(
                        baseline, RELEASES.get("harbor-cove-64-honored-waterfall")));

        // The gate itself stays in effect: a passing plan column cannot substitute for an empty
        // block column, and the identity stream — the shape of every intentional no-op — always fails.
        assertTrue(IntentConformancePortfolioV2
                        .readHydrologyValidationReport(RELEASES.get("harbor-cove-64-honored-river"))
                        .passesHardValidation(),
                "the plan column itself regressed; the separation claim below would be vacuous");
        FeatureMaterializationV2.BlockEffectV2 identity =
                FeatureMaterializationV2.measureBlockEffect(baseline, baseline);
        assertEquals(0L, identity.changedCells());
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> FeatureMaterializationV2.requireMaterialized(
                        new FeatureMaterializationV2.MaterializationClaimV2(
                                IntentConformancePortfolioV2.RIVER_FEATURE_ID,
                                TerrainIntentV2.FeatureKind.RIVER,
                                Set.of("hydrology.river.channel-mask"),
                                Set.of(FeatureMaterializationV2.EffectClassV2.SOLID_SHAPE,
                                        FeatureMaterializationV2.EffectClassV2.FLUID)),
                        identity));
        assertTrue(rejected.getMessage().contains("no effect on the final canonical block stream"),
                rejected::getMessage);
    }

    @Test
    void theFailClosedExportSpineContractsIncludeTheV219Additions() {
        // The V2-18 spine is unchanged at V2-19 close…
        assertEquals("diagnostic-gate-contract-v1", DiagnosticGateContractV2.CONTRACT_VERSION);
        assertEquals(EnumSet.of(
                        TerrainIntentV2.FeatureKind.SANDY_BEACH,
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE),
                DiagnosticGateContractV2.builtIn().productionConnectedKinds());
        assertEquals("v2.preflight.hard-constraint-unevaluated",
                HardPreflightGateV2.RULE_HARD_CONSTRAINT_UNEVALUATED);
        assertEquals("v2.preflight.hard-relation-unconsumed",
                HardPreflightGateV2.RULE_HARD_RELATION_UNCONSUMED);
        assertEquals("v2.preflight.map-reference-unresolved",
                HardPreflightGateV2.RULE_MAP_REFERENCE_UNRESOLVED);
        assertTrue(TargetDrivenValidatorV2.BUILT_IN_EVALUATED_CONSTRAINT_RULES
                .contains("v2.edge-classification"));
        assertEquals("surface-foundation-owner-gate-v1", SurfaceFoundationOwnerGateV2.CONTRACT_VERSION);
        assertEquals("v2.export.foundation-owner-coverage-incomplete",
                SurfaceFoundationOwnerGateV2.RULE_FOUNDATION_OWNER_COVERAGE_INCOMPLETE);
        assertEquals(1_000_000, SurfaceFoundationOwnerGateV2.REQUIRED_COVERAGE_MILLIONTHS);

        // …and the contracts V2-19 added on top of it are pinned by their public identifiers.
        // V2-15-10 / ADR 0039 Candidate A: the offline-production route class rides the v2 registry.
        assertEquals("production-dispatch-registry-v2", ProductionDispatchRegistryV2.CONTRACT_VERSION);
        // V2-19-08: design-time support lint stays advisory (its NON_GATING nature is enforced by
        // the DesignAuditV2 model and schema; the leaf corpus pins the rejection of GATING findings).
        assertEquals("design-support-lint-v1", DesignSupportSurfaceV2.CONTRACT_VERSION);
        // V2-19-09 / ADR 0040 D5: no pipeline requires a companion kind any more, per pipeline and
        // in union — the audit's "beach alone cannot export" precondition is gone, not relocated.
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        for (List<String> capabilities : registry.pipelineCapabilitySets()) {
            assertEquals(Set.of(),
                    ProductionRoutePreconditionsV2.requiredCompanionKinds(
                            registry.pipelineIdFor(capabilities)));
        }
        assertEquals(Set.of(), ProductionRoutePreconditionsV2.requiredCompanionKinds());
        // V2-19-10: the closed surface material vocabulary and its two binding stages.
        assertEquals("surface-material-profile-v1", SurfaceMaterialProfileV2.CONTRACT_VERSION);
        assertEquals("surface-material-binding-v1", SurfaceMaterializationV2.CONTRACT_VERSION);
        assertEquals("environment-surface-material-v1", EnvironmentSurfaceMaterialV2.CONTRACT_VERSION);
        // V2-19-12 / ADR 0041: the coherent detail kernel's fixed integer contract.
        assertEquals("coherent-detail-fixed-v1", CoherentDetailKernelV2.VERSION);
        // V2-19-14 / ADR 0043: the opt-in reconcile pre-pass; masks stay one-directional inputs.
        assertEquals("mask-feature-reconcile-v1", MaskFeatureReconcileV2.CONTRACT_VERSION);
    }

    @Test
    void theReopenedTrackELeafSurfaceIsExactlyTheRegistryProjection() {
        // V2-15 restart state: what an operator (and a provider) is told about reachable kinds is
        // one projection of the dispatch registry — never the support catalog. V2-15-11 wired LAKE,
        // V2-15-12 wired CANYON (both basin/corridor only; the oxbow cutoff subtype is split to
        // V2-15-48) and V2-15-13 wired WATERFALL (plunge basin only; the WATERFALL_VOLUME overlay
        // needs the sparse-volume prefix and is split to its own leaf), so this gate now pins the
        // post-V2-15-13 surface.
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        DesignSupportSurfaceV2 surface = new DesignSupportLintServiceV2(registry)
                .surface(EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED));
        assertEquals(ProductionDispatchRegistryV2.CONTRACT_VERSION, surface.dispatchRegistryVersion());
        assertEquals(registry.registryChecksum(), surface.dispatchRegistryChecksum());
        assertEquals(PublicDispatchReachabilityV2.builtIn().canonicalChecksum(),
                surface.reachabilityChecksum());
        assertEquals(List.of("BREAKWATER_HARBOR", "HARBOR_BASIN", "ROCKY_CAPE", "SANDY_BEACH"),
                surface.productionConnectedKinds());
        assertEquals(List.of("CANYON", "LAKE", "MEANDERING_RIVER", "PLAIN", "RIVER", "WATERFALL"),
                surface.offlineProductionKinds());
        assertEquals(List.of("BACKSHORE_PLAINS"), surface.contractOnlyKinds());
        assertEquals(List.of(), surface.requiredCompanionKinds());

        // The oxbow cutoff subtype's kind is honestly displayed as not yet reachable: nothing
        // pre-claims OXBOW_LAKE before its own leaf (V2-15-48) routes and materializes it.
        PublicDispatchReachabilityV2 reachability = PublicDispatchReachabilityV2.builtIn();
        assertEquals(PublicDispatchReachabilityV2.ReachabilityV2.OFFLINE_PRODUCTION,
                reachability.entry(TerrainIntentV2.FeatureKind.LAKE).reachability());
        assertEquals(PublicDispatchReachabilityV2.ReachabilityV2.OFFLINE_PRODUCTION,
                reachability.entry(TerrainIntentV2.FeatureKind.CANYON).reachability());
        assertEquals(PublicDispatchReachabilityV2.ReachabilityV2.OFFLINE_PRODUCTION,
                reachability.entry(TerrainIntentV2.FeatureKind.WATERFALL).reachability());
        assertEquals(PublicDispatchReachabilityV2.ReachabilityV2.NOT_PUBLICLY_DISPATCHABLE,
                reachability.entry(TerrainIntentV2.FeatureKind.OXBOW_LAKE).reachability());

        // ADR 0038 D4 per-leaf obligation: the wired kinds are NORMATIVE (RIVER via ADR 0039's
        // confidence-only amendment, LAKE, CANYON and WATERFALL via the same pattern in
        // V2-15-11/12/13), and the
        // registry keeps the accepted table's shape. A wiring leaf confirming a kind must move the
        // registry and this expectation in the same commit — never the test expectation alone.
        CompositionProfileRegistryV2 profiles = CompositionProfileRegistryV2.builtIn();
        Set<TerrainIntentV2.FeatureKind> normative =
                Stream.of(TerrainIntentV2.FeatureKind.values())
                        .filter(kind -> profiles.registration(kind).confidence()
                                == CompositionProfileRegistryV2.Confidence.NORMATIVE)
                        .collect(Collectors.toCollection(
                                () -> EnumSet.noneOf(TerrainIntentV2.FeatureKind.class)));
        assertEquals(EnumSet.of(
                        TerrainIntentV2.FeatureKind.SANDY_BEACH,
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                        TerrainIntentV2.FeatureKind.PLAIN,
                        TerrainIntentV2.FeatureKind.HILL_RANGE,
                        TerrainIntentV2.FeatureKind.RIVER,
                        TerrainIntentV2.FeatureKind.LAKE,
                        TerrainIntentV2.FeatureKind.CANYON,
                        TerrainIntentV2.FeatureKind.WATERFALL),
                normative);
    }

    @Test
    void thePhaseGatePromotesNoCapabilityOrDimension() throws Exception {
        // V2-19 is an offline input-integrity phase: no Paper capability, dimension, or Release 2
        // capability may have moved, and the sealed catalog stays byte-stable.
        FeatureSupportCatalogV2 catalog =
                new FeatureSupportCatalogConsistencyVerifierV2().requireConsistentBuiltIn();
        assertEquals(Set.of("SANDY_BEACH", "BREAKWATER_HARBOR", "HARBOR_BASIN", "ROCKY_CAPE"),
                catalog.entries().stream()
                        .filter(entry -> entry.support().level(FeatureSupportCapabilityV2.PAPER_APPLY)
                                == FeatureSupportLevelV2.SUPPORTED)
                        .map(FeatureSupportEntryV2::entryId)
                        .collect(Collectors.toSet()),
                "exactly the four surface-2_5d features may be paper_apply SUPPORTED");

        // The offline-routed kinds stay below Paper SUPPORTED across all five Paper columns, and
        // PLAIN's standalone usage stays PARTIAL (ADR 0040 D7: no single-feature materialization
        // evidence was taken in this phase).
        List<FeatureSupportCapabilityV2> paperColumns = List.of(
                FeatureSupportCapabilityV2.PAPER_APPLY,
                FeatureSupportCapabilityV2.POST_APPLY_VALIDATION,
                FeatureSupportCapabilityV2.SNAPSHOT,
                FeatureSupportCapabilityV2.ROLLBACK,
                FeatureSupportCapabilityV2.RESTART_RECOVERY);
        for (String riverEntry : List.of("RIVER", "MEANDERING_RIVER")) {
            FeatureSupportEntryV2 entry = catalog.require(riverEntry);
            for (FeatureSupportCapabilityV2 column : paperColumns) {
                assertEquals(FeatureSupportLevelV2.EXPERIMENTAL, entry.support().level(column),
                        () -> riverEntry + " " + column);
            }
        }
        FeatureSupportEntryV2 plain = catalog.require("PLAIN");
        for (FeatureSupportCapabilityV2 column : paperColumns) {
            assertEquals(FeatureSupportLevelV2.UNSUPPORTED, plain.support().level(column),
                    () -> "PLAIN " + column);
        }
        assertEquals(FeatureSupportLevelV2.PARTIAL,
                plain.support().level(FeatureSupportCapabilityV2.STANDALONE_USAGE));

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
}
