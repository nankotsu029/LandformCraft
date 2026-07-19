package com.github.nankotsu029.landformcraft.format.v2.design;

import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignAuditV2;
import com.github.nankotsu029.landformcraft.model.v2.design.ImageDraftEvidenceV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Strict read-back verifier for Release 2 design packages. */
public final class DesignArtifactVerifierV2 {
    private static final Pattern CHECKSUM_LINE = Pattern.compile("^([0-9a-f]{64})  ([a-z0-9.-]+)$");
    private static final Set<String> FILES_WITH_DRAFT = Set.of(
            DesignArtifactPublisherV2.AUDIT_FILE,
            DesignArtifactPublisherV2.DRAFT_EVIDENCE_FILE,
            DesignArtifactPublisherV2.INTENT_FILE,
            DesignArtifactPublisherV2.CHECKSUMS_FILE
    );
    private static final Set<String> FILES_WITHOUT_DRAFT = Set.of(
            DesignArtifactPublisherV2.AUDIT_FILE,
            DesignArtifactPublisherV2.INTENT_FILE,
            DesignArtifactPublisherV2.CHECKSUMS_FILE
    );
    private static final Pattern SECRET_LIKE = Pattern.compile(
            "(?i)(?:api[_-]?key|authorization|bearer\\s+|sk-[a-z0-9_-]{16,})");

    private final LandformV2DataCodec dataCodec = new LandformV2DataCodec();
    private final DesignPackageCodecV2 packageCodec = new DesignPackageCodecV2();

    public DesignVerificationV2 verify(Path directory) throws IOException {
        return verify(directory, true);
    }

    DesignVerificationV2 verifyForPublish(Path directory) throws IOException {
        return verify(directory, false);
    }

    private DesignVerificationV2 verify(Path directory, boolean verifyDirectoryIdentity) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw new IOException("design artifact must be a non-symbolic directory");
        }
        Set<String> actual;
        try (var stream = Files.list(root)) {
            actual = stream.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
        boolean withDraft = actual.equals(FILES_WITH_DRAFT);
        if (!withDraft && !actual.equals(FILES_WITHOUT_DRAFT)) {
            throw new IOException("design artifact contains a missing or unknown canonical file");
        }
        for (String name : actual) {
            Path file = root.resolve(name);
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new IOException("design artifact contains a non-regular file: " + name);
            }
        }

        Map<String, String> expected = readChecksums(root.resolve(DesignArtifactPublisherV2.CHECKSUMS_FILE));
        Set<String> expectedFiles = withDraft
                ? Set.of(
                        DesignArtifactPublisherV2.AUDIT_FILE,
                        DesignArtifactPublisherV2.DRAFT_EVIDENCE_FILE,
                        DesignArtifactPublisherV2.INTENT_FILE)
                : Set.of(DesignArtifactPublisherV2.AUDIT_FILE, DesignArtifactPublisherV2.INTENT_FILE);
        if (!expected.keySet().equals(expectedFiles)) {
            throw new IOException("checksums.sha256 does not exactly cover design files");
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!Sha256.file(root.resolve(entry.getKey())).equals(entry.getValue())) {
                throw new IOException("checksum mismatch: " + entry.getKey());
            }
        }

        Path auditPath = root.resolve(DesignArtifactPublisherV2.AUDIT_FILE);
        Path intentPath = root.resolve(DesignArtifactPublisherV2.INTENT_FILE);
        rejectSecretLikeContent(auditPath);
        TerrainIntentV2 intent = dataCodec.readTerrainIntent(intentPath);
        DesignAuditV2 audit = packageCodec.readAudit(auditPath);
        Optional<ImageDraftEvidenceV2> draftEvidence = Optional.empty();
        if (withDraft) {
            Path draftPath = root.resolve(DesignArtifactPublisherV2.DRAFT_EVIDENCE_FILE);
            rejectSecretLikeContent(draftPath);
            draftEvidence = Optional.of(packageCodec.readDraftEvidence(draftPath));
        }
        if (!audit.intentChecksum().equals(expected.get(DesignArtifactPublisherV2.INTENT_FILE))) {
            throw new IOException("audit intent checksum is inconsistent");
        }
        if (!audit.requestId().equals(intent.intentId())) {
            throw new IOException("audit request ID is inconsistent with intent ID");
        }
        if (withDraft) {
            ImageDraftEvidenceV2 evidence = draftEvidence.orElseThrow();
            if (evidence.width() <= 0 || evidence.length() <= 0) {
                throw new IOException("draft evidence dimensions are invalid");
            }
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
        return new DesignVerificationV2(root, intent, audit, draftEvidence, expected.size());
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

    private static void rejectSecretLikeContent(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (SECRET_LIKE.matcher(content).find()) {
            throw new IOException("design artifact contains secret-like content: " + path.getFileName());
        }
    }
}
