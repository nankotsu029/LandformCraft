package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.model.PlacementJournal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlacementJournalRepository {
    CompletableFuture<PlacementJournal> save(PlacementJournal journal);

    CompletableFuture<Optional<PlacementJournal>> find(UUID placementId);

    CompletableFuture<List<PlacementJournal>> findAll();
}
