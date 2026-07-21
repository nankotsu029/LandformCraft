package com.github.nankotsu029.landformcraft.ai.spi.v2;

import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainIntentPromptV2Test {

    @Test
    void everyRoleHasSoftProposalGuardText() {
        for (GenerationRequestV2.ReferenceImageRole role : GenerationRequestV2.ReferenceImageRole.values()) {
            String text = TerrainIntentPromptV2.imageRoleText(preparedImage(role));
            assertFalse(text.isBlank(), "role text must not be blank: " + role);
            assertTrue(text.contains(role.name()), "role text must name the role: " + role);
            assertTrue(text.toLowerCase().contains("do not"),
                    "role text must state a prohibition: " + role);
        }
    }

    @Test
    void obliqueAndMultiViewRolesForbidCoordinateAndUndergroundInference() {
        String oblique = TerrainIntentPromptV2.imageRoleText(
                preparedImage(GenerationRequestV2.ReferenceImageRole.OBLIQUE_TERRAIN_REFERENCE));
        assertTrue(oblique.contains("map coordinates") && oblique.contains("HARD geometry"),
                "oblique role must forbid coordinate and HARD geometry inference");
        assertTrue(oblique.contains("top-down"),
                "oblique role must forbid rectifying to a top-down view");
        assertTrue(oblique.toLowerCase().contains("below the surface"),
                "oblique role must forbid inferring hidden underground terrain");

        String multiView = TerrainIntentPromptV2.imageRoleText(
                preparedImage(GenerationRequestV2.ReferenceImageRole.MULTI_VIEW_REFERENCE));
        assertTrue(multiView.contains("coordinates") && multiView.contains("HARD geometry"),
                "multi-view role must forbid triangulating coordinates and HARD geometry");
        assertTrue(multiView.toLowerCase().contains("underground"),
                "multi-view role must forbid inferring unobserved underground terrain");
    }

    @Test
    void systemTextKeepsImagesSoftOnly() {
        assertEquals("terrain-intent-v2-structured-guards", TerrainIntentPromptV2.VERSION);
        assertTrue(TerrainIntentPromptV2.systemText().contains("soft mood or sketch cues only"));
    }

    private static PreparedReferenceImageV2 preparedImage(GenerationRequestV2.ReferenceImageRole role) {
        return new PreparedReferenceImageV2(
                0,
                "references/reference.png",
                role,
                "image/png",
                8,
                8,
                "0".repeat(64),
                "png-bytes".getBytes(StandardCharsets.UTF_8));
    }
}
