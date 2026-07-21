package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable row-major zone-label draft extracted from a sanitized sketch image. Palette indices
 * reference the frozen extract palette; {@link #UNKNOWN_INDEX} marks ambiguous / transparent cells.
 * Drafts are SOFT proposals — promotion must declare categorical encoding explicitly.
 */
public final class ExtractedZoneLabelDraftV2 {
    public static final int UNKNOWN_INDEX = 255;
    public static final String SAMPLE_SPACE_DECLARATION =
            "fixed-palette-distance-quantize-requires-explicit-categorical-encoding-v1";

    private final int width;
    private final int length;
    private final String algorithmVersion;
    private final String sourceChecksum;
    private final String semanticChecksum;
    private final String sampleSpaceDeclaration;
    private final List<ZonePaletteEntryV2> proposedLabels;
    private final byte[] labelIndices;
    private final byte[] confidence;
    private final int labeledCells;
    private final int unknownCells;

    ExtractedZoneLabelDraftV2(
            int width,
            int length,
            String algorithmVersion,
            String sourceChecksum,
            String semanticChecksum,
            String sampleSpaceDeclaration,
            List<ZonePaletteEntryV2> proposedLabels,
            byte[] labelIndices,
            byte[] confidence,
            int labeledCells,
            int unknownCells
    ) {
        this.width = width;
        this.length = length;
        this.algorithmVersion = Objects.requireNonNull(algorithmVersion, "algorithmVersion");
        this.sourceChecksum = Objects.requireNonNull(sourceChecksum, "sourceChecksum");
        this.semanticChecksum = Objects.requireNonNull(semanticChecksum, "semanticChecksum");
        this.sampleSpaceDeclaration = Objects.requireNonNull(
                sampleSpaceDeclaration, "sampleSpaceDeclaration");
        this.proposedLabels = List.copyOf(Objects.requireNonNull(proposedLabels, "proposedLabels"));
        this.labelIndices = Objects.requireNonNull(labelIndices, "labelIndices");
        this.confidence = Objects.requireNonNull(confidence, "confidence");
        this.labeledCells = labeledCells;
        this.unknownCells = unknownCells;
        int expected = Math.multiplyExact(width, length);
        if (labelIndices.length != expected || confidence.length != expected
                || labeledCells + unknownCells != expected) {
            throw new IllegalArgumentException("zone label draft storage does not match dimensions");
        }
        if (proposedLabels.isEmpty() || proposedLabels.size() > 64) {
            throw new IllegalArgumentException("proposedLabels size is outside 1..64");
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

    public List<ZonePaletteEntryV2> proposedLabels() {
        return proposedLabels;
    }

    public int labeledCells() {
        return labeledCells;
    }

    public int unknownCells() {
        return unknownCells;
    }

    public boolean isUnknown(int x, int z) {
        return labelIndexAt(x, z) == UNKNOWN_INDEX;
    }

    /** Palette index in {@code 0..proposedLabels.size()-1}, or {@link #UNKNOWN_INDEX}. */
    public int labelIndexAt(int x, int z) {
        return Byte.toUnsignedInt(labelIndices[index(x, z)]);
    }

    public int confidenceAt(int x, int z) {
        return Byte.toUnsignedInt(confidence[index(x, z)]);
    }

    /** Source sample for a labeled cell; throws if the cell is UNKNOWN. */
    public int sampleAt(int x, int z) {
        int labelIndex = labelIndexAt(x, z);
        if (labelIndex == UNKNOWN_INDEX) {
            throw new IllegalStateException("UNKNOWN cells do not expose a categorical sample");
        }
        return proposedLabels.get(labelIndex).sample();
    }

    byte[] copyLabelIndices() {
        return labelIndices.clone();
    }

    byte[] copyConfidence() {
        return confidence.clone();
    }

    static ExtractedZoneLabelDraftV2 restore(
            int width,
            int length,
            String algorithmVersion,
            String sourceChecksum,
            String semanticChecksum,
            String sampleSpaceDeclaration,
            List<ZonePaletteEntryV2> proposedLabels,
            byte[] labelIndices,
            byte[] confidence,
            int labeledCells,
            int unknownCells
    ) {
        return new ExtractedZoneLabelDraftV2(
                width, length, algorithmVersion, sourceChecksum, semanticChecksum,
                sampleSpaceDeclaration, proposedLabels, labelIndices.clone(), confidence.clone(),
                labeledCells, unknownCells);
    }

    private int index(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("zone label coordinate outside bounds");
        }
        return z * width + x;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExtractedZoneLabelDraftV2 that)) {
            return false;
        }
        return width == that.width
                && length == that.length
                && labeledCells == that.labeledCells
                && unknownCells == that.unknownCells
                && algorithmVersion.equals(that.algorithmVersion)
                && sourceChecksum.equals(that.sourceChecksum)
                && semanticChecksum.equals(that.semanticChecksum)
                && sampleSpaceDeclaration.equals(that.sampleSpaceDeclaration)
                && proposedLabels.equals(that.proposedLabels)
                && Arrays.equals(labelIndices, that.labelIndices)
                && Arrays.equals(confidence, that.confidence);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                width, length, algorithmVersion, sourceChecksum, semanticChecksum,
                sampleSpaceDeclaration, proposedLabels, labeledCells, unknownCells);
        result = 31 * result + Arrays.hashCode(labelIndices);
        result = 31 * result + Arrays.hashCode(confidence);
        return result;
    }
}
