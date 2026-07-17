package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical V2-3-07 execution plan for one DELTA distributary DAG and 2.5D fan.
 * Tidal bidirectionality, wetland ecology, and semantic sediment material are out of scope.
 */
public record DeltaPlanV2(
        int planVersion,
        String featureId,
        String trunkRiverFeatureId,
        String trunkReachId,
        String drainsToRelationId,
        String emptiesIntoRelationId,
        TerrainIntentV2.Edge receivingSeaBoundary,
        TerrainIntentV2.DeltaFanProfile fanProfile,
        String apexNodeId,
        FanPoint apex,
        List<FanPoint> fanRing,
        int minimumDistributaryCount,
        int maximumDistributaryCount,
        int selectedDistributaryCount,
        long minimumFanOpeningDegreesMillionths,
        long maximumFanOpeningDegreesMillionths,
        long selectedFanOpeningDegreesMillionths,
        int minimumFanReliefBlocks,
        int maximumFanReliefBlocks,
        int selectedFanReliefBlocks,
        int minimumSandbarCount,
        int maximumSandbarCount,
        int selectedSandbarCount,
        int minimumShallowSeaDepthBlocks,
        int maximumShallowSeaDepthBlocks,
        int selectedShallowSeaDepthBlocks,
        int shallowSeaBandBlocks,
        List<DistributaryBranch> branches,
        List<Sandbar> sandbars,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String fanMaskFieldId,
        String channelMaskFieldId,
        String branchIndexFieldId,
        String fanSurfaceFieldId,
        String sandbarMaskFieldId,
        String shallowSeaDepthFieldId,
        String dischargeShareFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String riverGeometryChecksum
) {
    public static final int VERSION = 1;
    public static final int MAXIMUM_RING_POINTS = 1_024;
    public static final int MAXIMUM_BRANCHES = 16;
    public static final int MAXIMUM_SANDBARS = 32;
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;

    public DeltaPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("delta planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        trunkRiverFeatureId = V2Validation.slug(trunkRiverFeatureId, "trunkRiverFeatureId");
        trunkReachId = V2Validation.slug(trunkReachId, "trunkReachId");
        drainsToRelationId = V2Validation.slug(drainsToRelationId, "drainsToRelationId");
        emptiesIntoRelationId = V2Validation.slug(emptiesIntoRelationId, "emptiesIntoRelationId");
        Objects.requireNonNull(receivingSeaBoundary, "receivingSeaBoundary");
        Objects.requireNonNull(fanProfile, "fanProfile");
        apexNodeId = V2Validation.slug(apexNodeId, "apexNodeId");
        Objects.requireNonNull(apex, "apex");
        fanRing = List.copyOf(Objects.requireNonNull(fanRing, "fanRing"));
        if (fanRing.size() < 4 || fanRing.size() > MAXIMUM_RING_POINTS
                || !fanRing.getFirst().equals(fanRing.getLast())) {
            throw new IllegalArgumentException("delta fan ring must be closed with at least three vertices");
        }
        requireSelectedRange(minimumDistributaryCount, selectedDistributaryCount,
                maximumDistributaryCount, 2, MAXIMUM_BRANCHES, "distributary count");
        if (minimumFanOpeningDegreesMillionths < 10L * TerrainIntentV2.FIXED_SCALE
                || minimumFanOpeningDegreesMillionths > selectedFanOpeningDegreesMillionths
                || selectedFanOpeningDegreesMillionths > maximumFanOpeningDegreesMillionths
                || maximumFanOpeningDegreesMillionths > 170L * TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("delta fan opening contract is invalid");
        }
        requireSelectedRange(minimumFanReliefBlocks, selectedFanReliefBlocks,
                maximumFanReliefBlocks, 1, 16, "fan relief");
        requireSelectedRange(minimumSandbarCount, selectedSandbarCount,
                maximumSandbarCount, 0, MAXIMUM_SANDBARS, "sandbar count");
        requireSelectedRange(minimumShallowSeaDepthBlocks, selectedShallowSeaDepthBlocks,
                maximumShallowSeaDepthBlocks, 1, 16, "shallow sea depth");
        if (shallowSeaBandBlocks < 2 || shallowSeaBandBlocks > 64) {
            throw new IllegalArgumentException("delta shallow sea band outside 2..64");
        }
        branches = V2Validation.sorted(
                branches, "branches", MAXIMUM_BRANCHES, Comparator.comparing(DistributaryBranch::branchId));
        sandbars = V2Validation.sorted(
                sandbars, "sandbars", MAXIMUM_SANDBARS, Comparator.comparing(Sandbar::sandbarId));
        if (branches.size() != selectedDistributaryCount || sandbars.size() != selectedSandbarCount) {
            throw new IllegalArgumentException("delta selected counts do not match frozen descriptors");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000
                || minY >= maxY || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("delta world bounds are invalid");
        }
        long maximumX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maximumZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        for (FanPoint point : fanRing) requireInBounds(point, maximumX, maximumZ, "fan ring");
        requireInBounds(apex, maximumX, maximumZ, "apex");
        for (DistributaryBranch branch : branches) {
            for (FanPoint point : branch.path()) requireInBounds(point, maximumX, maximumZ, "branch path");
        }
        for (Sandbar sandbar : sandbars) {
            requireInBounds(sandbar.center(), maximumX, maximumZ, "sandbar");
        }
        validateGraph(apexNodeId, apex, receivingSeaBoundary, branches, width, length);
        fanMaskFieldId = V2Validation.qualifiedId(fanMaskFieldId, "fanMaskFieldId");
        channelMaskFieldId = V2Validation.qualifiedId(channelMaskFieldId, "channelMaskFieldId");
        branchIndexFieldId = V2Validation.qualifiedId(branchIndexFieldId, "branchIndexFieldId");
        fanSurfaceFieldId = V2Validation.qualifiedId(fanSurfaceFieldId, "fanSurfaceFieldId");
        sandbarMaskFieldId = V2Validation.qualifiedId(sandbarMaskFieldId, "sandbarMaskFieldId");
        shallowSeaDepthFieldId = V2Validation.qualifiedId(shallowSeaDepthFieldId, "shallowSeaDepthFieldId");
        dischargeShareFieldId = V2Validation.qualifiedId(dischargeShareFieldId, "dischargeShareFieldId");
        int maximumBranchHalfWidth = branches.stream()
                .mapToInt(DistributaryBranch::halfWidthBlocks).max().orElseThrow();
        int maximumSandbarRadius = sandbars.stream().mapToInt(Sandbar::radiusBlocks).max().orElse(0);
        if (supportRadiusXZ < Math.max(shallowSeaBandBlocks,
                Math.max(maximumBranchHalfWidth, maximumSandbarRadius)) || supportRadiusXZ > 128) {
            throw new IllegalArgumentException("delta support radius is insufficient");
        }
        if (estimatedRasterWorkUnits < 1L || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("delta raster work budget is invalid");
        }
        geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
        riverGeometryChecksum = V2Validation.checksum(riverGeometryChecksum, "riverGeometryChecksum");
    }

    private static void validateGraph(
            String apexNodeId,
            FanPoint apex,
            TerrainIntentV2.Edge boundary,
            List<DistributaryBranch> branches,
            int width,
            int length
    ) {
        Set<String> branchIds = new HashSet<>();
        Set<String> mouthNodes = new HashSet<>();
        Set<FanPoint> mouths = new HashSet<>();
        long totalDischarge = 0L;
        for (DistributaryBranch branch : branches) {
            if (!branchIds.add(branch.branchId())) throw new IllegalArgumentException("duplicate delta branch id");
            if (!branch.fromNodeId().equals(apexNodeId) || branch.toNodeId().equals(apexNodeId)) {
                throw new IllegalArgumentException("delta distributary graph contains a loop or dead branch");
            }
            if (!mouthNodes.add(branch.toNodeId()) || !mouths.add(branch.path().getLast())) {
                throw new IllegalArgumentException("delta distributary mouths must be unique");
            }
            if (!branch.path().getFirst().equals(apex)
                    || !onBoundary(branch.path().getLast(), boundary, width, length)) {
                throw new IllegalArgumentException("delta distributary is not marine-reachable");
            }
            totalDischarge = Math.addExact(totalDischarge, branch.dischargeShareMillionths());
        }
        if (totalDischarge != TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("delta distributary flow is not conserved");
        }
    }

    private static boolean onBoundary(
            FanPoint point,
            TerrainIntentV2.Edge boundary,
            int width,
            int length
    ) {
        long maximumX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maximumZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        return switch (boundary) {
            case NORTH -> point.zMillionths() == 0L;
            case EAST -> point.xMillionths() == maximumX;
            case SOUTH -> point.zMillionths() == maximumZ;
            case WEST -> point.xMillionths() == 0L;
        };
    }

    private static void requireSelectedRange(
            int minimum,
            int selected,
            int maximum,
            int allowedMinimum,
            int allowedMaximum,
            String name
    ) {
        if (minimum < allowedMinimum || minimum > selected || selected > maximum || maximum > allowedMaximum) {
            throw new IllegalArgumentException("delta " + name + " contract is invalid");
        }
    }

    private static void requireInBounds(FanPoint point, long maximumX, long maximumZ, String name) {
        if (point.xMillionths() < 0L || point.xMillionths() > maximumX
                || point.zMillionths() < 0L || point.zMillionths() > maximumZ) {
            throw new IllegalArgumentException("delta " + name + " is outside bounds");
        }
    }

    public record FanPoint(long xMillionths, long zMillionths) {
        public FanPoint {
            if (xMillionths < 0L || zMillionths < 0L) {
                throw new IllegalArgumentException("delta point is out of range");
            }
        }
    }

    public record DistributaryBranch(
            String branchId,
            String fromNodeId,
            String toNodeId,
            List<FanPoint> path,
            long apexBedYMillionths,
            long mouthBedYMillionths,
            int halfWidthBlocks,
            int dischargeShareMillionths
    ) {
        public DistributaryBranch {
            branchId = V2Validation.slug(branchId, "branchId");
            fromNodeId = V2Validation.slug(fromNodeId, "fromNodeId");
            toNodeId = V2Validation.slug(toNodeId, "toNodeId");
            path = V2Validation.immutable(path, "branch path", 16);
            if (path.size() < 2 || halfWidthBlocks < 1 || halfWidthBlocks > 32
                    || dischargeShareMillionths < 1 || dischargeShareMillionths > TerrainIntentV2.FIXED_SCALE
                    || mouthBedYMillionths > apexBedYMillionths) {
                throw new IllegalArgumentException("delta distributary branch is invalid");
            }
        }
    }

    public record Sandbar(String sandbarId, FanPoint center, int radiusBlocks) {
        public Sandbar {
            sandbarId = V2Validation.slug(sandbarId, "sandbarId");
            Objects.requireNonNull(center, "center");
            if (radiusBlocks < 1 || radiusBlocks > 8) {
                throw new IllegalArgumentException("delta sandbar radius outside 1..8");
            }
        }
    }
}
