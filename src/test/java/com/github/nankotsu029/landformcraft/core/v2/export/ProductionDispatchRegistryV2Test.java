package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.v2.catalog.CurrentFeatureStateRegistryV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
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
            "bec55cada3e479a182b027c873b9ed50a81d283bea51527b479e739999342afc";
    private static final String EXPECTED_ENVIRONMENT_PLAN_CHECKSUM =
            "40303ba4cb75354b7d7df06d2fc87b393ce3e497d8f2d0d22477aeedfa463caf";

    @Test
    void builtInRegistrySelectsTheCompleteCoastalProductionChain() throws Exception {
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(INTENT);

        ProductionDispatchRegistryV2.DispatchPlan plan = registry.select(intent).plan();

        assertEquals("production-dispatch-registry-v1", plan.contractVersion());
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
        assertEquals("cda5fc94b4147006c859503c964d4da9152aaad744980e2759acd3e396f3756c",
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
        assertEquals("b592c5b77f9e74d1ab26dd663b7945a0321cda8a2cdbf1c4bce2e8e09730ee3d",
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
        assertEquals("b01959e3a4a032869ef95e5d447367d9e667d26503c37b77bb8d491224aa9a08",
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
        List<ProductionDispatchRegistryV2.Route> routes = ProductionDispatchRegistryV2.builtIn().routes();

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, routes.subList(1, routes.size()), List.of(pipeline)));
        assertTrue(missing.getMessage().contains("exactly cover"), missing.getMessage());

        List<ProductionDispatchRegistryV2.Route> duplicateRoutes = new ArrayList<>(routes);
        duplicateRoutes.add(routes.getFirst());
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, duplicateRoutes, List.of(pipeline)));
        assertTrue(duplicate.getMessage().contains("duplicate production dispatch kind"), duplicate.getMessage());

        ProductionDispatchRegistryV2.Route first = routes.getFirst();
        List<ProductionDispatchRegistryV2.Route> unknownRoutes = replaceFirst(routes, new ProductionDispatchRegistryV2.Route(
                first.featureKind(), first.moduleId(), "v2.unknown.pipeline",
                first.handlers(), first.requiredCapabilities()));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, unknownRoutes, List.of(pipeline)));
        assertTrue(unknown.getMessage().contains("unknown pipeline"), unknown.getMessage());

        var partialHandlers = new ProductionExportPipelineV2.HandlerSet(
                first.handlers().generatorHandlerId(),
                "v2.wrong.validator",
                first.handlers().previewHandlerId(),
                first.handlers().exportHandlerId());
        List<ProductionDispatchRegistryV2.Route> partialRoutes = replaceFirst(routes,
                new ProductionDispatchRegistryV2.Route(
                        first.featureKind(), first.moduleId(), first.pipelineId(),
                        partialHandlers, first.requiredCapabilities()));
        IllegalArgumentException partial = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, partialRoutes, List.of(pipeline)));
        assertTrue(partial.getMessage().contains("handler chain differs"), partial.getMessage());

        List<ProductionDispatchRegistryV2.Route> elevatedRoutes = replaceFirst(routes,
                new ProductionDispatchRegistryV2.Route(
                        TerrainIntentV2.FeatureKind.PLAIN,
                        BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                        first.pipelineId(), first.handlers(), first.requiredCapabilities()));
        IllegalArgumentException elevated = assertThrows(IllegalArgumentException.class,
                () -> new ProductionDispatchRegistryV2(source, elevatedRoutes, List.of(pipeline)));
        assertTrue(elevated.getMessage().contains("would elevate"), elevated.getMessage());
    }

    @Test
    void unsupportedAndContractOnlyFeatureSetsFailClosed() throws Exception {
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        TerrainIntentV2 coastal = new LandformV2DataCodec().readTerrainIntent(INTENT);

        IllegalArgumentException unsupported = assertThrows(IllegalArgumentException.class,
                () -> registry.select(replaceBackshore(coastal, TerrainIntentV2.FeatureKind.PLAIN)));
        assertTrue(unsupported.getMessage().contains("no production dispatch route: PLAIN"),
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
                ProductionDispatchRegistryV2.builtIn().routes());
        routes.sort(Comparator.comparing(
                (ProductionDispatchRegistryV2.Route route) -> route.featureKind().name()).reversed());
        ProductionDispatchRegistryV2 registry = new ProductionDispatchRegistryV2(
                source, routes, List.of(new CoastalSurfaceExportPipelineV2()));
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
            TerrainIntentV2.FeatureParameters parameters = replacementKind == TerrainIntentV2.FeatureKind.PLAIN
                    ? new TerrainIntentV2.PlainParameters(
                            new TerrainIntentV2.IntRange(4, 12),
                            new TerrainIntentV2.IntRange(1, 2),
                            new TerrainIntentV2.IntRange(1, 4))
                    : new TerrainIntentV2.NoParameters();
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
