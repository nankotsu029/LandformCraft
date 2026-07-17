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
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everySchemaIsValidJsonAndEveryStaticExampleLoads() throws Exception {
        try (var files = Files.list(Path.of("schemas"))) {
            for (Path schema : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonNode root = mapper.readTree(schema.toFile());
                assertTrue(root.isObject(), schema.toString());
                assertEquals("https://json-schema.org/draft/2020-12/schema", root.path("$schema").asText());
            }
        }
        LandformDataCodec codec = new LandformDataCodec();
        codec.readGenerationRequest(Path.of("examples/rocky-coast/request.yml"));
        codec.readTerrainIntent(Path.of("examples/rocky-coast/terrain-intent.json"));
        codec.readGenerationRequest(Path.of("examples/phase6-structures/request.yml"));
        codec.readTerrainIntent(Path.of("examples/phase6-structures/terrain-intent.json"));
        codec.readExportManifest(Path.of("examples/release-manifest.json"));
        codec.readPlacementJournal(Path.of("examples/placement-journal.json"));
        codec.readDesignAudit(Path.of("examples/design-audit.json"));
        codec.readGenerationJob(Path.of("examples/generation-job.json"));
        codec.readImageInputEvidence(Path.of("examples/image-input-evidence.json"));
        var v2 = new com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec();
        v2.readGenerationRequest(Path.of("examples/v2/diagnostic/azure-coast.request-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json"));
        v2.readGenerationRequest(Path.of("examples/v2/manual-constraint-island/request-v2.json"));
        v2.readTerrainIntent(Path.of("examples/v2/manual-constraint-island/terrain-intent-v2.json"));
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
        new com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyRoutingArtifactCodecV2().read(
                Path.of("examples/v2/hydrology/hydrology-routing-artifact-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyReconciliationArtifactCodecV2().read(
                Path.of("examples/v2/hydrology/hydrology-reconciliation-artifact-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2().read(
                Path.of("examples/v2/offline-tile/offline-tile-artifact-v2.json"));
        new com.github.nankotsu029.landformcraft.format.v2.release.ReleaseManifestCodecV2().read(
                Path.of("examples/v2/release-core/release-manifest-v2.json"));
        var assets = codec.readRequiredAssets(Path.of("examples/required-assets.json"));
        var placements = codec.readStructurePlacements(Path.of("examples/structure-placements.json"));
        var catalog = new com.github.nankotsu029.landformcraft.structure.BuiltInStructureAssetCatalog();
        assets.assets().forEach(asset -> assertEquals(
                catalog.requireById(asset.assetId()).semanticChecksum(), asset.semanticChecksum()));
        placements.structures().forEach(placement -> assertEquals(
                catalog.requireById(placement.assetId()).semanticChecksum(), placement.assetChecksum()));
    }

    @Test
    void schemaEnumsStayInSyncWithJavaEnums() throws Exception {
        JsonNode request = mapper.readTree(Path.of("schemas/generation-request.schema.json").toFile());
        assertEquals(enumNames(ReferenceImageRole.class),
                values(request.at("/$defs/referenceImage/properties/role/enum")));

        JsonNode job = mapper.readTree(Path.of("schemas/generation-job.schema.json").toFile());
        assertEquals(enumNames(GenerationStage.class), values(job.at("/properties/stage/enum")));

        JsonNode evidence = mapper.readTree(Path.of("schemas/image-input-evidence.schema.json").toFile());
        assertEquals(enumNames(ImageTransformation.class),
                values(evidence.at("/$defs/image/properties/transformations/items/enum")));

        JsonNode placement = mapper.readTree(Path.of("schemas/placement-journal.schema.json").toFile());
        assertEquals(enumNames(PlacementState.class), values(placement.at("/properties/state/enum")));
        assertEquals(enumNames(PlacementTileState.class), values(placement.at("/$defs/tile/properties/state/enum")));

        JsonNode intent = mapper.readTree(Path.of("schemas/terrain-intent.schema.json").toFile());
        assertEquals(enumNames(StructureType.class), values(intent.at("/$defs/structure/properties/type/enum")));
        JsonNode assets = mapper.readTree(Path.of("schemas/required-assets.schema.json").toFile());
        assertEquals(enumNames(StructureType.class), values(assets.at("/$defs/structureType/enum")));
        JsonNode structures = mapper.readTree(Path.of("schemas/structure-placements.schema.json").toFile());
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
    }

    private static Set<String> values(JsonNode values) {
        return StreamSupport.stream(values.spliterator(), false).map(JsonNode::asText).collect(Collectors.toSet());
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }
}
