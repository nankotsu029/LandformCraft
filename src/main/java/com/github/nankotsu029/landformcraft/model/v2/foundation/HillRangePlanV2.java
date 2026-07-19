package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Comparator;
import java.util.List;

/** Frozen V2-9-02 execution plan for an EXPERIMENTAL hill-range foundation profile. */
public record HillRangePlanV2(
        int planVersion,
        String featureId,
        List<RidgePoint> ridgePoints,
        List<RidgeStation> ridgeStations,
        List<Saddle> saddles,
        int selectedRidgeHalfWidthBlocks,
        int selectedMaxReliefBlocks,
        int plainTransitionBandBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String ridgeMaskFieldId,
        String saddleMaskFieldId,
        String elevationFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.hill-range";
    public static final String MODULE_VERSION = "0.1.0-v2-9-02";
    public static final String CONTRACT = "hill-range-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String RIDGE_MASK_FIELD_ID = "foundation.hill.ridge-mask";
    public static final String SADDLE_MASK_FIELD_ID = "foundation.hill.saddle-mask";
    public static final String ELEVATION_FIELD_ID = "foundation.hill.elevation";

    public HillRangePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("hill range planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        ridgePoints = FoundationValidationV2.sorted(ridgePoints, "ridgePoints", 16_384,
                Comparator.comparingLong(RidgePoint::arcLengthMillionths));
        if (ridgePoints.size() < 2) {
            throw new IllegalArgumentException("hill range needs at least two ridge points");
        }
        ridgeStations = FoundationValidationV2.sorted(ridgeStations, "ridgeStations", 8,
                Comparator.comparingLong(RidgeStation::arcLengthMillionths)
                        .thenComparing(RidgeStation::stationId));
        saddles = FoundationValidationV2.sorted(saddles, "saddles", 7,
                Comparator.comparingLong(Saddle::arcLengthMillionths).thenComparing(Saddle::saddleId));
        if (ridgeStations.size() < 2 || ridgeStations.size() > 8
                || saddles.size() != ridgeStations.size() - 1) {
            throw new IllegalArgumentException("hill range closed ridge/saddle budget is invalid");
        }
        if (selectedRidgeHalfWidthBlocks < 1 || selectedRidgeHalfWidthBlocks > 32
                || selectedMaxReliefBlocks < 1 || selectedMaxReliefBlocks > 64) {
            throw new IllegalArgumentException("hill range profile dimensions are invalid");
        }
        if (plainTransitionBandBlocks < 1 || plainTransitionBandBlocks > 32) {
            throw new IllegalArgumentException("plainTransitionBandBlocks outside 1..32");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("hill range bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        long previousArc = -1L;
        for (RidgePoint point : ridgePoints) {
            if (point.arcLengthMillionths() <= previousArc
                    || point.xMillionths() < 0 || point.xMillionths() > maxX
                    || point.zMillionths() < 0 || point.zMillionths() > maxZ) {
                throw new IllegalArgumentException("hill range ridge is invalid");
            }
            previousArc = point.arcLengthMillionths();
        }
        ridgeMaskFieldId = FoundationValidationV2.qualified(ridgeMaskFieldId, "ridgeMaskFieldId");
        saddleMaskFieldId = FoundationValidationV2.qualified(saddleMaskFieldId, "saddleMaskFieldId");
        elevationFieldId = FoundationValidationV2.qualified(elevationFieldId, "elevationFieldId");
        if (supportRadiusXZ < selectedRidgeHalfWidthBlocks || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("hill range support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public HillRangePlanV2 withCanonicalChecksum(String checksum) {
        return new HillRangePlanV2(
                planVersion, featureId, ridgePoints, ridgeStations, saddles,
                selectedRidgeHalfWidthBlocks, selectedMaxReliefBlocks, plainTransitionBandBlocks,
                minY, maxY, waterLevel, width, length,
                ridgeMaskFieldId, saddleMaskFieldId, elevationFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }

    public record RidgePoint(long xMillionths, long zMillionths, long arcLengthMillionths) {
        public RidgePoint {
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0) {
                throw new IllegalArgumentException("hill range ridge point is invalid");
            }
        }
    }

    public record RidgeStation(
            String stationId,
            long xMillionths,
            long zMillionths,
            long arcLengthMillionths,
            int reliefBlocks
    ) {
        public RidgeStation {
            stationId = FoundationValidationV2.slug(stationId, "stationId");
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0
                    || reliefBlocks < 1 || reliefBlocks > 64) {
                throw new IllegalArgumentException("hill range ridge station is invalid");
            }
        }
    }

    public record Saddle(
            String saddleId,
            long xMillionths,
            long zMillionths,
            long arcLengthMillionths,
            int reliefBlocks
    ) {
        public Saddle {
            saddleId = FoundationValidationV2.slug(saddleId, "saddleId");
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0
                    || reliefBlocks < 1 || reliefBlocks > 64) {
                throw new IllegalArgumentException("hill range saddle is invalid");
            }
        }
    }
}
