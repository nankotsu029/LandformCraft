package com.github.nankotsu029.landformcraft.core.v2.design;

import com.github.nankotsu029.landformcraft.ai.anthropic.AnthropicTerrainDesignProviderV2;
import com.github.nankotsu029.landformcraft.ai.openai.OpenAiTerrainDesignProviderV2;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignPolicy;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignExceptionV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignFailureCodeV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;
import com.github.nankotsu029.landformcraft.model.v2.design.SoftDraftConfirmationStateV2;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactsV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.design.ImageDraftEvidenceV2;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainDesignApplicationServiceV2Test {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void importAndFixturePathsPublishEquivalentCanonicalIntents(@TempDir Path root) throws Exception {
        Path workspace = copyAzureCoast(root.resolve("import-fixture"));
        try (GenerationExecutors executors = GenerationExecutors.create(2, 1, 4)) {
            TerrainDesignApplicationServiceV2 service = new TerrainDesignApplicationServiceV2(executors, null);
            assertTrue(service.isRelease2Path());

            DesignArtifactsV2 imported = service.design(new DesignDispatchRequestV2(
                    2,
                    DesignPathKindV2.IMPORT,
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                    workspace.resolve("request-v2.json"),
                    root.resolve("designs-import"),
                    "terrain-intent-v2.json",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            )).get(30, TimeUnit.SECONDS);

            DesignArtifactsV2 fixture = service.design(new DesignDispatchRequestV2(
                    2,
                    DesignPathKindV2.FIXTURE,
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                    workspace.resolve("request-v2.json"),
                    root.resolve("designs-fixture"),
                    "terrain-intent-v2.json",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            )).get(30, TimeUnit.SECONDS);

            assertEquals(
                    codec.terrainIntentChecksum(imported.intent()),
                    codec.terrainIntentChecksum(fixture.intent()));
            assertEquals("imported-json-v2", imported.audit().providerId());
            assertEquals("fixture-v2", fixture.audit().providerId());
            assertTrue(Files.isRegularFile(imported.directory().resolve("terrain-intent-v2.json")));
            assertTrue(Files.isRegularFile(imported.directory().resolve("audit-v2.json")));
            assertFalse(Files.exists(imported.directory().resolve("image-draft-evidence-v2.json")));
        }
    }

    @Test
    void openAiAndAnthropicPathsProduceSameCanonicalIntentChecksum(@TempDir Path root) throws Exception {
        Path workspace = copyAzureCoast(root.resolve("providers"));
        String intentJson = Files.readString(workspace.resolve("terrain-intent-v2.json"));
        try (GenerationExecutors executors = GenerationExecutors.create(2, 1, 4);
             TestServer openAiServer = new TestServer(exchange -> respondOpenAi(exchange, intentJson));
             TestServer anthropicServer = new TestServer(exchange -> respondAnthropic(exchange, intentJson))) {
            TerrainDesignPolicy policy = new TerrainDesignPolicy(
                    Duration.ofSeconds(5), 2, Duration.ofMillis(10), 4_096, 20, 100_000);
            TerrainDesignApplicationServiceV2 service = new TerrainDesignApplicationServiceV2(
                    executors,
                    (path, model) -> switch (path) {
                        case OPENAI -> new OpenAiTerrainDesignProviderV2(
                                executors, "test-secret", model, openAiServer.uri(), policy,
                                Clock.systemUTC(), HttpClient.newHttpClient());
                        case ANTHROPIC -> new AnthropicTerrainDesignProviderV2(
                                executors, "test-secret", model, anthropicServer.uri(), policy,
                                Clock.systemUTC(), HttpClient.newHttpClient());
                        default -> throw new IllegalArgumentException(path.name());
                    });

            DesignArtifactsV2 openAi = service.design(new DesignDispatchRequestV2(
                    2,
                    DesignPathKindV2.OPENAI,
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                    workspace.resolve("request-v2.json"),
                    root.resolve("designs-openai"),
                    "gpt-test-v2",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            )).get(30, TimeUnit.SECONDS);

            DesignArtifactsV2 anthropic = service.design(new DesignDispatchRequestV2(
                    2,
                    DesignPathKindV2.ANTHROPIC,
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                    workspace.resolve("request-v2.json"),
                    root.resolve("designs-anthropic"),
                    "claude-test-v2",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            )).get(30, TimeUnit.SECONDS);

            assertEquals(
                    codec.terrainIntentChecksum(openAi.intent()),
                    codec.terrainIntentChecksum(anthropic.intent()));
            assertEquals("openai-v2", openAi.audit().providerId());
            assertEquals("anthropic-v2", anthropic.audit().providerId());
            String openAiBody = Files.readString(openAi.directory().resolve("audit-v2.json"));
            assertFalse(openAiBody.toLowerCase(Locale.ROOT).contains("test-secret"));
            assertFalse(openAiBody.toLowerCase(Locale.ROOT).contains("authorization"));
        }
    }

    @Test
    void referenceImageDraftPathKeepsSoftConfirmationAndForbidsHardPromotion(@TempDir Path root)
            throws Exception {
        Path workspace = copyMinimalNoMaps(root.resolve("draft"));
        int width = 4;
        int length = 4;
        int[] pixels = new int[width * length];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF2040C0; // blue-dominant water
        }
        String sourceChecksum = Sha256.bytes(("draft-source-" + width + "x" + length)
                .getBytes(StandardCharsets.UTF_8));
        try (GenerationExecutors executors = GenerationExecutors.create(2, 1, 4)) {
            TerrainDesignApplicationServiceV2 service = new TerrainDesignApplicationServiceV2(executors, null);
            DesignArtifactsV2 artifacts = service.design(new DesignDispatchRequestV2(
                    2,
                    DesignPathKindV2.REFERENCE_IMAGE_DRAFT,
                    EnumSet.of(DesignCapabilityV2.REFERENCE_IMAGE_SOFT_DRAFT),
                    workspace.resolve("request-v2.json"),
                    root.resolve("designs-draft"),
                    "image-land-water-extract-v1",
                    Optional.of(workspace.resolve("terrain-intent-v2.json")),
                    Optional.of(new SoftDraftPixelInputV2(width, length, pixels, sourceChecksum)),
                    Optional.empty()
            )).get(30, TimeUnit.SECONDS);

            assertTrue(artifacts.draftEvidence().isPresent());
            ImageDraftEvidenceV2 evidence = artifacts.draftEvidence().orElseThrow();
            assertEquals(SoftDraftConfirmationStateV2.UNCONFIRMED, evidence.confirmationState());
            assertTrue(artifacts.intent().mapReferences().isEmpty());

            ReferenceImageSoftDraftServiceV2 soft = new ReferenceImageSoftDraftServiceV2();
            ImageDraftEvidenceV2 confirmed = soft.confirmSoft(evidence);
            assertEquals(SoftDraftConfirmationStateV2.CONFIRMED_SOFT, confirmed.confirmationState());
            DesignExceptionV2 hard = assertThrows(
                    DesignExceptionV2.class,
                    () -> soft.forbidHardPromotion(confirmed));
            assertEquals(DesignFailureCodeV2.HARD_PROMOTION_FORBIDDEN, hard.code());
        }
    }

    @Test
    void unsupportedModelAndPathTraversalAreRejected(@TempDir Path root) throws Exception {
        Path workspace = copyAzureCoast(root.resolve("reject"));
        try (GenerationExecutors executors = GenerationExecutors.create(2, 1, 4)) {
            TerrainDesignApplicationServiceV2 service = new TerrainDesignApplicationServiceV2(executors, null);
            DesignExceptionV2 unsupported = unwrapDesign(() -> service.design(new DesignDispatchRequestV2(
                    2,
                    DesignPathKindV2.OPENAI,
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                    workspace.resolve("request-v2.json"),
                    root.resolve("designs-bad-model"),
                    "not-a-supported-model",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            )).join());
            assertEquals(DesignFailureCodeV2.UNSUPPORTED_MODEL, unsupported.code());

            DesignExceptionV2 traversal = unwrapDesign(() -> service.design(new DesignDispatchRequestV2(
                    2,
                    DesignPathKindV2.IMPORT,
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                    workspace.resolve("request-v2.json"),
                    root.resolve("designs-traversal"),
                    "../secret/intent.json",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            )).join());
            assertEquals(DesignFailureCodeV2.PATH_SECURITY, traversal.code());
        }
    }

    /**
     * V2-19-03: a request declaring reference images used to fail before any provider was called,
     * because the orchestrator handed the provider an empty image list. Every design path shares
     * that construction, so the offline fixture path is the cheapest place to pin the repair.
     */
    @Test
    void aRequestDeclaringReferenceImagesReachesTheProvider(@TempDir Path root) throws Exception {
        Path workspace = copyObliqueMultiView(root.resolve("images"));
        try (GenerationExecutors executors = GenerationExecutors.create(2, 1, 4)) {
            TerrainDesignApplicationServiceV2 service = new TerrainDesignApplicationServiceV2(executors, null);
            DesignArtifactsV2 artifacts = service.design(new DesignDispatchRequestV2(
                    2,
                    DesignPathKindV2.FIXTURE,
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                    workspace.resolve("request-v2.json"),
                    root.resolve("designs-images"),
                    "terrain-intent-v2.json",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            )).get(30, TimeUnit.SECONDS);

            assertEquals("oblique-multi-view-64", artifacts.audit().requestId());
            assertTrue(Files.isRegularFile(artifacts.directory().resolve("terrain-intent-v2.json")));
            assertTrue(artifacts.intent().mapReferences().isEmpty(),
                    "reference images stay soft cues and never become HARD map references");
        }
    }

    /** The declared image must arrive as re-encoded PNG bytes, with no path and no source metadata. */
    @Test
    void theProviderPayloadCarriesPreparedImagesWithoutPathsOrSourceMetadata(@TempDir Path root)
            throws Exception {
        Path workspace = copyObliqueMultiView(root.resolve("provider-images"));
        String secret = "camera-serial-and-gps-fix";
        Path mood = workspace.resolve("references/mood.png");
        Files.write(mood, insertAfterIhdr(
                Files.readAllBytes(mood),
                pngChunk("tEXt", ("Comment " + secret).getBytes(StandardCharsets.ISO_8859_1))));
        // The pinned digest belongs to the committed example file, which this test just rewrote.
        Path requestPath = workspace.resolve("request-v2.json");
        Files.writeString(
                requestPath,
                Files.readString(requestPath, StandardCharsets.UTF_8)
                        .replaceAll("\"expectedSha256\": \"[0-9a-f]{64}\"", "\"expectedSha256\": \""
                                + Sha256.file(mood) + "\""),
                StandardCharsets.UTF_8);
        String intentJson = Files.readString(workspace.resolve("terrain-intent-v2.json"));
        AtomicReference<String> captured = new AtomicReference<>("");

        try (GenerationExecutors executors = GenerationExecutors.create(2, 1, 4);
             TestServer anthropicServer = new TestServer(exchange -> {
                 captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                 respond(exchange, 200, """
                         {
                           "id": "msg_anthropic_v2",
                           "model": "claude-test-v2",
                           "content": [{ "type": "text", "text": %s }],
                           "usage": { "input_tokens": 11, "output_tokens": 21 }
                         }
                         """.formatted(jsonString(intentJson)));
             })) {
            TerrainDesignPolicy policy = new TerrainDesignPolicy(
                    Duration.ofSeconds(5), 2, Duration.ofMillis(10), 4_096, 20, 100_000);
            TerrainDesignApplicationServiceV2 service = new TerrainDesignApplicationServiceV2(
                    executors,
                    (path, model) -> new AnthropicTerrainDesignProviderV2(
                            executors, "test-secret", model, anthropicServer.uri(), policy,
                            Clock.systemUTC(), HttpClient.newHttpClient()));

            DesignArtifactsV2 artifacts = service.design(new DesignDispatchRequestV2(
                    2,
                    DesignPathKindV2.ANTHROPIC,
                    EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                    requestPath,
                    root.resolve("designs-provider-images"),
                    "claude-test-v2",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            )).get(30, TimeUnit.SECONDS);
            assertEquals("anthropic-v2", artifacts.audit().providerId());
        }

        String body = captured.get();
        assertEquals(4, countOccurrences(body, "\"media_type\":\"image/png\""), body.length() + " byte body");
        assertTrue(body.contains("Reference image role MULTI_VIEW_REFERENCE"), "role text is missing");
        assertFalse(body.contains("references/mood.png"), "the filesystem path must not be sent");
        assertFalse(body.contains(Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8))),
                "source metadata must not survive re-encoding");
        assertFalse(body.contains(secret));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int index = haystack.indexOf(needle); index >= 0; index = haystack.indexOf(needle, index + 1)) {
            count++;
        }
        return count;
    }

    private static DesignExceptionV2 unwrapDesign(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected DesignExceptionV2");
        } catch (DesignExceptionV2 exception) {
            return exception;
        } catch (RuntimeException exception) {
            Throwable current = exception;
            while (current.getCause() != null
                    && (current instanceof java.util.concurrent.CompletionException
                    || current instanceof java.io.UncheckedIOException)) {
                current = current.getCause();
            }
            if (current instanceof DesignExceptionV2 design) {
                return design;
            }
            throw exception;
        }
    }

    @Test
    void manualConstraintPathBindsCanonicalMapsWithoutAi(@TempDir Path root) throws Exception {
        Path workspace = root.resolve("manual");
        Files.createDirectories(workspace.resolve("maps"));
        Path land = writeGray(workspace.resolve("maps/land-water-u8.png"), false,
                new int[][]{{0, 0, 0, 0}, {0, 1, 1, 0}, {0, 1, 1, 0}, {0, 0, 0, 0}});
        Path height = writeGray(workspace.resolve("maps/height-u16.png"), true,
                new int[][]{{38_250, 38_750, 39_250, 39_750}, {39_250, 40_500, 41_500, 39_250},
                        {39_750, 41_250, 42_500, 39_750}, {38_250, 38_750, 39_250, 39_750}});
        Path zones = writeGray(workspace.resolve("maps/zones-u16.png"), true,
                new int[][]{{10, 10, 10, 10}, {10, 20, 20, 10}, {10, 20, 20, 10}, {10, 10, 10, 10}});
        String requestJson = Files.readString(Path.of(
                        "examples/v2/manual-constraint-island/request-v2.json"))
                .replace("a".repeat(64), Sha256.file(land))
                .replace("b".repeat(64), Sha256.file(height))
                .replace("c".repeat(64), Sha256.file(zones));
        Path requestPath = workspace.resolve("request-v2.json");
        Files.writeString(requestPath, requestJson, StandardCharsets.UTF_8);
        Files.copy(
                Path.of("examples/v2/manual-constraint-island/terrain-intent-v2.json"),
                workspace.resolve("terrain-intent-v2.json"));

        try (GenerationExecutors executors = GenerationExecutors.create(2, 1, 4)) {
            TerrainDesignApplicationServiceV2 service = new TerrainDesignApplicationServiceV2(executors, null);
            DesignArtifactsV2 artifacts = service.design(new DesignDispatchRequestV2(
                    2,
                    DesignPathKindV2.MANUAL_CONSTRAINT,
                    EnumSet.of(DesignCapabilityV2.MANUAL_CONSTRAINT_BUNDLE),
                    requestPath,
                    root.resolve("designs-manual"),
                    "manual-constraint-bundle-v1",
                    Optional.of(workspace.resolve("terrain-intent-v2.json")),
                    Optional.empty(),
                    Optional.empty()
            )).get(60, TimeUnit.SECONDS);

            assertFalse(artifacts.intent().mapReferences().isEmpty());
            assertEquals("manual-constraint-v2", artifacts.audit().providerId());
            assertEquals(DesignPathKindV2.MANUAL_CONSTRAINT, artifacts.audit().pathKind());
            assertTrue(artifacts.draftEvidence().isEmpty());
        }
    }

    private static Path writeGray(Path path, boolean sixteenBit, int[][] samples) throws IOException {
        Files.createDirectories(path.getParent());
        int height = samples.length;
        int width = samples[0].length;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                width,
                height,
                sixteenBit
                        ? java.awt.image.BufferedImage.TYPE_USHORT_GRAY
                        : java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int sample = samples[z][x];
                if (sixteenBit) {
                    image.getRaster().setSample(x, z, 0, sample);
                } else {
                    image.getRaster().setSample(x, z, 0, sample);
                }
            }
        }
        javax.imageio.ImageIO.write(image, "png", path.toFile());
        return path;
    }

    private Path copyAzureCoast(Path target) throws IOException {
        Files.createDirectories(target);
        Files.copy(
                Path.of("examples/v2/diagnostic/azure-coast.request-v2.json"),
                target.resolve("request-v2.json"));
        Files.copy(
                Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json"),
                target.resolve("terrain-intent-v2.json"));
        return target;
    }

    private Path copyObliqueMultiView(Path target) throws IOException {
        Files.createDirectories(target.resolve("references"));
        Files.copy(
                Path.of("examples/v2/diagnostic/oblique-multi-view.request-v2.json"),
                target.resolve("request-v2.json"));
        Files.copy(
                Path.of("examples/v2/diagnostic/oblique-multi-view.terrain-intent-v2.json"),
                target.resolve("terrain-intent-v2.json"));
        for (String imageId : ReferenceImageExampleFixtureV2.IMAGE_IDS) {
            Files.copy(
                    ReferenceImageExampleFixtureV2.EXAMPLE_DIRECTORY.resolve(imageId + ".png"),
                    target.resolve("references").resolve(imageId + ".png"));
        }
        return target;
    }

    private static byte[] insertAfterIhdr(byte[] png, byte[] chunk) {
        int position = 8;
        while (position + 12 <= png.length) {
            int length = java.nio.ByteBuffer.wrap(png, position, 4).getInt();
            String type = new String(png, position + 4, 4, StandardCharsets.US_ASCII);
            int next = position + 12 + length;
            if ("IHDR".equals(type)) {
                byte[] result = new byte[png.length + chunk.length];
                System.arraycopy(png, 0, result, 0, next);
                System.arraycopy(chunk, 0, result, next, chunk.length);
                System.arraycopy(png, next, result, next + chunk.length, png.length - next);
                return result;
            }
            position = next;
        }
        throw new IllegalStateException("IHDR not found");
    }

    private static byte[] pngChunk(String type, byte[] payload) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        output.write(java.nio.ByteBuffer.allocate(4).putInt(payload.length).array(), 0, 4);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        output.write(typeBytes, 0, 4);
        output.write(payload, 0, payload.length);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(typeBytes);
        crc.update(payload);
        output.write(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array(), 0, 4);
        return output.toByteArray();
    }

    private Path copyMinimalNoMaps(Path target) throws IOException {
        Files.createDirectories(target);
        TerrainIntentV2 source = codec.readTerrainIntent(
                Path.of("examples/v2/diagnostic/scenarios/overhang.terrain-intent-v2.json"));
        TerrainIntentV2 intent = new TerrainIntentV2(
                source.intentVersion(),
                "soft-draft-design-v2",
                source.theme(),
                source.coordinateSystem(),
                source.features(),
                source.relations(),
                source.constraints(),
                source.environment(),
                java.util.List.of(),
                source.structures(),
                source.provenance());
        String request = """
                {
                  "requestVersion": 2,
                  "requestId": "soft-draft-design-v2",
                  "bounds": { "width": 64, "length": 64, "minY": 32, "maxY": 96, "waterLevel": 50 },
                  "prompt": "Soft draft only.",
                  "referenceImages": [],
                  "constraintMaps": [],
                  "generation": { "globalSeed": 1, "tileSize": 64 },
                  "constraintMapBudget": {
                    "maximumMapCount": 8,
                    "maximumTotalSourceBytes": 33554432,
                    "maximumDecodedBytes": 33554432,
                    "maximumPixels": 16000000,
                    "maximumArtifactBytes": 67108864,
                    "maximumResidentBytes": 100663296
                  }
                }
                """;
        Files.writeString(target.resolve("request-v2.json"), request, StandardCharsets.UTF_8);
        codec.writeTerrainIntent(target.resolve("terrain-intent-v2.json"), intent);
        // re-read request through codec to ensure schema validity; rewrite if needed
        codec.readGenerationRequest(target.resolve("request-v2.json"));
        return target;
    }

    private static void respondOpenAi(HttpExchange exchange, String intentJson) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        assertFalse(body.contains("test-secret"));
        assertFalse(body.contains("/private/"));
        assertTrue(body.contains("terrain_intent_v2"));
        String response = """
                {
                  "id": "resp_openai_v2",
                  "model": "gpt-test-v2",
                  "output": [{
                    "type": "message",
                    "content": [{ "type": "output_text", "text": %s }]
                  }],
                  "usage": { "input_tokens": 10, "output_tokens": 20, "total_tokens": 30 }
                }
                """.formatted(jsonString(intentJson));
        respond(exchange, 200, response);
    }

    private static void respondAnthropic(HttpExchange exchange, String intentJson) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        assertFalse(body.contains("test-secret"));
        String response = """
                {
                  "id": "msg_anthropic_v2",
                  "model": "claude-test-v2",
                  "content": [{ "type": "text", "text": %s }],
                  "usage": { "input_tokens": 11, "output_tokens": 21 }
                }
                """.formatted(jsonString(intentJson));
        respond(exchange, 200, response);
    }

    private static String jsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                + "\"";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;

        private TestServer(Handler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> handler.handle(exchange));
            server.start();
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
