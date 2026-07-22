package com.github.nankotsu029.landformcraft.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.model.GenerationStage;
import com.github.nankotsu029.landformcraft.model.ImageTransformation;
import com.github.nankotsu029.landformcraft.model.PlacementState;
import com.github.nankotsu029.landformcraft.model.PlacementTileState;
import com.github.nankotsu029.landformcraft.model.ReferenceImageRole;
import com.github.nankotsu029.landformcraft.model.StructureType;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AdvancedRiverLakeSplitContractV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AdvancedIslandReefCatalogContractV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.BarrierIslandPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AtollPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationAdvancedIslandReefValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.DryLandModifierContractV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.EscarpmentPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationLavaTubeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.LavaTubePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationOxbowLakeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationSpringValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OxbowLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SpringPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationEscarpmentPlateauValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlateauPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationGlacialDepositionValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationKarstHydrologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MoraineFieldPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AbyssalPlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SeamountPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationAdditionalMarineValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OutwashPlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PermafrostPlainProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SinkholePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstHydrologyGraphPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeTileCachePlanV2;
import org.junit.jupiter.api.Test;

import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everySchemaIsValidJsonAndEveryStaticExampleLoads() throws Exception {
        for (Path schema : schemaFiles()) {
            JsonNode root = mapper.readTree(schema.toFile());
            assertTrue(root.isObject(), schema.toString());
            assertEquals("https://json-schema.org/draft/2020-12/schema", root.path("$schema").asText());
        }
        LandformDataCodec codec = new LandformDataCodec();
        Path legacyFixtures = Path.of("src/main/resources/legacy/v1/fixtures");
        codec.readGenerationRequest(legacyFixtures.resolve("rocky-coast/request.yml"));
        codec.readGenerationRequest(legacyFixtures.resolve("mountain-stream/request.yml"));
        codec.readCustomAssetMetadata(Path.of("examples/custom-asset/metadata.json"));
        codec.readTerrainIntent(legacyFixtures.resolve(
                "azure-coast/results/38834796-183d-45ff-a567-6ee80cb9b243/terrain-intent.json"));
        codec.readDesignAudit(legacyFixtures.resolve(
                "azure-coast/results/38834796-183d-45ff-a567-6ee80cb9b243/audit.json"));
        codec.readImageInputEvidence(legacyFixtures.resolve(
                "azure-coast/results/38834796-183d-45ff-a567-6ee80cb9b243/image-evidence.json"));
        codec.readTerrainIntent(legacyFixtures.resolve("rocky-coast/terrain-intent.json"));
        codec.readGenerationRequest(Path.of("examples/phase6-structures/request.yml"));
        codec.readTerrainIntent(Path.of("examples/phase6-structures/terrain-intent.json"));
        codec.readExportManifest(legacyFixtures.resolve("release-manifest.json"));
        codec.readPlacementJournal(legacyFixtures.resolve("placement-journal.json"));
        codec.readDesignAudit(legacyFixtures.resolve("design-audit.json"));
        codec.readGenerationJob(legacyFixtures.resolve("generation-job.json"));
        codec.readImageInputEvidence(Path.of("examples/image-input-evidence.json"));
        var v2 = new com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec();
        v2.readGenerationRequest(Path.of("examples/v2/diagnostic/azure-coast.request-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json"));
        v2.readGenerationRequest(Path.of("examples/v2/diagnostic/coastal-fishing-map.request-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/diagnostic/coastal-fishing-map.terrain-intent-v2.json"));
        v2.readGenerationRequest(Path.of("examples/v2/diagnostic/harbor-cove-64.request-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/diagnostic/harbor-cove-64.terrain-intent-v2.json"));
        v2.readGenerationRequest(Path.of("examples/v2/manual-constraint-island/request-v2.json"));
        v2.readGenerationRequest(Path.of("examples/v2/diagnostic/oblique-multi-view.request-v2.json"));
        v2.readGenerationRequest(Path.of("examples/v2/diagnostic/medium-1024.request-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/manual-constraint-island/terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/diagnostic/scenarios/lush-cave.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/diagnostic/scenarios/sky-islands.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/diagnostic/scenarios/snowy-mountains.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/hydrology/canyon-river-skeleton.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of(
                "examples/v2/hydrology/volcanic-archipelago-skeleton.terrain-intent-v2.json"));
        v2.readHydrologyPlan(Path.of("examples/v2/hydrology/hydrology-plan-v2.json"));
        v2.readHydrologyReconciliationPlan(Path.of(
                "examples/v2/hydrology/hydrology-reconciliation-plan-v2.json"));
        v2.readTerrainIntent(Path.of(
                "examples/v2/hydrology/delta-distributary-fan.terrain-intent-v2.json"));
        v2.readGeologyPlan(Path.of("examples/v2/geology/geology-plan-v2.json"));
        v2.readLithologyPlan(Path.of("examples/v2/geology/lithology-plan-v2.json"));
        v2.readStrataPlan(Path.of("examples/v2/geology/strata-plan-v2.json"));
        v2.readClimatePlan(Path.of("examples/v2/climate/climate-plan-v2.json"));
        v2.readWaterConditionPlan(Path.of("examples/v2/environment/water-condition-plan-v2.json"));
        v2.readSurfaceFoundationPlan(Path.of("examples/v2/foundation/surface-foundation-plan-v2.json"));
        v2.readPlainPlan(Path.of("examples/v2/foundation/plain-plan-v2.json"));
        v2.readHillRangePlan(Path.of("examples/v2/foundation/hill-range-plan-v2.json"));
        v2.readMountainRangePlan(Path.of("examples/v2/foundation/mountain-range-plan-v2.json"));
        v2.readValleyPlan(Path.of("examples/v2/foundation/valley-plan-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/plain-hill-slice.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/mountain-valley-slice.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/general-river-slice.terrain-intent-v2.json"));
        v2.readRiverPlan(Path.of("examples/v2/foundation/river-plan-v2.json"));
        v2.readRiverPlan(Path.of("examples/v2/foundation/river-graph-roles-plan-v2.json"));
        v2.readWaterfallChainPlan(Path.of("examples/v2/foundation/waterfall-chain-plan-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/submarine-canyon-positive.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/submarine-canyon-out-of-host.terrain-intent-v2.json"));
        v2.readSubmarineCanyonPlan(Path.of("examples/v2/foundation/submarine-canyon-plan-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/cave-entrance-positive.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/cave-entrance-orphan.terrain-intent-v2.json"));
        v2.readCaveEntrancePlan(Path.of("examples/v2/foundation/cave-entrance-plan-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/lava-tube-positive.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/lava-tube-orphan.terrain-intent-v2.json"));
        v2.readLavaTubePlan(Path.of("examples/v2/foundation/lava-tube-plan-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/spring-positive.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/spring-orphan.terrain-intent-v2.json"));
        v2.readSpringPlan(Path.of("examples/v2/foundation/spring-plan-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/oxbow-lake-positive.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/oxbow-lake-orphan.terrain-intent-v2.json"));
        v2.readOxbowLakePlan(Path.of("examples/v2/foundation/oxbow-lake-plan-v2.json"));
        v2.readMacroLandWaterTopologyPlan(Path.of(
                "examples/v2/foundation/macro-land-water-topology-plan-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/valley-glacier-positive.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/ice-cap-positive.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/ice-sheet-positive.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/ice-fjord-composition.terrain-intent-v2.json"));
        v2.readGlacialIcePlan(Path.of("examples/v2/foundation/glacial-ice-plan-v2.json"));
        v2.readIceFjordPlan(Path.of("examples/v2/foundation/ice-fjord-plan-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/barrier-island-composition.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/atoll-composition.terrain-intent-v2.json"));
        v2.readBarrierIslandPlan(Path.of("examples/v2/foundation/barrier-island-plan-v2.json"));
        v2.readAtollPlan(Path.of("examples/v2/foundation/atoll-plan-v2.json"));
        v2.readAdvancedIslandReefCatalogContract(Path.of(
                "examples/v2/foundation/advanced-island-reef-catalog-contract-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2()
                .read(Path.of("examples/v2/catalog/feature-support-catalog-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.CanonicalTerrainIntentCodecV2()
                .read(Path.of("examples/v2/catalog/meandering-river.terrain-intent-v2-canonical.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/moraine-field-positive.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/outwash-plain-positive.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/permafrost-plain-profile.terrain-intent-v2.json"));
        v2.readMoraineFieldPlan(Path.of("examples/v2/foundation/moraine-field-plan-v2.json"));
        v2.readOutwashPlainPlan(Path.of("examples/v2/foundation/outwash-plain-plan-v2.json"));
        v2.readPermafrostPlainProfile(Path.of("examples/v2/foundation/permafrost-plain-profile-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/abyssal-plain-positive.terrain-intent-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/seamount-positive.terrain-intent-v2.json"));
        v2.readAbyssalPlainPlan(Path.of("examples/v2/foundation/abyssal-plain-plan-v2.json"));
        v2.readSeamountPlan(Path.of("examples/v2/foundation/seamount-plan-v2.json"));
        v2.readAdvancedRiverLakeSplitContract(Path.of(
                "examples/v2/foundation/advanced-river-lake-split-contract-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/plateau-escarpment-slice.terrain-intent-v2.json"));
        v2.readEscarpmentPlan(Path.of("examples/v2/foundation/escarpment-plan-v2.json"));
        v2.readPlateauPlan(Path.of("examples/v2/foundation/plateau-plan-v2.json"));
        v2.readDryLandModifierContract(Path.of("examples/v2/foundation/dry-land-modifier-contract-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/foundation/karst-hydrology-positive.terrain-intent-v2.json"));
        v2.readSinkholePlan(Path.of("examples/v2/foundation/sinkhole-plan-v2.json"));
        v2.readKarstSpringPlan(Path.of("examples/v2/foundation/karst-spring-plan-v2.json"));
        v2.readKarstHydrologyGraphPlan(Path.of("examples/v2/foundation/karst-hydrology-graph-plan-v2.json"));
        v2.readCenotePlan(Path.of("examples/v2/foundation/cenote-plan-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyRoutingArtifactCodecV2().read(
                Path.of("examples/v2/hydrology/hydrology-routing-artifact-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyReconciliationArtifactCodecV2().read(
                Path.of("examples/v2/hydrology/hydrology-reconciliation-artifact-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2().read(
                Path.of("examples/v2/offline-tile/offline-tile-artifact-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2().read(
                Path.of("examples/v2/volume/offline-volume-tile-artifact-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.release.ReleaseManifestCodecV2().read(
                Path.of("examples/v2/release-core/release-manifest-v2.json"));
        v2.readPlacementPlan(Path.of("examples/v2/placement/placement-plan-v2.json"));
        v2.readPlacementJournal(Path.of("examples/v2/placement/placement-journal-v2.json"));
        v2.readPlacementEnvelopePlan(Path.of("examples/v2/placement/placement-envelope-plan-v2.json"));
        v2.readPlacementReservationPlan(Path.of("examples/v2/placement/placement-reservation-plan-v2.json"));
        v2.readPlacementSafetyStateV2(Path.of("examples/v2/placement/placement-safety-state-v2.json"));
        v2.readPlacementSnapshotPlan(Path.of("examples/v2/placement/placement-snapshot-plan-v2.json"));
        v2.readPlacementContainmentPolicy(Path.of("examples/v2/placement/placement-containment-policy-v2.json"));
        v2.readPlacementContainmentEvidence(Path.of("examples/v2/placement/placement-containment-evidence-v2.json"));
        v2.readPlacementSettleVerifyPolicy(Path.of("examples/v2/placement/placement-settle-verify-policy-v2.json"));
        v2.readPlacementVerifyEvidence(Path.of("examples/v2/placement/placement-verify-evidence-v2.json"));
        v2.readPlacementUndoPlan(Path.of("examples/v2/placement/placement-undo-plan-v2.json"));
        v2.readPlacementRecoveryPlan(Path.of("examples/v2/placement/placement-recovery-plan-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.migration.LegacyMigrationReportCodecV2()
                .read(Path.of("examples/v2/migration/migration-report-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.job.ExportJobCodecV2()
                .read(Path.of("examples/v2/job/export-job-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/migration/mountain-stream.terrain-intent-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.design.DesignPackageCodecV2()
                .readAudit(Path.of("examples/v2/design/design-audit-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.design.DesignPackageCodecV2()
                .readDraftEvidence(Path.of("examples/v2/design/image-draft-evidence-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskDraftArtifactCodecV2()
                .read(Path.of("examples/v2/extract/extracted-mask-draft-v2.json"));
        new com.github.nankotsu029.landformcraft.preview.v2.ExtractedMaskDraftPreviewIndexCodecV2()
                .read(Path.of("examples/v2/extract/extracted-mask-draft-preview-index-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskPromotionRecordCodecV2()
                .read(Path.of("examples/v2/extract/extracted-mask-promotion-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuideDraftArtifactCodecV2()
                .read(Path.of("examples/v2/extract/extracted-height-guide-draft-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuidePromotionRecordCodecV2()
                .read(Path.of("examples/v2/extract/extracted-height-guide-promotion-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelDraftArtifactCodecV2()
                .read(Path.of("examples/v2/extract/extracted-zone-label-draft-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelPromotionRecordCodecV2()
                .read(Path.of("examples/v2/extract/extracted-zone-label-promotion-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.constraint.extract.MultiSourceReconciliationArtifactCodecV2()
                .read(Path.of("examples/v2/extract/multi-source-reconciliation-v2.json"));
        new com.github.nankotsu029.landformcraft.preview.v2.MultiSourceReconciliationPreviewIndexCodecV2()
                .read(Path.of("examples/v2/extract/multi-source-reconciliation-preview-index-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.validation.EnvironmentValidationArtifactCodecV2()
                .read(Path.of("examples/v2/environment/environment-validation-artifact-v2.json"));
        var structured = new StructuredDataValidator();
        structured.validate("operational-metrics-snapshot-v2.schema.json",
                "examples/v2/operations/operational-metrics-snapshot-v2.json",
                mapper.readTree(Path.of("examples/v2/operations/operational-metrics-snapshot-v2.json").toFile()));
        structured.validate("operational-audit-event-v2.schema.json",
                "examples/v2/operations/operational-audit-event-v2.json",
                mapper.readTree(Path.of("examples/v2/operations/operational-audit-event-v2.json").toFile()));
        structured.validate("release-2-retention-cleanup-plan-v2.schema.json",
                "examples/v2/operations/release-2-retention-cleanup-plan-v2.json",
                mapper.readTree(Path.of("examples/v2/operations/release-2-retention-cleanup-plan-v2.json").toFile()));
        var assets = codec.readRequiredAssets(Path.of("examples/required-assets.json"));
        var placements = codec.readStructurePlacements(legacyFixtures.resolve("structure-placements.json"));
        var catalog = new com.github.nankotsu029.landformcraft.structure.BuiltInStructureAssetCatalog();
        assets.assets().forEach(asset -> assertEquals(
                catalog.requireById(asset.assetId()).semanticChecksum(), asset.semanticChecksum()));
        placements.structures().forEach(placement -> assertEquals(
                catalog.requireById(placement.assetId()).semanticChecksum(), placement.assetChecksum()));
    }

    @Test
    void everySchemaDeclaresATitleAndDescription() throws Exception {
        List<String> undocumented = new ArrayList<>();
        for (Path schema : schemaFiles()) {
            JsonNode root = mapper.readTree(schema.toFile());
            if (root.path("title").asText("").isBlank() || root.path("description").asText("").isBlank()) {
                undocumented.add(schema.getFileName().toString());
            }
        }
        assertEquals(List.of(), undocumented, "schemas without a title or description");
    }

    /**
     * Inventory check: the schemas on disk, the schemas bundled into the validator and the schemas
     * packaged onto the classpath must be the same set, so a new schema cannot silently skip the
     * shared strict validation path.
     */
    @Test
    void schemaInventoryMatchesBundledAndPackagedSchemas() throws Exception {
        Set<String> onDisk = schemaFiles().stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> bundled = new TreeSet<>(StructuredDataValidator.bundledSchemaFiles());
        assertEquals(onDisk, bundled, "schemas/ and StructuredDataValidator.BUNDLED_SCHEMAS disagree");
        assertEquals(bundled.size(), StructuredDataValidator.bundledSchemaFiles().size(),
                "duplicate entry in StructuredDataValidator.BUNDLED_SCHEMAS");
        for (String schemaFile : bundled) {
            assertTrue(getClass().getResource("/schemas/" + schemaFile) != null,
                    "schema is not packaged onto the classpath: " + schemaFile);
        }
    }

    /**
     * Inventory check: every example document on disk must be named by at least one source, test or
     * documentation file, so example fixtures cannot drift out of the verified corpus.
     */
    @Test
    void everyExampleDocumentIsReferencedBySourceOrDocs() throws Exception {
        String corpus = readAll(List.of(Path.of("src"), Path.of("docs"), Path.of("README.md")));
        List<String> orphans = new ArrayList<>();
        for (Path example : exampleDocuments()) {
            if (!referenced(corpus, example)) {
                orphans.add(example.toString());
            }
        }
        assertEquals(List.of(), orphans, "example documents referenced by nothing");
    }

    private static boolean referenced(String corpus, Path example) {
        // Match any suffix of at least two path segments so relative fixture roots ("scenarios/x.json")
        // count, while a bare file name shared by unrelated fixtures does not.
        int count = example.getNameCount();
        for (int start = count - 2; start >= 0; start--) {
            String suffix = StreamSupport.stream(example.subpath(start, count).spliterator(), false)
                    .map(Path::toString)
                    .collect(Collectors.joining("/"));
            if (corpus.contains(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String readAll(List<Path> roots) throws Exception {
        StringBuilder corpus = new StringBuilder();
        for (Path root : roots) {
            if (Files.isRegularFile(root)) {
                corpus.append(Files.readString(root, StandardCharsets.UTF_8)).append('\n');
                continue;
            }
            try (var walk = Files.walk(root)) {
                for (Path path : walk.filter(Files::isRegularFile).filter(SchemaContractTest::isText).toList()) {
                    corpus.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
                }
            }
        }
        return corpus.toString();
    }

    private static boolean isText(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".md") || name.endsWith(".json")
                || name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".txt");
    }

    private static List<Path> schemaFiles() throws Exception {
        try (var files = Files.list(Path.of("schemas"))) {
            return files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static List<Path> exampleDocuments() throws Exception {
        try (var walk = Files.walk(Path.of("examples"))) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json") || path.toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void schemaEnumsStayInSyncWithJavaEnums() throws Exception {
        Path legacy = Path.of("src/main/resources/legacy/v1/contracts");
        JsonNode request = mapper.readTree(legacy.resolve("generation-request.schema.json").toFile());
        assertEquals(enumNames(ReferenceImageRole.class),
                values(request.at("/$defs/referenceImage/properties/role/enum")));

        JsonNode requestV2 = mapper.readTree(Path.of("schemas/generation-request-v2.schema.json").toFile());
        assertEquals(enumNames(GenerationRequestV2.ReferenceImageRole.class),
                values(requestV2.at("/$defs/referenceImage/properties/role/enum")));

        JsonNode job = mapper.readTree(legacy.resolve("generation-job.schema.json").toFile());
        assertEquals(enumNames(GenerationStage.class), values(job.at("/properties/stage/enum")));

        JsonNode evidence = mapper.readTree(Path.of("schemas/image-input-evidence.schema.json").toFile());
        assertEquals(enumNames(ImageTransformation.class),
                values(evidence.at("/$defs/image/properties/transformations/items/enum")));

        JsonNode placement = mapper.readTree(legacy.resolve("placement-journal.schema.json").toFile());
        assertEquals(enumNames(PlacementState.class), values(placement.at("/properties/state/enum")));
        assertEquals(enumNames(PlacementTileState.class), values(placement.at("/$defs/tile/properties/state/enum")));

        JsonNode intent = mapper.readTree(legacy.resolve("terrain-intent.schema.json").toFile());
        assertEquals(enumNames(StructureType.class), values(intent.at("/$defs/structure/properties/type/enum")));
        JsonNode assets = mapper.readTree(Path.of("schemas/required-assets.schema.json").toFile());
        assertEquals(enumNames(StructureType.class), values(assets.at("/$defs/structureType/enum")));
        JsonNode structures = mapper.readTree(legacy.resolve("structure-placements.schema.json").toFile());
        assertEquals(enumNames(StructureType.class), values(structures.at("/$defs/placement/properties/type/enum")));

        JsonNode hydrology = mapper.readTree(Path.of("schemas/hydrology-plan-v2.schema.json").toFile());
        assertEquals(enumNames(HydrologyPlanV2.NodeKind.class),
                values(hydrology.at("/$defs/node/properties/kind/enum")));
        assertEquals(enumNames(HydrologyPlanV2.ReachKind.class),
                values(hydrology.at("/$defs/reach/properties/kind/enum")));
        assertEquals(enumNames(HydrologyPlanV2.WaterBodyKind.class),
                values(hydrology.at("/$defs/waterBody/properties/kind/enum")));
        assertEquals(enumNames(HydrologyPlanV2.FieldSemantic.class),
                values(hydrology.at("/$defs/fieldBinding/properties/semantic/enum")));

        JsonNode geology = mapper.readTree(Path.of("schemas/geology-plan-v2.schema.json").toFile());
        assertEquals(enumNames(GeologyPlanV2.FieldSemantic.class),
                values(geology.at("/$defs/fieldBinding/properties/semantic/enum")));

        JsonNode foundation = mapper.readTree(Path.of("schemas/surface-foundation-plan-v2.schema.json").toFile());
        assertEquals(enumNames(SurfaceFoundationPlanV2.FieldSemantic.class),
                values(foundation.at("/$defs/fieldBinding/properties/semantic/enum")));
        assertEquals(Set.of("PLAIN", "HILL", "MOUNTAIN", "VALLEY", "RIVER", "WETLAND", "COAST", "CLIFF", "ISLAND", "CONE", "OCEAN", "SHELF", "SLOPE", "SUBMARINE_CANYON", "ENTRANCE"),
                values(foundation.at("/$defs/owner/properties/surfaceClass/enum")));
        assertEquals(SurfaceFoundationPlanV2.FIELD_CONTRACT_VERSION,
                foundation.at("/properties/fieldContractVersion/const").asText());
        assertEquals(SurfaceFoundationPlanV2.MODULE_ID,
                foundation.at("/properties/moduleId/const").asText());

        JsonNode lithology = mapper.readTree(Path.of("schemas/lithology-plan-v2.schema.json").toFile());
        assertEquals(enumNames(LithologyPlanV2.SemanticLithology.class),
                values(lithology.at("/$defs/kind/enum")));
        assertEquals(enumNames(LithologyPlanV2.ErosionResponse.class),
                values(lithology.at("/$defs/erosionResponse/enum")));

        JsonNode strata = mapper.readTree(Path.of("schemas/strata-plan-v2.schema.json").toFile());
        assertEquals("BOTTOM_TO_TOP", strata.at("/$defs/profile/properties/layerOrder/const").asText());
        assertEquals("UNIFORM_GEOLOGY_PRIOR",
                strata.at("/$defs/hydrologyHandoff/properties/sourcePriorKind/const").asText());
        assertEquals("SURFACE_EXPOSED_STRATA_SCALARS",
                strata.at("/$defs/hydrologyHandoff/properties/inputMode/const").asText());
        assertEquals("EXPLICIT_VERSION_TRANSITION",
                strata.at("/$defs/hydrologyHandoff/properties/transitionMode/const").asText());
        assertEquals(StrataPlanV2.SourcePriorKind.UNIFORM_GEOLOGY_PRIOR.name(),
                strata.at("/$defs/hydrologyHandoff/properties/sourcePriorKind/const").asText());
        assertEquals(StrataPlanV2.InputMode.SURFACE_EXPOSED_STRATA_SCALARS.name(),
                strata.at("/$defs/hydrologyHandoff/properties/inputMode/const").asText());
        assertEquals(StrataPlanV2.TransitionMode.EXPLICIT_VERSION_TRANSITION.name(),
                strata.at("/$defs/hydrologyHandoff/properties/transitionMode/const").asText());

        JsonNode climate = mapper.readTree(Path.of("schemas/climate-plan-v2.schema.json").toFile());
        assertEquals(enumNames(ClimatePlanV2.BaseClimatePreset.class),
                values(climate.at("/$defs/baseClimatePreset/enum")));
        assertEquals(enumNames(ClimatePlanV2.FieldSemantic.class),
                values(climate.at("/$defs/fieldBinding/properties/semantic/enum")));
        assertEquals(ClimatePlanV2.SourcePriorKind.CONSTANT_RUNOFF_PRIOR.name(),
                climate.at("/$defs/hydrologyHandoff/properties/sourcePriorKind/const").asText());
        assertEquals(ClimatePlanV2.TransitionMode.EXPLICIT_VERSION_TRANSITION.name(),
                climate.at("/$defs/hydrologyHandoff/properties/transitionMode/const").asText());

        JsonNode waterCondition = mapper.readTree(
                Path.of("schemas/water-condition-plan-v2.schema.json").toFile());
        assertEquals(enumNames(WaterConditionPlanV2.FieldSemantic.class),
                values(waterCondition.at("/$defs/fieldBinding/properties/semantic/enum")));
        assertEquals(WaterConditionPlanV2.Kernel.KERNEL_VERSION,
                waterCondition.at("/$defs/kernel/properties/kernelVersion/const").asText());

        JsonNode blueprint = mapper.readTree(Path.of("schemas/world-blueprint-v2.schema.json").toFile());
        assertEquals(enumNames(WorldBlueprintV2.FieldSemantic.class),
                values(blueprint.at("/$defs/field/properties/semantic/enum")));
        assertEquals(enumNames(FieldArtifactDescriptorV2.FieldSemantic.class),
                values(blueprint.at("/$defs/fieldArtifactDefinition/properties/semantic/enum")));

        JsonNode fieldIndex = mapper.readTree(Path.of("schemas/constraint-field-index-v2.schema.json").toFile());
        Set<String> fieldSemantics = values(fieldIndex.at("/$defs/definition/properties/semantic/enum"));
        assertTrue(fieldSemantics.containsAll(Set.of(
                FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_DIRECTION.name(),
                FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_ACCUMULATION.name())));
        JsonNode routing = mapper.readTree(Path.of("schemas/hydrology-routing-artifact-v2.schema.json").toFile());
        assertEquals(enumNames(HydrologyRoutingArtifactV2.OutletKind.class),
                values(routing.at("/$defs/outlet/properties/kind/enum")));

        JsonNode reconciliationPlan = mapper.readTree(
                Path.of("schemas/hydrology-reconciliation-plan-v2.schema.json").toFile());
        assertEquals(enumNames(HydrologyReconciliationPlanV2.VariableKind.class),
                values(reconciliationPlan.at("/$defs/variable/properties/kind/enum")));
        assertEquals(enumNames(HydrologyReconciliationPlanV2.ConstraintKind.class),
                values(reconciliationPlan.at("/$defs/constraint/properties/kind/enum")));
        assertEquals(enumNames(HydrologyReconciliationPlanV2.CorrectionPolicy.class),
                values(reconciliationPlan.at("/$defs/constraint/properties/correctionPolicy/enum")));

        JsonNode reconciliationArtifact = mapper.readTree(
                Path.of("schemas/hydrology-reconciliation-artifact-v2.schema.json").toFile());
        assertEquals(enumNames(HydrologyReconciliationArtifactV2.Status.class),
                values(reconciliationArtifact.at("/properties/status/enum")));
        assertEquals(enumNames(HydrologyReconciliationArtifactV2.FailureReason.class),
                values(reconciliationArtifact.at("/$defs/residual/properties/failureReason/enum")));

        JsonNode volumeSdf = mapper.readTree(
                Path.of("schemas/volume-sdf-primitive-plan-v2.schema.json").toFile());
        assertEquals("volume-sdf-primitive-contract-v1",
                volumeSdf.at("/properties/primitiveContractVersion/const").asText());
        assertEquals("volume-sdf-fixed-v1",
                volumeSdf.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertEquals(enumNames(VolumeSdfPrimitiveV2.Kind.class), Set.of(
                volumeSdf.at("/$defs/sphere/properties/kind/const").asText(),
                volumeSdf.at("/$defs/ellipsoid/properties/kind/const").asText(),
                volumeSdf.at("/$defs/capsule/properties/kind/const").asText(),
                volumeSdf.at("/$defs/plane/properties/kind/const").asText(),
                volumeSdf.at("/$defs/roundedBox/properties/kind/const").asText(),
                volumeSdf.at("/$defs/sweptSpline/properties/kind/const").asText()));

        JsonNode volumeCsg = mapper.readTree(
                Path.of("schemas/volume-csg-plan-v2.schema.json").toFile());
        assertEquals("volume-csg-contract-v1",
                volumeCsg.at("/properties/csgContractVersion/const").asText());
        assertEquals("volume-csg-ordered-v1",
                volumeCsg.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertEquals(enumNames(VolumeCsgPlanV2.OperationKind.class),
                values(volumeCsg.at("/$defs/operator/properties/kind/enum")));
        assertEquals(enumNames(VolumeCsgPlanV2.MaskMode.class),
                values(volumeCsg.at("/$defs/operator/properties/mask/enum")));

        JsonNode volumeAabb = mapper.readTree(
                Path.of("schemas/volume-aabb-index-plan-v2.schema.json").toFile());
        assertEquals("volume-aabb-index-contract-v1",
                volumeAabb.at("/properties/indexContractVersion/const").asText());
        assertEquals("volume-aabb-index-v1",
                volumeAabb.at("/$defs/kernel/properties/kernelVersion/const").asText());

        JsonNode volumeCache = mapper.readTree(
                Path.of("schemas/volume-tile-cache-plan-v2.schema.json").toFile());
        assertEquals("volume-tile-cache-contract-v1",
                volumeCache.at("/properties/cacheContractVersion/const").asText());
        assertEquals("volume-tile-cache-v1",
                volumeCache.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertEquals(Set.of(16, 32), Set.of(
                volumeCache.at("/$defs/kernel/properties/chunkEdgeBlocks/enum/0").asInt(),
                volumeCache.at("/$defs/kernel/properties/chunkEdgeBlocks/enum/1").asInt()));
        assertEquals(VolumeTileCachePlanV2.CACHE_CONTRACT_VERSION,
                volumeCache.at("/properties/cacheContractVersion/const").asText());

        JsonNode cave = mapper.readTree(
                Path.of("schemas/cave-network-plan-v2.schema.json").toFile());
        assertEquals("cave-network-contract-v1",
                cave.at("/properties/caveContractVersion/const").asText());
        assertEquals("cave-network-v1",
                cave.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2.NodeKind.class),
                values(cave.at("/$defs/node/properties/kind/enum")));

        JsonNode lush = mapper.readTree(
                Path.of("schemas/lush-cave-plan-v2.schema.json").toFile());
        assertEquals("lush-cave-contract-v1",
                lush.at("/properties/lushContractVersion/const").asText());
        assertEquals("lush-cave-v1",
                lush.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.volume.LushCavePlanV2.WetSurfaceClass.class),
                values(lush.at("/$defs/wetCondition/properties/eligibleSurfaceClasses/items/enum")));
        assertEquals("WITHIN", lush.at("/$defs/hostBinding/properties/relationKind/const").asText());
        assertEquals("REACHABLE_FROM",
                lush.at("/$defs/reachableFrom/properties/relationKind/const").asText());

        JsonNode lake = mapper.readTree(
                Path.of("schemas/underground-lake-plan-v2.schema.json").toFile());
        assertEquals("underground-lake-contract-v1",
                lake.at("/properties/lakeContractVersion/const").asText());
        assertEquals("underground-lake-v1",
                lake.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertEquals("WITHIN", lake.at("/$defs/hostBinding/properties/relationKind/const").asText());
        assertEquals("REACHABLE_FROM",
                lake.at("/$defs/caveAccess/properties/relationKind/const").asText());

        JsonNode seaCave = mapper.readTree(
                Path.of("schemas/sea-cave-plan-v2.schema.json").toFile());
        assertEquals("sea-cave-contract-v1",
                seaCave.at("/properties/seaCaveContractVersion/const").asText());
        assertEquals("sea-cave-v1",
                seaCave.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertEquals("CARVES_FLANK_OF",
                seaCave.at("/$defs/hostCliff/properties/relationKind/const").asText());
        assertEquals("EMPTIES_INTO",
                seaCave.at("/$defs/marineBoundary/properties/relationKind/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.volume.SeaCavePlanV2.CardinalFace.class),
                values(seaCave.at("/$defs/hostCliff/properties/seawardFace/enum")));

        JsonNode overhang = mapper.readTree(
                Path.of("schemas/overhang-plan-v2.schema.json").toFile());
        assertEquals("overhang-contract-v1",
                overhang.at("/properties/overhangContractVersion/const").asText());
        assertEquals("overhang-v1",
                overhang.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertEquals("SUPPORTS_FROM",
                overhang.at("/$defs/hostCliff/properties/relationKind/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.volume.OverhangPlanV2.CardinalFace.class),
                values(overhang.at("/$defs/hostCliff/properties/seawardFace/enum")));

        JsonNode naturalArch = mapper.readTree(
                Path.of("schemas/natural-arch-plan-v2.schema.json").toFile());
        assertEquals("natural-arch-contract-v1",
                naturalArch.at("/properties/naturalArchContractVersion/const").asText());
        assertEquals("natural-arch-v1",
                naturalArch.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.volume.NaturalArchPlanV2.PassageAxis.class),
                values(naturalArch.at("/properties/passageAxis/enum")));

        JsonNode skyIsland = mapper.readTree(
                Path.of("schemas/sky-island-group-plan-v2.schema.json").toFile());
        assertEquals("sky-island-group-contract-v1",
                skyIsland.at("/properties/skyIslandGroupContractVersion/const").asText());
        assertEquals("sky-island-group-v1",
                skyIsland.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertTrue(skyIsland.at("/$defs/kernel/properties/supportFreeAllowed/const").asBoolean());

        JsonNode waterfallVolume = mapper.readTree(
                Path.of("schemas/waterfall-volume-plan-v2.schema.json").toFile());
        assertEquals("waterfall-volume-contract-v1",
                waterfallVolume.at("/properties/waterfallVolumeContractVersion/const").asText());
        assertEquals("waterfall-volume-v1",
                waterfallVolume.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertEquals("BOUND_TO_FALL",
                waterfallVolume.at("/$defs/fallNode/properties/relationKind/const").asText());

        JsonNode localEnv = mapper.readTree(
                Path.of("schemas/volume-local-environment-plan-v2.schema.json").toFile());
        assertEquals("volume-local-environment-contract-v1",
                localEnv.at("/properties/localEnvironmentContractVersion/const").asText());
        assertEquals("volume-local-environment-v1",
                localEnv.at("/$defs/kernel/properties/kernelVersion/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.environment.local
                        .VolumeLocalEnvironmentPlanV2.HostVolumeKind.class),
                values(localEnv.at("/$defs/hostKind/enum")));
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.environment.local
                        .VolumeLocalEnvironmentPlanV2.VolumeSurfaceClass.class),
                values(localEnv.at("/$defs/surfaceClass/enum")));

        JsonNode volumeValidation = mapper.readTree(
                Path.of("schemas/volume-validation-artifact-v2.schema.json").toFile());
        assertEquals("v2.volume.validation",
                volumeValidation.at("/properties/validatorId/const").asText());
        assertEquals("volume-validator-v1",
                volumeValidation.at("/properties/validatorVersion/const").asText());

        JsonNode volumePreview = mapper.readTree(
                Path.of("schemas/volume-preview-index-v2.schema.json").toFile());
        assertEquals(Set.of("AABB_FOOTPRINT", "OPERATOR_ORDINAL", "Y_SLICE", "SOLID_FLUID", "SURFACE_CLASS"),
                values(volumePreview.at("/$defs/layerId/enum")));
        assertEquals("volume-diagnostic-palette-v1",
                volumePreview.at("/$defs/layer/properties/paletteId/const").asText());

        JsonNode placementPlan = mapper.readTree(
                Path.of("schemas/placement-plan-v2.schema.json").toFile());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2
                        .PLACEMENT_CONTRACT_VERSION,
                placementPlan.at("/properties/placementContractVersion/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementActorKindV2.class),
                values(placementPlan.at("/$defs/actor/properties/kind/enum")));
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementConfirmationActionV2.class),
                values(placementPlan.at(
                        "/$defs/reservationConfirmationBinding/properties/confirmationAction/enum")));
        assertEquals(Set.of("MINIMUM_CORNER"),
                Set.of(placementPlan.at("/$defs/target/properties/anchorKind/const").asText()));

        JsonNode placementJournal = mapper.readTree(
                Path.of("schemas/placement-journal-v2.schema.json").toFile());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2
                        .JOURNAL_CONTRACT_VERSION,
                placementJournal.at("/properties/journalContractVersion/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementJournalStateV2.class),
                values(placementJournal.at("/properties/state/enum")));
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementTileStateV2.class),
                values(placementJournal.at("/$defs/tileEntry/properties/state/enum")));

        JsonNode placementEnvelope = mapper.readTree(
                Path.of("schemas/placement-envelope-plan-v2.schema.json").toFile());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2
                        .ENVELOPE_CONTRACT_VERSION,
                placementEnvelope.at("/properties/envelopeContractVersion/const").asText());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2
                        .PhysicsPolicy.VERSION,
                placementEnvelope.at("/$defs/physicsPolicy/properties/policyVersion/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementPhysicsClassV2.class),
                values(placementEnvelope.at("/$defs/tileEnvelope/properties/physicsClasses/items/enum")));

        JsonNode placementReservation = mapper.readTree(
                Path.of("schemas/placement-reservation-plan-v2.schema.json").toFile());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2
                        .RESERVATION_CONTRACT_VERSION,
                placementReservation.at("/properties/reservationContractVersion/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementReservationOperationV2.class),
                values(placementReservation.at("/$defs/operation/enum")));
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementReservationLeaseStateV2.class),
                values(placementReservation.at("/$defs/leaseState/enum")));

        JsonNode placementSafety = mapper.readTree(
                Path.of("schemas/placement-safety-state-v2.schema.json").toFile());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSafetyStateV2
                        .SAFETY_CONTRACT_VERSION,
                placementSafety.at("/properties/safetyContractVersion/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementReservationLeaseStateV2.class),
                values(placementSafety.at("/$defs/regionReservation/properties/state/enum")));

        JsonNode containmentPolicy = mapper.readTree(
                Path.of("schemas/placement-containment-policy-v2.schema.json").toFile());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentPolicyV2
                        .POLICY_VERSION,
                containmentPolicy.at("/properties/policyVersion/const").asText());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentPolicyV2
                        .CATALOG_VERSION,
                containmentPolicy.at("/properties/catalogVersion/const").asText());

        JsonNode containmentEvidence = mapper.readTree(
                Path.of("schemas/placement-containment-evidence-v2.schema.json").toFile());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentEvidenceV2
                        .CONTAINMENT_CONTRACT_VERSION,
                containmentEvidence.at("/properties/containmentContractVersion/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementContainmentEvidenceV2.FindingRuleV2.class),
                values(containmentEvidence.at("/$defs/finding/properties/rule/enum")));
        assertEquals(Set.of("CONTAINED"),
                Set.of(containmentEvidence.at("/properties/verdict/const").asText()));

        JsonNode settleVerifyPolicy = mapper.readTree(
                Path.of("schemas/placement-settle-verify-policy-v2.schema.json").toFile());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2
                        .POLICY_VERSION,
                settleVerifyPolicy.at("/properties/policyVersion/const").asText());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2
                        .ResourceBudget.VERSION,
                settleVerifyPolicy.at("/$defs/budget/properties/budgetVersion/const").asText());

        JsonNode verifyEvidence = mapper.readTree(
                Path.of("schemas/placement-verify-evidence-v2.schema.json").toFile());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementVerifyEvidenceV2
                        .VERIFY_CONTRACT_VERSION,
                verifyEvidence.at("/properties/verifyContractVersion/const").asText());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementVerifyEvidenceV2.ContinuityRuleV2.class),
                values(verifyEvidence.at("/$defs/continuityMetric/properties/rule/enum")));
        assertEquals(Set.of("VERIFIED"),
                Set.of(verifyEvidence.at("/properties/verdict/const").asText()));

        JsonNode undoPlan = mapper.readTree(
                Path.of("schemas/placement-undo-plan-v2.schema.json").toFile());
        assertEquals(com.github.nankotsu029.landformcraft.model.v2.placement.PlacementUndoPlanV2
                        .UNDO_CONTRACT_VERSION,
                undoPlan.at("/properties/undoContractVersion/const").asText());
        assertEquals(Set.of("UNDO"),
                Set.of(undoPlan.at("/properties/confirmationAction/const").asText()));
        assertEquals(Set.of("KEEP_SNAPSHOTS_FOR_CLEANUP"),
                Set.of(undoPlan.at("/properties/retentionTransition/const").asText()));

        JsonNode macroTopology = mapper.readTree(
                Path.of("schemas/macro-land-water-topology-plan-v2.schema.json").toFile());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.foundation
                        .MacroLandWaterTopologyPlanV2.MacroRegionKind.class),
                values(macroTopology.at("/$defs/macroRegionKind/enum")));
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.foundation
                        .MacroLandWaterTopologyPlanV2.Medium.class),
                values(macroTopology.at("/$defs/medium/enum")));

        JsonNode riverPlan = mapper.readTree(Path.of("schemas/river-plan-v2.schema.json").toFile());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.foundation
                        .RiverPlanV2.NodeKind.class),
                values(riverPlan.at("/$defs/nodeKind/enum")));
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.foundation
                        .RiverPlanV2.ReachClass.class),
                values(riverPlan.at("/$defs/reachClass/enum")));
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.foundation
                        .RiverPlanV2.ChildKind.class),
                values(riverPlan.at("/$defs/childKind/enum")));
        JsonNode waterfallChain = mapper.readTree(
                Path.of("schemas/waterfall-chain-plan-v2.schema.json").toFile());
        assertEquals("waterfall-chain-preset-contract-v1",
                waterfallChain.at("/properties/contractVersion/const").asText());
        JsonNode glacialIce = mapper.readTree(
                Path.of("schemas/glacial-ice-plan-v2.schema.json").toFile());
        assertEquals(enumNames(com.github.nankotsu029.landformcraft.model.v2.foundation
                        .GlacialIcePlanV2.IceKind.class),
                values(glacialIce.at("/properties/iceKind/enum")));
        JsonNode iceFjord = mapper.readTree(
                Path.of("schemas/ice-fjord-plan-v2.schema.json").toFile());
        assertEquals("ice-fjord-preset-contract-v1",
                iceFjord.at("/properties/contractVersion/const").asText());
        JsonNode moraineField = mapper.readTree(
                Path.of("schemas/moraine-field-plan-v2.schema.json").toFile());
        assertEquals(MoraineFieldPlanV2.CONTRACT, "moraine-field-plan-contract-v1");
        assertEquals(MoraineFieldPlanV2.SEDIMENT_OWNERSHIP_FIELD_ID,
                moraineField.at("/properties/sedimentOwnershipFieldId/const").asText());
        JsonNode outwashPlain = mapper.readTree(
                Path.of("schemas/outwash-plain-plan-v2.schema.json").toFile());
        assertEquals(OutwashPlainPlanV2.SEDIMENT_OWNERSHIP_FIELD_ID,
                outwashPlain.at("/properties/sedimentOwnershipFieldId/const").asText());
        JsonNode permafrostProfile = mapper.readTree(
                Path.of("schemas/permafrost-plain-profile-v2.schema.json").toFile());
        assertEquals(PermafrostPlainProfileV2.CONTRACT_VERSION,
                permafrostProfile.at("/properties/contractVersion/const").asText());
        JsonNode glacialDepositionValidation = mapper.readTree(
                Path.of("schemas/foundation-glacial-deposition-validation-artifact-v2.schema.json").toFile());
        assertEquals(FoundationGlacialDepositionValidationArtifactV2.CONTRACT_VERSION,
                glacialDepositionValidation.at("/properties/contractVersion/const").asText());
        JsonNode abyssalPlain = mapper.readTree(
                Path.of("schemas/abyssal-plain-plan-v2.schema.json").toFile());
        assertEquals(AbyssalPlainPlanV2.CONTRACT, "abyssal-plain-plan-contract-v1");
        assertEquals(AbyssalPlainPlanV2.DEPTH_FIELD_ID,
                abyssalPlain.at("/properties/depthFieldId/const").asText());
        JsonNode seamountPlan = mapper.readTree(
                Path.of("schemas/seamount-plan-v2.schema.json").toFile());
        assertEquals(SeamountPlanV2.CONTRACT, "seamount-plan-contract-v1");
        assertEquals(SeamountPlanV2.RELIEF_FIELD_ID,
                seamountPlan.at("/properties/reliefFieldId/const").asText());
        JsonNode additionalMarineValidation = mapper.readTree(
                Path.of("schemas/foundation-additional-marine-validation-artifact-v2.schema.json").toFile());
        assertEquals(FoundationAdditionalMarineValidationArtifactV2.CONTRACT_VERSION,
                additionalMarineValidation.at("/properties/contractVersion/const").asText());
        JsonNode sinkholePlan = mapper.readTree(Path.of("schemas/sinkhole-plan-v2.schema.json").toFile());
        assertEquals(SinkholePlanV2.CONTRACT, "sinkhole-plan-contract-v1");
        assertEquals(SinkholePlanV2.MATERIAL_HANDOFF_ID,
                sinkholePlan.at("/properties/materialHandoffId/const").asText());
        JsonNode karstGraph = mapper.readTree(
                Path.of("schemas/karst-hydrology-graph-plan-v2.schema.json").toFile());
        assertEquals(KarstHydrologyGraphPlanV2.CONTRACT,
                karstGraph.at("/properties/contractVersion/const").asText());
        JsonNode karstValidation = mapper.readTree(
                Path.of("schemas/foundation-karst-hydrology-validation-artifact-v2.schema.json").toFile());
        assertEquals(FoundationKarstHydrologyValidationArtifactV2.CONTRACT_VERSION,
                karstValidation.at("/properties/contractVersion/const").asText());
        JsonNode riverLakeSplit = mapper.readTree(
                Path.of("schemas/advanced-river-lake-split-contract-v2.schema.json").toFile());
        assertEquals(AdvancedRiverLakeSplitContractV2.CONTRACT_VERSION,
                riverLakeSplit.at("/properties/contractVersion/const").asText());
        assertEquals(AdvancedRiverLakeSplitContractV2.DECISION_ID,
                riverLakeSplit.at("/properties/decisionId/const").asText());
        JsonNode escarpmentPlan = mapper.readTree(Path.of("schemas/escarpment-plan-v2.schema.json").toFile());
        assertEquals(EscarpmentPlanV2.CONTRACT, "escarpment-plan-contract-v1");
        assertEquals(EscarpmentPlanV2.MATERIAL_HANDOFF_FIELD_ID,
                escarpmentPlan.at("/properties/materialHandoffFieldId/const").asText());
        JsonNode plateauPlan = mapper.readTree(Path.of("schemas/plateau-plan-v2.schema.json").toFile());
        assertEquals(PlateauPlanV2.CONTRACT, "plateau-plan-contract-v1");
        assertEquals(PlateauPlanV2.CAP_MASK_FIELD_ID,
                plateauPlan.at("/properties/capMaskFieldId/const").asText());
        JsonNode dryLandContract = mapper.readTree(
                Path.of("schemas/dry-land-modifier-contract-v2.schema.json").toFile());
        assertEquals(DryLandModifierContractV2.CONTRACT_VERSION,
                dryLandContract.at("/properties/contractVersion/const").asText());
        assertEquals(DryLandModifierContractV2.DECISION_ID,
                dryLandContract.at("/properties/decisionId/const").asText());
        JsonNode escarpmentPlateauValidation = mapper.readTree(
                Path.of("schemas/foundation-escarpment-plateau-validation-artifact-v2.schema.json").toFile());
        assertEquals(FoundationEscarpmentPlateauValidationArtifactV2.CONTRACT_VERSION,
                escarpmentPlateauValidation.at("/properties/contractVersion/const").asText());
        JsonNode lavaTubePlan = mapper.readTree(Path.of("schemas/lava-tube-plan-v2.schema.json").toFile());
        assertEquals(LavaTubePlanV2.CONTRACT, "lava-tube-plan-contract-v1");
        assertEquals(LavaTubePlanV2.MATERIAL_PROFILE_ID,
                lavaTubePlan.at("/properties/materialProfileId/const").asText());
        JsonNode lavaTubeValidation = mapper.readTree(
                Path.of("schemas/foundation-lava-tube-validation-artifact-v2.schema.json").toFile());
        assertEquals(FoundationLavaTubeValidationArtifactV2.CONTRACT_VERSION,
                lavaTubeValidation.at("/properties/contractVersion/const").asText());
        JsonNode springPlan = mapper.readTree(Path.of("schemas/spring-plan-v2.schema.json").toFile());
        assertEquals(SpringPlanV2.CONTRACT, "spring-plan-contract-v1");
        assertEquals(SpringPlanV2.SOURCE_MASK_FIELD_ID,
                springPlan.at("/properties/sourceMaskFieldId/const").asText());
        assertEquals(SpringPlanV2.OUTFLOW_MASK_FIELD_ID,
                springPlan.at("/properties/outflowMaskFieldId/const").asText());
        assertEquals(SpringPlanV2.OWNERSHIP_FIELD_ID,
                springPlan.at("/properties/ownershipFieldId/const").asText());
        JsonNode springValidation = mapper.readTree(
                Path.of("schemas/foundation-spring-validation-artifact-v2.schema.json").toFile());
        assertEquals(FoundationSpringValidationArtifactV2.CONTRACT_VERSION,
                springValidation.at("/properties/contractVersion/const").asText());
        JsonNode oxbowPlan = mapper.readTree(Path.of("schemas/oxbow-lake-plan-v2.schema.json").toFile());
        assertEquals(OxbowLakePlanV2.CONTRACT, "oxbow-lake-plan-contract-v1");
        assertEquals(OxbowLakePlanV2.BASIN_MASK_FIELD_ID,
                oxbowPlan.at("/properties/basinMaskFieldId/const").asText());
        assertEquals(OxbowLakePlanV2.TERMINAL_POLICY,
                oxbowPlan.at("/properties/terminalPolicy/const").asText());
        JsonNode oxbowValidation = mapper.readTree(
                Path.of("schemas/foundation-oxbow-lake-validation-artifact-v2.schema.json").toFile());
        assertEquals(FoundationOxbowLakeValidationArtifactV2.CONTRACT_VERSION,
                oxbowValidation.at("/properties/contractVersion/const").asText());
        JsonNode barrierIslandPlan = mapper.readTree(
                Path.of("schemas/barrier-island-plan-v2.schema.json").toFile());
        assertEquals(BarrierIslandPlanV2.CONTRACT_VERSION,
                barrierIslandPlan.at("/properties/contractVersion/const").asText());
        JsonNode atollPlan = mapper.readTree(Path.of("schemas/atoll-plan-v2.schema.json").toFile());
        assertEquals(AtollPlanV2.CONTRACT_VERSION,
                atollPlan.at("/properties/contractVersion/const").asText());
        JsonNode islandReefCatalog = mapper.readTree(
                Path.of("schemas/advanced-island-reef-catalog-contract-v2.schema.json").toFile());
        assertEquals(AdvancedIslandReefCatalogContractV2.CONTRACT_VERSION,
                islandReefCatalog.at("/properties/contractVersion/const").asText());
        assertEquals(AdvancedIslandReefCatalogContractV2.DECISION_ID,
                islandReefCatalog.at("/properties/decisionId/const").asText());
        JsonNode islandReefValidation = mapper.readTree(
                Path.of("schemas/foundation-advanced-island-reef-validation-artifact-v2.schema.json").toFile());
        assertEquals(FoundationAdvancedIslandReefValidationArtifactV2.CONTRACT_VERSION,
                islandReefValidation.at("/properties/contractVersion/const").asText());
    }

    private static Set<String> values(JsonNode values) {
        return StreamSupport.stream(values.spliterator(), false).map(JsonNode::asText).collect(Collectors.toSet());
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }
}
