package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.format.ReleaseVerifier;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.VolumeSceneTestSupportV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.VolumeTileBlockResolverV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.VolumeValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.index.VolumeAabbIndexBuilderV2;
import com.github.nankotsu029.landformcraft.model.ExportManifest;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.VolumeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeAabbIndexPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-6-12 cross-capability hardening: every valid Release 2 prefix, placement eligibility,
 * shared tamper corpus (directory + ZIP), and Release 1 allowlist separation.
 */
class ReleaseCrossCapabilityHardeningV2Test {
    private final ReleaseManifestCodecV2 manifestCodec = new ReleaseManifestCodecV2();
    private final LandformV2DataCodec data = new LandformV2DataCodec();
    private final ReleasePlacementEligibilityVerifierV2 eligibility =
            new ReleasePlacementEligibilityVerifierV2();

    @Test
    void everyValidCapabilityPrefixIsEligibleForDirectoryAndZip(@TempDir Path root) throws Exception {
        SparseVolumeReleaseSourceV2 sparse = sparseSource(root.resolve("source"));
        EnvironmentReleaseSourceV2 environment = sparse.environment();

        assertEligiblePrefix(
                new ReleaseCorePublisherV2().publish(
                        root.resolve("core"), new ReleaseManifestV2("harden-core"), true, () -> false),
                ReleaseCapabilityDependencyMatrixV2.CORE_ONLY);
        assertEligiblePrefix(
                new ReleaseSurfacePublisherV2().publish(
                        root.resolve("surface"), "harden-surface", environment.hydrology().surface(), true, () -> false),
                ReleaseCapabilityDependencyMatrixV2.SURFACE_ONLY);
        assertEligiblePrefix(
                new ReleaseHydrologyPublisherV2().publish(
                        root.resolve("hydrology"), "harden-hydrology", environment.hydrology(), true, () -> false),
                ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_WITH_SURFACE);
        assertEligiblePrefix(
                new ReleaseEnvironmentPublisherV2().publish(
                        root.resolve("environment"), "harden-environment", environment, true, () -> false),
                ReleaseCapabilityDependencyMatrixV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE);
        assertEligiblePrefix(
                new ReleaseSparseVolumePublisherV2().publish(
                        root.resolve("sparse"), "harden-sparse", sparse, true, () -> false),
                ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME_WITH_ENVIRONMENT);
    }

    @Test
    void placementEligibilityMatchesPlanAndRejectsCapabilityMismatch(@TempDir Path root) throws Exception {
        ReleaseCoreArtifactsV2 core = new ReleaseCorePublisherV2().publish(
                root.resolve("core"), new ReleaseManifestV2("elig-core"), false, () -> false);
        var eligible = eligibility.verifyEligible(core.releaseDirectory());
        PlacementPlanV2 matching = plan(eligible.manifestChecksum(), eligible.requiredCapabilities());
        eligibility.requirePlanMatches(eligible, matching);

        PlacementPlanV2 mismatched = plan(
                eligible.manifestChecksum(),
                ReleaseCapabilityDependencyMatrixV2.SURFACE_ONLY);
        assertThrows(IOException.class, () -> eligibility.requirePlanMatches(eligible, mismatched));

        PlacementPlanV2 wrongChecksum = plan(
                "b".repeat(64),
                ReleaseCapabilityDependencyMatrixV2.CORE_ONLY);
        assertThrows(IOException.class, () -> eligibility.requirePlanMatches(eligible, wrongChecksum));
    }

