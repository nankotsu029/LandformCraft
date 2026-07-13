package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.model.ExportManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Computes a bounded worst-case estimate without expanding any block list. */
public final class PlacementDiskEstimator {
    private static final long JOURNAL_ALLOWANCE = 1024L * 1024L;
    private static final long TILE_NBT_ALLOWANCE = 1024L * 1024L;

    public PlacementDiskEstimate estimate(
            Path releaseDirectory,
            ExportManifest manifest,
            DiskBudgetPolicy policy
    ) throws IOException {
        long releaseBytes = directoryBytes(releaseDirectory);
        long snapshotBytes = 0L;
        try {
            for (var tile : manifest.tiles()) {
                snapshotBytes = Math.addExact(snapshotBytes,
                        Math.addExact(Math.multiplyExact(tile.blockCount(), 5L), TILE_NBT_ALLOWANCE));
            }
            if (snapshotBytes > policy.maximumSnapshotBytes()) {
                throw new LandformException(
                        LandformErrorCode.SNAPSHOT_NO_SPACE,
                        "Snapshot estimate exceeds the configured per-placement limit.",
                        "placement-plan", manifest.requestId(), "disk-estimate",
                        "Use a smaller release or raise placement.disk.maximum-snapshot-bytes after capacity review."
                );
            }
            long temporary = snapshotBytes;
            long rollback = Math.max(16L * 1024L * 1024L, snapshotBytes / 4L);
            long total = Math.addExact(releaseBytes, snapshotBytes);
            total = Math.addExact(total, JOURNAL_ALLOWANCE);
            total = Math.addExact(total, temporary);
            total = Math.addExact(total, rollback);
            total = Math.addExact(total, policy.safetyMarginBytes());
            return new PlacementDiskEstimate(
                    releaseBytes, snapshotBytes, JOURNAL_ALLOWANCE, temporary, rollback,
                    policy.safetyMarginBytes(), total
            );
        } catch (ArithmeticException exception) {
            throw new LandformException(
                    LandformErrorCode.SNAPSHOT_NO_SPACE,
                    "Snapshot capacity estimate overflowed and was conservatively rejected.",
                    "placement-plan", manifest.requestId(), "disk-estimate",
                    "Reduce the release dimensions.", exception
            );
        }
    }

    private static long directoryBytes(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized) || Files.isSymbolicLink(normalized)) {
            throw new IOException("release directory is not a regular directory");
        }
        long total = 0L;
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("release contains a symbolic link");
                }
                if (Files.isRegularFile(path)) {
                    total = Math.addExact(total, Files.size(path));
                }
            }
        }
        return total;
    }
}
