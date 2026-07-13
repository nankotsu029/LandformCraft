package com.github.nankotsu029.landformcraft.format;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.ExportManifest;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.WorldBlueprint;
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

    public void writeWorldBlueprint(Path path, WorldBlueprint blueprint) throws IOException {
        writeValidatedJson(path, blueprint, "world-blueprint.schema.json");
    }

    public void writeExportManifest(Path path, ExportManifest manifest) throws IOException {
        writeValidatedJson(path, manifest, "export-manifest.schema.json");
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

    private static ObjectMapper configure(ObjectMapper mapper) {
        return mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
