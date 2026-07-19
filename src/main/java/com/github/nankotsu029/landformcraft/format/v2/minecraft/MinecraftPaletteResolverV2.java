package com.github.nankotsu029.landformcraft.format.v2.minecraft;

import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves V2-4-07 semantic material class codes to frozen Minecraft 1.21.11 block states.
 * Unknown IDs, future resolver versions, and unmapped aspects fail closed with no fallback.
 */
public final class MinecraftPaletteResolverV2 {
    private static final String FIELD_CHECKSUM_PREFIX = "LFC_MINECRAFT_PALETTE_FIELD_V1\n";

    private final MinecraftPalettePlanV2 plan;
    private final ConcurrentHashMap<Long, String> cache = new ConcurrentHashMap<>();

    public MinecraftPaletteResolverV2(MinecraftPalettePlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (!MinecraftPalettePlanV2.RESOLVER_VERSION.equals(plan.compatibility().resolverVersion())
                || !MinecraftPalettePlanV2.MINECRAFT_VERSION.equals(plan.compatibility().minecraftVersion())
                || plan.compatibility().dataVersion() != MinecraftPalettePlanV2.DATA_VERSION) {
            throw new IllegalArgumentException("unsupported minecraft-palette resolver compatibility");
        }
        for (MinecraftPalettePlanV2.Mapping mapping : plan.catalog().mappings()) {
            EnvironmentBlockStateCatalogV2.requireKnown(mapping.blockState());
        }
    }

    public MinecraftPalettePlanV2 plan() {
        return plan;
    }

    public String resolve(
            MaterialProfilePlanV2.SemanticMaterialClass kind,
            MaterialProfilePlanV2.SurfaceAspect aspect
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(aspect, "aspect");
        return EnvironmentBlockStateCatalogV2.requireKnown(
                plan.catalog().require(kind, aspect).blockState());
    }

    public String resolveByCode(int classCode, MaterialProfilePlanV2.SurfaceAspect aspect) {
        Objects.requireNonNull(aspect, "aspect");
        if (classCode < 1 || classCode > 255) {
            throw new IllegalArgumentException("unknown material-profile compact code: " + classCode);
        }
        long cacheKey = (((long) classCode) << 8) | aspect.ordinal();
        return cache.computeIfAbsent(cacheKey, ignored ->
                EnvironmentBlockStateCatalogV2.requireKnown(
                        plan.catalog().requireByCode(classCode, aspect).blockState()));
    }

    public String[] sampleWindow(
            MaterialProfilePlanV2.SurfaceAspect aspect,
            int startX,
            int startZ,
            int width,
            int length,
            ClassCodeSource source
    ) {
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(source, "source");
        if (width < 1 || length < 1
                || (long) width * length > plan.budget().maximumPaletteSize()) {
            throw new IllegalArgumentException("minecraft-palette sample window exceeds trusted bounds");
        }
        String[] out = new String[Math.multiplyExact(width, length)];
        int index = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                out[index++] = resolveByCode(source.classCodeAt(startX + x, startZ + z), aspect);
            }
        }
        return out;
    }

    public String checksum(
            MaterialProfilePlanV2.SurfaceAspect aspect,
            int width,
            int length,
            ClassCodeSource source
    ) {
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(source, "source");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(FIELD_CHECKSUM_PREFIX.getBytes(StandardCharsets.UTF_8));
            digest.update(aspect.name().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    String state = resolveByCode(source.classCodeAt(x, z), aspect);
                    buffer.clear();
                    buffer.putInt(state.hashCode());
                    digest.update(buffer.array());
                    digest.update(state.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    public void clearCache() {
        cache.clear();
    }

    @FunctionalInterface
    public interface ClassCodeSource {
        int classCodeAt(int globalX, int globalZ);
    }
}
