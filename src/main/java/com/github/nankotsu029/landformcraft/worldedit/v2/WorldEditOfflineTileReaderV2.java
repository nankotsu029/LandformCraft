package com.github.nankotsu029.landformcraft.worldedit.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.tile.CanonicalBlockStreamV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.SpongeV3TileInspectorV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.math.BlockVector3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.Objects;

/** Version-separated WorldEdit 7.3.19 offline read-back for a strict V2 tile artifact. */
public final class WorldEditOfflineTileReaderV2 {
    public VerifiedTile verify(
            Path artifactRoot,
            OfflineTileArtifactV2 artifact,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(artifactRoot, "artifactRoot");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Path root = artifactRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("offline tile root must be a non-symbolic directory");
        }
        Path file = resolveSafe(root, artifact.schematicPath());
        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("offline tile path is missing, unsafe, or escapes its root");
        }
        cancellationToken.throwIfCancellationRequested();
        long size = Files.size(file);
        if (size != artifact.byteLength() || size > OfflineTileArtifactV2.MAXIMUM_ARTIFACT_BYTES) {
            throw new IOException("offline tile byte length differs from its metadata");
        }
        byte[] bytes;
        try (SeekableByteChannel channel = Files.newByteChannel(
                file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             InputStream input = Channels.newInputStream(channel)) {
            bytes = input.readNBytes(Math.toIntExact(OfflineTileArtifactV2.MAXIMUM_ARTIFACT_BYTES + 1L));
        }
        if (bytes.length != size || !Sha256.bytes(bytes).equals(artifact.artifactChecksum())) {
            throw new IOException("offline tile artifact checksum mismatch");
        }
        SpongeV3TileInspectorV2.Inspection inspection =
                new SpongeV3TileInspectorV2().inspect(bytes, artifact.tilePlan());
        if (inspection.paletteSize() != artifact.paletteSize()
                || inspection.blockCount() != artifact.blockCount()
                || !inspection.semanticChecksum().equals(artifact.semanticChecksum())) {
            throw new IOException("offline tile strict read-back differs from its metadata");
        }
        cancellationToken.throwIfCancellationRequested();
        ClipboardFormat format = ClipboardFormats.findByFile(file.toFile());
        if (format == null) throw new IOException("WorldEdit 7.3.19 did not recognize Sponge v3 tile format");
        Clipboard clipboard;
        try (InputStream input = new ByteArrayInputStream(bytes); var reader = format.getReader(input)) {
            clipboard = reader.read();
        } catch (RuntimeException exception) {
            throw new IOException("WorldEdit 7.3.19 rejected the offline tile", exception);
        }
        BlockVector3 dimensions = clipboard.getDimensions();
        if (dimensions.x() != artifact.width() || dimensions.y() != artifact.maxY() - artifact.minY() + 1
                || dimensions.z() != artifact.length()
                || !clipboard.getMinimumPoint().equals(BlockVector3.ZERO)
                || !clipboard.getOrigin().equals(BlockVector3.ZERO)) {
            throw new IOException("WorldEdit read-back dimensions or origin differ from tile metadata");
        }
        String worldEditSemantic = CanonicalBlockStreamV2.checksum(
                artifact.tilePlan(),
                (x, y, z) -> clipboard.getBlock(BlockVector3.at(
                        x - artifact.originX(), y - artifact.minY(), z - artifact.originZ()))
                        .toImmutableState().getAsString(),
                cancellationToken);
        if (!worldEditSemantic.equals(artifact.semanticChecksum())) {
            throw new IOException("WorldEdit read-back canonical block checksum mismatch");
        }
        cancellationToken.throwIfCancellationRequested();
        return new VerifiedTile(artifact, inspection, worldEditSemantic);
    }

    private static Path resolveSafe(Path root, String relative) throws IOException {
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root)) throw new IOException("offline tile path escapes its root");
        Path current = root;
        for (Path segment : Path.of(relative)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("offline tile path contains a symbolic link");
            }
        }
        return file;
    }

    public record VerifiedTile(
            OfflineTileArtifactV2 artifact,
            SpongeV3TileInspectorV2.Inspection inspection,
            String worldEditSemanticChecksum
    ) {
        public VerifiedTile {
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(inspection, "inspection");
            Objects.requireNonNull(worldEditSemanticChecksum, "worldEditSemanticChecksum");
        }
    }
}
