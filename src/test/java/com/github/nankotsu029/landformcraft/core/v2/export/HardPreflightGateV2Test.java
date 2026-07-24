package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-18-03 HARD preflight gate: rejects un-honorable HARD requirements before generation with stable
 * rule ids, warns on the SOFT equivalents, and never offers an override.
 */
class HardPreflightGateV2Test {
    private static final Path DIAGNOSTIC = Path.of("examples/v2/diagnostic");
    private static final CancellationToken NEVER = () -> false;
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final HardPreflightGateV2 gate = new HardPreflightGateV2();

    @Test
    void theHonoredFixturePassesWithSoftDeclarationsReportedOnlyAsWarnings() throws IOException {
        HardPreflightResultV2 result = evaluate("coastal-honored-400");

        assertFalse(result.rejected(), () -> result.rejections().toString());
        // beach-width / cape-irregularity (SOFT METRIC, still no evaluator) and the SOFT backshore
        // adjacency are preflight warnings. The HARD edge constraints V2-18-11 restored
        // (north-is-land / south-is-sea) are neither warned nor rejected: V2-18-04 gave EDGE an
        // evaluator, so the target-driven framework measures them after generation instead.
        assertEquals(Set.of("beach-width", "cape-irregularity", "backshore-adjoins-beach"),
                result.warnings().stream().map(HardPreflightResultV2.Finding::subjectId)
                        .collect(Collectors.toSet()));
    }

    @Test
    void aResolvableMaskDoesNotSaveAnIntentWhoseHardConstraintsAndRelationsAreUnhonorable()
            throws IOException {
        // coastal-fishing-map's LAND_WATER_MASK was fixed to a real PNG + digest (V2-18-03 scope), so
        // the map resolves; the intent is still rejected on its HARD beach-width METRIC_RANGE (no
        // evaluator) and its HARD relation to the contract-only BACKSHORE_PLAINS kind. Its HARD EDGE
        // constraints (north-is-land / south-is-sea) are no longer preflight-rejected because V2-18-04
        // gave them an evaluator; they are now measured after generation instead.
        HardPreflightResultV2 result = evaluate("coastal-fishing-map");

        assertTrue(result.rejected());
        assertEquals(Set.of(HardPreflightResultV2.Category.HARD_CONSTRAINT_UNEVALUATED,
                        HardPreflightResultV2.Category.HARD_RELATION_UNCONSUMED),
                result.rejections().stream().map(HardPreflightResultV2.Finding::category)
                        .collect(Collectors.toSet()));
        assertEquals(Set.of("beach-width"),
                subjectsFor(result, HardPreflightResultV2.Category.HARD_CONSTRAINT_UNEVALUATED));
        assertEquals(Set.of("backshore-adjoins-beach"),
                subjectsFor(result, HardPreflightResultV2.Category.HARD_RELATION_UNCONSUMED));
    }

    @Test
    void aMissingMaskIsRejectedAsAnUnresolvedMapReference() throws IOException {
        // azure-coast keeps its dummy digest and never shipped a mask, so all three rejection
        // categories fire, including the unresolved map reference.
        HardPreflightResultV2 result = evaluate("azure-coast");

        assertEquals(Set.of(HardPreflightResultV2.Category.HARD_CONSTRAINT_UNEVALUATED,
                        HardPreflightResultV2.Category.HARD_RELATION_UNCONSUMED,
                        HardPreflightResultV2.Category.MAP_REFERENCE_UNRESOLVED),
                result.rejections().stream().map(HardPreflightResultV2.Finding::category)
                        .collect(Collectors.toSet()));
        assertEquals(Set.of("coast-mask-binding"),
                subjectsFor(result, HardPreflightResultV2.Category.MAP_REFERENCE_UNRESOLVED));
    }

    @Test
    void requireHonorableThrowsAnIoExceptionCarryingEveryStableRuleId() throws IOException {
        GenerationRequestV2 request = codec.readGenerationRequest(request("azure-coast"));
        TerrainIntentV2 intent = codec.readTerrainIntent(intent("azure-coast"));

        HardPreflightRejectedV2 failure = assertThrows(HardPreflightRejectedV2.class,
                () -> gate.requireHonorable(request, request("azure-coast"), intent, NEVER));

        Set<String> ruleIds = failure.rejections().stream()
                .map(HardPreflightResultV2.Finding::ruleId).collect(Collectors.toSet());
        assertEquals(Set.of(
                HardPreflightGateV2.RULE_HARD_CONSTRAINT_UNEVALUATED,
                HardPreflightGateV2.RULE_HARD_RELATION_UNCONSUMED,
                HardPreflightGateV2.RULE_MAP_REFERENCE_UNRESOLVED), ruleIds);
        for (String ruleId : ruleIds) {
            assertTrue(failure.getMessage().contains(ruleId), failure.getMessage());
        }
    }

