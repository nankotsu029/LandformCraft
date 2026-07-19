package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Objects;
import java.util.Set;

/**
 * COMPOSITE_PRESET for {@code FJORD + VALLEY_GLACIER + cold/snow/ice profile}.
 * Not a FeatureKind and not a dedicated world generator (V2-10-01).
 */
public record IceFjordPlanV2(
        int planVersion,
        String presetId,
        String contractVersion,
        String fjordFeatureId,
        String fjordGeometryChecksum,
        String valleyGlacierFeatureId,
        String valleyGlacierPlanChecksum,
        String climatePreset,
        String climateBindingChecksum,
        String snowProfileId,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "ice-fjord-preset-contract-v1";
    public static final String MODULE_ID = "v2.foundation.ice-fjord";
    public static final String MODULE_VERSION = "0.1.0-v2-10-01";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;
    public static final String DEFAULT_SNOW_PROFILE_ID = GlacialIcePlanV2.DEFAULT_SNOW_PROFILE_ID;

    public IceFjordPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("ice fjord planVersion must be 1");
        }
        presetId = FoundationValidationV2.slug(presetId, "presetId");
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown ice fjord contract version");
        }
        fjordFeatureId = FoundationValidationV2.slug(fjordFeatureId, "fjordFeatureId");
        fjordGeometryChecksum = FoundationValidationV2.checksum(fjordGeometryChecksum, "fjordGeometryChecksum");
        valleyGlacierFeatureId = FoundationValidationV2.slug(
                valleyGlacierFeatureId, "valleyGlacierFeatureId");
        valleyGlacierPlanChecksum = FoundationValidationV2.checksum(
                valleyGlacierPlanChecksum, "valleyGlacierPlanChecksum");
        climatePreset = FoundationValidationV2.nonBlank(climatePreset, "climatePreset", 64);
        if (!GlacialIcePlanV2.COLD_CLIMATE_PRESETS.contains(climatePreset)) {
            throw new IllegalArgumentException("ice fjord requires a cold climatePreset");
        }
        climateBindingChecksum = FoundationValidationV2.checksum(
                climateBindingChecksum, "climateBindingChecksum");
        snowProfileId = FoundationValidationV2.qualified(snowProfileId, "snowProfileId");
        if (supportRadiusXZ < 2 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("ice fjord support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        Objects.requireNonNull(Set.of(fjordFeatureId, valleyGlacierFeatureId), "composition ids");
    }

    public IceFjordPlanV2 withCanonicalChecksum(String checksum) {
        return new IceFjordPlanV2(
                planVersion, presetId, contractVersion, fjordFeatureId, fjordGeometryChecksum,
                valleyGlacierFeatureId, valleyGlacierPlanChecksum, climatePreset,
                climateBindingChecksum, snowProfileId, supportRadiusXZ, estimatedWorkUnits,
                geometryChecksum, checksum);
    }
}
