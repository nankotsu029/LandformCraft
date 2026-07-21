package com.github.nankotsu029.landformcraft.model.v2;

/**
 * Strict index for a published extracted land-water draft artifact bundle. Sidecar rasters live
 * beside the index; this record never embeds pixel arrays.
 */
public record ExtractedMaskDraftArtifactV2(
        int schemaVersion,
        String algorithmVersion,
        String sourceChecksum,
        String semanticChecksum,
        int width,
        int length,
        int waterCells,
        int landCells,
        int unknownCells,
        String classesPath,
        String confidencePath,
        String classesSha256,
        String confidenceSha256,
        long classesByteLength,
        long confidenceByteLength,
        String sourceRelativePath,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CLASSES_PATH = "classes.u8";
    public static final String CONFIDENCE_PATH = "confidence.u8";
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);
    public static final int MAXIMUM_DIMENSION = 4_096;
    public static final long MAXIMUM_PIXELS = 16_000_000L;
    public static final long MAXIMUM_RASTER_BYTES = MAXIMUM_PIXELS;

    public ExtractedMaskDraftArtifactV2 {
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("extracted mask draft artifact schemaVersion must be 1");
        }
        algorithmVersion = V2Validation.nonBlank(algorithmVersion, "algorithmVersion", 64);
        sourceChecksum = V2Validation.checksum(sourceChecksum, "sourceChecksum");
        semanticChecksum = V2Validation.checksum(semanticChecksum, "semanticChecksum");
        if (width < 1 || width > MAXIMUM_DIMENSION || length < 1 || length > MAXIMUM_DIMENSION) {
            throw new IllegalArgumentException("extracted mask draft dimensions must be within 1..4096");
        }
        long pixels = (long) width * length;
        if (pixels > MAXIMUM_PIXELS) {
            throw new IllegalArgumentException("extracted mask draft pixel count exceeds the limit");
        }
        if (waterCells < 0 || landCells < 0 || unknownCells < 0
                || waterCells + landCells + unknownCells != pixels) {
            throw new IllegalArgumentException("extracted mask draft cell counts do not match dimensions");
        }
        if (!CLASSES_PATH.equals(classesPath) || !CONFIDENCE_PATH.equals(confidencePath)) {
            throw new IllegalArgumentException("extracted mask draft sidecar paths are fixed");
        }
        classesSha256 = V2Validation.checksum(classesSha256, "classesSha256");
        confidenceSha256 = V2Validation.checksum(confidenceSha256, "confidenceSha256");
        if (classesByteLength != pixels || confidenceByteLength != pixels
                || classesByteLength > MAXIMUM_RASTER_BYTES
                || confidenceByteLength > MAXIMUM_RASTER_BYTES) {
            throw new IllegalArgumentException("extracted mask draft raster byte lengths are invalid");
        }
        if (sourceRelativePath != null) {
            sourceRelativePath = V2Validation.safeRelativePath(sourceRelativePath, "sourceRelativePath");
        }
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public ExtractedMaskDraftArtifactV2(
            int schemaVersion,
            String algorithmVersion,
            String sourceChecksum,
            String semanticChecksum,
            int width,
            int length,
            int waterCells,
            int landCells,
            int unknownCells,
            String classesPath,
            String confidencePath,
            String classesSha256,
            String confidenceSha256,
            long classesByteLength,
            long confidenceByteLength,
            String sourceRelativePath
    ) {
        this(schemaVersion, algorithmVersion, sourceChecksum, semanticChecksum, width, length,
                waterCells, landCells, unknownCells, classesPath, confidencePath, classesSha256,
                confidenceSha256, classesByteLength, confidenceByteLength, sourceRelativePath,
                PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public ExtractedMaskDraftArtifactV2 withCanonicalChecksum(String checksum) {
        return new ExtractedMaskDraftArtifactV2(
                schemaVersion, algorithmVersion, sourceChecksum, semanticChecksum, width, length,
                waterCells, landCells, unknownCells, classesPath, confidencePath, classesSha256,
                confidenceSha256, classesByteLength, confidenceByteLength, sourceRelativePath, checksum);
    }
}
