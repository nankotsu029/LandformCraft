package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.model.RecoveryClassification;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RecoveryReport(
        UUID placementId,
        RecoveryClassification classification,
        List<String> tileFindings,
        String suggestedAction
) {
    public RecoveryReport {
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(classification, "classification");
        tileFindings = List.copyOf(tileFindings);
        suggestedAction = Objects.requireNonNull(suggestedAction, "suggestedAction");
    }
}
