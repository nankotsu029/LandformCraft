package com.github.nankotsu029.landformcraft.ai.http.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.ai.http.ProviderHttpResponse;
import com.github.nankotsu029.landformcraft.ai.http.ProviderJsonSupport;
import com.github.nankotsu029.landformcraft.ai.http.ProviderPayload;
import com.github.nankotsu029.landformcraft.ai.http.RetriableJsonHttpClient;
import com.github.nankotsu029.landformcraft.ai.spi.ProviderFailureCode;
import com.github.nankotsu029.landformcraft.ai.spi.ProviderQuota;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignException;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignPolicy;
import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignCapabilityNegotiatorV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignExceptionV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignFailureCodeV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.ProviderCapabilityCatalogV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.ProviderCapabilityDescriptorV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.TerrainDesignProviderV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.TerrainDesignRequestV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.TerrainDesignResultV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.TerrainIntentPromptV2;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractHttpTerrainDesignProviderV2 implements TerrainDesignProviderV2 {
    protected final ObjectMapper mapper = ProviderJsonSupport.strictMapper();
    protected final TerrainDesignPolicy policy;
    private final String model;
    private final URI endpoint;
    private final GenerationExecutors executors;
    private final LandformV2DataCodec codec;
    private final ProviderQuota quota;
    private final RetriableJsonHttpClient transport;
    private final Clock clock;
    private final DesignPathKindV2 path;

    protected AbstractHttpTerrainDesignProviderV2(
            DesignPathKindV2 path,
            GenerationExecutors executors,
            URI endpoint,
            String model,
            TerrainDesignPolicy policy,
            Clock clock,
            HttpClient httpClient
    ) {
        this.path = Objects.requireNonNull(path, "path");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.model = requireNonBlank(model, "model");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.codec = new LandformV2DataCodec();
        this.quota = new ProviderQuota(policy, clock);
        this.transport = new RetriableJsonHttpClient(
                Objects.requireNonNull(httpClient, "httpClient"), policy, quota
        );
    }

    protected final String model() {
        return model;
    }

    @Override
    public final DesignPathKindV2 path() {
        return path;
    }

    @Override
    public final CompletableFuture<TerrainDesignResultV2> design(TerrainDesignRequestV2 request) {
        Objects.requireNonNull(request, "request");
        return executors.supplyIo(() -> execute(request));
    }

    @Override
    public final boolean submitsReferenceImages() {
        return true;
    }

    private TerrainDesignResultV2 execute(TerrainDesignRequestV2 request) {
        ProviderCapabilityDescriptorV2 negotiated;
        try {
            negotiated = DesignCapabilityNegotiatorV2.negotiate(
                    request.intentContractVersion(),
                    request.path(),
                    model,
                    request.requestedCapabilities());
        } catch (DesignExceptionV2 exception) {
            throw exception;
        }
        if (request.path() != path) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.CAPABILITY_MISMATCH,
                    "request path does not match provider path");
        }
        try {
            byte[] body = createRequestBody(request);
            String fingerprint = Sha256.bytes((request.operationId() + "\n" + id() + "\n" + model
                    + "\n" + TerrainIntentPromptV2.VERSION + "\n" + Sha256.bytes(body))
                    .getBytes(StandardCharsets.UTF_8));
            Map<String, String> requestHeaders = new LinkedHashMap<>(headers());
            requestHeaders.putAll(correlationHeaders(request, fingerprint));
            assertNoSecretLeak(body, requestHeaders);
            ProviderHttpResponse response = transport.post(endpoint, requestHeaders, body);
            ProviderPayload payload = parseResponse(response.body());
            quota.record(payload.usage());
            var intent = codec.readTerrainIntent(payload.intentJson(), id() + " response");
            return new TerrainDesignResultV2(
                    intent,
                    id(),
                    payload.modelId(),
                    TerrainIntentPromptV2.VERSION,
                    payload.responseId(),
                    payload.usage(),
                    response.attempts(),
                    clock.instant(),
                    negotiated.capabilities(),
                    ProviderCapabilityCatalogV2.CONTRACT_VERSION
            );
        } catch (DesignExceptionV2 exception) {
            throw exception;
        } catch (TerrainDesignException exception) {
            throw new DesignExceptionV2(
                    mapFailure(exception.code()),
                    "provider transport failed",
                    exception
            );
        } catch (IOException | IllegalArgumentException | StructuredDataValidationException exception) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.INVALID_RESPONSE,
                    "provider returned an invalid structured TerrainIntent v2",
                    exception
            );
        }
    }

    private static void assertNoSecretLeak(byte[] body, Map<String, String> headers) {
        String payload = new String(body, StandardCharsets.UTF_8);
        if (payload.contains("Bearer ") || payload.contains("x-api-key")) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.SECRET_REDACTION,
                    "provider request body must not embed credentials");
        }
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String name = header.getKey().toLowerCase(java.util.Locale.ROOT);
            if (!name.equals("authorization") && !name.equals("x-api-key")
                    && header.getValue() != null
                    && (header.getValue().startsWith("sk-") || header.getValue().contains("secret"))) {
                throw new DesignExceptionV2(
                        DesignFailureCodeV2.SECRET_REDACTION,
                        "unexpected secret-bearing header");
            }
        }
    }

    private static DesignFailureCodeV2 mapFailure(ProviderFailureCode code) {
        return switch (code) {
            case AUTHENTICATION, INVALID_REQUEST -> DesignFailureCodeV2.INVALID_REQUEST;
            case RATE_LIMITED, LOCAL_RATE_LIMIT, TOKEN_BUDGET_EXCEEDED -> DesignFailureCodeV2.BUDGET_EXCEEDED;
            case TIMEOUT, SERVER_ERROR, TRANSPORT_ERROR -> DesignFailureCodeV2.PROVIDER_TRANSPORT;
            case INVALID_RESPONSE -> DesignFailureCodeV2.INVALID_RESPONSE;
        };
    }

    protected abstract Map<String, String> headers();

    protected Map<String, String> correlationHeaders(TerrainDesignRequestV2 request, String fingerprint) {
        return Map.of();
    }

    protected static String deterministicRequestId(String fingerprint) {
        return UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.US_ASCII)).toString();
    }

    protected abstract byte[] createRequestBody(TerrainDesignRequestV2 request) throws IOException;

    protected abstract ProviderPayload parseResponse(byte[] responseBody) throws IOException;

    protected static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
