package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.VolumeSceneTestSupportV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.VolumeTileBlockResolverV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.VolumeValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.index.VolumeAabbIndexBuilderV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.VolumeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeAabbIndexPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
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

class ReleaseSparseVolumePublisherVerifierV2Test {
    private final ReleaseManifestCodecV2 manifestCodec = new ReleaseManifestCodecV2();
    private final LandformV2DataCodec data = new LandformV2DataCodec();

    @Test
    void publishesCompleteSparseVolumeCapabilityAndStrictlyVerifiesDirectoryZipAndOrder(@TempDir Path root)
            throws Exception {
        SparseVolumeReleaseSourceV2 source = source(root.resolve("source"));
        ReleaseSparseVolumeArtifactsV2 first = new ReleaseSparseVolumePublisherV2().publish(
                root.resolve("first"), "sparse-volume-fixture", source, true, () -> false);
        ReleaseSparseVolumeArtifactsV2 second = new ReleaseSparseVolumePublisherV2().publish(
                root.resolve("second"), "sparse-volume-fixture", source, true, () -> false);

        ReleaseCoreVerificationV2 directory = new ReleaseSparseVolumeVerifierV2().verify(first.releaseDirectory());
        ReleaseCoreVerificationV2 zip = new ReleaseSparseVolumeVerifierV2().verify(first.zip().orElseThrow());
        assertEquals(ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT,
                directory.manifest().requiredCapabilities());
        assertEquals(directory.manifest(), zip.manifest());
        assertEquals(directory.manifest().artifacts().stream().map(ReleaseArtifactDescriptorV2::path).sorted().toList(),
                directory.manifest().artifacts().stream().map(ReleaseArtifactDescriptorV2::path).toList());
        assertArrayEquals(Files.readAllBytes(first.releaseDirectory().resolve("manifest.json")),
                Files.readAllBytes(second.releaseDirectory().resolve("manifest.json")));
        assertArrayEquals(Files.readAllBytes(first.zip().orElseThrow()), Files.readAllBytes(second.zip().orElseThrow()));
        for (String type : List.of(
                SparseVolumeReleaseCapabilityVerifierV2.SDF_TYPE, SparseVolumeReleaseCapabilityVerifierV2.CSG_TYPE,
                SparseVolumeReleaseCapabilityVerifierV2.AABB_TYPE, SparseVolumeReleaseCapabilityVerifierV2.VALIDATION_TYPE,
                SparseVolumeReleaseCapabilityVerifierV2.TILE_METADATA_TYPE,
                SparseVolumeReleaseCapabilityVerifierV2.SCHEMATIC_TYPE)) {
            assertTrue(directory.manifest().artifacts().stream().anyMatch(d -> d.artifactType().equals(type)),
                    "missing volume artifact type: " + type);
        }
        // The surface tile set is not polluted by the distinct volume tile type.
        assertEquals(1, directory.manifest().artifacts().stream()
                .filter(d -> d.artifactType().equals("offline-tile-artifact-v2")).count());
    }

    @Test
    void earlierCapabilitySetsStillStrictlyVerifyAfterSparseVolume(@TempDir Path root) throws Exception {
        EnvironmentReleaseSourceV2 environment = EnvironmentReleaseFixtureV2.build(root.resolve("source")).source();
        ReleaseEnvironmentArtifactsV2 environmentRelease = new ReleaseEnvironmentPublisherV2().publish(
                root.resolve("environment"), "environment-regression", environment, true, () -> false);
        assertEquals(ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE,
                new ReleaseEnvironmentVerifierV2().verify(environmentRelease.releaseDirectory())
                        .manifest().requiredCapabilities());

        ReleaseHydrologyArtifactsV2 hydrology = new ReleaseHydrologyPublisherV2().publish(
                root.resolve("hydrology"), "hydrology-regression", environment.hydrology(), false, () -> false);
        assertEquals(ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE,
                new ReleaseHydrologyVerifierV2().verify(hydrology.releaseDirectory()).manifest().requiredCapabilities());

        ReleaseSurfaceArtifactsV2 surface = new ReleaseSurfacePublisherV2().publish(
                root.resolve("surface"), "surface-regression", environment.hydrology().surface(), false, () -> false);
        assertEquals(List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                new ReleaseSurfaceVerifierV2().verify(surface.releaseDirectory()).manifest().requiredCapabilities());
    }

