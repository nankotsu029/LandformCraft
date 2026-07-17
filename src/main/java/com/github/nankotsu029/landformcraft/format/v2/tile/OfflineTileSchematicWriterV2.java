package com.github.nankotsu029.landformcraft.format.v2.tile;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;

/** V2-only two-pass streaming Sponge v3 writer. It retains palette counts, never a block list. */
public final class OfflineTileSchematicWriterV2 {
    public static final int MAXIMUM_ENCODED_BLOCK_BYTES = 40 * 1024 * 1024;
    public static final long MAXIMUM_PALETTE_RETAINED_BYTES = 16L * 1024L * 1024L;

    private static final byte TAG_END = 0;
    private static final byte TAG_SHORT = 2;
    private static final byte TAG_INT = 3;
    private static final byte TAG_BYTE_ARRAY = 7;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARRAY = 11;

    public OfflineTileArtifactV2 write(
            Path target,
            OfflineTilePlanV2 plan,
            String sourceBlueprintChecksum,
            TerrainBlockResolver resolver,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(sourceBlueprintChecksum, "sourceBlueprintChecksum");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (!target.getFileName().toString().equals(plan.defaultSchematicFileName())) {
            throw new IOException("offline tile target filename must match its canonical tile ID");
        }
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(absolute.getParent(), "offline tile target requires a parent");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("offline tile target parent must be a non-symbolic directory");
        }
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("offline tile target already exists");
        }

        cancellationToken.throwIfCancellationRequested();
        PaletteScan scan = scan(plan, resolver, cancellationToken);
        Path temporary = Files.createTempFile(parent, ".offline-tile-", ".schem.tmp");
        boolean published = false;
        try {
            writeSchematic(temporary, plan, resolver, scan, cancellationToken);
            SpongeV3TileInspectorV2.Inspection inspection = new SpongeV3TileInspectorV2().inspect(temporary, plan);
            if (inspection.paletteSize() != scan.paletteIds().size()
                    || !inspection.semanticChecksum().equals(scan.semanticChecksum())) {
                throw new IOException("offline tile changed during staged strict read-back");
            }
            long byteLength = Files.size(temporary);
            if (byteLength < 1 || byteLength > OfflineTileArtifactV2.MAXIMUM_ARTIFACT_BYTES) {
                throw new IOException("offline tile schematic exceeds its artifact byte budget");
            }
            String artifactChecksum = Sha256.file(temporary);
            OfflineTileArtifactV2 artifact = new OfflineTileArtifactCodecV2().seal(new OfflineTileArtifactV2(
                    plan, sourceBlueprintChecksum, absolute.getFileName().toString(), scan.paletteIds().size(),
                    byteLength, artifactChecksum, scan.semanticChecksum()));
            // This is the final cancellation observation. The atomic move is the commit point.
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for offline tile publication", exception);
            }
            published = true;
            return artifact;
        } finally {
            if (!published) Files.deleteIfExists(temporary);
        }
    }

    public static long estimatePaletteRetainedBytes(int paletteSize, long totalStateUtf8Bytes) {
        if (paletteSize < 0 || paletteSize > OfflineTileArtifactV2.MAXIMUM_PALETTE_SIZE
                || totalStateUtf8Bytes < 0 || totalStateUtf8Bytes > (long) paletteSize * 512L) {
            throw new IllegalArgumentException("invalid palette retained-byte estimate input");
        }
        return Math.addExact(512L, Math.addExact(totalStateUtf8Bytes,
                Math.multiplyExact((long) paletteSize, 160L)));
    }

    private static PaletteScan scan(
            OfflineTilePlanV2 plan,
            TerrainBlockResolver resolver,
            CancellationToken cancellationToken
    ) {
        TreeMap<String, Long> counts = new TreeMap<>();
        MessageDigest digest = CanonicalBlockStreamV2.newDigest(plan);
        for (int y = plan.minY(); y <= plan.maxY(); y++) {
            cancellationToken.throwIfCancellationRequested();
            for (int z = plan.originZ(); z < plan.originZ() + plan.length(); z++) {
                for (int x = plan.originX(); x < plan.originX() + plan.width(); x++) {
                    String state = CoastalBlockStateCatalogV2.requireKnown(resolver.blockStateAt(x, y, z));
                    counts.merge(state, 1L, Math::addExact);
                    CanonicalBlockStreamV2.updateState(digest, state);
                }
            }
        }
        if (counts.size() > OfflineTileArtifactV2.MAXIMUM_PALETTE_SIZE) {
            throw new IllegalArgumentException("offline tile palette exceeds 16384 entries");
        }
        long stateBytes = counts.keySet().stream()
                .mapToLong(state -> state.getBytes(StandardCharsets.UTF_8).length).sum();
        if (estimatePaletteRetainedBytes(counts.size(), stateBytes) > MAXIMUM_PALETTE_RETAINED_BYTES) {
            throw new IllegalArgumentException("offline tile palette exceeds its retained-memory budget");
        }
        LinkedHashMap<String, Integer> paletteIds = new LinkedHashMap<>();
        int next = 0;
        long encodedBytes = 0;
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            int id = next++;
            paletteIds.put(entry.getKey(), id);
            encodedBytes = Math.addExact(encodedBytes,
                    Math.multiplyExact(entry.getValue(), VarIntV2.encodedSize(id)));
        }
        if (encodedBytes > MAXIMUM_ENCODED_BLOCK_BYTES) {
            throw new IllegalArgumentException("offline tile block data exceeds its encoded-byte budget");
        }
        return new PaletteScan(
                Collections.unmodifiableMap(new LinkedHashMap<>(paletteIds)),
                Math.toIntExact(encodedBytes), CanonicalBlockStreamV2.finish(digest));
    }

    private static void writeSchematic(
            Path target,
            OfflineTilePlanV2 plan,
            TerrainBlockResolver resolver,
            PaletteScan scan,
            CancellationToken cancellationToken
    ) throws IOException {
        try (OutputStream file = Files.newOutputStream(target);
             GZIPOutputStream gzip = new GZIPOutputStream(new BufferedOutputStream(file));
             DataOutputStream output = new DataOutputStream(gzip)) {
            writeNamedTag(output, TAG_COMPOUND, "");
            writeNamedTag(output, TAG_COMPOUND, "Schematic");
            writeInt(output, "Version", OfflineTileArtifactV2.SCHEMATIC_VERSION);
            writeInt(output, "DataVersion", OfflineTileArtifactV2.DATA_VERSION);
            writeShort(output, "Width", plan.width());
            writeShort(output, "Height", plan.height());
            writeShort(output, "Length", plan.length());
            writeNamedTag(output, TAG_INT_ARRAY, "Offset");
            output.writeInt(3);
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(0);
            writeNamedTag(output, TAG_COMPOUND, "Blocks");
            writeNamedTag(output, TAG_COMPOUND, "Palette");
            for (Map.Entry<String, Integer> entry : scan.paletteIds().entrySet()) {
                writeInt(output, entry.getKey(), entry.getValue());
            }
            output.writeByte(TAG_END);
            writeNamedTag(output, TAG_BYTE_ARRAY, "Data");
            output.writeInt(scan.encodedBlockBytes());
            MessageDigest secondPass = CanonicalBlockStreamV2.newDigest(plan);
            for (int y = plan.minY(); y <= plan.maxY(); y++) {
                cancellationToken.throwIfCancellationRequested();
                for (int z = plan.originZ(); z < plan.originZ() + plan.length(); z++) {
                    for (int x = plan.originX(); x < plan.originX() + plan.width(); x++) {
                        String state = CoastalBlockStateCatalogV2.requireKnown(resolver.blockStateAt(x, y, z));
                        Integer paletteId = scan.paletteIds().get(state);
                        if (paletteId == null) {
                            throw new IOException("TerrainBlockResolver changed its palette between writer passes");
                        }
                        CanonicalBlockStreamV2.updateState(secondPass, state);
                        VarIntV2.write(output, paletteId);
                    }
                }
            }
            if (!CanonicalBlockStreamV2.finish(secondPass).equals(scan.semanticChecksum())) {
                throw new IOException("TerrainBlockResolver changed its block stream between writer passes");
            }
            writeNamedTag(output, TAG_LIST, "BlockEntities");
            output.writeByte(TAG_COMPOUND);
            output.writeInt(0);
            output.writeByte(TAG_END);
            output.writeByte(TAG_END);
            output.writeByte(TAG_END);
        }
    }

    private static void writeInt(DataOutputStream output, String name, int value) throws IOException {
        writeNamedTag(output, TAG_INT, name);
        output.writeInt(value);
    }

    private static void writeShort(DataOutputStream output, String name, int value) throws IOException {
        writeNamedTag(output, TAG_SHORT, name);
        output.writeShort(value);
    }

    private static void writeNamedTag(DataOutputStream output, byte type, String name) throws IOException {
        output.writeByte(type);
        byte[] encoded = name.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 65_535) throw new IOException("NBT name is too long");
        output.writeShort(encoded.length);
        output.write(encoded);
    }

    private record PaletteScan(Map<String, Integer> paletteIds, int encodedBlockBytes, String semanticChecksum) {
    }
}
