package com.github.nankotsu029.landformcraft.ai.spi.v2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignCapabilityNegotiatorV2Test {
    @Test
    void negotiatesDeclaredProviderModelsAndRejectsUnknownWithoutFallback() {
        ProviderCapabilityDescriptorV2 openAi = DesignCapabilityNegotiatorV2.negotiate(
                2,
                DesignPathKindV2.OPENAI,
                "gpt-test-v2",
                EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED));
        assertEquals(DesignPathKindV2.OPENAI, openAi.path());
        assertTrue(openAi.supports(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED));

        DesignExceptionV2 unknownVersion = assertThrows(
                DesignExceptionV2.class,
                () -> DesignCapabilityNegotiatorV2.negotiate(
                        1,
                        DesignPathKindV2.IMPORT,
                        "manual-json-v2",
                        EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED)));
        assertEquals(DesignFailureCodeV2.UNKNOWN_INTENT_VERSION, unknownVersion.code());

        DesignExceptionV2 unsupportedModel = assertThrows(
                DesignExceptionV2.class,
                () -> DesignCapabilityNegotiatorV2.negotiate(
                        2,
                        DesignPathKindV2.OPENAI,
                        "unknown-model-x",
                        EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED)));
        assertEquals(DesignFailureCodeV2.UNSUPPORTED_MODEL, unsupportedModel.code());

        DesignExceptionV2 capabilityMismatch = assertThrows(
                DesignExceptionV2.class,
                () -> DesignCapabilityNegotiatorV2.negotiate(
                        2,
                        DesignPathKindV2.MANUAL_CONSTRAINT,
                        "manual-constraint-bundle-v1",
                        EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED)));
        assertEquals(DesignFailureCodeV2.CAPABILITY_MISMATCH, capabilityMismatch.code());

        DesignExceptionV2 unsupportedCapability = assertThrows(
                DesignExceptionV2.class,
                () -> DesignCapabilityNegotiatorV2.negotiate(
                        2,
                        DesignPathKindV2.IMPORT,
                        "manual-json-v2",
                        Set.of(
                                DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED,
                                DesignCapabilityV2.REFERENCE_IMAGE_SOFT_DRAFT)));
        assertEquals(DesignFailureCodeV2.UNSUPPORTED_CAPABILITY, unsupportedCapability.code());
    }
}
