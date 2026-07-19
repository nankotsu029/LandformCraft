package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.validation.EnvironmentValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.EnvironmentPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.EnvironmentValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.feature.FeatureMaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;
import com.github.nankotsu029.landformcraft.preview.v2.EnvironmentPreviewIndexCodecV2;

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
 * Stages and atomically publishes Release 2 {@code environment-fields} with its required
 * {@code hydrology-plan} and {@code surface-2_5d} dependencies.
 */
public final class ReleaseEnvironmentPublisherV2 {
    private final LandformV2DataCodec dataCodec = new LandformV2DataCodec();
    private final EnvironmentValidationArtifactCodecV2 validationCodec = new EnvironmentValidationArtifactCodecV2();
    private final EnvironmentPreviewIndexCodecV2 previewCodec = new EnvironmentPreviewIndexCodecV2();
    private final ReleaseManifestCodecV2 manifestCodec = new ReleaseManifestCodecV2();
    private final ReleaseSurfacePublisherV2 surfacePublisher;
    private final ReleaseHydrologyPublisherV2 hydrologyPublisher;
    private final ReleaseEnvironmentVerifierV2 verifier;
    private final ReleaseV2Limits limits;

    public ReleaseEnvironmentPublisherV2() {
        this(ReleaseV2Limits.defaults());
    }

    public ReleaseEnvironmentPublisherV2(ReleaseV2Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.surfacePublisher = new ReleaseSurfacePublisherV2(limits);
        this.hydrologyPublisher = new ReleaseHydrologyPublisherV2(limits);
        this.verifier = new ReleaseEnvironmentVerifierV2(limits);
    }

