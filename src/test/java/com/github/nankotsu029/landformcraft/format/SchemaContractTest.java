package com.github.nankotsu029.landformcraft.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.model.GenerationStage;
import com.github.nankotsu029.landformcraft.model.ImageTransformation;
import com.github.nankotsu029.landformcraft.model.PlacementState;
import com.github.nankotsu029.landformcraft.model.PlacementTileState;
import com.github.nankotsu029.landformcraft.model.ReferenceImageRole;
import com.github.nankotsu029.landformcraft.model.StructureType;
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
    }

    private static Set<String> values(JsonNode values) {
        return StreamSupport.stream(values.spliterator(), false).map(JsonNode::asText).collect(Collectors.toSet());
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }
}
