package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.ai.anthropic.AnthropicTerrainDesignProviderV2;
import com.github.nankotsu029.landformcraft.ai.openai.OpenAiTerrainDesignProviderV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.TerrainDesignProviderV2;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.ProviderSettings;
import com.github.nankotsu029.landformcraft.core.v2.design.TerrainDesignApplicationServiceV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Objects;

/**
 * Paper runtime factory for the HTTP v2 design providers (V2-12-03).
 *
 * <p>Only {@code OPENAI} and {@code ANTHROPIC} reach a factory; the {@code IMPORT} and
 * {@code FIXTURE} paths are built inside {@code TerrainDesignApplicationServiceV2} from operator
 * paths that the Paper adapter has already sandboxed. API keys stay in {@link ProviderSettings} and
 * never enter a command argument or a log line.</p>
 */
public final class PaperTerrainDesignProviderFactoryV2 implements TerrainDesignApplicationServiceV2.ProviderFactory {
    private final GenerationExecutors executors;
    private final ProviderSettings settings;
    private final Clock clock;

    public PaperTerrainDesignProviderFactoryV2(
            GenerationExecutors executors,
            ProviderSettings settings,
            Clock clock
    ) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public TerrainDesignProviderV2 apply(DesignPathKindV2 path, String modelOrIntentPath) {
        return switch (path) {
            case OPENAI -> new OpenAiTerrainDesignProviderV2(
                    executors, settings.key("openai"), settings.model("openai", modelOrIntentPath),
                    OpenAiTerrainDesignProviderV2.DEFAULT_ENDPOINT, settings.policy(), clock, client());
            case ANTHROPIC -> new AnthropicTerrainDesignProviderV2(
                    executors, settings.key("anthropic"), settings.model("anthropic", modelOrIntentPath),
                    AnthropicTerrainDesignProviderV2.DEFAULT_ENDPOINT, settings.policy(), clock, client());
            case IMPORT, FIXTURE, MANUAL_CONSTRAINT, REFERENCE_IMAGE_DRAFT -> throw new IllegalArgumentException(
                    "design path " + path + " does not use an HTTP provider factory");
        };
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }
}
