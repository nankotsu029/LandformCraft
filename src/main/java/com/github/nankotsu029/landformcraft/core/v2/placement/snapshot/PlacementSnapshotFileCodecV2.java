package com.github.nankotsu029.landformcraft.core.v2.placement.snapshot;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Strict writer／reader for `release-2-placement-snapshot-file-v1` effect-envelope snapshot files
 * (V2-6-04). The payload is a lexicographically sorted block-state palette followed by general
 * VarInt palette indices in canonical X-fastest→Z→Y order. The writer scans the world region twice
 * with a bounded buffer — first to freeze the palette, then to emit indices — and hashes the
 * canonical block-state stream on both passes; a hash difference means the world changed between
 * observation and use and is hard-rejected as {@code WORLD_DRIFT}.
 */
public final class PlacementSnapshotFileCodecV2 {
    public static final int STREAM_BUFFER_BYTES = 64 * 1024;
    public static final int MAXIMUM_BLOCK_STATE_LENGTH = 256;
    public static final int CANCEL_CHECK_INTERVAL_BLOCKS = 4_096;

    private static final String MAGIC = "LFC-PLACEMENT-SNAPSHOT";

    /** Result of writing one tile snapshot; checksums are re-verified during strict read-back. */
    public record WrittenTileSnapshotV2(
            long fileBytes,
            long blockCount,
            int paletteSize,
            String artifactChecksum,
            String blockStateStreamChecksum
    ) {
    }

