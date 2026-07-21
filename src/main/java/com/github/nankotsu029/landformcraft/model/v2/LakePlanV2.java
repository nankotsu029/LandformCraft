package com.github.nankotsu029.landformcraft.model.v2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Canonical V2-3-04 execution plan for one independent LAKE basin.
 * Dam／reservoir structures are out of scope; spill is a natural rim opening only.
 */
public record LakePlanV2(
        int planVersion,
        String featureId,
        TerrainIntentV2.LakeTerminalPolicy terminalPolicy,
        TerrainIntentV2.LakeSpillSelection spillSelection,
        TerrainIntentV2.LakeFloorProfile floorProfile,
        String basinId,
        String waterBodyId,
        String outletNodeId,
        List<String> inletNodeIds,
        List<RingPoint> basinRing,
        long waterSurfaceYMillionths,
        long rimMinimumYMillionths,
        long maximumDepthMillionths,
        int minimumTargetDepthBlocks,
        int maximumTargetDepthBlocks,
        int selectedTargetDepthBlocks,
        int shoreWidthBlocks,
        int spillEdgeStartIndex,
        RingPoint spillFirst,
        RingPoint spillSecond,
        long outwardUnitXMillionths,
        long outwardUnitZMillionths,
        int spillwayWidthBlocks,
        int spillwayCorridorLengthBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String basinMaskFieldId,
        String rimMaskFieldId,
        String spillwayMaskFieldId,
        String depthFieldId,
        String floorHeightFieldId,
        String surfaceFieldId,
        String bedElevationFieldId,
        String waterSurfaceFieldId,
        String waterDepthFieldId,
        String waterBodyIdFieldId,
        int supportRadiusXZ,
        String geometryChecksum
) {
    public static final int VERSION = 1;
    public static final int MAXIMUM_RING_POINTS = 1_024;

    public LakePlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("lake planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        Objects.requireNonNull(terminalPolicy, "terminalPolicy");
        Objects.requireNonNull(spillSelection, "spillSelection");
        Objects.requireNonNull(floorProfile, "floorProfile");
        basinId = V2Validation.slug(basinId, "basinId");
        waterBodyId = V2Validation.slug(waterBodyId, "waterBodyId");
        outletNodeId = V2Validation.slug(outletNodeId, "outletNodeId");
        inletNodeIds = V2Validation.sorted(inletNodeIds, "inletNodeIds", 64, Comparator.naturalOrder())
                .stream().map(id -> V2Validation.slug(id, "inletNodeId")).toList();
        basinRing = List.copyOf(Objects.requireNonNull(basinRing, "basinRing"));
        if (basinRing.size() < 4 || basinRing.size() > MAXIMUM_RING_POINTS
                || !basinRing.getFirst().equals(basinRing.getLast())) {
            throw new IllegalArgumentException("lake basin ring must be closed with at least three vertices");
        }
        if (minimumTargetDepthBlocks < 1
                || minimumTargetDepthBlocks > selectedTargetDepthBlocks
                || selectedTargetDepthBlocks > maximumTargetDepthBlocks
                || maximumTargetDepthBlocks > 64
                || shoreWidthBlocks < 1 || shoreWidthBlocks > 16
                || maximumDepthMillionths != (long) selectedTargetDepthBlocks * TerrainIntentV2.FIXED_SCALE
                || waterSurfaceYMillionths < rimMinimumYMillionths
                || waterSurfaceYMillionths - maximumDepthMillionths < (long) minY * TerrainIntentV2.FIXED_SCALE
                || waterSurfaceYMillionths > (long) maxY * TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("lake depth/surface contract is invalid");
        }
        if (terminalPolicy == TerrainIntentV2.LakeTerminalPolicy.CLOSED) {
            if (spillSelection != TerrainIntentV2.LakeSpillSelection.DECLARED_EDGE
                    || spillEdgeStartIndex != -1
                    || spillwayWidthBlocks != 0
                    || spillwayCorridorLengthBlocks != 0
                    || spillFirst != null
                    || spillSecond != null
                    || outwardUnitXMillionths != 0L
                    || outwardUnitZMillionths != 0L) {
                throw new IllegalArgumentException("closed lake must not carry spillway geometry");
            }
        } else {
            Objects.requireNonNull(spillFirst, "spillFirst");
            Objects.requireNonNull(spillSecond, "spillSecond");
            if (spillEdgeStartIndex < 0 || spillEdgeStartIndex >= basinRing.size() - 1
                    || spillwayWidthBlocks < 2 || spillwayWidthBlocks > 16
                    || spillwayCorridorLengthBlocks < 1 || spillwayCorridorLengthBlocks > 32) {
                throw new IllegalArgumentException("open lake spillway contract is invalid");
            }
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING
                || minY >= maxY || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("lake world bounds are invalid");
        }
        basinMaskFieldId = V2Validation.qualifiedId(basinMaskFieldId, "basinMaskFieldId");
        rimMaskFieldId = V2Validation.qualifiedId(rimMaskFieldId, "rimMaskFieldId");
        spillwayMaskFieldId = V2Validation.qualifiedId(spillwayMaskFieldId, "spillwayMaskFieldId");
        depthFieldId = V2Validation.qualifiedId(depthFieldId, "depthFieldId");
        floorHeightFieldId = V2Validation.qualifiedId(floorHeightFieldId, "floorHeightFieldId");
        surfaceFieldId = V2Validation.qualifiedId(surfaceFieldId, "surfaceFieldId");
        bedElevationFieldId = V2Validation.qualifiedId(bedElevationFieldId, "bedElevationFieldId");
        waterSurfaceFieldId = V2Validation.qualifiedId(waterSurfaceFieldId, "waterSurfaceFieldId");
        waterDepthFieldId = V2Validation.qualifiedId(waterDepthFieldId, "waterDepthFieldId");
        waterBodyIdFieldId = V2Validation.qualifiedId(waterBodyIdFieldId, "waterBodyIdFieldId");
        if (supportRadiusXZ < Math.max(shoreWidthBlocks, spillwayCorridorLengthBlocks)
                || supportRadiusXZ > 128) {
            throw new IllegalArgumentException("lake support radius is insufficient");
        }
        geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
    }

    public record RingPoint(long xMillionths, long zMillionths) {
        public RingPoint {
            if (xMillionths < 0 || zMillionths < 0) {
                throw new IllegalArgumentException("lake ring point is out of range");
            }
        }
    }
}
