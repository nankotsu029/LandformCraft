package com.github.nankotsu029.landformcraft.core.v2.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.nankotsu029.landformcraft.format.Sha256;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ADR 0035 D8 preflight for retiring production v1 writers and state backends.
 *
 * <p>The preflight is deliberately independent of the v1 repositories that V2-12-06 removes. It
 * inventories every known v1 operational root, rejects links and non-regular entries, and can copy
 * the byte-exact state into a neutral, checksum-indexed archive. Source state is never modified.
 * A retirement gate passes only when there is no state, or every discovered byte has been copied
 * and read back from a newly-created archive.</p>
 */
public final class LegacyRetirementPreflightV2 {
    public static final String REPORT_VERSION = "legacy-retirement-preflight-v1";
    public static final int MAX_ENTRIES = 100_000;
    public static final long MAX_TOTAL_BYTES = 100L * 1024L * 1024L * 1024L;

    private static final Pattern ARCHIVE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final List<String> STATE_ROOTS = List.of(
            "jobs",
            "placements",
            "snapshots",
            "cleanup-plans",
            "requests",
            "designs",
            "candidates",
            "exports",
            "imports",
            "confirmations",
            "placement-safety-state.json"
    );

    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public LegacyRetirementPreflightV2() {
        this(Clock.systemUTC());
    }

    LegacyRetirementPreflightV2(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Result inspect(Path dataRoot) throws IOException {
        List<Entry> entries = inventory(dataRoot);
        return result("INSPECT_ONLY", entries.isEmpty(), entries, null, entries.size());
    }

    public Result archive(Path dataRoot, Path archiveRoot, String archiveId) throws IOException {
        Objects.requireNonNull(archiveRoot, "archiveRoot");
        if (archiveId == null || !ARCHIVE_ID.matcher(archiveId).matches()) {
            throw new IllegalArgumentException("archiveId must be a portable lowercase slug");
        }
        List<Entry> entries = inventory(dataRoot);
        Path target = archiveRoot.toAbsolutePath().normalize().resolve(archiveId).normalize();
        Path normalizedArchiveRoot = archiveRoot.toAbsolutePath().normalize();
        if (!target.startsWith(normalizedArchiveRoot) || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("neutral archive target already exists or escapes archive root");
        }

        Files.createDirectories(target);
        boolean complete = false;
        try {
            Path normalizedDataRoot = dataRoot.toAbsolutePath().normalize();
            Path payload = target.resolve("payload");
            for (Entry entry : entries) {
                Path source = normalizedDataRoot.resolve(entry.relativePath()).normalize();
                Path destination = payload.resolve(entry.relativePath()).normalize();
                if (!source.startsWith(normalizedDataRoot) || !destination.startsWith(payload)) {
                    throw new IOException("inventory path escapes its root: " + entry.relativePath());
                }
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                String copied = Sha256.file(destination);
                String current = Sha256.file(source);
                if (!entry.sha256().equals(copied) || !entry.sha256().equals(current)
                        || Files.size(destination) != entry.bytes()) {
                    throw new IOException("state changed or archive read-back failed: " + entry.relativePath());
                }
            }
            Result result = result("NEUTRAL_ARCHIVE", true, entries, archiveId, 0);
            writeReport(target.resolve("inventory.json"), result);
            Result readBack = mapper.readValue(target.resolve("inventory.json").toFile(), Result.class);
            if (!result.equals(readBack)) {
                throw new IOException("neutral archive inventory read-back mismatch");
            }
            complete = true;
            return result;
        } finally {
            if (!complete) {
                // Keep the failed archive for operator forensics, but never mark it as a passed gate.
                Files.writeString(target.resolve("INCOMPLETE"), "archive did not pass strict read-back\n",
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
        }
    }

    public void writeReport(Path report, Result result) throws IOException {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(result, "result");
        Path target = report.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "report parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".retirement-preflight-", ".json");
        try {
            mapper.writeValue(temporary.toFile(), result);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic report publish is unavailable", exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private List<Entry> inventory(Path dataRoot) throws IOException {
        Objects.requireNonNull(dataRoot, "dataRoot");
        Path root = dataRoot.toAbsolutePath().normalize();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
            throw new IOException("data root must not be a symbolic link");
        }
        List<Entry> entries = new ArrayList<>();
        long totalBytes = 0L;
        for (String stateRoot : STATE_ROOTS) {
            Path candidate = root.resolve(stateRoot).normalize();
            if (!candidate.startsWith(root) || !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (Files.isSymbolicLink(candidate)) {
                throw new IOException("v1 state root must not be a symbolic link: " + stateRoot);
            }
            List<Path> files;
            if (Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                files = List.of(candidate);
            } else if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                try (var walked = Files.walk(candidate)) {
                    files = walked.filter(path -> !path.equals(candidate))
                            .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                            .toList();
                }
            } else {
                throw new IOException("v1 state root is not a regular file or directory: " + stateRoot);
            }
            for (Path file : files) {
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("v1 state contains a symbolic link: " + relative(root, file));
                }
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("v1 state contains a non-regular entry: " + relative(root, file));
                }
                long bytes = Files.size(file);
                totalBytes = Math.addExact(totalBytes, bytes);
                if (entries.size() >= MAX_ENTRIES || totalBytes > MAX_TOTAL_BYTES) {
                    throw new IOException("v1 state exceeds preflight admission budget");
                }
                String relative = relative(root, file);
                entries.add(new Entry(category(relative), relative, bytes, Sha256.file(file)));
            }
        }
        return List.copyOf(entries);
    }

    private Result result(
            String resolution,
            boolean passed,
            List<Entry> entries,
            String archiveId,
            int unresolvedCount
    ) {
        long bytes = entries.stream().mapToLong(Entry::bytes).sum();
        return new Result(REPORT_VERSION, passed ? "PASS" : "BLOCKED", resolution,
                Instant.now(clock).toString(), "operator-data-root", archiveId,
                entries.size(), bytes, unresolvedCount, entries);
    }

    private static String relative(Path root, Path value) throws IOException {
        Path normalized = value.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IOException("v1 state path escapes data root");
        }
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    private static String category(String relative) {
        int slash = relative.indexOf('/');
        return slash < 0 ? relative : relative.substring(0, slash);
    }

    public record Entry(String category, String relativePath, long bytes, String sha256) {
        public Entry {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(relativePath, "relativePath");
            Objects.requireNonNull(sha256, "sha256");
            if (bytes < 0L || relativePath.isBlank() || relativePath.startsWith("/")
                    || relativePath.contains("../") || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid retirement inventory entry");
            }
        }
    }

    public record Result(
            String reportVersion,
            String status,
            String resolution,
            String generatedAt,
            String dataRootLabel,
            String archiveId,
            int entryCount,
            long totalBytes,
            int unresolvedCount,
            List<Entry> entries
    ) {
        public Result {
            Objects.requireNonNull(reportVersion, "reportVersion");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(resolution, "resolution");
            Objects.requireNonNull(generatedAt, "generatedAt");
            Objects.requireNonNull(dataRootLabel, "dataRootLabel");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            if (!Set.of("PASS", "BLOCKED").contains(status)
                    || entryCount != entries.size() || totalBytes < 0L || unresolvedCount < 0) {
                throw new IllegalArgumentException("invalid retirement preflight result");
            }
        }
    }
}
