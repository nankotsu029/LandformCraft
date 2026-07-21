package com.github.nankotsu029.landformcraft.model.v2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Frozen V2-3-10 execution plan for an EXPERIMENTAL 2.5D mountain ridge skeleton. */
public record MountainPlanV2(
        int planVersion,
        String featureId,
        TerrainIntentV2.MountainVariant variant,
        List<RidgePoint> ridge,
        List<NamedStation> peaks,
        List<NamedStation> saddles,
        List<SpurSegment> spurs,
        int selectedPeakCount,
        int selectedRidgeHalfWidthBlocks,
        int selectedMaxReliefBlocks,
        int spurCount,
        long ridgeSharpnessMillionths,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String ridgeMaskFieldId,
        String peakMaskFieldId,
        String saddleMaskFieldId,
        String spurMaskFieldId,
        String provisionalSurfaceFieldId,
        String ridgeSegmentIdFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum
) {
    public static final int VERSION = 1;
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;

    public MountainPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("mountain planVersion must be 1");
        }
        featureId = V2Validation.slug(featureId, "featureId");
        Objects.requireNonNull(variant, "variant");
        ridge = V2Validation.sorted(ridge, "ridge", 16_384,
                Comparator.comparingLong(RidgePoint::arcLengthMillionths));
        if (ridge.size() < 2) {
            throw new IllegalArgumentException("mountain needs a ridge");
        }
        peaks = V2Validation.sorted(peaks, "peaks", 64,
                Comparator.comparingLong(NamedStation::arcLengthMillionths).thenComparing(NamedStation::stationId));
        saddles = V2Validation.sorted(saddles, "saddles", 64,
                Comparator.comparingLong(NamedStation::arcLengthMillionths).thenComparing(NamedStation::stationId));
        spurs = V2Validation.sorted(spurs, "spurs", 64, Comparator.comparing(SpurSegment::spurId));
        if (selectedPeakCount < 2 || selectedPeakCount > 16
                || peaks.size() != selectedPeakCount
                || saddles.size() != Math.max(0, selectedPeakCount - 1)
                || spurCount < 0 || spurCount > 8
                || spurs.size() != spurCount) {
            throw new IllegalArgumentException("mountain peak/saddle/spur counts are invalid");
        }
        if (selectedRidgeHalfWidthBlocks < 4 || selectedRidgeHalfWidthBlocks > 64
                || selectedMaxReliefBlocks < 16 || selectedMaxReliefBlocks > 256
                || ridgeSharpnessMillionths < 100_000L || ridgeSharpnessMillionths > TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("mountain profile dimensions are invalid");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("mountain bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        long previous = -1L;
        for (RidgePoint point : ridge) {
            if (point.arcLengthMillionths() <= previous
                    || point.xMillionths() < 0 || point.xMillionths() > maxX
                    || point.zMillionths() < 0 || point.zMillionths() > maxZ) {
                throw new IllegalArgumentException("mountain ridge is invalid");
            }
            previous = point.arcLengthMillionths();
        }
        ridgeMaskFieldId = V2Validation.qualifiedId(ridgeMaskFieldId, "ridgeMaskFieldId");
        peakMaskFieldId = V2Validation.qualifiedId(peakMaskFieldId, "peakMaskFieldId");
        saddleMaskFieldId = V2Validation.qualifiedId(saddleMaskFieldId, "saddleMaskFieldId");
        spurMaskFieldId = V2Validation.qualifiedId(spurMaskFieldId, "spurMaskFieldId");
        provisionalSurfaceFieldId = V2Validation.qualifiedId(provisionalSurfaceFieldId, "provisionalSurfaceFieldId");
        ridgeSegmentIdFieldId = V2Validation.qualifiedId(ridgeSegmentIdFieldId, "ridgeSegmentIdFieldId");
        if (supportRadiusXZ < selectedRidgeHalfWidthBlocks || supportRadiusXZ > 256
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("mountain support or work budget is invalid");
        }
        geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
    }

    public record RidgePoint(long xMillionths, long zMillionths, long arcLengthMillionths, int segmentId) {
        public RidgePoint {
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0 || segmentId < 1) {
                throw new IllegalArgumentException("mountain ridge point is invalid");
            }
        }
    }

    public record NamedStation(String stationId, long xMillionths, long zMillionths, long arcLengthMillionths, int reliefBlocks) {
        public NamedStation {
            stationId = V2Validation.slug(stationId, "stationId");
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0
                    || reliefBlocks < 0 || reliefBlocks > 256) {
                throw new IllegalArgumentException("mountain station is invalid");
            }
        }
    }

    public record SpurSegment(
            String spurId,
            long originXMillionths,
            long originZMillionths,
            long tipXMillionths,
            long tipZMillionths,
            long arcLengthMillionths
    ) {
        public SpurSegment {
            spurId = V2Validation.slug(spurId, "spurId");
            if (originXMillionths < 0 || originZMillionths < 0 || tipXMillionths < 0 || tipZMillionths < 0
                    || arcLengthMillionths < TerrainIntentV2.FIXED_SCALE) {
                throw new IllegalArgumentException("mountain spur is invalid");
            }
        }
    }
}
