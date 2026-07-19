package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;

import java.util.regex.Pattern;
import java.util.Objects;

/** Frozen V2-10-10 EXPERIMENTAL surface spring outlet plan bound to a general RIVER source node. */
public record SpringPlanV2(
        int planVersion,
        String featureId,
        String outletSurfaceFeatureId,
        String outletSurfaceGeometryChecksum,
        String outletRelationId,
        String riverFeatureId,
        String riverPlanChecksum,
        String riverSourceNodeId,
        String downstreamReachId,
        String riverRelationId,
        String hydrologyNodeId,
        String hydrologyNodeKind,
        long openingXMillionths,
        long openingYMillionths,
        long openingZMillionths,
        long sourceBedYMillionths,
        int sourceDischargeBlocks,
        TerrainIntentV2.DischargeClass dischargeClass,
        int outflowRadiusBlocks,
        String sourceMaskFieldId,
        String outflowMaskFieldId,
        String ownershipFieldId,
        String continuityFieldId,
        String reachabilityFieldId,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.spring";
    public static final String MODULE_VERSION = "0.1.0-v2-10-10";
    public static final String CONTRACT = "spring-plan-contract-v1";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;
    public static final String HYDROLOGY_NODE_KIND = HydrologyPlanV2.NodeKind.SPRING.name();
    public static final String SOURCE_MASK_FIELD_ID = "foundation.spring.source-mask";
    public static final String OUTFLOW_MASK_FIELD_ID = "foundation.spring.outflow-mask";
    public static final String OWNERSHIP_FIELD_ID = "foundation.spring.ownership";
    public static final String CONTINUITY_FIELD_ID = "foundation.spring.continuity";
    public static final String REACHABILITY_FIELD_ID = "foundation.spring.reachability";

    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");

    public SpringPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("spring planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        outletSurfaceFeatureId = FoundationValidationV2.slug(outletSurfaceFeatureId, "outletSurfaceFeatureId");
        outletSurfaceGeometryChecksum = requireChecksum(
                outletSurfaceGeometryChecksum, "outletSurfaceGeometryChecksum");
        outletRelationId = FoundationValidationV2.slug(outletRelationId, "outletRelationId");
        riverFeatureId = FoundationValidationV2.slug(riverFeatureId, "riverFeatureId");
        riverPlanChecksum = requireChecksum(riverPlanChecksum, "riverPlanChecksum");
        riverSourceNodeId = FoundationValidationV2.qualified(riverSourceNodeId, "riverSourceNodeId");
        downstreamReachId = FoundationValidationV2.qualified(downstreamReachId, "downstreamReachId");
        riverRelationId = FoundationValidationV2.slug(riverRelationId, "riverRelationId");
        hydrologyNodeId = FoundationValidationV2.qualified(hydrologyNodeId, "hydrologyNodeId");
        hydrologyNodeKind = FoundationValidationV2.nonBlank(hydrologyNodeKind, "hydrologyNodeKind", 32);
        if (!HYDROLOGY_NODE_KIND.equals(hydrologyNodeKind)) {
            throw new IllegalArgumentException("hydrologyNodeKind must be SPRING");
        }
        if (openingXMillionths < 0L || openingZMillionths < 0L || openingYMillionths < 0L) {
            throw new IllegalArgumentException("spring opening coordinates must be non-negative");
        }
        if (sourceDischargeBlocks < 1 || sourceDischargeBlocks > 1_000_000) {
            throw new IllegalArgumentException("sourceDischargeBlocks must be in 1..1000000");
        }
        Objects.requireNonNull(dischargeClass, "dischargeClass");
        if (outflowRadiusBlocks < 1 || outflowRadiusBlocks > 16) {
            throw new IllegalArgumentException("outflowRadiusBlocks must be in 1..16");
        }
        sourceMaskFieldId = FoundationValidationV2.qualified(sourceMaskFieldId, "sourceMaskFieldId");
        outflowMaskFieldId = FoundationValidationV2.qualified(outflowMaskFieldId, "outflowMaskFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        continuityFieldId = FoundationValidationV2.qualified(continuityFieldId, "continuityFieldId");
        reachabilityFieldId = FoundationValidationV2.qualified(reachabilityFieldId, "reachabilityFieldId");
        if (supportRadiusXZ < 2 || supportRadiusXZ > 32
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("spring support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public SpringPlanV2 withCanonicalChecksum(String checksum) {
        return new SpringPlanV2(
                planVersion, featureId, outletSurfaceFeatureId, outletSurfaceGeometryChecksum,
                outletRelationId, riverFeatureId, riverPlanChecksum, riverSourceNodeId,
                downstreamReachId, riverRelationId, hydrologyNodeId, hydrologyNodeKind,
                openingXMillionths, openingYMillionths, openingZMillionths, sourceBedYMillionths,
                sourceDischargeBlocks, dischargeClass, outflowRadiusBlocks,
                sourceMaskFieldId, outflowMaskFieldId, ownershipFieldId, continuityFieldId,
                reachabilityFieldId, supportRadiusXZ, estimatedWorkUnits, geometryChecksum, checksum);
    }

    private static String requireChecksum(String value, String field) {
        String checksum = FoundationValidationV2.checksum(value, field);
        if (!CHECKSUM.matcher(checksum).matches()) {
            throw new IllegalArgumentException(field + " must be 64 lowercase hex");
        }
        return checksum;
    }
}