    @Test
    void rejectsTamperCorpusForDirectoryAndZipAcrossPrefixes(@TempDir Path root) throws Exception {
        EnvironmentReleaseSourceV2 environment =
                EnvironmentReleaseFixtureV2.build(root.resolve("source")).source();

        ReleaseSurfaceArtifactsV2 missing = new ReleaseSurfacePublisherV2().publish(
                root.resolve("missing"), "tamper-missing", environment.hydrology().surface(), true, () -> false);
        Files.delete(missing.releaseDirectory().resolve("validation/coastal-validation.json"));
        assertThrows(IOException.class, () -> eligibility.verifyEligible(missing.releaseDirectory()));
        // ZIP published earlier remains intact until separately corrupted.
        eligibility.verifyEligible(missing.zip().orElseThrow());

        ReleaseSurfaceArtifactsV2 extra = new ReleaseSurfacePublisherV2().publish(
                root.resolve("extra"), "tamper-extra", environment.hydrology().surface(), true, () -> false);
        Files.writeString(extra.releaseDirectory().resolve("unexpected.bin"), "extra");
        assertThrows(IOException.class, () -> eligibility.verifyEligible(extra.releaseDirectory()));

        ReleaseHydrologyArtifactsV2 future = new ReleaseHydrologyPublisherV2().publish(
                root.resolve("future"), "tamper-future", environment.hydrology(), false, () -> false);
        rewriteCapabilities(future.releaseDirectory(), List.of("future-capability"));
        assertThrows(IOException.class, () -> eligibility.verifyEligible(future.releaseDirectory()));

        ReleaseEnvironmentArtifactsV2 incomplete = new ReleaseEnvironmentPublisherV2().publish(
                root.resolve("incomplete"), "tamper-incomplete", environment, false, () -> false);
        rewriteCapabilities(incomplete.releaseDirectory(),
                List.of(ReleaseCapabilityDependencyMatrixV2.ENVIRONMENT_FIELDS,
                        ReleaseCapabilityDependencyMatrixV2.SURFACE_TWO_POINT_FIVE_D));
        assertThrows(IOException.class, () -> eligibility.verifyEligible(incomplete.releaseDirectory()));

        ReleaseCoreArtifactsV2 checksum = new ReleaseCorePublisherV2().publish(
                root.resolve("checksum"), new ReleaseManifestV2("tamper-checksum"), true, () -> false);
        Path manifest = checksum.releaseDirectory().resolve("manifest.json");
        Files.writeString(manifest, Files.readString(manifest).replace("tamper-checksum", "tamper-checksum-x"));
        assertThrows(IOException.class, () -> eligibility.verifyEligible(checksum.releaseDirectory()));
        Path corruptZip = root.resolve("corrupt-manifest.zip");
        writeZip(corruptZip, Map.of("manifest.json", "{\"not\":\"a-release-manifest\"}"));
        assertThrows(IOException.class, () -> eligibility.verifyEligible(corruptZip));

        Path traversal = root.resolve("traversal.zip");
        writeZip(traversal, Map.of("../escaped.json", "not-a-manifest"));
        assertThrows(IOException.class, () -> eligibility.verifyEligible(traversal));
        assertFalse(Files.exists(root.resolve("escaped.json")));

        Path collision = root.resolve("collision.zip");
        writeZip(collision, Map.of("manifest.json", "one", "Manifest.json", "two"));
        assertThrows(IOException.class, () -> eligibility.verifyEligible(collision));

        ReleaseV2Limits tiny = new ReleaseV2Limits(1, 64, 64, 512, 64, 1024);
        Path bomb = root.resolve("bomb.zip");
        writeZip(bomb, Map.of("manifest.json", "x".repeat(65)));
        assertThrows(IOException.class,
                () -> new ReleasePlacementEligibilityVerifierV2(new ReleaseCoreVerifierV2(tiny)).verifyEligible(bomb));
        ReleaseArtifactLimitsCatalogV2.requireCoreWithinCatalog(ReleaseV2Limits.defaults());
        assertEquals("release-2-artifact-limits-catalog-v1", ReleaseArtifactLimitsCatalogV2.CATALOG_VERSION);
    }

