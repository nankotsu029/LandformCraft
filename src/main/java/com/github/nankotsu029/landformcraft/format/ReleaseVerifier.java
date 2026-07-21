package com.github.nankotsu029.landformcraft.format;

import com.github.nankotsu029.landformcraft.model.ExportManifest;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.ManifestTile;
import com.github.nankotsu029.landformcraft.model.RequiredAsset;
import com.github.nankotsu029.landformcraft.model.RequiredAssets;
import com.github.nankotsu029.landformcraft.model.StructurePlacementManifest;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.WorldBlueprint;
import com.github.nankotsu029.landformcraft.structure.BuiltInStructureAssetCatalog;
import com.github.nankotsu029.landformcraft.structure.StructureAsset;
import com.github.nankotsu029.landformcraft.worldedit.SchematicInfo;
import com.github.nankotsu029.landformcraft.worldedit.SpongeSchematicInspector;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Strict release reader used before publish, after transport, and before future world placement. */
public final class ReleaseVerifier {
    private static final Pattern CHECKSUM_LINE = Pattern.compile("^([0-9a-f]{64})  ([^\\r\\n]+)$");
    private static final int MAX_ZIP_ENTRIES = 2_100;
    private static final long MAX_ZIP_EXPANDED_BYTES = 2L * 1024L * 1024L * 1024L;

    private final LandformDataCodec codec = new LandformDataCodec();
    private final SpongeSchematicInspector schematicInspector = new SpongeSchematicInspector();
    private final BuiltInStructureAssetCatalog assetCatalog = new BuiltInStructureAssetCatalog();

