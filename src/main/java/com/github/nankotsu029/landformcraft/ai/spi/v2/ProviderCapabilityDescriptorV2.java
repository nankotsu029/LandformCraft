package com.github.nankotsu029.landformcraft.ai.spi.v2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;

import java.util.Objects;
import java.util.Set;

/** Immutable capability declaration for one provider path + model. */
public record ProviderCapabilityDescriptorV2(
        String catalogVersion,
        DesignPathKindV2 path,
        String modelId,
        Set<DesignCapabilityV2> capabilities,
        boolean submitsReferenceImages
) {
    public ProviderCapabilityDescriptorV2 {
        catalogVersion = requireNonBlank(catalogVersion, "catalogVersion");
        Objects.requireNonNull(path, "path");
        modelId = requireNonBlank(modelId, "modelId");
        Objects.requireNonNull(capabilities, "capabilities");
        capabilities = Set.copyOf(capabilities);
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("capabilities must not be empty");
        }
        if (capabilities.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("capabilities must not contain null");
        }
    }

    public boolean supports(DesignCapabilityV2 capability) {
        return capabilities.contains(Objects.requireNonNull(capability, "capability"));
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(name + " must be non-blank and at most 128");
        }
        return value;
    }
}
