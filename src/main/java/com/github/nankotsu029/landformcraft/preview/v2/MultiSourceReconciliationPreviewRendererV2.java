package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.MultiSourceReconciliationServiceV2;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceConflictCodeV2;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceReconciliationPreviewIndexV2;

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

/**
 * Renders result / conflict / source-diff diagnostic PNGs one layer at a time for a reconcile
 * result and atomically publishes a strict preview index.
 */
public final class MultiSourceReconciliationPreviewRendererV2 {
    private static final long MAXIMUM_PNG_BYTES = 8L * 1024L * 1024L;
    private static final int COLOR_BG = 0xff202020;
    private static final int COLOR_WATER = 0xff245c9e;
    private static final int COLOR_LAND = 0xffd9c989;
    private static final int COLOR_OTHER = 0xff888888;
    private static final int COLOR_HARD = 0xffff2020;
    private static final int COLOR_SOFT_PEER = 0xffffcc00;
    private static final int COLOR_OK = 0xff206020;

    private final MultiSourceReconciliationPreviewIndexCodecV2 indexCodec =
            new MultiSourceReconciliationPreviewIndexCodecV2();

    public MultiSourceReconciliationPreviewIndexV2 render(
            Path targetDirectory,
            int width,
            int length,
            int resultNoDataSample,
            MultiSourceReconciliationServiceV2.ReconcileResult reconcile,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(reconcile, "reconcile");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "preview target requires a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("multi-source preview target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-multisource-preview-" + UUID.randomUUID());
        boolean committed = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            List<MultiSourceReconciliationPreviewIndexV2.Layer> layers = new ArrayList<>();
            for (MultiSourceReconciliationPreviewIndexV2.LayerId layerId
                    : MultiSourceReconciliationPreviewIndexV2.LayerId.values()) {
                cancellationToken.throwIfCancellationRequested();
                Path file = staging.resolve(layerId.fileName());
                renderLayer(file, layerId, width, length, resultNoDataSample, reconcile, cancellationToken);
                long size = Files.size(file);
                if (size < 1 || size > MAXIMUM_PNG_BYTES) {
                    throw new IOException("preview PNG exceeds byte budget: " + layerId.fileName());
                }
                layers.add(new MultiSourceReconciliationPreviewIndexV2.Layer(
                        layerId,
                        1,
                        layerId.fileName(),
                        layerId.semantic(),
                        Sha256.file(file),
                        size,
                        width,
                        length,
                        MultiSourceReconciliationPreviewIndexV2.Layer.PALETTE_ID));
            }
            MultiSourceReconciliationPreviewIndexV2 index = indexCodec.seal(
                    new MultiSourceReconciliationPreviewIndexV2(
                            MultiSourceReconciliationPreviewIndexV2.VERSION,
                            reconcile.semanticChecksum(),
                            width,
                            length,
                            layers));
            indexCodec.write(
                    staging.resolve(MultiSourceReconciliationPreviewIndexCodecV2.INDEX_FILE_NAME), index);
            indexCodec.readAndVerify(
                    staging.resolve(MultiSourceReconciliationPreviewIndexCodecV2.INDEX_FILE_NAME),
                    staging,
                    cancellationToken);
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for multi-source preview", exception);
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
            MultiSourceReconciliationPreviewIndexV2.LayerId layerId,
            int width,
            int length,
            int resultNoDataSample,
            MultiSourceReconciliationServiceV2.ReconcileResult reconcile,
            CancellationToken cancellationToken
    ) throws IOException {
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_ARGB);
        try {
            for (int z = 0; z < length; z++) {
                if ((z & 31) == 0) {
                    cancellationToken.throwIfCancellationRequested();
                }
                for (int x = 0; x < width; x++) {
                    int index = z * width + x;
                    image.setRGB(x, z, color(layerId, reconcile, index, resultNoDataSample));
                }
            }
            if (!ImageIO.write(image, "PNG", path.toFile())) {
                throw new IOException("failed to write preview PNG: " + path.getFileName());
            }
        } finally {
            image.flush();
        }
    }

    private static int color(
            MultiSourceReconciliationPreviewIndexV2.LayerId layerId,
            MultiSourceReconciliationServiceV2.ReconcileResult reconcile,
            int index,
            int resultNoDataSample
    ) {
        return switch (layerId) {
            case RESULT -> {
                int sample = Byte.toUnsignedInt(reconcile.result()[index]);
                if (sample == resultNoDataSample) {
                    yield COLOR_BG;
                }
                yield switch (sample) {
                    case 0 -> COLOR_WATER;
                    case 1 -> COLOR_LAND;
                    default -> COLOR_OTHER;
                };
            }
            case CONFLICT -> switch (MultiSourceConflictCodeV2.fromCode(
                    Byte.toUnsignedInt(reconcile.conflict()[index]))) {
                case NONE -> COLOR_OK;
                case HARD_CONFLICT -> COLOR_HARD;
                case SOFT_PEER_CONFLICT -> COLOR_SOFT_PEER;
            };
            case SOURCE_DIFF -> {
                int diff = Byte.toUnsignedInt(reconcile.sourceDiff()[index]);
                if (diff == 0) {
                    yield COLOR_OK;
                }
                if (diff == 255) {
                    yield COLOR_HARD;
                }
                int gray = Math.min(255, 40 + diff);
                yield 0xff000000 | (gray << 16) | (gray << 8) | 0x20;
            }
        };
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
