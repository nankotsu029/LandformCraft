package com.github.nankotsu029.landformcraft.worldedit;

import com.sk89q.worldedit.extent.clipboard.Clipboard;

import java.util.Objects;

/** Opaque WorldEdit clipboard handle that prevents WorldEdit types leaking into Paper orchestration. */
public final class LoadedSchematic {
    private final Clipboard clipboard;

    LoadedSchematic(Clipboard clipboard) {
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
    }

    Clipboard clipboard() {
        return clipboard;
    }
}
