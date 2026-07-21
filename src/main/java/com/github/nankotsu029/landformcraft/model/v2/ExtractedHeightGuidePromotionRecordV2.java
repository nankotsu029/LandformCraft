package com.github.nankotsu029.landformcraft.model.v2;

/**
 * Strict provenance record for one explicit height-guide draft→numeric PNG promotion.
 */
public record ExtractedHeightGuidePromotionRecordV2(
        int schemaVersion,
        String promotionVersion,
        String role,
        String sampleSpaceDeclaration,
        String sourceChecksum,
        String draftSemanticChecksum,
        String draftAlgorithmVersion,
        int confidenceThreshold,
        String valueMeaning,
        long valueScaleMillionths,
        long valueOffsetMillionths,
        int validSampleMinimum,
        int validSampleMaximum,
        int noDataSample,
        int width,
        int length,
        String mapPath,
        String mapSha256,
        long mapByteLength,
        String sourceId,
        int validCells,
        int noDataCells,
        int thresholdSuppressedCells,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PROMOTION_VERSION = "extracted-height-guide-promote-v1";
    public static final String ROLE = "HEIGHT_GUIDE";
    public static final String MAP_PATH = "height-guide.png";
    public static final String SOURCE_ID = "constraint-source:extracted-height-guide";
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);
    public static final int MAXIMUM_DIMENSION = 4_096;
    public static final long MAXIMUM_PIXELS = 4_000_000L;
    public static final long MAXIMUM_MAP_BYTES = 8L * 1024L * 1024L;

    public ExtractedHeightGuidePromotionRecordV2 {
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("extracted height guide promotion schemaVersion must be 1");
        }
        promotionVersion = V2Validation.nonBlank(promotionVersion, "promotionVersion", 64);
        if (!PROMOTION_VERSION.equals(promotionVersion)) {
            throw new IllegalArgumentException("unsupported extracted height guide promotionVersion");
        }
        role = V2Validation.nonBlank(role, "role", 64);
        if (!ROLE.equals(role)) {
            throw new IllegalArgumentException("extracted height guide promotion role must be HEIGHT_GUIDE");
        }
        sampleSpaceDeclaration = V2Validation.nonBlank(
                sampleSpaceDeclaration, "sampleSpaceDeclaration", 128);
        sourceChecksum = V2Validation.checksum(sourceChecksum, "sourceChecksum");
        draftSemanticChecksum = V2Validation.checksum(draftSemanticChecksum, "draftSemanticChecksum");
        draftAlgorithmVersion = V2Validation.nonBlank(draftAlgorithmVersion, "draftAlgorithmVersion", 64);
        if (confidenceThreshold < 0 || confidenceThreshold > 255) {
            throw new IllegalArgumentException("confidenceThreshold must be within 0..255");
        }
        valueMeaning = V2Validation.nonBlank(valueMeaning, "valueMeaning", 64);
        if (!valueMeaning.equals("ABSOLUTE_BLOCK_Y")
                && !valueMeaning.equals("BLOCKS_ABOVE_REQUEST_MIN_Y")
                && !valueMeaning.equals("BLOCKS_RELATIVE_TO_WATER_LEVEL")) {
            throw new IllegalArgumentException("valueMeaning must be one of the three V2-1 height meanings");
        }
        if (valueScaleMillionths == 0L) {
            throw new IllegalArgumentException("valueScaleMillionths must not be zero");
        }
        if (validSampleMinimum < 0 || validSampleMaximum > 254
                || validSampleMinimum > validSampleMaximum) {
            throw new IllegalArgumentException("validSampleRange must lie within 0..254");
        }
        if (noDataSample != 255) {
            throw new IllegalArgumentException("noDataSample must be 255 for U8 height promotion");
        }
        if (width < 1 || width > MAXIMUM_DIMENSION || length < 1 || length > MAXIMUM_DIMENSION) {
            throw new IllegalArgumentException("promoted height map dimensions must be within 1..4096");
        }
        long pixels = (long) width * length;
        if (pixels > MAXIMUM_PIXELS) {
            throw new IllegalArgumentException("promoted height map pixel count exceeds the V2-1 limit");
        }
        if (!MAP_PATH.equals(mapPath)) {
            throw new IllegalArgumentException("promoted height map path is fixed to height-guide.png");
        }
        mapSha256 = V2Validation.checksum(mapSha256, "mapSha256");
        if (mapByteLength < 1 || mapByteLength > MAXIMUM_MAP_BYTES) {
            throw new IllegalArgumentException("promoted height map byte length is outside the limit");
        }
        sourceId = V2Validation.nonBlank(sourceId, "sourceId", 96);
        if (!SOURCE_ID.equals(sourceId)) {
            throw new IllegalArgumentException("promoted height sourceId is fixed");
        }
        if (validCells < 0 || noDataCells < 0 || thresholdSuppressedCells < 0
                || validCells + noDataCells != pixels) {
            throw new IllegalArgumentException("promoted height cell counts do not match dimensions");
        }
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public ExtractedHeightGuidePromotionRecordV2(
            int schemaVersion,
            String promotionVersion,
            String role,
            String sampleSpaceDeclaration,
            String sourceChecksum,
            String draftSemanticChecksum,
            String draftAlgorithmVersion,
            int confidenceThreshold,
            String valueMeaning,
            long valueScaleMillionths,
            long valueOffsetMillionths,
            int validSampleMinimum,
            int validSampleMaximum,
            int noDataSample,
            int width,
            int length,
            String mapPath,
            String mapSha256,
            long mapByteLength,
            String sourceId,
            int validCells,
            int noDataCells,
            int thresholdSuppressedCells
    ) {
        this(schemaVersion, promotionVersion, role, sampleSpaceDeclaration, sourceChecksum,
                draftSemanticChecksum, draftAlgorithmVersion, confidenceThreshold, valueMeaning,
                valueScaleMillionths, valueOffsetMillionths, validSampleMinimum, validSampleMaximum,
                noDataSample, width, length, mapPath, mapSha256, mapByteLength, sourceId,
                validCells, noDataCells, thresholdSuppressedCells, PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public ExtractedHeightGuidePromotionRecordV2 withCanonicalChecksum(String checksum) {
        return new ExtractedHeightGuidePromotionRecordV2(
                schemaVersion, promotionVersion, role, sampleSpaceDeclaration, sourceChecksum,
                draftSemanticChecksum, draftAlgorithmVersion, confidenceThreshold, valueMeaning,
                valueScaleMillionths, valueOffsetMillionths, validSampleMinimum, validSampleMaximum,
                noDataSample, width, length, mapPath, mapSha256, mapByteLength, sourceId,
                validCells, noDataCells, thresholdSuppressedCells, checksum);
    }
}
