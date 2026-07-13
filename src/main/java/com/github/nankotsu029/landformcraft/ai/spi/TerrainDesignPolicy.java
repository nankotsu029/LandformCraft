package com.github.nankotsu029.landformcraft.ai.spi;

import java.time.Duration;
import java.util.Objects;

/** Provider-independent availability and cost guardrails. */
public record TerrainDesignPolicy(
        Duration requestTimeout,
        int maxAttempts,
        Duration initialBackoff,
        int maxOutputTokens,
        int maxRequestsPerMinute,
        long maxTotalTokens
) {
    public TerrainDesignPolicy {
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        Objects.requireNonNull(initialBackoff, "initialBackoff");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 10");
        }
        if (initialBackoff.isNegative() || initialBackoff.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("initialBackoff must be between zero and one minute");
        }
        if (maxOutputTokens < 256 || maxOutputTokens > 32_768) {
            throw new IllegalArgumentException("maxOutputTokens must be between 256 and 32768");
        }
        if (maxRequestsPerMinute < 1 || maxRequestsPerMinute > 10_000) {
            throw new IllegalArgumentException("maxRequestsPerMinute must be between 1 and 10000");
        }
        if (maxTotalTokens < maxOutputTokens) {
            throw new IllegalArgumentException("maxTotalTokens must be at least maxOutputTokens");
        }
    }

    public static TerrainDesignPolicy defaults() {
        return new TerrainDesignPolicy(
                Duration.ofSeconds(60), 3, Duration.ofMillis(250), 4_096, 20, 100_000
        );
    }
}
