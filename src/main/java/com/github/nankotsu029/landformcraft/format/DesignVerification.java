package com.github.nankotsu029.landformcraft.format;

import com.github.nankotsu029.landformcraft.model.DesignAudit;
import com.github.nankotsu029.landformcraft.model.ImageInputEvidence;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;

import java.nio.file.Path;
import java.util.Objects;

public record DesignVerification(
        Path directory,
        TerrainIntent intent,
        DesignAudit audit,
        ImageInputEvidence imageEvidence,
        int verifiedFiles
) {
    public DesignVerification {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(imageEvidence, "imageEvidence");
        if (verifiedFiles < 1) {
            throw new IllegalArgumentException("verifiedFiles must be positive");
        }
    }
}
