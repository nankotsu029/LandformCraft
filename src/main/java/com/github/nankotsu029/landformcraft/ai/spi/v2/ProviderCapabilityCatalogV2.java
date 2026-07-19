package com.github.nankotsu029.landformcraft.ai.spi.v2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Frozen capability catalog for v2 design paths. Unknown providers/models are unsupported;
 * there is no silent fallback to v1.
 */
public final class ProviderCapabilityCatalogV2 {
    public static final String CONTRACT_VERSION = "provider-capability-catalog-v1";
    public static final int INTENT_CONTRACT_VERSION = 2;

    private ProviderCapabilityCatalogV2() {
    }

    public static Optional<ProviderCapabilityDescriptorV2> lookup(
            DesignPathKindV2 path,
            String modelId
    ) {
        Objects.requireNonNull(path, "path");
        return switch (path) {
            case OPENAI -> lookupOpenAi(modelId);
            case ANTHROPIC -> lookupAnthropic(modelId);
            case IMPORT -> Optional.of(descriptor(
                    DesignPathKindV2.IMPORT, "manual-json-v2",
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED), false));
            case FIXTURE -> Optional.of(descriptor(
                    DesignPathKindV2.FIXTURE, "fixture-v2",
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED), false));
            case MANUAL_CONSTRAINT -> Optional.of(descriptor(
                    DesignPathKindV2.MANUAL_CONSTRAINT, "manual-constraint-bundle-v1",
                    EnumSet.of(DesignCapabilityV2.MANUAL_CONSTRAINT_BUNDLE), false));
            case REFERENCE_IMAGE_DRAFT -> Optional.of(descriptor(
                    DesignPathKindV2.REFERENCE_IMAGE_DRAFT, "image-land-water-extract-v1",
                    EnumSet.of(DesignCapabilityV2.REFERENCE_IMAGE_SOFT_DRAFT), false));
        };
    }

    public static DesignCapabilityV2 requiredCapability(DesignPathKindV2 path) {
        return switch (Objects.requireNonNull(path, "path")) {
            case OPENAI, ANTHROPIC, IMPORT, FIXTURE -> DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED;
            case MANUAL_CONSTRAINT -> DesignCapabilityV2.MANUAL_CONSTRAINT_BUNDLE;
            case REFERENCE_IMAGE_DRAFT -> DesignCapabilityV2.REFERENCE_IMAGE_SOFT_DRAFT;
        };
    }

    private static Optional<ProviderCapabilityDescriptorV2> lookupOpenAi(String modelId) {
        String model = normalizeModel(modelId);
        if (model.isEmpty()) {
            return Optional.empty();
        }
        // Contract allowlist: explicit v2 structured-output capable ids only. Unknown → empty.
        if (model.startsWith("gpt-") || model.startsWith("o1") || model.startsWith("o3")
                || model.startsWith("o4") || model.contains("gpt-test")) {
            return Optional.of(descriptor(
                    DesignPathKindV2.OPENAI, model,
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED), true));
        }
        return Optional.empty();
    }

    private static Optional<ProviderCapabilityDescriptorV2> lookupAnthropic(String modelId) {
        String model = normalizeModel(modelId);
        if (model.isEmpty()) {
            return Optional.empty();
        }
        if (model.startsWith("claude-") || model.contains("claude-test")) {
            return Optional.of(descriptor(
                    DesignPathKindV2.ANTHROPIC, model,
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED), true));
        }
        return Optional.empty();
    }

    private static ProviderCapabilityDescriptorV2 descriptor(
            DesignPathKindV2 path,
            String modelId,
            Set<DesignCapabilityV2> capabilities,
            boolean submitsReferenceImages
    ) {
        return new ProviderCapabilityDescriptorV2(
                CONTRACT_VERSION, path, modelId, Set.copyOf(capabilities), submitsReferenceImages);
    }

    private static String normalizeModel(String modelId) {
        if (modelId == null) {
            return "";
        }
        return modelId.trim().toLowerCase(Locale.ROOT);
    }
}
