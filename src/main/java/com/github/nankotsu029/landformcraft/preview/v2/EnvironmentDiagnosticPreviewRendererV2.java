package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.EnvironmentPreviewIndexV2;

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

/** Writes and strictly read-backs one atomically published V2-4-13 environment diagnostic bundle. */
public final class EnvironmentDiagnosticPreviewRendererV2 {
    public static final String PALETTE_ID = "environment-diagnostic-palette-v1";
    private static final long MAXIMUM_PNG_BYTES = 8L * 1024L * 1024L;

    private final EnvironmentPreviewIndexCodecV2 indexCodec = new EnvironmentPreviewIndexCodecV2();

    public EnvironmentPreviewIndexV2 render(
            Path targetDirectory,
            String sourcePlanChecksum,
            EnvironmentDiagnosticFieldsV2 fields,
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
            throw new IOException("environment preview target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-environment-preview-" + UUID.randomUUID());
        boolean committed = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            List<EnvironmentPreviewIndexV2.Layer> layers = new ArrayList<>();
            for (EnvironmentPreviewIndexV2.LayerId layerId : EnvironmentPreviewIndexV2.LayerId.values()) {
                cancellationToken.throwIfCancellationRequested();
                Path file = staging.resolve(layerId.fileName());
                renderLayer(file, layerId, fields, cancellationToken);
                long size = Files.size(file);
                if (size < 1 || size > MAXIMUM_PNG_BYTES) {
                    throw new IOException("environment preview PNG exceeds byte budget: " + layerId.fileName());
                }
                layers.add(new EnvironmentPreviewIndexV2.Layer(
                        layerId, 1, layerId.fileName(), layerId.semantic(), Sha256.file(file), size,
                        fields.width(), fields.length(), PALETTE_ID));
            }
            EnvironmentPreviewIndexV2 index = indexCodec.seal(new EnvironmentPreviewIndexV2(
                    EnvironmentPreviewIndexV2.VERSION, sourcePlanChecksum, fields.width(), fields.length(), layers));
            indexCodec.write(staging.resolve(EnvironmentPreviewIndexCodecV2.INDEX_FILE_NAME), index);
            indexCodec.readAndVerify(
                    staging.resolve(EnvironmentPreviewIndexCodecV2.INDEX_FILE_NAME), staging, cancellationToken);
            // This is the final cancellation observation. The atomic directory move is the commit point.
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for environment preview publication", exception);
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
            EnvironmentPreviewIndexV2.LayerId layerId,
            EnvironmentDiagnosticFieldsV2 fields,
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
            EnvironmentPreviewIndexV2.LayerId layer,
            EnvironmentDiagnosticFieldsV2 fields,
            int x,
            int z
    ) {
        return switch (layer) {
            case TEMPERATURE -> continuous(fields.temperature().valueAt(x, z));
            case MOISTURE -> continuous(fields.moisture().valueAt(x, z));
            case WETNESS -> continuous(fields.wetness().valueAt(x, z));
            case SALINITY -> continuous(fields.salinity().valueAt(x, z));
            case HYDROPERIOD -> continuous(fields.hydroperiod().valueAt(x, z));
            case SNOW_COVER -> continuous(fields.snowCover().valueAt(x, z));
            case HABITAT -> categorical(fields.habitat().valueAt(x, z), 0xff2ca25f, 0xff99d8c9);
            case MATERIAL_PROFILE -> categorical(fields.materialProfile().valueAt(x, z), 0xff8c6bb1, 0xff9ebcda);
            case FEATURE_MATERIAL -> categorical(fields.featureMaterial().valueAt(x, z), 0xffe6550d, 0xfffdae6b);
            case CONSTRAINT_ERROR -> fields.constraintError().valueAt(x, z) == 0 ? 0xff202020 : 0xffff1a1a;
        };
    }

    private static int categorical(int value, int first, int second) {
        if (value == 0) {
            return 0xff202020;
        }
        return (value & 1) == 0 ? first : second;
    }

    private static int continuous(int raw) {
        int clamped = Math.max(0, Math.min(1_000, raw));
        int shade = (clamped * 255 + 500) / 1_000;
        return 0xff000000 | (shade << 16) | (shade << 8) | shade;
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
