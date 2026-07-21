package com.github.nankotsu029.landformcraft.format.v2.design;

import com.github.nankotsu029.landformcraft.format.FileTreeOperations;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignAuditV2;
import com.github.nankotsu029.landformcraft.model.v2.design.ImageDraftEvidenceV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/** Writes and verifies a Release 2 design package before one atomic directory publish. */
public final class DesignArtifactPublisherV2 {
    public static final String INTENT_FILE = "terrain-intent-v2.json";
    public static final String AUDIT_FILE = "audit-v2.json";
    public static final String DRAFT_EVIDENCE_FILE = "image-draft-evidence-v2.json";
    public static final String CHECKSUMS_FILE = "checksums.sha256";

    private final LandformV2DataCodec dataCodec = new LandformV2DataCodec();
    private final DesignPackageCodecV2 packageCodec = new DesignPackageCodecV2();
    private final DesignArtifactVerifierV2 verifier = new DesignArtifactVerifierV2();

    public DesignArtifactsV2 publish(
            Path requestPath,
            Path designsRoot,
            UUID jobId,
            String requestId,
            Instant startedAt,
            TerrainIntentV2 intent,
            DesignAuditV2 audit,
            Optional<ImageDraftEvidenceV2> draftEvidence
    ) throws IOException {
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(designsRoot, "designsRoot");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(draftEvidence, "draftEvidence");

        Path requestRoot = designsRoot.toAbsolutePath().normalize().resolve(requestId);
        Files.createDirectories(requestRoot);
        Path target = requestRoot.resolve(jobId.toString());
        if (Files.exists(target)) {
            throw new IOException("design artifact already exists: " + target);
        }
        Path temporary = requestRoot.resolve(".tmp-" + jobId + "-" + UUID.randomUUID());
        try {
            ensureNotCancelled();
            Files.createDirectory(temporary);
            Path intentPath = temporary.resolve(INTENT_FILE);
            dataCodec.writeTerrainIntent(intentPath, intent);
            ensureNotCancelled();
            String intentFileChecksum = Sha256.file(intentPath);
            DesignAuditV2 sealedAudit = resealAudit(audit, intentFileChecksum);
            packageCodec.writeAudit(temporary.resolve(AUDIT_FILE), sealedAudit);
            Optional<Path> draftPath = Optional.empty();
            if (draftEvidence.isPresent()) {
                draftPath = Optional.of(temporary.resolve(DRAFT_EVIDENCE_FILE));
                packageCodec.writeDraftEvidence(draftPath.get(), draftEvidence.get());
            }
            ensureNotCancelled();
            writeChecksums(temporary, draftEvidence.isPresent());
            verifier.verifyForPublish(temporary);
            ensureNotCancelled();
            moveAtomically(temporary, target);
            try {
                ensureNotCancelled();
                DesignVerificationV2 verification = verifier.verify(target);
                ensureNotCancelled();
                return new DesignArtifactsV2(
                        target,
                        verification.intent(),
                        verification.audit(),
                        verification.draftEvidence()
                );
            } catch (IOException | RuntimeException exception) {
                FileTreeOperations.deleteTree(target);
                throw exception;
            }
        } finally {
            FileTreeOperations.deleteTree(temporary);
        }
    }

    private static DesignAuditV2 resealAudit(DesignAuditV2 audit, String intentFileChecksum) {
        return new DesignAuditV2(
                audit.schemaVersion(),
                audit.jobId(),
                audit.requestId(),
                audit.pathKind(),
                audit.providerId(),
                audit.modelId(),
                audit.promptVersion(),
                audit.responseId(),
                audit.usage(),
                audit.attempts(),
                audit.requestChecksum(),
                intentFileChecksum,
                audit.negotiatedCapabilities(),
                audit.capabilityCatalogVersion(),
                audit.startedAt(),
                audit.completedAt()
        );
    }

    private void writeChecksums(Path root, boolean includeDraftEvidence) throws IOException {
        Map<String, String> checksums = new TreeMap<>();
        checksums.put(AUDIT_FILE, Sha256.file(root.resolve(AUDIT_FILE)));
        if (includeDraftEvidence) {
            checksums.put(DRAFT_EVIDENCE_FILE, Sha256.file(root.resolve(DRAFT_EVIDENCE_FILE)));
        }
        checksums.put(INTENT_FILE, Sha256.file(root.resolve(INTENT_FILE)));
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : checksums.entrySet()) {
            body.append(entry.getValue()).append("  ").append(entry.getKey()).append('\n');
        }
        Files.writeString(
                root.resolve(CHECKSUMS_FILE),
                body.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("atomic design artifact publish is not supported", exception);
        }
    }

    private static void ensureNotCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("design artifact publication cancelled");
        }
    }
}
