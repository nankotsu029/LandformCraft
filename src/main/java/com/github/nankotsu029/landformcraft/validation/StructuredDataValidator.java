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
import java.util.Set;
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
            "design-audit-v2.schema.json",
            "image-draft-evidence-v2.schema.json",
            "generation-job.schema.json",
            "image-input-evidence.schema.json",
            "required-assets.schema.json",
            "structure-placements.schema.json",
            "constraint-field-index-v2.schema.json",
            "coastal-preview-index-v2.schema.json",
            "coastal-validation-artifact-v2.schema.json",
            "hydrology-preview-index-v2.schema.json",
            "hydrology-validation-artifact-v2.schema.json",
            "environment-preview-index-v2.schema.json",
            "environment-validation-artifact-v2.schema.json",
            "volume-preview-index-v2.schema.json",
            "volume-validation-artifact-v2.schema.json",
            "offline-tile-artifact-v2.schema.json",
            "release-manifest-v2.schema.json",
            "generation-request-v2.schema.json",
            "terrain-intent-v2.schema.json",
            "geology-plan-v2.schema.json",
            "lithology-plan-v2.schema.json",
            "strata-plan-v2.schema.json",
            "climate-plan-v2.schema.json",
            "water-condition-plan-v2.schema.json",
            "snow-plan-v2.schema.json",
            "hydrology-plan-v2.schema.json",
            "hydrology-routing-artifact-v2.schema.json",
            "hydrology-reconciliation-plan-v2.schema.json",
            "hydrology-reconciliation-artifact-v2.schema.json",
            "material-profile-plan-v2.schema.json",
            "minecraft-palette-plan-v2.schema.json",
            "ecology-plan-v2.schema.json",
            "surface-foundation-plan-v2.schema.json",
            "plain-plan-v2.schema.json",
            "hill-range-plan-v2.schema.json",
            "valley-plan-v2.schema.json",
            "mountain-range-plan-v2.schema.json",
            "river-plan-v2.schema.json",
            "floodplain-plan-v2.schema.json",
            "marsh-plan-v2.schema.json",
            "rocky-coast-plan-v2.schema.json",
            "sea-cliff-plan-v2.schema.json",
            "single-island-plan-v2.schema.json",
            "archipelago-plan-v2.schema.json",
            "volcanic-cone-plan-v2.schema.json",
            "foundation-validation-artifact-v2.schema.json",
            "foundation-range-valley-validation-artifact-v2.schema.json",
            "foundation-river-validation-artifact-v2.schema.json",
            "foundation-floodplain-marsh-validation-artifact-v2.schema.json",
            "foundation-rocky-coast-cliff-validation-artifact-v2.schema.json",
            "foundation-single-island-validation-artifact-v2.schema.json",
            "foundation-archipelago-validation-artifact-v2.schema.json",
            "foundation-volcanic-cone-validation-artifact-v2.schema.json",
            "ocean-basin-plan-v2.schema.json",
            "continental-shelf-plan-v2.schema.json",
            "continental-slope-plan-v2.schema.json",
            "foundation-ocean-basin-validation-artifact-v2.schema.json",
            "foundation-continental-shelf-validation-artifact-v2.schema.json",
            "foundation-continental-slope-validation-artifact-v2.schema.json",
            "submarine-canyon-plan-v2.schema.json",
            "foundation-submarine-canyon-validation-artifact-v2.schema.json",
            "cave-entrance-plan-v2.schema.json",
            "foundation-cave-entrance-validation-artifact-v2.schema.json",
            "underground-river-plan-v2.schema.json",
            "foundation-underground-river-validation-artifact-v2.schema.json",
            "lava-tube-plan-v2.schema.json",
            "foundation-lava-tube-validation-artifact-v2.schema.json",
            "spring-plan-v2.schema.json",
            "foundation-spring-validation-artifact-v2.schema.json",
            "oxbow-lake-plan-v2.schema.json",
            "foundation-oxbow-lake-validation-artifact-v2.schema.json",
            "macro-land-water-topology-plan-v2.schema.json",
            "foundation-macro-land-water-topology-validation-artifact-v2.schema.json",
            "waterfall-chain-plan-v2.schema.json",
            "foundation-river-graph-roles-validation-artifact-v2.schema.json",
            "glacial-ice-plan-v2.schema.json",
            "foundation-glacial-ice-validation-artifact-v2.schema.json",
            "ice-fjord-plan-v2.schema.json",
            "barrier-island-plan-v2.schema.json",
            "atoll-plan-v2.schema.json",
            "advanced-island-reef-catalog-contract-v2.schema.json",
            "foundation-advanced-island-reef-validation-artifact-v2.schema.json",
            "moraine-field-plan-v2.schema.json",
            "outwash-plain-plan-v2.schema.json",
            "permafrost-plain-profile-v2.schema.json",
            "foundation-glacial-deposition-validation-artifact-v2.schema.json",
            "abyssal-plain-plan-v2.schema.json",
            "seamount-plan-v2.schema.json",
            "foundation-additional-marine-validation-artifact-v2.schema.json",
            "advanced-river-lake-split-contract-v2.schema.json",
            "escarpment-plan-v2.schema.json",
            "plateau-plan-v2.schema.json",
            "dry-land-modifier-contract-v2.schema.json",
            "foundation-escarpment-plateau-validation-artifact-v2.schema.json",
            "sinkhole-plan-v2.schema.json",
            "karst-spring-plan-v2.schema.json",
            "karst-hydrology-graph-plan-v2.schema.json",
            "cenote-plan-v2.schema.json",
            "foundation-karst-hydrology-validation-artifact-v2.schema.json",
            "foundation-preview-index-v2.schema.json",
            "feature-material-profile-plan-v2.schema.json",
            "volume-sdf-primitive-plan-v2.schema.json",
            "volume-csg-plan-v2.schema.json",
            "volume-aabb-index-plan-v2.schema.json",
            "volume-tile-cache-plan-v2.schema.json",
            "cave-network-plan-v2.schema.json",
            "lush-cave-plan-v2.schema.json",
            "underground-lake-plan-v2.schema.json",
            "sea-cave-plan-v2.schema.json",
            "overhang-plan-v2.schema.json",
            "natural-arch-plan-v2.schema.json",
            "sky-island-group-plan-v2.schema.json",
            "waterfall-volume-plan-v2.schema.json",
            "volume-local-environment-plan-v2.schema.json",
            "placement-plan-v2.schema.json",
            "placement-journal-v2.schema.json",
            "placement-envelope-plan-v2.schema.json",
            "placement-reservation-plan-v2.schema.json",
            "placement-safety-state-v2.schema.json",
            "placement-snapshot-plan-v2.schema.json",
            "placement-containment-policy-v2.schema.json",
            "placement-containment-evidence-v2.schema.json",
            "placement-settle-verify-policy-v2.schema.json",
            "placement-verify-evidence-v2.schema.json",
            "placement-undo-plan-v2.schema.json",
            "placement-recovery-plan-v2.schema.json",
            "operational-metrics-snapshot-v2.schema.json",
            "operational-audit-event-v2.schema.json",
            "release-2-retention-cleanup-plan-v2.schema.json",
            "feature-support-catalog-v2.schema.json",
            "generation-job-v2.schema.json",
            "migration-report-v2.schema.json",
            "world-blueprint-v2.schema.json"
    );
    private static final Set<String> LEGACY_V1_SCHEMAS = Set.of(
            "terrain-intent.schema.json",
            "world-blueprint.schema.json",
            "generation-request.schema.json",
            "generation-job.schema.json",
            "design-audit.schema.json",
            "export-manifest.schema.json",
            "placement-journal.schema.json",
            "placement-safety-state.schema.json",
            "snapshot-cleanup-plan.schema.json",
            "structure-placements.schema.json"
    );

    /**
     * Isolated resource root for v1 contracts (ADR 0035 D2b, R7).
     *
     * <p>{@code V2-12-06} removes the v1 schemas from the active {@code schemas/} inventory and keeps
     * an immutable copy here so the migration-only readers can still strict-read existing user
     * assets from the packaged JAR alone. The copy is staged by {@code V2-12-04} so the migration
     * path already resolves against it, and this loader prefers whichever copy exists.</p>
     */
    private static final String LEGACY_V1_CONTRACT_ROOT = "/legacy/v1/contracts/";

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

    /** Active public schema inventory. Legacy contracts are intentionally excluded (ADR 0035 R7). */
    public static List<String> bundledSchemaFiles() {
        return BUNDLED_SCHEMAS.stream()
                .filter(schema -> !LEGACY_V1_SCHEMAS.contains(schema))
                .toList();
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
        String active = readResourceOrNull("/schemas/" + schemaFile);
        if (active != null) {
            return active;
        }
        String legacy = readResourceOrNull(LEGACY_V1_CONTRACT_ROOT + schemaFile);
        if (legacy != null) {
            return legacy;
        }
        throw new IllegalStateException("schema resource not found: " + schemaFile);
    }

    private static String readResourceOrNull(String resource) {
        try (InputStream input = StructuredDataValidator.class.getResourceAsStream(resource)) {
            return input == null ? null : new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read schema resource: " + resource, exception);
        }
    }
}
