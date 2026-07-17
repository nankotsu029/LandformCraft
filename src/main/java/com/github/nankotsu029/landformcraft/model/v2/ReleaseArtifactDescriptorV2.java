package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Objects;

/**
 * One immutable, checksum-bound entry in a Release format 2 artifact index.
 *
 * <p>The core format only validates the index shape and byte checksum. A capability-specific
 * verifier owns the interpretation of {@code artifactType}, {@code artifactVersion}, and the
 * semantic checksum once that capability has been enabled.</p>
 */
public record ReleaseArtifactDescriptorV2(
        String artifactId,
        String artifactType,
        int artifactVersion,
        String path,
        long byteLength,
        String artifactChecksum,
        String semanticChecksum
) {
    public ReleaseArtifactDescriptorV2 {
        artifactId = V2Validation.qualifiedId(artifactId, "artifactId");
        artifactType = V2Validation.qualifiedId(artifactType, "artifactType");
        if (artifactVersion < 1 || artifactVersion > 1_000) {
            throw new IllegalArgumentException("artifactVersion must be between 1 and 1000");
        }
        path = V2Validation.safeRelativePath(path, "path");
        if ("manifest.json".equals(path) || byteLength < 1 || byteLength > 64L * 1024L * 1024L) {
            throw new IllegalArgumentException("artifact path or byteLength is invalid");
        }
        artifactChecksum = V2Validation.checksum(artifactChecksum, "artifactChecksum");
        semanticChecksum = V2Validation.checksum(semanticChecksum, "semanticChecksum");
        Objects.requireNonNull(artifactId, "artifactId");
    }
}
