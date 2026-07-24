package com.github.nankotsu029.landformcraft.core.v2.migration;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.ReleaseVerifier;
import com.github.nankotsu029.landformcraft.format.FileTreeOperations;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactPublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactsV2;
import com.github.nankotsu029.landformcraft.format.v2.migration.LegacyMigrationBundleV2;
import com.github.nankotsu029.landformcraft.format.v2.migration.LegacyMigrationBundleVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.migration.LegacyMigrationReportCodecV2;
import com.github.nankotsu029.landformcraft.model.ProviderUsage;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignAuditV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationReportV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationStatusV2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Explicit v1 → v2 migration (V2-12-04, ADR 0035 D9).
 *
 * <p>One invocation reads exactly one v1 asset the operator names, maps what v1 stated exactly onto
 * the v2 intent contract, records everything it could not carry, and — unless this is a dry run —
 * publishes a migration bundle through staging, strict read-back and one atomic move. The source is
 * never written to, an existing bundle is never overwritten, and the same source always produces the
 * same bundle because every identifier and timestamp is derived from the source itself.</p>
 *
 * <p>What migration deliberately does <em>not</em> do is turn a Release 1 back into a Release 2. A
 * Release 2 container is sealed from a compiled v2 Blueprint with module, stage and field
 * descriptors; a Release 1 carries none of them. The migrated intent is the input to the normal
 * {@code lfc v2 export} path, and the report says so.</p>
 */
public final class LegacyMigrationApplicationServiceV2 {
    /** Namespace for the deterministic job UUID, so unrelated digests can never collide with one. */
    private static final String JOB_NAMESPACE = "landformcraft/v1-migration/";
    private static final String PROVIDER_ID = "landformcraft-v1-migration";
    private static final String MODEL_ID = "v1-intent-upgrade";
    private static final String PROMPT_VERSION = "v2-12-04";
    private static final String CATALOG_VERSION = "v2-12-04";

    private final GenerationExecutors executors;
    private final LegacyV1ArtifactReaderV2 reader = new LegacyV1ArtifactReaderV2();
    private final LegacyV1IntentMapperV2 mapper = new LegacyV1IntentMapperV2();
    private final DesignArtifactPublisherV2 designPublisher = new DesignArtifactPublisherV2();
    private final LegacyMigrationReportCodecV2 reportCodec = new LegacyMigrationReportCodecV2();
    private final LegacyMigrationBundleVerifierV2 bundleVerifier = new LegacyMigrationBundleVerifierV2();

    public LegacyMigrationApplicationServiceV2(GenerationExecutors executors) {
        this.executors = Objects.requireNonNull(executors, "executors");
    }

