package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.GenerationStage;
import com.github.nankotsu029.landformcraft.model.PlacementState;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Locale;

/** Performs bounded, read-only or temporary-file diagnostics without revealing secret values. */
public final class DoctorService {
    private final LandformDataCodec codec = new LandformDataCodec();

    public DoctorReport inspect(Path dataDirectory, String runtime) throws IOException {
        Path root = dataDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) {
            throw new IOException("data directory must not be a symbolic link");
        }
        boolean writable = Files.isWritable(root);
        boolean atomicMove = checkAtomicMove(root);
        long usable = Files.getFileStore(root).getUsableSpace();
        int runningJobs = countRunningJobs(root.resolve("jobs"));
        int recovery = countRecovery(root.resolve("placements"));
        ArrayList<String> warnings = new ArrayList<>();
        if (!writable) {
            warnings.add("data directory is not writable");
        }
        if (!atomicMove) {
            warnings.add("atomic move is unavailable; safety-state publication must be refused");
        }
        if (usable <= 0L) {
            warnings.add("usable disk capacity could not be determined");
        }
        return new DoctorReport(
                System.getProperty("java.version", "unknown"), runtime, root.toString(), writable,
                atomicMove, usable, present("OPENAI_API_KEY"), present("ANTHROPIC_API_KEY"),
                runningJobs, recovery, warnings);
    }

    private int countRunningJobs(Path jobs) throws IOException {
        if (!Files.isDirectory(jobs)) {
            return 0;
        }
        int count = 0;
        try (var files = Files.list(jobs)) {
            for (Path file : files.filter(value -> value.getFileName().toString().endsWith(".json")).toList()) {
                if (Files.isSymbolicLink(file)) {
                    continue;
                }
                GenerationStage stage = codec.readGenerationJob(file).stage();
                if (stage != GenerationStage.READY && stage != GenerationStage.FAILED
                        && stage != GenerationStage.CANCELLED) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countRecovery(Path placements) throws IOException {
        if (!Files.isDirectory(placements)) {
            return 0;
        }
        int count = 0;
        try (var files = Files.list(placements)) {
            for (Path file : files.filter(value -> value.getFileName().toString().endsWith(".json")).toList()) {
                if (!Files.isSymbolicLink(file)
                        && codec.readPlacementJournalCompatible(file).state() == PlacementState.RECOVERY_REQUIRED) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean checkAtomicMove(Path root) throws IOException {
        Path source = Files.createTempFile(root, ".doctor-", ".tmp");
        Path target = source.resolveSibling(source.getFileName() + ".moved");
        try {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (AtomicMoveNotSupportedException exception) {
                return false;
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
        }
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
