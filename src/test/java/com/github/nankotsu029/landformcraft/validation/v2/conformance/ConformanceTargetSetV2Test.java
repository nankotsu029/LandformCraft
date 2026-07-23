package com.github.nankotsu029.landformcraft.validation.v2.conformance;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.ValidationTargetV2;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-18-07 conformance target set: classification into desired-raster / aggregate / topology / geometric
 * kinds, and the provenance origin that distinguishes an honest input-mask binding from a self-derived
 * one. The positive fixture is the shipped {@code coastal-honored-400} request/intent pair.
 */
class ConformanceTargetSetV2Test {
    private static final Path REQUEST =
            Path.of("examples/v2/diagnostic/coastal-honored-400.request-v2.json");
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/coastal-honored-400.terrain-intent-v2.json");
    private static final String DECLARED_DIGEST =
            "60095de7148a92a805e5574f970c56e5f0156740f9904418a0a19e1d845aaa23";
    private static final String OTHER_DIGEST =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void classifiesEachRuleFamilyIntoItsKind() {
        List<ValidationTargetV2> targets = List.of(
                target("t-edge", "v2.edge-classification", "edge.north.land", range(0, 1_000_000)),
                target("t-beach", "coastal.beach.width", "coastal.beach.width-p50", range(10_000_000, 30_000_000)),
                target("t-river", "hydrology.river.reachability", "hydrology.river.source-mouth-reachable", range(1, 1)),
                target("t-custom", "v2.custom-unregistered", "custom.metric.value", range(0, 5)));

        ConformanceTargetSetV2 set = ConformanceTargetSetV2.from(targets, List.of(), Map.of());

        assertEquals(List.of("t-edge", "t-custom"),
                set.aggregateMetricTargets().stream().map(ConformanceTargetV2.AggregateMetric::targetId).toList());
        assertEquals(List.of("t-beach"),
                set.geometricTargets().stream().map(ConformanceTargetV2.Geometric::targetId).toList());
        assertEquals(List.of("t-river"),
                set.topologyTargets().stream().map(ConformanceTargetV2.Topology::targetId).toList());
        assertTrue(set.desiredRasterTargets().isEmpty(), "no LAND_WATER_MASK binding means no desired raster");
        // Unknown rule ids degrade to the general scalar shape rather than being dropped.
        assertEquals(ConformanceTargetKindV2.AGGREGATE_METRIC,
                ConformanceTargetClassifierV2.kindOf("v2.custom-unregistered"));
    }

    @Test
    void aDesiredRasterIsOptionalAndAbsentWithoutAMaskBinding() {
        ConformanceTargetSetV2 set = ConformanceTargetSetV2.from(List.of(), List.of(), Map.of());
        assertTrue(set.desiredRasterTargets().isEmpty());
        assertTrue(set.provenanceBindings().isEmpty());
        assertTrue(set.targets().isEmpty());
    }

    @Test
    void provenanceIsInputMaskWhenTheBoundDigestMatchesTheDeclaredSource() {
        ConformanceTargetSetV2 set = ConformanceTargetSetV2.from(
                List.of(),
                List.of(landWaterBinding(DECLARED_DIGEST)),
                Map.of("constraint-source:coast-mask", DECLARED_DIGEST));

        assertEquals(1, set.desiredRasterTargets().size());
        ConformanceTargetV2.DesiredRaster raster = set.desiredRasterTargets().getFirst();
        assertEquals(ConformanceTargetSetV2.LAND_WATER_FIELD_ID, raster.fieldId());
        ConformanceProvenanceV2 provenance = raster.provenance();
        assertEquals(ConformanceProvenanceV2.Origin.INPUT_MASK, provenance.origin());
        assertTrue(provenance.resolvable());
        assertEquals(DECLARED_DIGEST, provenance.artifactDigest());
        assertEquals(List.of(provenance), set.provenanceBindings());
    }

