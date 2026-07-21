package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.List;

/** Frozen V2-9-03 execution plan for an EXPERIMENTAL mountain-range foundation profile. */
public record MountainRangePlanV2(
        int planVersion,
        String featureId,
        List<RidgePoint> ridgePoints,
        List<Peak> peaks,
        List<Saddle> saddles,
        List<Spur> spurs,
        List<Pass> passes,
        List<Foothill> foothills,
        int selectedRidgeHalfWidthBlocks,
        int selectedMaxReliefBlocks,
        int foothillBandBlocks,
        int valleyTransitionBandBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String ridgeMaskFieldId,
        String peakMaskFieldId,
        String saddleMaskFieldId,
        String spurMaskFieldId,
        String passMaskFieldId,
        String elevationFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.mountain-range";
    public static final String MODULE_VERSION = "0.1.0-v2-9-03";
    public static final String CONTRACT = "mountain-range-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String RIDGE_MASK_FIELD_ID = "foundation.mountain.ridge-mask";
    public static final String PEAK_MASK_FIELD_ID = "foundation.mountain.peak-mask";
    public static final String SADDLE_MASK_FIELD_ID = "foundation.mountain.saddle-mask";
    public static final String SPUR_MASK_FIELD_ID = "foundation.mountain.spur-mask";
    public static final String PASS_MASK_FIELD_ID = "foundation.mountain.pass-mask";
    public static final String ELEVATION_FIELD_ID = "foundation.mountain.elevation";

    public MountainRangePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("mountain range planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        ridgePoints = FoundationValidationV2.sorted(ridgePoints, "ridgePoints", 16_384,
                Comparator.comparingLong(RidgePoint::arcLengthMillionths));
        if (ridgePoints.size() < 2) {
            throw new IllegalArgumentException("mountain range needs at least two ridge points");
        }
        peaks = FoundationValidationV2.sorted(peaks, "peaks", 8,
                Comparator.comparingLong(Peak::arcLengthMillionths).thenComparing(Peak::peakId));
        saddles = FoundationValidationV2.sorted(saddles, "saddles", 7,
                Comparator.comparingLong(Saddle::arcLengthMillionths).thenComparing(Saddle::saddleId));
        spurs = FoundationValidationV2.sorted(spurs, "spurs", 8,
                Comparator.comparing(Spur::spurId));
        passes = FoundationValidationV2.sorted(passes, "passes", 7,
                Comparator.comparingLong(Pass::arcLengthMillionths).thenComparing(Pass::passId));
        foothills = FoundationValidationV2.sorted(foothills, "foothills", 8,
                Comparator.comparing(Foothill::foothillId));
        if (peaks.size() < 2 || peaks.size() > 8
                || saddles.size() != peaks.size() - 1
                || passes.size() > peaks.size() - 1) {
            throw new IllegalArgumentException("mountain range peak/saddle/pass budget is invalid");
        }
        if (selectedRidgeHalfWidthBlocks < 4 || selectedRidgeHalfWidthBlocks > 64
                || selectedMaxReliefBlocks < 16 || selectedMaxReliefBlocks > 128) {
            throw new IllegalArgumentException("mountain range profile dimensions are invalid");
        }
        if (foothillBandBlocks < 1 || foothillBandBlocks > 32
                || valleyTransitionBandBlocks < 1 || valleyTransitionBandBlocks > 32) {
            throw new IllegalArgumentException("mountain range transition bands outside 1..32");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("mountain range bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        long previousArc = -1L;
        for (RidgePoint point : ridgePoints) {
            if (point.arcLengthMillionths() <= previousArc
                    || point.xMillionths() < 0 || point.xMillionths() > maxX
                    || point.zMillionths() < 0 || point.zMillionths() > maxZ) {
                throw new IllegalArgumentException("mountain range ridge is invalid");
            }
            previousArc = point.arcLengthMillionths();
        }
        ridgeMaskFieldId = FoundationValidationV2.qualified(ridgeMaskFieldId, "ridgeMaskFieldId");
        peakMaskFieldId = FoundationValidationV2.qualified(peakMaskFieldId, "peakMaskFieldId");
        saddleMaskFieldId = FoundationValidationV2.qualified(saddleMaskFieldId, "saddleMaskFieldId");
        spurMaskFieldId = FoundationValidationV2.qualified(spurMaskFieldId, "spurMaskFieldId");
        passMaskFieldId = FoundationValidationV2.qualified(passMaskFieldId, "passMaskFieldId");
        elevationFieldId = FoundationValidationV2.qualified(elevationFieldId, "elevationFieldId");
        if (supportRadiusXZ < selectedRidgeHalfWidthBlocks || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("mountain range support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public MountainRangePlanV2 withCanonicalChecksum(String checksum) {
        return new MountainRangePlanV2(
                planVersion, featureId, ridgePoints, peaks, saddles, spurs, passes, foothills,
                selectedRidgeHalfWidthBlocks, selectedMaxReliefBlocks, foothillBandBlocks,
                valleyTransitionBandBlocks, minY, maxY, waterLevel, width, length,
                ridgeMaskFieldId, peakMaskFieldId, saddleMaskFieldId, spurMaskFieldId, passMaskFieldId,
                elevationFieldId, supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }

    public record RidgePoint(long xMillionths, long zMillionths, long arcLengthMillionths) {
        public RidgePoint {
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0) {
                throw new IllegalArgumentException("mountain range ridge point is invalid");
            }
        }
    }

    public record Peak(String peakId, long xMillionths, long zMillionths, long arcLengthMillionths, int reliefBlocks) {
        public Peak {
            peakId = FoundationValidationV2.slug(peakId, "peakId");
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0
                    || reliefBlocks < 1 || reliefBlocks > 128) {
                throw new IllegalArgumentException("mountain range peak is invalid");
            }
        }
    }

    public record Saddle(String saddleId, long xMillionths, long zMillionths, long arcLengthMillionths, int reliefBlocks) {
        public Saddle {
            saddleId = FoundationValidationV2.slug(saddleId, "saddleId");
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0
                    || reliefBlocks < 1 || reliefBlocks > 128) {
                throw new IllegalArgumentException("mountain range saddle is invalid");
            }
        }
    }

    public record Spur(String spurId, long xMillionths, long zMillionths, long arcLengthMillionths, int lengthBlocks) {
        public Spur {
            spurId = FoundationValidationV2.slug(spurId, "spurId");
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0
                    || lengthBlocks < 1 || lengthBlocks > 64) {
                throw new IllegalArgumentException("mountain range spur is invalid");
            }
        }
    }

    public record Pass(String passId, long xMillionths, long zMillionths, long arcLengthMillionths, int reliefBlocks) {
        public Pass {
            passId = FoundationValidationV2.slug(passId, "passId");
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0
                    || reliefBlocks < 1 || reliefBlocks > 128) {
                throw new IllegalArgumentException("mountain range pass is invalid");
            }
        }
    }

    public record Foothill(String foothillId, long xMillionths, long zMillionths, int bandBlocks) {
        public Foothill {
            foothillId = FoundationValidationV2.slug(foothillId, "foothillId");
            if (xMillionths < 0 || zMillionths < 0 || bandBlocks < 1 || bandBlocks > 32) {
                throw new IllegalArgumentException("mountain range foothill is invalid");
            }
        }
    }
}