    public ReleaseVerification verify(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            return verifyDirectory(normalized);
        }
        if (Files.isRegularFile(normalized) && normalized.getFileName().toString().endsWith(".zip")) {
            return verifyZip(normalized);
        }
        throw new IOException("release must be a directory or .zip file: " + path);
    }

    public ReleaseVerification verifyDirectory(Path releaseDirectory) throws IOException {
        Path root = releaseDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw new IOException("release root must be a non-symbolic directory");
        }
        Map<String, Path> actualFiles = collectFiles(root);
        Path checksumsPath = requiredFile(actualFiles, "checksums.sha256");
        Map<String, String> expectedChecksums = readChecksums(checksumsPath);
        Set<String> actualCanonical = new HashSet<>(actualFiles.keySet());
        actualCanonical.remove("checksums.sha256");
        if (!expectedChecksums.keySet().equals(actualCanonical)) {
            throw new IOException("checksums.sha256 does not exactly cover the release files");
        }
        for (Map.Entry<String, String> entry : expectedChecksums.entrySet()) {
            if (!Sha256.file(requiredFile(actualFiles, entry.getKey())).equals(entry.getValue())) {
                throw new IOException("checksum mismatch: " + entry.getKey());
            }
        }

        ExportManifest manifest;
        GenerationRequest request;
        TerrainIntent intent;
        WorldBlueprint blueprint;
        RequiredAssets requiredAssets;
        StructurePlacementManifest placements;
        try {
            manifest = codec.readExportManifest(requiredFile(actualFiles, "manifest.json"));
            request = codec.readGenerationRequest(requiredFile(actualFiles, "request.yml"));
            intent = codec.readTerrainIntent(requiredFile(actualFiles, "terrain-intent.json"));
            blueprint = codec.readWorldBlueprint(requiredFile(actualFiles, "world-blueprint.json"));
            requiredAssets = codec.readRequiredAssets(requiredFile(actualFiles, "assets/required-assets.json"));
            placements = codec.readStructurePlacements(requiredFile(actualFiles, "structures.json"));
        } catch (com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException exception) {
            throw new IOException("release structured data failed schema validation", exception);
        }
        if (!manifest.requestId().equals(request.requestId())
                || !manifest.requestId().equals(blueprint.requestId())
                || manifest.seed() != blueprint.seed()
                || manifest.width() != request.bounds().width()
                || manifest.length() != request.bounds().length()
                || manifest.minY() != request.bounds().minY()
                || manifest.maxY() != request.bounds().maxY()
                || manifest.tileSize() != request.output().tileSize()
                || !blueprint.intent().equals(intent)
                || !manifest.generatorVersion().equals(placements.generatorVersion())
                || !manifest.minecraftVersion().equals(requiredAssets.minecraftVersion())) {
            throw new IOException("release contracts do not describe the same request and blueprint");
        }
        Set<String> allowedFiles = new HashSet<>(List.of(
                "manifest.json", "request.yml", "terrain-intent.json", "world-blueprint.json",
                "validation.json", "README.txt", "structures.json", "assets/required-assets.json",
                "previews/overview.png", "previews/height.png", "previews/water.png",
                "previews/slope.png", "previews/materials.png", "previews/features.png",
                "previews/structures.png", "previews/validation.png"
        ));
        manifest.tiles().forEach(tile -> allowedFiles.add(tile.file()));
        requiredAssets.assets().forEach(asset -> allowedFiles.add(asset.file()));
        if (!expectedChecksums.keySet().equals(allowedFiles)) {
            throw new IOException("release contains a missing or unknown canonical artifact");
        }
        verifyStructureAssets(actualFiles, expectedChecksums, manifest, requiredAssets, placements);
        for (ManifestTile tile : manifest.tiles()) {
            Path tileFile = requiredFile(actualFiles, tile.file());
            if (!tile.file().startsWith("schematics/") || !tile.file().endsWith(".schem")
                    || !expectedChecksums.get(tile.file()).equals(tile.checksum())) {
                throw new IOException("manifest tile checksum or path is inconsistent: " + tile.id());
            }
            SchematicInfo info = schematicInspector.inspect(tileFile);
            if (info.width() != tile.width() || info.length() != tile.length()
                    || info.height() != tile.maxY() - tile.minY() + 1
                    || info.offsetX() != 0 || info.offsetY() != 0 || info.offsetZ() != 0
                    || info.blockEntryCount() != tile.blockCount()) {
                throw new IOException("schematic metadata does not match manifest tile: " + tile.id());
            }
        }
        return new ReleaseVerification(root, manifest, expectedChecksums.size(), manifest.tiles().size());
    }

    private void verifyStructureAssets(
            Map<String, Path> actualFiles,
            Map<String, String> expectedChecksums,
            ExportManifest manifest,
            RequiredAssets requiredAssets,
            StructurePlacementManifest placements
    ) throws IOException {
        Map<String, RequiredAsset> requiredById = new HashMap<>();
        for (RequiredAsset required : requiredAssets.assets()) {
            if (!expectedChecksums.get(required.file()).equals(required.fileChecksum())) {
                throw new IOException("asset file checksum does not match release checksum: " + required.assetId());
            }
            StructureAsset builtIn;
            try {
                builtIn = assetCatalog.requireById(required.assetId());
            } catch (IllegalArgumentException exception) {
                throw new IOException("release references an unknown structure asset", exception);
            }
            if (required.type() != builtIn.type()
                    || !required.minecraftVersion().equals(builtIn.minecraftVersion())
                    || !required.semanticChecksum().equals(builtIn.semanticChecksum())
                    || required.width() != builtIn.width()
                    || required.height() != builtIn.height()
                    || required.length() != builtIn.length()) {
                throw new IOException("asset metadata does not match built-in catalog: " + required.assetId());
            }
            SchematicInfo info = schematicInspector.inspect(requiredFile(actualFiles, required.file()));
            long expectedEntries = (long) required.width() * required.height() * required.length();
            if (info.width() != required.width() || info.height() != required.height()
                    || info.length() != required.length()
                    || info.offsetX() != 0 || info.offsetY() != 0 || info.offsetZ() != 0
                    || info.blockEntryCount() != expectedEntries) {
                throw new IOException("asset schematic metadata is inconsistent: " + required.assetId());
            }
            requiredById.put(required.assetId(), required);
        }

        Set<String> usedIds = new HashSet<>();
        for (var placement : placements.structures()) {
            RequiredAsset required = requiredById.get(placement.assetId());
            StructureAsset builtIn;
            try {
                builtIn = assetCatalog.requireById(placement.assetId());
            } catch (IllegalArgumentException exception) {
                throw new IOException("placement references an unknown structure asset", exception);
            }
            if (required == null
                    || placement.type() != builtIn.type()
                    || !placement.assetChecksum().equals(builtIn.semanticChecksum())
                    || !placement.minecraftVersion().equals(manifest.minecraftVersion())
                    || placement.sizeX() != builtIn.rotatedWidth(placement.rotation())
                    || placement.sizeY() != builtIn.height()
                    || placement.sizeZ() != builtIn.rotatedLength(placement.rotation())
                    || placement.terrainFollowing() != builtIn.terrainFollowing()
                    || placement.anchorX() < 0 || placement.anchorZ() < 0
                    || placement.anchorX() + placement.sizeX() > manifest.width()
                    || placement.anchorZ() + placement.sizeZ() > manifest.length()
                    || placement.anchorY() < manifest.minY()
                    || placement.anchorY() + placement.sizeY() - 1 > manifest.maxY()) {
                throw new IOException("invalid structure placement: " + placement.assetId());
            }
            usedIds.add(placement.assetId());
        }
        if (!usedIds.equals(requiredById.keySet())) {
            throw new IOException("required asset catalog must exactly match placement usage");
        }
        for (int left = 0; left < placements.structures().size(); left++) {
            var a = placements.structures().get(left);
            for (int right = left + 1; right < placements.structures().size(); right++) {
                var b = placements.structures().get(right);
                if (a.anchorX() < b.anchorX() + b.sizeX() && a.anchorX() + a.sizeX() > b.anchorX()
                        && a.anchorZ() < b.anchorZ() + b.sizeZ() && a.anchorZ() + a.sizeZ() > b.anchorZ()) {
                    throw new IOException("structure placements overlap");
                }
            }
        }
    }

    private ReleaseVerification verifyZip(Path zip) throws IOException {
        verifySiblingZipChecksumIfPresent(zip);
        Path parent = zip.getParent();
        Path temporary = Files.createTempDirectory(parent, ".lfc-verify-");
        try {
            extractZip(zip, temporary);
            ReleaseVerification verification = verifyDirectory(temporary);
            return new ReleaseVerification(zip, verification.manifest(),
                    verification.verifiedFiles(), verification.verifiedTiles());
        } finally {
            FileTreeOperations.deleteTree(temporary);
        }
    }

    private static void verifySiblingZipChecksumIfPresent(Path zip) throws IOException {
        Path digest = zip.resolveSibling(zip.getFileName() + ".sha256");
        if (!Files.exists(digest)) {
            return;
        }
        if (!Files.isRegularFile(digest) || Files.isSymbolicLink(digest)) {
            throw new IOException("ZIP checksum must be a regular non-symbolic file");
        }
        String expectedLine = Sha256.file(zip) + "  " + zip.getFileName();
        String actual = Files.readString(digest, StandardCharsets.UTF_8).stripTrailing();
        if (!actual.equals(expectedLine)) {
            throw new IOException("ZIP delivery checksum mismatch");
        }
    }

    private static void extractZip(Path zip, Path target) throws IOException {
        Set<String> names = new HashSet<>();
        Set<String> foldedNames = new HashSet<>();
        int entries = 0;
        long expanded = 0;
        try (InputStream file = Files.newInputStream(zip);
             ZipInputStream input = new ZipInputStream(new BufferedInputStream(file), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new IOException("ZIP contains too many entries");
                }
                String name = canonicalRelativePath(entry.getName());
                if (!names.add(name) || !foldedNames.add(name.toLowerCase(Locale.ROOT))) {
                    throw new IOException("ZIP contains duplicate or case-colliding entries");
                }
                Path destination = target.resolve(name).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("ZIP entry escapes extraction root");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    try (var output = Files.newOutputStream(destination)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            if (read > 0) {
                                expanded += read;
                                if (expanded > MAX_ZIP_EXPANDED_BYTES) {
                                    throw new IOException("ZIP expands beyond the allowed size");
                                }
                                output.write(buffer, 0, read);
                            }
                        }
                    }
                }
                input.closeEntry();
            }
        }
    }

    private static Map<String, Path> collectFiles(Path root) throws IOException {
        Map<String, Path> files = new HashMap<>();
        Set<String> folded = new HashSet<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("symbolic links are not allowed in releases");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) {
                    throw new IOException("release contains a non-regular file");
                }
                String relative = canonicalRelativePath(root.relativize(file).toString().replace('\\', '/'));
                if (files.putIfAbsent(relative, file) != null || !folded.add(relative.toLowerCase(Locale.ROOT))) {
                    throw new IOException("release contains duplicate or case-colliding files");
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static Map<String, String> readChecksums(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || lines.size() > MAX_ZIP_ENTRIES) {
            throw new IOException("invalid checksums.sha256 entry count");
        }
        Map<String, String> values = new java.util.TreeMap<>();
        String previous = null;
        for (String line : lines) {
            var matcher = CHECKSUM_LINE.matcher(line);
            if (!matcher.matches()) {
                throw new IOException("invalid checksums.sha256 line");
            }
            String relative = canonicalRelativePath(matcher.group(2));
            if ("checksums.sha256".equals(relative) || values.putIfAbsent(relative, matcher.group(1)) != null
                    || previous != null && previous.compareTo(relative) >= 0) {
                throw new IOException("checksums.sha256 paths must be unique and sorted");
            }
            previous = relative;
        }
        return Map.copyOf(values);
    }

    static String canonicalRelativePath(String input) throws IOException {
        if (input.isBlank() || input.startsWith("/") || input.startsWith("\\")
                || input.contains("\\") || input.contains("//") || input.matches("^[A-Za-z]:.*")) {
            throw new IOException("unsafe release path: " + input);
        }
        Path normalized;
        try {
            normalized = Path.of(input).normalize();
        } catch (RuntimeException exception) {
            throw new IOException("invalid release path", exception);
        }
        String value = normalized.toString().replace('\\', '/');
        if (value.equals(".") || value.equals("..") || value.startsWith("../") || !value.equals(input)) {
            throw new IOException("non-canonical release path: " + input);
        }
        return value;
    }

    private static Path requiredFile(Map<String, Path> files, String name) throws IOException {
        Path path = files.get(name);
        if (path == null) {
            throw new IOException("required release file is missing: " + name);
        }
        return path;
    }

}
