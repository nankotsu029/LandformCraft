package com.github.nankotsu029.landformcraft.ai.spi;

import com.github.nankotsu029.landformcraft.model.ProviderUsage;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Per-provider-process fixed-window request limit and cumulative token budget. */
public final class ProviderQuota {
    private final TerrainDesignPolicy policy;
    private final Clock clock;
    private Instant windowStart;
    private int requestsInWindow;
    private long consumedTokens;

    public ProviderQuota(TerrainDesignPolicy policy, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.windowStart = clock.instant().truncatedTo(ChronoUnit.MINUTES);
    }

    public synchronized void beforeAttempt() {
        Instant currentWindow = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        if (!currentWindow.equals(windowStart)) {
            windowStart = currentWindow;
            requestsInWindow = 0;
        }
        if (requestsInWindow >= policy.maxRequestsPerMinute()) {
            throw new TerrainDesignException(
                    ProviderFailureCode.LOCAL_RATE_LIMIT,
                    "local provider request limit reached",
                    0,
                    0
            );
        }
        if (consumedTokens >= policy.maxTotalTokens()) {
            throw new TerrainDesignException(
                    ProviderFailureCode.TOKEN_BUDGET_EXCEEDED,
                    "local provider token budget exhausted",
                    0,
                    0
            );
        }
        requestsInWindow++;
    }

    public synchronized void record(ProviderUsage usage) {
        Objects.requireNonNull(usage, "usage");
        consumedTokens = Math.addExact(consumedTokens, usage.totalTokens());
        if (consumedTokens > policy.maxTotalTokens()) {
            throw new TerrainDesignException(
                    ProviderFailureCode.TOKEN_BUDGET_EXCEEDED,
                    "provider response exceeded the local token budget",
                    0,
                    0
            );
        }
    }

    public synchronized long consumedTokens() {
        return consumedTokens;
    }
}
