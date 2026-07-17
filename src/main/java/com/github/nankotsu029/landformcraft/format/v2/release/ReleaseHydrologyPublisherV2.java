package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyRoutingArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.HydrologyValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
import com.github.nankotsu029.landformcraft.preview.v2.HydrologyPreviewIndexCodecV2;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Stages and atomically publishes Release 2 {@code hydrology-plan} with its required
 * {@code surface-2_5d} dependency. Raw source paths do not enter the portable manifest.
 */
public final class ReleaseHydrologyPublisherV2 {
    private final LandformV2DataCodec dataCodec = new LandformV2DataCodec();
    private final HydrologyRoutingArtifactCodecV2 routingCodec = new HydrologyRoutingArtifactCodecV2();
    private final HydrologyReconciliationArtifactCodecV2 reconciliationCodec =
            new HydrologyReconciliationArtifactCodecV2();
    private final HydrologyValidationArtifactCodecV2 validationCodec = new HydrologyValidationArtifactCodecV2();
    private final HydrologyPreviewIndexCodecV2 previewCodec = new HydrologyPreviewIndexCodecV2();
    private final ReleaseManifestCodecV2 manifestCodec = new ReleaseManifestCodecV2();
    private final ReleaseSurfacePublisherV2 surfacePublisher;
    private final ReleaseHydrologyVerifierV2 verifier;
    private final ReleaseV2Limits limits;

    public ReleaseHydrologyPublisherV2() {
        this(ReleaseV2Limits.defaults());
    }

    public ReleaseHydrologyPublisherV2(ReleaseV2Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.surfacePublisher = new ReleaseSurfacePublisherV2(limits);
        this.verifier = new ReleaseHydrologyVerifierV2(limits);
    }

