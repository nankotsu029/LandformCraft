package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

/** Strict file-backed Release 2 journal store, separate from the v1 repository. */
public final class FilePlacementJournalStoreV2 implements PlacementJournalStoreV2 {
    private final Path root;
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public FilePlacementJournalStoreV2(Path root) throws IOException {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        if (!Files.isDirectory(this.root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(this.root)) {
            throw new IOException("Release 2 journal root must be a non-symbolic directory");
        }
    }

    @Override
    public synchronized void save(PlacementJournalV2 journal) throws IOException {
        Objects.requireNonNull(journal, "journal");
        Path target = root.resolve(journal.plan().placementId() + ".json").normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Release 2 journal path escaped its root");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(target))) {
            throw new IOException("Release 2 journal target must be a regular non-symbolic file");
        }
        Path staging = Files.createTempFile(root, ".placement-journal-v2-", ".tmp");
        try {
            codec.writePlacementJournal(staging, journal);
            PlacementJournalV2 staged = codec.readPlacementJournal(staging);
            if (!staged.equals(journal)) {
                throw new IOException("Release 2 staged journal changed during strict read-back");
            }
            try (FileChannel channel = FileChannel.open(staging, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(
                        staging,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Release 2 journal store requires atomic move", exception);
            }
            try (FileChannel directory = FileChannel.open(root, StandardOpenOption.READ)) {
                directory.force(true);
            }
            PlacementJournalV2 published = codec.readPlacementJournal(target);
            if (!published.equals(journal)) {
                throw new IOException("Release 2 published journal changed during strict read-back");
            }
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    public PlacementJournalV2 load(UUID placementId) throws IOException {
        Objects.requireNonNull(placementId, "placementId");
        Path target = journalPath(placementId);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Release 2 journal is missing for placement " + placementId);
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new IOException("Release 2 journal target must be a regular non-symbolic file");
        }
        return codec.readPlacementJournal(target);
    }

    public boolean exists(UUID placementId) throws IOException {
        Objects.requireNonNull(placementId, "placementId");
        Path target = journalPath(placementId);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new IOException("Release 2 journal target must be a regular non-symbolic file");
        }
        return true;
    }

    private Path journalPath(UUID placementId) throws IOException {
        Path target = root.resolve(placementId + ".json").normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Release 2 journal path escaped its root");
        }
        return target;
    }
}
