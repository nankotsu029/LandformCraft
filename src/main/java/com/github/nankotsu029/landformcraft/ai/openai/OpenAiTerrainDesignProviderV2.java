package com.github.nankotsu029.landformcraft.ai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.ai.http.ProviderJsonSupport;
import com.github.nankotsu029.landformcraft.ai.http.ProviderPayload;
import com.github.nankotsu029.landformcraft.ai.http.v2.AbstractHttpTerrainDesignProviderV2;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignPolicy;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.TerrainDesignRequestV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.TerrainIntentPromptV2;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.model.ProviderUsage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** OpenAI Responses API adapter for TerrainIntent v2 structured output. */
public final class OpenAiTerrainDesignProviderV2 extends AbstractHttpTerrainDesignProviderV2 {
    public static final URI DEFAULT_ENDPOINT = URI.create("https://api.openai.com/v1/responses");

    private final String apiKey;

    public OpenAiTerrainDesignProviderV2(
            GenerationExecutors executors,
            String apiKey,
            String model,
            URI endpoint,
            TerrainDesignPolicy policy,
            Clock clock,
            HttpClient httpClient
    ) {
        super(DesignPathKindV2.OPENAI, executors, endpoint, model, policy, clock, httpClient);
        this.apiKey = requireNonBlank(apiKey, "apiKey");
    }

    @Override
    public String id() {
        return "openai-v2";
    }

    @Override
    protected Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    @Override
    protected Map<String, String> correlationHeaders(TerrainDesignRequestV2 request, String fingerprint) {
        return Map.of("X-Client-Request-Id", deterministicRequestId(fingerprint));
    }

    @Override
    protected byte[] createRequestBody(TerrainDesignRequestV2 request) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model());
        root.put("store", false);
        root.put("max_output_tokens", policy.maxOutputTokens());
        ArrayNode input = root.putArray("input");
        addInput(input, "system", TerrainIntentPromptV2.systemText());
        ObjectNode user = input.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        addText(content, TerrainIntentPromptV2.userText(request.generationRequest()));
        addText(content, TerrainIntentPromptV2.supportSurfaceText(request.supportSurface()));
        request.images().forEach(image -> {
            addText(content, TerrainIntentPromptV2.imageRoleText(image));
            ObjectNode imageContent = content.addObject();
            imageContent.put("type", "input_image");
            imageContent.put(
                    "image_url",
                    "data:" + image.mediaType() + ";base64," + Base64.getEncoder().encodeToString(image.content())
            );
            imageContent.put("detail", "high");
            if (image.sourceFile().contains("..") || image.sourceFile().startsWith("/")) {
                throw new IllegalArgumentException("image path must remain portable");
            }
        });
        ObjectNode format = root.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "terrain_intent_v2");
        format.put("strict", true);
        format.set("schema", ProviderJsonSupport.terrainIntentV2Schema(mapper));
        byte[] body = mapper.writeValueAsBytes(root);
        String asText = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        for (var image : request.images()) {
            if (asText.contains(image.sourceFile())) {
                throw new IllegalArgumentException("provider body must not include raw image paths");
            }
        }
        return body;
    }

    @Override
    protected ProviderPayload parseResponse(byte[] responseBody) throws IOException {
        JsonNode root = mapper.readTree(responseBody);
        if (root == null || !root.isObject()) {
            throw new IOException("OpenAI response must be a JSON object");
        }
        String text = "";
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    text = content.path("text").asText("");
                    break;
                }
            }
        }
        JsonNode usage = root.path("usage");
        return new ProviderPayload(
                root.path("id").asText(""),
                root.path("model").asText(""),
                text,
                new ProviderUsage(
                        usage.path("input_tokens").asLong(-1),
                        usage.path("output_tokens").asLong(-1),
                        usage.path("total_tokens").asLong(-1)
                )
        );
    }

    private static void addInput(ArrayNode input, String role, String text) {
        ObjectNode message = input.addObject();
        message.put("role", role);
        addText(message.putArray("content"), text);
    }

    private static void addText(ArrayNode content, String text) {
        ObjectNode block = content.addObject();
        block.put("type", "input_text");
        block.put("text", text);
    }
}
