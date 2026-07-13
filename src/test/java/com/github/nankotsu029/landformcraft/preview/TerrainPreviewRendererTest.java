package com.github.nankotsu029.landformcraft.preview;

import com.github.nankotsu029.landformcraft.core.BlueprintCompiler;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.generator.TerrainGenerator;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.GenerationOptions;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.OutputOptions;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.validation.TerrainValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainPreviewRendererTest {
    @Test
    void writesAllPhaseOnePreviewLayers(@TempDir Path directory) throws IOException {
        LandformDataCodec codec = new LandformDataCodec();
        var intent = codec.readTerrainIntent(Path.of("examples/rocky-coast/terrain-intent.json"));
        GenerationRequest request = new GenerationRequest(
                1,
                "preview-test",
                new GenerationBounds(64, 64, -32, 160, 62),
                "Preview fixture",
                List.of(),
                new GenerationOptions(1, 1234L),
                new OutputOptions(64, false, false)
        );
        TerrainPlan plan = new TerrainGenerator().generate(
                new BlueprintCompiler().compile(request, intent, 0),
                () -> false
        );

        TerrainPreviewRenderer renderer = new TerrainPreviewRenderer();
        PreviewArtifacts artifacts = renderer.render(
                plan,
                new TerrainValidator().validate(plan),
                directory.resolve("first"),
                () -> false
        );
        PreviewArtifacts repeated = renderer.render(
                plan,
                new TerrainValidator().validate(plan),
                directory.resolve("second"),
                () -> false
        );

        assertEquals(8, artifacts.files().size());
        for (int index = 0; index < artifacts.files().size(); index++) {
            Path file = artifacts.files().get(index);
            assertTrue(Files.size(file) > 0L);
            var image = ImageIO.read(file.toFile());
            assertNotNull(image);
            assertEquals(64, image.getWidth());
            assertEquals(64, image.getHeight());
            assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(repeated.files().get(index)));
        }
    }
}
