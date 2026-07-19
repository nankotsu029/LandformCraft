package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Objects;
import java.util.Set;

/**
 * COMPOSITE_PRESET for {@code SINGLE_ISLAND + LAGOON} shore-parallel barrier composition.
 * Not a FeatureKind and not a dedicated world generator (V2-10-08).
 */
public record BarrierIslandPlanV2(
        int planVersion,
        String presetId,
        String contractVersion,
        String islandFeatureId,
        String islandGeometryChecksum,
        String lagoonFeatureId,
        String lagoonGeometryChecksum,
        int shoreParallelAzimuthDegrees,
        int ridgeHalfWidthBlocks,
        boolean marineConnectivityOk,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "barrier-island-preset-contract-v1";
    public static final String MODULE_ID = "v2.foundation.barrier-island";
    public static final String MODULE_VERSION = "0.1.0-v2-10-08";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;

    public BarrierIslandPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("barrier island planVersion must be 1");
        }
        presetId = FoundationValidationV2.slug(presetId, "presetId");
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown barrier island contract version");
        }
        islandFeatureId = FoundationValidationV2.slug(islandFeatureId, "islandFeatureId");
        islandGeometryChecksum = FoundationValidationV2.checksum(islandGeometryChecksum, "islandGeometryChecksum");
        lagoonFeatureId = FoundationValidationV2.slug(lagoonFeatureId, "lagoonFeatureId");
        lagoonGeometryChecksum = FoundationValidationV2.checksum(lagoonGeometryChecksum, "lagoonGeometryChecksum");
        if (shoreParallelAzimuthDegrees < 0 || shoreParallelAzimuthDegrees > 359) {
            throw new IllegalArgumentException("shoreParallelAzimuthDegrees must be 0..359");
        }
        if (ridgeHalfWidthBlocks < 1 || ridgeHalfWidthBlocks > 128) {
            throw new IllegalArgumentException("ridgeHalfWidthBlocks is invalid");
        }
        if (supportRadiusXZ < 2 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("barrier island support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        Objects.requireNonNull(Set.of(islandFeatureId, lagoonFeatureId), "composition ids");
    }

    public BarrierIslandPlanV2 withCanonicalChecksum(String checksum) {
        return new BarrierIslandPlanV2(
                planVersion, presetId, contractVersion, islandFeatureId, islandGeometryChecksum,
                lagoonFeatureId, lagoonGeometryChecksum, shoreParallelAzimuthDegrees, ridgeHalfWidthBlocks,
                marineConnectivityOk, supportRadiusXZ, estimatedWorkUnits, geometryChecksum, checksum);
    }
}
