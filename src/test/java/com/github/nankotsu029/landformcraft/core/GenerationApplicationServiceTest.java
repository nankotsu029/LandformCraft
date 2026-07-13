package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationApplicationServiceTest {
    @Test
    void validatesGeneratesAndRendersThroughAsyncApplicationBoundary(@TempDir Path directory) throws IOException {
        String request = Files.readString(Path.of("examples/rocky-coast/request.yml"))
                .replace("width: 500", "width: 64")
                .replace("length: 500", "length: 64");
        Path requestPath = directory.resolve("request.yml");
        Files.writeString(requestPath, request);
        Path output = directory.resolve("output");

        try (GenerationExecutors executors = GenerationExecutors.create(2, 2, 8)) {
            GenerationOutcome outcome = new GenerationApplicationService(executors)
                    .generatePreview(
                            requestPath,
                            Path.of("examples/rocky-coast/terrain-intent.json"),
                            output,
                            0
                    )
                    .join();

            assertTrue(outcome.validation().isValid());
            assertEquals(1, outcome.terrainPlan().tiles().size());
            assertTrue(Files.isRegularFile(output.resolve("overview.png")));
            assertTrue(Files.isRegularFile(output.resolve("world-blueprint.json")));
            assertTrue(Files.isRegularFile(output.resolve("generation-summary.json")));
            assertEquals("rocky-coast-001", new LandformDataCodec()
                    .readWorldBlueprint(output.resolve("world-blueprint.json"))
                    .requestId());
        }
    }
}
