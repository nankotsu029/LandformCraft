package com.github.nankotsu029.landformcraft.model.v2;

/**
 * Strict index for a published extracted height-guide draft artifact bundle. Sidecar rasters live
 * beside the index; this record never embeds sample arrays.
 */
public record ExtractedHeightGuideDraftArtifactV2(
        int schemaVersion,
        String algorithmVersion,
        String sampleSpaceDeclaration,
        String sourceChecksum,
        String semanticChecksum,
        int width,
        int length,
        int validCells,
        int noDataCells,
        String samplesPath,
        String confidencePath,
        String samplesSha256,
        String confidenceSha256,
        long samplesByteLength,
        long confidenceByteLength,
        String sourceRelativePath,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String SAMPLES_PATH = "samples.u8";
    public static final String CONFIDENCE_PATH = "confidence.u8";
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);
    public static final int MAXIMUM_DIMENSION = 4_096;
    public static final long MAXIMUM_PIXELS = 16_000_000L;
    public static final long MAXIMUM_RASTER_BYTES = MAXIMUM_PIXELS;

    public ExtractedHeightGuideDraftArtifactV2 {
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("extracted height guide draft schemaVersion must be 1");
        }
        algorithmVersion = V2Validation.nonBlank(algorithmVersion, "algorithmVersion", 64);
        sampleSpaceDeclaration = V2Validation.nonBlank(
                sampleSpaceDeclaration, "sampleSpaceDeclaration", 128);
        sourceChecksum = V2Validation.checksum(sourceChecksum, "sourceChecksum");
        semanticChecksum = V2Validation.checksum(semanticChecksum, "semanticChecksum");
        if (width < 1 || width > MAXIMUM_DIMENSION || length < 1 || length > MAXIMUM_DIMENSION) {
            throw new IllegalArgumentException("extracted height guide dimensions must be within 1..4096");
        }
        long pixels = (long) width * length;
        if (pixels > MAXIMUM_PIXELS) {
            throw new IllegalArgumentException("extracted height guide pixel count exceeds the limit");
        }
        if (validCells < 0 || noDataCells < 0 || validCells + noDataCells != pixels) {
            throw new IllegalArgumentException("extracted height guide cell counts do not match dimensions");
        }
        if (!SAMPLES_PATH.equals(samplesPath) || !CONFIDENCE_PATH.equals(confidencePath)) {
            throw new IllegalArgumentException("extracted height guide sidecar paths are fixed");
        }
        samplesSha256 = V2Validation.checksum(samplesSha256, "samplesSha256");
        confidenceSha256 = V2Validation.checksum(confidenceSha256, "confidenceSha256");
        if (samplesByteLength != pixels || confidenceByteLength != pixels
                || samplesByteLength > MAXIMUM_RASTER_BYTES
                || confidenceByteLength > MAXIMUM_RASTER_BYTES) {
            throw new IllegalArgumentException("extracted height guide raster byte lengths are invalid");
        }
        if (sourceRelativePath != null) {
            sourceRelativePath = V2Validation.safeRelativePath(sourceRelativePath, "sourceRelativePath");
        }
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public ExtractedHeightGuideDraftArtifactV2(
            int schemaVersion,
            String algorithmVersion,
            String sampleSpaceDeclaration,
            String sourceChecksum,
            String semanticChecksum,
            int width,
            int length,
            int validCells,
            int noDataCells,
            String samplesPath,
            String confidencePath,
            String samplesSha256,
            String confidenceSha256,
            long samplesByteLength,
            long confidenceByteLength,
            String sourceRelativePath
    ) {
        this(schemaVersion, algorithmVersion, sampleSpaceDeclaration, sourceChecksum, semanticChecksum,
                width, length, validCells, noDataCells, samplesPath, confidencePath,
                samplesSha256, confidenceSha256, samplesByteLength, confidenceByteLength,
                sourceRelativePath, PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public ExtractedHeightGuideDraftArtifactV2 withCanonicalChecksum(String checksum) {
        return new ExtractedHeightGuideDraftArtifactV2(
                schemaVersion, algorithmVersion, sampleSpaceDeclaration, sourceChecksum, semanticChecksum,
                width, length, validCells, noDataCells, samplesPath, confidencePath,
                samplesSha256, confidenceSha256, samplesByteLength, confidenceByteLength,
                sourceRelativePath, checksum);
    }
}
