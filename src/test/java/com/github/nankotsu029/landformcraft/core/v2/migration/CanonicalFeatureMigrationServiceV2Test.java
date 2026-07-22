package com.github.nankotsu029.landformcraft.core.v2.migration;

import com.github.nankotsu029.landformcraft.format.v2.CanonicalTerrainIntentCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.CanonicalTerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalFeatureMigrationServiceV2Test {
    private static final Path MEANDERING = Path.of(
            "examples/v2/hydrology/meandering-river.terrain-intent-v2.json");
    private static final Path CANONICAL_EXAMPLE = Path.of(
            "examples/v2/catalog/meandering-river.terrain-intent-v2-canonical.json");
    private static final List<Path> MAPPING_FIXTURES = List.of(
            MEANDERING,
            Path.of("examples/v2/diagnostic/scenarios/fjord.terrain-intent-v2.json"),
            Path.of("examples/v2/diagnostic/scenarios/volcanic-archipelago.terrain-intent-v2.json"),
            Path.of("examples/v2/diagnostic/scenarios/mangrove-wetland.terrain-intent-v2.json"),
            Path.of("examples/v2/diagnostic/harbor-cove-64.terrain-intent-v2.json"),
            Path.of("examples/v2/foundation/oxbow-lake-positive.terrain-intent-v2.json"),
            Path.of("examples/v2/foundation/atoll-composition.terrain-intent-v2.json"),
            Path.of("examples/v2/hydrology/volcanic-archipelago-skeleton.terrain-intent-v2.json"));

    private final LandformV2DataCodec legacyCodec = new LandformV2DataCodec();
    private final CanonicalTerrainIntentCodecV2 canonicalCodec = new CanonicalTerrainIntentCodecV2();
    private final CanonicalFeatureMigrationServiceV2 service = new CanonicalFeatureMigrationServiceV2();

    @Test
    void migratesCompatibilityPairAndKeepsSeedTupleInCanonicalSemantics() throws Exception {
        TerrainIntentV2 source = legacyCodec.readTerrainIntent(MEANDERING);
        var result = service.migrateLegacy(source, MEANDERING.getFileName().toString(), seeds(source));
        CanonicalTerrainIntentV2 expected = canonicalCodec.read(CANONICAL_EXAMPLE);

        assertEquals(expected, result.target());
        assertEquals(CanonicalTerrainIntentV2.FeatureProjection.CANONICAL_V2,
                result.target().featureProjection());
        assertEquals(TerrainIntentV2.FeatureKind.RIVER, result.target().features().getFirst().kind());
        assertEquals(CanonicalTerrainIntentV2.Morphology.MEANDERING,
                ((CanonicalTerrainIntentV2.RiverParameters) result.target().features().getFirst().parameters())
                        .morphology());
        assertNotEquals(result.report().sourceCanonicalChecksum(), result.report().targetCanonicalChecksum());
        assertEquals(canonicalCodec.checksum(result.target()), result.report().targetCanonicalChecksum());

        CanonicalTerrainIntentV2 withoutBinding = new CanonicalTerrainIntentV2(
                result.target().intentVersion(), result.target().featureProjection(), result.target().intentId(),
                result.target().theme(), result.target().coordinateSystem(),
                result.target().features().stream().map(feature -> new CanonicalTerrainIntentV2.Feature(
                        feature.id(), feature.kind(), feature.geometry(), feature.parameters(), feature.priority(),
                        feature.provenance(), feature.children(), null)).toList(),
                result.target().relations(), result.target().constraints(), result.target().environment(),
                result.target().mapReferences(), result.target().structures(), result.target().provenance());
        assertNotEquals(canonicalCodec.checksum(result.target()), canonicalCodec.checksum(withoutBinding));
    }

    @Test
    void everyApprovedLegacyMappingIsExercisedByCompatibilityFixtures() throws Exception {
        Set<TerrainIntentV2.FeatureKind> migrated = EnumSet.noneOf(TerrainIntentV2.FeatureKind.class);
        for (Path fixture : MAPPING_FIXTURES) {
            TerrainIntentV2 source = legacyCodec.readTerrainIntent(fixture);
            var result = service.migrateLegacy(source, fixture.getFileName().toString(), seeds(source));
            result.report().features().forEach(entry -> migrated.add(entry.sourceKind()));
            assertTrue(result.target().features().stream().noneMatch(feature ->
                    CanonicalTerrainIntentV2.LEGACY_SOURCE_KINDS.contains(feature.kind())));
        }
        TerrainIntentV2 snowy = legacyCodec.readTerrainIntent(Path.of(
                "examples/v2/diagnostic/scenarios/snowy-mountains.terrain-intent-v2.json"));
        assertThrows(IOException.class, () -> service.migrateLegacy(
                snowy, "unsupported-alpine-cirque-owner", seeds(snowy)));
        TerrainIntentV2 alpineOnly = copy(snowy, List.of(snowy.features().getFirst()), List.of());
        service.migrateLegacy(alpineOnly, "alpine-parent", seeds(alpineOnly)).report().features()
                .forEach(entry -> migrated.add(entry.sourceKind()));
        TerrainIntentV2 fjord = legacyCodec.readTerrainIntent(Path.of(
                "examples/v2/diagnostic/scenarios/fjord.terrain-intent-v2.json"));
        TerrainIntentV2.Feature glacial = fjord.features().stream().filter(feature ->
                feature.kind() == TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE).findFirst().orElseThrow();
        TerrainIntentV2 glacialCirque = copy(snowy, List.of(glacial, snowy.features().get(1)), List.of(
                new TerrainIntentV2.Relation("cirques-cut-glacial-ridge",
                        TerrainIntentV2.RelationKind.CARVES_FLANK_OF, "feature:north-cirques",
                        "feature:" + glacial.id(), TerrainIntentV2.Strength.HARD)));
        service.migrateLegacy(glacialCirque, "glacial-cirque", seeds(glacialCirque)).report().features()
                .forEach(entry -> migrated.add(entry.sourceKind()));
        TerrainIntentV2 karst = legacyCodec.readTerrainIntent(Path.of(
                "examples/v2/foundation/karst-hydrology-positive.terrain-intent-v2.json"));
        List<TerrainIntentV2.Relation> floodedRelations = new java.util.ArrayList<>(karst.relations());
        floodedRelations.add(new TerrainIntentV2.Relation(
                "flooded-hook-within-cave", TerrainIntentV2.RelationKind.WITHIN,
                "feature:flooded-cenote-hook", "feature:cave.fixture-network", TerrainIntentV2.Strength.HARD));
        TerrainIntentV2 flooded = copy(karst, karst.features(), floodedRelations);
        service.migrateLegacy(flooded, "flooded-cave", seeds(flooded)).report().features()
                .forEach(entry -> migrated.add(entry.sourceKind()));
        TerrainIntentV2 meandering = legacyCodec.readTerrainIntent(MEANDERING);
        TerrainIntentV2.Feature river = meandering.features().getFirst();
        TerrainIntentV2.Feature bedrock = new TerrainIntentV2.Feature(
                river.id(), TerrainIntentV2.FeatureKind.BEDROCK_RIVER, river.geometry(),
                new TerrainIntentV2.NoParameters(), river.priority(), river.provenance());
        TerrainIntentV2 bedrockSource = copy(meandering, List.of(bedrock), List.of());
        service.migrateLegacy(bedrockSource, "bedrock-river", seeds(bedrockSource)).report().features()
                .forEach(entry -> migrated.add(entry.sourceKind()));
        Set<TerrainIntentV2.FeatureKind> expected = EnumSet.copyOf(CanonicalTerrainIntentV2.LEGACY_SOURCE_KINDS);
        assertEquals(expected, migrated);
    }

    @Test
    void projectionIsExplicitMigrationIsIdempotentAndPublishIsStrict(@TempDir Path directory) throws Exception {
        TerrainIntentV2 source = legacyCodec.readTerrainIntent(MEANDERING);
        assertThrows(IOException.class, () -> service.migrate(
                MEANDERING, CanonicalTerrainIntentV2.FeatureProjection.CANONICAL_V2,
                "wrong-projection", seeds(source)));
        assertThrows(IOException.class, () -> service.migrateLegacy(
                source, "missing-seed", Map.of()));
        Path sourceLink = directory.resolve("source-link.json");
        Files.createSymbolicLink(sourceLink, MEANDERING.toAbsolutePath());
        assertThrows(IOException.class, () -> service.migrate(
                sourceLink, CanonicalTerrainIntentV2.FeatureProjection.LEGACY_V2,
                "symlink-source", seeds(source)));

        var migrated = service.migrateLegacy(source, "meandering-source", seeds(source));
        var published = service.publish(migrated, directory.resolve("published"));
        assertEquals(migrated, published.verified());
        try (var files = Files.list(published.root())) {
            assertEquals(Set.of(CanonicalFeatureMigrationServiceV2.TARGET_FILE,
                            CanonicalFeatureMigrationServiceV2.REPORT_FILE),
                    files.map(path -> path.getFileName().toString())
                            .collect(java.util.stream.Collectors.toSet()));
        }
        assertThrows(IOException.class, () -> service.publish(migrated, published.root()));

        var canonical = service.migrate(published.root().resolve(CanonicalFeatureMigrationServiceV2.TARGET_FILE),
                CanonicalTerrainIntentV2.FeatureProjection.CANONICAL_V2, "canonical-source", Map.of());
        assertEquals(migrated.target(), canonical.target());
        assertEquals(canonical.report().sourceCanonicalChecksum(), canonical.report().targetCanonicalChecksum());

        Files.writeString(published.root().resolve("extra.json"), "{}");
        assertThrows(IOException.class, () -> service.verifyBundle(published.root()));
        Files.delete(published.root().resolve("extra.json"));
        Path report = published.root().resolve(CanonicalFeatureMigrationServiceV2.REPORT_FILE);
        Files.writeString(report, Files.readString(report).replaceFirst("\\{", "{\"unknown\":true,"));
        assertThrows(IOException.class, () -> service.verifyBundle(published.root()));
        assertFalse(Files.exists(directory.resolve("unpublished")));
    }

    @Test
    void canonicalCodecRejectsUnknownTrailingAndLegacyProjection() throws Exception {
        String valid = Files.readString(CANONICAL_EXAMPLE);
        assertThrows(IOException.class, () -> canonicalCodec.read(valid + "{}", "trailing"));
        assertThrows(Exception.class, () -> canonicalCodec.read(
                valid.replaceFirst("\\{", "{\"unknown\":true,"), "unknown"));
        assertThrows(Exception.class, () -> canonicalCodec.read(
                valid.replace("CANONICAL_V2", "LEGACY_V2"), "legacy-discriminator"));
        assertThrows(Exception.class, () -> canonicalCodec.read(
                valid.replace("MEANDERING", "FUTURE_MORPHOLOGY"), "future-discriminator"));
    }

    @Test
    void migrationChecksumIsStableAcrossLocaleTimezoneAndThreads() throws Exception {
        TerrainIntentV2 source = legacyCodec.readTerrainIntent(MEANDERING);
        Map<String, CanonicalTerrainIntentV2.LegacySeedBinding> bindings = seeds(source);
        String baseline = service.migrateLegacy(source, "determinism-source", bindings)
                .report().targetCanonicalChecksum();
        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            try (var executor = Executors.newFixedThreadPool(4)) {
                List<Callable<String>> tasks = java.util.stream.IntStream.range(0, 4)
                        .mapToObj(index -> (Callable<String>) () -> service.migrateLegacy(
                                source, "determinism-source", bindings).report().targetCanonicalChecksum())
                        .toList();
                for (var future : executor.invokeAll(tasks)) assertEquals(baseline, future.get());
            }
        } finally {
            Locale.setDefault(locale);
            TimeZone.setDefault(zone);
        }
    }

    private static Map<String, CanonicalTerrainIntentV2.LegacySeedBinding> seeds(TerrainIntentV2 source) {
        Map<String, CanonicalTerrainIntentV2.LegacySeedBinding> result = new HashMap<>();
        for (TerrainIntentV2.Feature feature : source.features()) {
            if (!CanonicalTerrainIntentV2.LEGACY_SOURCE_KINDS.contains(feature.kind())) continue;
            result.put(feature.id(), new CanonicalTerrainIntentV2.LegacySeedBinding(
                    feature.kind(), "sha256-tagged-v1", "legacy.v2.feature/" + feature.id(),
                    "landformcraft.legacy.catalog", "2.0.0", "3.0.0-phase6"));
        }
        return result;
    }

    private static TerrainIntentV2 copy(
            TerrainIntentV2 source,
            List<TerrainIntentV2.Feature> features,
            List<TerrainIntentV2.Relation> relations
    ) {
        return new TerrainIntentV2(source.intentVersion(), source.intentId(), source.theme(),
                source.coordinateSystem(), features, relations, List.of(), source.environment(),
                source.mapReferences(), source.structures(), source.provenance());
    }
}
