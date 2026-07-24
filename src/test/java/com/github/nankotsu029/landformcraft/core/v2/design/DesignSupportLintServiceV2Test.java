package com.github.nankotsu029.landformcraft.core.v2.design;

import com.github.nankotsu029.landformcraft.core.v2.catalog.PublicDispatchReachabilityV2;
import com.github.nankotsu029.landformcraft.core.v2.export.ProductionDispatchRegistryV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportEntryV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportLevelV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignSupportLintV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignSupportSurfaceV2;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-08: the design-time support lint reports what the production dispatch registry would do with
 * a designed intent, and never rejects one.
 */
class DesignSupportLintServiceV2Test {
    private static final Path COASTAL =
            Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");
    private static final Path RIVER =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored-river.terrain-intent-v2.json");

    private final DesignSupportLintServiceV2 service = new DesignSupportLintServiceV2();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void theReachableSetIsProjectedFromTheDispatchRegistryNotTheSupportCatalog() {
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        DesignSupportSurfaceV2 surface = surface();

        assertEquals(DesignSupportSurfaceV2.CONTRACT_VERSION, surface.contractVersion());
        assertEquals(ProductionDispatchRegistryV2.CONTRACT_VERSION, surface.dispatchRegistryVersion());
        assertEquals(registry.registryChecksum(), surface.dispatchRegistryChecksum());
        assertEquals(PublicDispatchReachabilityV2.builtIn().canonicalChecksum(),
                surface.reachabilityChecksum());
        assertEquals(
                registry.routes().stream()
                        .map(route -> route.featureKind().name())
                        .sorted()
                        .toList(),
                surface.reachableKinds());
        assertEquals(List.of("BREAKWATER_HARBOR", "HARBOR_BASIN", "ROCKY_CAPE", "SANDY_BEACH"),
                surface.productionConnectedKinds());
        assertEquals(List.of("CANYON", "LAKE", "MEANDERING_RIVER", "PLAIN", "RIVER", "WATERFALL"),
                surface.offlineProductionKinds());
        assertEquals(List.of("BACKSHORE_PLAINS"), surface.contractOnlyKinds());
        // V2-19-09 (ADR 0040 D5): the only runtime companion requirement this table ever carried —
        // the coastal four — is gone, because the surface runtime no longer imposes it. The column
        // stays in the contract for a future pipeline that does; today it is honestly empty.
        assertEquals(List.of(), surface.requiredCompanionKinds());
        assertEquals(List.of("TERRAIN_INTENT_V2_STRUCTURED"), surface.designCapabilities());

        // The audit's §2.2 finding: export == SUPPORTED is a different question. Kinds that are
        // export-supported without a route must not appear as reachable.
        Set<String> routed = new TreeSet<>();
        registry.routes().forEach(route -> routed.add(route.featureKind().name()));
        Set<String> unroutedButExportSupported = new TreeSet<>();
        new FeatureSupportCatalogCodecV2().builtInSealed().entries().stream()
                .filter(entry -> entry.hasFeatureKind()
                        && entry.support().export() == FeatureSupportLevelV2.SUPPORTED)
                .map(FeatureSupportEntryV2::featureKindName)
                .filter(name -> !routed.contains(name))
                .forEach(unroutedButExportSupported::add);
        assertFalse(unroutedButExportSupported.isEmpty(),
                "the fixture for this assertion requires at least one export-supported unrouted kind");
        for (String kind : unroutedButExportSupported) {
            assertFalse(surface.reachableKinds().contains(kind),
                    "export-supported but unrouted kind must not be advertised as reachable: " + kind);
        }
    }

