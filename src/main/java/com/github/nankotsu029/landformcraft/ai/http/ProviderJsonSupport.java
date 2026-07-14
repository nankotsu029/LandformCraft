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
            JsonNode constant = object.remove("const");
            if (constant != null) {
                object.putArray("enum").add(constant);
            }
            if (!object.has("type")) {
                String type = enumJsonSchemaType(object.path("enum"));
                if (!type.isEmpty()) {
                    object.put("type", type);
                }
            }
            for (String field : new String[]{
                    "$schema", "$id", "title", "minLength", "maxLength", "pattern", "format",
                    "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
                    "minItems", "maxItems", "uniqueItems"
            }) {
                object.remove(field);
            }
            transformSchemaMap(object.path("properties"));
            transformSchemaMap(object.path("$defs"));
            transformSchemaNode(object.path("items"));
            transformSchemaArray(object.path("anyOf"));
        }
    }

    private static void transformSchemaMap(JsonNode schemas) {
        if (schemas.isObject()) {
            schemas.elements().forEachRemaining(ProviderJsonSupport::removeUnsupportedConstraints);
        }
    }

    private static void transformSchemaNode(JsonNode schema) {
        if (schema.isObject()) {
            removeUnsupportedConstraints(schema);
        }
    }

    private static void transformSchemaArray(JsonNode schemas) {
        if (schemas.isArray()) {
            schemas.elements().forEachRemaining(ProviderJsonSupport::removeUnsupportedConstraints);
        }
    }

    private static String enumJsonSchemaType(JsonNode values) {
        if (!values.isArray() || values.isEmpty()) {
            return "";
        }
        String type = "";
        for (JsonNode value : values) {
            String valueType = jsonSchemaType(value);
            if (valueType.isEmpty()) {
                return "";
            }
            if (type.isEmpty()) {
                type = valueType;
            } else if (!type.equals(valueType)) {
                if ((type.equals("integer") && valueType.equals("number"))
                        || (type.equals("number") && valueType.equals("integer"))) {
                    type = "number";
                } else {
                    return "";
                }
            }
        }
        return type;
    }

    private static String jsonSchemaType(JsonNode value) {
        if (value.isIntegralNumber()) {
            return "integer";
        }
        if (value.isNumber()) {
            return "number";
        }
        if (value.isTextual()) {
            return "string";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        if (value.isObject()) {
            return "object";
        }
        if (value.isArray()) {
            return "array";
        }
        return "";
    }
}
