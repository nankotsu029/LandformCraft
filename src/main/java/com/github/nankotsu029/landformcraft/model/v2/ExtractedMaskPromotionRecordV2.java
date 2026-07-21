package com.github.nankotsu029.landformcraft.model.v2;

/**
 * Strict provenance record for one explicit draft→numeric land-water PNG promotion. Sidecar PNG
 * lives beside the index; this record never embeds samples.
 */
public record ExtractedMaskPromotionRecordV2(
        int schemaVersion,
        String promotionVersion,
        String role,
        String sourceChecksum,
        String draftSemanticChecksum,
        String draftAlgorithmVersion,
        int confidenceThreshold,
        String unknownHandling,
        Integer noDataSample,
        int width,
        int length,
        String mapPath,
        String mapSha256,
        long mapByteLength,
        String sourceId,
        int waterCells,
        int landCells,
        int noDataCells,
        int thresholdSuppressedCells,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PROMOTION_VERSION = "extracted-mask-promote-land-water-v1";
    public static final String ROLE = "LAND_WATER_MASK";
    public static final String MAP_PATH = "land-water.png";
    public static final String SOURCE_ID = "constraint-source:extracted-mask-land-water";
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);
    public static final int MAXIMUM_DIMENSION = 4_096;
    public static final long MAXIMUM_PIXELS = 4_000_000L;
    public static final long MAXIMUM_MAP_BYTES = 8L * 1024L * 1024L;

    public ExtractedMaskPromotionRecordV2 {
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("extracted mask promotion schemaVersion must be 1");
        }
        promotionVersion = V2Validation.nonBlank(promotionVersion, "promotionVersion", 64);
        if (!PROMOTION_VERSION.equals(promotionVersion)) {
            throw new IllegalArgumentException("unsupported extracted mask promotionVersion");
        }
        role = V2Validation.nonBlank(role, "role", 64);
        if (!ROLE.equals(role)) {
            throw new IllegalArgumentException("extracted mask promotion role must be LAND_WATER_MASK");
        }
        sourceChecksum = V2Validation.checksum(sourceChecksum, "sourceChecksum");
        draftSemanticChecksum = V2Validation.checksum(draftSemanticChecksum, "draftSemanticChecksum");
        draftAlgorithmVersion = V2Validation.nonBlank(draftAlgorithmVersion, "draftAlgorithmVersion", 64);
        if (confidenceThreshold < 0 || confidenceThreshold > 255) {
            throw new IllegalArgumentException("confidenceThreshold must be within 0..255");
        }
        unknownHandling = V2Validation.nonBlank(unknownHandling, "unknownHandling", 32);
        if (!unknownHandling.equals("REJECT")
                && !unknownHandling.equals("MAP_TO_WATER")
                && !unknownHandling.equals("MAP_TO_LAND")
                && !unknownHandling.equals("MAP_TO_NODATA")) {
            throw new IllegalArgumentException("unknownHandling is not a supported promotion policy");
        }
        if (noDataSample != null) {
            if (noDataSample < 0 || noDataSample > 255 || noDataSample == 0 || noDataSample == 1) {
                throw new IllegalArgumentException("noDataSample must be a U8 value distinct from 0 and 1");
            }
            if (!"MAP_TO_NODATA".equals(unknownHandling)) {
                throw new IllegalArgumentException("noDataSample requires MAP_TO_NODATA");
            }
        } else if ("MAP_TO_NODATA".equals(unknownHandling)) {
            throw new IllegalArgumentException("MAP_TO_NODATA requires noDataSample");
        }
        if (width < 1 || width > MAXIMUM_DIMENSION || length < 1 || length > MAXIMUM_DIMENSION) {
            throw new IllegalArgumentException("promoted map dimensions must be within 1..4096");
        }
        long pixels = (long) width * length;
        if (pixels > MAXIMUM_PIXELS) {
            throw new IllegalArgumentException("promoted map pixel count exceeds the V2-1 limit");
        }
        if (!MAP_PATH.equals(mapPath)) {
            throw new IllegalArgumentException("promoted map path is fixed to land-water.png");
        }
        mapSha256 = V2Validation.checksum(mapSha256, "mapSha256");
        if (mapByteLength < 1 || mapByteLength > MAXIMUM_MAP_BYTES) {
            throw new IllegalArgumentException("promoted map byte length is outside the limit");
        }
        sourceId = V2Validation.nonBlank(sourceId, "sourceId", 96);
        if (!SOURCE_ID.equals(sourceId)) {
            throw new IllegalArgumentException("promoted sourceId is fixed");
        }
        if (waterCells < 0 || landCells < 0 || noDataCells < 0
                || thresholdSuppressedCells < 0
                || waterCells + landCells + noDataCells != pixels) {
            throw new IllegalArgumentException("promoted cell counts do not match dimensions");
        }
        if (thresholdSuppressedCells > pixels) {
            throw new IllegalArgumentException("thresholdSuppressedCells exceeds pixel count");
        }
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public ExtractedMaskPromotionRecordV2(
            int schemaVersion,
            String promotionVersion,
            String role,
            String sourceChecksum,
            String draftSemanticChecksum,
            String draftAlgorithmVersion,
            int confidenceThreshold,
            String unknownHandling,
            Integer noDataSample,
            int width,
            int length,
            String mapPath,
            String mapSha256,
            long mapByteLength,
            String sourceId,
            int waterCells,
            int landCells,
            int noDataCells,
            int thresholdSuppressedCells
    ) {
        this(schemaVersion, promotionVersion, role, sourceChecksum, draftSemanticChecksum,
                draftAlgorithmVersion, confidenceThreshold, unknownHandling, noDataSample,
                width, length, mapPath, mapSha256, mapByteLength, sourceId,
                waterCells, landCells, noDataCells, thresholdSuppressedCells,
                PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public ExtractedMaskPromotionRecordV2 withCanonicalChecksum(String checksum) {
        return new ExtractedMaskPromotionRecordV2(
                schemaVersion, promotionVersion, role, sourceChecksum, draftSemanticChecksum,
                draftAlgorithmVersion, confidenceThreshold, unknownHandling, noDataSample,
                width, length, mapPath, mapSha256, mapByteLength, sourceId,
                waterCells, landCells, noDataCells, thresholdSuppressedCells, checksum);
    }
}
