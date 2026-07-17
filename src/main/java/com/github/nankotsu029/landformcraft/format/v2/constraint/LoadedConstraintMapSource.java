package com.github.nankotsu029.landformcraft.format.v2.constraint;

import java.util.Objects;

/** Immutable verified source bytes; raw filesystem state does not cross this boundary. */
public final class LoadedConstraintMapSource {
    private final String sourceId;
    private final String relativePath;
    private final String mediaType;
    private final String sourceChecksum;
    private final byte[] content;

    LoadedConstraintMapSource(
            String sourceId,
            String relativePath,
            String mediaType,
            String sourceChecksum,
            byte[] content
    ) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        this.sourceChecksum = Objects.requireNonNull(sourceChecksum, "sourceChecksum");
        // The package-private constructor receives ownership from the secure loader. Public callers only get copies.
        this.content = Objects.requireNonNull(content, "content");
    }

    public String sourceId() {
        return sourceId;
    }

    public String relativePath() {
        return relativePath;
    }

    public String mediaType() {
        return mediaType;
    }

    public String sourceChecksum() {
        return sourceChecksum;
    }

    public int sourceBytes() {
        return content.length;
    }

    public byte[] contentCopy() {
        return content.clone();
    }

    byte[] contentForDecode() {
        return content;
    }
}
