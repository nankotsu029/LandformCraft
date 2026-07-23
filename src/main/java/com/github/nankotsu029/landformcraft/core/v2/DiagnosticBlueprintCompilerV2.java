package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.core.v2.foundation.MeanderingRiverSubtypeBridgeV2;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.NamedSeedDeriverV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationException;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.beach.SandyBeachGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.coast.beach.SandyBeachPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater.BreakwaterGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater.BreakwaterHarborPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.harbor.HarborBasinGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.coast.harbor.HarborBasinPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.cape.RockyCapeGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.coast.cape.RockyCapePlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionException;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.geology.GeologyFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.climate.ClimateFieldModulesV2;
import com.github.nankotsu029.landformcraft.generator.v2.environment.water.WaterConditionFieldModulesV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta.DeltaGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta.DeltaGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta.DeltaPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta.HydrologyDeltaModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.HydrologyTidalModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.TidalChannelGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.TidalChannelPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.TidalGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.HydrologyLakeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.LakeGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.LakePlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationException;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.HydrologyRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.RiverConfluenceValidatorV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.RiverGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.HydrologyWaterfallModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.WaterfallGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.WaterfallPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.CanyonGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.CanyonPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.LandformCanyonModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.FjordGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.FjordGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.FjordPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.LandformFjordModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove.LandformMangroveModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove.MangroveGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove.MangroveGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove.MangrovePlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.reef.CoralReefGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.landform.reef.CoralReefGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.reef.CoralReefPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.reef.LandformCoralReefModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mountain.LandformMountainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mountain.MountainGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mountain.MountainGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.mountain.MountainPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic.LandformVolcanicModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic.VolcanicGenerationException;
import com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic.VolcanicGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic.VolcanicPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.BreakwaterHarborPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.DiagnosticIssueV2;
import com.github.nankotsu029.landformcraft.model.v2.DeltaPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.FjordPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoralReefPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MangroveWetlandPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TidalChannelPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MountainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.SandyBeachPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.HarborBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.RockyCapePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalTransitionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.ValidationTargetV2;
import com.github.nankotsu029.landformcraft.model.v2.WaterfallPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** V2 compiler through the V2-4-05 water-condition field contract; it never publishes Release artifacts. */
public final class DiagnosticBlueprintCompilerV2 {
    public static final String COMPILER_VERSION = "2.0.0-v2-4-05-water-condition";
    // Keep the completed V2-2-06 feature seed namespace stable. The transition kernel has its own
    // version in CoastalTransitionCompositorV2 and does not perturb prior feature-local seeds.
    public static final String GENERATOR_VERSION = "v2-rocky-cape-fixed-v1";
    public static final String SEED_NAMESPACE = "terrain.v2.feature";

    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();
    private final DiagnosticGateContractV2 gateContract = DiagnosticGateContractV2.builtIn();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final SandyBeachPlanCompilerV2 sandyBeachCompiler = new SandyBeachPlanCompilerV2();
    private final MeanderingRiverPlanCompilerV2 riverCompiler = new MeanderingRiverPlanCompilerV2();
    private final LakePlanCompilerV2 lakeCompiler = new LakePlanCompilerV2();
    private final CanyonPlanCompilerV2 canyonCompiler = new CanyonPlanCompilerV2();
    private final WaterfallPlanCompilerV2 waterfallCompiler = new WaterfallPlanCompilerV2();
    private final DeltaPlanCompilerV2 deltaCompiler = new DeltaPlanCompilerV2();
    private final TidalChannelPlanCompilerV2 tidalCompiler = new TidalChannelPlanCompilerV2();
    private final FjordPlanCompilerV2 fjordCompiler = new FjordPlanCompilerV2();
    private final MangrovePlanCompilerV2 mangroveCompiler = new MangrovePlanCompilerV2();
    private final CoralReefPlanCompilerV2 coralReefCompiler = new CoralReefPlanCompilerV2();
    private final MountainPlanCompilerV2 mountainCompiler = new MountainPlanCompilerV2();
    private final VolcanicPlanCompilerV2 volcanicCompiler = new VolcanicPlanCompilerV2();
    private final HarborBasinPlanCompilerV2 harborBasinCompiler = new HarborBasinPlanCompilerV2();
    private final BreakwaterHarborPlanCompilerV2 breakwaterCompiler = new BreakwaterHarborPlanCompilerV2();
    private final RockyCapePlanCompilerV2 rockyCapeCompiler = new RockyCapePlanCompilerV2();
    private final CoastalTransitionPlanCompilerV2 coastalTransitionCompiler =
            new CoastalTransitionPlanCompilerV2();
    private final HydrologyPlanCompilerV2 hydrologyCompiler = new HydrologyPlanCompilerV2();
    private final GeologyPlanCompilerV2 geologyCompiler = new GeologyPlanCompilerV2();
    private final LithologyPlanCompilerV2 lithologyCompiler = new LithologyPlanCompilerV2();
    private final StrataPlanCompilerV2 strataCompiler = new StrataPlanCompilerV2();
    private final ClimatePlanCompilerV2 climateCompiler = new ClimatePlanCompilerV2();
    private final WaterConditionPlanCompilerV2 waterConditionCompiler = new WaterConditionPlanCompilerV2();
    private final HydrologyReconciliationPlanCompilerV2 hydrologyReconciliationCompiler =
            new HydrologyReconciliationPlanCompilerV2();