    public ReleaseEnvironmentArtifactsV2 publish(
            Path exportsRoot,
            String releaseId,
            EnvironmentReleaseSourceV2 source,
            boolean createZip,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(exportsRoot, "exportsRoot");
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();

        ReleaseSurfacePublisherV2.SourceSnapshot surfaceSnapshot =
                surfacePublisher.inspectSource(source.hydrology().surface(), cancellationToken);
        ReleaseHydrologyPublisherV2.HydrologySnapshot hydrologySnapshot = hydrologyPublisher.inspectHydrology(
                source.hydrology(), surfaceSnapshot.blueprintChecksum(), cancellationToken);
        EnvironmentSnapshot environmentSnapshot = inspectEnvironment(
                source, surfaceSnapshot.blueprintChecksum(), cancellationToken);
        long sourceBytes = add(add(surfaceSnapshot.sourceBytes(), hydrologySnapshot.sourceBytes()),
                environmentSnapshot.sourceBytes());

        Path root = exportsRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("environment Release export root must be a non-symbolic directory");
        }
        ReleaseManifestV2 identity = manifestCodec.seal(new ReleaseManifestV2(releaseId));
        Path finalDirectory = root.resolve(identity.releaseId());
        Path finalZip = root.resolve(identity.releaseId() + ".zip");
        if (Files.exists(finalDirectory, LinkOption.NOFOLLOW_LINKS)
                || createZip && Files.exists(finalZip, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("environment Release target already exists");
        }
        ensureDiskBudget(root, sourceBytes, createZip);

        Path stagingDirectory = Files.createTempDirectory(root, ".release-v2-environment-stage-");
        Path stagingZip = root.resolve(".release-v2-environment-" + identity.releaseId() + ".tmp.zip");
        boolean directoryPublished = false;
        boolean zipPublished = false;
        try {
            List<ReleaseArtifactDescriptorV2> descriptors = new ArrayList<>(surfacePublisher.copyAndDescribe(
                    stagingDirectory, source.hydrology().surface(), surfaceSnapshot, cancellationToken));
            Set<String> paths = new HashSet<>();
            for (ReleaseArtifactDescriptorV2 descriptor : descriptors) {
                paths.add(descriptor.path());
            }
            descriptors.addAll(hydrologyPublisher.copyHydrology(
                    stagingDirectory, source.hydrology(), hydrologySnapshot, paths, cancellationToken));
            descriptors.addAll(copyEnvironment(
                    stagingDirectory, source, environmentSnapshot, paths, cancellationToken));
            ReleaseManifestV2 manifest = manifestCodec.seal(new ReleaseManifestV2(
                    ReleaseManifestV2.RELEASE_FORMAT_VERSION, ReleaseManifestV2.MANIFEST_VERSION, identity.releaseId(),
                    ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE, descriptors,
                    ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
            manifestCodec.write(stagingDirectory.resolve("manifest.json"), manifest);
            verifier.verify(stagingDirectory, cancellationToken);
            forceTree(stagingDirectory);
            cancellationToken.throwIfCancellationRequested();
            moveAtomically(stagingDirectory, finalDirectory);
            directoryPublished = true;
            if (!createZip) {
                return new ReleaseEnvironmentArtifactsV2(manifest.releaseId(), finalDirectory, Optional.empty());
            }

            createZip(finalDirectory, stagingZip, cancellationToken);
            verifier.verify(stagingZip, cancellationToken);
            forceFile(stagingZip);
            cancellationToken.throwIfCancellationRequested();
            moveAtomically(stagingZip, finalZip);
            zipPublished = true;
            return new ReleaseEnvironmentArtifactsV2(manifest.releaseId(), finalDirectory, Optional.of(finalZip));
        } catch (IOException | RuntimeException exception) {
            if (!zipPublished) {
                Files.deleteIfExists(stagingZip);
            }
            if (directoryPublished) {
                ReleaseCoreVerifierV2.deleteTree(finalDirectory);
            }
            throw exception;
        } finally {
            ReleaseCoreVerifierV2.deleteTree(stagingDirectory);
            if (!zipPublished) {
                Files.deleteIfExists(stagingZip);
            }
        }
    }

    EnvironmentSnapshot inspectEnvironment(
            EnvironmentReleaseSourceV2 source,
            String blueprintChecksum,
            CancellationToken token
    ) throws IOException {
        requireSafeRegular(source.geologyPlan());
        requireSafeRegular(source.lithologyPlan());
        requireSafeRegular(source.strataPlan());
        requireSafeRegular(source.climatePlan());
        requireSafeRegular(source.waterConditionPlan());
        requireSafeRegular(source.snowPlan());
        requireSafeRegular(source.materialProfilePlan());
        requireSafeRegular(source.minecraftPalettePlan());
        requireSafeRegular(source.ecologyPlan());
        requireSafeRegular(source.featureMaterialProfilePlan());
        requireSafeRegular(source.environmentValidationArtifact());
        requireSafeRegular(source.environmentPreviewIndex());
        requireSafeDirectory(source.environmentPreviewRoot());

        WorldBlueprintV2 blueprint = dataCodec.readWorldBlueprint(source.hydrology().surface().worldBlueprint());
        if (!blueprint.canonicalChecksum().equals(blueprintChecksum)) {
            throw new IOException("environment Release Blueprint changed after surface inspection");
        }
        GeologyPlanV2 geology = dataCodec.readGeologyPlan(source.geologyPlan());
        if (!geology.equals(blueprint.geologyPlan())) {
            throw new IOException("geology plan source differs from the released Blueprint");
        }
        LithologyPlanV2 lithology = dataCodec.readLithologyPlan(source.lithologyPlan());
        if (!lithology.equals(blueprint.lithologyPlan())) {
            throw new IOException("lithology plan source differs from the released Blueprint");
        }
        StrataPlanV2 strata = dataCodec.readStrataPlan(source.strataPlan());
        if (!strata.equals(blueprint.strataPlan())) {
            throw new IOException("strata plan source differs from the released Blueprint");
        }
        ClimatePlanV2 climate = dataCodec.readClimatePlan(source.climatePlan());
        if (!climate.equals(blueprint.climatePlan())) {
            throw new IOException("climate plan source differs from the released Blueprint");
        }
        WaterConditionPlanV2 water = dataCodec.readWaterConditionPlan(source.waterConditionPlan());
        if (!water.equals(blueprint.waterConditionPlan())) {
            throw new IOException("water-condition plan source differs from the released Blueprint");
        }
        SnowPlanV2 snow = dataCodec.readSnowPlan(source.snowPlan());
        snow.requireClimatePlan(climate);
        MaterialProfilePlanV2 material = dataCodec.readMaterialProfilePlan(source.materialProfilePlan());
        material.requireGeologyPlan(geology, lithology, strata);
        material.requireWaterConditionPlan(water);
        material.requireSnowPlan(snow);
        MinecraftPalettePlanV2 palette = dataCodec.readMinecraftPalettePlan(source.minecraftPalettePlan());
        palette.requireMaterialProfilePlan(material);
        EcologyPlanV2 ecology = dataCodec.readEcologyPlan(source.ecologyPlan());
        ecology.requireClimatePlan(climate);
        ecology.requireWaterConditionPlan(water);
        ecology.requireSnowPlan(snow);
        FeatureMaterialProfilePlanV2 feature =
                dataCodec.readFeatureMaterialProfilePlan(source.featureMaterialProfilePlan());
        feature.requireMaterialProfilePlan(material);
        feature.requireGeologyPlan(geology, lithology, strata);
        EnvironmentValidationArtifactV2 validation =
                validationCodec.read(source.environmentValidationArtifact());
        if (!validation.sourcePlanChecksum().equals(blueprint.canonicalChecksum())
                || !validation.report().passesHardValidation()) {
            throw new IOException("environment validation source does not pass hard validation");
        }
        EnvironmentPreviewIndexV2 previews = previewCodec.readAndVerify(
                source.environmentPreviewIndex(), source.environmentPreviewRoot(), token);
        if (!previews.sourcePlanChecksum().equals(blueprint.canonicalChecksum())
                || previews.width() != blueprint.space().bounds().width()
                || previews.length() != blueprint.space().bounds().length()) {
            throw new IOException("environment preview source does not bind to the Blueprint");
        }
        long bytes = Files.size(source.geologyPlan());
        bytes = add(bytes, Files.size(source.lithologyPlan()));
        bytes = add(bytes, Files.size(source.strataPlan()));
        bytes = add(bytes, Files.size(source.climatePlan()));
        bytes = add(bytes, Files.size(source.waterConditionPlan()));
        bytes = add(bytes, Files.size(source.snowPlan()));
        bytes = add(bytes, Files.size(source.materialProfilePlan()));
        bytes = add(bytes, Files.size(source.minecraftPalettePlan()));
        bytes = add(bytes, Files.size(source.ecologyPlan()));
        bytes = add(bytes, Files.size(source.featureMaterialProfilePlan()));
        bytes = add(bytes, Files.size(source.environmentValidationArtifact()));
        bytes = add(bytes, Files.size(source.environmentPreviewIndex()));
        for (EnvironmentPreviewIndexV2.Layer layer : previews.layers()) {
            bytes = add(bytes, Files.size(source.environmentPreviewRoot().resolve(layer.path())));
        }
        return new EnvironmentSnapshot(
                geology, lithology, strata, climate, water, snow, material, palette, ecology, feature,
                validation, previews, bytes);
    }

    List<ReleaseArtifactDescriptorV2> copyEnvironment(
            Path staging,
            EnvironmentReleaseSourceV2 source,
            EnvironmentSnapshot snapshot,
            Set<String> paths,
            CancellationToken token
    ) throws IOException {
        List<ReleaseArtifactDescriptorV2> result = new ArrayList<>();
        copyAndAdd(result, paths, "environment-geology", EnvironmentReleaseCapabilityVerifierV2.GEOLOGY_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.GEOLOGY_PATH, source.geologyPlan(),
                snapshot.geology().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "environment-lithology", EnvironmentReleaseCapabilityVerifierV2.LITHOLOGY_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.LITHOLOGY_PATH, source.lithologyPlan(),
                snapshot.lithology().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "environment-strata", EnvironmentReleaseCapabilityVerifierV2.STRATA_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.STRATA_PATH, source.strataPlan(),
                snapshot.strata().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "environment-climate", EnvironmentReleaseCapabilityVerifierV2.CLIMATE_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.CLIMATE_PATH, source.climatePlan(),
                snapshot.climate().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "environment-water-condition",
                EnvironmentReleaseCapabilityVerifierV2.WATER_CONDITION_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.WATER_CONDITION_PATH, source.waterConditionPlan(),
                snapshot.water().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "environment-snow", EnvironmentReleaseCapabilityVerifierV2.SNOW_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.SNOW_PATH, source.snowPlan(),
                snapshot.snow().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "environment-material-profile",
                EnvironmentReleaseCapabilityVerifierV2.MATERIAL_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.MATERIAL_PATH, source.materialProfilePlan(),
                snapshot.material().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "environment-minecraft-palette",
                EnvironmentReleaseCapabilityVerifierV2.PALETTE_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.PALETTE_PATH, source.minecraftPalettePlan(),
                snapshot.palette().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "environment-ecology", EnvironmentReleaseCapabilityVerifierV2.ECOLOGY_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.ECOLOGY_PATH, source.ecologyPlan(),
                snapshot.ecology().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "environment-feature-material",
                EnvironmentReleaseCapabilityVerifierV2.FEATURE_MATERIAL_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.FEATURE_MATERIAL_PATH, source.featureMaterialProfilePlan(),
                snapshot.feature().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "environment-validation", EnvironmentReleaseCapabilityVerifierV2.VALIDATION_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.VALIDATION_PATH, source.environmentValidationArtifact(),
                snapshot.validation().canonicalChecksum(), staging, token);
        copyAndAdd(result, paths, "environment-preview-index",
                EnvironmentReleaseCapabilityVerifierV2.PREVIEW_INDEX_TYPE,
                EnvironmentReleaseCapabilityVerifierV2.PREVIEW_INDEX_PATH, source.environmentPreviewIndex(),
                snapshot.previews().canonicalChecksum(), staging, token);
        for (EnvironmentPreviewIndexV2.Layer layer : snapshot.previews().layers()) {
            copyAndAdd(result, paths, "environment-preview." + layer.layerId().name().toLowerCase(Locale.ROOT),
                    EnvironmentReleaseCapabilityVerifierV2.PREVIEW_PNG_TYPE,
                    "environment/previews/" + layer.path(),
                    source.environmentPreviewRoot().resolve(layer.path()), layer.sha256(), staging, token);
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
        if (!paths.add(canonical)) {
            throw new IOException("environment Release source maps multiple artifacts to one path");
        }
        Path target = staging.resolve(canonical).normalize();
        if (!target.startsWith(staging)) {
            throw new IOException("environment Release artifact path escapes staging root");
        }
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
            throw new IOException("environment Release disk estimate overflow", exception);
        }
        if (sourceBytes > limits.maximumDirectoryBytes()
                || expected > limits.maximumDirectoryBytes() + limits.maximumZipBytes()
                || Files.getFileStore(root).getUsableSpace() < expected) {
            throw new IOException("insufficient disk budget for environment Release staging and publish");
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
            throw new IOException("environment Release ZIP exceeds its compressed byte budget");
        }
    }

    private static void forceTree(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                forceFile(file);
            }
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
            throw new IOException("filesystem does not support required environment Release atomic publish", exception);
        }
    }

