package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.ai.spi.FixtureTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.ImportedJsonTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.ProviderFailureCode;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignException;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignRequest;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignResult;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.generator.TerrainGenerator;
import com.github.nankotsu029.landformcraft.model.GenerationStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainDesignApplicationServiceTest {
    private final LandformDataCodec codec = new LandformDataCodec();

    @Test
    void importedAndFixtureProvidersFeedTheSameDeterministicGenerator(@TempDir Path directory) throws Exception {
        var request = codec.readGenerationRequest(Path.of("examples/rocky-coast/request.yml"));
        var intent = codec.readTerrainIntent(Path.of("examples/rocky-coast/terrain-intent.json"));
        try (GenerationExecutors executors = GenerationExecutors.create(4, 2, 8)) {
            var imported = new ImportedJsonTerrainDesignProvider(
                    executors, Path.of("examples/rocky-coast/terrain-intent.json")
            ).design(new TerrainDesignRequest(request)).join();
            var fixture = new FixtureTerrainDesignProvider(intent)
                    .design(new TerrainDesignRequest(request)).join();
            var compiler = new BlueprintCompiler();
            var generator = new TerrainGenerator();

            String importedChecksum = generator.generate(
                    compiler.compile(request, imported.intent(), 0), () -> false
            ).checksum();
            String fixtureChecksum = generator.generate(
                    compiler.compile(request, fixture.intent(), 0), () -> false
            ).checksum();

            assertEquals(importedChecksum, fixtureChecksum);
        }
    }

    @Test
    void publishesAuditedDesignAndPersistsReadyJob(@TempDir Path directory) throws Exception {
        try (GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var jobs = new FileGenerationJobRepository(directory.resolve("jobs"), executors);
            var provider = new ImportedJsonTerrainDesignProvider(
                    executors, Path.of("examples/rocky-coast/terrain-intent.json")
            );
            var handle = new TerrainDesignApplicationService(executors, provider, jobs)
                    .start(Path.of("examples/rocky-coast/request.yml"), directory.resolve("designs"));

            var artifacts = handle.completion().join();
            var job = jobs.find(handle.jobId().toString()).join().orElseThrow();

            assertEquals(GenerationStage.READY, job.stage());
            assertEquals("imported-json", artifacts.audit().providerId());
            assertFalse(Files.readString(artifacts.directory().resolve("audit.json"))
                    .contains("Terrain requirements"));
            assertTrue(Files.isRegularFile(artifacts.directory().resolve("checksums.sha256")));
        }
    }

    @Test
    void imageRequestPublishesVerifiedEvidence(@TempDir Path directory) throws Exception {
        Path requestRoot = Files.createDirectories(directory.resolve("request"));
        Path images = Files.createDirectories(requestRoot.resolve("images"));
        String requestYaml = Files.readString(Path.of("examples/rocky-coast/request.yml")).replace(
                "images: []",
                "images:\n  - file: images/map.png\n    role: TOP_DOWN_SKETCH"
        );
        Path requestPath = requestRoot.resolve("request.yml");
        Files.writeString(requestPath, requestYaml);
        BufferedImage map = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                map.setRGB(
                        x, y,
                        (x < map.getWidth() / 2 ? new Color(70, 140, 70) : new Color(50, 95, 210)).getRGB()
                );
            }
        }
        assertTrue(ImageIO.write(map, "png", images.resolve("map.png").toFile()));

        try (GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var jobs = new FileGenerationJobRepository(directory.resolve("jobs"), executors);
            var provider = new FixtureTerrainDesignProvider(
                    codec.readTerrainIntent(Path.of("examples/rocky-coast/terrain-intent.json"))
            );
            var artifacts = new TerrainDesignApplicationService(executors, provider, jobs)
                    .start(requestPath, directory.resolve("designs")).completion().join();

            assertEquals(1, artifacts.imageEvidence().images().size());
            assertEquals("fixture", artifacts.imageEvidence().providerId());
            assertEquals("fixture-response-v1", artifacts.imageEvidence().providerResponseId());
            assertEquals("image-normalization-v1", artifacts.imageEvidence().normalizationVersion());
            assertFalse(artifacts.imageEvidence().images().getFirst().providerSubmitted());
            assertEquals(2, artifacts.imageEvidence().consistencyChecks().size());
            assertTrue(Files.isRegularFile(artifacts.directory().resolve("image-evidence.json")));
            assertEquals(
                    artifacts.imageEvidence(),
                    codec.readImageInputEvidence(artifacts.directory().resolve("image-evidence.json"))
            );
        }
    }

    @ParameterizedTest
    @EnumSource(value = ProviderFailureCode.class, names = {"TIMEOUT", "RATE_LIMITED", "SERVER_ERROR"})
    void transientProviderFailuresBecomeFailedJobs(ProviderFailureCode code, @TempDir Path directory)
            throws Exception {
        try (GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var jobs = new FileGenerationJobRepository(directory.resolve("jobs"), executors);
            TerrainDesignProvider provider = failingProvider(code);
            var handle = new TerrainDesignApplicationService(executors, provider, jobs)
                    .start(Path.of("examples/rocky-coast/request.yml"), directory.resolve("designs"));

            assertThrows(CompletionException.class, () -> handle.completion().join());
            var snapshot = jobs.find(handle.jobId().toString()).join().orElseThrow();

            assertEquals(GenerationStage.FAILED, snapshot.stage());
            assertEquals("provider failure: " + code, snapshot.message());
            assertFalse(Files.exists(directory.resolve("designs/rocky-coast-001").resolve(
                    handle.jobId().toString()
            )));
        }
    }

    @Test
    void malformedAndOutOfRangeIntentNeverProduceDesignArtifact(@TempDir Path directory) throws Exception {
        String valid = Files.readString(Path.of("examples/rocky-coast/terrain-intent.json"));
        Path malformed = directory.resolve("malformed.json");
        Path outOfRange = directory.resolve("out-of-range.json");
        Files.writeString(malformed, "{\"schemaVersion\":1");
        Files.writeString(outOfRange, valid.replace("\"landRatio\": 0.48", "\"landRatio\": 1.5"));

        for (Path invalid : java.util.List.of(malformed, outOfRange)) {
            Path run = directory.resolve(invalid.getFileName().toString() + "-run");
            try (GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
                var jobs = new FileGenerationJobRepository(run.resolve("jobs"), executors);
                var provider = new ImportedJsonTerrainDesignProvider(executors, invalid);
                var handle = new TerrainDesignApplicationService(executors, provider, jobs)
                        .start(Path.of("examples/rocky-coast/request.yml"), run.resolve("designs"));

                assertThrows(CompletionException.class, () -> handle.completion().join());
                assertEquals(
                        GenerationStage.FAILED,
                        jobs.find(handle.jobId().toString()).join().orElseThrow().stage()
                );
                assertFalse(Files.exists(run.resolve("designs/rocky-coast-001").resolve(
                        handle.jobId().toString()
                )));
            }
        }
    }

    @Test
    void cancelInterruptsProviderFutureAndPersistsCancelledJob(@TempDir Path directory) throws Exception {
        CountDownLatch called = new CountDownLatch(1);
        CompletableFuture<TerrainDesignResult> providerFuture = new CompletableFuture<>();
        TerrainDesignProvider provider = new TerrainDesignProvider() {
            @Override
            public String id() {
                return "blocking-test";
            }

            @Override
            public CompletableFuture<TerrainDesignResult> design(TerrainDesignRequest request) {
                called.countDown();
                return providerFuture;
            }
        };
        try (GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var jobs = new FileGenerationJobRepository(directory.resolve("jobs"), executors);
            var handle = new TerrainDesignApplicationService(executors, provider, jobs)
                    .start(Path.of("examples/rocky-coast/request.yml"), directory.resolve("designs"));
            assertTrue(called.await(2, TimeUnit.SECONDS));

            assertTrue(handle.cancel());
            assertTrue(providerFuture.isCancelled());
            assertEquals(GenerationStage.CANCELLED, awaitStage(jobs, handle.jobId().toString()));
        }
    }

    private static TerrainDesignProvider failingProvider(ProviderFailureCode code) {
        return new TerrainDesignProvider() {
            @Override
            public String id() {
                return "failure-test";
            }

            @Override
            public CompletableFuture<TerrainDesignResult> design(TerrainDesignRequest request) {
                return CompletableFuture.failedFuture(new TerrainDesignException(code, "sanitized", 0, 3));
            }
        };
    }

    private static GenerationStage awaitStage(FileGenerationJobRepository jobs, String jobId)
            throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            var snapshot = jobs.find(jobId).join();
            if (snapshot.isPresent() && snapshot.orElseThrow().stage() == GenerationStage.CANCELLED) {
                return GenerationStage.CANCELLED;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("CANCELLED job state was not persisted");
    }
}
