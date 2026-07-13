package com.github.nankotsu029.landformcraft.ai.spi;

import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.ProviderUsage;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Deterministic provider for tests and offline development. */
public final class FixtureTerrainDesignProvider implements TerrainDesignProvider {
    private final TerrainIntent intent;
    private final Clock clock;

    public FixtureTerrainDesignProvider(TerrainIntent intent) {
        this(intent, Clock.systemUTC());
    }

    public FixtureTerrainDesignProvider(TerrainIntent intent, Clock clock) {
        this.intent = Objects.requireNonNull(intent, "intent");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return "fixture";
    }

    @Override
    public CompletableFuture<TerrainDesignResult> design(TerrainDesignRequest request) {
        Objects.requireNonNull(request, "request");
        return CompletableFuture.completedFuture(new TerrainDesignResult(
                intent, id(), "fixture-v1", TerrainIntentPrompt.VERSION, "fixture-response-v1",
                ProviderUsage.ZERO, 1, clock.instant()
        ));
    }
}
