package com.github.nankotsu029.landformcraft.ai.spi.v2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * No-fallback capability negotiation. Unknown version/capability/model fails closed and never
 * rewrites the request onto the v1 design path.
 */
public final class DesignCapabilityNegotiatorV2 {
    private DesignCapabilityNegotiatorV2() {
    }

    public static ProviderCapabilityDescriptorV2 negotiate(
            int intentContractVersion,
            DesignPathKindV2 path,
            String modelId,
            Set<DesignCapabilityV2> requestedCapabilities
    ) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(requestedCapabilities, "requestedCapabilities");
        if (intentContractVersion != ProviderCapabilityCatalogV2.INTENT_CONTRACT_VERSION) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.UNKNOWN_INTENT_VERSION,
                    "intent contract version must be exactly "
                            + ProviderCapabilityCatalogV2.INTENT_CONTRACT_VERSION);
        }
        if (requestedCapabilities.isEmpty()) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.INVALID_REQUEST, "requestedCapabilities must not be empty");
        }
        for (DesignCapabilityV2 capability : requestedCapabilities) {
            if (capability == null) {
                throw new DesignExceptionV2(
                        DesignFailureCodeV2.UNKNOWN_CAPABILITY, "requested capability must not be null");
            }
        }
        Set<DesignCapabilityV2> requested = EnumSet.copyOf(requestedCapabilities);
        DesignCapabilityV2 required = ProviderCapabilityCatalogV2.requiredCapability(path);
        if (!requested.contains(required)) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.CAPABILITY_MISMATCH,
                    "path " + path + " requires capability " + required);
        }
        ProviderCapabilityDescriptorV2 descriptor = ProviderCapabilityCatalogV2.lookup(path, modelId)
                .orElseThrow(() -> new DesignExceptionV2(
                        path == DesignPathKindV2.OPENAI || path == DesignPathKindV2.ANTHROPIC
                                ? DesignFailureCodeV2.UNSUPPORTED_MODEL
                                : DesignFailureCodeV2.UNSUPPORTED_PROVIDER,
                        "provider/model is not declared for v2 capability negotiation"));
        for (DesignCapabilityV2 capability : requested) {
            if (!descriptor.supports(capability)) {
                throw new DesignExceptionV2(
                        DesignFailureCodeV2.UNSUPPORTED_CAPABILITY,
                        "capability " + capability + " is unsupported for " + path);
            }
        }
        // Extra catalog capabilities beyond the request are fine; missing ones already rejected.
        return descriptor;
    }
}
