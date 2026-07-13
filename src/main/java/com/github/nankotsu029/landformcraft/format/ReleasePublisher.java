package com.github.nankotsu029.landformcraft.format;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationOutcome;
import com.github.nankotsu029.landformcraft.model.ExportManifest;
import com.github.nankotsu029.landformcraft.model.ManifestAirPolicy;
import com.github.nankotsu029.landformcraft.model.ManifestAnchor;
import com.github.nankotsu029.landformcraft.model.ManifestTile;
import com.github.nankotsu029.landformcraft.model.ManifestTileStatus;
import com.github.nankotsu029.landformcraft.model.RequiredAsset;
import com.github.nankotsu029.landformcraft.model.RequiredAssets;
import com.github.nankotsu029.landformcraft.model.StructurePlacementManifest;
import com.github.nankotsu029.landformcraft.model.TilePlan;
import com.github.nankotsu029.landformcraft.structure.BuiltInStructureAssetCatalog;
import com.github.nankotsu029.landformcraft.structure.StructureAsset;
import com.github.nankotsu029.landformcraft.worldedit.SpongeSchematicWriter;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds, verifies, fsyncs, and atomically publishes a canonical release directory and optional ZIP. */
public final class ReleasePublisher {
    private final LandformDataCodec codec = new LandformDataCodec();
    private final SpongeSchematicWriter schematicWriter = new SpongeSchematicWriter();
    private final ReleaseVerifier verifier = new ReleaseVerifier();
    private final BuiltInStructureAssetCatalog assetCatalog = new BuiltInStructureAssetCatalog();

