package com.github.nankotsu029.landformcraft.format.v2;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.v2.CanonicalTerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Strict codec for the explicitly discriminated CANONICAL_V2 TerrainIntent projection. */
public final class CanonicalTerrainIntentCodecV2 {
    public static final String SCHEMA = "terrain-intent-v2-canonical.schema.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final StructuredDataValidator validator = new StructuredDataValidator();
    private final LandformV2DataCodec legacy = new LandformV2DataCodec();

    public CanonicalTerrainIntentV2 read(Path path) throws IOException {
        return read(readBounded(path), path.toString());
    }

    public CanonicalTerrainIntentV2 read(String input, String documentName) throws IOException {
        JsonNode node = mapper.readTree(input);
        if (node == null || !node.isObject()) {
            throw new IOException("canonical TerrainIntent must be an object: " + documentName);
        }
        validator.validate(SCHEMA, documentName, node);
        return parse((ObjectNode) node, documentName);
    }

    public String canonical(CanonicalTerrainIntentV2 intent) {
        return CanonicalJsonV2.string(tree(intent));
    }

    public String checksum(CanonicalTerrainIntentV2 intent) {
        return CanonicalJsonV2.checksum(tree(intent));
    }

    /** Writes a new file, strict-reads it, and atomically publishes it without overwrite. */
    public void write(Path path, CanonicalTerrainIntentV2 intent) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(absolute.getParent(), "canonical intent path requires a parent");
        Files.createDirectories(parent);
        if (Files.exists(absolute)) {
            throw new IOException("canonical intent target already exists: " + absolute);
        }
        byte[] bytes = canonical(intent).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > LandformDataCodec.MAX_DOCUMENT_BYTES) {
            throw new IOException("canonical TerrainIntent exceeds document budget");
        }
        Path temporary = parent.resolve("." + absolute.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            CanonicalTerrainIntentV2 verified = read(temporary);
            if (!intent.equals(verified)) {
                throw new IOException("canonical TerrainIntent strict read-back mismatch");
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("canonical TerrainIntent atomic publish is not supported", exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private CanonicalTerrainIntentV2 parse(ObjectNode root, String documentName) throws IOException {
        ObjectNode legacyRoot = root.deepCopy();
        legacyRoot.remove("featureProjection");
        ArrayNode flattened = mapper.createArrayNode();
        Map<String, ObjectNode> parents = new HashMap<>();
        Map<String, ObjectNode> children = new HashMap<>();
        for (JsonNode raw : root.path("features")) {
            ObjectNode canonicalFeature = (ObjectNode) raw;
            String id = canonicalFeature.path("id").asText();
            parents.put(id, canonicalFeature);
            flattened.add(toLegacyParent(canonicalFeature));
            for (JsonNode childRaw : canonicalFeature.path("children")) {
                ObjectNode child = (ObjectNode) childRaw;
                String childId = child.path("id").asText();
                if (children.putIfAbsent(childId, child) != null) {
                    throw new IOException("duplicate canonical child id: " + childId);
                }
                flattened.add(toLegacyChild(child));
            }
        }
        legacyRoot.set("features", flattened);
        TerrainIntentV2 validation = legacy.readTerrainIntent(
                CanonicalJsonV2.string(legacyRoot), documentName + "#legacy-validation-projection");
        Map<String, TerrainIntentV2.Feature> parsed = new HashMap<>();
        for (TerrainIntentV2.Feature feature : validation.features()) parsed.put(feature.id(), feature);

        List<CanonicalTerrainIntentV2.Feature> canonicalFeatures = new ArrayList<>();
        for (JsonNode raw : root.path("features")) {
            ObjectNode source = (ObjectNode) raw;
            TerrainIntentV2.Feature parsedParent = parsed.get(source.path("id").asText());
            TerrainIntentV2.FeatureKind targetKind = enumValue(
                    source, "kind", TerrainIntentV2.FeatureKind.class);
            List<CanonicalTerrainIntentV2.Child> canonicalChildren = new ArrayList<>();
            for (JsonNode childRaw : source.path("children")) {
                ObjectNode childNode = (ObjectNode) childRaw;
                TerrainIntentV2.Feature parsedChild = parsed.get(childNode.path("id").asText());
                canonicalChildren.add(new CanonicalTerrainIntentV2.Child(
                        parsedChild.id(),
                        enumValue(childNode, "childKind", CanonicalTerrainIntentV2.ChildKind.class),
                        parsedChild.geometry(), parsedChild.parameters(), parsedChild.priority(),
                        parsedChild.provenance(), optionalSeedBinding(childNode)));
            }
            canonicalFeatures.add(new CanonicalTerrainIntentV2.Feature(
                    parsedParent.id(), targetKind, parsedParent.geometry(),
                    canonicalParameters(targetKind, source.path("parameters"), parsedParent.parameters()),
                    parsedParent.priority(), parsedParent.provenance(), canonicalChildren,
                    optionalSeedBinding(source)));
        }
        return new CanonicalTerrainIntentV2(
                validation.intentVersion(), CanonicalTerrainIntentV2.FeatureProjection.CANONICAL_V2,
                validation.intentId(), validation.theme(), validation.coordinateSystem(), canonicalFeatures,
                validation.relations(), validation.constraints(), validation.environment(),
                validation.mapReferences(), validation.structures(), validation.provenance());
    }

    private ObjectNode tree(CanonicalTerrainIntentV2 intent) {
        TerrainIntentV2 flattened = flattened(intent);
        ObjectNode root;
        try {
            root = (ObjectNode) mapper.readTree(legacy.canonicalTerrainIntent(flattened));
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory legacy projection could not be parsed", exception);
        }
        root.put("featureProjection", CanonicalTerrainIntentV2.FeatureProjection.CANONICAL_V2.name());
        Map<String, ObjectNode> legacyFeatures = new HashMap<>();
        for (JsonNode raw : root.path("features")) {
            ObjectNode feature = (ObjectNode) raw;
            legacyFeatures.put(feature.path("id").asText(), feature);
        }
        ArrayNode features = mapper.createArrayNode();
        for (CanonicalTerrainIntentV2.Feature feature : intent.features()) {
            ObjectNode item = legacyFeatures.get(feature.id()).deepCopy();
            item.put("kind", feature.kind().name());
            item.set("parameters", canonicalParametersTree(feature.parameters(), item.path("parameters")));
            if (!feature.children().isEmpty()) {
                ArrayNode childArray = item.putArray("children");
                for (CanonicalTerrainIntentV2.Child child : feature.children()) {
                    ObjectNode childNode = legacyFeatures.get(child.id()).deepCopy();
                    childNode.remove("kind");
                    childNode.put("childKind", child.childKind().name());
                    if (child.legacySeedBinding() != null) {
                        childNode.set("legacySeedBinding", seedBindingTree(child.legacySeedBinding()));
                    }
                    childArray.add(childNode);
                }
            }
            if (feature.legacySeedBinding() != null) {
                item.set("legacySeedBinding", seedBindingTree(feature.legacySeedBinding()));
            }
            features.add(item);
        }
        root.set("features", features);
        validator.validate(SCHEMA, "canonical TerrainIntent writer", root);
        return root;
    }

    private TerrainIntentV2 flattened(CanonicalTerrainIntentV2 intent) {
        List<TerrainIntentV2.Feature> features = new ArrayList<>();
        for (CanonicalTerrainIntentV2.Feature feature : intent.features()) {
            features.add(new TerrainIntentV2.Feature(feature.id(), feature.parameters().validationKind(),
                    feature.geometry(), feature.parameters().validationParameters(), feature.priority(),
                    feature.provenance()));
            for (CanonicalTerrainIntentV2.Child child : feature.children()) {
                features.add(new TerrainIntentV2.Feature(child.id(), child.childKind().legacyKind(),
                        child.geometry(), child.parameters(), child.priority(), child.provenance()));
            }
        }
        return new TerrainIntentV2(intent.intentVersion(), intent.intentId(), intent.theme(),
                intent.coordinateSystem(), features, intent.relations(), intent.constraints(),
                intent.environment(), intent.mapReferences(), intent.structures(), intent.provenance());
    }

    private ObjectNode toLegacyParent(ObjectNode canonical) throws IOException {
        ObjectNode result = canonical.deepCopy();
        result.remove("children");
        result.remove("legacySeedBinding");
        TerrainIntentV2.FeatureKind target = enumValue(canonical, "kind", TerrainIntentV2.FeatureKind.class);
        JsonNode parameters = canonical.path("parameters");
        LegacyParameterProjection projection = legacyParameters(target, parameters);
        result.put("kind", projection.sourceKind().name());
        result.set("parameters", projection.parameters());
        return result;
    }

    private ObjectNode toLegacyChild(ObjectNode canonical) throws IOException {
        ObjectNode result = canonical.deepCopy();
        result.remove("childKind");
        result.remove("legacySeedBinding");
        CanonicalTerrainIntentV2.ChildKind childKind = enumValue(
                canonical, "childKind", CanonicalTerrainIntentV2.ChildKind.class);
        result.put("kind", childKind.legacyKind().name());
        return result;
    }

    private LegacyParameterProjection legacyParameters(
            TerrainIntentV2.FeatureKind target,
            JsonNode parameters
    ) throws IOException {
        ObjectNode copy = ((ObjectNode) parameters).deepCopy();
        return switch (target) {
            case RIVER -> {
                String morphology = requiredText(copy, "morphology");
                String channel = requiredText(copy, "channelSubtype");
                if ("MEANDERING".equals(morphology) && "DEFAULT".equals(channel)) {
                    yield new LegacyParameterProjection(TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                            requiredObject(copy, "meanderingParameters").deepCopy());
                }
                copy.remove("morphology"); copy.remove("channelSubtype");
                yield new LegacyParameterProjection("BEDROCK".equals(channel)
                        ? TerrainIntentV2.FeatureKind.BEDROCK_RIVER : TerrainIntentV2.FeatureKind.RIVER, copy);
            }
            case MOUNTAIN_RANGE -> specialized(copy, "profile", "profileParameters",
                    Map.of("ALPINE", TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE,
                            "GLACIAL", TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE), target);
            case ARCHIPELAGO -> specialized(copy, "origin", "volcanicParameters",
                    Map.of("VOLCANIC", TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO), target);
            case MARSH -> specialized(copy, "wetlandType", "mangroveParameters",
                    Map.of("MANGROVE", TerrainIntentV2.FeatureKind.MANGROVE_WETLAND), target);
            case PLAIN -> specialized(copy, "context", "backshoreParameters",
                    Map.of("BACKSHORE", TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS), target);
            case LAKE -> specialized(copy, "origin", "riverCutoffParameters",
                    Map.of("RIVER_CUTOFF", TerrainIntentV2.FeatureKind.OXBOW_LAKE), target);
            default -> new LegacyParameterProjection(target, copy);
        };
    }

    private LegacyParameterProjection specialized(
            ObjectNode parameters,
            String discriminator,
            String payload,
            Map<String, TerrainIntentV2.FeatureKind> specializations,
            TerrainIntentV2.FeatureKind defaultKind
    ) throws IOException {
        String value = requiredText(parameters, discriminator);
        TerrainIntentV2.FeatureKind source = specializations.get(value);
        if (source != null) {
            return new LegacyParameterProjection(source, requiredObject(parameters, payload).deepCopy());
        }
        parameters.remove(discriminator);
        return new LegacyParameterProjection(defaultKind, parameters);
    }

    private CanonicalTerrainIntentV2.Parameters canonicalParameters(
            TerrainIntentV2.FeatureKind target,
            JsonNode canonical,
            TerrainIntentV2.FeatureParameters parsed
    ) throws IOException {
        return switch (target) {
            case RIVER -> {
                var morphology = enumValue(canonical, "morphology", CanonicalTerrainIntentV2.Morphology.class);
                var channel = enumValue(canonical, "channelSubtype", CanonicalTerrainIntentV2.ChannelSubtype.class);
                yield new CanonicalTerrainIntentV2.RiverParameters(morphology, channel,
                        parsed instanceof TerrainIntentV2.RiverParameters value ? value : null,
                        parsed instanceof TerrainIntentV2.MeanderingRiverParameters value ? value : null);
            }
            case MOUNTAIN_RANGE -> {
                var profile = enumValue(canonical, "profile", CanonicalTerrainIntentV2.MountainProfile.class);
                yield new CanonicalTerrainIntentV2.MountainRangeParameters(profile,
                        parsed instanceof TerrainIntentV2.MountainRangeParameters value ? value : null,
                        parsed instanceof TerrainIntentV2.MountainParameters value ? value : null);
            }
            case ARCHIPELAGO -> {
                var origin = enumValue(canonical, "origin", CanonicalTerrainIntentV2.ArchipelagoOrigin.class);
                yield new CanonicalTerrainIntentV2.ArchipelagoParameters(origin,
                        parsed instanceof TerrainIntentV2.ArchipelagoParameters value ? value : null,
                        parsed instanceof TerrainIntentV2.VolcanicArchipelagoParameters value ? value : null);
            }
            case MARSH -> {
                var type = enumValue(canonical, "wetlandType", CanonicalTerrainIntentV2.WetlandType.class);
                yield new CanonicalTerrainIntentV2.MarshParameters(type,
                        parsed instanceof TerrainIntentV2.MarshParameters value ? value : null,
                        parsed instanceof TerrainIntentV2.MangroveWetlandParameters value ? value : null);
            }
            case PLAIN -> {
                var context = enumValue(canonical, "context", CanonicalTerrainIntentV2.PlainContext.class);
                yield new CanonicalTerrainIntentV2.PlainParameters(context,
                        parsed instanceof TerrainIntentV2.PlainParameters value ? value : null,
                        parsed instanceof TerrainIntentV2.BackshorePlainsParameters value ? value : null);
            }
            case LAKE -> {
                var origin = enumValue(canonical, "origin", CanonicalTerrainIntentV2.LakeOrigin.class);
                yield new CanonicalTerrainIntentV2.LakeParameters(origin,
                        parsed instanceof TerrainIntentV2.LakeParameters value ? value : null,
                        parsed instanceof TerrainIntentV2.OxbowLakeParameters value ? value : null);
            }
            default -> new CanonicalTerrainIntentV2.UnchangedParameters(target, parsed);
        };
    }

    private ObjectNode canonicalParametersTree(
            CanonicalTerrainIntentV2.Parameters parameters,
            JsonNode legacyParameters
    ) {
        ObjectNode result = ((ObjectNode) legacyParameters).deepCopy();
        if (parameters instanceof CanonicalTerrainIntentV2.RiverParameters value) {
            result = mapper.createObjectNode();
            result.put("morphology", value.morphology().name());
            result.put("channelSubtype", value.channelSubtype().name());
            if (value.defaultParameters() != null) result.setAll((ObjectNode) legacyParameters);
            if (value.meanderingParameters() != null) result.set("meanderingParameters", legacyParameters);
        } else if (parameters instanceof CanonicalTerrainIntentV2.MountainRangeParameters value) {
            result = specializationTree("profile", value.profile().name(), "profileParameters",
                    value.defaultParameters() != null, legacyParameters);
        } else if (parameters instanceof CanonicalTerrainIntentV2.ArchipelagoParameters value) {
            result = specializationTree("origin", value.origin().name(), "volcanicParameters",
                    value.defaultParameters() != null, legacyParameters);
        } else if (parameters instanceof CanonicalTerrainIntentV2.MarshParameters value) {
            result = specializationTree("wetlandType", value.wetlandType().name(), "mangroveParameters",
                    value.defaultParameters() != null, legacyParameters);
        } else if (parameters instanceof CanonicalTerrainIntentV2.PlainParameters value) {
            result = specializationTree("context", value.context().name(), "backshoreParameters",
                    value.defaultParameters() != null, legacyParameters);
        } else if (parameters instanceof CanonicalTerrainIntentV2.LakeParameters value) {
            result = specializationTree("origin", value.origin().name(), "riverCutoffParameters",
                    value.defaultParameters() != null, legacyParameters);
        }
        return result;
    }

    private ObjectNode specializationTree(
            String discriminator,
            String value,
            String payload,
            boolean defaultValue,
            JsonNode legacyParameters
    ) {
        ObjectNode result = defaultValue ? ((ObjectNode) legacyParameters).deepCopy() : mapper.createObjectNode();
        result.put(discriminator, value);
        if (!defaultValue) result.set(payload, legacyParameters);
        return result;
    }

    private CanonicalTerrainIntentV2.LegacySeedBinding optionalSeedBinding(ObjectNode node) throws IOException {
        JsonNode seed = node.path("legacySeedBinding");
        if (seed.isMissingNode()) return null;
        return new CanonicalTerrainIntentV2.LegacySeedBinding(
                enumValue(seed, "sourceKind", TerrainIntentV2.FeatureKind.class),
                requiredText(seed, "derivationVersion"), requiredText(seed, "seedNamespace"),
                requiredText(seed, "moduleId"), requiredText(seed, "moduleVersion"),
                requiredText(seed, "generatorVersion"));
    }

    private ObjectNode seedBindingTree(CanonicalTerrainIntentV2.LegacySeedBinding seed) {
        ObjectNode result = mapper.createObjectNode();
        result.put("sourceKind", seed.sourceKind().name());
        result.put("derivationVersion", seed.derivationVersion());
        result.put("seedNamespace", seed.seedNamespace());
        result.put("moduleId", seed.moduleId());
        result.put("moduleVersion", seed.moduleVersion());
        result.put("generatorVersion", seed.generatorVersion());
        return result;
    }

    private String readBounded(Path path) throws IOException {
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(Math.toIntExact(LandformDataCodec.MAX_DOCUMENT_BYTES + 1));
        }
        if (bytes.length > LandformDataCodec.MAX_DOCUMENT_BYTES) {
            throw new IOException("canonical TerrainIntent exceeds document budget: " + path);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static ObjectNode requiredObject(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isObject()) throw new IOException("required canonical object is missing: " + field);
        return (ObjectNode) value;
    }

    private static String requiredText(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IOException("required canonical text is missing: " + field);
        }
        return value.textValue();
    }

    private static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type)
            throws IOException {
        try {
            return Enum.valueOf(type, requiredText(node, field));
        } catch (IllegalArgumentException exception) {
            throw new IOException("unknown canonical enum " + field, exception);
        }
    }

    private record LegacyParameterProjection(
            TerrainIntentV2.FeatureKind sourceKind,
            ObjectNode parameters
    ) { }
}
