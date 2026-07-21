package com.github.nankotsu029.landformcraft.model.v2;

import java.util.List;

/** Strict preview index for multi-source reconciliation diagnostic PNGs. */
public record MultiSourceReconciliationPreviewIndexV2(
        int schemaVersion,
        String reconciliationSemanticChecksum,
        int width,
        int length,
        List<Layer> layers,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);
    public static final int MAXIMUM_DIMENSION = 4_096;

    public MultiSourceReconciliationPreviewIndexV2 {
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("multi-source preview schemaVersion must be 1");
        }
        reconciliationSemanticChecksum = V2Validation.checksum(
                reconciliationSemanticChecksum, "reconciliationSemanticChecksum");
        if (width < 1 || width > MAXIMUM_DIMENSION || length < 1 || length > MAXIMUM_DIMENSION) {
            throw new IllegalArgumentException("preview dimensions must be within 1..4096");
        }
        layers = V2Validation.immutable(layers, "layers", LayerId.values().length);
        if (layers.size() != LayerId.values().length) {
            throw new IllegalArgumentException("preview must declare all fixed layers");
        }
        for (int i = 0; i < LayerId.values().length; i++) {
            if (layers.get(i).layerId() != LayerId.values()[i]) {
                throw new IllegalArgumentException("preview layers must follow fixed LayerId order");
            }
        }
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public MultiSourceReconciliationPreviewIndexV2(
            int schemaVersion,
            String reconciliationSemanticChecksum,
            int width,
            int length,
            List<Layer> layers
    ) {
        this(schemaVersion, reconciliationSemanticChecksum, width, length, layers, PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public MultiSourceReconciliationPreviewIndexV2 withCanonicalChecksum(String checksum) {
        return new MultiSourceReconciliationPreviewIndexV2(
                schemaVersion, reconciliationSemanticChecksum, width, length, layers, checksum);
    }

    public enum LayerId {
        RESULT("result.png", "RESULT"),
        CONFLICT("conflict.png", "CONFLICT"),
        SOURCE_DIFF("source-diff.png", "SOURCE_DIFF");

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
            String fileName,
            String semantic,
            String sha256,
            long byteLength,
            int width,
            int length,
            String paletteId
    ) {
        public static final String PALETTE_ID = "multi-source-reconcile-palette-v1";

        public Layer {
            if (layerId == null) {
                throw new IllegalArgumentException("layerId is required");
            }
            if (layerVersion != 1) {
                throw new IllegalArgumentException("layerVersion must be 1");
            }
            if (!layerId.fileName().equals(fileName) || !layerId.semantic().equals(semantic)) {
                throw new IllegalArgumentException("layer fileName/semantic must match LayerId");
            }
            sha256 = V2Validation.checksum(sha256, "sha256");
            if (byteLength < 1 || byteLength > 8L * 1024L * 1024L) {
                throw new IllegalArgumentException("preview PNG byte length is outside budget");
            }
            if (width < 1 || length < 1) {
                throw new IllegalArgumentException("layer dimensions are invalid");
            }
            paletteId = V2Validation.nonBlank(paletteId, "paletteId", 64);
            if (!PALETTE_ID.equals(paletteId)) {
                throw new IllegalArgumentException("preview paletteId is fixed");
            }
        }
    }
}
