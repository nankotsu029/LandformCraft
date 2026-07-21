package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict diagnostic preview index for an extracted mask draft; not a Release manifest. */
public record ExtractedMaskDraftPreviewIndexV2(
        int previewIndexVersion,
        String sourceDraftSemanticChecksum,
        int width,
        int length,
        List<Layer> layers,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);
    public static final int MAXIMUM_DIMENSION = 4_096;

    public ExtractedMaskDraftPreviewIndexV2 {
        if (previewIndexVersion != VERSION) {
            throw new IllegalArgumentException("extracted mask draft preview index version must be 1");
        }
        sourceDraftSemanticChecksum = V2Validation.checksum(
                sourceDraftSemanticChecksum, "sourceDraftSemanticChecksum");
        if (width < 1 || width > MAXIMUM_DIMENSION || length < 1 || length > MAXIMUM_DIMENSION) {
            throw new IllegalArgumentException(
                    "extracted mask draft preview dimensions must be within 1..4096");
        }
        layers = V2Validation.sorted(layers, "layers", LayerId.values().length,
                Comparator.comparing(Layer::layerId));
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
        Set<LayerId> ids = new HashSet<>();
        for (Layer layer : layers) {
            if (!ids.add(layer.layerId()) || layer.width() != width || layer.length() != length) {
                throw new IllegalArgumentException(
                        "extracted mask draft preview layers must be unique and share index dimensions");
            }
            if (!layer.path().equals(layer.layerId().fileName())) {
                throw new IllegalArgumentException(
                        "extracted mask draft preview layer path does not match its fixed layer ID");
            }
        }
        if (!ids.equals(Set.of(LayerId.values()))) {
            throw new IllegalArgumentException(
                    "extracted mask draft preview index must contain the complete fixed layer set");
        }
    }

    public ExtractedMaskDraftPreviewIndexV2(
            int previewIndexVersion,
            String sourceDraftSemanticChecksum,
            int width,
            int length,
            List<Layer> layers
    ) {
        this(previewIndexVersion, sourceDraftSemanticChecksum, width, length, layers,
                PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public ExtractedMaskDraftPreviewIndexV2 withCanonicalChecksum(String checksum) {
        return new ExtractedMaskDraftPreviewIndexV2(
                previewIndexVersion, sourceDraftSemanticChecksum, width, length, layers, checksum);
    }

    public enum LayerId {
        CLASS("class.png", "extract.draft.class"),
        CONFIDENCE("confidence.png", "extract.draft.confidence"),
        UNKNOWN("unknown.png", "extract.draft.unknown");

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
        public static final String PALETTE_ID = "extracted-mask-draft-palette-v1";

        public Layer {
            Objects.requireNonNull(layerId, "layerId");
            if (layerVersion != 1) {
                throw new IllegalArgumentException(
                        "extracted mask draft preview layerVersion must be 1");
            }
            path = V2Validation.nonBlank(path, "path", 80);
            if (!path.matches("^[a-z0-9][a-z0-9-]{0,63}\\.png$")) {
                throw new IllegalArgumentException(
                        "extracted mask draft preview path must be one portable PNG filename");
            }
            semantic = V2Validation.qualifiedId(semantic, "semantic");
            if (!semantic.equals(layerId.semantic())) {
                throw new IllegalArgumentException(
                        "extracted mask draft preview semantic does not match layer ID");
            }
            sha256 = V2Validation.checksum(sha256, "sha256");
            if (byteLength < 1 || byteLength > 8L * 1024L * 1024L) {
                throw new IllegalArgumentException(
                        "extracted mask draft preview layer byteLength out of range");
            }
            if (width < 1 || length < 1 || width > MAXIMUM_DIMENSION || length > MAXIMUM_DIMENSION) {
                throw new IllegalArgumentException(
                        "extracted mask draft preview layer dimensions invalid");
            }
            paletteId = V2Validation.nonBlank(paletteId, "paletteId", 64);
            if (!PALETTE_ID.equals(paletteId)) {
                throw new IllegalArgumentException("unknown extracted mask draft preview palette");
            }
        }
    }
}
