package com.github.nankotsu029.landformcraft.core.v2.design;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.ProviderCapabilityCatalogV2;
import com.github.nankotsu029.landformcraft.core.CancellationToken;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Release 2 design dispatch request. Paths are caller-owned; no secret fields are carried here. */
public record DesignDispatchRequestV2(
        int intentContractVersion,
        DesignPathKindV2 path,
        Set<DesignCapabilityV2> capabilities,
        Path requestPath,
        Path designsRoot,
        String modelOrIntentPath,
        Optional<Path> draftIntentPath,
        Optional<SoftDraftPixelInputV2> draftInput,
        Optional<CancellationToken> cancellationToken
) {
    public DesignDispatchRequestV2 {
        if (intentContractVersion != ProviderCapabilityCatalogV2.INTENT_CONTRACT_VERSION) {
            throw new IllegalArgumentException("intentContractVersion must be exactly "
                    + ProviderCapabilityCatalogV2.INTENT_CONTRACT_VERSION);
        }
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(capabilities, "capabilities");
        capabilities = Set.copyOf(capabilities);
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("capabilities must not be empty");
        }
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(designsRoot, "designsRoot");
        modelOrIntentPath = requireNonBlank(modelOrIntentPath, "modelOrIntentPath");
        Objects.requireNonNull(draftIntentPath, "draftIntentPath");
        Objects.requireNonNull(draftInput, "draftInput");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank() || value.length() > 512) {
            throw new IllegalArgumentException(fieldName + " must be non-blank and <= 512");
        }
        return value;
    }
}
