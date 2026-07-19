package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;
import java.util.Objects;

/** Frozen V2-10-06 execution plan for an EXPERIMENTAL plateau foundation profile. */
public record PlateauPlanV2(
        int planVersion,
        String featureId,
        List<Ring> rings,
        int selectedCapElevationBlocks,
        int selectedCapReliefBlocks,
        TerrainIntentV2.PlateauProfile plateauProfile,
        int escarpmentTransitionBandBlocks,
        int minY,
        int maxY,
        int width,
        int length,
        String capMaskFieldId,
        String ownershipFieldId,
        String elevationFieldId,
        String materialHandoffFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.plateau";
    public static final String MODULE_VERSION = "0.1.0-v2-10-06";
    public static final String CONTRACT = "plateau-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 16_000_000L;
    public static final String CAP_MASK_FIELD_ID = "foundation.plateau.cap-mask";
    public static final String OWNERSHIP_FIELD_ID = "foundation.plateau.ownership";
    public static final String ELEVATION_FIELD_ID = "foundation.plateau.elevation";
    public static final String MATERIAL_HANDOFF_FIELD_ID = "foundation.plateau.material-handoff";

    public PlateauPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("plateau planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        rings = FoundationValidationV2.immutable(rings, "rings", 64);
        if (rings.isEmpty()) {
            throw new IllegalArgumentException("plateau requires at least one ring");
        }
        Objects.requireNonNull(plateauProfile, "plateauProfile");
        if (selectedCapElevationBlocks < 8 || selectedCapElevationBlocks > 96
                || selectedCapReliefBlocks < 0 || selectedCapReliefBlocks > 8
                || escarpmentTransitionBandBlocks < 2 || escarpmentTransitionBandBlocks > 32) {
            throw new IllegalArgumentException("plateau profile dimensions are invalid");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY) {
            throw new IllegalArgumentException("plateau bounds are invalid");
        }
        long maxX = Math.addExact(
                Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        long maxZ = Math.addExact(
                Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        for (Ring ring : rings) {
            if (ring.vertices().size() < 4 || !ring.vertices().getFirst().equals(ring.vertices().getLast())) {
                throw new IllegalArgumentException("plateau ring must be closed with at least three vertices");
            }
            for (Vertex vertex : ring.vertices()) {
                if (vertex.xMillionths() < 0 || vertex.xMillionths() > maxX
                        || vertex.zMillionths() < 0 || vertex.zMillionths() > maxZ) {
                    throw new IllegalArgumentException("plateau ring vertex is out of bounds");
                }
            }
        }
        capMaskFieldId = FoundationValidationV2.qualified(capMaskFieldId, "capMaskFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        elevationFieldId = FoundationValidationV2.qualified(elevationFieldId, "elevationFieldId");
        materialHandoffFieldId = FoundationValidationV2.qualified(materialHandoffFieldId, "materialHandoffFieldId");
        if (supportRadiusXZ < Math.max(selectedCapReliefBlocks, 1) || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("plateau support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public PlateauPlanV2 withCanonicalChecksum(String checksum) {
        return new PlateauPlanV2(
                planVersion, featureId, rings, selectedCapElevationBlocks, selectedCapReliefBlocks,
                plateauProfile, escarpmentTransitionBandBlocks,
                minY, maxY, width, length,
                capMaskFieldId, ownershipFieldId, elevationFieldId, materialHandoffFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }

    public record Ring(List<Vertex> vertices) {
        public Ring {
            vertices = FoundationValidationV2.immutable(vertices, "vertices", 1_024);
        }
    }

    public record Vertex(long xMillionths, long zMillionths) {
        public Vertex {
            if (xMillionths < 0 || zMillionths < 0) {
                throw new IllegalArgumentException("plateau vertex is invalid");
            }
        }
    }
}
