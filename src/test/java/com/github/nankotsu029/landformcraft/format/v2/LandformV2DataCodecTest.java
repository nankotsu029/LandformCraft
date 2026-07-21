package com.github.nankotsu029.landformcraft.format.v2;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandformV2DataCodecTest {
    private static final Path COASTAL = Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void parsesNormalizesAndCanonicallyRoundTripsCoastalFixture(@TempDir Path directory) throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        String canonical = codec.canonicalTerrainIntent(intent);
        Path copy = directory.resolve("intent-v2.json");

        codec.writeTerrainIntent(copy, intent);
        TerrainIntentV2 roundTrip = codec.readTerrainIntent(copy);

        assertEquals(TerrainIntentV2.VERSION, intent.intentVersion());
        assertEquals(5, intent.features().size());
        assertEquals(intent, roundTrip);
        assertEquals(canonical, Files.readString(copy));
        assertEquals(codec.terrainIntentChecksum(intent), codec.terrainIntentChecksum(roundTrip));
        assertEquals(intent.features().stream().map(TerrainIntentV2.Feature::id).sorted().toList(),
                intent.features().stream().map(TerrainIntentV2.Feature::id).toList());
    }

    @Test
    void canonicalJsonSortsObjectKeysAndNormalizesDecimalSpelling() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        var first = mapper.readTree("{\"z\":-0.000,\"a\":1e-6,\"items\":[2,1]}");
        var second = mapper.readTree("{\"items\":[2,1],\"a\":0.0000010,\"z\":0}");

        assertEquals("{\"a\":0.000001,\"items\":[2,1],\"z\":0}", CanonicalJsonV2.string(first));
        assertEquals(CanonicalJsonV2.string(first), CanonicalJsonV2.string(second));
        assertEquals(CanonicalJsonV2.checksum(first), CanonicalJsonV2.checksum(second));
    }

    @Test
    void allRequiredScenarioContractsParseAndRoundTrip() throws IOException {
        List<Path> fixtures;
        try (var files = Files.list(Path.of("examples/v2/diagnostic/scenarios"))) {
            fixtures = files.sorted().toList();
        }
        assertEquals(10, fixtures.size());
        for (Path fixture : fixtures) {
            TerrainIntentV2 intent = codec.readTerrainIntent(fixture);
            String canonical = codec.canonicalTerrainIntent(intent);
            TerrainIntentV2 roundTrip = codec.readTerrainIntent(canonical, fixture.toString());
            assertEquals(intent, roundTrip, fixture.toString());
            assertEquals(codec.terrainIntentChecksum(intent), codec.terrainIntentChecksum(roundTrip), fixture.toString());
        }
    }

    @Test
    void exactVersionDispatcherKeepsV1AndV2Separate() throws IOException {
        TerrainIntentVersionDispatcher dispatcher = new TerrainIntentVersionDispatcher();

        assertInstanceOf(VersionedTerrainIntent.V1.class,
                dispatcher.read(Path.of(
                        "src/main/resources/legacy/v1/fixtures/rocky-coast/terrain-intent.json")));
        assertInstanceOf(VersionedTerrainIntent.V2.class, dispatcher.read(COASTAL));

        String v2 = Files.readString(COASTAL);
        assertThrows(IOException.class, () -> dispatcher.read(v2.replace("\"intentVersion\": 2", "\"intentVersion\": 3"), "future-v2"));
        assertThrows(IOException.class, () -> dispatcher.read(v2.replaceFirst("\\{", "{\"schemaVersion\":1,"), "ambiguous"));
        assertThrows(IOException.class, () -> dispatcher.read(v2.replace("\"intentVersion\": 2,", ""), "missing"));
    }

    @Test
    void rejectsUnknownFieldsEnumsFeaturesDuplicateKeysAndFutureVersion() throws IOException {
        String valid = Files.readString(COASTAL);

        assertThrows(StructuredDataValidationException.class,
                () -> codec.readTerrainIntent(valid.replaceFirst("\\{", "{\"unknown\":true,"), "unknown-field"));
        assertThrows(StructuredDataValidationException.class,
                () -> codec.readTerrainIntent(valid.replace("\"sampling\": \"NEAREST\"", "\"sampling\": \"CUBIC\""), "unknown-enum"));
        assertThrows(StructuredDataValidationException.class,
                () -> codec.readTerrainIntent(valid.replace("\"kind\": \"SANDY_BEACH\"", "\"kind\": \"MAGIC_BEACH\""), "unknown-feature"));
        assertThrows(JsonParseException.class,
                () -> codec.readTerrainIntent(valid.replaceFirst("\\{", "{\"intentVersion\":2,"), "duplicate-key"));
        assertThrows(StructuredDataValidationException.class,
                () -> codec.readTerrainIntent(valid.replace("\"intentVersion\": 2", "\"intentVersion\": 9"), "future-version"));
        assertThrows(StructuredDataValidationException.class,
                () -> codec.readTerrainIntent(valid.replace(
                        "\"widthBlocks\": { \"min\": 20, \"max\": 55 }",
                        "\"widthBlocks\": { \"min\": 20, \"max\": 55 }, \"script\": \"run()\""), "free-parameter"));
    }

    @Test
    void rejectsMissingNonFiniteOverflowAndOutOfRangeSandyBeachParameters() throws IOException {
        String valid = Files.readString(COASTAL);

        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace("        \"foreshoreShare01\": 0.6,\n", ""), "missing-foreshore-share"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace("\"foreshoreShare01\": 0.6", "\"foreshoreShare01\": 0.95"),
                "out-of-range-foreshore-share"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace("\"endpointTaperBlocks\": 64", "\"endpointTaperBlocks\": 0"),
                "zero-endpoint-taper"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace("\"atDistance\": 40", "\"atDistance\": 64"),
                "nearshore-without-observation-margin"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace("\"shoreSlopeDegrees\": { \"min\": 2, \"max\": 8 }",
                        "\"shoreSlopeDegrees\": { \"min\": 0, \"max\": 8 }"), "zero-shore-slope"));
        assertThrows(IOException.class, () -> codec.readTerrainIntent(
                valid.replace("\"foreshoreShare01\": 0.6", "\"foreshoreShare01\": NaN"),
                "non-finite-foreshore-share"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace("\"foreshoreShare01\": 0.6", "\"foreshoreShare01\": 1e1000"),
                "overflow-foreshore-share"));
    }

    @Test
    void rejectsMissingUnknownAndOutOfRangeDeltaParameters() throws IOException {
        String valid = Files.readString(
                Path.of("examples/v2/hydrology/delta-distributary-fan.terrain-intent-v2.json"));

        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace(",\n        \"fanProfile\": \"APEX_TO_SEA_LINEAR\"", ""),
                "missing-delta-profile"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace("\"distributaryCount\": { \"min\": 4, \"max\": 8 }",
                        "\"distributaryCount\": { \"min\": 1, \"max\": 8 }"),
                "single-delta-branch"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace("\"fanProfile\": \"APEX_TO_SEA_LINEAR\"",
                        "\"fanProfile\": \"FUTURE_TIDAL_FAN\""),
                "future-delta-profile"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(
                valid.replace("\"fanReliefBlocks\": { \"min\": 2, \"max\": 14 }",
                        "\"fanReliefBlocks\": { \"min\": 2, \"max\": 14 }, \"script\": \"run()\""),
                "unknown-delta-parameter"));
    }

    @Test
    void rejectsInvalidGeometryReferencesCyclesAndHardConflicts() throws IOException {
        String valid = Files.readString(COASTAL);
        assertThrows(StructuredDataValidationException.class,
                () -> codec.readTerrainIntent(valid.replace(
                        "[[0.02, 0.42], [0.20, 0.35], [0.42, 0.41]]",
                        "[[0.02, 0.42]]"), "short-spline"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.readTerrainIntent(valid.replace(
                        "[[[0.62, 0.28], [0.94, 0.22], [1.0, 0.56], [0.72, 0.60], [0.62, 0.28]]]",
                        "[[[0.1, 0.1], [0.9, 0.9], [0.9, 0.1], [0.1, 0.9], [0.1, 0.1]]]"), "self-intersection"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.readTerrainIntent(valid.replace("feature:harbor-basin", "feature:missing-feature"), "unknown-reference"));

        TerrainIntentV2 intent = codec.readTerrainIntent(Path.of("examples/v2/diagnostic/scenarios/delta.terrain-intent-v2.json"));
        List<TerrainIntentV2.Relation> cyclicRelations = new ArrayList<>(intent.relations());
        cyclicRelations.add(new TerrainIntentV2.Relation(
                "delta-drains-to-river", TerrainIntentV2.RelationKind.DRAINS_TO,
                "feature:river-delta", "feature:main-river", TerrainIntentV2.Strength.HARD));
        assertThrows(IllegalArgumentException.class, () -> copy(intent, cyclicRelations, intent.constraints()));

        List<TerrainIntentV2.Constraint> conflicts = new ArrayList<>(intent.constraints());
        conflicts.add(new TerrainIntentV2.MetricRangeConstraint(
                "impossible-branch-count", TerrainIntentV2.Strength.HARD, "feature:river-delta",
                "ACTIVE_DISTRIBUTARY_COUNT", new TerrainIntentV2.FixedRange(20_000_000, 30_000_000), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> copy(intent, intent.relations(), conflicts));
    }

    @Test
    void rejectsDuplicateDomainIdsAndRelationEndpointTypeMismatch() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(
                Path.of("examples/v2/diagnostic/scenarios/delta.terrain-intent-v2.json"));

        List<TerrainIntentV2.Feature> duplicateFeatures = new ArrayList<>(intent.features());
        duplicateFeatures.add(intent.features().getFirst());
        assertThrows(IllegalArgumentException.class, () -> new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                duplicateFeatures, intent.relations(), intent.constraints(), intent.environment(),
                intent.mapReferences(), intent.structures(), intent.provenance()));

        List<TerrainIntentV2.Relation> duplicateRelations = new ArrayList<>(intent.relations());
        duplicateRelations.add(intent.relations().getFirst());
        assertThrows(IllegalArgumentException.class, () -> copy(intent, duplicateRelations, intent.constraints()));

        List<TerrainIntentV2.Constraint> duplicateConstraints = new ArrayList<>(intent.constraints());
        duplicateConstraints.add(intent.constraints().getFirst());
        assertThrows(IllegalArgumentException.class, () -> copy(intent, intent.relations(), duplicateConstraints));

        var duplicatePaths = List.of(
                new TerrainIntentV2.NamedPath("same-path", "", "", List.of(
                        new TerrainIntentV2.Point2(0, 0), new TerrainIntentV2.Point2(100_000, 100_000))),
                new TerrainIntentV2.NamedPath("same-path", "", "", List.of(
                        new TerrainIntentV2.Point2(200_000, 200_000), new TerrainIntentV2.Point2(300_000, 300_000))));
        assertThrows(IllegalArgumentException.class, () -> new TerrainIntentV2.MultiSplineGeometry(
                duplicatePaths, TerrainIntentV2.Interpolation.POLYLINE));

        List<TerrainIntentV2.Relation> invalidEndpointType = new ArrayList<>(intent.relations());
        invalidEndpointType.add(new TerrainIntentV2.Relation(
                "invalid-boundary-adjacency", TerrainIntentV2.RelationKind.ADJACENT_TO,
                "feature:main-river", "boundary:SOUTH", TerrainIntentV2.Strength.HARD));
        assertThrows(IllegalArgumentException.class, () -> copy(intent, invalidEndpointType, intent.constraints()));
    }

    @Test
    void rejectsRawPathsUrlsBase64AndMinecraftBlockStates() throws IOException {
        String valid = Files.readString(COASTAL);
        String canonicalArtifact = "constraint:land-water:sha256-" + "0".repeat(64);
        for (String forbidden : List.of(
                "/tmp/mask.png",
                "https://example.invalid/mask.png",
                "data:image/png;base64,AAAA",
                "minecraft:stone"
        )) {
            String invalid = valid.replace(canonicalArtifact, forbidden);
            assertNotEquals(valid, invalid);
            assertThrows(StructuredDataValidationException.class,
                    () -> codec.readTerrainIntent(invalid, "forbidden-artifact"));
        }
    }

    @Test
    void freezesRoleSpecificMapSamplingAndRejectsDuplicateApplication() throws IOException {
        String checksum = "a".repeat(64);
        assertThrows(IllegalArgumentException.class, () -> new TerrainIntentV2.ConstraintMapBinding(
                "bad-land-sampling", "constraint-source:land", TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                "constraint:land-water:sha256-" + checksum, TerrainIntentV2.Strength.HARD,
                TerrainIntentV2.Sampling.BILINEAR_FIXED, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TerrainIntentV2.ConstraintMapBinding(
                "bad-land-tolerance", "constraint-source:land", TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                "constraint:land-water:sha256-" + checksum, TerrainIntentV2.Strength.HARD,
                TerrainIntentV2.Sampling.NEAREST, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TerrainIntentV2.ConstraintMapBinding(
                "bad-zone-sampling", "constraint-source:zones", TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP,
                "constraint:zone-label-map:sha256-" + checksum, TerrainIntentV2.Strength.SOFT,
                TerrainIntentV2.Sampling.BILINEAR_FIXED, 0, 500_000));

        TerrainIntentV2 intent = codec.readTerrainIntent(COASTAL);
        TerrainIntentV2.ConstraintMapBinding existing = intent.mapReferences().getFirst();
        List<TerrainIntentV2.ConstraintMapBinding> duplicateSource = new ArrayList<>(intent.mapReferences());
        duplicateSource.add(new TerrainIntentV2.ConstraintMapBinding(
                "same-source-twice", existing.sourceId(), existing.role(),
                "constraint:land-water:sha256-" + "b".repeat(64), existing.strength(),
                existing.sampling(), existing.toleranceBlocks(), existing.weightMillionths()));
        assertThrows(IllegalArgumentException.class, () -> new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(), intent.features(),
                intent.relations(), intent.constraints(), intent.environment(), duplicateSource,
                intent.structures(), intent.provenance()));
    }

    private static TerrainIntentV2 copy(
            TerrainIntentV2 source,
            List<TerrainIntentV2.Relation> relations,
            List<TerrainIntentV2.Constraint> constraints
    ) {
        return new TerrainIntentV2(
                source.intentVersion(), source.intentId(), source.theme(), source.coordinateSystem(), source.features(),
                relations, constraints, source.environment(), source.mapReferences(), source.structures(), source.provenance());
    }
}
