package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.PlacementJournal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Atomic JSON journal storage confined to one configured placement directory. */
public final class FilePlacementJournalRepository implements PlacementJournalRepository {
    private final Path root;
    private final GenerationExecutors executors;
    private final LandformDataCodec codec = new LandformDataCodec();

    public FilePlacementJournalRepository(Path root, GenerationExecutors executors) {
        this.root = root.toAbsolutePath().normalize();
        this.executors = java.util.Objects.requireNonNull(executors, "executors");
    }

    @Override
    public CompletableFuture<PlacementJournal> save(PlacementJournal journal) {
        return executors.supplyIo(() -> {
            try {
                Files.createDirectories(root);
                codec.writePlacementJournal(path(journal.plan().placementId()), journal);
                return journal;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<PlacementJournal>> find(UUID placementId) {
        return executors.supplyIo(() -> {
            Path file = path(placementId);
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            try {
                return Optional.of(codec.readPlacementJournalCompatible(file));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    @Override
    public CompletableFuture<List<PlacementJournal>> findAll() {
        return executors.supplyIo(() -> {
            if (!Files.isDirectory(root)) {
                return List.of();
            }
            try (var stream = Files.list(root)) {
                return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(path -> {
                            try {
                                return codec.readPlacementJournalCompatible(path);
                            } catch (IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        })
                        .toList();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private Path path(UUID placementId) {
        return root.resolve(placementId + ".json");
    }
}
