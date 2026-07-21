package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.format.v2.release.ReleasePlacementEligibilityVerifierV2;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed evidence of one published, strict-verified, placement-eligible Release 2 (V2-12-02).
 * Only produced after the directory (and optional ZIP) passed the capability verifier and the
 * placement eligibility gate.
 */
public record Release2ExportResultV2(
        String releaseId,
        Path releaseDirectory,
        Optional<Path> zip,
        String blueprintChecksum,
        String manifestChecksum,
        List<String> requiredCapabilities,
        List<String> tileIds,
        ReleasePlacementEligibilityVerifierV2.EligibilityResultV2 eligibility
) {
    public Release2ExportResultV2 {
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(releaseDirectory, "releaseDirectory");
        Objects.requireNonNull(zip, "zip");
        Objects.requireNonNull(blueprintChecksum, "blueprintChecksum");
        Objects.requireNonNull(manifestChecksum, "manifestChecksum");
        Objects.requireNonNull(eligibility, "eligibility");
        requiredCapabilities = List.copyOf(requiredCapabilities);
        tileIds = List.copyOf(tileIds);
        if (requiredCapabilities.isEmpty() || tileIds.isEmpty()) {
            throw new IllegalArgumentException("a published Release 2 export needs capabilities and tiles");
        }
    }
}
