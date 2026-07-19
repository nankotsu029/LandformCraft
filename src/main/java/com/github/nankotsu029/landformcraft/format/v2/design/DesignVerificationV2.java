package com.github.nankotsu029.landformcraft.format.v2.design;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignAuditV2;
import com.github.nankotsu029.landformcraft.model.v2.design.ImageDraftEvidenceV2;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record DesignVerificationV2(
        Path directory,
        TerrainIntentV2 intent,
        DesignAuditV2 audit,
        Optional<ImageDraftEvidenceV2> draftEvidence,
        int verifiedFiles
) {
    public DesignVerificationV2 {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(draftEvidence, "draftEvidence");
        if (verifiedFiles < 1) {
            throw new IllegalArgumentException("verifiedFiles must be positive");
        }
    }
}