    /** Asynchronous entry point. Strict reads and artifact I/O never run on the caller's thread. */
    public CompletableFuture<LegacyMigrationResultV2> migrate(LegacyMigrationRequestV2 request) {
        Objects.requireNonNull(request, "request");
        return executors.supplyIo(() -> {
            try {
                return migrateNow(request);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    /** Synchronous entry point for callers that already own a worker thread. */
    public LegacyMigrationResultV2 migrateNow(LegacyMigrationRequestV2 request) throws IOException {
        Objects.requireNonNull(request, "request");
        Path scratchParent = request.outputRoot()
                .map(root -> root.toAbsolutePath().normalize())
                .orElseGet(() -> Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize());
        Files.createDirectories(scratchParent);
        Path staging = Files.createTempDirectory(scratchParent, ".lfc-migration-");
        try {
            return run(request, staging);
        } finally {
            FileTreeOperations.deleteTree(staging);
        }
    }

    private LegacyMigrationResultV2 run(LegacyMigrationRequestV2 request, Path staging) throws IOException {
        Path target = request.outputRoot()
                .map(root -> root.toAbsolutePath().normalize().resolve(request.migrationId()))
                .orElse(null);
        if (target != null && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("migration target already exists and is never overwritten: " + target);
        }

        LegacyV1SourceV2 source = reader.read(request.sourceKind(), request.source(),
                staging.resolve("read"));
        // The Release 2 design package binds intentId to the audit requestId, and both are derived
        // from the source, so the produced intent never depends on the operator's bundle name.
        LegacyV1IntentMapperV2.MappingV2 mapping = mapper.map(source.requestId(), source.intent());
        List<LegacyMigrationReportV2.UnmappedElement> unmapped = concat(
                mapping.unmapped(), source.carrierUnmapped());
        if (!unmapped.isEmpty() && !request.acceptLossy()) {
            throw new IOException("this v1 source carries " + unmapped.size() + " element(s) v2 cannot "
                    + "express without inventing terrain; rehearse with the migration dry run and, if the "
                    + "loss is acceptable, re-run with the lossy migration explicitly accepted");
        }

        UUID jobId = deterministicJobId(request.sourceKind().name(), source.digest());
        Path designsRoot = staging.resolve(LegacyMigrationBundleVerifierV2.DESIGNS_DIRECTORY);
        DesignArtifactsV2 design = designPublisher.publish(
                request.source(),
                designsRoot,
                jobId,
                source.requestId(),
                source.sealedAt(),
                mapping.intent(),
                audit(jobId, source),
                Optional.empty());

        LegacyMigrationReportV2 report = new LegacyMigrationReportV2(
                LegacyMigrationReportV2.VERSION,
                request.migrationId(),
                source.kind(),
                source.schemaVersion(),
                source.digest(),
                source.canonicalChecksum(),
                source.requestId(),
                jobId.toString(),
                TerrainIntentV2.VERSION,
                design.audit().intentChecksum(),
                mapping.mappedFields(),
                unmapped,
                request.dryRun() ? LegacyMigrationStatusV2.DRY_RUN : LegacyMigrationStatusV2.PUBLISHED);

        if (request.dryRun()) {
            // The rehearsal built the same design package the publish would have built, inside the
            // staging directory that the caller's finally block deletes. Nothing reaches the operator's
            // output root, so a dry run cannot leave a half-migrated asset behind.
            return new LegacyMigrationResultV2(report, Optional.empty());
        }

        reportCodec.write(staging.resolve(LegacyMigrationBundleVerifierV2.REPORT_FILE), report);
        bundleVerifier.writeChecksums(staging);
        bundleVerifier.verify(staging);
        moveAtomically(staging, Objects.requireNonNull(target, "target"));
        LegacyMigrationBundleV2 bundle;
        try {
            bundle = bundleVerifier.verify(target);
        } catch (IOException | RuntimeException exception) {
            FileTreeOperations.deleteTree(target);
            throw exception;
        }
        return new LegacyMigrationResultV2(bundle.report(), Optional.of(bundle));
    }

    /**
     * Audit for the migrated package. Every value is derived from the source, so re-running the
     * migration of the same asset produces byte-identical artifacts (ADR 0035 D9-4). The intent
     * checksum is a placeholder the publisher reseals with the checksum of the file it actually
     * wrote.
     */
    private static DesignAuditV2 audit(UUID jobId, LegacyV1SourceV2 source) {
        return new DesignAuditV2(
                DesignAuditV2.VERSION,
                jobId,
                source.requestId(),
                DesignPathKindV2.IMPORT,
                PROVIDER_ID,
                MODEL_ID,
                PROMPT_VERSION,
                source.digest(),
                ProviderUsage.ZERO,
                1,
                source.digest(),
                source.digest(),
                Set.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                CATALOG_VERSION,
                source.sealedAt(),
                source.sealedAt(),
                // V2-19-08: a migration converts a sealed v1 asset; it designs nothing against the
                // current dispatch registry, so it records no support lint and its bytes are unchanged.
                null);
    }

    private static UUID deterministicJobId(String kind, String digest) {
        return UUID.nameUUIDFromBytes((JOB_NAMESPACE + kind + "/" + digest).getBytes(StandardCharsets.UTF_8));
    }

    private static List<LegacyMigrationReportV2.UnmappedElement> concat(
            List<LegacyMigrationReportV2.UnmappedElement> first,
            List<LegacyMigrationReportV2.UnmappedElement> second
    ) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        Path parent = Objects.requireNonNull(target.getParent(), "migration target requires a parent");
        Files.createDirectories(parent);
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("atomic migration bundle publish is not supported", exception);
        }
    }

    /** Machine-readable summary of one migration. Contains no absolute source paths. */
    public static java.util.Map<String, Object> summarize(LegacyMigrationResultV2 result) {
        Objects.requireNonNull(result, "result");
        LegacyMigrationReportV2 report = result.report();
        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("status", report.status().name());
        summary.put("migrationId", report.migrationId());
        summary.put("sourceKind", report.sourceKind().name());
        summary.put("sourceSchemaVersion", report.sourceSchemaVersion());
        summary.put("sourceDigest", report.sourceDigest());
        summary.put("sourceCanonicalChecksum", report.sourceCanonicalChecksum());
        summary.put("targetRequestId", report.targetRequestId());
        summary.put("targetJobId", report.targetJobId());
        summary.put("targetIntentChecksum", report.targetIntentChecksum());
        summary.put("mappedFields", report.mappedFields());
        summary.put("lossy", report.lossy());
        summary.put("unmappedElements", report.unmappedElements().stream()
                .map(element -> element.elementId() + " (" + element.sourceField() + "): " + element.reason())
                .toList());
        summary.put("bundle", result.bundle()
                .map(bundle -> bundle.root().getFileName().toString())
                .orElse(""));
        summary.put("verifiedFiles", result.bundle().map(LegacyMigrationBundleV2::verifiedFiles).orElse(0));
        return java.util.Collections.unmodifiableMap(summary);
    }
}
