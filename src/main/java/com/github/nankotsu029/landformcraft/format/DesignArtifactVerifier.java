package com.github.nankotsu029.landformcraft.format;

import com.github.nankotsu029.landformcraft.model.DesignAudit;
import com.github.nankotsu029.landformcraft.model.ImageInputEvidence;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Strict read-back verifier for canonical design packages, including Phase 5 image evidence. */
public final class DesignArtifactVerifier {
    private static final Pattern CHECKSUM_LINE = Pattern.compile("^([0-9a-f]{64})  ([a-z0-9.-]+)$");
    private static final Set<String> CURRENT_FILES = Set.of(
            "audit.json", "image-evidence.json", "terrain-intent.json", "checksums.sha256"
    );
    private static final Set<String> LEGACY_FILES = Set.of(
            "audit.json", "terrain-intent.json", "checksums.sha256"
    );

    private final LandformDataCodec codec = new LandformDataCodec();

    public DesignVerification verify(Path directory) throws IOException {
        return verify(directory, true);
    }

    DesignVerification verifyForPublish(Path directory) throws IOException {
        return verify(directory, false);
    }

    private DesignVerification verify(Path directory, boolean verifyDirectoryIdentity) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw new IOException("design artifact must be a non-symbolic directory");
        }
        Set<String> actual;
        try (var stream = Files.list(root)) {
            actual = stream.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
        boolean currentFormat = actual.equals(CURRENT_FILES);
        if (!currentFormat && !actual.equals(LEGACY_FILES)) {
            throw new IOException("design artifact contains a missing or unknown canonical file");
        }
        for (String name : actual) {
            Path file = root.resolve(name);
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new IOException("design artifact contains a non-regular file: " + name);
            }
        }

        Map<String, String> expected = readChecksums(root.resolve("checksums.sha256"));
        Set<String> expectedFiles = currentFormat
                ? Set.of("audit.json", "image-evidence.json", "terrain-intent.json")
                : Set.of("audit.json", "terrain-intent.json");
        if (!expected.keySet().equals(expectedFiles)) {
            throw new IOException("checksums.sha256 does not exactly cover design files");
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!Sha256.file(root.resolve(entry.getKey())).equals(entry.getValue())) {
                throw new IOException("checksum mismatch: " + entry.getKey());
            }
        }
        TerrainIntent intent = codec.readTerrainIntent(root.resolve("terrain-intent.json"));
        DesignAudit audit = codec.readDesignAudit(root.resolve("audit.json"));
        ImageInputEvidence evidence = currentFormat
                ? codec.readImageInputEvidence(root.resolve("image-evidence.json"))
                : ImageInputEvidence.empty(audit.requestId(), audit.completedAt());
        if (!audit.intentChecksum().equals(expected.get("terrain-intent.json"))) {
            throw new IOException("audit intent checksum is inconsistent");
        }
        if (!evidence.requestId().equals(audit.requestId())) {
            throw new IOException("image evidence request ID is inconsistent");
        }
        if (currentFormat && (!evidence.providerId().equals(audit.providerId())
                || !evidence.providerResponseId().equals(audit.responseId())
                || !evidence.promptVersion().equals(audit.promptVersion()))) {
            throw new IOException("image evidence provider relationship is inconsistent");
        }
        if (verifyDirectoryIdentity) {
            if (!root.getFileName().toString().equals(audit.jobId().toString())) {
                throw new IOException("design directory does not match audit job ID");
            }
            Path parent = root.getParent();
            if (parent == null || !parent.getFileName().toString().equals(audit.requestId())) {
                throw new IOException("design parent directory does not match audit request ID");
            }
        }
        return new DesignVerification(root, intent, audit, evidence, expected.size());
    }

    private static Map<String, String> readChecksums(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() != 2 && lines.size() != 3) {
            throw new IOException("invalid design checksum entry count");
        }
        Map<String, String> values = new TreeMap<>();
        String previous = null;
        for (String line : lines) {
            var matcher = CHECKSUM_LINE.matcher(line);
            if (!matcher.matches()) {
                throw new IOException("invalid design checksum line");
            }
            String name = matcher.group(2);
            if (values.putIfAbsent(name, matcher.group(1)) != null
                    || previous != null && previous.compareTo(name) >= 0) {
                throw new IOException("design checksum paths must be unique and sorted");
            }
            previous = name;
        }
        return Map.copyOf(values);
    }
}