    @Test
    void provenanceIsSelfDerivedWhenTheBoundDigestDiffersFromTheDeclaredSource() {
        ConformanceTargetSetV2 set = ConformanceTargetSetV2.from(
                List.of(),
                List.of(landWaterBinding(OTHER_DIGEST)),
                Map.of("constraint-source:coast-mask", DECLARED_DIGEST));

        ConformanceProvenanceV2 provenance = set.desiredRasterTargets().getFirst().provenance();
        assertEquals(ConformanceProvenanceV2.Origin.SELF_DERIVED, provenance.origin());
        assertFalse(provenance.resolvable());
    }

    @Test
    void provenanceIsSelfDerivedWhenNoSourceWasDeclared() {
        ConformanceTargetSetV2 set = ConformanceTargetSetV2.from(
                List.of(), List.of(landWaterBinding(DECLARED_DIGEST)), Map.of());
        assertEquals(ConformanceProvenanceV2.Origin.SELF_DERIVED,
                set.desiredRasterTargets().getFirst().provenance().origin());
    }

    @Test
    void honoredFixtureIsSelfDerivedUntilExportRebindsItToTheInputDigest() throws IOException {
        GenerationRequestV2 request = codec.readGenerationRequest(REQUEST);
        TerrainIntentV2 intent = codec.readTerrainIntent(INTENT);
        Map<String, String> declared = Map.of(
                request.constraintMaps().getFirst().sourceId(),
                request.constraintMaps().getFirst().expectedSha256());

        // The raw design intent still carries the placeholder digest, so it reads as self-derived.
        ConformanceTargetSetV2 draft = ConformanceTargetSetV2.from(
                List.of(), intent.mapReferences(), declared);
        assertEquals(ConformanceProvenanceV2.Origin.SELF_DERIVED,
                draft.desiredRasterTargets().getFirst().provenance().origin());

        // Rebinding to the declared input digest (what the V2-18-07 export fix now does) makes it honest.
        TerrainIntentV2.ConstraintMapBinding rebound = withArtifactId(
                intent.mapReferences().getFirst(),
                "constraint:land-water:sha256-" + request.constraintMaps().getFirst().expectedSha256());
        ConformanceTargetSetV2 sealed = ConformanceTargetSetV2.from(List.of(), List.of(rebound), declared);
        assertEquals(ConformanceProvenanceV2.Origin.INPUT_MASK,
                sealed.desiredRasterTargets().getFirst().provenance().origin());
    }

    @Test
    void classificationIsIndependentOfLocaleAndTimezone() {
        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            ConformanceTargetSetV2 first = sampleSet();
            Locale.setDefault(Locale.ROOT);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            ConformanceTargetSetV2 second = sampleSet();
            assertEquals(first.targets(), second.targets());
        } finally {
            Locale.setDefault(locale);
            TimeZone.setDefault(zone);
        }
    }

    private ConformanceTargetSetV2 sampleSet() {
        return ConformanceTargetSetV2.from(
                List.of(target("t-edge", "v2.edge-classification", "edge.north.land", range(0, 1_000_000))),
                List.of(landWaterBinding(DECLARED_DIGEST)),
                Map.of("constraint-source:coast-mask", DECLARED_DIGEST));
    }

    private static ValidationTargetV2 target(
            String targetId, String ruleId, String metric, TerrainIntentV2.FixedRange expected) {
        return new ValidationTargetV2(
                targetId, "constraint-" + targetId, List.of(), ruleId, 1, TerrainIntentV2.Strength.HARD, 0,
                metric, expected, 0, List.of("intent.land-water-mask"), "diagnostic.validation");
    }

    private static TerrainIntentV2.ConstraintMapBinding landWaterBinding(String digest) {
        return new TerrainIntentV2.ConstraintMapBinding(
                "coast-mask-binding", "constraint-source:coast-mask",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                "constraint:land-water:sha256-" + digest,
                TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0);
    }

    private static TerrainIntentV2.ConstraintMapBinding withArtifactId(
            TerrainIntentV2.ConstraintMapBinding binding, String artifactId) {
        return new TerrainIntentV2.ConstraintMapBinding(
                binding.id(), binding.sourceId(), binding.role(), artifactId,
                binding.strength(), binding.sampling(), binding.toleranceBlocks(), binding.weightMillionths());
    }

    private static TerrainIntentV2.FixedRange range(long min, long max) {
        return new TerrainIntentV2.FixedRange(min, max);
    }
}
