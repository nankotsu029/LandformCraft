package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Frozen V2-10-02 EXPERIMENTAL moraine-field deposition plan.
 * Independent sediment-ridge ownership; binds a glacial-ice parent by checksum.
 */
public record MoraineFieldPlanV2(
        int planVersion,
        String featureId,
        String glacialParentFeatureId,
        String glacialParentCanonicalChecksum,
        String glacialParentRelationId,
        int selectedRidgeCount,
        int selectedRidgeHalfWidthBlocks,
        int selectedSedimentThicknessBlocks,
        int flowAzimuthDegrees,
        List<Ring> rings,
        int width,
        int length,
        int minY,
        int maxY,
        String sedimentOwnershipFieldId,
        String ridgeMaskFieldId,
        String provenanceFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.moraine-field";
    public static final String MODULE_VERSION = "0.1.0-v2-10-02";
    public static final String CONTRACT = "moraine-field-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 16_000_000L;
    public static final String SEDIMENT_OWNERSHIP_FIELD_ID = "foundation.moraine-field.sediment-ownership";
    public static final String RIDGE_MASK_FIELD_ID = "foundation.moraine-field.ridge-mask";
    public static final String PROVENANCE_FIELD_ID = "foundation.moraine-field.provenance";

    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");

    public MoraineFieldPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("moraine field planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        glacialParentFeatureId = FoundationValidationV2.slug(
                glacialParentFeatureId, "glacialParentFeatureId");
        glacialParentCanonicalChecksum = requireChecksum(
                glacialParentCanonicalChecksum, "glacialParentCanonicalChecksum");
        glacialParentRelationId = FoundationValidationV2.slug(
                glacialParentRelationId, "glacialParentRelationId");
        if (selectedRidgeCount < 1 || selectedRidgeCount > 16
                || selectedRidgeHalfWidthBlocks < 2 || selectedRidgeHalfWidthBlocks > 32
                || selectedSedimentThicknessBlocks < 1 || selectedSedimentThicknessBlocks > 16) {
            throw new IllegalArgumentException("moraine field selected dimensions are invalid");
        }
        if (flowAzimuthDegrees < 0 || flowAzimuthDegrees > 359) {
            throw new IllegalArgumentException("flowAzimuthDegrees must be in 0..359");
        }
        rings = FoundationValidationV2.immutable(rings, "rings", 64);
        if (rings.isEmpty()) {
            throw new IllegalArgumentException("moraine field requires at least one ring");
        }
        validateBounds(width, length, minY, maxY, rings);
        sedimentOwnershipFieldId = FoundationValidationV2.qualified(
                sedimentOwnershipFieldId, "sedimentOwnershipFieldId");
        ridgeMaskFieldId = FoundationValidationV2.qualified(ridgeMaskFieldId, "ridgeMaskFieldId");
        provenanceFieldId = FoundationValidationV2.qualified(provenanceFieldId, "provenanceFieldId");
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1L || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("moraine field support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public MoraineFieldPlanV2 withCanonicalChecksum(String checksum) {
        return new MoraineFieldPlanV2(
                planVersion, featureId, glacialParentFeatureId, glacialParentCanonicalChecksum,
                glacialParentRelationId, selectedRidgeCount, selectedRidgeHalfWidthBlocks,
                selectedSedimentThicknessBlocks, flowAzimuthDegrees, rings, width, length, minY, maxY,
                sedimentOwnershipFieldId, ridgeMaskFieldId, provenanceFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }

    public record Ring(List<Vertex> vertices) {
        public Ring {
            vertices = FoundationValidationV2.immutable(vertices, "vertices", 256);
        }
    }

    public record Vertex(long xMillionths, long zMillionths) {
    }

    private static void validateBounds(int width, int length, int minY, int maxY, List<Ring> rings) {
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY) {
            throw new IllegalArgumentException("moraine field bounds are invalid");
        }
        long maxX = Math.addExact(
                Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        long maxZ = Math.addExact(
                Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        for (Ring ring : rings) {
            if (ring.vertices().size() < 4 || !ring.vertices().getFirst().equals(ring.vertices().getLast())) {
                throw new IllegalArgumentException("moraine field ring must be closed");
            }
            for (Vertex vertex : ring.vertices()) {
                Objects.requireNonNull(vertex, "vertex");
                if (vertex.xMillionths() < 0 || vertex.xMillionths() > maxX
                        || vertex.zMillionths() < 0 || vertex.zMillionths() > maxZ) {
                    throw new IllegalArgumentException("moraine field ring vertex is out of bounds");
                }
            }
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
