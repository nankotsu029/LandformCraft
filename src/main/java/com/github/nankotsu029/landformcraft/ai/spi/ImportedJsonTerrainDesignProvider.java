package com.github.nankotsu029.landformcraft.ai.spi;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.ProviderUsage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Manual JSON import through the same asynchronous SPI used by network providers. */
public final class ImportedJsonTerrainDesignProvider implements TerrainDesignProvider {
    private final GenerationExecutors executors;
    private final Path intentPath;
    private final LandformDataCodec codec;
    private final Clock clock;

    public ImportedJsonTerrainDesignProvider(GenerationExecutors executors, Path intentPath) {
        this(executors, intentPath, new LandformDataCodec(), Clock.systemUTC());
    }

    public ImportedJsonTerrainDesignProvider(
            GenerationExecutors executors,
            Path intentPath,
            LandformDataCodec codec,
            Clock clock
    ) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.intentPath = Objects.requireNonNull(intentPath, "intentPath").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return "imported-json";
    }

    @Override
    public CompletableFuture<TerrainDesignResult> design(TerrainDesignRequest request) {
        Objects.requireNonNull(request, "request");
        return executors.supplyIo(() -> {
            try {
                String checksum = Sha256.file(intentPath);
                return new TerrainDesignResult(
                        codec.readTerrainIntent(intentPath), id(), "manual-json-v1",
                        TerrainIntentPrompt.VERSION, "import-" + checksum.substring(0, 24),
                        ProviderUsage.ZERO, 1, clock.instant()
                );
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }
}