    public WorldBlueprintV2 compile(DiagnosticCompileRequestV2 request, TerrainIntentV2 intent) {
        if (!request.requestId().equals(intent.intentId())) {
            throw new DiagnosticCompilationException("v2.request-intent-id", "requestId and intentId must match");
        }
        List<ModuleDescriptorV2> modules = catalog.modules();
        List<WorldBlueprintV2.StageDescriptor> stages = catalog.stages();
        List<WorldBlueprintV2.FieldDescriptor> fields = fields(request);
        WorldBlueprintV2.Bounds blueprintBounds = new WorldBlueprintV2.Bounds(
                request.bounds().width(), request.bounds().length(), request.bounds().minY(),
                request.bounds().maxY(), request.bounds().waterLevel());
        HydrologyPlanV2 hydrologyPlan = hydrologyCompiler.compile(blueprintBounds);
        GeologyPlanV2 geologyPlan = geologyCompiler.compile(
                blueprintBounds, request.tileSize(), request.globalSeed(), hydrologyPlan.fixedPriors());
        LithologyPlanV2 lithologyPlan = lithologyCompiler.compile(geologyPlan);
        StrataPlanV2 strataPlan = strataCompiler.compile(geologyPlan, lithologyPlan);
        ClimatePlanV2 climatePlan;
        try {
            climatePlan = climateCompiler.compile(
                    blueprintBounds, request.tileSize(), request.globalSeed(), hydrologyPlan,
                    ClimatePlanCompilerV2.requirePreset(intent.environment().climatePreset()));
        } catch (IllegalArgumentException exception) {
            throw new DiagnosticCompilationException("v2.unknown-climate", exception.getMessage());
        }
        WaterConditionPlanV2 waterConditionPlan = waterConditionCompiler.compile(
                blueprintBounds, request.tileSize(), request.globalSeed(), hydrologyPlan, climatePlan);
        preflight(request, intent, modules.size(), fields.size(), hydrologyPlan.budget(), geologyPlan.budget(),
                lithologyPlan.budget(), lithologyPlan.catalog().budget(), strataPlan.budget(), climatePlan.budget(),
                waterConditionPlan.budget());

        List<ValidationTargetV2> targets = new ArrayList<>(compileTargets(intent));
        List<WorldBlueprintV2.FeaturePlan> plans = new ArrayList<>();
        List<CoastalFeaturePlanV2> coastalPlans = new ArrayList<>();
        List<SandyBeachPlanV2> sandyBeachPlans = new ArrayList<>();
        List<HarborBasinPlanV2> harborBasinPlans = new ArrayList<>();
        List<BreakwaterHarborPlanV2> breakwaterHarborPlans = new ArrayList<>();
        List<RockyCapePlanV2> rockyCapePlans = new ArrayList<>();
        List<CoastalTransitionPlanV2> coastalTransitionPlans = new ArrayList<>();
        List<MeanderingRiverPlanV2> meanderingRiverPlans = new ArrayList<>();
        List<LakePlanV2> lakePlans = new ArrayList<>();
        List<CanyonPlanV2> canyonPlans = new ArrayList<>();
        List<WaterfallPlanV2> waterfallPlans = new ArrayList<>();
        List<DeltaPlanV2> deltaPlans = new ArrayList<>();
        List<TidalChannelPlanV2> tidalChannelPlans = new ArrayList<>();
        List<MangroveWetlandPlanV2> mangroveWetlandPlans = new ArrayList<>();
        List<CoralReefPlanV2> coralReefPlans = new ArrayList<>();
        List<FjordPlanV2> fjordPlans = new ArrayList<>();
        List<MountainPlanV2> mountainPlans = new ArrayList<>();
        List<VolcanicPlanV2> volcanicPlans = new ArrayList<>();
        List<DiagnosticIssueV2> issues = new ArrayList<>();
        int issueSequence = 0;
        for (TerrainIntentV2.Feature feature : intent.features()) {
            ModuleDescriptorV2 module;
            try {
                module = catalog.requireFor(feature.kind());
            } catch (IllegalArgumentException exception) {
                throw new DiagnosticCompilationException("v2.unknown-module", exception.getMessage());
            }
            List<String> relationIds = intent.relations().stream()
                    .filter(relation -> referencesFeature(relation, feature.id()))
                    .map(TerrainIntentV2.Relation::id).sorted().toList();
            List<String> targetIds = targets.stream()
                    .filter(target -> target.featureIds().contains(feature.id()))
                    .map(ValidationTargetV2::targetId).sorted().toList();
            long seed = NamedSeedDeriverV2.derive(
                    request.globalSeed(), module.moduleId(), module.moduleVersion(), feature.id(),
                    SEED_NAMESPACE, GENERATOR_VERSION);
            plans.add(new WorldBlueprintV2.FeaturePlan(
                    feature.id(), feature.kind(), feature.priority(), module.moduleId(), module.moduleVersion(),
                    feature.geometry().type(),
                    codec.geometryChecksum(feature.geometry()), seed, SEED_NAMESPACE, relationIds,
                    module.requiredFields(), module.providedFields(), targetIds
            ));
            if (feature.kind() == TerrainIntentV2.FeatureKind.MEANDERING_RIVER) {
                try {
                    meanderingRiverPlans.add(riverCompiler.compile(
                            feature,
                            new WorldBlueprintV2.Bounds(
                                    request.bounds().width(), request.bounds().length(),
                                    request.bounds().minY(), request.bounds().maxY(),
                                    request.bounds().waterLevel()),
                            codec.geometryChecksum(feature.geometry())));
                } catch (RiverGenerationException exception) {
                    throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
                }
            }
            // V2-15-10 / ADR 0039 Candidate A: RIVER wires the offline hydrology-plan pipeline by
            // compiling through the same MeanderingRiverPlanCompilerV2 shape as MEANDERING_RIVER, via a
            // synthetic MEANDERING_RIVER-kind feature carrying bridged parameters. The original
            // FeaturePlan above keeps FeatureKind.RIVER; only the compiled MeanderingRiverPlanV2 (and its
            // downstream reconciliation/validation) uses the bridged shape. MeanderingRiverPlanCompilerV2's
            // math and the MEANDERING_RIVER kind's own contract are unchanged.
            if (feature.kind() == TerrainIntentV2.FeatureKind.RIVER) {
                TerrainIntentV2.RiverParameters riverParameters =
                        (TerrainIntentV2.RiverParameters) feature.parameters();
                TerrainIntentV2.Feature meanderingFeature = new TerrainIntentV2.Feature(
                        feature.id(),
                        TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                        feature.geometry(),
                        MeanderingRiverSubtypeBridgeV2.meanderingParametersFor(
                                riverParameters, TerrainIntentV2.RiverVariant.MEANDERING_RIVER),
                        feature.priority(),
                        feature.provenance());
                try {
                    meanderingRiverPlans.add(riverCompiler.compile(
                            meanderingFeature,
                            new WorldBlueprintV2.Bounds(
                                    request.bounds().width(), request.bounds().length(),
                                    request.bounds().minY(), request.bounds().maxY(),
                                    request.bounds().waterLevel()),
                            codec.geometryChecksum(feature.geometry())));
                } catch (RiverGenerationException exception) {
                    throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
                }
            }
            if (feature.kind() == TerrainIntentV2.FeatureKind.LAKE) {
                try {
                    lakePlans.add(lakeCompiler.compile(
                            feature,
                            intent,
                            new WorldBlueprintV2.Bounds(
                                    request.bounds().width(), request.bounds().length(),
                                    request.bounds().minY(), request.bounds().maxY(),
                                    request.bounds().waterLevel()),
                            codec.geometryChecksum(feature.geometry())));
                } catch (LakeGenerationException exception) {
                    throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
                }
            }
            if (CoastalFeaturePlanV2.isFoundationKind(feature.kind())) {
                try {
                    CoastalFeaturePlanV2 coastalPlan = catalog.requireCoastalFor(feature.kind()).compileFoundation(
                            feature, intent, request.bounds().width(), request.bounds().length(),
                            codec.geometryChecksum(feature.geometry()));
                    coastalPlans.add(coastalPlan);
                    if (feature.kind() == TerrainIntentV2.FeatureKind.SANDY_BEACH) {
                        sandyBeachPlans.add(sandyBeachCompiler.compile(feature, coastalPlan,
                                new WorldBlueprintV2.Bounds(
                                        request.bounds().width(), request.bounds().length(),
                                        request.bounds().minY(), request.bounds().maxY(),
                                        request.bounds().waterLevel())));
                    } else if (feature.kind() == TerrainIntentV2.FeatureKind.HARBOR_BASIN) {
                        harborBasinPlans.add(harborBasinCompiler.compile(
                                feature, intent, coastalPlan,
                                new WorldBlueprintV2.Bounds(
                                        request.bounds().width(), request.bounds().length(),
                                        request.bounds().minY(), request.bounds().maxY(),
                                        request.bounds().waterLevel())));
                    } else if (feature.kind() == TerrainIntentV2.FeatureKind.ROCKY_CAPE) {
                        rockyCapePlans.add(rockyCapeCompiler.compile(
                                feature, coastalPlan,
                                new WorldBlueprintV2.Bounds(
                                        request.bounds().width(), request.bounds().length(),
                                        request.bounds().minY(), request.bounds().maxY(),
                                        request.bounds().waterLevel()),
                                seed));
                    }
                } catch (CoastalFoundationException exception) {
                    throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
                } catch (SandyBeachGenerationException exception) {
                    throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
                } catch (HarborBasinGenerationException exception) {
                    throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
                } catch (RockyCapeGenerationException exception) {
                    throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
                }
            }

            // V2-18-01: production-connected kinds have a real export route, so the blanket
            // unsupported-capability ERROR (audit item 4) no longer applies to them. Kinds without a
            // production route still receive it as an honest "no production export capability" signal.
            if (!gateContract.isProductionConnected(feature.kind())) {
                issues.add(issue(
                        "unsupported-" + issueSequence++, "v2.unsupported-capability",
                        DiagnosticIssueV2.Severity.ERROR, feature, module, "v2.unsupported-capability"));
            }
            if (!catalog.hasValidatorCapability(feature.kind())) {
                issues.add(issue("validator-" + issueSequence++, "v2.missing-validator-capability",
                        DiagnosticIssueV2.Severity.ERROR, feature, module, "v2.missing-validator-capability"));
            }
            if (!catalog.hasPreviewCapability(feature.kind())) {
                issues.add(issue("preview-" + issueSequence++, "v2.missing-preview-capability",
                        DiagnosticIssueV2.Severity.ERROR, feature, module, "v2.missing-preview-capability"));
            }
        }
        for (TerrainIntentV2.Feature feature : intent.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR) continue;
            CoastalFeaturePlanV2 coastalPlan = coastalPlans.stream()
                    .filter(candidate -> candidate.featureId().equals(feature.id()))
                    .findFirst().orElseThrow();
            List<HarborBasinPlanV2> connectedBasins = harborBasinPlans.stream()
                    .filter(basin -> intent.relations().stream().anyMatch(relation ->
                            relation.strength() == TerrainIntentV2.Strength.HARD
                                    && ((relation.kind() == TerrainIntentV2.RelationKind.ENCLOSES
                                    && relation.from().equals("feature:" + feature.id())
                                    && relation.to().equals("feature:" + basin.featureId()))
                                    || (relation.kind() == TerrainIntentV2.RelationKind.ENCLOSED_BY
                                    && relation.from().equals("feature:" + basin.featureId())
                                    && relation.to().equals("feature:" + feature.id())))))
                    .toList();
            if (connectedBasins.size() != 1) {
                throw new DiagnosticCompilationException(
                        "v2.breakwater-basin-relation",
                        "breakwater requires exactly one compiled HARD enclosed harbor basin");
            }
            try {
                breakwaterHarborPlans.add(breakwaterCompiler.compile(
                        feature, intent, coastalPlan, connectedBasins.getFirst(),
                        new WorldBlueprintV2.Bounds(
                                request.bounds().width(), request.bounds().length(),
                                request.bounds().minY(), request.bounds().maxY(),
                                request.bounds().waterLevel())));
            } catch (BreakwaterGenerationException exception) {
                throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
            }
        }
        if (!coastalPlans.isEmpty()) {
            try {
                coastalTransitionPlans.add(coastalTransitionCompiler.compile(intent));
            } catch (CoastalTransitionException exception) {
                throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
            }
        }
        try {
            RiverConfluenceValidatorV2.requireConfluenceDischargeConsistent(intent, meanderingRiverPlans);
        } catch (RiverGenerationException exception) {
            throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
        }
        for (TerrainIntentV2.Feature feature : intent.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.CANYON) continue;
            try {
                canyonPlans.add(canyonCompiler.compile(
                        feature,
                        intent,
                        meanderingRiverPlans,
                        new WorldBlueprintV2.Bounds(
                                request.bounds().width(), request.bounds().length(),
                                request.bounds().minY(), request.bounds().maxY(),
                                request.bounds().waterLevel()),
                        codec.geometryChecksum(feature.geometry())));
            } catch (CanyonGenerationException exception) {
                throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
            }
        }
        for (TerrainIntentV2.Feature feature : intent.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.WATERFALL) continue;
            try {
                waterfallPlans.add(waterfallCompiler.compile(
                        feature,
                        intent,
                        meanderingRiverPlans,
                        new WorldBlueprintV2.Bounds(
                                request.bounds().width(), request.bounds().length(),
                                request.bounds().minY(), request.bounds().maxY(),
                                request.bounds().waterLevel()),
                        codec.geometryChecksum(feature.geometry())));
            } catch (WaterfallGenerationException exception) {
                throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
            }
        }
        for (TerrainIntentV2.Feature feature : intent.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.DELTA) continue;
            try {
                deltaPlans.add(deltaCompiler.compile(
                        feature,
                        intent,
                        meanderingRiverPlans,
                        new WorldBlueprintV2.Bounds(
                                request.bounds().width(), request.bounds().length(),
                                request.bounds().minY(), request.bounds().maxY(),
                                request.bounds().waterLevel()),
                        codec.geometryChecksum(feature.geometry())));
            } catch (DeltaGenerationException exception) {
                throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
            }
        }
        for (TerrainIntentV2.Feature feature : intent.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK) continue;
            try {
                tidalChannelPlans.add(tidalCompiler.compile(
                        feature,
                        intent,
                        new WorldBlueprintV2.Bounds(
                                request.bounds().width(), request.bounds().length(),
                                request.bounds().minY(), request.bounds().maxY(),
                                request.bounds().waterLevel()),
                        codec.geometryChecksum(feature.geometry())));
            } catch (TidalGenerationException exception) {
                throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
            }
        }
        for (TerrainIntentV2.Feature feature : intent.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.MANGROVE_WETLAND) continue;
            try {
                mangroveWetlandPlans.add(mangroveCompiler.compile(
                        feature, intent, blueprintBounds, codec.geometryChecksum(feature.geometry())));
            } catch (MangroveGenerationException exception) {
                throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
            }
        }
        for (TerrainIntentV2.Feature feature : intent.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.CORAL_REEF) continue;
            try {
                coralReefPlans.add(coralReefCompiler.compile(
                        feature, intent, blueprintBounds, codec.geometryChecksum(feature.geometry())));
            } catch (CoralReefGenerationException exception) {
                throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
            }
        }
        for (TerrainIntentV2.Feature feature : intent.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.FJORD) continue;
            try {
                fjordPlans.add(fjordCompiler.compile(feature, intent, blueprintBounds,
                        codec.geometryChecksum(feature.geometry())));
            } catch (FjordGenerationException exception) {
                throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
            }
        }
        for (TerrainIntentV2.Feature feature : intent.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE
                    && feature.kind() != TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE) {
                continue;
            }
            try {
                mountainPlans.add(mountainCompiler.compile(
                        feature, intent, blueprintBounds, codec.geometryChecksum(feature.geometry())));
            } catch (MountainGenerationException exception) {
                throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
            }
        }
        for (TerrainIntentV2.Feature feature : intent.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO) continue;
            try {
                volcanicPlans.add(volcanicCompiler.compile(
                        feature, intent, blueprintBounds, codec.geometryChecksum(feature.geometry())));
            } catch (VolcanicGenerationException exception) {
                throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
            }
        }
        preflightDeltaPlans(request, hydrologyPlan.budget(), geologyPlan.budget(), lithologyPlan.budget(),
                strataPlan.budget(), climatePlan.budget(), waterConditionPlan.budget(), deltaPlans);
        preflightTidalPlans(request, hydrologyPlan.budget(), geologyPlan.budget(), lithologyPlan.budget(),
                strataPlan.budget(), climatePlan.budget(), waterConditionPlan.budget(), tidalChannelPlans);
        preflightMangrovePlans(request, hydrologyPlan.budget(), geologyPlan.budget(), lithologyPlan.budget(),
                strataPlan.budget(), climatePlan.budget(), waterConditionPlan.budget(), mangroveWetlandPlans);
        preflightCoralReefPlans(request, hydrologyPlan.budget(), geologyPlan.budget(), lithologyPlan.budget(),
                strataPlan.budget(), climatePlan.budget(), waterConditionPlan.budget(), coralReefPlans);
        preflightFjordPlans(request, hydrologyPlan.budget(), geologyPlan.budget(), lithologyPlan.budget(),
                strataPlan.budget(), climatePlan.budget(), waterConditionPlan.budget(), fjordPlans);
        preflightMountainPlans(request, hydrologyPlan.budget(), geologyPlan.budget(), lithologyPlan.budget(),
                strataPlan.budget(), climatePlan.budget(), waterConditionPlan.budget(), mountainPlans);
        preflightVolcanicPlans(request, hydrologyPlan.budget(), geologyPlan.budget(), lithologyPlan.budget(),
                strataPlan.budget(), climatePlan.budget(), waterConditionPlan.budget(), volcanicPlans);
        HydrologyReconciliationPlanV2 hydrologyReconciliationPlan;
        try {
            HydrologyReconciliationPlanV2 draftReconciliationPlan = hydrologyReconciliationCompiler.compile(
                    hydrologyPlan.canonicalChecksum(),
                    meanderingRiverPlans,
                    lakePlans,
                    deltaPlans,
                    tidalChannelPlans,
                    mangroveWetlandPlans,
                    coralReefPlans,
                    fjordPlans,
                    waterfallPlans,
                    request.budget().maximumCpuWorkUnits(),
                    request.budget().maximumResidentBytes(),
                    request.budget().maximumArtifactBytes());
            hydrologyReconciliationPlan = codec.sealHydrologyReconciliationPlan(draftReconciliationPlan);
        } catch (HydrologyReconciliationException exception) {
            throw new DiagnosticCompilationException(exception.ruleId(), exception.getMessage());
        }
        targets.addAll(coastalValidationTargets(
                sandyBeachPlans, harborBasinPlans, breakwaterHarborPlans, rockyCapePlans, coastalTransitionPlans));
        targets.addAll(hydrologyValidationTargets(
                meanderingRiverPlans, lakePlans, deltaPlans, tidalChannelPlans, mangroveWetlandPlans, fjordPlans,
                waterfallPlans, mountainPlans, volcanicPlans, hydrologyReconciliationPlan));
        plans = plans.stream().map(plan -> new WorldBlueprintV2.FeaturePlan(
                plan.featureId(), plan.kind(), plan.priority(), plan.moduleId(), plan.moduleVersion(),
                plan.geometryType(), plan.geometryChecksum(), plan.namedSeed(), plan.seedNamespace(),
                plan.relationIds(), plan.requiredFields(), plan.providedFields(), targets.stream()
                        .filter(target -> target.featureIds().contains(plan.featureId()))
                        .map(ValidationTargetV2::targetId).sorted().toList())).toList();
        for (TerrainIntentV2.ConstraintMapBinding map : intent.mapReferences()) {
            issues.add(new DiagnosticIssueV2(
                    "map-" + issueSequence++, "v2.unsupported-constraint-map", 1,
                    DiagnosticIssueV2.Severity.ERROR, map.strength(),
                    List.of(new DiagnosticIssueV2.Reference(DiagnosticIssueV2.ReferenceType.FIELD, map.id())),
                    List.of(), "v2.unsupported-constraint-map", List.of("diagnostic.constraint-map")));
        }
        for (TerrainIntentV2.StructureRequest structure : intent.structures()) {
            issues.add(new DiagnosticIssueV2(
                    "structure-" + issueSequence++, "v2.unsupported-structure-capability", 1,
                    DiagnosticIssueV2.Severity.ERROR, TerrainIntentV2.Strength.HARD,
                    List.of(new DiagnosticIssueV2.Reference(DiagnosticIssueV2.ReferenceType.FEATURE,
                            structure.preferredFeatureId())),
                    List.of(), "v2.unsupported-structure-capability", List.of("diagnostic.structures")));
        }

        List<WorldBlueprintV2.FieldOwnership> ownership = modules.stream()
                .flatMap(module -> module.fieldWrites().stream().map(write -> new WorldBlueprintV2.FieldOwnership(
                        write.fieldId(), module.moduleId(), write.mergeOperator())))
                .sorted(Comparator.comparing(WorldBlueprintV2.FieldOwnership::fieldId))
                .toList();
        int maximumHaloXZ = modules.stream().mapToInt(ModuleDescriptorV2::requiredHaloXZ).max().orElse(0);
        int maximumHaloY = modules.stream().mapToInt(ModuleDescriptorV2::requiredHaloY).max().orElse(0);
        WorldBlueprintV2 draft = new WorldBlueprintV2(
                new WorldBlueprintV2.Identity(
                        WorldBlueprintV2.VERSION, request.requestId(), request.sourceRequestChecksum(),
                        codec.terrainIntentChecksum(intent), COMPILER_VERSION, GENERATOR_VERSION),
                new WorldBlueprintV2.Space(
                        blueprintBounds,
                        intent.coordinateSystem(),
                        new WorldBlueprintV2.TilePolicy(request.tileSize(), maximumHaloXZ, maximumHaloY)),
                new WorldBlueprintV2.Determinism(
                        request.globalSeed(), SEED_NAMESPACE, NamedSeedDeriverV2.VERSION, CanonicalJsonV2.VERSION),
                modules, stages, fields, ownership, plans, coastalPlans, sandyBeachPlans,
                harborBasinPlans, breakwaterHarborPlans, rockyCapePlans, coastalTransitionPlans,
                meanderingRiverPlans, lakePlans, canyonPlans, waterfallPlans,
                deltaPlans, tidalChannelPlans, mangroveWetlandPlans, coralReefPlans, fjordPlans, mountainPlans, volcanicPlans, geologyPlan, lithologyPlan,
                strataPlan,
                climatePlan,
                waterConditionPlan,
                hydrologyPlan,
                hydrologyReconciliationPlan, targets,
                request.budget(), issues, "0".repeat(64));
        return codec.sealWorldBlueprint(draft);
    }

    private static long priorStageCpuWorkUnits(
            HydrologyPlanV2.GraphWorkBudget hydrologyBudget,
            GeologyPlanV2.ResourceBudget geologyBudget,
            LithologyPlanV2.ResourceBudget lithologyBudget,
            StrataPlanV2.ResourceBudget strataBudget,
            ClimatePlanV2.ResourceBudget climateBudget,
            WaterConditionPlanV2.ResourceBudget waterConditionBudget
    ) {
        return Math.addExact(Math.addExact(Math.addExact(Math.addExact(Math.addExact(
                hydrologyBudget.estimatedCpuWorkUnits(), geologyBudget.estimatedCpuWorkUnits()),
                lithologyBudget.estimatedCpuWorkUnits()), strataBudget.estimatedCpuWorkUnits()),
                climateBudget.estimatedCpuWorkUnits()), waterConditionBudget.estimatedCpuWorkUnits());
    }

    private static long priorStageResidentBytes(
            HydrologyPlanV2.GraphWorkBudget hydrologyBudget,
            GeologyPlanV2.ResourceBudget geologyBudget,
            LithologyPlanV2.ResourceBudget lithologyBudget,
            StrataPlanV2.ResourceBudget strataBudget,
            ClimatePlanV2.ResourceBudget climateBudget,
            WaterConditionPlanV2.ResourceBudget waterConditionBudget
    ) {
        return Math.addExact(Math.addExact(Math.addExact(Math.addExact(Math.addExact(Math.addExact(
                hydrologyBudget.estimatedResidentBytes(), geologyBudget.estimatedRetainedBytes()),
                geologyBudget.maximumWorkingBytes()), lithologyBudget.estimatedRetainedBytes()),
                strataBudget.estimatedRetainedBytes()),
                Math.addExact(climateBudget.estimatedRetainedBytes(), climateBudget.maximumWorkingBytes())),
                Math.addExact(waterConditionBudget.estimatedRetainedBytes(), waterConditionBudget.maximumWorkingBytes()));
    }

    private static void preflightDeltaPlans(
            DiagnosticCompileRequestV2 request,
            HydrologyPlanV2.GraphWorkBudget hydrologyBudget,
            GeologyPlanV2.ResourceBudget geologyBudget,
            LithologyPlanV2.ResourceBudget lithologyBudget,
            StrataPlanV2.ResourceBudget strataBudget,
            ClimatePlanV2.ResourceBudget climateBudget,
            WaterConditionPlanV2.ResourceBudget waterConditionBudget,
            List<DeltaPlanV2> deltaPlans
    ) {
        try {
            long deltaCpu = 0L;
            long deltaResident = 0L;
            for (DeltaPlanV2 plan : deltaPlans) {
                deltaCpu = Math.addExact(deltaCpu, plan.estimatedRasterWorkUnits());
                int windowWidth = Math.min(plan.width(), Math.addExact(request.tileSize(),
                        Math.multiplyExact(plan.supportRadiusXZ(), 2)));
                int windowLength = Math.min(plan.length(), Math.addExact(request.tileSize(),
                        Math.multiplyExact(plan.supportRadiusXZ(), 2)));
                deltaResident = Math.addExact(deltaResident,
                        DeltaGeneratorV2.estimateWindowRetainedBytes(windowWidth, windowLength));
            }
            if (Math.addExact(priorStageCpuWorkUnits(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget),
                    deltaCpu) > request.budget().maximumCpuWorkUnits()
                    || Math.addExact(priorStageResidentBytes(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), deltaResident)
                    > request.budget().maximumResidentBytes()) {
                throw new DiagnosticCompilationException(
                        "v2.delta-budget", "delta stage exceeds declared CPU/resident budget");
            }
        } catch (ArithmeticException exception) {
            throw new DiagnosticCompilationException("v2.delta-budget", "delta budget arithmetic overflow");
        }
    }

    private static void preflightTidalPlans(
            DiagnosticCompileRequestV2 request,
            HydrologyPlanV2.GraphWorkBudget hydrologyBudget,
            GeologyPlanV2.ResourceBudget geologyBudget,
            LithologyPlanV2.ResourceBudget lithologyBudget,
            StrataPlanV2.ResourceBudget strataBudget,
            ClimatePlanV2.ResourceBudget climateBudget,
            WaterConditionPlanV2.ResourceBudget waterConditionBudget,
            List<TidalChannelPlanV2> tidalChannelPlans
    ) {
        try {
            long tidalCpu = 0L;
            long tidalResident = 0L;
            for (TidalChannelPlanV2 plan : tidalChannelPlans) {
                tidalCpu = Math.addExact(tidalCpu, plan.estimatedRasterWorkUnits());
                int windowWidth = Math.min(plan.width(), Math.addExact(request.tileSize(),
                        Math.multiplyExact(plan.supportRadiusXZ(), 2)));
                int windowLength = Math.min(plan.length(), Math.addExact(request.tileSize(),
                        Math.multiplyExact(plan.supportRadiusXZ(), 2)));
                tidalResident = Math.addExact(tidalResident,
                        TidalChannelGeneratorV2.estimateWindowRetainedBytes(windowWidth, windowLength));
            }
            if (Math.addExact(priorStageCpuWorkUnits(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget),
                    tidalCpu) > request.budget().maximumCpuWorkUnits()
                    || Math.addExact(priorStageResidentBytes(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), tidalResident)
                    > request.budget().maximumResidentBytes()) {
                throw new DiagnosticCompilationException(
                        "v2.tidal-budget", "tidal stage exceeds declared CPU/resident budget");
            }
        } catch (ArithmeticException exception) {
            throw new DiagnosticCompilationException("v2.tidal-budget", "tidal budget arithmetic overflow");
        }
    }

    private static void preflightMangrovePlans(
            DiagnosticCompileRequestV2 request,
            HydrologyPlanV2.GraphWorkBudget hydrologyBudget,
            GeologyPlanV2.ResourceBudget geologyBudget,
            LithologyPlanV2.ResourceBudget lithologyBudget,
            StrataPlanV2.ResourceBudget strataBudget,
            ClimatePlanV2.ResourceBudget climateBudget,
            WaterConditionPlanV2.ResourceBudget waterConditionBudget,
            List<MangroveWetlandPlanV2> mangroveWetlandPlans
    ) {
        try {
            long cpu = 0L;
            long resident = 0L;
            for (MangroveWetlandPlanV2 plan : mangroveWetlandPlans) {
                cpu = Math.addExact(cpu, plan.estimatedRasterWorkUnits());
                resident = Math.addExact(resident, MangroveGeneratorV2.estimateWindowRetainedBytes(
                        Math.min(plan.width(), request.tileSize() + 2 * plan.supportRadiusXZ()),
                        Math.min(plan.length(), request.tileSize() + 2 * plan.supportRadiusXZ())));
            }
            if (Math.addExact(priorStageCpuWorkUnits(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), cpu)
                    > request.budget().maximumCpuWorkUnits()
                    || Math.addExact(priorStageResidentBytes(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), resident)
                    > request.budget().maximumResidentBytes()) {
                throw new DiagnosticCompilationException("v2.mangrove-budget", "mangrove CPU or resident-memory budget exceeded");
            }
        } catch (ArithmeticException exception) {
            throw new DiagnosticCompilationException("v2.mangrove-budget", "mangrove budget arithmetic overflow");
        }
    }

    private static void preflightCoralReefPlans(
            DiagnosticCompileRequestV2 request,
            HydrologyPlanV2.GraphWorkBudget hydrologyBudget,
            GeologyPlanV2.ResourceBudget geologyBudget,
            LithologyPlanV2.ResourceBudget lithologyBudget,
            StrataPlanV2.ResourceBudget strataBudget,
            ClimatePlanV2.ResourceBudget climateBudget,
            WaterConditionPlanV2.ResourceBudget waterConditionBudget,
            List<CoralReefPlanV2> coralReefPlans
    ) {
        try {
            long cpu = 0L;
            long resident = 0L;
            for (CoralReefPlanV2 plan : coralReefPlans) {
                cpu = Math.addExact(cpu, plan.estimatedRasterWorkUnits());
                resident = Math.addExact(resident, CoralReefGeneratorV2.estimateWindowRetainedBytes(
                        Math.min(plan.width(), request.tileSize() + 2 * plan.supportRadiusXZ()),
                        Math.min(plan.length(), request.tileSize() + 2 * plan.supportRadiusXZ())));
            }
            if (Math.addExact(priorStageCpuWorkUnits(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), cpu)
                    > request.budget().maximumCpuWorkUnits()
                    || Math.addExact(priorStageResidentBytes(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), resident)
                    > request.budget().maximumResidentBytes()) {
                throw new DiagnosticCompilationException("v2.reef-budget", "coral reef CPU or resident-memory budget exceeded");
            }
        } catch (ArithmeticException exception) {
            throw new DiagnosticCompilationException("v2.reef-budget", "coral reef budget arithmetic overflow");
        }
    }

    private static void preflightFjordPlans(
            DiagnosticCompileRequestV2 request,
            HydrologyPlanV2.GraphWorkBudget hydrologyBudget,
            GeologyPlanV2.ResourceBudget geologyBudget,
            LithologyPlanV2.ResourceBudget lithologyBudget,
            StrataPlanV2.ResourceBudget strataBudget,
            ClimatePlanV2.ResourceBudget climateBudget,
            WaterConditionPlanV2.ResourceBudget waterConditionBudget,
            List<FjordPlanV2> fjordPlans
    ) {
        try {
            long cpu = 0L, resident = 0L;
            for (FjordPlanV2 plan : fjordPlans) {
                cpu = Math.addExact(cpu, plan.estimatedRasterWorkUnits());
                resident = Math.addExact(resident, FjordGeneratorV2.estimateWindowRetainedBytes(
                        Math.min(plan.width(), request.tileSize() + 2 * plan.supportRadiusXZ()),
                        Math.min(plan.length(), request.tileSize() + 2 * plan.supportRadiusXZ())));
            }
            if (Math.addExact(priorStageCpuWorkUnits(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), cpu)
                    > request.budget().maximumCpuWorkUnits()
                    || Math.addExact(priorStageResidentBytes(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), resident)
                    > request.budget().maximumResidentBytes()) {
                throw new DiagnosticCompilationException("v2.fjord-budget", "fjord CPU or resident-memory budget exceeded");
            }
        } catch (ArithmeticException exception) {
            throw new DiagnosticCompilationException("v2.fjord-budget", "fjord budget arithmetic overflow");
        }
    }

    private static void preflightMountainPlans(
            DiagnosticCompileRequestV2 request,
            HydrologyPlanV2.GraphWorkBudget hydrologyBudget,
            GeologyPlanV2.ResourceBudget geologyBudget,
            LithologyPlanV2.ResourceBudget lithologyBudget,
            StrataPlanV2.ResourceBudget strataBudget,
            ClimatePlanV2.ResourceBudget climateBudget,
            WaterConditionPlanV2.ResourceBudget waterConditionBudget,
            List<MountainPlanV2> mountainPlans
    ) {
        try {
            long cpu = 0L;
            long resident = 0L;
            for (MountainPlanV2 plan : mountainPlans) {
                cpu = Math.addExact(cpu, plan.estimatedRasterWorkUnits());
                int windowWidth = Math.min(plan.width(), Math.addExact(
                        request.tileSize(), Math.multiplyExact(plan.supportRadiusXZ(), 2)));
                int windowLength = Math.min(plan.length(), Math.addExact(
                        request.tileSize(), Math.multiplyExact(plan.supportRadiusXZ(), 2)));
                resident = Math.addExact(
                        resident, MountainGeneratorV2.estimateWindowRetainedBytes(windowWidth, windowLength));
            }
            if (Math.addExact(priorStageCpuWorkUnits(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), cpu)
                    > request.budget().maximumCpuWorkUnits()
                    || Math.addExact(priorStageResidentBytes(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), resident)
                    > request.budget().maximumResidentBytes()) {
                throw new DiagnosticCompilationException(
                        "v2.mountain-budget", "mountain CPU or resident-memory budget exceeded");
            }
        } catch (ArithmeticException exception) {
            throw new DiagnosticCompilationException("v2.mountain-budget", "mountain budget arithmetic overflow");
        }
    }

    private static void preflightVolcanicPlans(
            DiagnosticCompileRequestV2 request,
            HydrologyPlanV2.GraphWorkBudget hydrologyBudget,
            GeologyPlanV2.ResourceBudget geologyBudget,
            LithologyPlanV2.ResourceBudget lithologyBudget,
            StrataPlanV2.ResourceBudget strataBudget,
            ClimatePlanV2.ResourceBudget climateBudget,
            WaterConditionPlanV2.ResourceBudget waterConditionBudget,
            List<VolcanicPlanV2> volcanicPlans
    ) {
        try {
            long cpu = 0L;
            long resident = 0L;
            for (VolcanicPlanV2 plan : volcanicPlans) {
                cpu = Math.addExact(cpu, plan.estimatedRasterWorkUnits());
                int windowWidth = Math.min(plan.width(), Math.addExact(
                        request.tileSize(), Math.multiplyExact(plan.supportRadiusXZ(), 2)));
                int windowLength = Math.min(plan.length(), Math.addExact(
                        request.tileSize(), Math.multiplyExact(plan.supportRadiusXZ(), 2)));
                resident = Math.addExact(
                        resident, VolcanicGeneratorV2.estimateWindowRetainedBytes(windowWidth, windowLength));
            }
            if (Math.addExact(priorStageCpuWorkUnits(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), cpu)
                    > request.budget().maximumCpuWorkUnits()
                    || Math.addExact(priorStageResidentBytes(
                    hydrologyBudget, geologyBudget, lithologyBudget, strataBudget, climateBudget, waterConditionBudget), resident)
                    > request.budget().maximumResidentBytes()) {
                throw new DiagnosticCompilationException(
                        "v2.volcanic-budget", "volcanic CPU or resident-memory budget exceeded");
            }
        } catch (ArithmeticException exception) {
            throw new DiagnosticCompilationException("v2.volcanic-budget", "volcanic budget arithmetic overflow");
        }
    }

    private void preflight(
            DiagnosticCompileRequestV2 request,
            TerrainIntentV2 intent,
            int moduleCount,
            int fieldCount,
            HydrologyPlanV2.GraphWorkBudget hydrologyBudget,
            GeologyPlanV2.ResourceBudget geologyBudget,
            LithologyPlanV2.ResourceBudget lithologyBudget,
            LithologyPlanV2.CatalogBudget catalogBudget,
            StrataPlanV2.ResourceBudget strataBudget,
            ClimatePlanV2.ResourceBudget climateBudget,
            WaterConditionPlanV2.ResourceBudget waterConditionBudget
    ) {
        WorldBlueprintV2.ResourceBudget budget = request.budget();
        int geometryPoints = intent.features().stream().mapToInt(feature -> geometryPointCount(feature.geometry())).sum();
        long residentEstimate = Math.addExact(Math.multiplyExact((long) geometryPoints, 64L),
                Math.multiplyExact((long) intent.features().size(), 4_096L));
        int transitionWindowWidth = Math.min(request.bounds().width(), request.tileSize() + 64);
        int transitionWindowLength = Math.min(request.bounds().length(), request.tileSize() + 64);
        residentEstimate = Math.addExact(residentEstimate, Math.multiplyExact(
                Math.multiplyExact((long) transitionWindowWidth, transitionWindowLength), 20L));
        residentEstimate = Math.addExact(residentEstimate, hydrologyBudget.estimatedResidentBytes());
        residentEstimate = Math.addExact(residentEstimate, Math.addExact(
                geologyBudget.estimatedRetainedBytes(), geologyBudget.maximumWorkingBytes()));
        residentEstimate = Math.addExact(residentEstimate, Math.addExact(
                lithologyBudget.estimatedRetainedBytes(), catalogBudget.maximumCanonicalBytes()));
        residentEstimate = Math.addExact(residentEstimate, strataBudget.estimatedRetainedBytes());
        residentEstimate = Math.addExact(residentEstimate, Math.addExact(
                climateBudget.estimatedRetainedBytes(), climateBudget.maximumWorkingBytes()));
        residentEstimate = Math.addExact(residentEstimate, Math.addExact(
                waterConditionBudget.estimatedRetainedBytes(), waterConditionBudget.maximumWorkingBytes()));
        long cpuEstimate = Math.addExact(
                Math.addExact(geometryPoints, Math.multiplyExact((long) intent.features().size(), 1_000L)),
                hydrologyBudget.estimatedCpuWorkUnits());
        cpuEstimate = Math.addExact(cpuEstimate, geologyBudget.estimatedCpuWorkUnits());
        cpuEstimate = Math.addExact(cpuEstimate, lithologyBudget.estimatedCpuWorkUnits());
        cpuEstimate = Math.addExact(cpuEstimate, strataBudget.estimatedCpuWorkUnits());
        cpuEstimate = Math.addExact(cpuEstimate, climateBudget.estimatedCpuWorkUnits());
        cpuEstimate = Math.addExact(cpuEstimate, waterConditionBudget.estimatedCpuWorkUnits());
        long artifactEstimate = Math.addExact(
                codec.canonicalTerrainIntent(intent).getBytes(StandardCharsets.UTF_8).length,
                geologyBudget.estimatedArtifactBytes());
        int requiredHaloXZ = catalog.modules().stream().mapToInt(ModuleDescriptorV2::requiredHaloXZ).max().orElse(0);
        int requiredHaloY = catalog.modules().stream().mapToInt(ModuleDescriptorV2::requiredHaloY).max().orElse(0);
        if (intent.features().size() > budget.maximumFeatures()
                || intent.relations().size() > budget.maximumRelations()
                || intent.constraints().size() > budget.maximumConstraints()
                || geometryPoints > budget.maximumGeometryPoints()
                || moduleCount > budget.maximumModules()
                || fieldCount > budget.maximumFields()
                || requiredHaloXZ > budget.maximumHaloXZ()
                || requiredHaloY > budget.maximumHaloY()
                || residentEstimate > budget.maximumResidentBytes()
                || cpuEstimate > budget.maximumCpuWorkUnits()
                || artifactEstimate > budget.maximumArtifactBytes()) {
            throw new DiagnosticCompilationException("v2.budget-exceeded", "diagnostic compile exceeds declared budget");
        }
    }

    private static List<WorldBlueprintV2.FieldDescriptor> fields(DiagnosticCompileRequestV2 request) {
        List<WorldBlueprintV2.FieldDescriptor> result = new ArrayList<>(List.of(
                new WorldBlueprintV2.FieldDescriptor(
                        BuiltInLandformModuleCatalogV2.CONTRACT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LAND_WATER_MASK,
                        WorldBlueprintV2.FieldValueType.BIT,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        BuiltInLandformModuleCatalogV2.DIAGNOSTIC_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.DIAGNOSTIC_FEATURE_PLAN,
                        WorldBlueprintV2.FieldValueType.U16,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.ACTUAL_LAND_WATER_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.ACTUAL_LAND_WATER,
                        WorldBlueprintV2.FieldValueType.U8,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.COAST_SIDE_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.COAST_SIDE,
                        WorldBlueprintV2.FieldValueType.U8,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.SIGNED_DISTANCE_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.COAST_SIGNED_DISTANCE,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY,
                        null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.NORMAL_X_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.COAST_NORMAL_X,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY,
                        null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.NORMAL_Z_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.COAST_NORMAL_Z,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY,
                        null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.NEARSHORE_PROFILE_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.NEARSHORE_PROFILE,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY,
                        null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.BEACH_LOCAL_WIDTH,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY, null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.BEACH_SURFACE_HEIGHT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.BEACH_SURFACE_HEIGHT,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY, null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.BEACH_BAND,
                        WorldBlueprintV2.FieldValueType.U8,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.BEACH_SEMANTIC_SAND_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.BEACH_SEMANTIC_SAND,
                        WorldBlueprintV2.FieldValueType.U8,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HARBOR_BASIN_REGION,
                        WorldBlueprintV2.FieldValueType.U8,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HARBOR_BASIN_WATER,
                        WorldBlueprintV2.FieldValueType.U8,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HARBOR_BASIN_WATER_DEPTH,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY, null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.HARBOR_BOTTOM_HEIGHT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HARBOR_BASIN_BOTTOM_HEIGHT,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY, null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.BREAKWATER_REGION,
                        WorldBlueprintV2.FieldValueType.U8,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.BREAKWATER_ARM_INDEX_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.BREAKWATER_ARM_INDEX,
                        WorldBlueprintV2.FieldValueType.U16,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.BREAKWATER_TOP_HEIGHT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.BREAKWATER_TOP_HEIGHT,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY, null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.BREAKWATER_BOTTOM_HEIGHT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.BREAKWATER_BOTTOM_HEIGHT,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY, null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.CAPE_REGION,
                        WorldBlueprintV2.FieldValueType.U8,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.CAPE_SURFACE_HEIGHT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.CAPE_SURFACE_HEIGHT,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY, null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.CAPE_ROCK_EXPOSURE_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.CAPE_ROCK_EXPOSURE,
                        WorldBlueprintV2.FieldValueType.U8,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalFoundationModuleV2.CAPE_DESCRIPTOR_INDEX_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.CAPE_DESCRIPTOR_INDEX,
                        WorldBlueprintV2.FieldValueType.U16,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalTransitionModuleV2.LAND_WATER_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.COASTAL_COMPOSED_LAND_WATER,
                        WorldBlueprintV2.FieldValueType.U8,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.COASTAL_COMPOSED_SURFACE_HEIGHT,
                        WorldBlueprintV2.FieldValueType.I32,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY, null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.COASTAL_COMPOSED_OWNER_INDEX,
                        WorldBlueprintV2.FieldValueType.U16,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalTransitionModuleV2.BLEND_WEIGHT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.COASTAL_COMPOSED_BLEND_WEIGHT,
                        WorldBlueprintV2.FieldValueType.Q16,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY, null),
                new WorldBlueprintV2.FieldDescriptor(
                        CoastalTransitionModuleV2.CONFLICT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.COASTAL_COMPOSED_CONFLICT,
                        WorldBlueprintV2.FieldValueType.U8,
                        request.bounds().width(), request.bounds().length(),
                        WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                        WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY)
        ));
        result.addAll(List.of(
                geologyField(request, GeologyFoundationModuleV2.PROVINCE_ID_FIELD,
                        WorldBlueprintV2.FieldSemantic.GEOLOGY_PROVINCE_ID),
                geologyField(request, GeologyFoundationModuleV2.FORMATION_ID_FIELD,
                        WorldBlueprintV2.FieldSemantic.GEOLOGY_FORMATION_ID),
                geologyField(request, GeologyFoundationModuleV2.HARDNESS_FIELD,
                        WorldBlueprintV2.FieldSemantic.GEOLOGY_HARDNESS),
                geologyField(request, GeologyFoundationModuleV2.PERMEABILITY_FIELD,
                        WorldBlueprintV2.FieldSemantic.GEOLOGY_PERMEABILITY)));
        result.addAll(List.of(
                climateField(request, ClimateFieldModulesV2.PRIOR_PRECIPITATION_FIELD,
                        WorldBlueprintV2.FieldSemantic.CLIMATE_PRIOR_PRECIPITATION,
                        WorldBlueprintV2.FieldValueType.U16),
                climateField(request, ClimateFieldModulesV2.PRIOR_RUNOFF_FIELD,
                        WorldBlueprintV2.FieldSemantic.CLIMATE_PRIOR_RUNOFF,
                        WorldBlueprintV2.FieldValueType.U16),
                climateField(request, ClimateFieldModulesV2.FINAL_TEMPERATURE_FIELD,
                        WorldBlueprintV2.FieldSemantic.CLIMATE_FINAL_TEMPERATURE,
                        WorldBlueprintV2.FieldValueType.I16),
                climateField(request, ClimateFieldModulesV2.FINAL_MOISTURE_FIELD,
                        WorldBlueprintV2.FieldSemantic.CLIMATE_FINAL_MOISTURE,
                        WorldBlueprintV2.FieldValueType.U16)));
        result.addAll(List.of(
                waterConditionField(request, WaterConditionFieldModulesV2.WATER_DISTANCE_FIELD,
                        WorldBlueprintV2.FieldSemantic.WATER_CONDITION_WATER_DISTANCE,
                        WorldBlueprintV2.FieldValueType.U16),
                waterConditionField(request, WaterConditionFieldModulesV2.GROUNDWATER_PROXY_FIELD,
                        WorldBlueprintV2.FieldSemantic.WATER_CONDITION_GROUNDWATER_PROXY,
                        WorldBlueprintV2.FieldValueType.U16),
                waterConditionField(request, WaterConditionFieldModulesV2.TIDAL_INFLUENCE_FIELD,
                        WorldBlueprintV2.FieldSemantic.WATER_CONDITION_TIDAL_INFLUENCE,
                        WorldBlueprintV2.FieldValueType.U16),
                waterConditionField(request, WaterConditionFieldModulesV2.SALINITY_FIELD,
                        WorldBlueprintV2.FieldSemantic.WATER_CONDITION_SALINITY,
                        WorldBlueprintV2.FieldValueType.U16),
                waterConditionField(request, WaterConditionFieldModulesV2.HYDROPERIOD_FIELD,
                        WorldBlueprintV2.FieldSemantic.WATER_CONDITION_HYDROPERIOD,
                        WorldBlueprintV2.FieldValueType.U16),
                waterConditionField(request, WaterConditionFieldModulesV2.WETNESS_FIELD,
                        WorldBlueprintV2.FieldSemantic.WATER_CONDITION_WETNESS,
                        WorldBlueprintV2.FieldValueType.U16),
                waterConditionField(request, WaterConditionFieldModulesV2.WETNESS_RESIDUAL_FIELD,
                        WorldBlueprintV2.FieldSemantic.WATER_CONDITION_WETNESS_RESIDUAL,
                        WorldBlueprintV2.FieldValueType.I16)));
        result.addAll(List.of(
                hydrologyField(request, HydrologyIrModuleV2.WATER_BODY_ID_FIELD,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_WATER_BODY_ID,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyIrModuleV2.FLOW_DIRECTION_FIELD,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_FLOW_DIRECTION,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyIrModuleV2.FLOW_ACCUMULATION_FIELD,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_FLOW_ACCUMULATION,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyIrModuleV2.BED_ELEVATION_FIELD,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_BED_ELEVATION,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyIrModuleV2.WATER_SURFACE_FIELD,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_WATER_SURFACE,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyIrModuleV2.WATER_DEPTH_FIELD,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_WATER_DEPTH,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyRiverModuleV2.CHANNEL_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_RIVER_CHANNEL_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyRiverModuleV2.BANK_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_RIVER_BANK_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyRiverModuleV2.FLOODPLAIN_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_RIVER_FLOODPLAIN_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyRiverModuleV2.MEANDER_CORRIDOR_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_RIVER_MEANDER_CORRIDOR,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyRiverModuleV2.LOCAL_WIDTH_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_RIVER_LOCAL_WIDTH,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyRiverModuleV2.DISCHARGE_INDEX_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_RIVER_DISCHARGE_INDEX,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyLakeModuleV2.BASIN_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_LAKE_BASIN_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyLakeModuleV2.RIM_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_LAKE_RIM_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyLakeModuleV2.SPILLWAY_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_LAKE_SPILLWAY_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyLakeModuleV2.DEPTH_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_LAKE_DEPTH,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyLakeModuleV2.FLOOR_HEIGHT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_LAKE_FLOOR_HEIGHT,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyLakeModuleV2.SURFACE_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_LAKE_SURFACE,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, LandformCanyonModuleV2.CANYON_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_CANYON_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformCanyonModuleV2.FLOOR_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_CANYON_FLOOR_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformCanyonModuleV2.RIM_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_CANYON_RIM_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformCanyonModuleV2.TERRACE_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_CANYON_TERRACE_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformCanyonModuleV2.SURFACE_HEIGHT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_CANYON_SURFACE_HEIGHT,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, LandformCanyonModuleV2.WALL_HEIGHT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_CANYON_WALL_HEIGHT,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyWaterfallModuleV2.LIP_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_WATERFALL_LIP_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyWaterfallModuleV2.BASE_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_WATERFALL_BASE_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyWaterfallModuleV2.PLUNGE_POOL_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_WATERFALL_PLUNGE_POOL_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyWaterfallModuleV2.LIP_ELEVATION_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_WATERFALL_LIP_ELEVATION,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyWaterfallModuleV2.BASE_ELEVATION_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_WATERFALL_BASE_ELEVATION,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyWaterfallModuleV2.PLUNGE_POOL_FLOOR_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_WATERFALL_PLUNGE_POOL_FLOOR,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyDeltaModuleV2.FAN_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_DELTA_FAN_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyDeltaModuleV2.CHANNEL_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_DELTA_CHANNEL_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyDeltaModuleV2.BRANCH_INDEX_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_DELTA_BRANCH_INDEX,
                        WorldBlueprintV2.FieldValueType.U16),
                hydrologyField(request, HydrologyDeltaModuleV2.FAN_SURFACE_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_DELTA_FAN_SURFACE,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyDeltaModuleV2.SANDBAR_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_DELTA_SANDBAR_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyDeltaModuleV2.SHALLOW_SEA_DEPTH_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_DELTA_SHALLOW_SEA_DEPTH,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyDeltaModuleV2.DISCHARGE_SHARE_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_DELTA_DISCHARGE_SHARE,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyTidalModuleV2.CHANNEL_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_TIDAL_CHANNEL_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, HydrologyTidalModuleV2.BRANCH_INDEX_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_TIDAL_BRANCH_INDEX,
                        WorldBlueprintV2.FieldValueType.U16),
                hydrologyField(request, HydrologyTidalModuleV2.DEPTH_CORRIDOR_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_TIDAL_DEPTH_CORRIDOR,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, HydrologyTidalModuleV2.MARINE_CONNECTION_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.HYDROLOGY_TIDAL_MARINE_CONNECTION,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformMangroveModuleV2.WETLAND_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_MANGROVE_WETLAND_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformMangroveModuleV2.SURFACE_HEIGHT_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_MANGROVE_SURFACE_HEIGHT,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, LandformMangroveModuleV2.OPEN_WATER_GAP_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_MANGROVE_OPEN_WATER_GAP,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformMangroveModuleV2.SUBSTRATE_CLASS_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_MANGROVE_SUBSTRATE_CLASS,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformMangroveModuleV2.MICRO_RELIEF_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_MANGROVE_MICRO_RELIEF,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformCoralReefModuleV2.REEF_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_REEF_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformCoralReefModuleV2.CREST_DEPTH_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_REEF_CREST_DEPTH,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, LandformCoralReefModuleV2.LAGOON_DEPTH_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_REEF_LAGOON_DEPTH,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, LandformCoralReefModuleV2.PASS_CORRIDOR_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_REEF_PASS_CORRIDOR,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformCoralReefModuleV2.MARINE_CONNECTION_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_REEF_MARINE_CONNECTION,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformFjordModuleV2.CHANNEL_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_FJORD_CHANNEL_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformFjordModuleV2.FLOOR_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_FJORD_FLOOR_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformFjordModuleV2.SIDEWALL_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_FJORD_SIDEWALL_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformFjordModuleV2.THALWEG_DEPTH_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_FJORD_THALWEG_DEPTH,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, LandformFjordModuleV2.SIDEWALL_RELIEF_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_FJORD_SIDEWALL_RELIEF,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, LandformMountainModuleV2.RIDGE_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_MOUNTAIN_RIDGE_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformMountainModuleV2.PEAK_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_MOUNTAIN_PEAK_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformMountainModuleV2.SADDLE_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_MOUNTAIN_SADDLE_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformMountainModuleV2.SPUR_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_MOUNTAIN_SPUR_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformMountainModuleV2.PROVISIONAL_SURFACE_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_MOUNTAIN_PROVISIONAL_SURFACE,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, LandformMountainModuleV2.RIDGE_SEGMENT_ID_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_MOUNTAIN_RIDGE_SEGMENT_ID,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, LandformVolcanicModuleV2.ISLAND_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_VOLCANIC_ISLAND_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformVolcanicModuleV2.ISLAND_INDEX_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_VOLCANIC_ISLAND_INDEX,
                        WorldBlueprintV2.FieldValueType.U16),
                hydrologyField(request, LandformVolcanicModuleV2.SUMMIT_RELIEF_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_VOLCANIC_SUMMIT_RELIEF,
                        WorldBlueprintV2.FieldValueType.I32),
                hydrologyField(request, LandformVolcanicModuleV2.SUBMARINE_SADDLE_MASK_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_VOLCANIC_SUBMARINE_SADDLE_MASK,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformVolcanicModuleV2.RADIAL_DRAINAGE_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_VOLCANIC_RADIAL_DRAINAGE,
                        WorldBlueprintV2.FieldValueType.U8),
                hydrologyField(request, LandformVolcanicModuleV2.PROVISIONAL_SURFACE_FIELD_ID,
                        WorldBlueprintV2.FieldSemantic.LANDFORM_VOLCANIC_PROVISIONAL_SURFACE,
                        WorldBlueprintV2.FieldValueType.I32)));
        return List.copyOf(result);
    }

    private static WorldBlueprintV2.FieldDescriptor geologyField(
            DiagnosticCompileRequestV2 request,
            String fieldId,
            WorldBlueprintV2.FieldSemantic semantic
    ) {
        return new WorldBlueprintV2.FieldDescriptor(
                fieldId, semantic, WorldBlueprintV2.FieldValueType.U16,
                request.bounds().width(), request.bounds().length(),
                WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY);
    }

    private static WorldBlueprintV2.FieldDescriptor hydrologyField(
            DiagnosticCompileRequestV2 request,
            String fieldId,
            WorldBlueprintV2.FieldSemantic semantic,
            WorldBlueprintV2.FieldValueType valueType
    ) {
        return new WorldBlueprintV2.FieldDescriptor(
                fieldId, semantic, valueType,
                request.bounds().width(), request.bounds().length(),
                WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY);
    }

    private static WorldBlueprintV2.FieldDescriptor climateField(
            DiagnosticCompileRequestV2 request,
            String fieldId,
            WorldBlueprintV2.FieldSemantic semantic,
            WorldBlueprintV2.FieldValueType valueType
    ) {
        return new WorldBlueprintV2.FieldDescriptor(
                fieldId, semantic, valueType,
                request.bounds().width(), request.bounds().length(),
                WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                WorldBlueprintV2.FieldSampling.BILINEAR_FIXED,
                WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY,
                null);
    }

    private static WorldBlueprintV2.FieldDescriptor waterConditionField(
            DiagnosticCompileRequestV2 request,
            String fieldId,
            WorldBlueprintV2.FieldSemantic semantic,
            WorldBlueprintV2.FieldValueType valueType
    ) {
        return new WorldBlueprintV2.FieldDescriptor(
                fieldId, semantic, valueType,
                request.bounds().width(), request.bounds().length(),
                WorldBlueprintV2.FieldSpace.RELEASE_LOCAL_XZ,
                WorldBlueprintV2.FieldSampling.NEAREST,
                WorldBlueprintV2.FieldStorage.DESCRIPTOR_ONLY,
                null);
    }

    private static List<ValidationTargetV2> compileTargets(TerrainIntentV2 intent) {
        List<ValidationTargetV2> result = new ArrayList<>();
        for (TerrainIntentV2.Constraint constraint : intent.constraints()) {
            List<String> featureIds = constraint.subject().startsWith("feature:")
                    ? List.of(constraint.subject().substring("feature:".length())) : List.of();
            if (constraint instanceof TerrainIntentV2.MetricRangeConstraint metric) {
                result.add(new ValidationTargetV2(
                        constraint.id(), constraint.id(), featureIds,
                        "v2.metric-range", 1, constraint.strength(), constraint.weightMillionths(),
                        metric.metric().toLowerCase(Locale.ROOT).replace('_', '.'), metric.range(),
                        metric.toleranceMillionths(), List.of(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_FIELD_ID),
                        "diagnostic.validation"));
            } else if (constraint instanceof TerrainIntentV2.EdgeClassificationConstraint edge) {
                result.add(new ValidationTargetV2(
                        constraint.id(), constraint.id(), featureIds,
                        "v2.edge-classification", 1, constraint.strength(), constraint.weightMillionths(),
                        "edge." + edge.edge().name().toLowerCase(Locale.ROOT) + "." + edge.classification().name().toLowerCase(Locale.ROOT),
                        new TerrainIntentV2.FixedRange(edge.minimumShareMillionths(), TerrainIntentV2.FIXED_SCALE),
                        0, List.of(BuiltInLandformModuleCatalogV2.CONTRACT_FIELD_ID), "diagnostic.validation"));
            }
        }
        return List.copyOf(result);
    }

    /** Adds generated coastal targets after all feature plans have frozen their numeric contracts. */
    private static List<ValidationTargetV2> coastalValidationTargets(
            List<SandyBeachPlanV2> beaches,
            List<HarborBasinPlanV2> harbors,
            List<BreakwaterHarborPlanV2> breakwaters,
            List<RockyCapePlanV2> capes,
            List<CoastalTransitionPlanV2> transitions
    ) {
        List<ValidationTargetV2> targets = new ArrayList<>();
        for (SandyBeachPlanV2 beach : beaches) {
            targets.add(target("coastal-" + beach.featureId() + "-width", List.of(beach.featureId()),
                    "coastal.beach.width", "coastal.beach.width-p50", new TerrainIntentV2.FixedRange(
                            (long) beach.minimumWidthBlocks() * TerrainIntentV2.FIXED_SCALE,
                            (long) beach.maximumWidthBlocks() * TerrainIntentV2.FIXED_SCALE), 0,
                    List.of(beach.localWidthFieldId(), beach.bandFieldId()), "coastal.beach.overlay"));
        }
        for (HarborBasinPlanV2 harbor : harbors) {
            targets.add(target("coastal-" + harbor.featureId() + "-depth", List.of(harbor.featureId()),
                    "coastal.harbor.depth", "coastal.harbor.depth-p50", new TerrainIntentV2.FixedRange(
                            (long) harbor.minimumDepthBlocks() * TerrainIntentV2.FIXED_SCALE,
                            (long) harbor.maximumDepthBlocks() * TerrainIntentV2.FIXED_SCALE), 0,
                    List.of(harbor.regionFieldId(), harbor.waterFieldId(), harbor.depthFieldId()),
                    "coastal.harbor.overlay"));
        }
        for (BreakwaterHarborPlanV2 breakwater : breakwaters) {
            targets.add(target("coastal-" + breakwater.featureId() + "-opening",
                    List.of(breakwater.featureId(), breakwater.basinFeatureId()), "coastal.breakwater.opening",
                    "coastal.breakwater.clear-opening", new TerrainIntentV2.FixedRange(
                            breakwater.actualClearOpeningWidthMillionths(), breakwater.actualClearOpeningWidthMillionths()),
                    0, List.of(breakwater.regionFieldId()), "coastal.breakwater.overlay"));
        }
        for (RockyCapePlanV2 cape : capes) {
            targets.add(target("coastal-" + cape.featureId() + "-exposure", List.of(cape.featureId()),
                    "coastal.cape.exposure", "coastal.cape.rock-exposure", new TerrainIntentV2.FixedRange(
                            cape.minimumRockExposureMillionths(), cape.maximumRockExposureMillionths()), 10_000,
                    List.of(cape.regionFieldId(), cape.rockExposureFieldId(), cape.descriptorIndexFieldId()),
                    "coastal.cape.overlay"));
        }
        for (CoastalTransitionPlanV2 transition : transitions) {
            targets.add(target("coastal-" + transition.planId() + "-conflict",
                    transition.contributors().stream().map(CoastalTransitionPlanV2.Contributor::featureId).toList(),
                    "coastal.transition.conflict", "coastal.transition.conflict-cells",
                    new TerrainIntentV2.FixedRange(0, 0), 0,
                    List.of(transition.conflictFieldId(), transition.landWaterFieldId(), transition.surfaceHeightFieldId()),
                    "coastal.constraint-errors"));
        }
        return List.copyOf(targets);
    }

    /** Adds generated hydrology targets after regional plans have frozen their numeric contracts. */
    private static List<ValidationTargetV2> hydrologyValidationTargets(
            List<MeanderingRiverPlanV2> rivers,
            List<LakePlanV2> lakes,
            List<DeltaPlanV2> deltas,
            List<TidalChannelPlanV2> tidals,
            List<MangroveWetlandPlanV2> mangroves,
            List<FjordPlanV2> fjords,
            List<WaterfallPlanV2> waterfalls,
            List<MountainPlanV2> mountains,
            List<VolcanicPlanV2> volcanics,
            HydrologyReconciliationPlanV2 reconciliationPlan
    ) {
        List<ValidationTargetV2> targets = new ArrayList<>();
        for (MeanderingRiverPlanV2 river : rivers) {
            targets.add(target("hydrology-" + river.featureId() + "-reachability", List.of(river.featureId()),
                    "hydrology.river.reachability", "hydrology.river.source-mouth-reachable",
                    new TerrainIntentV2.FixedRange(1, 1), 0,
                    List.of(river.channelMaskFieldId(), river.bedElevationFieldId()), "hydrology.reach.graph"));
            targets.add(target("hydrology-" + river.featureId() + "-bed", List.of(river.featureId()),
                    "hydrology.river.reverse-gradient", "hydrology.river.reverse-gradient-cells",
                    new TerrainIntentV2.FixedRange(0, 0), 0,
                    List.of(river.bedElevationFieldId(), river.channelMaskFieldId()), "hydrology.bed.elevation"));
        }
        for (LakePlanV2 lake : lakes) {
            if (lake.terminalPolicy() == TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL) {
                targets.add(target("hydrology-" + lake.featureId() + "-spill", List.of(lake.featureId()),
                        "hydrology.lake.leaking", "hydrology.lake.spillway-cells",
                        new TerrainIntentV2.FixedRange(1, Long.MAX_VALUE / 4), 0,
                        List.of(lake.basinMaskFieldId(), lake.spillwayMaskFieldId()), "hydrology.lake.rim-spill"));
            }
        }
        for (DeltaPlanV2 delta : deltas) {
            targets.add(target("hydrology-" + delta.featureId() + "-mouth", List.of(delta.featureId()),
                    "hydrology.delta.dead-branch", "hydrology.delta.mouth-connection",
                    new TerrainIntentV2.FixedRange(delta.branches().size(), delta.branches().size()), 0,
                    List.of(delta.channelMaskFieldId(), delta.shallowSeaDepthFieldId()),
                    "hydrology.delta.distributary"));
        }
        for (TidalChannelPlanV2 tidal : tidals) {
            targets.add(target("hydrology-" + tidal.featureId() + "-marine", List.of(tidal.featureId()),
                    "hydrology.tidal.marine-connection", "hydrology.tidal.marine-cells",
                    new TerrainIntentV2.FixedRange(1, Long.MAX_VALUE / 4), 0,
                    List.of(tidal.marineConnectionFieldId(), tidal.channelMaskFieldId()), "hydrology.reach.graph"));
        }
        for (FjordPlanV2 fjord : fjords) {
            targets.add(target("hydrology-" + fjord.featureId() + "-outlet", List.of(fjord.featureId()),
                    "hydrology.fjord.broken-outlet", "hydrology.fjord.mouth-connection",
                    new TerrainIntentV2.FixedRange(1, 1), 0,
                    List.of(fjord.channelMaskFieldId(), fjord.thalwegDepthFieldId()), "hydrology.fjord.thalweg"));
        }
        for (WaterfallPlanV2 waterfall : waterfalls) {
            targets.add(target("hydrology-" + waterfall.featureId() + "-drop", List.of(waterfall.featureId()),
                    "hydrology.waterfall.fall-mismatch", "hydrology.waterfall.drop-millionths",
                    new TerrainIntentV2.FixedRange(
                            (long) waterfall.minimumDropBlocks() * TerrainIntentV2.FIXED_SCALE,
                            (long) waterfall.maximumDropBlocks() * TerrainIntentV2.FIXED_SCALE),
                    0, List.of(waterfall.lipElevationFieldId(), waterfall.baseElevationFieldId(),
                            waterfall.lipMaskFieldId(), waterfall.baseMaskFieldId()),
                    "hydrology.waterfall.envelope"));
        }
        for (MountainPlanV2 mountain : mountains) {
            targets.add(target("hydrology-" + mountain.featureId() + "-ridge", List.of(mountain.featureId()),
                    "hydrology.mountain.ridge", "hydrology.mountain.ridge-cells",
                    new TerrainIntentV2.FixedRange(1, Long.MAX_VALUE / 4), 0,
                    List.of(mountain.ridgeMaskFieldId(), mountain.peakMaskFieldId()), "hydrology.reach.graph"));
        }
        for (VolcanicPlanV2 volcanic : volcanics) {
            targets.add(target("hydrology-" + volcanic.featureId() + "-islands", List.of(volcanic.featureId()),
                    "hydrology.volcanic.components", "hydrology.volcanic.island-components",
                    new TerrainIntentV2.FixedRange(volcanic.islands().size(), volcanic.islands().size()), 0,
                    List.of(volcanic.islandMaskFieldId(), volcanic.islandIndexFieldId()), "hydrology.reach.graph"));
        }
        if (reconciliationPlan != null && !reconciliationPlan.constraints().isEmpty()) {
            targets.add(target("hydrology-reconcile-residual", List.of(),
                    "hydrology.reconcile.residual", "hydrology.reconcile.unsatisfied",
                    new TerrainIntentV2.FixedRange(0, 0), 0,
                    List.of(HydrologyIrModuleV2.BED_ELEVATION_FIELD, HydrologyIrModuleV2.WATER_SURFACE_FIELD),
                    "hydrology.constraint.residual"));
        }
        return List.copyOf(targets);
    }

    private static ValidationTargetV2 target(
            String targetId,
            List<String> featureIds,
            String ruleId,
            String metric,
            TerrainIntentV2.FixedRange expected,
            long tolerance,
            List<String> requiredFields,
            String diagnosticLayer
    ) {
        return new ValidationTargetV2(targetId, targetId, featureIds, ruleId, 1,
                TerrainIntentV2.Strength.HARD, 0, metric, expected, tolerance, requiredFields, diagnosticLayer);
    }

    private static DiagnosticIssueV2 issue(
            String issueId,
            String ruleId,
            DiagnosticIssueV2.Severity severity,
            TerrainIntentV2.Feature feature,
            ModuleDescriptorV2 module,
            String messageKey
    ) {
        return new DiagnosticIssueV2(
                issueId, ruleId, 1, severity, TerrainIntentV2.Strength.HARD,
                List.of(
                        new DiagnosticIssueV2.Reference(DiagnosticIssueV2.ReferenceType.FEATURE, feature.id()),
                        new DiagnosticIssueV2.Reference(DiagnosticIssueV2.ReferenceType.MODULE, module.moduleId())),
                List.of(), messageKey, List.of("diagnostic.capabilities"));
    }

    private static boolean referencesFeature(TerrainIntentV2.Relation relation, String featureId) {
        String endpoint = "feature:" + featureId;
        return relation.from().equals(endpoint) || relation.to().equals(endpoint);
    }

    private static int geometryPointCount(TerrainIntentV2.Geometry geometry) {
        if (geometry instanceof TerrainIntentV2.PointGeometry) return 1;
        if (geometry instanceof TerrainIntentV2.MultiPointGeometry multi) return multi.points().size();
        if (geometry instanceof TerrainIntentV2.SplineGeometry spline) return spline.points().size();
        if (geometry instanceof TerrainIntentV2.MultiSplineGeometry multi) return multi.paths().stream().mapToInt(path -> path.points().size()).sum();
        if (geometry instanceof TerrainIntentV2.PolygonGeometry polygon) return polygon.rings().stream().mapToInt(List::size).sum();
        if (geometry instanceof TerrainIntentV2.VolumeGuideGeometry volume) return geometryPointCount(volume.footprint());
        throw new IllegalArgumentException("unknown geometry type");
    }
}
