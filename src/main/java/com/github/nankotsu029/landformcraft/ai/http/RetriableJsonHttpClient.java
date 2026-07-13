package com.github.nankotsu029.landformcraft.ai.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.ai.spi.ProviderFailureCode;
import com.github.nankotsu029.landformcraft.ai.spi.ProviderQuota;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignException;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignPolicy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;

/** Blocking transport intended to run on an admitted virtual thread. */
public final class RetriableJsonHttpClient {
    public static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    public static final int MAX_REQUEST_BYTES = 24 * 1024 * 1024;

    private final HttpClient client;
    private final TerrainDesignPolicy policy;
    private final ProviderQuota quota;
    private final ObjectMapper mapper = ProviderJsonSupport.strictMapper();

    public RetriableJsonHttpClient(HttpClient client, TerrainDesignPolicy policy, ProviderQuota quota) {
        this.client = Objects.requireNonNull(client, "client");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.quota = Objects.requireNonNull(quota, "quota");
    }

    public ProviderHttpResponse post(URI endpoint, Map<String, String> headers, byte[] requestBody) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(requestBody, "requestBody");
        if (requestBody.length > MAX_REQUEST_BYTES) {
            throw new TerrainDesignException(
                    ProviderFailureCode.INVALID_REQUEST,
                    "provider request exceeded the local size limit",
                    0,
                    0
            );
        }
        TerrainDesignException lastFailure = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("provider request cancelled");
            }
            quota.beforeAttempt();
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(policy.requestTimeout())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
            headers.forEach(builder::header);
            try {
                HttpResponse<InputStream> response = client.send(
                        builder.build(), HttpResponse.BodyHandlers.ofInputStream()
                );
                byte[] responseBody = readResponse(response, attempt);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return new ProviderHttpResponse(responseBody, attempt);
                }
                lastFailure = httpFailure(response.statusCode(), responseBody, attempt);
                if (!isRetryable(response.statusCode()) || attempt == policy.maxAttempts()) {
                    throw lastFailure;
                }
                waitBeforeRetry(attempt, response.headers().firstValue("retry-after"));
            } catch (HttpTimeoutException exception) {
                lastFailure = new TerrainDesignException(
                        ProviderFailureCode.TIMEOUT, "provider request timed out", 0, attempt, exception
                );
                if (attempt == policy.maxAttempts()) {
                    throw lastFailure;
                }
                waitBeforeRetry(attempt, Optional.empty());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("provider request cancelled");
            } catch (IOException exception) {
                lastFailure = new TerrainDesignException(
                        ProviderFailureCode.TRANSPORT_ERROR,
                        "provider transport failed",
                        0,
                        attempt,
                        exception
                );
                if (attempt == policy.maxAttempts()) {
                    throw lastFailure;
                }
                waitBeforeRetry(attempt, Optional.empty());
            }
        }
        throw Objects.requireNonNull(lastFailure, "lastFailure");
    }

    private static byte[] readResponse(HttpResponse<InputStream> response, int attempt) throws IOException {
        try (InputStream body = response.body()) {
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new TerrainDesignException(
                        ProviderFailureCode.INVALID_RESPONSE,
                        "provider response exceeded the local size limit",
                        response.statusCode(), attempt
                );
            }
            return bytes;
        }
    }

    private TerrainDesignException httpFailure(int status, byte[] body, int attempt) {
        ProviderFailureCode code;
        if (status == 401 || status == 403) {
            code = ProviderFailureCode.AUTHENTICATION;
        } else if (status == 429) {
            code = ProviderFailureCode.RATE_LIMITED;
        } else if (status >= 500) {
            code = ProviderFailureCode.SERVER_ERROR;
        } else {
            code = ProviderFailureCode.INVALID_REQUEST;
        }
        String errorType = safeErrorType(body);
        return new TerrainDesignException(
                code,
                "provider returned HTTP " + status + (errorType.isEmpty() ? "" : " (" + errorType + ")"),
                status,
                attempt
        );
    }

    private String safeErrorType(byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode error = root == null ? null : root.path("error");
            String type = error == null ? "" : error.path("type").asText("");
            if (type.length() > 64 || !type.matches("[A-Za-z0-9_.-]*")) {
                return "";
            }
            return type;
        } catch (IOException ignored) {
            return "";
        }
    }

    private static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void waitBeforeRetry(int attempt, Optional<String> retryAfter) {
        Duration delay = retryAfter.flatMap(RetriableJsonHttpClient::parseRetryAfter)
                .orElseGet(() -> exponentialDelay(attempt));
        if (delay.compareTo(Duration.ofMinutes(1)) > 0) {
            delay = Duration.ofMinutes(1);
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("provider retry cancelled");
        }
    }

    private Duration exponentialDelay(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 10);
        try {
            Duration base = policy.initialBackoff().multipliedBy(multiplier);
            long nanos = base.toNanos();
            if (nanos == 0L) {
                return base;
            }
            long jitterBound = Math.max(1L, nanos / 4L);
            return base.plusNanos(ThreadLocalRandom.current().nextLong(jitterBound));
        } catch (ArithmeticException exception) {
            return Duration.ofMinutes(1);
        }
    }

    private static Optional<Duration> parseRetryAfter(String value) {
        try {
            long seconds = Long.parseLong(value.strip());
            return seconds < 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(seconds));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
