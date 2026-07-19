package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Sealed V2-10-05 contract/split decision for advanced river and lake landforms.
 * Records ownership classification and the first two implementation Task IDs only —
 * no FeatureKind and no generator is introduced by this contract.
 */
public record AdvancedRiverLakeSplitContractV2(
        int planVersion,
        String contractVersion,
        String decisionId,
        List<String> selectedTaskIds,
        List<String> selectedKinds,
        List<Candidate> candidates,
        List<RiskExclusion> riskExclusions,
        List<String> compatibilityNotes,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "advanced-river-lake-split-contract-v1";
    public static final String DECISION_ID = "v2-10-05-advanced-river-lake-split";
    public static final int MAXIMUM_FIRST_SLICES = 2;
    public static final List<String> REQUIRED_SELECTED_TASK_IDS = List.of("V2-10-10", "V2-10-11");
    public static final List<String> REQUIRED_SELECTED_KINDS = List.of("OXBOW_LAKE", "SPRING");
    public static final List<String> REQUIRED_CANDIDATE_KINDS = List.of(
            "ALLUVIAL_FAN", "BRAIDED_RIVER", "DAM_RESERVOIR", "ESTUARY",
            "OXBOW_LAKE", "RIVER_TERRACE", "SPRING");

    public AdvancedRiverLakeSplitContractV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("advanced river/lake split planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown advanced river/lake split contract version");
        }
        decisionId = FoundationValidationV2.slug(decisionId, "decisionId");
        selectedTaskIds = FoundationValidationV2.sorted(
                selectedTaskIds, "selectedTaskIds", MAXIMUM_FIRST_SLICES, Comparator.naturalOrder());
        selectedKinds = FoundationValidationV2.sorted(
                selectedKinds, "selectedKinds", MAXIMUM_FIRST_SLICES, Comparator.naturalOrder());
        candidates = FoundationValidationV2.sorted(
                candidates, "candidates", 16, Comparator.comparing(Candidate::kind));
        riskExclusions = FoundationValidationV2.sorted(
                riskExclusions, "riskExclusions", 32,
                Comparator.comparing(RiskExclusion::leftKind).thenComparing(RiskExclusion::rightKind));
        compatibilityNotes = FoundationValidationV2.immutable(compatibilityNotes, "compatibilityNotes", 32);
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        validateDecision(selectedTaskIds, selectedKinds, candidates);
    }

    public AdvancedRiverLakeSplitContractV2 withCanonicalChecksum(String checksum) {
        return new AdvancedRiverLakeSplitContractV2(
                planVersion, contractVersion, decisionId, selectedTaskIds, selectedKinds,
                candidates, riskExclusions, compatibilityNotes, checksum);
    }

    /** Frozen V2-10-05 Acceptance decision: SPRING + OXBOW_LAKE → V2-10-10 / V2-10-11. */
    public static AdvancedRiverLakeSplitContractV2 decisionV21005() {
        return new AdvancedRiverLakeSplitContractV2(
                VERSION,
                CONTRACT_VERSION,
                DECISION_ID,
                REQUIRED_SELECTED_TASK_IDS,
                REQUIRED_SELECTED_KINDS,
                List.of(
                        new Candidate(
                                "ALLUVIAL_FAN",
                                OwnershipModel.SEDIMENT_FAN,
                                Disposition.DEFERRED_STANDALONE,
                                "Inland sediment-fan terminal needs a policy distinct from DELTA EMPTIES_INTO sea"),
                        new Candidate(
                                "BRAIDED_RIVER",
                                OwnershipModel.MULTI_CHANNEL_DAG,
                                Disposition.DEFERRED_STANDALONE,
                                "Multi-active-channel ownership exceeds V2-9-13 bifurcation roles"),
                        new Candidate(
                                "DAM_RESERVOIR",
                                OwnershipModel.BARRIER_BASIN,
                                Disposition.DEFERRED_BARRIER,
                                "Barrier crest/spillway/level ownership; no dam entity/block entity"),
                        new Candidate(
                                "ESTUARY",
                                OwnershipModel.MOUTH_COMPOSITION,
                                Disposition.DEFERRED_COMPOSITION,
                                "DELTA + tidal mouth coupling; avoid rewriting sealed delta fixtures"),
                        new Candidate(
                                "OXBOW_LAKE",
                                OwnershipModel.REACH_CUTOFF_BASIN,
                                Disposition.FIRST_SLICE,
                                "Reach cutoff + LakePlan-compatible basin; Task V2-10-11"),
                        new Candidate(
                                "RIVER_TERRACE",
                                OwnershipModel.REACH_PROFILE,
                                Disposition.DEFERRED_CHILD_OR_PROFILE,
                                "Reach-derived terrace profile; FeatureKind only if ownership proves independent"),
                        new Candidate(
                                "SPRING",
                                OwnershipModel.GRAPH_SOURCE_NODE,
                                Disposition.FIRST_SLICE,
                                "Surface source/outflow node; distinct from KARST_SPRING; Task V2-10-10")),
                List.of(
                        new RiskExclusion(
                                "DAM_RESERVOIR", "BRAIDED_RIVER",
                                "Barrier/level ownership conflicts with multi-channel sediment DAG"),
                        new RiskExclusion(
                                "DAM_RESERVOIR", "OXBOW_LAKE",
                                "Two competing basin/level owners"),
                        new RiskExclusion(
                                "ESTUARY", "ALLUVIAL_FAN",
                                "Two sediment-fan terminals (marine vs terrestrial)"),
                        new RiskExclusion(
                                "ESTUARY", "BRAIDED_RIVER",
                                "Tidal mouth coupling mixed with inland braid DAG"),
                        new RiskExclusion(
                                "SPRING", "KARST_SPRING",
                                "Surface spring must not rewrite sealed karst spring graph")),
                List.of(
                        "v1 Schema, generator 3.0.0-phase6, Release format 1, and v1 placement/Undo remain frozen",
                        "V2-10-05 introduces no FeatureKind and no generator for the seven candidates",
                        "Surface SPRING remains distinct from KARST_SPRING (V2-10-03)",
                        "Open-spill lake and river-graph-roles sealed fixtures remain unchanged until slice Tasks",
                        "Pond/crater/glacial lake kindization remains forbidden; keep as LAKE profile only"),
                "0".repeat(64));
    }

    private static void validateDecision(
            List<String> selectedTaskIds,
            List<String> selectedKinds,
            List<Candidate> candidates
    ) {
        if (!selectedTaskIds.equals(REQUIRED_SELECTED_TASK_IDS)) {
            throw new IllegalArgumentException("selectedTaskIds must be exactly V2-10-10 and V2-10-11");
        }
        if (!selectedKinds.equals(REQUIRED_SELECTED_KINDS)) {
            throw new IllegalArgumentException("selectedKinds must be exactly OXBOW_LAKE and SPRING");
        }
        if (candidates.size() != REQUIRED_CANDIDATE_KINDS.size()) {
            throw new IllegalArgumentException("candidates must list all seven advanced river/lake kinds");
        }
        Set<String> seen = new HashSet<>();
        long firstSlices = 0L;
        for (Candidate candidate : candidates) {
            if (!seen.add(candidate.kind())) {
                throw new IllegalArgumentException("duplicate candidate kind: " + candidate.kind());
            }
            if (candidate.disposition() == Disposition.FIRST_SLICE) {
                firstSlices++;
                if (!selectedKinds.contains(candidate.kind())) {
                    throw new IllegalArgumentException(
                            "FIRST_SLICE candidate must be in selectedKinds: " + candidate.kind());
                }
            } else if (selectedKinds.contains(candidate.kind())) {
                throw new IllegalArgumentException(
                        "selected kind must use FIRST_SLICE disposition: " + candidate.kind());
            }
        }
        if (!seen.containsAll(REQUIRED_CANDIDATE_KINDS)) {
            throw new IllegalArgumentException("candidates missing one or more required kinds");
        }
        if (firstSlices != MAXIMUM_FIRST_SLICES) {
            throw new IllegalArgumentException("exactly two FIRST_SLICE candidates are required");
        }
        Objects.requireNonNull(selectedTaskIds, "selectedTaskIds");
    }

    public enum OwnershipModel {
        GRAPH_SOURCE_NODE,
        REACH_CUTOFF_BASIN,
        REACH_PROFILE,
        SEDIMENT_FAN,
        MOUTH_COMPOSITION,
        MULTI_CHANNEL_DAG,
        BARRIER_BASIN
    }

    public enum Disposition {
        FIRST_SLICE,
        DEFERRED_STANDALONE,
        DEFERRED_CHILD_OR_PROFILE,
        DEFERRED_COMPOSITION,
        DEFERRED_BARRIER
    }

    public record Candidate(
            String kind,
            OwnershipModel ownershipModel,
            Disposition disposition,
            String rationale
    ) {
        public Candidate {
            kind = FoundationValidationV2.nonBlank(kind, "kind", 64);
            Objects.requireNonNull(ownershipModel, "ownershipModel");
            Objects.requireNonNull(disposition, "disposition");
            rationale = FoundationValidationV2.nonBlank(rationale, "rationale", 512);
        }
    }

    public record RiskExclusion(String leftKind, String rightKind, String reason) {
        public RiskExclusion {
            leftKind = FoundationValidationV2.nonBlank(leftKind, "leftKind", 64);
            rightKind = FoundationValidationV2.nonBlank(rightKind, "rightKind", 64);
            reason = FoundationValidationV2.nonBlank(reason, "reason", 512);
            if (leftKind.compareTo(rightKind) > 0) {
                String swap = leftKind;
                leftKind = rightKind;
                rightKind = swap;
            }
            if (leftKind.equals(rightKind)) {
                throw new IllegalArgumentException("risk exclusion kinds must differ");
            }
        }
    }
}
