package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedRiverLakeSplitContractV2Test {
    private static final Path EXAMPLE =
            Path.of("examples/v2/foundation/advanced-river-lake-split-contract-v2.json");
    private static final Path RIVER_ROLES =
            Path.of("examples/v2/foundation/river-graph-roles-plan-v2.json");
    private static final Path OPEN_SPILL_LAKE =
            Path.of("examples/v2/hydrology/open-spill-lake.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void decisionFixesExactlyTwoFirstSlicesAndTaskIds() {
        AdvancedRiverLakeSplitContractV2 sealed =
                codec.sealAdvancedRiverLakeSplitContract(AdvancedRiverLakeSplitContractV2.decisionV21005());
        assertEquals(List.of("V2-10-10", "V2-10-11"), sealed.selectedTaskIds());
        assertEquals(List.of("OXBOW_LAKE", "SPRING"), sealed.selectedKinds());
        assertEquals(2, sealed.candidates().stream()
                .filter(candidate -> candidate.disposition()
                        == AdvancedRiverLakeSplitContractV2.Disposition.FIRST_SLICE)
                .count());
        assertEquals(7, sealed.candidates().size());
        assertFalse(sealed.riskExclusions().isEmpty());
        assertFalse(sealed.compatibilityNotes().isEmpty());
    }

    @Test
    void deferredSevenCandidatesExceptCompletedFirstSlicesRemainUnintroduced() {
        Set<String> kinds = Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertTrue(kinds.contains("SPRING"), "V2-10-10 introduces SPRING FeatureKind");
        assertTrue(kinds.contains("OXBOW_LAKE"), "V2-10-11 introduces OXBOW_LAKE FeatureKind");
        for (String candidate : AdvancedRiverLakeSplitContractV2.REQUIRED_CANDIDATE_KINDS) {
            if ("SPRING".equals(candidate) || "OXBOW_LAKE".equals(candidate)) {
                continue;
            }
            assertFalse(kinds.contains(candidate),
                    "candidate FeatureKind must not be introduced before its slice Task: " + candidate);
        }
    }

    @Test
    void moreThanTwoFirstSlicesIsRejected() {
        AdvancedRiverLakeSplitContractV2 base = AdvancedRiverLakeSplitContractV2.decisionV21005();
        List<AdvancedRiverLakeSplitContractV2.Candidate> candidates = base.candidates().stream()
                .map(candidate -> candidate.kind().equals("ESTUARY")
                        ? new AdvancedRiverLakeSplitContractV2.Candidate(
                        candidate.kind(),
                        candidate.ownershipModel(),
                        AdvancedRiverLakeSplitContractV2.Disposition.FIRST_SLICE,
                        candidate.rationale())
                        : candidate)
                .toList();
        assertThrows(IllegalArgumentException.class, () -> new AdvancedRiverLakeSplitContractV2(
                base.planVersion(), base.contractVersion(), base.decisionId(),
                base.selectedTaskIds(), base.selectedKinds(), candidates,
                base.riskExclusions(), base.compatibilityNotes(), base.canonicalChecksum()));
    }

    @Test
    void sealedContractRoundTripAndLocaleStability(@TempDir Path temp) throws Exception {
        AdvancedRiverLakeSplitContractV2 sealed =
                codec.sealAdvancedRiverLakeSplitContract(AdvancedRiverLakeSplitContractV2.decisionV21005());
        Path out = temp.resolve("advanced-river-lake-split-contract-v2.json");
        codec.writeAdvancedRiverLakeSplitContract(out, sealed);
        assertEquals(sealed, codec.readAdvancedRiverLakeSplitContract(out));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            AdvancedRiverLakeSplitContractV2 second =
                    codec.sealAdvancedRiverLakeSplitContract(AdvancedRiverLakeSplitContractV2.decisionV21005());
            assertEquals(sealed.canonicalChecksum(), second.canonicalChecksum());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        Files.copy(out, EXAMPLE, StandardCopyOption.REPLACE_EXISTING);
        assertEquals(sealed, codec.readAdvancedRiverLakeSplitContract(EXAMPLE));
    }

    @Test
    void hydrologyRegressionFixturesStillLoad() throws Exception {
        assertFalse(codec.readRiverPlan(RIVER_ROLES).nodes().isEmpty());
        assertFalse(codec.readTerrainIntent(OPEN_SPILL_LAKE).features().isEmpty());
    }
}
