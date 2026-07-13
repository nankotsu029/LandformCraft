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

    @Test
    void rejectsPreparedImagesAboveTheAggregateProviderLimit() {
        var declarations = List.of(
                new ReferenceImage("images/one.png", ReferenceImageRole.MOOD_REFERENCE),
                new ReferenceImage("images/two.png", ReferenceImageRole.MATERIAL_REFERENCE)
        );
        GenerationRequest request = new GenerationRequest(
                1, "image-request", new GenerationBounds(128, 128, -32, 160, 62),
                "Create a coast from the references.", declarations,
                new GenerationOptions(1, 42L), new OutputOptions(128, true, true)
        );
        byte[] first = new byte[8 * 1024 * 1024];
        byte[] second = new byte[8 * 1024 * 1024 + 1];
        var prepared = List.of(
                new PreparedReferenceImage(0, declarations.get(0).file(), declarations.get(0).role(),
                        "image/png", 1, 1,
                        com.github.nankotsu029.landformcraft.format.Sha256.bytes(first), first),
                new PreparedReferenceImage(1, declarations.get(1).file(), declarations.get(1).role(),
                        "image/png", 1, 1,
                        com.github.nankotsu029.landformcraft.format.Sha256.bytes(second), second)
        );

        assertThrows(IllegalArgumentException.class, () -> new TerrainDesignRequest(request, prepared));
    }
}
