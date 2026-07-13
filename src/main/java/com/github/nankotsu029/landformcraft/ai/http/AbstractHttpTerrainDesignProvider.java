package com.github.nankotsu029.landformcraft.ai.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.ai.spi.ProviderFailureCode;
import com.github.nankotsu029.landformcraft.ai.spi.ProviderQuota;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignException;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignPolicy;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignRequest;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignResult;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainIntentPrompt;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.Sha256;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.UUID;

public abstract class AbstractHttpTerrainDesignProvider implements TerrainDesignProvider {
    protected final ObjectMapper mapper = ProviderJsonSupport.strictMapper();
    protected final TerrainDesignPolicy policy;
    private final String model;
    private final URI endpoint;
    private final GenerationExecutors executors;
    private final LandformDataCodec codec;
    private final ProviderQuota quota;
    private final RetriableJsonHttpClient transport;
    private final Clock clock;

    protected AbstractHttpTerrainDesignProvider(
            GenerationExecutors executors,
            URI endpoint,
            String model,
            TerrainDesignPolicy policy,
            Clock clock,
            HttpClient httpClient
    ) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.model = requireNonBlank(model, "model");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.codec = new LandformDataCodec();
        this.quota = new ProviderQuota(policy, clock);
        this.transport = new RetriableJsonHttpClient(
                Objects.requireNonNull(httpClient, "httpClient"), policy, quota
        );
    }

    protected final String model() {
        return model;
    }

    @Override
    public final CompletableFuture<TerrainDesignResult> design(TerrainDesignRequest request) {
        Objects.requireNonNull(request, "request");
        return executors.supplyIo(() -> execute(request));
    }

    @Override
    public final boolean submitsReferenceImages() {
        return true;
    }

    private TerrainDesignResult execute(TerrainDesignRequest request) {
        try {
            byte[] body = createRequestBody(request);
            String fingerprint = Sha256.bytes((request.operationId() + "\n" + id() + "\n" + model
                    + "\n" + TerrainIntentPrompt.VERSION + "\n" + Sha256.bytes(body))
                    .getBytes(StandardCharsets.UTF_8));
            Map<String, String> requestHeaders = new LinkedHashMap<>(headers());
            requestHeaders.putAll(correlationHeaders(request, fingerprint));
            ProviderHttpResponse response = transport.post(endpoint, requestHeaders, body);
            ProviderPayload payload = parseResponse(response.body());
            var intent = codec.readTerrainIntent(payload.intentJson(), id() + " response");
            quota.record(payload.usage());
            return new TerrainDesignResult(
                    intent,
                    id(),
                    payload.modelId(),
                    TerrainIntentPrompt.VERSION,
                    payload.responseId(),
                    payload.usage(),
                    response.attempts(),
                    clock.instant()
            );
        } catch (TerrainDesignException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new TerrainDesignException(
                    ProviderFailureCode.INVALID_RESPONSE,
                    "provider returned an invalid structured TerrainIntent",
                    0,
                    1,
                    exception
            );
        }
    }

    protected abstract Map<String, String> headers();

    /** Provider-specific request correlation. This is not represented as an exactly-once guarantee. */
    protected Map<String, String> correlationHeaders(TerrainDesignRequest request, String fingerprint) {
        return Map.of();
    }

    protected static String deterministicRequestId(String fingerprint) {
        return UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.US_ASCII)).toString();
    }

    protected abstract byte[] createRequestBody(TerrainDesignRequest request) throws IOException;

    protected abstract ProviderPayload parseResponse(byte[] responseBody) throws IOException;

    protected static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
