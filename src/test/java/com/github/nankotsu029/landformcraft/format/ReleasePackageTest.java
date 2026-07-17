package com.github.nankotsu029.landformcraft.format;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.ReleaseApplicationService;
import com.github.nankotsu029.landformcraft.model.ExportManifest;
import com.github.nankotsu029.landformcraft.model.ManifestTile;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleasePackageTest {
    @Test
    void publishesReadsWithWorldEditAndVerifiesAfterTransport(@TempDir Path directory) throws Exception {
        Fixture fixture = exportFixture(directory);
        ReleaseVerifier verifier = new ReleaseVerifier();

        ReleaseVerification directoryVerification = verifier.verify(fixture.releaseDirectory());
        assertEquals(4, directoryVerification.verifiedTiles());
        Path transported = Files.createDirectory(directory.resolve("transported")).resolve("release-copy.zip");
        Files.copy(fixture.zip(), transported);
        ReleaseVerification zipVerification = verifier.verify(transported);
        assertEquals(directoryVerification.manifest(), zipVerification.manifest());

        ExportManifest manifest = directoryVerification.manifest();
        var assets = new LandformDataCodec().readRequiredAssets(
                fixture.releaseDirectory().resolve("assets/required-assets.json"));
        var structures = new LandformDataCodec().readStructurePlacements(
                fixture.releaseDirectory().resolve("structures.json"));
        assertEquals(1, assets.assets().size());
        assertEquals(2, structures.structures().size());
        assertEquals("fence-v1", assets.assets().getFirst().assetId());
        assertTrue(Files.isRegularFile(fixture.releaseDirectory().resolve(assets.assets().getFirst().file())));
        registerWorldEditTestPlatform();
        BlockArrayClipboard assembled = new BlockArrayClipboard(new CuboidRegion(
                BlockVector3.ZERO,
                BlockVector3.at(manifest.width() - 1, manifest.maxY() - manifest.minY(), manifest.length() - 1)
        ));
        for (ManifestTile tile : manifest.tiles()) {
            Path schematic = fixture.releaseDirectory().resolve(tile.file());
            ClipboardFormat format = ClipboardFormats.findByFile(schematic.toFile());
            assertNotNull(format);
            try (var input = Files.newInputStream(schematic); var reader = format.getReader(input)) {
                Clipboard clipboard = reader.read();
                assertEquals(tile.width(), clipboard.getDimensions().x());
                assertEquals(tile.maxY() - tile.minY() + 1, clipboard.getDimensions().y());
                assertEquals(tile.length(), clipboard.getDimensions().z());
                assertEquals(0, clipboard.getMinimumPoint().x());
                assertEquals(0, clipboard.getMinimumPoint().y());
                assertEquals(0, clipboard.getMinimumPoint().z());
                assertEquals(clipboard.getMinimumPoint(), clipboard.getOrigin());
                assertEquals("minecraft:bedrock", clipboard.getBlock(clipboard.getMinimumPoint()).getBlockType().id());
                Operations.complete(new ForwardExtentCopy(
                        clipboard, clipboard.getRegion(), assembled,
                        BlockVector3.at(tile.originX(), 0, tile.originZ())
                ));
            }
        }
        assertTrue(structures.structures().stream().allMatch(structure -> {
            int localY = assembled.getRegion().getMaximumPoint().y();
            for (int y = 0; y <= localY; y++) {
                for (int z = structure.anchorZ(); z < structure.anchorZ() + structure.sizeZ(); z++) {
                    for (int x = structure.anchorX(); x < structure.anchorX() + structure.sizeX(); x++) {
                        if (assembled.getBlock(BlockVector3.at(x, y, z)).getBlockType().id()
                                .equals("minecraft:oak_fence")) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }));
        for (ManifestTile tile : manifest.tiles()) {
            assertEquals("minecraft:bedrock", assembled
                    .getBlock(BlockVector3.at(tile.originX(), 0, tile.originZ())).getBlockType().id());
        }
    }

    private static void registerWorldEditTestPlatform() throws IOException {
        com.github.nankotsu029.landformcraft.worldedit.WorldEditTestPlatformSupport.ensureRegistered();
    }

    @Test
    void rejectsModifiedAndMissingArtifacts(@TempDir Path directory) throws IOException {
        Fixture fixture = exportFixture(directory);
        ReleaseVerifier verifier = new ReleaseVerifier();
        Path overview = fixture.releaseDirectory().resolve("previews/overview.png");
        Files.write(overview, new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> verifier.verify(fixture.releaseDirectory()));

        Fixture missingFixture = exportFixture(directory.resolve("second"));
        Files.delete(missingFixture.releaseDirectory().resolve("schematics/tile-00-00.schem"));
        assertThrows(IOException.class, () -> verifier.verify(missingFixture.releaseDirectory()));
    }

    @Test
    void rejectsModifiedAndMissingStructureAssets(@TempDir Path directory) throws IOException {
        Fixture fixture = exportFixture(directory);
        var asset = new LandformDataCodec().readRequiredAssets(
                fixture.releaseDirectory().resolve("assets/required-assets.json")).assets().getFirst();
        Path schematic = fixture.releaseDirectory().resolve(asset.file());
        byte[] original = Files.readAllBytes(schematic);
        Files.write(schematic, new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> new ReleaseVerifier().verify(fixture.releaseDirectory()));

        Files.write(schematic, original);
        new ReleaseVerifier().verify(fixture.releaseDirectory());
        Files.delete(schematic);
        assertThrows(IOException.class, () -> new ReleaseVerifier().verify(fixture.releaseDirectory()));
    }

    @Test
    void rejectsAssetSemanticChecksumAndMinecraftVersionEvenAfterOuterChecksumRewrite(@TempDir Path directory)
            throws IOException {
        Fixture fixture = exportFixture(directory);
        Path catalog = fixture.releaseDirectory().resolve("assets/required-assets.json");
        String original = Files.readString(catalog);
        String badSemantic = original.replaceFirst(
                "\"semanticChecksum\" : \"[0-9a-f]{64}\"",
                "\"semanticChecksum\" : \"0000000000000000000000000000000000000000000000000000000000000000\""
        );
        Files.writeString(catalog, badSemantic);
        rewriteReleaseChecksum(fixture.releaseDirectory(), "assets/required-assets.json");
        assertThrows(IOException.class, () -> new ReleaseVerifier().verify(fixture.releaseDirectory()));

        Files.writeString(catalog, original.replace("1.21.11", "1.21.10"));
        rewriteReleaseChecksum(fixture.releaseDirectory(), "assets/required-assets.json");
        assertThrows(IOException.class, () -> new ReleaseVerifier().verify(fixture.releaseDirectory()));
    }

    @Test
    void rejectsDuplicateTileEvenWithUpdatedFileChecksum(@TempDir Path directory) throws IOException {
        Fixture fixture = exportFixture(directory);
        Path manifest = fixture.releaseDirectory().resolve("manifest.json");
        String original = Files.readString(manifest);
        String duplicate = original
                .replaceFirst("\\\"xIndex\\\" : 1", "\\\"xIndex\\\" : 0")
                .replaceFirst("\\\"originX\\\" : 32", "\\\"originX\\\" : 0");
        assertTrue(!duplicate.equals(original));
        Files.writeString(manifest, duplicate);
        Path checksums = fixture.releaseDirectory().resolve("checksums.sha256");
        List<String> updated = Files.readAllLines(checksums).stream()
                .map(line -> line.endsWith("  manifest.json")
                        ? uncheckedHash(manifest) + "  manifest.json" : line)
                .toList();
        Files.write(checksums, updated, StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> new ReleaseVerifier().verify(fixture.releaseDirectory()));
    }

    @Test
    void zipChecksumProtectsDeliveryArtifact(@TempDir Path directory) throws IOException {
        Fixture fixture = exportFixture(directory);
        String line = Files.readString(fixture.zipChecksum(), StandardCharsets.UTF_8);
        assertTrue(line.startsWith(Sha256.file(fixture.zip())));
        Files.write(fixture.zip(), new byte[]{0}, java.nio.file.StandardOpenOption.APPEND);
        assertTrue(!Files.readString(fixture.zipChecksum()).startsWith(Sha256.file(fixture.zip())));
        assertThrows(IOException.class, () -> new ReleaseVerifier().verify(fixture.zip()));
    }

    @Test
    void rejectsSymlinkedReleaseContent(@TempDir Path directory) throws IOException {
        Fixture fixture = exportFixture(directory);
        Path target = fixture.releaseDirectory().resolve("README.txt");
        Files.createSymbolicLink(fixture.releaseDirectory().resolve("linked-readme.txt"), target);

        assertThrows(IOException.class, () -> new ReleaseVerifier().verify(fixture.releaseDirectory()));
    }

    @Test
    void rejectsZipTraversalAndCaseCollisionsBeforeExtraction(@TempDir Path directory) throws IOException {
        Path traversal = directory.resolve("traversal.zip");
        writeZip(traversal, Map.of("../escaped.txt", "no"));
        assertThrows(IOException.class, () -> new ReleaseVerifier().verify(traversal));
        assertTrue(Files.notExists(directory.resolve("escaped.txt")));

        Path collision = directory.resolve("collision.zip");
        writeZip(collision, Map.of("manifest.json", "one", "Manifest.json", "two"));
        assertThrows(IOException.class, () -> new ReleaseVerifier().verify(collision));
    }

    @Test
    void rejectsFutureManifestVersionEvenWhenChecksumsAreRewritten(@TempDir Path directory) throws IOException {
        Fixture fixture = exportFixture(directory);
        Path manifest = fixture.releaseDirectory().resolve("manifest.json");
        String original = Files.readString(manifest);
        String future = original.replaceFirst("\\\"formatVersion\\\" : 1", "\\\"formatVersion\\\" : 2");
        assertTrue(!future.equals(original));
        Files.writeString(manifest, future);
        Path checksums = fixture.releaseDirectory().resolve("checksums.sha256");
        Files.write(
                checksums,
                Files.readAllLines(checksums).stream()
                        .map(line -> line.endsWith("  manifest.json")
                                ? uncheckedHash(manifest) + "  manifest.json" : line)
                        .toList(),
                StandardCharsets.UTF_8
        );

        assertThrows(IOException.class, () -> new ReleaseVerifier().verify(fixture.releaseDirectory()));
    }

    private static void writeZip(Path path, Map<String, String> entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static Fixture exportFixture(Path directory) throws IOException {
        Files.createDirectories(directory);
        String request = Files.readString(Path.of("examples/rocky-coast/request.yml"))
                .replace("rocky-coast-001", "release-test")
                .replace("width: 500", "width: 64")
                .replace("length: 500", "length: 64")
                .replace("min-y: -32", "min-y: 0")
                .replace("max-y: 160", "max-y: 31")
                .replace("water-level: 62", "water-level: 15")
                .replace("tile-size: 128", "tile-size: 32");
        String intent = Files.readString(Path.of("examples/rocky-coast/terrain-intent.json"))
                .replace("rocky-coast-001", "release-test")
                .replace("SMALL_PIER", "FENCE");
        Path requestPath = directory.resolve("request.yml");
        Path intentPath = directory.resolve("terrain-intent.json");
        Files.writeString(requestPath, request);
        Files.writeString(intentPath, intent);
        Path exports = directory.resolve("exports");
        try (GenerationExecutors executors = GenerationExecutors.create(2, 2, 8)) {
            var release = new ReleaseApplicationService(executors)
                    .export(requestPath, intentPath, exports, 0).join();
            return new Fixture(release.releaseDirectory(), release.zip().orElseThrow(),
                    release.zipChecksum().orElseThrow());
        }
    }

    private static String uncheckedHash(Path path) {
        try {
            return Sha256.file(path);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static void rewriteReleaseChecksum(Path release, String relative) throws IOException {
        Path checksums = release.resolve("checksums.sha256");
        Path artifact = release.resolve(relative);
        Files.write(
                checksums,
                Files.readAllLines(checksums).stream()
                        .map(line -> line.endsWith("  " + relative)
                                ? uncheckedHash(artifact) + "  " + relative : line)
                        .toList(),
                StandardCharsets.UTF_8
        );
    }

    private record Fixture(Path releaseDirectory, Path zip, Path zipChecksum) {
    }
}
