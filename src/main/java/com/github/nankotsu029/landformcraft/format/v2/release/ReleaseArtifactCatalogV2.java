package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.core.CancellationToken;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compile-time artifact and capability catalog for Release format 2.
 *
 * <p>Empty core, {@code surface-2_5d}, and {@code hydrology-plan}+{@code surface-2_5d} are
 * intentionally dispatched by distinct strict rules. Unknown capability/type/version values are
 * never forward-read.</p>
 */
public final class ReleaseArtifactCatalogV2 {
    public static final String SURFACE_TWO_POINT_FIVE_D = "surface-2_5d";
    public static final String HYDROLOGY_PLAN = "hydrology-plan";
    public static final List<String> HYDROLOGY_WITH_SURFACE = List.of(HYDROLOGY_PLAN, SURFACE_TWO_POINT_FIVE_D);

    private static final Set<String> KNOWN_CAPABILITIES = Set.of(SURFACE_TWO_POINT_FIVE_D, HYDROLOGY_PLAN);
    private static final Map<ArtifactKey, String> RESERVED_ARTIFACT_CAPABILITIES;

    static {
        Map<ArtifactKey, String> map = new HashMap<>();
        map.put(new ArtifactKey("generation-request-v2", 1), SURFACE_TWO_POINT_FIVE_D);
        map.put(new ArtifactKey("terrain-intent-v2", 1), SURFACE_TWO_POINT_FIVE_D);
        map.put(new ArtifactKey("world-blueprint-v2", 1), SURFACE_TWO_POINT_FIVE_D);
        map.put(new ArtifactKey("constraint-field-index-v2", 1), SURFACE_TWO_POINT_FIVE_D);
        map.put(new ArtifactKey("constraint-field-grid-v1", 1), SURFACE_TWO_POINT_FIVE_D);
        map.put(new ArtifactKey("coastal-validation-artifact-v2", 1), SURFACE_TWO_POINT_FIVE_D);
        map.put(new ArtifactKey("coastal-preview-index-v2", 1), SURFACE_TWO_POINT_FIVE_D);
        map.put(new ArtifactKey("coastal-preview-png-v1", 1), SURFACE_TWO_POINT_FIVE_D);
        map.put(new ArtifactKey("offline-tile-artifact-v2", 1), SURFACE_TWO_POINT_FIVE_D);
        map.put(new ArtifactKey("sponge-schematic-v3", 1), SURFACE_TWO_POINT_FIVE_D);
        map.put(new ArtifactKey(HydrologyReleaseCapabilityVerifierV2.PLAN_TYPE, 1), HYDROLOGY_PLAN);
        map.put(new ArtifactKey(HydrologyReleaseCapabilityVerifierV2.ROUTING_TYPE, 1), HYDROLOGY_PLAN);
        map.put(new ArtifactKey(HydrologyReleaseCapabilityVerifierV2.FIELD_GRID_TYPE, 1), HYDROLOGY_PLAN);
        map.put(new ArtifactKey(HydrologyReleaseCapabilityVerifierV2.RECONCILIATION_PLAN_TYPE, 1), HYDROLOGY_PLAN);
        map.put(new ArtifactKey(HydrologyReleaseCapabilityVerifierV2.RECONCILIATION_ARTIFACT_TYPE, 1), HYDROLOGY_PLAN);
        map.put(new ArtifactKey(HydrologyReleaseCapabilityVerifierV2.VALIDATION_TYPE, 1), HYDROLOGY_PLAN);
        map.put(new ArtifactKey(HydrologyReleaseCapabilityVerifierV2.PREVIEW_INDEX_TYPE, 1), HYDROLOGY_PLAN);
        map.put(new ArtifactKey(HydrologyReleaseCapabilityVerifierV2.PREVIEW_PNG_TYPE, 1), HYDROLOGY_PLAN);
        RESERVED_ARTIFACT_CAPABILITIES = Map.copyOf(map);
    }

    public void verifyCoreManifest(ReleaseManifestV2 manifest) throws IOException {
        verifyKnownKeys(manifest);
        if (!manifest.requiredCapabilities().isEmpty() || !manifest.artifacts().isEmpty()) {
            throw new IOException("Release format 2 core does not enable payload capabilities");
        }
    }

    public void verifyManifest(
            ReleaseManifestV2 manifest,
            Path releaseRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        verifyKnownKeys(manifest);
        if (manifest.requiredCapabilities().isEmpty() && manifest.artifacts().isEmpty()) {
            return;
        }
        if (manifest.requiredCapabilities().equals(List.of(SURFACE_TWO_POINT_FIVE_D))) {
            new SurfaceReleaseCapabilityVerifierV2().verify(releaseRoot, manifest, cancellationToken);
            return;
        }
        if (manifest.requiredCapabilities().equals(HYDROLOGY_WITH_SURFACE)) {
            new HydrologyReleaseCapabilityVerifierV2().verify(releaseRoot, manifest, cancellationToken);
            return;
        }
        throw new IOException("Release format 2 capability combination is unsupported");
    }

    private static void verifyKnownKeys(ReleaseManifestV2 manifest) throws IOException {
        for (String capability : manifest.requiredCapabilities()) {
            if (!KNOWN_CAPABILITIES.contains(capability)) {
                throw new IOException("unknown Release format 2 capability: " + capability);
            }
        }
        for (ReleaseArtifactDescriptorV2 artifact : manifest.artifacts()) {
            ArtifactKey key = new ArtifactKey(artifact.artifactType(), artifact.artifactVersion());
            if (!RESERVED_ARTIFACT_CAPABILITIES.containsKey(key)) {
                throw new IOException("unknown Release format 2 artifact type/version: "
                        + artifact.artifactType() + "/" + artifact.artifactVersion());
            }
        }
        if (manifest.requiredCapabilities().equals(List.of(HYDROLOGY_PLAN))) {
            throw new IOException("hydrology-plan requires surface-2_5d");
        }
        if (manifest.requiredCapabilities().contains(HYDROLOGY_PLAN)
                && !manifest.requiredCapabilities().contains(SURFACE_TWO_POINT_FIVE_D)) {
            throw new IOException("hydrology-plan requires surface-2_5d");
        }
    }

    private record ArtifactKey(String type, int version) {
    }
}
