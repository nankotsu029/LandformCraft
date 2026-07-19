package com.github.nankotsu029.landformcraft.core.v2.placement.undo;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Explicit Release 2 Undo path for Paper command dispatch. Wraps prepare + execute so callers can
 * discriminate from the frozen v1 {@code PlacementApplicationService} undo path via
 * {@link #isRelease2Path()}.
 */
public final class PlacementUndoApplicationServiceV2 implements AutoCloseable {
    private final PlacementUndoPrepareCompilerV2 prepareCompiler;
    private final PlacementUndoServiceV2 undoService;

    public PlacementUndoApplicationServiceV2(
            PlacementUndoPrepareCompilerV2 prepareCompiler,
            PlacementUndoServiceV2 undoService
    ) {
        this.prepareCompiler = Objects.requireNonNull(prepareCompiler, "prepareCompiler");
        this.undoService = Objects.requireNonNull(undoService, "undoService");
    }

    public PlacementUndoPrepareCompilerV2.PreparedUndoV2 prepareUndo(
            PlacementUndoPrepareRequestV2 request
    ) {
        return prepareCompiler.prepare(request);
    }

    public CompletionStage<PlacementUndoServiceV2.UndoResultV2> undo(PlacementUndoRequestV2 request) {
        return undoService.undo(request);
    }

    /** Discriminator for Paper command routing onto the Release 2 Undo path. */
    public boolean isRelease2Path() {
        return true;
    }

    @Override
    public void close() {
        undoService.close();
    }

    public CompletionStage<Void> termination() {
        return undoService.termination();
    }
}
