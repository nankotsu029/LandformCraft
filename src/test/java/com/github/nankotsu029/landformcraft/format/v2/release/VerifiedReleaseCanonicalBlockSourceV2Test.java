package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.v2.placement.PlacementPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementDesiredBlockV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.VerifiedReleaseCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifiedReleaseCanonicalBlockSourceV2Test {
    @Test
    void directoryAndZipExposeTheSameRestartableSurfaceStream(@TempDir Path root) throws Exception {
        EnvironmentReleaseFixtureV2.Fixture fixture = EnvironmentReleaseFixtureV2.build(root.resolve("source"));
        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                root.resolve("release"), "source-surface", fixture.source().hydrology().surface(), true,
                () -> false);
        ReleaseManifestV2 manifest = new ReleaseCoreVerifierV2().verify(release.releaseDirectory()).manifest();
        PlacementPlanV2 plan = plan(manifest, 4, 4, 101);
        WorldAabbV2 region = new WorldAabbV2(10, 64, 20, 13, 164, 23);

        List<PlacementDesiredBlockV2> directory;
        PlacementCanonicalBlockSourceV2.SourceBindingV2 directoryBinding;
        try (VerifiedReleaseCanonicalBlockSourceV2 source =
                     VerifiedReleaseCanonicalBlockSourceV2.open(release.releaseDirectory(), () -> false)) {
            directoryBinding = source.binding();
            directory = read(source, plan, region);
            assertEquals(directory, read(source, plan, region));
        }
        assertEquals(4 * 4 * 101, directory.size());
        assertEquals(10, directory.getFirst().x());
        assertEquals(64, directory.getFirst().y());
        assertEquals(20, directory.getFirst().z());
        assertEquals(13, directory.getLast().x());
        assertEquals(164, directory.getLast().y());
        assertEquals(23, directory.getLast().z());

        Path zip = release.zip().orElseThrow();
        VerifiedReleaseCanonicalBlockSourceV2 closedSource;
        try (VerifiedReleaseCanonicalBlockSourceV2 source = VerifiedReleaseCanonicalBlockSourceV2.open(
                zip, () -> false)) {
            closedSource = source;
            assertEquals(directoryBinding, source.binding());
            assertEquals(directory, read(source, plan, region));
            assertTrue(hasVerifyStaging(zip.getParent()));
        }
        assertFalse(hasVerifyStaging(zip.getParent()));
        assertThrows(IOException.class, () -> read(closedSource, plan, region));
    }

    @Test
    void sparseVolumeUsesFinalVolumeTileAndVolumeOverlayOrdinals(@TempDir Path root) throws Exception {
        SparseVolumeReleaseSourceV2 input = new ReleaseSparseVolumePublisherVerifierV2Test()
                .source(root.resolve("source"));
        ReleaseSparseVolumeArtifactsV2 release = new ReleaseSparseVolumePublisherV2().publish(
                root.resolve("release"), "source-volume", input, true, () -> false);
        ReleaseManifestV2 manifest = new ReleaseCoreVerifierV2().verify(release.releaseDirectory()).manifest();
        PlacementPlanV2 plan = plan(manifest, 16, 16, 16);
        WorldAabbV2 region = new WorldAabbV2(10, 64, 20, 25, 79, 35);

        try (VerifiedReleaseCanonicalBlockSourceV2 source =
                     VerifiedReleaseCanonicalBlockSourceV2.open(release.releaseDirectory(), () -> false)) {
            List<PlacementDesiredBlockV2> blocks = read(source, plan, region);
            assertEquals(16 * 16 * 16, blocks.size());
            Set<Integer> ordinals = blocks.stream().map(PlacementDesiredBlockV2::overlayOrdinal)
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(ordinals.contains(3));
            assertTrue(ordinals.contains(4));
            assertTrue(ordinals.contains(5));
        }
    }

    @Test
    void rejectsContainerAndPostVerifyTileTamperingAndCleansZipStaging(@TempDir Path root) throws Exception {
        EnvironmentReleaseFixtureV2.Fixture fixture = EnvironmentReleaseFixtureV2.build(root.resolve("source"));
        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                root.resolve("release"), "source-tamper", fixture.source().hydrology().surface(), true,
                () -> false);
        ReleaseManifestV2 manifest = new ReleaseCoreVerifierV2().verify(release.releaseDirectory()).manifest();
        PlacementPlanV2 plan = plan(manifest, 4, 4, 101);
        WorldAabbV2 region = new WorldAabbV2(10, 64, 20, 13, 164, 23);

        try (VerifiedReleaseCanonicalBlockSourceV2 source =
                     VerifiedReleaseCanonicalBlockSourceV2.open(release.releaseDirectory(), () -> false)) {
            Files.write(release.releaseDirectory().resolve("tiles/tile-00-00.schem"), new byte[]{0},
                    java.nio.file.StandardOpenOption.APPEND);
            assertThrows(IOException.class, () -> read(source, plan, region));
        }

        Path extraRoot = root.resolve("extra-release");
        ReleaseSurfaceArtifactsV2 extra = new ReleaseSurfacePublisherV2().publish(
                extraRoot, "source-extra", fixture.source().hydrology().surface(), false, () -> false);
        Files.writeString(extra.releaseDirectory().resolve("unexpected.txt"), "extra");
        assertThrows(IOException.class,
                () -> VerifiedReleaseCanonicalBlockSourceV2.open(extra.releaseDirectory(), () -> false));

        Path staging;
        try (VerifiedReleaseViewV2 view = new ReleaseCoreVerifierV2().openVerified(
                release.zip().orElseThrow(), () -> false)) {
            staging = view.root();
            assertTrue(Files.isDirectory(staging));
        }
        assertFalse(Files.exists(staging));
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> VerifiedReleaseCanonicalBlockSourceV2.open(release.zip().orElseThrow(), () -> true));
    }

    private static PlacementPlanV2 plan(ReleaseManifestV2 manifest, int width, int length, int height) {
        return new PlacementPlanCompilerV2().compile(new PlacementPlanCompilerV2.PlacementCompileRequestV2(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                manifest.releaseId(),
                PlacementPlanV2.PlacementActorV2.console(),
                new PlacementPlanV2.PlacementTargetV2(
                        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                        "world",
                        PlacementPlanV2.AnchorKind.MINIMUM_CORNER,
                        10, 64, 20,
                        10, 64, 20,
                        10 + width - 1, 64 + height - 1, 20 + length - 1),
                new PlacementPlanV2.ReleaseBindingV2(
                        PlacementPlanV2.ReleaseBindingV2.VERSION,
                        2,
                        "verified-release",
                        manifest.canonicalChecksum(),
                        PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION),
                manifest.requiredCapabilities(),
                TilePlanV2.of(width, length, ScaleProfileV2.defaults(ScaleClassV2.SMALL)))).plan();
    }

    private static List<PlacementDesiredBlockV2> read(
            VerifiedReleaseCanonicalBlockSourceV2 source,
            PlacementPlanV2 plan,
            WorldAabbV2 region
    ) throws IOException {
        ArrayList<PlacementDesiredBlockV2> blocks = new ArrayList<>();
        try (PlacementCanonicalBlockSourceV2.BlockCursorV2 cursor = source.openTile(
                plan, plan.tileOrder().tiles().getFirst(), region)) {
            PlacementDesiredBlockV2 block;
            while ((block = cursor.next()) != null) blocks.add(block);
        }
        return List.copyOf(blocks);
    }

    private static boolean hasVerifyStaging(Path root) throws IOException {
        try (var paths = Files.list(root)) {
            return paths.anyMatch(path -> path.getFileName().toString().startsWith(".release-v2-verify-"));
        }
    }
}
