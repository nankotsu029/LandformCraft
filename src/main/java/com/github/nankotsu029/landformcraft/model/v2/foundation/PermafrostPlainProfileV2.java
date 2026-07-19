package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Set;

/**
 * ENVIRONMENT_PROFILE for cold-ground permafrost on an existing {@code PLAIN}.
 * Not a FeatureKind and not a dedicated landform generator (V2-10-02).
 */
public record PermafrostPlainProfileV2(
        int planVersion,
        String profileId,
        String contractVersion,
        String plainFeatureId,
        String plainPlanChecksum,
        String climatePreset,
        String climateBindingChecksum,
        int activeLayerDepthBlocks,
        int iceContent01Millionths,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "permafrost-plain-profile-contract-v1";
    public static final String MODULE_ID = "v2.foundation.permafrost-plain-profile";
    public static final String MODULE_VERSION = "0.1.0-v2-10-02";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;

    public PermafrostPlainProfileV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("permafrost plain profile planVersion must be 1");
        }
        profileId = FoundationValidationV2.slug(profileId, "profileId");
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown permafrost plain profile contract version");
        }
        plainFeatureId = FoundationValidationV2.slug(plainFeatureId, "plainFeatureId");
        plainPlanChecksum = FoundationValidationV2.checksum(plainPlanChecksum, "plainPlanChecksum");
        climatePreset = FoundationValidationV2.nonBlank(climatePreset, "climatePreset", 64);
        if (!GlacialIcePlanV2.COLD_CLIMATE_PRESETS.contains(climatePreset)) {
            throw new IllegalArgumentException("permafrost plain requires a cold climatePreset");
        }
        climateBindingChecksum = FoundationValidationV2.checksum(
                climateBindingChecksum, "climateBindingChecksum");
        if (activeLayerDepthBlocks < 1 || activeLayerDepthBlocks > 16) {
            throw new IllegalArgumentException("activeLayerDepthBlocks must be in 1..16");
        }
        if (iceContent01Millionths < 100_000 || iceContent01Millionths > 900_000) {
            throw new IllegalArgumentException("iceContent01 must be in 0.1..0.9");
        }
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("permafrost plain support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        Set.of(plainFeatureId, climatePreset);
    }

    public PermafrostPlainProfileV2 withCanonicalChecksum(String checksum) {
        return new PermafrostPlainProfileV2(
                planVersion, profileId, contractVersion, plainFeatureId, plainPlanChecksum,
                climatePreset, climateBindingChecksum, activeLayerDepthBlocks, iceContent01Millionths,
                supportRadiusXZ, estimatedWorkUnits, geometryChecksum, checksum);
    }
}
