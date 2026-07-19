package com.github.nankotsu029.landformcraft.model.v2.catalog;

/**
 * Per-capability support level for the V2-6-18 Feature Support Catalog.
 * Do not collapse these into a single lifecycleStatus for capability decisions.
 */
public enum FeatureSupportLevelV2 {
    SUPPORTED,
    PARTIAL,
    EXPERIMENTAL,
    UNSUPPORTED,
    NOT_APPLICABLE
}
