package com.github.nankotsu029.landformcraft.ai.http;

public record ProviderHttpResponse(byte[] body, int attempts) {
    public ProviderHttpResponse {
        body = body.clone();
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be positive");
        }
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
