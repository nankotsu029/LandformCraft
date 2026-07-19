package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;
import java.util.Objects;

/** Frozen V2-9-08 execution plan for an EXPERIMENTAL continental-shelf foundation profile. */
public record ContinentalShelfPlanV2(
        int planVersion,
        String featureId,
        List<BathymetryRingsV2.Ring> rings,
        int selectedShelfWidthBlocks,
        int selectedShelfDepthBlocksBelowSea,
        int selectedCoastDistanceBandBlocks,
        TerrainIntentV2.Edge seawardSide,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String depthFieldId,
        String slopeFieldId,
        String coastDistanceFieldId,
        String ownershipFieldId,
        String fluidColumnHintFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.continental-shelf";
    public static final String MODULE_VERSION = "0.1.0-v2-9-08";
    public static final String CONTRACT = "continental-shelf-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String DEPTH_FIELD_ID = "foundation.continental-shelf.depth";
    public static final String SLOPE_FIELD_ID = "foundation.continental-shelf.slope";
    public static final String COAST_DISTANCE_FIELD_ID = "foundation.continental-shelf.coast-distance";
    public static final String OWNERSHIP_FIELD_ID = "foundation.continental-shelf.ownership";
    public static final String FLUID_COLUMN_HINT_FIELD_ID = "foundation.continental-shelf.fluid-column-hint";

    public ContinentalShelfPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("continental shelf planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        rings = FoundationValidationV2.immutable(rings, "rings", 64);
        if (rings.isEmpty()) {
            throw new IllegalArgumentException("continental shelf requires at least one ring");
        }
        Objects.requireNonNull(seawardSide, "seawardSide");
        if (selectedShelfWidthBlocks < 8 || selectedShelfWidthBlocks > 128
                || selectedShelfDepthBlocksBelowSea < 2 || selectedShelfDepthBlocksBelowSea > 64
                || selectedCoastDistanceBandBlocks < 1 || selectedCoastDistanceBandBlocks > 32) {
            throw new IllegalArgumentException("continental shelf profile dimensions are invalid");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("continental shelf bounds are invalid");
        }
        long maxX = Math.addExact(
                Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        long maxZ = Math.addExact(
                Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        for (BathymetryRingsV2.Ring ring : rings) {
            if (ring.vertices().size() < 4 || !ring.vertices().getFirst().equals(ring.vertices().getLast())) {
                throw new IllegalArgumentException("continental shelf ring must be closed");
            }
            for (BathymetryRingsV2.Vertex vertex : ring.vertices()) {
                if (vertex.xMillionths() < 0 || vertex.xMillionths() > maxX
                        || vertex.zMillionths() < 0 || vertex.zMillionths() > maxZ) {
                    throw new IllegalArgumentException("continental shelf ring vertex is out of bounds");
                }
            }
        }
        depthFieldId = FoundationValidationV2.qualified(depthFieldId, "depthFieldId");
        slopeFieldId = FoundationValidationV2.qualified(slopeFieldId, "slopeFieldId");
        coastDistanceFieldId = FoundationValidationV2.qualified(coastDistanceFieldId, "coastDistanceFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        fluidColumnHintFieldId = FoundationValidationV2.qualified(
                fluidColumnHintFieldId, "fluidColumnHintFieldId");
        if (supportRadiusXZ < selectedCoastDistanceBandBlocks || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("continental shelf support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public ContinentalShelfPlanV2 withCanonicalChecksum(String checksum) {
        return new ContinentalShelfPlanV2(
                planVersion, featureId, rings, selectedShelfWidthBlocks, selectedShelfDepthBlocksBelowSea,
                selectedCoastDistanceBandBlocks, seawardSide, minY, maxY, waterLevel, width, length,
                depthFieldId, slopeFieldId, coastDistanceFieldId, ownershipFieldId, fluidColumnHintFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }
}
