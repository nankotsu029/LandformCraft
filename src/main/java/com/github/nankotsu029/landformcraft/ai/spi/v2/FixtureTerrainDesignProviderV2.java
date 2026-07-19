package com.github.nankotsu029.landformcraft.ai.spi.v2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;

import com.github.nankotsu029.landformcraft.model.ProviderUsage;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Deterministic v2 fixture provider for tests and offline development. */
public final class FixtureTerrainDesignProviderV2 implements TerrainDesignProviderV2 {
    private final TerrainIntentV2 intent;
    private final Clock clock;

    public FixtureTerrainDesignProviderV2(TerrainIntentV2 intent) {
        this(intent, Clock.systemUTC());
    }

    public FixtureTerrainDesignProviderV2(TerrainIntentV2 intent, Clock clock) {
        this.intent = Objects.requireNonNull(intent, "intent");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return "fixture-v2";
    }

    @Override
    public DesignPathKindV2 path() {
        return DesignPathKindV2.FIXTURE;
    }

    @Override
    public CompletableFuture<TerrainDesignResultV2> design(TerrainDesignRequestV2 request) {
        Objects.requireNonNull(request, "request");
        DesignCapabilityNegotiatorV2.negotiate(
                request.intentContractVersion(),
                request.path(),
                "fixture-v2",
                request.requestedCapabilities());
        return CompletableFuture.completedFuture(new TerrainDesignResultV2(
                intent,
                id(),
                "fixture-v2",
                TerrainIntentPromptV2.VERSION,
                "fixture-response-v2",
                ProviderUsage.ZERO,
                1,
                clock.instant(),
                EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                ProviderCapabilityCatalogV2.CONTRACT_VERSION
        ));
    }
}
