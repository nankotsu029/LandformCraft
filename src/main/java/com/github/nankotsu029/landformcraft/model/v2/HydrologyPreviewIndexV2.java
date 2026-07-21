package com.github.nankotsu029.landformcraft.model.v2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict, bounded V2-3 hydrology diagnostic preview index; it is not a Release manifest. */
public record HydrologyPreviewIndexV2(
        int previewIndexVersion,
        String sourceBlueprintChecksum,
        int width,
        int length,
        List<Layer> layers,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);

    public HydrologyPreviewIndexV2 {
        if (previewIndexVersion != VERSION) {
            throw new IllegalArgumentException("hydrology preview index version must be 1");
        }
        sourceBlueprintChecksum = V2Validation.checksum(sourceBlueprintChecksum, "sourceBlueprintChecksum");
        if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) {
            throw new IllegalArgumentException("hydrology preview dimensions must be within 1.." + ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING);
        }
        layers = V2Validation.sorted(layers, "layers", LayerId.values().length,
                Comparator.comparing(Layer::layerId));
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
        Set<LayerId> ids = new HashSet<>();
        for (Layer layer : layers) {
            if (!ids.add(layer.layerId()) || layer.width() != width || layer.length() != length) {
                throw new IllegalArgumentException("hydrology preview layers must be unique and share index dimensions");
            }
            if (!layer.path().equals(layer.layerId().fileName())) {
                throw new IllegalArgumentException("hydrology preview layer path does not match its fixed layer ID");
            }
        }
        if (!ids.equals(Set.of(LayerId.values()))) {
            throw new IllegalArgumentException("hydrology preview index must contain the complete fixed layer set");
        }
    }

    public HydrologyPreviewIndexV2(
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

    public HydrologyPreviewIndexV2 withCanonicalChecksum(String checksum) {
        return new HydrologyPreviewIndexV2(
                previewIndexVersion, sourceBlueprintChecksum, width, length, layers, checksum);
    }

    public enum LayerId {
        BASIN_ID("basin-id.png", "hydrology.basin.id"),
        FLOW_DIRECTION("flow-direction.png", "hydrology.flow.direction"),
        FLOW_ACCUMULATION("flow-accumulation.png", "hydrology.flow.accumulation"),
        REACH_GRAPH("reach-graph.png", "hydrology.reach.graph"),
        BED_ELEVATION("bed-elevation.png", "hydrology.bed.elevation"),
        WATER_SURFACE("water-surface.png", "hydrology.water.surface"),
        WATER_BODY("water-body.png", "hydrology.water.body"),
        LAKE_RIM_SPILL("lake-rim-spill.png", "hydrology.lake.rim-spill"),
        DELTA_DISTRIBUTARY("delta-distributary.png", "hydrology.delta.distributary"),
        FJORD_THALWEG("fjord-thalweg.png", "hydrology.fjord.thalweg"),
        WATERFALL_ENVELOPE("waterfall-envelope.png", "hydrology.waterfall.envelope"),
        CONSTRAINT_RESIDUAL("constraint-residual.png", "hydrology.constraint.residual");

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
        public Layer {
            Objects.requireNonNull(layerId, "layerId");
            if (layerVersion != 1) {
                throw new IllegalArgumentException("hydrology preview layer version must be 1");
            }
            path = V2Validation.nonBlank(path, "path", 64);
            if (!path.matches("^[a-z0-9][a-z0-9-]{0,63}\\.png$")) {
                throw new IllegalArgumentException("hydrology preview path must be one portable PNG filename");
            }
            semantic = V2Validation.qualifiedId(semantic, "semantic");
            if (!semantic.equals(layerId.semantic())) {
                throw new IllegalArgumentException("hydrology preview semantic does not match layer ID");
            }
            sha256 = V2Validation.checksum(sha256, "sha256");
            if (byteLength < 1 || byteLength > 8L * 1024L * 1024L
                    || width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) {
                throw new IllegalArgumentException("hydrology preview layer exceeds fixed budget");
            }
            paletteId = V2Validation.qualifiedId(paletteId, "paletteId");
            if (!"hydrology-diagnostic-palette-v1".equals(paletteId)) {
                throw new IllegalArgumentException("hydrology preview palette is unsupported");
            }
        }
    }
}
