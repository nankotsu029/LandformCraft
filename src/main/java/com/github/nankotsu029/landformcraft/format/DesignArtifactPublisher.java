package com.github.nankotsu029.landformcraft.format;

import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignResult;
import com.github.nankotsu029.landformcraft.model.DesignAudit;
import com.github.nankotsu029.landformcraft.model.ImageInputEvidence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/** Writes and verifies a design package before one atomic directory publish. */
public final class DesignArtifactPublisher {
    private final LandformDataCodec codec = new LandformDataCodec();
    private final DesignArtifactVerifier verifier = new DesignArtifactVerifier();

    public DesignArtifacts publish(
            Path requestPath,
            Path designsRoot,
            UUID jobId,
            String requestId,
            Instant startedAt,
            TerrainDesignResult result,
            ImageInputEvidence imageEvidence
    ) throws IOException {
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
            Path intentPath = temporary.resolve("terrain-intent.json");
            codec.writeTerrainIntent(intentPath, result.intent());
            Path evidencePath = temporary.resolve("image-evidence.json");
            codec.writeImageInputEvidence(evidencePath, imageEvidence);
            ensureNotCancelled();
            String intentChecksum = Sha256.file(intentPath);
            DesignAudit audit = new DesignAudit(
                    1, jobId, requestId, result.providerId(), result.modelId(), result.promptVersion(),
                    result.responseId(), result.usage(), result.attempts(), Sha256.file(requestPath),
                    intentChecksum, startedAt, result.createdAt()
            );
            codec.writeDesignAudit(temporary.resolve("audit.json"), audit);
            ensureNotCancelled();
            String checksums = Sha256.file(temporary.resolve("audit.json")) + "  audit.json\n"
                    + Sha256.file(evidencePath) + "  image-evidence.json\n"
                    + intentChecksum + "  terrain-intent.json\n";
            Files.writeString(
                    temporary.resolve("checksums.sha256"), checksums, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            );
            verifier.verifyForPublish(temporary);
            ensureNotCancelled();
            moveAtomically(temporary, target);
            try {
                ensureNotCancelled();
                DesignVerification verification = verifier.verify(target);
                ensureNotCancelled();
                return new DesignArtifacts(
                        target, verification.intent(), verification.audit(), verification.imageEvidence()
                );
            } catch (IOException | RuntimeException exception) {
                ReleaseVerifier.deleteTree(target);
                throw exception;
            }
        } finally {
            ReleaseVerifier.deleteTree(temporary);
        }
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
