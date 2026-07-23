package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.v2.catalog.CurrentFeatureStateRegistryV2;
import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportEntryV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportLevelV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable application registry for production generator/validator/preview/export dispatch
 * (V2-15-05 spine, V2-15-06 hydrology, V2-15-07 environment, V2-15-08 sparse-volume capability
 * overlays, and the V2-15-10 {@code v2} contract bump adding {@link RouteClass#OFFLINE_PRODUCTION}
 * per Accepted ADR 0039 Candidate A). The registry is assembled only from compile-time handlers and
 * the V2-15-02 current source-state projection. It does not change Feature Support Catalog levels or
 * Release contracts, and {@code PRODUCTION_CONNECTED} keeps its existing meaning (Paper-and-export
 * complete; coastal four only).
 */
public final class ProductionDispatchRegistryV2 {
    public static final String CONTRACT_VERSION = "production-dispatch-registry-v2";
    public static final int MAXIMUM_PIPELINES = 16;
    public static final int MAXIMUM_ROUTES = 128;

    /**
     * V2-15-10 / ADR 0039 Candidate A: a route either covers the exact current
     * {@code PRODUCTION_CONNECTED} kind set (Paper-and-export complete) or is an explicit
     * {@code OFFLINE_PRODUCTION} allowlist entry (export-only; Paper stays below SUPPORTED). Adding a
     * kind to the offline allowlist never changes {@code PRODUCTION_CONNECTED} or the Paper
     * {@code SUPPORTED} exact set.
     */
    public enum RouteClass { PRODUCTION_CONNECTED, OFFLINE_PRODUCTION }

    /**
     * Kinds the {@code surface-2_5d} coastal pipeline executes as {@link RouteClass#OFFLINE_PRODUCTION}
     * rather than as production-connected coastal modifiers (V2-19-07): the macro foundation producers
     * of ADR 0038 D1, wired one kind per leaf.
     */
    private static final Set<TerrainIntentV2.FeatureKind> COASTAL_OFFLINE_PRODUCTION_KINDS =
            EnumSet.of(TerrainIntentV2.FeatureKind.PLAIN);

    private final Map<TerrainIntentV2.FeatureKind, Route> routes;
    private final Map<TerrainIntentV2.FeatureKind, String> contractOnlyPipelines;
    private final Map<String, ProductionExportPipelineV2> pipelines;
    private final Map<List<String>, ProductionExportPipelineV2> pipelinesByCapabilities;
    private final String registryChecksum;

    public static ProductionDispatchRegistryV2 builtIn() {
        BuiltInLandformModuleCatalogV2 modules = new BuiltInLandformModuleCatalogV2();
        Set<String> compatibilityKinds = new TreeSet<>();
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            compatibilityKinds.add(kind.name());
        }
        FeatureSupportCatalogV2 catalog = new FeatureSupportCatalogCodecV2().builtInSealed();
        CurrentFeatureStateRegistryV2 source = CurrentFeatureStateRegistryV2.project(
                compatibilityKinds,
                catalog,
                modules.featureBindings(),
                modules.modules());
        source.requireConsistent();

        ProductionExportPipelineV2 coastal = new CoastalSurfaceExportPipelineV2();
        ProductionExportPipelineV2 hydrology = new HydrologyPlanExportPipelineV2();
        ProductionExportPipelineV2 environment = new EnvironmentFieldsExportPipelineV2();
        ProductionExportPipelineV2 sparseVolume = new SparseVolumeExportPipelineV2();
        ProductionExportPipelineV2.PipelineDescriptor coastalDescriptor = coastal.descriptor();
        ProductionExportPipelineV2.PipelineDescriptor hydrologyDescriptor = hydrology.descriptor();
        Map<TerrainIntentV2.FeatureKind, CurrentFeatureStateRegistryV2.Entry> sourceEntries =
                sourceEntries(source.entries());
        // V2-19-07: the coastal pipeline now executes one kind that is not a coastal modifier — the
        // PLAIN macro foundation producer — so the PRODUCTION_CONNECTED routes are the executable
        // kinds minus the offline ones rather than "all executable kinds". The exact-cover check at
        // the end of the constructor still proves the coastal four are all routed.
        List<Route> routes = new ArrayList<>(coastalDescriptor.executableKinds().stream()
                .filter(kind -> !COASTAL_OFFLINE_PRODUCTION_KINDS.contains(kind))
                .map(kind -> new Route(
                        kind,
                        sourceEntries.get(kind).moduleId(),
                        coastalDescriptor.pipelineId(),
                        coastalDescriptor.handlers(),
                        coastalDescriptor.requiredCapabilities(),
                        RouteClass.PRODUCTION_CONNECTED))
                .toList());
        // V2-15-10 / ADR 0039 Candidate A: the one explicit OFFLINE_PRODUCTION allowlist entry for
        // this Task. RIVER and MEANDERING_RIVER are export-SUPPORTED but stay below Paper SUPPORTED
        // (coastal 4 only); they run on the shared hydrology-plan pipeline that already executes
        // MeanderingRiverGeneratorV2. No other export-SUPPORTED kind is admitted here.
        for (TerrainIntentV2.FeatureKind offlineKind : List.of(
                TerrainIntentV2.FeatureKind.RIVER, TerrainIntentV2.FeatureKind.MEANDERING_RIVER)) {
            routes.add(new Route(
                    offlineKind,
                    sourceEntries.get(offlineKind).moduleId(),
                    hydrologyDescriptor.pipelineId(),
                    hydrologyDescriptor.handlers(),
                    hydrologyDescriptor.requiredCapabilities(),
                    RouteClass.OFFLINE_PRODUCTION));
        }
        // V2-19-07: the second application of the same ADR 0039 Candidate A pattern, for the first
        // wired macro foundation producer. PLAIN runs on the coastal surface pipeline's foundation
        // tier (ADR 0038 D1) rather than as a coastal modifier, and stays below Paper SUPPORTED.
        for (TerrainIntentV2.FeatureKind offlineKind : COASTAL_OFFLINE_PRODUCTION_KINDS) {
            routes.add(new Route(
                    offlineKind,
                    sourceEntries.get(offlineKind).moduleId(),
                    coastalDescriptor.pipelineId(),
                    coastalDescriptor.handlers(),
                    coastalDescriptor.requiredCapabilities(),
                    RouteClass.OFFLINE_PRODUCTION));
        }
        return new ProductionDispatchRegistryV2(
                source.entries(), routes, List.of(coastal, hydrology, environment, sparseVolume),
                exportSupportedKinds(catalog));
    }

    static Set<TerrainIntentV2.FeatureKind> exportSupportedKinds(FeatureSupportCatalogV2 catalog) {
        Set<TerrainIntentV2.FeatureKind> result = EnumSet.noneOf(TerrainIntentV2.FeatureKind.class);
        for (FeatureSupportEntryV2 entry : catalog.entries()) {
            if (entry.hasFeatureKind() && entry.support().export() == FeatureSupportLevelV2.SUPPORTED) {
                result.add(TerrainIntentV2.FeatureKind.valueOf(entry.featureKindName()));
            }
        }
        return result;
    }

    /**
     * @param exportSupportedKinds current Feature Support Catalog kinds with {@code export==SUPPORTED};
     *         only used to validate {@link RouteClass#OFFLINE_PRODUCTION} routes (ADR 0039 Candidate A).
     */
    public ProductionDispatchRegistryV2(
            List<CurrentFeatureStateRegistryV2.Entry> sourceEntries,
            List<Route> routes,
            List<ProductionExportPipelineV2> pipelines,
            Set<TerrainIntentV2.FeatureKind> exportSupportedKinds
    ) {
        Objects.requireNonNull(sourceEntries, "sourceEntries");
        Objects.requireNonNull(routes, "routes");
        Objects.requireNonNull(pipelines, "pipelines");
        Objects.requireNonNull(exportSupportedKinds, "exportSupportedKinds");
        if (routes.size() > MAXIMUM_ROUTES) {
            throw new IllegalArgumentException("production dispatch route budget exceeded");
        }
        if (pipelines.isEmpty() || pipelines.size() > MAXIMUM_PIPELINES) {
            throw new IllegalArgumentException("production dispatch pipeline budget exceeded");
        }

        Map<TerrainIntentV2.FeatureKind, CurrentFeatureStateRegistryV2.Entry> sourceByKind =
                sourceEntries(sourceEntries);
        Map<String, ProductionExportPipelineV2> pipelineById = new TreeMap<>();
        Map<List<String>, ProductionExportPipelineV2> pipelineByCapabilities = new TreeMap<>(
                Comparator.comparing(capabilities -> String.join(",", capabilities)));
        Map<TerrainIntentV2.FeatureKind, String> contractOnly = new EnumMap<>(TerrainIntentV2.FeatureKind.class);
        for (ProductionExportPipelineV2 pipeline : pipelines) {
            Objects.requireNonNull(pipeline, "pipeline");
            ProductionExportPipelineV2.PipelineDescriptor descriptor =
                    Objects.requireNonNull(pipeline.descriptor(), "pipeline descriptor");
            if (pipelineById.putIfAbsent(descriptor.pipelineId(), pipeline) != null) {
                throw new IllegalArgumentException("duplicate production pipeline id: " + descriptor.pipelineId());
            }
            if (pipelineByCapabilities.putIfAbsent(descriptor.requiredCapabilities(), pipeline) != null) {
                throw new IllegalArgumentException("duplicate production pipeline capability set: "
                        + descriptor.requiredCapabilities());
            }
            for (TerrainIntentV2.FeatureKind kind : descriptor.contractOnlyKinds()) {
                CurrentFeatureStateRegistryV2.Entry source = requireSource(sourceByKind, kind);
                if (source.currentState() == CurrentFeatureStateRegistryV2.CurrentState.PRODUCTION_CONNECTED
                        || source.moduleBinding() != CurrentFeatureStateRegistryV2.ModuleBinding.DIAGNOSTIC) {
                    throw new IllegalArgumentException(
                            "contract-only kind must remain a non-production diagnostic binding: " + kind);
                }
                // Capability overlays may redeclare the same fixture contract-only kinds; the first
                // registered owner remains the checksum identity used by feature-route selection.
                contractOnly.putIfAbsent(kind, descriptor.pipelineId());
            }
        }

        Map<TerrainIntentV2.FeatureKind, Route> routeByKind = new EnumMap<>(TerrainIntentV2.FeatureKind.class);
        for (Route route : routes) {
            Objects.requireNonNull(route, "route");
            if (routeByKind.putIfAbsent(route.featureKind(), route) != null) {
                throw new IllegalArgumentException("duplicate production dispatch kind: " + route.featureKind());
            }
            CurrentFeatureStateRegistryV2.Entry source = requireSource(sourceByKind, route.featureKind());
            if (route.routeClass() == RouteClass.PRODUCTION_CONNECTED) {
                if (source.currentState() != CurrentFeatureStateRegistryV2.CurrentState.PRODUCTION_CONNECTED) {
                    throw new IllegalArgumentException("dispatch route would elevate a non-production feature: "
                            + route.featureKind());
                }
            } else {
                // ADR 0039 Candidate A allowlist: dedicated module, export==SUPPORTED, and not already
                // PRODUCTION_CONNECTED (which, combined with export==SUPPORTED, means paperApply is not
                // SUPPORTED either). PRODUCTION_CONNECTED's meaning and the Paper SUPPORTED exact set are
                // unchanged; only the offline export route is new.
                if (source.currentState() == CurrentFeatureStateRegistryV2.CurrentState.PRODUCTION_CONNECTED) {
                    throw new IllegalArgumentException(
                            "offline production route kind must not already be production-connected: "
                                    + route.featureKind());
                }
                if (source.moduleBinding() != CurrentFeatureStateRegistryV2.ModuleBinding.DEDICATED) {
                    throw new IllegalArgumentException(
                            "offline production route requires a dedicated module binding: " + route.featureKind());
                }
                if (!exportSupportedKinds.contains(route.featureKind())) {
                    throw new IllegalArgumentException(
                            "offline production route requires an export-supported kind: " + route.featureKind());
                }
            }
            if (!source.moduleId().equals(route.moduleId())) {
                throw new IllegalArgumentException("dispatch route module differs from current registry: "
                        + route.featureKind());
            }
            ProductionExportPipelineV2 pipeline = pipelineById.get(route.pipelineId());
            if (pipeline == null) {
                throw new IllegalArgumentException("dispatch route references unknown pipeline: "
                        + route.pipelineId());
            }
            ProductionExportPipelineV2.PipelineDescriptor descriptor = pipeline.descriptor();
            if (!descriptor.executableKinds().contains(route.featureKind())) {
                throw new IllegalArgumentException("pipeline does not execute route kind: " + route.featureKind());
            }
            if (!descriptor.handlers().equals(route.handlers())) {
                throw new IllegalArgumentException("dispatch route handler chain differs from pipeline: "
                        + route.featureKind());
            }
            if (!descriptor.requiredCapabilities().equals(route.requiredCapabilities())) {
                throw new IllegalArgumentException("dispatch route capability set differs from pipeline: "
                        + route.featureKind());
            }
        }

        Set<TerrainIntentV2.FeatureKind> expected = new TreeSet<>(Comparator.comparing(Enum::name));
        for (CurrentFeatureStateRegistryV2.Entry entry : sourceEntries) {
            if (entry.currentState() == CurrentFeatureStateRegistryV2.CurrentState.PRODUCTION_CONNECTED) {
                expected.add(entry.featureKind());
            }
        }
        Set<TerrainIntentV2.FeatureKind> actualProductionConnected = new TreeSet<>(Comparator.comparing(Enum::name));
        for (Route route : routeByKind.values()) {
            if (route.routeClass() == RouteClass.PRODUCTION_CONNECTED) {
                actualProductionConnected.add(route.featureKind());
            }
        }
        if (!expected.equals(actualProductionConnected)) {
            throw new IllegalArgumentException("production dispatch routes must exactly cover current production kinds");
        }
        this.routes = Map.copyOf(routeByKind);
        this.contractOnlyPipelines = Map.copyOf(contractOnly);
        this.pipelines = Map.copyOf(pipelineById);
        this.pipelinesByCapabilities = Map.copyOf(pipelineByCapabilities);
        this.registryChecksum = checksum(canonicalRegistryLines());
    }

    public DispatchSelection select(TerrainIntentV2 intent) {
        return select(intent, List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D));
    }

    /**
     * Selects the production pipeline whose required capability set exactly matches
     * {@code requiredCapabilities}, after validating that the intent's feature kinds are covered by
     * the current production routes (or shared contract-only fixture kinds).
     */
    public DispatchSelection select(TerrainIntentV2 intent, List<String> requiredCapabilities) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(requiredCapabilities, "requiredCapabilities");
        List<String> capabilities = List.copyOf(requiredCapabilities);
        ProductionExportPipelineV2 capabilityPipeline = pipelinesByCapabilities.get(capabilities);
        if (capabilityPipeline == null) {
            throw new IllegalArgumentException("no production pipeline for capability set: " + capabilities);
        }

        Set<TerrainIntentV2.FeatureKind> inputKinds = new TreeSet<>(Comparator.comparing(Enum::name));
        for (TerrainIntentV2.Feature feature : intent.features()) {
            inputKinds.add(feature.kind());
        }
        if (inputKinds.isEmpty()) {
            throw new IllegalArgumentException("production dispatch requires at least one feature");
        }

        List<Route> selectedRoutes = new ArrayList<>();
        List<TerrainIntentV2.FeatureKind> contractOnly = new ArrayList<>();
        for (TerrainIntentV2.FeatureKind kind : inputKinds) {
            Route route = routes.get(kind);
            if (route != null) {
                if (!capabilityPipeline.descriptor().executableKinds().contains(kind)) {
                    throw new IllegalArgumentException(
                            "capability pipeline does not execute production kind: " + kind);
                }
                selectedRoutes.add(new Route(
                        route.featureKind(),
                        route.moduleId(),
                        capabilityPipeline.descriptor().pipelineId(),
                        capabilityPipeline.descriptor().handlers(),
                        capabilityPipeline.descriptor().requiredCapabilities(),
                        route.routeClass()));
                continue;
            }
            if (!contractOnlyPipelines.containsKey(kind)
                    || !capabilityPipeline.descriptor().contractOnlyKinds().contains(kind)) {
                throw new IllegalArgumentException("feature kind has no production dispatch route: " + kind);
            }
            contractOnly.add(kind);
        }
        if (selectedRoutes.isEmpty()) {
            throw new IllegalArgumentException("contract-only features cannot select a production pipeline");
        }
        selectedRoutes.sort(Comparator.comparing(route -> route.featureKind().name()));
        contractOnly.sort(Comparator.comparing(Enum::name));
        DispatchPlan plan = new DispatchPlan(
                CONTRACT_VERSION,
                capabilityPipeline.descriptor().pipelineId(),
                selectedRoutes,
                contractOnly,
                capabilityPipeline.descriptor().requiredCapabilities(),
                registryChecksum,
                checksum(canonicalPlanLines(
                        capabilityPipeline.descriptor().pipelineId(), selectedRoutes, contractOnly)));
        return new DispatchSelection(plan, capabilityPipeline);
    }

    public String registryChecksum() {
        return registryChecksum;
    }

    public List<Route> routes() {
        return routes.values().stream()
                .sorted(Comparator.comparing(route -> route.featureKind().name()))
                .toList();
    }

    /**
     * Contract-only compatibility kinds mapped to the pipeline owning each fixture contract. These
     * kinds are accepted as diagnostic inputs alongside a routed kind but never select a production
     * pipeline themselves; the V2-19-01 reachability projection displays them as their own class.
     */
    public Map<TerrainIntentV2.FeatureKind, String> contractOnlyKinds() {
        return contractOnlyPipelines;
    }

    private List<String> canonicalRegistryLines() {
        List<String> lines = new ArrayList<>();
        lines.add(CONTRACT_VERSION);
        for (Route route : routes()) {
            lines.add(route.canonicalLine());
        }
        contractOnlyPipelines.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                .forEach(entry -> lines.add("contract-only|" + entry.getKey().name() + "|" + entry.getValue()));
        pipelinesByCapabilities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(capabilities ->
                        String.join(",", capabilities))))
                .forEach(entry -> lines.add("capability-pipeline|"
                        + String.join(",", entry.getKey()) + "|"
                        + entry.getValue().descriptor().pipelineId()));
        return lines;
    }

    private static List<String> canonicalPlanLines(
            String pipelineId,
            List<Route> routes,
            List<TerrainIntentV2.FeatureKind> contractOnly
    ) {
        List<String> lines = new ArrayList<>();
        lines.add(CONTRACT_VERSION);
        lines.add(pipelineId);
        routes.stream().map(Route::canonicalLine).forEach(lines::add);
        contractOnly.stream().map(kind -> "contract-only|" + kind.name()).forEach(lines::add);
        return lines;
    }

    private static Map<TerrainIntentV2.FeatureKind, CurrentFeatureStateRegistryV2.Entry> sourceEntries(
            List<CurrentFeatureStateRegistryV2.Entry> entries
    ) {
        Map<TerrainIntentV2.FeatureKind, CurrentFeatureStateRegistryV2.Entry> result =
                new EnumMap<>(TerrainIntentV2.FeatureKind.class);
        for (CurrentFeatureStateRegistryV2.Entry entry : entries) {
            Objects.requireNonNull(entry, "source entry");
            if (result.putIfAbsent(entry.featureKind(), entry) != null) {
                throw new IllegalArgumentException("duplicate current source entry: " + entry.featureKind());
            }
        }
        if (result.size() != TerrainIntentV2.FeatureKind.values().length) {
            throw new IllegalArgumentException("current source entries must cover every compatibility FeatureKind");
        }
        return result;
    }

    private static CurrentFeatureStateRegistryV2.Entry requireSource(
            Map<TerrainIntentV2.FeatureKind, CurrentFeatureStateRegistryV2.Entry> entries,
            TerrainIntentV2.FeatureKind kind
    ) {
        CurrentFeatureStateRegistryV2.Entry entry = entries.get(kind);
        if (entry == null) {
            throw new IllegalArgumentException("missing current source entry for " + kind);
        }
        return entry;
    }

    private static String checksum(List<String> lines) {
        String canonical = String.join("\n", lines) + "\n";
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

    public record Route(
            TerrainIntentV2.FeatureKind featureKind,
            String moduleId,
            String pipelineId,
            ProductionExportPipelineV2.HandlerSet handlers,
            List<String> requiredCapabilities,
            RouteClass routeClass
    ) {
        public Route {
            Objects.requireNonNull(featureKind, "featureKind");
            moduleId = stableText(moduleId, "moduleId");
            pipelineId = stableText(pipelineId, "pipelineId");
            Objects.requireNonNull(handlers, "handlers");
            requiredCapabilities = List.copyOf(Objects.requireNonNull(
                    requiredCapabilities, "requiredCapabilities"));
            Objects.requireNonNull(routeClass, "routeClass");
            if (requiredCapabilities.isEmpty()) {
                throw new IllegalArgumentException("dispatch route requires a capability");
            }
        }

        private String canonicalLine() {
            return String.join("|",
                    featureKind.name(),
                    moduleId,
                    pipelineId,
                    handlers.generatorHandlerId(),
                    handlers.validatorHandlerId(),
                    handlers.previewHandlerId(),
                    handlers.exportHandlerId(),
                    String.join(",", requiredCapabilities),
                    routeClass.name());
        }
    }

    public record DispatchPlan(
            String contractVersion,
            String pipelineId,
            List<Route> routes,
            List<TerrainIntentV2.FeatureKind> contractOnlyKinds,
            List<String> requiredCapabilities,
            String registryChecksum,
            String planChecksum
    ) {
        public DispatchPlan {
            contractVersion = stableText(contractVersion, "contractVersion");
            pipelineId = stableText(pipelineId, "pipelineId");
            routes = List.copyOf(routes);
            contractOnlyKinds = List.copyOf(contractOnlyKinds);
            requiredCapabilities = List.copyOf(requiredCapabilities);
            registryChecksum = sha256(registryChecksum, "registryChecksum");
            planChecksum = sha256(planChecksum, "planChecksum");
        }
    }

    public record DispatchSelection(
            DispatchPlan plan,
            ProductionExportPipelineV2 pipeline
    ) {
        public DispatchSelection {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(pipeline, "pipeline");
            if (!plan.pipelineId().equals(pipeline.descriptor().pipelineId())) {
                throw new IllegalArgumentException("dispatch selection pipeline differs from plan");
            }
        }
    }

    private static String stableText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 96) {
            throw new IllegalArgumentException(field + " must be non-blank and bounded");
        }
        return value;
    }

    private static String sha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return value;
    }
}
