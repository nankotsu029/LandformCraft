package com.github.nankotsu029.landformcraft.model.v2;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict index for a published extracted zone-label draft artifact bundle.
 */
public record ExtractedZoneLabelDraftArtifactV2(
        int schemaVersion,
        String algorithmVersion,
        String sampleSpaceDeclaration,
        String sourceChecksum,
        String semanticChecksum,
        int width,
        int length,
        int labeledCells,
        int unknownCells,
        String labelsPath,
        String confidencePath,
        String labelsSha256,
        String confidenceSha256,
        long labelsByteLength,
        long confidenceByteLength,
        List<ZoneLabelProposalV2> proposedLabels,
        String sourceRelativePath,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String LABELS_PATH = "labels.u8";
    public static final String CONFIDENCE_PATH = "confidence.u8";
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);
    public static final int MAXIMUM_DIMENSION = 4_096;
    public static final long MAXIMUM_PIXELS = 16_000_000L;
    public static final int MAXIMUM_LABELS = 64;

    public ExtractedZoneLabelDraftArtifactV2 {
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("extracted zone label draft schemaVersion must be 1");
        }
        algorithmVersion = V2Validation.nonBlank(algorithmVersion, "algorithmVersion", 64);
        sampleSpaceDeclaration = V2Validation.nonBlank(
                sampleSpaceDeclaration, "sampleSpaceDeclaration", 128);
        sourceChecksum = V2Validation.checksum(sourceChecksum, "sourceChecksum");
        semanticChecksum = V2Validation.checksum(semanticChecksum, "semanticChecksum");
        if (width < 1 || width > MAXIMUM_DIMENSION || length < 1 || length > MAXIMUM_DIMENSION) {
            throw new IllegalArgumentException("extracted zone label dimensions must be within 1..4096");
        }
        long pixels = (long) width * length;
        if (pixels > MAXIMUM_PIXELS) {
            throw new IllegalArgumentException("extracted zone label pixel count exceeds the limit");
        }
        if (labeledCells < 0 || unknownCells < 0 || labeledCells + unknownCells != pixels) {
            throw new IllegalArgumentException("extracted zone label cell counts do not match dimensions");
        }
        if (!LABELS_PATH.equals(labelsPath) || !CONFIDENCE_PATH.equals(confidencePath)) {
            throw new IllegalArgumentException("extracted zone label sidecar paths are fixed");
        }
        labelsSha256 = V2Validation.checksum(labelsSha256, "labelsSha256");
        confidenceSha256 = V2Validation.checksum(confidenceSha256, "confidenceSha256");
        if (labelsByteLength != pixels || confidenceByteLength != pixels) {
            throw new IllegalArgumentException("extracted zone label raster byte lengths are invalid");
        }
        proposedLabels = V2Validation.immutable(proposedLabels, "proposedLabels", MAXIMUM_LABELS);
        if (proposedLabels.isEmpty()) {
            throw new IllegalArgumentException("proposedLabels must not be empty");
        }
        Set<Integer> samples = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (ZoneLabelProposalV2 proposal : proposedLabels) {
            if (!samples.add(proposal.sample()) || !names.add(proposal.label())) {
                throw new IllegalArgumentException("proposedLabels must have unique samples and labels");
            }
        }
        if (sourceRelativePath != null) {
            sourceRelativePath = V2Validation.safeRelativePath(sourceRelativePath, "sourceRelativePath");
        }
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public ExtractedZoneLabelDraftArtifactV2(
            int schemaVersion,
            String algorithmVersion,
            String sampleSpaceDeclaration,
            String sourceChecksum,
            String semanticChecksum,
            int width,
            int length,
            int labeledCells,
            int unknownCells,
            String labelsPath,
            String confidencePath,
            String labelsSha256,
            String confidenceSha256,
            long labelsByteLength,
            long confidenceByteLength,
            List<ZoneLabelProposalV2> proposedLabels,
            String sourceRelativePath
    ) {
        this(schemaVersion, algorithmVersion, sampleSpaceDeclaration, sourceChecksum, semanticChecksum,
                width, length, labeledCells, unknownCells, labelsPath, confidencePath,
                labelsSha256, confidenceSha256, labelsByteLength, confidenceByteLength,
                proposedLabels, sourceRelativePath, PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public ExtractedZoneLabelDraftArtifactV2 withCanonicalChecksum(String checksum) {
        return new ExtractedZoneLabelDraftArtifactV2(
                schemaVersion, algorithmVersion, sampleSpaceDeclaration, sourceChecksum, semanticChecksum,
                width, length, labeledCells, unknownCells, labelsPath, confidencePath,
                labelsSha256, confidenceSha256, labelsByteLength, confidenceByteLength,
                proposedLabels, sourceRelativePath, checksum);
    }
}
