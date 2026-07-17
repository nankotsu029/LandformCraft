package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.CoastalPreviewIndexV2;

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

/** Writes and strictly read-backs one atomically published V2-2 coastal diagnostic bundle. */
public final class CoastalDiagnosticPreviewRendererV2 {
    public static final String PALETTE_ID = "coastal-diagnostic-palette-v1";
    private static final long MAXIMUM_PNG_BYTES = 8L * 1024L * 1024L;

    private final CoastalPreviewIndexCodecV2 indexCodec = new CoastalPreviewIndexCodecV2();

    public CoastalPreviewIndexV2 render(
            Path targetDirectory,
            String sourceBlueprintChecksum,
            CoastalDiagnosticFieldsV2 fields,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(sourceBlueprintChecksum, "sourceBlueprintChecksum");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "target directory must have a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) throw new IOException("coastal preview target already exists: " + target);
        Path staging = parent.resolve(".tmp-coastal-preview-" + UUID.randomUUID());
        boolean committed = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            List<CoastalPreviewIndexV2.Layer> layers = new ArrayList<>();
            for (CoastalPreviewIndexV2.LayerId layerId : CoastalPreviewIndexV2.LayerId.values()) {
                cancellationToken.throwIfCancellationRequested();
                Path file = staging.resolve(layerId.fileName());
                renderLayer(file, layerId, fields, cancellationToken);
                long size = Files.size(file);
                if (size < 1 || size > MAXIMUM_PNG_BYTES) {
                    throw new IOException("coastal preview PNG exceeds byte budget: " + layerId.fileName());
                }
                layers.add(new CoastalPreviewIndexV2.Layer(
                        layerId, 1, layerId.fileName(), layerId.semantic(), Sha256.file(file), size,
                        fields.width(), fields.length(), PALETTE_ID));
            }
            CoastalPreviewIndexV2 index = indexCodec.seal(new CoastalPreviewIndexV2(
                    CoastalPreviewIndexV2.VERSION, sourceBlueprintChecksum, fields.width(), fields.length(), layers));
            indexCodec.write(staging.resolve(CoastalPreviewIndexCodecV2.INDEX_FILE_NAME), index);
            indexCodec.readAndVerify(
                    staging.resolve(CoastalPreviewIndexCodecV2.INDEX_FILE_NAME), staging, cancellationToken);
            // This is the final cancellation observation. The atomic directory move is the commit point.
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for coastal preview publication", exception);
            }
            committed = true;
            return index;
        } finally {
            if (!committed) deleteTree(staging);
        }
    }

    private static void renderLayer(
            Path path,
            CoastalPreviewIndexV2.LayerId layerId,
            CoastalDiagnosticFieldsV2 fields,
            CancellationToken cancellationToken
    ) throws IOException {
        BufferedImage image = new BufferedImage(fields.width(), fields.length(), BufferedImage.TYPE_INT_ARGB);
        try {
            for (int z = 0; z < fields.length(); z++) {
                if ((z & 31) == 0) cancellationToken.throwIfCancellationRequested();
                for (int x = 0; x < fields.width(); x++) {
                    image.setRGB(x, z, color(layerId, fields, x, z));
                }
            }
            if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("PNG writer is unavailable");
        } finally {
            image.flush();
        }
    }

    private static int color(CoastalPreviewIndexV2.LayerId layer, CoastalDiagnosticFieldsV2 fields, int x, int z) {
        return switch (layer) {
            case BEACH_OVERLAY -> categorical(fields.beachOverlay().valueAt(x, z), 0xffe9cf87, 0xfff7e9b0);
            case HARBOR_OVERLAY -> categorical(fields.harborOverlay().valueAt(x, z), 0xff2c7fb8, 0xff7fcdbb);
            case BREAKWATER_OVERLAY -> categorical(fields.breakwaterOverlay().valueAt(x, z), 0xff6e6259, 0xffb0a79b);
            case CAPE_OVERLAY -> categorical(fields.capeOverlay().valueAt(x, z), 0xff4f5a52, 0xff95a16d);
            case DESIRED_LAND_WATER -> landWater(fields.desiredLandWater().valueAt(x, z));
            case ACTUAL_LAND_WATER -> landWater(fields.actualLandWater().valueAt(x, z));
            case RESIDUAL_LAND_WATER -> residual(fields.residualLandWater().valueAt(x, z));
            case DESIRED_HEIGHT -> height(fields, fields.desiredHeight().valueAt(x, z));
            case ACTUAL_HEIGHT -> height(fields, fields.actualHeight().valueAt(x, z));
            case RESIDUAL_HEIGHT -> residual(fields.residualHeight().valueAt(x, z));
            case CONSTRAINT_ERRORS -> fields.constraintErrors().valueAt(x, z) == 0 ? 0xff202020 : 0xffff1a1a;
        };
    }

    private static int categorical(int value, int first, int second) {
        if (value == CoastalDiagnosticFieldsV2.NO_DATA) return 0xffff00ff;
        if (value == 0) return 0xff202020;
        return (value & 1) == 0 ? first : second;
    }

    private static int landWater(int value) {
        if (value == CoastalDiagnosticFieldsV2.NO_DATA) return 0xffff00ff;
        if (value == 0) return 0xff245c9e;
        if (value == 1) return 0xffd9c989;
        return 0xffff2020;
    }

    private static int height(CoastalDiagnosticFieldsV2 fields, int value) {
        if (value == CoastalDiagnosticFieldsV2.NO_DATA) return 0xffff00ff;
        long range = (long) fields.maximumHeightMillionths() - fields.minimumHeightMillionths();
        long relative = Math.max(0L, Math.min(range, (long) value - fields.minimumHeightMillionths()));
        int shade = (int) ((relative * 255L + range / 2L) / range);
        return 0xff000000 | (shade << 16) | (shade << 8) | shade;
    }

    private static int residual(int value) {
        if (value == CoastalDiagnosticFieldsV2.NO_DATA) return 0xffff00ff;
        if (value == 0) return 0xff202020;
        return value < 0 ? 0xff3182bd : 0xffde2d26;
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
