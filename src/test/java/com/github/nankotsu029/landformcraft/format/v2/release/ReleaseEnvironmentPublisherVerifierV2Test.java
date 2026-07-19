package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseEnvironmentPublisherVerifierV2Test {
    private final ReleaseManifestCodecV2 manifestCodec = new ReleaseManifestCodecV2();

    @Test
    void publishesCompleteEnvironmentCapabilityAndStrictlyVerifiesDirectoryZipAndOrder(@TempDir Path root)
            throws Exception {
        EnvironmentReleaseSourceV2 source = EnvironmentReleaseFixtureV2.build(root.resolve("source")).source();
        ReleaseEnvironmentArtifactsV2 first = new ReleaseEnvironmentPublisherV2().publish(
                root.resolve("first"), "environment-fixture", source, true, () -> false);
        ReleaseEnvironmentArtifactsV2 second = new ReleaseEnvironmentPublisherV2().publish(
                root.resolve("second"), "environment-fixture", source, true, () -> false);

        ReleaseCoreVerificationV2 directory = new ReleaseEnvironmentVerifierV2().verify(first.releaseDirectory());
        ReleaseCoreVerificationV2 zip = new ReleaseEnvironmentVerifierV2().verify(first.zip().orElseThrow());
        assertEquals(ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE,
                directory.manifest().requiredCapabilities());
        assertEquals(directory.manifest(), zip.manifest());
        assertTrue(directory.manifest().artifacts().size() >= 55);
        assertEquals(directory.manifest().artifacts().stream().map(ReleaseArtifactDescriptorV2::path).sorted().toList(),
                directory.manifest().artifacts().stream().map(ReleaseArtifactDescriptorV2::path).toList());
        assertArrayEquals(Files.readAllBytes(first.releaseDirectory().resolve("manifest.json")),
                Files.readAllBytes(second.releaseDirectory().resolve("manifest.json")));
        assertArrayEquals(Files.readAllBytes(first.zip().orElseThrow()), Files.readAllBytes(second.zip().orElseThrow()));
        assertTrue(directory.manifest().artifacts().stream().anyMatch(descriptor ->
                descriptor.artifactType().equals(EnvironmentReleaseCapabilityVerifierV2.PALETTE_TYPE)));
    }

    @Test
    void hydrologyAndSurfaceReleasesStillStrictlyVerifyWithoutEnvironment(@TempDir Path root) throws Exception {
        EnvironmentReleaseSourceV2 source = EnvironmentReleaseFixtureV2.build(root.resolve("source")).source();
        ReleaseHydrologyArtifactsV2 hydrology = new ReleaseHydrologyPublisherV2().publish(
                root.resolve("hydrology"), "hydrology-only", source.hydrology(), true, () -> false);
        ReleaseCoreVerificationV2 hydro = new ReleaseHydrologyVerifierV2().verify(hydrology.releaseDirectory());
        assertEquals(ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE, hydro.manifest().requiredCapabilities());
        assertTrue(hydro.manifest().artifacts().stream().noneMatch(descriptor ->
                descriptor.path().startsWith("environment/")));

        ReleaseSurfaceArtifactsV2 surface = new ReleaseSurfacePublisherV2().publish(
                root.resolve("surface"), "surface-only", source.hydrology().surface(), true, () -> false);
        ReleaseCoreVerificationV2 surfaceOnly = new ReleaseSurfaceVerifierV2().verify(surface.releaseDirectory());
        assertEquals(List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                surfaceOnly.manifest().requiredCapabilities());
    }

    @Test
    void rejectsMissingExtraVersionPaletteChecksumAndCapabilityDependencyTampering(@TempDir Path root)
            throws Exception {
        EnvironmentReleaseSourceV2 source = EnvironmentReleaseFixtureV2.build(root.resolve("source")).source();

        ReleaseEnvironmentArtifactsV2 missing = new ReleaseEnvironmentPublisherV2().publish(
                root.resolve("missing"), "environment-missing", source, false, () -> false);
        Files.delete(missing.releaseDirectory().resolve("environment/minecraft-palette-plan.json"));
        assertThrows(IOException.class, () -> new ReleaseEnvironmentVerifierV2().verify(missing.releaseDirectory()));

        ReleaseEnvironmentArtifactsV2 extra = new ReleaseEnvironmentPublisherV2().publish(
                root.resolve("extra"), "environment-extra", source, false, () -> false);
        Files.writeString(extra.releaseDirectory().resolve("environment/unexpected.bin"), "extra");
        assertThrows(IOException.class, () -> new ReleaseEnvironmentVerifierV2().verify(extra.releaseDirectory()));

        ReleaseEnvironmentArtifactsV2 future = new ReleaseEnvironmentPublisherV2().publish(
                root.resolve("future"), "environment-future", source, false, () -> false);
        ReleaseManifestV2 existing = manifestCodec.read(future.releaseDirectory().resolve("manifest.json"));
        manifestCodec.write(future.releaseDirectory().resolve("manifest.json"), new ReleaseManifestV2(
                existing.releaseFormatVersion(), existing.manifestVersion(), existing.releaseId(),
                List.of("future-capability"), existing.artifacts(), ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
        assertThrows(IOException.class, () -> new ReleaseEnvironmentVerifierV2().verify(future.releaseDirectory()));

        ReleaseEnvironmentArtifactsV2 dependency = new ReleaseEnvironmentPublisherV2().publish(
                root.resolve("dependency"), "environment-dependency", source, false, () -> false);
        ReleaseManifestV2 dependencyManifest = manifestCodec.read(
                dependency.releaseDirectory().resolve("manifest.json"));
        manifestCodec.write(dependency.releaseDirectory().resolve("manifest.json"), new ReleaseManifestV2(
                dependencyManifest.releaseFormatVersion(), dependencyManifest.manifestVersion(),
                dependencyManifest.releaseId(), List.of(ReleaseArtifactCatalogV2.ENVIRONMENT_FIELDS),
                dependencyManifest.artifacts(), ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
        assertThrows(IOException.class, () -> new ReleaseEnvironmentVerifierV2().verify(dependency.releaseDirectory()));

        ReleaseEnvironmentArtifactsV2 version = new ReleaseEnvironmentPublisherV2().publish(
                root.resolve("version"), "environment-version", source, false, () -> false);
        ReleaseManifestV2 versionManifest = manifestCodec.read(version.releaseDirectory().resolve("manifest.json"));
        List<ReleaseArtifactDescriptorV2> futureDescriptors = versionManifest.artifacts().stream().map(descriptor ->
                descriptor.artifactType().equals(EnvironmentReleaseCapabilityVerifierV2.PALETTE_TYPE)
                        ? new ReleaseArtifactDescriptorV2(descriptor.artifactId(), descriptor.artifactType(), 2,
                        descriptor.path(), descriptor.byteLength(), descriptor.artifactChecksum(),
                        descriptor.semanticChecksum())
                        : descriptor).toList();
        manifestCodec.write(version.releaseDirectory().resolve("manifest.json"), new ReleaseManifestV2(
                versionManifest.releaseFormatVersion(), versionManifest.manifestVersion(), versionManifest.releaseId(),
                versionManifest.requiredCapabilities(), futureDescriptors, ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
        assertThrows(IOException.class, () -> new ReleaseEnvironmentVerifierV2().verify(version.releaseDirectory()));

        ReleaseEnvironmentArtifactsV2 palette = new ReleaseEnvironmentPublisherV2().publish(
                root.resolve("palette"), "environment-palette", source, false, () -> false);
        Path palettePath = palette.releaseDirectory().resolve("environment/minecraft-palette-plan.json");
        String paletteJson = Files.readString(palettePath);
        String corrupted = paletteJson.replaceFirst(
                "\"canonicalChecksum\"\\s*:\\s*\"[0-9a-f]{64}\"",
                "\"canonicalChecksum\":\"" + "f".repeat(64) + "\"");
        assertFalse(corrupted.equals(paletteJson));
        Files.writeString(palettePath, corrupted);
        rewriteDescriptor(palette.releaseDirectory(), "environment/minecraft-palette-plan.json",
                Sha256.file(palettePath));
        assertThrows(IOException.class, () -> new ReleaseEnvironmentVerifierV2().verify(palette.releaseDirectory()));

        Path cancelRoot = root.resolve("cancel");
        assertThrows(CancellationException.class, () -> new ReleaseEnvironmentPublisherV2().publish(
                cancelRoot, "environment-cancel", source, true, () -> true));
        assertFalse(Files.exists(cancelRoot.resolve("environment-cancel")));
        if (Files.exists(cancelRoot)) {
            try (var files = Files.list(cancelRoot)) {
                assertTrue(files.noneMatch(path ->
                        path.getFileName().toString().startsWith(".release-v2-environment-")));
            }
        }
    }

    private void rewriteDescriptor(Path release, String path, String artifactChecksum) throws Exception {
        ReleaseManifestV2 manifest = manifestCodec.read(release.resolve("manifest.json"));
        List<ReleaseArtifactDescriptorV2> descriptors = manifest.artifacts().stream().map(descriptor -> {
            if (!descriptor.path().equals(path)) {
                return descriptor;
            }
            try {
                byte[] bytes = Files.readAllBytes(release.resolve(path));
                return new ReleaseArtifactDescriptorV2(descriptor.artifactId(), descriptor.artifactType(),
                        descriptor.artifactVersion(), descriptor.path(), bytes.length, artifactChecksum,
                        descriptor.semanticChecksum());
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }).toList();
        manifestCodec.write(release.resolve("manifest.json"), new ReleaseManifestV2(
                manifest.releaseFormatVersion(), manifest.manifestVersion(), manifest.releaseId(),
                manifest.requiredCapabilities(), descriptors, ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
    }
}
