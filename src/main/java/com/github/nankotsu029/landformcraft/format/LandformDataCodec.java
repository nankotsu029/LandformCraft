package com.github.nankotsu029.landformcraft.format;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.ExportManifest;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.WorldBlueprint;
import com.github.nankotsu029.landformcraft.model.PlacementJournal;
import com.github.nankotsu029.landformcraft.model.DesignAudit;
import com.github.nankotsu029.landformcraft.model.GenerationJobSnapshot;
import com.github.nankotsu029.landformcraft.model.ImageInputEvidence;
import com.github.nankotsu029.landformcraft.model.RequiredAssets;
import com.github.nankotsu029.landformcraft.model.StructurePlacementManifest;
import com.github.nankotsu029.landformcraft.model.PlacementSafetyState;
import com.github.nankotsu029.landformcraft.model.SnapshotCleanupPlan;
import com.github.nankotsu029.landformcraft.model.CustomAssetMetadata;
import com.github.nankotsu029.landformcraft.model.CustomAssetCatalogEntry;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Strict JSON/YAML boundary: duplicate keys, unknown fields, trailing data and schema violations are rejected. */
public final class LandformDataCodec {
    public static final long MAX_DOCUMENT_BYTES = 4L * 1024L * 1024L;

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;
    private final StructuredDataValidator validator;

    public LandformDataCodec() {
        this.validator = new StructuredDataValidator();
        this.jsonMapper = configure(new ObjectMapper(
                JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()
        ));
        this.yamlMapper = configure(new ObjectMapper(
                YAMLFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()
        ));
        this.yamlMapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
    }

    public GenerationRequest readGenerationRequest(Path path) throws IOException {
        JsonNode document = readTree(yamlMapper, path);
        validator.validate("generation-request.schema.json", path.toString(), document);
        return yamlMapper.treeToValue(document, GenerationRequest.class);
    }

