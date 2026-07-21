package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pre-admission budget for one Release 2 export (V2-12-02). Every limit is checked before any
 * field, preview, or tile is produced, so an oversized request never partially writes artifacts.
 */
public record ExportBudgetV2(
        int maximumTileCount,
        long maximumResidentBytes,
        long maximumFreeDiskBytes
) {
    public static final int MAXIMUM_RELEASE_TILES = 64;

    public ExportBudgetV2 {
        if (maximumTileCount < 1 || maximumTileCount > MAXIMUM_RELEASE_TILES) {
            throw new IllegalArgumentException("export tile budget must be in 1.." + MAXIMUM_RELEASE_TILES);
        }
        if (maximumResidentBytes < 1 || maximumFreeDiskBytes < 1) {
            throw new IllegalArgumentException("export budgets must be positive");
        }
    }

    public static ExportBudgetV2 defaults() {
        return new ExportBudgetV2(MAXIMUM_RELEASE_TILES, 256L * 1024L * 1024L, 64L * 1024L * 1024L);
    }

    void requireAdmitted(int width, int length, int tileCount, ScaleProfileV2 profile) throws IOException {
        if (tileCount > Math.min(maximumTileCount, profile.maximumTileCount())) {
            throw new IOException("Release 2 export tile count exceeds its budget: " + tileCount);
        }
        long resident = CoastalSurfaceFieldsV2.estimatedResidentBytes(width, length);
        if (resident > Math.min(maximumResidentBytes, profile.maximumRetainedBytes())) {
            throw new IOException("Release 2 export descriptor working set exceeds its budget: " + resident);
        }
    }

    /**
     * Admits the staging area's free disk. Public so the V2-12-09 export plan can reserve before it
     * issues a confirmation token: a reservation the runtime cannot honour must fail at plan time,
     * not after the operator confirms.
     */
    public void requireFreeDisk(Path root) throws IOException {
        Files.createDirectories(root);
        FileStore store = Files.getFileStore(root);
        if (store.getUsableSpace() < maximumFreeDiskBytes) {
            throw new IOException("insufficient free disk for a Release 2 export staging area");
        }
    }
}
