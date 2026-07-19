package com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** Shared SHA-256 field and underwater-column checksum helpers for V2-9-08 bathymetry. */
public final class BathymetryChecksumSupportV2 {
    @FunctionalInterface
    public interface CellSource {
        BathymetrySampleV2 sampleAt(int x, int z);
    }

    private BathymetryChecksumSupportV2() {
    }

    public static Map<BathymetrySampleV2.BathymetryField, String> fieldChecksumsFrom(
            String version,
            int width,
            int length,
            CellSource source
    ) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(source, "source");
        EnumMap<BathymetrySampleV2.BathymetryField, MessageDigest> digests =
                new EnumMap<>(BathymetrySampleV2.BathymetryField.class);
        for (BathymetrySampleV2.BathymetryField field : BathymetrySampleV2.BathymetryField.values()) {
            MessageDigest digest = sha256();
            digest.update((version + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width);
            updateInt(digest, length);
            digests.put(field, digest);
        }
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                BathymetrySampleV2 sample = Objects.requireNonNull(source.sampleAt(x, z), "sample");
                for (BathymetrySampleV2.BathymetryField field : BathymetrySampleV2.BathymetryField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<BathymetrySampleV2.BathymetryField, String> result =
                new EnumMap<>(BathymetrySampleV2.BathymetryField.class);
        for (BathymetrySampleV2.BathymetryField field : BathymetrySampleV2.BathymetryField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    public static Map<BathymetrySampleV2.BathymetryField, String> tiledFieldChecksums(
            String version,
            int width,
            int length,
            int tileSize,
            CellSource source
    ) {
        Objects.requireNonNull(source, "source");
        if (tileSize < 1) {
            throw new IllegalArgumentException("tileSize must be positive");
        }
        int cells = Math.multiplyExact(width, length);
        int[][] values = new int[BathymetrySampleV2.BathymetryField.values().length][cells];
        boolean[] covered = new boolean[cells];
        for (int originZ = 0; originZ < length; originZ += tileSize) {
            for (int originX = 0; originX < width; originX += tileSize) {
                int endX = Math.min(width, originX + tileSize);
                int endZ = Math.min(length, originZ + tileSize);
                for (int z = originZ; z < endZ; z++) {
                    for (int x = originX; x < endX; x++) {
                        int index = z * width + x;
                        covered[index] = true;
                        BathymetrySampleV2 sample = Objects.requireNonNull(source.sampleAt(x, z), "sample");
                        for (BathymetrySampleV2.BathymetryField field
                                : BathymetrySampleV2.BathymetryField.values()) {
                            values[field.ordinal()][index] = sample.rawValue(field);
                        }
                    }
                }
            }
        }
        for (boolean cell : covered) {
            if (!cell) {
                throw new IllegalStateException("tiled bathymetry coverage incomplete");
            }
        }
        return fieldChecksumsFrom(version, width, length, (x, z) -> {
            int index = z * width + x;
            return new BathymetrySampleV2(
                    values[BathymetrySampleV2.BathymetryField.DEPTH.ordinal()][index],
                    values[BathymetrySampleV2.BathymetryField.SLOPE.ordinal()][index],
                    values[BathymetrySampleV2.BathymetryField.COAST_DISTANCE.ordinal()][index],
                    values[BathymetrySampleV2.BathymetryField.OWNERSHIP.ordinal()][index],
                    0,
                    0);
        });
    }

    /**
     * Streams X→Z→Y solid/fluid tags for owned columns without allocating width×length×height arrays.
     * Solid owns floorY and below; fluid occupies (floorY+1)..waterLevel when floorY &lt; waterLevel.
     */
    public static String underwaterColumnExportChecksum(
            String version,
            int width,
            int length,
            int waterLevel,
            int minY,
            CellSource source
    ) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(source, "source");
        MessageDigest digest = sha256();
        digest.update((version + "\0underwater-column\0").getBytes(StandardCharsets.UTF_8));
        updateInt(digest, width);
        updateInt(digest, length);
        updateInt(digest, waterLevel);
        updateInt(digest, minY);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                BathymetrySampleV2 sample = Objects.requireNonNull(source.sampleAt(x, z), "sample");
                if (!sample.owned()) {
                    digest.update(BathymetrySampleV2.TAG_EMPTY);
                    continue;
                }
                int floorY = sample.floorY();
                updateInt(digest, floorY);
                int top = Math.max(floorY, waterLevel);
                for (int y = minY; y <= top; y++) {
                    byte tag;
                    if (y <= floorY) {
                        tag = BathymetrySampleV2.TAG_SOLID;
                    } else if (y <= waterLevel) {
                        tag = BathymetrySampleV2.TAG_FLUID;
                    } else {
                        tag = BathymetrySampleV2.TAG_EMPTY;
                    }
                    digest.update(tag);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static boolean fluidSolidConflictFree(BathymetrySampleV2 sample, int waterLevel) {
        if (!sample.owned()) {
            return true;
        }
        if (sample.depthBlocksBelowSea() < 0) {
            return false;
        }
        int floorY = waterLevel - sample.depthBlocksBelowSea();
        if (floorY != sample.floorY()) {
            return false;
        }
        // Fluid hint must never claim below the solid floor.
        if (sample.fluidColumnHintTopY() < floorY) {
            return false;
        }
        if (floorY < waterLevel && sample.fluidColumnHintTopY() != waterLevel) {
            return false;
        }
        return true;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }
}
