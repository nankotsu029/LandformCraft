package com.github.nankotsu029.landformcraft.generator.v2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Stable tagged SHA-256 seed derivation. No enum ordinal or collection order participates. */
public final class NamedSeedDeriverV2 {
    public static final String VERSION = "sha256-tagged-v1";

    private NamedSeedDeriverV2() {
    }

    public static long derive(
            long globalSeed,
            String moduleId,
            String moduleVersion,
            String featureId,
            String seedNamespace,
            String generatorVersion
    ) {
        MessageDigest digest = sha256();
        updateLong(digest, "global-seed", globalSeed);
        updateString(digest, "module-id", moduleId);
        updateString(digest, "module-version", moduleVersion);
        updateString(digest, "feature-id", featureId);
        updateString(digest, "seed-namespace", seedNamespace);
        updateString(digest, "generator-version", generatorVersion);
        return ByteBuffer.wrap(digest.digest(), 0, Long.BYTES).order(ByteOrder.BIG_ENDIAN).getLong();
    }

    private static void updateString(MessageDigest digest, String tag, String value) {
        Objects.requireNonNull(value, tag);
        updateBytes(digest, tag.getBytes(StandardCharsets.UTF_8));
        updateBytes(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void updateLong(MessageDigest digest, String tag, long value) {
        updateBytes(digest, tag.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).array();
        updateBytes(digest, bytes);
    }

    private static void updateBytes(MessageDigest digest, byte[] bytes) {
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
