package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignPolicy;

import java.util.Objects;
import java.util.regex.Pattern;

/** Validated Paper provider wiring; only environment variable names are retained. */
public record ProviderSettings(
        boolean openAiEnabled,
        String openAiKeyEnvironment,
        String openAiDefaultModel,
        boolean anthropicEnabled,
        String anthropicKeyEnvironment,
        String anthropicDefaultModel,
        TerrainDesignPolicy policy
) {
    private static final Pattern ENVIRONMENT = Pattern.compile("[A-Z_][A-Z0-9_]{0,127}");

    public ProviderSettings {
        openAiKeyEnvironment = environment(openAiKeyEnvironment, "openAiKeyEnvironment");
        anthropicKeyEnvironment = environment(anthropicKeyEnvironment, "anthropicKeyEnvironment");
        openAiDefaultModel = Objects.requireNonNullElse(openAiDefaultModel, "").strip();
        anthropicDefaultModel = Objects.requireNonNullElse(anthropicDefaultModel, "").strip();
        Objects.requireNonNull(policy, "policy");
    }

    public String model(String provider, String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.strip();
        }
        String configured = switch (provider) {
            case "openai" -> openAiDefaultModel;
            case "anthropic" -> anthropicDefaultModel;
            default -> "";
        };
        if (configured.isBlank()) {
            throw new LandformException(LandformErrorCode.CONFIG_INVALID,
                    "A model ID is required.", "design-create", provider, "provider-config",
                    "Pass a model ID in the command or configure a non-empty default model.");
        }
        return configured;
    }

    public String key(String provider) {
        boolean enabled;
        String environment;
        switch (provider) {
            case "openai" -> {
                enabled = openAiEnabled;
                environment = openAiKeyEnvironment;
            }
            case "anthropic" -> {
                enabled = anthropicEnabled;
                environment = anthropicKeyEnvironment;
            }
            default -> throw new IllegalArgumentException("unknown API provider: " + provider);
        }
        if (!enabled) {
            throw new LandformException(LandformErrorCode.CONFIG_INVALID,
                    "The requested provider is disabled.", "design-create", provider,
                    "provider-config", "Enable it in config.yml after setting its environment key.");
        }
        String value = System.getenv(environment);
        if (value == null || value.isBlank()) {
            throw new LandformException(LandformErrorCode.CONFIG_INVALID,
                    "The provider API key environment variable is missing.", "design-create", provider,
                    "provider-config", "Set " + environment + " in the server process environment.");
        }
        return value;
    }

    private static String environment(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!ENVIRONMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be an uppercase environment variable name");
        }
        return value;
    }
}
