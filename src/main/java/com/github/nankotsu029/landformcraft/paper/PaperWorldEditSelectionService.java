package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.model.SelectionBounds;
import com.github.nankotsu029.landformcraft.worldedit.WorldEditSelectionAccess;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Reads a player's WorldEdit selection exclusively through the Paper scheduler. */
public final class PaperWorldEditSelectionService {
    private final PaperMainThreadDispatcher dispatcher;
    private final WorldEditSelectionAccess worldEdit = new WorldEditSelectionAccess();

    public PaperWorldEditSelectionService(PaperMainThreadDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public CompletionStage<SelectionBounds> selection(Player player) {
        Objects.requireNonNull(player, "player");
        return dispatcher.supply(() -> worldEdit.selection(player));
    }
}
