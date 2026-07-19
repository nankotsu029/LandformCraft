package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict bounded V2-4-13 environment diagnostic preview index; not a Release manifest. */
public record EnvironmentPreviewIndexV2(
        int previewIndexVersion,
        String sourcePlanChecksum,
        int width,
        int length,
        List<Layer> layers,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);

    public EnvironmentPreviewIndexV2 {
        if (previewIndexVersion != VERSION) {
            throw new IllegalArgumentException("environment preview index version must be 1");
        }
        sourcePlanChecksum = V2Validation.checksum(sourcePlanChecksum, "sourcePlanChecksum");
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000) {
            throw new IllegalArgumentException("environment preview dimensions must be within 1..1000");
        }
        layers = V2Validation.sorted(layers, "layers", LayerId.values().length,
                Comparator.comparing(Layer::layerId));
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
        Set<LayerId> ids = new HashSet<>();
        for (Layer layer : layers) {
            if (!ids.add(layer.layerId()) || layer.width() != width || layer.length() != length) {
                throw new IllegalArgumentException(
                        "environment preview layers must be unique and share index dimensions");
            }
            if (!layer.path().equals(layer.layerId().fileName())) {
                throw new IllegalArgumentException("environment preview layer path does not match its fixed layer ID");
            }
        }
        if (!ids.equals(Set.of(LayerId.values()))) {
            throw new IllegalArgumentException("environment preview index must contain the complete fixed layer set");
        }
    }

    public EnvironmentPreviewIndexV2(
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

    public EnvironmentPreviewIndexV2 withCanonicalChecksum(String checksum) {
        return new EnvironmentPreviewIndexV2(
                previewIndexVersion, sourcePlanChecksum, width, length, layers, checksum);
    }

    public enum LayerId {
        TEMPERATURE("temperature.png", "environment.climate.temperature"),
        MOISTURE("moisture.png", "environment.climate.moisture"),
        WETNESS("wetness.png", "environment.water.wetness"),
        SALINITY("salinity.png", "environment.water.salinity"),
        HYDROPERIOD("hydroperiod.png", "environment.water.hydroperiod"),
        SNOW_COVER("snow-cover.png", "environment.snow.cover"),
        HABITAT("habitat.png", "environment.ecology.habitat"),
        MATERIAL_PROFILE("material-profile.png", "environment.material.profile"),
        FEATURE_MATERIAL("feature-material.png", "environment.material.feature"),
        CONSTRAINT_ERROR("constraint-error.png", "environment.constraint.error");

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
                throw new IllegalArgumentException("environment preview layer version must be 1");
            }
            path = V2Validation.nonBlank(path, "path", 64);
            if (!path.matches("^[a-z0-9][a-z0-9-]{0,63}\\.png$")) {
                throw new IllegalArgumentException("environment preview path must be one portable PNG filename");
            }
            semantic = V2Validation.qualifiedId(semantic, "semantic");
            if (!semantic.equals(layerId.semantic())) {
                throw new IllegalArgumentException("environment preview semantic does not match layer ID");
            }
            sha256 = V2Validation.checksum(sha256, "sha256");
            if (byteLength < 1 || byteLength > 8L * 1024L * 1024L) {
                throw new IllegalArgumentException("environment preview PNG byte length is out of budget");
            }
            if (width < 1 || length < 1 || width > 1_000 || length > 1_000) {
                throw new IllegalArgumentException("environment preview layer dimensions are invalid");
            }
            paletteId = V2Validation.nonBlank(paletteId, "paletteId", 64);
            if (!"environment-diagnostic-palette-v1".equals(paletteId)) {
                throw new IllegalArgumentException("unknown environment preview palette");
            }
        }
    }
}
