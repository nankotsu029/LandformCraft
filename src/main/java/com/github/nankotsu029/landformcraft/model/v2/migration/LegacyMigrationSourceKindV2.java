package com.github.nankotsu029.landformcraft.model.v2.migration;

/**
 * The v1 asset kinds the migration tool accepts (V2-12-04).
 *
 * <p>The kind is always stated by the operator. Nothing is auto-detected, so a corrupted or
 * mislabelled artifact fails the strict reader for its declared kind instead of silently falling
 * through to a weaker one.</p>
 */
public enum LegacyMigrationSourceKindV2 {
    /** A standalone v1 {@code terrain-intent.json}. */
    V1_TERRAIN_INTENT,
    /** A canonical v1 design package directory. */
    V1_DESIGN_PACKAGE,
    /** A Release format 1 directory or {@code .zip}. */
    V1_RELEASE
}