    @Test
    void theCoastalFixtureIsSelectableAndOnlyReportsItsContractOnlyCompanion() throws IOException {
        DesignSupportLintV2 lint = service.lint(surface(), codec.readTerrainIntent(COASTAL));

        assertEquals(DesignSupportLintV2.DispatchDryRunV2.SELECTABLE, lint.dispatchDryRun());
        assertEquals(List.of(
                        "v2.production.environment-fields.shared",
                        "v2.production.hydrology-plan.shared",
                        "v2.production.sparse-volume.shared",
                        "v2.production.surface-2_5d.coastal"),
                lint.selectablePipelineIds());
        assertEquals(List.of("BACKSHORE_PLAINS", "BREAKWATER_HARBOR", "HARBOR_BASIN", "ROCKY_CAPE",
                        "SANDY_BEACH"),
                lint.declaredKinds());
        assertEquals(List.of("BACKSHORE_PLAINS"),
                lint.kindsWith(DesignSupportLintV2.RULE_KIND_CONTRACT_ONLY));
        assertEquals(List.of(), lint.kindsWith(DesignSupportLintV2.RULE_KIND_NOT_DISPATCHABLE));
        assertEquals(List.of(), lint.kindsWith(DesignSupportLintV2.RULE_COMPANION_MISSING));
        assertTrue(lint.allFindingsAreNonGating());
    }

    @Test
    void theRiverFixtureSelectsTheHydrologyPipelineOnly() throws IOException {
        DesignSupportLintV2 lint = service.lint(surface(), codec.readTerrainIntent(RIVER));

        assertEquals(DesignSupportLintV2.DispatchDryRunV2.SELECTABLE, lint.dispatchDryRun());
        assertEquals(List.of("v2.production.hydrology-plan.shared"), lint.selectablePipelineIds());
        assertTrue(lint.declaredKinds().contains("RIVER"));
        // V2-19-05 materialized both river kinds, so the plan-only rule is currently vacuous. It is
        // asserted empty rather than removed: a future OFFLINE_PRODUCTION route that changes no block
        // must show up here instead of passing silently.
        assertEquals(List.of(), lint.kindsWith(DesignSupportLintV2.RULE_KIND_PLAN_ONLY));
        assertTrue(lint.allFindingsAreNonGating());
    }

    @Test
    void aRoutedCoastalSubsetIsSelectableSinceTheRuntimeRequirementWasRemoved() throws IOException {
        // V2-19-09 (ADR 0040): V2-19-08 reported a beach-only intent as NOT_SELECTABLE plus three
        // missing companions, because the surface runtime demanded all four coastal contributors.
        // ADR 0040 removed that demand, so the lint now reports the truth: the design exports.
        TerrainIntentV2 beachOnly = onlyKinds(
                codec.readTerrainIntent(COASTAL), EnumSet.of(TerrainIntentV2.FeatureKind.SANDY_BEACH));

        DesignSupportLintV2 lint = service.lint(surface(), beachOnly);

        assertEquals(List.of("SANDY_BEACH"), lint.declaredKinds());
        assertEquals(DesignSupportLintV2.DispatchDryRunV2.SELECTABLE, lint.dispatchDryRun());
        assertEquals(List.of(
                        "v2.production.environment-fields.shared",
                        "v2.production.hydrology-plan.shared",
                        "v2.production.sparse-volume.shared",
                        "v2.production.surface-2_5d.coastal"),
                lint.selectablePipelineIds());
        assertEquals(List.of(), lint.kindsWith(DesignSupportLintV2.RULE_COMPANION_MISSING));
        assertEquals(List.of(), lint.kindsWith(DesignSupportLintV2.RULE_KIND_NOT_DISPATCHABLE));
        assertTrue(lint.findings().stream()
                .noneMatch(finding -> finding.ruleId().equals(
                        DesignSupportLintV2.RULE_DISPATCH_UNSELECTABLE)));
        assertTrue(lint.allFindingsAreNonGating());
    }

    @Test
    void anUnroutedKindIsReportedWithoutFailingTheLint() throws IOException {
        TerrainIntentV2 withPlateau = replaceBackshore(
                codec.readTerrainIntent(COASTAL), TerrainIntentV2.FeatureKind.PLATEAU);

        DesignSupportLintV2 lint = service.lint(surface(), withPlateau);

        assertEquals(List.of("PLATEAU"), lint.kindsWith(DesignSupportLintV2.RULE_KIND_NOT_DISPATCHABLE));
        assertEquals(DesignSupportLintV2.DispatchDryRunV2.NOT_SELECTABLE, lint.dispatchDryRun());
        assertTrue(lint.allFindingsAreNonGating(),
                "fail-closed lint requires separate human approval (Task Index §19.2)");
    }

