package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Objects;

/** Strict metadata for one standalone V2 Sponge v3 tile; this is not a Release 2 manifest. */
public record OfflineTileArtifactV2(
        int tileArtifactVersion,
        String tileId,
        String sourceBlueprintChecksum,
        int xIndex,
        int zIndex,
        int originX,
        int originZ,
        int width,
        int length,
        int minY,
        int maxY,
        CoordinateSpace coordinateSpace,
        BlockOrder blockOrder,
        String minecraftVersion,
        int dataVersion,
        int schematicVersion,
        String schematicPath,
        int blockCount,
        int paletteSize,
        long byteLength,
        String artifactChecksum,
        String semanticChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MINECRAFT_VERSION = "1.21.11";
    public static final int DATA_VERSION = 4671;
    public static final int SCHEMATIC_VERSION = 3;
    public static final int MAXIMUM_PALETTE_SIZE = 16_384;
    public static final long MAXIMUM_ARTIFACT_BYTES = 64L * 1024L * 1024L;
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);

    public OfflineTileArtifactV2 {
        if (tileArtifactVersion != VERSION) throw new IllegalArgumentException("tileArtifactVersion must be 1");
        tileId = V2Validation.qualifiedId(tileId, "tileId");
        sourceBlueprintChecksum = V2Validation.checksum(sourceBlueprintChecksum, "sourceBlueprintChecksum");
        OfflineTilePlanV2 plan = new OfflineTilePlanV2(
                OfflineTilePlanV2.VERSION, tileId, xIndex, zIndex, originX, originZ,
                width, length, minY, maxY);
        Objects.requireNonNull(coordinateSpace, "coordinateSpace");
        Objects.requireNonNull(blockOrder, "blockOrder");
        if (coordinateSpace != CoordinateSpace.RELEASE_LOCAL_XYZ
                || blockOrder != BlockOrder.SPONGE_X_Z_Y_V1
                || !MINECRAFT_VERSION.equals(minecraftVersion)
                || dataVersion != DATA_VERSION || schematicVersion != SCHEMATIC_VERSION) {
            throw new IllegalArgumentException("offline tile compatibility tuple is unsupported");
        }
        schematicPath = V2Validation.safeRelativePath(schematicPath, "schematicPath");
        if (!schematicPath.endsWith(".schem")) {
            throw new IllegalArgumentException("schematicPath must name a .schem artifact");
        }
        if (blockCount != plan.blockCount() || paletteSize < 1 || paletteSize > MAXIMUM_PALETTE_SIZE
                || byteLength < 1 || byteLength > MAXIMUM_ARTIFACT_BYTES) {
            throw new IllegalArgumentException("offline tile artifact exceeds its fixed budgets");
        }
        artifactChecksum = V2Validation.checksum(artifactChecksum, "artifactChecksum");
        semanticChecksum = V2Validation.checksum(semanticChecksum, "semanticChecksum");
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public OfflineTileArtifactV2(
            OfflineTilePlanV2 plan,
            String sourceBlueprintChecksum,
            String schematicPath,
            int paletteSize,
            long byteLength,
            String artifactChecksum,
            String semanticChecksum
    ) {
        this(VERSION, plan.tileId(), sourceBlueprintChecksum, plan.xIndex(), plan.zIndex(),
                plan.originX(), plan.originZ(), plan.width(), plan.length(), plan.minY(), plan.maxY(),
                CoordinateSpace.RELEASE_LOCAL_XYZ, BlockOrder.SPONGE_X_Z_Y_V1,
                MINECRAFT_VERSION, DATA_VERSION, SCHEMATIC_VERSION, schematicPath,
                plan.blockCount(), paletteSize, byteLength, artifactChecksum, semanticChecksum,
                PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public OfflineTileArtifactV2 withCanonicalChecksum(String checksum) {
        return new OfflineTileArtifactV2(
                tileArtifactVersion, tileId, sourceBlueprintChecksum, xIndex, zIndex, originX, originZ,
                width, length, minY, maxY, coordinateSpace, blockOrder, minecraftVersion, dataVersion,
                schematicVersion, schematicPath, blockCount, paletteSize, byteLength,
                artifactChecksum, semanticChecksum, checksum);
    }

    public OfflineTilePlanV2 tilePlan() {
        return new OfflineTilePlanV2(
                OfflineTilePlanV2.VERSION, tileId, xIndex, zIndex, originX, originZ,
                width, length, minY, maxY);
    }

    public enum CoordinateSpace { RELEASE_LOCAL_XYZ }
    public enum BlockOrder { SPONGE_X_Z_Y_V1 }
}
