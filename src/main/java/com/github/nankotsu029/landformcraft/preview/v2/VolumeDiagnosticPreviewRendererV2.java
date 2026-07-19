package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.VolumePreviewIndexV2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Writes and strictly read-backs one atomically published V2-5-15 volume diagnostic bundle. */
public final class VolumeDiagnosticPreviewRendererV2 {
    public static final String PALETTE_ID = "volume-diagnostic-palette-v1";
    private static final long MAXIMUM_PNG_BYTES = 8L * 1024L * 1024L;

    private final VolumePreviewIndexCodecV2 indexCodec = new VolumePreviewIndexCodecV2();

    public VolumePreviewIndexV2 render(
            Path targetDirectory,
            String sourcePlanChecksum,
            VolumeDiagnosticFieldsV2 fields,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(sourcePlanChecksum, "sourcePlanChecksum");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "target directory must have a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("volume preview target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-volume-preview-" + UUID.randomUUID());
        boolean committed = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            List<VolumePreviewIndexV2.Layer> layers = new ArrayList<>();
            for (VolumePreviewIndexV2.LayerId layerId : VolumePreviewIndexV2.LayerId.values()) {
                cancellationToken.throwIfCancellationRequested();
                Path file = staging.resolve(layerId.fileName());
                renderLayer(file, layerId, fields, cancellationToken);
                long size = Files.size(file);
                if (size < 1 || size > MAXIMUM_PNG_BYTES) {
                    throw new IOException("volume preview PNG exceeds byte budget: " + layerId.fileName());
                }
                layers.add(new VolumePreviewIndexV2.Layer(
                        layerId, 1, layerId.fileName(), layerId.semantic(), Sha256.file(file), size,
                        fields.width(), fields.length(), PALETTE_ID));
            }
            VolumePreviewIndexV2 index = indexCodec.seal(new VolumePreviewIndexV2(
                    VolumePreviewIndexV2.VERSION, sourcePlanChecksum, fields.width(), fields.length(), layers));
            indexCodec.write(staging.resolve(VolumePreviewIndexCodecV2.INDEX_FILE_NAME), index);
            indexCodec.readAndVerify(
                    staging.resolve(VolumePreviewIndexCodecV2.INDEX_FILE_NAME), staging, cancellationToken);
            // This is the final cancellation observation. The atomic directory move is the commit point.
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for volume preview publication", exception);
            }
            committed = true;
            return index;
        } finally {
            if (!committed) {
                deleteTree(staging);
            }
        }
    }

    private static void renderLayer(
            Path path,
            VolumePreviewIndexV2.LayerId layerId,
            VolumeDiagnosticFieldsV2 fields,
            CancellationToken cancellationToken
    ) throws IOException {
        BufferedImage image = new BufferedImage(fields.width(), fields.length(), BufferedImage.TYPE_INT_ARGB);
        try {
            for (int z = 0; z < fields.length(); z++) {
                if ((z & 31) == 0) {
                    cancellationToken.throwIfCancellationRequested();
                }
                for (int x = 0; x < fields.width(); x++) {
                    image.setRGB(x, z, color(layerId, fields, x, z));
                }
            }
            if (!ImageIO.write(image, "png", path.toFile())) {
                throw new IOException("PNG writer is unavailable");
            }
        } finally {
            image.flush();
        }
    }

    private static int color(
            VolumePreviewIndexV2.LayerId layer,
            VolumeDiagnosticFieldsV2 fields,
            int x,
            int z
    ) {
        return switch (layer) {
            case AABB_FOOTPRINT -> categorical(fields.aabbFootprint().valueAt(x, z), 0xff3182bd, 0xff9ecae1);
            case OPERATOR_ORDINAL -> categorical(fields.operatorOrdinal().valueAt(x, z), 0xff756bb1, 0xffbcbddc);
            case Y_SLICE -> categorical(fields.ySlice().valueAt(x, z), 0xff31a354, 0xffa1d99b);
            case SOLID_FLUID -> solidFluid(fields.solidFluid().valueAt(x, z));
            case SURFACE_CLASS -> categorical(fields.surfaceClass().valueAt(x, z), 0xffe6550d, 0xfffdae6b);
        };
    }

    private static int solidFluid(int code) {
        return switch (code) {
            case 1 -> 0xff636363;
            case 2 -> 0xff3182bd;
            case 3 -> 0xffff1a1a;
            default -> 0xff202020;
        };
    }

    private static int categorical(int value, int first, int second) {
        if (value == 0) {
            return 0xff202020;
        }
        return (value & 1) == 0 ? first : second;
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
