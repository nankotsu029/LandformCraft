package com.github.nankotsu029.landformcraft.ai.spi;

import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.GenerationOptions;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.OutputOptions;
import com.github.nankotsu029.landformcraft.model.ReferenceImage;
import com.github.nankotsu029.landformcraft.model.ReferenceImageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TerrainDesignRequestTest {
    @Test
    void rejectsRawImagePathsUntilVerifiedImageHandlesExist() {
        GenerationRequest request = new GenerationRequest(
                1,
                "image-request",
                new GenerationBounds(128, 128, -32, 160, 62),
                "Create a coast from the reference.",
                List.of(new ReferenceImage("images/coast.png", ReferenceImageRole.MOOD_REFERENCE)),
                new GenerationOptions(1, 42L),
                new OutputOptions(128, true, true)
        );

        assertThrows(IllegalArgumentException.class, () -> new TerrainDesignRequest(request));
    }
}
