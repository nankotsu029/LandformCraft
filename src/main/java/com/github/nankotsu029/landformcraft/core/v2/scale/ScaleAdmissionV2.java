package com.github.nankotsu029.landformcraft.core.v2.scale;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;

import java.util.Objects;

/**
 * Pre-allocation admission for one generation area against a scale profile. Estimates use
 * frozen conservative constants; they gate planning only and never replace the measured
 * per-subsystem budget tests that a scale class needs before becoming supported.
 */
public final class ScaleAdmissionV2 {
    public static final String ADMISSION_VERSION = "scale-admission-v1";

    /** Conservative planning estimate: concurrently retained full-resolution field layers. */
    static final long ESTIMATED_ACTIVE_FIELD_LAYERS = 6L;
    /** Conservative planning estimate: bytes per cell of one field layer. */
    static final long ESTIMATED_BYTES_PER_FIELD_CELL = 4L;
    /** Conservative planning estimate: retained scheduling state per tile. */
    static final long ESTIMATED_TILE_STATE_BYTES = 1_024L;

    private ScaleAdmissionV2() {
    }

    public static ScaleAdmissionDecisionV2 admit(int widthBlocks, int lengthBlocks, ScaleProfileV2 profile) {
        Objects.requireNonNull(profile, "profile");
        if (widthBlocks < 1 || lengthBlocks < 1
                || widthBlocks > ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS
                || lengthBlocks > ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS) {
            throw failure(ScaleAdmissionFailureCodeV2.DIMENSIONS_OUT_OF_RANGE,
                    "generation area must be between 1 and "
                            + ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS + " blocks per side");
        }
        ScaleClassV2 requiredClass = ScaleClassV2.forDimensions(widthBlocks, lengthBlocks);
        if (requiredClass.maximumHorizontalBlocks() > profile.scaleClass().maximumHorizontalBlocks()) {
            throw failure(ScaleAdmissionFailureCodeV2.SCALE_CLASS_EXCEEDED,
                    "area requires scale class " + requiredClass.id()
                            + " but the profile covers " + profile.scaleClass().id());
        }
        TilePlanV2 tilePlan = TilePlanV2.of(widthBlocks, lengthBlocks, profile);
        if (tilePlan.tileCount() > profile.maximumTileCount()) {
            throw failure(ScaleAdmissionFailureCodeV2.TILE_BUDGET_EXCEEDED,
                    "tile plan needs " + tilePlan.tileCount() + " tiles but the profile allows "
                            + profile.maximumTileCount());
        }
        long workingBytesPerTile = estimateWorkingBytesPerTile(profile);
        if (workingBytesPerTile > profile.maximumWorkingBytes()) {
            throw failure(ScaleAdmissionFailureCodeV2.WORKING_BUDGET_EXCEEDED,
                    "estimated per-tile working set " + workingBytesPerTile
                            + " bytes exceeds the profile working budget");
        }
        long retainedBytes = estimateRetainedBytes(widthBlocks, lengthBlocks, profile, tilePlan);
        if (retainedBytes > profile.maximumRetainedBytes()) {
            throw failure(ScaleAdmissionFailureCodeV2.RETAINED_BUDGET_EXCEEDED,
                    "estimated retained coarse plan " + retainedBytes
                            + " bytes exceeds the profile retained budget");
        }
        long artifactBytes = estimateArtifactBytes(widthBlocks, lengthBlocks);
        if (artifactBytes > profile.maximumArtifactBytes()) {
            throw failure(ScaleAdmissionFailureCodeV2.ARTIFACT_BUDGET_EXCEEDED,
                    "estimated artifact volume " + artifactBytes
                            + " bytes exceeds the profile artifact budget");
        }
        return new ScaleAdmissionDecisionV2(
                ADMISSION_VERSION,
                requiredClass,
                tilePlan,
                profile.scaleClass().requiresStreamingExecution(),
                workingBytesPerTile,
                retainedBytes,
                artifactBytes);
    }

    private static long estimateWorkingBytesPerTile(ScaleProfileV2 profile) {
        long window = (long) profile.tileSizeBlocks() + 2L * profile.haloBlocks();
        return Math.multiplyExact(
                Math.multiplyExact(window, window),
                ESTIMATED_ACTIVE_FIELD_LAYERS * ESTIMATED_BYTES_PER_FIELD_CELL);
    }

    private static long estimateRetainedBytes(
            int widthBlocks, int lengthBlocks, ScaleProfileV2 profile, TilePlanV2 tilePlan) {
        long coarseCellsX = ceilDiv(widthBlocks, profile.coarseCellBlocks());
        long coarseCellsZ = ceilDiv(lengthBlocks, profile.coarseCellBlocks());
        long coarseBytes = Math.multiplyExact(
                Math.multiplyExact(coarseCellsX, coarseCellsZ),
                ESTIMATED_ACTIVE_FIELD_LAYERS * ESTIMATED_BYTES_PER_FIELD_CELL);
        long tileStateBytes = Math.multiplyExact((long) tilePlan.tileCount(), ESTIMATED_TILE_STATE_BYTES);
        return Math.addExact(coarseBytes, tileStateBytes);
    }

    private static long estimateArtifactBytes(int widthBlocks, int lengthBlocks) {
        return Math.multiplyExact(
                Math.multiplyExact((long) widthBlocks, (long) lengthBlocks),
                ESTIMATED_ACTIVE_FIELD_LAYERS * ESTIMATED_BYTES_PER_FIELD_CELL);
    }

    private static long ceilDiv(long value, long divisor) {
        return (value + divisor - 1L) / divisor;
    }

    private static ScaleAdmissionExceptionV2 failure(ScaleAdmissionFailureCodeV2 code, String message) {
        return new ScaleAdmissionExceptionV2(code, message);
    }
}
