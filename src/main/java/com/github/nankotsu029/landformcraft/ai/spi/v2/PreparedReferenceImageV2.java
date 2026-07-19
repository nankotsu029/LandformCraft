package com.github.nankotsu029.landformcraft.ai.spi.v2;

import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;

import java.util.Objects;
import java.util.regex.Pattern;

/** Sanitized in-memory image content for v2 providers. No filesystem path reaches the adapter. */
public record PreparedReferenceImageV2(
        int index,
        String sourceFile,
        GenerationRequestV2.ReferenceImageRole role,
        String mediaType,
        int width,
        int height,
        String checksum,
        byte[] content
) {
    private static final Pattern SAFE_PATH = Pattern.compile("(?!.*(?:^|/)\\.\\.?(/|$))[A-Za-z0-9._/-]{1,512}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public PreparedReferenceImageV2 {
        if (index < 0 || index >= GenerationRequestV2.MAX_REFERENCE_IMAGES) {
            throw new IllegalArgumentException("index must be within GenerationRequestV2.MAX_REFERENCE_IMAGES");
        }
        Objects.requireNonNull(sourceFile, "sourceFile");
        if (!SAFE_PATH.matcher(sourceFile).matches() || sourceFile.startsWith("/") || sourceFile.contains("//")) {
            throw new IllegalArgumentException("sourceFile must be a portable relative path");
        }
        Objects.requireNonNull(role, "role");
        if (!"image/png".equals(mediaType)) {
            throw new IllegalArgumentException("prepared image mediaType must be image/png");
        }
        if (width < 1 || width > 4096 || height < 1 || height > 4096) {
            throw new IllegalArgumentException("prepared image dimensions must be between 1 and 4096");
        }
        Objects.requireNonNull(checksum, "checksum");
        if (!SHA_256.matcher(checksum).matches()) {
            throw new IllegalArgumentException("checksum must be a lowercase SHA-256");
        }
        Objects.requireNonNull(content, "content");
        if (content.length < 1 || content.length > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("prepared image bytes are outside the allowed range");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
