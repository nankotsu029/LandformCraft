package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.v2.catalog.CurrentFeatureStateRegistryV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionDispatchRegistryV2Test {
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");
    // Updated when capability overlays change; recompute via builtIn().registryChecksum().
    private static final String EXPECTED_REGISTRY_CHECKSUM =
            "0d829c9dba7a58c1d252ae308735e1a068e40124cf2d1b5817218ac7d9378b0e";
    private static final String EXPECTED_ENVIRONMENT_PLAN_CHECKSUM =
            "e9064809b5864b8ed817e7a6090581cb89d96f5d9473ab7e46ccd3cefd4f4325";

    @Test
    void builtInRegistrySelectsTheCompleteCoastalProductionChain() throws Exception {
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(INTENT);

        ProductionDispatchRegistryV2.DispatchPlan plan = registry.select(intent).plan();

        assertEquals("production-dispatch-registry-v2", plan.contractVersion());
        assertEquals(CoastalSurfaceExportPipelineV2.PIPELINE_ID, plan.pipelineId());
        assertEquals(List.of(
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                        TerrainIntentV2.FeatureKind.SANDY_BEACH),
                plan.routes().stream().map(ProductionDispatchRegistryV2.Route::featureKind).toList());
        assertEquals(List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS), plan.contractOnlyKinds());
        assertEquals(List.of("surface-2_5d"), plan.requiredCapabilities());
        assertTrue(plan.routes().stream().allMatch(route -> route.handlers().equals(
                new ProductionExportPipelineV2.HandlerSet(
                        CoastalSurfaceExportPipelineV2.GENERATOR_HANDLER_ID,
                        CoastalSurfaceExportPipelineV2.VALIDATOR_HANDLER_ID,
                        CoastalSurfaceExportPipelineV2.PREVIEW_HANDLER_ID,
                        CoastalSurfaceExportPipelineV2.EXPORT_HANDLER_ID))));
        assertEquals(EXPECTED_REGISTRY_CHECKSUM, registry.registryChecksum());
        assertEquals("7504eb98bd041b2bb9a46be3e5221fa590fff4a24595b9893ac752fcc91a5bd6",
                plan.planChecksum());
    }

    @Test
    void builtInRegistrySelectsHydrologySharedCapabilityOverlay() throws Exception {
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(INTENT);

        ProductionDispatchRegistryV2.DispatchPlan plan = registry.select(
                intent, ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE).plan();

        assertEquals(HydrologyPlanExportPipelineV2.PIPELINE_ID, plan.pipelineId());
        assertEquals(ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE, plan.requiredCapabilities());
        assertEquals(List.of(
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                        TerrainIntentV2.FeatureKind.SANDY_BEACH),
                plan.routes().stream().map(ProductionDispatchRegistryV2.Route::featureKind).toList());
        assertEquals(List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS), plan.contractOnlyKinds());
        assertTrue(plan.routes().stream().allMatch(route -> route.handlers().equals(
                new ProductionExportPipelineV2.HandlerSet(
                        HydrologyPlanExportPipelineV2.GENERATOR_HANDLER_ID,
                        HydrologyPlanExportPipelineV2.VALIDATOR_HANDLER_ID,
                        HydrologyPlanExportPipelineV2.PREVIEW_HANDLER_ID,
                        HydrologyPlanExportPipelineV2.EXPORT_HANDLER_ID))));
        assertEquals(EXPECTED_REGISTRY_CHECKSUM, registry.registryChecksum());
        assertEquals("ce05b9aba6e87e938b37e30bc9e5ee9f2776f330cb891a15fde46826a8d48257",
                plan.planChecksum());
    }

    @Test
    void builtInRegistrySelectsEnvironmentSharedCapabilityOverlay() throws Exception {
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(INTENT);

        ProductionDispatchRegistryV2.DispatchPlan plan = registry.select(
                intent, ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE).plan();

        assertEquals(EnvironmentFieldsExportPipelineV2.PIPELINE_ID, plan.pipelineId());
        assertEquals(ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE,
                plan.requiredCapabilities());
        assertEquals(List.of(
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                        TerrainIntentV2.FeatureKind.SANDY_BEACH),
                plan.routes().stream().map(ProductionDispatchRegistryV2.Route::featureKind).toList());
        assertEquals(List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS), plan.contractOnlyKinds());
        assertTrue(plan.routes().stream().allMatch(route -> route.handlers().equals(
                new ProductionExportPipelineV2.HandlerSet(
                        EnvironmentFieldsExportPipelineV2.GENERATOR_HANDLER_ID,
                        EnvironmentFieldsExportPipelineV2.VALIDATOR_HANDLER_ID,
                        EnvironmentFieldsExportPipelineV2.PREVIEW_HANDLER_ID,
                        EnvironmentFieldsExportPipelineV2.EXPORT_HANDLER_ID))));
        assertEquals(EXPECTED_REGISTRY_CHECKSUM, registry.registryChecksum());
        assertEquals(EXPECTED_ENVIRONMENT_PLAN_CHECKSUM, plan.planChecksum());
    }

    @Test
    void builtInRegistrySelectsSparseVolumeSharedCapabilityOverlay() throws Exception {
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(INTENT);

        ProductionDispatchRegistryV2.DispatchPlan plan = registry.select(
                intent, ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT).plan();

        assertEquals(SparseVolumeExportPipelineV2.PIPELINE_ID, plan.pipelineId());
        assertEquals(ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT,
                plan.requiredCapabilities());
        assertEquals(List.of(
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                        TerrainIntentV2.FeatureKind.SANDY_BEACH),
                plan.routes().stream().map(ProductionDispatchRegistryV2.Route::featureKind).toList());
        assertEquals(List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS), plan.contractOnlyKinds());
        assertTrue(plan.routes().stream().allMatch(route -> route.handlers().equals(
                new ProductionExportPipelineV2.HandlerSet(
                        SparseVolumeExportPipelineV2.GENERATOR_HANDLER_ID,
                        SparseVolumeExportPipelineV2.VALIDATOR_HANDLER_ID,
                        SparseVolumeExportPipelineV2.PREVIEW_HANDLER_ID,
                        SparseVolumeExportPipelineV2.EXPORT_HANDLER_ID))));
        assertEquals(EXPECTED_REGISTRY_CHECKSUM, registry.registryChecksum());
        assertEquals("24113e63fc647e682d0f35b2d12558e0c021dbaa2dffea04a2592952cd176880",
                plan.planChecksum());
    }

    @Test
    void rejectsUnknownCapabilitySet() throws Exception {
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(INTENT);
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> registry.select(intent, List.of("future-capability")));
        assertTrue(missing.getMessage().contains("no production pipeline for capability set"),
                missing.getMessage());
    }

    @Test
    void rejectsMissingDuplicateUnknownPartialAndElevatingRoutes() {
        List<CurrentFeatureStateRegistryV2.Entry> source = currentSource();
        ProductionExportPipelineV2 pipeline = new CoastalSurfaceExportPipelineV2();
        // Only PRODUCTION_CONNECTED (coastal) routes for these exact-cover / elevation checks; the
        // built-in OFFLINE_PRODUCTION river routes reference the hydrology pipeline, which is not in
        // scope for this pipeline-only fixture.
        List<ProductionDispatchRegistryV2.Route> routes = ProductionDispatchRegistryV2.builtIn().routes().stream()
                .filter(route -> route.routeClass() == ProductionDispatchRegistryV2.RouteClass.PRODUCTION_CONNECTED)
                .toList();
        Set<TerrainIntentV2.FeatureKind> exportSupported = exportSupportedKinds();

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(
                        source, routes.subList(1, routes.size()), List.of(pipeline), exportSupported));
        assertTrue(missing.getMessage().contains("exactly cover"), missing.getMessage());

        List<ProductionDispatchRegistryV2.Route> duplicateRoutes = new ArrayList<>(routes);
        duplicateRoutes.add(routes.getFirst());
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, duplicateRoutes, List.of(pipeline), exportSupported));
        assertTrue(duplicate.getMessage().contains("duplicate production dispatch kind"), duplicate.getMessage());

        ProductionDispatchRegistryV2.Route first = routes.getFirst();
        List<ProductionDispatchRegistryV2.Route> unknownRoutes = replaceFirst(routes, new ProductionDispatchRegistryV2.Route(
                first.featureKind(), first.moduleId(), "v2.unknown.pipeline",
                first.handlers(), first.requiredCapabilities(), first.routeClass()));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, unknownRoutes, List.of(pipeline), exportSupported));
        assertTrue(unknown.getMessage().contains("unknown pipeline"), unknown.getMessage());

        var partialHandlers = new ProductionExportPipelineV2.HandlerSet(
                first.handlers().generatorHandlerId(),
                "v2.wrong.validator",
                first.handlers().previewHandlerId(),
                first.handlers().exportHandlerId());
        List<ProductionDispatchRegistryV2.Route> partialRoutes = replaceFirst(routes,
                new ProductionDispatchRegistryV2.Route(
                        first.featureKind(), first.moduleId(), first.pipelineId(),
                        partialHandlers, first.requiredCapabilities(), first.routeClass()));
        IllegalArgumentException partial = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, partialRoutes, List.of(pipeline), exportSupported));
        assertTrue(partial.getMessage().contains("handler chain differs"), partial.getMessage());

        List<ProductionDispatchRegistryV2.Route> elevatedRoutes = replaceFirst(routes,
                new ProductionDispatchRegistryV2.Route(
                        TerrainIntentV2.FeatureKind.PLAIN,
                        BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                        first.pipelineId(), first.handlers(), first.requiredCapabilities(),
                        ProductionDispatchRegistryV2.RouteClass.PRODUCTION_CONNECTED));
        IllegalArgumentException elevated = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, elevatedRoutes, List.of(pipeline), exportSupported));
        assertTrue(elevated.getMessage().contains("would elevate"), elevated.getMessage());
    }

    @Test
    void offlineProductionRouteRequiresDedicatedModuleExportSupportAndNonProductionConnectedKind() {
        List<CurrentFeatureStateRegistryV2.Entry> source = currentSource();
        ProductionExportPipelineV2 hydrology = new HydrologyPlanExportPipelineV2();
        Set<TerrainIntentV2.FeatureKind> exportSupported = exportSupportedKinds();
        ProductionDispatchRegistryV2.Route riverRoute = ProductionDispatchRegistryV2.builtIn().routes().stream()
                .filter(route -> route.featureKind() == TerrainIntentV2.FeatureKind.RIVER)
                .findFirst().orElseThrow();

        // A PRODUCTION_CONNECTED-labeled route for a kind that is not PRODUCTION_CONNECTED fails the
        // exact-cover check for the coastal-four set (empty here, since no PRODUCTION_CONNECTED routes
        // are supplied), not the offline allowlist path.
        List<ProductionDispatchRegistryV2.Route> mislabeled = List.of(new ProductionDispatchRegistryV2.Route(
                riverRoute.featureKind(), riverRoute.moduleId(), riverRoute.pipelineId(),
                riverRoute.handlers(), riverRoute.requiredCapabilities(),
                ProductionDispatchRegistryV2.RouteClass.PRODUCTION_CONNECTED));
        IllegalArgumentException elevated = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, mislabeled, List.of(hydrology), exportSupported));
        assertTrue(elevated.getMessage().contains("would elevate"), elevated.getMessage());

        // Offline route for a kind whose module binding is diagnostic (not dedicated) is rejected.
        // VALLEY has no dedicated module binding, so its current moduleId is the diagnostic module id.
        List<ProductionDispatchRegistryV2.Route> nonDedicated = List.of(new ProductionDispatchRegistryV2.Route(
                TerrainIntentV2.FeatureKind.VALLEY, BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                riverRoute.pipelineId(), riverRoute.handlers(), riverRoute.requiredCapabilities(),
                ProductionDispatchRegistryV2.RouteClass.OFFLINE_PRODUCTION));
        IllegalArgumentException nonDedicatedError = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, nonDedicated, List.of(hydrology), exportSupported));
        assertTrue(nonDedicatedError.getMessage().contains("dedicated module binding"),
                nonDedicatedError.getMessage());

        // Offline route for a kind that is not export-SUPPORTED is rejected even with a dedicated
        // module and a pipeline that (hypothetically) executes it.
        IllegalArgumentException notExportSupported = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, List.of(riverRoute), List.of(hydrology), Set.of()));
        assertTrue(notExportSupported.getMessage().contains("export-supported kind"),
                notExportSupported.getMessage());
    }

    @Test
    void builtInRegistrySelectsRiverMeanderingRiverAndLakeAsOfflineProductionOnHydrologyOverlay() throws Exception {
        // V2-15-11 adds LAKE, V2-15-12 adds CANYON and V2-15-13 adds WATERFALL to the same explicit
        // OFFLINE_PRODUCTION allowlist ADR 0039 Candidate A opened for RIVER / MEANDERING_RIVER, on
        // the same shared hydrology-plan pipeline.
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        for (TerrainIntentV2.FeatureKind offlineKind : List.of(
                TerrainIntentV2.FeatureKind.RIVER,
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                TerrainIntentV2.FeatureKind.LAKE,
                TerrainIntentV2.FeatureKind.CANYON,
                TerrainIntentV2.FeatureKind.WATERFALL)) {
            ProductionDispatchRegistryV2.Route route = registry.routes().stream()
                    .filter(candidate -> candidate.featureKind() == offlineKind)
                    .findFirst().orElseThrow(() -> new AssertionError("missing route for " + offlineKind));
            assertEquals(ProductionDispatchRegistryV2.RouteClass.OFFLINE_PRODUCTION, route.routeClass(),
                    offlineKind.name());
            assertEquals(HydrologyPlanExportPipelineV2.PIPELINE_ID, route.pipelineId(), offlineKind.name());
            assertEquals(ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE, route.requiredCapabilities(),
                    offlineKind.name());
        }
    }

    @Test
    void coastalFourStillExactlyCoverProductionConnectedRoutes() {
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        Set<TerrainIntentV2.FeatureKind> productionConnected = registry.routes().stream()
                .filter(route -> route.routeClass() == ProductionDispatchRegistryV2.RouteClass.PRODUCTION_CONNECTED)
                .map(ProductionDispatchRegistryV2.Route::featureKind)
                .collect(java.util.stream.Collectors.toCollection(
                        () -> java.util.EnumSet.noneOf(TerrainIntentV2.FeatureKind.class)));
        assertEquals(java.util.EnumSet.of(
                TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                TerrainIntentV2.FeatureKind.SANDY_BEACH), productionConnected);

        Set<TerrainIntentV2.FeatureKind> offline = registry.routes().stream()
                .filter(route -> route.routeClass() == ProductionDispatchRegistryV2.RouteClass.OFFLINE_PRODUCTION)
                .map(ProductionDispatchRegistryV2.Route::featureKind)
                .collect(java.util.stream.Collectors.toCollection(
                        () -> java.util.EnumSet.noneOf(TerrainIntentV2.FeatureKind.class)));
        // V2-19-07 added the PLAIN macro foundation producer to the same offline class, on the
        // coastal surface pipeline rather than the hydrology one; V2-15-11 added LAKE, V2-15-12
        // added CANYON and V2-15-13 added WATERFALL on the hydrology pipeline.
        assertEquals(java.util.EnumSet.of(
                TerrainIntentV2.FeatureKind.RIVER,
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                TerrainIntentV2.FeatureKind.PLAIN,
                TerrainIntentV2.FeatureKind.LAKE,
                TerrainIntentV2.FeatureKind.CANYON,
                TerrainIntentV2.FeatureKind.WATERFALL), offline);
        assertEquals(CoastalSurfaceExportPipelineV2.PIPELINE_ID, registry.routes().stream()
                .filter(route -> route.featureKind() == TerrainIntentV2.FeatureKind.PLAIN)
                .map(ProductionDispatchRegistryV2.Route::pipelineId)
                .findFirst().orElseThrow());
    }

    private static Set<TerrainIntentV2.FeatureKind> exportSupportedKinds() {
        FeatureSupportCatalogV2 catalog = new FeatureSupportCatalogCodecV2().builtInSealed();
        return ProductionDispatchRegistryV2.exportSupportedKinds(catalog);
    }

    @Test
    void unsupportedAndContractOnlyFeatureSetsFailClosed() throws Exception {
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        TerrainIntentV2 coastal = new LandformV2DataCodec().readTerrainIntent(INTENT);

        // PLATEAU is a foundation-eligible kind whose own V2-15 wiring leaf has not run, so it must
        // still fail dispatch rather than be silently dropped. PLAIN stopped being an example of this
        // when V2-19-07 wired it as the first macro foundation producer.
        IllegalArgumentException unsupported = assertThrows(IllegalArgumentException.class,
                () -> registry.select(replaceBackshore(coastal, TerrainIntentV2.FeatureKind.PLATEAU)));
        assertTrue(unsupported.getMessage().contains("no production dispatch route: PLATEAU"),
                unsupported.getMessage());

        TerrainIntentV2.Feature backshore = coastal.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS)
                .findFirst().orElseThrow();
        TerrainIntentV2 contractOnly = new TerrainIntentV2(
                coastal.intentVersion(), coastal.intentId(), coastal.theme(), coastal.coordinateSystem(),
                List.of(backshore), List.of(), List.of(), coastal.environment(), coastal.mapReferences(),
                List.of(), coastal.provenance());
        IllegalArgumentException noExecutor = assertThrows(IllegalArgumentException.class,
                () -> registry.select(contractOnly));
        assertTrue(noExecutor.getMessage().contains("contract-only features cannot select"), noExecutor.getMessage());
    }

    @Test
    void registryAndPlanAreStableAcrossOrderThreadsLocaleAndTimezone() throws Exception {
        List<CurrentFeatureStateRegistryV2.Entry> source = new ArrayList<>(currentSource());
        source.sort(Comparator.comparing(
                (CurrentFeatureStateRegistryV2.Entry entry) -> entry.featureKind().name()).reversed());
        List<ProductionDispatchRegistryV2.Route> routes = new ArrayList<>(
                ProductionDispatchRegistryV2.builtIn().routes().stream()
                        .filter(route -> route.routeClass()
                                == ProductionDispatchRegistryV2.RouteClass.PRODUCTION_CONNECTED)
                        .toList());
        routes.sort(Comparator.comparing(
                (ProductionDispatchRegistryV2.Route route) -> route.featureKind().name()).reversed());
        ProductionDispatchRegistryV2 registry = new ProductionDispatchRegistryV2(
                source, routes, List.of(new CoastalSurfaceExportPipelineV2()), exportSupportedKinds());
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(INTENT);
        String expected = registry.registryChecksum() + ":" + registry.select(intent).plan().planChecksum();

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            try (var executor = Executors.newFixedThreadPool(4)) {
                List<Callable<String>> tasks = List.of(
                        () -> checksum(registry, intent),
                        () -> checksum(registry, intent),
                        () -> checksum(registry, intent),
                        () -> checksum(registry, intent));
                for (var future : executor.invokeAll(tasks)) {
                    assertEquals(expected, future.get());
                }
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private static String checksum(ProductionDispatchRegistryV2 registry, TerrainIntentV2 intent) {
        return registry.registryChecksum() + ":" + registry.select(intent).plan().planChecksum();
    }

    private static List<ProductionDispatchRegistryV2.Route> replaceFirst(
            List<ProductionDispatchRegistryV2.Route> routes,
            ProductionDispatchRegistryV2.Route replacement
    ) {
        List<ProductionDispatchRegistryV2.Route> result = new ArrayList<>(routes);
        result.set(0, replacement);
        return result;
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
                case PLAIN -> new TerrainIntentV2.PlainParameters(
                        new TerrainIntentV2.IntRange(4, 12),
                        new TerrainIntentV2.IntRange(1, 2),
                        new TerrainIntentV2.IntRange(1, 4));
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
                features, intent.relations(), intent.constraints(), intent.environment(), intent.mapReferences(),
                intent.structures(), intent.provenance());
    }

    private static List<CurrentFeatureStateRegistryV2.Entry> currentSource() {
        BuiltInLandformModuleCatalogV2 modules = new BuiltInLandformModuleCatalogV2();
        Set<String> schemaKinds = new HashSet<>();
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            schemaKinds.add(kind.name());
        }
        CurrentFeatureStateRegistryV2 source = CurrentFeatureStateRegistryV2.project(
                schemaKinds,
                new FeatureSupportCatalogCodecV2().builtInSealed(),
                modules.featureBindings(),
                modules.modules());
        source.requireConsistent();
        return source.entries();
    }
}
