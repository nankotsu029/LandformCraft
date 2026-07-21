package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskDraftV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskDraftPreviewIndexV2;

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
 * Renders class / confidence / unknown diagnostic PNGs one layer at a time and atomically
 * publishes a strict preview index for an extracted mask draft.
 */
public final class ExtractedMaskDraftPreviewRendererV2 {
    public static final String PALETTE_ID = ExtractedMaskDraftPreviewIndexV2.Layer.PALETTE_ID;
    private static final long MAXIMUM_PNG_BYTES = 8L * 1024L * 1024L;

    private static final int COLOR_WATER = 0xff245c9e;
    private static final int COLOR_LAND = 0xffd9c989;
    private static final int COLOR_UNKNOWN = 0xffff00ff;
    private static final int COLOR_BACKGROUND = 0xff202020;
    private static final int COLOR_UNKNOWN_MASK = 0xffffffff;

    private final ExtractedMaskDraftPreviewIndexCodecV2 indexCodec = new ExtractedMaskDraftPreviewIndexCodecV2();

    public ExtractedMaskDraftPreviewIndexV2 render(
            Path targetDirectory,
            ExtractedMaskDraftV2 draft,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "target directory must have a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("extracted mask draft preview target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-extracted-mask-draft-preview-" + UUID.randomUUID());
        boolean committed = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            List<ExtractedMaskDraftPreviewIndexV2.Layer> layers = new ArrayList<>();
            for (ExtractedMaskDraftPreviewIndexV2.LayerId layerId
                    : ExtractedMaskDraftPreviewIndexV2.LayerId.values()) {
                // One layer at a time: render, write, checksum, then release the image.
                cancellationToken.throwIfCancellationRequested();
                Path file = staging.resolve(layerId.fileName());
                renderLayer(file, layerId, draft, cancellationToken);
                long size = Files.size(file);
                if (size < 1 || size > MAXIMUM_PNG_BYTES) {
                    throw new IOException(
                            "extracted mask draft preview PNG exceeds byte budget: " + layerId.fileName());
                }
                layers.add(new ExtractedMaskDraftPreviewIndexV2.Layer(
                        layerId,
                        1,
                        layerId.fileName(),
                        layerId.semantic(),
                        Sha256.file(file),
                        size,
                        draft.width(),
                        draft.length(),
                        PALETTE_ID));
            }
            ExtractedMaskDraftPreviewIndexV2 index = indexCodec.seal(new ExtractedMaskDraftPreviewIndexV2(
                    ExtractedMaskDraftPreviewIndexV2.VERSION,
                    draft.semanticChecksum(),
                    draft.width(),
                    draft.length(),
                    layers));
            indexCodec.write(staging.resolve(ExtractedMaskDraftPreviewIndexCodecV2.INDEX_FILE_NAME), index);
            indexCodec.readAndVerify(
                    staging.resolve(ExtractedMaskDraftPreviewIndexCodecV2.INDEX_FILE_NAME),
                    staging,
                    cancellationToken);
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "atomic move is required for extracted mask draft preview publication", exception);
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
            ExtractedMaskDraftPreviewIndexV2.LayerId layerId,
            ExtractedMaskDraftV2 draft,
            CancellationToken cancellationToken
    ) throws IOException {
        BufferedImage image = new BufferedImage(draft.width(), draft.length(), BufferedImage.TYPE_INT_ARGB);
        try {
            for (int z = 0; z < draft.length(); z++) {
                if ((z & 31) == 0) {
                    cancellationToken.throwIfCancellationRequested();
                }
                for (int x = 0; x < draft.width(); x++) {
                    image.setRGB(x, z, color(layerId, draft, x, z));
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
            ExtractedMaskDraftPreviewIndexV2.LayerId layer,
            ExtractedMaskDraftV2 draft,
            int x,
            int z
    ) {
        return switch (layer) {
            case CLASS -> switch (draft.classAt(x, z)) {
                case ExtractedMaskDraftV2.CLASS_WATER -> COLOR_WATER;
                case ExtractedMaskDraftV2.CLASS_LAND -> COLOR_LAND;
                default -> COLOR_UNKNOWN;
            };
            case CONFIDENCE -> {
                int confidence = draft.confidenceAt(x, z);
                yield 0xff000000 | (confidence << 16) | (confidence << 8) | confidence;
            }
            case UNKNOWN -> draft.classAt(x, z) == ExtractedMaskDraftV2.CLASS_UNKNOWN
                    ? COLOR_UNKNOWN_MASK
                    : COLOR_BACKGROUND;
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
