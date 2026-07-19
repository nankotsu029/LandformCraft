package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compile-time artifact and capability catalog for Release format 2.
 *
 * <p>Valid capability prefixes are owned by {@link ReleaseCapabilityDependencyMatrixV2}.
 * Unknown capability/type/version values are never forward-read.</p>
 */
public final class ReleaseArtifactCatalogV2 {
    public static final String SURFACE_TWO_POINT_FIVE_D =
            ReleaseCapabilityDependencyMatrixV2.SURFACE_TWO_POINT_FIVE_D;
    public static final String HYDROLOGY_PLAN =
            ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_PLAN;
    public static final String ENVIRONMENT_FIELDS =
            ReleaseCapabilityDependencyMatrixV2.ENVIRONMENT_FIELDS;
    public static final String SPARSE_VOLUME =
            ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME;
    public static final List<String> HYDROLOGY_WITH_SURFACE =
            ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_WITH_SURFACE;
    public static final List<String> ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE =
            ReleaseCapabilityDependencyMatrixV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE;
    public static final List<String> SPARSE_VOLUME_WITH_ENVIRONMENT =
            ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME_WITH_ENVIRONMENT;

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
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.GEOLOGY_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.LITHOLOGY_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.STRATA_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.CLIMATE_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.WATER_CONDITION_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.SNOW_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.MATERIAL_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.PALETTE_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.ECOLOGY_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.FEATURE_MATERIAL_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.VALIDATION_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.PREVIEW_INDEX_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(EnvironmentReleaseCapabilityVerifierV2.PREVIEW_PNG_TYPE, 1), ENVIRONMENT_FIELDS);
        map.put(new ArtifactKey(SparseVolumeReleaseCapabilityVerifierV2.SDF_TYPE, 1), SPARSE_VOLUME);
        map.put(new ArtifactKey(SparseVolumeReleaseCapabilityVerifierV2.CSG_TYPE, 1), SPARSE_VOLUME);
        map.put(new ArtifactKey(SparseVolumeReleaseCapabilityVerifierV2.AABB_TYPE, 1), SPARSE_VOLUME);
        map.put(new ArtifactKey(SparseVolumeReleaseCapabilityVerifierV2.VALIDATION_TYPE, 1), SPARSE_VOLUME);
        map.put(new ArtifactKey(SparseVolumeReleaseCapabilityVerifierV2.TILE_METADATA_TYPE, 1), SPARSE_VOLUME);
        map.put(new ArtifactKey(SparseVolumeReleaseCapabilityVerifierV2.SCHEMATIC_TYPE, 1), SPARSE_VOLUME);
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
        if (manifest.requiredCapabilities().equals(ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE)) {
            new EnvironmentReleaseCapabilityVerifierV2().verify(releaseRoot, manifest, cancellationToken);
            return;
        }
        if (manifest.requiredCapabilities().equals(SPARSE_VOLUME_WITH_ENVIRONMENT)) {
            new SparseVolumeReleaseCapabilityVerifierV2().verify(releaseRoot, manifest, cancellationToken);
            return;
        }
        throw new IOException(ReleaseCapabilityDependencyMatrixV2.dependencyFailureMessage(
                manifest.requiredCapabilities()));
    }

    private static void verifyKnownKeys(ReleaseManifestV2 manifest) throws IOException {
        ReleaseCrossVersionReaderPolicyV2.requireSupportedVersions(
                manifest.releaseFormatVersion(), manifest.manifestVersion());
        for (String capability : manifest.requiredCapabilities()) {
            if (!ReleaseCapabilityDependencyMatrixV2.isKnownCapability(capability)) {
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
        if (!ReleaseCapabilityDependencyMatrixV2.isValidPrefix(manifest.requiredCapabilities())) {
            throw new IOException(ReleaseCapabilityDependencyMatrixV2.dependencyFailureMessage(
                    manifest.requiredCapabilities()));
        }
    }

    private record ArtifactKey(String type, int version) {
    }
}
