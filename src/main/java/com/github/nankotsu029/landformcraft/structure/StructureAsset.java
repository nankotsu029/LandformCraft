package com.github.nankotsu029.landformcraft.structure;

import com.github.nankotsu029.landformcraft.model.StructureType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, checksummed built-in template used for placement and standalone schematic export. */
public record StructureAsset(
        String assetId,
        StructureType type,
        String minecraftVersion,
        int width,
        int height,
        int length,
        boolean terrainFollowing,
        StructurePlacementKind placementKind,
        int maximumSlope,
        List<StructureBlock> blocks,
        String semanticChecksum
) {
    public StructureAsset(
            String assetId,
            StructureType type,
            String minecraftVersion,
            int width,
            int height,
            int length,
            boolean terrainFollowing,
            StructurePlacementKind placementKind,
            int maximumSlope,
            List<StructureBlock> blocks
    ) {
        this(assetId, type, minecraftVersion, width, height, length, terrainFollowing, placementKind,
                maximumSlope, blocks, checksum(assetId, type, minecraftVersion, width, height, length,
                        terrainFollowing, placementKind, maximumSlope, blocks));
    }

    public StructureAsset {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(placementKind, "placementKind");
        if (!assetId.matches("[a-z0-9][a-z0-9._-]{0,63}")
                || width < 1 || width > 64 || height < 1 || height > 64 || length < 1 || length > 64
                || maximumSlope < 0 || maximumSlope > 32) {
            throw new IllegalArgumentException("invalid structure asset metadata");
        }
        blocks = List.copyOf(blocks);
        if (blocks.isEmpty() || blocks.size() > 32_768) {
            throw new IllegalArgumentException("structure asset must contain 1..32768 blocks");
        }
        Set<Long> positions = new HashSet<>();
        for (StructureBlock block : blocks) {
            if (block.x() >= width || block.y() >= height || block.z() >= length) {
                throw new IllegalArgumentException("structure block is outside asset dimensions");
            }
            long key = ((long) block.y() << 32) | ((long) block.z() << 16) | block.x();
            if (!positions.add(key)) {
                throw new IllegalArgumentException("structure asset contains duplicate block coordinates");
            }
        }
        String expected = checksum(assetId, type, minecraftVersion, width, height, length,
                terrainFollowing, placementKind, maximumSlope, blocks);
        if (!expected.equals(semanticChecksum)) {
            throw new IllegalArgumentException("structure asset semantic checksum mismatch");
        }
    }

    public int rotatedWidth(com.github.nankotsu029.landformcraft.model.QuarterTurn rotation) {
        return rotation == com.github.nankotsu029.landformcraft.model.QuarterTurn.CLOCKWISE_90
                || rotation == com.github.nankotsu029.landformcraft.model.QuarterTurn.CLOCKWISE_270
                ? length : width;
    }

    public int rotatedLength(com.github.nankotsu029.landformcraft.model.QuarterTurn rotation) {
        return rotation == com.github.nankotsu029.landformcraft.model.QuarterTurn.CLOCKWISE_90
                || rotation == com.github.nankotsu029.landformcraft.model.QuarterTurn.CLOCKWISE_270
                ? width : length;
    }

    private static String checksum(
            String assetId,
            StructureType type,
            String minecraftVersion,
            int width,
            int height,
            int length,
            boolean terrainFollowing,
            StructurePlacementKind placementKind,
            int maximumSlope,
            List<StructureBlock> blocks
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, assetId);
            update(digest, type.name());
            update(digest, minecraftVersion);
            update(digest, width);
            update(digest, height);
            update(digest, length);
            update(digest, terrainFollowing ? 1 : 0);
            update(digest, placementKind.name());
            update(digest, maximumSlope);
            blocks.stream().sorted(Comparator.comparingInt(StructureBlock::y)
                            .thenComparingInt(StructureBlock::z).thenComparingInt(StructureBlock::x))
                    .forEach(block -> {
                        update(digest, block.x());
                        update(digest, block.y());
                        update(digest, block.z());
                        update(digest, block.blockState());
                    });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        update(digest, bytes.length);
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }
}