    @Test
    void rejectsMissingExtraVersionBindingTileTamperAndDependencyDowngrade(@TempDir Path root) throws Exception {
        SparseVolumeReleaseSourceV2 source = source(root.resolve("source"));

        ReleaseSparseVolumeArtifactsV2 missing = new ReleaseSparseVolumePublisherV2().publish(
                root.resolve("missing"), "sv-missing", source, false, () -> false);
        Files.delete(missing.releaseDirectory().resolve("volume/csg-plan.json"));
        assertThrows(IOException.class, () -> new ReleaseSparseVolumeVerifierV2().verify(missing.releaseDirectory()));

        ReleaseSparseVolumeArtifactsV2 extra = new ReleaseSparseVolumePublisherV2().publish(
                root.resolve("extra"), "sv-extra", source, false, () -> false);
        Files.writeString(extra.releaseDirectory().resolve("volume/unexpected.bin"), "extra");
        assertThrows(IOException.class, () -> new ReleaseSparseVolumeVerifierV2().verify(extra.releaseDirectory()));

        ReleaseSparseVolumeArtifactsV2 future = new ReleaseSparseVolumePublisherV2().publish(
                root.resolve("future"), "sv-future", source, false, () -> false);
        rewriteCapabilities(future.releaseDirectory(), List.of("future-capability"));
        assertThrows(IOException.class, () -> new ReleaseSparseVolumeVerifierV2().verify(future.releaseDirectory()));

        ReleaseSparseVolumeArtifactsV2 dependency = new ReleaseSparseVolumePublisherV2().publish(
                root.resolve("dependency"), "sv-dependency", source, false, () -> false);
        rewriteCapabilities(dependency.releaseDirectory(),
                ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE);
        assertThrows(IOException.class,
                () -> new ReleaseSparseVolumeVerifierV2().verify(dependency.releaseDirectory()));

        ReleaseSparseVolumeArtifactsV2 version = new ReleaseSparseVolumePublisherV2().publish(
                root.resolve("version"), "sv-version", source, false, () -> false);
        bumpArtifactVersion(version.releaseDirectory(), SparseVolumeReleaseCapabilityVerifierV2.AABB_TYPE);
        assertThrows(IOException.class, () -> new ReleaseSparseVolumeVerifierV2().verify(version.releaseDirectory()));

        ReleaseSparseVolumeArtifactsV2 csg = new ReleaseSparseVolumePublisherV2().publish(
                root.resolve("csg"), "sv-csg", source, false, () -> false);
        Path csgPath = csg.releaseDirectory().resolve("volume/csg-plan.json");
        corruptCanonicalChecksum(csgPath);
        rewriteDescriptor(csg.releaseDirectory(), "volume/csg-plan.json", Sha256.file(csgPath));
        assertThrows(IOException.class, () -> new ReleaseSparseVolumeVerifierV2().verify(csg.releaseDirectory()));

        ReleaseSparseVolumeArtifactsV2 tile = new ReleaseSparseVolumePublisherV2().publish(
                root.resolve("tile"), "sv-tile", source, false, () -> false);
        Path schematic = tile.releaseDirectory().resolve("volume/tiles/volume-tile-00-00.schem");
        Files.write(schematic, new byte[]{0}, java.nio.file.StandardOpenOption.APPEND);
        rewriteDescriptor(tile.releaseDirectory(), "volume/tiles/volume-tile-00-00.schem", Sha256.file(schematic));
        assertThrows(IOException.class, () -> new ReleaseSparseVolumeVerifierV2().verify(tile.releaseDirectory()));

        Path cancelRoot = root.resolve("cancel");
        assertThrows(CancellationException.class, () -> new ReleaseSparseVolumePublisherV2().publish(
                cancelRoot, "sv-cancel", source, true, () -> true));
        assertFalse(Files.exists(cancelRoot.resolve("sv-cancel")));
        if (Files.exists(cancelRoot)) {
            try (var files = Files.list(cancelRoot)) {
                assertTrue(files.noneMatch(path ->
                        path.getFileName().toString().startsWith(".release-v2-sparse-volume-")));
            }
        }
    }