    public ReleaseHydrologyArtifactsV2 publish(
            Path exportsRoot,
            String releaseId,
            HydrologyReleaseSourceV2 source,
            boolean createZip,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(exportsRoot, "exportsRoot");
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();

        ReleaseSurfacePublisherV2.SourceSnapshot surfaceSnapshot =
                surfacePublisher.inspectSource(source.surface(), cancellationToken);
        HydrologySnapshot hydrologySnapshot = inspectHydrology(source, surfaceSnapshot.blueprintChecksum(),
                cancellationToken);
        long sourceBytes = add(surfaceSnapshot.sourceBytes(), hydrologySnapshot.sourceBytes());

        Path root = exportsRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("hydrology Release export root must be a non-symbolic directory");
        }
        ReleaseManifestV2 identity = manifestCodec.seal(new ReleaseManifestV2(releaseId));
        Path finalDirectory = root.resolve(identity.releaseId());
        Path finalZip = root.resolve(identity.releaseId() + ".zip");
        if (Files.exists(finalDirectory, LinkOption.NOFOLLOW_LINKS)
                || createZip && Files.exists(finalZip, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("hydrology Release target already exists");
        }
        ensureDiskBudget(root, sourceBytes, createZip);

        Path stagingDirectory = Files.createTempDirectory(root, ".release-v2-hydrology-stage-");
        Path stagingZip = root.resolve(".release-v2-hydrology-" + identity.releaseId() + ".tmp.zip");
        boolean directoryPublished = false;
        boolean zipPublished = false;
        try {
            List<ReleaseArtifactDescriptorV2> descriptors = new ArrayList<>(surfacePublisher.copyAndDescribe(
                    stagingDirectory, source.surface(), surfaceSnapshot, cancellationToken));
            Set<String> paths = new HashSet<>();
            for (ReleaseArtifactDescriptorV2 descriptor : descriptors) {
                paths.add(descriptor.path());
            }
            descriptors.addAll(copyHydrology(stagingDirectory, source, hydrologySnapshot, paths, cancellationToken));
            ReleaseManifestV2 manifest = manifestCodec.seal(new ReleaseManifestV2(
                    ReleaseManifestV2.RELEASE_FORMAT_VERSION, ReleaseManifestV2.MANIFEST_VERSION, identity.releaseId(),
                    ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE, descriptors,
                    ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
            manifestCodec.write(stagingDirectory.resolve("manifest.json"), manifest);
            verifier.verify(stagingDirectory, cancellationToken);
            forceTree(stagingDirectory);
            cancellationToken.throwIfCancellationRequested();
            moveAtomically(stagingDirectory, finalDirectory);
            directoryPublished = true;
            if (!createZip) {
                return new ReleaseHydrologyArtifactsV2(manifest.releaseId(), finalDirectory, Optional.empty());
            }

            createZip(finalDirectory, stagingZip, cancellationToken);
            verifier.verify(stagingZip, cancellationToken);
            forceFile(stagingZip);
            cancellationToken.throwIfCancellationRequested();
            moveAtomically(stagingZip, finalZip);
            zipPublished = true;
            return new ReleaseHydrologyArtifactsV2(manifest.releaseId(), finalDirectory, Optional.of(finalZip));
        } catch (IOException | RuntimeException exception) {
            if (!zipPublished) Files.deleteIfExists(stagingZip);
            if (directoryPublished) ReleaseCoreVerifierV2.deleteTree(finalDirectory);
            throw exception;
        } finally {
            ReleaseCoreVerifierV2.deleteTree(stagingDirectory);
            if (!zipPublished) Files.deleteIfExists(stagingZip);
        }
    }

    private HydrologySnapshot inspectHydrology(
            HydrologyReleaseSourceV2 source,
            String blueprintChecksum,
            CancellationToken token
    ) throws IOException {
        requireSafeRegular(source.hydrologyPlan());
        requireSafeRegular(source.routingIndex());
        requireSafeDirectory(source.routingRoot());
        requireSafeRegular(source.reconciliationPlan());
        requireSafeRegular(source.reconciliationArtifact());
        requireSafeRegular(source.hydrologyValidationArtifact());
        requireSafeRegular(source.hydrologyPreviewIndex());
        requireSafeDirectory(source.hydrologyPreviewRoot());

        WorldBlueprintV2 blueprint = dataCodec.readWorldBlueprint(source.surface().worldBlueprint());
        if (!blueprint.canonicalChecksum().equals(blueprintChecksum)) {
            throw new IOException("hydrology Release Blueprint changed after surface inspection");
        }
        HydrologyPlanV2 plan = dataCodec.readHydrologyPlan(source.hydrologyPlan());
        if (!plan.equals(blueprint.hydrologyPlan())) {
            throw new IOException("hydrology plan source differs from the released Blueprint");
        }
        HydrologyRoutingArtifactV2 routing = routingCodec.readAndVerify(
                source.routingIndex(), source.routingRoot(), token);
        if (!routing.sourceHydrologyPlanChecksum().equals(plan.canonicalChecksum())
                || routing.width() != blueprint.space().bounds().width()
                || routing.length() != blueprint.space().bounds().length()) {
            throw new IOException("hydrology routing source does not bind to plan and Blueprint");
        }
        HydrologyReconciliationPlanV2 reconciliationPlan =
                dataCodec.readHydrologyReconciliationPlan(source.reconciliationPlan());
        if (!reconciliationPlan.equals(blueprint.hydrologyReconciliationPlan())) {
            throw new IOException("hydrology reconciliation plan source differs from the Blueprint");
        }
        HydrologyReconciliationArtifactV2 reconciliationArtifact =
                reconciliationCodec.read(source.reconciliationArtifact());
        if (!reconciliationArtifact.sourceBlueprintChecksum().equals(blueprint.canonicalChecksum())
                || !reconciliationArtifact.sourcePlanChecksum().equals(reconciliationPlan.canonicalChecksum())) {
            throw new IOException("hydrology reconciliation artifact source binding differs");
        }
        HydrologyValidationArtifactV2 validation = validationCodec.read(source.hydrologyValidationArtifact());
        if (!validation.sourceBlueprintChecksum().equals(blueprint.canonicalChecksum())
                || !validation.report().passesHardValidation()) {
            throw new IOException("hydrology validation source does not pass hard validation");
        }
        HydrologyPreviewIndexV2 previews = previewCodec.readAndVerify(
                source.hydrologyPreviewIndex(), source.hydrologyPreviewRoot(), token);
        if (!previews.sourceBlueprintChecksum().equals(blueprint.canonicalChecksum())
                || previews.width() != blueprint.space().bounds().width()
                || previews.length() != blueprint.space().bounds().length()) {
            throw new IOException("hydrology preview source does not bind to the Blueprint");
        }
        long bytes = Files.size(source.hydrologyPlan());
        bytes = add(bytes, Files.size(source.routingIndex()));
        for (var field : routing.fields()) {
            bytes = add(bytes, Files.size(source.routingRoot().resolve(field.relativePath())));
        }
        bytes = add(bytes, Files.size(source.reconciliationPlan()));
        bytes = add(bytes, Files.size(source.reconciliationArtifact()));
        bytes = add(bytes, Files.size(source.hydrologyValidationArtifact()));
        bytes = add(bytes, Files.size(source.hydrologyPreviewIndex()));
        for (HydrologyPreviewIndexV2.Layer layer : previews.layers()) {
            bytes = add(bytes, Files.size(source.hydrologyPreviewRoot().resolve(layer.path())));
        }
        return new HydrologySnapshot(plan, routing, reconciliationPlan, reconciliationArtifact, validation, previews,
                bytes);
    }

    private List<ReleaseArtifactDescriptorV2> copyHydrology(
            Path staging,
            HydrologyReleaseSourceV2 source,
            HydrologySnapshot snapshot,
            Set<String> paths,
            CancellationToken token
    ) throws IOException {
        List<ReleaseArtifactDescriptorV2> result = new ArrayList<>();
        copyAndAdd(result, paths, "hydrology-plan", HydrologyReleaseCapabilityVerifierV2.PLAN_TYPE,
                HydrologyReleaseCapabilityVerifierV2.PLAN_PATH, source.hydrologyPlan(),
                snapshot.plan().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "hydrology-routing", HydrologyReleaseCapabilityVerifierV2.ROUTING_TYPE,
                HydrologyReleaseCapabilityVerifierV2.ROUTING_INDEX_PATH, source.routingIndex(),
                snapshot.routing().canonicalChecksum(), staging, token);
        for (var field : snapshot.routing().fields()) {
            copyAndAdd(result, paths, "hydrology-field." + field.definition().fieldId(),
                    HydrologyReleaseCapabilityVerifierV2.FIELD_GRID_TYPE,
                    "hydrology/routing/" + field.relativePath(),
                    source.routingRoot().resolve(field.relativePath()), field.semanticChecksum(), staging, token);
        }
        copyAndAdd(result, paths, "hydrology-reconciliation-plan",
                HydrologyReleaseCapabilityVerifierV2.RECONCILIATION_PLAN_TYPE,
                HydrologyReleaseCapabilityVerifierV2.RECONCILIATION_PLAN_PATH, source.reconciliationPlan(),
                snapshot.reconciliationPlan().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "hydrology-reconciliation-artifact",
                HydrologyReleaseCapabilityVerifierV2.RECONCILIATION_ARTIFACT_TYPE,
                HydrologyReleaseCapabilityVerifierV2.RECONCILIATION_ARTIFACT_PATH, source.reconciliationArtifact(),
                snapshot.reconciliationArtifact().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "hydrology-validation", HydrologyReleaseCapabilityVerifierV2.VALIDATION_TYPE,
                HydrologyReleaseCapabilityVerifierV2.VALIDATION_PATH, source.hydrologyValidationArtifact(),
                snapshot.validation().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "hydrology-preview-index", HydrologyReleaseCapabilityVerifierV2.PREVIEW_INDEX_TYPE,
                HydrologyReleaseCapabilityVerifierV2.PREVIEW_INDEX_PATH, source.hydrologyPreviewIndex(),
                snapshot.previews().canonicalChecksum(), staging, token);
        for (HydrologyPreviewIndexV2.Layer layer : snapshot.previews().layers()) {
            copyAndAdd(result, paths, "hydrology-preview." + layer.layerId().name().toLowerCase(Locale.ROOT),
                    HydrologyReleaseCapabilityVerifierV2.PREVIEW_PNG_TYPE,
                    "hydrology/previews/" + layer.path(),
                    source.hydrologyPreviewRoot().resolve(layer.path()), layer.sha256(), staging, token);
        }
        return List.copyOf(result);
    }

    private static void copyAndAdd(
            List<ReleaseArtifactDescriptorV2> descriptors,
            Set<String> paths,
            String id,
            String type,
            String targetPath,
            Path source,
            String semanticChecksum,
            Path staging,
            CancellationToken token
    ) throws IOException {
        token.throwIfCancellationRequested();
        String canonical = ReleaseV2Paths.canonicalRelativePath(targetPath);
        if (!paths.add(canonical)) throw new IOException("hydrology Release source maps multiple artifacts to one path");
        Path target = staging.resolve(canonical).normalize();
        if (!target.startsWith(staging)) throw new IOException("hydrology Release artifact path escapes staging root");
        Files.createDirectories(Objects.requireNonNull(target.getParent()));
        FileFingerprint before = fingerprint(source);
        Files.copy(source, target, LinkOption.NOFOLLOW_LINKS);
        requireStable(source, before);
        requireSafeRegular(target);
        descriptors.add(new ReleaseArtifactDescriptorV2(id, type, 1, canonical, Files.size(target),
                Sha256.file(target), semanticChecksum));
    }

    private void ensureDiskBudget(Path root, long sourceBytes, boolean createZip) throws IOException {
        long multiplier = createZip ? 3L : 2L;
        long expected;
        try {
            expected = Math.addExact(Math.multiplyExact(sourceBytes, multiplier), 1024L * 1024L);
        } catch (ArithmeticException exception) {
            throw new IOException("hydrology Release disk estimate overflow", exception);
        }
        if (sourceBytes > limits.maximumDirectoryBytes()
                || expected > limits.maximumDirectoryBytes() + limits.maximumZipBytes()
                || Files.getFileStore(root).getUsableSpace() < expected) {
            throw new IOException("insufficient disk budget for hydrology Release staging and publish");
        }
    }

    private void createZip(Path releaseDirectory, Path stagingZip, CancellationToken token) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(releaseDirectory)) {
            files = stream.filter(Files::isRegularFile).sorted(java.util.Comparator.comparing(
                    path -> releaseDirectory.relativize(path).toString().replace('\\', '/'))).toList();
        }
        try (var file = Files.newOutputStream(stagingZip, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             var output = new ZipOutputStream(new BufferedOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
            for (Path path : files) {
                token.throwIfCancellationRequested();
                ZipEntry entry = new ZipEntry(releaseDirectory.relativize(path).toString().replace('\\', '/'));
                entry.setTime(0L);
                output.putNextEntry(entry);
                Files.copy(path, output);
                output.closeEntry();
            }
        }
        if (Files.size(stagingZip) > limits.maximumZipBytes()) {
            throw new IOException("hydrology Release ZIP exceeds its compressed byte budget");
        }
    }

    private static void forceTree(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) forceFile(file);
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("filesystem does not support required hydrology Release atomic publish", exception);
        }
    }

