package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.ai.spi.FixtureTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignRequest;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignResult;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.ProviderUsage;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignResponseCacheTest {
    private final LandformDataCodec codec = new LandformDataCodec();
    private final DesignResponseCache cache = new DesignResponseCache(codec);

    private TerrainDesignResult sampleResult() throws Exception {
        TerrainIntent intent = codec.readTerrainIntent(
                Path.of("examples/rocky-coast/terrain-intent.json"));
        return new TerrainDesignResult(
                intent, "openai", "model-x", "prompt-v1", "resp-1",
                new ProviderUsage(10, 20, 30), 1, Instant.parse("2026-07-20T00:00:00Z"));
    }

    @Test
    void storeAndLoadRoundTripPreservesResult(@TempDir Path temp) throws Exception {
        TerrainDesignResult result = sampleResult();
        String key = cache.key("openai:model-x", "request".getBytes(StandardCharsets.UTF_8), List.of());
        cache.store(temp, key, result);
        TerrainDesignResult loaded = cache.load(temp, key).orElseThrow();
        assertEquals(result.providerId(), loaded.providerId());
        assertEquals(result.modelId(), loaded.modelId());
        assertEquals(result.responseId(), loaded.responseId());
        assertEquals(result.usage(), loaded.usage());
        assertEquals(codec.writeJsonString(result.intent()), codec.writeJsonString(loaded.intent()));
    }

    @Test
    void keyDistinguishesModelRequestBytesAndImages(@TempDir Path temp) {
        byte[] request = "request".getBytes(StandardCharsets.UTF_8);
        String base = cache.key("openai:model-x", request, List.of());
        assertNotEquals(base, cache.key("openai:model-y", request, List.of()));
        assertNotEquals(base, cache.key("openai:model-x",
                "request2".getBytes(StandardCharsets.UTF_8), List.of()));
        assertTrue(cache.load(temp, base).isEmpty());
    }

    @Test
    void corruptedTamperedOrForeignEntriesAreMisses(@TempDir Path temp) throws Exception {
        TerrainDesignResult result = sampleResult();
        String key = cache.key("openai:model-x", "request".getBytes(StandardCharsets.UTF_8), List.of());
        cache.store(temp, key, result);
        Path file = temp.resolve(key + ".json");

        String text = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, text.replace("resp-1", "resp-9"));
        // responseId is not checksummed on its own, but the key/intent checksum still bind;
        // flip the intent checksum to prove tampering is a miss, not a failure.
        Files.writeString(file, text.replaceFirst("\"intentChecksum\":\"[0-9a-f]{4}", "\"intentChecksum\":\"0000"));
        assertTrue(cache.load(temp, key).isEmpty());

        // An entry stored under a different key never satisfies this key.
        String otherKey = cache.key("anthropic:model-z", "request".getBytes(StandardCharsets.UTF_8), List.of());
        Files.writeString(temp.resolve(otherKey + ".json"), text);
        assertTrue(cache.load(temp, otherKey).isEmpty());

        // Unknown extra fields are rejected strictly.
        cache.store(temp, key, result);
        String fresh = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, "{\"extra\":true," + fresh.substring(1));
        assertTrue(cache.load(temp, key).isEmpty());
    }

    @Test
    void serviceReusesCachedResponseWithoutSecondProviderCall(@TempDir Path directory) throws Exception {
        TerrainIntent intent = codec.readTerrainIntent(
                Path.of("examples/rocky-coast/terrain-intent.json"));
        AtomicInteger calls = new AtomicInteger();
        TerrainDesignProvider counting = new TerrainDesignProvider() {
            private final FixtureTerrainDesignProvider delegate = new FixtureTerrainDesignProvider(intent);

            @Override
            public String id() {
                return "counting";
            }

            @Override
            public boolean supportsDurableResponseCache() {
                return true;
            }

            @Override
            public String cacheIdentity() {
                return "counting:model-a";
            }

            @Override
            public CompletableFuture<TerrainDesignResult> design(TerrainDesignRequest request) {
                calls.incrementAndGet();
                return delegate.design(request);
            }
        };
        Path requestPath = Path.of("examples/rocky-coast/request.yml");
        Path designs = directory.resolve("designs");
        try (GenerationExecutors executors = GenerationExecutors.create(2, 2, 8)) {
            var jobs = new FileGenerationJobRepository(directory.resolve("jobs"), executors);
            var service = new TerrainDesignApplicationService(executors, counting, jobs);
            service.start(requestPath, designs).completion().join();
            assertEquals(1, calls.get());
            service.start(requestPath, designs).completion().join();
            assertEquals(1, calls.get(), "second job must reuse the durable cached response");
        }
    }
}
