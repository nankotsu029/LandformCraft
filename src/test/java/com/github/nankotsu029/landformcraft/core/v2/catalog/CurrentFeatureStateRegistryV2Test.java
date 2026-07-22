package com.github.nankotsu029.landformcraft.core.v2.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportEntryV2;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentFeatureStateRegistryV2Test {
    private static final Path SCHEMA = Path.of("schemas/terrain-intent-v2.schema.json");
    private static final Path DOCUMENT = Path.of("docs/design-v2/current-feature-state-machine-registry.md");
    private static final String START = "<!-- current-feature-state-registry-v1:start -->";
    private static final String END = "<!-- current-feature-state-registry-v1:end -->";

    private final ObjectMapper mapper = new ObjectMapper();
    private final FeatureSupportCatalogCodecV2 codec = new FeatureSupportCatalogCodecV2();

    @Test
    void currentSourcesProjectToOneFailClosedRegistry() throws Exception {
        CurrentFeatureStateRegistryV2 registry = current();

        registry.requireConsistent();
        assertEquals(60, registry.entries().size());
        assertEquals(16, registry.entries().stream().filter(entry ->
                entry.moduleBinding() == CurrentFeatureStateRegistryV2.ModuleBinding.DEDICATED).count());
        assertEquals(44, registry.entries().stream().filter(entry ->
                entry.moduleBinding() == CurrentFeatureStateRegistryV2.ModuleBinding.DIAGNOSTIC).count());
        assertEquals(Set.of("SANDY_BEACH", "BREAKWATER_HARBOR", "HARBOR_BASIN", "ROCKY_CAPE"),
                names(registry, CurrentFeatureStateRegistryV2.CurrentState.PRODUCTION_CONNECTED));
        assertEquals(Set.of("BACKSHORE_PLAINS", "BEDROCK_RIVER", "FLOODED_CAVE", "GLACIAL_CIRQUE_FIELD"),
                names(registry, CurrentFeatureStateRegistryV2.CurrentState.ENUM_SCHEMA_ONLY));
        assertEquals(Set.of("LAGOON", "REEF_PASS", "VOLCANIC_CALDERA", "LAVA_FLOW_FIELD"),
                names(registry, CurrentFeatureStateRegistryV2.CurrentState.CHILD_PLAN_ONLY));
        assertEquals(48, names(registry,
                CurrentFeatureStateRegistryV2.CurrentState.OFFLINE_OR_PLAN_LEVEL).size());
    }

    @Test
    void detectsSchemaCatalogAndModuleBindingDrift() throws Exception {
        BuiltInLandformModuleCatalogV2 moduleCatalog = new BuiltInLandformModuleCatalogV2();
        FeatureSupportCatalogV2 catalog = codec.builtInSealed();

        Set<String> schema = new HashSet<>(schemaFeatureKinds());
        schema.remove("SANDY_BEACH");
        schema.add("FUTURE_FEATURE");
        CurrentFeatureStateRegistryV2 schemaDrift = project(schema, catalog,
                moduleCatalog.featureBindings(), moduleCatalog.modules());
        assertTrue(schemaDrift.differences().contains("schema missing FeatureKind: SANDY_BEACH"));
        assertTrue(schemaDrift.differences().contains("schema declares unknown FeatureKind: FUTURE_FEATURE"));

        List<FeatureSupportEntryV2> withoutBeach = new ArrayList<>(catalog.entries());
        withoutBeach.removeIf(entry -> entry.entryId().equals("SANDY_BEACH"));
        FeatureSupportCatalogV2 catalogDrift = new FeatureSupportCatalogV2(
                FeatureSupportCatalogV2.VERSION,
                FeatureSupportCatalogV2.CONTRACT_VERSION,
                catalog.placementDimensionLimit(),
                withoutBeach,
                catalog.availablePresets(),
                catalog.unsupportedDiagnostics(),
                catalog.deferredDiagnostics(),
                catalog.canonicalChecksum());
        CurrentFeatureStateRegistryV2 missingCatalog = project(schemaFeatureKinds(), catalogDrift,
                moduleCatalog.featureBindings(), moduleCatalog.modules());
        assertTrue(missingCatalog.differences().contains("catalog missing FeatureKind: SANDY_BEACH"));

        Map<TerrainIntentV2.FeatureKind, String> brokenBinding =
                new EnumMap<>(TerrainIntentV2.FeatureKind.class);
        brokenBinding.putAll(moduleCatalog.featureBindings());
        brokenBinding.put(TerrainIntentV2.FeatureKind.SANDY_BEACH, "v2.missing.module");
        CurrentFeatureStateRegistryV2 missingModule = project(schemaFeatureKinds(), catalog,
                brokenBinding, moduleCatalog.modules());
        assertTrue(missingModule.differences().contains(
                "module binding references unknown module: SANDY_BEACH -> v2.missing.module"));
    }

    @Test
    void documentationProjectionIsKeptInSyncByCi() throws Exception {
        CurrentFeatureStateRegistryV2 registry = current();
        String text = Files.readString(DOCUMENT, StandardCharsets.UTF_8);
        int start = text.indexOf(START);
        int end = text.indexOf(END);
        assertTrue(start >= 0 && end > start, "current-state projection markers must exist");
        String documented = text.substring(start + START.length(), end).strip();
        assertEquals(registry.documentationProjection(), documented);
    }

    @Test
    void projectionIsStableAcrossOrderLocaleTimezoneAndThreads() throws Exception {
        BuiltInLandformModuleCatalogV2 moduleCatalog = new BuiltInLandformModuleCatalogV2();
        FeatureSupportCatalogV2 catalog = codec.builtInSealed();
        List<ModuleDescriptorV2> reversedModules = new ArrayList<>(moduleCatalog.modules());
        reversedModules.sort(Comparator.comparing(ModuleDescriptorV2::moduleId).reversed());
        Map<TerrainIntentV2.FeatureKind, String> shuffledBindings = new HashMap<>(moduleCatalog.featureBindings());
        String baseline = project(schemaFeatureKinds(), catalog, shuffledBindings, reversedModules)
                .documentationProjection();
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            try (var executor = Executors.newFixedThreadPool(4)) {
                List<Callable<String>> tasks = List.of(
                        () -> project(new HashSet<>(schemaFeatureKinds()), catalog,
                                new HashMap<>(shuffledBindings), reversedModules).documentationProjection(),
                        () -> project(new HashSet<>(schemaFeatureKinds()), catalog,
                                new HashMap<>(shuffledBindings), reversedModules).documentationProjection(),
                        () -> project(new HashSet<>(schemaFeatureKinds()), catalog,
                                new HashMap<>(shuffledBindings), reversedModules).documentationProjection(),
                        () -> project(new HashSet<>(schemaFeatureKinds()), catalog,
                                new HashMap<>(shuffledBindings), reversedModules).documentationProjection());
                for (var future : executor.invokeAll(tasks)) {
                    assertEquals(baseline, future.get());
                }
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private CurrentFeatureStateRegistryV2 current() throws Exception {
        BuiltInLandformModuleCatalogV2 modules = new BuiltInLandformModuleCatalogV2();
        return project(schemaFeatureKinds(), codec.builtInSealed(), modules.featureBindings(), modules.modules());
    }

    private Set<String> schemaFeatureKinds() throws Exception {
        JsonNode root = mapper.readTree(SCHEMA.toFile());
        Set<String> result = new HashSet<>();
        root.at("/$defs/featureKind/enum").forEach(value -> result.add(value.asText()));
        return result;
    }

    private static CurrentFeatureStateRegistryV2 project(
            Set<String> schemaKinds,
            FeatureSupportCatalogV2 catalog,
            Map<TerrainIntentV2.FeatureKind, String> bindings,
            List<ModuleDescriptorV2> modules
    ) {
        return CurrentFeatureStateRegistryV2.project(schemaKinds, catalog, bindings, modules);
    }

    private static Set<String> names(
            CurrentFeatureStateRegistryV2 registry,
            CurrentFeatureStateRegistryV2.CurrentState state
    ) {
        return registry.entries().stream().filter(entry -> entry.currentState() == state)
                .map(entry -> entry.featureKind().name()).collect(java.util.stream.Collectors.toSet());
    }
}
