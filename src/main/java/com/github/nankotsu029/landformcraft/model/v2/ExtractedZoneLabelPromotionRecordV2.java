package com.github.nankotsu029.landformcraft.model.v2;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict provenance record for one explicit zone-label draft→numeric PNG promotion.
 */
public record ExtractedZoneLabelPromotionRecordV2(
        int schemaVersion,
        String promotionVersion,
        String role,
        String sampleSpaceDeclaration,
        String sourceChecksum,
        String draftSemanticChecksum,
        String draftAlgorithmVersion,
        int confidenceThreshold,
        int noDataSample,
        int width,
        int length,
        String mapPath,
        String mapSha256,
        long mapByteLength,
        String sourceId,
        List<ZoneLabelProposalV2> proposedLabels,
        int labeledCells,
        int noDataCells,
        int thresholdSuppressedCells,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PROMOTION_VERSION = "extracted-zone-label-promote-v1";
    public static final String ROLE = "ZONE_LABEL_MAP";
    public static final String MAP_PATH = "zone-labels.png";
    public static final String SOURCE_ID = "constraint-source:extracted-zone-label";
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);
    public static final int MAXIMUM_DIMENSION = 4_096;
    public static final long MAXIMUM_PIXELS = 4_000_000L;
    public static final long MAXIMUM_MAP_BYTES = 8L * 1024L * 1024L;
    public static final int MAXIMUM_LABELS = 64;

    public ExtractedZoneLabelPromotionRecordV2 {
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("extracted zone label promotion schemaVersion must be 1");
        }
        promotionVersion = V2Validation.nonBlank(promotionVersion, "promotionVersion", 64);
        if (!PROMOTION_VERSION.equals(promotionVersion)) {
            throw new IllegalArgumentException("unsupported extracted zone label promotionVersion");
        }
        role = V2Validation.nonBlank(role, "role", 64);
        if (!ROLE.equals(role)) {
            throw new IllegalArgumentException("extracted zone label promotion role must be ZONE_LABEL_MAP");
        }
        sampleSpaceDeclaration = V2Validation.nonBlank(
                sampleSpaceDeclaration, "sampleSpaceDeclaration", 128);
        sourceChecksum = V2Validation.checksum(sourceChecksum, "sourceChecksum");
        draftSemanticChecksum = V2Validation.checksum(draftSemanticChecksum, "draftSemanticChecksum");
        draftAlgorithmVersion = V2Validation.nonBlank(draftAlgorithmVersion, "draftAlgorithmVersion", 64);
        if (confidenceThreshold < 0 || confidenceThreshold > 255) {
            throw new IllegalArgumentException("confidenceThreshold must be within 0..255");
        }
        if (noDataSample < 0 || noDataSample > 255) {
            throw new IllegalArgumentException("noDataSample must be within 0..255");
        }
        if (width < 1 || width > MAXIMUM_DIMENSION || length < 1 || length > MAXIMUM_DIMENSION) {
            throw new IllegalArgumentException("promoted zone map dimensions must be within 1..4096");
        }
        long pixels = (long) width * length;
        if (pixels > MAXIMUM_PIXELS) {
            throw new IllegalArgumentException("promoted zone map pixel count exceeds the V2-1 limit");
        }
        if (!MAP_PATH.equals(mapPath)) {
            throw new IllegalArgumentException("promoted zone map path is fixed to zone-labels.png");
        }
        mapSha256 = V2Validation.checksum(mapSha256, "mapSha256");
        if (mapByteLength < 1 || mapByteLength > MAXIMUM_MAP_BYTES) {
            throw new IllegalArgumentException("promoted zone map byte length is outside the limit");
        }
        sourceId = V2Validation.nonBlank(sourceId, "sourceId", 96);
        if (!SOURCE_ID.equals(sourceId)) {
            throw new IllegalArgumentException("promoted zone sourceId is fixed");
        }
        proposedLabels = V2Validation.immutable(proposedLabels, "proposedLabels", MAXIMUM_LABELS);
        if (proposedLabels.isEmpty()) {
            throw new IllegalArgumentException("proposedLabels must not be empty");
        }
        Set<Integer> samples = new HashSet<>();
        for (ZoneLabelProposalV2 proposal : proposedLabels) {
            if (proposal.sample() == noDataSample) {
                throw new IllegalArgumentException("proposed label sample collides with noDataSample");
            }
            if (!samples.add(proposal.sample())) {
                throw new IllegalArgumentException("proposedLabels must have unique samples");
            }
        }
        if (labeledCells < 0 || noDataCells < 0 || thresholdSuppressedCells < 0
                || labeledCells + noDataCells != pixels) {
            throw new IllegalArgumentException("promoted zone cell counts do not match dimensions");
        }
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public ExtractedZoneLabelPromotionRecordV2(
            int schemaVersion,
            String promotionVersion,
            String role,
            String sampleSpaceDeclaration,
            String sourceChecksum,
            String draftSemanticChecksum,
            String draftAlgorithmVersion,
            int confidenceThreshold,
            int noDataSample,
            int width,
            int length,
            String mapPath,
            String mapSha256,
            long mapByteLength,
            String sourceId,
            List<ZoneLabelProposalV2> proposedLabels,
            int labeledCells,
            int noDataCells,
            int thresholdSuppressedCells
    ) {
        this(schemaVersion, promotionVersion, role, sampleSpaceDeclaration, sourceChecksum,
                draftSemanticChecksum, draftAlgorithmVersion, confidenceThreshold, noDataSample,
                width, length, mapPath, mapSha256, mapByteLength, sourceId, proposedLabels,
                labeledCells, noDataCells, thresholdSuppressedCells, PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public ExtractedZoneLabelPromotionRecordV2 withCanonicalChecksum(String checksum) {
        return new ExtractedZoneLabelPromotionRecordV2(
                schemaVersion, promotionVersion, role, sampleSpaceDeclaration, sourceChecksum,
                draftSemanticChecksum, draftAlgorithmVersion, confidenceThreshold, noDataSample,
                width, length, mapPath, mapSha256, mapByteLength, sourceId, proposedLabels,
                labeledCells, noDataCells, thresholdSuppressedCells, checksum);
    }
}
