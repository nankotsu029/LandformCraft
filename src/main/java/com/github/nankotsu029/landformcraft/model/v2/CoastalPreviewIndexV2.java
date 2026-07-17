package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict, bounded V2-2 coastal diagnostic preview index; it is not a Release manifest. */
public record CoastalPreviewIndexV2(
        int previewIndexVersion,
        String sourceBlueprintChecksum,
        int width,
        int length,
        List<Layer> layers,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);

    public CoastalPreviewIndexV2 {
        if (previewIndexVersion != VERSION) {
            throw new IllegalArgumentException("coastal preview index version must be 1");
        }
        sourceBlueprintChecksum = V2Validation.checksum(sourceBlueprintChecksum, "sourceBlueprintChecksum");
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000) {
            throw new IllegalArgumentException("coastal preview dimensions must be within 1..1000");
        }
        layers = V2Validation.sorted(layers, "layers", LayerId.values().length,
                Comparator.comparing(Layer::layerId));
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
        Set<LayerId> ids = new HashSet<>();
        for (Layer layer : layers) {
            if (!ids.add(layer.layerId()) || layer.width() != width || layer.length() != length) {
                throw new IllegalArgumentException("coastal preview layers must be unique and share index dimensions");
            }
            if (!layer.path().equals(layer.layerId().fileName())) {
                throw new IllegalArgumentException("coastal preview layer path does not match its fixed layer ID");
            }
        }
        if (!ids.equals(Set.of(LayerId.values()))) {
            throw new IllegalArgumentException("coastal preview index must contain the complete fixed layer set");
        }
    }

    public CoastalPreviewIndexV2(
            int previewIndexVersion,
            String sourceBlueprintChecksum,
            int width,
            int length,
            List<Layer> layers
    ) {
        this(previewIndexVersion, sourceBlueprintChecksum, width, length, layers, PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public CoastalPreviewIndexV2 withCanonicalChecksum(String checksum) {
        return new CoastalPreviewIndexV2(
                previewIndexVersion, sourceBlueprintChecksum, width, length, layers, checksum);
    }

    public enum LayerId {
        BEACH_OVERLAY("beach-overlay.png", "coastal.beach.overlay"),
        HARBOR_OVERLAY("harbor-overlay.png", "coastal.harbor.overlay"),
        BREAKWATER_OVERLAY("breakwater-overlay.png", "coastal.breakwater.overlay"),
        CAPE_OVERLAY("cape-overlay.png", "coastal.cape.overlay"),
        DESIRED_LAND_WATER("desired-land-water.png", "coastal.desired.land-water"),
        ACTUAL_LAND_WATER("actual-land-water.png", "coastal.actual.land-water"),
        RESIDUAL_LAND_WATER("residual-land-water.png", "coastal.residual.land-water"),
        DESIRED_HEIGHT("desired-height.png", "coastal.desired.height"),
        ACTUAL_HEIGHT("actual-height.png", "coastal.actual.height"),
        RESIDUAL_HEIGHT("residual-height.png", "coastal.residual.height"),
        CONSTRAINT_ERRORS("constraint-errors.png", "coastal.constraint-errors");

        private final String fileName;
        private final String semantic;

        LayerId(String fileName, String semantic) {
            this.fileName = fileName;
            this.semantic = semantic;
        }

        public String fileName() { return fileName; }
        public String semantic() { return semantic; }
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
        public Layer {
            Objects.requireNonNull(layerId, "layerId");
            if (layerVersion != 1) throw new IllegalArgumentException("coastal preview layer version must be 1");
            path = V2Validation.nonBlank(path, "path", 64);
            if (!path.matches("^[a-z0-9][a-z0-9-]{0,63}\\.png$")) {
                throw new IllegalArgumentException("coastal preview path must be one portable PNG filename");
            }
            semantic = V2Validation.qualifiedId(semantic, "semantic");
            if (!semantic.equals(layerId.semantic())) {
                throw new IllegalArgumentException("coastal preview semantic does not match layer ID");
            }
            sha256 = V2Validation.checksum(sha256, "sha256");
            if (byteLength < 1 || byteLength > 8L * 1024L * 1024L
                    || width < 1 || width > 1_000 || length < 1 || length > 1_000) {
                throw new IllegalArgumentException("coastal preview layer exceeds fixed budget");
            }
            paletteId = V2Validation.qualifiedId(paletteId, "paletteId");
            if (!"coastal-diagnostic-palette-v1".equals(paletteId)) {
                throw new IllegalArgumentException("coastal preview palette is unsupported");
            }
        }
    }
}
