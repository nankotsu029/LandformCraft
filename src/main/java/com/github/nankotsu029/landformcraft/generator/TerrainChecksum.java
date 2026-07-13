package com.github.nankotsu029.landformcraft.generator;

import com.github.nankotsu029.landformcraft.model.StructurePlan;
import com.github.nankotsu029.landformcraft.model.WorldBlueprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class TerrainChecksum {
    private TerrainChecksum() {
    }

    static String tile(
            int originX,
            int originZ,
            int width,
            int length,
            int mapWidth,
            int mapOriginX,
            int mapOriginZ,
            int[] heights,
            int[] waterDepths,
            byte[] materials,
            int[] featureMasks
    ) {
        MessageDigest digest = sha256();
        updateInt(digest, originX);
        updateInt(digest, originZ);
        updateInt(digest, width);
        updateInt(digest, length);
        for (int z = originZ; z < originZ + length; z++) {
            for (int x = originX; x < originX + width; x++) {
                int index = (z - mapOriginZ) * mapWidth + x - mapOriginX;
                updateInt(digest, heights[index]);
                updateInt(digest, waterDepths[index]);
                digest.update(materials[index]);
                updateInt(digest, featureMasks[index]);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String terrain(
            WorldBlueprint blueprint,
            int[] heights,
            int[] waterDepths,
            byte[] materials,
            int[] featureMasks,
            List<StructurePlan> structures
    ) {
        MessageDigest digest = sha256();
        digest.update(blueprint.requestId().getBytes(StandardCharsets.UTF_8));
        digest.update(blueprint.generatorVersion().getBytes(StandardCharsets.UTF_8));
        updateLong(digest, blueprint.seed());
        updateInt(digest, blueprint.bounds().width());
        updateInt(digest, blueprint.bounds().length());
        updateInt(digest, blueprint.bounds().minY());
        updateInt(digest, blueprint.bounds().maxY());
        updateInt(digest, blueprint.bounds().waterLevel());
        for (int value : heights) {
            updateInt(digest, value);
        }
        for (int value : waterDepths) {
            updateInt(digest, value);
        }
        digest.update(materials);
        for (int value : featureMasks) {
            updateInt(digest, value);
        }
        for (StructurePlan structure : structures) {
            digest.update(structure.assetId().getBytes(StandardCharsets.UTF_8));
            digest.update(structure.assetChecksum().getBytes(StandardCharsets.UTF_8));
            updateInt(digest, structure.type().ordinal());
            updateInt(digest, structure.anchorX());
            updateInt(digest, structure.anchorY());
            updateInt(digest, structure.anchorZ());
            updateInt(digest, structure.rotation().ordinal());
            updateInt(digest, structure.sizeX());
            updateInt(digest, structure.sizeY());
            updateInt(digest, structure.sizeZ());
            digest.update((byte) (structure.terrainFollowing() ? 1 : 0));
            digest.update((byte) (structure.preferredZoneFallback() ? 1 : 0));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateLong(MessageDigest digest, long value) {
        digest.update((byte) (value >>> 56));
        digest.update((byte) (value >>> 48));
        digest.update((byte) (value >>> 40));
        digest.update((byte) (value >>> 32));
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }
}
