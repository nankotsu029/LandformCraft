package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict bounded V2-5-15 volume diagnostic preview index; not a Release manifest. */
public record VolumePreviewIndexV2(
        int previewIndexVersion,
        String sourcePlanChecksum,
        int width,
        int length,
        List<Layer> layers,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);

    public VolumePreviewIndexV2 {
        if (previewIndexVersion != VERSION) {
            throw new IllegalArgumentException("volume preview index version must be 1");
        }
        sourcePlanChecksum = V2Validation.checksum(sourcePlanChecksum, "sourcePlanChecksum");
        if (width < 1 || width > 256 || length < 1 || length > 256) {
            throw new IllegalArgumentException("volume preview dimensions must be within 1..256");
        }
        layers = V2Validation.sorted(layers, "layers", LayerId.values().length,
                Comparator.comparing(Layer::layerId));
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
        Set<LayerId> ids = new HashSet<>();
        for (Layer layer : layers) {
            if (!ids.add(layer.layerId()) || layer.width() != width || layer.length() != length) {
                throw new IllegalArgumentException(
                        "volume preview layers must be unique and share index dimensions");
            }
            if (!layer.path().equals(layer.layerId().fileName())) {
                throw new IllegalArgumentException("volume preview layer path does not match its fixed layer ID");
            }
        }
        if (!ids.equals(Set.of(LayerId.values()))) {
            throw new IllegalArgumentException("volume preview index must contain the complete fixed layer set");
        }
    }

    public VolumePreviewIndexV2(
            int previewIndexVersion,
            String sourcePlanChecksum,
            int width,
            int length,
            List<Layer> layers
    ) {
        this(previewIndexVersion, sourcePlanChecksum, width, length, layers, PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public VolumePreviewIndexV2 withCanonicalChecksum(String checksum) {
        return new VolumePreviewIndexV2(
                previewIndexVersion, sourcePlanChecksum, width, length, layers, checksum);
    }

    public enum LayerId {
        AABB_FOOTPRINT("aabb-footprint.png", "volume.aabb.footprint"),
        OPERATOR_ORDINAL("operator-ordinal.png", "volume.csg.operator"),
        Y_SLICE("y-slice.png", "volume.slice.y"),
        SOLID_FLUID("solid-fluid.png", "volume.occupancy.solid-fluid"),
        SURFACE_CLASS("surface-class.png", "volume.surface.class");

        private final String fileName;
        private final String semantic;

        LayerId(String fileName, String semantic) {
            this.fileName = fileName;
            this.semantic = semantic;
        }

        public String fileName() {
            return fileName;
        }

        public String semantic() {
            return semantic;
        }
    }

    public record Layer(
            LayerId layerId,
            int layerVersion,
            String path,
            String semantic,
            String sha256,
            long byteLength,
            int width,
            int length,
            String paletteId
    ) {
        public static final String PALETTE_ID = "volume-diagnostic-palette-v1";

        public Layer {
            Objects.requireNonNull(layerId, "layerId");
            if (layerVersion != 1) {
                throw new IllegalArgumentException("volume preview layerVersion must be 1");
            }
            path = V2Validation.nonBlank(path, "path", 80);
            if (!path.matches("^[a-z0-9][a-z0-9-]{0,63}\\.png$")) {
                throw new IllegalArgumentException("volume preview path must be one portable PNG filename");
            }
            semantic = V2Validation.qualifiedId(semantic, "semantic");
            if (!semantic.equals(layerId.semantic())) {
                throw new IllegalArgumentException("volume preview semantic does not match layer ID");
            }
            sha256 = V2Validation.checksum(sha256, "sha256");
            if (byteLength < 1 || byteLength > 8L * 1024L * 1024L) {
                throw new IllegalArgumentException("volume preview layer byteLength out of range");
            }
            if (width < 1 || length < 1 || width > 256 || length > 256) {
                throw new IllegalArgumentException("volume preview layer dimensions invalid");
            }
            paletteId = V2Validation.nonBlank(paletteId, "paletteId", 64);
            if (!PALETTE_ID.equals(paletteId)) {
                throw new IllegalArgumentException("unknown volume preview palette");
            }
        }
    }
}
