package com.github.nankotsu029.landformcraft.core.v2.job;

import com.github.nankotsu029.landformcraft.format.v2.job.ExportJobCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobSnapshotV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobStateV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable, bounded store of v2 export job snapshots (V2-12-09).
 *
 * <p>File names are the job's canonical UUID, so an operator-supplied job id cannot address anything
 * outside the store: it is parsed as a UUID and re-rendered before it ever touches a path.</p>
 *
 * <p>Bukkit-free, so the CLI and the Paper adapter share it. The v1 job store is untouched.</p>
 */
public final class ExportJobStoreV2 {
    /** Upper bound on retained jobs, so an unattended server cannot grow the store without limit. */
    public static final int MAXIMUM_JOBS = 4_096;

    private static final String SUFFIX = ".job-v2.json";

    private final ExportJobCodecV2 codec = new ExportJobCodecV2();
    private final Path root;

    public ExportJobStoreV2(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    /** Admits and writes one snapshot. New jobs are admitted against {@link #MAXIMUM_JOBS}. */
    public void save(ExportJobSnapshotV2 snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        Path target = resolve(snapshot.jobUuid());
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) && count() >= MAXIMUM_JOBS) {
            throw new IOException("v2 export job store is full (" + MAXIMUM_JOBS
                    + " jobs); prune completed jobs before submitting another");
        }
        codec.write(target, snapshot);
    }

    public Optional<ExportJobSnapshotV2> find(String jobId) throws IOException {
        Path target = resolve(requireJobUuid(jobId));
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        return Optional.of(codec.read(target));
    }

    /** All snapshots in deterministic job-id order. */
    public List<ExportJobSnapshotV2> list() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (var paths = Files.list(root)) {
            paths.filter(path -> path.getFileName().toString().endsWith(SUFFIX))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .forEach(files::add);
        }
        List<ExportJobSnapshotV2> snapshots = new ArrayList<>(files.size());
        for (Path file : files) {
            snapshots.add(codec.read(file));
        }
        return List.copyOf(snapshots);
    }

    /**
     * Published jobs of one request, in deterministic job-id order. These are the v2 equivalent of v1
     * candidates: the successful exports an operator can compare before placing one.
     */
    public List<ExportJobSnapshotV2> candidatesOf(String requestId) throws IOException {
        Objects.requireNonNull(requestId, "requestId");
        List<ExportJobSnapshotV2> candidates = new ArrayList<>();
        for (ExportJobSnapshotV2 snapshot : list()) {
            if (snapshot.requestId().equals(requestId) && snapshot.state() == ExportJobStateV2.PUBLISHED) {
                candidates.add(snapshot);
            }
        }
        return List.copyOf(candidates);
    }

    public Path root() {
        return root;
    }

    private int count() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        try (var paths = Files.list(root)) {
            return (int) paths.filter(path -> path.getFileName().toString().endsWith(SUFFIX)).count();
        }
    }

    private Path resolve(UUID jobId) {
        Path target = root.resolve(jobId + SUFFIX).normalize();
        if (!target.startsWith(root) || !Objects.equals(target.getParent(), root)) {
            throw new IllegalArgumentException("v2 export job path escapes the job store");
        }
        return target;
    }

    /**
     * Parses an operator-supplied job id. Round-tripping rejects the shortened and mixed-case forms
     * {@link UUID#fromString} tolerates, so one job can never be addressed by two names.
     */
    public static UUID requireJobUuid(String jobId) {
        Objects.requireNonNull(jobId, "jobId");
        UUID parsed;
        try {
            parsed = UUID.fromString(jobId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("v2 job id must be a canonical UUID: " + jobId);
        }
        if (!parsed.toString().equals(jobId)) {
            throw new IllegalArgumentException("v2 job id must be a canonical lowercase UUID: " + jobId);
        }
        return parsed;
    }
}
