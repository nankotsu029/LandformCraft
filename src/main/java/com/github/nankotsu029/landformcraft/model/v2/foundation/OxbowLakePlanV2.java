package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.List;
import java.util.Objects;

/** Frozen V2-10-11 EXPERIMENTAL reach-cutoff oxbow basin plan bound to a parent river reach. */
public record OxbowLakePlanV2(
        int planVersion,
        String featureId,
        String parentRiverFeatureId,
        ParentRiverKind parentRiverKind,
        String parentRiverPlanChecksum,
        String parentRelationId,
        String cutoffReachId,
        String hostSurfaceFeatureId,
        String hostSurfaceGeometryChecksum,
        String hostRelationId,
        List<RingPoint> basinRing,
        long waterSurfaceYMillionths,
        long rimMinimumYMillionths,
        int selectedTargetDepthBlocks,
        int shoreWidthBlocks,
        int wetlandHandoffWidthBlocks,
        String terminalPolicy,
        int spillEdgeStartIndex,
        int spillwayWidthBlocks,
        int spillwayCorridorLengthBlocks,
        String basinMaskFieldId,
        String rimMaskFieldId,
        String wetlandHandoffFieldId,
        String ownershipFieldId,
        String cutoffFieldId,
        String levelFieldId,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.oxbow-lake";
    public static final String MODULE_VERSION = "0.1.0-v2-10-11";
    public static final String CONTRACT = "oxbow-lake-plan-contract-v1";
    public static final String TERMINAL_POLICY = "CLOSED";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;
    public static final String BASIN_MASK_FIELD_ID = "foundation.oxbow-lake.basin-mask";
    public static final String RIM_MASK_FIELD_ID = "foundation.oxbow-lake.rim-mask";
    public static final String WETLAND_HANDOFF_FIELD_ID = "foundation.oxbow-lake.wetland-handoff";
    public static final String OWNERSHIP_FIELD_ID = "foundation.oxbow-lake.ownership";
    public static final String CUTOFF_FIELD_ID = "foundation.oxbow-lake.cutoff";
    public static final String LEVEL_FIELD_ID = "foundation.oxbow-lake.stagnant-level";

    public enum ParentRiverKind {
        RIVER,
        MEANDERING_RIVER
    }

    public OxbowLakePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("oxbow lake planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        parentRiverFeatureId = FoundationValidationV2.slug(parentRiverFeatureId, "parentRiverFeatureId");
        Objects.requireNonNull(parentRiverKind, "parentRiverKind");
        parentRiverPlanChecksum = FoundationValidationV2.checksum(parentRiverPlanChecksum, "parentRiverPlanChecksum");
        parentRelationId = FoundationValidationV2.slug(parentRelationId, "parentRelationId");
        cutoffReachId = FoundationValidationV2.qualified(cutoffReachId, "cutoffReachId");
        hostSurfaceFeatureId = FoundationValidationV2.slug(hostSurfaceFeatureId, "hostSurfaceFeatureId");
        hostSurfaceGeometryChecksum = FoundationValidationV2.checksum(
                hostSurfaceGeometryChecksum, "hostSurfaceGeometryChecksum");
        hostRelationId = FoundationValidationV2.slug(hostRelationId, "hostRelationId");
        basinRing = List.copyOf(Objects.requireNonNull(basinRing, "basinRing"));
        if (basinRing.size() < 4 || !basinRing.getFirst().equals(basinRing.getLast())) {
            throw new IllegalArgumentException("oxbow basin ring must be closed with at least three vertices");
        }
        if (waterSurfaceYMillionths < 0L || rimMinimumYMillionths < 0L) {
            throw new IllegalArgumentException("oxbow surface coordinates must be non-negative");
        }
        if (waterSurfaceYMillionths != rimMinimumYMillionths) {
            throw new IllegalArgumentException("oxbow stagnant basin requires waterSurfaceY == rimMinimumY");
        }
        if (selectedTargetDepthBlocks < 1 || selectedTargetDepthBlocks > 32
                || shoreWidthBlocks < 1 || shoreWidthBlocks > 16
                || wetlandHandoffWidthBlocks < shoreWidthBlocks || wetlandHandoffWidthBlocks > 64) {
            throw new IllegalArgumentException("oxbow depth/shore/wetland contract is invalid");
        }
        terminalPolicy = FoundationValidationV2.nonBlank(terminalPolicy, "terminalPolicy", 16);
        if (!TERMINAL_POLICY.equals(terminalPolicy)) {
            throw new IllegalArgumentException("oxbow terminalPolicy must be CLOSED");
        }
        if (spillEdgeStartIndex != -1 || spillwayWidthBlocks != 0 || spillwayCorridorLengthBlocks != 0) {
            throw new IllegalArgumentException("closed oxbow must not carry spillway geometry");
        }
        basinMaskFieldId = FoundationValidationV2.qualified(basinMaskFieldId, "basinMaskFieldId");
        rimMaskFieldId = FoundationValidationV2.qualified(rimMaskFieldId, "rimMaskFieldId");
        wetlandHandoffFieldId = FoundationValidationV2.qualified(wetlandHandoffFieldId, "wetlandHandoffFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        cutoffFieldId = FoundationValidationV2.qualified(cutoffFieldId, "cutoffFieldId");
        levelFieldId = FoundationValidationV2.qualified(levelFieldId, "levelFieldId");
        if (supportRadiusXZ < 4 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("oxbow support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public OxbowLakePlanV2 withCanonicalChecksum(String checksum) {
        return new OxbowLakePlanV2(
                planVersion, featureId, parentRiverFeatureId, parentRiverKind, parentRiverPlanChecksum,
                parentRelationId, cutoffReachId, hostSurfaceFeatureId, hostSurfaceGeometryChecksum,
                hostRelationId, basinRing, waterSurfaceYMillionths, rimMinimumYMillionths,
                selectedTargetDepthBlocks, shoreWidthBlocks, wetlandHandoffWidthBlocks, terminalPolicy,
                spillEdgeStartIndex, spillwayWidthBlocks, spillwayCorridorLengthBlocks,
                basinMaskFieldId, rimMaskFieldId, wetlandHandoffFieldId, ownershipFieldId,
                cutoffFieldId, levelFieldId, supportRadiusXZ, estimatedWorkUnits, geometryChecksum, checksum);
    }

    public record RingPoint(long xMillionths, long zMillionths) {
        public RingPoint {
            if (xMillionths < 0L || zMillionths < 0L) {
                throw new IllegalArgumentException("ring point coordinates must be non-negative");
            }
        }
    }
}
