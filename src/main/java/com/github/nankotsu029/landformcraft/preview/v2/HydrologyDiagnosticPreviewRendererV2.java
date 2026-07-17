package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyPreviewIndexV2;
import com.github.nankotsu029.landformcraft.validation.v2.hydrology.HydrologyValidationInputV2;

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

/** Writes and strictly read-backs one atomically published V2-3 hydrology diagnostic bundle. */
public final class HydrologyDiagnosticPreviewRendererV2 {
    public static final String PALETTE_ID = "hydrology-diagnostic-palette-v1";
    private static final long MAXIMUM_PNG_BYTES = 8L * 1024L * 1024L;

    private final HydrologyPreviewIndexCodecV2 indexCodec = new HydrologyPreviewIndexCodecV2();

    public HydrologyPreviewIndexV2 render(
            Path targetDirectory,
            String sourceBlueprintChecksum,
            HydrologyDiagnosticFieldsV2 fields,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(sourceBlueprintChecksum, "sourceBlueprintChecksum");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "target directory must have a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("hydrology preview target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-hydrology-preview-" + UUID.randomUUID());
        boolean committed = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            List<HydrologyPreviewIndexV2.Layer> layers = new ArrayList<>();
            for (HydrologyPreviewIndexV2.LayerId layerId : HydrologyPreviewIndexV2.LayerId.values()) {
                cancellationToken.throwIfCancellationRequested();
                Path file = staging.resolve(layerId.fileName());
                renderLayer(file, layerId, fields, cancellationToken);
                long size = Files.size(file);
                if (size < 1 || size > MAXIMUM_PNG_BYTES) {
                    throw new IOException("hydrology preview PNG exceeds byte budget: " + layerId.fileName());
                }
                layers.add(new HydrologyPreviewIndexV2.Layer(
                        layerId, 1, layerId.fileName(), layerId.semantic(), Sha256.file(file), size,
                        fields.width(), fields.length(), PALETTE_ID));
            }
            HydrologyPreviewIndexV2 index = indexCodec.seal(new HydrologyPreviewIndexV2(
                    HydrologyPreviewIndexV2.VERSION, sourceBlueprintChecksum, fields.width(), fields.length(), layers));
            indexCodec.write(staging.resolve(HydrologyPreviewIndexCodecV2.INDEX_FILE_NAME), index);
            indexCodec.readAndVerify(
                    staging.resolve(HydrologyPreviewIndexCodecV2.INDEX_FILE_NAME), staging, cancellationToken);
            // This is the final cancellation observation. The atomic directory move is the commit point.
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for hydrology preview publication", exception);
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
            HydrologyPreviewIndexV2.LayerId layerId,
            HydrologyDiagnosticFieldsV2 fields,
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

    private static int color(HydrologyPreviewIndexV2.LayerId layer, HydrologyDiagnosticFieldsV2 fields, int x, int z) {
        return switch (layer) {
            case BASIN_ID -> categorical(fields.basinId().valueAt(x, z), 0xff2c7fb8, 0xff7fcdbb);
            case FLOW_DIRECTION -> categorical(fields.flowDirection().valueAt(x, z), 0xff3182bd, 0xff9ecae1);
            case FLOW_ACCUMULATION -> elevation(fields, fields.flowAccumulation().valueAt(x, z));
            case REACH_GRAPH -> categorical(fields.reachGraph().valueAt(x, z), 0xff08519c, 0xff6baed6);
            case BED_ELEVATION -> elevation(fields, fields.bedElevation().valueAt(x, z));
            case WATER_SURFACE -> elevation(fields, fields.waterSurface().valueAt(x, z));
            case WATER_BODY -> categorical(fields.waterBody().valueAt(x, z), 0xff2171b5, 0xffc6dbef);
            case LAKE_RIM_SPILL -> categorical(fields.lakeRimSpill().valueAt(x, z), 0xff41b6c4, 0xfffd8d3c);
            case DELTA_DISTRIBUTARY -> categorical(fields.deltaDistributary().valueAt(x, z), 0xfffe9929, 0xfffec44f);
            case FJORD_THALWEG -> elevation(fields, fields.fjordThalweg().valueAt(x, z));
            case WATERFALL_ENVELOPE -> categorical(fields.waterfallEnvelope().valueAt(x, z), 0xff4292c6, 0xff084594);
            case CONSTRAINT_RESIDUAL -> fields.constraintResidual().valueAt(x, z) == 0 ? 0xff202020 : 0xffff1a1a;
        };
    }

    private static int categorical(int value, int first, int second) {
        if (value == HydrologyDiagnosticFieldsV2.NO_DATA || value == HydrologyValidationInputV2.NO_DATA) {
            return 0xffff00ff;
        }
        if (value == 0) {
            return 0xff202020;
        }
        return (value & 1) == 0 ? first : second;
    }

    private static int elevation(HydrologyDiagnosticFieldsV2 fields, int value) {
        if (value == HydrologyDiagnosticFieldsV2.NO_DATA || value == HydrologyValidationInputV2.NO_DATA) {
            return 0xffff00ff;
        }
        long range = (long) fields.maximumElevationMillionths() - fields.minimumElevationMillionths();
        if (range <= 0L) {
            return 0xff808080;
        }
        long relative = Math.max(0L, Math.min(range, (long) value - fields.minimumElevationMillionths()));
        int shade = (int) ((relative * 255L + range / 2L) / range);
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
