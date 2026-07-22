package com.github.nankotsu029.landformcraft.format.v2.migration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.CanonicalTerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.CanonicalFeatureMigrationReportV2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Exact, schema-independent codec for the ADR-approved migration report contract. */
public final class CanonicalFeatureMigrationReportCodecV2 {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "migrationContractVersion", "sourceArtifactIdentity", "sourceProjection",
            "targetProjection", "sourceCanonicalChecksum", "targetCanonicalChecksum",
            "status", "features", "diagnostics");
    private static final Set<String> FEATURE_FIELDS = Set.of(
            "sourceFeatureId", "sourceKind", "targetOwnerId", "targetDisposition",
            "legacySeedBinding", "diagnostic");
    private static final Set<String> SEED_FIELDS = Set.of(
            "sourceKind", "derivationVersion", "seedNamespace", "moduleId",
            "moduleVersion", "generatorVersion");

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public String canonical(CanonicalFeatureMigrationReportV2 report) {
        return CanonicalJsonV2.string(tree(report));
    }

    public CanonicalFeatureMigrationReportV2 read(Path path) throws IOException {
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(Math.toIntExact(LandformDataCodec.MAX_DOCUMENT_BYTES + 1));
        }
        if (bytes.length > LandformDataCodec.MAX_DOCUMENT_BYTES) {
            throw new IOException("canonical feature migration report exceeds document budget");
        }
        return read(new String(bytes, StandardCharsets.UTF_8), path.toString());
    }

    public CanonicalFeatureMigrationReportV2 read(String input, String documentName) throws IOException {
        JsonNode raw = mapper.readTree(input);
        if (raw == null || !raw.isObject()) {
            throw new IOException("canonical feature migration report must be an object: " + documentName);
        }
        ObjectNode root = (ObjectNode) raw;
        requireExactFields(root, ROOT_FIELDS, "migration report");
        List<CanonicalFeatureMigrationReportV2.FeatureMigration> features = new ArrayList<>();
        if (!root.path("features").isArray()) throw new IOException("migration report features must be an array");
        for (JsonNode rawFeature : root.path("features")) {
            if (!rawFeature.isObject()) throw new IOException("migration report feature must be an object");
            ObjectNode feature = (ObjectNode) rawFeature;
            requireExactFields(feature, FEATURE_FIELDS, "migration report feature");
            JsonNode rawSeed = feature.path("legacySeedBinding");
            if (!rawSeed.isObject()) throw new IOException("migration report seed binding must be an object");
            ObjectNode seed = (ObjectNode) rawSeed;
            requireExactFields(seed, SEED_FIELDS, "legacy seed binding");
            CanonicalTerrainIntentV2.LegacySeedBinding binding = seed(seed);
            features.add(new CanonicalFeatureMigrationReportV2.FeatureMigration(
                    text(feature, "sourceFeatureId"), enumValue(feature, "sourceKind", TerrainIntentV2.FeatureKind.class),
                    text(feature, "targetOwnerId"), text(feature, "targetDisposition"), binding,
                    text(feature, "diagnostic")));
        }
        List<String> diagnostics = new ArrayList<>();
        if (!root.path("diagnostics").isArray()) {
            throw new IOException("migration report diagnostics must be an array");
        }
        for (JsonNode value : root.path("diagnostics")) {
            if (!value.isTextual()) throw new IOException("migration diagnostic must be text");
            diagnostics.add(value.textValue());
        }
        return new CanonicalFeatureMigrationReportV2(
                text(root, "migrationContractVersion"), text(root, "sourceArtifactIdentity"),
                enumValue(root, "sourceProjection", CanonicalTerrainIntentV2.FeatureProjection.class),
                enumValue(root, "targetProjection", CanonicalTerrainIntentV2.FeatureProjection.class),
                text(root, "sourceCanonicalChecksum"), text(root, "targetCanonicalChecksum"),
                enumValue(root, "status", CanonicalFeatureMigrationReportV2.Status.class),
                features, diagnostics);
    }

    private ObjectNode tree(CanonicalFeatureMigrationReportV2 report) {
        ObjectNode root = mapper.createObjectNode();
        root.put("migrationContractVersion", report.migrationContractVersion());
        root.put("sourceArtifactIdentity", report.sourceArtifactIdentity());
        root.put("sourceProjection", report.sourceProjection().name());
        root.put("targetProjection", report.targetProjection().name());
        root.put("sourceCanonicalChecksum", report.sourceCanonicalChecksum());
        root.put("targetCanonicalChecksum", report.targetCanonicalChecksum());
        root.put("status", report.status().name());
        ArrayNode features = root.putArray("features");
        for (CanonicalFeatureMigrationReportV2.FeatureMigration feature : report.features()) {
            ObjectNode item = features.addObject();
            item.put("sourceFeatureId", feature.sourceFeatureId());
            item.put("sourceKind", feature.sourceKind().name());
            item.put("targetOwnerId", feature.targetOwnerId());
            item.put("targetDisposition", feature.targetDisposition());
            item.set("legacySeedBinding", seedTree(feature.legacySeedBinding()));
            item.put("diagnostic", feature.diagnostic());
        }
        ArrayNode diagnostics = root.putArray("diagnostics");
        report.diagnostics().forEach(diagnostics::add);
        return root;
    }

    private ObjectNode seedTree(CanonicalTerrainIntentV2.LegacySeedBinding seed) {
        ObjectNode result = mapper.createObjectNode();
        result.put("sourceKind", seed.sourceKind().name());
        result.put("derivationVersion", seed.derivationVersion());
        result.put("seedNamespace", seed.seedNamespace());
        result.put("moduleId", seed.moduleId());
        result.put("moduleVersion", seed.moduleVersion());
        result.put("generatorVersion", seed.generatorVersion());
        return result;
    }

    private static CanonicalTerrainIntentV2.LegacySeedBinding seed(JsonNode seed) throws IOException {
        return new CanonicalTerrainIntentV2.LegacySeedBinding(
                enumValue(seed, "sourceKind", TerrainIntentV2.FeatureKind.class),
                text(seed, "derivationVersion"), text(seed, "seedNamespace"),
                text(seed, "moduleId"), text(seed, "moduleVersion"), text(seed, "generatorVersion"));
    }

    private static void requireExactFields(ObjectNode node, Set<String> expected, String subject) throws IOException {
        Set<String> actual = new TreeSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new IOException(subject + " fields differ; expected=" + new TreeSet<>(expected) + ", actual=" + actual);
        }
    }

    private static String text(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isTextual()) throw new IOException("migration report field must be text: " + field);
        return value.textValue();
    }

    private static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type)
            throws IOException {
        try {
            return Enum.valueOf(type, text(node, field));
        } catch (IllegalArgumentException exception) {
            throw new IOException("unknown migration report enum: " + field, exception);
        }
    }
}
