package com.github.nankotsu029.landformcraft.model;

public record ProviderUsage(long inputTokens, long outputTokens, long totalTokens) {
    public static final ProviderUsage ZERO = new ProviderUsage(0, 0, 0);

    public ProviderUsage {
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token usage must not be negative");
        }
        if (totalTokens < inputTokens + outputTokens) {
            throw new IllegalArgumentException("totalTokens must cover inputTokens and outputTokens");
        }
    }
}
