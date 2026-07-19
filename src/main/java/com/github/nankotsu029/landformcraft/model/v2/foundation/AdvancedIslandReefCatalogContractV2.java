package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Sealed V2-10-08 contract for advanced island and reef landform classification.
 * Records composition preset and catalog candidate disposition only — no FeatureKind is introduced.
 */
public record AdvancedIslandReefCatalogContractV2(
        int planVersion,
        String contractVersion,
        String decisionId,
        List<String> selectedCompositionPresets,
        List<Candidate> candidates,
        List<String> compatibilityNotes,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "advanced-island-reef-catalog-contract-v1";
    public static final String DECISION_ID = "v2-10-08-advanced-island-reef-catalog";
    public static final List<String> REQUIRED_COMPOSITION_PRESETS = List.of("ATOLL", "BARRIER_ISLAND");
    public static final List<String> REQUIRED_CANDIDATE_KINDS = List.of(
            "CORAL_COAST", "MANGROVE_COAST", "VOLCANIC_COAST", "FLOATING_REEF", "DUNE_BACKED_BEACH");

    public AdvancedIslandReefCatalogContractV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("advanced island/reef catalog planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown advanced island/reef catalog contract version");
        }
        decisionId = FoundationValidationV2.slug(decisionId, "decisionId");
        selectedCompositionPresets = FoundationValidationV2.sorted(
                selectedCompositionPresets, "selectedCompositionPresets", 8, Comparator.naturalOrder());
        candidates = FoundationValidationV2.sorted(
                candidates, "candidates", 16, Comparator.comparing(Candidate::kind));
        compatibilityNotes = FoundationValidationV2.immutable(compatibilityNotes, "compatibilityNotes", 32);
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        validateContract(selectedCompositionPresets, candidates);
    }

    public AdvancedIslandReefCatalogContractV2 withCanonicalChecksum(String checksum) {
        return new AdvancedIslandReefCatalogContractV2(
                planVersion, contractVersion, decisionId, selectedCompositionPresets,
                candidates, compatibilityNotes, checksum);
    }

    /** Frozen V2-10-08 Acceptance decision for barrier/atoll presets and catalog candidates. */
    public static AdvancedIslandReefCatalogContractV2 decisionV21008() {
        return new AdvancedIslandReefCatalogContractV2(
                VERSION,
                CONTRACT_VERSION,
                DECISION_ID,
                REQUIRED_COMPOSITION_PRESETS,
                List.of(
                        new Candidate("CORAL_COAST", Disposition.COMPOSITE_PRESET_ONLY,
                                "Reuses CORAL_REEF/LAGOON/REEF_PASS child contracts; no standalone coast FeatureKind"),
                        new Candidate("MANGROVE_COAST", Disposition.COMPOSITE_PRESET_ONLY,
                                "Mangrove wetland + coast transition preset; no new FeatureKind"),
                        new Candidate("VOLCANIC_COAST", Disposition.COMPOSITE_PRESET_ONLY,
                                "Volcanic cone/coast composition preset; sealed volcanic fixtures unchanged"),
                        new Candidate("FLOATING_REEF", Disposition.DEFERRED,
                                "Floating reef dedicated generator deferred; classification only"),
                        new Candidate("DUNE_BACKED_BEACH", Disposition.COMPOSITE_PRESET_ONLY,
                                "Dune-backed beach composition; dry-land modifiers remain contract-only")),
                List.of(
                        "v1 Schema, generator 3.0.0-phase6, Release format 1, and v1 placement/Undo remain frozen",
                        "BARRIER_ISLAND and ATOLL are COMPOSITE_PRESET only; no FeatureKind or dedicated generator",
                        "LAGOON and REEF_PASS remain CHILD_PLAN_ONLY; do not promote standalone",
                        "Diagnostic coral-reef, volcanic, single-island, and archipelago fixtures remain unchanged",
                        "FLOATING_REEF has no dedicated generator in V2-10-08"),
                "0".repeat(64));
    }

    private static void validateContract(
            List<String> selectedCompositionPresets,
            List<Candidate> candidates
    ) {
        if (!selectedCompositionPresets.equals(REQUIRED_COMPOSITION_PRESETS)) {
            throw new IllegalArgumentException("selectedCompositionPresets must be BARRIER_ISLAND and ATOLL");
        }
        if (candidates.size() != REQUIRED_CANDIDATE_KINDS.size()) {
            throw new IllegalArgumentException("candidates must list all five catalog kinds");
        }
        Set<String> seen = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (!seen.add(candidate.kind())) {
                throw new IllegalArgumentException("duplicate candidate kind: " + candidate.kind());
            }
            if (isForbiddenFeatureKindName(candidate.kind())) {
                throw new IllegalArgumentException("candidate kind must not be a FeatureKind: " + candidate.kind());
            }
        }
        if (!seen.containsAll(REQUIRED_CANDIDATE_KINDS)) {
            throw new IllegalArgumentException("candidates missing one or more required kinds");
        }
    }

    public static boolean isForbiddenFeatureKindName(String kind) {
        return Arrays.stream(com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2.FeatureKind.values())
                .anyMatch(featureKind -> featureKind.name().equals(kind));
    }

    public enum Disposition {
        COMPOSITE_PRESET_ONLY,
        DEFERRED
    }

    public record Candidate(String kind, Disposition disposition, String rationale) {
        public Candidate {
            kind = FoundationValidationV2.nonBlank(kind, "kind", 64);
            Objects.requireNonNull(disposition, "disposition");
            rationale = FoundationValidationV2.nonBlank(rationale, "rationale", 512);
        }
    }
}
