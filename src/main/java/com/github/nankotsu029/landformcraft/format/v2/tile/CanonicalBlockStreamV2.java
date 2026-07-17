package com.github.nankotsu029.landformcraft.format.v2.tile;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Stable semantic block-state stream: X fastest, then Z, then Y, all in release-local coordinates. */
public final class CanonicalBlockStreamV2 {
    public static final String VERSION = "lfc-tile-block-stream-v1";

    private CanonicalBlockStreamV2() {
    }

    public static String checksum(
            OfflineTilePlanV2 plan,
            TerrainBlockResolver resolver,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        MessageDigest digest = newDigest(plan);
        for (int y = plan.minY(); y <= plan.maxY(); y++) {
            cancellationToken.throwIfCancellationRequested();
            for (int z = plan.originZ(); z < plan.originZ() + plan.length(); z++) {
                for (int x = plan.originX(); x < plan.originX() + plan.width(); x++) {
                    updateState(digest, CanonicalBlockStateV2.requireCanonical(resolver.blockStateAt(x, y, z)));
                }
            }
        }
        return finish(digest);
    }

    static MessageDigest newDigest(OfflineTilePlanV2 plan) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        byte[] version = VERSION.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, version.length);
        digest.update(version);
        updateInt(digest, plan.originX());
        updateInt(digest, plan.originZ());
        updateInt(digest, plan.width());
        updateInt(digest, plan.length());
        updateInt(digest, plan.minY());
        updateInt(digest, plan.maxY());
        return digest;
    }

    static void updateState(MessageDigest digest, String state) {
        byte[] bytes = state.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    static String finish(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }
}