    @Test
    void requireHonorablePassesTheHonoredFixture() throws IOException {
        GenerationRequestV2 request = codec.readGenerationRequest(request("coastal-honored-400"));
        TerrainIntentV2 intent = codec.readTerrainIntent(intent("coastal-honored-400"));

        gate.requireHonorable(request, request("coastal-honored-400"), intent, NEVER);
    }

    @Test
    void aHardRelationToAnOfflineProductionKindIsNoLongerRejectedAsUnconsumed() throws IOException {
        // V2-15-12: harbor-cove-64-honored-canyon declares a HARD WITHIN relation from a CANYON
        // feature to a MEANDERING_RIVER feature. MEANDERING_RIVER is an ADR 0039 OFFLINE_PRODUCTION
        // route (V2-15-10), not PRODUCTION_CONNECTED (the coastal four), but CanyonPlanCompilerV2's
        // own WITHIN resolution is a real production consumer of the relation — the gate must not
        // reject an honorable relation just because its consumer's route is the offline one.
        HardPreflightResultV2 result = evaluate("harbor-cove-64-honored-canyon");

        assertTrue(subjectsFor(result, HardPreflightResultV2.Category.HARD_RELATION_UNCONSUMED)
                        .stream().noneMatch("canyon-within-river"::equals),
                () -> "canyon-within-river must not be reported unconsumed: " + result.rejections());
    }

    @Test
    void aTamperedMaskDigestIsRejectedAsAnUnresolvedMapReference(@TempDir Path root) throws IOException {
        Path relocated = relocateHonored64(root);
        // Break only the declared digest; the byte content and dimensions still match the file.
        String json = Files.readString(relocated)
                .replace("b1d98bff54aa3fa8a6df022df9057f05ee0ad2ece1873c13982222614989a48e",
                        "a".repeat(64));
        Files.writeString(relocated, json);

        GenerationRequestV2 request = codec.readGenerationRequest(relocated);
        TerrainIntentV2 intent = codec.readTerrainIntent(intent("harbor-cove-64-honored"));
        HardPreflightResultV2 result = gate.evaluate(request, relocated, intent, NEVER);

        assertEquals(Set.of("coast-mask-binding"),
                subjectsFor(result, HardPreflightResultV2.Category.MAP_REFERENCE_UNRESOLVED));
    }

    @Test
    void declaredDimensionsThatDisagreeWithThePngAreRejected(@TempDir Path root) throws IOException {
        Path relocated = relocateHonored64(root);
        // Keep the real digest (bytes unchanged) but declare larger dimensions (and a matching crop /
        // bounds so the descriptor stays internally valid) that disagree with the 64x64 PNG the digest
        // points at. Growing rather than shrinking keeps bounds >= tileSize.
        String json = Files.readString(relocated)
                .replace("\"expectedWidth\": 64", "\"expectedWidth\": 128")
                .replace("\"expectedLength\": 64", "\"expectedLength\": 128")
                .replace("\"width\": 64", "\"width\": 128")
                .replace("\"length\": 64", "\"length\": 128");
        Files.writeString(relocated, json);

        GenerationRequestV2 request = codec.readGenerationRequest(relocated);
        TerrainIntentV2 intent = codec.readTerrainIntent(intent("harbor-cove-64-honored"));
        HardPreflightResultV2 result = gate.evaluate(request, relocated, intent, NEVER);

        List<HardPreflightResultV2.Finding> mapFindings = result.rejections().stream()
                .filter(finding -> finding.category() == HardPreflightResultV2.Category.MAP_REFERENCE_UNRESOLVED)
                .toList();
        assertEquals(1, mapFindings.size());
        assertTrue(mapFindings.getFirst().detail().contains("64x64"), mapFindings.getFirst().detail());
    }

    private Path relocateHonored64(Path root) throws IOException {
        Path maps = Files.createDirectories(root.resolve("maps"));
        Files.copy(DIAGNOSTIC.resolve("maps/harbor-cove-64-honored-land-water-u8.png"),
                maps.resolve("harbor-cove-64-honored-land-water-u8.png"));
        Path relocated = root.resolve("harbor-cove-64-honored.request-v2.json");
        Files.copy(request("harbor-cove-64-honored"), relocated);
        return relocated;
    }

    private HardPreflightResultV2 evaluate(String fixture) throws IOException {
        GenerationRequestV2 request = codec.readGenerationRequest(request(fixture));
        TerrainIntentV2 intent = codec.readTerrainIntent(intent(fixture));
        return gate.evaluate(request, request(fixture), intent, NEVER);
    }

    private static Set<String> subjectsFor(HardPreflightResultV2 result, HardPreflightResultV2.Category category) {
        return result.rejections().stream()
                .filter(finding -> finding.category() == category)
                .map(HardPreflightResultV2.Finding::subjectId)
                .collect(Collectors.toSet());
    }

    private static Path request(String fixture) {
        return DIAGNOSTIC.resolve(fixture + ".request-v2.json");
    }

    private static Path intent(String fixture) {
        return DIAGNOSTIC.resolve(fixture + ".terrain-intent-v2.json");
    }
}
