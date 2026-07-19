package com.github.nankotsu029.landformcraft.format.v2.release;

/**
 * Consolidated Release format 2 artifact limits catalog (V2-6-12). Core container limits live in
 * {@link ReleaseV2Limits}; this catalog also freezes payload-adjacent ceilings that capability
 * verifiers must not exceed when admitting decode or resident buffers.
 */
public final class ReleaseArtifactLimitsCatalogV2 {
    public static final String CATALOG_VERSION = "release-2-artifact-limits-catalog-v1";

    public static final long MAXIMUM_MANIFEST_BYTES = 256L * 1024L;
    public static final long MAXIMUM_HYDROLOGY_ROUTING_INDEX_BYTES = 512L * 1024L;
    public static final int MAXIMUM_SPONGE_PALETTE_ENTRIES = 16_384;
    public static final int MAXIMUM_PREVIEW_PNG_EDGE = 4_096;
    public static final long MAXIMUM_CONSTRAINT_FIELD_GRID_BYTES = 64L * 1024L * 1024L;

    private ReleaseArtifactLimitsCatalogV2() {
    }

    public static ReleaseV2Limits coreLimits() {
        return ReleaseV2Limits.defaults();
    }

    public static void requireCoreWithinCatalog(ReleaseV2Limits limits) {
        ReleaseV2Limits defaults = coreLimits();
        if (limits.maximumFileCount() > defaults.maximumFileCount()
                || limits.maximumArtifactBytes() > defaults.maximumArtifactBytes()
                || limits.maximumDirectoryBytes() > defaults.maximumDirectoryBytes()
                || limits.maximumZipBytes() > defaults.maximumZipBytes()
                || limits.maximumExpandedBytes() > defaults.maximumExpandedBytes()) {
            throw new IllegalArgumentException(
                    "Release format 2 limits exceed " + CATALOG_VERSION + " trusted ceiling");
        }
    }
}
