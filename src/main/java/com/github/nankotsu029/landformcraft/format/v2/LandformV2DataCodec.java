package com.github.nankotsu029.landformcraft.format.v2;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationRangeValleyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationFloodplainMarshValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationRockyCoastCliffValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationRiverValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FloodplainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationRiverGraphRolesValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.WaterfallChainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MarshPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.HillRangePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MountainRangePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RockyCoastPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SeaCliffPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SingleIslandPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ArchipelagoPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.VolcanicConePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationSingleIslandValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationArchipelagoValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationVolcanicConeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalShelfPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalSlopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationMacroLandWaterTopologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationOceanBasinValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationContinentalShelfValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationContinentalSlopeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SubmarineCanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationSubmarineCanyonValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.CaveEntrancePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationCaveEntranceValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationUndergroundRiverValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.UndergroundRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.LavaTubePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationLavaTubeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OxbowLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SpringPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationOxbowLakeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationSpringValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.GlacialIcePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.IceFjordPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.BarrierIslandPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AtollPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AdvancedIslandReefCatalogContractV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationAdvancedIslandReefValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationGlacialIceValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationGlacialDepositionValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationAdditionalMarineValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AbyssalPlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AdvancedRiverLakeSplitContractV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.DryLandModifierContractV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.EscarpmentPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationEscarpmentPlateauValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlateauPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SeamountPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MoraineFieldPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OutwashPlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PermafrostPlainProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SinkholePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstSpringPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstHydrologyGraphPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.CenotePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationKarstHydrologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ValleyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.feature.FeatureMaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeAabbIndexPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.LushCavePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.NaturalArchPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.SkyIslandGroupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.WaterfallVolumePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentEvidenceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSafetyStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementUndoPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementVerifyEvidenceV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.OverhangPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.SeaCavePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.UndergroundLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeTileCachePlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Strict codec for v2 diagnostic contracts. It does not publish a Release or invoke a generator. */
public final class LandformV2DataCodec {
    private static final String REQUEST_SCHEMA = "generation-request-v2.schema.json";
    private static final String INTENT_SCHEMA = "terrain-intent-v2.schema.json";
    private static final String BLUEPRINT_SCHEMA = "world-blueprint-v2.schema.json";
    private static final String CLIMATE_PLAN_SCHEMA = "climate-plan-v2.schema.json";
    private static final String WATER_CONDITION_PLAN_SCHEMA = "water-condition-plan-v2.schema.json";
    private static final String SNOW_PLAN_SCHEMA = "snow-plan-v2.schema.json";
    private static final String GEOLOGY_PLAN_SCHEMA = "geology-plan-v2.schema.json";
    private static final String LITHOLOGY_PLAN_SCHEMA = "lithology-plan-v2.schema.json";
    private static final String STRATA_PLAN_SCHEMA = "strata-plan-v2.schema.json";
    private static final String HYDROLOGY_PLAN_SCHEMA = "hydrology-plan-v2.schema.json";
    private static final String HYDROLOGY_RECONCILIATION_PLAN_SCHEMA =
            "hydrology-reconciliation-plan-v2.schema.json";
    private static final String MATERIAL_PROFILE_PLAN_SCHEMA = "material-profile-plan-v2.schema.json";
    private static final String MINECRAFT_PALETTE_PLAN_SCHEMA = "minecraft-palette-plan-v2.schema.json";
    private static final String ECOLOGY_PLAN_SCHEMA = "ecology-plan-v2.schema.json";
    private static final String SURFACE_FOUNDATION_PLAN_SCHEMA =
            "surface-foundation-plan-v2.schema.json";
    private static final String PLAIN_PLAN_SCHEMA = "plain-plan-v2.schema.json";
    private static final String HILL_RANGE_PLAN_SCHEMA = "hill-range-plan-v2.schema.json";
    private static final String MOUNTAIN_RANGE_PLAN_SCHEMA = "mountain-range-plan-v2.schema.json";
    private static final String VALLEY_PLAN_SCHEMA = "valley-plan-v2.schema.json";
    private static final String RIVER_PLAN_SCHEMA = "river-plan-v2.schema.json";
    private static final String FLOODPLAIN_PLAN_SCHEMA = "floodplain-plan-v2.schema.json";
    private static final String MARSH_PLAN_SCHEMA = "marsh-plan-v2.schema.json";
    private static final String ROCKY_COAST_PLAN_SCHEMA = "rocky-coast-plan-v2.schema.json";
    private static final String SEA_CLIFF_PLAN_SCHEMA = "sea-cliff-plan-v2.schema.json";
    private static final String SINGLE_ISLAND_PLAN_SCHEMA = "single-island-plan-v2.schema.json";
    private static final String ARCHIPELAGO_PLAN_SCHEMA = "archipelago-plan-v2.schema.json";
    private static final String VOLCANIC_CONE_PLAN_SCHEMA = "volcanic-cone-plan-v2.schema.json";
    private static final String FOUNDATION_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-validation-artifact-v2.schema.json";
    private static final String FOUNDATION_RANGE_VALLEY_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-range-valley-validation-artifact-v2.schema.json";
    private static final String FOUNDATION_RIVER_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-river-validation-artifact-v2.schema.json";
    private static final String FOUNDATION_FLOODPLAIN_MARSH_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-floodplain-marsh-validation-artifact-v2.schema.json";
    private static final String FOUNDATION_ROCKY_COAST_CLIFF_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-rocky-coast-cliff-validation-artifact-v2.schema.json";
    private static final String FOUNDATION_SINGLE_ISLAND_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-single-island-validation-artifact-v2.schema.json";
    private static final String FOUNDATION_ARCHIPELAGO_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-archipelago-validation-artifact-v2.schema.json";
    private static final String FOUNDATION_VOLCANIC_CONE_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-volcanic-cone-validation-artifact-v2.schema.json";
    private static final String OCEAN_BASIN_PLAN_SCHEMA = "ocean-basin-plan-v2.schema.json";
    private static final String CONTINENTAL_SHELF_PLAN_SCHEMA = "continental-shelf-plan-v2.schema.json";
    private static final String CONTINENTAL_SLOPE_PLAN_SCHEMA = "continental-slope-plan-v2.schema.json";
    private static final String FOUNDATION_OCEAN_BASIN_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-ocean-basin-validation-artifact-v2.schema.json";
    private static final String FOUNDATION_CONTINENTAL_SHELF_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-continental-shelf-validation-artifact-v2.schema.json";
    private static final String FOUNDATION_CONTINENTAL_SLOPE_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-continental-slope-validation-artifact-v2.schema.json";
    private static final String SUBMARINE_CANYON_PLAN_SCHEMA = "submarine-canyon-plan-v2.schema.json";
    private static final String FOUNDATION_SUBMARINE_CANYON_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-submarine-canyon-validation-artifact-v2.schema.json";
    private static final String CAVE_ENTRANCE_PLAN_SCHEMA = "cave-entrance-plan-v2.schema.json";
    private static final String FOUNDATION_CAVE_ENTRANCE_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-cave-entrance-validation-artifact-v2.schema.json";
    private static final String UNDERGROUND_RIVER_PLAN_SCHEMA = "underground-river-plan-v2.schema.json";
    private static final String FOUNDATION_UNDERGROUND_RIVER_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-underground-river-validation-artifact-v2.schema.json";
    private static final String LAVA_TUBE_PLAN_SCHEMA = "lava-tube-plan-v2.schema.json";
    private static final String FOUNDATION_LAVA_TUBE_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-lava-tube-validation-artifact-v2.schema.json";
    private static final String SPRING_PLAN_SCHEMA = "spring-plan-v2.schema.json";
    private static final String FOUNDATION_SPRING_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-spring-validation-artifact-v2.schema.json";
    private static final String OXBOW_LAKE_PLAN_SCHEMA = "oxbow-lake-plan-v2.schema.json";
    private static final String FOUNDATION_OXBOW_LAKE_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-oxbow-lake-validation-artifact-v2.schema.json";
    private static final String MACRO_LAND_WATER_TOPOLOGY_PLAN_SCHEMA =
            "macro-land-water-topology-plan-v2.schema.json";
    private static final String FOUNDATION_MACRO_LAND_WATER_TOPOLOGY_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-macro-land-water-topology-validation-artifact-v2.schema.json";
    private static final String WATERFALL_CHAIN_PLAN_SCHEMA = "waterfall-chain-plan-v2.schema.json";
    private static final String FOUNDATION_RIVER_GRAPH_ROLES_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-river-graph-roles-validation-artifact-v2.schema.json";
    private static final String GLACIAL_ICE_PLAN_SCHEMA = "glacial-ice-plan-v2.schema.json";
    private static final String FOUNDATION_GLACIAL_ICE_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-glacial-ice-validation-artifact-v2.schema.json";
    private static final String ICE_FJORD_PLAN_SCHEMA = "ice-fjord-plan-v2.schema.json";
    private static final String BARRIER_ISLAND_PLAN_SCHEMA = "barrier-island-plan-v2.schema.json";
    private static final String ATOLL_PLAN_SCHEMA = "atoll-plan-v2.schema.json";
    private static final String ADVANCED_ISLAND_REEF_CATALOG_CONTRACT_SCHEMA =
            "advanced-island-reef-catalog-contract-v2.schema.json";
    private static final String FOUNDATION_ADVANCED_ISLAND_REEF_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-advanced-island-reef-validation-artifact-v2.schema.json";
    private static final String MORAINE_FIELD_PLAN_SCHEMA = "moraine-field-plan-v2.schema.json";
    private static final String OUTWASH_PLAIN_PLAN_SCHEMA = "outwash-plain-plan-v2.schema.json";
    private static final String PERMAFROST_PLAIN_PROFILE_SCHEMA = "permafrost-plain-profile-v2.schema.json";
    private static final String FOUNDATION_GLACIAL_DEPOSITION_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-glacial-deposition-validation-artifact-v2.schema.json";
    private static final String ABYSSAL_PLAIN_PLAN_SCHEMA = "abyssal-plain-plan-v2.schema.json";
    private static final String SEAMOUNT_PLAN_SCHEMA = "seamount-plan-v2.schema.json";
    private static final String FOUNDATION_ADDITIONAL_MARINE_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-additional-marine-validation-artifact-v2.schema.json";
    private static final String ADVANCED_RIVER_LAKE_SPLIT_CONTRACT_SCHEMA =
            "advanced-river-lake-split-contract-v2.schema.json";
    private static final String ESCARPMENT_PLAN_SCHEMA = "escarpment-plan-v2.schema.json";
    private static final String PLATEAU_PLAN_SCHEMA = "plateau-plan-v2.schema.json";
    private static final String DRY_LAND_MODIFIER_CONTRACT_SCHEMA = "dry-land-modifier-contract-v2.schema.json";
    private static final String FOUNDATION_ESCARPMENT_PLATEAU_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-escarpment-plateau-validation-artifact-v2.schema.json";
    private static final String SINKHOLE_PLAN_SCHEMA = "sinkhole-plan-v2.schema.json";
    private static final String KARST_SPRING_PLAN_SCHEMA = "karst-spring-plan-v2.schema.json";
    private static final String KARST_HYDROLOGY_GRAPH_PLAN_SCHEMA = "karst-hydrology-graph-plan-v2.schema.json";
    private static final String CENOTE_PLAN_SCHEMA = "cenote-plan-v2.schema.json";
    private static final String FOUNDATION_KARST_HYDROLOGY_VALIDATION_ARTIFACT_SCHEMA =
            "foundation-karst-hydrology-validation-artifact-v2.schema.json";
    private static final String FOUNDATION_PREVIEW_INDEX_SCHEMA =
            "foundation-preview-index-v2.schema.json";
    private static final String FEATURE_MATERIAL_PROFILE_PLAN_SCHEMA =
            "feature-material-profile-plan-v2.schema.json";
    private static final String VOLUME_SDF_PRIMITIVE_PLAN_SCHEMA =
            "volume-sdf-primitive-plan-v2.schema.json";
    private static final String VOLUME_CSG_PLAN_SCHEMA = "volume-csg-plan-v2.schema.json";
    private static final String VOLUME_AABB_INDEX_PLAN_SCHEMA = "volume-aabb-index-plan-v2.schema.json";
    private static final String VOLUME_TILE_CACHE_PLAN_SCHEMA = "volume-tile-cache-plan-v2.schema.json";
    private static final String CAVE_NETWORK_PLAN_SCHEMA = "cave-network-plan-v2.schema.json";
    private static final String LUSH_CAVE_PLAN_SCHEMA = "lush-cave-plan-v2.schema.json";
    private static final String UNDERGROUND_LAKE_PLAN_SCHEMA = "underground-lake-plan-v2.schema.json";
    private static final String SEA_CAVE_PLAN_SCHEMA = "sea-cave-plan-v2.schema.json";
    private static final String OVERHANG_PLAN_SCHEMA = "overhang-plan-v2.schema.json";
    private static final String NATURAL_ARCH_PLAN_SCHEMA = "natural-arch-plan-v2.schema.json";
    private static final String SKY_ISLAND_GROUP_PLAN_SCHEMA = "sky-island-group-plan-v2.schema.json";
    private static final String WATERFALL_VOLUME_PLAN_SCHEMA = "waterfall-volume-plan-v2.schema.json";
    private static final String VOLUME_LOCAL_ENVIRONMENT_PLAN_SCHEMA =
            "volume-local-environment-plan-v2.schema.json";
    private static final String PLACEMENT_PLAN_SCHEMA = "placement-plan-v2.schema.json";
    private static final String PLACEMENT_JOURNAL_SCHEMA = "placement-journal-v2.schema.json";
    private static final String PLACEMENT_ENVELOPE_PLAN_SCHEMA = "placement-envelope-plan-v2.schema.json";
    private static final String PLACEMENT_RESERVATION_PLAN_SCHEMA = "placement-reservation-plan-v2.schema.json";
    private static final String PLACEMENT_SAFETY_STATE_SCHEMA = "placement-safety-state-v2.schema.json";
    private static final String PLACEMENT_SNAPSHOT_PLAN_SCHEMA = "placement-snapshot-plan-v2.schema.json";
    private static final String PLACEMENT_CONTAINMENT_POLICY_SCHEMA =
            "placement-containment-policy-v2.schema.json";
    private static final String PLACEMENT_CONTAINMENT_EVIDENCE_SCHEMA =
            "placement-containment-evidence-v2.schema.json";
    private static final String PLACEMENT_SETTLE_VERIFY_POLICY_SCHEMA =
            "placement-settle-verify-policy-v2.schema.json";
    private static final String PLACEMENT_VERIFY_EVIDENCE_SCHEMA =
            "placement-verify-evidence-v2.schema.json";
    private static final String PLACEMENT_UNDO_PLAN_SCHEMA =
            "placement-undo-plan-v2.schema.json";
    private static final String PLACEMENT_RECOVERY_PLAN_SCHEMA =
            "placement-recovery-plan-v2.schema.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public GenerationRequestV2 readGenerationRequest(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(REQUEST_SCHEMA, path.toString(), node);
        return parseGenerationRequest(node);
    }

    public GenerationRequestV2 readGenerationRequest(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(REQUEST_SCHEMA, documentName, node);
        return parseGenerationRequest(node);
    }

    public TerrainIntentV2 readTerrainIntent(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(INTENT_SCHEMA, path.toString(), node);
        return parseIntent(node);
    }

    public TerrainIntentV2 readTerrainIntent(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(INTENT_SCHEMA, documentName, node);
        return parseIntent(node);
    }

    public WorldBlueprintV2 readWorldBlueprint(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(BLUEPRINT_SCHEMA, path.toString(), node);
        WorldBlueprintV2 blueprint = mapper.treeToValue(node, WorldBlueprintV2.class);
        verifyGeologyPlanChecksum(blueprint.geologyPlan());
        verifyLithologyPlanChecksum(blueprint.lithologyPlan());
        verifyStrataPlanChecksum(blueprint.strataPlan());
        verifyClimatePlanChecksum(blueprint.climatePlan());
        verifyWaterConditionPlanChecksum(blueprint.waterConditionPlan());
        verifyHydrologyPlanChecksum(blueprint.hydrologyPlan());
        verifyHydrologyReconciliationPlanChecksum(blueprint.hydrologyReconciliationPlan());
        verifyBlueprintChecksum(blueprint);
        return blueprint;
    }

    public WorldBlueprintV2 readWorldBlueprint(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(BLUEPRINT_SCHEMA, documentName, node);
        WorldBlueprintV2 blueprint = mapper.treeToValue(node, WorldBlueprintV2.class);
        verifyGeologyPlanChecksum(blueprint.geologyPlan());
        verifyLithologyPlanChecksum(blueprint.lithologyPlan());
        verifyStrataPlanChecksum(blueprint.strataPlan());
        verifyClimatePlanChecksum(blueprint.climatePlan());
        verifyWaterConditionPlanChecksum(blueprint.waterConditionPlan());
        verifyHydrologyPlanChecksum(blueprint.hydrologyPlan());
        verifyHydrologyReconciliationPlanChecksum(blueprint.hydrologyReconciliationPlan());
        verifyBlueprintChecksum(blueprint);
        return blueprint;
    }

    public GeologyPlanV2 readGeologyPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(GEOLOGY_PLAN_SCHEMA, path.toString(), node);
        GeologyPlanV2 plan = mapper.treeToValue(node, GeologyPlanV2.class);
        verifyGeologyPlanChecksum(plan);
        return plan;
    }

    public GeologyPlanV2 readGeologyPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(GEOLOGY_PLAN_SCHEMA, documentName, node);
        GeologyPlanV2 plan = mapper.treeToValue(node, GeologyPlanV2.class);
        verifyGeologyPlanChecksum(plan);
        return plan;
    }

    public LithologyPlanV2 readLithologyPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(LITHOLOGY_PLAN_SCHEMA, path.toString(), node);
        LithologyPlanV2 plan = mapper.treeToValue(node, LithologyPlanV2.class);
        verifyLithologyPlanChecksum(plan);
        return plan;
    }

    public LithologyPlanV2 readLithologyPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(LITHOLOGY_PLAN_SCHEMA, documentName, node);
        LithologyPlanV2 plan = mapper.treeToValue(node, LithologyPlanV2.class);
        verifyLithologyPlanChecksum(plan);
        return plan;
    }

    public StrataPlanV2 readStrataPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(STRATA_PLAN_SCHEMA, path.toString(), node);
        StrataPlanV2 plan = mapper.treeToValue(node, StrataPlanV2.class);
        verifyStrataPlanChecksum(plan);
        return plan;
    }

    public StrataPlanV2 readStrataPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(STRATA_PLAN_SCHEMA, documentName, node);
        StrataPlanV2 plan = mapper.treeToValue(node, StrataPlanV2.class);
        verifyStrataPlanChecksum(plan);
        return plan;
    }

    public ClimatePlanV2 readClimatePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(CLIMATE_PLAN_SCHEMA, path.toString(), node);
        ClimatePlanV2 plan = mapper.treeToValue(node, ClimatePlanV2.class);
        verifyClimatePlanChecksum(plan);
        return plan;
    }

    public ClimatePlanV2 readClimatePlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(CLIMATE_PLAN_SCHEMA, documentName, node);
        ClimatePlanV2 plan = mapper.treeToValue(node, ClimatePlanV2.class);
        verifyClimatePlanChecksum(plan);
        return plan;
    }

    public WaterConditionPlanV2 readWaterConditionPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(WATER_CONDITION_PLAN_SCHEMA, path.toString(), node);
        WaterConditionPlanV2 plan = mapper.treeToValue(node, WaterConditionPlanV2.class);
        verifyWaterConditionPlanChecksum(plan);
        return plan;
    }

    public WaterConditionPlanV2 readWaterConditionPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(WATER_CONDITION_PLAN_SCHEMA, documentName, node);
        WaterConditionPlanV2 plan = mapper.treeToValue(node, WaterConditionPlanV2.class);
        verifyWaterConditionPlanChecksum(plan);
        return plan;
    }

    public HydrologyPlanV2 readHydrologyPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(HYDROLOGY_PLAN_SCHEMA, path.toString(), node);
        HydrologyPlanV2 plan = mapper.treeToValue(node, HydrologyPlanV2.class);
        verifyHydrologyPlanChecksum(plan);
        return plan;
    }

    public HydrologyPlanV2 readHydrologyPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(HYDROLOGY_PLAN_SCHEMA, documentName, node);
        HydrologyPlanV2 plan = mapper.treeToValue(node, HydrologyPlanV2.class);
        verifyHydrologyPlanChecksum(plan);
        return plan;
    }

    public MaterialProfilePlanV2 readMaterialProfilePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(MATERIAL_PROFILE_PLAN_SCHEMA, path.toString(), node);
        MaterialProfilePlanV2 plan = mapper.treeToValue(node, MaterialProfilePlanV2.class);
        verifyMaterialProfilePlanChecksum(plan);
        return plan;
    }

    public MaterialProfilePlanV2 readMaterialProfilePlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(MATERIAL_PROFILE_PLAN_SCHEMA, documentName, node);
        MaterialProfilePlanV2 plan = mapper.treeToValue(node, MaterialProfilePlanV2.class);
        verifyMaterialProfilePlanChecksum(plan);
        return plan;
    }

    public MinecraftPalettePlanV2 readMinecraftPalettePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(MINECRAFT_PALETTE_PLAN_SCHEMA, path.toString(), node);
        MinecraftPalettePlanV2 plan = mapper.treeToValue(node, MinecraftPalettePlanV2.class);
        verifyMinecraftPalettePlanChecksum(plan);
        return plan;
    }

    public MinecraftPalettePlanV2 readMinecraftPalettePlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(MINECRAFT_PALETTE_PLAN_SCHEMA, documentName, node);
        MinecraftPalettePlanV2 plan = mapper.treeToValue(node, MinecraftPalettePlanV2.class);
        verifyMinecraftPalettePlanChecksum(plan);
        return plan;
    }

    public EcologyPlanV2 readEcologyPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(ECOLOGY_PLAN_SCHEMA, path.toString(), node);
        EcologyPlanV2 plan = mapper.treeToValue(node, EcologyPlanV2.class);
        verifyEcologyPlanChecksum(plan);
        return plan;
    }

    public EcologyPlanV2 readEcologyPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(ECOLOGY_PLAN_SCHEMA, documentName, node);
        EcologyPlanV2 plan = mapper.treeToValue(node, EcologyPlanV2.class);
        verifyEcologyPlanChecksum(plan);
        return plan;
    }

    public SurfaceFoundationPlanV2 readSurfaceFoundationPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(SURFACE_FOUNDATION_PLAN_SCHEMA, path.toString(), node);
        SurfaceFoundationPlanV2 plan = mapper.treeToValue(node, SurfaceFoundationPlanV2.class);
        verifySurfaceFoundationPlanChecksum(plan);
        return plan;
    }

    public SurfaceFoundationPlanV2 readSurfaceFoundationPlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(SURFACE_FOUNDATION_PLAN_SCHEMA, documentName, node);
        SurfaceFoundationPlanV2 plan = mapper.treeToValue(node, SurfaceFoundationPlanV2.class);
        verifySurfaceFoundationPlanChecksum(plan);
        return plan;
    }

    public PlainPlanV2 readPlainPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLAIN_PLAN_SCHEMA, path.toString(), node);
        PlainPlanV2 plan = mapper.treeToValue(node, PlainPlanV2.class);
        verifyPlainPlanChecksum(plan);
        return plan;
    }

    public PlainPlanV2 readPlainPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLAIN_PLAN_SCHEMA, documentName, node);
        PlainPlanV2 plan = mapper.treeToValue(node, PlainPlanV2.class);
        verifyPlainPlanChecksum(plan);
        return plan;
    }

    public HillRangePlanV2 readHillRangePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(HILL_RANGE_PLAN_SCHEMA, path.toString(), node);
        HillRangePlanV2 plan = mapper.treeToValue(node, HillRangePlanV2.class);
        verifyHillRangePlanChecksum(plan);
        return plan;
    }

    public HillRangePlanV2 readHillRangePlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(HILL_RANGE_PLAN_SCHEMA, documentName, node);
        HillRangePlanV2 plan = mapper.treeToValue(node, HillRangePlanV2.class);
        verifyHillRangePlanChecksum(plan);
        return plan;
    }

    public MountainRangePlanV2 readMountainRangePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(MOUNTAIN_RANGE_PLAN_SCHEMA, path.toString(), node);
        MountainRangePlanV2 plan = mapper.treeToValue(node, MountainRangePlanV2.class);
        verifyMountainRangePlanChecksum(plan);
        return plan;
    }

    public MountainRangePlanV2 readMountainRangePlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(MOUNTAIN_RANGE_PLAN_SCHEMA, documentName, node);
        MountainRangePlanV2 plan = mapper.treeToValue(node, MountainRangePlanV2.class);
        verifyMountainRangePlanChecksum(plan);
        return plan;
    }

    public ValleyPlanV2 readValleyPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(VALLEY_PLAN_SCHEMA, path.toString(), node);
        ValleyPlanV2 plan = mapper.treeToValue(node, ValleyPlanV2.class);
        verifyValleyPlanChecksum(plan);
        return plan;
    }

    public ValleyPlanV2 readValleyPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(VALLEY_PLAN_SCHEMA, documentName, node);
        ValleyPlanV2 plan = mapper.treeToValue(node, ValleyPlanV2.class);
        verifyValleyPlanChecksum(plan);
        return plan;
    }

    public RiverPlanV2 readRiverPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(RIVER_PLAN_SCHEMA, path.toString(), node);
        RiverPlanV2 plan = mapper.treeToValue(node, RiverPlanV2.class);
        verifyRiverPlanChecksum(plan);
        return plan;
    }

    public RiverPlanV2 readRiverPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(RIVER_PLAN_SCHEMA, documentName, node);
        RiverPlanV2 plan = mapper.treeToValue(node, RiverPlanV2.class);
        verifyRiverPlanChecksum(plan);
        return plan;
    }

    public FloodplainPlanV2 readFloodplainPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FLOODPLAIN_PLAN_SCHEMA, path.toString(), node);
        FloodplainPlanV2 plan = mapper.treeToValue(node, FloodplainPlanV2.class);
        verifyFloodplainPlanChecksum(plan);
        return plan;
    }

    public FloodplainPlanV2 readFloodplainPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(FLOODPLAIN_PLAN_SCHEMA, documentName, node);
        FloodplainPlanV2 plan = mapper.treeToValue(node, FloodplainPlanV2.class);
        verifyFloodplainPlanChecksum(plan);
        return plan;
    }

    public MarshPlanV2 readMarshPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(MARSH_PLAN_SCHEMA, path.toString(), node);
        MarshPlanV2 plan = mapper.treeToValue(node, MarshPlanV2.class);
        verifyMarshPlanChecksum(plan);
        return plan;
    }

    public MarshPlanV2 readMarshPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(MARSH_PLAN_SCHEMA, documentName, node);
        MarshPlanV2 plan = mapper.treeToValue(node, MarshPlanV2.class);
        verifyMarshPlanChecksum(plan);
        return plan;
    }

    public RockyCoastPlanV2 readRockyCoastPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(ROCKY_COAST_PLAN_SCHEMA, path.toString(), node);
        RockyCoastPlanV2 plan = mapper.treeToValue(node, RockyCoastPlanV2.class);
        verifyRockyCoastPlanChecksum(plan);
        return plan;
    }

    public RockyCoastPlanV2 readRockyCoastPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(ROCKY_COAST_PLAN_SCHEMA, documentName, node);
        RockyCoastPlanV2 plan = mapper.treeToValue(node, RockyCoastPlanV2.class);
        verifyRockyCoastPlanChecksum(plan);
        return plan;
    }

    public SeaCliffPlanV2 readSeaCliffPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(SEA_CLIFF_PLAN_SCHEMA, path.toString(), node);
        SeaCliffPlanV2 plan = mapper.treeToValue(node, SeaCliffPlanV2.class);
        verifySeaCliffPlanChecksum(plan);
        return plan;
    }

    public SeaCliffPlanV2 readSeaCliffPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(SEA_CLIFF_PLAN_SCHEMA, documentName, node);
        SeaCliffPlanV2 plan = mapper.treeToValue(node, SeaCliffPlanV2.class);
        verifySeaCliffPlanChecksum(plan);
        return plan;
    }

    public SingleIslandPlanV2 readSingleIslandPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(SINGLE_ISLAND_PLAN_SCHEMA, path.toString(), node);
        SingleIslandPlanV2 plan = mapper.treeToValue(node, SingleIslandPlanV2.class);
        verifySingleIslandPlanChecksum(plan);
        return plan;
    }

    public SingleIslandPlanV2 readSingleIslandPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(SINGLE_ISLAND_PLAN_SCHEMA, documentName, node);
        SingleIslandPlanV2 plan = mapper.treeToValue(node, SingleIslandPlanV2.class);
        verifySingleIslandPlanChecksum(plan);
        return plan;
    }

    public ArchipelagoPlanV2 readArchipelagoPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(ARCHIPELAGO_PLAN_SCHEMA, path.toString(), node);
        ArchipelagoPlanV2 plan = mapper.treeToValue(node, ArchipelagoPlanV2.class);
        verifyArchipelagoPlanChecksum(plan);
        return plan;
    }

    public ArchipelagoPlanV2 readArchipelagoPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(ARCHIPELAGO_PLAN_SCHEMA, documentName, node);
        ArchipelagoPlanV2 plan = mapper.treeToValue(node, ArchipelagoPlanV2.class);
        verifyArchipelagoPlanChecksum(plan);
        return plan;
    }

    public VolcanicConePlanV2 readVolcanicConePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(VOLCANIC_CONE_PLAN_SCHEMA, path.toString(), node);
        VolcanicConePlanV2 plan = mapper.treeToValue(node, VolcanicConePlanV2.class);
        verifyVolcanicConePlanChecksum(plan);
        return plan;
    }

    public VolcanicConePlanV2 readVolcanicConePlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(VOLCANIC_CONE_PLAN_SCHEMA, documentName, node);
        VolcanicConePlanV2 plan = mapper.treeToValue(node, VolcanicConePlanV2.class);
        verifyVolcanicConePlanChecksum(plan);
        return plan;
    }

    public OceanBasinPlanV2 readOceanBasinPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(OCEAN_BASIN_PLAN_SCHEMA, path.toString(), node);
        OceanBasinPlanV2 plan = mapper.treeToValue(node, OceanBasinPlanV2.class);
        verifyOceanBasinPlanChecksum(plan);
        return plan;
    }

    public ContinentalShelfPlanV2 readContinentalShelfPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(CONTINENTAL_SHELF_PLAN_SCHEMA, path.toString(), node);
        ContinentalShelfPlanV2 plan = mapper.treeToValue(node, ContinentalShelfPlanV2.class);
        verifyContinentalShelfPlanChecksum(plan);
        return plan;
    }

    public ContinentalSlopePlanV2 readContinentalSlopePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(CONTINENTAL_SLOPE_PLAN_SCHEMA, path.toString(), node);
        ContinentalSlopePlanV2 plan = mapper.treeToValue(node, ContinentalSlopePlanV2.class);
        verifyContinentalSlopePlanChecksum(plan);
        return plan;
    }

    public FoundationOceanBasinValidationArtifactV2 readFoundationOceanBasinValidationArtifact(Path path)
            throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_OCEAN_BASIN_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationOceanBasinValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationOceanBasinValidationArtifactV2.class);
        verifyFoundationOceanBasinValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationContinentalShelfValidationArtifactV2 readFoundationContinentalShelfValidationArtifact(
            Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_CONTINENTAL_SHELF_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationContinentalShelfValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationContinentalShelfValidationArtifactV2.class);
        verifyFoundationContinentalShelfValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationContinentalSlopeValidationArtifactV2 readFoundationContinentalSlopeValidationArtifact(
            Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_CONTINENTAL_SLOPE_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationContinentalSlopeValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationContinentalSlopeValidationArtifactV2.class);
        verifyFoundationContinentalSlopeValidationArtifactChecksum(artifact);
        return artifact;
    }

    public SubmarineCanyonPlanV2 readSubmarineCanyonPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(SUBMARINE_CANYON_PLAN_SCHEMA, path.toString(), node);
        SubmarineCanyonPlanV2 plan = mapper.treeToValue(node, SubmarineCanyonPlanV2.class);
        verifySubmarineCanyonPlanChecksum(plan);
        return plan;
    }

    public FoundationSubmarineCanyonValidationArtifactV2 readFoundationSubmarineCanyonValidationArtifact(
            Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_SUBMARINE_CANYON_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationSubmarineCanyonValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationSubmarineCanyonValidationArtifactV2.class);
        verifyFoundationSubmarineCanyonValidationArtifactChecksum(artifact);
        return artifact;
    }

    public CaveEntrancePlanV2 readCaveEntrancePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(CAVE_ENTRANCE_PLAN_SCHEMA, path.toString(), node);
        CaveEntrancePlanV2 plan = mapper.treeToValue(node, CaveEntrancePlanV2.class);
        verifyCaveEntrancePlanChecksum(plan);
        return plan;
    }

    public FoundationCaveEntranceValidationArtifactV2 readFoundationCaveEntranceValidationArtifact(
            Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_CAVE_ENTRANCE_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationCaveEntranceValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationCaveEntranceValidationArtifactV2.class);
        verifyFoundationCaveEntranceValidationArtifactChecksum(artifact);
        return artifact;
    }

    public UndergroundRiverPlanV2 readUndergroundRiverPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(UNDERGROUND_RIVER_PLAN_SCHEMA, path.toString(), node);
        UndergroundRiverPlanV2 plan = mapper.treeToValue(node, UndergroundRiverPlanV2.class);
        verifyUndergroundRiverPlanChecksum(plan);
        return plan;
    }

    public LavaTubePlanV2 readLavaTubePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(LAVA_TUBE_PLAN_SCHEMA, path.toString(), node);
        LavaTubePlanV2 plan = mapper.treeToValue(node, LavaTubePlanV2.class);
        verifyLavaTubePlanChecksum(plan);
        return plan;
    }

    public FoundationLavaTubeValidationArtifactV2 readFoundationLavaTubeValidationArtifact(
            Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_LAVA_TUBE_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationLavaTubeValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationLavaTubeValidationArtifactV2.class);
        verifyFoundationLavaTubeValidationArtifactChecksum(artifact);
        return artifact;
    }

    public SpringPlanV2 readSpringPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(SPRING_PLAN_SCHEMA, path.toString(), node);
        SpringPlanV2 plan = mapper.treeToValue(node, SpringPlanV2.class);
        verifySpringPlanChecksum(plan);
        return plan;
    }

    public FoundationSpringValidationArtifactV2 readFoundationSpringValidationArtifact(
            Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_SPRING_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationSpringValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationSpringValidationArtifactV2.class);
        verifyFoundationSpringValidationArtifactChecksum(artifact);
        return artifact;
    }

    public OxbowLakePlanV2 readOxbowLakePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(OXBOW_LAKE_PLAN_SCHEMA, path.toString(), node);
        OxbowLakePlanV2 plan = mapper.treeToValue(node, OxbowLakePlanV2.class);
        verifyOxbowLakePlanChecksum(plan);
        return plan;
    }

    public FoundationOxbowLakeValidationArtifactV2 readFoundationOxbowLakeValidationArtifact(
            Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_OXBOW_LAKE_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationOxbowLakeValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationOxbowLakeValidationArtifactV2.class);
        verifyFoundationOxbowLakeValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationUndergroundRiverValidationArtifactV2 readFoundationUndergroundRiverValidationArtifact(
            Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_UNDERGROUND_RIVER_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationUndergroundRiverValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationUndergroundRiverValidationArtifactV2.class);
        verifyFoundationUndergroundRiverValidationArtifactChecksum(artifact);
        return artifact;
    }

    public MacroLandWaterTopologyPlanV2 readMacroLandWaterTopologyPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(MACRO_LAND_WATER_TOPOLOGY_PLAN_SCHEMA, path.toString(), node);
        MacroLandWaterTopologyPlanV2 plan = mapper.treeToValue(node, MacroLandWaterTopologyPlanV2.class);
        verifyMacroLandWaterTopologyPlanChecksum(plan);
        return plan;
    }

    public FoundationMacroLandWaterTopologyValidationArtifactV2
            readFoundationMacroLandWaterTopologyValidationArtifact(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(
                FOUNDATION_MACRO_LAND_WATER_TOPOLOGY_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationMacroLandWaterTopologyValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationMacroLandWaterTopologyValidationArtifactV2.class);
        verifyFoundationMacroLandWaterTopologyValidationArtifactChecksum(artifact);
        return artifact;
    }

    public WaterfallChainPlanV2 readWaterfallChainPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(WATERFALL_CHAIN_PLAN_SCHEMA, path.toString(), node);
        WaterfallChainPlanV2 plan = mapper.treeToValue(node, WaterfallChainPlanV2.class);
        verifyWaterfallChainPlanChecksum(plan);
        return plan;
    }

    public FoundationRiverGraphRolesValidationArtifactV2
            readFoundationRiverGraphRolesValidationArtifact(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(
                FOUNDATION_RIVER_GRAPH_ROLES_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationRiverGraphRolesValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationRiverGraphRolesValidationArtifactV2.class);
        verifyFoundationRiverGraphRolesValidationArtifactChecksum(artifact);
        return artifact;
    }

    public GlacialIcePlanV2 readGlacialIcePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(GLACIAL_ICE_PLAN_SCHEMA, path.toString(), node);
        GlacialIcePlanV2 plan = mapper.treeToValue(node, GlacialIcePlanV2.class);
        verifyGlacialIcePlanChecksum(plan);
        return plan;
    }

    public FoundationGlacialIceValidationArtifactV2 readFoundationGlacialIceValidationArtifact(Path path)
            throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_GLACIAL_ICE_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationGlacialIceValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationGlacialIceValidationArtifactV2.class);
        verifyFoundationGlacialIceValidationArtifactChecksum(artifact);
        return artifact;
    }

    public IceFjordPlanV2 readIceFjordPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(ICE_FJORD_PLAN_SCHEMA, path.toString(), node);
        IceFjordPlanV2 plan = mapper.treeToValue(node, IceFjordPlanV2.class);
        verifyIceFjordPlanChecksum(plan);
        return plan;
    }

    public BarrierIslandPlanV2 readBarrierIslandPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(BARRIER_ISLAND_PLAN_SCHEMA, path.toString(), node);
        BarrierIslandPlanV2 plan = mapper.treeToValue(node, BarrierIslandPlanV2.class);
        verifyBarrierIslandPlanChecksum(plan);
        return plan;
    }

    public AtollPlanV2 readAtollPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(ATOLL_PLAN_SCHEMA, path.toString(), node);
        AtollPlanV2 plan = mapper.treeToValue(node, AtollPlanV2.class);
        verifyAtollPlanChecksum(plan);
        return plan;
    }

    public AdvancedIslandReefCatalogContractV2 readAdvancedIslandReefCatalogContract(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(ADVANCED_ISLAND_REEF_CATALOG_CONTRACT_SCHEMA, path.toString(), node);
        AdvancedIslandReefCatalogContractV2 contract =
                mapper.treeToValue(node, AdvancedIslandReefCatalogContractV2.class);
        verifyAdvancedIslandReefCatalogContractChecksum(contract);
        return contract;
    }

    public FoundationAdvancedIslandReefValidationArtifactV2 readFoundationAdvancedIslandReefValidationArtifact(
            Path path
    ) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_ADVANCED_ISLAND_REEF_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationAdvancedIslandReefValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationAdvancedIslandReefValidationArtifactV2.class);
        verifyFoundationAdvancedIslandReefValidationArtifactChecksum(artifact);
        return artifact;
    }

    public AdvancedRiverLakeSplitContractV2 readAdvancedRiverLakeSplitContract(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(ADVANCED_RIVER_LAKE_SPLIT_CONTRACT_SCHEMA, path.toString(), node);
        AdvancedRiverLakeSplitContractV2 contract =
                mapper.treeToValue(node, AdvancedRiverLakeSplitContractV2.class);
        verifyAdvancedRiverLakeSplitContractChecksum(contract);
        return contract;
    }

    public EscarpmentPlanV2 readEscarpmentPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(ESCARPMENT_PLAN_SCHEMA, path.toString(), node);
        EscarpmentPlanV2 plan = mapper.treeToValue(node, EscarpmentPlanV2.class);
        verifyEscarpmentPlanChecksum(plan);
        return plan;
    }

    public PlateauPlanV2 readPlateauPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLATEAU_PLAN_SCHEMA, path.toString(), node);
        PlateauPlanV2 plan = mapper.treeToValue(node, PlateauPlanV2.class);
        verifyPlateauPlanChecksum(plan);
        return plan;
    }

    public DryLandModifierContractV2 readDryLandModifierContract(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(DRY_LAND_MODIFIER_CONTRACT_SCHEMA, path.toString(), node);
        DryLandModifierContractV2 contract = mapper.treeToValue(node, DryLandModifierContractV2.class);
        verifyDryLandModifierContractChecksum(contract);
        return contract;
    }

    public FoundationEscarpmentPlateauValidationArtifactV2 readFoundationEscarpmentPlateauValidationArtifact(
            Path path
    ) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_ESCARPMENT_PLATEAU_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationEscarpmentPlateauValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationEscarpmentPlateauValidationArtifactV2.class);
        verifyFoundationEscarpmentPlateauValidationArtifactChecksum(artifact);
        return artifact;
    }

    public MoraineFieldPlanV2 readMoraineFieldPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(MORAINE_FIELD_PLAN_SCHEMA, path.toString(), node);
        MoraineFieldPlanV2 plan = mapper.treeToValue(node, MoraineFieldPlanV2.class);
        verifyMoraineFieldPlanChecksum(plan);
        return plan;
    }

    public OutwashPlainPlanV2 readOutwashPlainPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(OUTWASH_PLAIN_PLAN_SCHEMA, path.toString(), node);
        OutwashPlainPlanV2 plan = mapper.treeToValue(node, OutwashPlainPlanV2.class);
        verifyOutwashPlainPlanChecksum(plan);
        return plan;
    }

    public PermafrostPlainProfileV2 readPermafrostPlainProfile(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PERMAFROST_PLAIN_PROFILE_SCHEMA, path.toString(), node);
        PermafrostPlainProfileV2 profile = mapper.treeToValue(node, PermafrostPlainProfileV2.class);
        verifyPermafrostPlainProfileChecksum(profile);
        return profile;
    }

    public FoundationGlacialDepositionValidationArtifactV2 readFoundationGlacialDepositionValidationArtifact(
            Path path
    ) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_GLACIAL_DEPOSITION_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationGlacialDepositionValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationGlacialDepositionValidationArtifactV2.class);
        verifyFoundationGlacialDepositionValidationArtifactChecksum(artifact);
        return artifact;
    }

    public AbyssalPlainPlanV2 readAbyssalPlainPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(ABYSSAL_PLAIN_PLAN_SCHEMA, path.toString(), node);
        AbyssalPlainPlanV2 plan = mapper.treeToValue(node, AbyssalPlainPlanV2.class);
        verifyAbyssalPlainPlanChecksum(plan);
        return plan;
    }

    public SeamountPlanV2 readSeamountPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(SEAMOUNT_PLAN_SCHEMA, path.toString(), node);
        SeamountPlanV2 plan = mapper.treeToValue(node, SeamountPlanV2.class);
        verifySeamountPlanChecksum(plan);
        return plan;
    }

    public FoundationAdditionalMarineValidationArtifactV2 readFoundationAdditionalMarineValidationArtifact(
            Path path
    ) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_ADDITIONAL_MARINE_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationAdditionalMarineValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationAdditionalMarineValidationArtifactV2.class);
        verifyFoundationAdditionalMarineValidationArtifactChecksum(artifact);
        return artifact;
    }

    public SinkholePlanV2 readSinkholePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(SINKHOLE_PLAN_SCHEMA, path.toString(), node);
        SinkholePlanV2 plan = mapper.treeToValue(node, SinkholePlanV2.class);
        verifySinkholePlanChecksum(plan);
        return plan;
    }

    public KarstSpringPlanV2 readKarstSpringPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(KARST_SPRING_PLAN_SCHEMA, path.toString(), node);
        KarstSpringPlanV2 plan = mapper.treeToValue(node, KarstSpringPlanV2.class);
        verifyKarstSpringPlanChecksum(plan);
        return plan;
    }

    public KarstHydrologyGraphPlanV2 readKarstHydrologyGraphPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(KARST_HYDROLOGY_GRAPH_PLAN_SCHEMA, path.toString(), node);
        KarstHydrologyGraphPlanV2 plan = mapper.treeToValue(node, KarstHydrologyGraphPlanV2.class);
        verifyKarstHydrologyGraphPlanChecksum(plan);
        return plan;
    }

    public CenotePlanV2 readCenotePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(CENOTE_PLAN_SCHEMA, path.toString(), node);
        CenotePlanV2 plan = mapper.treeToValue(node, CenotePlanV2.class);
        verifyCenotePlanChecksum(plan);
        return plan;
    }

    public FoundationKarstHydrologyValidationArtifactV2 readFoundationKarstHydrologyValidationArtifact(
            Path path
    ) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_KARST_HYDROLOGY_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationKarstHydrologyValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationKarstHydrologyValidationArtifactV2.class);
        verifyFoundationKarstHydrologyValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationSingleIslandValidationArtifactV2 readFoundationSingleIslandValidationArtifact(Path path)
            throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_SINGLE_ISLAND_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationSingleIslandValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationSingleIslandValidationArtifactV2.class);
        verifyFoundationSingleIslandValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationArchipelagoValidationArtifactV2 readFoundationArchipelagoValidationArtifact(Path path)
            throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_ARCHIPELAGO_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationArchipelagoValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationArchipelagoValidationArtifactV2.class);
        verifyFoundationArchipelagoValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationVolcanicConeValidationArtifactV2 readFoundationVolcanicConeValidationArtifact(Path path)
            throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_VOLCANIC_CONE_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationVolcanicConeValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationVolcanicConeValidationArtifactV2.class);
        verifyFoundationVolcanicConeValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationValidationArtifactV2 readFoundationValidationArtifact(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationValidationArtifactV2 artifact = mapper.treeToValue(node, FoundationValidationArtifactV2.class);
        verifyFoundationValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationValidationArtifactV2 readFoundationValidationArtifact(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(FOUNDATION_VALIDATION_ARTIFACT_SCHEMA, documentName, node);
        FoundationValidationArtifactV2 artifact = mapper.treeToValue(node, FoundationValidationArtifactV2.class);
        verifyFoundationValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationRangeValleyValidationArtifactV2 readFoundationRangeValleyValidationArtifact(Path path)
            throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_RANGE_VALLEY_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationRangeValleyValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationRangeValleyValidationArtifactV2.class);
        verifyFoundationRangeValleyValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationRiverValidationArtifactV2 readFoundationRiverValidationArtifact(Path path)
            throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_RIVER_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationRiverValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationRiverValidationArtifactV2.class);
        verifyFoundationRiverValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationFloodplainMarshValidationArtifactV2 readFoundationFloodplainMarshValidationArtifact(
            Path path
    ) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_FLOODPLAIN_MARSH_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationFloodplainMarshValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationFloodplainMarshValidationArtifactV2.class);
        verifyFoundationFloodplainMarshValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationRockyCoastCliffValidationArtifactV2 readFoundationRockyCoastCliffValidationArtifact(
            Path path
    ) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_ROCKY_COAST_CLIFF_VALIDATION_ARTIFACT_SCHEMA, path.toString(), node);
        FoundationRockyCoastCliffValidationArtifactV2 artifact =
                mapper.treeToValue(node, FoundationRockyCoastCliffValidationArtifactV2.class);
        verifyFoundationRockyCoastCliffValidationArtifactChecksum(artifact);
        return artifact;
    }

    public FoundationPreviewIndexV2 readFoundationPreviewIndex(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FOUNDATION_PREVIEW_INDEX_SCHEMA, path.toString(), node);
        FoundationPreviewIndexV2 index = mapper.treeToValue(node, FoundationPreviewIndexV2.class);
        verifyFoundationPreviewIndexChecksum(index);
        return index;
    }

    public FoundationPreviewIndexV2 readFoundationPreviewIndex(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(FOUNDATION_PREVIEW_INDEX_SCHEMA, documentName, node);
        FoundationPreviewIndexV2 index = mapper.treeToValue(node, FoundationPreviewIndexV2.class);
        verifyFoundationPreviewIndexChecksum(index);
        return index;
    }

    public FeatureMaterialProfilePlanV2 readFeatureMaterialProfilePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(FEATURE_MATERIAL_PROFILE_PLAN_SCHEMA, path.toString(), node);
        FeatureMaterialProfilePlanV2 plan = mapper.treeToValue(node, FeatureMaterialProfilePlanV2.class);
        verifyFeatureMaterialProfilePlanChecksum(plan);
        return plan;
    }

    public FeatureMaterialProfilePlanV2 readFeatureMaterialProfilePlan(
            String input,
            String documentName
    ) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(FEATURE_MATERIAL_PROFILE_PLAN_SCHEMA, documentName, node);
        FeatureMaterialProfilePlanV2 plan = mapper.treeToValue(node, FeatureMaterialProfilePlanV2.class);
        verifyFeatureMaterialProfilePlanChecksum(plan);
        return plan;
    }

    public VolumeSdfPrimitivePlanV2 readVolumeSdfPrimitivePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(VOLUME_SDF_PRIMITIVE_PLAN_SCHEMA, path.toString(), node);
        VolumeSdfPrimitivePlanV2 plan = parseVolumeSdfPrimitivePlan(node);
        verifyVolumeSdfPrimitivePlanChecksum(plan);
        return plan;
    }

    public VolumeSdfPrimitivePlanV2 readVolumeSdfPrimitivePlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(VOLUME_SDF_PRIMITIVE_PLAN_SCHEMA, documentName, node);
        VolumeSdfPrimitivePlanV2 plan = parseVolumeSdfPrimitivePlan(node);
        verifyVolumeSdfPrimitivePlanChecksum(plan);
        return plan;
    }

    public VolumeCsgPlanV2 readVolumeCsgPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(VOLUME_CSG_PLAN_SCHEMA, path.toString(), node);
        VolumeCsgPlanV2 plan = parseVolumeCsgPlan(node);
        verifyVolumeCsgPlanChecksum(plan);
        return plan;
    }

    public VolumeCsgPlanV2 readVolumeCsgPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(VOLUME_CSG_PLAN_SCHEMA, documentName, node);
        VolumeCsgPlanV2 plan = parseVolumeCsgPlan(node);
        verifyVolumeCsgPlanChecksum(plan);
        return plan;
    }

    public VolumeAabbIndexPlanV2 readVolumeAabbIndexPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(VOLUME_AABB_INDEX_PLAN_SCHEMA, path.toString(), node);
        VolumeAabbIndexPlanV2 plan = parseVolumeAabbIndexPlan(node);
        verifyVolumeAabbIndexPlanChecksum(plan);
        return plan;
    }

    public VolumeAabbIndexPlanV2 readVolumeAabbIndexPlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(VOLUME_AABB_INDEX_PLAN_SCHEMA, documentName, node);
        VolumeAabbIndexPlanV2 plan = parseVolumeAabbIndexPlan(node);
        verifyVolumeAabbIndexPlanChecksum(plan);
        return plan;
    }

    public VolumeTileCachePlanV2 readVolumeTileCachePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(VOLUME_TILE_CACHE_PLAN_SCHEMA, path.toString(), node);
        VolumeTileCachePlanV2 plan = parseVolumeTileCachePlan(node);
        verifyVolumeTileCachePlanChecksum(plan);
        return plan;
    }

    public VolumeTileCachePlanV2 readVolumeTileCachePlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(VOLUME_TILE_CACHE_PLAN_SCHEMA, documentName, node);
        VolumeTileCachePlanV2 plan = parseVolumeTileCachePlan(node);
        verifyVolumeTileCachePlanChecksum(plan);
        return plan;
    }

    public CaveNetworkPlanV2 readCaveNetworkPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(CAVE_NETWORK_PLAN_SCHEMA, path.toString(), node);
        CaveNetworkPlanV2 plan = parseCaveNetworkPlan(node);
        verifyCaveNetworkPlanChecksum(plan);
        return plan;
    }

    public CaveNetworkPlanV2 readCaveNetworkPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(CAVE_NETWORK_PLAN_SCHEMA, documentName, node);
        CaveNetworkPlanV2 plan = parseCaveNetworkPlan(node);
        verifyCaveNetworkPlanChecksum(plan);
        return plan;
    }

    public LushCavePlanV2 readLushCavePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(LUSH_CAVE_PLAN_SCHEMA, path.toString(), node);
        LushCavePlanV2 plan = parseLushCavePlan(node);
        verifyLushCavePlanChecksum(plan);
        return plan;
    }

    public LushCavePlanV2 readLushCavePlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(LUSH_CAVE_PLAN_SCHEMA, documentName, node);
        LushCavePlanV2 plan = parseLushCavePlan(node);
        verifyLushCavePlanChecksum(plan);
        return plan;
    }

    public UndergroundLakePlanV2 readUndergroundLakePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(UNDERGROUND_LAKE_PLAN_SCHEMA, path.toString(), node);
        UndergroundLakePlanV2 plan = parseUndergroundLakePlan(node);
        verifyUndergroundLakePlanChecksum(plan);
        return plan;
    }

    public UndergroundLakePlanV2 readUndergroundLakePlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(UNDERGROUND_LAKE_PLAN_SCHEMA, documentName, node);
        UndergroundLakePlanV2 plan = parseUndergroundLakePlan(node);
        verifyUndergroundLakePlanChecksum(plan);
        return plan;
    }

    public SeaCavePlanV2 readSeaCavePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(SEA_CAVE_PLAN_SCHEMA, path.toString(), node);
        SeaCavePlanV2 plan = parseSeaCavePlan(node);
        verifySeaCavePlanChecksum(plan);
        return plan;
    }

    public SeaCavePlanV2 readSeaCavePlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(SEA_CAVE_PLAN_SCHEMA, documentName, node);
        SeaCavePlanV2 plan = parseSeaCavePlan(node);
        verifySeaCavePlanChecksum(plan);
        return plan;
    }

    public OverhangPlanV2 readOverhangPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(OVERHANG_PLAN_SCHEMA, path.toString(), node);
        OverhangPlanV2 plan = parseOverhangPlan(node);
        verifyOverhangPlanChecksum(plan);
        return plan;
    }

    public OverhangPlanV2 readOverhangPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(OVERHANG_PLAN_SCHEMA, documentName, node);
        OverhangPlanV2 plan = parseOverhangPlan(node);
        verifyOverhangPlanChecksum(plan);
        return plan;
    }

    public NaturalArchPlanV2 readNaturalArchPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(NATURAL_ARCH_PLAN_SCHEMA, path.toString(), node);
        NaturalArchPlanV2 plan = parseNaturalArchPlan(node);
        verifyNaturalArchPlanChecksum(plan);
        return plan;
    }

    public NaturalArchPlanV2 readNaturalArchPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(NATURAL_ARCH_PLAN_SCHEMA, documentName, node);
        NaturalArchPlanV2 plan = parseNaturalArchPlan(node);
        verifyNaturalArchPlanChecksum(plan);
        return plan;
    }

    public SkyIslandGroupPlanV2 readSkyIslandGroupPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(SKY_ISLAND_GROUP_PLAN_SCHEMA, path.toString(), node);
        SkyIslandGroupPlanV2 plan = parseSkyIslandGroupPlan(node);
        verifySkyIslandGroupPlanChecksum(plan);
        return plan;
    }

    public SkyIslandGroupPlanV2 readSkyIslandGroupPlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(SKY_ISLAND_GROUP_PLAN_SCHEMA, documentName, node);
        SkyIslandGroupPlanV2 plan = parseSkyIslandGroupPlan(node);
        verifySkyIslandGroupPlanChecksum(plan);
        return plan;
    }

    public WaterfallVolumePlanV2 readWaterfallVolumePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(WATERFALL_VOLUME_PLAN_SCHEMA, path.toString(), node);
        WaterfallVolumePlanV2 plan = parseWaterfallVolumePlan(node);
        verifyWaterfallVolumePlanChecksum(plan);
        return plan;
    }

    public WaterfallVolumePlanV2 readWaterfallVolumePlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(WATERFALL_VOLUME_PLAN_SCHEMA, documentName, node);
        WaterfallVolumePlanV2 plan = parseWaterfallVolumePlan(node);
        verifyWaterfallVolumePlanChecksum(plan);
        return plan;
    }

    public VolumeLocalEnvironmentPlanV2 readVolumeLocalEnvironmentPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(VOLUME_LOCAL_ENVIRONMENT_PLAN_SCHEMA, path.toString(), node);
        VolumeLocalEnvironmentPlanV2 plan = parseVolumeLocalEnvironmentPlan(node);
        verifyVolumeLocalEnvironmentPlanChecksum(plan);
        return plan;
    }

    public VolumeLocalEnvironmentPlanV2 readVolumeLocalEnvironmentPlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(VOLUME_LOCAL_ENVIRONMENT_PLAN_SCHEMA, documentName, node);
        VolumeLocalEnvironmentPlanV2 plan = parseVolumeLocalEnvironmentPlan(node);
        verifyVolumeLocalEnvironmentPlanChecksum(plan);
        return plan;
    }

    public PlacementPlanV2 readPlacementPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_PLAN_SCHEMA, path.toString(), node);
        PlacementPlanV2 plan = mapper.treeToValue(node, PlacementPlanV2.class);
        verifyPlacementPlanChecksum(plan);
        return plan;
    }

    public PlacementPlanV2 readPlacementPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_PLAN_SCHEMA, documentName, node);
        PlacementPlanV2 plan = mapper.treeToValue(node, PlacementPlanV2.class);
        verifyPlacementPlanChecksum(plan);
        return plan;
    }

    public PlacementJournalV2 readPlacementJournal(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_JOURNAL_SCHEMA, path.toString(), node);
        PlacementJournalV2 journal = mapper.treeToValue(node, PlacementJournalV2.class);
        verifyPlacementJournalChecksum(journal);
        return journal;
    }

    public PlacementJournalV2 readPlacementJournal(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_JOURNAL_SCHEMA, documentName, node);
        PlacementJournalV2 journal = mapper.treeToValue(node, PlacementJournalV2.class);
        verifyPlacementJournalChecksum(journal);
        return journal;
    }

    public PlacementEnvelopePlanV2 readPlacementEnvelopePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_ENVELOPE_PLAN_SCHEMA, path.toString(), node);
        PlacementEnvelopePlanV2 plan = mapper.treeToValue(node, PlacementEnvelopePlanV2.class);
        verifyPlacementEnvelopePlanChecksum(plan);
        return plan;
    }

    public PlacementEnvelopePlanV2 readPlacementEnvelopePlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_ENVELOPE_PLAN_SCHEMA, documentName, node);
        PlacementEnvelopePlanV2 plan = mapper.treeToValue(node, PlacementEnvelopePlanV2.class);
        verifyPlacementEnvelopePlanChecksum(plan);
        return plan;
    }

    public PlacementReservationPlanV2 readPlacementReservationPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_RESERVATION_PLAN_SCHEMA, path.toString(), node);
        PlacementReservationPlanV2 plan = mapper.treeToValue(node, PlacementReservationPlanV2.class);
        verifyPlacementReservationPlanChecksum(plan);
        return plan;
    }

    public PlacementReservationPlanV2 readPlacementReservationPlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_RESERVATION_PLAN_SCHEMA, documentName, node);
        PlacementReservationPlanV2 plan = mapper.treeToValue(node, PlacementReservationPlanV2.class);
        verifyPlacementReservationPlanChecksum(plan);
        return plan;
    }

    public PlacementSafetyStateV2 readPlacementSafetyStateV2(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_SAFETY_STATE_SCHEMA, path.toString(), node);
        PlacementSafetyStateV2 state = mapper.treeToValue(node, PlacementSafetyStateV2.class);
        verifyPlacementSafetyStateChecksum(state);
        return state;
    }

    public PlacementSafetyStateV2 readPlacementSafetyStateV2(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_SAFETY_STATE_SCHEMA, documentName, node);
        PlacementSafetyStateV2 state = mapper.treeToValue(node, PlacementSafetyStateV2.class);
        verifyPlacementSafetyStateChecksum(state);
        return state;
    }

    public PlacementSnapshotPlanV2 readPlacementSnapshotPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_SNAPSHOT_PLAN_SCHEMA, path.toString(), node);
        PlacementSnapshotPlanV2 plan = mapper.treeToValue(node, PlacementSnapshotPlanV2.class);
        verifyPlacementSnapshotPlanChecksum(plan);
        return plan;
    }

    public PlacementSnapshotPlanV2 readPlacementSnapshotPlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_SNAPSHOT_PLAN_SCHEMA, documentName, node);
        PlacementSnapshotPlanV2 plan = mapper.treeToValue(node, PlacementSnapshotPlanV2.class);
        verifyPlacementSnapshotPlanChecksum(plan);
        return plan;
    }

    public PlacementContainmentPolicyV2 readPlacementContainmentPolicy(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_CONTAINMENT_POLICY_SCHEMA, path.toString(), node);
        return mapper.treeToValue(node, PlacementContainmentPolicyV2.class);
    }

    public PlacementContainmentPolicyV2 readPlacementContainmentPolicy(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_CONTAINMENT_POLICY_SCHEMA, documentName, node);
        return mapper.treeToValue(node, PlacementContainmentPolicyV2.class);
    }

    public PlacementContainmentEvidenceV2 readPlacementContainmentEvidence(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_CONTAINMENT_EVIDENCE_SCHEMA, path.toString(), node);
        PlacementContainmentEvidenceV2 evidence = mapper.treeToValue(node, PlacementContainmentEvidenceV2.class);
        verifyPlacementContainmentEvidenceChecksum(evidence);
        return evidence;
    }

    public PlacementContainmentEvidenceV2 readPlacementContainmentEvidence(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_CONTAINMENT_EVIDENCE_SCHEMA, documentName, node);
        PlacementContainmentEvidenceV2 evidence = mapper.treeToValue(node, PlacementContainmentEvidenceV2.class);
        verifyPlacementContainmentEvidenceChecksum(evidence);
        return evidence;
    }

    public PlacementSettleVerifyPolicyV2 readPlacementSettleVerifyPolicy(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_SETTLE_VERIFY_POLICY_SCHEMA, path.toString(), node);
        return mapper.treeToValue(node, PlacementSettleVerifyPolicyV2.class);
    }

    public PlacementSettleVerifyPolicyV2 readPlacementSettleVerifyPolicy(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_SETTLE_VERIFY_POLICY_SCHEMA, documentName, node);
        return mapper.treeToValue(node, PlacementSettleVerifyPolicyV2.class);
    }

    public PlacementVerifyEvidenceV2 readPlacementVerifyEvidence(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_VERIFY_EVIDENCE_SCHEMA, path.toString(), node);
        PlacementVerifyEvidenceV2 evidence = mapper.treeToValue(node, PlacementVerifyEvidenceV2.class);
        verifyPlacementVerifyEvidenceChecksum(evidence);
        return evidence;
    }

    public PlacementVerifyEvidenceV2 readPlacementVerifyEvidence(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_VERIFY_EVIDENCE_SCHEMA, documentName, node);
        PlacementVerifyEvidenceV2 evidence = mapper.treeToValue(node, PlacementVerifyEvidenceV2.class);
        verifyPlacementVerifyEvidenceChecksum(evidence);
        return evidence;
    }

    public PlacementUndoPlanV2 readPlacementUndoPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_UNDO_PLAN_SCHEMA, path.toString(), node);
        PlacementUndoPlanV2 plan = mapper.treeToValue(node, PlacementUndoPlanV2.class);
        verifyPlacementUndoPlanChecksum(plan);
        return plan;
    }

    public PlacementUndoPlanV2 readPlacementUndoPlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_UNDO_PLAN_SCHEMA, documentName, node);
        PlacementUndoPlanV2 plan = mapper.treeToValue(node, PlacementUndoPlanV2.class);
        verifyPlacementUndoPlanChecksum(plan);
        return plan;
    }

    public PlacementRecoveryPlanV2 readPlacementRecoveryPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(PLACEMENT_RECOVERY_PLAN_SCHEMA, path.toString(), node);
        PlacementRecoveryPlanV2 plan = mapper.treeToValue(node, PlacementRecoveryPlanV2.class);
        verifyPlacementRecoveryPlanChecksum(plan);
        return plan;
    }

    public PlacementRecoveryPlanV2 readPlacementRecoveryPlan(String input, String documentName)
            throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(PLACEMENT_RECOVERY_PLAN_SCHEMA, documentName, node);
        PlacementRecoveryPlanV2 plan = mapper.treeToValue(node, PlacementRecoveryPlanV2.class);
        verifyPlacementRecoveryPlanChecksum(plan);
        return plan;
    }

    public HydrologyReconciliationPlanV2 readHydrologyReconciliationPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(HYDROLOGY_RECONCILIATION_PLAN_SCHEMA, path.toString(), node);
        HydrologyReconciliationPlanV2 plan = mapper.treeToValue(node, HydrologyReconciliationPlanV2.class);
        verifyHydrologyReconciliationPlanChecksum(plan);
        return plan;
    }

    public HydrologyReconciliationPlanV2 readHydrologyReconciliationPlan(
            String input,
            String documentName
    ) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(HYDROLOGY_RECONCILIATION_PLAN_SCHEMA, documentName, node);
        HydrologyReconciliationPlanV2 plan = mapper.treeToValue(node, HydrologyReconciliationPlanV2.class);
        verifyHydrologyReconciliationPlanChecksum(plan);
        return plan;
    }

    public String canonicalTerrainIntent(TerrainIntentV2 intent) {
        return CanonicalJsonV2.string(intentTree(intent));
    }

    public String canonicalGenerationRequest(GenerationRequestV2 request) {
        return CanonicalJsonV2.string(generationRequestTree(request));
    }

    public String generationRequestChecksum(GenerationRequestV2 request) {
        return CanonicalJsonV2.checksum(generationRequestTree(request));
    }

    public String terrainIntentChecksum(TerrainIntentV2 intent) {
        return CanonicalJsonV2.checksum(intentTree(intent));
    }

    public String geometryChecksum(TerrainIntentV2.Geometry geometry) {
        return CanonicalJsonV2.checksum(geometryTree(geometry));
    }

    public String canonicalWorldBlueprint(WorldBlueprintV2 blueprint) {
        return CanonicalJsonV2.string(mapper.valueToTree(blueprint));
    }

    public String worldBlueprintChecksum(WorldBlueprintV2 blueprint) {
        ObjectNode tree = mapper.valueToTree(blueprint);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public WorldBlueprintV2 sealWorldBlueprint(WorldBlueprintV2 blueprint) {
        return blueprint.withCanonicalChecksum(worldBlueprintChecksum(blueprint));
    }

    public String canonicalGeologyPlan(GeologyPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String geologyPlanChecksum(GeologyPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public GeologyPlanV2 sealGeologyPlan(GeologyPlanV2 plan) {
        return plan.withCanonicalChecksum(geologyPlanChecksum(plan));
    }

    public String canonicalLithologyCatalog(LithologyPlanV2.Catalog catalog) {
        return CanonicalJsonV2.string(mapper.valueToTree(catalog));
    }

    public String lithologyCatalogChecksum(LithologyPlanV2.Catalog catalog) {
        ObjectNode tree = mapper.valueToTree(catalog);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public LithologyPlanV2.Catalog sealLithologyCatalog(LithologyPlanV2.Catalog catalog) {
        return catalog.withCanonicalChecksum(lithologyCatalogChecksum(catalog));
    }

    public String canonicalLithologyPlan(LithologyPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String lithologyPlanChecksum(LithologyPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public LithologyPlanV2 sealLithologyPlan(LithologyPlanV2 plan) {
        return plan.withCanonicalChecksum(lithologyPlanChecksum(plan));
    }

    public String canonicalStrataPlan(StrataPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String strataPlanChecksum(StrataPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public StrataPlanV2 sealStrataPlan(StrataPlanV2 plan) {
        return plan.withCanonicalChecksum(strataPlanChecksum(plan));
    }

    public String canonicalClimatePlan(ClimatePlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String climatePlanChecksum(ClimatePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public ClimatePlanV2 sealClimatePlan(ClimatePlanV2 plan) {
        return plan.withCanonicalChecksum(climatePlanChecksum(plan));
    }

    public String canonicalWaterConditionPlan(WaterConditionPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String waterConditionPlanChecksum(WaterConditionPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public WaterConditionPlanV2 sealWaterConditionPlan(WaterConditionPlanV2 plan) {
        return plan.withCanonicalChecksum(waterConditionPlanChecksum(plan));
    }

    public String canonicalSnowPlan(SnowPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String snowPlanChecksum(SnowPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public SnowPlanV2 sealSnowPlan(SnowPlanV2 plan) {
        return plan.withCanonicalChecksum(snowPlanChecksum(plan));
    }

    public String canonicalMaterialProfilePlan(MaterialProfilePlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String materialProfilePlanChecksum(MaterialProfilePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public MaterialProfilePlanV2 sealMaterialProfilePlan(MaterialProfilePlanV2 plan) {
        return plan.withCanonicalChecksum(materialProfilePlanChecksum(plan));
    }

    public String canonicalMinecraftPalettePlan(MinecraftPalettePlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String minecraftPalettePlanChecksum(MinecraftPalettePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public MinecraftPalettePlanV2 sealMinecraftPalettePlan(MinecraftPalettePlanV2 plan) {
        return plan.withCanonicalChecksum(minecraftPalettePlanChecksum(plan));
    }

    public String canonicalEcologyPlan(EcologyPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String ecologyPlanChecksum(EcologyPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public EcologyPlanV2 sealEcologyPlan(EcologyPlanV2 plan) {
        return plan.withCanonicalChecksum(ecologyPlanChecksum(plan));
    }

    public String canonicalSurfaceFoundationPlan(SurfaceFoundationPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String surfaceFoundationPlanChecksum(SurfaceFoundationPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public SurfaceFoundationPlanV2 sealSurfaceFoundationPlan(SurfaceFoundationPlanV2 plan) {
        return plan.withCanonicalChecksum(surfaceFoundationPlanChecksum(plan));
    }

    public String canonicalPlainPlan(PlainPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String plainPlanChecksum(PlainPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlainPlanV2 sealPlainPlan(PlainPlanV2 plan) {
        return plan.withCanonicalChecksum(plainPlanChecksum(plan));
    }

    public String canonicalHillRangePlan(HillRangePlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String hillRangePlanChecksum(HillRangePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public HillRangePlanV2 sealHillRangePlan(HillRangePlanV2 plan) {
        return plan.withCanonicalChecksum(hillRangePlanChecksum(plan));
    }

    public String canonicalMountainRangePlan(MountainRangePlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String mountainRangePlanChecksum(MountainRangePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public MountainRangePlanV2 sealMountainRangePlan(MountainRangePlanV2 plan) {
        return plan.withCanonicalChecksum(mountainRangePlanChecksum(plan));
    }

    public String canonicalValleyPlan(ValleyPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String valleyPlanChecksum(ValleyPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public ValleyPlanV2 sealValleyPlan(ValleyPlanV2 plan) {
        return plan.withCanonicalChecksum(valleyPlanChecksum(plan));
    }

    public String canonicalRiverPlan(RiverPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String riverPlanChecksum(RiverPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public RiverPlanV2 sealRiverPlan(RiverPlanV2 plan) {
        return plan.withCanonicalChecksum(riverPlanChecksum(plan));
    }

    public String canonicalFloodplainPlan(FloodplainPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String floodplainPlanChecksum(FloodplainPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FloodplainPlanV2 sealFloodplainPlan(FloodplainPlanV2 plan) {
        return plan.withCanonicalChecksum(floodplainPlanChecksum(plan));
    }

    public String canonicalMarshPlan(MarshPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String marshPlanChecksum(MarshPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public MarshPlanV2 sealMarshPlan(MarshPlanV2 plan) {
        return plan.withCanonicalChecksum(marshPlanChecksum(plan));
    }

    public String canonicalFoundationValidationArtifact(FoundationValidationArtifactV2 artifact) {
        return CanonicalJsonV2.string(mapper.valueToTree(artifact));
    }

    public String foundationValidationArtifactChecksum(FoundationValidationArtifactV2 artifact) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationValidationArtifactV2 sealFoundationValidationArtifact(
            FoundationValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationValidationArtifactChecksum(artifact));
    }

    public String canonicalFoundationRangeValleyValidationArtifact(
            FoundationRangeValleyValidationArtifactV2 artifact
    ) {
        return CanonicalJsonV2.string(mapper.valueToTree(artifact));
    }

    public String foundationRangeValleyValidationArtifactChecksum(
            FoundationRangeValleyValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationRangeValleyValidationArtifactV2 sealFoundationRangeValleyValidationArtifact(
            FoundationRangeValleyValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationRangeValleyValidationArtifactChecksum(artifact));
    }

    public String canonicalFoundationRiverValidationArtifact(FoundationRiverValidationArtifactV2 artifact) {
        return CanonicalJsonV2.string(mapper.valueToTree(artifact));
    }

    public String foundationRiverValidationArtifactChecksum(FoundationRiverValidationArtifactV2 artifact) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationRiverValidationArtifactV2 sealFoundationRiverValidationArtifact(
            FoundationRiverValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationRiverValidationArtifactChecksum(artifact));
    }

    public String canonicalFoundationFloodplainMarshValidationArtifact(
            FoundationFloodplainMarshValidationArtifactV2 artifact
    ) {
        return CanonicalJsonV2.string(mapper.valueToTree(artifact));
    }

    public String foundationFloodplainMarshValidationArtifactChecksum(
            FoundationFloodplainMarshValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationFloodplainMarshValidationArtifactV2 sealFoundationFloodplainMarshValidationArtifact(
            FoundationFloodplainMarshValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationFloodplainMarshValidationArtifactChecksum(artifact));
    }

    public String canonicalRockyCoastPlan(RockyCoastPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String rockyCoastPlanChecksum(RockyCoastPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public RockyCoastPlanV2 sealRockyCoastPlan(RockyCoastPlanV2 plan) {
        return plan.withCanonicalChecksum(rockyCoastPlanChecksum(plan));
    }

    public String canonicalSeaCliffPlan(SeaCliffPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String seaCliffPlanChecksum(SeaCliffPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public SeaCliffPlanV2 sealSeaCliffPlan(SeaCliffPlanV2 plan) {
        return plan.withCanonicalChecksum(seaCliffPlanChecksum(plan));
    }

    public String canonicalSingleIslandPlan(SingleIslandPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String singleIslandPlanChecksum(SingleIslandPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public SingleIslandPlanV2 sealSingleIslandPlan(SingleIslandPlanV2 plan) {
        return plan.withCanonicalChecksum(singleIslandPlanChecksum(plan));
    }

    public String canonicalArchipelagoPlan(ArchipelagoPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String archipelagoPlanChecksum(ArchipelagoPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public ArchipelagoPlanV2 sealArchipelagoPlan(ArchipelagoPlanV2 plan) {
        return plan.withCanonicalChecksum(archipelagoPlanChecksum(plan));
    }

    public String canonicalVolcanicConePlan(VolcanicConePlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String volcanicConePlanChecksum(VolcanicConePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public VolcanicConePlanV2 sealVolcanicConePlan(VolcanicConePlanV2 plan) {
        return plan.withCanonicalChecksum(volcanicConePlanChecksum(plan));
    }

    public String oceanBasinPlanChecksum(OceanBasinPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public OceanBasinPlanV2 sealOceanBasinPlan(OceanBasinPlanV2 plan) {
        return plan.withCanonicalChecksum(oceanBasinPlanChecksum(plan));
    }

    public String continentalShelfPlanChecksum(ContinentalShelfPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public ContinentalShelfPlanV2 sealContinentalShelfPlan(ContinentalShelfPlanV2 plan) {
        return plan.withCanonicalChecksum(continentalShelfPlanChecksum(plan));
    }

    public String continentalSlopePlanChecksum(ContinentalSlopePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public ContinentalSlopePlanV2 sealContinentalSlopePlan(ContinentalSlopePlanV2 plan) {
        return plan.withCanonicalChecksum(continentalSlopePlanChecksum(plan));
    }

    public String foundationOceanBasinValidationArtifactChecksum(
            FoundationOceanBasinValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationOceanBasinValidationArtifactV2 sealFoundationOceanBasinValidationArtifact(
            FoundationOceanBasinValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationOceanBasinValidationArtifactChecksum(artifact));
    }

    public String foundationContinentalShelfValidationArtifactChecksum(
            FoundationContinentalShelfValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationContinentalShelfValidationArtifactV2 sealFoundationContinentalShelfValidationArtifact(
            FoundationContinentalShelfValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(
                foundationContinentalShelfValidationArtifactChecksum(artifact));
    }

    public String foundationContinentalSlopeValidationArtifactChecksum(
            FoundationContinentalSlopeValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationContinentalSlopeValidationArtifactV2 sealFoundationContinentalSlopeValidationArtifact(
            FoundationContinentalSlopeValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(
                foundationContinentalSlopeValidationArtifactChecksum(artifact));
    }

    public String submarineCanyonPlanChecksum(SubmarineCanyonPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public SubmarineCanyonPlanV2 sealSubmarineCanyonPlan(SubmarineCanyonPlanV2 plan) {
        return plan.withCanonicalChecksum(submarineCanyonPlanChecksum(plan));
    }

    public String foundationSubmarineCanyonValidationArtifactChecksum(
            FoundationSubmarineCanyonValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationSubmarineCanyonValidationArtifactV2 sealFoundationSubmarineCanyonValidationArtifact(
            FoundationSubmarineCanyonValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(
                foundationSubmarineCanyonValidationArtifactChecksum(artifact));
    }

    public String caveEntrancePlanChecksum(CaveEntrancePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public CaveEntrancePlanV2 sealCaveEntrancePlan(CaveEntrancePlanV2 plan) {
        return plan.withCanonicalChecksum(caveEntrancePlanChecksum(plan));
    }

    public String foundationCaveEntranceValidationArtifactChecksum(
            FoundationCaveEntranceValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationCaveEntranceValidationArtifactV2 sealFoundationCaveEntranceValidationArtifact(
            FoundationCaveEntranceValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(
                foundationCaveEntranceValidationArtifactChecksum(artifact));
    }

    public String undergroundRiverPlanChecksum(UndergroundRiverPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public UndergroundRiverPlanV2 sealUndergroundRiverPlan(UndergroundRiverPlanV2 plan) {
        return plan.withCanonicalChecksum(undergroundRiverPlanChecksum(plan));
    }

    public String lavaTubePlanChecksum(LavaTubePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public LavaTubePlanV2 sealLavaTubePlan(LavaTubePlanV2 plan) {
        return plan.withCanonicalChecksum(lavaTubePlanChecksum(plan));
    }

    public String foundationLavaTubeValidationArtifactChecksum(
            FoundationLavaTubeValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationLavaTubeValidationArtifactV2 sealFoundationLavaTubeValidationArtifact(
            FoundationLavaTubeValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationLavaTubeValidationArtifactChecksum(artifact));
    }

    public String springPlanChecksum(SpringPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public SpringPlanV2 sealSpringPlan(SpringPlanV2 plan) {
        return plan.withCanonicalChecksum(springPlanChecksum(plan));
    }

    public String foundationSpringValidationArtifactChecksum(
            FoundationSpringValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationSpringValidationArtifactV2 sealFoundationSpringValidationArtifact(
            FoundationSpringValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationSpringValidationArtifactChecksum(artifact));
    }

    public String oxbowLakePlanChecksum(OxbowLakePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public OxbowLakePlanV2 sealOxbowLakePlan(OxbowLakePlanV2 plan) {
        return plan.withCanonicalChecksum(oxbowLakePlanChecksum(plan));
    }

    public String foundationOxbowLakeValidationArtifactChecksum(
            FoundationOxbowLakeValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationOxbowLakeValidationArtifactV2 sealFoundationOxbowLakeValidationArtifact(
            FoundationOxbowLakeValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationOxbowLakeValidationArtifactChecksum(artifact));
    }

    public String foundationUndergroundRiverValidationArtifactChecksum(
            FoundationUndergroundRiverValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationUndergroundRiverValidationArtifactV2 sealFoundationUndergroundRiverValidationArtifact(
            FoundationUndergroundRiverValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(
                foundationUndergroundRiverValidationArtifactChecksum(artifact));
    }

    public String macroLandWaterTopologyPlanChecksum(MacroLandWaterTopologyPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public MacroLandWaterTopologyPlanV2 sealMacroLandWaterTopologyPlan(MacroLandWaterTopologyPlanV2 plan) {
        return plan.withCanonicalChecksum(macroLandWaterTopologyPlanChecksum(plan));
    }

    public String foundationMacroLandWaterTopologyValidationArtifactChecksum(
            FoundationMacroLandWaterTopologyValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationMacroLandWaterTopologyValidationArtifactV2
            sealFoundationMacroLandWaterTopologyValidationArtifact(
                    FoundationMacroLandWaterTopologyValidationArtifactV2 artifact
            ) {
        return artifact.withCanonicalChecksum(
                foundationMacroLandWaterTopologyValidationArtifactChecksum(artifact));
    }

    public String waterfallChainPlanChecksum(WaterfallChainPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public WaterfallChainPlanV2 sealWaterfallChainPlan(WaterfallChainPlanV2 plan) {
        return plan.withCanonicalChecksum(waterfallChainPlanChecksum(plan));
    }

    public String foundationRiverGraphRolesValidationArtifactChecksum(
            FoundationRiverGraphRolesValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationRiverGraphRolesValidationArtifactV2
            sealFoundationRiverGraphRolesValidationArtifact(
                    FoundationRiverGraphRolesValidationArtifactV2 artifact
            ) {
        return artifact.withCanonicalChecksum(
                foundationRiverGraphRolesValidationArtifactChecksum(artifact));
    }

    public String glacialIcePlanChecksum(GlacialIcePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public GlacialIcePlanV2 sealGlacialIcePlan(GlacialIcePlanV2 plan) {
        return plan.withCanonicalChecksum(glacialIcePlanChecksum(plan));
    }

    public String foundationGlacialIceValidationArtifactChecksum(
            FoundationGlacialIceValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationGlacialIceValidationArtifactV2 sealFoundationGlacialIceValidationArtifact(
            FoundationGlacialIceValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationGlacialIceValidationArtifactChecksum(artifact));
    }

    public String iceFjordPlanChecksum(IceFjordPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public IceFjordPlanV2 sealIceFjordPlan(IceFjordPlanV2 plan) {
        return plan.withCanonicalChecksum(iceFjordPlanChecksum(plan));
    }

    public String barrierIslandPlanChecksum(BarrierIslandPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public BarrierIslandPlanV2 sealBarrierIslandPlan(BarrierIslandPlanV2 plan) {
        return plan.withCanonicalChecksum(barrierIslandPlanChecksum(plan));
    }

    public String atollPlanChecksum(AtollPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public AtollPlanV2 sealAtollPlan(AtollPlanV2 plan) {
        return plan.withCanonicalChecksum(atollPlanChecksum(plan));
    }

    public String advancedIslandReefCatalogContractChecksum(AdvancedIslandReefCatalogContractV2 contract) {
        ObjectNode tree = mapper.valueToTree(contract);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public AdvancedIslandReefCatalogContractV2 sealAdvancedIslandReefCatalogContract(
            AdvancedIslandReefCatalogContractV2 contract
    ) {
        return contract.withCanonicalChecksum(advancedIslandReefCatalogContractChecksum(contract));
    }

    public String foundationAdvancedIslandReefValidationArtifactChecksum(
            FoundationAdvancedIslandReefValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationAdvancedIslandReefValidationArtifactV2 sealFoundationAdvancedIslandReefValidationArtifact(
            FoundationAdvancedIslandReefValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationAdvancedIslandReefValidationArtifactChecksum(artifact));
    }

    public String advancedRiverLakeSplitContractChecksum(AdvancedRiverLakeSplitContractV2 contract) {
        ObjectNode tree = mapper.valueToTree(contract);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public AdvancedRiverLakeSplitContractV2 sealAdvancedRiverLakeSplitContract(
            AdvancedRiverLakeSplitContractV2 contract
    ) {
        return contract.withCanonicalChecksum(advancedRiverLakeSplitContractChecksum(contract));
    }

    public String escarpmentPlanChecksum(EscarpmentPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public EscarpmentPlanV2 sealEscarpmentPlan(EscarpmentPlanV2 plan) {
        return plan.withCanonicalChecksum(escarpmentPlanChecksum(plan));
    }

    public String plateauPlanChecksum(PlateauPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlateauPlanV2 sealPlateauPlan(PlateauPlanV2 plan) {
        return plan.withCanonicalChecksum(plateauPlanChecksum(plan));
    }

    public String dryLandModifierContractChecksum(DryLandModifierContractV2 contract) {
        ObjectNode tree = mapper.valueToTree(contract);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public DryLandModifierContractV2 sealDryLandModifierContract(DryLandModifierContractV2 contract) {
        return contract.withCanonicalChecksum(dryLandModifierContractChecksum(contract));
    }

    public String foundationEscarpmentPlateauValidationArtifactChecksum(
            FoundationEscarpmentPlateauValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationEscarpmentPlateauValidationArtifactV2 sealFoundationEscarpmentPlateauValidationArtifact(
            FoundationEscarpmentPlateauValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationEscarpmentPlateauValidationArtifactChecksum(artifact));
    }

    public String moraineFieldPlanChecksum(MoraineFieldPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public MoraineFieldPlanV2 sealMoraineFieldPlan(MoraineFieldPlanV2 plan) {
        return plan.withCanonicalChecksum(moraineFieldPlanChecksum(plan));
    }

    public String outwashPlainPlanChecksum(OutwashPlainPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public OutwashPlainPlanV2 sealOutwashPlainPlan(OutwashPlainPlanV2 plan) {
        return plan.withCanonicalChecksum(outwashPlainPlanChecksum(plan));
    }

    public String permafrostPlainProfileChecksum(PermafrostPlainProfileV2 profile) {
        ObjectNode tree = mapper.valueToTree(profile);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PermafrostPlainProfileV2 sealPermafrostPlainProfile(PermafrostPlainProfileV2 profile) {
        return profile.withCanonicalChecksum(permafrostPlainProfileChecksum(profile));
    }

    public String foundationGlacialDepositionValidationArtifactChecksum(
            FoundationGlacialDepositionValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationGlacialDepositionValidationArtifactV2 sealFoundationGlacialDepositionValidationArtifact(
            FoundationGlacialDepositionValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationGlacialDepositionValidationArtifactChecksum(artifact));
    }

    public String abyssalPlainPlanChecksum(AbyssalPlainPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public AbyssalPlainPlanV2 sealAbyssalPlainPlan(AbyssalPlainPlanV2 plan) {
        return plan.withCanonicalChecksum(abyssalPlainPlanChecksum(plan));
    }

    public String seamountPlanChecksum(SeamountPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public SeamountPlanV2 sealSeamountPlan(SeamountPlanV2 plan) {
        return plan.withCanonicalChecksum(seamountPlanChecksum(plan));
    }

    public String foundationAdditionalMarineValidationArtifactChecksum(
            FoundationAdditionalMarineValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationAdditionalMarineValidationArtifactV2 sealFoundationAdditionalMarineValidationArtifact(
            FoundationAdditionalMarineValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationAdditionalMarineValidationArtifactChecksum(artifact));
    }

    public String sinkholePlanChecksum(SinkholePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public SinkholePlanV2 sealSinkholePlan(SinkholePlanV2 plan) {
        return plan.withCanonicalChecksum(sinkholePlanChecksum(plan));
    }

    public String karstSpringPlanChecksum(KarstSpringPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public KarstSpringPlanV2 sealKarstSpringPlan(KarstSpringPlanV2 plan) {
        return plan.withCanonicalChecksum(karstSpringPlanChecksum(plan));
    }

    public String karstHydrologyGraphPlanChecksum(KarstHydrologyGraphPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public KarstHydrologyGraphPlanV2 sealKarstHydrologyGraphPlan(KarstHydrologyGraphPlanV2 plan) {
        return plan.withCanonicalChecksum(karstHydrologyGraphPlanChecksum(plan));
    }

    public String cenotePlanChecksum(CenotePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public CenotePlanV2 sealCenotePlan(CenotePlanV2 plan) {
        return plan.withCanonicalChecksum(cenotePlanChecksum(plan));
    }

    public String foundationKarstHydrologyValidationArtifactChecksum(
            FoundationKarstHydrologyValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationKarstHydrologyValidationArtifactV2 sealFoundationKarstHydrologyValidationArtifact(
            FoundationKarstHydrologyValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationKarstHydrologyValidationArtifactChecksum(artifact));
    }

    public String canonicalFoundationSingleIslandValidationArtifact(
            FoundationSingleIslandValidationArtifactV2 artifact
    ) {
        return CanonicalJsonV2.string(mapper.valueToTree(artifact));
    }

    public String foundationSingleIslandValidationArtifactChecksum(
            FoundationSingleIslandValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationSingleIslandValidationArtifactV2 sealFoundationSingleIslandValidationArtifact(
            FoundationSingleIslandValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationSingleIslandValidationArtifactChecksum(artifact));
    }

    public String canonicalFoundationArchipelagoValidationArtifact(
            FoundationArchipelagoValidationArtifactV2 artifact
    ) {
        return CanonicalJsonV2.string(mapper.valueToTree(artifact));
    }

    public String foundationArchipelagoValidationArtifactChecksum(
            FoundationArchipelagoValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationArchipelagoValidationArtifactV2 sealFoundationArchipelagoValidationArtifact(
            FoundationArchipelagoValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationArchipelagoValidationArtifactChecksum(artifact));
    }

    public String canonicalFoundationVolcanicConeValidationArtifact(
            FoundationVolcanicConeValidationArtifactV2 artifact
    ) {
        return CanonicalJsonV2.string(mapper.valueToTree(artifact));
    }

    public String foundationVolcanicConeValidationArtifactChecksum(
            FoundationVolcanicConeValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationVolcanicConeValidationArtifactV2 sealFoundationVolcanicConeValidationArtifact(
            FoundationVolcanicConeValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationVolcanicConeValidationArtifactChecksum(artifact));
    }

    public String canonicalFoundationRockyCoastCliffValidationArtifact(
            FoundationRockyCoastCliffValidationArtifactV2 artifact
    ) {
        return CanonicalJsonV2.string(mapper.valueToTree(artifact));
    }

    public String foundationRockyCoastCliffValidationArtifactChecksum(
            FoundationRockyCoastCliffValidationArtifactV2 artifact
    ) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationRockyCoastCliffValidationArtifactV2 sealFoundationRockyCoastCliffValidationArtifact(
            FoundationRockyCoastCliffValidationArtifactV2 artifact
    ) {
        return artifact.withCanonicalChecksum(foundationRockyCoastCliffValidationArtifactChecksum(artifact));
    }

    public String canonicalFoundationPreviewIndex(FoundationPreviewIndexV2 index) {
        return CanonicalJsonV2.string(mapper.valueToTree(index));
    }

    public String foundationPreviewIndexChecksum(FoundationPreviewIndexV2 index) {
        ObjectNode tree = mapper.valueToTree(index);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FoundationPreviewIndexV2 sealFoundationPreviewIndex(FoundationPreviewIndexV2 index) {
        return index.withCanonicalChecksum(foundationPreviewIndexChecksum(index));
    }

    public String canonicalFeatureMaterialProfilePlan(FeatureMaterialProfilePlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String featureMaterialProfilePlanChecksum(FeatureMaterialProfilePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public FeatureMaterialProfilePlanV2 sealFeatureMaterialProfilePlan(FeatureMaterialProfilePlanV2 plan) {
        return plan.withCanonicalChecksum(featureMaterialProfilePlanChecksum(plan));
    }

    public String canonicalVolumeSdfPrimitivePlan(VolumeSdfPrimitivePlanV2 plan) {
        return CanonicalJsonV2.string(volumeSdfPrimitivePlanTree(plan));
    }

    public String volumeSdfPrimitivePlanChecksum(VolumeSdfPrimitivePlanV2 plan) {
        ObjectNode tree = volumeSdfPrimitivePlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public VolumeSdfPrimitivePlanV2 sealVolumeSdfPrimitivePlan(VolumeSdfPrimitivePlanV2 plan) {
        return plan.withCanonicalChecksum(volumeSdfPrimitivePlanChecksum(plan));
    }

    public String canonicalVolumeCsgPlan(VolumeCsgPlanV2 plan) {
        return CanonicalJsonV2.string(volumeCsgPlanTree(plan));
    }

    public String volumeCsgPlanChecksum(VolumeCsgPlanV2 plan) {
        ObjectNode tree = volumeCsgPlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public VolumeCsgPlanV2 sealVolumeCsgPlan(VolumeCsgPlanV2 plan) {
        return plan.withCanonicalChecksum(volumeCsgPlanChecksum(plan));
    }

    public String canonicalVolumeAabbIndexPlan(VolumeAabbIndexPlanV2 plan) {
        return CanonicalJsonV2.string(volumeAabbIndexPlanTree(plan));
    }

    public String volumeAabbIndexPlanChecksum(VolumeAabbIndexPlanV2 plan) {
        ObjectNode tree = volumeAabbIndexPlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public VolumeAabbIndexPlanV2 sealVolumeAabbIndexPlan(VolumeAabbIndexPlanV2 plan) {
        return plan.withCanonicalChecksum(volumeAabbIndexPlanChecksum(plan));
    }

    public String canonicalVolumeTileCachePlan(VolumeTileCachePlanV2 plan) {
        return CanonicalJsonV2.string(volumeTileCachePlanTree(plan));
    }

    public String volumeTileCachePlanChecksum(VolumeTileCachePlanV2 plan) {
        ObjectNode tree = volumeTileCachePlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public VolumeTileCachePlanV2 sealVolumeTileCachePlan(VolumeTileCachePlanV2 plan) {
        return plan.withCanonicalChecksum(volumeTileCachePlanChecksum(plan));
    }

    public String canonicalCaveNetworkPlan(CaveNetworkPlanV2 plan) {
        return CanonicalJsonV2.string(caveNetworkPlanTree(plan));
    }

    public String caveNetworkPlanChecksum(CaveNetworkPlanV2 plan) {
        ObjectNode tree = caveNetworkPlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public CaveNetworkPlanV2 sealCaveNetworkPlan(CaveNetworkPlanV2 plan) {
        return plan.withCanonicalChecksum(caveNetworkPlanChecksum(plan));
    }

    public String canonicalLushCavePlan(LushCavePlanV2 plan) {
        return CanonicalJsonV2.string(lushCavePlanTree(plan));
    }

    public String lushCavePlanChecksum(LushCavePlanV2 plan) {
        ObjectNode tree = lushCavePlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public LushCavePlanV2 sealLushCavePlan(LushCavePlanV2 plan) {
        return plan.withCanonicalChecksum(lushCavePlanChecksum(plan));
    }

    public String canonicalUndergroundLakePlan(UndergroundLakePlanV2 plan) {
        return CanonicalJsonV2.string(undergroundLakePlanTree(plan));
    }

    public String undergroundLakePlanChecksum(UndergroundLakePlanV2 plan) {
        ObjectNode tree = undergroundLakePlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public UndergroundLakePlanV2 sealUndergroundLakePlan(UndergroundLakePlanV2 plan) {
        return plan.withCanonicalChecksum(undergroundLakePlanChecksum(plan));
    }

    public String canonicalSeaCavePlan(SeaCavePlanV2 plan) {
        return CanonicalJsonV2.string(seaCavePlanTree(plan));
    }

    public String seaCavePlanChecksum(SeaCavePlanV2 plan) {
        ObjectNode tree = seaCavePlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public SeaCavePlanV2 sealSeaCavePlan(SeaCavePlanV2 plan) {
        return plan.withCanonicalChecksum(seaCavePlanChecksum(plan));
    }

    public String canonicalOverhangPlan(OverhangPlanV2 plan) {
        return CanonicalJsonV2.string(overhangPlanTree(plan));
    }

    public String overhangPlanChecksum(OverhangPlanV2 plan) {
        ObjectNode tree = overhangPlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public OverhangPlanV2 sealOverhangPlan(OverhangPlanV2 plan) {
        return plan.withCanonicalChecksum(overhangPlanChecksum(plan));
    }

    public String canonicalNaturalArchPlan(NaturalArchPlanV2 plan) {
        return CanonicalJsonV2.string(naturalArchPlanTree(plan));
    }

    public String naturalArchPlanChecksum(NaturalArchPlanV2 plan) {
        ObjectNode tree = naturalArchPlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public NaturalArchPlanV2 sealNaturalArchPlan(NaturalArchPlanV2 plan) {
        return plan.withCanonicalChecksum(naturalArchPlanChecksum(plan));
    }

    public String canonicalSkyIslandGroupPlan(SkyIslandGroupPlanV2 plan) {
        return CanonicalJsonV2.string(skyIslandGroupPlanTree(plan));
    }

    public String skyIslandGroupPlanChecksum(SkyIslandGroupPlanV2 plan) {
        ObjectNode tree = skyIslandGroupPlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public SkyIslandGroupPlanV2 sealSkyIslandGroupPlan(SkyIslandGroupPlanV2 plan) {
        return plan.withCanonicalChecksum(skyIslandGroupPlanChecksum(plan));
    }

    public String canonicalWaterfallVolumePlan(WaterfallVolumePlanV2 plan) {
        return CanonicalJsonV2.string(waterfallVolumePlanTree(plan));
    }

    public String waterfallVolumePlanChecksum(WaterfallVolumePlanV2 plan) {
        ObjectNode tree = waterfallVolumePlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public WaterfallVolumePlanV2 sealWaterfallVolumePlan(WaterfallVolumePlanV2 plan) {
        return plan.withCanonicalChecksum(waterfallVolumePlanChecksum(plan));
    }

    public String canonicalVolumeLocalEnvironmentPlan(VolumeLocalEnvironmentPlanV2 plan) {
        return CanonicalJsonV2.string(volumeLocalEnvironmentPlanTree(plan));
    }

    public String volumeLocalEnvironmentPlanChecksum(VolumeLocalEnvironmentPlanV2 plan) {
        ObjectNode tree = volumeLocalEnvironmentPlanTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public VolumeLocalEnvironmentPlanV2 sealVolumeLocalEnvironmentPlan(VolumeLocalEnvironmentPlanV2 plan) {
        return plan.withCanonicalChecksum(volumeLocalEnvironmentPlanChecksum(plan));
    }

    public String canonicalPlacementPlan(PlacementPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String placementPlanChecksum(PlacementPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlacementPlanV2 sealPlacementPlan(PlacementPlanV2 plan) {
        return plan.withCanonicalChecksum(placementPlanChecksum(plan));
    }

    public String canonicalPlacementJournal(PlacementJournalV2 journal) {
        return CanonicalJsonV2.string(mapper.valueToTree(journal));
    }

    public String placementJournalChecksum(PlacementJournalV2 journal) {
        ObjectNode tree = mapper.valueToTree(journal);
        tree.remove("journalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlacementJournalV2 sealPlacementJournal(PlacementJournalV2 journal) {
        return journal.withJournalChecksum(placementJournalChecksum(journal));
    }

    public String canonicalPlacementEnvelopePlan(PlacementEnvelopePlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String placementEnvelopeMutationChecksum(PlacementEnvelopePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        tree.remove("mutationEnvelopeChecksum");
        tree.remove("unionEffectEnvelope");
        tree.remove("diskEstimate");
        ArrayNode tiles = (ArrayNode) tree.get("tiles");
        if (tiles != null) {
            for (JsonNode tile : tiles) {
                ((ObjectNode) tile).remove("effectAabb");
            }
        }
        return CanonicalJsonV2.checksum(tree);
    }

    public String placementEnvelopePlanChecksum(PlacementEnvelopePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        tree.remove("mutationEnvelopeChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlacementEnvelopePlanV2 sealPlacementEnvelopePlan(PlacementEnvelopePlanV2 plan) {
        return plan.withChecksums(
                placementEnvelopeMutationChecksum(plan),
                placementEnvelopePlanChecksum(plan));
    }

    public String canonicalPlacementReservationPlan(PlacementReservationPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String placementReservationPlanChecksum(PlacementReservationPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlacementReservationPlanV2 sealPlacementReservationPlan(PlacementReservationPlanV2 plan) {
        return plan.withCanonicalChecksum(placementReservationPlanChecksum(plan));
    }

    public String canonicalPlacementSafetyState(PlacementSafetyStateV2 state) {
        return CanonicalJsonV2.string(mapper.valueToTree(state));
    }

    public String placementSafetyStateChecksum(PlacementSafetyStateV2 state) {
        ObjectNode tree = mapper.valueToTree(state);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlacementSafetyStateV2 sealPlacementSafetyState(PlacementSafetyStateV2 state) {
        return state.withCanonicalChecksum(placementSafetyStateChecksum(state));
    }

    public String canonicalPlacementSnapshotPlan(PlacementSnapshotPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String placementSnapshotPlanChecksum(PlacementSnapshotPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlacementSnapshotPlanV2 sealPlacementSnapshotPlan(PlacementSnapshotPlanV2 plan) {
        return plan.withCanonicalChecksum(placementSnapshotPlanChecksum(plan));
    }

    public String canonicalPlacementContainmentEvidence(PlacementContainmentEvidenceV2 evidence) {
        return CanonicalJsonV2.string(mapper.valueToTree(evidence));
    }

    public String placementContainmentEvidenceChecksum(PlacementContainmentEvidenceV2 evidence) {
        ObjectNode tree = mapper.valueToTree(evidence);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlacementContainmentEvidenceV2 sealPlacementContainmentEvidence(
            PlacementContainmentEvidenceV2 evidence
    ) {
        return evidence.withCanonicalChecksum(placementContainmentEvidenceChecksum(evidence));
    }

    public String canonicalPlacementVerifyEvidence(PlacementVerifyEvidenceV2 evidence) {
        return CanonicalJsonV2.string(mapper.valueToTree(evidence));
    }

    public String placementVerifyEvidenceChecksum(PlacementVerifyEvidenceV2 evidence) {
        ObjectNode tree = mapper.valueToTree(evidence);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlacementVerifyEvidenceV2 sealPlacementVerifyEvidence(
            PlacementVerifyEvidenceV2 evidence
    ) {
        return evidence.withCanonicalChecksum(placementVerifyEvidenceChecksum(evidence));
    }

    public String canonicalPlacementUndoPlan(PlacementUndoPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String placementUndoPlanChecksum(PlacementUndoPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlacementUndoPlanV2 sealPlacementUndoPlan(PlacementUndoPlanV2 plan) {
        return plan.withCanonicalChecksum(placementUndoPlanChecksum(plan));
    }

    public String canonicalPlacementRecoveryPlan(PlacementRecoveryPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String placementRecoveryPlanChecksum(PlacementRecoveryPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public PlacementRecoveryPlanV2 sealPlacementRecoveryPlan(PlacementRecoveryPlanV2 plan) {
        return plan.withCanonicalChecksum(placementRecoveryPlanChecksum(plan));
    }

    public String canonicalHydrologyPlan(HydrologyPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String hydrologyPlanChecksum(HydrologyPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public HydrologyPlanV2 sealHydrologyPlan(HydrologyPlanV2 plan) {
        return plan.withCanonicalChecksum(hydrologyPlanChecksum(plan));
    }

    public String canonicalHydrologyReconciliationPlan(HydrologyReconciliationPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String hydrologyReconciliationPlanChecksum(HydrologyReconciliationPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public HydrologyReconciliationPlanV2 sealHydrologyReconciliationPlan(
            HydrologyReconciliationPlanV2 plan
    ) {
        return plan.withCanonicalChecksum(hydrologyReconciliationPlanChecksum(plan));
    }

    public void writeTerrainIntent(Path path, TerrainIntentV2 intent) throws IOException {
        writeCanonical(path, intentTree(intent), INTENT_SCHEMA);
    }

    public void writeGenerationRequest(Path path, GenerationRequestV2 request) throws IOException {
        writeCanonical(path, generationRequestTree(request), REQUEST_SCHEMA);
    }

    public void writeWorldBlueprint(Path path, WorldBlueprintV2 blueprint) throws IOException {
        verifyGeologyPlanChecksum(blueprint.geologyPlan());
        verifyLithologyPlanChecksum(blueprint.lithologyPlan());
        verifyStrataPlanChecksum(blueprint.strataPlan());
        verifyClimatePlanChecksum(blueprint.climatePlan());
        verifyWaterConditionPlanChecksum(blueprint.waterConditionPlan());
        verifyHydrologyPlanChecksum(blueprint.hydrologyPlan());
        verifyHydrologyReconciliationPlanChecksum(blueprint.hydrologyReconciliationPlan());
        verifyBlueprintChecksum(blueprint);
        writeCanonical(path, mapper.valueToTree(blueprint), BLUEPRINT_SCHEMA);
    }

    public void writeGeologyPlan(Path path, GeologyPlanV2 plan) throws IOException {
        verifyGeologyPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), GEOLOGY_PLAN_SCHEMA);
    }

    public void writeLithologyPlan(Path path, LithologyPlanV2 plan) throws IOException {
        verifyLithologyPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), LITHOLOGY_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeStrataPlan(Path path, StrataPlanV2 plan) throws IOException {
        verifyStrataPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), STRATA_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeClimatePlan(Path path, ClimatePlanV2 plan) throws IOException {
        verifyClimatePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), CLIMATE_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeWaterConditionPlan(Path path, WaterConditionPlanV2 plan) throws IOException {
        verifyWaterConditionPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), WATER_CONDITION_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public SnowPlanV2 readSnowPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(SNOW_PLAN_SCHEMA, path.toString(), node);
        SnowPlanV2 plan = mapper.treeToValue(node, SnowPlanV2.class);
        verifySnowPlanChecksum(plan);
        return plan;
    }

    public SnowPlanV2 readSnowPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(SNOW_PLAN_SCHEMA, documentName, node);
        SnowPlanV2 plan = mapper.treeToValue(node, SnowPlanV2.class);
        verifySnowPlanChecksum(plan);
        return plan;
    }

    public void writeSnowPlan(Path path, SnowPlanV2 plan) throws IOException {
        verifySnowPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), SNOW_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeMaterialProfilePlan(Path path, MaterialProfilePlanV2 plan) throws IOException {
        verifyMaterialProfilePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), MATERIAL_PROFILE_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeMinecraftPalettePlan(Path path, MinecraftPalettePlanV2 plan) throws IOException {
        verifyMinecraftPalettePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), MINECRAFT_PALETTE_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeEcologyPlan(Path path, EcologyPlanV2 plan) throws IOException {
        verifyEcologyPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), ECOLOGY_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeSurfaceFoundationPlan(Path path, SurfaceFoundationPlanV2 plan) throws IOException {
        verifySurfaceFoundationPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), SURFACE_FOUNDATION_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writePlainPlan(Path path, PlainPlanV2 plan) throws IOException {
        verifyPlainPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), PLAIN_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeHillRangePlan(Path path, HillRangePlanV2 plan) throws IOException {
        verifyHillRangePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), HILL_RANGE_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeMountainRangePlan(Path path, MountainRangePlanV2 plan) throws IOException {
        verifyMountainRangePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), MOUNTAIN_RANGE_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeValleyPlan(Path path, ValleyPlanV2 plan) throws IOException {
        verifyValleyPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), VALLEY_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeRiverPlan(Path path, RiverPlanV2 plan) throws IOException {
        verifyRiverPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), RIVER_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeFloodplainPlan(Path path, FloodplainPlanV2 plan) throws IOException {
        verifyFloodplainPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), FLOODPLAIN_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeMarshPlan(Path path, MarshPlanV2 plan) throws IOException {
        verifyMarshPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), MARSH_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeRockyCoastPlan(Path path, RockyCoastPlanV2 plan) throws IOException {
        verifyRockyCoastPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), ROCKY_COAST_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeSeaCliffPlan(Path path, SeaCliffPlanV2 plan) throws IOException {
        verifySeaCliffPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), SEA_CLIFF_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeSingleIslandPlan(Path path, SingleIslandPlanV2 plan) throws IOException {
        verifySingleIslandPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), SINGLE_ISLAND_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeArchipelagoPlan(Path path, ArchipelagoPlanV2 plan) throws IOException {
        verifyArchipelagoPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), ARCHIPELAGO_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeVolcanicConePlan(Path path, VolcanicConePlanV2 plan) throws IOException {
        verifyVolcanicConePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), VOLCANIC_CONE_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeOceanBasinPlan(Path path, OceanBasinPlanV2 plan) throws IOException {
        verifyOceanBasinPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), OCEAN_BASIN_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeContinentalShelfPlan(Path path, ContinentalShelfPlanV2 plan) throws IOException {
        verifyContinentalShelfPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), CONTINENTAL_SHELF_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeContinentalSlopePlan(Path path, ContinentalSlopePlanV2 plan) throws IOException {
        verifyContinentalSlopePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), CONTINENTAL_SLOPE_PLAN_SCHEMA, 64L * 1024L);
    }

    public void writeFoundationOceanBasinValidationArtifact(
            Path path,
            FoundationOceanBasinValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationOceanBasinValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_OCEAN_BASIN_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeFoundationContinentalShelfValidationArtifact(
            Path path,
            FoundationContinentalShelfValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationContinentalShelfValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_CONTINENTAL_SHELF_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeFoundationContinentalSlopeValidationArtifact(
            Path path,
            FoundationContinentalSlopeValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationContinentalSlopeValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_CONTINENTAL_SLOPE_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeSubmarineCanyonPlan(Path path, SubmarineCanyonPlanV2 plan) throws IOException {
        verifySubmarineCanyonPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), SUBMARINE_CANYON_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationSubmarineCanyonValidationArtifact(
            Path path,
            FoundationSubmarineCanyonValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationSubmarineCanyonValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_SUBMARINE_CANYON_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeCaveEntrancePlan(Path path, CaveEntrancePlanV2 plan) throws IOException {
        verifyCaveEntrancePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), CAVE_ENTRANCE_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationCaveEntranceValidationArtifact(
            Path path,
            FoundationCaveEntranceValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationCaveEntranceValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_CAVE_ENTRANCE_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeUndergroundRiverPlan(Path path, UndergroundRiverPlanV2 plan) throws IOException {
        verifyUndergroundRiverPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), UNDERGROUND_RIVER_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeLavaTubePlan(Path path, LavaTubePlanV2 plan) throws IOException {
        verifyLavaTubePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), LAVA_TUBE_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationLavaTubeValidationArtifact(
            Path path,
            FoundationLavaTubeValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationLavaTubeValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_LAVA_TUBE_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeSpringPlan(Path path, SpringPlanV2 plan) throws IOException {
        verifySpringPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), SPRING_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationSpringValidationArtifact(
            Path path,
            FoundationSpringValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationSpringValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_SPRING_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeOxbowLakePlan(Path path, OxbowLakePlanV2 plan) throws IOException {
        verifyOxbowLakePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), OXBOW_LAKE_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationOxbowLakeValidationArtifact(
            Path path,
            FoundationOxbowLakeValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationOxbowLakeValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_OXBOW_LAKE_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeFoundationUndergroundRiverValidationArtifact(
            Path path,
            FoundationUndergroundRiverValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationUndergroundRiverValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_UNDERGROUND_RIVER_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeMacroLandWaterTopologyPlan(Path path, MacroLandWaterTopologyPlanV2 plan)
            throws IOException {
        verifyMacroLandWaterTopologyPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), MACRO_LAND_WATER_TOPOLOGY_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationMacroLandWaterTopologyValidationArtifact(
            Path path,
            FoundationMacroLandWaterTopologyValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationMacroLandWaterTopologyValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_MACRO_LAND_WATER_TOPOLOGY_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeWaterfallChainPlan(Path path, WaterfallChainPlanV2 plan) throws IOException {
        verifyWaterfallChainPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), WATERFALL_CHAIN_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationRiverGraphRolesValidationArtifact(
            Path path,
            FoundationRiverGraphRolesValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationRiverGraphRolesValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_RIVER_GRAPH_ROLES_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeGlacialIcePlan(Path path, GlacialIcePlanV2 plan) throws IOException {
        verifyGlacialIcePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), GLACIAL_ICE_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationGlacialIceValidationArtifact(
            Path path,
            FoundationGlacialIceValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationGlacialIceValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_GLACIAL_ICE_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeIceFjordPlan(Path path, IceFjordPlanV2 plan) throws IOException {
        verifyIceFjordPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), ICE_FJORD_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeBarrierIslandPlan(Path path, BarrierIslandPlanV2 plan) throws IOException {
        verifyBarrierIslandPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), BARRIER_ISLAND_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeAtollPlan(Path path, AtollPlanV2 plan) throws IOException {
        verifyAtollPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), ATOLL_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeAdvancedIslandReefCatalogContract(Path path, AdvancedIslandReefCatalogContractV2 contract)
            throws IOException {
        verifyAdvancedIslandReefCatalogContractChecksum(contract);
        writeCanonical(path, mapper.valueToTree(contract), ADVANCED_ISLAND_REEF_CATALOG_CONTRACT_SCHEMA,
                256L * 1024L);
    }

    public void writeFoundationAdvancedIslandReefValidationArtifact(
            Path path,
            FoundationAdvancedIslandReefValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationAdvancedIslandReefValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_ADVANCED_ISLAND_REEF_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeAdvancedRiverLakeSplitContract(Path path, AdvancedRiverLakeSplitContractV2 contract)
            throws IOException {
        verifyAdvancedRiverLakeSplitContractChecksum(contract);
        writeCanonical(path, mapper.valueToTree(contract), ADVANCED_RIVER_LAKE_SPLIT_CONTRACT_SCHEMA,
                256L * 1024L);
    }

    public void writeEscarpmentPlan(Path path, EscarpmentPlanV2 plan) throws IOException {
        verifyEscarpmentPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), ESCARPMENT_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writePlateauPlan(Path path, PlateauPlanV2 plan) throws IOException {
        verifyPlateauPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), PLATEAU_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeDryLandModifierContract(Path path, DryLandModifierContractV2 contract) throws IOException {
        verifyDryLandModifierContractChecksum(contract);
        writeCanonical(path, mapper.valueToTree(contract), DRY_LAND_MODIFIER_CONTRACT_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationEscarpmentPlateauValidationArtifact(
            Path path,
            FoundationEscarpmentPlateauValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationEscarpmentPlateauValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_ESCARPMENT_PLATEAU_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeMoraineFieldPlan(Path path, MoraineFieldPlanV2 plan) throws IOException {
        verifyMoraineFieldPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), MORAINE_FIELD_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeOutwashPlainPlan(Path path, OutwashPlainPlanV2 plan) throws IOException {
        verifyOutwashPlainPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), OUTWASH_PLAIN_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writePermafrostPlainProfile(Path path, PermafrostPlainProfileV2 profile) throws IOException {
        verifyPermafrostPlainProfileChecksum(profile);
        writeCanonical(path, mapper.valueToTree(profile), PERMAFROST_PLAIN_PROFILE_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationGlacialDepositionValidationArtifact(
            Path path,
            FoundationGlacialDepositionValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationGlacialDepositionValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_GLACIAL_DEPOSITION_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeAbyssalPlainPlan(Path path, AbyssalPlainPlanV2 plan) throws IOException {
        verifyAbyssalPlainPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), ABYSSAL_PLAIN_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeSeamountPlan(Path path, SeamountPlanV2 plan) throws IOException {
        verifySeamountPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), SEAMOUNT_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationAdditionalMarineValidationArtifact(
            Path path,
            FoundationAdditionalMarineValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationAdditionalMarineValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_ADDITIONAL_MARINE_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeSinkholePlan(Path path, SinkholePlanV2 plan) throws IOException {
        verifySinkholePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), SINKHOLE_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeKarstSpringPlan(Path path, KarstSpringPlanV2 plan) throws IOException {
        verifyKarstSpringPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), KARST_SPRING_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeKarstHydrologyGraphPlan(Path path, KarstHydrologyGraphPlanV2 plan) throws IOException {
        verifyKarstHydrologyGraphPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), KARST_HYDROLOGY_GRAPH_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeCenotePlan(Path path, CenotePlanV2 plan) throws IOException {
        verifyCenotePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), CENOTE_PLAN_SCHEMA, 256L * 1024L);
    }

    public void writeFoundationKarstHydrologyValidationArtifact(
            Path path,
            FoundationKarstHydrologyValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationKarstHydrologyValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_KARST_HYDROLOGY_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeFoundationSingleIslandValidationArtifact(
            Path path,
            FoundationSingleIslandValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationSingleIslandValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_SINGLE_ISLAND_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeFoundationArchipelagoValidationArtifact(
            Path path,
            FoundationArchipelagoValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationArchipelagoValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_ARCHIPELAGO_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeFoundationVolcanicConeValidationArtifact(
            Path path,
            FoundationVolcanicConeValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationVolcanicConeValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_VOLCANIC_CONE_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeFoundationValidationArtifact(Path path, FoundationValidationArtifactV2 artifact)
            throws IOException {
        verifyFoundationValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact), FOUNDATION_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeFoundationRangeValleyValidationArtifact(
            Path path,
            FoundationRangeValleyValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationRangeValleyValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact), FOUNDATION_RANGE_VALLEY_VALIDATION_ARTIFACT_SCHEMA,
                64L * 1024L);
    }

    public void writeFoundationRiverValidationArtifact(
            Path path,
            FoundationRiverValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationRiverValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact), FOUNDATION_RIVER_VALIDATION_ARTIFACT_SCHEMA,
                64L * 1024L);
    }

    public void writeFoundationFloodplainMarshValidationArtifact(
            Path path,
            FoundationFloodplainMarshValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationFloodplainMarshValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_FLOODPLAIN_MARSH_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeFoundationRockyCoastCliffValidationArtifact(
            Path path,
            FoundationRockyCoastCliffValidationArtifactV2 artifact
    ) throws IOException {
        verifyFoundationRockyCoastCliffValidationArtifactChecksum(artifact);
        writeCanonical(path, mapper.valueToTree(artifact),
                FOUNDATION_ROCKY_COAST_CLIFF_VALIDATION_ARTIFACT_SCHEMA, 64L * 1024L);
    }

    public void writeFoundationPreviewIndex(Path path, FoundationPreviewIndexV2 index) throws IOException {
        verifyFoundationPreviewIndexChecksum(index);
        writeCanonical(path, mapper.valueToTree(index), FOUNDATION_PREVIEW_INDEX_SCHEMA, 64L * 1024L);
    }

    public void writeFeatureMaterialProfilePlan(Path path, FeatureMaterialProfilePlanV2 plan) throws IOException {
        verifyFeatureMaterialProfilePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), FEATURE_MATERIAL_PROFILE_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeVolumeSdfPrimitivePlan(Path path, VolumeSdfPrimitivePlanV2 plan) throws IOException {
        verifyVolumeSdfPrimitivePlanChecksum(plan);
        writeCanonical(path, volumeSdfPrimitivePlanTree(plan), VOLUME_SDF_PRIMITIVE_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeVolumeCsgPlan(Path path, VolumeCsgPlanV2 plan) throws IOException {
        verifyVolumeCsgPlanChecksum(plan);
        writeCanonical(path, volumeCsgPlanTree(plan), VOLUME_CSG_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeVolumeAabbIndexPlan(Path path, VolumeAabbIndexPlanV2 plan) throws IOException {
        verifyVolumeAabbIndexPlanChecksum(plan);
        writeCanonical(path, volumeAabbIndexPlanTree(plan), VOLUME_AABB_INDEX_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeVolumeTileCachePlan(Path path, VolumeTileCachePlanV2 plan) throws IOException {
        verifyVolumeTileCachePlanChecksum(plan);
        writeCanonical(path, volumeTileCachePlanTree(plan), VOLUME_TILE_CACHE_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeCaveNetworkPlan(Path path, CaveNetworkPlanV2 plan) throws IOException {
        verifyCaveNetworkPlanChecksum(plan);
        writeCanonical(path, caveNetworkPlanTree(plan), CAVE_NETWORK_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeLushCavePlan(Path path, LushCavePlanV2 plan) throws IOException {
        verifyLushCavePlanChecksum(plan);
        writeCanonical(path, lushCavePlanTree(plan), LUSH_CAVE_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeUndergroundLakePlan(Path path, UndergroundLakePlanV2 plan) throws IOException {
        verifyUndergroundLakePlanChecksum(plan);
        writeCanonical(path, undergroundLakePlanTree(plan), UNDERGROUND_LAKE_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeSeaCavePlan(Path path, SeaCavePlanV2 plan) throws IOException {
        verifySeaCavePlanChecksum(plan);
        writeCanonical(path, seaCavePlanTree(plan), SEA_CAVE_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeOverhangPlan(Path path, OverhangPlanV2 plan) throws IOException {
        verifyOverhangPlanChecksum(plan);
        writeCanonical(path, overhangPlanTree(plan), OVERHANG_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeNaturalArchPlan(Path path, NaturalArchPlanV2 plan) throws IOException {
        verifyNaturalArchPlanChecksum(plan);
        writeCanonical(path, naturalArchPlanTree(plan), NATURAL_ARCH_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeSkyIslandGroupPlan(Path path, SkyIslandGroupPlanV2 plan) throws IOException {
        verifySkyIslandGroupPlanChecksum(plan);
        writeCanonical(path, skyIslandGroupPlanTree(plan), SKY_ISLAND_GROUP_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeWaterfallVolumePlan(Path path, WaterfallVolumePlanV2 plan) throws IOException {
        verifyWaterfallVolumePlanChecksum(plan);
        writeCanonical(path, waterfallVolumePlanTree(plan), WATERFALL_VOLUME_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeVolumeLocalEnvironmentPlan(Path path, VolumeLocalEnvironmentPlanV2 plan)
            throws IOException {
        verifyVolumeLocalEnvironmentPlanChecksum(plan);
        writeCanonical(path, volumeLocalEnvironmentPlanTree(plan), VOLUME_LOCAL_ENVIRONMENT_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writePlacementPlan(Path path, PlacementPlanV2 plan) throws IOException {
        verifyPlacementPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), PLACEMENT_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writePlacementJournal(Path path, PlacementJournalV2 journal) throws IOException {
        verifyPlacementJournalChecksum(journal);
        writeCanonical(path, mapper.valueToTree(journal), PLACEMENT_JOURNAL_SCHEMA,
                PlacementJournalV2.MAX_CANONICAL_BYTES);
    }

    public void writePlacementEnvelopePlan(Path path, PlacementEnvelopePlanV2 plan) throws IOException {
        verifyPlacementEnvelopePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), PLACEMENT_ENVELOPE_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writePlacementReservationPlan(Path path, PlacementReservationPlanV2 plan) throws IOException {
        verifyPlacementReservationPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), PLACEMENT_RESERVATION_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writePlacementSafetyStateV2(Path path, PlacementSafetyStateV2 state) throws IOException {
        verifyPlacementSafetyStateChecksum(state);
        writeCanonical(path, mapper.valueToTree(state), PLACEMENT_SAFETY_STATE_SCHEMA,
                PlacementSafetyStateV2.MAX_CANONICAL_BYTES);
    }

    public void writePlacementSnapshotPlan(Path path, PlacementSnapshotPlanV2 plan) throws IOException {
        verifyPlacementSnapshotPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), PLACEMENT_SNAPSHOT_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writePlacementContainmentPolicy(Path path, PlacementContainmentPolicyV2 policy)
            throws IOException {
        writeCanonical(path, mapper.valueToTree(policy), PLACEMENT_CONTAINMENT_POLICY_SCHEMA,
                PlacementContainmentPolicyV2.ResourceBudget.MAX_CANONICAL_BYTES);
    }

    public void writePlacementContainmentEvidence(Path path, PlacementContainmentEvidenceV2 evidence)
            throws IOException {
        verifyPlacementContainmentEvidenceChecksum(evidence);
        writeCanonical(path, mapper.valueToTree(evidence), PLACEMENT_CONTAINMENT_EVIDENCE_SCHEMA,
                evidence.policy().budget().maximumCanonicalBytes());
    }

    public void writePlacementSettleVerifyPolicy(Path path, PlacementSettleVerifyPolicyV2 policy)
            throws IOException {
        writeCanonical(path, mapper.valueToTree(policy), PLACEMENT_SETTLE_VERIFY_POLICY_SCHEMA,
                PlacementSettleVerifyPolicyV2.ResourceBudget.MAX_CANONICAL_BYTES);
    }

    public void writePlacementVerifyEvidence(Path path, PlacementVerifyEvidenceV2 evidence)
            throws IOException {
        verifyPlacementVerifyEvidenceChecksum(evidence);
        writeCanonical(path, mapper.valueToTree(evidence), PLACEMENT_VERIFY_EVIDENCE_SCHEMA,
                evidence.policy().budget().maximumCanonicalBytes());
    }

    public void writePlacementUndoPlan(Path path, PlacementUndoPlanV2 plan) throws IOException {
        verifyPlacementUndoPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), PLACEMENT_UNDO_PLAN_SCHEMA,
                PlacementUndoPlanV2.MAX_CANONICAL_BYTES);
    }

    public void writePlacementRecoveryPlan(Path path, PlacementRecoveryPlanV2 plan) throws IOException {
        verifyPlacementRecoveryPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), PLACEMENT_RECOVERY_PLAN_SCHEMA,
                PlacementRecoveryPlanV2.MAX_CANONICAL_BYTES);
    }

    public void writeHydrologyPlan(Path path, HydrologyPlanV2 plan) throws IOException {
        verifyHydrologyPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), HYDROLOGY_PLAN_SCHEMA);
    }

    public void writeHydrologyReconciliationPlan(
            Path path,
            HydrologyReconciliationPlanV2 plan
    ) throws IOException {
        verifyHydrologyReconciliationPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), HYDROLOGY_RECONCILIATION_PLAN_SCHEMA,
                plan.budget().maximumArtifactBytes());
    }

    private void writeCanonical(Path path, JsonNode tree, String schema) throws IOException {
        writeCanonical(path, tree, schema, LandformDataCodec.MAX_DOCUMENT_BYTES);
    }

    private void writeCanonical(Path path, JsonNode tree, String schema, long maximumBytes) throws IOException {
        validator.validate(schema, path.toString(), tree);
        byte[] bytes = CanonicalJsonV2.bytes(tree);
        long effectiveMaximum = Math.min(maximumBytes, LandformDataCodec.MAX_DOCUMENT_BYTES);
        if (bytes.length > effectiveMaximum) {
            throw new IOException("document exceeds output byte budget: " + path);
        }
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(absolute.getParent(), "output path must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "lfc-v2-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void verifyBlueprintChecksum(WorldBlueprintV2 blueprint) throws IOException {
        String actual = worldBlueprintChecksum(blueprint);
        if (!actual.equals(blueprint.canonicalChecksum())) {
            throw new IOException("world blueprint canonical checksum mismatch");
        }
    }

    private void verifyGeologyPlanChecksum(GeologyPlanV2 plan) throws IOException {
        String actual = geologyPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("geology plan canonical checksum mismatch");
        }
    }

    private void verifyLithologyPlanChecksum(LithologyPlanV2 plan) throws IOException {
        if (canonicalLithologyCatalog(plan.catalog()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.catalog().budget().maximumCanonicalBytes()) {
            throw new IOException("lithology catalog exceeds declared canonical byte budget");
        }
        if (canonicalLithologyPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("lithology plan exceeds declared canonical byte budget");
        }
        String catalogChecksum = lithologyCatalogChecksum(plan.catalog());
        if (!catalogChecksum.equals(plan.catalog().canonicalChecksum())) {
            throw new IOException("lithology catalog canonical checksum mismatch");
        }
        String planChecksum = lithologyPlanChecksum(plan);
        if (!planChecksum.equals(plan.canonicalChecksum())) {
            throw new IOException("lithology plan canonical checksum mismatch");
        }
    }

    private void verifyStrataPlanChecksum(StrataPlanV2 plan) throws IOException {
        if (canonicalStrataPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("strata plan exceeds declared canonical byte budget");
        }
        String actual = strataPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("strata plan canonical checksum mismatch");
        }
    }

    private void verifyClimatePlanChecksum(ClimatePlanV2 plan) throws IOException {
        if (canonicalClimatePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("climate plan exceeds declared canonical byte budget");
        }
        String actual = climatePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("climate plan canonical checksum mismatch");
        }
    }

    private void verifyWaterConditionPlanChecksum(WaterConditionPlanV2 plan) throws IOException {
        if (canonicalWaterConditionPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("water-condition plan exceeds declared canonical byte budget");
        }
        String actual = waterConditionPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("water-condition plan canonical checksum mismatch");
        }
    }

    private void verifySnowPlanChecksum(SnowPlanV2 plan) throws IOException {
        if (canonicalSnowPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("snow plan exceeds declared canonical byte budget");
        }
        String actual = snowPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("snow plan canonical checksum mismatch");
        }
    }

    private void verifyMaterialProfilePlanChecksum(MaterialProfilePlanV2 plan) throws IOException {
        if (canonicalMaterialProfilePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("material-profile plan exceeds declared canonical byte budget");
        }
        String actual = materialProfilePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("material-profile plan canonical checksum mismatch");
        }
    }

    private void verifyMinecraftPalettePlanChecksum(MinecraftPalettePlanV2 plan) throws IOException {
        if (canonicalMinecraftPalettePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("minecraft-palette plan exceeds declared canonical byte budget");
        }
        String actual = minecraftPalettePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("minecraft-palette plan canonical checksum mismatch");
        }
    }

    private void verifyEcologyPlanChecksum(EcologyPlanV2 plan) throws IOException {
        if (canonicalEcologyPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("ecology plan exceeds declared canonical byte budget");
        }
        String actual = ecologyPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("ecology plan canonical checksum mismatch");
        }
    }

    private void verifySurfaceFoundationPlanChecksum(SurfaceFoundationPlanV2 plan) throws IOException {
        if (canonicalSurfaceFoundationPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("surface foundation plan exceeds declared canonical byte budget");
        }
        String actual = surfaceFoundationPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("surface foundation plan canonical checksum mismatch");
        }
    }

    private void verifyPlainPlanChecksum(PlainPlanV2 plan) throws IOException {
        String actual = plainPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("plain plan canonical checksum mismatch");
        }
    }

    private void verifyHillRangePlanChecksum(HillRangePlanV2 plan) throws IOException {
        String actual = hillRangePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("hill range plan canonical checksum mismatch");
        }
    }

    private void verifyMountainRangePlanChecksum(MountainRangePlanV2 plan) throws IOException {
        String actual = mountainRangePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("mountain range plan canonical checksum mismatch");
        }
    }

    private void verifyValleyPlanChecksum(ValleyPlanV2 plan) throws IOException {
        String actual = valleyPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("valley plan canonical checksum mismatch");
        }
    }

    private void verifyRiverPlanChecksum(RiverPlanV2 plan) throws IOException {
        String actual = riverPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("river plan canonical checksum mismatch");
        }
    }

    private void verifyFloodplainPlanChecksum(FloodplainPlanV2 plan) throws IOException {
        String actual = floodplainPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("floodplain plan canonical checksum mismatch");
        }
    }

    private void verifyMarshPlanChecksum(MarshPlanV2 plan) throws IOException {
        String actual = marshPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("marsh plan canonical checksum mismatch");
        }
    }

    private void verifyRockyCoastPlanChecksum(RockyCoastPlanV2 plan) throws IOException {
        String actual = rockyCoastPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("rocky coast plan canonical checksum mismatch");
        }
    }

    private void verifySeaCliffPlanChecksum(SeaCliffPlanV2 plan) throws IOException {
        String actual = seaCliffPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("sea cliff plan canonical checksum mismatch");
        }
    }

    private void verifySingleIslandPlanChecksum(SingleIslandPlanV2 plan) throws IOException {
        String actual = singleIslandPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("single island plan canonical checksum mismatch");
        }
    }

    private void verifyArchipelagoPlanChecksum(ArchipelagoPlanV2 plan) throws IOException {
        String actual = archipelagoPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("archipelago plan canonical checksum mismatch");
        }
    }

    private void verifyVolcanicConePlanChecksum(VolcanicConePlanV2 plan) throws IOException {
        String actual = volcanicConePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("volcanic cone plan canonical checksum mismatch");
        }
    }

    private void verifyOceanBasinPlanChecksum(OceanBasinPlanV2 plan) throws IOException {
        String actual = oceanBasinPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("ocean basin plan canonical checksum mismatch");
        }
    }

    private void verifyContinentalShelfPlanChecksum(ContinentalShelfPlanV2 plan) throws IOException {
        String actual = continentalShelfPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("continental shelf plan canonical checksum mismatch");
        }
    }

    private void verifyContinentalSlopePlanChecksum(ContinentalSlopePlanV2 plan) throws IOException {
        String actual = continentalSlopePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("continental slope plan canonical checksum mismatch");
        }
    }

    private void verifyFoundationOceanBasinValidationArtifactChecksum(
            FoundationOceanBasinValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationOceanBasinValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("ocean basin validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationContinentalShelfValidationArtifactChecksum(
            FoundationContinentalShelfValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationContinentalShelfValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("continental shelf validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationContinentalSlopeValidationArtifactChecksum(
            FoundationContinentalSlopeValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationContinentalSlopeValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("continental slope validation artifact canonical checksum mismatch");
        }
    }

    private void verifySubmarineCanyonPlanChecksum(SubmarineCanyonPlanV2 plan) throws IOException {
        String actual = submarineCanyonPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("submarine canyon plan canonical checksum mismatch");
        }
    }

    private void verifyFoundationSubmarineCanyonValidationArtifactChecksum(
            FoundationSubmarineCanyonValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationSubmarineCanyonValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("submarine canyon validation artifact canonical checksum mismatch");
        }
    }

    private void verifyCaveEntrancePlanChecksum(CaveEntrancePlanV2 plan) throws IOException {
        String actual = caveEntrancePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("cave entrance plan canonical checksum mismatch");
        }
    }

    private void verifyFoundationCaveEntranceValidationArtifactChecksum(
            FoundationCaveEntranceValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationCaveEntranceValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("cave entrance validation artifact canonical checksum mismatch");
        }
    }

    private void verifyUndergroundRiverPlanChecksum(UndergroundRiverPlanV2 plan) throws IOException {
        String actual = undergroundRiverPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("underground river plan canonical checksum mismatch");
        }
    }

    private void verifyLavaTubePlanChecksum(LavaTubePlanV2 plan) throws IOException {
        String actual = lavaTubePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("lava tube plan canonical checksum mismatch");
        }
    }

    private void verifyFoundationLavaTubeValidationArtifactChecksum(
            FoundationLavaTubeValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationLavaTubeValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("lava tube validation artifact canonical checksum mismatch");
        }
    }

    private void verifySpringPlanChecksum(SpringPlanV2 plan) throws IOException {
        String actual = springPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("spring plan canonical checksum mismatch");
        }
    }

    private void verifyFoundationSpringValidationArtifactChecksum(
            FoundationSpringValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationSpringValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("spring validation artifact canonical checksum mismatch");
        }
    }

    private void verifyOxbowLakePlanChecksum(OxbowLakePlanV2 plan) throws IOException {
        String actual = oxbowLakePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("oxbow lake plan canonical checksum mismatch");
        }
    }

    private void verifyFoundationOxbowLakeValidationArtifactChecksum(
            FoundationOxbowLakeValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationOxbowLakeValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("oxbow lake validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationUndergroundRiverValidationArtifactChecksum(
            FoundationUndergroundRiverValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationUndergroundRiverValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("underground river validation artifact canonical checksum mismatch");
        }
    }

    private void verifyMacroLandWaterTopologyPlanChecksum(MacroLandWaterTopologyPlanV2 plan)
            throws IOException {
        String actual = macroLandWaterTopologyPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("macro land-water topology plan canonical checksum mismatch");
        }
    }

    private void verifyFoundationMacroLandWaterTopologyValidationArtifactChecksum(
            FoundationMacroLandWaterTopologyValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationMacroLandWaterTopologyValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("macro land-water topology validation artifact canonical checksum mismatch");
        }
    }

    private void verifyWaterfallChainPlanChecksum(WaterfallChainPlanV2 plan) throws IOException {
        String actual = waterfallChainPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("waterfall chain plan canonical checksum mismatch");
        }
    }

    private void verifyFoundationRiverGraphRolesValidationArtifactChecksum(
            FoundationRiverGraphRolesValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationRiverGraphRolesValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("river graph roles validation artifact canonical checksum mismatch");
        }
    }

    private void verifyGlacialIcePlanChecksum(GlacialIcePlanV2 plan) throws IOException {
        String actual = glacialIcePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("glacial ice plan canonical checksum mismatch");
        }
    }

    private void verifyFoundationGlacialIceValidationArtifactChecksum(
            FoundationGlacialIceValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationGlacialIceValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("glacial ice validation artifact canonical checksum mismatch");
        }
    }

    private void verifyIceFjordPlanChecksum(IceFjordPlanV2 plan) throws IOException {
        String actual = iceFjordPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("ice fjord plan canonical checksum mismatch");
        }
    }

    private void verifyBarrierIslandPlanChecksum(BarrierIslandPlanV2 plan) throws IOException {
        String actual = barrierIslandPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("barrier island plan canonical checksum mismatch");
        }
    }

    private void verifyAtollPlanChecksum(AtollPlanV2 plan) throws IOException {
        String actual = atollPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("atoll plan canonical checksum mismatch");
        }
    }

    private void verifyAdvancedIslandReefCatalogContractChecksum(AdvancedIslandReefCatalogContractV2 contract)
            throws IOException {
        String actual = advancedIslandReefCatalogContractChecksum(contract);
        if (!actual.equals(contract.canonicalChecksum())) {
            throw new IOException("advanced island/reef catalog contract canonical checksum mismatch");
        }
    }

    private void verifyFoundationAdvancedIslandReefValidationArtifactChecksum(
            FoundationAdvancedIslandReefValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationAdvancedIslandReefValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("advanced island/reef validation artifact canonical checksum mismatch");
        }
    }

    private void verifyAdvancedRiverLakeSplitContractChecksum(AdvancedRiverLakeSplitContractV2 contract)
            throws IOException {
        String actual = advancedRiverLakeSplitContractChecksum(contract);
        if (!actual.equals(contract.canonicalChecksum())) {
            throw new IOException("advanced river/lake split contract canonical checksum mismatch");
        }
    }

    private void verifyEscarpmentPlanChecksum(EscarpmentPlanV2 plan) throws IOException {
        String actual = escarpmentPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("escarpment plan canonical checksum mismatch");
        }
    }

    private void verifyPlateauPlanChecksum(PlateauPlanV2 plan) throws IOException {
        String actual = plateauPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("plateau plan canonical checksum mismatch");
        }
    }

    private void verifyDryLandModifierContractChecksum(DryLandModifierContractV2 contract) throws IOException {
        String actual = dryLandModifierContractChecksum(contract);
        if (!actual.equals(contract.canonicalChecksum())) {
            throw new IOException("dry land modifier contract canonical checksum mismatch");
        }
    }

    private void verifyFoundationEscarpmentPlateauValidationArtifactChecksum(
            FoundationEscarpmentPlateauValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationEscarpmentPlateauValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("escarpment/plateau validation artifact canonical checksum mismatch");
        }
    }

    private void verifyMoraineFieldPlanChecksum(MoraineFieldPlanV2 plan) throws IOException {
        String actual = moraineFieldPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("moraine field plan canonical checksum mismatch");
        }
    }

    private void verifyOutwashPlainPlanChecksum(OutwashPlainPlanV2 plan) throws IOException {
        String actual = outwashPlainPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("outwash plain plan canonical checksum mismatch");
        }
    }

    private void verifyPermafrostPlainProfileChecksum(PermafrostPlainProfileV2 profile) throws IOException {
        String actual = permafrostPlainProfileChecksum(profile);
        if (!actual.equals(profile.canonicalChecksum())) {
            throw new IOException("permafrost plain profile canonical checksum mismatch");
        }
    }

    private void verifyFoundationGlacialDepositionValidationArtifactChecksum(
            FoundationGlacialDepositionValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationGlacialDepositionValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("glacial deposition validation artifact canonical checksum mismatch");
        }
    }

    private void verifyAbyssalPlainPlanChecksum(AbyssalPlainPlanV2 plan) throws IOException {
        String actual = abyssalPlainPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("abyssal plain plan canonical checksum mismatch");
        }
    }

    private void verifySeamountPlanChecksum(SeamountPlanV2 plan) throws IOException {
        String actual = seamountPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("seamount plan canonical checksum mismatch");
        }
    }

    private void verifyFoundationAdditionalMarineValidationArtifactChecksum(
            FoundationAdditionalMarineValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationAdditionalMarineValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("additional marine validation artifact canonical checksum mismatch");
        }
    }

    private void verifySinkholePlanChecksum(SinkholePlanV2 plan) throws IOException {
        String actual = sinkholePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("sinkhole plan canonical checksum mismatch");
        }
    }

    private void verifyKarstSpringPlanChecksum(KarstSpringPlanV2 plan) throws IOException {
        String actual = karstSpringPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("karst spring plan canonical checksum mismatch");
        }
    }

    private void verifyKarstHydrologyGraphPlanChecksum(KarstHydrologyGraphPlanV2 plan) throws IOException {
        String actual = karstHydrologyGraphPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("karst hydrology graph plan canonical checksum mismatch");
        }
    }

    private void verifyCenotePlanChecksum(CenotePlanV2 plan) throws IOException {
        String actual = cenotePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("cenote plan canonical checksum mismatch");
        }
    }

    private void verifyFoundationKarstHydrologyValidationArtifactChecksum(
            FoundationKarstHydrologyValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationKarstHydrologyValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("karst hydrology validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationSingleIslandValidationArtifactChecksum(
            FoundationSingleIslandValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationSingleIslandValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("foundation single island validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationArchipelagoValidationArtifactChecksum(
            FoundationArchipelagoValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationArchipelagoValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("foundation archipelago validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationVolcanicConeValidationArtifactChecksum(
            FoundationVolcanicConeValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationVolcanicConeValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("foundation volcanic cone validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationValidationArtifactChecksum(FoundationValidationArtifactV2 artifact)
            throws IOException {
        String actual = foundationValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("foundation validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationRangeValleyValidationArtifactChecksum(
            FoundationRangeValleyValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationRangeValleyValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("foundation range/valley validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationRiverValidationArtifactChecksum(
            FoundationRiverValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationRiverValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("foundation river validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationFloodplainMarshValidationArtifactChecksum(
            FoundationFloodplainMarshValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationFloodplainMarshValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException(
                    "foundation floodplain/marsh validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationRockyCoastCliffValidationArtifactChecksum(
            FoundationRockyCoastCliffValidationArtifactV2 artifact
    ) throws IOException {
        String actual = foundationRockyCoastCliffValidationArtifactChecksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException(
                    "foundation rocky-coast/cliff validation artifact canonical checksum mismatch");
        }
    }

    private void verifyFoundationPreviewIndexChecksum(FoundationPreviewIndexV2 index) throws IOException {
        String actual = foundationPreviewIndexChecksum(index);
        if (!actual.equals(index.canonicalChecksum())) {
            throw new IOException("foundation preview index canonical checksum mismatch");
        }
    }

    private void verifyFeatureMaterialProfilePlanChecksum(FeatureMaterialProfilePlanV2 plan) throws IOException {
        if (canonicalFeatureMaterialProfilePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("feature-material plan exceeds declared canonical byte budget");
        }
        String actual = featureMaterialProfilePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("feature-material plan canonical checksum mismatch");
        }
    }

    private void verifyVolumeSdfPrimitivePlanChecksum(VolumeSdfPrimitivePlanV2 plan) throws IOException {
        if (canonicalVolumeSdfPrimitivePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("volume-sdf-primitive plan exceeds declared canonical byte budget");
        }
        String actual = volumeSdfPrimitivePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("volume-sdf-primitive plan canonical checksum mismatch");
        }
    }

    private void verifyVolumeCsgPlanChecksum(VolumeCsgPlanV2 plan) throws IOException {
        if (canonicalVolumeCsgPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("volume-csg plan exceeds declared canonical byte budget");
        }
        String actual = volumeCsgPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("volume-csg plan canonical checksum mismatch");
        }
    }

    private void verifyVolumeAabbIndexPlanChecksum(VolumeAabbIndexPlanV2 plan) throws IOException {
        if (canonicalVolumeAabbIndexPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("volume-aabb-index plan exceeds declared canonical byte budget");
        }
        String actual = volumeAabbIndexPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("volume-aabb-index plan canonical checksum mismatch");
        }
    }

    private void verifyVolumeTileCachePlanChecksum(VolumeTileCachePlanV2 plan) throws IOException {
        if (canonicalVolumeTileCachePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("volume-tile-cache plan exceeds declared canonical byte budget");
        }
        String actual = volumeTileCachePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("volume-tile-cache plan canonical checksum mismatch");
        }
    }

    private void verifyCaveNetworkPlanChecksum(CaveNetworkPlanV2 plan) throws IOException {
        if (canonicalCaveNetworkPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("cave-network plan exceeds declared canonical byte budget");
        }
        String actual = caveNetworkPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("cave-network plan canonical checksum mismatch");
        }
    }

    private void verifyLushCavePlanChecksum(LushCavePlanV2 plan) throws IOException {
        if (canonicalLushCavePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("lush-cave plan exceeds declared canonical byte budget");
        }
        String actual = lushCavePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("lush-cave plan canonical checksum mismatch");
        }
    }

    private void verifyUndergroundLakePlanChecksum(UndergroundLakePlanV2 plan) throws IOException {
        if (canonicalUndergroundLakePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("underground-lake plan exceeds declared canonical byte budget");
        }
        String actual = undergroundLakePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("underground-lake plan canonical checksum mismatch");
        }
    }

    private void verifySeaCavePlanChecksum(SeaCavePlanV2 plan) throws IOException {
        if (canonicalSeaCavePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("sea-cave plan exceeds declared canonical byte budget");
        }
        String actual = seaCavePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("sea-cave plan canonical checksum mismatch");
        }
    }

    private void verifyOverhangPlanChecksum(OverhangPlanV2 plan) throws IOException {
        if (canonicalOverhangPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("overhang plan exceeds declared canonical byte budget");
        }
        String actual = overhangPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("overhang plan canonical checksum mismatch");
        }
    }

    private void verifyNaturalArchPlanChecksum(NaturalArchPlanV2 plan) throws IOException {
        if (canonicalNaturalArchPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("natural-arch plan exceeds declared canonical byte budget");
        }
        String actual = naturalArchPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("natural-arch plan canonical checksum mismatch");
        }
    }

    private void verifySkyIslandGroupPlanChecksum(SkyIslandGroupPlanV2 plan) throws IOException {
        if (canonicalSkyIslandGroupPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("sky-island-group plan exceeds declared canonical byte budget");
        }
        String actual = skyIslandGroupPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("sky-island-group plan canonical checksum mismatch");
        }
    }

    private void verifyWaterfallVolumePlanChecksum(WaterfallVolumePlanV2 plan) throws IOException {
        if (canonicalWaterfallVolumePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("waterfall-volume plan exceeds declared canonical byte budget");
        }
        String actual = waterfallVolumePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("waterfall-volume plan canonical checksum mismatch");
        }
    }

    private void verifyVolumeLocalEnvironmentPlanChecksum(VolumeLocalEnvironmentPlanV2 plan)
            throws IOException {
        if (canonicalVolumeLocalEnvironmentPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("volume-local-environment plan exceeds declared canonical byte budget");
        }
        String actual = volumeLocalEnvironmentPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("volume-local-environment plan canonical checksum mismatch");
        }
    }

    private void verifyPlacementPlanChecksum(PlacementPlanV2 plan) throws IOException {
        if (canonicalPlacementPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("placement plan exceeds declared canonical byte budget");
        }
        String actual = placementPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("placement plan canonical checksum mismatch");
        }
    }

    private void verifyPlacementJournalChecksum(PlacementJournalV2 journal) throws IOException {
        if (canonicalPlacementJournal(journal).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > PlacementJournalV2.MAX_CANONICAL_BYTES) {
            throw new IOException("placement journal exceeds declared canonical byte budget");
        }
        verifyPlacementPlanChecksum(journal.plan());
        if (!journal.planChecksum().equals(journal.plan().canonicalChecksum())) {
            throw new IOException("placement journal planChecksum mismatch");
        }
        String actual = placementJournalChecksum(journal);
        if (!actual.equals(journal.journalChecksum())) {
            throw new IOException("placement journal checksum mismatch");
        }
    }

    private void verifyPlacementEnvelopePlanChecksum(PlacementEnvelopePlanV2 plan) throws IOException {
        if (canonicalPlacementEnvelopePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("placement envelope plan exceeds declared canonical byte budget");
        }
        String mutation = placementEnvelopeMutationChecksum(plan);
        if (!mutation.equals(plan.mutationEnvelopeChecksum())) {
            throw new IOException("placement envelope mutation checksum mismatch");
        }
        String actual = placementEnvelopePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("placement envelope plan canonical checksum mismatch");
        }
    }

    private void verifyPlacementReservationPlanChecksum(PlacementReservationPlanV2 plan) throws IOException {
        if (canonicalPlacementReservationPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("placement reservation plan exceeds declared canonical byte budget");
        }
        String actual = placementReservationPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("placement reservation plan canonical checksum mismatch");
        }
    }

    private void verifyPlacementSafetyStateChecksum(PlacementSafetyStateV2 state) throws IOException {
        if (canonicalPlacementSafetyState(state).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > PlacementSafetyStateV2.MAX_CANONICAL_BYTES) {
            throw new IOException("placement safety state exceeds declared canonical byte budget");
        }
        String actual = placementSafetyStateChecksum(state);
        if (!actual.equals(state.canonicalChecksum())) {
            throw new IOException("placement safety state canonical checksum mismatch");
        }
    }

    private void verifyPlacementSnapshotPlanChecksum(PlacementSnapshotPlanV2 plan) throws IOException {
        if (canonicalPlacementSnapshotPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("placement snapshot plan exceeds declared canonical byte budget");
        }
        String actual = placementSnapshotPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("placement snapshot plan canonical checksum mismatch");
        }
    }

    private void verifyPlacementContainmentEvidenceChecksum(PlacementContainmentEvidenceV2 evidence)
            throws IOException {
        if (canonicalPlacementContainmentEvidence(evidence)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > evidence.policy().budget().maximumCanonicalBytes()) {
            throw new IOException("placement containment evidence exceeds declared canonical byte budget");
        }
        String actual = placementContainmentEvidenceChecksum(evidence);
        if (!actual.equals(evidence.canonicalChecksum())) {
            throw new IOException("placement containment evidence canonical checksum mismatch");
        }
    }

    private void verifyPlacementVerifyEvidenceChecksum(PlacementVerifyEvidenceV2 evidence)
            throws IOException {
        if (canonicalPlacementVerifyEvidence(evidence)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > evidence.policy().budget().maximumCanonicalBytes()) {
            throw new IOException("placement verify evidence exceeds declared canonical byte budget");
        }
        String actual = placementVerifyEvidenceChecksum(evidence);
        if (!actual.equals(evidence.canonicalChecksum())) {
            throw new IOException("placement verify evidence canonical checksum mismatch");
        }
    }

    private void verifyPlacementUndoPlanChecksum(PlacementUndoPlanV2 plan) throws IOException {
        if (canonicalPlacementUndoPlan(plan)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > PlacementUndoPlanV2.MAX_CANONICAL_BYTES) {
            throw new IOException("placement undo plan exceeds declared canonical byte budget");
        }
        String actual = placementUndoPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("placement undo plan canonical checksum mismatch");
        }
    }

    private void verifyPlacementRecoveryPlanChecksum(PlacementRecoveryPlanV2 plan) throws IOException {
        if (canonicalPlacementRecoveryPlan(plan)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > PlacementRecoveryPlanV2.MAX_CANONICAL_BYTES) {
            throw new IOException("placement recovery plan exceeds declared canonical byte budget");
        }
        String actual = placementRecoveryPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("placement recovery plan canonical checksum mismatch");
        }
    }

    private void verifyHydrologyPlanChecksum(HydrologyPlanV2 plan) throws IOException {
        String actual = hydrologyPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("hydrology plan canonical checksum mismatch");
        }
    }

    private void verifyHydrologyReconciliationPlanChecksum(
            HydrologyReconciliationPlanV2 plan
    ) throws IOException {
        String actual = hydrologyReconciliationPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("hydrology reconciliation plan canonical checksum mismatch");
        }
    }

    private GenerationRequestV2 parseGenerationRequest(JsonNode root) {
        JsonNode bounds = root.path("bounds");
        List<GenerationRequestV2.ReferenceImageSource> references = new ArrayList<>();
        for (JsonNode reference : root.path("referenceImages")) {
            references.add(new GenerationRequestV2.ReferenceImageSource(
                    reference.path("id").textValue(), reference.path("file").textValue(),
                    enumValue(reference, "role", GenerationRequestV2.ReferenceImageRole.class)));
        }
        List<GenerationRequestV2.ConstraintMapSource> maps = new ArrayList<>();
        for (JsonNode map : root.path("constraintMaps")) {
            maps.add(parseConstraintMapSource(map));
        }
        JsonNode generation = root.path("generation");
        JsonNode budget = root.path("constraintMapBudget");
        return new GenerationRequestV2(
                root.path("requestVersion").intValue(), root.path("requestId").textValue(),
                new GenerationRequestV2.Bounds(
                        bounds.path("width").intValue(), bounds.path("length").intValue(),
                        bounds.path("minY").intValue(), bounds.path("maxY").intValue(),
                        bounds.path("waterLevel").intValue()),
                root.path("prompt").textValue(), references, maps,
                new GenerationRequestV2.GenerationSettings(
                        generation.path("globalSeed").longValue(), generation.path("tileSize").intValue()),
                new GenerationRequestV2.ConstraintMapBudget(
                        budget.path("maximumMapCount").intValue(),
                        budget.path("maximumTotalSourceBytes").longValue(),
                        budget.path("maximumDecodedBytes").longValue(),
                        budget.path("maximumPixels").longValue(),
                        budget.path("maximumArtifactBytes").longValue(),
                        budget.path("maximumResidentBytes").longValue()),
                root.has("foundationBaseLevels")
                        ? java.util.Optional.of(new GenerationRequestV2.FoundationBaseLevels(
                                root.path("foundationBaseLevels").path("landSurfaceY").intValue(),
                                root.path("foundationBaseLevels").path("waterBedY").intValue()))
                        : java.util.Optional.empty());
    }

    private GenerationRequestV2.ConstraintMapSource parseConstraintMapSource(JsonNode node) {
        JsonNode mapping = node.path("coordinateMapping");
        JsonNode crop = mapping.path("crop");
        return new GenerationRequestV2.ConstraintMapSource(
                node.path("sourceId").textValue(), node.path("file").textValue(),
                node.path("expectedSha256").textValue(), node.path("expectedWidth").intValue(),
                node.path("expectedLength").intValue(),
                enumValue(node, "decoderKind", GenerationRequestV2.DecoderKind.class),
                new GenerationRequestV2.CoordinateMapping(
                        enumValue(mapping, "origin", GenerationRequestV2.CoordinateOrigin.class),
                        enumValue(mapping, "xAxis", GenerationRequestV2.XAxis.class),
                        enumValue(mapping, "zAxis", GenerationRequestV2.ZAxis.class),
                        enumValue(mapping, "pixelReference", GenerationRequestV2.PixelReference.class),
                        enumValue(mapping, "aspectMismatchPolicy", GenerationRequestV2.AspectMismatchPolicy.class),
                        enumValue(mapping, "rotation", GenerationRequestV2.QuarterTurn.class),
                        mapping.path("flipX").booleanValue(), mapping.path("flipZ").booleanValue(),
                        new GenerationRequestV2.PixelCrop(
                                crop.path("x").intValue(), crop.path("z").intValue(),
                                crop.path("width").intValue(), crop.path("length").intValue())),
                parseConstraintMapEncoding(node.path("encoding")));
    }

    private GenerationRequestV2.ConstraintMapEncoding parseConstraintMapEncoding(JsonNode node) {
        GenerationRequestV2.SampleType sampleType =
                enumValue(node, "sampleType", GenerationRequestV2.SampleType.class);
        GenerationRequestV2.RasterChannel channel =
                enumValue(node, "channel", GenerationRequestV2.RasterChannel.class);
        GenerationRequestV2.NoData noData = parseNoData(node.path("noData"));
        return switch (node.path("kind").textValue()) {
            case "CATEGORICAL" -> {
                List<GenerationRequestV2.LabelMapping> labels = new ArrayList<>();
                for (JsonNode label : node.path("labels")) {
                    labels.add(new GenerationRequestV2.LabelMapping(
                            label.path("sample").intValue(), label.path("label").textValue()));
                }
                yield new GenerationRequestV2.CategoricalEncoding(
                        node.path("encodingVersion").intValue(), sampleType, channel, labels, noData);
            }
            case "HEIGHT" -> {
                JsonNode range = node.path("validSampleRange");
                yield new GenerationRequestV2.HeightEncoding(
                        node.path("encodingVersion").intValue(), sampleType, channel,
                        enumValue(node, "valueMeaning", GenerationRequestV2.HeightValueMeaning.class),
                        node.path("valueScaleMillionths").longValue(),
                        node.path("valueOffsetMillionths").longValue(),
                        new GenerationRequestV2.IntRange(
                                range.path("minimum").intValue(), range.path("maximum").intValue()),
                        noData);
            }
            default -> throw new IllegalArgumentException("unknown constraint map encoding kind");
        };
    }

    private static GenerationRequestV2.NoData parseNoData(JsonNode node) {
        return switch (node.path("mode").textValue()) {
            case "FORBIDDEN" -> new GenerationRequestV2.NoDataForbidden();
            case "SENTINEL" -> new GenerationRequestV2.NoDataSentinel(node.path("sample").intValue());
            default -> throw new IllegalArgumentException("unknown no-data mode");
        };
    }

    private TerrainIntentV2 parseIntent(JsonNode root) {
        TerrainIntentV2.CoordinateSystem coordinateSystem = new TerrainIntentV2.CoordinateSystem(
                enumValue(root.path("coordinateSystem"), "horizontal", TerrainIntentV2.HorizontalCoordinates.class),
                enumValue(root.path("coordinateSystem"), "origin", TerrainIntentV2.CoordinateOrigin.class),
                enumValue(root.path("coordinateSystem"), "xAxis", TerrainIntentV2.XAxis.class),
                enumValue(root.path("coordinateSystem"), "zAxis", TerrainIntentV2.ZAxis.class),
                enumValue(root.path("coordinateSystem"), "vertical", TerrainIntentV2.VerticalCoordinates.class)
        );
        List<TerrainIntentV2.Feature> features = new ArrayList<>();
        for (JsonNode feature : root.path("features")) features.add(parseFeature(feature));
        List<TerrainIntentV2.Relation> relations = new ArrayList<>();
        for (JsonNode relation : root.path("relations")) {
            JsonNode transition = relation.path("transition");
            relations.add(new TerrainIntentV2.Relation(
                    relation.path("id").textValue(),
                    enumValue(relation, "kind", TerrainIntentV2.RelationKind.class),
                    relation.path("from").textValue(),
                    relation.path("to").textValue(),
                    enumValue(relation, "strength", TerrainIntentV2.Strength.class),
                    transition.isMissingNode()
                            ? TerrainIntentV2.TransitionPolicy.NONE
                            : new TerrainIntentV2.TransitionPolicy(
                                    transition.path("transitionVersion").intValue(),
                                    enumValue(transition, "profile", TerrainIntentV2.TransitionProfile.class),
                                    transition.path("bandBlocks").intValue())
            ));
        }
        List<TerrainIntentV2.Constraint> constraints = new ArrayList<>();
        for (JsonNode constraint : root.path("constraints")) constraints.add(parseConstraint(constraint));
        JsonNode environment = root.path("environment");
        TerrainIntentV2.EnvironmentDescriptor environmentDescriptor = new TerrainIntentV2.EnvironmentDescriptor(
                optionalText(environment, "geologyPreset"),
                optionalText(environment, "climatePreset"),
                optionalText(environment, "ecologyPreset")
        );
        List<TerrainIntentV2.ConstraintMapBinding> maps = new ArrayList<>();
        for (JsonNode map : root.path("mapReferences")) {
            TerrainIntentV2.Strength strength = enumValue(map, "strength", TerrainIntentV2.Strength.class);
            maps.add(new TerrainIntentV2.ConstraintMapBinding(
                    map.path("id").textValue(), map.path("sourceId").textValue(),
                    enumValue(map, "role", TerrainIntentV2.ConstraintMapRole.class),
                    map.path("artifactId").textValue(), strength,
                    enumValue(map, "sampling", TerrainIntentV2.Sampling.class), map.path("toleranceBlocks").intValue(),
                    strength == TerrainIntentV2.Strength.SOFT ? fixedInt(map.path("weight")) : 0
            ));
        }
        List<TerrainIntentV2.StructureRequest> structures = new ArrayList<>();
        for (JsonNode structure : root.path("structures")) {
            structures.add(new TerrainIntentV2.StructureRequest(
                    structure.path("id").textValue(),
                    enumValue(structure, "kind", TerrainIntentV2.StructureKind.class),
                    structure.path("count").intValue(), structure.path("preferredFeatureId").textValue()
            ));
        }
        return new TerrainIntentV2(
                root.path("intentVersion").intValue(), root.path("intentId").textValue(), root.path("theme").textValue(),
                coordinateSystem, features, relations, constraints, environmentDescriptor, maps, structures,
                parseProvenance(root.path("provenance"))
        );
    }

    private TerrainIntentV2.Feature parseFeature(JsonNode node) {
        TerrainIntentV2.FeatureKind kind = enumValue(node, "kind", TerrainIntentV2.FeatureKind.class);
        return new TerrainIntentV2.Feature(
                node.path("id").textValue(), kind, parseGeometry(node.path("geometry")),
                parseParameters(kind, node.path("parameters")), node.path("priority").intValue(),
                parseProvenance(node.path("provenance"))
        );
    }

    private TerrainIntentV2.Geometry parseGeometry(JsonNode node) {
        TerrainIntentV2.GeometryType type = enumValue(node, "type", TerrainIntentV2.GeometryType.class);
        return switch (type) {
            case POINT -> new TerrainIntentV2.PointGeometry(parsePoint(node.path("point")));
            case MULTI_POINT -> {
                List<TerrainIntentV2.NamedPoint> points = new ArrayList<>();
                for (JsonNode point : node.path("points")) points.add(new TerrainIntentV2.NamedPoint(point.path("id").textValue(), parsePoint(point.path("point"))));
                yield new TerrainIntentV2.MultiPointGeometry(points);
            }
            case SPLINE -> new TerrainIntentV2.SplineGeometry(
                    parsePoints(node.path("points")), enumValue(node, "interpolation", TerrainIntentV2.Interpolation.class));
            case MULTI_SPLINE -> {
                List<TerrainIntentV2.NamedPath> paths = new ArrayList<>();
                for (JsonNode path : node.path("paths")) {
                    paths.add(new TerrainIntentV2.NamedPath(
                            path.path("id").textValue(), optionalText(path, "startEndpointId"),
                            optionalText(path, "endEndpointId"), parsePoints(path.path("points"))));
                }
                yield new TerrainIntentV2.MultiSplineGeometry(
                        paths, enumValue(node, "interpolation", TerrainIntentV2.Interpolation.class));
            }
            case POLYGON -> parsePolygon(node);
            case VOLUME_GUIDE -> new TerrainIntentV2.VolumeGuideGeometry(
                    parsePolygon(node.path("footprint")),
                    new TerrainIntentV2.VerticalGuide(
                            enumValue(node.path("vertical"), "mode", TerrainIntentV2.VerticalMode.class),
                            node.path("vertical").path("min").intValue(), node.path("vertical").path("max").intValue()));
        };
    }

    private TerrainIntentV2.PolygonGeometry parsePolygon(JsonNode node) {
        List<List<TerrainIntentV2.Point2>> rings = new ArrayList<>();
        for (JsonNode ring : node.path("rings")) rings.add(parsePoints(ring));
        return new TerrainIntentV2.PolygonGeometry(rings);
    }

    private TerrainIntentV2.FeatureParameters parseParameters(TerrainIntentV2.FeatureKind kind, JsonNode node) {
        return switch (kind) {
            case SANDY_BEACH -> new TerrainIntentV2.SandyBeachParameters(
                    intRange(node.path("widthBlocks")), fixedRange(node.path("shoreSlopeDegrees")),
                    new TerrainIntentV2.NearshoreDepth(node.path("nearshoreDepthBlocks").path("atDistance").intValue(),
                            node.path("nearshoreDepthBlocks").path("target").intValue()),
                    Math.toIntExact(fixed(node.path("foreshoreShare01"))),
                    node.path("endpointTaperBlocks").intValue(),
                    enumValue(node, "landSide", TerrainIntentV2.LandSide.class));
            case BREAKWATER_HARBOR -> new TerrainIntentV2.BreakwaterHarborParameters(
                    node.path("crestWidthBlocks").intValue(), node.path("crestAboveWaterBlocks").intValue(),
                    node.path("outerDepthBlocks").intValue(),
                    enumValue(node, "crestProfile", TerrainIntentV2.BreakwaterCrestProfile.class),
                    enumValue(node, "foundationProfile", TerrainIntentV2.BreakwaterFoundationProfile.class),
                    Math.toIntExact(fixed(node.path("foundationSideSlopeRunPerRise"))),
                    new TerrainIntentV2.HarborOpening(
                            strings(node.path("opening").path("betweenEndpointIds")),
                            node.path("opening").path("widthBlocks").intValue(),
                            enumValue(node.path("opening"), "measurement", TerrainIntentV2.Measurement.class)),
                    enumValue(node, "innerSide", TerrainIntentV2.InnerSide.class));
            case HARBOR_BASIN -> new TerrainIntentV2.HarborBasinParameters(
                    intRange(node.path("waterDepthBlocks")), strings(node.path("entranceEndpointIds")),
                    node.path("entranceCorridorLengthBlocks").intValue(),
                    enumValue(node, "bottomProfile", TerrainIntentV2.HarborBottomProfile.class),
                    node.path("profileTransitionBlocks").intValue());
            case ROCKY_CAPE -> new TerrainIntentV2.RockyCapeParameters(
                    intRange(node.path("cliffHeightBlocks")), intRange(node.path("localReliefAboveSeaBlocks")),
                    intRange(node.path("cliffBandWidthBlocks")), intRange(node.path("seaStackCount")),
                    intRange(node.path("seaStackRadiusBlocks")),
                    intRange(node.path("seaStackOffshoreDistanceBlocks")),
                    intRange(node.path("channelCount")), intRange(node.path("channelWidthBlocks")),
                    intRange(node.path("channelLengthBlocks")), intRange(node.path("channelDepthBlocks")),
                    fixedRange(node.path("rockExposure01")),
                    enumValue(node, "seawardSide", TerrainIntentV2.Edge.class),
                    enumValue(node, "capeMode", TerrainIntentV2.CapeMode.class));
            case ROCKY_COAST -> new TerrainIntentV2.RockyCoastParameters(
                    intRange(node.path("rockShelfWidthBlocks")),
                    fixedRange(node.path("rockExposure01")),
                    enumValue(node, "shoreSide", TerrainIntentV2.Edge.class),
                    intRange(node.path("channelCount")),
                    node.path("capeOrBeachTransitionBandBlocks").intValue(),
                    intRange(node.path("talusHandoffDepthBlocks")));
            case SEA_CLIFF -> new TerrainIntentV2.SeaCliffParameters(
                    intRange(node.path("cliffHeightBlocks")),
                    intRange(node.path("talusWidthBlocks")),
                    intRange(node.path("notchDepthBlocks")),
                    enumValue(node, "seawardSide", TerrainIntentV2.Edge.class),
                    intRange(node.path("supportHalfExtentXZBlocks")),
                    node.path("coastTransitionBandBlocks").intValue());
            case BACKSHORE_PLAINS -> new TerrainIntentV2.BackshorePlainsParameters(
                    intRange(node.path("elevationAboveWaterBlocks")), fixedRange(node.path("grassCover01")));
            case PLAIN -> new TerrainIntentV2.PlainParameters(
                    intRange(node.path("baseElevationAboveDatumBlocks")),
                    intRange(node.path("microReliefBlocks")),
                    intRange(node.path("groundwaterHandoffDepthBlocks")));
            case HILL_RANGE -> new TerrainIntentV2.HillRangeParameters(
                    intRange(node.path("ridgeHalfWidthBlocks")),
                    intRange(node.path("maxReliefBlocks")),
                    intRange(node.path("ridgeStationCount")),
                    intRange(node.path("saddleCount")),
                    node.path("plainTransitionBandBlocks").intValue());
            case MOUNTAIN_RANGE -> new TerrainIntentV2.MountainRangeParameters(
                    intRange(node.path("peakCount")),
                    intRange(node.path("ridgeHalfWidthBlocks")),
                    intRange(node.path("maxReliefBlocks")),
                    intRange(node.path("saddleCount")),
                    node.path("spurCount").intValue(),
                    node.path("passCount").intValue(),
                    node.path("foothillBandBlocks").intValue(),
                    node.path("valleyTransitionBandBlocks").intValue());
            case VALLEY -> new TerrainIntentV2.ValleyParameters(
                    enumValue(node, "crossSection", TerrainIntentV2.ValleyCrossSection.class),
                    intRange(node.path("floorHalfWidthBlocks")),
                    intRange(node.path("shoulderWidthBlocks")),
                    intRange(node.path("maxDepthBlocks")),
                    node.path("mountainTransitionBandBlocks").intValue(),
                    enumValue(node, "connectionRole", TerrainIntentV2.ValleyConnectionRole.class));
            case RIVER -> new TerrainIntentV2.RiverParameters(
                    intRange(node.path("bankfullWidthBlocks")),
                    enumValue(node, "dischargeClass", TerrainIntentV2.DischargeClass.class),
                    fixed(node.path("minimumBedSlope01")),
                    intRange(node.path("floodplainHandoffWidthBlocks")),
                    node.path("maxReachCount").intValue(),
                    node.path("maxNodeDegree").intValue());
            case FLOODPLAIN -> new TerrainIntentV2.FloodplainParameters(
                    intRange(node.path("riverAdjacencyBandBlocks")),
                    intRange(node.path("groundwaterHandoffDepthBlocks")),
                    intRange(node.path("microReliefBlocks")));
            case MARSH -> new TerrainIntentV2.MarshParameters(
                    intRange(node.path("hydroperiodBlocks")),
                    fixedRange(node.path("wetness01")),
                    fixedRange(node.path("openWaterShare01")),
                    intRange(node.path("microReliefBlocks")),
                    intRange(node.path("groundwaterMinDepthBlocks")));
            case MEANDERING_RIVER -> new TerrainIntentV2.MeanderingRiverParameters(
                    intRange(node.path("bankfullWidthBlocks")),
                    enumValue(node, "dischargeClass", TerrainIntentV2.DischargeClass.class),
                    fixed(node.path("minimumBedSlope01")),
                    enumValue(node, "variant", TerrainIntentV2.RiverVariant.class));
            case LAKE -> new TerrainIntentV2.LakeParameters(
                    intRange(node.path("targetDepthBlocks")),
                    node.path("shoreWidthBlocks").intValue(),
                    enumValue(node, "terminalPolicy", TerrainIntentV2.LakeTerminalPolicy.class),
                    enumValue(node, "spillSelection", TerrainIntentV2.LakeSpillSelection.class),
                    node.path("spillEdgeStartIndex").intValue(),
                    node.path("spillwayWidthBlocks").intValue(),
                    node.path("spillwayCorridorLengthBlocks").intValue(),
                    enumValue(node, "floorProfile", TerrainIntentV2.LakeFloorProfile.class));
            case CANYON -> new TerrainIntentV2.CanyonParameters(
                    intRange(node.path("floorWidthBlocks")),
                    intRange(node.path("rimWidthBlocks")),
                    intRange(node.path("depthBlocks")),
                    enumValue(node, "crossSection", TerrainIntentV2.CanyonCrossSection.class),
                    node.path("terraceCount").intValue(),
                    node.path("terraceWidthBlocks").intValue());
            case WATERFALL -> new TerrainIntentV2.WaterfallParameters(
                    intRange(node.path("dropBlocks")),
                    node.path("lipWidthBlocks").intValue(),
                    node.path("plungePoolRadiusBlocks").intValue(),
                    node.path("behindFallClearanceBlocks").intValue());
            case DELTA -> new TerrainIntentV2.DeltaParameters(
                    intRange(node.path("distributaryCount")),
                    fixedRange(node.path("fanOpeningDegrees")),
                    intRange(node.path("fanReliefBlocks")),
                    intRange(node.path("sandbarCount")),
                    intRange(node.path("shallowSeaDepthBlocks")),
                    enumValue(node, "fanProfile", TerrainIntentV2.DeltaFanProfile.class));
            case TIDAL_CHANNEL_NETWORK -> new TerrainIntentV2.TidalChannelParameters(
                    intRange(node.path("widthBlocks")),
                    node.path("tidalRangeBlocks").intValue(),
                    enumValue(node, "edgeKind", TerrainIntentV2.TidalEdgeKind.class));
            case FJORD -> new TerrainIntentV2.FjordParameters(
                    intRange(node.path("surfaceWidthBlocks")),
                    intRange(node.path("channelDepthBlocks")),
                    enumValue(node, "crossSection", TerrainIntentV2.FjordCrossSection.class),
                    node.path("headBasinRadiusBlocks").intValue());
            case MANGROVE_WETLAND -> new TerrainIntentV2.MangroveWetlandParameters(
                    intRange(node.path("microReliefBlocks")),
                    fixedRange(node.path("waterloggedShare01")));
            case CORAL_REEF -> new TerrainIntentV2.CoralReefParameters(
                    intRange(node.path("reefCrestDepthBlocks")),
                    intRange(node.path("reefWidthBlocks")),
                    intRange(node.path("outerSlopeDegrees")));
            case LAGOON -> new TerrainIntentV2.LagoonParameters(intRange(node.path("depthBlocks")));
            case REEF_PASS -> new TerrainIntentV2.ReefPassParameters(
                    intRange(node.path("widthBlocks")),
                    intRange(node.path("waterDepthBlocks")));
            case ALPINE_MOUNTAIN_RANGE, GLACIAL_MOUNTAIN_RANGE -> new TerrainIntentV2.MountainParameters(
                    intRange(node.path("peakCount")),
                    intRange(node.path("ridgeHalfWidthBlocks")),
                    intRange(node.path("maxReliefBlocks")),
                    node.path("spurCount").intValue(),
                    fixed(node.path("ridgeSharpness01")));
            case VOLCANIC_ARCHIPELAGO -> {
                List<TerrainIntentV2.IslandSpec> islands = new ArrayList<>();
                for (JsonNode island : node.path("islands")) {
                    islands.add(new TerrainIntentV2.IslandSpec(
                            island.path("pointId").textValue(),
                            island.path("radiusBlocks").intValue(),
                            island.path("summitHeightBlocksAboveSea").intValue()));
                }
                yield new TerrainIntentV2.VolcanicArchipelagoParameters(
                        islands, intRange(node.path("submarineSaddleDepthBlocks")));
            }
            case VOLCANIC_CALDERA -> new TerrainIntentV2.VolcanicCalderaParameters(
                    node.path("rimRadiusBlocks").intValue(),
                    node.path("rimReliefBlocks").intValue(),
                    node.path("craterFloorDepthBlocks").intValue(),
                    enumValue(node, "breachDirection", TerrainIntentV2.CalderaBreachDirection.class));
            case LAVA_FLOW_FIELD -> new TerrainIntentV2.LavaFlowParameters(
                    intRange(node.path("widthBlocks")),
                    fixed(node.path("surfaceRoughness01")));
            case SINGLE_ISLAND -> new TerrainIntentV2.SingleIslandParameters(
                    intRange(node.path("radiusBlocks")),
                    intRange(node.path("summitHeightBlocksAboveSea")),
                    intRange(node.path("shoreBandWidthBlocks")),
                    fixedRange(node.path("radialDrainage01")),
                    intRange(node.path("submarineApronDepthBlocks")));
            case ARCHIPELAGO -> {
                List<TerrainIntentV2.IslandSpec> islands = new ArrayList<>();
                for (JsonNode island : node.path("islands")) {
                    islands.add(new TerrainIntentV2.IslandSpec(
                            island.path("pointId").textValue(),
                            island.path("radiusBlocks").intValue(),
                            island.path("summitHeightBlocksAboveSea").intValue()));
                }
                yield new TerrainIntentV2.ArchipelagoParameters(
                        islands,
                        intRange(node.path("submarineSaddleDepthBlocks")),
                        node.path("minDryLandGapBlocks").intValue());
            }
            case VOLCANIC_CONE -> new TerrainIntentV2.VolcanicConeParameters(
                    intRange(node.path("baseRadiusBlocks")),
                    intRange(node.path("summitHeightBlocksAboveSea")),
                    intRange(node.path("craterRadiusBlocks")),
                    intRange(node.path("craterFloorDepthBlocks")),
                    fixedRange(node.path("radialDrainage01")));
            case OCEAN_BASIN -> new TerrainIntentV2.OceanBasinParameters(
                    intRange(node.path("maxDepthBlocksBelowSea")),
                    intRange(node.path("floorReliefBlocks")));
            case CONTINENTAL_SHELF -> new TerrainIntentV2.ContinentalShelfParameters(
                    intRange(node.path("shelfWidthBlocks")),
                    intRange(node.path("shelfDepthBlocksBelowSea")),
                    intRange(node.path("coastDistanceBandBlocks")),
                    enumValue(node, "seawardSide", TerrainIntentV2.Edge.class));
            case CONTINENTAL_SLOPE -> new TerrainIntentV2.ContinentalSlopeParameters(
                    intRange(node.path("slopeWidthBlocks")),
                    intRange(node.path("upperDepthBlocksBelowSea")),
                    intRange(node.path("lowerDepthBlocksBelowSea")),
                    fixedRange(node.path("maxGradient01")));
            case SUBMARINE_CANYON -> new TerrainIntentV2.SubmarineCanyonParameters(
                    intRange(node.path("floorWidthBlocks")),
                    intRange(node.path("rimWidthBlocks")),
                    intRange(node.path("additionalCarveDepthBlocks")),
                    enumValue(node, "crossSection", TerrainIntentV2.CanyonCrossSection.class),
                    node.path("terraceCount").intValue(),
                    node.path("terraceWidthBlocks").intValue());
            case CAVE_ENTRANCE -> new TerrainIntentV2.CaveEntranceParameters(
                    node.path("surfaceOffsetBlocks").intValue(),
                    node.path("minimumOpeningBlocks").intValue(),
                    node.path("approachLengthBlocks").intValue(),
                    node.path("roofClearanceBlocks").intValue(),
                    node.path("targetEntranceNodeId").textValue());
            case UNDERGROUND_RIVER -> new TerrainIntentV2.UndergroundRiverParameters(
                    intRange(node.path("channelRadiusBlocks")),
                    intRange(node.path("fluidDepthBlocks")),
                    node.path("minimumAirPocketBlocks").intValue(),
                    node.path("sourceCaveNodeId").textValue(),
                    node.path("outletCaveNodeId").textValue(),
                    node.path("fluidBodyId").textValue());
            case LAVA_TUBE -> new TerrainIntentV2.LavaTubeParameters(
                    intRange(node.path("tubeRadiusBlocks")),
                    intRange(node.path("roofClearanceBlocks")),
                    intRange(node.path("supportRadiusBlocks")),
                    node.path("entranceOffsetBlocks").asInt(0));
            case SPRING -> new TerrainIntentV2.SpringParameters(
                    intRange(node.path("outflowRadiusBlocks")),
                    intRange(node.path("dischargeBlocks")),
                    intRange(node.path("supportRadiusBlocks")));
            case OXBOW_LAKE -> new TerrainIntentV2.OxbowLakeParameters(
                    intRange(node.path("targetDepthBlocks")),
                    node.path("shoreWidthBlocks").intValue(),
                    intRange(node.path("wetlandHandoffWidthBlocks")),
                    intRange(node.path("supportRadiusBlocks")),
                    node.path("cutoffReachIdHint").asText(""));
            case SINKHOLE -> new TerrainIntentV2.SinkholeParameters(
                    intRange(node.path("collapseRadiusBlocks")),
                    intRange(node.path("roofClearanceBlocks")),
                    intRange(node.path("lossVolumeBlocks")),
                    node.path("targetEntranceNodeId").textValue());
            case KARST_SPRING -> new TerrainIntentV2.KarstSpringParameters(
                    intRange(node.path("springDischargeBlocks")),
                    node.path("outletHint").asText(""));
            case FLOODED_CAVE -> new TerrainIntentV2.FloodedCaveParameters(
                    node.path("fluidBodyId").textValue(),
                    node.path("waterSurfaceYBlocks").intValue(),
                    node.path("hostCaveFeatureIdHint").asText(""));
            case VALLEY_GLACIER, ICE_CAP, ICE_SHEET -> new TerrainIntentV2.GlacialIceParameters(
                    intRange(node.path("thicknessBlocks")),
                    intRange(node.path("halfWidthBlocks")),
                    node.path("flowAzimuthDegrees").intValue(),
                    node.path("climatePreset").textValue(),
                    node.path("meltwaterHandoffFeatureIdHint").asText(""));
            case MORAINE_FIELD -> new TerrainIntentV2.MoraineFieldParameters(
                    intRange(node.path("ridgeCount")),
                    intRange(node.path("ridgeHalfWidthBlocks")),
                    intRange(node.path("sedimentThicknessBlocks")),
                    node.path("flowAzimuthDegrees").intValue());
            case OUTWASH_PLAIN -> new TerrainIntentV2.OutwashPlainParameters(
                    intRange(node.path("sedimentThicknessBlocks")),
                    intRange(node.path("channelSpacingBlocks")),
                    node.path("flowAzimuthDegrees").intValue(),
                    node.path("meltwaterHandoffFeatureIdHint").asText(""));
            case ESCARPMENT -> new TerrainIntentV2.EscarpmentParameters(
                    intRange(node.path("scarpHeightBlocks")),
                    intRange(node.path("talusWidthBlocks")),
                    intRange(node.path("floorDropBlocks")),
                    enumValue(node, "dropSide", TerrainIntentV2.Edge.class),
                    node.path("plateauTransitionBandBlocks").intValue());
            case PLATEAU -> new TerrainIntentV2.PlateauParameters(
                    intRange(node.path("capElevationBlocks")),
                    intRange(node.path("capReliefBlocks")),
                    enumValue(node, "profile", TerrainIntentV2.PlateauProfile.class),
                    node.path("escarpmentTransitionBandBlocks").intValue());
            case ABYSSAL_PLAIN -> new TerrainIntentV2.AbyssalPlainParameters(
                    intRange(node.path("floorDepthBlocksBelowSea")),
                    intRange(node.path("floorReliefBlocks")));
            case SEAMOUNT -> new TerrainIntentV2.SeamountParameters(
                    intRange(node.path("baseRadiusBlocks")),
                    intRange(node.path("reliefBlocks")),
                    intRange(node.path("summitDepthBlocksBelowSea")));
            default -> new TerrainIntentV2.NoParameters();
        };
    }

    private TerrainIntentV2.Constraint parseConstraint(JsonNode node) {
        TerrainIntentV2.Strength strength = enumValue(node, "strength", TerrainIntentV2.Strength.class);
        int weight = strength == TerrainIntentV2.Strength.SOFT ? fixedInt(node.path("weight")) : 0;
        return switch (node.path("kind").textValue()) {
            case "METRIC_RANGE" -> new TerrainIntentV2.MetricRangeConstraint(
                    node.path("id").textValue(), strength, node.path("subject").textValue(),
                    node.path("metric").textValue(), fixedRange(node.path("range")), fixed(node.path("tolerance")), weight);
            case "EDGE_CLASSIFICATION" -> new TerrainIntentV2.EdgeClassificationConstraint(
                    node.path("id").textValue(), strength, node.path("subject").textValue(),
                    enumValue(node.path("parameters"), "edge", TerrainIntentV2.Edge.class),
                    enumValue(node.path("parameters"), "classification", TerrainIntentV2.EdgeClassification.class),
                    fixedInt(node.path("parameters").path("minimumShare01")), weight);
            default -> throw new IllegalArgumentException("unsupported v2 constraint kind");
        };
    }

    private ObjectNode generationRequestTree(GenerationRequestV2 request) {
        ObjectNode node = mapper.createObjectNode();
        node.put("requestVersion", request.requestVersion());
        node.put("requestId", request.requestId());
        ObjectNode bounds = node.putObject("bounds");
        bounds.put("width", request.bounds().width());
        bounds.put("length", request.bounds().length());
        bounds.put("minY", request.bounds().minY());
        bounds.put("maxY", request.bounds().maxY());
        bounds.put("waterLevel", request.bounds().waterLevel());
        node.put("prompt", request.prompt());
        ArrayNode references = node.putArray("referenceImages");
        for (GenerationRequestV2.ReferenceImageSource reference : request.referenceImages()) {
            ObjectNode value = references.addObject();
            value.put("id", reference.id());
            value.put("file", reference.file());
            value.put("role", reference.role().name());
        }
        ArrayNode maps = node.putArray("constraintMaps");
        for (GenerationRequestV2.ConstraintMapSource map : request.constraintMaps()) {
            maps.add(constraintMapSourceTree(map));
        }
        ObjectNode generation = node.putObject("generation");
        generation.put("globalSeed", request.generation().globalSeed());
        generation.put("tileSize", request.generation().tileSize());
        ObjectNode budget = node.putObject("constraintMapBudget");
        budget.put("maximumMapCount", request.constraintMapBudget().maximumMapCount());
        budget.put("maximumTotalSourceBytes", request.constraintMapBudget().maximumTotalSourceBytes());
        budget.put("maximumDecodedBytes", request.constraintMapBudget().maximumDecodedBytes());
        budget.put("maximumPixels", request.constraintMapBudget().maximumPixels());
        budget.put("maximumArtifactBytes", request.constraintMapBudget().maximumArtifactBytes());
        budget.put("maximumResidentBytes", request.constraintMapBudget().maximumResidentBytes());
        // Absent foundation base levels serialize as an absent property, so every pre-V2-18-09
        // request keeps its canonical byte stream and checksum unchanged.
        request.foundationBaseLevels().ifPresent(levels -> {
            ObjectNode foundation = node.putObject("foundationBaseLevels");
            foundation.put("landSurfaceY", levels.landSurfaceY());
            foundation.put("waterBedY", levels.waterBedY());
        });
        return node;
    }

    private ObjectNode constraintMapSourceTree(GenerationRequestV2.ConstraintMapSource source) {
        ObjectNode node = mapper.createObjectNode();
        node.put("sourceId", source.sourceId());
        node.put("file", source.file());
        node.put("expectedSha256", source.expectedSha256());
        node.put("expectedWidth", source.expectedWidth());
        node.put("expectedLength", source.expectedLength());
        node.put("decoderKind", source.decoderKind().name());
        ObjectNode mapping = node.putObject("coordinateMapping");
        mapping.put("origin", source.coordinateMapping().origin().name());
        mapping.put("xAxis", source.coordinateMapping().xAxis().name());
        mapping.put("zAxis", source.coordinateMapping().zAxis().name());
        mapping.put("pixelReference", source.coordinateMapping().pixelReference().name());
        mapping.put("aspectMismatchPolicy", source.coordinateMapping().aspectMismatchPolicy().name());
        mapping.put("rotation", source.coordinateMapping().rotation().name());
        mapping.put("flipX", source.coordinateMapping().flipX());
        mapping.put("flipZ", source.coordinateMapping().flipZ());
        ObjectNode crop = mapping.putObject("crop");
        crop.put("x", source.coordinateMapping().crop().x());
        crop.put("z", source.coordinateMapping().crop().z());
        crop.put("width", source.coordinateMapping().crop().width());
        crop.put("length", source.coordinateMapping().crop().length());
        node.set("encoding", constraintMapEncodingTree(source.encoding()));
        return node;
    }

    private ObjectNode constraintMapEncodingTree(GenerationRequestV2.ConstraintMapEncoding encoding) {
        ObjectNode node = mapper.createObjectNode();
        node.put("encodingVersion", encoding.encodingVersion());
        node.put("sampleType", encoding.sampleType().name());
        node.put("channel", encoding.channel().name());
        if (encoding instanceof GenerationRequestV2.CategoricalEncoding categorical) {
            node.put("kind", "CATEGORICAL");
            ArrayNode labels = node.putArray("labels");
            for (GenerationRequestV2.LabelMapping label : categorical.labels()) {
                ObjectNode value = labels.addObject();
                value.put("sample", label.sample());
                value.put("label", label.label());
            }
        } else if (encoding instanceof GenerationRequestV2.HeightEncoding height) {
            node.put("kind", "HEIGHT");
            node.put("valueMeaning", height.valueMeaning().name());
            node.put("valueScaleMillionths", height.valueScaleMillionths());
            node.put("valueOffsetMillionths", height.valueOffsetMillionths());
            ObjectNode range = node.putObject("validSampleRange");
            range.put("minimum", height.validSampleRange().minimum());
            range.put("maximum", height.validSampleRange().maximum());
        } else {
            throw new IllegalArgumentException("unknown constraint map encoding");
        }
        node.set("noData", noDataTree(encoding.noData()));
        return node;
    }

    private ObjectNode noDataTree(GenerationRequestV2.NoData noData) {
        ObjectNode node = mapper.createObjectNode();
        if (noData instanceof GenerationRequestV2.NoDataForbidden) {
            node.put("mode", "FORBIDDEN");
        } else if (noData instanceof GenerationRequestV2.NoDataSentinel sentinel) {
            node.put("mode", "SENTINEL");
            node.put("sample", sentinel.sample());
        } else {
            throw new IllegalArgumentException("unknown no-data contract");
        }
        return node;
    }

    private ObjectNode intentTree(TerrainIntentV2 intent) {
        ObjectNode root = mapper.createObjectNode();
        root.put("intentVersion", intent.intentVersion()); root.put("intentId", intent.intentId()); root.put("theme", intent.theme());
        ObjectNode coordinate = root.putObject("coordinateSystem");
        coordinate.put("horizontal", intent.coordinateSystem().horizontal().name());
        coordinate.put("origin", intent.coordinateSystem().origin().name()); coordinate.put("xAxis", intent.coordinateSystem().xAxis().name());
        coordinate.put("zAxis", intent.coordinateSystem().zAxis().name()); coordinate.put("vertical", intent.coordinateSystem().vertical().name());
        ArrayNode features = root.putArray("features");
        for (TerrainIntentV2.Feature feature : intent.features()) features.add(featureTree(feature));
        ArrayNode relations = root.putArray("relations");
        for (TerrainIntentV2.Relation relation : intent.relations()) {
            ObjectNode item = relations.addObject(); item.put("id", relation.id()); item.put("kind", relation.kind().name());
            item.put("from", relation.from()); item.put("to", relation.to()); item.put("strength", relation.strength().name());
            if (relation.transition().profile() != TerrainIntentV2.TransitionProfile.NONE) {
                ObjectNode transition = item.putObject("transition");
                transition.put("transitionVersion", relation.transition().transitionVersion());
                transition.put("profile", relation.transition().profile().name());
                transition.put("bandBlocks", relation.transition().bandBlocks());
            }
        }
        ArrayNode constraints = root.putArray("constraints");
        for (TerrainIntentV2.Constraint constraint : intent.constraints()) constraints.add(constraintTree(constraint));
        ObjectNode environment = root.putObject("environment");
        putOptional(environment, "geologyPreset", intent.environment().geologyPreset());
        putOptional(environment, "climatePreset", intent.environment().climatePreset());
        putOptional(environment, "ecologyPreset", intent.environment().ecologyPreset());
        ArrayNode maps = root.putArray("mapReferences");
        for (TerrainIntentV2.ConstraintMapBinding map : intent.mapReferences()) {
            ObjectNode item = maps.addObject(); item.put("id", map.id()); item.put("sourceId", map.sourceId()); item.put("role", map.role().name());
            item.put("artifactId", map.artifactId()); item.put("strength", map.strength().name()); item.put("sampling", map.sampling().name());
            item.put("toleranceBlocks", map.toleranceBlocks()); if (map.strength() == TerrainIntentV2.Strength.SOFT) item.set("weight", fixedNode(map.weightMillionths()));
        }
        ArrayNode structures = root.putArray("structures");
        for (TerrainIntentV2.StructureRequest structure : intent.structures()) {
            ObjectNode item = structures.addObject(); item.put("id", structure.id()); item.put("kind", structure.kind().name());
            item.put("count", structure.count()); item.put("preferredFeatureId", structure.preferredFeatureId());
        }
        root.set("provenance", provenanceTree(intent.provenance()));
        return root;
    }

    private ObjectNode featureTree(TerrainIntentV2.Feature feature) {
        ObjectNode node = mapper.createObjectNode(); node.put("id", feature.id()); node.put("kind", feature.kind().name());
        node.set("geometry", geometryTree(feature.geometry())); node.set("parameters", parameterTree(feature.parameters()));
        node.put("priority", feature.priority()); node.set("provenance", provenanceTree(feature.provenance())); return node;
    }

    private ObjectNode geometryTree(TerrainIntentV2.Geometry geometry) {
        ObjectNode node = mapper.createObjectNode(); node.put("type", geometry.type().name());
        if (geometry instanceof TerrainIntentV2.PointGeometry point) node.set("point", pointTree(point.point()));
        else if (geometry instanceof TerrainIntentV2.MultiPointGeometry multi) {
            ArrayNode points = node.putArray("points"); for (TerrainIntentV2.NamedPoint point : multi.points()) { ObjectNode item = points.addObject(); item.put("id", point.id()); item.set("point", pointTree(point.point())); }
        } else if (geometry instanceof TerrainIntentV2.SplineGeometry spline) {
            node.set("points", pointsTree(spline.points())); node.put("interpolation", spline.interpolation().name());
        } else if (geometry instanceof TerrainIntentV2.MultiSplineGeometry multi) {
            ArrayNode paths = node.putArray("paths"); for (TerrainIntentV2.NamedPath path : multi.paths()) {
                ObjectNode item = paths.addObject(); item.put("id", path.id()); putOptional(item, "startEndpointId", path.startEndpointId());
                putOptional(item, "endEndpointId", path.endEndpointId()); item.set("points", pointsTree(path.points()));
            } node.put("interpolation", multi.interpolation().name());
        } else if (geometry instanceof TerrainIntentV2.PolygonGeometry polygon) {
            node.set("rings", ringsTree(polygon));
        } else if (geometry instanceof TerrainIntentV2.VolumeGuideGeometry volume) {
            ObjectNode footprint = mapper.createObjectNode(); footprint.put("type", "POLYGON"); footprint.set("rings", ringsTree(volume.footprint())); node.set("footprint", footprint);
            ObjectNode vertical = node.putObject("vertical"); vertical.put("mode", volume.vertical().mode().name()); vertical.put("min", volume.vertical().minimum()); vertical.put("max", volume.vertical().maximum());
        }
        return node;
    }

    private ObjectNode parameterTree(TerrainIntentV2.FeatureParameters parameters) {
        ObjectNode node = mapper.createObjectNode();
        if (parameters instanceof TerrainIntentV2.SandyBeachParameters value) {
            node.set("widthBlocks", intRangeTree(value.widthBlocks())); node.set("shoreSlopeDegrees", fixedRangeTree(value.shoreSlopeDegrees()));
            ObjectNode nearshore = node.putObject("nearshoreDepthBlocks"); nearshore.put("atDistance", value.nearshoreDepthBlocks().atDistance()); nearshore.put("target", value.nearshoreDepthBlocks().target());
            node.set("foreshoreShare01", fixedNode(value.foreshoreShareMillionths()));
            node.put("endpointTaperBlocks", value.endpointTaperBlocks());
            node.put("landSide", value.landSide().name());
        } else if (parameters instanceof TerrainIntentV2.BreakwaterHarborParameters value) {
            node.put("crestWidthBlocks", value.crestWidthBlocks()); node.put("crestAboveWaterBlocks", value.crestAboveWaterBlocks()); node.put("outerDepthBlocks", value.outerDepthBlocks());
            node.put("crestProfile", value.crestProfile().name());
            node.put("foundationProfile", value.foundationProfile().name());
            node.set("foundationSideSlopeRunPerRise", fixedNode(value.foundationSideSlopeRunPerRiseMillionths()));
            ObjectNode opening = node.putObject("opening"); ArrayNode endpoints = opening.putArray("betweenEndpointIds"); value.opening().betweenEndpointIds().forEach(endpoints::add);
            opening.put("widthBlocks", value.opening().widthBlocks()); opening.put("measurement", value.opening().measurement().name()); node.put("innerSide", value.innerSide().name());
        } else if (parameters instanceof TerrainIntentV2.HarborBasinParameters value) {
            node.set("waterDepthBlocks", intRangeTree(value.waterDepthBlocks()));
            ArrayNode endpoints = node.putArray("entranceEndpointIds"); value.entranceEndpointIds().forEach(endpoints::add);
            node.put("entranceCorridorLengthBlocks", value.entranceCorridorLengthBlocks());
            node.put("bottomProfile", value.bottomProfile().name());
            node.put("profileTransitionBlocks", value.profileTransitionBlocks());
        } else if (parameters instanceof TerrainIntentV2.RockyCapeParameters value) {
            node.set("cliffHeightBlocks", intRangeTree(value.cliffHeightBlocks())); node.set("localReliefAboveSeaBlocks", intRangeTree(value.localReliefAboveSeaBlocks()));
            node.set("cliffBandWidthBlocks", intRangeTree(value.cliffBandWidthBlocks()));
            node.set("seaStackCount", intRangeTree(value.seaStackCount()));
            node.set("seaStackRadiusBlocks", intRangeTree(value.seaStackRadiusBlocks()));
            node.set("seaStackOffshoreDistanceBlocks", intRangeTree(value.seaStackOffshoreDistanceBlocks()));
            node.set("channelCount", intRangeTree(value.channelCount()));
            node.set("channelWidthBlocks", intRangeTree(value.channelWidthBlocks()));
            node.set("channelLengthBlocks", intRangeTree(value.channelLengthBlocks()));
            node.set("channelDepthBlocks", intRangeTree(value.channelDepthBlocks()));
            node.set("rockExposure01", fixedRangeTree(value.rockExposure01()));
            node.put("seawardSide", value.seawardSide().name());
            node.put("capeMode", value.capeMode().name());
        } else if (parameters instanceof TerrainIntentV2.RockyCoastParameters value) {
            node.set("rockShelfWidthBlocks", intRangeTree(value.rockShelfWidthBlocks()));
            node.set("rockExposure01", fixedRangeTree(value.rockExposure01()));
            node.put("shoreSide", value.shoreSide().name());
            node.set("channelCount", intRangeTree(value.channelCount()));
            node.put("capeOrBeachTransitionBandBlocks", value.capeOrBeachTransitionBandBlocks());
            node.set("talusHandoffDepthBlocks", intRangeTree(value.talusHandoffDepthBlocks()));
        } else if (parameters instanceof TerrainIntentV2.SeaCliffParameters value) {
            node.set("cliffHeightBlocks", intRangeTree(value.cliffHeightBlocks()));
            node.set("talusWidthBlocks", intRangeTree(value.talusWidthBlocks()));
            node.set("notchDepthBlocks", intRangeTree(value.notchDepthBlocks()));
            node.put("seawardSide", value.seawardSide().name());
            node.set("supportHalfExtentXZBlocks", intRangeTree(value.supportHalfExtentXZBlocks()));
            node.put("coastTransitionBandBlocks", value.coastTransitionBandBlocks());
        } else if (parameters instanceof TerrainIntentV2.BackshorePlainsParameters value) {
            node.set("elevationAboveWaterBlocks", intRangeTree(value.elevationAboveWaterBlocks())); node.set("grassCover01", fixedRangeTree(value.grassCover01()));
        } else if (parameters instanceof TerrainIntentV2.PlainParameters value) {
            node.set("baseElevationAboveDatumBlocks", intRangeTree(value.baseElevationAboveDatumBlocks()));
            node.set("microReliefBlocks", intRangeTree(value.microReliefBlocks()));
            node.set("groundwaterHandoffDepthBlocks", intRangeTree(value.groundwaterHandoffDepthBlocks()));
        } else if (parameters instanceof TerrainIntentV2.HillRangeParameters value) {
            node.set("ridgeHalfWidthBlocks", intRangeTree(value.ridgeHalfWidthBlocks()));
            node.set("maxReliefBlocks", intRangeTree(value.maxReliefBlocks()));
            node.set("ridgeStationCount", intRangeTree(value.ridgeStationCount()));
            node.set("saddleCount", intRangeTree(value.saddleCount()));
            node.put("plainTransitionBandBlocks", value.plainTransitionBandBlocks());
        } else if (parameters instanceof TerrainIntentV2.MountainRangeParameters value) {
            node.set("peakCount", intRangeTree(value.peakCount()));
            node.set("ridgeHalfWidthBlocks", intRangeTree(value.ridgeHalfWidthBlocks()));
            node.set("maxReliefBlocks", intRangeTree(value.maxReliefBlocks()));
            node.set("saddleCount", intRangeTree(value.saddleCount()));
            node.put("spurCount", value.spurCount());
            node.put("passCount", value.passCount());
            node.put("foothillBandBlocks", value.foothillBandBlocks());
            node.put("valleyTransitionBandBlocks", value.valleyTransitionBandBlocks());
        } else if (parameters instanceof TerrainIntentV2.ValleyParameters value) {
            node.put("crossSection", value.crossSection().name());
            node.set("floorHalfWidthBlocks", intRangeTree(value.floorHalfWidthBlocks()));
            node.set("shoulderWidthBlocks", intRangeTree(value.shoulderWidthBlocks()));
            node.set("maxDepthBlocks", intRangeTree(value.maxDepthBlocks()));
            node.put("mountainTransitionBandBlocks", value.mountainTransitionBandBlocks());
            node.put("connectionRole", value.connectionRole().name());
        } else if (parameters instanceof TerrainIntentV2.RiverParameters value) {
            node.set("bankfullWidthBlocks", intRangeTree(value.bankfullWidthBlocks()));
            node.put("dischargeClass", value.dischargeClass().name());
            node.set("minimumBedSlope01", fixedNode(value.minimumBedSlopeMillionths()));
            node.set("floodplainHandoffWidthBlocks", intRangeTree(value.floodplainHandoffWidthBlocks()));
            node.put("maxReachCount", value.maxReachCount());
            node.put("maxNodeDegree", value.maxNodeDegree());
        } else if (parameters instanceof TerrainIntentV2.FloodplainParameters value) {
            node.set("riverAdjacencyBandBlocks", intRangeTree(value.riverAdjacencyBandBlocks()));
            node.set("groundwaterHandoffDepthBlocks", intRangeTree(value.groundwaterHandoffDepthBlocks()));
            node.set("microReliefBlocks", intRangeTree(value.microReliefBlocks()));
        } else if (parameters instanceof TerrainIntentV2.MarshParameters value) {
            node.set("hydroperiodBlocks", intRangeTree(value.hydroperiodBlocks()));
            node.set("wetness01", fixedRangeTree(value.wetness01()));
            node.set("openWaterShare01", fixedRangeTree(value.openWaterShare01()));
            node.set("microReliefBlocks", intRangeTree(value.microReliefBlocks()));
            node.set("groundwaterMinDepthBlocks", intRangeTree(value.groundwaterMinDepthBlocks()));
        } else if (parameters instanceof TerrainIntentV2.MeanderingRiverParameters value) {
            node.set("bankfullWidthBlocks", intRangeTree(value.bankfullWidthBlocks()));
            node.put("dischargeClass", value.dischargeClass().name());
            node.set("minimumBedSlope01", fixedNode(value.minimumBedSlopeMillionths()));
            node.put("variant", value.variant().name());
        } else if (parameters instanceof TerrainIntentV2.LakeParameters value) {
            node.set("targetDepthBlocks", intRangeTree(value.targetDepthBlocks()));
            node.put("shoreWidthBlocks", value.shoreWidthBlocks());
            node.put("terminalPolicy", value.terminalPolicy().name());
            node.put("spillSelection", value.spillSelection().name());
            node.put("spillEdgeStartIndex", value.spillEdgeStartIndex());
            node.put("spillwayWidthBlocks", value.spillwayWidthBlocks());
            node.put("spillwayCorridorLengthBlocks", value.spillwayCorridorLengthBlocks());
            node.put("floorProfile", value.floorProfile().name());
        } else if (parameters instanceof TerrainIntentV2.CanyonParameters value) {
            node.set("floorWidthBlocks", intRangeTree(value.floorWidthBlocks()));
            node.set("rimWidthBlocks", intRangeTree(value.rimWidthBlocks()));
            node.set("depthBlocks", intRangeTree(value.depthBlocks()));
            node.put("crossSection", value.crossSection().name());
            node.put("terraceCount", value.terraceCount());
            node.put("terraceWidthBlocks", value.terraceWidthBlocks());
        } else if (parameters instanceof TerrainIntentV2.WaterfallParameters value) {
            node.set("dropBlocks", intRangeTree(value.dropBlocks()));
            node.put("lipWidthBlocks", value.lipWidthBlocks());
            node.put("plungePoolRadiusBlocks", value.plungePoolRadiusBlocks());
            node.put("behindFallClearanceBlocks", value.behindFallClearanceBlocks());
        } else if (parameters instanceof TerrainIntentV2.DeltaParameters value) {
            node.set("distributaryCount", intRangeTree(value.distributaryCount()));
            node.set("fanOpeningDegrees", fixedRangeTree(value.fanOpeningDegrees()));
            node.set("fanReliefBlocks", intRangeTree(value.fanReliefBlocks()));
            node.set("sandbarCount", intRangeTree(value.sandbarCount()));
            node.set("shallowSeaDepthBlocks", intRangeTree(value.shallowSeaDepthBlocks()));
            node.put("fanProfile", value.fanProfile().name());
        } else if (parameters instanceof TerrainIntentV2.TidalChannelParameters value) {
            node.set("widthBlocks", intRangeTree(value.widthBlocks()));
            node.put("tidalRangeBlocks", value.tidalRangeBlocks());
            node.put("edgeKind", value.edgeKind().name());
        } else if (parameters instanceof TerrainIntentV2.FjordParameters value) {
            node.set("surfaceWidthBlocks", intRangeTree(value.surfaceWidthBlocks()));
            node.set("channelDepthBlocks", intRangeTree(value.channelDepthBlocks()));
            node.put("crossSection", value.crossSection().name());
            node.put("headBasinRadiusBlocks", value.headBasinRadiusBlocks());
        } else if (parameters instanceof TerrainIntentV2.MangroveWetlandParameters value) {
            node.set("microReliefBlocks", intRangeTree(value.microReliefBlocks()));
            node.set("waterloggedShare01", fixedRangeTree(value.waterloggedShare01()));
        } else if (parameters instanceof TerrainIntentV2.CoralReefParameters value) {
            node.set("reefCrestDepthBlocks", intRangeTree(value.reefCrestDepthBlocks()));
            node.set("reefWidthBlocks", intRangeTree(value.reefWidthBlocks()));
            node.set("outerSlopeDegrees", intRangeTree(value.outerSlopeDegrees()));
        } else if (parameters instanceof TerrainIntentV2.LagoonParameters value) {
            node.set("depthBlocks", intRangeTree(value.depthBlocks()));
        } else if (parameters instanceof TerrainIntentV2.ReefPassParameters value) {
            node.set("widthBlocks", intRangeTree(value.widthBlocks()));
            node.set("waterDepthBlocks", intRangeTree(value.waterDepthBlocks()));
        } else if (parameters instanceof TerrainIntentV2.MountainParameters value) {
            node.set("peakCount", intRangeTree(value.peakCount()));
            node.set("ridgeHalfWidthBlocks", intRangeTree(value.ridgeHalfWidthBlocks()));
            node.set("maxReliefBlocks", intRangeTree(value.maxReliefBlocks()));
            node.put("spurCount", value.spurCount());
            node.set("ridgeSharpness01", fixedNode(value.ridgeSharpnessMillionths()));
        } else if (parameters instanceof TerrainIntentV2.VolcanicArchipelagoParameters value) {
            ArrayNode islands = node.putArray("islands");
            for (TerrainIntentV2.IslandSpec island : value.islands()) {
                ObjectNode item = islands.addObject();
                item.put("pointId", island.pointId());
                item.put("radiusBlocks", island.radiusBlocks());
                item.put("summitHeightBlocksAboveSea", island.summitHeightBlocksAboveSea());
            }
            node.set("submarineSaddleDepthBlocks", intRangeTree(value.submarineSaddleDepthBlocks()));
        } else if (parameters instanceof TerrainIntentV2.VolcanicCalderaParameters value) {
            node.put("rimRadiusBlocks", value.rimRadiusBlocks());
            node.put("rimReliefBlocks", value.rimReliefBlocks());
            node.put("craterFloorDepthBlocks", value.craterFloorDepthBlocks());
            node.put("breachDirection", value.breachDirection().name());
        } else if (parameters instanceof TerrainIntentV2.LavaFlowParameters value) {
            node.set("widthBlocks", intRangeTree(value.widthBlocks()));
            node.set("surfaceRoughness01", fixedNode(value.surfaceRoughnessMillionths()));
        } else if (parameters instanceof TerrainIntentV2.SingleIslandParameters value) {
            node.set("radiusBlocks", intRangeTree(value.radiusBlocks()));
            node.set("summitHeightBlocksAboveSea", intRangeTree(value.summitHeightBlocksAboveSea()));
            node.set("shoreBandWidthBlocks", intRangeTree(value.shoreBandWidthBlocks()));
            node.set("radialDrainage01", fixedRangeTree(value.radialDrainage01()));
            node.set("submarineApronDepthBlocks", intRangeTree(value.submarineApronDepthBlocks()));
        } else if (parameters instanceof TerrainIntentV2.ArchipelagoParameters value) {
            ArrayNode islands = node.putArray("islands");
            for (TerrainIntentV2.IslandSpec island : value.islands()) {
                ObjectNode item = islands.addObject();
                item.put("pointId", island.pointId());
                item.put("radiusBlocks", island.radiusBlocks());
                item.put("summitHeightBlocksAboveSea", island.summitHeightBlocksAboveSea());
            }
            node.set("submarineSaddleDepthBlocks", intRangeTree(value.submarineSaddleDepthBlocks()));
            node.put("minDryLandGapBlocks", value.minDryLandGapBlocks());
        } else if (parameters instanceof TerrainIntentV2.VolcanicConeParameters value) {
            node.set("baseRadiusBlocks", intRangeTree(value.baseRadiusBlocks()));
            node.set("summitHeightBlocksAboveSea", intRangeTree(value.summitHeightBlocksAboveSea()));
            node.set("craterRadiusBlocks", intRangeTree(value.craterRadiusBlocks()));
            node.set("craterFloorDepthBlocks", intRangeTree(value.craterFloorDepthBlocks()));
            node.set("radialDrainage01", fixedRangeTree(value.radialDrainage01()));
        } else if (parameters instanceof TerrainIntentV2.OceanBasinParameters value) {
            node.set("maxDepthBlocksBelowSea", intRangeTree(value.maxDepthBlocksBelowSea()));
            node.set("floorReliefBlocks", intRangeTree(value.floorReliefBlocks()));
        } else if (parameters instanceof TerrainIntentV2.ContinentalShelfParameters value) {
            node.set("shelfWidthBlocks", intRangeTree(value.shelfWidthBlocks()));
            node.set("shelfDepthBlocksBelowSea", intRangeTree(value.shelfDepthBlocksBelowSea()));
            node.set("coastDistanceBandBlocks", intRangeTree(value.coastDistanceBandBlocks()));
            node.put("seawardSide", value.seawardSide().name());
        } else if (parameters instanceof TerrainIntentV2.ContinentalSlopeParameters value) {
            node.set("slopeWidthBlocks", intRangeTree(value.slopeWidthBlocks()));
            node.set("upperDepthBlocksBelowSea", intRangeTree(value.upperDepthBlocksBelowSea()));
            node.set("lowerDepthBlocksBelowSea", intRangeTree(value.lowerDepthBlocksBelowSea()));
            node.set("maxGradient01", fixedRangeTree(value.maxGradient01()));
        } else if (parameters instanceof TerrainIntentV2.SubmarineCanyonParameters value) {
            node.set("floorWidthBlocks", intRangeTree(value.floorWidthBlocks()));
            node.set("rimWidthBlocks", intRangeTree(value.rimWidthBlocks()));
            node.set("additionalCarveDepthBlocks", intRangeTree(value.additionalCarveDepthBlocks()));
            node.put("crossSection", value.crossSection().name());
            node.put("terraceCount", value.terraceCount());
            node.put("terraceWidthBlocks", value.terraceWidthBlocks());
        } else if (parameters instanceof TerrainIntentV2.CaveEntranceParameters value) {
            node.put("surfaceOffsetBlocks", value.surfaceOffsetBlocks());
            node.put("minimumOpeningBlocks", value.minimumOpeningBlocks());
            node.put("approachLengthBlocks", value.approachLengthBlocks());
            node.put("roofClearanceBlocks", value.roofClearanceBlocks());
            node.put("targetEntranceNodeId", value.targetEntranceNodeId());
        } else if (parameters instanceof TerrainIntentV2.UndergroundRiverParameters value) {
            node.set("channelRadiusBlocks", intRangeTree(value.channelRadiusBlocks()));
            node.set("fluidDepthBlocks", intRangeTree(value.fluidDepthBlocks()));
            node.put("minimumAirPocketBlocks", value.minimumAirPocketBlocks());
            node.put("sourceCaveNodeId", value.sourceCaveNodeId());
            node.put("outletCaveNodeId", value.outletCaveNodeId());
            node.put("fluidBodyId", value.fluidBodyId());
        } else if (parameters instanceof TerrainIntentV2.LavaTubeParameters value) {
            node.set("tubeRadiusBlocks", intRangeTree(value.tubeRadiusBlocks()));
            node.set("roofClearanceBlocks", intRangeTree(value.roofClearanceBlocks()));
            node.set("supportRadiusBlocks", intRangeTree(value.supportRadiusBlocks()));
            if (value.entranceOffsetBlocks() != 0) {
                node.put("entranceOffsetBlocks", value.entranceOffsetBlocks());
            }
        } else if (parameters instanceof TerrainIntentV2.SpringParameters value) {
            node.set("outflowRadiusBlocks", intRangeTree(value.outflowRadiusBlocks()));
            node.set("dischargeBlocks", intRangeTree(value.dischargeBlocks()));
            node.set("supportRadiusBlocks", intRangeTree(value.supportRadiusBlocks()));
        } else if (parameters instanceof TerrainIntentV2.OxbowLakeParameters value) {
            node.set("targetDepthBlocks", intRangeTree(value.targetDepthBlocks()));
            node.put("shoreWidthBlocks", value.shoreWidthBlocks());
            node.set("wetlandHandoffWidthBlocks", intRangeTree(value.wetlandHandoffWidthBlocks()));
            node.set("supportRadiusBlocks", intRangeTree(value.supportRadiusBlocks()));
            if (!value.cutoffReachIdHint().isBlank()) {
                node.put("cutoffReachIdHint", value.cutoffReachIdHint());
            }
        } else if (parameters instanceof TerrainIntentV2.SinkholeParameters value) {
            node.set("collapseRadiusBlocks", intRangeTree(value.collapseRadiusBlocks()));
            node.set("roofClearanceBlocks", intRangeTree(value.roofClearanceBlocks()));
            node.set("lossVolumeBlocks", intRangeTree(value.lossVolumeBlocks()));
            node.put("targetEntranceNodeId", value.targetEntranceNodeId());
        } else if (parameters instanceof TerrainIntentV2.KarstSpringParameters value) {
            node.set("springDischargeBlocks", intRangeTree(value.springDischargeBlocks()));
            node.put("outletHint", value.outletHint());
        } else if (parameters instanceof TerrainIntentV2.FloodedCaveParameters value) {
            node.put("fluidBodyId", value.fluidBodyId());
            node.put("waterSurfaceYBlocks", value.waterSurfaceYBlocks());
            node.put("hostCaveFeatureIdHint", value.hostCaveFeatureIdHint());
        } else if (parameters instanceof TerrainIntentV2.GlacialIceParameters value) {
            node.set("thicknessBlocks", intRangeTree(value.thicknessBlocks()));
            node.set("halfWidthBlocks", intRangeTree(value.halfWidthBlocks()));
            node.put("flowAzimuthDegrees", value.flowAzimuthDegrees());
            node.put("climatePreset", value.climatePreset());
            node.put("meltwaterHandoffFeatureIdHint", value.meltwaterHandoffFeatureIdHint());
        } else if (parameters instanceof TerrainIntentV2.MoraineFieldParameters value) {
            node.set("ridgeCount", intRangeTree(value.ridgeCount()));
            node.set("ridgeHalfWidthBlocks", intRangeTree(value.ridgeHalfWidthBlocks()));
            node.set("sedimentThicknessBlocks", intRangeTree(value.sedimentThicknessBlocks()));
            node.put("flowAzimuthDegrees", value.flowAzimuthDegrees());
        } else if (parameters instanceof TerrainIntentV2.OutwashPlainParameters value) {
            node.set("sedimentThicknessBlocks", intRangeTree(value.sedimentThicknessBlocks()));
            node.set("channelSpacingBlocks", intRangeTree(value.channelSpacingBlocks()));
            node.put("flowAzimuthDegrees", value.flowAzimuthDegrees());
            node.put("meltwaterHandoffFeatureIdHint", value.meltwaterHandoffFeatureIdHint());
        } else if (parameters instanceof TerrainIntentV2.EscarpmentParameters value) {
            node.set("scarpHeightBlocks", intRangeTree(value.scarpHeightBlocks()));
            node.set("talusWidthBlocks", intRangeTree(value.talusWidthBlocks()));
            node.set("floorDropBlocks", intRangeTree(value.floorDropBlocks()));
            node.put("dropSide", value.dropSide().name());
            node.put("plateauTransitionBandBlocks", value.plateauTransitionBandBlocks());
        } else if (parameters instanceof TerrainIntentV2.PlateauParameters value) {
            node.set("capElevationBlocks", intRangeTree(value.capElevationBlocks()));
            node.set("capReliefBlocks", intRangeTree(value.capReliefBlocks()));
            node.put("profile", value.profile().name());
            node.put("escarpmentTransitionBandBlocks", value.escarpmentTransitionBandBlocks());
        }
        return node;
    }

    private ObjectNode constraintTree(TerrainIntentV2.Constraint constraint) {
        ObjectNode node = mapper.createObjectNode(); node.put("id", constraint.id()); node.put("strength", constraint.strength().name());
        if (constraint instanceof TerrainIntentV2.MetricRangeConstraint metric) {
            node.put("kind", "METRIC_RANGE"); node.put("subject", metric.subject()); node.put("metric", metric.metric());
            node.set("range", fixedRangeTree(metric.range())); node.set("tolerance", fixedNode(metric.toleranceMillionths()));
        } else if (constraint instanceof TerrainIntentV2.EdgeClassificationConstraint edge) {
            node.put("kind", "EDGE_CLASSIFICATION"); node.put("subject", edge.subject()); ObjectNode parameters = node.putObject("parameters");
            parameters.put("edge", edge.edge().name()); parameters.put("classification", edge.classification().name()); parameters.set("minimumShare01", fixedNode(edge.minimumShareMillionths()));
        }
        if (constraint.strength() == TerrainIntentV2.Strength.SOFT) node.set("weight", fixedNode(constraint.weightMillionths()));
        return node;
    }

    private ObjectNode provenanceTree(TerrainIntentV2.Provenance provenance) {
        ObjectNode node = mapper.createObjectNode(); node.put("source", provenance.source().name()); node.put("sourceId", provenance.sourceId());
        node.set("confidence", fixedNode(provenance.confidenceMillionths())); node.put("confirmationState", provenance.confirmationState().name()); return node;
    }

    private TerrainIntentV2.Provenance parseProvenance(JsonNode node) {
        return new TerrainIntentV2.Provenance(
                enumValue(node, "source", TerrainIntentV2.ProvenanceSource.class), node.path("sourceId").textValue(),
                fixedInt(node.path("confidence")), enumValue(node, "confirmationState", TerrainIntentV2.ConfirmationState.class));
    }

    private TerrainIntentV2.Point2 parsePoint(JsonNode node) { return new TerrainIntentV2.Point2(fixedInt(node.get(0)), fixedInt(node.get(1))); }
    private List<TerrainIntentV2.Point2> parsePoints(JsonNode node) { List<TerrainIntentV2.Point2> points = new ArrayList<>(); for (JsonNode point : node) points.add(parsePoint(point)); return points; }
    private TerrainIntentV2.IntRange intRange(JsonNode node) { return new TerrainIntentV2.IntRange(node.path("min").intValue(), node.path("max").intValue()); }
    private TerrainIntentV2.FixedRange fixedRange(JsonNode node) { return new TerrainIntentV2.FixedRange(fixed(node.path("min")), fixed(node.path("max"))); }
    private List<String> strings(JsonNode node) { List<String> values = new ArrayList<>(); node.forEach(value -> values.add(value.textValue())); return values; }
    private ArrayNode pointTree(TerrainIntentV2.Point2 point) { ArrayNode node = mapper.createArrayNode(); node.add(fixedNode(point.xMillionths())); node.add(fixedNode(point.zMillionths())); return node; }
    private ArrayNode pointsTree(List<TerrainIntentV2.Point2> points) { ArrayNode node = mapper.createArrayNode(); points.forEach(point -> node.add(pointTree(point))); return node; }
    private ArrayNode ringsTree(TerrainIntentV2.PolygonGeometry polygon) { ArrayNode rings = mapper.createArrayNode(); polygon.rings().forEach(ring -> rings.add(pointsTree(ring))); return rings; }
    private ObjectNode intRangeTree(TerrainIntentV2.IntRange range) { ObjectNode node = mapper.createObjectNode(); node.put("min", range.minimum()); node.put("max", range.maximum()); return node; }
    private ObjectNode fixedRangeTree(TerrainIntentV2.FixedRange range) { ObjectNode node = mapper.createObjectNode(); node.set("min", fixedNode(range.minimumMillionths())); node.set("max", fixedNode(range.maximumMillionths())); return node; }
    private JsonNode fixedNode(long millionths) { return mapper.getNodeFactory().numberNode(BigDecimal.valueOf(millionths, 6).stripTrailingZeros()); }

    private static long fixed(JsonNode node) {
        return node.decimalValue().setScale(6, RoundingMode.UNNECESSARY).movePointRight(6).longValueExact();
    }

    private static int fixedInt(JsonNode node) { return Math.toIntExact(fixed(node)); }

    private static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type) {
        return Enum.valueOf(type, node.path(field).textValue());
    }

    private static String optionalText(JsonNode node, String field) { return node.has(field) ? node.path(field).textValue() : ""; }
    private static void putOptional(ObjectNode node, String field, String value) { if (!value.isEmpty()) node.put(field, value); }

    private VolumeSdfPrimitivePlanV2 parseVolumeSdfPrimitivePlan(JsonNode node) {
        List<VolumeSdfPrimitiveV2> primitives = new ArrayList<>();
        for (JsonNode primitive : node.path("primitives")) {
            primitives.add(parseVolumeSdfPrimitive(primitive));
        }
        JsonNode quantization = node.path("quantization");
        JsonNode kernel = node.path("kernel");
        JsonNode budget = node.path("budget");
        return new VolumeSdfPrimitivePlanV2(
                node.path("planVersion").intValue(),
                node.path("primitiveContractVersion").textValue(),
                new VolumeSdfPrimitivePlanV2.Quantization(
                        quantization.path("quantizationVersion").textValue(),
                        quantization.path("fixedScale").intValue(),
                        quantization.path("geometryScale").intValue()),
                new VolumeSdfPrimitivePlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("maximumSweptControlPoints").intValue(),
                        kernel.path("maximumSampleOperationsPerPrimitive").intValue()),
                primitives,
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("maximumPrimitives").intValue(),
                        budget.path("maximumSweptControlPoints").intValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue()),
                node.path("canonicalChecksum").textValue());
    }

    private VolumeCsgPlanV2 parseVolumeCsgPlan(JsonNode node) {
        List<VolumeCsgPlanV2.Operator> operators = new ArrayList<>();
        for (JsonNode operator : node.path("operators")) {
            List<String> dependencies = new ArrayList<>();
            for (JsonNode dependency : operator.path("dependsOnOperatorIds")) {
                dependencies.add(dependency.textValue());
            }
            operators.add(new VolumeCsgPlanV2.Operator(
                    operator.path("operatorId").textValue(),
                    operator.path("ordinal").intValue(),
                    enumValue(operator, "kind", VolumeCsgPlanV2.OperationKind.class),
                    operator.path("primitiveId").textValue(),
                    enumValue(operator, "mask", VolumeCsgPlanV2.MaskMode.class),
                    operator.path("maskPrimitiveId").textValue(),
                    dependencies,
                    operator.path("fluidBodyId").textValue()));
        }
        JsonNode binding = node.path("primitivePlanBinding");
        JsonNode kernel = node.path("kernel");
        JsonNode budget = node.path("budget");
        return new VolumeCsgPlanV2(
                node.path("planVersion").intValue(),
                node.path("csgContractVersion").textValue(),
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        binding.path("bindingVersion").intValue(),
                        binding.path("sourceVolumeSdfPrimitivePlanChecksum").textValue(),
                        binding.path("bindingContractVersion").textValue()),
                new VolumeCsgPlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("maximumOperators").intValue(),
                        kernel.path("maximumDependencyDepth").intValue(),
                        kernel.path("maximumCpuWorkUnits").longValue()),
                operators,
                new VolumeCsgPlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("maximumOperators").intValue(),
                        budget.path("maximumDependencyDepth").intValue(),
                        budget.path("estimatedCpuWorkUnits").longValue(),
                        budget.path("maximumCpuWorkUnits").longValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode volumeCsgPlanTree(VolumeCsgPlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("csgContractVersion", plan.csgContractVersion());
        ObjectNode binding = node.putObject("primitivePlanBinding");
        binding.put("bindingVersion", plan.primitivePlanBinding().bindingVersion());
        binding.put("sourceVolumeSdfPrimitivePlanChecksum",
                plan.primitivePlanBinding().sourceVolumeSdfPrimitivePlanChecksum());
        binding.put("bindingContractVersion", plan.primitivePlanBinding().bindingContractVersion());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("maximumOperators", plan.kernel().maximumOperators());
        kernel.put("maximumDependencyDepth", plan.kernel().maximumDependencyDepth());
        kernel.put("maximumCpuWorkUnits", plan.kernel().maximumCpuWorkUnits());
        ArrayNode operators = node.putArray("operators");
        for (VolumeCsgPlanV2.Operator operator : plan.operators()) {
            ObjectNode item = operators.addObject();
            item.put("operatorId", operator.operatorId());
            item.put("ordinal", operator.ordinal());
            item.put("kind", operator.kind().name());
            item.put("primitiveId", operator.primitiveId());
            item.put("mask", operator.mask().name());
            item.put("maskPrimitiveId", operator.maskPrimitiveId());
            ArrayNode deps = item.putArray("dependsOnOperatorIds");
            for (String dependency : operator.dependsOnOperatorIds()) {
                deps.add(dependency);
            }
            item.put("fluidBodyId", operator.fluidBodyId());
        }
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("maximumOperators", plan.budget().maximumOperators());
        budget.put("maximumDependencyDepth", plan.budget().maximumDependencyDepth());
        budget.put("estimatedCpuWorkUnits", plan.budget().estimatedCpuWorkUnits());
        budget.put("maximumCpuWorkUnits", plan.budget().maximumCpuWorkUnits());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private VolumeAabbIndexPlanV2 parseVolumeAabbIndexPlan(JsonNode node) {
        List<VolumeAabbIndexPlanV2.Entry> entries = new ArrayList<>();
        for (JsonNode entry : node.path("entries")) {
            entries.add(new VolumeAabbIndexPlanV2.Entry(
                    entry.path("entryId").textValue(),
                    entry.path("operatorId").textValue(),
                    entry.path("ordinal").intValue(),
                    parseVolumeAabb(entry.path("aabb")),
                    entry.path("supportRadiusXZBlocks").intValue(),
                    entry.path("supportRadiusYBlocks").intValue()));
        }
        JsonNode binding = node.path("csgPlanBinding");
        JsonNode kernel = node.path("kernel");
        JsonNode budget = node.path("budget");
        return new VolumeAabbIndexPlanV2(
                node.path("planVersion").intValue(),
                node.path("indexContractVersion").textValue(),
                new VolumeAabbIndexPlanV2.CsgPlanBinding(
                        binding.path("bindingVersion").intValue(),
                        binding.path("sourceVolumeCsgPlanChecksum").textValue(),
                        binding.path("bindingContractVersion").textValue()),
                new VolumeAabbIndexPlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("maximumEntries").intValue(),
                        kernel.path("maximumQueryResults").intValue(),
                        kernel.path("maximumSupportBlocksXZ").intValue(),
                        kernel.path("maximumSupportBlocksY").intValue()),
                entries,
                new VolumeAabbIndexPlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("maximumEntries").intValue(),
                        budget.path("maximumQueryResults").intValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue(),
                        budget.path("maximumIndexNodes").longValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode volumeAabbIndexPlanTree(VolumeAabbIndexPlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("indexContractVersion", plan.indexContractVersion());
        ObjectNode binding = node.putObject("csgPlanBinding");
        binding.put("bindingVersion", plan.csgPlanBinding().bindingVersion());
        binding.put("sourceVolumeCsgPlanChecksum",
                plan.csgPlanBinding().sourceVolumeCsgPlanChecksum());
        binding.put("bindingContractVersion", plan.csgPlanBinding().bindingContractVersion());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("maximumEntries", plan.kernel().maximumEntries());
        kernel.put("maximumQueryResults", plan.kernel().maximumQueryResults());
        kernel.put("maximumSupportBlocksXZ", plan.kernel().maximumSupportBlocksXZ());
        kernel.put("maximumSupportBlocksY", plan.kernel().maximumSupportBlocksY());
        ArrayNode entries = node.putArray("entries");
        for (VolumeAabbIndexPlanV2.Entry entry : plan.entries()) {
            ObjectNode item = entries.addObject();
            item.put("entryId", entry.entryId());
            item.put("operatorId", entry.operatorId());
            item.put("ordinal", entry.ordinal());
            item.set("aabb", volumeAabbTree(entry.aabb()));
            item.put("supportRadiusXZBlocks", entry.supportRadiusXZBlocks());
            item.put("supportRadiusYBlocks", entry.supportRadiusYBlocks());
        }
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("maximumEntries", plan.budget().maximumEntries());
        budget.put("maximumQueryResults", plan.budget().maximumQueryResults());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        budget.put("maximumIndexNodes", plan.budget().maximumIndexNodes());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private VolumeTileCachePlanV2 parseVolumeTileCachePlan(JsonNode node) {
        JsonNode binding = node.path("csgPlanBinding");
        JsonNode kernel = node.path("kernel");
        JsonNode budget = node.path("budget");
        return new VolumeTileCachePlanV2(
                node.path("planVersion").intValue(),
                node.path("cacheContractVersion").textValue(),
                new VolumeTileCachePlanV2.CsgPlanBinding(
                        binding.path("bindingVersion").intValue(),
                        binding.path("sourceVolumeCsgPlanChecksum").textValue(),
                        binding.path("bindingContractVersion").textValue()),
                new VolumeTileCachePlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("chunkEdgeBlocks").intValue(),
                        kernel.path("haloBlocksXyz").intValue(),
                        kernel.path("maximumRetainedChunks").intValue(),
                        kernel.path("maximumConcurrentChunks").intValue(),
                        kernel.path("maximumSolidIntervalsPerColumn").intValue(),
                        kernel.path("maximumFluidIntervalsPerColumn").intValue()),
                new VolumeTileCachePlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("maximumRetainedBytes").longValue(),
                        budget.path("maximumPeakWorkingBytesPerChunk").longValue(),
                        budget.path("maximumConcurrency").intValue(),
                        budget.path("maximumCacheBytes").longValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode volumeTileCachePlanTree(VolumeTileCachePlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("cacheContractVersion", plan.cacheContractVersion());
        ObjectNode binding = node.putObject("csgPlanBinding");
        binding.put("bindingVersion", plan.csgPlanBinding().bindingVersion());
        binding.put("sourceVolumeCsgPlanChecksum",
                plan.csgPlanBinding().sourceVolumeCsgPlanChecksum());
        binding.put("bindingContractVersion", plan.csgPlanBinding().bindingContractVersion());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("chunkEdgeBlocks", plan.kernel().chunkEdgeBlocks());
        kernel.put("haloBlocksXyz", plan.kernel().haloBlocksXyz());
        kernel.put("maximumRetainedChunks", plan.kernel().maximumRetainedChunks());
        kernel.put("maximumConcurrentChunks", plan.kernel().maximumConcurrentChunks());
        kernel.put("maximumSolidIntervalsPerColumn", plan.kernel().maximumSolidIntervalsPerColumn());
        kernel.put("maximumFluidIntervalsPerColumn", plan.kernel().maximumFluidIntervalsPerColumn());
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("maximumRetainedBytes", plan.budget().maximumRetainedBytes());
        budget.put("maximumPeakWorkingBytesPerChunk", plan.budget().maximumPeakWorkingBytesPerChunk());
        budget.put("maximumConcurrency", plan.budget().maximumConcurrency());
        budget.put("maximumCacheBytes", plan.budget().maximumCacheBytes());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private CaveNetworkPlanV2 parseCaveNetworkPlan(JsonNode node) {
        java.util.ArrayList<CaveNetworkPlanV2.Node> nodes = new java.util.ArrayList<>();
        for (JsonNode n : node.path("nodes")) {
            nodes.add(new CaveNetworkPlanV2.Node(
                    n.path("nodeId").textValue(),
                    enumValue(n, "kind", CaveNetworkPlanV2.NodeKind.class),
                    parseVolumeVec3(n.path("center")),
                    n.path("radiusMillionths").longValue()));
        }
        java.util.ArrayList<CaveNetworkPlanV2.Edge> edges = new java.util.ArrayList<>();
        for (JsonNode e : node.path("edges")) {
            edges.add(new CaveNetworkPlanV2.Edge(
                    e.path("edgeId").textValue(),
                    e.path("fromNodeId").textValue(),
                    e.path("toNodeId").textValue(),
                    e.path("radiusMillionths").longValue()));
        }
        java.util.ArrayList<String> entrances = new java.util.ArrayList<>();
        for (JsonNode e : node.path("entranceNodeIds")) {
            entrances.add(e.textValue());
        }
        JsonNode kernel = node.path("kernel");
        JsonNode budget = node.path("budget");
        JsonNode sdf = node.path("sdfPlanBinding");
        JsonNode csg = node.path("csgPlanBinding");
        return new CaveNetworkPlanV2(
                node.path("planVersion").intValue(),
                node.path("caveContractVersion").textValue(),
                node.path("featureId").textValue(),
                new CaveNetworkPlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("minimumRoofBlocks").intValue(),
                        kernel.path("maximumNodes").intValue(),
                        kernel.path("maximumEdges").intValue(),
                        kernel.path("maximumRadiusMillionths").longValue()),
                nodes,
                edges,
                entrances,
                parseVolumeAabb(node.path("aabb")),
                node.path("surfaceHeightBlocks").intValue(),
                new CaveNetworkPlanV2.ArtifactBinding(
                        sdf.path("bindingVersion").intValue(),
                        sdf.path("sourceArtifactChecksum").textValue(),
                        sdf.path("bindingContractVersion").textValue()),
                new CaveNetworkPlanV2.ArtifactBinding(
                        csg.path("bindingVersion").intValue(),
                        csg.path("sourceArtifactChecksum").textValue(),
                        csg.path("bindingContractVersion").textValue()),
                new CaveNetworkPlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("maximumNodes").intValue(),
                        budget.path("maximumEdges").intValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue(),
                        budget.path("maximumGraphBytes").longValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode caveNetworkPlanTree(CaveNetworkPlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("caveContractVersion", plan.caveContractVersion());
        node.put("featureId", plan.featureId());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("minimumRoofBlocks", plan.kernel().minimumRoofBlocks());
        kernel.put("maximumNodes", plan.kernel().maximumNodes());
        kernel.put("maximumEdges", plan.kernel().maximumEdges());
        kernel.put("maximumRadiusMillionths", plan.kernel().maximumRadiusMillionths());
        ArrayNode nodes = node.putArray("nodes");
        for (CaveNetworkPlanV2.Node n : plan.nodes()) {
            ObjectNode item = nodes.addObject();
            item.put("nodeId", n.nodeId());
            item.put("kind", n.kind().name());
            item.set("center", volumeVec3Tree(n.center()));
            item.put("radiusMillionths", n.radiusMillionths());
        }
        ArrayNode edges = node.putArray("edges");
        for (CaveNetworkPlanV2.Edge e : plan.edges()) {
            ObjectNode item = edges.addObject();
            item.put("edgeId", e.edgeId());
            item.put("fromNodeId", e.fromNodeId());
            item.put("toNodeId", e.toNodeId());
            item.put("radiusMillionths", e.radiusMillionths());
        }
        ArrayNode entrances = node.putArray("entranceNodeIds");
        for (String id : plan.entranceNodeIds()) {
            entrances.add(id);
        }
        node.set("aabb", volumeAabbTree(plan.aabb()));
        node.put("surfaceHeightBlocks", plan.surfaceHeightBlocks());
        ObjectNode sdf = node.putObject("sdfPlanBinding");
        sdf.put("bindingVersion", plan.sdfPlanBinding().bindingVersion());
        sdf.put("sourceArtifactChecksum", plan.sdfPlanBinding().sourceArtifactChecksum());
        sdf.put("bindingContractVersion", plan.sdfPlanBinding().bindingContractVersion());
        ObjectNode csg = node.putObject("csgPlanBinding");
        csg.put("bindingVersion", plan.csgPlanBinding().bindingVersion());
        csg.put("sourceArtifactChecksum", plan.csgPlanBinding().sourceArtifactChecksum());
        csg.put("bindingContractVersion", plan.csgPlanBinding().bindingContractVersion());
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("maximumNodes", plan.budget().maximumNodes());
        budget.put("maximumEdges", plan.budget().maximumEdges());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        budget.put("maximumGraphBytes", plan.budget().maximumGraphBytes());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private LushCavePlanV2 parseLushCavePlan(JsonNode node) {
        JsonNode kernel = node.path("kernel");
        JsonNode host = node.path("hostBinding");
        JsonNode reachable = node.path("reachableFrom");
        JsonNode chamber = node.path("chamber");
        JsonNode wet = node.path("wetCondition");
        JsonNode ecology = node.path("ecologyHook");
        JsonNode sdf = node.path("sdfPlanBinding");
        JsonNode csg = node.path("csgPlanBinding");
        JsonNode budget = node.path("budget");
        java.util.ArrayList<LushCavePlanV2.WetSurfaceClass> surfaces = new java.util.ArrayList<>();
        for (JsonNode surface : wet.path("eligibleSurfaceClasses")) {
            surfaces.add(Enum.valueOf(LushCavePlanV2.WetSurfaceClass.class, surface.textValue()));
        }
        java.util.ArrayList<String> assemblages = new java.util.ArrayList<>();
        for (JsonNode id : ecology.path("reservedAssemblageIds")) {
            assemblages.add(id.textValue());
        }
        return new LushCavePlanV2(
                node.path("planVersion").intValue(),
                node.path("lushContractVersion").textValue(),
                node.path("featureId").textValue(),
                new LushCavePlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("minimumRoofBlocks").intValue(),
                        kernel.path("minimumMoistureMillionths").intValue(),
                        kernel.path("minimumCeilingClearanceBlocks").intValue(),
                        kernel.path("maximumRadiusMillionths").longValue(),
                        kernel.path("maximumDescriptorSamples").intValue()),
                new LushCavePlanV2.HostBinding(
                        host.path("relationKind").textValue(),
                        host.path("hostNetworkFeatureId").textValue(),
                        host.path("hostNetworkPlanChecksum").textValue(),
                        host.path("hostChamberNodeId").textValue()),
                new LushCavePlanV2.ReachableFromBinding(
                        reachable.path("relationKind").textValue(),
                        reachable.path("entranceNodeId").textValue()),
                new LushCavePlanV2.ChamberSpec(
                        parseVolumeVec3(chamber.path("center")),
                        chamber.path("radiusMillionths").longValue(),
                        chamber.path("ceilingClearanceBlocks").intValue()),
                new LushCavePlanV2.WetCondition(
                        wet.path("moistureMillionths").intValue(),
                        wet.path("poolShareMillionths").intValue(),
                        surfaces),
                new LushCavePlanV2.EcologyHook(
                        ecology.path("hookVersion").textValue(),
                        ecology.path("ecologyPreset").textValue(),
                        assemblages),
                parseVolumeAabb(node.path("aabb")),
                node.path("surfaceHeightBlocks").intValue(),
                new LushCavePlanV2.ArtifactBinding(
                        sdf.path("bindingVersion").intValue(),
                        sdf.path("sourceArtifactChecksum").textValue(),
                        sdf.path("bindingContractVersion").textValue()),
                new LushCavePlanV2.ArtifactBinding(
                        csg.path("bindingVersion").intValue(),
                        csg.path("sourceArtifactChecksum").textValue(),
                        csg.path("bindingContractVersion").textValue()),
                new LushCavePlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue(),
                        budget.path("maximumDescriptorSamples").intValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode lushCavePlanTree(LushCavePlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("lushContractVersion", plan.lushContractVersion());
        node.put("featureId", plan.featureId());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("minimumRoofBlocks", plan.kernel().minimumRoofBlocks());
        kernel.put("minimumMoistureMillionths", plan.kernel().minimumMoistureMillionths());
        kernel.put("minimumCeilingClearanceBlocks", plan.kernel().minimumCeilingClearanceBlocks());
        kernel.put("maximumRadiusMillionths", plan.kernel().maximumRadiusMillionths());
        kernel.put("maximumDescriptorSamples", plan.kernel().maximumDescriptorSamples());
        ObjectNode host = node.putObject("hostBinding");
        host.put("relationKind", plan.hostBinding().relationKind());
        host.put("hostNetworkFeatureId", plan.hostBinding().hostNetworkFeatureId());
        host.put("hostNetworkPlanChecksum", plan.hostBinding().hostNetworkPlanChecksum());
        host.put("hostChamberNodeId", plan.hostBinding().hostChamberNodeId());
        ObjectNode reachable = node.putObject("reachableFrom");
        reachable.put("relationKind", plan.reachableFrom().relationKind());
        reachable.put("entranceNodeId", plan.reachableFrom().entranceNodeId());
        ObjectNode chamber = node.putObject("chamber");
        chamber.set("center", volumeVec3Tree(plan.chamber().center()));
        chamber.put("radiusMillionths", plan.chamber().radiusMillionths());
        chamber.put("ceilingClearanceBlocks", plan.chamber().ceilingClearanceBlocks());
        ObjectNode wet = node.putObject("wetCondition");
        wet.put("moistureMillionths", plan.wetCondition().moistureMillionths());
        wet.put("poolShareMillionths", plan.wetCondition().poolShareMillionths());
        ArrayNode surfaces = wet.putArray("eligibleSurfaceClasses");
        for (LushCavePlanV2.WetSurfaceClass surface : plan.wetCondition().eligibleSurfaceClasses()) {
            surfaces.add(surface.name());
        }
        ObjectNode ecology = node.putObject("ecologyHook");
        ecology.put("hookVersion", plan.ecologyHook().hookVersion());
        ecology.put("ecologyPreset", plan.ecologyHook().ecologyPreset());
        ArrayNode assemblages = ecology.putArray("reservedAssemblageIds");
        for (String id : plan.ecologyHook().reservedAssemblageIds()) {
            assemblages.add(id);
        }
        node.set("aabb", volumeAabbTree(plan.aabb()));
        node.put("surfaceHeightBlocks", plan.surfaceHeightBlocks());
        ObjectNode sdf = node.putObject("sdfPlanBinding");
        sdf.put("bindingVersion", plan.sdfPlanBinding().bindingVersion());
        sdf.put("sourceArtifactChecksum", plan.sdfPlanBinding().sourceArtifactChecksum());
        sdf.put("bindingContractVersion", plan.sdfPlanBinding().bindingContractVersion());
        ObjectNode csg = node.putObject("csgPlanBinding");
        csg.put("bindingVersion", plan.csgPlanBinding().bindingVersion());
        csg.put("sourceArtifactChecksum", plan.csgPlanBinding().sourceArtifactChecksum());
        csg.put("bindingContractVersion", plan.csgPlanBinding().bindingContractVersion());
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        budget.put("maximumDescriptorSamples", plan.budget().maximumDescriptorSamples());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private UndergroundLakePlanV2 parseUndergroundLakePlan(JsonNode node) {
        JsonNode kernel = node.path("kernel");
        JsonNode host = node.path("hostBinding");
        JsonNode access = node.path("caveAccess");
        JsonNode basin = node.path("basin");
        JsonNode fluid = node.path("fluidBody");
        JsonNode sdf = node.path("sdfPlanBinding");
        JsonNode csg = node.path("csgPlanBinding");
        JsonNode budget = node.path("budget");
        return new UndergroundLakePlanV2(
                node.path("planVersion").intValue(),
                node.path("lakeContractVersion").textValue(),
                node.path("featureId").textValue(),
                new UndergroundLakePlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("minimumRoofBlocks").intValue(),
                        kernel.path("minimumAirCavityBlocks").intValue(),
                        kernel.path("minimumRimThicknessBlocks").intValue(),
                        kernel.path("maximumRadiusMillionths").longValue(),
                        kernel.path("maximumDescriptorSamples").intValue()),
                new UndergroundLakePlanV2.HostBinding(
                        host.path("relationKind").textValue(),
                        host.path("hostNetworkFeatureId").textValue(),
                        host.path("hostNetworkPlanChecksum").textValue(),
                        host.path("hostChamberNodeId").textValue()),
                new UndergroundLakePlanV2.CaveAccessBinding(
                        access.path("relationKind").textValue(),
                        access.path("entranceNodeId").textValue()),
                new UndergroundLakePlanV2.BasinSpec(
                        parseVolumeVec3(basin.path("center")),
                        basin.path("radiusMillionths").longValue(),
                        basin.path("minimumAirCavityBlocks").intValue()),
                new UndergroundLakePlanV2.FluidBody(
                        fluid.path("fluidBodyId").textValue(),
                        fluid.path("waterSurfaceYBlocks").intValue()),
                parseVolumeAabb(node.path("aabb")),
                node.path("surfaceHeightBlocks").intValue(),
                new UndergroundLakePlanV2.ArtifactBinding(
                        sdf.path("bindingVersion").intValue(),
                        sdf.path("sourceArtifactChecksum").textValue(),
                        sdf.path("bindingContractVersion").textValue()),
                new UndergroundLakePlanV2.ArtifactBinding(
                        csg.path("bindingVersion").intValue(),
                        csg.path("sourceArtifactChecksum").textValue(),
                        csg.path("bindingContractVersion").textValue()),
                new UndergroundLakePlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue(),
                        budget.path("maximumDescriptorSamples").intValue(),
                        budget.path("maximumFluidIntervalsPerColumn").intValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode undergroundLakePlanTree(UndergroundLakePlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("lakeContractVersion", plan.lakeContractVersion());
        node.put("featureId", plan.featureId());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("minimumRoofBlocks", plan.kernel().minimumRoofBlocks());
        kernel.put("minimumAirCavityBlocks", plan.kernel().minimumAirCavityBlocks());
        kernel.put("minimumRimThicknessBlocks", plan.kernel().minimumRimThicknessBlocks());
        kernel.put("maximumRadiusMillionths", plan.kernel().maximumRadiusMillionths());
        kernel.put("maximumDescriptorSamples", plan.kernel().maximumDescriptorSamples());
        ObjectNode host = node.putObject("hostBinding");
        host.put("relationKind", plan.hostBinding().relationKind());
        host.put("hostNetworkFeatureId", plan.hostBinding().hostNetworkFeatureId());
        host.put("hostNetworkPlanChecksum", plan.hostBinding().hostNetworkPlanChecksum());
        host.put("hostChamberNodeId", plan.hostBinding().hostChamberNodeId());
        ObjectNode access = node.putObject("caveAccess");
        access.put("relationKind", plan.caveAccess().relationKind());
        access.put("entranceNodeId", plan.caveAccess().entranceNodeId());
        ObjectNode basin = node.putObject("basin");
        basin.set("center", volumeVec3Tree(plan.basin().center()));
        basin.put("radiusMillionths", plan.basin().radiusMillionths());
        basin.put("minimumAirCavityBlocks", plan.basin().minimumAirCavityBlocks());
        ObjectNode fluid = node.putObject("fluidBody");
        fluid.put("fluidBodyId", plan.fluidBody().fluidBodyId());
        fluid.put("waterSurfaceYBlocks", plan.fluidBody().waterSurfaceYBlocks());
        node.set("aabb", volumeAabbTree(plan.aabb()));
        node.put("surfaceHeightBlocks", plan.surfaceHeightBlocks());
        ObjectNode sdf = node.putObject("sdfPlanBinding");
        sdf.put("bindingVersion", plan.sdfPlanBinding().bindingVersion());
        sdf.put("sourceArtifactChecksum", plan.sdfPlanBinding().sourceArtifactChecksum());
        sdf.put("bindingContractVersion", plan.sdfPlanBinding().bindingContractVersion());
        ObjectNode csg = node.putObject("csgPlanBinding");
        csg.put("bindingVersion", plan.csgPlanBinding().bindingVersion());
        csg.put("sourceArtifactChecksum", plan.csgPlanBinding().sourceArtifactChecksum());
        csg.put("bindingContractVersion", plan.csgPlanBinding().bindingContractVersion());
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        budget.put("maximumDescriptorSamples", plan.budget().maximumDescriptorSamples());
        budget.put("maximumFluidIntervalsPerColumn", plan.budget().maximumFluidIntervalsPerColumn());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private SeaCavePlanV2 parseSeaCavePlan(JsonNode node) {
        JsonNode kernel = node.path("kernel");
        JsonNode host = node.path("hostCliff");
        JsonNode marine = node.path("marineBoundary");
        JsonNode chamber = node.path("chamber");
        JsonNode fluid = node.path("fluidBody");
        JsonNode sdf = node.path("sdfPlanBinding");
        JsonNode csg = node.path("csgPlanBinding");
        JsonNode budget = node.path("budget");
        return new SeaCavePlanV2(
                node.path("planVersion").intValue(),
                node.path("seaCaveContractVersion").textValue(),
                node.path("featureId").textValue(),
                new SeaCavePlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("minimumRoofBlocks").intValue(),
                        kernel.path("minimumOpeningBlocks").intValue(),
                        kernel.path("maximumRadiusMillionths").longValue(),
                        kernel.path("maximumDescriptorSamples").intValue()),
                new SeaCavePlanV2.HostCliffBinding(
                        host.path("relationKind").textValue(),
                        host.path("hostCliffFeatureId").textValue(),
                        Enum.valueOf(SeaCavePlanV2.CardinalFace.class, host.path("seawardFace").textValue()),
                        parseVolumeAabb(host.path("hostAabb"))),
                new SeaCavePlanV2.MarineBoundaryBinding(
                        marine.path("relationKind").textValue(),
                        marine.path("marineBoundaryId").textValue(),
                        marine.path("seaLevelYBlocks").intValue()),
                new SeaCavePlanV2.ChamberSpec(
                        parseVolumeVec3(chamber.path("openingCenter")),
                        parseVolumeVec3(chamber.path("inlandCenter")),
                        chamber.path("radiusMillionths").longValue()),
                new SeaCavePlanV2.FluidBody(
                        fluid.path("fluidBodyId").textValue(),
                        fluid.path("waterSurfaceYBlocks").intValue()),
                parseVolumeAabb(node.path("aabb")),
                node.path("surfaceHeightBlocks").intValue(),
                new SeaCavePlanV2.ArtifactBinding(
                        sdf.path("bindingVersion").intValue(),
                        sdf.path("sourceArtifactChecksum").textValue(),
                        sdf.path("bindingContractVersion").textValue()),
                new SeaCavePlanV2.ArtifactBinding(
                        csg.path("bindingVersion").intValue(),
                        csg.path("sourceArtifactChecksum").textValue(),
                        csg.path("bindingContractVersion").textValue()),
                new SeaCavePlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue(),
                        budget.path("maximumDescriptorSamples").intValue(),
                        budget.path("maximumFluidIntervalsPerColumn").intValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode seaCavePlanTree(SeaCavePlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("seaCaveContractVersion", plan.seaCaveContractVersion());
        node.put("featureId", plan.featureId());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("minimumRoofBlocks", plan.kernel().minimumRoofBlocks());
        kernel.put("minimumOpeningBlocks", plan.kernel().minimumOpeningBlocks());
        kernel.put("maximumRadiusMillionths", plan.kernel().maximumRadiusMillionths());
        kernel.put("maximumDescriptorSamples", plan.kernel().maximumDescriptorSamples());
        ObjectNode host = node.putObject("hostCliff");
        host.put("relationKind", plan.hostCliff().relationKind());
        host.put("hostCliffFeatureId", plan.hostCliff().hostCliffFeatureId());
        host.put("seawardFace", plan.hostCliff().seawardFace().name());
        host.set("hostAabb", volumeAabbTree(plan.hostCliff().hostAabb()));
        ObjectNode marine = node.putObject("marineBoundary");
        marine.put("relationKind", plan.marineBoundary().relationKind());
        marine.put("marineBoundaryId", plan.marineBoundary().marineBoundaryId());
        marine.put("seaLevelYBlocks", plan.marineBoundary().seaLevelYBlocks());
        ObjectNode chamber = node.putObject("chamber");
        chamber.set("openingCenter", volumeVec3Tree(plan.chamber().openingCenter()));
        chamber.set("inlandCenter", volumeVec3Tree(plan.chamber().inlandCenter()));
        chamber.put("radiusMillionths", plan.chamber().radiusMillionths());
        ObjectNode fluid = node.putObject("fluidBody");
        fluid.put("fluidBodyId", plan.fluidBody().fluidBodyId());
        fluid.put("waterSurfaceYBlocks", plan.fluidBody().waterSurfaceYBlocks());
        node.set("aabb", volumeAabbTree(plan.aabb()));
        node.put("surfaceHeightBlocks", plan.surfaceHeightBlocks());
        ObjectNode sdf = node.putObject("sdfPlanBinding");
        sdf.put("bindingVersion", plan.sdfPlanBinding().bindingVersion());
        sdf.put("sourceArtifactChecksum", plan.sdfPlanBinding().sourceArtifactChecksum());
        sdf.put("bindingContractVersion", plan.sdfPlanBinding().bindingContractVersion());
        ObjectNode csg = node.putObject("csgPlanBinding");
        csg.put("bindingVersion", plan.csgPlanBinding().bindingVersion());
        csg.put("sourceArtifactChecksum", plan.csgPlanBinding().sourceArtifactChecksum());
        csg.put("bindingContractVersion", plan.csgPlanBinding().bindingContractVersion());
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        budget.put("maximumDescriptorSamples", plan.budget().maximumDescriptorSamples());
        budget.put("maximumFluidIntervalsPerColumn", plan.budget().maximumFluidIntervalsPerColumn());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private OverhangPlanV2 parseOverhangPlan(JsonNode node) {
        JsonNode kernel = node.path("kernel");
        JsonNode host = node.path("hostCliff");
        JsonNode lobe = node.path("lobe");
        JsonNode recess = node.path("recess");
        JsonNode sdf = node.path("sdfPlanBinding");
        JsonNode csg = node.path("csgPlanBinding");
        JsonNode budget = node.path("budget");
        return new OverhangPlanV2(
                node.path("planVersion").intValue(),
                node.path("overhangContractVersion").textValue(),
                node.path("featureId").textValue(),
                new OverhangPlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("minimumRoofBlocks").intValue(),
                        kernel.path("minimumSupportSamples").intValue(),
                        kernel.path("minimumClearanceSamples").intValue(),
                        kernel.path("minimumProjectionBlocks").intValue(),
                        kernel.path("maximumHalfExtentMillionths").longValue(),
                        kernel.path("maximumDescriptorSamples").intValue()),
                new OverhangPlanV2.HostCliffBinding(
                        host.path("relationKind").textValue(),
                        host.path("hostCliffFeatureId").textValue(),
                        Enum.valueOf(OverhangPlanV2.CardinalFace.class, host.path("seawardFace").textValue()),
                        parseVolumeAabb(host.path("hostAabb"))),
                new OverhangPlanV2.LobeSpec(
                        parseVolumeVec3(lobe.path("center")),
                        parseVolumeVec3(lobe.path("halfExtentsMillionths")),
                        lobe.path("cornerRadiusMillionths").longValue()),
                new OverhangPlanV2.RecessSpec(
                        parseVolumeVec3(recess.path("center")),
                        parseVolumeVec3(recess.path("halfExtentsMillionths")),
                        recess.path("cornerRadiusMillionths").longValue()),
                parseVolumeAabb(node.path("aabb")),
                new OverhangPlanV2.ArtifactBinding(
                        sdf.path("bindingVersion").intValue(),
                        sdf.path("sourceArtifactChecksum").textValue(),
                        sdf.path("bindingContractVersion").textValue()),
                new OverhangPlanV2.ArtifactBinding(
                        csg.path("bindingVersion").intValue(),
                        csg.path("sourceArtifactChecksum").textValue(),
                        csg.path("bindingContractVersion").textValue()),
                new OverhangPlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue(),
                        budget.path("maximumDescriptorSamples").intValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode overhangPlanTree(OverhangPlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("overhangContractVersion", plan.overhangContractVersion());
        node.put("featureId", plan.featureId());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("minimumRoofBlocks", plan.kernel().minimumRoofBlocks());
        kernel.put("minimumSupportSamples", plan.kernel().minimumSupportSamples());
        kernel.put("minimumClearanceSamples", plan.kernel().minimumClearanceSamples());
        kernel.put("minimumProjectionBlocks", plan.kernel().minimumProjectionBlocks());
        kernel.put("maximumHalfExtentMillionths", plan.kernel().maximumHalfExtentMillionths());
        kernel.put("maximumDescriptorSamples", plan.kernel().maximumDescriptorSamples());
        ObjectNode host = node.putObject("hostCliff");
        host.put("relationKind", plan.hostCliff().relationKind());
        host.put("hostCliffFeatureId", plan.hostCliff().hostCliffFeatureId());
        host.put("seawardFace", plan.hostCliff().seawardFace().name());
        host.set("hostAabb", volumeAabbTree(plan.hostCliff().hostAabb()));
        ObjectNode lobe = node.putObject("lobe");
        lobe.set("center", volumeVec3Tree(plan.lobe().center()));
        lobe.set("halfExtentsMillionths", volumeVec3Tree(plan.lobe().halfExtentsMillionths()));
        lobe.put("cornerRadiusMillionths", plan.lobe().cornerRadiusMillionths());
        ObjectNode recess = node.putObject("recess");
        recess.set("center", volumeVec3Tree(plan.recess().center()));
        recess.set("halfExtentsMillionths", volumeVec3Tree(plan.recess().halfExtentsMillionths()));
        recess.put("cornerRadiusMillionths", plan.recess().cornerRadiusMillionths());
        node.set("aabb", volumeAabbTree(plan.aabb()));
        ObjectNode sdf = node.putObject("sdfPlanBinding");
        sdf.put("bindingVersion", plan.sdfPlanBinding().bindingVersion());
        sdf.put("sourceArtifactChecksum", plan.sdfPlanBinding().sourceArtifactChecksum());
        sdf.put("bindingContractVersion", plan.sdfPlanBinding().bindingContractVersion());
        ObjectNode csg = node.putObject("csgPlanBinding");
        csg.put("bindingVersion", plan.csgPlanBinding().bindingVersion());
        csg.put("sourceArtifactChecksum", plan.csgPlanBinding().sourceArtifactChecksum());
        csg.put("bindingContractVersion", plan.csgPlanBinding().bindingContractVersion());
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        budget.put("maximumDescriptorSamples", plan.budget().maximumDescriptorSamples());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private NaturalArchPlanV2 parseNaturalArchPlan(JsonNode node) {
        JsonNode kernel = node.path("kernel");
        JsonNode mass = node.path("mass");
        JsonNode opening = node.path("opening");
        JsonNode sdf = node.path("sdfPlanBinding");
        JsonNode csg = node.path("csgPlanBinding");
        JsonNode budget = node.path("budget");
        return new NaturalArchPlanV2(
                node.path("planVersion").intValue(),
                node.path("naturalArchContractVersion").textValue(),
                node.path("featureId").textValue(),
                new NaturalArchPlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("minimumPierBlocks").intValue(),
                        kernel.path("minimumCrownBlocks").intValue(),
                        kernel.path("minimumClearanceSamples").intValue(),
                        kernel.path("minimumSpanBlocks").intValue(),
                        kernel.path("maximumHalfExtentMillionths").longValue(),
                        kernel.path("maximumDescriptorSamples").intValue()),
                Enum.valueOf(NaturalArchPlanV2.PassageAxis.class, node.path("passageAxis").textValue()),
                new NaturalArchPlanV2.MassSpec(
                        parseVolumeVec3(mass.path("center")),
                        parseVolumeVec3(mass.path("halfExtentsMillionths")),
                        mass.path("cornerRadiusMillionths").longValue()),
                new NaturalArchPlanV2.OpeningSpec(
                        parseVolumeVec3(opening.path("center")),
                        parseVolumeVec3(opening.path("halfExtentsMillionths")),
                        opening.path("cornerRadiusMillionths").longValue()),
                parseVolumeAabb(node.path("aabb")),
                new NaturalArchPlanV2.ArtifactBinding(
                        sdf.path("bindingVersion").intValue(),
                        sdf.path("sourceArtifactChecksum").textValue(),
                        sdf.path("bindingContractVersion").textValue()),
                new NaturalArchPlanV2.ArtifactBinding(
                        csg.path("bindingVersion").intValue(),
                        csg.path("sourceArtifactChecksum").textValue(),
                        csg.path("bindingContractVersion").textValue()),
                new NaturalArchPlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue(),
                        budget.path("maximumDescriptorSamples").intValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode naturalArchPlanTree(NaturalArchPlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("naturalArchContractVersion", plan.naturalArchContractVersion());
        node.put("featureId", plan.featureId());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("minimumPierBlocks", plan.kernel().minimumPierBlocks());
        kernel.put("minimumCrownBlocks", plan.kernel().minimumCrownBlocks());
        kernel.put("minimumClearanceSamples", plan.kernel().minimumClearanceSamples());
        kernel.put("minimumSpanBlocks", plan.kernel().minimumSpanBlocks());
        kernel.put("maximumHalfExtentMillionths", plan.kernel().maximumHalfExtentMillionths());
        kernel.put("maximumDescriptorSamples", plan.kernel().maximumDescriptorSamples());
        node.put("passageAxis", plan.passageAxis().name());
        ObjectNode mass = node.putObject("mass");
        mass.set("center", volumeVec3Tree(plan.mass().center()));
        mass.set("halfExtentsMillionths", volumeVec3Tree(plan.mass().halfExtentsMillionths()));
        mass.put("cornerRadiusMillionths", plan.mass().cornerRadiusMillionths());
        ObjectNode opening = node.putObject("opening");
        opening.set("center", volumeVec3Tree(plan.opening().center()));
        opening.set("halfExtentsMillionths", volumeVec3Tree(plan.opening().halfExtentsMillionths()));
        opening.put("cornerRadiusMillionths", plan.opening().cornerRadiusMillionths());
        node.set("aabb", volumeAabbTree(plan.aabb()));
        ObjectNode sdf = node.putObject("sdfPlanBinding");
        sdf.put("bindingVersion", plan.sdfPlanBinding().bindingVersion());
        sdf.put("sourceArtifactChecksum", plan.sdfPlanBinding().sourceArtifactChecksum());
        sdf.put("bindingContractVersion", plan.sdfPlanBinding().bindingContractVersion());
        ObjectNode csg = node.putObject("csgPlanBinding");
        csg.put("bindingVersion", plan.csgPlanBinding().bindingVersion());
        csg.put("sourceArtifactChecksum", plan.csgPlanBinding().sourceArtifactChecksum());
        csg.put("bindingContractVersion", plan.csgPlanBinding().bindingContractVersion());
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        budget.put("maximumDescriptorSamples", plan.budget().maximumDescriptorSamples());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private SkyIslandGroupPlanV2 parseSkyIslandGroupPlan(JsonNode node) {
        JsonNode kernel = node.path("kernel");
        JsonNode sdf = node.path("sdfPlanBinding");
        JsonNode csg = node.path("csgPlanBinding");
        JsonNode budget = node.path("budget");
        List<SkyIslandGroupPlanV2.IslandComponent> components = new ArrayList<>();
        for (JsonNode component : node.path("components")) {
            JsonNode lobe = component.path("lobe");
            JsonNode underside = component.path("underside");
            components.add(new SkyIslandGroupPlanV2.IslandComponent(
                    component.path("componentId").textValue(),
                    new SkyIslandGroupPlanV2.BoxSpec(
                            parseVolumeVec3(lobe.path("center")),
                            parseVolumeVec3(lobe.path("halfExtentsMillionths")),
                            lobe.path("cornerRadiusMillionths").longValue()),
                    new SkyIslandGroupPlanV2.BoxSpec(
                            parseVolumeVec3(underside.path("center")),
                            parseVolumeVec3(underside.path("halfExtentsMillionths")),
                            underside.path("cornerRadiusMillionths").longValue())));
        }
        return new SkyIslandGroupPlanV2(
                node.path("planVersion").intValue(),
                node.path("skyIslandGroupContractVersion").textValue(),
                node.path("featureId").textValue(),
                new SkyIslandGroupPlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("minimumComponentCount").intValue(),
                        kernel.path("maximumComponentCount").intValue(),
                        kernel.path("minimumGroundClearanceBlocks").intValue(),
                        kernel.path("minimumInterIslandGapBlocks").intValue(),
                        kernel.path("minimumThicknessBlocks").intValue(),
                        kernel.path("supportFreeAllowed").booleanValue(),
                        kernel.path("maximumHalfExtentMillionths").longValue(),
                        kernel.path("maximumDescriptorSamples").intValue()),
                node.path("groundReferenceYBlocks").intValue(),
                node.path("minimumAllowedYBlocks").intValue(),
                node.path("maximumAllowedYBlocks").intValue(),
                components,
                parseVolumeAabb(node.path("aabb")),
                new SkyIslandGroupPlanV2.ArtifactBinding(
                        sdf.path("bindingVersion").intValue(),
                        sdf.path("sourceArtifactChecksum").textValue(),
                        sdf.path("bindingContractVersion").textValue()),
                new SkyIslandGroupPlanV2.ArtifactBinding(
                        csg.path("bindingVersion").intValue(),
                        csg.path("sourceArtifactChecksum").textValue(),
                        csg.path("bindingContractVersion").textValue()),
                new SkyIslandGroupPlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue(),
                        budget.path("maximumDescriptorSamples").intValue(),
                        budget.path("maximumComponents").intValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode skyIslandGroupPlanTree(SkyIslandGroupPlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("skyIslandGroupContractVersion", plan.skyIslandGroupContractVersion());
        node.put("featureId", plan.featureId());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("minimumComponentCount", plan.kernel().minimumComponentCount());
        kernel.put("maximumComponentCount", plan.kernel().maximumComponentCount());
        kernel.put("minimumGroundClearanceBlocks", plan.kernel().minimumGroundClearanceBlocks());
        kernel.put("minimumInterIslandGapBlocks", plan.kernel().minimumInterIslandGapBlocks());
        kernel.put("minimumThicknessBlocks", plan.kernel().minimumThicknessBlocks());
        kernel.put("supportFreeAllowed", plan.kernel().supportFreeAllowed());
        kernel.put("maximumHalfExtentMillionths", plan.kernel().maximumHalfExtentMillionths());
        kernel.put("maximumDescriptorSamples", plan.kernel().maximumDescriptorSamples());
        node.put("groundReferenceYBlocks", plan.groundReferenceYBlocks());
        node.put("minimumAllowedYBlocks", plan.minimumAllowedYBlocks());
        node.put("maximumAllowedYBlocks", plan.maximumAllowedYBlocks());
        ArrayNode components = node.putArray("components");
        for (SkyIslandGroupPlanV2.IslandComponent component : plan.components()) {
            ObjectNode entry = components.addObject();
            entry.put("componentId", component.componentId());
            ObjectNode lobe = entry.putObject("lobe");
            lobe.set("center", volumeVec3Tree(component.lobe().center()));
            lobe.set("halfExtentsMillionths", volumeVec3Tree(component.lobe().halfExtentsMillionths()));
            lobe.put("cornerRadiusMillionths", component.lobe().cornerRadiusMillionths());
            ObjectNode underside = entry.putObject("underside");
            underside.set("center", volumeVec3Tree(component.underside().center()));
            underside.set("halfExtentsMillionths",
                    volumeVec3Tree(component.underside().halfExtentsMillionths()));
            underside.put("cornerRadiusMillionths", component.underside().cornerRadiusMillionths());
        }
        node.set("aabb", volumeAabbTree(plan.aabb()));
        ObjectNode sdf = node.putObject("sdfPlanBinding");
        sdf.put("bindingVersion", plan.sdfPlanBinding().bindingVersion());
        sdf.put("sourceArtifactChecksum", plan.sdfPlanBinding().sourceArtifactChecksum());
        sdf.put("bindingContractVersion", plan.sdfPlanBinding().bindingContractVersion());
        ObjectNode csg = node.putObject("csgPlanBinding");
        csg.put("bindingVersion", plan.csgPlanBinding().bindingVersion());
        csg.put("sourceArtifactChecksum", plan.csgPlanBinding().sourceArtifactChecksum());
        csg.put("bindingContractVersion", plan.csgPlanBinding().bindingContractVersion());
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        budget.put("maximumDescriptorSamples", plan.budget().maximumDescriptorSamples());
        budget.put("maximumComponents", plan.budget().maximumComponents());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private WaterfallVolumePlanV2 parseWaterfallVolumePlan(JsonNode node) {
        JsonNode kernel = node.path("kernel");
        JsonNode fallNode = node.path("fallNode");
        JsonNode behind = node.path("behindFall");
        JsonNode plunge = node.path("plungePool");
        JsonNode sdf = node.path("sdfPlanBinding");
        JsonNode csg = node.path("csgPlanBinding");
        JsonNode budget = node.path("budget");
        return new WaterfallVolumePlanV2(
                node.path("planVersion").intValue(),
                node.path("waterfallVolumeContractVersion").textValue(),
                node.path("featureId").textValue(),
                new WaterfallVolumePlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("minimumColumnSamples").intValue(),
                        kernel.path("minimumPoolSamples").intValue(),
                        kernel.path("minimumBehindClearanceSamples").intValue(),
                        kernel.path("maximumRadiusMillionths").longValue(),
                        kernel.path("maximumDescriptorSamples").intValue()),
                new WaterfallVolumePlanV2.FallNodeBinding(
                        fallNode.path("relationKind").textValue(),
                        fallNode.path("fallNodeId").textValue(),
                        fallNode.path("waterfallFeatureId").textValue(),
                        fallNode.path("sourceGeometryChecksum").textValue()),
                parseVolumeVec3(node.path("lipCenter")),
                parseVolumeVec3(node.path("baseCenter")),
                node.path("columnRadiusMillionths").longValue(),
                new WaterfallVolumePlanV2.BehindFallSpec(
                        parseVolumeVec3(behind.path("center")),
                        parseVolumeVec3(behind.path("halfExtentsMillionths")),
                        behind.path("cornerRadiusMillionths").longValue()),
                new WaterfallVolumePlanV2.PlungePoolSpec(
                        parseVolumeVec3(plunge.path("center")),
                        plunge.path("radiusMillionths").longValue(),
                        plunge.path("waterSurfaceYBlocks").intValue()),
                node.path("fluidBodyId").textValue(),
                parseVolumeAabb(node.path("aabb")),
                new WaterfallVolumePlanV2.ArtifactBinding(
                        sdf.path("bindingVersion").intValue(),
                        sdf.path("sourceArtifactChecksum").textValue(),
                        sdf.path("bindingContractVersion").textValue()),
                new WaterfallVolumePlanV2.ArtifactBinding(
                        csg.path("bindingVersion").intValue(),
                        csg.path("sourceArtifactChecksum").textValue(),
                        csg.path("bindingContractVersion").textValue()),
                new WaterfallVolumePlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue(),
                        budget.path("maximumDescriptorSamples").intValue(),
                        budget.path("maximumFluidIntervalsPerColumn").intValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode waterfallVolumePlanTree(WaterfallVolumePlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("waterfallVolumeContractVersion", plan.waterfallVolumeContractVersion());
        node.put("featureId", plan.featureId());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("minimumColumnSamples", plan.kernel().minimumColumnSamples());
        kernel.put("minimumPoolSamples", plan.kernel().minimumPoolSamples());
        kernel.put("minimumBehindClearanceSamples", plan.kernel().minimumBehindClearanceSamples());
        kernel.put("maximumRadiusMillionths", plan.kernel().maximumRadiusMillionths());
        kernel.put("maximumDescriptorSamples", plan.kernel().maximumDescriptorSamples());
        ObjectNode fallNode = node.putObject("fallNode");
        fallNode.put("relationKind", plan.fallNode().relationKind());
        fallNode.put("fallNodeId", plan.fallNode().fallNodeId());
        fallNode.put("waterfallFeatureId", plan.fallNode().waterfallFeatureId());
        fallNode.put("sourceGeometryChecksum", plan.fallNode().sourceGeometryChecksum());
        node.set("lipCenter", volumeVec3Tree(plan.lipCenter()));
        node.set("baseCenter", volumeVec3Tree(plan.baseCenter()));
        node.put("columnRadiusMillionths", plan.columnRadiusMillionths());
        ObjectNode behind = node.putObject("behindFall");
        behind.set("center", volumeVec3Tree(plan.behindFall().center()));
        behind.set("halfExtentsMillionths", volumeVec3Tree(plan.behindFall().halfExtentsMillionths()));
        behind.put("cornerRadiusMillionths", plan.behindFall().cornerRadiusMillionths());
        ObjectNode plunge = node.putObject("plungePool");
        plunge.set("center", volumeVec3Tree(plan.plungePool().center()));
        plunge.put("radiusMillionths", plan.plungePool().radiusMillionths());
        plunge.put("waterSurfaceYBlocks", plan.plungePool().waterSurfaceYBlocks());
        node.put("fluidBodyId", plan.fluidBodyId());
        node.set("aabb", volumeAabbTree(plan.aabb()));
        ObjectNode sdf = node.putObject("sdfPlanBinding");
        sdf.put("bindingVersion", plan.sdfPlanBinding().bindingVersion());
        sdf.put("sourceArtifactChecksum", plan.sdfPlanBinding().sourceArtifactChecksum());
        sdf.put("bindingContractVersion", plan.sdfPlanBinding().bindingContractVersion());
        ObjectNode csg = node.putObject("csgPlanBinding");
        csg.put("bindingVersion", plan.csgPlanBinding().bindingVersion());
        csg.put("sourceArtifactChecksum", plan.csgPlanBinding().sourceArtifactChecksum());
        csg.put("bindingContractVersion", plan.csgPlanBinding().bindingContractVersion());
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        budget.put("maximumDescriptorSamples", plan.budget().maximumDescriptorSamples());
        budget.put("maximumFluidIntervalsPerColumn", plan.budget().maximumFluidIntervalsPerColumn());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private VolumeLocalEnvironmentPlanV2 parseVolumeLocalEnvironmentPlan(JsonNode node) {
        JsonNode kernel = node.path("kernel");
        JsonNode catalog = node.path("catalog");
        JsonNode binding = node.path("materialProfileBinding");
        JsonNode budget = node.path("budget");
        List<VolumeLocalEnvironmentPlanV2.HostVolumeBinding> hosts = new ArrayList<>();
        for (JsonNode host : node.path("hostBindings")) {
            hosts.add(new VolumeLocalEnvironmentPlanV2.HostVolumeBinding(
                    Enum.valueOf(VolumeLocalEnvironmentPlanV2.HostVolumeKind.class,
                            host.path("hostKind").textValue()),
                    host.path("featureId").textValue(),
                    host.path("sourceGeometryChecksum").textValue()));
        }
        List<VolumeLocalEnvironmentPlanV2.CatalogEntry> entries = new ArrayList<>();
        for (JsonNode entry : catalog.path("entries")) {
            entries.add(new VolumeLocalEnvironmentPlanV2.CatalogEntry(
                    Enum.valueOf(VolumeLocalEnvironmentPlanV2.LocalMaterialClass.class,
                            entry.path("kind").textValue()),
                    entry.path("classCode").intValue(),
                    entry.path("classId").textValue()));
        }
        List<VolumeLocalEnvironmentPlanV2.SurfaceProfileRule> profiles = new ArrayList<>();
        for (JsonNode profile : node.path("surfaceProfiles")) {
            profiles.add(new VolumeLocalEnvironmentPlanV2.SurfaceProfileRule(
                    profile.path("ruleOrder").intValue(),
                    profile.path("ruleId").textValue(),
                    Enum.valueOf(VolumeLocalEnvironmentPlanV2.HostVolumeKind.class,
                            profile.path("hostKind").textValue()),
                    Enum.valueOf(VolumeLocalEnvironmentPlanV2.VolumeSurfaceClass.class,
                            profile.path("surfaceClass").textValue()),
                    Enum.valueOf(VolumeLocalEnvironmentPlanV2.LocalMaterialClass.class,
                            profile.path("materialClass").textValue()),
                    profile.path("requiresWetness").booleanValue(),
                    profile.path("requiresSupport").booleanValue(),
                    profile.path("minimumWetnessMillionths").intValue(),
                    profile.path("minimumDripMillionths").intValue(),
                    profile.path("minimumShadeMillionths").intValue(),
                    Enum.valueOf(VolumeLocalEnvironmentPlanV2.LightExposureClass.class,
                            profile.path("maximumLightExposure").textValue())));
        }
        List<VolumeLocalEnvironmentPlanV2.SparsePlacementRule> sparse = new ArrayList<>();
        for (JsonNode rule : node.path("sparsePlacements")) {
            List<VolumeLocalEnvironmentPlanV2.VolumeSurfaceClass> allowed = new ArrayList<>();
            for (JsonNode surface : rule.path("allowedSurfaceClasses")) {
                allowed.add(Enum.valueOf(VolumeLocalEnvironmentPlanV2.VolumeSurfaceClass.class,
                        surface.textValue()));
            }
            sparse.add(new VolumeLocalEnvironmentPlanV2.SparsePlacementRule(
                    rule.path("ruleOrder").intValue(),
                    rule.path("ruleId").textValue(),
                    Enum.valueOf(VolumeLocalEnvironmentPlanV2.SparsePlacementKind.class,
                            rule.path("placementKind").textValue()),
                    Enum.valueOf(VolumeLocalEnvironmentPlanV2.HostVolumeKind.class,
                            rule.path("hostKind").textValue()),
                    allowed,
                    rule.path("requiresWetness").booleanValue(),
                    rule.path("requiresSupport").booleanValue(),
                    rule.path("minimumWetnessMillionths").intValue()));
        }
        return new VolumeLocalEnvironmentPlanV2(
                node.path("planVersion").intValue(),
                node.path("localEnvironmentContractVersion").textValue(),
                node.path("featureId").textValue(),
                new VolumeLocalEnvironmentPlanV2.Kernel(
                        kernel.path("kernelVersion").textValue(),
                        kernel.path("minimumWetnessMillionths").intValue(),
                        kernel.path("minimumDripMillionths").intValue(),
                        kernel.path("minimumShadeMillionths").intValue(),
                        kernel.path("maximumDescriptorSamples").intValue(),
                        kernel.path("maximumSparsePlacementsPerWindow").intValue()),
                hosts,
                new VolumeLocalEnvironmentPlanV2.Catalog(
                        catalog.path("catalogId").textValue(),
                        catalog.path("catalogContractVersion").textValue(),
                        entries),
                profiles,
                sparse,
                new VolumeLocalEnvironmentPlanV2.MaterialProfileBinding(
                        binding.path("bindingVersion").intValue(),
                        binding.path("sourceMaterialProfilePlanChecksum").textValue(),
                        binding.path("bindingContractVersion").textValue()),
                new VolumeLocalEnvironmentPlanV2.ResourceBudget(
                        budget.path("budgetVersion").textValue(),
                        budget.path("estimatedCanonicalBytes").longValue(),
                        budget.path("maximumCanonicalBytes").longValue(),
                        budget.path("maximumWorkingBytes").longValue(),
                        budget.path("maximumDescriptorSamples").intValue(),
                        budget.path("maximumSparsePlacementsPerWindow").intValue(),
                        budget.path("hostBindingCount").intValue(),
                        budget.path("surfaceProfileCount").intValue(),
                        budget.path("sparseRuleCount").intValue()),
                node.path("canonicalChecksum").textValue());
    }

    private ObjectNode volumeLocalEnvironmentPlanTree(VolumeLocalEnvironmentPlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("localEnvironmentContractVersion", plan.localEnvironmentContractVersion());
        node.put("featureId", plan.featureId());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("minimumWetnessMillionths", plan.kernel().minimumWetnessMillionths());
        kernel.put("minimumDripMillionths", plan.kernel().minimumDripMillionths());
        kernel.put("minimumShadeMillionths", plan.kernel().minimumShadeMillionths());
        kernel.put("maximumDescriptorSamples", plan.kernel().maximumDescriptorSamples());
        kernel.put("maximumSparsePlacementsPerWindow", plan.kernel().maximumSparsePlacementsPerWindow());
        ArrayNode hosts = node.putArray("hostBindings");
        for (VolumeLocalEnvironmentPlanV2.HostVolumeBinding host : plan.hostBindings()) {
            ObjectNode entry = hosts.addObject();
            entry.put("hostKind", host.hostKind().name());
            entry.put("featureId", host.featureId());
            entry.put("sourceGeometryChecksum", host.sourceGeometryChecksum());
        }
        ObjectNode catalog = node.putObject("catalog");
        catalog.put("catalogId", plan.catalog().catalogId());
        catalog.put("catalogContractVersion", plan.catalog().catalogContractVersion());
        ArrayNode entries = catalog.putArray("entries");
        for (VolumeLocalEnvironmentPlanV2.CatalogEntry entry : plan.catalog().entries()) {
            ObjectNode item = entries.addObject();
            item.put("kind", entry.kind().name());
            item.put("classCode", entry.classCode());
            item.put("classId", entry.classId());
        }
        ArrayNode profiles = node.putArray("surfaceProfiles");
        for (VolumeLocalEnvironmentPlanV2.SurfaceProfileRule rule : plan.surfaceProfiles()) {
            ObjectNode item = profiles.addObject();
            item.put("ruleOrder", rule.ruleOrder());
            item.put("ruleId", rule.ruleId());
            item.put("hostKind", rule.hostKind().name());
            item.put("surfaceClass", rule.surfaceClass().name());
            item.put("materialClass", rule.materialClass().name());
            item.put("requiresWetness", rule.requiresWetness());
            item.put("requiresSupport", rule.requiresSupport());
            item.put("minimumWetnessMillionths", rule.minimumWetnessMillionths());
            item.put("minimumDripMillionths", rule.minimumDripMillionths());
            item.put("minimumShadeMillionths", rule.minimumShadeMillionths());
            item.put("maximumLightExposure", rule.maximumLightExposure().name());
        }
        ArrayNode sparse = node.putArray("sparsePlacements");
        for (VolumeLocalEnvironmentPlanV2.SparsePlacementRule rule : plan.sparsePlacements()) {
            ObjectNode item = sparse.addObject();
            item.put("ruleOrder", rule.ruleOrder());
            item.put("ruleId", rule.ruleId());
            item.put("placementKind", rule.placementKind().name());
            item.put("hostKind", rule.hostKind().name());
            ArrayNode allowed = item.putArray("allowedSurfaceClasses");
            for (VolumeLocalEnvironmentPlanV2.VolumeSurfaceClass surface : rule.allowedSurfaceClasses()) {
                allowed.add(surface.name());
            }
            item.put("requiresWetness", rule.requiresWetness());
            item.put("requiresSupport", rule.requiresSupport());
            item.put("minimumWetnessMillionths", rule.minimumWetnessMillionths());
        }
        ObjectNode binding = node.putObject("materialProfileBinding");
        binding.put("bindingVersion", plan.materialProfileBinding().bindingVersion());
        binding.put("sourceMaterialProfilePlanChecksum",
                plan.materialProfileBinding().sourceMaterialProfilePlanChecksum());
        binding.put("bindingContractVersion", plan.materialProfileBinding().bindingContractVersion());
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        budget.put("maximumDescriptorSamples", plan.budget().maximumDescriptorSamples());
        budget.put("maximumSparsePlacementsPerWindow", plan.budget().maximumSparsePlacementsPerWindow());
        budget.put("hostBindingCount", plan.budget().hostBindingCount());
        budget.put("surfaceProfileCount", plan.budget().surfaceProfileCount());
        budget.put("sparseRuleCount", plan.budget().sparseRuleCount());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private VolumeSdfPrimitiveV2 parseVolumeSdfPrimitive(JsonNode node) {
        VolumeSdfPrimitiveV2.Kind kind = enumValue(node, "kind", VolumeSdfPrimitiveV2.Kind.class);
        String primitiveId = node.path("primitiveId").textValue();
        return switch (kind) {
            case SPHERE -> new VolumeSdfPrimitiveV2.Sphere(
                    primitiveId, parseVolumeVec3(node.path("center")),
                    node.path("radiusMillionths").longValue());
            case ELLIPSOID -> new VolumeSdfPrimitiveV2.Ellipsoid(
                    primitiveId, parseVolumeVec3(node.path("center")),
                    parseVolumeVec3(node.path("radiiMillionths")));
            case CAPSULE -> new VolumeSdfPrimitiveV2.Capsule(
                    primitiveId, parseVolumeVec3(node.path("pointA")),
                    parseVolumeVec3(node.path("pointB")),
                    node.path("radiusMillionths").longValue());
            case PLANE -> new VolumeSdfPrimitiveV2.Plane(
                    primitiveId, parseVolumeVec3(node.path("point")),
                    parseVolumeVec3(node.path("normalMillionths")),
                    parseVolumeAabb(node.path("clipAabb")));
            case ROUNDED_BOX -> new VolumeSdfPrimitiveV2.RoundedBox(
                    primitiveId, parseVolumeVec3(node.path("center")),
                    parseVolumeVec3(node.path("halfExtentsMillionths")),
                    node.path("cornerRadiusMillionths").longValue());
            case SWEPT_SPLINE -> {
                List<VolumeSdfVec3V2> points = new ArrayList<>();
                for (JsonNode point : node.path("controlPoints")) {
                    points.add(parseVolumeVec3(point));
                }
                yield new VolumeSdfPrimitiveV2.SweptSpline(
                        primitiveId, points, node.path("radiusMillionths").longValue());
            }
        };
    }

    private ObjectNode volumeSdfPrimitivePlanTree(VolumeSdfPrimitivePlanV2 plan) {
        ObjectNode node = mapper.createObjectNode();
        node.put("planVersion", plan.planVersion());
        node.put("primitiveContractVersion", plan.primitiveContractVersion());
        ObjectNode quantization = node.putObject("quantization");
        quantization.put("quantizationVersion", plan.quantization().quantizationVersion());
        quantization.put("fixedScale", plan.quantization().fixedScale());
        quantization.put("geometryScale", plan.quantization().geometryScale());
        ObjectNode kernel = node.putObject("kernel");
        kernel.put("kernelVersion", plan.kernel().kernelVersion());
        kernel.put("maximumSweptControlPoints", plan.kernel().maximumSweptControlPoints());
        kernel.put("maximumSampleOperationsPerPrimitive",
                plan.kernel().maximumSampleOperationsPerPrimitive());
        ArrayNode primitives = node.putArray("primitives");
        for (VolumeSdfPrimitiveV2 primitive : plan.primitives()) {
            primitives.add(volumeSdfPrimitiveTree(primitive));
        }
        ObjectNode budget = node.putObject("budget");
        budget.put("budgetVersion", plan.budget().budgetVersion());
        budget.put("maximumPrimitives", plan.budget().maximumPrimitives());
        budget.put("maximumSweptControlPoints", plan.budget().maximumSweptControlPoints());
        budget.put("estimatedCanonicalBytes", plan.budget().estimatedCanonicalBytes());
        budget.put("maximumCanonicalBytes", plan.budget().maximumCanonicalBytes());
        budget.put("maximumWorkingBytes", plan.budget().maximumWorkingBytes());
        node.put("canonicalChecksum", plan.canonicalChecksum());
        return node;
    }

    private ObjectNode volumeSdfPrimitiveTree(VolumeSdfPrimitiveV2 primitive) {
        ObjectNode node = mapper.createObjectNode();
        node.put("primitiveId", primitive.primitiveId());
        node.put("kind", primitive.kind().name());
        switch (primitive) {
            case VolumeSdfPrimitiveV2.Sphere sphere -> {
                node.set("center", volumeVec3Tree(sphere.center()));
                node.put("radiusMillionths", sphere.radiusMillionths());
            }
            case VolumeSdfPrimitiveV2.Ellipsoid ellipsoid -> {
                node.set("center", volumeVec3Tree(ellipsoid.center()));
                node.set("radiiMillionths", volumeVec3Tree(ellipsoid.radiiMillionths()));
            }
            case VolumeSdfPrimitiveV2.Capsule capsule -> {
                node.set("pointA", volumeVec3Tree(capsule.pointA()));
                node.set("pointB", volumeVec3Tree(capsule.pointB()));
                node.put("radiusMillionths", capsule.radiusMillionths());
            }
            case VolumeSdfPrimitiveV2.Plane plane -> {
                node.set("point", volumeVec3Tree(plane.point()));
                node.set("normalMillionths", volumeVec3Tree(plane.normalMillionths()));
                node.set("clipAabb", volumeAabbTree(plane.clipAabb()));
            }
            case VolumeSdfPrimitiveV2.RoundedBox box -> {
                node.set("center", volumeVec3Tree(box.center()));
                node.set("halfExtentsMillionths", volumeVec3Tree(box.halfExtentsMillionths()));
                node.put("cornerRadiusMillionths", box.cornerRadiusMillionths());
            }
            case VolumeSdfPrimitiveV2.SweptSpline spline -> {
                ArrayNode points = node.putArray("controlPoints");
                for (VolumeSdfVec3V2 point : spline.controlPoints()) {
                    points.add(volumeVec3Tree(point));
                }
                node.put("radiusMillionths", spline.radiusMillionths());
            }
        }
        return node;
    }

    private static VolumeSdfVec3V2 parseVolumeVec3(JsonNode node) {
        return new VolumeSdfVec3V2(
                node.path("xMillionths").longValue(),
                node.path("yMillionths").longValue(),
                node.path("zMillionths").longValue());
    }

    private static VolumeSdfAabbV2 parseVolumeAabb(JsonNode node) {
        return new VolumeSdfAabbV2(
                node.path("minXMillionths").longValue(),
                node.path("minYMillionths").longValue(),
                node.path("minZMillionths").longValue(),
                node.path("maxXMillionths").longValue(),
                node.path("maxYMillionths").longValue(),
                node.path("maxZMillionths").longValue());
    }

    private ObjectNode volumeVec3Tree(VolumeSdfVec3V2 vec) {
        ObjectNode node = mapper.createObjectNode();
        node.put("xMillionths", vec.xMillionths());
        node.put("yMillionths", vec.yMillionths());
        node.put("zMillionths", vec.zMillionths());
        return node;
    }

    private ObjectNode volumeAabbTree(VolumeSdfAabbV2 aabb) {
        ObjectNode node = mapper.createObjectNode();
        node.put("minXMillionths", aabb.minXMillionths());
        node.put("minYMillionths", aabb.minYMillionths());
        node.put("minZMillionths", aabb.minZMillionths());
        node.put("maxXMillionths", aabb.maxXMillionths());
        node.put("maxYMillionths", aabb.maxYMillionths());
        node.put("maxZMillionths", aabb.maxZMillionths());
        return node;
    }

    private JsonNode readTree(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(LandformDataCodec.MAX_DOCUMENT_BYTES + 1));
            if (bytes.length > LandformDataCodec.MAX_DOCUMENT_BYTES) throw new IOException("document exceeds limit: " + path);
            JsonNode node = mapper.readTree(bytes); if (node == null) throw new IOException("document is empty: " + path); return node;
        }
    }

    private JsonNode readTree(String input, String documentName) throws IOException {
        if (input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > LandformDataCodec.MAX_DOCUMENT_BYTES) throw new IOException("document exceeds limit: " + documentName);
        JsonNode node = mapper.readTree(input); if (node == null) throw new IOException("document is empty: " + documentName); return node;
    }
}