    /**
     * Streams the region from the gateway into {@code file}. {@code remainingDiskBytes} is the
     * hard admission ceiling for this file; exceeding it aborts before further disk use.
     */
    public WrittenTileSnapshotV2 write(
            PlacementWorldGatewayV2 gateway,
            UUID worldId,
            String tileId,
            WorldAabbV2 region,
            Path file,
            int maximumPaletteEntries,
            long remainingDiskBytes,
            CancellationToken cancellation
    ) throws IOException {
        Objects.requireNonNull(gateway, "gateway");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(tileId, "tileId");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(cancellation, "cancellation");
        long volume = region.volumeBlocks();

        TreeSet<String> palette = new TreeSet<>();
        MessageDigest firstPassDigest = sha256();
        long firstPassCount = streamRegion(gateway, worldId, region, cancellation, (state) -> {
            if (palette.add(state) && palette.size() > maximumPaletteEntries) {
                throw new PlacementSnapshotExceptionV2(
                        PlacementSnapshotFailureCodeV2.PALETTE_BUDGET_EXCEEDED,
                        "snapshot palette exceeds " + maximumPaletteEntries + " entries for " + tileId);
            }
            updateStreamDigest(firstPassDigest, state);
        });
        requireVolume(firstPassCount, volume, tileId);
        String firstPassStreamChecksum = hex(firstPassDigest.digest());

        List<String> paletteOrder = new ArrayList<>(palette);
        Map<String, Integer> paletteIndex = new HashMap<>();
        for (int i = 0; i < paletteOrder.size(); i++) {
            paletteIndex.put(paletteOrder.get(i), i);
        }

        MessageDigest artifactDigest = sha256();
        MessageDigest secondPassDigest = sha256();
        long fileBytes;
        try (OutputStream fileStream = Files.newOutputStream(
                file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                CountingOutputStream counting = new CountingOutputStream(fileStream, remainingDiskBytes, tileId);
                DigestOutputStream digesting = new DigestOutputStream(counting, artifactDigest);
                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(digesting, STREAM_BUFFER_BYTES))) {
            out.writeUTF(MAGIC);
            out.writeUTF(PlacementSnapshotPlanV2.SNAPSHOT_FILE_FORMAT_VERSION);
            out.writeUTF(tileId);
            out.writeLong(worldId.getMostSignificantBits());
            out.writeLong(worldId.getLeastSignificantBits());
            out.writeInt(region.minX());
            out.writeInt(region.minY());
            out.writeInt(region.minZ());
            out.writeInt(region.maxX());
            out.writeInt(region.maxY());
            out.writeInt(region.maxZ());
            out.writeInt(paletteOrder.size());
            for (String state : paletteOrder) {
                out.writeUTF(state);
            }
            out.writeLong(volume);
            long secondPassCount = streamRegion(gateway, worldId, region, cancellation, (state) -> {
                Integer index = paletteIndex.get(state);
                if (index == null) {
                    throw new PlacementSnapshotExceptionV2(
                            PlacementSnapshotFailureCodeV2.WORLD_DRIFT,
                            "world changed between snapshot passes for " + tileId
                                    + ": unknown block state " + state);
                }
                updateStreamDigest(secondPassDigest, state);
                writeVarInt(out, index);
            });
            requireVolume(secondPassCount, volume, tileId);
            out.flush();
            fileBytes = counting.written();
        }
        String secondPassStreamChecksum = hex(secondPassDigest.digest());
        if (!firstPassStreamChecksum.equals(secondPassStreamChecksum)) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.WORLD_DRIFT,
                    "world changed between snapshot passes for " + tileId);
        }
        return new WrittenTileSnapshotV2(
                fileBytes,
                volume,
                paletteOrder.size(),
                hex(artifactDigest.digest()),
                secondPassStreamChecksum);
    }

    /**
     * Strict read-back. Verifies structure, identity, bounded palette, exact trailing EOF, and
     * recomputes both the artifact checksum and the canonical block-state stream checksum.
     */
    public WrittenTileSnapshotV2 readStrict(
            Path file,
            UUID expectedWorldId,
            String expectedTileId,
            WorldAabbV2 expectedRegion,
            int maximumPaletteEntries,
            CancellationToken cancellation
    ) throws IOException {
        return readStrict(
                file,
                expectedWorldId,
                expectedTileId,
                expectedRegion,
                maximumPaletteEntries,
                cancellation,
                (index, state) -> { });
    }

    /**
     * Strict read-back that additionally streams every decoded block state to {@code sink} in
     * canonical X-fastest→Z→Y order (V2-6-08 rollback restore). Identical validation to
     * {@link #readStrict(Path, UUID, String, WorldAabbV2, int, CancellationToken)}.
     */
    public WrittenTileSnapshotV2 readStrict(
            Path file,
            UUID expectedWorldId,
            String expectedTileId,
            WorldAabbV2 expectedRegion,
            int maximumPaletteEntries,
            CancellationToken cancellation,
            SnapshotBlockStateSinkV2 sink
    ) throws IOException {
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(expectedWorldId, "expectedWorldId");
        Objects.requireNonNull(expectedTileId, "expectedTileId");
        Objects.requireNonNull(expectedRegion, "expectedRegion");
        Objects.requireNonNull(cancellation, "cancellation");
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.PATH_UNSAFE,
                    "snapshot file must be a regular non-symbolic file: " + file.getFileName());
        }
        MessageDigest artifactDigest = sha256();
        MessageDigest streamDigest = sha256();
        long fileBytes = Files.size(file);
        try (InputStream raw = Files.newInputStream(file, StandardOpenOption.READ);
                DigestInputStream digesting = new DigestInputStream(raw, artifactDigest);
                DataInputStream in = new DataInputStream(
                        new BufferedInputStream(digesting, STREAM_BUFFER_BYTES))) {
            requireEquals(MAGIC, in.readUTF(), "snapshot magic", expectedTileId);
            requireEquals(
                    PlacementSnapshotPlanV2.SNAPSHOT_FILE_FORMAT_VERSION,
                    in.readUTF(),
                    "snapshot file format version",
                    expectedTileId);
            requireEquals(expectedTileId, in.readUTF(), "snapshot tileId", expectedTileId);
            UUID worldId = new UUID(in.readLong(), in.readLong());
            if (!expectedWorldId.equals(worldId)) {
                throw corrupt("snapshot worldId mismatch", expectedTileId);
            }
            WorldAabbV2 region = new WorldAabbV2(
                    in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
            if (!expectedRegion.equals(region)) {
                throw corrupt("snapshot region mismatch", expectedTileId);
            }
            int paletteSize = in.readInt();
            if (paletteSize < 1 || paletteSize > maximumPaletteEntries) {
                throw new PlacementSnapshotExceptionV2(
                        PlacementSnapshotFailureCodeV2.PALETTE_BUDGET_EXCEEDED,
                        "snapshot palette size out of range for " + expectedTileId);
            }
            List<String> paletteOrder = new ArrayList<>(paletteSize);
            String previous = null;
            for (int i = 0; i < paletteSize; i++) {
                String state = in.readUTF();
                if (state.isEmpty() || state.length() > MAXIMUM_BLOCK_STATE_LENGTH
                        || (previous != null && previous.compareTo(state) >= 0)) {
                    throw corrupt("snapshot palette must be strictly sorted canonical states", expectedTileId);
                }
                paletteOrder.add(state);
                previous = state;
            }
            long blockCount = in.readLong();
            if (blockCount != expectedRegion.volumeBlocks()) {
                throw corrupt("snapshot blockCount must equal region volume", expectedTileId);
            }
            for (long index = 0; index < blockCount; index++) {
                if ((index % CANCEL_CHECK_INTERVAL_BLOCKS) == 0L) {
                    cancel(cancellation);
                }
                int paletteRef = readVarInt(in, expectedTileId);
                if (paletteRef < 0 || paletteRef >= paletteSize) {
                    throw corrupt("snapshot palette index out of range", expectedTileId);
                }
                String state = paletteOrder.get(paletteRef);
                updateStreamDigest(streamDigest, state);
                sink.accept(index, state);
            }
            if (in.read() != -1) {
                throw corrupt("snapshot file has trailing bytes", expectedTileId);
            }
        } catch (EOFException truncated) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_CORRUPT,
                    "snapshot file truncated: " + expectedTileId,
                    truncated);
        }
        return new WrittenTileSnapshotV2(
                fileBytes,
                expectedRegion.volumeBlocks(),
                0,
                hex(artifactDigest.digest()),
                hex(streamDigest.digest()));
    }

    /** Ordered consumer of decoded snapshot block states; index is the canonical stream offset. */
    @FunctionalInterface
    public interface SnapshotBlockStateSinkV2 {
        void accept(long index, String blockState) throws IOException;
    }

    private interface BlockStateSink {
        void accept(String blockState) throws IOException;
    }

    private long streamRegion(
            PlacementWorldGatewayV2 gateway,
            UUID worldId,
            WorldAabbV2 region,
            CancellationToken cancellation,
            BlockStateSink sink
    ) throws IOException {
        long[] count = {0L};
        long[] expectedCursor = {0L};
        gateway.streamRegionBlockStates(worldId, region, (x, y, z, blockState) -> {
            if ((count[0] % CANCEL_CHECK_INTERVAL_BLOCKS) == 0L) {
                cancel(cancellation);
            }
            if (blockState == null || blockState.isEmpty()
                    || blockState.length() > MAXIMUM_BLOCK_STATE_LENGTH) {
                throw new PlacementSnapshotExceptionV2(
                        PlacementSnapshotFailureCodeV2.GATEWAY_CONTRACT_VIOLATION,
                        "gateway emitted an invalid block state");
            }
            long cursor = canonicalOffset(region, x, y, z);
            if (cursor != expectedCursor[0]) {
                throw new PlacementSnapshotExceptionV2(
                        PlacementSnapshotFailureCodeV2.GATEWAY_CONTRACT_VIOLATION,
                        "gateway must stream X-fastest→Z→Y canonical order");
            }
            expectedCursor[0]++;
            count[0]++;
            sink.accept(blockState);
        });
        return count[0];
    }

    private static long canonicalOffset(WorldAabbV2 region, int x, int y, int z) {
        long width = (long) region.maxX() - region.minX() + 1L;
        long length = (long) region.maxZ() - region.minZ() + 1L;
        if (x < region.minX() || x > region.maxX()
                || y < region.minY() || y > region.maxY()
                || z < region.minZ() || z > region.maxZ()) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.GATEWAY_CONTRACT_VIOLATION,
                    "gateway streamed a position outside the requested region");
        }
        long dy = (long) y - region.minY();
        long dz = (long) z - region.minZ();
        long dx = (long) x - region.minX();
        return (dy * length + dz) * width + dx;
    }

    private static void requireVolume(long count, long volume, String tileId) {
        if (count != volume) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.GATEWAY_CONTRACT_VIOLATION,
                    "gateway visited " + count + " of " + volume + " blocks for " + tileId);
        }
    }

    private static void requireEquals(String expected, String actual, String field, String tileId) {
        if (!expected.equals(actual)) {
            throw corrupt(field + " mismatch", tileId);
        }
    }

    private static PlacementSnapshotExceptionV2 corrupt(String message, String tileId) {
        return new PlacementSnapshotExceptionV2(
                PlacementSnapshotFailureCodeV2.SNAPSHOT_CORRUPT, message + ": " + tileId);
    }

    private static void cancel(CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.CANCELLED, "snapshot-all was cancelled");
        }
    }

    private static void updateStreamDigest(MessageDigest digest, String blockState) {
        digest.update(blockState.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            out.writeByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.writeByte(remaining);
    }

    private static int readVarInt(DataInputStream in, String tileId) throws IOException {
        int value = 0;
        int shift = 0;
        while (true) {
            int read = in.readUnsignedByte();
            value |= (read & 0x7F) << shift;
            if ((read & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 28) {
                throw corrupt("snapshot VarInt too long", tileId);
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    /** Hard disk-budget guard: refuses to write past the admitted byte ceiling. */
    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final long limit;
        private final String tileId;
        private long written;

        private CountingOutputStream(OutputStream delegate, long limit, String tileId) {
            this.delegate = delegate;
            this.limit = limit;
            this.tileId = tileId;
        }

        long written() {
            return written;
        }

        @Override
        public void write(int value) throws IOException {
            admit(1L);
            delegate.write(value);
            written++;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            admit(length);
            delegate.write(buffer, offset, length);
            written += length;
        }

        private void admit(long additional) {
            if (written + additional > limit) {
                throw new PlacementSnapshotExceptionV2(
                        PlacementSnapshotFailureCodeV2.DISK_BUDGET_EXCEEDED,
                        "snapshot bytes exceed the reserved disk lease for " + tileId);
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