    public TerrainIntent readTerrainIntent(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("terrain-intent.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, TerrainIntent.class);
    }

    public TerrainIntent readTerrainIntent(String input, String documentName) throws IOException {
        JsonNode document = readTree(jsonMapper, input, documentName);
        validator.validate("terrain-intent.schema.json", documentName, document);
        return jsonMapper.treeToValue(document, TerrainIntent.class);
    }

    public WorldBlueprint readWorldBlueprint(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("world-blueprint.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, WorldBlueprint.class);
    }

    public ExportManifest readExportManifest(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("export-manifest.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, ExportManifest.class);
    }

    public PlacementJournal readPlacementJournal(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("placement-journal.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, PlacementJournal.class);
    }

    /**
     * Reads a Phase 3 journal through the current strict schema without weakening the public contract.
     * Missing beta safety fields are assigned conservative legacy identities and unknown byte counts.
     */
    public PlacementJournal readPlacementJournalCompatible(Path path) throws IOException {
        JsonNode input = readTree(jsonMapper, path);
        if (!(input instanceof ObjectNode document) || !(document.get("plan") instanceof ObjectNode plan)) {
            validator.validate("placement-journal.schema.json", path.toString(), input);
            return jsonMapper.treeToValue(input, PlacementJournal.class);
        }
        ObjectNode actor = jsonMapper.createObjectNode();
        actor.put("kind", "SYSTEM");
        actor.put("id", "LEGACY");
        if (!plan.has("actor")) {
            plan.set("actor", actor.deepCopy());
        }
        if (!document.has("confirmationActor")) {
            document.set("confirmationActor", actor.deepCopy());
        }
        if (!document.has("confirmationCreatedAt")) {
            String action = document.path("confirmationAction").asText("NONE");
            document.put("confirmationCreatedAt", action.equals("NONE")
                    ? "1970-01-01T00:00:00Z"
                    : plan.path("createdAt").asText("1970-01-01T00:00:00Z"));
        }
        if (!document.has("reservedBytes")) {
            document.put("reservedBytes", 0L);
        }
        if (!document.has("snapshotBytesUsed")) {
            document.put("snapshotBytesUsed", 0L);
        }
        validator.validate("placement-journal.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, PlacementJournal.class);
    }

    public PlacementSafetyState readPlacementSafetyState(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("placement-safety-state.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, PlacementSafetyState.class);
    }

    public SnapshotCleanupPlan readSnapshotCleanupPlan(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("snapshot-cleanup-plan.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, SnapshotCleanupPlan.class);
    }

    public CustomAssetMetadata readCustomAssetMetadata(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("custom-asset-metadata.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, CustomAssetMetadata.class);
    }

    public CustomAssetCatalogEntry readCustomAssetCatalogEntry(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("custom-asset-catalog-entry.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, CustomAssetCatalogEntry.class);
    }

    public DesignAudit readDesignAudit(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("design-audit.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, DesignAudit.class);
    }

    public GenerationJobSnapshot readGenerationJob(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("generation-job.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, GenerationJobSnapshot.class);
    }

    public ImageInputEvidence readImageInputEvidence(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("image-input-evidence.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, ImageInputEvidence.class);
    }

    public RequiredAssets readRequiredAssets(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("required-assets.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, RequiredAssets.class);
    }

    public StructurePlacementManifest readStructurePlacements(Path path) throws IOException {
        JsonNode document = readTree(jsonMapper, path);
        validator.validate("structure-placements.schema.json", path.toString(), document);
        return jsonMapper.treeToValue(document, StructurePlacementManifest.class);
    }

    public void writeWorldBlueprint(Path path, WorldBlueprint blueprint) throws IOException {
        writeValidatedJson(path, blueprint, "world-blueprint.schema.json");
    }

    public void writeExportManifest(Path path, ExportManifest manifest) throws IOException {
        writeValidatedJson(path, manifest, "export-manifest.schema.json");
    }

    public void writePlacementJournal(Path path, PlacementJournal journal) throws IOException {
        writeValidatedJson(path, journal, "placement-journal.schema.json");
    }

    public void writePlacementSafetyState(Path path, PlacementSafetyState state) throws IOException {
        writeValidatedJson(path, state, "placement-safety-state.schema.json");
    }

    public void writeSnapshotCleanupPlan(Path path, SnapshotCleanupPlan plan) throws IOException {
        writeValidatedJson(path, plan, "snapshot-cleanup-plan.schema.json");
    }

    public void writeCustomAssetCatalogEntry(Path path, CustomAssetCatalogEntry entry) throws IOException {
        writeValidatedJson(path, entry, "custom-asset-catalog-entry.schema.json");
    }

    public void writeGenerationRequest(Path path, GenerationRequest request) throws IOException {
        JsonNode document = yamlMapper.valueToTree(request);
        validator.validate("generation-request.schema.json", path.toString(), document);
        writeYaml(path, request);
    }

    public void writeTerrainIntent(Path path, TerrainIntent intent) throws IOException {
        writeValidatedJson(path, intent, "terrain-intent.schema.json");
    }

    public void writeDesignAudit(Path path, DesignAudit audit) throws IOException {
        writeValidatedJson(path, audit, "design-audit.schema.json");
    }

    public void writeGenerationJob(Path path, GenerationJobSnapshot snapshot) throws IOException {
        writeValidatedJson(path, snapshot, "generation-job.schema.json");
    }

    public void writeImageInputEvidence(Path path, ImageInputEvidence evidence) throws IOException {
        writeValidatedJson(path, evidence, "image-input-evidence.schema.json");
    }

    public void writeRequiredAssets(Path path, RequiredAssets assets) throws IOException {
        writeValidatedJson(path, assets, "required-assets.schema.json");
    }

    public void writeStructurePlacements(Path path, StructurePlacementManifest placements) throws IOException {
        writeValidatedJson(path, placements, "structure-placements.schema.json");
    }

    public void writeJson(Path path, Object value) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(value, "value");
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(absolute.getParent(), "output path must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "lfc-" + absolute.getFileName(), ".tmp");
        try {
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            moveAtomically(temporary, absolute);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public String writeJsonString(Object value) throws IOException {
        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private void writeValidatedJson(Path path, Object value, String schemaFile) throws IOException {
        JsonNode document = jsonMapper.valueToTree(value);
        validator.validate(schemaFile, path.toString(), document);
        writeJson(path, value);
    }

    private void writeYaml(Path path, Object value) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(absolute.getParent(), "output path must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "lfc-" + absolute.getFileName(), ".tmp");
        try {
            yamlMapper.writeValue(temporary.toFile(), value);
            moveAtomically(temporary, absolute);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static ObjectMapper configure(ObjectMapper mapper) {
        mapper.registerModule(new JavaTimeModule());
        return mapper
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    private static JsonNode readTree(ObjectMapper mapper, Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        byte[] bytes;
        try (InputStream inputStream = Files.newInputStream(path)) {
            bytes = inputStream.readNBytes(Math.toIntExact(MAX_DOCUMENT_BYTES + 1L));
        }
        if (bytes.length > MAX_DOCUMENT_BYTES) {
            throw new IOException("document exceeds " + MAX_DOCUMENT_BYTES + " bytes: " + path);
        }
        String input = new String(bytes, StandardCharsets.UTF_8);
        JsonNode result = mapper.readTree(input);
        if (result == null) {
            throw new IOException("document is empty: " + path);
        }
        return result;
    }

    private static JsonNode readTree(ObjectMapper mapper, String input, String documentName) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(documentName, "documentName");
        if (input.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
            throw new IOException("document exceeds " + MAX_DOCUMENT_BYTES + " bytes: " + documentName);
        }
        JsonNode result = mapper.readTree(input);
        if (result == null) {
            throw new IOException("document is empty: " + documentName);
        }
        return result;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
