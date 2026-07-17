package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Writes the fixed V2-1 diagnostic layer set as one atomically published bundle. */
public final class ConstraintMapPreviewRendererV2 {
    public static final List<String> FILE_NAMES = List.of(
            "desired-land-water.png",
            "actual-land-water.png",
            "residual-land-water.png",
            "desired-height.png",
            "actual-height.png",
            "residual-height.png",
            "zone-label-map.png",
            "constraint-errors.png"
    );

    public List<Path> render(
            Path targetDirectory,
            ConstraintDiagnosticFieldsV2 fields,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "targetDirectory must have a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("constraint preview target already exists: " + target);
        }
        Path temporary = parent.resolve(".tmp-constraint-preview-" + UUID.randomUUID());
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(temporary);
            renderMask(temporary.resolve(FILE_NAMES.get(0)), fields, fields.desiredLandWater(), cancellationToken);
            cancellationToken.throwIfCancellationRequested();
            renderMask(temporary.resolve(FILE_NAMES.get(1)), fields, fields.actualLandWater(), cancellationToken);
            cancellationToken.throwIfCancellationRequested();
            renderMaskResidual(temporary.resolve(FILE_NAMES.get(2)), fields, fields.residualLandWater(), cancellationToken);
            cancellationToken.throwIfCancellationRequested();
            renderHeight(temporary.resolve(FILE_NAMES.get(3)), fields, fields.desiredHeight(), cancellationToken);
            cancellationToken.throwIfCancellationRequested();
            renderHeight(temporary.resolve(FILE_NAMES.get(4)), fields, fields.actualHeight(), cancellationToken);
            cancellationToken.throwIfCancellationRequested();
            renderHeightResidual(temporary.resolve(FILE_NAMES.get(5)), fields, fields.residualHeight(), cancellationToken);
            cancellationToken.throwIfCancellationRequested();
            renderZones(temporary.resolve(FILE_NAMES.get(6)), fields, fields.zoneLabelMap(), cancellationToken);
            cancellationToken.throwIfCancellationRequested();
            renderErrors(temporary.resolve(FILE_NAMES.get(7)), fields, fields.constraintErrors(), cancellationToken);
            cancellationToken.throwIfCancellationRequested();
            List<Path> publishedPaths = FILE_NAMES.stream().map(target::resolve).toList();
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic constraint preview publication is not supported", exception);
            }
            // The atomic move is the commit point. A cancellation arriving after it is treated as
            // completion so a canonical bundle is never briefly published and then best-effort deleted.
            return publishedPaths;
        } catch (IOException | RuntimeException exception) {
            throw exception;
        } finally {
            deleteTree(temporary);
        }
    }

    private static void renderMask(
            Path path,
            ConstraintDiagnosticFieldsV2 fields,
            ConstraintDiagnosticFieldsV2.IntField source,
            CancellationToken cancellationToken
    ) throws IOException {
        render(path, fields, source, cancellationToken, value -> switch (value) {
            case 0 -> 0xff245c9e;
            case 1 -> 0xffd9c989;
            case ConstraintDiagnosticFieldsV2.NO_DATA -> 0xffff00ff;
            default -> 0xffff2020;
        });
    }

    private static void renderMaskResidual(
            Path path,
            ConstraintDiagnosticFieldsV2 fields,
            ConstraintDiagnosticFieldsV2.IntField source,
            CancellationToken cancellationToken
    ) throws IOException {
        render(path, fields, source, cancellationToken, value -> {
            if (value == ConstraintDiagnosticFieldsV2.NO_DATA) return 0xffff00ff;
            if (value == 0) return 0xff202020;
            return value < 0 ? 0xff3182bd : 0xffde2d26;
        });
    }

    private static void renderHeight(
            Path path,
            ConstraintDiagnosticFieldsV2 fields,
            ConstraintDiagnosticFieldsV2.IntField source,
            CancellationToken cancellationToken
    ) throws IOException {
        long range = (long) fields.maximumHeightMillionths() - fields.minimumHeightMillionths();
        render(path, fields, source, cancellationToken, value -> {
            if (value == ConstraintDiagnosticFieldsV2.NO_DATA) return 0xffff00ff;
            long relative = Math.max(0L, Math.min(range, (long) value - fields.minimumHeightMillionths()));
            int shade = (int) ((relative * 255L + range / 2L) / range);
            return 0xff000000 | (shade << 16) | (shade << 8) | shade;
        });
    }

    private static void renderHeightResidual(
            Path path,
            ConstraintDiagnosticFieldsV2 fields,
            ConstraintDiagnosticFieldsV2.IntField source,
            CancellationToken cancellationToken
    ) throws IOException {
        long maximumMagnitude = scanMaximumMagnitude(fields, source, cancellationToken);
        render(path, fields, source, cancellationToken, value -> {
            if (value == ConstraintDiagnosticFieldsV2.NO_DATA) return 0xffff00ff;
            if (value == 0 || maximumMagnitude == 0) return 0xff202020;
            int intensity = (int) Math.min(255L,
                    (Math.abs((long) value) * 255L + maximumMagnitude / 2L) / maximumMagnitude);
            return value < 0
                    ? 0xff000000 | (intensity << 8) | 0xff
                    : 0xff000000 | (0xff << 16) | intensity;
        });
    }

    private static void renderZones(
            Path path,
            ConstraintDiagnosticFieldsV2 fields,
            ConstraintDiagnosticFieldsV2.IntField source,
            CancellationToken cancellationToken
    ) throws IOException {
        render(path, fields, source, cancellationToken, value -> {
            if (value == ConstraintDiagnosticFieldsV2.NO_DATA) return 0xffff00ff;
            int mixed = value * 0x9e3779b9;
            int red = 48 + ((mixed >>> 16) & 0x9f);
            int green = 48 + ((mixed >>> 8) & 0x9f);
            int blue = 48 + (mixed & 0x9f);
            return 0xff000000 | (red << 16) | (green << 8) | blue;
        });
    }

    private static void renderErrors(
            Path path,
            ConstraintDiagnosticFieldsV2 fields,
            ConstraintDiagnosticFieldsV2.IntField source,
            CancellationToken cancellationToken
    ) throws IOException {
        render(path, fields, source, cancellationToken, value -> {
            if (value == ConstraintDiagnosticFieldsV2.NO_DATA) return 0xffff00ff;
            return value == 0 ? 0xff202020 : 0xffff1a1a;
        });
    }

    private static long scanMaximumMagnitude(
            ConstraintDiagnosticFieldsV2 fields,
            ConstraintDiagnosticFieldsV2.IntField source,
            CancellationToken cancellationToken
    ) {
        long maximum = 0;
        for (int z = 0; z < fields.length(); z++) {
            if ((z & 31) == 0) cancellationToken.throwIfCancellationRequested();
            for (int x = 0; x < fields.width(); x++) {
                int value = source.valueAt(x, z);
                if (value != ConstraintDiagnosticFieldsV2.NO_DATA) {
                    maximum = Math.max(maximum, Math.abs((long) value));
                }
            }
        }
        return maximum;
    }

    private static void render(
            Path path,
            ConstraintDiagnosticFieldsV2 fields,
            ConstraintDiagnosticFieldsV2.IntField source,
            CancellationToken cancellationToken,
            ColorMapper colorMapper
    ) throws IOException {
        BufferedImage image = new BufferedImage(fields.width(), fields.length(), BufferedImage.TYPE_INT_ARGB);
        try {
            for (int z = 0; z < fields.length(); z++) {
                if ((z & 31) == 0) cancellationToken.throwIfCancellationRequested();
                for (int x = 0; x < fields.width(); x++) {
                    image.setRGB(x, z, colorMapper.color(source.valueAt(x, z)));
                }
            }
            if (!ImageIO.write(image, "png", path.toFile())) {
                throw new IOException("PNG writer is unavailable");
            }
        } finally {
            image.flush();
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    private interface ColorMapper {
        int color(int value);
    }
}
