package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import java.util.Objects;

/**
 * Immutable row-major height-guide draft extracted from a sanitized image. Samples are unsigned
 * luminance-derived guide values in {@code 0..254}; confidence {@code 0} marks no-data. A draft is
 * a SOFT proposal: it never becomes a HARD height constraint implicitly, and it never invents a
 * {@link com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2.HeightValueMeaning}.
 * Promotion must declare one of the three V2-1 meanings explicitly.
 */
public final class ExtractedHeightGuideDraftV2 {
    /** Maximum stored sample so {@link #NO_DATA_SENTINEL_SAMPLE} remains available for promotion PNGs. */
    public static final int MAXIMUM_VALID_SAMPLE = 254;
    public static final int NO_DATA_SENTINEL_SAMPLE = 255;

    /**
     * Frozen declaration: samples are dimensionless luminance guides. Callers must bind them to
     * exactly one of {@code ABSOLUTE_BLOCK_Y}, {@code BLOCKS_ABOVE_REQUEST_MIN_Y}, or
     * {@code BLOCKS_RELATIVE_TO_WATER_LEVEL} at promotion time.
     */
    public static final String SAMPLE_SPACE_DECLARATION = "luminance-u8-requires-explicit-height-value-meaning-v1";

    private final int width;
    private final int length;
    private final String algorithmVersion;
    private final String sourceChecksum;
    private final String semanticChecksum;
    private final String sampleSpaceDeclaration;
    private final byte[] samples;
    private final byte[] confidence;
    private final int validCells;
    private final int noDataCells;

    ExtractedHeightGuideDraftV2(
            int width,
            int length,
            String algorithmVersion,
            String sourceChecksum,
            String semanticChecksum,
            String sampleSpaceDeclaration,
            byte[] samples,
            byte[] confidence,
            int validCells,
            int noDataCells
    ) {
        this.width = width;
        this.length = length;
        this.algorithmVersion = Objects.requireNonNull(algorithmVersion, "algorithmVersion");
        this.sourceChecksum = Objects.requireNonNull(sourceChecksum, "sourceChecksum");
        this.semanticChecksum = Objects.requireNonNull(semanticChecksum, "semanticChecksum");
        this.sampleSpaceDeclaration = Objects.requireNonNull(
                sampleSpaceDeclaration, "sampleSpaceDeclaration");
        this.samples = Objects.requireNonNull(samples, "samples");
        this.confidence = Objects.requireNonNull(confidence, "confidence");
        this.validCells = validCells;
        this.noDataCells = noDataCells;
        int expected = Math.multiplyExact(width, length);
        if (samples.length != expected || confidence.length != expected
                || validCells + noDataCells != expected) {
            throw new IllegalArgumentException("height guide draft storage does not match dimensions");
        }
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public String algorithmVersion() {
        return algorithmVersion;
    }

    public String sourceChecksum() {
        return sourceChecksum;
    }

    public String semanticChecksum() {
        return semanticChecksum;
    }

    public String sampleSpaceDeclaration() {
        return sampleSpaceDeclaration;
    }

    public int validCells() {
        return validCells;
    }

    public int noDataCells() {
        return noDataCells;
    }

    /** Returns {@code true} when the cell is no-data (confidence 0). */
    public boolean isNoData(int x, int z) {
        return confidenceAt(x, z) == 0;
    }

    /**
     * Guide sample in {@code 0..254}. No-data cells still expose the stored byte but callers must
     * check {@link #isNoData(int, int)}.
     */
    public int sampleAt(int x, int z) {
        return Byte.toUnsignedInt(samples[index(x, z)]);
    }

    /** Confidence 0..255; no-data cells always report 0. */
    public int confidenceAt(int x, int z) {
        return Byte.toUnsignedInt(confidence[index(x, z)]);
    }

    byte[] copySamples() {
        return samples.clone();
    }

    byte[] copyConfidence() {
        return confidence.clone();
    }

    static ExtractedHeightGuideDraftV2 restore(
            int width,
            int length,
            String algorithmVersion,
            String sourceChecksum,
            String semanticChecksum,
            String sampleSpaceDeclaration,
            byte[] samples,
            byte[] confidence,
            int validCells,
            int noDataCells
    ) {
        return new ExtractedHeightGuideDraftV2(
                width, length, algorithmVersion, sourceChecksum, semanticChecksum,
                sampleSpaceDeclaration, samples.clone(), confidence.clone(), validCells, noDataCells);
    }

    private int index(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("height guide coordinate outside bounds");
        }
        return z * width + x;
    }
}
