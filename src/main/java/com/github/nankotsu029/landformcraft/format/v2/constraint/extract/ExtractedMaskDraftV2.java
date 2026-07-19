package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import java.util.Objects;

/**
 * Immutable row-major land-water draft extracted from a sanitized image. A draft is a SOFT
 * proposal with per-cell confidence; it never becomes a HARD constraint implicitly. Promotion
 * requires an explicit user action that re-enters the strict numeric constraint-map path.
 */
public final class ExtractedMaskDraftV2 {
    public static final int CLASS_WATER = 0;
    public static final int CLASS_LAND = 1;
    public static final int CLASS_UNKNOWN = 2;

    private final int width;
    private final int length;
    private final String algorithmVersion;
    private final String sourceChecksum;
    private final String semanticChecksum;
    private final byte[] classes;
    private final byte[] confidence;
    private final int waterCells;
    private final int landCells;
    private final int unknownCells;

    ExtractedMaskDraftV2(
            int width,
            int length,
            String algorithmVersion,
            String sourceChecksum,
            String semanticChecksum,
            byte[] classes,
            byte[] confidence,
            int waterCells,
            int landCells,
            int unknownCells
    ) {
        this.width = width;
        this.length = length;
        this.algorithmVersion = Objects.requireNonNull(algorithmVersion, "algorithmVersion");
        this.sourceChecksum = Objects.requireNonNull(sourceChecksum, "sourceChecksum");
        this.semanticChecksum = Objects.requireNonNull(semanticChecksum, "semanticChecksum");
        this.classes = Objects.requireNonNull(classes, "classes");
        this.confidence = Objects.requireNonNull(confidence, "confidence");
        this.waterCells = waterCells;
        this.landCells = landCells;
        this.unknownCells = unknownCells;
        int expected = Math.multiplyExact(width, length);
        if (classes.length != expected || confidence.length != expected
                || waterCells + landCells + unknownCells != expected) {
            throw new IllegalArgumentException("extracted mask storage does not match dimensions");
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

    /** SHA-256 over the tagged header and both rasters; equal drafts have equal checksums. */
    public String semanticChecksum() {
        return semanticChecksum;
    }

    public int waterCells() {
        return waterCells;
    }

    public int landCells() {
        return landCells;
    }

    public int unknownCells() {
        return unknownCells;
    }

    public int classAt(int x, int z) {
        return classes[index(x, z)];
    }

    /** Confidence 0..255; UNKNOWN cells always report 0. */
    public int confidenceAt(int x, int z) {
        return Byte.toUnsignedInt(confidence[index(x, z)]);
    }

    private int index(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("mask coordinate outside bounds");
        }
        return z * width + x;
    }
}