    @Test
    void everyRegisteredRuleIsAdvisoryInThisContractVersion() throws IOException {
        List<String> registered = List.of(
                DesignSupportLintV2.RULE_KIND_NOT_DISPATCHABLE,
                DesignSupportLintV2.RULE_KIND_CONTRACT_ONLY,
                DesignSupportLintV2.RULE_KIND_PLAN_ONLY,
                DesignSupportLintV2.RULE_DISPATCH_UNSELECTABLE,
                DesignSupportLintV2.RULE_COMPANION_MISSING);
        List<DesignSupportLintV2> reports = List.of(
                service.lint(surface(), codec.readTerrainIntent(COASTAL)),
                service.lint(surface(), codec.readTerrainIntent(RIVER)),
                service.lint(surface(), replaceBackshore(
                        codec.readTerrainIntent(COASTAL), TerrainIntentV2.FeatureKind.PLATEAU)),
                service.lint(surface(), onlyKinds(codec.readTerrainIntent(COASTAL),
                        EnumSet.of(TerrainIntentV2.FeatureKind.SANDY_BEACH))));

        for (DesignSupportLintV2 report : reports) {
            for (DesignSupportLintV2.FindingV2 finding : report.findings()) {
                assertTrue(registered.contains(finding.ruleId()),
                        "unregistered lint rule id: " + finding.ruleId());
                assertEquals(DesignSupportLintV2.GateClassV2.NON_GATING, finding.gateClass());
            }
        }
    }

    @Test
    void lintOutputIsStableAcrossLocaleTimezoneAndThreads() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        String expected = canonical(service.lint(surface(), intent));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            try (var executor = Executors.newFixedThreadPool(4)) {
                List<Callable<String>> tasks = List.of(
                        () -> canonical(service.lint(surface(), intent)),
                        () -> canonical(service.lint(surface(), intent)),
                        () -> canonical(service.lint(surface(), intent)),
                        () -> canonical(service.lint(surface(), intent)));
                for (var future : executor.invokeAll(tasks)) {
                    assertEquals(expected, future.get());
                }
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private DesignSupportSurfaceV2 surface() {
        return service.surface(EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED));
    }

    private static String canonical(DesignSupportLintV2 lint) {
        List<String> lines = new ArrayList<>();
        lines.add(lint.surface().contractVersion() + "|" + lint.surface().reachabilityChecksum());
        lines.add(lint.dispatchDryRun().name());
        lines.add(String.join(",", lint.selectablePipelineIds()));
        lines.add(String.join(",", lint.declaredKinds()));
        lint.findings().forEach(finding -> lines.add(finding.ruleId() + "|" + finding.gateClass()
                + "|" + String.join(",", finding.featureKinds())));
        return String.join("\n", lines);
    }

    private static TerrainIntentV2 onlyKinds(
            TerrainIntentV2 intent,
            Set<TerrainIntentV2.FeatureKind> kinds
    ) {
        List<TerrainIntentV2.Feature> features = intent.features().stream()
                .filter(feature -> kinds.contains(feature.kind()))
                .toList();
        return new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                features, List.of(), List.of(), intent.environment(), intent.mapReferences(),
                List.of(), intent.provenance());
    }

    private static TerrainIntentV2 replaceBackshore(
            TerrainIntentV2 intent,
            TerrainIntentV2.FeatureKind replacementKind
    ) {
        List<TerrainIntentV2.Feature> features = intent.features().stream().map(feature -> {
            if (feature.kind() != TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS) {
                return feature;
            }
            TerrainIntentV2.FeatureParameters parameters = switch (replacementKind) {
                case PLATEAU -> new TerrainIntentV2.PlateauParameters(
                        new TerrainIntentV2.IntRange(12, 20),
                        new TerrainIntentV2.IntRange(1, 3),
                        TerrainIntentV2.PlateauProfile.MESA,
                        4);
                default -> new TerrainIntentV2.NoParameters();
            };
            return new TerrainIntentV2.Feature(
                    feature.id(), replacementKind, feature.geometry(), parameters,
                    feature.priority(), feature.provenance());
        }).toList();
        return new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                features, intent.relations(), intent.constraints(), intent.environment(),
                intent.mapReferences(), intent.structures(), intent.provenance());
    }
}
