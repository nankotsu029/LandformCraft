package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Release format 2's capability-neutral, strict artifact index.
 *
 * <p>V2-2-10 intentionally permits only the empty-capability core. Later capability tasks add
 * their required artifacts through the versioned catalog rather than changing format 1.</p>
 */
public record ReleaseManifestV2(
        int releaseFormatVersion,
        int manifestVersion,
        String releaseId,
        List<String> requiredCapabilities,
        List<ReleaseArtifactDescriptorV2> artifacts,
        String canonicalChecksum
) {
    public static final int RELEASE_FORMAT_VERSION = 2;
    public static final int MANIFEST_VERSION = 1;
    public static final int MAXIMUM_ARTIFACTS = 256;
    public static final int MAXIMUM_CAPABILITIES = 32;
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);

    public ReleaseManifestV2 {
        if (releaseFormatVersion != RELEASE_FORMAT_VERSION || manifestVersion != MANIFEST_VERSION) {
            throw new IllegalArgumentException("unsupported Release format 2 manifest version");
        }
        releaseId = V2Validation.slug(releaseId, "releaseId");
        requiredCapabilities = V2Validation.sorted(
                requiredCapabilities, "requiredCapabilities", MAXIMUM_CAPABILITIES, Comparator.naturalOrder());
        for (String capability : requiredCapabilities) {
            V2Validation.qualifiedId(capability, "requiredCapability");
        }
        if (new HashSet<>(requiredCapabilities).size() != requiredCapabilities.size()) {
            throw new IllegalArgumentException("requiredCapabilities must be unique");
        }
        artifacts = V2Validation.sorted(
                artifacts, "artifacts", MAXIMUM_ARTIFACTS,
                Comparator.comparing(ReleaseArtifactDescriptorV2::path));
        Set<String> ids = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (ReleaseArtifactDescriptorV2 artifact : artifacts) {
            if (!ids.add(artifact.artifactId()) || !paths.add(artifact.path().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("artifact IDs and paths must be unique without case collisions");
            }
        }
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public ReleaseManifestV2(String releaseId) {
        this(RELEASE_FORMAT_VERSION, MANIFEST_VERSION, releaseId, List.of(), List.of(),
                PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public ReleaseManifestV2 withCanonicalChecksum(String checksum) {
        return new ReleaseManifestV2(
                releaseFormatVersion, manifestVersion, releaseId, requiredCapabilities, artifacts, checksum);
    }
}