    private static void requireSafeDirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("environment Release source directory must be a non-symbolic directory");
        }
    }

    private static void requireSafeRegular(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("environment Release source artifact must be a regular non-symbolic file");
        }
    }

    private static FileFingerprint fingerprint(Path path) throws IOException {
        requireSafeRegular(path);
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return new FileFingerprint(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime());
    }

    private static void requireStable(Path path, FileFingerprint before) throws IOException {
        if (!before.equals(fingerprint(path))) {
            throw new IOException("environment Release source changed while it was being staged");
        }
    }

    private static long add(long current, long addition) throws IOException {
        try {
            return Math.addExact(current, addition);
        } catch (ArithmeticException exception) {
            throw new IOException("environment Release source byte total overflow", exception);
        }
    }

    record EnvironmentSnapshot(
            GeologyPlanV2 geology,
            LithologyPlanV2 lithology,
            StrataPlanV2 strata,
            ClimatePlanV2 climate,
            WaterConditionPlanV2 water,
            SnowPlanV2 snow,
            MaterialProfilePlanV2 material,
            MinecraftPalettePlanV2 palette,
            EcologyPlanV2 ecology,
            FeatureMaterialProfilePlanV2 feature,
            EnvironmentValidationArtifactV2 validation,
            EnvironmentPreviewIndexV2 previews,
            long sourceBytes
    ) {
    }

    private record FileFingerprint(Object fileKey, long size, java.nio.file.attribute.FileTime modified) {
    }
}
