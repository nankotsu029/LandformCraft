package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.ConstraintCompilationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.binding.BoundConstraintFieldV2;
import com.github.nankotsu029.landformcraft.core.v2.binding.ConstraintMapFieldBindingV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapInputException;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Macro foundation production stage of the surface export spine (V2-18-09, ADR 0038 D5-1).
 *
 * <p>Runs before feature composition and turns the explicit foundation input into the
 * {@link MacroFoundationV2} background candidate: the HARD {@code LAND_WATER_MASK} reference is
 * resolved through the permanent V2-18-06 binding (secure resolve → digest → decode → canonical
 * XZ registration) and the provisional elevation comes from the request's declared per-medium base
 * levels. Nothing is inferred: a request without a complete explicit foundation input — a HARD
 * mask binding <em>and</em> declared base levels — resolves to empty, and the pipeline keeps the
 * legacy surface-baseline path for it (ADR 0038 D8-2), so a working production path always exists
 * while V2-18-10 owns the later fail-closed coverage promotion.</p>
 */
final class MacroFoundationStageV2 {
    private final ConstraintMapFieldBindingV2 binding = new ConstraintMapFieldBindingV2();

    /**
     * Resolves the macro foundation when the request carries the complete explicit foundation
     * input. Decodes mask bytes, so callers must already be off the Paper main thread.
     */
    Optional<MacroFoundationV2> resolve(
            GenerationRequestV2 request,
            Path requestSource,
            TerrainIntentV2 intent,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(requestSource, "requestSource");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(token, "token");
        Optional<TerrainIntentV2.ConstraintMapBinding> maskBinding = hardLandWaterBinding(intent);
        if (maskBinding.isEmpty() || request.foundationBaseLevels().isEmpty()) {
            return Optional.empty();
        }
        token.throwIfCancellationRequested();
        BoundConstraintFieldV2 mask;
        try {
            mask = binding.bind(requestSource, request, maskBinding.get(),
                    ConstraintMapDecodeLimits.defaults(), token);
        } catch (ConstraintCompilationExceptionV2 exception) {
            throw new IOException("macro foundation mask binding failed ["
                    + exception.code() + "]: " + exception.getMessage(), exception);
        } catch (ConstraintMapInputException exception) {
            throw new IOException("macro foundation mask resolution failed ["
                    + exception.code() + "]: " + exception.getMessage(), exception);
        }
        return Optional.of(new MacroFoundationV2(
                mask, request.foundationBaseLevels().get(), List.of()));
    }

    private static Optional<TerrainIntentV2.ConstraintMapBinding> hardLandWaterBinding(
            TerrainIntentV2 intent
    ) {
        return intent.mapReferences().stream()
                .filter(binding -> binding.role() == TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK)
                .filter(binding -> binding.strength() == TerrainIntentV2.Strength.HARD)
                .findFirst();
    }
}
