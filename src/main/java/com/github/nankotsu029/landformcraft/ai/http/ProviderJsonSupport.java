package com.github.nankotsu029.landformcraft.ai.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;

public final class ProviderJsonSupport {
    private ProviderJsonSupport() {
    }

    public static ObjectMapper strictMapper() {
        return new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public static JsonNode terrainIntentSchema(ObjectMapper mapper) {
        try (InputStream input = ProviderJsonSupport.class
                .getResourceAsStream("/schemas/terrain-intent.schema.json")) {
            if (input == null) {
                throw new IllegalStateException("terrain intent schema resource is missing");
            }
            JsonNode schema = mapper.readTree(input);
            if (schema == null || !schema.isObject()) {
                throw new IllegalStateException("terrain intent schema must be an object");
            }
            JsonNode compatible = schema.deepCopy();
            removeUnsupportedConstraints(compatible);
            return compatible;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load terrain intent schema", exception);
        }
    }

    private static void removeUnsupportedConstraints(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (String field : new String[]{
                    "$schema", "$id", "title", "minLength", "maxLength", "pattern", "format",
                    "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
                    "minItems", "maxItems", "uniqueItems"
            }) {
                object.remove(field);
            }
            object.elements().forEachRemaining(ProviderJsonSupport::removeUnsupportedConstraints);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(ProviderJsonSupport::removeUnsupportedConstraints);
        }
    }
}
