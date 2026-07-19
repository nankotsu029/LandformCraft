package com.github.nankotsu029.landformcraft.ai.spi.v2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.ProviderUsage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Manual TerrainIntent v2 JSON import through the versioned SPI. */
public final class ImportedJsonTerrainDesignProviderV2 implements TerrainDesignProviderV2 {
    private final GenerationExecutors executors;
    private final Path intentPath;
    private final LandformV2DataCodec codec;
    private final Clock clock;

    public ImportedJsonTerrainDesignProviderV2(GenerationExecutors executors, Path intentPath) {
        this(executors, intentPath, new LandformV2DataCodec(), Clock.systemUTC());
    }

    public ImportedJsonTerrainDesignProviderV2(
            GenerationExecutors executors,
            Path intentPath,
            LandformV2DataCodec codec,
            Clock clock
    ) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.intentPath = Objects.requireNonNull(intentPath, "intentPath").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return "imported-json-v2";
    }

    @Override
    public DesignPathKindV2 path() {
        return DesignPathKindV2.IMPORT;
    }

    @Override
    public CompletableFuture<TerrainDesignResultV2> design(TerrainDesignRequestV2 request) {
        Objects.requireNonNull(request, "request");
        DesignCapabilityNegotiatorV2.negotiate(
                request.intentContractVersion(),
                request.path(),
                "manual-json-v2",
                request.requestedCapabilities());
        return executors.supplyIo(() -> {
            try {
                String checksum = Sha256.file(intentPath);
                return new TerrainDesignResultV2(
                        codec.readTerrainIntent(intentPath),
                        id(),
                        "manual-json-v2",
                        TerrainIntentPromptV2.VERSION,
                        "import-" + checksum.substring(0, 24),
                        ProviderUsage.ZERO,
                        1,
                        clock.instant(),
                        EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                        ProviderCapabilityCatalogV2.CONTRACT_VERSION
                );
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }
}
