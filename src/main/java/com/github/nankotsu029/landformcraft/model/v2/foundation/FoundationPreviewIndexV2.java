package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict bounded V2-9-02 foundation diagnostic preview index; not a Release manifest. */
public record FoundationPreviewIndexV2(
        int planVersion,
        String contractVersion,
        List<Layer> layers,
        int width,
        int length,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-preview-index-v1";

    public FoundationPreviewIndexV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("foundation preview index planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown foundation preview contract version");
        }
        if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) {
            throw new IllegalArgumentException("foundation preview dimensions must be within 1.." + ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING);
        }
        layers = FoundationValidationV2.immutable(layers, "layers", 16).stream()
                .sorted(Comparator.comparing(Layer::layerId))
                .toList();
        Set<String> layerIds = new HashSet<>();
        for (Layer layer : layers) {
            if (!layerIds.add(layer.layerId())) {
                throw new IllegalArgumentException("duplicate foundation preview layer id");
            }
        }
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationPreviewIndexV2 withCanonicalChecksum(String checksum) {
        return new FoundationPreviewIndexV2(planVersion, contractVersion, layers, width, length, checksum);
    }

    public record Layer(String layerId, String fieldId, String checksum) {
        public Layer {
            layerId = FoundationValidationV2.slug(layerId, "layerId");
            fieldId = FoundationValidationV2.qualified(fieldId, "fieldId");
            checksum = FoundationValidationV2.checksum(checksum, "checksum");
        }
    }
}
