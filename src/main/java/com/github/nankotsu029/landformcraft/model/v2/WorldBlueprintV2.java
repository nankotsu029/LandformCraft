package com.github.nankotsu029.landformcraft.model.v2;

import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Version-2 compiled IR. It contains no provider data or embedded dense field payloads. */
public record WorldBlueprintV2(
        Identity identity,
        Space space,
        Determinism determinism,
        List<ModuleDescriptorV2> modules,
        List<StageDescriptor> stages,
        List<FieldDescriptor> fields,
        List<FieldOwnership> fieldOwnership,
        List<FeaturePlan> featurePlans,
        List<CoastalFeaturePlanV2> coastalFeaturePlans,
        List<SandyBeachPlanV2> sandyBeachPlans,
        List<HarborBasinPlanV2> harborBasinPlans,
        List<BreakwaterHarborPlanV2> breakwaterHarborPlans,
        List<RockyCapePlanV2> rockyCapePlans,
        List<CoastalTransitionPlanV2> coastalTransitionPlans,
        List<MeanderingRiverPlanV2> meanderingRiverPlans,
        List<LakePlanV2> lakePlans,
        List<CanyonPlanV2> canyonPlans,
        List<WaterfallPlanV2> waterfallPlans,
        List<DeltaPlanV2> deltaPlans,
        List<TidalChannelPlanV2> tidalChannelPlans,
        List<MangroveWetlandPlanV2> mangroveWetlandPlans,
        List<CoralReefPlanV2> coralReefPlans,
        List<FjordPlanV2> fjordPlans,
        List<MountainPlanV2> mountainPlans,
        List<VolcanicPlanV2> volcanicPlans,
        GeologyPlanV2 geologyPlan,
        LithologyPlanV2 lithologyPlan,
        StrataPlanV2 strataPlan,
        ClimatePlanV2 climatePlan,
        WaterConditionPlanV2 waterConditionPlan,
        HydrologyPlanV2 hydrologyPlan,
        HydrologyReconciliationPlanV2 hydrologyReconciliationPlan,
        List<ValidationTargetV2> validationTargets,
        ResourceBudget budgets,
        List<DiagnosticIssueV2> diagnosticIssues,
        String canonicalChecksum
) {
    public static final int VERSION = 2;

    public WorldBlueprintV2 {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(space, "space");
        Objects.requireNonNull(determinism, "determinism");
        modules = V2Validation.sorted(modules, "modules", 128, Comparator.comparing(ModuleDescriptorV2::moduleId));
        stages = V2Validation.sorted(stages, "stages", 64, Comparator.comparing(StageDescriptor::stageId));
        fields = V2Validation.sorted(fields, "fields", 256, Comparator.comparing(FieldDescriptor::fieldId));
        fieldOwnership = V2Validation.sorted(fieldOwnership, "fieldOwnership", 256,
                Comparator.comparing(FieldOwnership::fieldId).thenComparing(FieldOwnership::moduleId));
        featurePlans = V2Validation.sorted(featurePlans, "featurePlans", 256, Comparator.comparing(FeaturePlan::featureId));
        coastalFeaturePlans = V2Validation.sorted(
                coastalFeaturePlans, "coastalFeaturePlans", 256,
                Comparator.comparing(CoastalFeaturePlanV2::featureId));
        sandyBeachPlans = V2Validation.sorted(
                sandyBeachPlans, "sandyBeachPlans", 256,
                Comparator.comparing(SandyBeachPlanV2::featureId));
        harborBasinPlans = V2Validation.sorted(
                harborBasinPlans, "harborBasinPlans", 256,
                Comparator.comparing(HarborBasinPlanV2::featureId));
        breakwaterHarborPlans = V2Validation.sorted(
                breakwaterHarborPlans, "breakwaterHarborPlans", 256,
                Comparator.comparing(BreakwaterHarborPlanV2::featureId));
        rockyCapePlans = V2Validation.sorted(
                rockyCapePlans, "rockyCapePlans", 256,
                Comparator.comparing(RockyCapePlanV2::featureId));
        coastalTransitionPlans = V2Validation.sorted(
                coastalTransitionPlans, "coastalTransitionPlans", 8,
                Comparator.comparing(CoastalTransitionPlanV2::planId));
        meanderingRiverPlans = V2Validation.sorted(
                meanderingRiverPlans, "meanderingRiverPlans", 256,
                Comparator.comparing(MeanderingRiverPlanV2::featureId));
        lakePlans = V2Validation.sorted(
                lakePlans, "lakePlans", 256,
                Comparator.comparing(LakePlanV2::featureId));
        canyonPlans = V2Validation.sorted(
                canyonPlans, "canyonPlans", 256,
                Comparator.comparing(CanyonPlanV2::featureId));
        waterfallPlans = V2Validation.sorted(
                waterfallPlans, "waterfallPlans", 256,
                Comparator.comparing(WaterfallPlanV2::featureId));
        deltaPlans = V2Validation.sorted(
                deltaPlans, "deltaPlans", 256,
                Comparator.comparing(DeltaPlanV2::featureId));
        tidalChannelPlans = V2Validation.sorted(
                tidalChannelPlans, "tidalChannelPlans", 256,
                Comparator.comparing(TidalChannelPlanV2::featureId));
        mangroveWetlandPlans = V2Validation.sorted(
                mangroveWetlandPlans, "mangroveWetlandPlans", 256,
                Comparator.comparing(MangroveWetlandPlanV2::featureId));
        coralReefPlans = V2Validation.sorted(
                coralReefPlans, "coralReefPlans", 256,
                Comparator.comparing(CoralReefPlanV2::featureId));
        fjordPlans = V2Validation.sorted(fjordPlans, "fjordPlans", 256,
                Comparator.comparing(FjordPlanV2::featureId));
        mountainPlans = V2Validation.sorted(mountainPlans, "mountainPlans", 256,
                Comparator.comparing(MountainPlanV2::featureId));
        volcanicPlans = V2Validation.sorted(volcanicPlans, "volcanicPlans", 256,
                Comparator.comparing(VolcanicPlanV2::featureId));
        Objects.requireNonNull(geologyPlan, "geologyPlan");
        Objects.requireNonNull(lithologyPlan, "lithologyPlan");
        Objects.requireNonNull(strataPlan, "strataPlan");
        Objects.requireNonNull(climatePlan, "climatePlan");
        Objects.requireNonNull(waterConditionPlan, "waterConditionPlan");
        Objects.requireNonNull(hydrologyPlan, "hydrologyPlan");
        Objects.requireNonNull(hydrologyReconciliationPlan, "hydrologyReconciliationPlan");
        validationTargets = V2Validation.sorted(validationTargets, "validationTargets", 512,
                Comparator.comparing(ValidationTargetV2::targetId));
        Objects.requireNonNull(budgets, "budgets");
        diagnosticIssues = V2Validation.sorted(diagnosticIssues, "diagnosticIssues", 2_048,
                Comparator.comparing(DiagnosticIssueV2::issueId));
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
        validateDescriptors(space, determinism, modules, stages, fields, fieldOwnership,
                featurePlans, coastalFeaturePlans, sandyBeachPlans, harborBasinPlans, breakwaterHarborPlans,
                rockyCapePlans, coastalTransitionPlans, meanderingRiverPlans, lakePlans, canyonPlans,
                waterfallPlans, deltaPlans, tidalChannelPlans, mangroveWetlandPlans, coralReefPlans, fjordPlans, mountainPlans,
                volcanicPlans, geologyPlan,
                lithologyPlan,
                strataPlan,
                climatePlan,
                waterConditionPlan,
                hydrologyPlan,
                hydrologyReconciliationPlan,
                validationTargets, budgets, diagnosticIssues);
    }

    public WorldBlueprintV2 withCanonicalChecksum(String checksum) {
        return new WorldBlueprintV2(identity, space, determinism, modules, stages, fields, fieldOwnership,
                featurePlans, coastalFeaturePlans, sandyBeachPlans, harborBasinPlans, breakwaterHarborPlans,
                rockyCapePlans, coastalTransitionPlans, meanderingRiverPlans, lakePlans, canyonPlans,
                waterfallPlans, deltaPlans, tidalChannelPlans, mangroveWetlandPlans, coralReefPlans, fjordPlans, mountainPlans,
                volcanicPlans, geologyPlan,
                lithologyPlan,
                strataPlan,
                climatePlan,
                waterConditionPlan,
                hydrologyPlan,
                hydrologyReconciliationPlan,
                validationTargets, budgets, diagnosticIssues, checksum);
    }

    public record Identity(
            int blueprintVersion,
            String requestId,
            String sourceRequestChecksum,
            String sourceIntentChecksum,
            String compilerVersion,
            String generatorVersion
    ) {
        public Identity {
            if (blueprintVersion != VERSION) throw new IllegalArgumentException("blueprintVersion must be 2");
            requestId = V2Validation.slug(requestId, "requestId");
            sourceRequestChecksum = V2Validation.checksum(sourceRequestChecksum, "sourceRequestChecksum");
            sourceIntentChecksum = V2Validation.checksum(sourceIntentChecksum, "sourceIntentChecksum");
            compilerVersion = V2Validation.nonBlank(compilerVersion, "compilerVersion", 64);
            generatorVersion = V2Validation.nonBlank(generatorVersion, "generatorVersion", 64);
        }
    }

    public record Space(
            Bounds bounds,
            TerrainIntentV2.CoordinateSystem coordinateSystem,
            TilePolicy tilePolicy
    ) {
        public Space {
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(coordinateSystem, "coordinateSystem");
            Objects.requireNonNull(tilePolicy, "tilePolicy");
        }
    }

    public record Bounds(int width, int length, int minY, int maxY, int waterLevel) {
        public Bounds {
            if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) {
                throw new IllegalArgumentException("bounds horizontal dimensions outside 1.." + ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING);
            }
            if (minY >= maxY || (long) maxY - minY + 1L > 512L
                    || waterLevel < minY || waterLevel > maxY) {
                throw new IllegalArgumentException("bounds vertical range is invalid");
            }
        }
    }

    public record TilePolicy(int tileSize, int maximumHaloXZ, int maximumHaloY) {
        public TilePolicy {
            if (tileSize < 32 || tileSize > 256 || Integer.bitCount(tileSize) != 1) throw new IllegalArgumentException("invalid tileSize");
            if (maximumHaloXZ < 0 || maximumHaloXZ > 1_000 || maximumHaloY < 0 || maximumHaloY > 512) throw new IllegalArgumentException("invalid halo");
        }
    }

    public record Determinism(
            long globalSeed,
            String seedNamespace,
            String seedDerivationVersion,
            String canonicalizationVersion
    ) {
        public Determinism {
            seedNamespace = V2Validation.qualifiedId(seedNamespace, "seedNamespace");
            seedDerivationVersion = V2Validation.nonBlank(seedDerivationVersion, "seedDerivationVersion", 64);
            canonicalizationVersion = V2Validation.nonBlank(canonicalizationVersion, "canonicalizationVersion", 64);
        }
    }

    public record StageDescriptor(String stageId, List<String> dependsOnStageIds) {
        public StageDescriptor {
            stageId = V2Validation.qualifiedId(stageId, "stageId");
            dependsOnStageIds = V2Validation.sorted(dependsOnStageIds, "dependsOnStageIds", 32,
                    Comparator.naturalOrder()).stream().map(value -> V2Validation.qualifiedId(value, "stage dependency")).toList();
        }
    }

    public enum FieldSemantic {
        LAND_WATER_MASK,
        HEIGHT_GUIDE,
        ZONE_LABEL_MAP,
        DESIRED_LAND_WATER,
        ACTUAL_LAND_WATER,
        RESIDUAL_LAND_WATER,
        DESIRED_HEIGHT,
        ACTUAL_HEIGHT,
        RESIDUAL_HEIGHT,
        DIAGNOSTIC_FEATURE_PLAN,
        COAST_NORMAL_X,
        COAST_NORMAL_Z,
        COAST_SIDE,
        COAST_SIGNED_DISTANCE,
        NEARSHORE_PROFILE,
        BEACH_LOCAL_WIDTH,
        BEACH_SURFACE_HEIGHT,
        BEACH_BAND,
        BEACH_SEMANTIC_SAND,
        HARBOR_BASIN_REGION,
        HARBOR_BASIN_WATER,
        HARBOR_BASIN_WATER_DEPTH,
        HARBOR_BASIN_BOTTOM_HEIGHT,
        BREAKWATER_REGION,
        BREAKWATER_ARM_INDEX,
        BREAKWATER_TOP_HEIGHT,
        BREAKWATER_BOTTOM_HEIGHT,
        CAPE_REGION,
        CAPE_SURFACE_HEIGHT,
        CAPE_ROCK_EXPOSURE,
        CAPE_DESCRIPTOR_INDEX,
        COASTAL_COMPOSED_LAND_WATER,
        COASTAL_COMPOSED_SURFACE_HEIGHT,
        COASTAL_COMPOSED_OWNER_INDEX,
        COASTAL_COMPOSED_BLEND_WEIGHT,
        COASTAL_COMPOSED_CONFLICT,
        HYDROLOGY_WATER_BODY_ID,
        HYDROLOGY_FLOW_DIRECTION,
        HYDROLOGY_FLOW_ACCUMULATION,
        HYDROLOGY_BED_ELEVATION,
        HYDROLOGY_WATER_SURFACE,
        HYDROLOGY_WATER_DEPTH,
        HYDROLOGY_RIVER_CHANNEL_MASK,
        HYDROLOGY_RIVER_BANK_MASK,
        HYDROLOGY_RIVER_FLOODPLAIN_MASK,
        HYDROLOGY_RIVER_MEANDER_CORRIDOR,
        HYDROLOGY_RIVER_LOCAL_WIDTH,
        HYDROLOGY_RIVER_DISCHARGE_INDEX,
        HYDROLOGY_LAKE_BASIN_MASK,
        HYDROLOGY_LAKE_RIM_MASK,
        HYDROLOGY_LAKE_SPILLWAY_MASK,
        HYDROLOGY_LAKE_DEPTH,
        HYDROLOGY_LAKE_FLOOR_HEIGHT,
        HYDROLOGY_LAKE_SURFACE,
        LANDFORM_CANYON_MASK,
        LANDFORM_CANYON_FLOOR_MASK,
        LANDFORM_CANYON_RIM_MASK,
        LANDFORM_CANYON_TERRACE_MASK,
        LANDFORM_CANYON_SURFACE_HEIGHT,
        LANDFORM_CANYON_WALL_HEIGHT,
        HYDROLOGY_WATERFALL_LIP_MASK,
        HYDROLOGY_WATERFALL_BASE_MASK,
        HYDROLOGY_WATERFALL_PLUNGE_POOL_MASK,
        HYDROLOGY_WATERFALL_LIP_ELEVATION,
        HYDROLOGY_WATERFALL_BASE_ELEVATION,
        HYDROLOGY_WATERFALL_PLUNGE_POOL_FLOOR,
        HYDROLOGY_DELTA_FAN_MASK,
        HYDROLOGY_DELTA_CHANNEL_MASK,
        HYDROLOGY_DELTA_BRANCH_INDEX,
        HYDROLOGY_DELTA_FAN_SURFACE,
        HYDROLOGY_DELTA_SANDBAR_MASK,
        HYDROLOGY_DELTA_SHALLOW_SEA_DEPTH,
        HYDROLOGY_DELTA_DISCHARGE_SHARE,
        HYDROLOGY_TIDAL_CHANNEL_MASK,
        HYDROLOGY_TIDAL_BRANCH_INDEX,
        HYDROLOGY_TIDAL_DEPTH_CORRIDOR,
        HYDROLOGY_TIDAL_MARINE_CONNECTION,
        LANDFORM_MANGROVE_WETLAND_MASK,
        LANDFORM_MANGROVE_SURFACE_HEIGHT,
        LANDFORM_MANGROVE_OPEN_WATER_GAP,
        LANDFORM_MANGROVE_SUBSTRATE_CLASS,
        LANDFORM_MANGROVE_MICRO_RELIEF,
        LANDFORM_REEF_MASK,
        LANDFORM_REEF_CREST_DEPTH,
        LANDFORM_REEF_LAGOON_DEPTH,
        LANDFORM_REEF_PASS_CORRIDOR,
        LANDFORM_REEF_MARINE_CONNECTION,
        LANDFORM_FJORD_CHANNEL_MASK,
        LANDFORM_FJORD_FLOOR_MASK,
        LANDFORM_FJORD_SIDEWALL_MASK,
        LANDFORM_FJORD_THALWEG_DEPTH,
        LANDFORM_FJORD_SIDEWALL_RELIEF,
        LANDFORM_MOUNTAIN_RIDGE_MASK,
        LANDFORM_MOUNTAIN_PEAK_MASK,
        LANDFORM_MOUNTAIN_SADDLE_MASK,
        LANDFORM_MOUNTAIN_SPUR_MASK,
        LANDFORM_MOUNTAIN_PROVISIONAL_SURFACE,
        LANDFORM_MOUNTAIN_RIDGE_SEGMENT_ID,
        LANDFORM_VOLCANIC_ISLAND_MASK,
        LANDFORM_VOLCANIC_ISLAND_INDEX,
        LANDFORM_VOLCANIC_SUMMIT_RELIEF,
        LANDFORM_VOLCANIC_SUBMARINE_SADDLE_MASK,
        LANDFORM_VOLCANIC_RADIAL_DRAINAGE,
        LANDFORM_VOLCANIC_PROVISIONAL_SURFACE,
        FOUNDATION_PLAIN_MASK,
        FOUNDATION_PLAIN_BASE_ELEVATION,
        FOUNDATION_PLAIN_MICRO_RELIEF,
        FOUNDATION_PLAIN_GROUNDWATER_HANDOFF,
        GEOLOGY_PROVINCE_ID,
        GEOLOGY_FORMATION_ID,
        GEOLOGY_HARDNESS,
        GEOLOGY_PERMEABILITY,
        CLIMATE_PRIOR_PRECIPITATION,
        CLIMATE_PRIOR_RUNOFF,
        CLIMATE_FINAL_TEMPERATURE,
        CLIMATE_FINAL_MOISTURE,
        WATER_CONDITION_WATER_DISTANCE,
        WATER_CONDITION_GROUNDWATER_PROXY,
        WATER_CONDITION_TIDAL_INFLUENCE,
        WATER_CONDITION_SALINITY,
        WATER_CONDITION_HYDROPERIOD,
        WATER_CONDITION_WETNESS,
        WATER_CONDITION_WETNESS_RESIDUAL
    }
    public enum FieldValueType { BIT, U8, U16, I16, I32, Q16 }
    public enum FieldSpace { RELEASE_LOCAL_XZ }
    public enum FieldSampling { NEAREST, BILINEAR_FIXED }
    public enum FieldStorage { DESCRIPTOR_ONLY, SIDECAR }

    public record FieldDescriptor(
            String fieldId,
            FieldSemantic semantic,
            FieldValueType valueType,
            int width,
            int length,
            FieldSpace space,
            FieldSampling sampling,
            FieldStorage storage,
            FieldArtifactDescriptorV2 artifact
    ) {
        public FieldDescriptor(
                String fieldId,
                FieldSemantic semantic,
                FieldValueType valueType,
                int width,
                int length,
                FieldSpace space,
                FieldStorage storage
        ) {
            this(fieldId, semantic, valueType, width, length, space, FieldSampling.NEAREST, storage, null);
        }

        public FieldDescriptor {
            fieldId = V2Validation.qualifiedId(fieldId, "fieldId");
            Objects.requireNonNull(semantic, "semantic");
            Objects.requireNonNull(valueType, "valueType");
            if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) throw new IllegalArgumentException("field dimensions outside bounds");
            Objects.requireNonNull(space, "space");
            Objects.requireNonNull(sampling, "sampling");
            Objects.requireNonNull(storage, "storage");
            if (storage == FieldStorage.DESCRIPTOR_ONLY) {
                if (artifact != null) {
                    throw new IllegalArgumentException("descriptor-only field must not reference an artifact");
                }
            } else {
                requireMatchingArtifact(fieldId, semantic, valueType, width, length, space, sampling, artifact);
            }
        }

        private static void requireMatchingArtifact(
                String fieldId,
                FieldSemantic semantic,
                FieldValueType valueType,
                int width,
                int length,
                FieldSpace space,
                FieldSampling sampling,
                FieldArtifactDescriptorV2 artifact
        ) {
            Objects.requireNonNull(artifact, "SIDECAR field artifact");
            FieldArtifactDescriptorV2.Definition definition = artifact.definition();
            boolean matches = definition.fieldId().equals(fieldId)
                    && definition.semantic().name().equals(semantic.name())
                    && definition.valueType().name().equals(valueType.name())
                    && definition.width() == width
                    && definition.length() == length
                    && definition.coordinateSpace().name().equals(space.name())
                    && definition.sampling().name().equals(sampling.name());
            if (!matches) {
                throw new IllegalArgumentException("field artifact definition does not match its Blueprint field");
            }
        }
    }

    public record FieldOwnership(String fieldId, String moduleId, ModuleDescriptorV2.MergeOperator mergeOperator) {
        public FieldOwnership {
            fieldId = V2Validation.qualifiedId(fieldId, "fieldId");
            moduleId = V2Validation.qualifiedId(moduleId, "moduleId");
            Objects.requireNonNull(mergeOperator, "mergeOperator");
        }
    }

    public record FeaturePlan(
            String featureId,
            TerrainIntentV2.FeatureKind kind,
            int priority,
            String moduleId,
            String moduleVersion,
            TerrainIntentV2.GeometryType geometryType,
            String geometryChecksum,
            long namedSeed,
            String seedNamespace,
            List<String> relationIds,
            List<String> requiredFields,
            List<String> providedFields,
            List<String> validationTargetIds
    ) {
        public FeaturePlan {
            featureId = V2Validation.slug(featureId, "featureId");
            Objects.requireNonNull(kind, "kind");
            if (priority < -100 || priority > 100) throw new IllegalArgumentException("priority outside -100..100");
            moduleId = V2Validation.qualifiedId(moduleId, "moduleId");
            moduleVersion = V2Validation.nonBlank(moduleVersion, "moduleVersion", 64);
            Objects.requireNonNull(geometryType, "geometryType");
            geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
            seedNamespace = V2Validation.qualifiedId(seedNamespace, "seedNamespace");
            relationIds = slugs(relationIds, "relationIds");
            requiredFields = qualified(requiredFields, "requiredFields");
            providedFields = qualified(providedFields, "providedFields");
            validationTargetIds = slugs(validationTargetIds, "validationTargetIds");
        }
    }

    public record ResourceBudget(
            int maximumFeatures,
            int maximumRelations,
            int maximumConstraints,
            int maximumGeometryPoints,
            int maximumModules,
            int maximumFields,
            int maximumHaloXZ,
            int maximumHaloY,
            long maximumResidentBytes,
            long maximumCpuWorkUnits,
            long maximumArtifactBytes
    ) {
        public ResourceBudget {
            if (maximumFeatures < 1 || maximumRelations < 0 || maximumConstraints < 0
                    || maximumGeometryPoints < 1 || maximumModules < 1 || maximumFields < 1
                    || maximumHaloXZ < 0 || maximumHaloXZ > 1_000
                    || maximumHaloY < 0 || maximumHaloY > 512
                    || maximumResidentBytes < 1 || maximumCpuWorkUnits < 1 || maximumArtifactBytes < 1) {
                throw new IllegalArgumentException("resource budget values must be positive");
            }
        }
    }

    private static void validateDescriptors(
            Space space,
            Determinism determinism,
            List<ModuleDescriptorV2> modules,
            List<StageDescriptor> stages,
            List<FieldDescriptor> fields,
            List<FieldOwnership> ownership,
            List<FeaturePlan> plans,
            List<CoastalFeaturePlanV2> coastalPlans,
            List<SandyBeachPlanV2> sandyBeachPlans,
            List<HarborBasinPlanV2> harborBasinPlans,
            List<BreakwaterHarborPlanV2> breakwaterHarborPlans,
            List<RockyCapePlanV2> rockyCapePlans,
            List<CoastalTransitionPlanV2> coastalTransitionPlans,
            List<MeanderingRiverPlanV2> meanderingRiverPlans,
            List<LakePlanV2> lakePlans,
            List<CanyonPlanV2> canyonPlans,
            List<WaterfallPlanV2> waterfallPlans,
        List<DeltaPlanV2> deltaPlans,
        List<TidalChannelPlanV2> tidalChannelPlans,
        List<MangroveWetlandPlanV2> mangroveWetlandPlans,
        List<CoralReefPlanV2> coralReefPlans,
        List<FjordPlanV2> fjordPlans,
        List<MountainPlanV2> mountainPlans,
            List<VolcanicPlanV2> volcanicPlans,
            GeologyPlanV2 geologyPlan,
            LithologyPlanV2 lithologyPlan,
            StrataPlanV2 strataPlan,
            ClimatePlanV2 climatePlan,
            WaterConditionPlanV2 waterConditionPlan,
            HydrologyPlanV2 hydrologyPlan,
            HydrologyReconciliationPlanV2 hydrologyReconciliationPlan,
            List<ValidationTargetV2> targets,
            ResourceBudget budgets,
            List<DiagnosticIssueV2> issues
    ) {
        Map<String, ModuleDescriptorV2> modulesById = uniqueMap(
                modules, ModuleDescriptorV2::moduleId, "module");
        Map<String, StageDescriptor> stagesById = uniqueMap(stages, StageDescriptor::stageId, "stage");
        Map<String, FieldDescriptor> fieldsById = uniqueMap(fields, FieldDescriptor::fieldId, "field");
        Map<String, ValidationTargetV2> targetsById = uniqueMap(
                targets, ValidationTargetV2::targetId, "validation target");
        Map<String, FeaturePlan> plansById = uniqueMap(plans, FeaturePlan::featureId, "feature plan");
        Map<String, CoastalFeaturePlanV2> coastalPlansById = uniqueMap(
                coastalPlans, CoastalFeaturePlanV2::featureId, "coastal feature plan");
        Map<String, SandyBeachPlanV2> sandyBeachPlansById = uniqueMap(
                sandyBeachPlans, SandyBeachPlanV2::featureId, "sandy beach plan");
        Map<String, HarborBasinPlanV2> harborBasinPlansById = uniqueMap(
                harborBasinPlans, HarborBasinPlanV2::featureId, "harbor basin plan");
        Map<String, BreakwaterHarborPlanV2> breakwaterPlansById = uniqueMap(
                breakwaterHarborPlans, BreakwaterHarborPlanV2::featureId, "breakwater plan");
        Map<String, RockyCapePlanV2> capePlansById = uniqueMap(
                rockyCapePlans, RockyCapePlanV2::featureId, "rocky cape plan");
        Map<String, CoastalTransitionPlanV2> transitionPlansById = uniqueMap(
                coastalTransitionPlans, CoastalTransitionPlanV2::planId, "coastal transition plan");
        Map<String, MeanderingRiverPlanV2> riverPlansById = uniqueMap(
                meanderingRiverPlans, MeanderingRiverPlanV2::featureId, "meandering river plan");
        Map<String, LakePlanV2> lakePlansById = uniqueMap(
                lakePlans, LakePlanV2::featureId, "lake plan");
        Map<String, CanyonPlanV2> canyonPlansById = uniqueMap(
                canyonPlans, CanyonPlanV2::featureId, "canyon plan");
        Map<String, WaterfallPlanV2> waterfallPlansById = uniqueMap(
                waterfallPlans, WaterfallPlanV2::featureId, "waterfall plan");
        Map<String, DeltaPlanV2> deltaPlansById = uniqueMap(
                deltaPlans, DeltaPlanV2::featureId, "delta plan");
        Map<String, TidalChannelPlanV2> tidalPlansById = uniqueMap(
                tidalChannelPlans, TidalChannelPlanV2::featureId, "tidal channel plan");
        Map<String, MangroveWetlandPlanV2> mangrovePlansById = uniqueMap(
                mangroveWetlandPlans, MangroveWetlandPlanV2::featureId, "mangrove wetland plan");
        Map<String, CoralReefPlanV2> coralReefPlansById = uniqueMap(
                coralReefPlans, CoralReefPlanV2::featureId, "coral reef plan");
        Map<String, FjordPlanV2> fjordPlansById = uniqueMap(
                fjordPlans, FjordPlanV2::featureId, "fjord plan");
        Map<String, MountainPlanV2> mountainPlansById = uniqueMap(
                mountainPlans, MountainPlanV2::featureId, "mountain plan");
        Map<String, VolcanicPlanV2> volcanicPlansById = uniqueMap(
                volcanicPlans, VolcanicPlanV2::featureId, "volcanic plan");
        uniqueMap(issues, DiagnosticIssueV2::issueId, "diagnostic issue");

        Map<String, List<String>> stageGraph = new HashMap<>();
        for (StageDescriptor stage : stages) {
            for (String dependency : stage.dependsOnStageIds()) {
                if (!stagesById.containsKey(dependency)) {
                    throw new IllegalArgumentException("stage references unknown dependency: " + dependency);
                }
                stageGraph.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(stage.stageId());
            }
        }
        requireAcyclic(stageGraph, "stage graph");

        Map<String, List<FieldOwnership>> ownersByField = new HashMap<>();
        Set<String> ownershipPairs = new HashSet<>();
        for (FieldOwnership owner : ownership) {
            if (!fieldsById.containsKey(owner.fieldId())) {
                throw new IllegalArgumentException("ownership references unknown field: " + owner.fieldId());
            }
            if (!modulesById.containsKey(owner.moduleId())) {
                throw new IllegalArgumentException("ownership references unknown module: " + owner.moduleId());
            }
            if (!ownershipPairs.add(owner.fieldId() + '\n' + owner.moduleId())) {
                throw new IllegalArgumentException("duplicate field ownership");
            }
            ownersByField.computeIfAbsent(owner.fieldId(), ignored -> new ArrayList<>()).add(owner);
        }
        for (FieldDescriptor field : fields) {
            if (field.width() != space.bounds().width() || field.length() != space.bounds().length()) {
                throw new IllegalArgumentException("field dimensions must match blueprint bounds: " + field.fieldId());
            }
            List<FieldOwnership> owners = ownersByField.getOrDefault(field.fieldId(), List.of());
            if (owners.isEmpty()) throw new IllegalArgumentException("field has no owner: " + field.fieldId());
            ModuleDescriptorV2.MergeOperator operator = owners.getFirst().mergeOperator();
            if (owners.stream().anyMatch(owner -> owner.mergeOperator() != operator)
                    || (operator == ModuleDescriptorV2.MergeOperator.SINGLE_OWNER && owners.size() != 1)) {
                throw new IllegalArgumentException("field ownership merge contract is inconsistent: " + field.fieldId());
            }
        }

        Map<String, List<String>> moduleGraph = new HashMap<>();
        for (ModuleDescriptorV2 module : modules) {
            if (!stagesById.containsKey(module.stageId())) {
                throw new IllegalArgumentException("module references unknown stage: " + module.stageId());
            }
            for (String field : module.requiredFields()) {
                requireField(fieldsById, field);
                for (FieldOwnership owner : ownersByField.getOrDefault(field, List.of())) {
                    if (!owner.moduleId().equals(module.moduleId())) {
                        moduleGraph.computeIfAbsent(owner.moduleId(), ignored -> new ArrayList<>())
                                .add(module.moduleId());
                    }
                }
            }
            for (ModuleDescriptorV2.FieldWrite write : module.fieldWrites()) {
                requireField(fieldsById, write.fieldId());
                if (!ownership.contains(new FieldOwnership(
                        write.fieldId(), module.moduleId(), write.mergeOperator()))) {
                    throw new IllegalArgumentException("module field write is missing ownership: " + write.fieldId());
                }
            }
        }
        requireAcyclic(moduleGraph, "field owner graph");

        for (ValidationTargetV2 target : targets) {
            target.requiredFields().forEach(field -> requireField(fieldsById, field));
        }
        for (FeaturePlan plan : plans) {
            ModuleDescriptorV2 module = modulesById.get(plan.moduleId());
            if (module == null) throw new IllegalArgumentException("feature plan references unknown module: " + plan.moduleId());
            if (!module.moduleVersion().equals(plan.moduleVersion())
                    || !module.supportedFeatureKinds().contains(plan.kind())) {
                throw new IllegalArgumentException("feature plan module capability mismatch: " + plan.featureId());
            }
            if (!module.requiredFields().equals(plan.requiredFields())
                    || !module.providedFields().equals(plan.providedFields())) {
                throw new IllegalArgumentException("feature plan field contract mismatch: " + plan.featureId());
            }
            if (!determinism.seedNamespace().equals(plan.seedNamespace())) {
                throw new IllegalArgumentException("feature plan seed namespace mismatch: " + plan.featureId());
            }
            for (String targetId : plan.validationTargetIds()) {
                if (!targetsById.containsKey(targetId)) {
                    throw new IllegalArgumentException("feature plan references unknown validation target: " + targetId);
                }
            }
        }
        for (CoastalFeaturePlanV2 coastalPlan : coastalPlans) {
            FeaturePlan plan = plansById.get(coastalPlan.featureId());
            if (plan == null || plan.kind() != coastalPlan.kind()
                    || plan.geometryType() != coastalPlan.geometry().geometryType()
                    || !plan.geometryChecksum().equals(coastalPlan.geometry().sourceGeometryChecksum())) {
                throw new IllegalArgumentException(
                        "coastal plan does not match its feature plan: " + coastalPlan.featureId());
            }
            for (String fieldId : List.of(
                    coastalPlan.coastSideFieldId(),
                    coastalPlan.signedDistance().fieldId(),
                    coastalPlan.nearshoreProfile().fieldId())) {
                requireField(fieldsById, fieldId);
                if (!plan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException(
                            "coastal descriptor field is not provided by feature module: " + fieldId);
                }
            }
            validateBlockCoordinates(space.bounds(), coastalPlan.geometry());
        }
        for (FeaturePlan plan : plans) {
            if (CoastalFeaturePlanV2.isFoundationKind(plan.kind())
                    != coastalPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "coastal plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (SandyBeachPlanV2 beachPlan : sandyBeachPlans) {
            FeaturePlan plan = plansById.get(beachPlan.featureId());
            CoastalFeaturePlanV2 coastalPlan = coastalPlansById.get(beachPlan.featureId());
            if (plan == null || plan.kind() != TerrainIntentV2.FeatureKind.SANDY_BEACH
                    || coastalPlan == null || coastalPlan.kind() != TerrainIntentV2.FeatureKind.SANDY_BEACH) {
                throw new IllegalArgumentException(
                        "sandy beach plan does not match its feature plan: " + beachPlan.featureId());
            }
            for (String fieldId : List.of(
                    beachPlan.localWidthFieldId(), beachPlan.surfaceHeightFieldId(),
                    beachPlan.bandFieldId(), beachPlan.semanticSandFieldId())) {
                requireField(fieldsById, fieldId);
                if (!plan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException(
                            "sandy beach field is not provided by feature module: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.SANDY_BEACH)
                    != sandyBeachPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "sandy beach plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (HarborBasinPlanV2 harborPlan : harborBasinPlans) {
            FeaturePlan plan = plansById.get(harborPlan.featureId());
            CoastalFeaturePlanV2 coastalPlan = coastalPlansById.get(harborPlan.featureId());
            if (plan == null || plan.kind() != TerrainIntentV2.FeatureKind.HARBOR_BASIN
                    || coastalPlan == null || coastalPlan.kind() != TerrainIntentV2.FeatureKind.HARBOR_BASIN) {
                throw new IllegalArgumentException(
                        "harbor basin plan does not match its feature plan: " + harborPlan.featureId());
            }
            for (String fieldId : List.of(
                    harborPlan.regionFieldId(), harborPlan.waterFieldId(),
                    harborPlan.depthFieldId(), harborPlan.bottomHeightFieldId())) {
                requireField(fieldsById, fieldId);
                if (!plan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException(
                            "harbor basin field is not provided by feature module: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.HARBOR_BASIN)
                    != harborBasinPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "harbor basin plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (BreakwaterHarborPlanV2 breakwaterPlan : breakwaterHarborPlans) {
            FeaturePlan plan = plansById.get(breakwaterPlan.featureId());
            CoastalFeaturePlanV2 coastalPlan = coastalPlansById.get(breakwaterPlan.featureId());
            HarborBasinPlanV2 basinPlan = harborBasinPlansById.get(breakwaterPlan.basinFeatureId());
            if (plan == null || plan.kind() != TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR
                    || coastalPlan == null
                    || coastalPlan.kind() != TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR
                    || basinPlan == null
                    || !plan.relationIds().contains(breakwaterPlan.enclosureRelationId())) {
                throw new IllegalArgumentException(
                        "breakwater plan does not match its feature or basin plan: " + breakwaterPlan.featureId());
            }
            for (String fieldId : List.of(
                    breakwaterPlan.regionFieldId(), breakwaterPlan.armIndexFieldId(),
                    breakwaterPlan.topHeightFieldId(), breakwaterPlan.bottomHeightFieldId())) {
                requireField(fieldsById, fieldId);
                if (!plan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException(
                            "breakwater field is not provided by feature module: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR)
                    != breakwaterPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "breakwater plan presence does not match feature kind: " + plan.featureId());
            }
        }
        long maximumX = Math.multiplyExact((long) space.bounds().width() - 1L, TerrainIntentV2.FIXED_SCALE);
        long maximumZ = Math.multiplyExact((long) space.bounds().length() - 1L, TerrainIntentV2.FIXED_SCALE);
        for (RockyCapePlanV2 capePlan : rockyCapePlans) {
            FeaturePlan plan = plansById.get(capePlan.featureId());
            CoastalFeaturePlanV2 coastalPlan = coastalPlansById.get(capePlan.featureId());
            if (plan == null || plan.kind() != TerrainIntentV2.FeatureKind.ROCKY_CAPE
                    || coastalPlan == null || coastalPlan.kind() != TerrainIntentV2.FeatureKind.ROCKY_CAPE) {
                throw new IllegalArgumentException(
                        "rocky cape plan does not match its feature plan: " + capePlan.featureId());
            }
            for (String fieldId : List.of(
                    capePlan.regionFieldId(), capePlan.surfaceHeightFieldId(),
                    capePlan.rockExposureFieldId(), capePlan.descriptorIndexFieldId())) {
                requireField(fieldsById, fieldId);
                if (!plan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException(
                            "rocky cape field is not provided by feature module: " + fieldId);
                }
            }
            capePlan.channels().forEach(channel -> {
                requireBlockPoint(channel.mouth(), maximumX, maximumZ);
                requireBlockPoint(channel.inlandEnd(), maximumX, maximumZ);
            });
            capePlan.seaStacks().forEach(stack -> requireBlockPoint(stack.center(), maximumX, maximumZ));
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.ROCKY_CAPE)
                    != capePlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "rocky cape plan presence does not match feature kind: " + plan.featureId());
            }
        }
        if (coastalPlans.isEmpty() != transitionPlansById.isEmpty()
                || transitionPlansById.size() > 1) {
            throw new IllegalArgumentException("coastal transition plan presence does not match coastal features");
        }
        for (CoastalTransitionPlanV2 transitionPlan : coastalTransitionPlans) {
            ModuleDescriptorV2 module = modulesById.get(transitionPlan.moduleId());
            if (module == null || !module.moduleVersion().equals(transitionPlan.moduleVersion())
                    || !module.requiredFields().equals(transitionPlan.inputFieldIds())) {
                throw new IllegalArgumentException("coastal transition module contract mismatch");
            }
            List<String> outputs = List.of(
                    transitionPlan.landWaterFieldId(), transitionPlan.surfaceHeightFieldId(),
                    transitionPlan.ownerIndexFieldId(), transitionPlan.blendWeightFieldId(),
                    transitionPlan.conflictFieldId()).stream().sorted().toList();
            if (!module.providedFields().equals(outputs)) {
                throw new IllegalArgumentException("coastal transition output fields do not match module");
            }
            for (String fieldId : transitionPlan.inputFieldIds()) requireField(fieldsById, fieldId);
            for (String fieldId : outputs) {
                requireField(fieldsById, fieldId);
                List<FieldOwnership> owners = ownersByField.getOrDefault(fieldId, List.of());
                if (owners.size() != 1 || !owners.getFirst().moduleId().equals(transitionPlan.moduleId())
                        || owners.getFirst().mergeOperator() != ModuleDescriptorV2.MergeOperator.SINGLE_OWNER) {
                    throw new IllegalArgumentException("coastal transition output ownership mismatch: " + fieldId);
                }
            }
            if (transitionPlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException("coastal transition support exceeds Blueprint halo");
            }
            if (transitionPlan.contributors().size() != coastalPlans.size()) {
                throw new IllegalArgumentException("coastal transition contributors do not cover coastal plans");
            }
            for (CoastalTransitionPlanV2.Contributor contributor : transitionPlan.contributors()) {
                FeaturePlan featurePlan = plansById.get(contributor.featureId());
                if (featurePlan == null || featurePlan.kind() != contributor.kind()
                        || featurePlan.priority() != contributor.priority()
                        || !coastalPlansById.containsKey(contributor.featureId())) {
                    throw new IllegalArgumentException(
                            "coastal transition contributor does not match feature plan: " + contributor.featureId());
                }
            }
            for (CoastalTransitionPlanV2.Interaction interaction : transitionPlan.interactions()) {
                FeaturePlan first = plansById.get(interaction.firstFeatureId());
                FeaturePlan second = plansById.get(interaction.secondFeatureId());
                if (first == null || second == null
                        || !first.relationIds().contains(interaction.relationId())
                        || !second.relationIds().contains(interaction.relationId())) {
                    throw new IllegalArgumentException(
                            "coastal transition interaction is not shared by both feature plans: "
                                    + interaction.relationId());
                }
            }
        }
        for (MeanderingRiverPlanV2 riverPlan : meanderingRiverPlans) {
            FeaturePlan featurePlan = plansById.get(riverPlan.featureId());
            if (featurePlan == null || !isRiverFamily(featurePlan.kind())) {
                throw new IllegalArgumentException(
                        "meandering river plan does not match feature plan: " + riverPlan.featureId());
            }
            if (riverPlan.width() != space.bounds().width()
                    || riverPlan.length() != space.bounds().length()
                    || riverPlan.minY() != space.bounds().minY()
                    || riverPlan.maxY() != space.bounds().maxY()
                    || riverPlan.waterLevel() != space.bounds().waterLevel()) {
                throw new IllegalArgumentException(
                        "meandering river plan bounds mismatch: " + riverPlan.featureId());
            }
            if (riverPlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException(
                        "meandering river support exceeds Blueprint halo: " + riverPlan.featureId());
            }
            for (String fieldId : List.of(
                    riverPlan.channelMaskFieldId(),
                    riverPlan.bankMaskFieldId(),
                    riverPlan.floodplainMaskFieldId(),
                    riverPlan.meanderCorridorFieldId(),
                    riverPlan.localWidthFieldId(),
                    riverPlan.dischargeIndexFieldId(),
                    riverPlan.bedElevationFieldId(),
                    riverPlan.waterSurfaceFieldId(),
                    riverPlan.waterDepthFieldId(),
                    riverPlan.waterBodyIdFieldId())) {
                if (!fieldsById.containsKey(fieldId)) {
                    throw new IllegalArgumentException(
                            "meandering river field is missing from Blueprint: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if (isRiverFamily(plan.kind()) != riverPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "meandering river plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (LakePlanV2 lakePlan : lakePlans) {
            FeaturePlan featurePlan = plansById.get(lakePlan.featureId());
            if (featurePlan == null || featurePlan.kind() != TerrainIntentV2.FeatureKind.LAKE) {
                throw new IllegalArgumentException(
                        "lake plan does not match feature plan: " + lakePlan.featureId());
            }
            if (lakePlan.width() != space.bounds().width()
                    || lakePlan.length() != space.bounds().length()
                    || lakePlan.minY() != space.bounds().minY()
                    || lakePlan.maxY() != space.bounds().maxY()
                    || lakePlan.waterLevel() != space.bounds().waterLevel()) {
                throw new IllegalArgumentException(
                        "lake plan bounds mismatch: " + lakePlan.featureId());
            }
            if (lakePlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException(
                        "lake support exceeds Blueprint halo: " + lakePlan.featureId());
            }
            for (String fieldId : List.of(
                    lakePlan.basinMaskFieldId(),
                    lakePlan.rimMaskFieldId(),
                    lakePlan.spillwayMaskFieldId(),
                    lakePlan.depthFieldId(),
                    lakePlan.floorHeightFieldId(),
                    lakePlan.surfaceFieldId(),
                    lakePlan.bedElevationFieldId(),
                    lakePlan.waterSurfaceFieldId(),
                    lakePlan.waterDepthFieldId(),
                    lakePlan.waterBodyIdFieldId())) {
                if (!fieldsById.containsKey(fieldId)) {
                    throw new IllegalArgumentException(
                            "lake field is missing from Blueprint: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.LAKE)
                    != lakePlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "lake plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (CanyonPlanV2 canyonPlan : canyonPlans) {
            FeaturePlan featurePlan = plansById.get(canyonPlan.featureId());
            if (featurePlan == null || featurePlan.kind() != TerrainIntentV2.FeatureKind.CANYON) {
                throw new IllegalArgumentException(
                        "canyon plan does not match feature plan: " + canyonPlan.featureId());
            }
            MeanderingRiverPlanV2 riverPlan = riverPlansById.get(canyonPlan.riverFeatureId());
            if (riverPlan == null
                    || !riverPlan.geometryChecksum().equals(canyonPlan.riverGeometryChecksum())
                    || riverPlan.selectedBankfullWidthBlocks() > canyonPlan.selectedFloorWidthBlocks()) {
                throw new IllegalArgumentException(
                        "canyon river binding is invalid: " + canyonPlan.featureId());
            }
            if (canyonPlan.width() != space.bounds().width()
                    || canyonPlan.length() != space.bounds().length()
                    || canyonPlan.minY() != space.bounds().minY()
                    || canyonPlan.maxY() != space.bounds().maxY()
                    || canyonPlan.waterLevel() != space.bounds().waterLevel()) {
                throw new IllegalArgumentException(
                        "canyon plan bounds mismatch: " + canyonPlan.featureId());
            }
            if (canyonPlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException(
                        "canyon support exceeds Blueprint halo: " + canyonPlan.featureId());
            }
            for (String fieldId : List.of(
                    canyonPlan.canyonMaskFieldId(),
                    canyonPlan.floorMaskFieldId(),
                    canyonPlan.rimMaskFieldId(),
                    canyonPlan.terraceMaskFieldId(),
                    canyonPlan.surfaceHeightFieldId(),
                    canyonPlan.wallHeightFieldId(),
                    canyonPlan.bedElevationFieldId())) {
                if (!fieldsById.containsKey(fieldId)) {
                    throw new IllegalArgumentException(
                            "canyon field is missing from Blueprint: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.CANYON)
                    != canyonPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "canyon plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (WaterfallPlanV2 waterfallPlan : waterfallPlans) {
            FeaturePlan featurePlan = plansById.get(waterfallPlan.featureId());
            if (featurePlan == null || featurePlan.kind() != TerrainIntentV2.FeatureKind.WATERFALL) {
                throw new IllegalArgumentException(
                        "waterfall plan does not match feature plan: " + waterfallPlan.featureId());
            }
            MeanderingRiverPlanV2 riverPlan = riverPlansById.get(waterfallPlan.riverFeatureId());
            if (riverPlan == null
                    || !riverPlan.geometryChecksum().equals(waterfallPlan.riverGeometryChecksum())) {
                throw new IllegalArgumentException(
                        "waterfall river binding is invalid: " + waterfallPlan.featureId());
            }
            if (waterfallPlan.width() != space.bounds().width()
                    || waterfallPlan.length() != space.bounds().length()
                    || waterfallPlan.minY() != space.bounds().minY()
                    || waterfallPlan.maxY() != space.bounds().maxY()
                    || waterfallPlan.waterLevel() != space.bounds().waterLevel()) {
                throw new IllegalArgumentException(
                        "waterfall plan bounds mismatch: " + waterfallPlan.featureId());
            }
            if (waterfallPlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException(
                        "waterfall support exceeds Blueprint halo: " + waterfallPlan.featureId());
            }
            for (String fieldId : List.of(
                    waterfallPlan.lipMaskFieldId(),
                    waterfallPlan.baseMaskFieldId(),
                    waterfallPlan.plungePoolMaskFieldId(),
                    waterfallPlan.lipElevationFieldId(),
                    waterfallPlan.baseElevationFieldId(),
                    waterfallPlan.plungePoolFloorFieldId(),
                    waterfallPlan.bedElevationFieldId())) {
                if (!fieldsById.containsKey(fieldId)) {
                    throw new IllegalArgumentException(
                            "waterfall field is missing from Blueprint: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.WATERFALL)
                    != waterfallPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "waterfall plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (DeltaPlanV2 deltaPlan : deltaPlans) {
            FeaturePlan featurePlan = plansById.get(deltaPlan.featureId());
            if (featurePlan == null || featurePlan.kind() != TerrainIntentV2.FeatureKind.DELTA) {
                throw new IllegalArgumentException(
                        "delta plan does not match feature plan: " + deltaPlan.featureId());
            }
            MeanderingRiverPlanV2 riverPlan = riverPlansById.get(deltaPlan.trunkRiverFeatureId());
            if (riverPlan == null
                    || !featurePlan.geometryChecksum().equals(deltaPlan.geometryChecksum())
                    || !riverPlan.reachId().equals(deltaPlan.trunkReachId())
                    || !riverPlan.geometryChecksum().equals(deltaPlan.riverGeometryChecksum())
                    || !featurePlan.relationIds().contains(deltaPlan.drainsToRelationId())
                    || !featurePlan.relationIds().contains(deltaPlan.emptiesIntoRelationId())) {
                throw new IllegalArgumentException(
                        "delta river/outlet binding is invalid: " + deltaPlan.featureId());
            }
            if (deltaPlan.width() != space.bounds().width()
                    || deltaPlan.length() != space.bounds().length()
                    || deltaPlan.minY() != space.bounds().minY()
                    || deltaPlan.maxY() != space.bounds().maxY()
                    || deltaPlan.waterLevel() != space.bounds().waterLevel()) {
                throw new IllegalArgumentException(
                        "delta plan bounds mismatch: " + deltaPlan.featureId());
            }
            if (deltaPlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException(
                        "delta support exceeds Blueprint halo: " + deltaPlan.featureId());
            }
            for (String fieldId : List.of(
                    deltaPlan.fanMaskFieldId(),
                    deltaPlan.channelMaskFieldId(),
                    deltaPlan.branchIndexFieldId(),
                    deltaPlan.fanSurfaceFieldId(),
                    deltaPlan.sandbarMaskFieldId(),
                    deltaPlan.shallowSeaDepthFieldId(),
                    deltaPlan.dischargeShareFieldId())) {
                if (!fieldsById.containsKey(fieldId) || !featurePlan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException("delta field is missing from Blueprint: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.DELTA)
                    != deltaPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "delta plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (TidalChannelPlanV2 tidalPlan : tidalChannelPlans) {
            FeaturePlan featurePlan = plansById.get(tidalPlan.featureId());
            if (featurePlan == null || featurePlan.kind() != TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK) {
                throw new IllegalArgumentException(
                        "tidal plan does not match feature plan: " + tidalPlan.featureId());
            }
            if (!featurePlan.geometryChecksum().equals(tidalPlan.geometryChecksum())
                    || !featurePlan.relationIds().contains(tidalPlan.emptiesIntoRelationId())) {
                throw new IllegalArgumentException(
                        "tidal outlet binding is invalid: " + tidalPlan.featureId());
            }
            if (tidalPlan.wetlandChildPlanHook() != null) {
                TidalChannelPlanV2.WetlandChildPlanHook hook = tidalPlan.wetlandChildPlanHook();
                if (!featurePlan.relationIds().contains(hook.withinRelationId())
                        || !plansById.containsKey(hook.wetlandFeatureId())
                        || plansById.get(hook.wetlandFeatureId()).kind()
                        != TerrainIntentV2.FeatureKind.MANGROVE_WETLAND) {
                    throw new IllegalArgumentException(
                            "tidal wetland child-plan hook is invalid: " + tidalPlan.featureId());
                }
            }
            if (tidalPlan.width() != space.bounds().width()
                    || tidalPlan.length() != space.bounds().length()
                    || tidalPlan.minY() != space.bounds().minY()
                    || tidalPlan.maxY() != space.bounds().maxY()
                    || tidalPlan.waterLevel() != space.bounds().waterLevel()) {
                throw new IllegalArgumentException(
                        "tidal plan bounds mismatch: " + tidalPlan.featureId());
            }
            if (tidalPlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException(
                        "tidal support exceeds Blueprint halo: " + tidalPlan.featureId());
            }
            for (String fieldId : List.of(
                    tidalPlan.channelMaskFieldId(),
                    tidalPlan.branchIndexFieldId(),
                    tidalPlan.depthCorridorFieldId(),
                    tidalPlan.marineConnectionFieldId())) {
                if (!fieldsById.containsKey(fieldId) || !featurePlan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException("tidal field is missing from Blueprint: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK)
                    != tidalPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "tidal plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (MangroveWetlandPlanV2 mangrovePlan : mangroveWetlandPlans) {
            FeaturePlan featurePlan = plansById.get(mangrovePlan.featureId());
            if (featurePlan == null || featurePlan.kind() != TerrainIntentV2.FeatureKind.MANGROVE_WETLAND
                    || !featurePlan.geometryChecksum().equals(mangrovePlan.geometryChecksum())) {
                throw new IllegalArgumentException(
                        "mangrove plan does not match feature plan: " + mangrovePlan.featureId());
            }
            if (mangrovePlan.tidalNetworkHook() != null) {
                MangroveWetlandPlanV2.TidalNetworkPlanHook hook = mangrovePlan.tidalNetworkHook();
                if (!featurePlan.relationIds().contains(hook.withinRelationId())
                        || !tidalPlansById.containsKey(hook.tidalFeatureId())
                        || plansById.get(hook.tidalFeatureId()).kind()
                        != TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK) {
                    throw new IllegalArgumentException(
                            "mangrove tidal hook is invalid: " + mangrovePlan.featureId());
                }
            }
            if (mangrovePlan.width() != space.bounds().width()
                    || mangrovePlan.length() != space.bounds().length()
                    || mangrovePlan.minY() != space.bounds().minY()
                    || mangrovePlan.maxY() != space.bounds().maxY()
                    || mangrovePlan.waterLevel() != space.bounds().waterLevel()) {
                throw new IllegalArgumentException(
                        "mangrove plan bounds mismatch: " + mangrovePlan.featureId());
            }
            if (mangrovePlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException(
                        "mangrove support exceeds Blueprint halo: " + mangrovePlan.featureId());
            }
            for (String fieldId : List.of(
                    mangrovePlan.wetlandMaskFieldId(),
                    mangrovePlan.surfaceHeightFieldId(),
                    mangrovePlan.openWaterGapFieldId(),
                    mangrovePlan.substrateClassFieldId(),
                    mangrovePlan.microReliefFieldId())) {
                if (!fieldsById.containsKey(fieldId) || !featurePlan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException("mangrove field is missing from Blueprint: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.MANGROVE_WETLAND)
                    != mangrovePlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "mangrove plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (CoralReefPlanV2 coralPlan : coralReefPlans) {
            FeaturePlan featurePlan = plansById.get(coralPlan.featureId());
            if (featurePlan == null || featurePlan.kind() != TerrainIntentV2.FeatureKind.CORAL_REEF
                    || !featurePlan.geometryChecksum().equals(coralPlan.geometryChecksum())) {
                throw new IllegalArgumentException(
                        "coral reef plan does not match feature plan: " + coralPlan.featureId());
            }
            CoralReefPlanV2.LagoonPlanHook lagoonHook = coralPlan.lagoonPlanHook();
            if (!featurePlan.relationIds().contains(lagoonHook.enclosedByRelationId())
                    || !plansById.containsKey(lagoonHook.lagoonFeatureId())
                    || plansById.get(lagoonHook.lagoonFeatureId()).kind()
                    != TerrainIntentV2.FeatureKind.LAGOON) {
                throw new IllegalArgumentException(
                        "coral reef lagoon hook is invalid: " + coralPlan.featureId());
            }
            for (CoralReefPlanV2.ReefPassPlanHook passHook : coralPlan.passHooks()) {
                FeaturePlan passPlan = plansById.get(passHook.passFeatureId());
                if (!featurePlan.relationIds().contains(passHook.carvesThroughRelationId())
                        || passPlan == null
                        || passPlan.kind() != TerrainIntentV2.FeatureKind.REEF_PASS
                        || !passPlan.relationIds().contains(passHook.carvesThroughRelationId())
                        || !passPlan.relationIds().contains(passHook.connectsToRelationId())) {
                    throw new IllegalArgumentException(
                            "coral reef pass hook is invalid: " + coralPlan.featureId());
                }
            }
            if (coralPlan.width() != space.bounds().width()
                    || coralPlan.length() != space.bounds().length()
                    || coralPlan.minY() != space.bounds().minY()
                    || coralPlan.maxY() != space.bounds().maxY()
                    || coralPlan.waterLevel() != space.bounds().waterLevel()) {
                throw new IllegalArgumentException(
                        "coral reef plan bounds mismatch: " + coralPlan.featureId());
            }
            if (coralPlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException(
                        "coral reef support exceeds Blueprint halo: " + coralPlan.featureId());
            }
            for (String fieldId : List.of(
                    coralPlan.reefMaskFieldId(),
                    coralPlan.crestDepthFieldId(),
                    coralPlan.lagoonDepthFieldId(),
                    coralPlan.passCorridorFieldId(),
                    coralPlan.marineConnectionFieldId())) {
                if (!fieldsById.containsKey(fieldId) || !featurePlan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException("coral reef field is missing from Blueprint: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.CORAL_REEF)
                    != coralReefPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "coral reef plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (FjordPlanV2 fjordPlan : fjordPlans) {
            FeaturePlan featurePlan = plansById.get(fjordPlan.featureId());
            if (featurePlan == null || featurePlan.kind() != TerrainIntentV2.FeatureKind.FJORD
                    || !featurePlan.geometryChecksum().equals(fjordPlan.geometryChecksum())
                    || !featurePlan.relationIds().contains(fjordPlan.emptiesIntoRelationId())) {
                throw new IllegalArgumentException("fjord plan binding is invalid: " + fjordPlan.featureId());
            }
            if (fjordPlan.glacialWallPlanHook() != null) {
                FjordPlanV2.GlacialWallPlanHook hook = fjordPlan.glacialWallPlanHook();
                if (!featurePlan.relationIds().contains(hook.flanksRelationId())
                        || !plansById.containsKey(hook.wallFeatureId())
                        || plansById.get(hook.wallFeatureId()).kind() != TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE) {
                    throw new IllegalArgumentException("fjord glacial wall hook is invalid: " + fjordPlan.featureId());
                }
            }
            if (fjordPlan.width() != space.bounds().width() || fjordPlan.length() != space.bounds().length()
                    || fjordPlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException("fjord plan bounds or halo mismatch: " + fjordPlan.featureId());
            }
            for (String fieldId : List.of(fjordPlan.channelMaskFieldId(), fjordPlan.floorMaskFieldId(),
                    fjordPlan.sidewallMaskFieldId(), fjordPlan.thalwegDepthFieldId(), fjordPlan.sidewallReliefFieldId())) {
                if (!fieldsById.containsKey(fieldId) || !featurePlan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException("fjord field is missing from Blueprint: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.FJORD) != fjordPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException("fjord plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (MountainPlanV2 mountainPlan : mountainPlans) {
            FeaturePlan featurePlan = plansById.get(mountainPlan.featureId());
            boolean alpine = featurePlan != null
                    && featurePlan.kind() == TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE
                    && mountainPlan.variant() == TerrainIntentV2.MountainVariant.ALPINE;
            boolean glacial = featurePlan != null
                    && featurePlan.kind() == TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE
                    && mountainPlan.variant() == TerrainIntentV2.MountainVariant.GLACIAL;
            if ((!alpine && !glacial)
                    || !featurePlan.geometryChecksum().equals(mountainPlan.geometryChecksum())) {
                throw new IllegalArgumentException(
                        "mountain plan binding is invalid: " + mountainPlan.featureId());
            }
            if (mountainPlan.width() != space.bounds().width()
                    || mountainPlan.length() != space.bounds().length()
                    || mountainPlan.minY() != space.bounds().minY()
                    || mountainPlan.maxY() != space.bounds().maxY()
                    || mountainPlan.waterLevel() != space.bounds().waterLevel()
                    || mountainPlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException(
                        "mountain plan bounds or halo mismatch: " + mountainPlan.featureId());
            }
            for (String fieldId : List.of(
                    mountainPlan.ridgeMaskFieldId(),
                    mountainPlan.peakMaskFieldId(),
                    mountainPlan.saddleMaskFieldId(),
                    mountainPlan.spurMaskFieldId(),
                    mountainPlan.provisionalSurfaceFieldId(),
                    mountainPlan.ridgeSegmentIdFieldId())) {
                if (!fieldsById.containsKey(fieldId) || !featurePlan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException("mountain field is missing from Blueprint: " + fieldId);
                }
            }
        }
        for (FeaturePlan plan : plans) {
            boolean mountainKind = plan.kind() == TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE
                    || plan.kind() == TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE;
            if (mountainKind != mountainPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "mountain plan presence does not match feature kind: " + plan.featureId());
            }
        }
        for (VolcanicPlanV2 volcanicPlan : volcanicPlans) {
            FeaturePlan featurePlan = plansById.get(volcanicPlan.featureId());
            if (featurePlan == null
                    || featurePlan.kind() != TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO
                    || !featurePlan.geometryChecksum().equals(volcanicPlan.geometryChecksum())) {
                throw new IllegalArgumentException(
                        "volcanic plan binding is invalid: " + volcanicPlan.featureId());
            }
            if (volcanicPlan.width() != space.bounds().width()
                    || volcanicPlan.length() != space.bounds().length()
                    || volcanicPlan.minY() != space.bounds().minY()
                    || volcanicPlan.maxY() != space.bounds().maxY()
                    || volcanicPlan.waterLevel() != space.bounds().waterLevel()
                    || volcanicPlan.supportRadiusXZ() > space.tilePolicy().maximumHaloXZ()) {
                throw new IllegalArgumentException(
                        "volcanic plan bounds or halo mismatch: " + volcanicPlan.featureId());
            }
            for (String fieldId : List.of(
                    volcanicPlan.islandMaskFieldId(), volcanicPlan.islandIndexFieldId(),
                    volcanicPlan.summitReliefFieldId(), volcanicPlan.submarineSaddleMaskFieldId(),
                    volcanicPlan.radialDrainageFieldId(), volcanicPlan.provisionalSurfaceFieldId())) {
                if (!fieldsById.containsKey(fieldId) || !featurePlan.providedFields().contains(fieldId)) {
                    throw new IllegalArgumentException("volcanic field is missing from Blueprint: " + fieldId);
                }
            }
            if (volcanicPlan.calderaPlanHook() != null
                    && (!plansById.containsKey(volcanicPlan.calderaPlanHook().calderaFeatureId())
                    || plansById.get(volcanicPlan.calderaPlanHook().calderaFeatureId()).kind()
                    != TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA
                    || !featurePlan.relationIds().contains(volcanicPlan.calderaPlanHook().withinRelationId()))) {
                throw new IllegalArgumentException("volcanic caldera hook is invalid: " + volcanicPlan.featureId());
            }
            if (volcanicPlan.lavaPlanHook() != null
                    && (!plansById.containsKey(volcanicPlan.lavaPlanHook().lavaFeatureId())
                    || plansById.get(volcanicPlan.lavaPlanHook().lavaFeatureId()).kind()
                    != TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD)) {
                throw new IllegalArgumentException("volcanic lava hook is invalid: " + volcanicPlan.featureId());
            }
        }
        for (FeaturePlan plan : plans) {
            if ((plan.kind() == TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO)
                    != volcanicPlansById.containsKey(plan.featureId())) {
                throw new IllegalArgumentException(
                        "volcanic plan presence does not match feature kind: " + plan.featureId());
            }
        }
        validateGeologyPlan(space, geologyPlan, hydrologyPlan, modulesById, fieldsById, ownersByField, budgets);
        validateLithologyPlan(lithologyPlan, geologyPlan, budgets);
        validateStrataPlan(strataPlan, geologyPlan, lithologyPlan, budgets);
        validateClimatePlan(space, climatePlan, hydrologyPlan, modulesById, fieldsById, ownersByField, budgets);
        validateWaterConditionPlan(
                space, waterConditionPlan, climatePlan, hydrologyPlan, modulesById, fieldsById, ownersByField, budgets);
        validateHydrologyPlan(space, hydrologyPlan, modulesById, fieldsById, ownersByField, budgets);
        validateHydrologyReconciliationPlan(
                hydrologyPlan, hydrologyReconciliationPlan, modulesById, plansById, budgets);
    }

    private static void validateGeologyPlan(
            Space space,
            GeologyPlanV2 plan,
            HydrologyPlanV2 hydrologyPlan,
            Map<String, ModuleDescriptorV2> modulesById,
            Map<String, FieldDescriptor> fieldsById,
            Map<String, List<FieldOwnership>> ownersByField,
            ResourceBudget budgets
    ) {
        ModuleDescriptorV2 module = modulesById.get(plan.moduleId());
        List<String> planFieldIds = plan.fields().stream()
                .map(GeologyPlanV2.FieldBinding::fieldId).sorted().toList();
        if (module == null
                || !module.moduleVersion().equals(plan.moduleVersion())
                || !module.stageId().equals(plan.stageId())
                || !module.requiredFields().isEmpty()
                || !module.providedFields().equals(planFieldIds)) {
            throw new IllegalArgumentException("geology plan module contract mismatch");
        }
        if (!plan.priorReplacement().sourcePriorChecksum()
                .equals(hydrologyPlan.fixedPriors().priorChecksum())) {
            throw new IllegalArgumentException("geology plan does not replace the frozen hydrology prior");
        }
        if (plan.width() != space.bounds().width() || plan.length() != space.bounds().length()) {
            throw new IllegalArgumentException("geology plan dimensions do not match Blueprint bounds");
        }
        for (GeologyPlanV2.FieldBinding binding : plan.fields()) {
            FieldDescriptor field = fieldsById.get(binding.fieldId());
            FieldSemantic semantic = FieldSemantic.valueOf("GEOLOGY_" + binding.semantic().name());
            List<FieldOwnership> owners = ownersByField.getOrDefault(binding.fieldId(), List.of());
            if (field == null || field.semantic() != semantic || field.valueType() != FieldValueType.U16
                    || field.width() != plan.width() || field.length() != plan.length()
                    || field.sampling() != FieldSampling.NEAREST
                    || !binding.ownerModuleId().equals(plan.moduleId())
                    || binding.ownership() != GeologyPlanV2.Ownership.SINGLE_OWNER
                    || owners.size() != 1
                    || !owners.getFirst().moduleId().equals(plan.moduleId())
                    || owners.getFirst().mergeOperator() != ModuleDescriptorV2.MergeOperator.SINGLE_OWNER) {
                throw new IllegalArgumentException("geology field contract mismatch: " + binding.fieldId());
            }
        }
        long retainedAndWorking = Math.addExact(
                plan.budget().estimatedRetainedBytes(), plan.budget().maximumWorkingBytes());
        if (plan.fields().size() > budgets.maximumFields()
                || plan.budget().estimatedCpuWorkUnits() > budgets.maximumCpuWorkUnits()
                || retainedAndWorking > budgets.maximumResidentBytes()
                || plan.budget().estimatedArtifactBytes() > budgets.maximumArtifactBytes()) {
            throw new IllegalArgumentException("geology plan exceeds Blueprint resource budget");
        }
    }

    private static void validateLithologyPlan(
            LithologyPlanV2 plan,
            GeologyPlanV2 geologyPlan,
            ResourceBudget budgets
    ) {
        plan.requireGeologyPlan(geologyPlan);
        long requiredRetained = Math.addExact(
                plan.budget().estimatedRetainedBytes(), plan.catalog().budget().maximumCanonicalBytes());
        if (plan.provinceAssignments().size() > plan.budget().maximumAssignments()
                || plan.budget().estimatedCpuWorkUnits() > budgets.maximumCpuWorkUnits()
                || requiredRetained > budgets.maximumResidentBytes()) {
            throw new IllegalArgumentException("lithology plan exceeds Blueprint resource budget");
        }
    }

    private static void validateStrataPlan(
            StrataPlanV2 plan,
            GeologyPlanV2 geologyPlan,
            LithologyPlanV2 lithologyPlan,
            ResourceBudget budgets
    ) {
        plan.requireLithologyPlan(geologyPlan, lithologyPlan);
        if (plan.profiles().size() > plan.budget().maximumProfiles()
                || plan.budget().estimatedCpuWorkUnits() > budgets.maximumCpuWorkUnits()
                || plan.budget().estimatedRetainedBytes() > budgets.maximumResidentBytes()) {
            throw new IllegalArgumentException("strata plan exceeds Blueprint resource budget");
        }
    }

    private static void validateClimatePlan(
            Space space,
            ClimatePlanV2 plan,
            HydrologyPlanV2 hydrologyPlan,
            Map<String, ModuleDescriptorV2> modulesById,
            Map<String, FieldDescriptor> fieldsById,
            Map<String, List<FieldOwnership>> ownersByField,
            ResourceBudget budgets
    ) {
        plan.requireHydrologyPlan(hydrologyPlan);
        ModuleDescriptorV2 priorModule = modulesById.get(plan.priorModuleId());
        ModuleDescriptorV2 finalModule = modulesById.get(plan.finalModuleId());
        List<String> priorFieldIds = plan.fields().stream()
                .filter(binding -> binding.phase() == ClimatePlanV2.FieldPhase.PRIOR)
                .map(ClimatePlanV2.FieldBinding::fieldId).sorted().toList();
        List<String> finalFieldIds = plan.fields().stream()
                .filter(binding -> binding.phase() == ClimatePlanV2.FieldPhase.FINAL)
                .map(ClimatePlanV2.FieldBinding::fieldId).sorted().toList();
        if (priorModule == null || finalModule == null
                || !priorModule.moduleVersion().equals(plan.priorModuleVersion())
                || !priorModule.stageId().equals(plan.priorStageId())
                || !priorModule.requiredFields().isEmpty()
                || !priorModule.providedFields().equals(priorFieldIds)
                || !finalModule.moduleVersion().equals(plan.finalModuleVersion())
                || !finalModule.stageId().equals(plan.finalStageId())
                || !finalModule.providedFields().equals(finalFieldIds)
                || !finalModule.requiredFields().containsAll(priorFieldIds)) {
            throw new IllegalArgumentException("climate plan module contract mismatch");
        }
        if (plan.width() != space.bounds().width() || plan.length() != space.bounds().length()
                || plan.minY() != space.bounds().minY() || plan.maxY() != space.bounds().maxY()) {
            throw new IllegalArgumentException("climate plan dimensions do not match Blueprint bounds");
        }
        for (ClimatePlanV2.FieldBinding binding : plan.fields()) {
            FieldDescriptor field = fieldsById.get(binding.fieldId());
            FieldSemantic semantic = FieldSemantic.valueOf("CLIMATE_" + binding.semantic().name());
            FieldValueType valueType = FieldValueType.valueOf(binding.valueType().name());
            List<FieldOwnership> owners = ownersByField.getOrDefault(binding.fieldId(), List.of());
            if (field == null || field.semantic() != semantic || field.valueType() != valueType
                    || field.width() != plan.width() || field.length() != plan.length()
                    || field.sampling() != FieldSampling.BILINEAR_FIXED
                    || field.storage() != FieldStorage.DESCRIPTOR_ONLY
                    || owners.size() != 1
                    || !owners.getFirst().moduleId().equals(binding.ownerModuleId())
                    || owners.getFirst().mergeOperator() != ModuleDescriptorV2.MergeOperator.SINGLE_OWNER) {
                throw new IllegalArgumentException("climate field contract mismatch: " + binding.fieldId());
            }
        }
        long retainedAndWorking = Math.addExact(
                plan.budget().estimatedRetainedBytes(), plan.budget().maximumWorkingBytes());
        if (plan.fields().size() > budgets.maximumFields()
                || plan.budget().estimatedCpuWorkUnits() > budgets.maximumCpuWorkUnits()
                || retainedAndWorking > budgets.maximumResidentBytes()) {
            throw new IllegalArgumentException("climate plan exceeds Blueprint resource budget");
        }
    }

    private static void validateWaterConditionPlan(
            Space space,
            WaterConditionPlanV2 plan,
            ClimatePlanV2 climatePlan,
            HydrologyPlanV2 hydrologyPlan,
            Map<String, ModuleDescriptorV2> modulesById,
            Map<String, FieldDescriptor> fieldsById,
            Map<String, List<FieldOwnership>> ownersByField,
            ResourceBudget budgets
    ) {
        plan.requireHydrologyPlan(hydrologyPlan);
        plan.requireClimatePlan(climatePlan);
        ModuleDescriptorV2 module = modulesById.get(plan.moduleId());
        List<String> planFieldIds = plan.fields().stream()
                .map(WaterConditionPlanV2.FieldBinding::fieldId).sorted().toList();
        if (module == null
                || !module.moduleVersion().equals(plan.moduleVersion())
                || !module.stageId().equals(plan.stageId())
                || !module.providedFields().equals(planFieldIds)
                || !module.requiredFields().contains(plan.climateBinding().moistureFieldId())) {
            throw new IllegalArgumentException("water-condition plan module contract mismatch");
        }
        if (plan.width() != space.bounds().width() || plan.length() != space.bounds().length()
                || plan.minY() != space.bounds().minY() || plan.maxY() != space.bounds().maxY()
                || plan.referenceWaterY() != space.bounds().waterLevel()) {
            throw new IllegalArgumentException("water-condition plan dimensions do not match Blueprint bounds");
        }
        for (WaterConditionPlanV2.FieldBinding binding : plan.fields()) {
            FieldDescriptor field = fieldsById.get(binding.fieldId());
            FieldSemantic semantic = FieldSemantic.valueOf("WATER_CONDITION_" + binding.semantic().name());
            FieldValueType valueType = FieldValueType.valueOf(binding.valueType().name());
            List<FieldOwnership> owners = ownersByField.getOrDefault(binding.fieldId(), List.of());
            if (field == null || field.semantic() != semantic || field.valueType() != valueType
                    || field.width() != plan.width() || field.length() != plan.length()
                    || field.sampling() != FieldSampling.NEAREST
                    || field.storage() != FieldStorage.DESCRIPTOR_ONLY
                    || owners.size() != 1
                    || !owners.getFirst().moduleId().equals(binding.ownerModuleId())
                    || owners.getFirst().mergeOperator() != ModuleDescriptorV2.MergeOperator.SINGLE_OWNER) {
                throw new IllegalArgumentException("water-condition field contract mismatch: " + binding.fieldId());
            }
        }
        long retainedAndWorking = Math.addExact(
                plan.budget().estimatedRetainedBytes(), plan.budget().maximumWorkingBytes());
        if (plan.fields().size() > budgets.maximumFields()
                || plan.budget().estimatedCpuWorkUnits() > budgets.maximumCpuWorkUnits()
                || retainedAndWorking > budgets.maximumResidentBytes()
                || plan.kernel().maximumDistanceBlocks() > budgets.maximumHaloXZ()) {
            throw new IllegalArgumentException("water-condition plan exceeds Blueprint resource budget");
        }
    }

    private static void validateHydrologyReconciliationPlan(
            HydrologyPlanV2 hydrologyPlan,
            HydrologyReconciliationPlanV2 plan,
            Map<String, ModuleDescriptorV2> modulesById,
            Map<String, FeaturePlan> featurePlansById,
            ResourceBudget budgets
    ) {
        ModuleDescriptorV2 module = modulesById.get(plan.moduleId());
        if (module == null
                || !module.moduleVersion().equals(plan.moduleVersion())
                || !module.stageId().equals(plan.stageId())
                || !module.providedFields().isEmpty()
                || !module.fieldWrites().isEmpty()
                || !module.validatorCapabilities().contains("hydrology.reconciliation")) {
            throw new IllegalArgumentException("hydrology reconciliation module contract mismatch");
        }
        if (!plan.sourceHydrologyPlanChecksum().equals(hydrologyPlan.canonicalChecksum())) {
            throw new IllegalArgumentException("hydrology reconciliation source plan checksum mismatch");
        }
        if (plan.budget().estimatedWorkUnits() > budgets.maximumCpuWorkUnits()
                || plan.budget().estimatedWorkingBytes() > budgets.maximumResidentBytes()
                || plan.budget().estimatedArtifactBytes() > budgets.maximumArtifactBytes()) {
            throw new IllegalArgumentException("hydrology reconciliation exceeds Blueprint resource budget");
        }
        for (HydrologyReconciliationPlanV2.VariableDescriptor variable : plan.variables()) {
            FeaturePlan featurePlan = featurePlansById.get(variable.featureId());
            if (featurePlan == null || !reconciliationVariableMatchesFeature(variable.kind(), featurePlan.kind())) {
                throw new IllegalArgumentException(
                        "hydrology reconciliation variable does not match feature: " + variable.variableId());
            }
        }
        for (HydrologyReconciliationPlanV2.Constraint constraint : plan.constraints()) {
            FeaturePlan featurePlan = featurePlansById.get(constraint.featureId());
            if (featurePlan == null || !reconciliationConstraintMatchesFeature(constraint.kind(), featurePlan.kind())) {
                throw new IllegalArgumentException(
                        "hydrology reconciliation constraint does not match feature: " + constraint.constraintId());
            }
        }
    }

    /**
     * V2-15-10 / ADR 0039 Candidate A: {@code RIVER} compiles into the same
     * {@link MeanderingRiverPlanV2} shape as {@code MEANDERING_RIVER} (via
     * {@code MeanderingRiverSubtypeBridgeV2}), so both kinds own exactly one river-family plan.
     */
    private static boolean isRiverFamily(TerrainIntentV2.FeatureKind kind) {
        return kind == TerrainIntentV2.FeatureKind.MEANDERING_RIVER || kind == TerrainIntentV2.FeatureKind.RIVER;
    }

    private static boolean reconciliationVariableMatchesFeature(
            HydrologyReconciliationPlanV2.VariableKind variableKind,
            TerrainIntentV2.FeatureKind featureKind
    ) {
        return switch (variableKind) {
            case REACH_BED -> isRiverFamily(featureKind);
            case LAKE_SURFACE, LAKE_SPILL -> featureKind == TerrainIntentV2.FeatureKind.LAKE;
            case WATERFALL_LIP, WATERFALL_BASE -> featureKind == TerrainIntentV2.FeatureKind.WATERFALL;
            case MARINE_CONNECTION -> featureKind == TerrainIntentV2.FeatureKind.DELTA
                    || featureKind == TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK
                    || featureKind == TerrainIntentV2.FeatureKind.FJORD
                    || featureKind == TerrainIntentV2.FeatureKind.MANGROVE_WETLAND
                    || featureKind == TerrainIntentV2.FeatureKind.CORAL_REEF;
        };
    }

    private static boolean reconciliationConstraintMatchesFeature(
            HydrologyReconciliationPlanV2.ConstraintKind constraintKind,
            TerrainIntentV2.FeatureKind featureKind
    ) {
        return switch (constraintKind) {
            case REACH_BED -> isRiverFamily(featureKind);
            case LAKE_SPILL -> featureKind == TerrainIntentV2.FeatureKind.LAKE;
            case DELTA_MOUTH -> featureKind == TerrainIntentV2.FeatureKind.DELTA;
            case TIDAL_CONNECTION -> featureKind == TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK;
            case MANGROVE_TIDAL_LINK -> featureKind == TerrainIntentV2.FeatureKind.MANGROVE_WETLAND;
            case REEF_LAGOON_PASS -> featureKind == TerrainIntentV2.FeatureKind.CORAL_REEF;
            case FJORD_CONNECTION -> featureKind == TerrainIntentV2.FeatureKind.FJORD;
            case WATERFALL_LIP_BASE -> featureKind == TerrainIntentV2.FeatureKind.WATERFALL;
        };
    }

    private static void validateHydrologyPlan(
            Space space,
            HydrologyPlanV2 plan,
            Map<String, ModuleDescriptorV2> modulesById,
            Map<String, FieldDescriptor> fieldsById,
            Map<String, List<FieldOwnership>> ownersByField,
            ResourceBudget budgets
    ) {
        ModuleDescriptorV2 module = modulesById.get(plan.moduleId());
        List<String> planFieldIds = plan.fields().stream().map(HydrologyPlanV2.FieldBinding::fieldId).sorted().toList();
        if (module == null || !module.moduleVersion().equals(plan.moduleVersion())
                || !module.requiredFields().isEmpty()
                || !module.providedFields().equals(planFieldIds)) {
            throw new IllegalArgumentException("hydrology plan module contract mismatch");
        }
        for (HydrologyPlanV2.FieldBinding binding : plan.fields()) {
            FieldDescriptor field = fieldsById.get(binding.fieldId());
            FieldSemantic semantic = FieldSemantic.valueOf("HYDROLOGY_" + binding.semantic().name());
            FieldValueType valueType = FieldValueType.valueOf(binding.valueType().name());
            List<FieldOwnership> owners = ownersByField.getOrDefault(binding.fieldId(), List.of());
            if (field == null || field.semantic() != semantic || field.valueType() != valueType
                    || field.storage() != FieldStorage.DESCRIPTOR_ONLY
                    || !binding.ownerModuleId().equals(plan.moduleId())
                    || binding.ownership() != HydrologyPlanV2.Ownership.SINGLE_OWNER
                    || owners.size() != 1
                    || !owners.getFirst().moduleId().equals(plan.moduleId())
                    || owners.getFirst().mergeOperator() != ModuleDescriptorV2.MergeOperator.SINGLE_OWNER) {
                throw new IllegalArgumentException("hydrology field contract mismatch: " + binding.fieldId());
            }
        }
        long expectedCells = Math.multiplyExact((long) space.bounds().width(), space.bounds().length());
        if (plan.budget().globalCellCount() != expectedCells
                || plan.fields().size() > budgets.maximumFields()
                || plan.budget().estimatedCpuWorkUnits() > budgets.maximumCpuWorkUnits()
                || plan.budget().estimatedResidentBytes() > budgets.maximumResidentBytes()) {
            throw new IllegalArgumentException("hydrology plan exceeds Blueprint resource budget");
        }
        long maximumX = Math.multiplyExact((long) space.bounds().width() - 1L, TerrainIntentV2.FIXED_SCALE);
        long maximumZ = Math.multiplyExact((long) space.bounds().length() - 1L, TerrainIntentV2.FIXED_SCALE);
        long minimumY = Math.multiplyExact((long) space.bounds().minY(), TerrainIntentV2.FIXED_SCALE);
        long maximumY = Math.multiplyExact((long) space.bounds().maxY(), TerrainIntentV2.FIXED_SCALE);
        for (HydrologyPlanV2.HydrologyNode node : plan.nodes()) {
            if (node.xMillionths() > maximumX || node.zMillionths() > maximumZ
                    || node.bedYMillionths() < minimumY || node.bedYMillionths() > maximumY
                    || node.waterSurfaceYMillionths() < minimumY || node.waterSurfaceYMillionths() > maximumY) {
                throw new IllegalArgumentException("hydrology node exceeds Blueprint bounds: " + node.nodeId());
            }
        }
        for (HydrologyPlanV2.WaterBodyPlan waterBody : plan.waterBodies()) {
            if (waterBody.minimumSurfaceYMillionths() < minimumY
                    || waterBody.maximumSurfaceYMillionths() > maximumY) {
                throw new IllegalArgumentException(
                        "hydrology water body exceeds Blueprint vertical bounds: " + waterBody.waterBodyId());
            }
        }
    }

    private static void validateBlockCoordinates(Bounds bounds, CoastalFeaturePlanV2.BlockGeometry geometry) {
        long maximumX = Math.multiplyExact((long) bounds.width() - 1L, TerrainIntentV2.FIXED_SCALE);
        long maximumZ = Math.multiplyExact((long) bounds.length() - 1L, TerrainIntentV2.FIXED_SCALE);
        geometry.paths().stream().flatMap(path -> path.points().stream())
                .forEach(point -> requireBlockPoint(point, maximumX, maximumZ));
        geometry.rings().stream().flatMap(ring -> ring.points().stream())
                .forEach(point -> requireBlockPoint(point, maximumX, maximumZ));
    }

    private static void requireBlockPoint(
            CoastalFeaturePlanV2.BlockPoint point,
            long maximumX,
            long maximumZ
    ) {
        if (point.xMillionths() > maximumX || point.zMillionths() > maximumZ) {
            throw new IllegalArgumentException("coastal block coordinate exceeds Blueprint bounds");
        }
    }

    private static void requireField(Map<String, FieldDescriptor> fields, String fieldId) {
        if (!fields.containsKey(fieldId)) throw new IllegalArgumentException("unknown field: " + fieldId);
    }

    private static <T> Map<String, T> uniqueMap(
            List<T> values,
            java.util.function.Function<T, String> key,
            String kind
    ) {
        Map<String, T> result = new HashMap<>();
        for (T value : values) {
            String id = key.apply(value);
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("duplicate " + kind + " id: " + id);
            }
        }
        return result;
    }

    private static void requireAcyclic(Map<String, List<String>> graph, String name) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String node : graph.keySet()) {
            if (hasCycle(node, graph, visiting, visited)) {
                throw new IllegalArgumentException(name + " contains a cycle");
            }
        }
    }

    private static boolean hasCycle(
            String node,
            Map<String, List<String>> graph,
            Set<String> visiting,
            Set<String> visited
    ) {
        if (visited.contains(node)) return false;
        if (!visiting.add(node)) return true;
        for (String next : graph.getOrDefault(node, List.of())) {
            if (hasCycle(next, graph, visiting, visited)) return true;
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private static List<String> qualified(List<String> values, String field) {
        return V2Validation.sorted(values, field, 128, Comparator.naturalOrder()).stream()
                .map(value -> V2Validation.qualifiedId(value, field + " entry")).toList();
    }

    private static List<String> slugs(List<String> values, String field) {
        return V2Validation.sorted(values, field, 512, Comparator.naturalOrder()).stream()
                .map(value -> V2Validation.slug(value, field + " entry")).toList();
    }
}
