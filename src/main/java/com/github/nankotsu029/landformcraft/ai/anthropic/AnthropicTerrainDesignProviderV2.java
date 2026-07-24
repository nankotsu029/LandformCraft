package com.github.nankotsu029.landformcraft.ai.anthropic;

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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Anthropic Messages API adapter for TerrainIntent v2 structured output. */
public final class AnthropicTerrainDesignProviderV2 extends AbstractHttpTerrainDesignProviderV2 {
    public static final URI DEFAULT_ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    public static final String API_VERSION = "2023-06-01";

    private final String apiKey;

    public AnthropicTerrainDesignProviderV2(
            GenerationExecutors executors,
            String apiKey,
            String model,
            URI endpoint,
            TerrainDesignPolicy policy,
            Clock clock,
            HttpClient httpClient
    ) {
        super(DesignPathKindV2.ANTHROPIC, executors, endpoint, model, policy, clock, httpClient);
        this.apiKey = requireNonBlank(apiKey, "apiKey");
    }

    @Override
    public String id() {
        return "anthropic-v2";
    }

    @Override
    protected Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", API_VERSION);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    @Override
    protected byte[] createRequestBody(TerrainDesignRequestV2 request) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model());
        root.put("max_tokens", policy.maxOutputTokens());
        root.put("system", TerrainIntentPromptV2.systemText());
        ObjectNode message = root.putArray("messages").addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        addText(content, TerrainIntentPromptV2.userText(request.generationRequest()));
        addText(content, TerrainIntentPromptV2.supportSurfaceText(request.supportSurface()));
        request.images().forEach(image -> {
            addText(content, TerrainIntentPromptV2.imageRoleText(image));
            ObjectNode imageContent = content.addObject();
            imageContent.put("type", "image");
            ObjectNode source = imageContent.putObject("source");
            source.put("type", "base64");
            source.put("media_type", image.mediaType());
            source.put("data", Base64.getEncoder().encodeToString(image.content()));
        });
        ObjectNode format = root.putObject("output_config").putObject("format");
        format.put("type", "json_schema");
        format.set("schema", ProviderJsonSupport.terrainIntentV2Schema(mapper));
        byte[] body = mapper.writeValueAsBytes(root);
        String asText = new String(body, StandardCharsets.UTF_8);
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
            throw new IOException("Anthropic response must be a JSON object");
        }
        String text = "";
        for (JsonNode content : root.path("content")) {
            if ("text".equals(content.path("type").asText())) {
                text = content.path("text").asText("");
                break;
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
                        usage.path("input_tokens").asLong(0) + usage.path("output_tokens").asLong(0)
                )
        );
    }

    private static void addText(ArrayNode content, String text) {
        ObjectNode block = content.addObject();
        block.put("type", "text");
        block.put("text", text);
    }
}
