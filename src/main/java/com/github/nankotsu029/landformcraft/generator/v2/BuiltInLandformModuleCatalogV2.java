package com.github.nankotsu029.landformcraft.generator.v2;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalLandformModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformPlainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.geology.GeologyFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.climate.ClimateFieldModulesV2;
import com.github.nankotsu029.landformcraft.generator.v2.environment.water.WaterConditionFieldModulesV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta.HydrologyDeltaModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.HydrologyTidalModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.HydrologyLakeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.HydrologyRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.HydrologyWaterfallModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.LandformCanyonModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.LandformFjordModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove.LandformMangroveModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.reef.LandformCoralReefModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mountain.LandformMountainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic.LandformVolcanicModuleV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationPreviewModuleV2;
import com.github.nankotsu029.landformcraft.validation.v2.hydrology.HydrologyValidationPreviewModuleV2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compile-time catalog. It never scans ServiceLoader, external JARs, scripts, or class names. */
public final class BuiltInLandformModuleCatalogV2 {
    public static final String CONTRACT_MODULE_ID = "v2.core.intent-contract";
    public static final String DIAGNOSTIC_MODULE_ID = "v2.feature.diagnostic";
    public static final String CONTRACT_FIELD_ID = "intent.land-water-mask";
    public static final String DIAGNOSTIC_FIELD_ID = "diagnostic.feature-plan";
    public static final String INPUT_STAGE_ID = "compile.inputs";
    public static final String FEATURE_STAGE_ID = "compile.features";

    private static final CoastalFoundationModuleV2 COASTAL_MODULE =
            new CoastalFoundationModuleV2(CONTRACT_FIELD_ID);
    private static final CoastalTransitionModuleV2 COASTAL_TRANSITION_MODULE =
            new CoastalTransitionModuleV2();
    private static final CoastalValidationPreviewModuleV2 COASTAL_VALIDATION_PREVIEW_MODULE =
            new CoastalValidationPreviewModuleV2();
    private static final HydrologyValidationPreviewModuleV2 HYDROLOGY_VALIDATION_PREVIEW_MODULE =
            new HydrologyValidationPreviewModuleV2();
    private static final LandformPlainModuleV2 LANDFORM_PLAIN_MODULE = new LandformPlainModuleV2();
    private static final GeologyFoundationModuleV2 GEOLOGY_FOUNDATION_MODULE =
            new GeologyFoundationModuleV2();
    private static final ClimateFieldModulesV2 CLIMATE_FIELD_MODULES = new ClimateFieldModulesV2();
    private static final WaterConditionFieldModulesV2 WATER_CONDITION_FIELD_MODULES =
            new WaterConditionFieldModulesV2();
    private static final HydrologyIrModuleV2 HYDROLOGY_IR_MODULE = new HydrologyIrModuleV2();
    private static final HydrologyRiverModuleV2 HYDROLOGY_RIVER_MODULE = new HydrologyRiverModuleV2();
    private static final HydrologyLakeModuleV2 HYDROLOGY_LAKE_MODULE = new HydrologyLakeModuleV2();
    private static final LandformCanyonModuleV2 LANDFORM_CANYON_MODULE = new LandformCanyonModuleV2();
    private static final LandformFjordModuleV2 LANDFORM_FJORD_MODULE = new LandformFjordModuleV2();
    private static final LandformMangroveModuleV2 LANDFORM_MANGROVE_MODULE = new LandformMangroveModuleV2();
    private static final LandformCoralReefModuleV2 LANDFORM_CORAL_REEF_MODULE = new LandformCoralReefModuleV2();
    private static final LandformMountainModuleV2 LANDFORM_MOUNTAIN_MODULE = new LandformMountainModuleV2();
    private static final LandformVolcanicModuleV2 LANDFORM_VOLCANIC_MODULE = new LandformVolcanicModuleV2();
    private static final HydrologyWaterfallModuleV2 HYDROLOGY_WATERFALL_MODULE = new HydrologyWaterfallModuleV2();
    private static final HydrologyDeltaModuleV2 HYDROLOGY_DELTA_MODULE = new HydrologyDeltaModuleV2();
    private static final HydrologyTidalModuleV2 HYDROLOGY_TIDAL_MODULE = new HydrologyTidalModuleV2();
    private static final HydrologyReconciliationModuleV2 HYDROLOGY_RECONCILIATION_MODULE =
            new HydrologyReconciliationModuleV2();

