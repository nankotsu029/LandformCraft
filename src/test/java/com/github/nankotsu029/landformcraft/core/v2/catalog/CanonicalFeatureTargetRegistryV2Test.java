package com.github.nankotsu029.landformcraft.core.v2.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.model.v2.CanonicalTerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalFeatureTargetRegistryV2Test {
    private static final Path SCHEMA = Path.of("schemas/terrain-intent-v2-canonical.schema.json");
    private static final Path DOCUMENT = Path.of("docs/design-v2/canonical-feature-target-registry.md");
    private static final String START = "<!-- canonical-feature-target-registry-v1:start -->";
    private static final String END = "<!-- canonical-feature-target-registry-v1:end -->";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exactApprovedProjectionIsFailClosed() throws Exception {
        CanonicalFeatureTargetRegistryV2 registry = current();
        registry.requireConsistent();
        assertEquals(60, registry.entries().size());
        assertEquals(46, registry.canonicalSchemaKinds().size());
        assertEquals(14, registry.entries().stream().filter(entry ->
                entry.disposition() != CanonicalFeatureTargetRegistryV2.Disposition.DIRECT).count());
        assertEquals(CanonicalFeatureTargetRegistryV2.Disposition.PARENT_SUBTYPE,
                registry.entry(TerrainIntentV2.FeatureKind.MEANDERING_RIVER).disposition());
        assertEquals(CanonicalFeatureTargetRegistryV2.Disposition.PARENT_OVERLAY,
                registry.entry(TerrainIntentV2.FeatureKind.FLOODED_CAVE).disposition());

        Set<String> drift = new HashSet<>(schemaKinds());
        drift.remove("RIVER");
        drift.add("FUTURE_KIND");
        CanonicalFeatureTargetRegistryV2 broken = CanonicalFeatureTargetRegistryV2.project(drift);
        assertTrue(broken.differences().contains("canonical Schema missing kind: RIVER"));
        assertTrue(broken.differences().contains("canonical Schema declares unapproved kind: FUTURE_KIND"));
        assertThrows(IllegalStateException.class, broken::requireConsistent);
    }

    @Test
    void lifecycleIsOneWayWhileOperationalRollbackPreservesIt() throws Exception {
        CanonicalFeatureTargetRegistryV2 current = current();
        var deprecated = current.transition(TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                CanonicalFeatureTargetRegistryV2.LifecycleState.DEPRECATED_AUTHORING);
        var stopped = deprecated.withOperationalMode(TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                CanonicalFeatureTargetRegistryV2.OperationalMode.STOPPED);
        assertEquals(CanonicalFeatureTargetRegistryV2.LifecycleState.DEPRECATED_AUTHORING,
                stopped.entry(TerrainIntentV2.FeatureKind.MEANDERING_RIVER).lifecycleState());
        assertThrows(IllegalArgumentException.class, () -> stopped.transition(
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                CanonicalFeatureTargetRegistryV2.LifecycleState.CURRENT_PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> current.withOperationalMode(
                TerrainIntentV2.FeatureKind.RIVER, CanonicalFeatureTargetRegistryV2.OperationalMode.STOPPED));
        assertEquals(CanonicalFeatureTargetRegistryV2.LifecycleState.LEGACY_READABLE_ONLY,
                deprecated.transition(TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                        CanonicalFeatureTargetRegistryV2.LifecycleState.LEGACY_READABLE_ONLY)
                        .entry(TerrainIntentV2.FeatureKind.MEANDERING_RIVER).lifecycleState());
    }

    @Test
    void documentationProjectionAndChecksumAreStable() throws Exception {
        CanonicalFeatureTargetRegistryV2 registry = current();
        String text = Files.readString(DOCUMENT, StandardCharsets.UTF_8);
        int start = text.indexOf(START);
        int end = text.indexOf(END);
        assertTrue(start >= 0 && end > start);
        assertEquals(registry.documentationProjection(), text.substring(start + START.length(), end).strip());

        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(registry.projectionChecksum(), current().projectionChecksum());
        } finally {
            Locale.setDefault(locale);
            TimeZone.setDefault(zone);
        }
    }

    private CanonicalFeatureTargetRegistryV2 current() throws Exception {
        return CanonicalFeatureTargetRegistryV2.project(schemaKinds());
    }

    private Set<String> schemaKinds() throws Exception {
        JsonNode root = mapper.readTree(SCHEMA.toFile());
        Set<String> result = new HashSet<>();
        root.at("/$defs/featureKind/enum").forEach(value -> result.add(value.asText()));
        assertEquals(CanonicalTerrainIntentV2.AUTHORING_KINDS.stream().map(Enum::name).collect(
                java.util.stream.Collectors.toSet()), result);
        return result;
    }
}
