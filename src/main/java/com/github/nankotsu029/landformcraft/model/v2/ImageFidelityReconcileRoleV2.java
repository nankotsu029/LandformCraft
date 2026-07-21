package com.github.nankotsu029.landformcraft.model.v2;

/**
 * Roles accepted by multi-source reconciliation. Values are opaque U8 samples; semantics stay
 * with the caller (land-water classes, height samples, zone samples).
 */
public enum ImageFidelityReconcileRoleV2 {
    LAND_WATER_MASK,
    HEIGHT_GUIDE,
    ZONE_LABEL_MAP
}
