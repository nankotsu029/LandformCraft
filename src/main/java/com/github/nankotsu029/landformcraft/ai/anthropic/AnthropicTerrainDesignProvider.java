package com.github.nankotsu029.landformcraft.ai.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.ai.http.AbstractHttpTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.http.ProviderJsonSupport;
import com.github.nankotsu029.landformcraft.ai.http.ProviderPayload;
import com.github.nankotsu029.landformcraft.model.ProviderUsage;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignPolicy;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignRequest;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainIntentPrompt;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Claude Messages API adapter using output_config.format JSON Schema output. */
public final class AnthropicTerrainDesignProvider extends AbstractHttpTerrainDesignProvider {
    public static final URI DEFAULT_ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    public static final String API_VERSION = "2023-06-01";

    private final String apiKey;

    public AnthropicTerrainDesignProvider(GenerationExecutors executors, String apiKey, String model) {
        this(
                executors, apiKey, model, DEFAULT_ENDPOINT, TerrainDesignPolicy.defaults(),
                Clock.systemUTC(), HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
        );
    }

    public AnthropicTerrainDesignProvider(
            GenerationExecutors executors,
            String apiKey,
            String model,
            URI endpoint,
            TerrainDesignPolicy policy,
            Clock clock,
            HttpClient httpClient
    ) {
        super(executors, endpoint, model, policy, clock, httpClient);
        this.apiKey = requireNonBlank(apiKey, "apiKey");
    }

    @Override
    public String id() {
        return "anthropic";
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
    protected byte[] createRequestBody(TerrainDesignRequest request) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model());
        root.put("max_tokens", policy.maxOutputTokens());
        root.put("system", TerrainIntentPrompt.systemText());
        ObjectNode message = root.putArray("messages").addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        addText(content, TerrainIntentPrompt.userText(request.generationRequest()));
        request.images().forEach(image -> {
            addText(content, TerrainIntentPrompt.imageRoleText(image));
            ObjectNode imageContent = content.addObject();
            imageContent.put("type", "image");
            ObjectNode source = imageContent.putObject("source");
            source.put("type", "base64");
            source.put("media_type", image.mediaType());
            source.put("data", Base64.getEncoder().encodeToString(image.content()));
        });
        ObjectNode format = root.putObject("output_config").putObject("format");
        format.put("type", "json_schema");
        format.set("schema", ProviderJsonSupport.terrainIntentSchema(mapper));
        return mapper.writeValueAsBytes(root);
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
        long inputTokens = usage.path("input_tokens").asLong(-1);
        long outputTokens = usage.path("output_tokens").asLong(-1);
        return new ProviderPayload(
                root.path("id").asText(""),
                root.path("model").asText(""),
                text,
                new ProviderUsage(inputTokens, outputTokens, Math.addExact(inputTokens, outputTokens))
        );
    }

    private static void addText(ArrayNode content, String text) {
        ObjectNode block = content.addObject();
        block.put("type", "text");
        block.put("text", text);
    }
}
