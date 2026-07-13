package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.ai.anthropic.AnthropicTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.openai.OpenAiTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.FixtureTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.ImportedJsonTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignProvider;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.ProviderSettings;
import com.github.nankotsu029.landformcraft.core.TerrainDesignProviderFactory;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

/** Paper runtime factory for concrete Provider and local import adapters. */
public final class PaperTerrainDesignProviderFactory implements TerrainDesignProviderFactory {
    private final Path importsRoot;
    private final GenerationExecutors executors;
    private final ProviderSettings settings;
    private final Clock clock;

    public PaperTerrainDesignProviderFactory(
            Path importsRoot, GenerationExecutors executors, ProviderSettings settings, Clock clock
    ) {
        this.importsRoot = importsRoot.toAbsolutePath().normalize();
        this.executors = Objects.requireNonNull(executors, "executors");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public TerrainDesignProvider create(String provider, String modelOrPath) {
        return switch (provider) {
            case "openai" -> new OpenAiTerrainDesignProvider(
                    executors, settings.key(provider), settings.model(provider, modelOrPath),
                    OpenAiTerrainDesignProvider.DEFAULT_ENDPOINT, settings.policy(), clock, client());
            case "anthropic" -> new AnthropicTerrainDesignProvider(
                    executors, settings.key(provider), settings.model(provider, modelOrPath),
                    AnthropicTerrainDesignProvider.DEFAULT_ENDPOINT, settings.policy(), clock, client());
            case "import" -> new ImportedJsonTerrainDesignProvider(executors, importPath(modelOrPath));
            case "fixture" -> {
                try {
                    yield new FixtureTerrainDesignProvider(
                            new LandformDataCodec().readTerrainIntent(importPath(modelOrPath)));
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
            default -> throw new IllegalArgumentException("unknown design provider: " + provider);
        };
    }

    private Path importPath(String relative) {
        Path value = Path.of(relative);
        Path resolved = importsRoot.resolve(value).normalize();
        if (value.isAbsolute() || relative.contains("\\") || !value.normalize().equals(value)
                || !resolved.startsWith(importsRoot) || !Files.isRegularFile(resolved)
                || Files.isSymbolicLink(resolved)) {
            throw new IllegalArgumentException("design import path must be a safe relative regular file");
        }
        return resolved;
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }
}
