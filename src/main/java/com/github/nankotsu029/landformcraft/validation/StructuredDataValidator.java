package com.github.nankotsu029.landformcraft.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Validates already parsed input against bundled immutable Draft 2020-12 schemas. */
public final class StructuredDataValidator {
    private static final String SCHEMA_ID_PREFIX =
            "https://github.com/nankotsu029/LandformCraft/schemas/v1/";
    private static final String V2_SCHEMA_ID_PREFIX =
            "https://github.com/nankotsu029/LandformCraft/schemas/v2/";
    private static final List<String> BUNDLED_SCHEMAS = List.of(
            "generation-request.schema.json",
            "terrain-intent.schema.json",
            "world-blueprint.schema.json",
            "export-manifest.schema.json",
            "placement-journal.schema.json",
            "placement-safety-state.schema.json",
            "snapshot-cleanup-plan.schema.json",
            "custom-asset-metadata.schema.json",
            "custom-asset-catalog-entry.schema.json",
            "design-audit.schema.json",
            "generation-job.schema.json",
            "image-input-evidence.schema.json",
            "required-assets.schema.json",
            "structure-placements.schema.json",
            "constraint-field-index-v2.schema.json",
            "coastal-preview-index-v2.schema.json",
            "coastal-validation-artifact-v2.schema.json",
            "hydrology-preview-index-v2.schema.json",
            "hydrology-validation-artifact-v2.schema.json",
            "offline-tile-artifact-v2.schema.json",
            "release-manifest-v2.schema.json",
            "generation-request-v2.schema.json",
            "terrain-intent-v2.schema.json",
            "geology-plan-v2.schema.json",
            "lithology-plan-v2.schema.json",
            "strata-plan-v2.schema.json",
            "climate-plan-v2.schema.json",
            "water-condition-plan-v2.schema.json",
            "hydrology-plan-v2.schema.json",
            "hydrology-routing-artifact-v2.schema.json",
            "hydrology-reconciliation-plan-v2.schema.json",
            "hydrology-reconciliation-artifact-v2.schema.json",
            "world-blueprint-v2.schema.json"
    );

    private final SchemaRegistry registry;
    private final Map<String, Schema> schemas = new ConcurrentHashMap<>();

    public StructuredDataValidator() {
        Map<String, String> bundled = new LinkedHashMap<>();
        for (String schemaFile : BUNDLED_SCHEMAS) {
            bundled.put(schemaId(schemaFile), readSchemaResource(schemaFile));
        }
        this.registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(bundled)
        );
    }

    public void validate(String schemaFile, String documentName, JsonNode document) {
        List<String> violations;
        try {
            violations = schema(schemaFile).validate(document).stream()
                    .sorted(Comparator.comparing(error -> error.getInstanceLocation().toString()))
                    .map(StructuredDataValidator::describe)
                    .toList();
        } catch (NumberFormatException exception) {
            // Keep extreme JSON numbers on the normal untrusted-input rejection path even when
            // the schema engine cannot construct its internal finite decimal representation.
            throw new StructuredDataValidationException(
                    documentName, List.of("/: numeric value is outside the supported finite range"));
        }
        if (!violations.isEmpty()) {
            throw new StructuredDataValidationException(documentName, violations);
        }
    }

    private Schema schema(String schemaFile) {
        return schemas.computeIfAbsent(schemaFile, this::loadSchema);
    }

    private Schema loadSchema(String schemaFile) {
        if (!BUNDLED_SCHEMAS.contains(schemaFile)) {
            throw new IllegalArgumentException("unknown bundled schema: " + schemaFile);
        }
        return registry.getSchema(SchemaLocation.of(schemaId(schemaFile)));
    }

    private static String schemaId(String schemaFile) {
        return (schemaFile.endsWith("-v2.schema.json") ? V2_SCHEMA_ID_PREFIX : SCHEMA_ID_PREFIX) + schemaFile;
    }

    private static String describe(Error error) {
        String location = error.getInstanceLocation().toString();
        return (location.isEmpty() ? "/" : location) + ": " + error.getMessage();
    }

    private static String readSchemaResource(String schemaFile) {
        String resource = "/schemas/" + schemaFile;
        try (InputStream input = StructuredDataValidator.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("schema resource not found: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read schema resource: " + resource, exception);
        }
    }
}