    private static void requireSafeDirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("hydrology Release source directory must be a non-symbolic directory");
        }
    }

    private static void requireSafeRegular(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("hydrology Release source artifact must be a regular non-symbolic file");
        }
    }

    private static FileFingerprint fingerprint(Path path) throws IOException {
        requireSafeRegular(path);
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return new FileFingerprint(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime());
    }

    private static void requireStable(Path path, FileFingerprint before) throws IOException {
        if (!before.equals(fingerprint(path))) {
            throw new IOException("hydrology Release source changed while it was being staged");
        }
    }

    private static long add(long current, long addition) throws IOException {
        try {
            return Math.addExact(current, addition);
        } catch (ArithmeticException exception) {
            throw new IOException("hydrology Release source byte total overflow", exception);
        }
    }

    private record HydrologySnapshot(
            HydrologyPlanV2 plan,
            HydrologyRoutingArtifactV2 routing,
            HydrologyReconciliationPlanV2 reconciliationPlan,
            HydrologyReconciliationArtifactV2 reconciliationArtifact,
            HydrologyValidationArtifactV2 validation,
            HydrologyPreviewIndexV2 previews,
            long sourceBytes
    ) { }

    private record FileFingerprint(Object fileKey, long size, java.nio.file.attribute.FileTime modified) { }
}