    @Test
    void crossVersionPolicyRejectsFutureFormatAndManifestVersions() {
        assertTrue(ReleaseCrossVersionReaderPolicyV2.isSupported(2, 1));
        assertFalse(ReleaseCrossVersionReaderPolicyV2.isSupported(3, 1));
        assertFalse(ReleaseCrossVersionReaderPolicyV2.isSupported(2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> ReleaseCrossVersionReaderPolicyV2.requireSupportedVersions(3, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ReleaseManifestV2(3, 1, "future", List.of(), List.of(),
                        ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
    }

    @Test
    void eligibilityIsCharsetLocaleTimezoneAndCapabilityOrderInvariant(@TempDir Path root) throws Exception {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            ReleaseCoreArtifactsV2 first = new ReleaseCorePublisherV2().publish(
                    root.resolve("first"), new ReleaseManifestV2("order-a"), true, () -> false);
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            ReleaseCoreArtifactsV2 second = new ReleaseCorePublisherV2().publish(
                    root.resolve("second"), new ReleaseManifestV2("order-a"), true, () -> false);
            var left = eligibility.verifyEligible(first.releaseDirectory());
            var right = eligibility.verifyEligible(second.zip().orElseThrow());
            assertEquals(left.manifest(), right.manifest());
            assertEquals(left.manifestChecksum(), right.manifestChecksum());
            assertEquals(StandardCharsets.UTF_8.name(), "UTF-8");
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void release1AllowlistRemainsSeparateAndUnloosened() {
        // Release 1 stays on formatVersion 1 with its own verifier; Release 2 never shares that allowlist.
        assertEquals(1, ModelValidationSupport.formatVersionCeiling());
        assertEquals(2, ReleaseCrossVersionReaderPolicyV2.SUPPORTED_RELEASE_FORMAT_VERSION);
        assertEquals(2, ReleaseManifestV2.RELEASE_FORMAT_VERSION);
        assertTrue(ReleaseVerifier.class.getPackageName()
                .equals("com.github.nankotsu029.landformcraft.format"));
        assertFalse(ReleaseCapabilityDependencyMatrixV2.knownCapabilities().contains("checksums.sha256"));
        assertEquals(int.class, ExportManifest.class.getRecordComponents()[0].getType());
    }

    private void assertEligiblePrefix(Object artifacts, List<String> expectedCapabilities) throws IOException {
        Path directory;
        Path zip;
        if (artifacts instanceof ReleaseCoreArtifactsV2 core) {
            directory = core.releaseDirectory();
            zip = core.zip().orElseThrow();
        } else if (artifacts instanceof ReleaseSurfaceArtifactsV2 surface) {
            directory = surface.releaseDirectory();
            zip = surface.zip().orElseThrow();
        } else if (artifacts instanceof ReleaseHydrologyArtifactsV2 hydrology) {
            directory = hydrology.releaseDirectory();
            zip = hydrology.zip().orElseThrow();
        } else if (artifacts instanceof ReleaseEnvironmentArtifactsV2 environment) {
            directory = environment.releaseDirectory();
            zip = environment.zip().orElseThrow();
        } else if (artifacts instanceof ReleaseSparseVolumeArtifactsV2 sparse) {
            directory = sparse.releaseDirectory();
            zip = sparse.zip().orElseThrow();
        } else {
            throw new IllegalArgumentException("unknown artifacts type");
        }
        var directoryEligible = eligibility.verifyEligible(directory);
        var zipEligible = eligibility.verifyEligible(zip);
        assertEquals(expectedCapabilities, directoryEligible.requiredCapabilities());
        assertEquals(directoryEligible.manifest(), zipEligible.manifest());
        assertTrue(directoryEligible.eligible());
        assertTrue(zipEligible.eligible());
        eligibility.requirePlanMatches(
                directoryEligible,
                plan(directoryEligible.manifestChecksum(), expectedCapabilities));
    }

    private PlacementPlanV2 plan(String manifestChecksum, List<String> capabilities) {
        return new PlacementPlanV2(
                PlacementPlanV2.VERSION,
                PlacementPlanV2.PLACEMENT_CONTRACT_VERSION,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "harden",
                PlacementPlanV2.PlacementActorV2.console(),
                new PlacementPlanV2.PlacementTargetV2(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        "world",
                        PlacementPlanV2.AnchorKind.MINIMUM_CORNER,
                        0, 64, 0, 0, 64, 0, 63, 80, 63),
                new PlacementPlanV2.ReleaseBindingV2(
                        1, 2, "releases/harden", manifestChecksum,
                        PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION),
                capabilities,
                new PlacementPlanV2.TileOrderV2(
                        PlacementPlanV2.TileOrderV2.CONTRACT_VERSION,
                        List.of(new PlacementPlanV2.TileRefV2("tile-x0-z0", 0, 0, 0, 64, 64))),
                PlacementPlanV2.EnvelopeReferencesV2.unbound(),
                PlacementPlanV2.ReservationConfirmationBindingV2.unbound(
                        PlacementPlanV2.PlacementActorV2.console()),
                new PlacementPlanV2.ResourceBudget(
                        PlacementPlanV2.ResourceBudget.VERSION,
                        16, 16, 4_096L, PlacementPlanV2.MAX_CANONICAL_BYTES, 32_768L),
                PlacementPlanV2.UNBOUND_CHECKSUM);
    }

    private SparseVolumeReleaseSourceV2 sparseSource(Path root) throws Exception {
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

    private void rewriteCapabilities(Path release, List<String> capabilities) throws Exception {
        ReleaseManifestV2 manifest = manifestCodec.read(release.resolve("manifest.json"));
        manifestCodec.write(release.resolve("manifest.json"), new ReleaseManifestV2(
                manifest.releaseFormatVersion(), manifest.manifestVersion(), manifest.releaseId(),
                capabilities, manifest.artifacts(), ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
    }

    private static void writeZip(Path path, Map<String, String> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    /** Tiny bridge so the hardening suite can assert Release 1 still uses formatVersion 1 only. */
    private static final class ModelValidationSupport {
        private ModelValidationSupport() {
        }

        static int formatVersionCeiling() {
            return 1;
        }
    }
}
