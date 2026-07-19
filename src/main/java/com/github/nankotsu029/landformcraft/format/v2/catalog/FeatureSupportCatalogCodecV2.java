package com.github.nankotsu029.landformcraft.format.v2.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.BuiltInFeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeaturePrimaryRoleV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCapabilitiesV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportEntryV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportLevelV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Strict codec for the V2-6-18 Feature Support Catalog. */
public final class FeatureSupportCatalogCodecV2 {
    private static final String CATALOG_SCHEMA = "feature-support-catalog-v2.schema.json";

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public FeatureSupportCatalogV2 builtInSealed() {
        return seal(BuiltInFeatureSupportCatalogV2.unsealed());
    }

    public FeatureSupportCatalogV2 read(Path path) throws IOException {
        JsonNode node = mapper.readTree(Files.readString(path));
        try {
            validator.validate(CATALOG_SCHEMA, path.toString(), node);
        } catch (StructuredDataValidationException exception) {
            // Keep the codec's single rejection contract: invalid catalogs are IllegalArgumentException.
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
        FeatureSupportCatalogV2 catalog = parse(node);
        verifyChecksum(catalog);
        return catalog;
    }

    public void write(Path path, FeatureSupportCatalogV2 catalog) throws IOException {
        verifyChecksum(catalog);
        Files.writeString(
                path,
                CanonicalJsonV2.string(toTree(catalog)),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    public FeatureSupportCatalogV2 seal(FeatureSupportCatalogV2 catalog) {
        FeatureSupportCatalogV2 placeholder = catalog.withCanonicalChecksum("0".repeat(64));
        String checksum = CanonicalJsonV2.checksum(toTree(placeholder));
        return catalog.withCanonicalChecksum(checksum);
    }

    public String checksum(FeatureSupportCatalogV2 catalog) {
        return CanonicalJsonV2.checksum(toTree(catalog.withCanonicalChecksum("0".repeat(64))));
    }

    public void verifyChecksum(FeatureSupportCatalogV2 catalog) {
        String actual = checksum(catalog);
        if (!actual.equals(catalog.canonicalChecksum())) {
            throw new IllegalArgumentException(
                    "feature support catalog checksum mismatch: expected "
                            + catalog.canonicalChecksum() + " actual " + actual);
        }
    }

    public JsonNode toTree(FeatureSupportCatalogV2 catalog) {
        ObjectNode root = mapper.createObjectNode();
        root.put("catalogVersion", catalog.catalogVersion());
        root.put("contractVersion", catalog.contractVersion());
        ObjectNode limit = root.putObject("placementDimensionLimit");
        limit.put("maximumWidth", catalog.placementDimensionLimit().maximumWidth());
        limit.put("maximumLength", catalog.placementDimensionLimit().maximumLength());
        ArrayNode entries = root.putArray("entries");
        for (FeatureSupportEntryV2 entry : catalog.entries()) {
            entries.add(entryTree(entry));
        }
        ArrayNode presets = root.putArray("availablePresets");
        catalog.availablePresets().forEach(presets::add);
        ArrayNode unsupported = root.putArray("unsupportedDiagnostics");
        catalog.unsupportedDiagnostics().forEach(unsupported::add);
        ArrayNode deferred = root.putArray("deferredDiagnostics");
        catalog.deferredDiagnostics().forEach(deferred::add);
        root.put("canonicalChecksum", catalog.canonicalChecksum());
        return root;
    }

    private ObjectNode entryTree(FeatureSupportEntryV2 entry) {
        ObjectNode node = mapper.createObjectNode();
        node.put("entryId", entry.entryId());
        node.put("profileId", entry.profileId());
        node.put("primaryRole", entry.primaryRole().name());
        ArrayNode usages = node.putArray("allowedUsages");
        entry.allowedUsages().forEach(role -> usages.add(role.name()));
        ObjectNode support = node.putObject("support");
        for (FeatureSupportCapabilityV2 capability : FeatureSupportCapabilityV2.values()) {
            support.put(capability.wireName(), entry.support().level(capability).name());
        }
        node.put("featureKindName", entry.featureKindName());
        node.put("lifecycleStatus", entry.lifecycleStatus().name());
        node.put("requiredReleaseCapability", entry.requiredReleaseCapability());
        node.put("requiredRuntime", entry.requiredRuntime());
        node.put("evidenceRef", entry.evidenceRef());
        ArrayNode notes = node.putArray("notes");
        entry.notes().forEach(notes::add);
        return node;
    }

    private FeatureSupportCatalogV2 parse(JsonNode root) {
        requireObject(root, "catalog root");
        rejectUnknown(root, List.of(
                "catalogVersion", "contractVersion", "placementDimensionLimit", "entries",
                "availablePresets", "unsupportedDiagnostics", "deferredDiagnostics",
                "canonicalChecksum"));
        int version = root.path("catalogVersion").asInt(-1);
        String contract = text(root, "contractVersion");
        JsonNode limitNode = root.path("placementDimensionLimit");
        requireObject(limitNode, "placementDimensionLimit");
        rejectUnknown(limitNode, List.of("maximumWidth", "maximumLength"));
        PlacementDimensionLimitV2 limit = new PlacementDimensionLimitV2(
                limitNode.path("maximumWidth").asInt(-1),
                limitNode.path("maximumLength").asInt(-1));
        List<FeatureSupportEntryV2> entries = new ArrayList<>();
        JsonNode entriesNode = root.path("entries");
        if (!entriesNode.isArray()) {
            throw new IllegalArgumentException("entries must be an array");
        }
        for (JsonNode entryNode : entriesNode) {
            entries.add(parseEntry(entryNode));
        }
        return new FeatureSupportCatalogV2(
                version,
                contract,
                limit,
                entries,
                stringList(root.path("availablePresets"), "availablePresets"),
                stringList(root.path("unsupportedDiagnostics"), "unsupportedDiagnostics"),
                stringList(root.path("deferredDiagnostics"), "deferredDiagnostics"),
                text(root, "canonicalChecksum"));
    }

    private FeatureSupportEntryV2 parseEntry(JsonNode node) {
        requireObject(node, "entry");
        rejectUnknown(node, List.of(
                "entryId", "profileId", "primaryRole", "allowedUsages", "support",
                "featureKindName", "lifecycleStatus", "requiredReleaseCapability",
                "requiredRuntime", "evidenceRef", "notes"));
        List<FeaturePrimaryRoleV2> usages = new ArrayList<>();
        for (JsonNode usage : node.path("allowedUsages")) {
            usages.add(FeaturePrimaryRoleV2.valueOf(usage.asText()));
        }
        return new FeatureSupportEntryV2(
                text(node, "entryId"),
                text(node, "profileId"),
                FeaturePrimaryRoleV2.valueOf(text(node, "primaryRole")),
                usages,
                parseSupport(node.path("support")),
                text(node, "featureKindName"),
                ModuleDescriptorV2.LifecycleStatus.valueOf(text(node, "lifecycleStatus")),
                text(node, "requiredReleaseCapability"),
                text(node, "requiredRuntime"),
                text(node, "evidenceRef"),
                stringList(node.path("notes"), "notes"));
    }

    private FeatureSupportCapabilitiesV2 parseSupport(JsonNode node) {
        requireObject(node, "support");
        for (String field : iterableFieldNames(node)) {
            FeatureSupportCapabilityV2.requireKnown(field);
        }
        for (FeatureSupportCapabilityV2 capability : FeatureSupportCapabilityV2.values()) {
            if (!node.has(capability.wireName())) {
                throw new IllegalArgumentException("support missing " + capability.wireName());
            }
        }
        return new FeatureSupportCapabilitiesV2(
                level(node, FeatureSupportCapabilityV2.INTENT_COMPILE),
                level(node, FeatureSupportCapabilityV2.OFFLINE_GENERATE),
                level(node, FeatureSupportCapabilityV2.VALIDATION),
                level(node, FeatureSupportCapabilityV2.PREVIEW),
                level(node, FeatureSupportCapabilityV2.EXPORT),
                level(node, FeatureSupportCapabilityV2.STANDALONE_USAGE),
                level(node, FeatureSupportCapabilityV2.CHILD_PLAN_USAGE),
                level(node, FeatureSupportCapabilityV2.VOLUME_OVERLAY_USAGE),
                level(node, FeatureSupportCapabilityV2.PAPER_APPLY),
                level(node, FeatureSupportCapabilityV2.POST_APPLY_VALIDATION),
                level(node, FeatureSupportCapabilityV2.SNAPSHOT),
                level(node, FeatureSupportCapabilityV2.ROLLBACK),
                level(node, FeatureSupportCapabilityV2.RESTART_RECOVERY));
    }

    private static FeatureSupportLevelV2 level(JsonNode node, FeatureSupportCapabilityV2 capability) {
        return FeatureSupportLevelV2.valueOf(text(node, capability.wireName()));
    }

    private static List<String> stringList(JsonNode node, String field) {
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode child : node) {
            values.add(child.asText());
        }
        return values;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.asText();
    }

    private static void requireObject(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
    }

    private static void rejectUnknown(JsonNode node, List<String> allowed) {
        for (String field : iterableFieldNames(node)) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("unknown field: " + field);
            }
        }
    }

    private static Iterable<String> iterableFieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        names.sort(String::compareTo);
        return names;
    }
}