    SparseVolumeReleaseSourceV2 source(Path root) throws Exception {
        EnvironmentReleaseFixtureV2.Fixture fixture = EnvironmentReleaseFixtureV2.build(root);
        WorldBlueprintV2 blueprint = fixture.blueprint();
        Path volume = Files.createDirectories(root.resolve("volume"));

        VolumeSdfPrimitivePlanV2 sdf = VolumeSceneTestSupportV2.sdfPlan();
        Path sdfPath = volume.resolve("sdf-primitive-plan.json");
        data.writeVolumeSdfPrimitivePlan(sdfPath, sdf);

        VolumeCsgPlanV2 csg = VolumeSceneTestSupportV2.csgPlan();
        Path csgPath = volume.resolve("csg-plan.json");
        data.writeVolumeCsgPlan(csgPath, csg);

        VolumeAabbIndexPlanV2 aabb = data.sealVolumeAabbIndexPlan(
                VolumeAabbIndexBuilderV2.buildDraft(csg, sdf, 0, 0));
        Path aabbPath = volume.resolve("aabb-index-plan.json");
        data.writeVolumeAabbIndexPlan(aabbPath, aabb);

        Path validationPath = volume.resolve("validation.json");
        new VolumeValidationArtifactCodecV2().write(validationPath, new VolumeValidationArtifactV2(
                csg.canonicalChecksum(),
                new VolumeValidationArtifactV2.VolumeValidationReport(List.of(), List.of())));

        OfflineTilePlanV2 tilePlan = VolumeSceneTestSupportV2.plan("volume-tile-00-00", 0, 0, 0, 0, 16, 16);
        Path tileRoot = Files.createDirectories(volume.resolve("tiles"));
        Path schematic = tileRoot.resolve(tilePlan.defaultSchematicFileName());
        OfflineTileArtifactV2 tile = new OfflineTileSchematicWriterV2().write(
                schematic, tilePlan, blueprint.canonicalChecksum(),
                new VolumeTileBlockResolverV2(VolumeSceneTestSupportV2.scene(0, 0, 16, 16)), () -> false);
        Path metadata = tileRoot.resolve("volume-tile-00-00.json");
        new OfflineTileArtifactCodecV2().write(metadata, tile);

        return new SparseVolumeReleaseSourceV2(fixture.source(), sdfPath, csgPath, aabbPath, validationPath,
                List.of(new SparseVolumeReleaseSourceV2.TileSource(tile.tileId(), metadata, schematic)));
    }

    private void corruptCanonicalChecksum(Path path) throws IOException {
        String json = Files.readString(path);
        String corrupted = json.replaceFirst(
                "\"canonicalChecksum\"\\s*:\\s*\"[0-9a-f]{64}\"",
                "\"canonicalChecksum\":\"" + "e".repeat(64) + "\"");
        assertFalse(corrupted.equals(json));
        Files.writeString(path, corrupted);
    }

    private void rewriteCapabilities(Path release, List<String> capabilities) throws Exception {
        ReleaseManifestV2 manifest = manifestCodec.read(release.resolve("manifest.json"));
        manifestCodec.write(release.resolve("manifest.json"), new ReleaseManifestV2(
                manifest.releaseFormatVersion(), manifest.manifestVersion(), manifest.releaseId(),
                capabilities, manifest.artifacts(), ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
    }

    private void bumpArtifactVersion(Path release, String type) throws Exception {
        ReleaseManifestV2 manifest = manifestCodec.read(release.resolve("manifest.json"));
        List<ReleaseArtifactDescriptorV2> descriptors = manifest.artifacts().stream().map(descriptor ->
                descriptor.artifactType().equals(type)
                        ? new ReleaseArtifactDescriptorV2(descriptor.artifactId(), descriptor.artifactType(), 2,
                        descriptor.path(), descriptor.byteLength(), descriptor.artifactChecksum(),
                        descriptor.semanticChecksum())
                        : descriptor).toList();
        manifestCodec.write(release.resolve("manifest.json"), new ReleaseManifestV2(
                manifest.releaseFormatVersion(), manifest.manifestVersion(), manifest.releaseId(),
                manifest.requiredCapabilities(), descriptors, ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
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
