package com.github.nankotsu029.landformcraft.format.v2;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Strict codec for v2 diagnostic contracts. It does not publish a Release or invoke a generator. */
public final class LandformV2DataCodec {
    private static final String REQUEST_SCHEMA = "generation-request-v2.schema.json";
    private static final String INTENT_SCHEMA = "terrain-intent-v2.schema.json";
    private static final String BLUEPRINT_SCHEMA = "world-blueprint-v2.schema.json";
    private static final String CLIMATE_PLAN_SCHEMA = "climate-plan-v2.schema.json";
    private static final String WATER_CONDITION_PLAN_SCHEMA = "water-condition-plan-v2.schema.json";
    private static final String GEOLOGY_PLAN_SCHEMA = "geology-plan-v2.schema.json";
    private static final String LITHOLOGY_PLAN_SCHEMA = "lithology-plan-v2.schema.json";
    private static final String STRATA_PLAN_SCHEMA = "strata-plan-v2.schema.json";
    private static final String HYDROLOGY_PLAN_SCHEMA = "hydrology-plan-v2.schema.json";
    private static final String HYDROLOGY_RECONCILIATION_PLAN_SCHEMA =
            "hydrology-reconciliation-plan-v2.schema.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public GenerationRequestV2 readGenerationRequest(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(REQUEST_SCHEMA, path.toString(), node);
        return parseGenerationRequest(node);
    }

    public GenerationRequestV2 readGenerationRequest(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(REQUEST_SCHEMA, documentName, node);
        return parseGenerationRequest(node);
    }

    public TerrainIntentV2 readTerrainIntent(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(INTENT_SCHEMA, path.toString(), node);
        return parseIntent(node);
    }

    public TerrainIntentV2 readTerrainIntent(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(INTENT_SCHEMA, documentName, node);
        return parseIntent(node);
    }

    public WorldBlueprintV2 readWorldBlueprint(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(BLUEPRINT_SCHEMA, path.toString(), node);
        WorldBlueprintV2 blueprint = mapper.treeToValue(node, WorldBlueprintV2.class);
        verifyGeologyPlanChecksum(blueprint.geologyPlan());
        verifyLithologyPlanChecksum(blueprint.lithologyPlan());
        verifyStrataPlanChecksum(blueprint.strataPlan());
        verifyClimatePlanChecksum(blueprint.climatePlan());
        verifyWaterConditionPlanChecksum(blueprint.waterConditionPlan());
        verifyHydrologyPlanChecksum(blueprint.hydrologyPlan());
        verifyHydrologyReconciliationPlanChecksum(blueprint.hydrologyReconciliationPlan());
        verifyBlueprintChecksum(blueprint);
        return blueprint;
    }

    public WorldBlueprintV2 readWorldBlueprint(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(BLUEPRINT_SCHEMA, documentName, node);
        WorldBlueprintV2 blueprint = mapper.treeToValue(node, WorldBlueprintV2.class);
        verifyGeologyPlanChecksum(blueprint.geologyPlan());
        verifyLithologyPlanChecksum(blueprint.lithologyPlan());
        verifyStrataPlanChecksum(blueprint.strataPlan());
        verifyClimatePlanChecksum(blueprint.climatePlan());
        verifyWaterConditionPlanChecksum(blueprint.waterConditionPlan());
        verifyHydrologyPlanChecksum(blueprint.hydrologyPlan());
        verifyHydrologyReconciliationPlanChecksum(blueprint.hydrologyReconciliationPlan());
        verifyBlueprintChecksum(blueprint);
        return blueprint;
    }

    public GeologyPlanV2 readGeologyPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(GEOLOGY_PLAN_SCHEMA, path.toString(), node);
        GeologyPlanV2 plan = mapper.treeToValue(node, GeologyPlanV2.class);
        verifyGeologyPlanChecksum(plan);
        return plan;
    }

    public GeologyPlanV2 readGeologyPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(GEOLOGY_PLAN_SCHEMA, documentName, node);
        GeologyPlanV2 plan = mapper.treeToValue(node, GeologyPlanV2.class);
        verifyGeologyPlanChecksum(plan);
        return plan;
    }

    public LithologyPlanV2 readLithologyPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(LITHOLOGY_PLAN_SCHEMA, path.toString(), node);
        LithologyPlanV2 plan = mapper.treeToValue(node, LithologyPlanV2.class);
        verifyLithologyPlanChecksum(plan);
        return plan;
    }

    public LithologyPlanV2 readLithologyPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(LITHOLOGY_PLAN_SCHEMA, documentName, node);
        LithologyPlanV2 plan = mapper.treeToValue(node, LithologyPlanV2.class);
        verifyLithologyPlanChecksum(plan);
        return plan;
    }

    public StrataPlanV2 readStrataPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(STRATA_PLAN_SCHEMA, path.toString(), node);
        StrataPlanV2 plan = mapper.treeToValue(node, StrataPlanV2.class);
        verifyStrataPlanChecksum(plan);
        return plan;
    }

    public StrataPlanV2 readStrataPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(STRATA_PLAN_SCHEMA, documentName, node);
        StrataPlanV2 plan = mapper.treeToValue(node, StrataPlanV2.class);
        verifyStrataPlanChecksum(plan);
        return plan;
    }

    public ClimatePlanV2 readClimatePlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(CLIMATE_PLAN_SCHEMA, path.toString(), node);
        ClimatePlanV2 plan = mapper.treeToValue(node, ClimatePlanV2.class);
        verifyClimatePlanChecksum(plan);
        return plan;
    }

    public ClimatePlanV2 readClimatePlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(CLIMATE_PLAN_SCHEMA, documentName, node);
        ClimatePlanV2 plan = mapper.treeToValue(node, ClimatePlanV2.class);
        verifyClimatePlanChecksum(plan);
        return plan;
    }

    public WaterConditionPlanV2 readWaterConditionPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(WATER_CONDITION_PLAN_SCHEMA, path.toString(), node);
        WaterConditionPlanV2 plan = mapper.treeToValue(node, WaterConditionPlanV2.class);
        verifyWaterConditionPlanChecksum(plan);
        return plan;
    }

    public WaterConditionPlanV2 readWaterConditionPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(WATER_CONDITION_PLAN_SCHEMA, documentName, node);
        WaterConditionPlanV2 plan = mapper.treeToValue(node, WaterConditionPlanV2.class);
        verifyWaterConditionPlanChecksum(plan);
        return plan;
    }

    public HydrologyPlanV2 readHydrologyPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(HYDROLOGY_PLAN_SCHEMA, path.toString(), node);
        HydrologyPlanV2 plan = mapper.treeToValue(node, HydrologyPlanV2.class);
        verifyHydrologyPlanChecksum(plan);
        return plan;
    }

    public HydrologyPlanV2 readHydrologyPlan(String input, String documentName) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(HYDROLOGY_PLAN_SCHEMA, documentName, node);
        HydrologyPlanV2 plan = mapper.treeToValue(node, HydrologyPlanV2.class);
        verifyHydrologyPlanChecksum(plan);
        return plan;
    }

    public HydrologyReconciliationPlanV2 readHydrologyReconciliationPlan(Path path) throws IOException {
        JsonNode node = readTree(path);
        validator.validate(HYDROLOGY_RECONCILIATION_PLAN_SCHEMA, path.toString(), node);
        HydrologyReconciliationPlanV2 plan = mapper.treeToValue(node, HydrologyReconciliationPlanV2.class);
        verifyHydrologyReconciliationPlanChecksum(plan);
        return plan;
    }

    public HydrologyReconciliationPlanV2 readHydrologyReconciliationPlan(
            String input,
            String documentName
    ) throws IOException {
        JsonNode node = readTree(input, documentName);
        validator.validate(HYDROLOGY_RECONCILIATION_PLAN_SCHEMA, documentName, node);
        HydrologyReconciliationPlanV2 plan = mapper.treeToValue(node, HydrologyReconciliationPlanV2.class);
        verifyHydrologyReconciliationPlanChecksum(plan);
        return plan;
    }

    public String canonicalTerrainIntent(TerrainIntentV2 intent) {
        return CanonicalJsonV2.string(intentTree(intent));
    }

    public String canonicalGenerationRequest(GenerationRequestV2 request) {
        return CanonicalJsonV2.string(generationRequestTree(request));
    }

    public String generationRequestChecksum(GenerationRequestV2 request) {
        return CanonicalJsonV2.checksum(generationRequestTree(request));
    }

    public String terrainIntentChecksum(TerrainIntentV2 intent) {
        return CanonicalJsonV2.checksum(intentTree(intent));
    }

    public String geometryChecksum(TerrainIntentV2.Geometry geometry) {
        return CanonicalJsonV2.checksum(geometryTree(geometry));
    }

    public String canonicalWorldBlueprint(WorldBlueprintV2 blueprint) {
        return CanonicalJsonV2.string(mapper.valueToTree(blueprint));
    }

    public String worldBlueprintChecksum(WorldBlueprintV2 blueprint) {
        ObjectNode tree = mapper.valueToTree(blueprint);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public WorldBlueprintV2 sealWorldBlueprint(WorldBlueprintV2 blueprint) {
        return blueprint.withCanonicalChecksum(worldBlueprintChecksum(blueprint));
    }

    public String canonicalGeologyPlan(GeologyPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String geologyPlanChecksum(GeologyPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public GeologyPlanV2 sealGeologyPlan(GeologyPlanV2 plan) {
        return plan.withCanonicalChecksum(geologyPlanChecksum(plan));
    }

    public String canonicalLithologyCatalog(LithologyPlanV2.Catalog catalog) {
        return CanonicalJsonV2.string(mapper.valueToTree(catalog));
    }

    public String lithologyCatalogChecksum(LithologyPlanV2.Catalog catalog) {
        ObjectNode tree = mapper.valueToTree(catalog);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public LithologyPlanV2.Catalog sealLithologyCatalog(LithologyPlanV2.Catalog catalog) {
        return catalog.withCanonicalChecksum(lithologyCatalogChecksum(catalog));
    }

    public String canonicalLithologyPlan(LithologyPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String lithologyPlanChecksum(LithologyPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public LithologyPlanV2 sealLithologyPlan(LithologyPlanV2 plan) {
        return plan.withCanonicalChecksum(lithologyPlanChecksum(plan));
    }

    public String canonicalStrataPlan(StrataPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String strataPlanChecksum(StrataPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public StrataPlanV2 sealStrataPlan(StrataPlanV2 plan) {
        return plan.withCanonicalChecksum(strataPlanChecksum(plan));
    }

    public String canonicalClimatePlan(ClimatePlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String climatePlanChecksum(ClimatePlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public ClimatePlanV2 sealClimatePlan(ClimatePlanV2 plan) {
        return plan.withCanonicalChecksum(climatePlanChecksum(plan));
    }

    public String canonicalWaterConditionPlan(WaterConditionPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String waterConditionPlanChecksum(WaterConditionPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public WaterConditionPlanV2 sealWaterConditionPlan(WaterConditionPlanV2 plan) {
        return plan.withCanonicalChecksum(waterConditionPlanChecksum(plan));
    }

    public String canonicalHydrologyPlan(HydrologyPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String hydrologyPlanChecksum(HydrologyPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public HydrologyPlanV2 sealHydrologyPlan(HydrologyPlanV2 plan) {
        return plan.withCanonicalChecksum(hydrologyPlanChecksum(plan));
    }

    public String canonicalHydrologyReconciliationPlan(HydrologyReconciliationPlanV2 plan) {
        return CanonicalJsonV2.string(mapper.valueToTree(plan));
    }

    public String hydrologyReconciliationPlanChecksum(HydrologyReconciliationPlanV2 plan) {
        ObjectNode tree = mapper.valueToTree(plan);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public HydrologyReconciliationPlanV2 sealHydrologyReconciliationPlan(
            HydrologyReconciliationPlanV2 plan
    ) {
        return plan.withCanonicalChecksum(hydrologyReconciliationPlanChecksum(plan));
    }

    public void writeTerrainIntent(Path path, TerrainIntentV2 intent) throws IOException {
        writeCanonical(path, intentTree(intent), INTENT_SCHEMA);
    }

    public void writeGenerationRequest(Path path, GenerationRequestV2 request) throws IOException {
        writeCanonical(path, generationRequestTree(request), REQUEST_SCHEMA);
    }

    public void writeWorldBlueprint(Path path, WorldBlueprintV2 blueprint) throws IOException {
        verifyGeologyPlanChecksum(blueprint.geologyPlan());
        verifyLithologyPlanChecksum(blueprint.lithologyPlan());
        verifyStrataPlanChecksum(blueprint.strataPlan());
        verifyClimatePlanChecksum(blueprint.climatePlan());
        verifyWaterConditionPlanChecksum(blueprint.waterConditionPlan());
        verifyHydrologyPlanChecksum(blueprint.hydrologyPlan());
        verifyHydrologyReconciliationPlanChecksum(blueprint.hydrologyReconciliationPlan());
        verifyBlueprintChecksum(blueprint);
        writeCanonical(path, mapper.valueToTree(blueprint), BLUEPRINT_SCHEMA);
    }

    public void writeGeologyPlan(Path path, GeologyPlanV2 plan) throws IOException {
        verifyGeologyPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), GEOLOGY_PLAN_SCHEMA);
    }

    public void writeLithologyPlan(Path path, LithologyPlanV2 plan) throws IOException {
        verifyLithologyPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), LITHOLOGY_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeStrataPlan(Path path, StrataPlanV2 plan) throws IOException {
        verifyStrataPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), STRATA_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeClimatePlan(Path path, ClimatePlanV2 plan) throws IOException {
        verifyClimatePlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), CLIMATE_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeWaterConditionPlan(Path path, WaterConditionPlanV2 plan) throws IOException {
        verifyWaterConditionPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), WATER_CONDITION_PLAN_SCHEMA,
                plan.budget().maximumCanonicalBytes());
    }

    public void writeHydrologyPlan(Path path, HydrologyPlanV2 plan) throws IOException {
        verifyHydrologyPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), HYDROLOGY_PLAN_SCHEMA);
    }

    public void writeHydrologyReconciliationPlan(
            Path path,
            HydrologyReconciliationPlanV2 plan
    ) throws IOException {
        verifyHydrologyReconciliationPlanChecksum(plan);
        writeCanonical(path, mapper.valueToTree(plan), HYDROLOGY_RECONCILIATION_PLAN_SCHEMA,
                plan.budget().maximumArtifactBytes());
    }

    private void writeCanonical(Path path, JsonNode tree, String schema) throws IOException {
        writeCanonical(path, tree, schema, LandformDataCodec.MAX_DOCUMENT_BYTES);
    }

    private void writeCanonical(Path path, JsonNode tree, String schema, long maximumBytes) throws IOException {
        validator.validate(schema, path.toString(), tree);
        byte[] bytes = CanonicalJsonV2.bytes(tree);
        long effectiveMaximum = Math.min(maximumBytes, LandformDataCodec.MAX_DOCUMENT_BYTES);
        if (bytes.length > effectiveMaximum) {
            throw new IOException("document exceeds output byte budget: " + path);
        }
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(absolute.getParent(), "output path must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "lfc-v2-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void verifyBlueprintChecksum(WorldBlueprintV2 blueprint) throws IOException {
        String actual = worldBlueprintChecksum(blueprint);
        if (!actual.equals(blueprint.canonicalChecksum())) {
            throw new IOException("world blueprint canonical checksum mismatch");
        }
    }

    private void verifyGeologyPlanChecksum(GeologyPlanV2 plan) throws IOException {
        String actual = geologyPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("geology plan canonical checksum mismatch");
        }
    }

    private void verifyLithologyPlanChecksum(LithologyPlanV2 plan) throws IOException {
        if (canonicalLithologyCatalog(plan.catalog()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.catalog().budget().maximumCanonicalBytes()) {
            throw new IOException("lithology catalog exceeds declared canonical byte budget");
        }
        if (canonicalLithologyPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("lithology plan exceeds declared canonical byte budget");
        }
        String catalogChecksum = lithologyCatalogChecksum(plan.catalog());
        if (!catalogChecksum.equals(plan.catalog().canonicalChecksum())) {
            throw new IOException("lithology catalog canonical checksum mismatch");
        }
        String planChecksum = lithologyPlanChecksum(plan);
        if (!planChecksum.equals(plan.canonicalChecksum())) {
            throw new IOException("lithology plan canonical checksum mismatch");
        }
    }

    private void verifyStrataPlanChecksum(StrataPlanV2 plan) throws IOException {
        if (canonicalStrataPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("strata plan exceeds declared canonical byte budget");
        }
        String actual = strataPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("strata plan canonical checksum mismatch");
        }
    }

    private void verifyClimatePlanChecksum(ClimatePlanV2 plan) throws IOException {
        if (canonicalClimatePlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("climate plan exceeds declared canonical byte budget");
        }
        String actual = climatePlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("climate plan canonical checksum mismatch");
        }
    }

    private void verifyWaterConditionPlanChecksum(WaterConditionPlanV2 plan) throws IOException {
        if (canonicalWaterConditionPlan(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > plan.budget().maximumCanonicalBytes()) {
            throw new IOException("water-condition plan exceeds declared canonical byte budget");
        }
        String actual = waterConditionPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("water-condition plan canonical checksum mismatch");
        }
    }

    private void verifyHydrologyPlanChecksum(HydrologyPlanV2 plan) throws IOException {
        String actual = hydrologyPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("hydrology plan canonical checksum mismatch");
        }
    }

    private void verifyHydrologyReconciliationPlanChecksum(
            HydrologyReconciliationPlanV2 plan
    ) throws IOException {
        String actual = hydrologyReconciliationPlanChecksum(plan);
        if (!actual.equals(plan.canonicalChecksum())) {
            throw new IOException("hydrology reconciliation plan canonical checksum mismatch");
        }
    }

    private GenerationRequestV2 parseGenerationRequest(JsonNode root) {
        JsonNode bounds = root.path("bounds");
        List<GenerationRequestV2.ReferenceImageSource> references = new ArrayList<>();
        for (JsonNode reference : root.path("referenceImages")) {
            references.add(new GenerationRequestV2.ReferenceImageSource(
                    reference.path("id").textValue(), reference.path("file").textValue(),
                    enumValue(reference, "role", GenerationRequestV2.ReferenceImageRole.class)));
        }
        List<GenerationRequestV2.ConstraintMapSource> maps = new ArrayList<>();
        for (JsonNode map : root.path("constraintMaps")) {
            maps.add(parseConstraintMapSource(map));
        }
        JsonNode generation = root.path("generation");
        JsonNode budget = root.path("constraintMapBudget");
        return new GenerationRequestV2(
                root.path("requestVersion").intValue(), root.path("requestId").textValue(),
                new GenerationRequestV2.Bounds(
                        bounds.path("width").intValue(), bounds.path("length").intValue(),
                        bounds.path("minY").intValue(), bounds.path("maxY").intValue(),
                        bounds.path("waterLevel").intValue()),
                root.path("prompt").textValue(), references, maps,
                new GenerationRequestV2.GenerationSettings(
                        generation.path("globalSeed").longValue(), generation.path("tileSize").intValue()),
                new GenerationRequestV2.ConstraintMapBudget(
                        budget.path("maximumMapCount").intValue(),
                        budget.path("maximumTotalSourceBytes").longValue(),
                        budget.path("maximumDecodedBytes").longValue(),
                        budget.path("maximumPixels").longValue(),
                        budget.path("maximumArtifactBytes").longValue(),
                        budget.path("maximumResidentBytes").longValue()));
    }

    private GenerationRequestV2.ConstraintMapSource parseConstraintMapSource(JsonNode node) {
        JsonNode mapping = node.path("coordinateMapping");
        JsonNode crop = mapping.path("crop");
        return new GenerationRequestV2.ConstraintMapSource(
                node.path("sourceId").textValue(), node.path("file").textValue(),
                node.path("expectedSha256").textValue(), node.path("expectedWidth").intValue(),
                node.path("expectedLength").intValue(),
                enumValue(node, "decoderKind", GenerationRequestV2.DecoderKind.class),
                new GenerationRequestV2.CoordinateMapping(
                        enumValue(mapping, "origin", GenerationRequestV2.CoordinateOrigin.class),
                        enumValue(mapping, "xAxis", GenerationRequestV2.XAxis.class),
                        enumValue(mapping, "zAxis", GenerationRequestV2.ZAxis.class),
                        enumValue(mapping, "pixelReference", GenerationRequestV2.PixelReference.class),
                        enumValue(mapping, "aspectMismatchPolicy", GenerationRequestV2.AspectMismatchPolicy.class),
                        enumValue(mapping, "rotation", GenerationRequestV2.QuarterTurn.class),
                        mapping.path("flipX").booleanValue(), mapping.path("flipZ").booleanValue(),
                        new GenerationRequestV2.PixelCrop(
                                crop.path("x").intValue(), crop.path("z").intValue(),
                                crop.path("width").intValue(), crop.path("length").intValue())),
                parseConstraintMapEncoding(node.path("encoding")));
    }

    private GenerationRequestV2.ConstraintMapEncoding parseConstraintMapEncoding(JsonNode node) {
        GenerationRequestV2.SampleType sampleType =
                enumValue(node, "sampleType", GenerationRequestV2.SampleType.class);
        GenerationRequestV2.RasterChannel channel =
                enumValue(node, "channel", GenerationRequestV2.RasterChannel.class);
        GenerationRequestV2.NoData noData = parseNoData(node.path("noData"));
        return switch (node.path("kind").textValue()) {
            case "CATEGORICAL" -> {
                List<GenerationRequestV2.LabelMapping> labels = new ArrayList<>();
                for (JsonNode label : node.path("labels")) {
                    labels.add(new GenerationRequestV2.LabelMapping(
                            label.path("sample").intValue(), label.path("label").textValue()));
                }
                yield new GenerationRequestV2.CategoricalEncoding(
                        node.path("encodingVersion").intValue(), sampleType, channel, labels, noData);
            }
            case "HEIGHT" -> {
                JsonNode range = node.path("validSampleRange");
                yield new GenerationRequestV2.HeightEncoding(
                        node.path("encodingVersion").intValue(), sampleType, channel,
                        enumValue(node, "valueMeaning", GenerationRequestV2.HeightValueMeaning.class),
                        node.path("valueScaleMillionths").longValue(),
                        node.path("valueOffsetMillionths").longValue(),
                        new GenerationRequestV2.IntRange(
                                range.path("minimum").intValue(), range.path("maximum").intValue()),
                        noData);
            }
            default -> throw new IllegalArgumentException("unknown constraint map encoding kind");
        };
    }

    private static GenerationRequestV2.NoData parseNoData(JsonNode node) {
        return switch (node.path("mode").textValue()) {
            case "FORBIDDEN" -> new GenerationRequestV2.NoDataForbidden();
            case "SENTINEL" -> new GenerationRequestV2.NoDataSentinel(node.path("sample").intValue());
            default -> throw new IllegalArgumentException("unknown no-data mode");
        };
    }

    private TerrainIntentV2 parseIntent(JsonNode root) {
        TerrainIntentV2.CoordinateSystem coordinateSystem = new TerrainIntentV2.CoordinateSystem(
                enumValue(root.path("coordinateSystem"), "horizontal", TerrainIntentV2.HorizontalCoordinates.class),
                enumValue(root.path("coordinateSystem"), "origin", TerrainIntentV2.CoordinateOrigin.class),
                enumValue(root.path("coordinateSystem"), "xAxis", TerrainIntentV2.XAxis.class),
                enumValue(root.path("coordinateSystem"), "zAxis", TerrainIntentV2.ZAxis.class),
                enumValue(root.path("coordinateSystem"), "vertical", TerrainIntentV2.VerticalCoordinates.class)
        );
        List<TerrainIntentV2.Feature> features = new ArrayList<>();
        for (JsonNode feature : root.path("features")) features.add(parseFeature(feature));
        List<TerrainIntentV2.Relation> relations = new ArrayList<>();
        for (JsonNode relation : root.path("relations")) {
            JsonNode transition = relation.path("transition");
            relations.add(new TerrainIntentV2.Relation(
                    relation.path("id").textValue(),
                    enumValue(relation, "kind", TerrainIntentV2.RelationKind.class),
                    relation.path("from").textValue(),
                    relation.path("to").textValue(),
                    enumValue(relation, "strength", TerrainIntentV2.Strength.class),
                    transition.isMissingNode()
                            ? TerrainIntentV2.TransitionPolicy.NONE
                            : new TerrainIntentV2.TransitionPolicy(
                                    transition.path("transitionVersion").intValue(),
                                    enumValue(transition, "profile", TerrainIntentV2.TransitionProfile.class),
                                    transition.path("bandBlocks").intValue())
            ));
        }
        List<TerrainIntentV2.Constraint> constraints = new ArrayList<>();
        for (JsonNode constraint : root.path("constraints")) constraints.add(parseConstraint(constraint));
        JsonNode environment = root.path("environment");
        TerrainIntentV2.EnvironmentDescriptor environmentDescriptor = new TerrainIntentV2.EnvironmentDescriptor(
                optionalText(environment, "geologyPreset"),
                optionalText(environment, "climatePreset"),
                optionalText(environment, "ecologyPreset")
        );
        List<TerrainIntentV2.ConstraintMapBinding> maps = new ArrayList<>();
        for (JsonNode map : root.path("mapReferences")) {
            TerrainIntentV2.Strength strength = enumValue(map, "strength", TerrainIntentV2.Strength.class);
            maps.add(new TerrainIntentV2.ConstraintMapBinding(
                    map.path("id").textValue(), map.path("sourceId").textValue(),
                    enumValue(map, "role", TerrainIntentV2.ConstraintMapRole.class),
                    map.path("artifactId").textValue(), strength,
                    enumValue(map, "sampling", TerrainIntentV2.Sampling.class), map.path("toleranceBlocks").intValue(),
                    strength == TerrainIntentV2.Strength.SOFT ? fixedInt(map.path("weight")) : 0
            ));
        }
        List<TerrainIntentV2.StructureRequest> structures = new ArrayList<>();
        for (JsonNode structure : root.path("structures")) {
            structures.add(new TerrainIntentV2.StructureRequest(
                    structure.path("id").textValue(),
                    enumValue(structure, "kind", TerrainIntentV2.StructureKind.class),
                    structure.path("count").intValue(), structure.path("preferredFeatureId").textValue()
            ));
        }
        return new TerrainIntentV2(
                root.path("intentVersion").intValue(), root.path("intentId").textValue(), root.path("theme").textValue(),
                coordinateSystem, features, relations, constraints, environmentDescriptor, maps, structures,
                parseProvenance(root.path("provenance"))
        );
    }

    private TerrainIntentV2.Feature parseFeature(JsonNode node) {
        TerrainIntentV2.FeatureKind kind = enumValue(node, "kind", TerrainIntentV2.FeatureKind.class);
        return new TerrainIntentV2.Feature(
                node.path("id").textValue(), kind, parseGeometry(node.path("geometry")),
                parseParameters(kind, node.path("parameters")), node.path("priority").intValue(),
                parseProvenance(node.path("provenance"))
        );
    }

    private TerrainIntentV2.Geometry parseGeometry(JsonNode node) {
        TerrainIntentV2.GeometryType type = enumValue(node, "type", TerrainIntentV2.GeometryType.class);
        return switch (type) {
            case POINT -> new TerrainIntentV2.PointGeometry(parsePoint(node.path("point")));
            case MULTI_POINT -> {
                List<TerrainIntentV2.NamedPoint> points = new ArrayList<>();
                for (JsonNode point : node.path("points")) points.add(new TerrainIntentV2.NamedPoint(point.path("id").textValue(), parsePoint(point.path("point"))));
                yield new TerrainIntentV2.MultiPointGeometry(points);
            }
            case SPLINE -> new TerrainIntentV2.SplineGeometry(
                    parsePoints(node.path("points")), enumValue(node, "interpolation", TerrainIntentV2.Interpolation.class));
            case MULTI_SPLINE -> {
                List<TerrainIntentV2.NamedPath> paths = new ArrayList<>();
                for (JsonNode path : node.path("paths")) {
                    paths.add(new TerrainIntentV2.NamedPath(
                            path.path("id").textValue(), optionalText(path, "startEndpointId"),
                            optionalText(path, "endEndpointId"), parsePoints(path.path("points"))));
                }
                yield new TerrainIntentV2.MultiSplineGeometry(
                        paths, enumValue(node, "interpolation", TerrainIntentV2.Interpolation.class));
            }
            case POLYGON -> parsePolygon(node);
            case VOLUME_GUIDE -> new TerrainIntentV2.VolumeGuideGeometry(
                    parsePolygon(node.path("footprint")),
                    new TerrainIntentV2.VerticalGuide(
                            enumValue(node.path("vertical"), "mode", TerrainIntentV2.VerticalMode.class),
                            node.path("vertical").path("min").intValue(), node.path("vertical").path("max").intValue()));
        };
    }

    private TerrainIntentV2.PolygonGeometry parsePolygon(JsonNode node) {
        List<List<TerrainIntentV2.Point2>> rings = new ArrayList<>();
        for (JsonNode ring : node.path("rings")) rings.add(parsePoints(ring));
        return new TerrainIntentV2.PolygonGeometry(rings);
    }

    private TerrainIntentV2.FeatureParameters parseParameters(TerrainIntentV2.FeatureKind kind, JsonNode node) {
        return switch (kind) {
            case SANDY_BEACH -> new TerrainIntentV2.SandyBeachParameters(
                    intRange(node.path("widthBlocks")), fixedRange(node.path("shoreSlopeDegrees")),
                    new TerrainIntentV2.NearshoreDepth(node.path("nearshoreDepthBlocks").path("atDistance").intValue(),
                            node.path("nearshoreDepthBlocks").path("target").intValue()),
                    Math.toIntExact(fixed(node.path("foreshoreShare01"))),
                    node.path("endpointTaperBlocks").intValue(),
                    enumValue(node, "landSide", TerrainIntentV2.LandSide.class));
            case BREAKWATER_HARBOR -> new TerrainIntentV2.BreakwaterHarborParameters(
                    node.path("crestWidthBlocks").intValue(), node.path("crestAboveWaterBlocks").intValue(),
                    node.path("outerDepthBlocks").intValue(),
                    enumValue(node, "crestProfile", TerrainIntentV2.BreakwaterCrestProfile.class),
                    enumValue(node, "foundationProfile", TerrainIntentV2.BreakwaterFoundationProfile.class),
                    Math.toIntExact(fixed(node.path("foundationSideSlopeRunPerRise"))),
                    new TerrainIntentV2.HarborOpening(
                            strings(node.path("opening").path("betweenEndpointIds")),
                            node.path("opening").path("widthBlocks").intValue(),
                            enumValue(node.path("opening"), "measurement", TerrainIntentV2.Measurement.class)),
                    enumValue(node, "innerSide", TerrainIntentV2.InnerSide.class));
            case HARBOR_BASIN -> new TerrainIntentV2.HarborBasinParameters(
                    intRange(node.path("waterDepthBlocks")), strings(node.path("entranceEndpointIds")),
                    node.path("entranceCorridorLengthBlocks").intValue(),
                    enumValue(node, "bottomProfile", TerrainIntentV2.HarborBottomProfile.class),
                    node.path("profileTransitionBlocks").intValue());
            case ROCKY_CAPE -> new TerrainIntentV2.RockyCapeParameters(
                    intRange(node.path("cliffHeightBlocks")), intRange(node.path("localReliefAboveSeaBlocks")),
                    intRange(node.path("cliffBandWidthBlocks")), intRange(node.path("seaStackCount")),
                    intRange(node.path("seaStackRadiusBlocks")),
                    intRange(node.path("seaStackOffshoreDistanceBlocks")),
                    intRange(node.path("channelCount")), intRange(node.path("channelWidthBlocks")),
                    intRange(node.path("channelLengthBlocks")), intRange(node.path("channelDepthBlocks")),
                    fixedRange(node.path("rockExposure01")),
                    enumValue(node, "seawardSide", TerrainIntentV2.Edge.class),
                    enumValue(node, "capeMode", TerrainIntentV2.CapeMode.class));
            case BACKSHORE_PLAINS -> new TerrainIntentV2.BackshorePlainsParameters(
                    intRange(node.path("elevationAboveWaterBlocks")), fixedRange(node.path("grassCover01")));
            case MEANDERING_RIVER -> new TerrainIntentV2.MeanderingRiverParameters(
                    intRange(node.path("bankfullWidthBlocks")),
                    enumValue(node, "dischargeClass", TerrainIntentV2.DischargeClass.class),
                    fixed(node.path("minimumBedSlope01")),
                    enumValue(node, "variant", TerrainIntentV2.RiverVariant.class));
            case LAKE -> new TerrainIntentV2.LakeParameters(
                    intRange(node.path("targetDepthBlocks")),
                    node.path("shoreWidthBlocks").intValue(),
                    enumValue(node, "terminalPolicy", TerrainIntentV2.LakeTerminalPolicy.class),
                    enumValue(node, "spillSelection", TerrainIntentV2.LakeSpillSelection.class),
                    node.path("spillEdgeStartIndex").intValue(),
                    node.path("spillwayWidthBlocks").intValue(),
                    node.path("spillwayCorridorLengthBlocks").intValue(),
                    enumValue(node, "floorProfile", TerrainIntentV2.LakeFloorProfile.class));
            case CANYON -> new TerrainIntentV2.CanyonParameters(
                    intRange(node.path("floorWidthBlocks")),
                    intRange(node.path("rimWidthBlocks")),
                    intRange(node.path("depthBlocks")),
                    enumValue(node, "crossSection", TerrainIntentV2.CanyonCrossSection.class),
                    node.path("terraceCount").intValue(),
                    node.path("terraceWidthBlocks").intValue());
            case WATERFALL -> new TerrainIntentV2.WaterfallParameters(
                    intRange(node.path("dropBlocks")),
                    node.path("lipWidthBlocks").intValue(),
                    node.path("plungePoolRadiusBlocks").intValue(),
                    node.path("behindFallClearanceBlocks").intValue());
            case DELTA -> new TerrainIntentV2.DeltaParameters(
                    intRange(node.path("distributaryCount")),
                    fixedRange(node.path("fanOpeningDegrees")),
                    intRange(node.path("fanReliefBlocks")),
                    intRange(node.path("sandbarCount")),
                    intRange(node.path("shallowSeaDepthBlocks")),
                    enumValue(node, "fanProfile", TerrainIntentV2.DeltaFanProfile.class));
            case TIDAL_CHANNEL_NETWORK -> new TerrainIntentV2.TidalChannelParameters(
                    intRange(node.path("widthBlocks")),
                    node.path("tidalRangeBlocks").intValue(),
                    enumValue(node, "edgeKind", TerrainIntentV2.TidalEdgeKind.class));
            case FJORD -> new TerrainIntentV2.FjordParameters(
                    intRange(node.path("surfaceWidthBlocks")),
                    intRange(node.path("channelDepthBlocks")),
                    enumValue(node, "crossSection", TerrainIntentV2.FjordCrossSection.class),
                    node.path("headBasinRadiusBlocks").intValue());
            case ALPINE_MOUNTAIN_RANGE, GLACIAL_MOUNTAIN_RANGE -> new TerrainIntentV2.MountainParameters(
                    intRange(node.path("peakCount")),
                    intRange(node.path("ridgeHalfWidthBlocks")),
                    intRange(node.path("maxReliefBlocks")),
                    node.path("spurCount").intValue(),
                    fixed(node.path("ridgeSharpness01")));
            case VOLCANIC_ARCHIPELAGO -> {
                List<TerrainIntentV2.IslandSpec> islands = new ArrayList<>();
                for (JsonNode island : node.path("islands")) {
                    islands.add(new TerrainIntentV2.IslandSpec(
                            island.path("pointId").textValue(),
                            island.path("radiusBlocks").intValue(),
                            island.path("summitHeightBlocksAboveSea").intValue()));
                }
                yield new TerrainIntentV2.VolcanicArchipelagoParameters(
                        islands, intRange(node.path("submarineSaddleDepthBlocks")));
            }
            case VOLCANIC_CALDERA -> new TerrainIntentV2.VolcanicCalderaParameters(
                    node.path("rimRadiusBlocks").intValue(),
                    node.path("rimReliefBlocks").intValue(),
                    node.path("craterFloorDepthBlocks").intValue(),
                    enumValue(node, "breachDirection", TerrainIntentV2.CalderaBreachDirection.class));
            case LAVA_FLOW_FIELD -> new TerrainIntentV2.LavaFlowParameters(
                    intRange(node.path("widthBlocks")),
                    fixed(node.path("surfaceRoughness01")));
            default -> new TerrainIntentV2.NoParameters();
        };
    }

    private TerrainIntentV2.Constraint parseConstraint(JsonNode node) {
        TerrainIntentV2.Strength strength = enumValue(node, "strength", TerrainIntentV2.Strength.class);
        int weight = strength == TerrainIntentV2.Strength.SOFT ? fixedInt(node.path("weight")) : 0;
        return switch (node.path("kind").textValue()) {
            case "METRIC_RANGE" -> new TerrainIntentV2.MetricRangeConstraint(
                    node.path("id").textValue(), strength, node.path("subject").textValue(),
                    node.path("metric").textValue(), fixedRange(node.path("range")), fixed(node.path("tolerance")), weight);
            case "EDGE_CLASSIFICATION" -> new TerrainIntentV2.EdgeClassificationConstraint(
                    node.path("id").textValue(), strength, node.path("subject").textValue(),
                    enumValue(node.path("parameters"), "edge", TerrainIntentV2.Edge.class),
                    enumValue(node.path("parameters"), "classification", TerrainIntentV2.EdgeClassification.class),
                    fixedInt(node.path("parameters").path("minimumShare01")), weight);
            default -> throw new IllegalArgumentException("unsupported v2 constraint kind");
        };
    }

    private ObjectNode generationRequestTree(GenerationRequestV2 request) {
        ObjectNode node = mapper.createObjectNode();
        node.put("requestVersion", request.requestVersion());
        node.put("requestId", request.requestId());
        ObjectNode bounds = node.putObject("bounds");
        bounds.put("width", request.bounds().width());
        bounds.put("length", request.bounds().length());
        bounds.put("minY", request.bounds().minY());
        bounds.put("maxY", request.bounds().maxY());
        bounds.put("waterLevel", request.bounds().waterLevel());
        node.put("prompt", request.prompt());
        ArrayNode references = node.putArray("referenceImages");
        for (GenerationRequestV2.ReferenceImageSource reference : request.referenceImages()) {
            ObjectNode value = references.addObject();
            value.put("id", reference.id());
            value.put("file", reference.file());
            value.put("role", reference.role().name());
        }
        ArrayNode maps = node.putArray("constraintMaps");
        for (GenerationRequestV2.ConstraintMapSource map : request.constraintMaps()) {
            maps.add(constraintMapSourceTree(map));
        }
        ObjectNode generation = node.putObject("generation");
        generation.put("globalSeed", request.generation().globalSeed());
        generation.put("tileSize", request.generation().tileSize());
        ObjectNode budget = node.putObject("constraintMapBudget");
        budget.put("maximumMapCount", request.constraintMapBudget().maximumMapCount());
        budget.put("maximumTotalSourceBytes", request.constraintMapBudget().maximumTotalSourceBytes());
        budget.put("maximumDecodedBytes", request.constraintMapBudget().maximumDecodedBytes());
        budget.put("maximumPixels", request.constraintMapBudget().maximumPixels());
        budget.put("maximumArtifactBytes", request.constraintMapBudget().maximumArtifactBytes());
        budget.put("maximumResidentBytes", request.constraintMapBudget().maximumResidentBytes());
        return node;
    }

    private ObjectNode constraintMapSourceTree(GenerationRequestV2.ConstraintMapSource source) {
        ObjectNode node = mapper.createObjectNode();
        node.put("sourceId", source.sourceId());
        node.put("file", source.file());
        node.put("expectedSha256", source.expectedSha256());
        node.put("expectedWidth", source.expectedWidth());
        node.put("expectedLength", source.expectedLength());
        node.put("decoderKind", source.decoderKind().name());
        ObjectNode mapping = node.putObject("coordinateMapping");
        mapping.put("origin", source.coordinateMapping().origin().name());
        mapping.put("xAxis", source.coordinateMapping().xAxis().name());
        mapping.put("zAxis", source.coordinateMapping().zAxis().name());
        mapping.put("pixelReference", source.coordinateMapping().pixelReference().name());
        mapping.put("aspectMismatchPolicy", source.coordinateMapping().aspectMismatchPolicy().name());
        mapping.put("rotation", source.coordinateMapping().rotation().name());
        mapping.put("flipX", source.coordinateMapping().flipX());
        mapping.put("flipZ", source.coordinateMapping().flipZ());
        ObjectNode crop = mapping.putObject("crop");
        crop.put("x", source.coordinateMapping().crop().x());
        crop.put("z", source.coordinateMapping().crop().z());
        crop.put("width", source.coordinateMapping().crop().width());
        crop.put("length", source.coordinateMapping().crop().length());
        node.set("encoding", constraintMapEncodingTree(source.encoding()));
        return node;
    }

    private ObjectNode constraintMapEncodingTree(GenerationRequestV2.ConstraintMapEncoding encoding) {
        ObjectNode node = mapper.createObjectNode();
        node.put("encodingVersion", encoding.encodingVersion());
        node.put("sampleType", encoding.sampleType().name());
        node.put("channel", encoding.channel().name());
        if (encoding instanceof GenerationRequestV2.CategoricalEncoding categorical) {
            node.put("kind", "CATEGORICAL");
            ArrayNode labels = node.putArray("labels");
            for (GenerationRequestV2.LabelMapping label : categorical.labels()) {
                ObjectNode value = labels.addObject();
                value.put("sample", label.sample());
                value.put("label", label.label());
            }
        } else if (encoding instanceof GenerationRequestV2.HeightEncoding height) {
            node.put("kind", "HEIGHT");
            node.put("valueMeaning", height.valueMeaning().name());
            node.put("valueScaleMillionths", height.valueScaleMillionths());
            node.put("valueOffsetMillionths", height.valueOffsetMillionths());
            ObjectNode range = node.putObject("validSampleRange");
            range.put("minimum", height.validSampleRange().minimum());
            range.put("maximum", height.validSampleRange().maximum());
        } else {
            throw new IllegalArgumentException("unknown constraint map encoding");
        }
        node.set("noData", noDataTree(encoding.noData()));
        return node;
    }

    private ObjectNode noDataTree(GenerationRequestV2.NoData noData) {
        ObjectNode node = mapper.createObjectNode();
        if (noData instanceof GenerationRequestV2.NoDataForbidden) {
            node.put("mode", "FORBIDDEN");
        } else if (noData instanceof GenerationRequestV2.NoDataSentinel sentinel) {
            node.put("mode", "SENTINEL");
            node.put("sample", sentinel.sample());
        } else {
            throw new IllegalArgumentException("unknown no-data contract");
        }
        return node;
    }

    private ObjectNode intentTree(TerrainIntentV2 intent) {
        ObjectNode root = mapper.createObjectNode();
        root.put("intentVersion", intent.intentVersion()); root.put("intentId", intent.intentId()); root.put("theme", intent.theme());
        ObjectNode coordinate = root.putObject("coordinateSystem");
        coordinate.put("horizontal", intent.coordinateSystem().horizontal().name());
        coordinate.put("origin", intent.coordinateSystem().origin().name()); coordinate.put("xAxis", intent.coordinateSystem().xAxis().name());
        coordinate.put("zAxis", intent.coordinateSystem().zAxis().name()); coordinate.put("vertical", intent.coordinateSystem().vertical().name());
        ArrayNode features = root.putArray("features");
        for (TerrainIntentV2.Feature feature : intent.features()) features.add(featureTree(feature));
        ArrayNode relations = root.putArray("relations");
        for (TerrainIntentV2.Relation relation : intent.relations()) {
            ObjectNode item = relations.addObject(); item.put("id", relation.id()); item.put("kind", relation.kind().name());
            item.put("from", relation.from()); item.put("to", relation.to()); item.put("strength", relation.strength().name());
            if (relation.transition().profile() != TerrainIntentV2.TransitionProfile.NONE) {
                ObjectNode transition = item.putObject("transition");
                transition.put("transitionVersion", relation.transition().transitionVersion());
                transition.put("profile", relation.transition().profile().name());
                transition.put("bandBlocks", relation.transition().bandBlocks());
            }
        }
        ArrayNode constraints = root.putArray("constraints");
        for (TerrainIntentV2.Constraint constraint : intent.constraints()) constraints.add(constraintTree(constraint));
        ObjectNode environment = root.putObject("environment");
        putOptional(environment, "geologyPreset", intent.environment().geologyPreset());
        putOptional(environment, "climatePreset", intent.environment().climatePreset());
        putOptional(environment, "ecologyPreset", intent.environment().ecologyPreset());
        ArrayNode maps = root.putArray("mapReferences");
        for (TerrainIntentV2.ConstraintMapBinding map : intent.mapReferences()) {
            ObjectNode item = maps.addObject(); item.put("id", map.id()); item.put("sourceId", map.sourceId()); item.put("role", map.role().name());
            item.put("artifactId", map.artifactId()); item.put("strength", map.strength().name()); item.put("sampling", map.sampling().name());
            item.put("toleranceBlocks", map.toleranceBlocks()); if (map.strength() == TerrainIntentV2.Strength.SOFT) item.set("weight", fixedNode(map.weightMillionths()));
        }
        ArrayNode structures = root.putArray("structures");
        for (TerrainIntentV2.StructureRequest structure : intent.structures()) {
            ObjectNode item = structures.addObject(); item.put("id", structure.id()); item.put("kind", structure.kind().name());
            item.put("count", structure.count()); item.put("preferredFeatureId", structure.preferredFeatureId());
        }
        root.set("provenance", provenanceTree(intent.provenance()));
        return root;
    }

    private ObjectNode featureTree(TerrainIntentV2.Feature feature) {
        ObjectNode node = mapper.createObjectNode(); node.put("id", feature.id()); node.put("kind", feature.kind().name());
        node.set("geometry", geometryTree(feature.geometry())); node.set("parameters", parameterTree(feature.parameters()));
        node.put("priority", feature.priority()); node.set("provenance", provenanceTree(feature.provenance())); return node;
    }

    private ObjectNode geometryTree(TerrainIntentV2.Geometry geometry) {
        ObjectNode node = mapper.createObjectNode(); node.put("type", geometry.type().name());
        if (geometry instanceof TerrainIntentV2.PointGeometry point) node.set("point", pointTree(point.point()));
        else if (geometry instanceof TerrainIntentV2.MultiPointGeometry multi) {
            ArrayNode points = node.putArray("points"); for (TerrainIntentV2.NamedPoint point : multi.points()) { ObjectNode item = points.addObject(); item.put("id", point.id()); item.set("point", pointTree(point.point())); }
        } else if (geometry instanceof TerrainIntentV2.SplineGeometry spline) {
            node.set("points", pointsTree(spline.points())); node.put("interpolation", spline.interpolation().name());
        } else if (geometry instanceof TerrainIntentV2.MultiSplineGeometry multi) {
            ArrayNode paths = node.putArray("paths"); for (TerrainIntentV2.NamedPath path : multi.paths()) {
                ObjectNode item = paths.addObject(); item.put("id", path.id()); putOptional(item, "startEndpointId", path.startEndpointId());
                putOptional(item, "endEndpointId", path.endEndpointId()); item.set("points", pointsTree(path.points()));
            } node.put("interpolation", multi.interpolation().name());
        } else if (geometry instanceof TerrainIntentV2.PolygonGeometry polygon) {
            node.set("rings", ringsTree(polygon));
        } else if (geometry instanceof TerrainIntentV2.VolumeGuideGeometry volume) {
            ObjectNode footprint = mapper.createObjectNode(); footprint.put("type", "POLYGON"); footprint.set("rings", ringsTree(volume.footprint())); node.set("footprint", footprint);
            ObjectNode vertical = node.putObject("vertical"); vertical.put("mode", volume.vertical().mode().name()); vertical.put("min", volume.vertical().minimum()); vertical.put("max", volume.vertical().maximum());
        }
        return node;
    }

    private ObjectNode parameterTree(TerrainIntentV2.FeatureParameters parameters) {
        ObjectNode node = mapper.createObjectNode();
        if (parameters instanceof TerrainIntentV2.SandyBeachParameters value) {
            node.set("widthBlocks", intRangeTree(value.widthBlocks())); node.set("shoreSlopeDegrees", fixedRangeTree(value.shoreSlopeDegrees()));
            ObjectNode nearshore = node.putObject("nearshoreDepthBlocks"); nearshore.put("atDistance", value.nearshoreDepthBlocks().atDistance()); nearshore.put("target", value.nearshoreDepthBlocks().target());
            node.set("foreshoreShare01", fixedNode(value.foreshoreShareMillionths()));
            node.put("endpointTaperBlocks", value.endpointTaperBlocks());
            node.put("landSide", value.landSide().name());
        } else if (parameters instanceof TerrainIntentV2.BreakwaterHarborParameters value) {
            node.put("crestWidthBlocks", value.crestWidthBlocks()); node.put("crestAboveWaterBlocks", value.crestAboveWaterBlocks()); node.put("outerDepthBlocks", value.outerDepthBlocks());
            node.put("crestProfile", value.crestProfile().name());
            node.put("foundationProfile", value.foundationProfile().name());
            node.set("foundationSideSlopeRunPerRise", fixedNode(value.foundationSideSlopeRunPerRiseMillionths()));
            ObjectNode opening = node.putObject("opening"); ArrayNode endpoints = opening.putArray("betweenEndpointIds"); value.opening().betweenEndpointIds().forEach(endpoints::add);
            opening.put("widthBlocks", value.opening().widthBlocks()); opening.put("measurement", value.opening().measurement().name()); node.put("innerSide", value.innerSide().name());
        } else if (parameters instanceof TerrainIntentV2.HarborBasinParameters value) {
            node.set("waterDepthBlocks", intRangeTree(value.waterDepthBlocks()));
            ArrayNode endpoints = node.putArray("entranceEndpointIds"); value.entranceEndpointIds().forEach(endpoints::add);
            node.put("entranceCorridorLengthBlocks", value.entranceCorridorLengthBlocks());
            node.put("bottomProfile", value.bottomProfile().name());
            node.put("profileTransitionBlocks", value.profileTransitionBlocks());
        } else if (parameters instanceof TerrainIntentV2.RockyCapeParameters value) {
            node.set("cliffHeightBlocks", intRangeTree(value.cliffHeightBlocks())); node.set("localReliefAboveSeaBlocks", intRangeTree(value.localReliefAboveSeaBlocks()));
            node.set("cliffBandWidthBlocks", intRangeTree(value.cliffBandWidthBlocks()));
            node.set("seaStackCount", intRangeTree(value.seaStackCount()));
            node.set("seaStackRadiusBlocks", intRangeTree(value.seaStackRadiusBlocks()));
            node.set("seaStackOffshoreDistanceBlocks", intRangeTree(value.seaStackOffshoreDistanceBlocks()));
            node.set("channelCount", intRangeTree(value.channelCount()));
            node.set("channelWidthBlocks", intRangeTree(value.channelWidthBlocks()));
            node.set("channelLengthBlocks", intRangeTree(value.channelLengthBlocks()));
            node.set("channelDepthBlocks", intRangeTree(value.channelDepthBlocks()));
            node.set("rockExposure01", fixedRangeTree(value.rockExposure01()));
            node.put("seawardSide", value.seawardSide().name());
            node.put("capeMode", value.capeMode().name());
        } else if (parameters instanceof TerrainIntentV2.BackshorePlainsParameters value) {
            node.set("elevationAboveWaterBlocks", intRangeTree(value.elevationAboveWaterBlocks())); node.set("grassCover01", fixedRangeTree(value.grassCover01()));
        } else if (parameters instanceof TerrainIntentV2.MeanderingRiverParameters value) {
            node.set("bankfullWidthBlocks", intRangeTree(value.bankfullWidthBlocks()));
            node.put("dischargeClass", value.dischargeClass().name());
            node.set("minimumBedSlope01", fixedNode(value.minimumBedSlopeMillionths()));
            node.put("variant", value.variant().name());
        } else if (parameters instanceof TerrainIntentV2.LakeParameters value) {
            node.set("targetDepthBlocks", intRangeTree(value.targetDepthBlocks()));
            node.put("shoreWidthBlocks", value.shoreWidthBlocks());
            node.put("terminalPolicy", value.terminalPolicy().name());
            node.put("spillSelection", value.spillSelection().name());
            node.put("spillEdgeStartIndex", value.spillEdgeStartIndex());
            node.put("spillwayWidthBlocks", value.spillwayWidthBlocks());
            node.put("spillwayCorridorLengthBlocks", value.spillwayCorridorLengthBlocks());
            node.put("floorProfile", value.floorProfile().name());
        } else if (parameters instanceof TerrainIntentV2.CanyonParameters value) {
            node.set("floorWidthBlocks", intRangeTree(value.floorWidthBlocks()));
            node.set("rimWidthBlocks", intRangeTree(value.rimWidthBlocks()));
            node.set("depthBlocks", intRangeTree(value.depthBlocks()));
            node.put("crossSection", value.crossSection().name());
            node.put("terraceCount", value.terraceCount());
            node.put("terraceWidthBlocks", value.terraceWidthBlocks());
        } else if (parameters instanceof TerrainIntentV2.WaterfallParameters value) {
            node.set("dropBlocks", intRangeTree(value.dropBlocks()));
            node.put("lipWidthBlocks", value.lipWidthBlocks());
            node.put("plungePoolRadiusBlocks", value.plungePoolRadiusBlocks());
            node.put("behindFallClearanceBlocks", value.behindFallClearanceBlocks());
        } else if (parameters instanceof TerrainIntentV2.DeltaParameters value) {
            node.set("distributaryCount", intRangeTree(value.distributaryCount()));
            node.set("fanOpeningDegrees", fixedRangeTree(value.fanOpeningDegrees()));
            node.set("fanReliefBlocks", intRangeTree(value.fanReliefBlocks()));
            node.set("sandbarCount", intRangeTree(value.sandbarCount()));
            node.set("shallowSeaDepthBlocks", intRangeTree(value.shallowSeaDepthBlocks()));
            node.put("fanProfile", value.fanProfile().name());
        } else if (parameters instanceof TerrainIntentV2.TidalChannelParameters value) {
            node.set("widthBlocks", intRangeTree(value.widthBlocks()));
            node.put("tidalRangeBlocks", value.tidalRangeBlocks());
            node.put("edgeKind", value.edgeKind().name());
        } else if (parameters instanceof TerrainIntentV2.FjordParameters value) {
            node.set("surfaceWidthBlocks", intRangeTree(value.surfaceWidthBlocks()));
            node.set("channelDepthBlocks", intRangeTree(value.channelDepthBlocks()));
            node.put("crossSection", value.crossSection().name());
            node.put("headBasinRadiusBlocks", value.headBasinRadiusBlocks());
        } else if (parameters instanceof TerrainIntentV2.MountainParameters value) {
            node.set("peakCount", intRangeTree(value.peakCount()));
            node.set("ridgeHalfWidthBlocks", intRangeTree(value.ridgeHalfWidthBlocks()));
            node.set("maxReliefBlocks", intRangeTree(value.maxReliefBlocks()));
            node.put("spurCount", value.spurCount());
            node.set("ridgeSharpness01", fixedNode(value.ridgeSharpnessMillionths()));
        } else if (parameters instanceof TerrainIntentV2.VolcanicArchipelagoParameters value) {
            ArrayNode islands = node.putArray("islands");
            for (TerrainIntentV2.IslandSpec island : value.islands()) {
                ObjectNode item = islands.addObject();
                item.put("pointId", island.pointId());
                item.put("radiusBlocks", island.radiusBlocks());
                item.put("summitHeightBlocksAboveSea", island.summitHeightBlocksAboveSea());
            }
            node.set("submarineSaddleDepthBlocks", intRangeTree(value.submarineSaddleDepthBlocks()));
        } else if (parameters instanceof TerrainIntentV2.VolcanicCalderaParameters value) {
            node.put("rimRadiusBlocks", value.rimRadiusBlocks());
            node.put("rimReliefBlocks", value.rimReliefBlocks());
            node.put("craterFloorDepthBlocks", value.craterFloorDepthBlocks());
            node.put("breachDirection", value.breachDirection().name());
        } else if (parameters instanceof TerrainIntentV2.LavaFlowParameters value) {
            node.set("widthBlocks", intRangeTree(value.widthBlocks()));
            node.set("surfaceRoughness01", fixedNode(value.surfaceRoughnessMillionths()));
        }
        return node;
    }

    private ObjectNode constraintTree(TerrainIntentV2.Constraint constraint) {
        ObjectNode node = mapper.createObjectNode(); node.put("id", constraint.id()); node.put("strength", constraint.strength().name());
        if (constraint instanceof TerrainIntentV2.MetricRangeConstraint metric) {
            node.put("kind", "METRIC_RANGE"); node.put("subject", metric.subject()); node.put("metric", metric.metric());
            node.set("range", fixedRangeTree(metric.range())); node.set("tolerance", fixedNode(metric.toleranceMillionths()));
        } else if (constraint instanceof TerrainIntentV2.EdgeClassificationConstraint edge) {
            node.put("kind", "EDGE_CLASSIFICATION"); node.put("subject", edge.subject()); ObjectNode parameters = node.putObject("parameters");
            parameters.put("edge", edge.edge().name()); parameters.put("classification", edge.classification().name()); parameters.set("minimumShare01", fixedNode(edge.minimumShareMillionths()));
        }
        if (constraint.strength() == TerrainIntentV2.Strength.SOFT) node.set("weight", fixedNode(constraint.weightMillionths()));
        return node;
    }

    private ObjectNode provenanceTree(TerrainIntentV2.Provenance provenance) {
        ObjectNode node = mapper.createObjectNode(); node.put("source", provenance.source().name()); node.put("sourceId", provenance.sourceId());
        node.set("confidence", fixedNode(provenance.confidenceMillionths())); node.put("confirmationState", provenance.confirmationState().name()); return node;
    }

    private TerrainIntentV2.Provenance parseProvenance(JsonNode node) {
        return new TerrainIntentV2.Provenance(
                enumValue(node, "source", TerrainIntentV2.ProvenanceSource.class), node.path("sourceId").textValue(),
                fixedInt(node.path("confidence")), enumValue(node, "confirmationState", TerrainIntentV2.ConfirmationState.class));
    }

    private TerrainIntentV2.Point2 parsePoint(JsonNode node) { return new TerrainIntentV2.Point2(fixedInt(node.get(0)), fixedInt(node.get(1))); }
    private List<TerrainIntentV2.Point2> parsePoints(JsonNode node) { List<TerrainIntentV2.Point2> points = new ArrayList<>(); for (JsonNode point : node) points.add(parsePoint(point)); return points; }
    private TerrainIntentV2.IntRange intRange(JsonNode node) { return new TerrainIntentV2.IntRange(node.path("min").intValue(), node.path("max").intValue()); }
    private TerrainIntentV2.FixedRange fixedRange(JsonNode node) { return new TerrainIntentV2.FixedRange(fixed(node.path("min")), fixed(node.path("max"))); }
    private List<String> strings(JsonNode node) { List<String> values = new ArrayList<>(); node.forEach(value -> values.add(value.textValue())); return values; }
    private ArrayNode pointTree(TerrainIntentV2.Point2 point) { ArrayNode node = mapper.createArrayNode(); node.add(fixedNode(point.xMillionths())); node.add(fixedNode(point.zMillionths())); return node; }
    private ArrayNode pointsTree(List<TerrainIntentV2.Point2> points) { ArrayNode node = mapper.createArrayNode(); points.forEach(point -> node.add(pointTree(point))); return node; }
    private ArrayNode ringsTree(TerrainIntentV2.PolygonGeometry polygon) { ArrayNode rings = mapper.createArrayNode(); polygon.rings().forEach(ring -> rings.add(pointsTree(ring))); return rings; }
    private ObjectNode intRangeTree(TerrainIntentV2.IntRange range) { ObjectNode node = mapper.createObjectNode(); node.put("min", range.minimum()); node.put("max", range.maximum()); return node; }
    private ObjectNode fixedRangeTree(TerrainIntentV2.FixedRange range) { ObjectNode node = mapper.createObjectNode(); node.set("min", fixedNode(range.minimumMillionths())); node.set("max", fixedNode(range.maximumMillionths())); return node; }
    private JsonNode fixedNode(long millionths) { return mapper.getNodeFactory().numberNode(BigDecimal.valueOf(millionths, 6).stripTrailingZeros()); }

    private static long fixed(JsonNode node) {
        return node.decimalValue().setScale(6, RoundingMode.UNNECESSARY).movePointRight(6).longValueExact();
    }

    private static int fixedInt(JsonNode node) { return Math.toIntExact(fixed(node)); }

    private static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type) {
        return Enum.valueOf(type, node.path(field).textValue());
    }

    private static String optionalText(JsonNode node, String field) { return node.has(field) ? node.path(field).textValue() : ""; }
    private static void putOptional(ObjectNode node, String field, String value) { if (!value.isEmpty()) node.put(field, value); }

    private JsonNode readTree(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(LandformDataCodec.MAX_DOCUMENT_BYTES + 1));
            if (bytes.length > LandformDataCodec.MAX_DOCUMENT_BYTES) throw new IOException("document exceeds limit: " + path);
            JsonNode node = mapper.readTree(bytes); if (node == null) throw new IOException("document is empty: " + path); return node;
        }
    }

    private JsonNode readTree(String input, String documentName) throws IOException {
        if (input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > LandformDataCodec.MAX_DOCUMENT_BYTES) throw new IOException("document exceeds limit: " + documentName);
        JsonNode node = mapper.readTree(input); if (node == null) throw new IOException("document is empty: " + documentName); return node;
    }
}
