package com.github.nankotsu029.landformcraft.core.v2.catalog;

import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeaturePrimaryRoleV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportEntryV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportLevelV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Read-only V2-15-02 projection of the current public FeatureKind state. It intentionally
 * records current code only: target aliases, migrations, and new kinds belong to later Tasks.
 */
public record CurrentFeatureStateRegistryV2(
        List<Entry> entries,
        List<String> differences
) {
    public static final String CONTRACT_VERSION = "current-feature-state-registry-v1";
    public static final int MAXIMUM_FEATURE_KINDS = 128;

    public CurrentFeatureStateRegistryV2 {
        entries = List.copyOf(entries.stream()
                .sorted(Comparator.comparing(entry -> entry.featureKind().name()))
                .toList());
        differences = List.copyOf(differences.stream().sorted().toList());
    }

    /**
     * Projects a Schema enum set with the compile-time module catalog and sealed support catalog.
     * Schema bytes are supplied by the CI/test boundary, keeping this core type free of filesystem
     * and Jackson dependencies.
     */
    public static CurrentFeatureStateRegistryV2 project(
            Set<String> schemaFeatureKindNames,
            FeatureSupportCatalogV2 catalog,
            Map<TerrainIntentV2.FeatureKind, String> bindings,
            List<ModuleDescriptorV2> modules
    ) {
        Objects.requireNonNull(schemaFeatureKindNames, "schemaFeatureKindNames");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(modules, "modules");
        if (schemaFeatureKindNames.size() > MAXIMUM_FEATURE_KINDS) {
            throw new IllegalArgumentException("schema FeatureKind projection exceeds parse budget");
        }

        Set<String> schemaKinds = new TreeSet<>(schemaFeatureKindNames);
        List<String> differences = new ArrayList<>();
        Set<String> expectedNames = new TreeSet<>();
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            expectedNames.add(kind.name());
        }
        addSetDifferences("schema", expectedNames, schemaKinds, differences);

        Map<String, FeatureSupportEntryV2> catalogByKind = catalogEntries(catalog, differences);
        addSetDifferences("catalog", expectedNames, catalogByKind.keySet(), differences);
        Set<TerrainIntentV2.FeatureKind> expectedKinds = Set.of(TerrainIntentV2.FeatureKind.values());
        if (!bindings.keySet().equals(expectedKinds)) {
            for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
                if (!bindings.containsKey(kind)) {
                    differences.add("module binding missing FeatureKind: " + kind.name());
                }
            }
        }

        Map<String, ModuleDescriptorV2> modulesById = modulesById(modules, differences);
        List<Entry> entries = new ArrayList<>();
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            String moduleId = bindings.get(kind);
            FeatureSupportEntryV2 catalogEntry = catalogByKind.get(kind.name());
            ModuleDescriptorV2 module = moduleId == null ? null : modulesById.get(moduleId);
            ModuleBinding binding = bindingFor(kind, moduleId, module, differences);
            if (module != null && !module.supportedFeatureKinds().contains(kind)) {
                differences.add("module binding is not declared by module: "
                        + kind.name() + " -> " + moduleId);
            }
            entries.add(new Entry(
                    kind,
                    schemaKinds.contains(kind.name()),
                    moduleId == null ? "<missing>" : moduleId,
                    binding,
                    catalogEntry == null ? "<missing>" : catalogEntry.profileId(),
                    catalogEntry == null ? null : catalogEntry.primaryRole(),
                    stateFor(catalogEntry)));
        }
        return new CurrentFeatureStateRegistryV2(entries, differences);
    }

    public void requireConsistent() {
        if (!differences.isEmpty()) {
            throw new IllegalStateException("current FeatureKind state registry differs:\n - "
                    + String.join("\n - ", differences));
        }
    }

    /** Stable content inserted between the markers in current-feature-state-machine-registry.md. */
    public String documentationProjection() {
        requireConsistent();
        Map<CurrentState, List<Entry>> byState = new TreeMap<>(Comparator.comparing(Enum::name));
        for (CurrentState state : CurrentState.values()) {
            byState.put(state, entries.stream().filter(entry -> entry.currentState() == state).toList());
        }
        long dedicated = entries.stream().filter(entry -> entry.moduleBinding() == ModuleBinding.DEDICATED).count();
        long diagnostic = entries.stream().filter(entry -> entry.moduleBinding() == ModuleBinding.DIAGNOSTIC).count();
        return String.join("\n",
                "- Contract: `" + CONTRACT_VERSION + "`",
                "- Sources: enum=" + entries.size() + ", Schema=" + entries.size()
                        + ", module bindings=" + entries.size() + ", catalog FeatureKind entries=" + entries.size(),
                "- Module bindings: dedicated=" + dedicated + ", diagnostic=" + diagnostic,
                "- States: production-connected=" + byState.get(CurrentState.PRODUCTION_CONNECTED).size()
                        + ", offline-or-plan-level=" + byState.get(CurrentState.OFFLINE_OR_PLAN_LEVEL).size()
                        + ", enum-schema-only=" + byState.get(CurrentState.ENUM_SCHEMA_ONLY).size()
                        + ", child-plan-only=" + byState.get(CurrentState.CHILD_PLAN_ONLY).size(),
                "- Production-connected: " + names(byState.get(CurrentState.PRODUCTION_CONNECTED)),
                "- Enum/Schema only: " + names(byState.get(CurrentState.ENUM_SCHEMA_ONLY)),
                "- Child-plan-only: " + names(byState.get(CurrentState.CHILD_PLAN_ONLY)),
                "- Canonical projection SHA-256: `" + projectionChecksum() + "`");
    }

    public String projectionChecksum() {
        String canonical = entries.stream().map(entry -> String.join("|",
                entry.featureKind().name(),
                Boolean.toString(entry.schemaDeclared()),
                entry.moduleId(),
                entry.moduleBinding().name(),
                entry.profileId(),
                entry.primaryRole() == null ? "<missing>" : entry.primaryRole().name(),
                entry.currentState().name())).reduce("", (left, right) -> left + right + "\n");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(Locale.ROOT, "%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static Map<String, FeatureSupportEntryV2> catalogEntries(
            FeatureSupportCatalogV2 catalog,
            List<String> differences
    ) {
        Map<String, FeatureSupportEntryV2> result = new TreeMap<>();
        for (FeatureSupportEntryV2 entry : catalog.entries()) {
            if (!entry.hasFeatureKind()) {
                continue;
            }
            if (result.putIfAbsent(entry.featureKindName(), entry) != null) {
                differences.add("catalog has duplicate FeatureKind entry: " + entry.featureKindName());
            }
        }
        return result;
    }

    private static Map<String, ModuleDescriptorV2> modulesById(
            List<ModuleDescriptorV2> modules,
            List<String> differences
    ) {
        Map<String, ModuleDescriptorV2> result = new TreeMap<>();
        for (ModuleDescriptorV2 module : modules) {
            Objects.requireNonNull(module, "module");
            if (result.putIfAbsent(module.moduleId(), module) != null) {
                differences.add("module catalog has duplicate moduleId: " + module.moduleId());
            }
        }
        return result;
    }

    private static ModuleBinding bindingFor(
            TerrainIntentV2.FeatureKind kind,
            String moduleId,
            ModuleDescriptorV2 module,
            List<String> differences
    ) {
        if (moduleId == null) {
            differences.add("module binding missing FeatureKind: " + kind.name());
            return ModuleBinding.MISSING;
        }
        if (module == null) {
            differences.add("module binding references unknown module: " + kind.name() + " -> " + moduleId);
            return ModuleBinding.MISSING;
        }
        return BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID.equals(moduleId)
                ? ModuleBinding.DIAGNOSTIC
                : ModuleBinding.DEDICATED;
    }

    private static CurrentState stateFor(FeatureSupportEntryV2 entry) {
        if (entry == null) {
            return CurrentState.MISSING_SOURCE;
        }
        if (entry.support().export() == FeatureSupportLevelV2.SUPPORTED
                && entry.support().paperApply() == FeatureSupportLevelV2.SUPPORTED) {
            return CurrentState.PRODUCTION_CONNECTED;
        }
        if (entry.primaryRole() == FeaturePrimaryRoleV2.CHILD_PLAN_ONLY) {
            return CurrentState.CHILD_PLAN_ONLY;
        }
        if (entry.support().offlineGenerate() == FeatureSupportLevelV2.UNSUPPORTED
                && entry.support().validation() == FeatureSupportLevelV2.UNSUPPORTED
                && entry.support().preview() == FeatureSupportLevelV2.UNSUPPORTED
                && entry.support().export() == FeatureSupportLevelV2.UNSUPPORTED) {
            return CurrentState.ENUM_SCHEMA_ONLY;
        }
        return CurrentState.OFFLINE_OR_PLAN_LEVEL;
    }

    private static void addSetDifferences(
            String source,
            Set<String> expected,
            Set<String> actual,
            List<String> differences
    ) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        for (String value : missing) {
            differences.add(source + " missing FeatureKind: " + value);
        }
        Set<String> unknown = new TreeSet<>(actual);
        unknown.removeAll(expected);
        for (String value : unknown) {
            differences.add(source + " declares unknown FeatureKind: " + value);
        }
    }

    private static String names(List<Entry> entries) {
        return entries.stream().map(entry -> "`" + entry.featureKind().name() + "`")
                .sorted().reduce((left, right) -> left + ", " + right).orElse("—");
    }

    public enum ModuleBinding { DEDICATED, DIAGNOSTIC, MISSING }

    public enum CurrentState {
        PRODUCTION_CONNECTED,
        OFFLINE_OR_PLAN_LEVEL,
        ENUM_SCHEMA_ONLY,
        CHILD_PLAN_ONLY,
        MISSING_SOURCE
    }

    public record Entry(
            TerrainIntentV2.FeatureKind featureKind,
            boolean schemaDeclared,
            String moduleId,
            ModuleBinding moduleBinding,
            String profileId,
            FeaturePrimaryRoleV2 primaryRole,
            CurrentState currentState
    ) {
        public Entry {
            Objects.requireNonNull(featureKind, "featureKind");
            moduleId = Objects.requireNonNull(moduleId, "moduleId");
            Objects.requireNonNull(moduleBinding, "moduleBinding");
            profileId = Objects.requireNonNull(profileId, "profileId");
            Objects.requireNonNull(currentState, "currentState");
        }
    }
}
