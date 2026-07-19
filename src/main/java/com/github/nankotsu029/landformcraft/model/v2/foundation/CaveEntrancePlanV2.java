package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Frozen V2-9-10 execution plan for an EXPERIMENTAL cave-entrance surface-volume connector. */
public record CaveEntrancePlanV2(
        int planVersion,
        String featureId,
        String surfaceHostFeatureId,
        SurfaceHostKind surfaceHostKind,
        String surfaceHostRelationId,
        String caveNetworkFeatureId,
        String caveEntranceRelationId,
        String targetEntranceNodeId,
        String hostCaveCanonicalChecksum,
        String surfaceHostGeometryChecksum,
        long openingXMillionths,
        long openingZMillionths,
        int surfaceYBlocks,
        int openingYBlocks,
        ApproachCapsule approach,
        int selectedSurfaceOffsetBlocks,
        int selectedMinimumOpeningBlocks,
        int selectedApproachLengthBlocks,
        int selectedRoofClearanceBlocks,
        VolumeSdfAabbV2 aabb,
        List<OrderedCarveOp> orderedCarveOps,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        String openingMaskFieldId,
        String approachMaskFieldId,
        String ownershipFieldId,
        String reachabilityFieldId,
        String roofClearanceFieldId,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.cave-entrance";
    public static final String MODULE_VERSION = "0.1.0-v2-9-10";
    public static final String CONTRACT = "cave-entrance-plan-contract-v1";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;
    public static final int MAXIMUM_CARVE_OPS = 8;
    public static final String OPENING_MASK_FIELD_ID = "foundation.cave-entrance.opening-mask";
    public static final String APPROACH_MASK_FIELD_ID = "foundation.cave-entrance.approach-mask";
    public static final String OWNERSHIP_FIELD_ID = "foundation.cave-entrance.ownership";
    public static final String REACHABILITY_FIELD_ID = "foundation.cave-entrance.reachability";
    public static final String ROOF_CLEARANCE_FIELD_ID = "foundation.cave-entrance.roof-clearance";

    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");

    public enum SurfaceHostKind {
        MOUNTAIN_RANGE,
        VALLEY
    }

    public CaveEntrancePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("cave entrance planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        surfaceHostFeatureId = FoundationValidationV2.slug(surfaceHostFeatureId, "surfaceHostFeatureId");
        Objects.requireNonNull(surfaceHostKind, "surfaceHostKind");
        surfaceHostRelationId = FoundationValidationV2.slug(surfaceHostRelationId, "surfaceHostRelationId");
        caveNetworkFeatureId = FoundationValidationV2.qualified(caveNetworkFeatureId, "caveNetworkFeatureId");
        caveEntranceRelationId = FoundationValidationV2.slug(caveEntranceRelationId, "caveEntranceRelationId");
        targetEntranceNodeId = FoundationValidationV2.qualified(targetEntranceNodeId, "targetEntranceNodeId");
        hostCaveCanonicalChecksum = requireChecksum(hostCaveCanonicalChecksum, "hostCaveCanonicalChecksum");
        surfaceHostGeometryChecksum = requireChecksum(surfaceHostGeometryChecksum, "surfaceHostGeometryChecksum");
        Objects.requireNonNull(approach, "approach");
        if (selectedSurfaceOffsetBlocks > -1 || selectedSurfaceOffsetBlocks < -64
                || selectedMinimumOpeningBlocks < 2 || selectedMinimumOpeningBlocks > 16
                || selectedApproachLengthBlocks < 2 || selectedApproachLengthBlocks > 32
                || selectedRoofClearanceBlocks < 1 || selectedRoofClearanceBlocks > 16) {
            throw new IllegalArgumentException("cave entrance selected dimensions are invalid");
        }
        if (openingYBlocks != Math.addExact(surfaceYBlocks, selectedSurfaceOffsetBlocks)) {
            throw new IllegalArgumentException("openingYBlocks must equal surfaceYBlocks + surfaceOffset");
        }
        Objects.requireNonNull(aabb, "aabb");
        orderedCarveOps = FoundationValidationV2.sorted(
                orderedCarveOps, "orderedCarveOps", MAXIMUM_CARVE_OPS,
                Comparator.comparingInt(OrderedCarveOp::ordinal).thenComparing(OrderedCarveOp::opId));
        if (orderedCarveOps.isEmpty()) {
            throw new IllegalArgumentException("cave entrance requires at least one ordered carve op");
        }
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        openingMaskFieldId = FoundationValidationV2.qualified(openingMaskFieldId, "openingMaskFieldId");
        approachMaskFieldId = FoundationValidationV2.qualified(approachMaskFieldId, "approachMaskFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        reachabilityFieldId = FoundationValidationV2.qualified(reachabilityFieldId, "reachabilityFieldId");
        roofClearanceFieldId = FoundationValidationV2.qualified(roofClearanceFieldId, "roofClearanceFieldId");
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("cave entrance support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public CaveEntrancePlanV2 withCanonicalChecksum(String checksum) {
        return new CaveEntrancePlanV2(
                planVersion, featureId, surfaceHostFeatureId, surfaceHostKind, surfaceHostRelationId,
                caveNetworkFeatureId, caveEntranceRelationId, targetEntranceNodeId,
                hostCaveCanonicalChecksum, surfaceHostGeometryChecksum,
                openingXMillionths, openingZMillionths, surfaceYBlocks, openingYBlocks, approach,
                selectedSurfaceOffsetBlocks, selectedMinimumOpeningBlocks, selectedApproachLengthBlocks,
                selectedRoofClearanceBlocks, aabb, orderedCarveOps, sdfPlanBinding, csgPlanBinding,
                openingMaskFieldId, approachMaskFieldId, ownershipFieldId, reachabilityFieldId,
                roofClearanceFieldId, supportRadiusXZ, estimatedWorkUnits, geometryChecksum, checksum);
    }

    public record ApproachCapsule(
            long startXMillionths,
            long startYMillionths,
            long startZMillionths,
            long endXMillionths,
            long endYMillionths,
            long endZMillionths,
            long radiusMillionths
    ) {
        public ApproachCapsule {
            if (radiusMillionths < TerrainIntentV2.FIXED_SCALE
                    || radiusMillionths > 16L * TerrainIntentV2.FIXED_SCALE) {
                throw new IllegalArgumentException("cave entrance approach radius is invalid");
            }
            if (startXMillionths == endXMillionths
                    && startYMillionths == endYMillionths
                    && startZMillionths == endZMillionths) {
                throw new IllegalArgumentException("cave entrance approach endpoints must be distinct");
            }
        }
    }

    public record OrderedCarveOp(
            String opId,
            int ordinal,
            String operationKind,
            String primitiveId
    ) {
        public static final String CARVE_SOLID = "CARVE_SOLID";

        public OrderedCarveOp {
            opId = FoundationValidationV2.qualified(opId, "opId");
            if (ordinal < 0 || ordinal > 1_000) {
                throw new IllegalArgumentException("ordered carve ordinal out of range");
            }
            operationKind = FoundationValidationV2.nonBlank(operationKind, "operationKind", 32);
            if (!CARVE_SOLID.equals(operationKind)) {
                throw new IllegalArgumentException("cave entrance carve op must be CARVE_SOLID");
            }
            primitiveId = FoundationValidationV2.qualified(primitiveId, "primitiveId");
        }
    }

    public record ArtifactBinding(
            int bindingVersion,
            String sourceArtifactChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String SDF_CONTRACT = "cave-entrance-sdf-binding-v1";
        public static final String CSG_CONTRACT = "cave-entrance-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("cave entrance artifact bindingVersion must be 1");
            }
            sourceArtifactChecksum = requireChecksum(sourceArtifactChecksum, "sourceArtifactChecksum");
            bindingContractVersion = FoundationValidationV2.nonBlank(
                    bindingContractVersion, "bindingContractVersion", 64);
        }
    }

    private static String requireChecksum(String value, String field) {
        String checksum = FoundationValidationV2.checksum(value, field);
        if (!CHECKSUM.matcher(checksum).matches()) {
            throw new IllegalArgumentException(field + " must be 64 lowercase hex");
        }
        return checksum;
    }
}
