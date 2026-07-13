package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.WorldBlueprint;

import java.util.Objects;

/** Converts validated input contracts into a provider-independent deterministic blueprint. */
public final class BlueprintCompiler {
    public static final String GENERATOR_VERSION = "1.1.0-phase1";

    public WorldBlueprint compile(GenerationRequest request, TerrainIntent intent, int candidateIndex) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(intent, "intent");
        if (request.schemaVersion() != intent.schemaVersion()) {
            throw new IllegalArgumentException("request and intent schema versions must match");
        }
        if (candidateIndex < 0 || candidateIndex >= request.generation().candidates()) {
            throw new IllegalArgumentException("candidateIndex outside requested candidate count");
        }
        int largestSide = Math.max(request.bounds().width(), request.bounds().length());
        int logicalResolution = largestSide <= 500 ? 64 : 128;
        long seed = candidateIndex == 0
                ? request.generation().baseSeed()
                : mixSeed(request.generation().baseSeed(), candidateIndex);
        return new WorldBlueprint(
                request.schemaVersion(),
                request.requestId(),
                request.bounds(),
                intent,
                seed,
                request.output().tileSize(),
                logicalResolution,
                GENERATOR_VERSION
        );
    }

    private static long mixSeed(long baseSeed, int candidateIndex) {
        long value = baseSeed + 0x9E3779B97F4A7C15L * candidateIndex;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
