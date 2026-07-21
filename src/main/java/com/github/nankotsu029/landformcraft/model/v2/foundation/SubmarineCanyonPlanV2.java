package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Frozen V2-9-09 execution plan for an EXPERIMENTAL submarine-canyon foundation carve. */
public record SubmarineCanyonPlanV2(
        int planVersion,
        String featureId,
        String shelfFeatureId,
        String slopeFeatureId,
        String basinFeatureId,
        String headRelationId,
        String crossingRelationId,
        String outletRelationId,
        TerrainIntentV2.CanyonCrossSection crossSection,
        int terraceCount,
        int terraceWidthBlocks,
        List<CenterlineSample> centerline,
        long totalArcLengthMillionths,
        int selectedFloorWidthBlocks,
        int selectedRimWidthBlocks,
        int selectedAdditionalCarveDepthBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String maskFieldId,
        String floorDepthFieldId,
        String ownershipFieldId,
        String hostHandoffFieldId,
        String fluidColumnHintFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String shelfGeometryChecksum,
        String slopeGeometryChecksum,
        String basinGeometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.submarine-canyon";
    public static final String MODULE_VERSION = "0.1.0-v2-9-09";
    public static final String CONTRACT = "submarine-canyon-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final int MAXIMUM_CENTERLINE_SAMPLES = 8_192;
    public static final String MASK_FIELD_ID = "foundation.submarine-canyon.mask";
    public static final String FLOOR_DEPTH_FIELD_ID = "foundation.submarine-canyon.floor-depth";
    public static final String OWNERSHIP_FIELD_ID = "foundation.submarine-canyon.ownership";
    public static final String HOST_HANDOFF_FIELD_ID = "foundation.submarine-canyon.host-handoff";
    public static final String FLUID_COLUMN_HINT_FIELD_ID = "foundation.submarine-canyon.fluid-column-hint";

    public enum HostRole {
        HEAD_SHELF(1),
        SLOPE_CROSSING(2),
        OUTLET_BASIN(3);

        private final int code;

        HostRole(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static HostRole fromCode(int code) {
            for (HostRole role : values()) {
                if (role.code == code) {
                    return role;
                }
            }
            throw new IllegalArgumentException("unknown submarine canyon host role code: " + code);
        }
    }

    public SubmarineCanyonPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("submarine canyon planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        shelfFeatureId = FoundationValidationV2.slug(shelfFeatureId, "shelfFeatureId");
        slopeFeatureId = FoundationValidationV2.slug(slopeFeatureId, "slopeFeatureId");
        basinFeatureId = FoundationValidationV2.slug(basinFeatureId, "basinFeatureId");
        headRelationId = FoundationValidationV2.slug(headRelationId, "headRelationId");
        crossingRelationId = FoundationValidationV2.slug(crossingRelationId, "crossingRelationId");
        outletRelationId = FoundationValidationV2.slug(outletRelationId, "outletRelationId");
        Objects.requireNonNull(crossSection, "crossSection");
        boolean terraced = crossSection == TerrainIntentV2.CanyonCrossSection.TERRACED_V
                || crossSection == TerrainIntentV2.CanyonCrossSection.TERRACED_U;
        if (terraced) {
            if (terraceCount < 1 || terraceCount > 4 || terraceWidthBlocks < 1 || terraceWidthBlocks > 32) {
                throw new IllegalArgumentException("terraced submarine canyon terrace contract is invalid");
            }
        } else if (terraceCount != 0 || terraceWidthBlocks != 0) {
            throw new IllegalArgumentException("non-terraced submarine canyon must not declare terraces");
        }
        centerline = FoundationValidationV2.sorted(
                centerline, "centerline", MAXIMUM_CENTERLINE_SAMPLES,
                Comparator.comparingInt(CenterlineSample::sequence));
        if (centerline.size() < 2) {
            throw new IllegalArgumentException("submarine canyon centerline requires at least two samples");
        }
        if (centerline.getFirst().arcLengthMillionths() != 0L
                || centerline.getLast().arcLengthMillionths() != totalArcLengthMillionths
                || totalArcLengthMillionths < TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("submarine canyon arc-length contract is invalid");
        }
        for (int index = 1; index < centerline.size(); index++) {
            CenterlineSample previous = centerline.get(index - 1);
            CenterlineSample current = centerline.get(index);
            if (current.arcLengthMillionths() < previous.arcLengthMillionths()) {
                throw new IllegalArgumentException("submarine canyon arc length must be non-decreasing");
            }
            if (current.floorDepthBlocksBelowSea() < previous.floorDepthBlocksBelowSea()) {
                throw new IllegalArgumentException("submarine canyon floor depth must be non-decreasing seaward");
            }
        }
        if (selectedFloorWidthBlocks < 2 || selectedFloorWidthBlocks > 64
                || selectedRimWidthBlocks < selectedFloorWidthBlocks + 2
                || selectedRimWidthBlocks > 256
                || selectedAdditionalCarveDepthBlocks < 1
                || selectedAdditionalCarveDepthBlocks > 64) {
            throw new IllegalArgumentException("submarine canyon profile dimensions are invalid");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("submarine canyon bounds are invalid");
        }
        long maxX = Math.addExact(
                Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        long maxZ = Math.addExact(
                Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        for (CenterlineSample sample : centerline) {
            if (sample.xMillionths() < 0 || sample.xMillionths() > maxX
                    || sample.zMillionths() < 0 || sample.zMillionths() > maxZ) {
                throw new IllegalArgumentException("submarine canyon centerline sample is out of bounds");
            }
        }
        maskFieldId = FoundationValidationV2.qualified(maskFieldId, "maskFieldId");
        floorDepthFieldId = FoundationValidationV2.qualified(floorDepthFieldId, "floorDepthFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        hostHandoffFieldId = FoundationValidationV2.qualified(hostHandoffFieldId, "hostHandoffFieldId");
        fluidColumnHintFieldId = FoundationValidationV2.qualified(
                fluidColumnHintFieldId, "fluidColumnHintFieldId");
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("submarine canyon support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        shelfGeometryChecksum = FoundationValidationV2.checksum(shelfGeometryChecksum, "shelfGeometryChecksum");
        slopeGeometryChecksum = FoundationValidationV2.checksum(slopeGeometryChecksum, "slopeGeometryChecksum");
        basinGeometryChecksum = FoundationValidationV2.checksum(basinGeometryChecksum, "basinGeometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public SubmarineCanyonPlanV2 withCanonicalChecksum(String checksum) {
        return new SubmarineCanyonPlanV2(
                planVersion, featureId, shelfFeatureId, slopeFeatureId, basinFeatureId,
                headRelationId, crossingRelationId, outletRelationId, crossSection,
                terraceCount, terraceWidthBlocks, centerline, totalArcLengthMillionths,
                selectedFloorWidthBlocks, selectedRimWidthBlocks, selectedAdditionalCarveDepthBlocks,
                minY, maxY, waterLevel, width, length,
                maskFieldId, floorDepthFieldId, ownershipFieldId, hostHandoffFieldId, fluidColumnHintFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum,
                shelfGeometryChecksum, slopeGeometryChecksum, basinGeometryChecksum, checksum);
    }

    public record CenterlineSample(
            int sequence,
            long xMillionths,
            long zMillionths,
            long arcLengthMillionths,
            HostRole hostRole,
            int floorDepthBlocksBelowSea
    ) {
        public CenterlineSample {
            Objects.requireNonNull(hostRole, "hostRole");
            if (sequence < 0 || floorDepthBlocksBelowSea < 1 || floorDepthBlocksBelowSea > 512
                    || arcLengthMillionths < 0L) {
                throw new IllegalArgumentException("submarine canyon centerline sample is invalid");
            }
        }
    }
}
