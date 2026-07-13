package com.github.nankotsu029.landformcraft.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/** Portable release reference to one verified structure schematic asset. */
public record RequiredAsset(
        String assetId,
        StructureType type,
        String minecraftVersion,
        String semanticChecksum,
        String file,
        String fileChecksum,
        int width,
        int height,
        int length
) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public RequiredAsset {
        assetId = ModelValidation.requireSlug(assetId, "assetId");
        Objects.requireNonNull(type, "type");
        minecraftVersion = ModelValidation.requireNonBlank(minecraftVersion, "minecraftVersion", 32);
        semanticChecksum = ModelValidation.requireNonBlank(semanticChecksum, "semanticChecksum");
        file = ModelValidation.requireNonBlank(file, "file", 256);
        fileChecksum = ModelValidation.requireNonBlank(fileChecksum, "fileChecksum");
        if (!SHA_256.matcher(semanticChecksum).matches() || !SHA_256.matcher(fileChecksum).matches()) {
            throw new IllegalArgumentException("asset checksums must be lowercase SHA-256 values");
        }
        Path value = Path.of(file);
        if (value.isAbsolute() || file.contains("\\") || !file.startsWith("assets/schematics/")
                || !file.endsWith(".schem") || !value.normalize().toString().replace('\\', '/').equals(file)) {
            throw new IllegalArgumentException("asset file must be a canonical assets/schematics path");
        }
        if (width < 1 || width > 64 || height < 1 || height > 64 || length < 1 || length > 64) {
            throw new IllegalArgumentException("asset dimensions must be between 1 and 64");
        }
    }
}