    public ReleaseArtifacts publish(
            Path requestPath,
            Path intentPath,
            Path exportsRoot,
            GenerationOutcome outcome,
            CancellationToken token
    ) throws IOException {
        if (!outcome.validation().isValid()) {
            throw new IOException("invalid terrain cannot be published as a release");
        }
        var plan = outcome.terrainPlan();
        var blueprint = plan.blueprint();
        var request = codec.readGenerationRequest(requestPath);
        if (!request.output().createSchematics()) {
            throw new IOException("release export requires output.create-schematics: true");
        }
        String releaseId = "release-" + plan.checksum().substring(0, 16);
        Path requestDirectory = exportsRoot.toAbsolutePath().normalize().resolve(blueprint.requestId());
        Files.createDirectories(requestDirectory);
        Path finalDirectory = requestDirectory.resolve(releaseId);
        if (Files.exists(finalDirectory)) {
            throw new IOException("release already exists: " + finalDirectory);
        }
        Path temporary = requestDirectory.resolve(".tmp-" + releaseId + "-" + UUID.randomUUID());
        boolean directoryPublished = false;
        Path finalZip = requestDirectory.resolve(releaseId + ".zip");
        Path finalZipChecksum = requestDirectory.resolve(releaseId + ".zip.sha256");
        try {
            buildDirectory(requestPath, intentPath, temporary, outcome, token);
            verifier.verifyDirectory(temporary);
            forceTree(temporary);
            moveAtomically(temporary, finalDirectory);
            directoryPublished = true;

            if (!request.output().createZip()) {
                return new ReleaseArtifacts(releaseId, finalDirectory, Optional.empty(), Optional.empty());
            }
            if (Files.exists(finalZip) || Files.exists(finalZipChecksum)) {
                throw new IOException("release ZIP already exists");
            }
            Path temporaryZip = requestDirectory.resolve(".tmp-" + UUID.randomUUID() + "-" + releaseId + ".zip");
            Path temporaryDigest = requestDirectory.resolve(".tmp-" + releaseId + ".sha256-" + UUID.randomUUID());
            try {
                createZip(finalDirectory, temporaryZip);
                verifier.verify(temporaryZip);
                forceFile(temporaryZip);
                moveAtomically(temporaryZip, finalZip);
                String digestLine = Sha256.file(finalZip) + "  " + finalZip.getFileName() + "\n";
                Files.writeString(temporaryDigest, digestLine, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                forceFile(temporaryDigest);
                moveAtomically(temporaryDigest, finalZipChecksum);
            } finally {
                Files.deleteIfExists(temporaryZip);
                Files.deleteIfExists(temporaryDigest);
            }
            return new ReleaseArtifacts(
                    releaseId, finalDirectory, Optional.of(finalZip), Optional.of(finalZipChecksum)
            );
        } catch (IOException | RuntimeException exception) {
            ReleaseVerifier.deleteTree(temporary);
            if (directoryPublished) {
                ReleaseVerifier.deleteTree(finalDirectory);
                Files.deleteIfExists(finalZip);
                Files.deleteIfExists(finalZipChecksum);
            }
            throw exception;
        }
    }

    private void buildDirectory(
            Path requestPath,
            Path intentPath,
            Path temporary,
            GenerationOutcome outcome,
            CancellationToken token
    ) throws IOException {
        Files.createDirectories(temporary.resolve("previews"));
        Files.createDirectories(temporary.resolve("schematics"));
        Files.createDirectories(temporary.resolve("assets/schematics"));
        copyContract(requestPath, temporary.resolve("request.yml"));
        copyContract(intentPath, temporary.resolve("terrain-intent.json"));
        codec.writeWorldBlueprint(temporary.resolve("world-blueprint.json"), outcome.terrainPlan().blueprint());
        codec.writeJson(temporary.resolve("validation.json"), outcome.validation());
        var plan = outcome.terrainPlan();
        codec.writeStructurePlacements(temporary.resolve("structures.json"),
                new StructurePlacementManifest(1, plan.blueprint().generatorVersion(), plan.structures()));
        List<RequiredAsset> requiredAssets = writeRequiredAssets(temporary, plan.structures(), token);
        codec.writeRequiredAssets(temporary.resolve("assets/required-assets.json"),
                new RequiredAssets(1, BuiltInStructureAssetCatalog.MINECRAFT_VERSION, requiredAssets));
        for (Path preview : outcome.previews().files()) {
            token.throwIfCancellationRequested();
            copyContract(preview, temporary.resolve("previews").resolve(preview.getFileName()));
        }

        List<ManifestTile> manifestTiles = new ArrayList<>();
        int minY = plan.blueprint().bounds().minY();
        int maxY = plan.blueprint().bounds().maxY();
        for (TilePlan tile : plan.tiles()) {
            token.throwIfCancellationRequested();
            String relative = "schematics/" + tile.id() + ".schem";
            Path file = temporary.resolve(relative);
            schematicWriter.write(file, plan, tile, token);
            long blockCount = (long) tile.width() * tile.length() * (maxY - minY + 1L);
            manifestTiles.add(new ManifestTile(
                    tile.id(), tile.xIndex(), tile.zIndex(), tile.originX(), minY, tile.originZ(),
                    tile.width(), tile.length(), minY, maxY, relative, Sha256.file(file), tile.checksum(), blockCount,
                    ManifestAirPolicy.INCLUDED, ManifestTileStatus.READY
            ));
        }
        var blueprint = plan.blueprint();
        ExportManifest manifest = new ExportManifest(
                1, blueprint.generatorVersion(), "1.21.11", blueprint.requestId(),
                blueprint.bounds().width(), blueprint.bounds().length(), minY, maxY,
                blueprint.tileSize(), blueprint.tileCountX(), blueprint.tileCountZ(),
                ManifestAnchor.MINIMUM_CORNER, blueprint.seed(), manifestTiles
        );
        codec.writeExportManifest(temporary.resolve("manifest.json"), manifest);
        Files.writeString(temporary.resolve("README.txt"),
                releaseReadme(manifest, plan.structures().size(), requiredAssets.size()), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        writeChecksums(temporary);
    }

    private List<RequiredAsset> writeRequiredAssets(
            Path root,
            List<com.github.nankotsu029.landformcraft.model.StructurePlan> placements,
            CancellationToken token
    ) throws IOException {
        List<StructureAsset> assets = placements.stream()
                .map(placement -> {
                    StructureAsset asset = assetCatalog.requireById(placement.assetId());
                    if (asset.type() != placement.type()
                            || !asset.semanticChecksum().equals(placement.assetChecksum())
                            || !asset.minecraftVersion().equals(placement.minecraftVersion())
                            || asset.rotatedWidth(placement.rotation()) != placement.sizeX()
                            || asset.height() != placement.sizeY()
                            || asset.rotatedLength(placement.rotation()) != placement.sizeZ()
                            || asset.terrainFollowing() != placement.terrainFollowing()) {
                        throw new IllegalArgumentException("structure placement does not match asset: "
                                + placement.assetId());
                    }
                    return asset;
                })
                .distinct()
                .sorted(Comparator.comparing(StructureAsset::assetId))
                .toList();
        List<RequiredAsset> required = new ArrayList<>();
        for (StructureAsset asset : assets) {
            token.throwIfCancellationRequested();
            String relative = "assets/schematics/" + asset.assetId() + ".schem";
            Path file = root.resolve(relative);
            schematicWriter.writeAsset(file, asset, token);
            required.add(new RequiredAsset(
                    asset.assetId(), asset.type(), asset.minecraftVersion(), asset.semanticChecksum(),
                    relative, Sha256.file(file), asset.width(), asset.height(), asset.length()
            ));
        }
        return List.copyOf(required);
    }

    private static void writeChecksums(Path root) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> !root.relativize(path).toString().equals("checksums.sha256"))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')))
                    .toList();
        }
        StringBuilder checksums = new StringBuilder();
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            checksums.append(Sha256.file(file)).append("  ").append(relative).append('\n');
        }
        Files.writeString(root.resolve("checksums.sha256"), checksums, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void createZip(Path release, Path target) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(release)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> release.relativize(path).toString().replace('\\', '/')))
                    .toList();
        }
        try (var file = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             var output = new ZipOutputStream(new BufferedOutputStream(file), StandardCharsets.UTF_8)) {
            for (Path path : files) {
                String relative = release.relativize(path).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(relative);
                entry.setTime(0L);
                output.putNextEntry(entry);
                Files.copy(path, output);
                output.closeEntry();
            }
        }
    }

    private static void copyContract(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)
                || Files.size(source) > LandformDataCodec.MAX_DOCUMENT_BYTES && !source.toString().endsWith(".png")) {
            throw new IOException("artifact source is not a safe regular file: " + source);
        }
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static String releaseReadme(ExportManifest manifest, int structures, int assets) {
        return "LandformCraft Release Package\n"
                + "requestId: " + manifest.requestId() + "\n"
                + "minecraftVersion: " + manifest.minecraftVersion() + "\n"
                + "anchor: MINIMUM_CORNER\n"
                + "tiles: " + manifest.tileCountX() + " x " + manifest.tileCountZ() + "\n\n"
                + "structures: " + structures + "\n"
                + "requiredAssets: " + assets + "\n\n"
                + "Run `landformcraft verify <release-directory-or-zip>` after transport.\n"
                + "For placement, add each tile origin to the chosen target minimum corner.\n"
                + "Do not place a release that fails verification.\n";
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
            throw new IOException("filesystem does not support required atomic publish", exception);
        }
    }
}
