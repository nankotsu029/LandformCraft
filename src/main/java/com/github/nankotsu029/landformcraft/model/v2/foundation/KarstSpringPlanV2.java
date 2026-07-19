package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.regex.Pattern;

/**
 * Frozen V2-10-03 EXPERIMENTAL karst spring outlet plan. Discharge volume must balance
 * paired sinkhole loss for static karst hydrology graph closure.
 */
public record KarstSpringPlanV2(
        int planVersion,
        String featureId,
        String caveNetworkFeatureId,
        String caveNetworkCanonicalChecksum,
        String caveRelationId,
        String outletSurfaceFeatureId,
        String outletSurfaceGeometryChecksum,
        String outletRelationId,
        int springDischargeBlocks,
        String materialHandoffId,
        long openingXMillionths,
        long openingYMillionths,
        long openingZMillionths,
        String dischargeMaskFieldId,
        String ownershipFieldId,
        String reachabilityFieldId,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.karst-spring";
    public static final String MODULE_VERSION = "0.1.0-v2-10-03";
    public static final String CONTRACT = "karst-spring-plan-contract-v1";
    public static final String MATERIAL_HANDOFF_ID = SinkholePlanV2.MATERIAL_HANDOFF_ID;
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;
    public static final String DISCHARGE_MASK_FIELD_ID = "foundation.karst-spring.discharge-mask";
    public static final String OWNERSHIP_FIELD_ID = "foundation.karst-spring.ownership";
    public static final String REACHABILITY_FIELD_ID = "foundation.karst-spring.reachability";

    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");

    public KarstSpringPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("karst spring planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        caveNetworkFeatureId = FoundationValidationV2.slug(caveNetworkFeatureId, "caveNetworkFeatureId");
        caveNetworkCanonicalChecksum = requireChecksum(caveNetworkCanonicalChecksum, "caveNetworkCanonicalChecksum");
        caveRelationId = FoundationValidationV2.slug(caveRelationId, "caveRelationId");
        outletSurfaceFeatureId = FoundationValidationV2.slug(outletSurfaceFeatureId, "outletSurfaceFeatureId");
        outletSurfaceGeometryChecksum = requireChecksum(
                outletSurfaceGeometryChecksum, "outletSurfaceGeometryChecksum");
        outletRelationId = FoundationValidationV2.slug(outletRelationId, "outletRelationId");
        if (springDischargeBlocks < 1 || springDischargeBlocks > 1_000_000) {
            throw new IllegalArgumentException("springDischargeBlocks must be in 1..1000000");
        }
        materialHandoffId = FoundationValidationV2.qualified(materialHandoffId, "materialHandoffId");
        if (!MATERIAL_HANDOFF_ID.equals(materialHandoffId)) {
            throw new IllegalArgumentException("karst spring materialHandoffId must be karst-limestone-handoff");
        }
        if (openingXMillionths < 0L || openingZMillionths < 0L || openingYMillionths < 0L) {
            throw new IllegalArgumentException("karst spring opening coordinates must be non-negative");
        }
        dischargeMaskFieldId = FoundationValidationV2.qualified(dischargeMaskFieldId, "dischargeMaskFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        reachabilityFieldId = FoundationValidationV2.qualified(reachabilityFieldId, "reachabilityFieldId");
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("karst spring support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public KarstSpringPlanV2 withCanonicalChecksum(String checksum) {
        return new KarstSpringPlanV2(
                planVersion, featureId, caveNetworkFeatureId, caveNetworkCanonicalChecksum,
                caveRelationId, outletSurfaceFeatureId, outletSurfaceGeometryChecksum,
                outletRelationId, springDischargeBlocks, materialHandoffId,
                openingXMillionths, openingYMillionths, openingZMillionths,
                dischargeMaskFieldId, ownershipFieldId, reachabilityFieldId,
                supportRadiusXZ, estimatedWorkUnits, geometryChecksum, checksum);
    }

    private static String requireChecksum(String value, String field) {
        String checksum = FoundationValidationV2.checksum(value, field);
        if (!CHECKSUM.matcher(checksum).matches()) {
            throw new IllegalArgumentException(field + " must be 64 lowercase hex");
        }
        return checksum;
    }
}
