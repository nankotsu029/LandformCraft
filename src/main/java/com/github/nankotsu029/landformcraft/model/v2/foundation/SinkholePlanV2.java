package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.regex.Pattern;

/**
 * Frozen V2-10-03 EXPERIMENTAL sinkhole plan. Surface collapse opening bound to host cave
 * by canonical checksum; static loss volume for karst hydrology balance.
 */
public record SinkholePlanV2(
        int planVersion,
        String featureId,
        String surfaceHostFeatureId,
        String surfaceHostGeometryChecksum,
        String surfaceHostRelationId,
        String caveNetworkFeatureId,
        String caveNetworkCanonicalChecksum,
        String caveRelationId,
        String targetEntranceNodeId,
        int collapseRadiusBlocks,
        int roofClearanceBlocks,
        int lossVolumeBlocks,
        String materialHandoffId,
        long openingXMillionths,
        long openingYMillionths,
        long openingZMillionths,
        String collapseMaskFieldId,
        String ownershipFieldId,
        String reachabilityFieldId,
        String roofClearanceFieldId,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.sinkhole";
    public static final String MODULE_VERSION = "0.1.0-v2-10-03";
    public static final String CONTRACT = "sinkhole-plan-contract-v1";
    public static final String MATERIAL_HANDOFF_ID = "karst-limestone-handoff";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;
    public static final String COLLAPSE_MASK_FIELD_ID = "foundation.sinkhole.collapse-mask";
    public static final String OWNERSHIP_FIELD_ID = "foundation.sinkhole.ownership";
    public static final String REACHABILITY_FIELD_ID = "foundation.sinkhole.reachability";
    public static final String ROOF_CLEARANCE_FIELD_ID = "foundation.sinkhole.roof-clearance";

    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");

    public SinkholePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("sinkhole planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        surfaceHostFeatureId = FoundationValidationV2.slug(surfaceHostFeatureId, "surfaceHostFeatureId");
        surfaceHostGeometryChecksum = requireChecksum(surfaceHostGeometryChecksum, "surfaceHostGeometryChecksum");
        surfaceHostRelationId = FoundationValidationV2.slug(surfaceHostRelationId, "surfaceHostRelationId");
        caveNetworkFeatureId = FoundationValidationV2.slug(caveNetworkFeatureId, "caveNetworkFeatureId");
        caveNetworkCanonicalChecksum = requireChecksum(caveNetworkCanonicalChecksum, "caveNetworkCanonicalChecksum");
        caveRelationId = FoundationValidationV2.slug(caveRelationId, "caveRelationId");
        targetEntranceNodeId = FoundationValidationV2.slug(targetEntranceNodeId, "targetEntranceNodeId");
        if (collapseRadiusBlocks < 2 || collapseRadiusBlocks > 16
                || roofClearanceBlocks < 1 || roofClearanceBlocks > 16
                || lossVolumeBlocks < 1 || lossVolumeBlocks > 1_000_000) {
            throw new IllegalArgumentException("sinkhole dimensional parameters are invalid");
        }
        materialHandoffId = FoundationValidationV2.qualified(materialHandoffId, "materialHandoffId");
        if (!MATERIAL_HANDOFF_ID.equals(materialHandoffId)) {
            throw new IllegalArgumentException("sinkhole materialHandoffId must be karst-limestone-handoff");
        }
        if (openingXMillionths < 0L || openingZMillionths < 0L || openingYMillionths < 0L) {
            throw new IllegalArgumentException("sinkhole opening coordinates must be non-negative");
        }
        collapseMaskFieldId = FoundationValidationV2.qualified(collapseMaskFieldId, "collapseMaskFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        reachabilityFieldId = FoundationValidationV2.qualified(reachabilityFieldId, "reachabilityFieldId");
        roofClearanceFieldId = FoundationValidationV2.qualified(roofClearanceFieldId, "roofClearanceFieldId");
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("sinkhole support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public SinkholePlanV2 withCanonicalChecksum(String checksum) {
        return new SinkholePlanV2(
                planVersion, featureId, surfaceHostFeatureId, surfaceHostGeometryChecksum,
                surfaceHostRelationId, caveNetworkFeatureId, caveNetworkCanonicalChecksum,
                caveRelationId, targetEntranceNodeId, collapseRadiusBlocks, roofClearanceBlocks,
                lossVolumeBlocks, materialHandoffId, openingXMillionths, openingYMillionths,
                openingZMillionths, collapseMaskFieldId, ownershipFieldId, reachabilityFieldId,
                roofClearanceFieldId, supportRadiusXZ, estimatedWorkUnits, geometryChecksum, checksum);
    }

    private static String requireChecksum(String value, String field) {
        String checksum = FoundationValidationV2.checksum(value, field);
        if (!CHECKSUM.matcher(checksum).matches()) {
            throw new IllegalArgumentException(field + " must be 64 lowercase hex");
        }
        return checksum;
    }
}
