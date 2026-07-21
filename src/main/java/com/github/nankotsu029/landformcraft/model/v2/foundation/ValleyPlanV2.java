package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Frozen V2-9-03 execution plan for an EXPERIMENTAL valley foundation profile. */
public record ValleyPlanV2(
        int planVersion,
        String featureId,
        List<ThalwegPoint> thalwegPoints,
        TerrainIntentV2.ValleyCrossSection crossSection,
        int selectedFloorHalfWidthBlocks,
        int selectedShoulderWidthBlocks,
        int selectedMaxDepthBlocks,
        int mountainTransitionBandBlocks,
        TerrainIntentV2.ValleyConnectionRole connectionRole,
        List<ConnectionAnchor> connectionAnchors,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String floorMaskFieldId,
        String shoulderMaskFieldId,
        String depthFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.valley";
    public static final String MODULE_VERSION = "0.1.0-v2-9-03";
    public static final String CONTRACT = "valley-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String FLOOR_MASK_FIELD_ID = "foundation.valley.floor-mask";
    public static final String SHOULDER_MASK_FIELD_ID = "foundation.valley.shoulder-mask";
    public static final String DEPTH_FIELD_ID = "foundation.valley.depth";

    public ValleyPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("valley planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        thalwegPoints = FoundationValidationV2.sorted(thalwegPoints, "thalwegPoints", 16_384,
                Comparator.comparingLong(ThalwegPoint::arcLengthMillionths));
        if (thalwegPoints.size() < 2) {
            throw new IllegalArgumentException("valley needs at least two thalweg points");
        }
        Objects.requireNonNull(crossSection, "crossSection");
        connectionRole = Objects.requireNonNull(connectionRole, "connectionRole");
        connectionAnchors = FoundationValidationV2.sorted(connectionAnchors, "connectionAnchors", 8,
                Comparator.comparing(ConnectionAnchor::anchorId));
        if (connectionRole == TerrainIntentV2.ValleyConnectionRole.NONE && !connectionAnchors.isEmpty()) {
            throw new IllegalArgumentException("valley NONE connection role forbids connection anchors");
        }
        if (connectionRole != TerrainIntentV2.ValleyConnectionRole.NONE && connectionAnchors.isEmpty()) {
            throw new IllegalArgumentException("valley connection role requires at least one connection anchor");
        }
        if (selectedFloorHalfWidthBlocks < 2 || selectedFloorHalfWidthBlocks > 64
                || selectedShoulderWidthBlocks < 1 || selectedShoulderWidthBlocks > 32
                || selectedMaxDepthBlocks < 4 || selectedMaxDepthBlocks > 64) {
            throw new IllegalArgumentException("valley profile dimensions are invalid");
        }
        if (mountainTransitionBandBlocks < 1 || mountainTransitionBandBlocks > 32) {
            throw new IllegalArgumentException("mountainTransitionBandBlocks outside 1..32");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("valley bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        long previousArc = -1L;
        for (ThalwegPoint point : thalwegPoints) {
            if (point.arcLengthMillionths() <= previousArc
                    || point.xMillionths() < 0 || point.xMillionths() > maxX
                    || point.zMillionths() < 0 || point.zMillionths() > maxZ) {
                throw new IllegalArgumentException("valley thalweg is invalid");
            }
            previousArc = point.arcLengthMillionths();
        }
        floorMaskFieldId = FoundationValidationV2.qualified(floorMaskFieldId, "floorMaskFieldId");
        shoulderMaskFieldId = FoundationValidationV2.qualified(shoulderMaskFieldId, "shoulderMaskFieldId");
        depthFieldId = FoundationValidationV2.qualified(depthFieldId, "depthFieldId");
        int supportNeed = selectedFloorHalfWidthBlocks + selectedShoulderWidthBlocks;
        if (supportRadiusXZ < supportNeed || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("valley support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public ValleyPlanV2 withCanonicalChecksum(String checksum) {
        return new ValleyPlanV2(
                planVersion, featureId, thalwegPoints, crossSection,
                selectedFloorHalfWidthBlocks, selectedShoulderWidthBlocks, selectedMaxDepthBlocks,
                mountainTransitionBandBlocks, connectionRole, connectionAnchors,
                minY, maxY, waterLevel, width, length,
                floorMaskFieldId, shoulderMaskFieldId, depthFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }

    public record ThalwegPoint(long xMillionths, long zMillionths, long arcLengthMillionths) {
        public ThalwegPoint {
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0) {
                throw new IllegalArgumentException("valley thalweg point is invalid");
            }
        }
    }

    public record ConnectionAnchor(
            String anchorId,
            TerrainIntentV2.ValleyConnectionRole role,
            long xMillionths,
            long zMillionths
    ) {
        public ConnectionAnchor {
            anchorId = FoundationValidationV2.slug(anchorId, "anchorId");
            Objects.requireNonNull(role, "role");
            if (role == TerrainIntentV2.ValleyConnectionRole.NONE) {
                throw new IllegalArgumentException("connection anchor role cannot be NONE");
            }
            if (xMillionths < 0 || zMillionths < 0) {
                throw new IllegalArgumentException("valley connection anchor is invalid");
            }
        }
    }
}
