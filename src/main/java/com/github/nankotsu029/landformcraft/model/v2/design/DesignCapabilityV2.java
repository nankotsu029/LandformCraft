package com.github.nankotsu029.landformcraft.model.v2.design;

/**
 * Explicit v2 design capabilities. Selection is mandatory; unsupported requests fail without
 * falling back to v1 or a different capability.
 */
public enum DesignCapabilityV2 {
    /** Provider or import returns a locally revalidated {@code TerrainIntentV2}. */
    TERRAIN_INTENT_V2_STRUCTURED,
    /** Numeric constraint-map bundle bound through the V2-1 manual path. */
    MANUAL_CONSTRAINT_BUNDLE,
    /** Deterministic reference-image land-water soft draft requiring confirmation. */
    REFERENCE_IMAGE_SOFT_DRAFT
}
