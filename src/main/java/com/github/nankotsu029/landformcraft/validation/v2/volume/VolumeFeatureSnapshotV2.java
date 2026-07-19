package com.github.nankotsu029.landformcraft.validation.v2.volume;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Public descriptor snapshot for one sealed volume feature. Corruption fixtures mutate these
 * fields without sharing generator-private metric code or allocating dense voxel grids.
 */
public record VolumeFeatureSnapshotV2(
        VolumeFeatureKind kind,
        String featureId,
        String planChecksum,
        int connectedComponents,
        boolean entranceReachable,
        int minRoofBlocks,
        boolean supportPresent,
        int clearanceBlocks,
        int componentCount,
        long fluidLeakSamples,
        long solidFluidConflictSamples,
        boolean fallContinuous,
        boolean islandsMerged,
        int materialClassCode
) {
    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public VolumeFeatureSnapshotV2 {
        Objects.requireNonNull(kind, "kind");
        if (featureId == null || !QUALIFIED.matcher(featureId).matches()) {
            throw new IllegalArgumentException("featureId invalid");
        }
        if (planChecksum == null || !CHECKSUM.matcher(planChecksum).matches()) {
            throw new IllegalArgumentException("planChecksum must be 64 lowercase hex");
        }
        if (connectedComponents < 0 || connectedComponents > 1_024
                || minRoofBlocks < 0 || minRoofBlocks > 512
                || clearanceBlocks < 0 || clearanceBlocks > 512
                || componentCount < 0 || componentCount > 1_024
                || fluidLeakSamples < 0
                || solidFluidConflictSamples < 0
                || materialClassCode < 0 || materialClassCode > 255) {
            throw new IllegalArgumentException("volume feature snapshot fields out of range");
        }
    }

    public enum VolumeFeatureKind {
        CAVE_NETWORK,
        LUSH_CAVE,
        UNDERGROUND_LAKE,
        SEA_CAVE,
        OVERHANG,
        NATURAL_ARCH,
        SKY_ISLAND_GROUP,
        WATERFALL_VOLUME
    }
}