    private static final List<WorldBlueprintV2.StageDescriptor> STAGES = List.of(
            new WorldBlueprintV2.StageDescriptor(INPUT_STAGE_ID, List.of()),
            new WorldBlueprintV2.StageDescriptor(FEATURE_STAGE_ID, List.of(INPUT_STAGE_ID)),
            // V2-19-07: the foundation producer tier resolves before feature composition (ADR 0038
            // D5-1), so its stage depends only on the compiled inputs.
            new WorldBlueprintV2.StageDescriptor(
                    LandformPlainModuleV2.STAGE_ID, List.of(INPUT_STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    GeologyFoundationModuleV2.STAGE_ID, List.of(FEATURE_STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    ClimateFieldModulesV2.PRIOR_STAGE_ID, List.of(GeologyFoundationModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    HydrologyIrModuleV2.STAGE_ID,
                    List.of(GeologyFoundationModuleV2.STAGE_ID, ClimateFieldModulesV2.PRIOR_STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    HydrologyRiverModuleV2.STAGE_ID, List.of(HydrologyIrModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    HydrologyLakeModuleV2.STAGE_ID, List.of(HydrologyIrModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    LandformCanyonModuleV2.STAGE_ID, List.of(HydrologyRiverModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    HydrologyWaterfallModuleV2.STAGE_ID, List.of(HydrologyRiverModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(CoastalFoundationModuleV2.STAGE_ID, List.of(INPUT_STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    CoastalTransitionModuleV2.STAGE_ID, List.of(CoastalFoundationModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    HydrologyDeltaModuleV2.STAGE_ID,
                    List.of(HydrologyRiverModuleV2.STAGE_ID, CoastalTransitionModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    HydrologyTidalModuleV2.STAGE_ID,
                    List.of(CoastalTransitionModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    LandformMangroveModuleV2.STAGE_ID,
                    List.of(HydrologyTidalModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    LandformCoralReefModuleV2.STAGE_ID,
                    List.of(CoastalTransitionModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    LandformFjordModuleV2.STAGE_ID,
                    List.of(CoastalTransitionModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    LandformMountainModuleV2.STAGE_ID,
                    List.of(CoastalTransitionModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    LandformVolcanicModuleV2.STAGE_ID,
                    List.of(LandformMountainModuleV2.STAGE_ID, CoastalTransitionModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    HydrologyReconciliationModuleV2.STAGE_ID,
                    List.of(
                            HydrologyIrModuleV2.STAGE_ID,
                            HydrologyRiverModuleV2.STAGE_ID,
                            HydrologyLakeModuleV2.STAGE_ID,
                            LandformCanyonModuleV2.STAGE_ID,
                            HydrologyWaterfallModuleV2.STAGE_ID,
                            HydrologyDeltaModuleV2.STAGE_ID,
                            HydrologyTidalModuleV2.STAGE_ID,
                            LandformMangroveModuleV2.STAGE_ID,
                            LandformCoralReefModuleV2.STAGE_ID,
                            LandformFjordModuleV2.STAGE_ID,
                            LandformMountainModuleV2.STAGE_ID,
                            LandformVolcanicModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    ClimateFieldModulesV2.FINAL_STAGE_ID,
                    List.of(ClimateFieldModulesV2.PRIOR_STAGE_ID, HydrologyReconciliationModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    WaterConditionFieldModulesV2.STAGE_ID,
                    List.of(ClimateFieldModulesV2.FINAL_STAGE_ID, HydrologyReconciliationModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    CoastalValidationPreviewModuleV2.STAGE_ID, List.of(CoastalTransitionModuleV2.STAGE_ID)),
            new WorldBlueprintV2.StageDescriptor(
                    HydrologyValidationPreviewModuleV2.STAGE_ID,
                    List.of(HydrologyValidationPreviewModuleV2.dependsOnStageId()))
    );
    private static final List<ModuleDescriptorV2> MODULES = List.of(
            new ModuleDescriptorV2(
                    CONTRACT_MODULE_ID,
                    "0.1.0-diagnostic",
                    ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                    List.of(),
                    List.of(),
                    List.of(CONTRACT_FIELD_ID),
                    List.of(new ModuleDescriptorV2.FieldWrite(
                            CONTRACT_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)),
                    INPUT_STAGE_ID,
                    0,
                    0,
                    ModuleDescriptorV2.ResourceClass.DIAGNOSTIC_LOW,
                    List.of("diagnostic.contract"),
                    List.of("diagnostic.geometry")
            ),
            new ModuleDescriptorV2(
                    DIAGNOSTIC_MODULE_ID,
                    "0.1.0-diagnostic",
                    ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                    diagnosticKinds(),
                    List.of(CONTRACT_FIELD_ID),
                    List.of(DIAGNOSTIC_FIELD_ID),
                    List.of(new ModuleDescriptorV2.FieldWrite(
                            DIAGNOSTIC_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)),
                    FEATURE_STAGE_ID,
                    0,
                    0,
                    ModuleDescriptorV2.ResourceClass.DIAGNOSTIC_LOW,
                    List.of("diagnostic.contract", "diagnostic.geometry"),
                    List.of("diagnostic.geometry")
            ),
            LANDFORM_PLAIN_MODULE.descriptor(),
            GEOLOGY_FOUNDATION_MODULE.descriptor(),
            CLIMATE_FIELD_MODULES.priorDescriptor(),
            CLIMATE_FIELD_MODULES.finalDescriptor(),
            WATER_CONDITION_FIELD_MODULES.descriptor(),
            HYDROLOGY_IR_MODULE.descriptor(),
            HYDROLOGY_RIVER_MODULE.descriptor(),
            HYDROLOGY_LAKE_MODULE.descriptor(),
            LANDFORM_CANYON_MODULE.descriptor(),
            HYDROLOGY_WATERFALL_MODULE.descriptor(),
            HYDROLOGY_DELTA_MODULE.descriptor(),
            HYDROLOGY_TIDAL_MODULE.descriptor(),
            LANDFORM_MANGROVE_MODULE.descriptor(),
            LANDFORM_CORAL_REEF_MODULE.descriptor(),
            LANDFORM_FJORD_MODULE.descriptor(),
            LANDFORM_MOUNTAIN_MODULE.descriptor(),
            LANDFORM_VOLCANIC_MODULE.descriptor(),
            HYDROLOGY_RECONCILIATION_MODULE.descriptor(),
            HYDROLOGY_VALIDATION_PREVIEW_MODULE.descriptor(),
            COASTAL_MODULE.descriptor(),
            COASTAL_TRANSITION_MODULE.descriptor(),
            COASTAL_VALIDATION_PREVIEW_MODULE.descriptor()
    );
    private static final Map<TerrainIntentV2.FeatureKind, String> BINDINGS = bindings();

    static {
        validateCatalog(MODULES, BINDINGS, STAGES);
    }

    public List<ModuleDescriptorV2> modules() {
        return MODULES;
    }

    public List<WorldBlueprintV2.StageDescriptor> stages() {
        return STAGES;
    }

    /**
     * Read-only current binding projection for inventory/CI checks. This does not expose module
     * registration or allow mutation; the compile-time catalog remains the only owner.
     */
    public Map<TerrainIntentV2.FeatureKind, String> featureBindings() {
        return BINDINGS;
    }

    public ModuleDescriptorV2 requireFor(TerrainIntentV2.FeatureKind kind) {
        String moduleId = BINDINGS.get(kind);
        if (moduleId == null) {
            throw new IllegalArgumentException("no built-in module binding for " + kind);
        }
        return MODULES.stream().filter(module -> module.moduleId().equals(moduleId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("built-in binding references unknown module " + moduleId));
    }

    public CoastalLandformModuleV2 requireCoastalFor(TerrainIntentV2.FeatureKind kind) {
        ModuleDescriptorV2 descriptor = requireFor(kind);
        if (!descriptor.moduleId().equals(COASTAL_MODULE.descriptor().moduleId())) {
            throw new IllegalArgumentException("feature kind is not bound to the coastal module: " + kind);
        }
        return COASTAL_MODULE;
    }

    public HydrologyIrModuleV2 hydrologyIrModule() {
        return HYDROLOGY_IR_MODULE;
    }

    public GeologyFoundationModuleV2 geologyFoundationModule() {
        return GEOLOGY_FOUNDATION_MODULE;
    }

    public ClimateFieldModulesV2 climateFieldModules() {
        return CLIMATE_FIELD_MODULES;
    }

    public WaterConditionFieldModulesV2 waterConditionFieldModules() {
        return WATER_CONDITION_FIELD_MODULES;
    }

    public HydrologyRiverModuleV2 hydrologyRiverModule() {
        return HYDROLOGY_RIVER_MODULE;
    }

    public HydrologyLakeModuleV2 hydrologyLakeModule() {
        return HYDROLOGY_LAKE_MODULE;
    }

    public LandformCanyonModuleV2 landformCanyonModule() {
        return LANDFORM_CANYON_MODULE;
    }

    public HydrologyWaterfallModuleV2 hydrologyWaterfallModule() {
        return HYDROLOGY_WATERFALL_MODULE;
    }

    public HydrologyDeltaModuleV2 hydrologyDeltaModule() {
        return HYDROLOGY_DELTA_MODULE;
    }

    public HydrologyTidalModuleV2 hydrologyTidalModule() {
        return HYDROLOGY_TIDAL_MODULE;
    }

    public LandformFjordModuleV2 landformFjordModule() {
        return LANDFORM_FJORD_MODULE;
    }

    public LandformMangroveModuleV2 landformMangroveModule() {
        return LANDFORM_MANGROVE_MODULE;
    }

    public LandformMountainModuleV2 landformMountainModule() {
        return LANDFORM_MOUNTAIN_MODULE;
    }

    public LandformVolcanicModuleV2 landformVolcanicModule() {
        return LANDFORM_VOLCANIC_MODULE;
    }

    public HydrologyReconciliationModuleV2 hydrologyReconciliationModule() {
        return HYDROLOGY_RECONCILIATION_MODULE;
    }

    public boolean hasValidatorCapability(TerrainIntentV2.FeatureKind kind) {
        return MODULES.stream().anyMatch(module -> module.validatorCapabilities().contains(capability(kind, "validator")));
    }

    public boolean hasPreviewCapability(TerrainIntentV2.FeatureKind kind) {
        return MODULES.stream().anyMatch(module -> module.previewCapabilities().contains(capability(kind, "preview")));
    }

    private static String capability(TerrainIntentV2.FeatureKind kind, String suffix) {
        return "feature." + kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + '.' + suffix;
    }

    /** Pure descriptor validation also used by corruption tests; it does not register executable modules. */
    public static void validateCatalog(
            List<ModuleDescriptorV2> modules,
            Map<TerrainIntentV2.FeatureKind, String> bindings,
            List<WorldBlueprintV2.StageDescriptor> stages
    ) {
        List<ModuleDescriptorV2> stableModules = List.copyOf(modules);
        Map<TerrainIntentV2.FeatureKind, String> stableBindings = Map.copyOf(bindings);
        List<WorldBlueprintV2.StageDescriptor> stableStages = List.copyOf(stages);
        Map<String, ModuleDescriptorV2> byId = new HashMap<>();
        for (ModuleDescriptorV2 module : stableModules) {
            if (byId.putIfAbsent(module.moduleId(), module) != null) {
                throw new IllegalArgumentException("duplicate module id: " + module.moduleId());
            }
        }
        for (Map.Entry<TerrainIntentV2.FeatureKind, String> binding : stableBindings.entrySet()) {
            ModuleDescriptorV2 module = byId.get(binding.getValue());
            if (module == null) {
                throw new IllegalArgumentException("feature binding references unknown module: " + binding.getValue());
            }
            if (!module.supportedFeatureKinds().contains(binding.getKey())) {
                throw new IllegalArgumentException("feature binding is not declared by module: " + binding.getKey());
            }
        }

        Map<String, WorldBlueprintV2.StageDescriptor> stagesById = new HashMap<>();
        for (WorldBlueprintV2.StageDescriptor stage : stableStages) {
            if (stagesById.putIfAbsent(stage.stageId(), stage) != null) {
                throw new IllegalArgumentException("duplicate stage id: " + stage.stageId());
            }
        }
        for (ModuleDescriptorV2 module : stableModules) {
            if (!stagesById.containsKey(module.stageId())) {
                throw new IllegalArgumentException("module references unknown stage: " + module.stageId());
            }
        }
        Map<String, List<String>> stageGraph = new HashMap<>();
        for (WorldBlueprintV2.StageDescriptor stage : stableStages) {
            for (String dependency : stage.dependsOnStageIds()) {
                if (!stagesById.containsKey(dependency)) {
                    throw new IllegalArgumentException("stage references unknown dependency: " + dependency);
                }
                stageGraph.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(stage.stageId());
            }
        }
        requireAcyclic(stageGraph, "stage graph");

        Map<String, String> fieldOwner = new HashMap<>();
        for (ModuleDescriptorV2 module : stableModules) {
            for (String field : module.providedFields()) {
                String previous = fieldOwner.putIfAbsent(field, module.moduleId());
                if (previous != null && !previous.equals(module.moduleId())) {
                    throw new IllegalArgumentException("field owner collision for " + field);
                }
            }
        }
        Map<String, List<String>> moduleGraph = new HashMap<>();
        for (ModuleDescriptorV2 module : stableModules) {
            for (String field : module.requiredFields()) {
                String owner = fieldOwner.get(field);
                if (owner == null) {
                    throw new IllegalArgumentException("required field has no owner: " + field);
                }
                if (!owner.equals(module.moduleId())) {
                    moduleGraph.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(module.moduleId());
                }
            }
        }
        requireAcyclic(moduleGraph, "field owner graph");
    }

    private static Map<TerrainIntentV2.FeatureKind, String> bindings() {
        EnumMap<TerrainIntentV2.FeatureKind, String> result = new EnumMap<>(TerrainIntentV2.FeatureKind.class);
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            result.put(kind, DIAGNOSTIC_MODULE_ID);
        }
        result.put(TerrainIntentV2.FeatureKind.SANDY_BEACH, CoastalFoundationModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR, CoastalFoundationModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.HARBOR_BASIN, CoastalFoundationModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.ROCKY_CAPE, CoastalFoundationModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.PLAIN, LandformPlainModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.MEANDERING_RIVER, HydrologyRiverModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.RIVER, HydrologyRiverModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.LAKE, HydrologyLakeModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.CANYON, LandformCanyonModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.WATERFALL, HydrologyWaterfallModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.DELTA, HydrologyDeltaModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK, HydrologyTidalModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.MANGROVE_WETLAND, LandformMangroveModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.CORAL_REEF, LandformCoralReefModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.FJORD, LandformFjordModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE, LandformMountainModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE, LandformMountainModuleV2.MODULE_ID);
        result.put(TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO, LandformVolcanicModuleV2.MODULE_ID);
        return Collections.unmodifiableMap(result);
    }

    private static List<TerrainIntentV2.FeatureKind> diagnosticKinds() {
        return java.util.Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .filter(kind -> !com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2
                        .isFoundationKind(kind)
                        && kind != TerrainIntentV2.FeatureKind.MEANDERING_RIVER
                        && kind != TerrainIntentV2.FeatureKind.RIVER
                        && kind != TerrainIntentV2.FeatureKind.LAKE
                        && kind != TerrainIntentV2.FeatureKind.CANYON
                        && kind != TerrainIntentV2.FeatureKind.WATERFALL)
                .filter(kind -> kind != TerrainIntentV2.FeatureKind.PLAIN)
                .filter(kind -> kind != TerrainIntentV2.FeatureKind.DELTA)
                .filter(kind -> kind != TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK)
                .filter(kind -> kind != TerrainIntentV2.FeatureKind.MANGROVE_WETLAND)
                .filter(kind -> kind != TerrainIntentV2.FeatureKind.CORAL_REEF)
                .filter(kind -> kind != TerrainIntentV2.FeatureKind.FJORD)
                .filter(kind -> kind != TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE)
                .filter(kind -> kind != TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE)
                .filter(kind -> kind != TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO)
                .toList();
    }

    private static void requireAcyclic(Map<String, List<String>> graph, String name) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String node : graph.keySet()) {
            if (cycle(node, graph, visiting, visited)) {
                throw new IllegalArgumentException(name + " contains a cycle");
            }
        }
    }

    private static boolean cycle(
            String node,
            Map<String, List<String>> graph,
            Set<String> visiting,
            Set<String> visited
    ) {
        if (visited.contains(node)) return false;
        if (!visiting.add(node)) return true;
        for (String next : graph.getOrDefault(node, List.of())) {
            if (cycle(next, graph, visiting, visited)) return true;
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }
}
