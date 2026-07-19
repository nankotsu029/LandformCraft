package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Objects;
import java.util.Set;

/**
 * COMPOSITE_PRESET for {@code CORAL_REEF + LAGOON + REEF_PASS} ring atoll composition.
 * Not a FeatureKind and not a dedicated world generator (V2-10-08).
 */
public record AtollPlanV2(
        int planVersion,
        String presetId,
        String contractVersion,
        String reefFeatureId,
        String reefGeometryChecksum,
        String lagoonFeatureId,
        String lagoonGeometryChecksum,
        String reefPassFeatureId,
        String reefPassGeometryChecksum,
        String optionalIsletFeatureId,
        String isletGeometryChecksum,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "atoll-preset-contract-v1";
    public static final String MODULE_ID = "v2.foundation.atoll";
    public static final String MODULE_VERSION = "0.1.0-v2-10-08";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;
    public static final String EMPTY_CHECKSUM = "0".repeat(64);

    public AtollPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("atoll planVersion must be 1");
        }
        presetId = FoundationValidationV2.slug(presetId, "presetId");
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown atoll contract version");
        }
        reefFeatureId = FoundationValidationV2.slug(reefFeatureId, "reefFeatureId");
        reefGeometryChecksum = FoundationValidationV2.checksum(reefGeometryChecksum, "reefGeometryChecksum");
        lagoonFeatureId = FoundationValidationV2.slug(lagoonFeatureId, "lagoonFeatureId");
        lagoonGeometryChecksum = FoundationValidationV2.checksum(lagoonGeometryChecksum, "lagoonGeometryChecksum");
        reefPassFeatureId = FoundationValidationV2.slug(reefPassFeatureId, "reefPassFeatureId");
        reefPassGeometryChecksum = FoundationValidationV2.checksum(reefPassGeometryChecksum, "reefPassGeometryChecksum");
        optionalIsletFeatureId = FoundationValidationV2.optionalSlug(optionalIsletFeatureId, "optionalIsletFeatureId");
        isletGeometryChecksum = FoundationValidationV2.checksum(isletGeometryChecksum, "isletGeometryChecksum");
        if (optionalIsletFeatureId.isBlank()) {
            if (!EMPTY_CHECKSUM.equals(isletGeometryChecksum)) {
                throw new IllegalArgumentException("absent islet requires zero isletGeometryChecksum");
            }
        } else if (EMPTY_CHECKSUM.equals(isletGeometryChecksum)) {
            throw new IllegalArgumentException("present islet requires bound isletGeometryChecksum");
        }
        if (supportRadiusXZ < 2 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("atoll support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        Objects.requireNonNull(Set.of(reefFeatureId, lagoonFeatureId, reefPassFeatureId), "composition ids");
    }

    public AtollPlanV2 withCanonicalChecksum(String checksum) {
        return new AtollPlanV2(
                planVersion, presetId, contractVersion, reefFeatureId, reefGeometryChecksum,
                lagoonFeatureId, lagoonGeometryChecksum, reefPassFeatureId, reefPassGeometryChecksum,
                optionalIsletFeatureId, isletGeometryChecksum, supportRadiusXZ, estimatedWorkUnits,
                geometryChecksum, checksum);
    }
}
