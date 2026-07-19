package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.v2.placement.undo.PlacementUndoApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.undo.PlacementUndoPrepareCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.undo.PlacementUndoPrepareRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.undo.PlacementUndoRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.undo.PlacementUndoServiceV2;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Paper-facing Release 2 Undo entry point (V2-6-09). Distinct from the frozen v1
 * {@code PlacementApplicationService} undo path. Command handlers obtain this adapter only for the
 * explicit Release 2 path ({@link PlacementUndoApplicationServiceV2#isRelease2Path()}).
 */
public final class PaperPlacementUndoServiceV2 {
    private final PlacementUndoApplicationServiceV2 undo;

    public PaperPlacementUndoServiceV2(PlacementUndoApplicationServiceV2 undo) {
        this.undo = Objects.requireNonNull(undo, "undo");
        if (!undo.isRelease2Path()) {
            throw new IllegalArgumentException("Paper Undo adapter requires the Release 2 Undo path");
        }
    }

    public boolean isRelease2Path() {
        return undo.isRelease2Path();
    }

    public PlacementUndoPrepareCompilerV2.PreparedUndoV2 prepare(PlacementUndoPrepareRequestV2 request) {
        return undo.prepareUndo(request);
    }

    public CompletionStage<PlacementUndoServiceV2.UndoResultV2> execute(PlacementUndoRequestV2 request) {
        return undo.undo(request);
    }
}
