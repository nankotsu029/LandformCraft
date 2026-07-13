package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.CustomAssetCatalogEntry;
import com.github.nankotsu029.landformcraft.model.CustomAssetMetadata;
import com.github.nankotsu029.landformcraft.structure.BuiltInStructureAssetCatalog;
import com.github.nankotsu029.landformcraft.worldedit.MinecraftBlockPalette;
import com.github.nankotsu029.landformcraft.worldedit.SchematicInfo;
import com.github.nankotsu029.landformcraft.worldedit.SpongeSchematicInspector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Restricted Sponge v3 custom-asset validation and atomic catalog publication. */
public final class CustomAssetService {
    private final Path importRoot;
    private final Path catalogRoot;
    private final Path exportsRoot;
    private final Clock clock;
    private final LandformDataCodec codec = new LandformDataCodec();
    private final SpongeSchematicInspector inspector = new SpongeSchematicInspector();

    public CustomAssetService(Path importRoot, Path catalogRoot, Path exportsRoot, Clock clock) {
        this.importRoot = normalizeRoot(importRoot);
        this.catalogRoot = normalizeRoot(catalogRoot);
        this.exportsRoot = normalizeRoot(exportsRoot);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CustomAssetCatalogEntry validate(String schematic, String metadata) throws IOException {
        Path schematicPath = resolveInput(schematic);
        Path metadataPath = resolveInput(metadata);
        SchematicInfo info = inspector.inspect(schematicPath);
        CustomAssetMetadata supplied = codec.readCustomAssetMetadata(metadataPath);
        validateIdentity(supplied, info);
        for (String state : info.palette().keySet()) {
            if (!MinecraftBlockPalette.states().containsKey(state)) {
                throw unsafe("Custom asset palette contains an unsupported block state.");
            }
        }
        String artifactChecksum = Sha256.file(schematicPath);
        String semanticInput = codec.writeJsonString(supplied) + "\n"
                + info.width() + ':' + info.height() + ':' + info.length() + '\n'
                + info.palette().entrySet().stream().sorted(MapEntryComparator.INSTANCE)
                .map(value -> value.getKey() + '=' + value.getValue()).reduce("", (a, b) -> a + b + "\n")
                + artifactChecksum;
        return new CustomAssetCatalogEntry(
                1, supplied, info.width(), info.height(), info.length(), info.blockEntryCount(),
                artifactChecksum, Sha256.bytes(semanticInput.getBytes(StandardCharsets.UTF_8)), clock.instant());
    }

    public CustomAssetCatalogEntry importAsset(String schematic, String metadata) throws IOException {
        CustomAssetCatalogEntry entry = validate(schematic, metadata);
        String assetId = entry.metadata().assetId();
        Files.createDirectories(catalogRoot);
        rejectSymlink(catalogRoot, "custom asset catalog root");
        Path target = catalogRoot.resolve(assetId).normalize();
        if (!target.startsWith(catalogRoot) || Files.exists(target)) {
            throw unsafe("Custom asset ID already exists or is unsafe.");
        }
        Path temporary = Files.createTempDirectory(catalogRoot, ".asset-");
        try {
            Files.copy(resolveInput(schematic), temporary.resolve("asset.schem"));
            Files.copy(resolveInput(metadata), temporary.resolve("metadata.json"));
            codec.writeCustomAssetCatalogEntry(temporary.resolve("catalog-entry.json"), entry);
            if (!Sha256.file(temporary.resolve("asset.schem")).equals(entry.artifactChecksum())) {
                throw unsafe("Custom asset changed while it was being imported.");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for custom asset import", exception);
            }
        } finally {
            if (Files.exists(temporary)) {
                deleteKnownFiles(temporary);
            }
        }
        return info(assetId);
    }

    public List<CustomAssetCatalogEntry> list() throws IOException {
        if (!Files.isDirectory(catalogRoot)) {
            return List.of();
        }
        rejectSymlink(catalogRoot, "custom asset catalog root");
        List<CustomAssetCatalogEntry> values = new ArrayList<>();
        try (var paths = Files.list(catalogRoot)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isSymbolicLink(path) || !Files.isDirectory(path)
                        || path.getFileName().toString().startsWith(".")) {
                    continue;
                }
                values.add(info(path.getFileName().toString()));
            }
        }
        return List.copyOf(values);
    }

    public CustomAssetCatalogEntry info(String assetId) throws IOException {
        requireSlug(assetId);
        Path directory = catalogRoot.resolve(assetId).normalize();
        if (!directory.startsWith(catalogRoot) || !Files.isDirectory(directory)
                || Files.isSymbolicLink(directory)) {
            throw new LandformException(LandformErrorCode.NOT_FOUND, "Custom asset was not found.",
                    "asset-info", assetId, "catalog", "Run asset list or import the asset first.");
        }
        CustomAssetCatalogEntry entry = codec.readCustomAssetCatalogEntry(directory.resolve("catalog-entry.json"));
        if (!entry.metadata().assetId().equals(assetId)
                || !Sha256.file(directory.resolve("asset.schem")).equals(entry.artifactChecksum())) {
            throw unsafe("Custom asset catalog identity or checksum does not match.");
        }
        return entry;
    }

    public void remove(String assetId) throws IOException {
        CustomAssetCatalogEntry ignored = info(assetId);
        if (isReferenced(assetId)) {
            throw unsafe("Custom asset is referenced by an existing Release Package.");
        }
        deleteKnownFiles(catalogRoot.resolve(assetId));
    }

    private boolean isReferenced(String assetId) throws IOException {
        if (!Files.isDirectory(exportsRoot)) {
            return false;
        }
        rejectSymlink(exportsRoot, "exports root");
        try (var paths = Files.walk(exportsRoot, 3)) {
            for (Path path : paths.filter(value -> value.getFileName().toString()
                    .equals("required-assets.json")).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw unsafe("A symbolic link was found while checking asset usage.");
                }
                if (codec.readRequiredAssets(path).assets().stream()
                        .anyMatch(value -> value.assetId().equals(assetId))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validateIdentity(CustomAssetMetadata metadata, SchematicInfo info) {
        if (new BuiltInStructureAssetCatalog().assets().stream()
                .anyMatch(value -> value.assetId().equals(metadata.assetId()))) {
            throw unsafe("Custom asset ID conflicts with a built-in asset.");
        }
        if (info.width() > 64 || info.height() > 64 || info.length() > 64
                || info.blockEntryCount() > 32_768) {
            throw unsafe("Custom asset exceeds the 64x64x64 or 32768-block beta limit.");
        }
    }

    private Path resolveInput(String relative) throws IOException {
        Objects.requireNonNull(relative, "relative");
        Path value = Path.of(relative);
        if (value.isAbsolute() || relative.contains("\\") || !value.normalize().equals(value)
                || relative.isBlank()) {
            throw unsafe("Asset input must be a canonical relative path under the import directory.");
        }
        Path resolved = importRoot.resolve(value).normalize();
        if (!resolved.startsWith(importRoot) || !Files.isRegularFile(resolved)
                || Files.isSymbolicLink(resolved)) {
            throw unsafe("Asset input is missing, outside the import directory, or a symbolic link.");
        }
        return resolved;
    }

    private static void deleteKnownFiles(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
            throw new IOException("asset directory must be a non-symbolic directory");
        }
        for (String name : List.of("asset.schem", "metadata.json", "catalog-entry.json")) {
            Path file = directory.resolve(name);
            if (Files.exists(file)) {
                if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                    throw new IOException("unexpected custom asset catalog entry");
                }
                Files.delete(file);
            }
        }
        try (var remaining = Files.list(directory)) {
            if (remaining.findAny().isPresent()) {
                throw new IOException("custom asset directory contains unexpected files");
            }
        }
        Files.delete(directory);
    }

    private static void rejectSymlink(Path path, String name) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException(name + " must not be a symbolic link");
        }
    }

    private static void requireSlug(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw unsafe("Invalid custom asset ID.");
        }
    }

    private static Path normalizeRoot(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static LandformException unsafe(String message) {
        return new LandformException(LandformErrorCode.ASSET_UNSAFE, message, "asset",
                "", "asset-validation", "Use a restricted Sponge v3 schematic and valid metadata.");
    }

    private enum MapEntryComparator implements Comparator<java.util.Map.Entry<String, Integer>> {
        INSTANCE;

        @Override
        public int compare(java.util.Map.Entry<String, Integer> left,
                           java.util.Map.Entry<String, Integer> right) {
            return left.getKey().compareTo(right.getKey());
        }
    }
}
