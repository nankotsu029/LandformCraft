package com.github.nankotsu029.landformcraft.core.v2.recovery;

import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryActionV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryClassificationV2;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic, mutation-free recovery diagnosis for one persisted Release 2 journal. Findings
 * are ordered stably; the same journal, artifacts, and world produce an equal diagnosis on every
 * repeat regardless of thread count or observation order. Ambiguity is never classified as
 * success — every classification carries the safe operator actions it permits, and
 * {@link PlacementRecoveryClassificationV2#MANUAL_INTERVENTION_REQUIRED} permits none.
 */
public record PlacementRecoveryDiagnosisV2(
        PlacementRecoveryClassificationV2 classification,
        List<PlacementRecoveryActionV2> safeActions,
        List<String> findings,
        PlacementJournalStateV2 journalState,
        String journalChecksum,
        String baselineChecksum,
        String observedWorldChecksum,
        String expectedAppliedStreamChecksum,
        long scannedBlocks
) {
    public PlacementRecoveryDiagnosisV2 {
        Objects.requireNonNull(classification, "classification");
        safeActions = List.copyOf(Objects.requireNonNull(safeActions, "safeActions"));
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        Objects.requireNonNull(journalState, "journalState");
        journalChecksum = Objects.requireNonNull(journalChecksum, "journalChecksum");
        baselineChecksum = Objects.requireNonNull(baselineChecksum, "baselineChecksum");
        observedWorldChecksum = Objects.requireNonNull(observedWorldChecksum, "observedWorldChecksum");
        expectedAppliedStreamChecksum = Objects.requireNonNull(
                expectedAppliedStreamChecksum, "expectedAppliedStreamChecksum");
        if (scannedBlocks < 0) {
            throw new IllegalArgumentException("scannedBlocks must be >= 0");
        }
        if (classification == PlacementRecoveryClassificationV2.MANUAL_INTERVENTION_REQUIRED
                && !safeActions.isEmpty()) {
            throw new IllegalArgumentException(
                    "MANUAL_INTERVENTION_REQUIRED must not offer automatic actions");
        }
    }

    public boolean permits(PlacementRecoveryActionV2 action) {
        return safeActions.contains(action);
    }
}
