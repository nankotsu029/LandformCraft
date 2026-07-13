package com.github.nankotsu029.landformcraft.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.ai.anthropic.AnthropicTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.openai.OpenAiTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.ProviderFailureCode;
import com.github.nankotsu029.landformcraft.ai.spi.PreparedReferenceImage;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignException;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignPolicy;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignRequest;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.BlueprintCompiler;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.ReferenceImage;
import com.github.nankotsu029.landformcraft.model.ReferenceImageRole;
import com.github.nankotsu029.landformcraft.generator.TerrainGenerator;
import com.github.nankotsu029.landformcraft.preview.TerrainPreviewRenderer;
import com.github.nankotsu029.landformcraft.validation.TerrainValidator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpTerrainDesignProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LandformDataCodec codec = new LandformDataCodec();

    @Test
    void openAiUsesStructuredResponsesContractAndFeedsGenerationAndPreview(@TempDir Path directory) throws Exception {
        try (TestServer server = new TestServer(exchange -> {
            assertEquals("Bearer test-secret", exchange.getRequestHeaders().getFirst("Authorization"));
            assertTrue(exchange.getRequestHeaders().getFirst("X-Client-Request-Id")
                    .matches("[0-9a-f-]{36}"));
            JsonNode request = mapper.readTree(exchange.getRequestBody());
            assertEquals("json_schema", request.at("/text/format/type").asText());
            assertTrue(request.at("/text/format/strict").asBoolean());
            assertTrue(request.at("/text/format/schema/properties/landRatio/minimum").isMissingNode());
            assertEquals(false, request.path("store").asBoolean());
            assertEquals("input_image", request.at("/input/1/content/2/type").asText());
            assertTrue(request.at("/input/1/content/1/text").asText().contains("TOP_DOWN_SKETCH"));
            assertTrue(request.at("/input/1/content/2/image_url").asText().startsWith(
                    "data:image/png;base64,"
            ));
            assertTrue(!request.toString().contains("private/map.png"));
            respond(exchange, 200, openAiResponse(validIntentJson()));
        }); GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var provider = new OpenAiTerrainDesignProvider(
                    executors, "test-secret", "gpt-test", server.uri(), policy(2, 500),
                    Clock.systemUTC(), HttpClient.newHttpClient()
            );

            var result = provider.design(imageRequest()).join();

            assertEquals("openai", result.providerId());
            assertEquals("gpt-test-response", result.modelId());
            assertEquals(18, result.usage().totalTokens());
            assertEquals(1, result.attempts());
            assertEquals("荒い岩礁と遠浅の砂浜が共存する海岸", result.intent().theme());
            var plan = new TerrainGenerator().generate(
                    new BlueprintCompiler().compile(imageRequest().generationRequest(), result.intent(), 0),
                    () -> false
            );
            var validation = new TerrainValidator().validate(plan);
            assertTrue(validation.isValid(), () -> validation.issues().toString());
            assertEquals(8, new TerrainPreviewRenderer().render(
                    plan, validation, directory.resolve("preview"), () -> false
            ).files().size());
            assertTrue(provider.submitsReferenceImages());
        }
    }

    @Test
    void anthropicUsesOutputConfigAndParsesTextBlock() throws Exception {
        try (TestServer server = new TestServer(exchange -> {
            assertEquals("2023-06-01", exchange.getRequestHeaders().getFirst("anthropic-version"));
            JsonNode request = mapper.readTree(exchange.getRequestBody());
            assertEquals("json_schema", request.at("/output_config/format/type").asText());
            assertEquals("image", request.at("/messages/0/content/2/type").asText());
            assertEquals("image/png", request.at("/messages/0/content/2/source/media_type").asText());
            assertTrue(request.at("/messages/0/content/1/text").asText().contains("TOP_DOWN_SKETCH"));
            assertTrue(!request.toString().contains("private/map.png"));
            respond(exchange, 200, anthropicResponse(validIntentJson()));
        }); GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var provider = new AnthropicTerrainDesignProvider(
                    executors, "test-secret", "claude-test", server.uri(), policy(2, 500),
                    Clock.systemUTC(), HttpClient.newHttpClient()
            );

            var result = provider.design(imageRequest()).join();

            assertEquals("anthropic", result.providerId());
            assertEquals(18, result.usage().totalTokens());
            assertEquals("msg_test", result.responseId());
        }
    }

    @Test
    void retries429ThenSucceedsAndReportsAttempts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (TestServer server = new TestServer(exchange -> {
            if (calls.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                respond(exchange, 429, "{\"error\":{\"type\":\"rate_limit_error\"}}");
            } else {
                respond(exchange, 200, openAiResponse(validIntentJson()));
            }
        }); GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var provider = openAi(executors, server.uri(), policy(2, 500));

            var result = provider.design(request()).join();

            assertEquals(2, result.attempts());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void reportsExhaustedServerErrorsWithoutResponseBodyLeak() throws Exception {
        try (TestServer server = new TestServer(exchange -> respond(
                exchange, 503, "{\"error\":{\"type\":\"api_error\",\"message\":\"private prompt\"}}"
        )); GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var provider = openAi(executors, server.uri(), policy(2, 500));

            CompletionException thrown = assertThrows(
                    CompletionException.class, () -> provider.design(request()).join()
            );
            TerrainDesignException failure = (TerrainDesignException) thrown.getCause();
            assertEquals(ProviderFailureCode.SERVER_ERROR, failure.code());
            assertEquals(2, failure.attempts());
            assertTrue(!failure.getMessage().contains("private prompt"));
        }
    }

    @Test
    void rejectsNonRetryableClientErrorAfterOneAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (TestServer server = new TestServer(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 400, "{\"error\":{\"type\":\"invalid_request_error\"}}");
        }); GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var provider = openAi(executors, server.uri(), policy(3, 500));

            CompletionException thrown = assertThrows(
                    CompletionException.class, () -> provider.design(request()).join()
            );

            assertEquals(ProviderFailureCode.INVALID_REQUEST,
                    ((TerrainDesignException) thrown.getCause()).code());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void rejectsOversizedResponseBeforeJsonParsing() throws Exception {
        String oversized = "x".repeat(
                com.github.nankotsu029.landformcraft.ai.http.RetriableJsonHttpClient.MAX_RESPONSE_BYTES + 1
        );
        try (TestServer server = new TestServer(exchange -> respond(exchange, 200, oversized));
             GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var provider = openAi(executors, server.uri(), policy(1, 1_000));

            CompletionException thrown = assertThrows(
                    CompletionException.class, () -> provider.design(request()).join()
            );

            assertEquals(ProviderFailureCode.INVALID_RESPONSE,
                    ((TerrainDesignException) thrown.getCause()).code());
        }
    }

    @Test
    void timeoutIsBoundedAndCancellationInterruptsTransport() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        try (TestServer server = new TestServer(exchange -> {
            entered.countDown();
            try {
                Thread.sleep(Duration.ofSeconds(5));
                respond(exchange, 200, openAiResponse(validIntentJson()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }); GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var timeoutProvider = openAi(executors, server.uri(), policy(1, 30));
            CompletionException timeout = assertThrows(
                    CompletionException.class, () -> timeoutProvider.design(request()).join()
            );
            assertEquals(ProviderFailureCode.TIMEOUT, ((TerrainDesignException) timeout.getCause()).code());

            var cancellingProvider = openAi(executors, server.uri(), policy(1, 5_000));
            var future = cancellingProvider.design(request());
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertTrue(future.cancel(true));
            assertTrue(future.isCancelled());
        }
    }

    private OpenAiTerrainDesignProvider openAi(
            GenerationExecutors executors, URI endpoint, TerrainDesignPolicy policy
    ) {
        return new OpenAiTerrainDesignProvider(
                executors, "test-secret", "gpt-test", endpoint, policy,
                Clock.systemUTC(), HttpClient.newHttpClient()
        );
    }

    private TerrainDesignRequest request() throws IOException {
        return new TerrainDesignRequest(codec.readGenerationRequest(Path.of("examples/rocky-coast/request.yml")));
    }

    private TerrainDesignRequest imageRequest() throws IOException {
        GenerationRequest base = codec.readGenerationRequest(Path.of("examples/rocky-coast/request.yml"));
        var declared = new ReferenceImage("private/map.png", ReferenceImageRole.TOP_DOWN_SKETCH);
        GenerationRequest request = new GenerationRequest(
                base.schemaVersion(), base.requestId(), base.bounds(), base.prompt(), java.util.List.of(declared),
                base.generation(), base.output()
        );
        byte[] normalized = {(byte) 0x89, 0x50, 0x4e, 0x47};
        var prepared = new PreparedReferenceImage(
                0, declared.file(), declared.role(), "image/png", 1, 1,
                Sha256.bytes(normalized), normalized
        );
        return new TerrainDesignRequest(request, java.util.List.of(prepared));
    }

    private String validIntentJson() throws IOException {
        return FilesSupport.readString(Path.of("examples/rocky-coast/terrain-intent.json"));
    }

    private TerrainDesignPolicy policy(int attempts, long timeoutMillis) {
        return new TerrainDesignPolicy(
                Duration.ofMillis(timeoutMillis), attempts, Duration.ZERO, 4096, 20, 100_000
        );
    }

    private String openAiResponse(String intent) throws IOException {
        var root = mapper.createObjectNode();
        root.put("id", "resp_test");
        root.put("model", "gpt-test-response");
        var content = root.putArray("output").addObject().put("type", "message")
                .putArray("content").addObject();
        content.put("type", "output_text");
        content.put("text", intent);
        root.putObject("usage").put("input_tokens", 10).put("output_tokens", 8).put("total_tokens", 18);
        return mapper.writeValueAsString(root);
    }

    private String anthropicResponse(String intent) throws IOException {
        var root = mapper.createObjectNode();
        root.put("id", "msg_test");
        root.put("model", "claude-test-response");
        var content = root.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", intent);
        root.putObject("usage").put("input_tokens", 10).put("output_tokens", 8);
        return mapper.writeValueAsString(root);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService executor;

        private TestServer(Handler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.createContext("/", exchange -> handler.handle(exchange));
            server.start();
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class FilesSupport {
        private FilesSupport() {
        }

        private static String readString(Path path) throws IOException {
            return java.nio.file.Files.readString(path);
        }
    }
}
